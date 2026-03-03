package org.zstack.compute.vm.metadata;

import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.compute.vm.VmGlobalConfig;
import org.zstack.core.cloudbus.CloudBusCallBack;
import org.zstack.core.cloudbus.ResourceDestinationMaker;
import org.zstack.core.gc.GC;
import org.zstack.core.gc.GCCompletion;
import org.zstack.core.gc.GCConstants;
import org.zstack.core.gc.SubmitTimeBasedGarbageCollectorMsg;
import org.zstack.core.gc.TimeBasedGarbageCollector;
import org.zstack.core.thread.ChainTask;
import org.zstack.core.thread.SyncTaskChain;
import org.zstack.core.thread.ThreadFacade;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.message.MessageReply;
import org.zstack.header.vm.UpdateVmInstanceMetadataMsg;
import org.zstack.header.vm.VmInstanceConstant;
import org.zstack.header.vm.VmInstanceVO;
import org.zstack.header.vm.VmMetadataCanonicalEvents;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.concurrent.TimeUnit;

/**
 * 虚拟机元数据更新 GC 任务。
 *
 * <h3>设计要点</h3>
 * <ol>
 *   <li><b>持久化重试计数</b>：{@code retryCount} 标记了 {@link GC}，序列化到
 *       {@code GarbageCollectorVO.context}，MN 重启后孤儿加载时不会丢失。</li>
 *   <li><b>指数退避</b>：每次失败后 {@code NEXT_TIME = BASE_DELAY_SECONDS * 2^retryCount}，
 *       由父类 {@link TimeBasedGarbageCollector#fail} 内部调用 {@code setupTimer()}
 *       使用更新后的值。退避序列：10s → 20s → 40s → 80s → 160s。</li>
 *   <li><b>ChainTask 内建去重</b>：通过 {@code getDeduplicateString()} + {@code getMaxPendingTasks(1)}
 *       在 {@code DispatchQueueImpl.doChainSyncSubmit} 的 {@code synchronized(chainTasks)} 块内
 *       原子地完成检查+入队，避免手动 {@code getChainTaskInfo + chainSubmit} 两步操作的竞态条件。</li>
 *   <li><b>去掉 GC 嵌套创建</b>：{@code triggerNow} 直接发送 {@code UpdateVmInstanceMetadataMsg}，
 *       重试统一由本 GC 实例的 {@code fail → setupTimer} 驱动，
 *       不再由 handler 失败路径创建新 GC 导致滚雪球式膨胀。</li>
 * </ol>
 *
 * <h3>串行化保证（四层）</h3>
 * <pre>
 *   Layer 0 — Owner 归集（方案 C）：triggerNow() 检查 isManagedByUs，非 owner 通过 delegate 转移到 owner MN
 *   Layer 1 — GC 框架：同一 GC 实例的 runTrigger() 通过 lockJob CAS 防止并发
 *   Layer 2 — ChainTask 队列 "update-vm-{vmUuid}-metadata"：同一 VM 最多 1 个正在执行 + 1 个排队
 *   Layer 3 — 主存储级队列 "update-metadata-on-ps-{psUuid}"：同一存储最多 N 个并发写入
 * </pre>
 *
 * <h3>调用方注意事项</h3>
 * <p>外部创建本 GC 实例时，必须使用 {@link #getGCName(String)} 设置 NAME，
 * 并通过 {@code SubmitTimeBasedGarbageCollectorMsg} 路由到 hash 环对应 MN，
 * reply 失败时回退本地 submit（方案 B）：</p>
 * <pre>
 *   UpdateVmInstanceMetadataGC gc = new UpdateVmInstanceMetadataGC();
 *   gc.vmInstanceUuid = vmUuid;
 *   gc.NAME = UpdateVmInstanceMetadataGC.getGCName(vmUuid);
 *
 *   SubmitTimeBasedGarbageCollectorMsg gcmsg = new SubmitTimeBasedGarbageCollectorMsg();
 *   gcmsg.setGc(gc);
 *   gcmsg.setGcInterval(BASE_DELAY_SECONDS);
 *   gcmsg.setUnit(TimeUnit.SECONDS);
 *   bus.makeTargetServiceIdByResourceUuid(gcmsg, GCConstants.SERVICE_ID, vmUuid);
 *   bus.send(gcmsg, new CloudBusCallBack(null) {
 *       public void run(MessageReply reply) {
 *           if (!reply.isSuccess()) gc.submit(BASE_DELAY_SECONDS, TimeUnit.SECONDS);
 *       }
 *   });
 * </pre>
 *
 * <p><b>为什么不用 deduplicateSubmit</b>：deduplicateSubmit 通过 existedAndNotCompleted()
 * 检查 DB 中是否存在同 NAME 且未完成的 GC。但 GC 从 timer 触发到实际执行完毕期间
 * status 始终为 Idle，新请求会被误判为"已有 GC 在处理"，导致元数据更新丢失。
 * 使用 submit() + ChainTask maxPendingTasks=1 可正确控制并发。</p>
 */

