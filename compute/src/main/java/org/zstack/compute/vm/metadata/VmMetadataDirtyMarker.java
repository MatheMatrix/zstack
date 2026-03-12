package org.zstack.compute.vm.metadata;

import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.compute.vm.VmGlobalConfig;
import org.zstack.core.Platform;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.cloudbus.CloudBusCallBack;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.Q;
import org.zstack.core.db.SQL;
import org.zstack.core.db.SQLBatch;
import org.zstack.core.db.SimpleQuery;
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
import org.zstack.header.vm.VmInstanceState;
import org.zstack.header.vm.VmInstanceVO;
import org.zstack.header.vm.VmInstanceVO_;
import org.zstack.header.vm.VmMetadataDirtyVO;
import org.zstack.header.vm.VmMetadataDirtyVO_;
import org.zstack.header.vm.VmMetadataPathFingerprintVO;
import org.zstack.header.storage.snapshot.VolumeSnapshotVO;
import org.zstack.header.storage.snapshot.VolumeSnapshotVO_;
import org.zstack.header.volume.VolumeVO;
import org.zstack.header.volume.VolumeVO_;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
 * <h3>串行化保证（四层）</h3>
 * <pre>
 *   Layer 1 — DB CAS 认领：UPDATE WHERE managementNodeUuid IS NULL → 同一行只被一个 MN 处理
 *   Layer 2 — AtomicInteger 全局限流：globalFlushInFlight（默认上限 10）
 *   Layer 3 — ChainTask 队列 "update-vm-{vmUuid}-metadata"：syncLevel=1, maxPending=1
 *   Layer 4 — 主存储级队列 "update-metadata-on-ps-{psUuid}"（在 PS handler 内部实现）
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

    // 指数退避参数改为 GlobalConfig（C-RB-04），详见 onFlushFailure()。

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
    private Future<Void> zombieCleanupFuture;

    // =====================================================================
    //  全局并发限流（Δ-1：替代原嵌套 ChainTask 外层）
    // =====================================================================

    /** 当前正在 flight 的 flush 任务数。per-MN JVM 本地计数器。 */
    private final AtomicInteger globalFlushInFlight = new AtomicInteger(0);

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
        stopZombieCleanupTask();
        return true;
    }

    // =====================================================================
    //  ManagementNodeReadyExtensionPoint：MN 就绪后启动 Poller
    // =====================================================================

    @Override
    public void managementNodeReady() {
        recoverStalledMigrationPauses();  // C-01C-8: must run before Poller starts
        startPoller();
        startZombieCleanupTask();

        VmGlobalConfig.VM_METADATA_DIRTY_POLL_INTERVAL.installUpdateExtension((oldValue, newValue) -> {
            restartPoller();
        });

        // §9a: 监听 vm.metadata.enabled 开关切换
        VmGlobalConfig.VM_METADATA.installUpdateExtension((oldValue, newValue) -> {
            boolean wasEnabled = Boolean.parseBoolean(oldValue.toString());
            boolean nowEnabled = Boolean.parseBoolean(newValue.toString());
            if (!wasEnabled && nowEnabled) {
                // false → true：分批全量初始化（§9a.1）
                logger.info("[MetadataDirty] vm.metadata.enabled toggled from false to true, starting batch initialization");
                submitBatchInitialization();
            } else if (wasEnabled && !nowEnabled) {
                // true → false：清理 PathFingerprint（§9a.2 讨论 Δ-10）
                logger.info("[MetadataDirty] vm.metadata.enabled toggled from true to false, cleaning up PathFingerprints");
                cleanupPathFingerprints();
            }
        });

        // §9.1: 升级后全量刷新 — 检查 DB 版本与 lastRefreshVersion 是否一致
        scheduleUpgradeRefreshIfNeeded();
    }

    /**
     * 恢复因存储迁移中断而"永久暂停"的脏标记行。
     *
     * <p>存储迁移期间 Poller 会将相关 dirty 行的 nextRetryTime 设为 2099-12-31 23:59:59
     * 以防止 flush 竞争。如果迁移流程崩溃（MN 宕机），这些行会卡在该时间点永远不被处理。</p>
     *
     * <p>本方法在 MN 重启后、Poller 启动前执行，将所有"远未来"暂停行重置为可处理状态。</p>
     *
     * @see <a href="vm-metadata-01c §1.6">Part 01c §1.6 迁移暂停恢复</a>
     */
    private void recoverStalledMigrationPauses() {
        int recovered = SQL.New(
                "UPDATE VmMetadataDirtyVO " +
                "SET nextRetryTime = NULL, retryCount = 0 " +
                "WHERE nextRetryTime = '2099-12-31 23:59:59'")
                .execute();
        if (recovered > 0) {
            logger.warn(String.format("[MetadataDirty] Recovered %d dirty rows with stalled migration pause (nextRetryTime far in future)", recovered));
        }
    }

    // =====================================================================
    //  ManagementNodeChangeListener：MN 拓扑变化处理
    // =====================================================================

    /** Timestamp of the most recent nodeLeft event, used by §9.1 M3 recent-nodeLeft check. */
    private volatile long lastNodeLeftTimestamp = 0;

    @Override
    public void nodeLeft(ManagementNodeInventory inv) {
        // MN 宕机 → FK SET_NULL 已释放其认领的 dirty 行
        // C-02B-1 §7.2: 延迟 N 秒后再触发 claimAndFlush()，降低 zombie MN 并发写入概率
        long delaySec = VmGlobalConfig.VM_METADATA_NODE_LEFT_DELAY.value(Long.class);
        logger.info(String.format("[MetadataDirty] node[%s] left, scheduling claim and flush after %ds delay",
                inv.getUuid(), delaySec));

        // M3 修复：记录 nodeLeft 时间戳，供 §9.1 升级刷新 recent-nodeLeft 检查使用
        lastNodeLeftTimestamp = System.currentTimeMillis();

        thdf.submit(new org.zstack.core.thread.Task<Void>() {
            @Override
            public String getName() {
                return "metadata-dirty-node-left-claim";
            }

            @Override
            public Void call() {
                try {
                    TimeUnit.SECONDS.sleep(delaySec);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("[MetadataDirty] nodeLeft delay interrupted");
                    return null;
                }
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
     * <p>使用 INSERT IGNORE + UPDATE 两步（C-DM-01: Galera 集群兼容），保证：</p>
     * <ul>
     *   <li>行不存在 → INSERT IGNORE 新建（dirtyVersion=1）</li>
     *   <li>行已存在 → UPDATE dirtyVersion +1（标记"有新变更"）</li>
     *   <li>竞态 inserted==0 && updated==0 → 重新 INSERT IGNORE（Q19 修复）</li>
     *   <li>storageStructureChange 使用 OR 升级策略</li>
     *   <li>不重置 retryCount / managementNodeUuid / nextRetryTime</li>
     * </ul>
     *
     * <p>markDirty 后立即调用 {@link #triggerFlushForVm(String)}，
     * 尝试认领并提交刷写，消除最长 N 秒的 Poller 等待延迟。</p>
     *
     * @param vmInstanceUuid       目标虚拟机 UUID
     * @param storageStructureChange 是否涉及存储结构变更
     * @return true 如果标脏成功（供 MetadataStaleRecoveryTask DP-03 使用）
     */
    public boolean markDirty(String vmInstanceUuid, boolean storageStructureChange) {
        // 前置检查：功能开关
        if (!VmGlobalConfig.VM_METADATA.value(Boolean.class)) {
            return false;
        }

        // 前置检查：仅处理 KVM 虚拟化 + UserVm 类型的 VM
        // 非 KVM（如 Simulator）或非 UserVm（如 ApplianceVm）不产生元数据
        boolean isTargetVm = Q.New(VmInstanceVO.class)
                .eq(VmInstanceVO_.uuid, vmInstanceUuid)
                .eq(VmInstanceVO_.type, VmInstanceConstant.USER_VM_TYPE)
                .eq(VmInstanceVO_.hypervisorType, VmInstanceConstant.KVM_HYPERVISOR_TYPE)
                .isExists();
        if (!isTargetVm) {
            logger.trace(String.format("[MetadataDirty] vm[uuid:%s] is not KVM UserVm, skipping markDirty",
                    vmInstanceUuid));
            return false;
        }

        try {
            // C-DM-01: Galera 集群兼容写法，避免 INSERT ON DUPLICATE KEY 在高并发下死锁
            // Step 1: INSERT IGNORE（新行）
            int inserted = SQL.New("INSERT IGNORE INTO VmMetadataDirtyVO " +
                    "(vmInstanceUuid, dirtyVersion, storageStructureChange) " +
                    "VALUES (:vmUuid, 1, :ssc)")
                    .param("vmUuid", vmInstanceUuid)
                    .param("ssc", storageStructureChange)
                    .execute();

            // Step 2: 仅在行已存在时执行 UPDATE（dirtyVersion +1, storageStructureChange OR 升级）
            if (inserted == 0) {
                int updated = SQL.New("UPDATE VmMetadataDirtyVO " +
                        "SET dirtyVersion = dirtyVersion + 1, " +
                        "    storageStructureChange = storageStructureChange OR :ssc " +
                        "WHERE vmInstanceUuid = :vmUuid")
                        .param("vmUuid", vmInstanceUuid)
                        .param("ssc", storageStructureChange)
                        .execute();

                // Q19 修复：INSERT IGNORE 返回 0（行已存在）但 UPDATE 也返回 0（行被并发删除）
                // 竞态窗口：INSERT IGNORE → onFlushSuccess DELETE → UPDATE（行已不存在）
                // 此时必须重新 INSERT，否则本次 markDirty 对应的 DB 变更将丢失
                if (updated == 0) {
                    SQL.New("INSERT IGNORE INTO VmMetadataDirtyVO " +
                            "(vmInstanceUuid, dirtyVersion, storageStructureChange) " +
                            "VALUES (:vmUuid, 1, :ssc)")
                            .param("vmUuid", vmInstanceUuid)
                            .param("ssc", storageStructureChange)
                            .execute();
                }
            }

            logger.debug(String.format("[MetadataDirty] marked dirty for vm[uuid:%s], storageStructureChange=%s",
                    vmInstanceUuid, storageStructureChange));

            // 立即唤醒：尝试认领并提交刷写，不等待 Poller 轮询
            triggerFlushForVm(vmInstanceUuid);
            return true;
        } catch (Exception e) {
            logger.warn(String.format("[MetadataDirty] markDirty failed for vm[uuid:%s]: %s",
                    vmInstanceUuid, e.getMessage()));
            return false;
        }
    }

    /**
     * 标脏入口（便捷重载，默认 storageStructureChange=false，即 CONFIG 级别）。
     *
     * @param vmInstanceUuid 目标虚拟机 UUID
     * @return true 如果标脏成功
     */
    public boolean markDirty(String vmInstanceUuid) {
        return markDirty(vmInstanceUuid, false);
    }

    // =====================================================================
    //  triggerFlushForVm — 立即唤醒（单 VM）
    // =====================================================================

    /**
     * 立即尝试认领并刷写指定 VM 的 dirty 行。
     * 若行已被认领或处于退避期，跳过（Poller 安全网会处理）。
     *
     * <p>Q20 修复：findStaleClaimOwner 可能返回 null（无 stale claim）。
     * SQL 的 OR 分支使用 :staleId 参数，当 staleId=null 时
     * MySQL 会将 {@code managementNodeUuid = NULL} 解析为 FALSE（SQL 三值逻辑），
     * 不会误匹配任何行。但为避免依赖此隐式行为，显式处理：
     * staleId=null 时仅使用 IS NULL 分支，不包含 stale 接管条件。</p>
     */
    private void triggerFlushForVm(String vmUuid) {
        String myId = Platform.getManagementServerId();
        long staleMinutes = VmGlobalConfig.VM_METADATA_TRIGGER_FLUSH_STALE.value(Long.class);
        String staleId = findStaleClaimOwner(vmUuid, Duration.ofMinutes(staleMinutes));

        String sql;
        if (staleId != null) {
            sql = "UPDATE VmMetadataDirtyVO " +
                    "SET managementNodeUuid = :myId, lastClaimTime = CURRENT_TIMESTAMP " +
                    "WHERE vmInstanceUuid = :vmUuid " +
                    "AND (managementNodeUuid IS NULL " +
                    "     OR (managementNodeUuid = :staleId AND lastClaimTime < CURRENT_TIMESTAMP - INTERVAL " + staleMinutes + " MINUTE)) " +
                    "AND (nextRetryTime IS NULL OR nextRetryTime <= CURRENT_TIMESTAMP)";
        } else {
            sql = "UPDATE VmMetadataDirtyVO " +
                    "SET managementNodeUuid = :myId, lastClaimTime = CURRENT_TIMESTAMP " +
                    "WHERE vmInstanceUuid = :vmUuid " +
                    "AND managementNodeUuid IS NULL " +
                    "AND (nextRetryTime IS NULL OR nextRetryTime <= CURRENT_TIMESTAMP)";
        }

        int claimed = SQL.New(sql)
                .param("myId", myId)
                .param("staleId", staleId)
                .param("vmUuid", vmUuid)
                .execute();

        if (claimed == 0) {
            logger.debug(String.format("[MetadataDirty] triggerFlushForVm skip claim, vmUuid=%s, " +
                    "reason=already-claimed-or-backoff", vmUuid));
            return;
        }

        VmMetadataDirtyVO dirty = dbf.findByUuid(vmUuid, VmMetadataDirtyVO.class);
        // DP-07 说明：dirty == null 是合法场景。CAS UPDATE 成功后、findByUuid 前，
        // 若同 MN 上一个 running flush 的 onFlushSuccess() 恰好执行了条件 DELETE，
        // 则该行已被删除。此时直接 return 即可——数据已经是最新的。
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
     * <p>DP-05 修复：僵尸 claim 清理已提取为独立低频任务 {@link #cleanupZombieClaims()}。</p>
     *
     * @return 认领到的 dirty 行列表
     */
    private List<VmMetadataDirtyVO> claimDirtyRows() {
        // Step 1: CAS 原子认领
        // Q17 修复：ORDER BY lastOpDate ASC + vmInstanceUuid ASC（稳定 tiebreaker）
        // C-CL-02: lastClaimTime = CURRENT_TIMESTAMP
        int claimed = SQL.New("UPDATE VmMetadataDirtyVO " +
                "SET managementNodeUuid = :myId, lastClaimTime = CURRENT_TIMESTAMP " +
                "WHERE managementNodeUuid IS NULL " +
                "AND (nextRetryTime IS NULL OR nextRetryTime <= CURRENT_TIMESTAMP) " +
                "ORDER BY lastOpDate ASC, vmInstanceUuid ASC " +
                "LIMIT :batchSize")
                .param("myId", Platform.getManagementServerId())
                .param("batchSize", VmGlobalConfig.VM_METADATA_DIRTY_BATCH_SIZE.value(Integer.class))
                .execute();

        if (claimed == 0) {
            return Collections.emptyList();
        }

        // Step 2: 查询刚认领到的行（DP-01 修复：增加 lastClaimTime 过滤，
        //         仅返回本轮 CAS 认领的行，避免与 triggerFlushForVm 并发认领的行混入）
        Timestamp thisCycleCutoff = Timestamp.from(Instant.now().minus(Duration.ofSeconds(5)));
        return Q.New(VmMetadataDirtyVO.class)
                .eq(VmMetadataDirtyVO_.managementNodeUuid, Platform.getManagementServerId())
                .gte(VmMetadataDirtyVO_.lastClaimTime, thisCycleCutoff)
                .list();
    }

    // =====================================================================
    //  submitFlushTask — AtomicInteger 全局限流 + per-VM 串行去重（Δ-1 重构）
    // =====================================================================

    /**
     * 将 dirty 行的刷写任务提交到 ChainTask 队列。
     *
     * <p>Δ-1 重构：原嵌套 ChainTask（外层全局限流 + 内层 per-VM 串行）
     * 改为 AtomicInteger 全局限流 + 单层 per-VM ChainTask。原因：</p>
     * <ol>
     *   <li>嵌套 ChainTask 的 outerChain.next() 在 exceedMaxPendingCallback 中直接调用
     *       导致 outer slot 提前释放，全局限流语义被破坏</li>
     *   <li>嵌套结构难以推断 Chain 生命周期</li>
     *   <li>AtomicInteger 语义简单明确：flush 开始 increment、完成 decrement、超限 skip</li>
     * </ol>
     */
    private void submitFlushTask(VmMetadataDirtyVO dirty) {
        final String vmUuid = dirty.getVmInstanceUuid();

        // 全局并发检查
        int maxConcurrent = VmGlobalConfig.VM_METADATA_GLOBAL_MAX_CONCURRENT.value(Integer.class);
        if (globalFlushInFlight.get() >= maxConcurrent) {
            // 全局并发已满，释放 claim，Poller 下轮重试
            releaseClaim(vmUuid);
            return;
        }
        globalFlushInFlight.incrementAndGet();

        // 单层 per-VM 串行 + 去重
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
                // Δ-1 改进：在单层结构中，exceed 时直接 decrement 并释放 claim
                globalFlushInFlight.decrementAndGet();
                releaseClaim(vmUuid);
            }

            @Override
            public void run(final SyncTaskChain chain) {
                doFlush(dirty, () -> {
                    globalFlushInFlight.decrementAndGet();
                    chain.next();
                });
            }

            @Override
            public String getName() {
                return String.format("update-vm-%s-metadata-task", vmUuid);
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
     *   <li>P2 修复：重新从 DB 读取 dirty 行（获取最新 storageStructureChange/dirtyVersion）</li>
     *   <li>前置检查（VM 是否存在、C-FL-08 Destroyed 状态过滤）</li>
     *   <li>发送 UpdateVmInstanceMetadataMsg（由 VmInstanceBase 构建 payload 并写入主存储）</li>
     *   <li>成功 → onFlushSuccess（条件删除 dirty 行）</li>
     *   <li>失败 → onFlushFailure（指数退避或放弃）</li>
     * </ol>
     */
    private void doFlush(VmMetadataDirtyVO dirty, Runnable chainNext) {
        String vmUuid = dirty.getVmInstanceUuid();

        // P2 修复：重新从 DB 读取 dirty 行，获取最新的 storageStructureChange 和 dirtyVersion。
        // 原因：submitFlushTask 传入的 dirty 对象是 CAS 认领时的缓存快照，排队等待期间
        // 可能有新的 markDirty(storageStructureChange=true) 通过 OR 升级了该字段。
        VmMetadataDirtyVO latestDirty = dbf.findByUuid(vmUuid, VmMetadataDirtyVO.class);
        if (latestDirty == null) {
            // VM 已删除（FK CASCADE）或 onFlushSuccess 已删除该行
            chainNext.run();
            return;
        }

        // 0. 记录刷写开始时的 dirtyVersion 快照（使用最新值）
        long snapshotVersion = latestDirty.getDirtyVersion();

        // C-02B-2 §7.6: Fence Check — 防止 zombie MN（GC pause 恢复后）并发写入
        // 验证 dirty 行仍被本 MN 认领。若认领已被 nodeLeft → FK SET_NULL → 其他 MN 接管，
        // 则本 MN 的旧 flush 任务必须立即中止。
        if (!Platform.getManagementServerId().equals(latestDirty.getManagementNodeUuid())) {
            logger.warn(String.format("[MetadataDirty] Lost claim on vm[uuid:%s], " +
                    "expected mnUuid=%s but got %s, abort flush write",
                    vmUuid, Platform.getManagementServerId(), latestDirty.getManagementNodeUuid()));
            chainNext.run();
            return;
        }

        // 1. 前置检查：VM 是否存在
        if (!dbf.isExist(vmUuid, VmInstanceVO.class)) {
            // VM 已删除，FK CASCADE 应已删除 dirty 行，兜底删除
            SQL.New(VmMetadataDirtyVO.class)
                    .eq(VmMetadataDirtyVO_.vmInstanceUuid, vmUuid)
                    .delete();
            chainNext.run();
            return;
        }

        // 1b. C-FL-08：过滤 Destroyed 状态的 VM
        // VM 正在销毁过程中（state=Destroyed），EO 尚未物理删除，FK CASCADE 未触发。
        // 此时刷写元数据无意义——销毁完成后 EO 删除时 dirty 行会被级联清理。
        VmInstanceState vmState = Q.New(VmInstanceVO.class)
                .eq(VmInstanceVO_.uuid, vmUuid)
                .select(VmInstanceVO_.state)
                .findValue();
        if (vmState == VmInstanceState.Destroyed) {
            SQL.New(VmMetadataDirtyVO.class)
                    .eq(VmMetadataDirtyVO_.vmInstanceUuid, vmUuid)
                    .delete();
            chainNext.run();
            return;
        }

        // 2. 发送到 VmInstanceBase 处理（由 VmInstanceBase 内部构建 payload 并写入主存储）
        // C-TM-03：超时 ≥ 5 分钟
        UpdateVmInstanceMetadataMsg msg = new UpdateVmInstanceMetadataMsg();
        msg.setUuid(vmUuid);
        msg.setStorageStructureChange(latestDirty.isStorageStructureChange());
        msg.setTimeout(TimeUnit.MINUTES.toMillis(5));
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
     * <p>Δ-2 修复：使用 SQLBatch 替代 @Transactional，避免 self-invocation 陷阱。</p>
     *
     * <p>条件删除：仅当 dirtyVersion == snapshotVersion 时删除，
     * 即"刷写期间没有新的 markDirty 到来"。</p>
     *
     * <p>如果 dirtyVersion > snapshotVersion，说明刷写期间有新变更，
     * 释放认领让 triggerFlush / Poller 重新处理。</p>
     */
    private void onFlushSuccess(String vmUuid, long snapshotVersion) {
        new SQLBatch() {
            @Override
            protected void scripts() {
                // 条件删除：仅当 dirtyVersion == snapshotVersion 时删除
                int deleted = SQL.New(VmMetadataDirtyVO.class)
                        .eq(VmMetadataDirtyVO_.vmInstanceUuid, vmUuid)
                        .eq(VmMetadataDirtyVO_.dirtyVersion, snapshotVersion)
                        .delete();

                if (deleted == 0) {
                    // dirtyVersion > snapshotVersion → 刷写期间有新变更
                    // 释放认领，让 triggerFlush / Poller 重新处理
                    // 同时重置 retryCount（本次成功说明通路正常）
                    SQL.New(VmMetadataDirtyVO.class)
                            .eq(VmMetadataDirtyVO_.vmInstanceUuid, vmUuid)
                            .set(VmMetadataDirtyVO_.managementNodeUuid, null)
                            .set(VmMetadataDirtyVO_.retryCount, 0)
                            .set(VmMetadataDirtyVO_.nextRetryTime, null)
                            .update();

                    logger.debug(String.format("[MetadataDirty] vm[uuid:%s] has new changes during flush " +
                            "(snapshotVersion=%d), released for re-processing", vmUuid, snapshotVersion));
                } else {
                    logger.debug(String.format("[MetadataDirty] vm[uuid:%s] flush completed and dirty row removed",
                            vmUuid));
                }
            }
        }.execute();

        // Δ-9：记录路径指纹（用于 PathDriftDetector 巡检）
        savePathFingerprint(vmUuid);
    }

    // =====================================================================
    //  onFlushFailure — 刷写失败处理（指数退避 / 放弃）
    // =====================================================================

    /**
     * 刷写失败后的处理。
     *
     * <p>retryCount++ → 达到上限则标记 stale + 删除行（MetadataStaleRecoveryTask 接管）；
     * 未达上限则释放认领 + 指数退避。</p>
     *
     * <p>C-RB-04: 退避参数来自 GlobalConfig，禁止硬编码。</p>
     * <p>C-SR-05: 重试耗尽时在 PathFingerprintVO 标记 lastFlushFailed=true。</p>
     */
    private void onFlushFailure(String vmUuid, ErrorCode error) {
        VmMetadataDirtyVO dirty = dbf.findByUuid(vmUuid, VmMetadataDirtyVO.class);
        if (dirty == null) {
            return;  // VM 已销毁，FK CASCADE 已清理
        }

        int newRetryCount = dirty.getRetryCount() + 1;
        int maxRetry = VmGlobalConfig.VM_METADATA_MAX_RETRY.value(Integer.class);
        int baseDelay = VmGlobalConfig.VM_METADATA_RETRY_BASE_DELAY.value(Integer.class);
        int maxExponent = VmGlobalConfig.VM_METADATA_RETRY_MAX_EXPONENT.value(Integer.class);

        if (newRetryCount >= maxRetry) {
            // 达到上限 → 告警 + 标记 stale（C-SR-05：不再直接删除后静默放弃）
            logger.error(String.format("[MetadataDirty] metadata update for vm[uuid:%s] failed " +
                    "after %d retries, marking as stale. MetadataStaleRecoveryTask will retry " +
                    "independently. Error: %s", vmUuid, newRetryCount, error));

            // C-SR-05: 在 PathFingerprintVO 上标记 lastFlushFailed=true
            SQL.New("UPDATE VmMetadataPathFingerprintVO " +
                    "SET lastFlushFailed = 1 WHERE vmInstanceUuid = :vmUuid")
                    .param("vmUuid", vmUuid)
                    .execute();

            // 删除 dirty 行（释放 Poller 资源），stale 恢复由独立任务接管
            SQL.New(VmMetadataDirtyVO.class)
                    .eq(VmMetadataDirtyVO_.vmInstanceUuid, vmUuid)
                    .delete();
            return;
        }

        // 未达上限 → 释放认领 + 指数退避（C-RB-04: 参数来自 GlobalConfig）
        long delaySec = baseDelay * (1L << Math.min(newRetryCount, maxExponent));
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

    /**
     * 查找指定 VM dirty 行的 stale claim owner。
     *
     * <p>若该 VM 的 dirty 行被某个 MN 认领，且 lastClaimTime 超过 staleThreshold，
     * 则返回该 MN 的 UUID；否则返回 null。</p>
     *
     * @param vmUuid         目标 VM UUID
     * @param staleThreshold 认领超时阈值
     * @return stale claim owner 的 MN UUID，或 null
     */
    private String findStaleClaimOwner(String vmUuid, Duration staleThreshold) {
        Timestamp cutoff = Timestamp.from(Instant.now().minus(staleThreshold));
        return Q.New(VmMetadataDirtyVO.class)
                .eq(VmMetadataDirtyVO_.vmInstanceUuid, vmUuid)
                .notNull(VmMetadataDirtyVO_.managementNodeUuid)
                .lt(VmMetadataDirtyVO_.lastClaimTime, cutoff)
                .select(VmMetadataDirtyVO_.managementNodeUuid)
                .findValue();
    }

    /**
     * 记录 VM 的路径指纹（用于 MetadataPathDriftDetector 巡检）。
     *
     * <p>每次 flush 成功后调用，INSERT or UPDATE VmMetadataPathFingerprintVO。
     * pathSnapshot 为当前 VM 所有 Volume + Snapshot 的 installPath 列表的 JSON。</p>
     *
     * <p>pathSnapshot 构建使用 {@link MetadataPathSnapshotBuilder#buildPathJson}，
     * 与 {@link MetadataPathDriftDetector} 巡检时使用完全相同的逻辑，确保一致性。</p>
     */
    private void savePathFingerprint(String vmUuid) {
        // 构建当前路径快照 JSON
        List<VolumeVO> volumes = Q.New(VolumeVO.class)
                .eq(VolumeVO_.vmInstanceUuid, vmUuid)
                .orderBy(VolumeVO_.uuid, SimpleQuery.Od.ASC)
                .list();

        List<VolumeSnapshotVO> snapshots;
        if (volumes.isEmpty()) {
            snapshots = new java.util.ArrayList<>();
        } else {
            List<String> volumeUuids = volumes.stream()
                    .map(VolumeVO::getUuid)
                    .collect(java.util.stream.Collectors.toList());
            snapshots = Q.New(VolumeSnapshotVO.class)
                    .in(VolumeSnapshotVO_.volumeUuid, volumeUuids)
                    .orderBy(VolumeSnapshotVO_.uuid, SimpleQuery.Od.ASC)
                    .list();
        }

        String pathJson = MetadataPathSnapshotBuilder.buildPathJson(volumes, snapshots);

        VmMetadataPathFingerprintVO fp = dbf.findByUuid(vmUuid, VmMetadataPathFingerprintVO.class);
        if (fp == null) {
            fp = new VmMetadataPathFingerprintVO();
            fp.setVmInstanceUuid(vmUuid);
            fp.setPathSnapshot(pathJson);
            fp.setLastFlushTime(new Timestamp(System.currentTimeMillis()));
            fp.setLastFlushFailed(false);
            fp.setStaleRecoveryCount(0);
            dbf.persist(fp);
        } else {
            fp.setPathSnapshot(pathJson);
            fp.setLastFlushTime(new Timestamp(System.currentTimeMillis()));
            dbf.update(fp);
        }
    }

    /**
     * 独立的僵尸 claim 清理任务（防御性措施，DP-05）。
     *
     * <p>从 claimDirtyRows() 提取为独立低频任务，避免每 5s Poller 周期执行不必要的
     * write-intent 扫描。覆盖的场景：</p>
     * <ul>
     *   <li>MN 进程 hang 住（JVM 死锁 / 长 GC），心跳未失效但 flush 永久阻塞</li>
     *   <li>网络分区导致目标 Agent 无响应，ChainTask 在 timeout 前持续持有 claim</li>
     *   <li>极端：MN 已离线但 ManagementNodeVO 记录因 heartbeat 延迟尚未被清理</li>
     * </ul>
     *
     * <p>C-CL-02: 阈值 15 分钟 > flush 最大超时（5min），安全余量充足。</p>
     */
    private void cleanupZombieClaims() {
        long thresholdMinutes = VmGlobalConfig.VM_METADATA_ZOMBIE_CLAIM_THRESHOLD.value(Long.class);
        int cleaned = SQL.New("UPDATE VmMetadataDirtyVO " +
                "SET managementNodeUuid = NULL, lastClaimTime = NULL " +
                "WHERE managementNodeUuid IS NOT NULL " +
                "AND lastClaimTime < CURRENT_TIMESTAMP - INTERVAL " + thresholdMinutes + " MINUTE")
                .execute();

        if (cleaned > 0) {
            logger.info(String.format("[MetadataDirty] cleanupZombieClaims released %d zombie claim(s) " +
                    "(threshold=%d minutes)", cleaned, thresholdMinutes));
        }
    }

    // =====================================================================
    //  升级全量刷新（§9.2）
    // =====================================================================

    /**
     * 升级后全量刷新：为所有 UserVm 标脏，Poller 自动处理。
     *
     * <p>§9.2: 使用 C-DM-01 兼容的 INSERT IGNORE + UPDATE 两步，keyset 分页。
     * storageStructureChange=1（C-SC-07：升级后无法判断存储拓扑是否变化）。</p>
     *
     * <p>lastRefreshVersion 在全量刷新完成后写入（讨论 Δ-8）：
     * 若刷新过程中 MN 崩溃，重启后 lastRefreshVersion 仍为旧值 → 重新触发 → 幂等安全。</p>
     */
    private void submitFullRefresh(String currentVersion) {
        logger.info(String.format("[MetadataDirty] metadata full refresh: starting for version %s", currentVersion));

        int batchSize = VmGlobalConfig.VM_METADATA_UPGRADE_REFRESH_BATCH_SIZE.value(Integer.class);
        String lastUuid = "";
        int totalProcessed = 0;

        while (true) {
            // Step 1: INSERT IGNORE — 为尚无 dirty 行的 VM 创建新行
            SQL.New(
                "INSERT IGNORE INTO VmMetadataDirtyVO (vmInstanceUuid, dirtyVersion, storageStructureChange) " +
                "SELECT v.uuid, 1, 1 FROM VmInstanceVO v " +
                "WHERE v.type = 'UserVm' AND v.uuid > :lastUuid " +
                "ORDER BY v.uuid ASC LIMIT :batchSize")
                .param("lastUuid", lastUuid)
                .param("batchSize", batchSize)
                .execute();

            // Step 2: UPDATE — 已有 dirty 行的 VM 递增 dirtyVersion + 升级 storageStructureChange
            SQL.New(
                "UPDATE VmMetadataDirtyVO d " +
                "INNER JOIN VmInstanceVO v ON d.vmInstanceUuid = v.uuid " +
                "SET d.dirtyVersion = d.dirtyVersion + 1, " +
                "    d.storageStructureChange = 1 " +
                "WHERE v.type = 'UserVm' AND v.uuid > :lastUuid " +
                "ORDER BY v.uuid ASC LIMIT :batchSize")
                .param("lastUuid", lastUuid)
                .param("batchSize", batchSize)
                .execute();

            // 更新 lastUuid 用于 keyset 分页
            List<String> batch = SQL.New("SELECT v.uuid FROM VmInstanceVO v " +
                "WHERE v.type = 'UserVm' AND v.uuid > :lastUuid " +
                "ORDER BY v.uuid ASC LIMIT :batchSize", String.class)
                .param("lastUuid", lastUuid)
                .param("batchSize", batchSize)
                .list();

            if (batch.isEmpty()) {
                break;
            }

            totalProcessed += batch.size();
            lastUuid = batch.get(batch.size() - 1);
        }

        logger.info(String.format("[MetadataDirty] metadata full refresh: %d VMs processed for version %s",
                totalProcessed, currentVersion));

        // 更新 lastRefreshVersion — 必须在全量刷新完成后写入（讨论 Δ-8）
        VmGlobalConfig.VM_METADATA_LAST_REFRESH_VERSION.updateValue(currentVersion);
    }

    // =====================================================================
    //  功能开关 false→true：分批全量初始化（§9a.1）
    // =====================================================================

    /**
     * 功能开关从 false 切换到 true 时，分批为尚无 dirty 行的 UserVm 创建 dirty 行。
     *
     * <p>与 §9.2 升级全量刷新的区别：</p>
     * <ul>
     *   <li>仅处理尚无 dirty 行的 VM（LEFT JOIN 排除已有行）</li>
     *   <li>storageStructureChange=0（首次初始化不涉及存储拓扑变更）</li>
     *   <li>每批之间有延迟（防止 IO 风暴）</li>
     *   <li>每轮重新检查开关状态（防御快速 toggle）</li>
     * </ul>
     */
    private void submitBatchInitialization() {
        thdf.submit(new org.zstack.core.thread.Task<Void>() {
            @Override
            public String getName() {
                return "metadata-batch-initialization";
            }

            @Override
            public Void call() {
                // 延迟 30s 启动，等待 Poller、ChainTask 线程池初始化完成
                try {
                    TimeUnit.SECONDS.sleep(30);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("[MetadataDirty] batch initialization startup delay interrupted");
                    return null;
                }

                if (!VmGlobalConfig.VM_METADATA.value(Boolean.class)) {
                    // 延迟执行前再次检查，防止快速 toggle 后仍执行初始化
                    logger.info("[MetadataDirty] vm.metadata.enabled toggled back to false before initialization, skip");
                    return null;
                }

                int batchSize = VmGlobalConfig.VM_METADATA_INIT_BATCH_SIZE.value(Integer.class);
                long batchDelaySec = VmGlobalConfig.VM_METADATA_INIT_BATCH_DELAY.value(Long.class);
                String lastUuid = "";
                int totalInitialized = 0;

                while (true) {
                    // 每轮检查开关状态，若已关闭则中止
                    if (!VmGlobalConfig.VM_METADATA.value(Boolean.class)) {
                        logger.info(String.format("[MetadataDirty] vm.metadata.enabled disabled during initialization, " +
                                "abort. initialized=%d", totalInitialized));
                        break;
                    }

                    // Keyset 分页查询尚无 dirty 行的 UserVm，INSERT IGNORE
                    int initialized = SQL.New(
                        "INSERT IGNORE INTO VmMetadataDirtyVO (vmInstanceUuid, dirtyVersion, storageStructureChange) " +
                        "SELECT v.uuid, 1, 0 FROM VmInstanceVO v " +
                        "LEFT JOIN VmMetadataDirtyVO d ON v.uuid = d.vmInstanceUuid " +
                        "WHERE v.type = 'UserVm' AND v.uuid > :lastUuid AND d.vmInstanceUuid IS NULL " +
                        "ORDER BY v.uuid ASC LIMIT :batchSize")
                        .param("lastUuid", lastUuid)
                        .param("batchSize", batchSize)
                        .execute();

                    totalInitialized += initialized;

                    // Q29 修复：lastUuid 基于 VmInstanceVO 全量 UUID 推进，
                    // 而非 INSERT 结果。当本批所有 VM 都已有 dirty 行时 INSERT IGNORE
                    // affected_rows=0，但后续批次可能还有未初始化的 VM。
                    List<String> batchUuids = SQL.New("SELECT v.uuid FROM VmInstanceVO v " +
                        "WHERE v.type = 'UserVm' AND v.uuid > :lastUuid " +
                        "ORDER BY v.uuid ASC", String.class)
                        .param("lastUuid", lastUuid)
                        .limit(batchSize)
                        .list();

                    if (batchUuids.isEmpty()) {
                        break;  // 真正遍历完所有 VM
                    }
                    lastUuid = batchUuids.get(batchUuids.size() - 1);

                    logger.info(String.format("[MetadataDirty] metadata initialization batch completed: " +
                            "%d VMs in this batch, %d total", initialized, totalInitialized));

                    // 批间延迟：等待 Poller 消化已有 dirty 行，避免瞬间堆积
                    if (batchDelaySec > 0) {
                        try {
                            TimeUnit.SECONDS.sleep(batchDelaySec);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            logger.warn("[MetadataDirty] metadata initialization interrupted");
                            break;
                        }
                    }
                }

                logger.info(String.format("[MetadataDirty] metadata initialization complete: %d VMs total",
                        totalInitialized));
                return null;
            }
        });
    }

    // =====================================================================
    //  功能开关 true→false：清理 PathFingerprint（§9a.2 讨论 Δ-10）
    // =====================================================================

    /**
     * 功能关闭时异步批量删除所有 VmMetadataPathFingerprintVO 行。
     *
     * <p>§9a.2 讨论 Δ-10：功能关闭期间存储拓扑可能发生变更，
     * 重新启用时旧指纹与实际拓扑不一致，会导致路径巡检产生大量误报。
     * 清理采用 keyset 分页异步删除（每批 1000 行），不阻塞 GlobalConfig 变更回调。</p>
     */
    private void cleanupPathFingerprints() {
        thdf.submit(new org.zstack.core.thread.Task<Void>() {
            @Override
            public String getName() {
                return "metadata-cleanup-path-fingerprints";
            }

            @Override
            public Void call() {
                String lastUuid = "";
                int totalDeleted = 0;
                int batchSize = 1000;

                while (true) {
                    List<String> batch = SQL.New(
                        "SELECT vmInstanceUuid FROM VmMetadataPathFingerprintVO " +
                        "WHERE vmInstanceUuid > :lastUuid " +
                        "ORDER BY vmInstanceUuid ASC LIMIT :batchSize", String.class)
                        .param("lastUuid", lastUuid)
                        .param("batchSize", batchSize)
                        .list();

                    if (batch.isEmpty()) {
                        break;
                    }

                    int deleted = SQL.New("DELETE FROM VmMetadataPathFingerprintVO " +
                        "WHERE vmInstanceUuid IN (:uuids)")
                        .param("uuids", batch)
                        .execute();
                    totalDeleted += deleted;
                    lastUuid = batch.get(batch.size() - 1);
                }

                if (totalDeleted > 0) {
                    logger.info(String.format("[MetadataDirty] cleaned up %d PathFingerprint rows " +
                            "after metadata feature disabled", totalDeleted));
                }
                return null;
            }
        });
    }

    // =====================================================================
    //  升级后全量刷新调度（§9.1）
    // =====================================================================

    /**
     * 升级后自动检测是否需要全量刷新。
     *
     * <p>§9.1: 比较 {@code dbf.getDbVersion()} 与 {@code VM_METADATA_LAST_REFRESH_VERSION}，
     * 若不一致则在延迟后执行 {@link #submitFullRefresh(String)}。</p>
     *
     * <p>延迟原因：升级后多个 MN 同时启动，仅需一个 MN 执行全量刷新。
     * 通过 {@code VM_METADATA_UPGRADE_REFRESH_DELAY}（默认 600s）延迟 + 执行前 re-check
     * 实现"最终只有一个 MN 执行"的效果（best-effort, 非 leader election）。</p>
     *
     * <p>M3 recent-nodeLeft 防护：延迟到期后若近 15 分钟内发生过 nodeLeft，
     * 说明集群可能不稳定，递归 reschedule 以避免在 MN 重新平衡期间执行全量刷新。</p>
     */
    private void scheduleUpgradeRefreshIfNeeded() {
        if (!VmGlobalConfig.VM_METADATA.value(Boolean.class)) {
            return;
        }

        String currentVersion = dbf.getDbVersion();
        String lastRefreshVersion = VmGlobalConfig.VM_METADATA_LAST_REFRESH_VERSION.value(String.class);

        if (currentVersion.equals(lastRefreshVersion)) {
            logger.debug("[MetadataDirty] DB version matches lastRefreshVersion, no upgrade refresh needed");
            return;
        }

        long delaySec = VmGlobalConfig.VM_METADATA_UPGRADE_REFRESH_DELAY.value(Long.class);
        logger.info(String.format("[MetadataDirty] DB version %s != lastRefreshVersion %s, " +
                "scheduling upgrade refresh after %ds delay", currentVersion, lastRefreshVersion, delaySec));

        thdf.submitTimeoutTask(() -> {
            // Re-check: version may have changed, or feature may be disabled
            if (!VmGlobalConfig.VM_METADATA.value(Boolean.class)) {
                return;
            }
            String recheckVersion = dbf.getDbVersion();
            if (!recheckVersion.equals(currentVersion)) {
                logger.warn("[MetadataDirty] DB version changed during upgrade refresh delay, skip");
                return;
            }
            String recheckLastRefresh = VmGlobalConfig.VM_METADATA_LAST_REFRESH_VERSION.value(String.class);
            if (recheckVersion.equals(recheckLastRefresh)) {
                logger.info("[MetadataDirty] another MN already completed upgrade refresh, skip");
                return;
            }

            // M3 recent-nodeLeft check: if nodeLeft within last 15 min, reschedule
            long recentNodeLeftWindowMs = 15L * 60 * 1000;
            if (System.currentTimeMillis() - lastNodeLeftTimestamp < recentNodeLeftWindowMs) {
                logger.info("[MetadataDirty] recent nodeLeft detected, rescheduling upgrade refresh");
                scheduleUpgradeRefreshIfNeeded();  // re-enter with fresh delay
                return;
            }

            submitFullRefresh(recheckVersion);
        }, TimeUnit.SECONDS, delaySec);
    }

    /**
     * 启动僵尸 claim 清理定时任务（60s 间隔）。
     */
    private synchronized void startZombieCleanupTask() {
        if (zombieCleanupFuture != null) {
            zombieCleanupFuture.cancel(false);
        }
        zombieCleanupFuture = thdf.submitPeriodicTask(new PeriodicTask() {
            @Override
            public TimeUnit getTimeUnit() {
                return TimeUnit.SECONDS;
            }

            @Override
            public long getInterval() {
                return 60;
            }

            @Override
            public String getName() {
                return "vm-metadata-zombie-claim-cleanup";
            }

            @Override
            public void run() {
                cleanupZombieClaims();
            }
        });
        logger.info("[MetadataDirty] zombie claim cleanup task started (interval=60s)");
    }

    /**
     * 停止僵尸 claim 清理定时任务。
     */
    private synchronized void stopZombieCleanupTask() {
        if (zombieCleanupFuture != null) {
            zombieCleanupFuture.cancel(false);
            zombieCleanupFuture = null;
            logger.info("[MetadataDirty] zombie claim cleanup task stopped");
        }
    }
}
