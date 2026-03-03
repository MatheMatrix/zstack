package org.zstack.compute.vm.metadata;

import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.compute.vm.VmGlobalConfig;
import org.zstack.core.Platform;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.cloudbus.CloudBusCallBack;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.Q;
import org.zstack.core.db.SQL;
import org.zstack.core.thread.ChainTask;
import org.zstack.core.thread.PeriodicTask;
import org.zstack.core.thread.SyncTaskChain;
import org.zstack.core.thread.ThreadFacade;
import org.zstack.header.Component;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.managementnode.ManagementNodeChangeListener;
import org.zstack.header.managementnode.ManagementNodeInventory;
import org.zstack.header.managementnode.ManagementNodeReadyExtensionPoint;
import org.zstack.header.message.MessageReply;
import org.zstack.header.vm.UpdateVmInstanceMetadataMsg;
import org.zstack.header.vm.VmInstanceConstant;
import org.zstack.header.vm.VmInstanceVO;
import org.zstack.header.vm.VmMetadataDirtyVO;
import org.zstack.header.vm.VmMetadataDirtyVO_;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * VM 元数据 Dirty Mark + Poller 机制的核心实现。
 *
 * <h3>职责</h3>
 * <ul>
 *   <li>{@link #markDirty(String)} — 标脏入口，INSERT ON DUPLICATE KEY UPDATE + 立即唤醒</li>
 *   <li>{@link MetadataDirtyPoller} — 周期轮询安全网，处理退避到期行、MN 宕机释放行等</li>
 *   <li>{@link #claimAndFlush()} — CAS 认领 + 提交刷写</li>
 *   <li>{@link #doFlush} — 构建 payload → 发送 UpdateVmInstanceMetadataMsg → 成功/失败处理</li>
 *   <li>{@link ManagementNodeChangeListener#nodeLeft} — MN 宕机后立即接管</li>
 * </ul>
 *
 * <h3>串行化保证（三层）</h3>
 * <pre>
 *   Layer 1 — DB CAS 认领：UPDATE WHERE managementNodeUuid IS NULL → 同一行只被一个 MN 处理
 *   Layer 2 — ChainTask 队列 "update-vm-{vmUuid}-metadata"：syncLevel=1, maxPending=1
 *   Layer 3 — 主存储级队列 "update-metadata-on-ps-{psUuid}"（在 PS handler 内部实现）
 * </pre>
 *
 * @see VmMetadataDirtyVO
 * @see VmMetadataUpdateInterceptor
 */
public class VmMetadataDirtyMarker implements Component, ManagementNodeChangeListener, ManagementNodeReadyExtensionPoint {
    private static final CLogger logger = Utils.getLogger(VmMetadataDirtyMarker.class);

    // =====================================================================
    //  常量
    // =====================================================================

    /** 指数退避基准延迟（秒） */
    private static final long BASE_DELAY_SECONDS = 10;

    /** 指数退避的指数上限，防止左移溢出。2^10 = 1024，最大延迟 = 10 * 1024 = 10240s ≈ 2.8h */
    private static final int MAX_EXPONENT = 10;

    // =====================================================================
    //  注入
    // =====================================================================

    @Autowired
    private CloudBus bus;

    @Autowired
    private DatabaseFacade dbf;

    @Autowired
    private ThreadFacade thdf;

    // =====================================================================
    //  Poller 状态
    // =====================================================================

    private Future<Void> pollerFuture;

    // =====================================================================
    //  Component 生命周期
    // =====================================================================

    @Override
    public boolean start() {
        return true;
    }

    @Override
    public boolean stop() {
        stopPoller();
        return true;
    }

    // =====================================================================
    //  ManagementNodeReadyExtensionPoint：MN 就绪后启动 Poller
    // =====================================================================

    @Override
    public void managementNodeReady() {
        startPoller();

        VmGlobalConfig.VM_METADATA_DIRTY_POLL_INTERVAL.installUpdateExtension((oldValue, newValue) -> {
            restartPoller();
        });
    }

    // =====================================================================
    //  ManagementNodeChangeListener：MN 拓扑变化处理
    // =====================================================================

    @Override
    public void nodeLeft(ManagementNodeInventory inv) {
        // MN 宕机 → FK SET_NULL 已释放其认领的 dirty 行
        // 异步触发一轮 Poller，避免阻塞心跳检测回调线程（P2-2.3 修复）
        logger.info(String.format("[MetadataDirty] node[%s] left, scheduling immediate claim and flush",
                inv.getUuid()));
        thdf.submit(new org.zstack.core.thread.Task<Void>() {
            @Override
            public String getName() {
                return "metadata-dirty-node-left-claim";
            }

            @Override
            public Void call() {
                claimAndFlush();
                return null;
            }
        });
    }

    @Override
    public void nodeJoin(ManagementNodeInventory inv) {
        // 无需特殊处理，新 MN 的 Poller 正常启动即可
    }

    @Override
    public void iAmDead(ManagementNodeInventory inv) {
        // 本 MN 即将死亡，不做处理
        // FK SET_NULL 会自动释放本 MN 认领的行
    }

    @Override
    public void iJoin(ManagementNodeInventory inv) {
        // 由 managementNodeReady 启动 Poller
    }

    // =====================================================================
    //  markDirty — 标脏入口（公开方法）
    // =====================================================================

    /**
     * 将指定 VM 标记为"元数据脏"，需要重新写入主存储。
     *
     * <p>使用 INSERT ON DUPLICATE KEY UPDATE，保证：</p>
     * <ul>
     *   <li>行不存在 → INSERT（dirtyVersion=1）</li>
     *   <li>行已存在 → dirtyVersion +1（标记"有新变更"）</li>
     *   <li>storageStructureChange 使用 OR 升级策略：一旦标记为 STORAGE，在本轮 dirty 生命周期内不会降级为 CONFIG</li>
     *   <li>不重置 retryCount（保留退避状态）</li>
     *   <li>不修改 managementNodeUuid（不干扰正在执行的刷写）</li>
     *   <li>不修改 nextRetryTime（不干扰退避计时）</li>
     * </ul>
     *
     * <p>markDirty 后立即调用 {@link #triggerFlushForVm(String)}，
     * 尝试认领并提交刷写，消除最长 N 秒的 Poller 等待延迟。</p>
     *
     * @param vmInstanceUuid       目标虚拟机 UUID
     * @param storageStructureChange 是否涉及存储结构变更（{@code @MetadataImpact(STORAGE)} → true）。
     *                               使用 OR 升级：若已有 STORAGE 标记，本次 CONFIG 不会将其降级。
     */
    public void markDirty(String vmInstanceUuid, boolean storageStructureChange) {
        // 前置检查：功能开关
        if (!VmGlobalConfig.VM_METADATA.value(Boolean.class)) {
            return;
        }

        // INSERT ... ON DUPLICATE KEY UPDATE dirtyVersion = dirtyVersion + 1
        // storageStructureChange 使用 OR 升级：false OR true = true, true OR false = true
        SQL.New("INSERT INTO VmMetadataDirtyVO (vmInstanceUuid, dirtyVersion, storageStructureChange) " +
                "VALUES (:vmUuid, 1, :ssc) " +
                "ON DUPLICATE KEY UPDATE dirtyVersion = dirtyVersion + 1, " +
                "storageStructureChange = storageStructureChange OR VALUES(storageStructureChange)")
                .param("vmUuid", vmInstanceUuid)
                .param("ssc", storageStructureChange)
                .execute();

        logger.debug(String.format("[MetadataDirty] marked dirty for vm[uuid:%s], storageStructureChange=%s",
                vmInstanceUuid, storageStructureChange));

        // 立即唤醒：尝试认领并提交刷写，不等待 Poller 轮询
        triggerFlushForVm(vmInstanceUuid);
    }

    /**
     * 标脏入口（便捷重载，默认 storageStructureChange=false，即 CONFIG 级别）。
     *
     * <p>适用于非 API 触发的调用方（如升级全量刷新等），不涉及存储拓扑变更。
     * 涉及存储拓扑变更的场景（级联删除 Volume、快照清理等）应明确调用
     * {@link #markDirty(String, boolean)} 并传入 {@code true}。</p>
     *
     * @param vmInstanceUuid 目标虚拟机 UUID
     */
    public void markDirty(String vmInstanceUuid) {
        markDirty(vmInstanceUuid, false);
    }

    // =====================================================================
    //  triggerFlushForVm — 立即唤醒（单 VM）
    // =====================================================================

    /**
     * 立即尝试认领并刷写指定 VM 的 dirty 行。
     * 若行已被认领或处于退避期，跳过（Poller 安全网会处理）。
     */
    private void triggerFlushForVm(String vmUuid) {
        int claimed = SQL.New("UPDATE VmMetadataDirtyVO " +
                "SET managementNodeUuid = :myId " +
                "WHERE vmInstanceUuid = :vmUuid " +
                "AND managementNodeUuid IS NULL " +
                "AND (nextRetryTime IS NULL OR nextRetryTime <= CURRENT_TIMESTAMP)")
                .param("myId", Platform.getManagementServerId())
                .param("vmUuid", vmUuid)
                .execute();

        if (claimed == 0) {
            return;  // 已被认领 or 退避中 → Poller 处理
        }

        VmMetadataDirtyVO dirty = dbf.findByUuid(vmUuid, VmMetadataDirtyVO.class);
        if (dirty == null) {
            return;
        }

        submitFlushTask(dirty);
    }

    // =====================================================================
    //  Poller — 轮询安全网
    // =====================================================================

    /**
     * 内部 PeriodicTask 实现。
     *
     * <p>Poller 角色定位：markDirty 后的 triggerFlushForVm 已覆盖常规场景。
     * Poller 降级为安全网，负责处理：
     * <ul>
     *   <li>退避中的行（nextRetryTime 到期后才能认领）</li>
     *   <li>MN 宕机后 FK SET_NULL 释放的孤儿行</li>
     *   <li>triggerFlushForVm 认领失败的行（已被其他 MN Poller 认领）</li>
     * </ul>
     */
    private class MetadataDirtyPoller implements PeriodicTask {
        @Override
        public TimeUnit getTimeUnit() {
            return TimeUnit.SECONDS;
        }

        @Override
        public long getInterval() {
            return VmGlobalConfig.VM_METADATA_DIRTY_POLL_INTERVAL.value(Long.class);
        }

        @Override
        public String getName() {
            return "vm-metadata-dirty-poller";
        }

        @Override
        public void run() {
            claimAndFlush();
        }
    }

    private synchronized void startPoller() {
        if (pollerFuture != null) {
            pollerFuture.cancel(false);
        }
        pollerFuture = thdf.submitPeriodicTask(new MetadataDirtyPoller());
        logger.info("[MetadataDirty] poller started");
    }

    private synchronized void stopPoller() {
        if (pollerFuture != null) {
            pollerFuture.cancel(false);
            pollerFuture = null;
            logger.info("[MetadataDirty] poller stopped");
        }
    }

    private void restartPoller() {
        logger.info("[MetadataDirty] restarting poller due to config change");
        startPoller();
    }

    // =====================================================================
    //  claimAndFlush — 认领 + 提交刷写（Poller 和 nodeLeft 共用）
    // =====================================================================

    /**
     * CAS 认领一批 dirty 行并提交刷写。
     */
    private void claimAndFlush() {
        // 功能关闭时跳过，避免 Poller 空转（P2-2.2 修复）
        if (!VmGlobalConfig.VM_METADATA.value(Boolean.class)) {
            return;
        }

        List<VmMetadataDirtyVO> claimed = claimDirtyRows();
        for (VmMetadataDirtyVO dirty : claimed) {
            submitFlushTask(dirty);
        }
    }

    /**
     * CAS 原子认领一批 dirty 行。
     *
     * <p>单条 UPDATE 天然原子，无锁等待，无死锁风险。</p>
     *
     * @return 认领到的 dirty 行列表
     */
    private List<VmMetadataDirtyVO> claimDirtyRows() {
        // Step 1: CAS 原子认领
        int claimed = SQL.New("UPDATE VmMetadataDirtyVO " +
                "SET managementNodeUuid = :myId " +
                "WHERE managementNodeUuid IS NULL " +
                "AND (nextRetryTime IS NULL OR nextRetryTime <= CURRENT_TIMESTAMP) " +
                "ORDER BY lastOpDate ASC " +
                "LIMIT :batchSize")
                .param("myId", Platform.getManagementServerId())
                .param("batchSize", VmGlobalConfig.VM_METADATA_DIRTY_BATCH_SIZE.value(Integer.class))
                .execute();

        if (claimed == 0) {
            return Collections.emptyList();
        }

        // Step 2: 查询刚认领到的行
        return Q.New(VmMetadataDirtyVO.class)
                .eq(VmMetadataDirtyVO_.managementNodeUuid, Platform.getManagementServerId())
                .list();
    }

    // =====================================================================
    //  submitFlushTask — 嵌套 ChainTask 提交（全局限流 + per-VM 串行去重）
    // =====================================================================

    /**
     * 将 dirty 行的刷写任务提交到嵌套 ChainTask 队列。
     *
     * <p>外层全局限流 + 内层 per-VM 串行 + 去重。</p>
     */
    private void submitFlushTask(VmMetadataDirtyVO dirty) {
        final String vmUuid = dirty.getVmInstanceUuid();

        // 外层全局限流
        thdf.chainSubmit(new ChainTask(null) {
            @Override
            public String getSyncSignature() {
                return "update-vm-metadata-global";
            }

            @Override
            public int getSyncLevel() {
                return VmGlobalConfig.VM_METADATA_GLOBAL_MAX_CONCURRENT.value(Integer.class);
            }

            @Override
            public void run(final SyncTaskChain outerChain) {
                // 内层 per-VM 串行 + 去重
                thdf.chainSubmit(new ChainTask(null) {
                    @Override
                    public String getSyncSignature() {
                        return String.format("update-vm-%s-metadata", vmUuid);
                    }

                    @Override
                    public int getSyncLevel() {
                        return 1;
                    }

                    @Override
                    protected int getMaxPendingTasks() {
                        return 1;
                    }

                    @Override
                    protected String getDeduplicateString() {
                        return getSyncSignature();
                    }

                    @Override
                    protected void exceedMaxPendingCallback() {
                        // 已有 running + pending，本次多余。
                        // 不释放认领——该行可能正在被当前 MN 的 running task 处理，
                        // 强行 releaseClaim 会导致其他 MN 并发认领同一 VM（P0-0.2 修复）。
                        // 直接推进外层 chain，running task 完成后自然释放。
                        logger.debug(String.format("[MetadataDirty] vm[uuid:%s] queue already has " +
                                "pending task, skipping (claim retained by running task)", vmUuid));
                        outerChain.next();
                    }

                    @Override
                    public void run(final SyncTaskChain innerChain) {
                        doFlush(dirty, () -> {
                            innerChain.next();
                            outerChain.next();
                        });
                    }

                    @Override
                    public String getName() {
                        return String.format("update-vm-%s-metadata-task", vmUuid);
                    }
                });
            }

            @Override
            public String getName() {
                return String.format("update-vm-%s-metadata-global-task", vmUuid);
            }
        });
    }

    // =====================================================================
    //  doFlush — 核心刷写逻辑
    // =====================================================================

    /**
     * 执行元数据刷写。
     *
     * <p>流程：</p>
     * <ol>
     *   <li>快照 dirtyVersion</li>
     *   <li>前置检查（VM 是否存在）</li>
     *   <li>发送 UpdateVmInstanceMetadataMsg（由 VmInstanceBase 构建 payload 并写入主存储）</li>
     *   <li>成功 → onFlushSuccess（条件删除 dirty 行）</li>
     *   <li>失败 → onFlushFailure（指数退避或放弃）</li>
     * </ol>
     */
    private void doFlush(VmMetadataDirtyVO dirty, Runnable chainNext) {
        String vmUuid = dirty.getVmInstanceUuid();

        // 0. 记录刷写开始时的 dirtyVersion 快照
        long snapshotVersion = dirty.getDirtyVersion();

        // 1. 前置检查：VM 是否存在
        if (!dbf.isExist(vmUuid, VmInstanceVO.class)) {
            // VM 已删除，FK CASCADE 应已删除 dirty 行，兜底删除
            SQL.New(VmMetadataDirtyVO.class)
                    .eq(VmMetadataDirtyVO_.vmInstanceUuid, vmUuid)
                    .delete();
            chainNext.run();
            return;
        }

        // 2. 发送到 VmInstanceBase 处理（由 VmInstanceBase 内部构建 payload 并写入主存储）
        UpdateVmInstanceMetadataMsg msg = new UpdateVmInstanceMetadataMsg();
        msg.setUuid(vmUuid);
        msg.setStorageStructureChange(dirty.isStorageStructureChange());
        msg.setTimeout(TimeUnit.MINUTES.toMillis(2));
        bus.makeLocalServiceId(msg, VmInstanceConstant.SERVICE_ID);

        bus.send(msg, new CloudBusCallBack(null) {
            @Override
            public void run(MessageReply reply) {
                if (reply.isSuccess()) {
                    onFlushSuccess(vmUuid, snapshotVersion);
                } else {
                    onFlushFailure(vmUuid, reply.getError());
                }
                chainNext.run();
            }
        });
    }

    // =====================================================================
    //  onFlushSuccess — 刷写成功处理（dirtyVersion 条件删除）
    // =====================================================================

    /**
     * 刷写成功后的处理。
     *
     * <p>条件删除：仅当 dirtyVersion == snapshotVersion 时删除，
     * 即"刷写期间没有新的 markDirty 到来"。</p>
     *
     * <p>如果 dirtyVersion > snapshotVersion，说明刷写期间有新变更，
     * 释放认领让 triggerFlush / Poller 重新处理。</p>
     */
    private void onFlushSuccess(String vmUuid, long snapshotVersion) {
        int deleted = SQL.New(VmMetadataDirtyVO.class)
                .eq(VmMetadataDirtyVO_.vmInstanceUuid, vmUuid)
                .eq(VmMetadataDirtyVO_.dirtyVersion, snapshotVersion)
                .delete();

        if (deleted == 0) {
            // dirtyVersion > snapshotVersion → 刷写期间有新变更
            // 释放认领，让 triggerFlush / Poller 重新处理
            // 同时重置 retryCount（本次成功说明通路正常）
            // 重置 storageStructureChange=false（本轮 STORAGE 变更已成功刷写，
            // 后续新变更的 OR 升级会在 markDirty 中重新标记）
            SQL.New(VmMetadataDirtyVO.class)
                    .eq(VmMetadataDirtyVO_.vmInstanceUuid, vmUuid)
                    .set(VmMetadataDirtyVO_.managementNodeUuid, null)
                    .set(VmMetadataDirtyVO_.retryCount, 0)
                    .set(VmMetadataDirtyVO_.nextRetryTime, null)
                    .set(VmMetadataDirtyVO_.storageStructureChange, false)
                    .update();

            logger.debug(String.format("[MetadataDirty] vm[uuid:%s] has new changes during flush " +
                    "(snapshotVersion=%d), released for re-processing", vmUuid, snapshotVersion));
        } else {
            logger.debug(String.format("[MetadataDirty] vm[uuid:%s] flush completed and dirty row removed",
                    vmUuid));
        }
    }

    // =====================================================================
    //  onFlushFailure — 刷写失败处理（指数退避 / 放弃）
    // =====================================================================

    /**
     * 刷写失败后的处理。
     *
     * <p>retryCount++ → 达到上限则告警 + 删除行（下次 API 自动重试）；
     * 未达上限则释放认领 + 指数退避。</p>
     */
    private void onFlushFailure(String vmUuid, ErrorCode error) {
        VmMetadataDirtyVO dirty = dbf.findByUuid(vmUuid, VmMetadataDirtyVO.class);
        if (dirty == null) {
            return;  // VM 已销毁，FK CASCADE 已清理
        }

        int newRetryCount = dirty.getRetryCount() + 1;
        int maxRetry = VmGlobalConfig.VM_METADATA_MAX_RETRY.value(Integer.class);

        if (newRetryCount >= maxRetry) {
            // 达到上限 → 告警 + 删除行
            // 下次该 VM 的 @MetadataImpact API 成功时会重新 markDirty，自然重试
            logger.error(String.format("[MetadataDirty] metadata update for vm[uuid:%s] failed " +
                    "after %d retries, giving up. Will auto-retry on next API that modifies this VM. " +
                    "Error: %s", vmUuid, newRetryCount, error));

            SQL.New(VmMetadataDirtyVO.class)
                    .eq(VmMetadataDirtyVO_.vmInstanceUuid, vmUuid)
                    .delete();
            return;
        }

        // 未达上限 → 释放认领 + 指数退避
        long delaySec = BASE_DELAY_SECONDS * (1L << Math.min(newRetryCount, MAX_EXPONENT));
        Timestamp nextRetry = Timestamp.from(Instant.now().plusSeconds(delaySec));

        SQL.New(VmMetadataDirtyVO.class)
                .eq(VmMetadataDirtyVO_.vmInstanceUuid, vmUuid)
                .set(VmMetadataDirtyVO_.managementNodeUuid, null)
                .set(VmMetadataDirtyVO_.retryCount, newRetryCount)
                .set(VmMetadataDirtyVO_.nextRetryTime, nextRetry)
                .update();

        logger.warn(String.format("[MetadataDirty] metadata update for vm[uuid:%s] failed " +
                "(retry %d/%d), next retry at %s. Error: %s",
                vmUuid, newRetryCount, maxRetry, nextRetry, error));
    }

    // =====================================================================
    //  辅助方法
    // =====================================================================

    /**
     * 释放 dirty 行的认领（managementNodeUuid 置 NULL）。
     */
    private void releaseClaim(String vmUuid) {
        SQL.New(VmMetadataDirtyVO.class)
                .eq(VmMetadataDirtyVO_.vmInstanceUuid, vmUuid)
                .set(VmMetadataDirtyVO_.managementNodeUuid, null)
                .update();
    }
}