public class UpdateVmInstanceMetadataGC extends TimeBasedGarbageCollector {
    private static final CLogger logger = Utils.getLogger(UpdateVmInstanceMetadataGC.class);

    // =====================================================================
    //  持久化字段（标记 @GC，序列化到 GarbageCollectorVO.context）
    // =====================================================================

    /** 目标虚拟机 UUID */
    @GC
    public String vmInstanceUuid;

    /**
     * 已重试次数。
     * <p>标记 {@link GC} 后会被序列化到 {@code GarbageCollectorVO.context} JSON 中，
     * MN 重启后通过孤儿加载 {@code loadFromVO()} 恢复，不会重置为 0。</p>
     */
    @GC
    public int retryCount = 0;

    // =====================================================================
    //  常量
    // =====================================================================

    /** 指数退避基准延迟（秒）。退避公式：baseDelay * 2^retryCount */
    private static final long BASE_DELAY_SECONDS = 10;

    /**
     * 最大重试次数的编译期默认值（仅作为 GlobalConfig 不可用时的兜底）。
     * 运行时实际值从 {@link VmGlobalConfig#VM_METADATA_GC_MAX_RETRY} 读取。
     */
    private static final int DEFAULT_MAX_RETRY = 5;

    /**
     * 指数退避的指数上限，防止左移溢出。
     * <p>2^10 = 1024，最大延迟 = 10 * 1024 = 10240s ≈ 2.8 小时。</p>
     */
    private static final int MAX_EXPONENT = 10;

    // =====================================================================
    //  注入
    // =====================================================================

    @Autowired
    protected ThreadFacade thdf;

    @Autowired
    protected ResourceDestinationMaker destMaker;

    // =====================================================================
    //  静态工具方法
    // =====================================================================

    /**
     * 生成 ChainTask 的 syncSignature / deduplicateString。
     * <p>同一 VM 的所有元数据更新任务共享同一个队列名，确保串行执行。</p>
     *
     * @param vmInstanceUuid 虚拟机 UUID
     * @return 队列签名，如 "update-vm-abc123-metadata"
     */
    public static String getUpdateVmInstanceMetadataSyncSignature(String vmInstanceUuid) {
        return String.format("update-vm-%s-metadata", vmInstanceUuid);
    }

    /**
     * 生成 GC 任务的全局唯一 NAME。
     *
     * <p><b>重要</b>：所有创建本 GC 实例的调用方必须统一使用此方法设置 NAME，
     * 保证 {@link TimeBasedGarbageCollector#deduplicateSubmit} 和
     * {@link org.zstack.core.gc.GarbageCollector#existedAndNotCompleted()}
     * 能基于 NAME 正确去重。</p>
     *
     * <p>错误示范（会导致去重失效）：</p>
     * <pre>
     *   gc.NAME = String.format("gc-update-vm-%s-metadata", vmUuid);  // ❌ 格式不一致
     * </pre>
     *
     * @param vmInstanceUuid 虚拟机 UUID
     * @return GC NAME，如 "update-vm-abc123-metadata-gc"
     */
    public static String getGCName(String vmInstanceUuid) {
        return String.format("update-vm-%s-metadata-gc", vmInstanceUuid);
    }

    // =====================================================================
    //  核心逻辑
    // =====================================================================

    /**
     * GC 触发入口，由父类 {@code GarbageCollector.runTrigger()} 调用。
     *
     * <p>执行流程：</p>
     * <pre>
     *  1. 前置检查
     *     ├─ VM 不存在 → cancel()（GC 标记 Done）
     *     ├─ 不是 hash 环 owner → delegateToOwner()（方案 C）
     *     │   ├─ delegate 成功 → success()（职责已转移）
     *     │   └─ delegate 失败 → onUpdateFail()（退避后重试）
     *     └─ 超过最大重试次数 → success()（GC 标记 Done + 触发告警）
     *
     *  2. 提交 ChainTask 到 "update-vm-{vmUuid}-metadata" 队列
     *     ├─ 队列已满（maxPending=1）→ exceedMaxPendingCallback → success()
     *     └─ 入队成功 → run()
     *
     *  3. ChainTask.run()
     *     ├─ 发送 UpdateVmInstanceMetadataMsg
     *     ├─ 成功 → completion.success() → GC Done
     *     └─ 失败 → onUpdateFail() → retryCount++, 指数退避, completion.fail()
     *                                → 父类 setupTimer() 在新延迟后重新触发
     * </pre>
     *
     * @param completion GC 完成回调，调用 success/fail/cancel 驱动 GC 状态机
     */
    @Override
    protected void triggerNow(GCCompletion completion) {
        // ── 前置检查 1：VM 已删除则取消 GC ──
        // VM 删除后元数据文件由 DeleteVmInstanceMetadataGC 负责清理
        if (!dbf.isExist(vmInstanceUuid, VmInstanceVO.class)) {
            logger.debug(String.format("[MetadataGC] VM[uuid:%s] has been deleted, "
                    + "cancel GC[name:%s, id:%s]", vmInstanceUuid, NAME, getUuid()));
            completion.cancel();
            return;
        }

        // ── 前置检查 2：owner 归集（方案 C）──
        // 如果当前 MN 不是 hash 环上该 VM 的 owner，将 GC 委托给 owner MN 执行。
        // 场景：MN 拓扑变更（加入/离开）后，回退到本地的 GC 可能不再属于当前 MN。
        if (!destMaker.isManagedByUs(vmInstanceUuid)) {
            delegateToOwner(completion);
            return;
        }

        // ── 前置检查 3：超过最大重试次数（运行时从 GlobalConfig 读取） ──
        int maxRetry = VmGlobalConfig.VM_METADATA_GC_MAX_RETRY.value(Integer.class);
        if (retryCount >= maxRetry) {
            logger.warn(String.format("[MetadataGC] VM[uuid:%s] exceeded max retry count %d, "
                            + "giving up GC[name:%s, id:%s]. "
                            + "Please check primary storage status and use manual API to retry.",
                    vmInstanceUuid, maxRetry, NAME, getUuid()));

            // 发布 MetadataStaleEvent 供巡检机制和监控系统消费。
            // MetadataHealthCheckJob 监听此事件，将 VM 加入优先刷新队列，
            // 下次巡检时提交新 GC（retryCount=0）重新尝试。
            evtf.fire(VmMetadataCanonicalEvents.VM_METADATA_STALE_PATH,
                    new VmMetadataCanonicalEvents.MetadataStaleData(vmInstanceUuid));

            // 标记 Done：不再自动重试。巡检机制或运维可通过手动 API 重新触发。
            completion.success();
            return;
        }

        // ── 提交到嵌套 ChainTask 队列（§7 全局并发控制）──
        //
        // 嵌套设计：
        //   外层: syncSignature = "update-vm-metadata-global", syncLevel = N（默认 10）
        //         ⇒ 控制同一 MN 上最多 N 个 VM 同时更新
        //   内层: syncSignature = "update-vm-{vmUuid}-metadata", syncLevel = 1
        //         ⇒ 保证 per-VM 串行 + maxPendingTasks=1 去重
        //
        // 外层槽位在内层任务实际完成后才释放（outerChain.next() 在 bus.send 回调中调用），
        // 确保全局并发计数准确。
        //
        final String queueName = getUpdateVmInstanceMetadataSyncSignature(vmInstanceUuid);

        thdf.chainSubmit(new ChainTask(completion) {
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
                submitPerVmChainTask(completion, queueName, outerChain);
            }

            @Override
            public String getName() {
                return String.format("update-vm-%s-metadata-global-chain-task", vmInstanceUuid);
            }
        });
    }

    // =====================================================================
    //  内层 per-VM ChainTask
    // =====================================================================

    /**
     * 提交内层 per-VM ChainTask，保证同一 VM 的元数据更新串行执行。
     *
     * <p>队列设计要点：</p>
     * <ul>
     *   <li>syncSignature = "update-vm-{vmUuid}-metadata"，每 VM 独立队列</li>
     *   <li>syncLevel = 1（默认），同时只有 1 个任务在执行</li>
     *   <li>maxPendingTasks = 1，最多 1 个排队任务</li>
     *   <li>deduplicateString = syncSignature，同队列内按此 key 去重</li>
     * </ul>
     *
     * <p>效果：队列中最多 1 running + 1 pending，满足 "pendingTask <= 1"</p>
     *
     * <p><b>外层 ChainTask 槽位管理</b>：{@code outerChain.next()} 在内层任务实际完成后
     * （bus.send 回调中或 exceedMaxPendingCallback 中）才调用，确保外层全局并发计数准确。</p>
     *
     * @param completion GC 完成回调
     * @param queueName  per-VM 队列名
     * @param outerChain 外层全局 ChainTask 链，完成后需调用 next() 释放全局槽位
     */
    private void submitPerVmChainTask(final GCCompletion completion, final String queueName,
                                      final SyncTaskChain outerChain) {
        thdf.chainSubmit(new ChainTask(completion) {
            @Override
            public String getSyncSignature() {
                return queueName;
            }

            /**
             * 去重 key：与 syncSignature 相同。
             *
             * <p>当多个 GC 几乎同时 trigger 时，
             * {@code DispatchQueueImpl.ChainTaskQueueWrapper.addTask()} 在
             * {@code synchronized(chainTasks)} 块内检查 {@code subPendingMap}，
             * 发现同一 deduplicateString 的 pending 数量已达 maxPendingTasks 时，
             * 拒绝入队并调用 {@code exceedMaxPendingCallback()}。</p>
             */
            @Override
            protected String getDeduplicateString() {
                return queueName;
            }

            /**
             * 最大排队任务数 = 1。
             * <p>元数据是全量更新，排队的任务执行时会拿到最新数据，丢弃的 GC 不会丢数据。</p>
             */
            @Override
            protected int getMaxPendingTasks() {
                return 1;
            }

            /**
             * 队列已满时的回调。
             * <p>此时队列中已有 1 running + 1 pending，当前 GC 的更新内容会被覆盖，
             * 因此安全地标记 success（职责已被接管）并释放外层全局槽位。</p>
             */
            @Override
            protected void exceedMaxPendingCallback() {
                logger.debug(String.format(
                        "[MetadataGC] VM[uuid:%s] queue[%s] already has pending task, "
                                + "current GC[name:%s, id:%s] is superseded, mark success",
                        vmInstanceUuid, queueName, NAME, getUuid()));
                completion.success();
                outerChain.next();
            }

            /**
             * 实际执行元数据更新。
             *
             * <p>发送 {@code UpdateVmInstanceMetadataMsg} 到本地 VM 服务，
             * 由 {@code VmInstanceBase.handle()} 从 DB 全量构建元数据后写入存储。</p>
             */
            @Override
            public void run(final SyncTaskChain innerChain) {
                UpdateVmInstanceMetadataMsg msg = new UpdateVmInstanceMetadataMsg();
                msg.setUuid(vmInstanceUuid);
                bus.makeLocalServiceId(msg, VmInstanceConstant.SERVICE_ID);
                bus.send(msg, new CloudBusCallBack(completion) {
                    @Override
                    public void run(MessageReply reply) {
                        if (reply.isSuccess()) {
                            retryCount = 0;
                            updateContext();
                            completion.success();
                        } else {
                            onUpdateFail(completion, reply.getError());
                        }

                        // 释放内层 per-VM 队列槽位
                        innerChain.next();
                        // 释放外层全局并发槽位
                        outerChain.next();
                    }
                });
            }

            @Override
            public String getName() {
                return String.format("update-vm-%s-metadata-chain-task", vmInstanceUuid);
            }
        });
    }

    // =====================================================================
    //  Owner 归集（方案 C）
    // =====================================================================

    /**
     * 将本 GC 委托给 hash 环 owner MN 执行。
     *
     * <p>发送 {@code SubmitTimeBasedGarbageCollectorMsg} 到 owner MN：</p>
     * <ul>
     *   <li>成功 → owner MN 上创建了新 GC，本地 GC 标记 Done（职责已转移）</li>
     *   <li>失败 → owner 不可达，走 fail 路径，指数退避后重试。
     *       下次 triggerNow() 时 hash 环可能已修正，当前 MN 成为 owner 则直接执行。</li>
     * </ul>
     *
     * @param completion GC 完成回调
     */
    private void delegateToOwner(GCCompletion completion) {
        String owner = destMaker.makeDestination(vmInstanceUuid);
        logger.debug(String.format("[MetadataGC] vm[uuid:%s] not managed by us, "
                + "delegating GC[name:%s, id:%s] to owner MN[%s]",
                vmInstanceUuid, NAME, getUuid(), owner));

        UpdateVmInstanceMetadataGC delegateGc = new UpdateVmInstanceMetadataGC();
        delegateGc.vmInstanceUuid = vmInstanceUuid;
        delegateGc.NAME = getGCName(vmInstanceUuid);

        SubmitTimeBasedGarbageCollectorMsg gcmsg = new SubmitTimeBasedGarbageCollectorMsg();
        gcmsg.setGc(delegateGc);
        gcmsg.setGcInterval(BASE_DELAY_SECONDS);
        gcmsg.setUnit(TimeUnit.SECONDS);
        bus.makeServiceIdByManagementNodeId(gcmsg, GCConstants.SERVICE_ID, owner);

        bus.send(gcmsg, new CloudBusCallBack(completion) {
            @Override
            public void run(MessageReply reply) {
                if (reply.isSuccess()) {
                    // owner 接管成功 → 本地 GC 使命完成
                    completion.success();
                } else {
                    // owner 不可达 → 走 fail 路径，退避后重试
                    // 下次 triggerNow() 时 hash 环可能已变化
                    logger.warn(String.format("[MetadataGC] delegate to owner MN[%s] failed "
                            + "for vm[uuid:%s], will retry with backoff: %s",
                            owner, vmInstanceUuid, reply.getError()));
                    onUpdateFail(completion, reply.getError());
                }
            }
        });
    }

    // =====================================================================
    //  失败处理
    // =====================================================================

    /**
     * 元数据更新失败时的处理：递增重试计数 + 指数退避 + 持久化 + 触发 GC 重新调度。
     *
     * <p>执行步骤：</p>
     * <ol>
     *   <li>递增 {@code retryCount}</li>
     *   <li>计算指数退避延迟：{@code BASE_DELAY_SECONDS * 2^min(retryCount, MAX_EXPONENT)}
     *       <br>退避序列：10s → 20s → 40s → 80s → 160s → 320s（若 MAX_RETRY 更大时）</li>
     *   <li>更新父类 {@code NEXT_TIME} / {@code NEXT_TIME_UNIT} 字段</li>
     *   <li>调用 {@code updateContext()} 将 retryCount + NEXT_TIME 持久化到 DB</li>
     *   <li>调用 {@code completion.fail(err)} 触发父类失败流程</li>
     * </ol>
     *
     * <p><b>为什么在 completion.fail() 之前更新 NEXT_TIME</b>：</p>
     * <p>父类 {@link TimeBasedGarbageCollector#fail} 的执行顺序是：</p>
     * <pre>
     *   super.fail(err)     → GarbageCollectorVO.status = Idle
     *   setupTimer()        → new Timer().schedule(task, NEXT_TIME_UNIT.toMillis(NEXT_TIME))
     * </pre>
     * <p>{@code setupTimer()} 直接读取 {@code NEXT_TIME}。
     * 如果不提前设置，它会使用上一次的旧间隔（初始固定间隔），无法实现指数退避。</p>
     *
     * <p><b>为什么要 updateContext()</b>：</p>
     * <p>如果 {@code fail()} 之后 MN 崩溃，孤儿 GC 被其他 MN 的 {@code loadOrphanJobs()} 加载时，
     * {@code retryCount} 和 {@code NEXT_TIME} 需要从 {@code GarbageCollectorVO.context} 恢复，
     * 否则会重置为 0 导致无限重试。</p>
     *
     * @param completion GC 完成回调
     * @param err        失败错误码
     */
    private void onUpdateFail(GCCompletion completion, ErrorCode err) {
        retryCount++;

        // 指数退避：baseDelay * 2^retryCount，上限 2^MAX_EXPONENT 防止左移溢出
        int exponent = Math.min(retryCount, MAX_EXPONENT);
        NEXT_TIME = BASE_DELAY_SECONDS * (1L << exponent);
        NEXT_TIME_UNIT = TimeUnit.SECONDS;

        logger.warn(String.format("[MetadataGC] VM[uuid:%s] metadata update failed "
                        + "(retry %d/%d), next attempt in %ds. GC[name:%s, id:%s]. Error: %s",
                vmInstanceUuid, retryCount,
                VmGlobalConfig.VM_METADATA_GC_MAX_RETRY.value(Integer.class),
                NEXT_TIME, NAME, getUuid(), err));

        // 持久化 retryCount + NEXT_TIME 到 GarbageCollectorVO.context，
        // 确保 MN 重启后孤儿加载能恢复正确的重试状态和退避间隔
        updateContext();

        // 触发父类 fail 流程：
        //   GarbageCollector.fail()               → 设置 VO.status = Idle, unlock()
        //   TimeBasedGarbageCollector.fail()       → 调用 setupTimer()
        //   setupTimer()                           → 使用已更新的 NEXT_TIME 创建新的 TimerTask
        //
        // 下次 TimerTask 到期时会调用 runTrigger() → triggerNow()，重新走一遍完整流程
        completion.fail(err);
    }
}
