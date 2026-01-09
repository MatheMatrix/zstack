package org.zstack.compute.vm.metadata;

import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.compute.vm.VmGlobalConfig;
import org.zstack.core.db.SQL;
import org.zstack.core.thread.PeriodicTask;
import org.zstack.core.thread.ThreadFacade;
import org.zstack.header.Component;
import org.zstack.header.managementnode.ManagementNodeReadyExtensionPoint;
import org.zstack.header.vm.VmMetadataPathFingerprintVO;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Stale 恢复任务：为重试耗尽（lastFlushFailed=true）的 VM 重新入队 markDirty。
 *
 * <h3>设计背景（Part 02 §4.8）</h3>
 * <p>当 dirty 行因重试耗尽被删除后，低频 VM（长期无 {@code @MetadataImpact} API）将失去
 * 自愈机会。本任务作为独立低频扫描器，周期性地将这些 VM 重新标脏，给予全新重试机会。</p>
 *
 * <h3>慢速重试闭环</h3>
 * <pre>
 *   lastFlushFailed=true
 *     → StaleRecoveryTask markDirty(retryCount=0)
 *       → Poller 5 次重试
 *         → 若仍失败 → lastFlushFailed=true → 30min 后再来
 *         → 若成功 → 正常完成
 * </pre>
 *
 * <h3>熔断机制（Q27）</h3>
 * <p>当 PS 长期不可达时，staleRecoveryCount 累加。达到上限（默认 10 ≈ 5 小时）后
 * 停止自动恢复，记 WARN 日志提示管理员手动触发。</p>
 *
 * <h3>约束</h3>
 * <ul>
 *   <li>C-SR-06: markDirty 使用 retryCount=0（全新起点），不继承历史退避</li>
 *   <li>C-02B-8: lastFlushFailed 仅在 markDirty 成功时重置为 false</li>
 *   <li>DP-03: 先验证 markDirty 返回值，仅在成功时清除 lastFlushFailed</li>
 * </ul>
 */
public class MetadataStaleRecoveryTask implements Component, ManagementNodeReadyExtensionPoint {
    private static final CLogger logger = Utils.getLogger(MetadataStaleRecoveryTask.class);

    @Autowired
    private VmMetadataDirtyMarker dirtyMarker;

    @Autowired
    private ThreadFacade thdf;

    private Future<Void> taskFuture;

    // =====================================================================
    //  Component 生命周期
    // =====================================================================

    @Override
    public boolean start() {
        return true;
    }

    @Override
    public boolean stop() {
        stopTask();
        return true;
    }

    // =====================================================================
    //  ManagementNodeReadyExtensionPoint
    // =====================================================================

    @Override
    public void managementNodeReady() {
        startTask();
    }

    // =====================================================================
    //  任务管理
    // =====================================================================

    private synchronized void startTask() {
        if (taskFuture != null) {
            taskFuture.cancel(false);
        }
        taskFuture = thdf.submitPeriodicTask(new PeriodicTask() {
            @Override
            public TimeUnit getTimeUnit() {
                return TimeUnit.SECONDS;
            }

            @Override
            public long getInterval() {
                return VmGlobalConfig.VM_METADATA_STALE_RECOVERY_INTERVAL.value(Long.class);
            }

            @Override
            public String getName() {
                return "vm-metadata-stale-recovery";
            }

            @Override
            public void run() {
                recoverStaleVms();
            }
        });
        logger.info("[MetadataStaleRecovery] task started (interval={}s)",
                VmGlobalConfig.VM_METADATA_STALE_RECOVERY_INTERVAL.value(Long.class));
    }

    private synchronized void stopTask() {
        if (taskFuture != null) {
            taskFuture.cancel(false);
            taskFuture = null;
            logger.info("[MetadataStaleRecovery] task stopped");
        }
    }

    // =====================================================================
    //  核心逻辑
    // =====================================================================

    private void recoverStaleVms() {
        if (!VmGlobalConfig.VM_METADATA.value(Boolean.class)) {
            return;
        }

        int batchSize = VmGlobalConfig.VM_METADATA_STALE_RECOVERY_BATCH_SIZE.value(Integer.class);
        int maxCycles = VmGlobalConfig.VM_METADATA_STALE_RECOVERY_MAX_CYCLES.value(Integer.class);

        // 查找所有 lastFlushFailed=true 的指纹记录
        List<VmMetadataPathFingerprintVO> staleVms = SQL.New(
                "SELECT fp FROM VmMetadataPathFingerprintVO fp WHERE fp.lastFlushFailed = 1",
                VmMetadataPathFingerprintVO.class)
                .limit(batchSize)
                .list();

        if (staleVms.isEmpty()) {
            return;
        }

        int requeued = 0;
        int circuitBroken = 0;

        for (VmMetadataPathFingerprintVO fp : staleVms) {
            String vmUuid = fp.getVmInstanceUuid();

            // Q27 熔断检查：staleRecoveryCount 达到上限 → 停止自动恢复
            if (fp.getStaleRecoveryCount() >= maxCycles) {
                // 置 lastFlushFailed=false 停止后续扫描
                SQL.New("UPDATE VmMetadataPathFingerprintVO " +
                        "SET lastFlushFailed = 0 WHERE vmInstanceUuid = :vmUuid")
                        .param("vmUuid", vmUuid)
                        .execute();

                logger.warn("VM [{}] metadata stale recovery exceeded {} cycles, entering permanent-stale. " +
                        "Use APIUpdateVmMetadataMsg to manually trigger.", vmUuid, maxCycles);
                circuitBroken++;
                continue;
            }

            // C-SR-06: markDirty 使用 retryCount=0（全新起点，由 markDirty 内部 INSERT IGNORE 保证）
            // DP-03: 先验证 markDirty 返回值
            boolean markSuccess = dirtyMarker.markDirty(vmUuid);

            if (markSuccess) {
                // markDirty 成功 → 安全清除 stale 标记 + 递增 staleRecoveryCount
                SQL.New("UPDATE VmMetadataPathFingerprintVO " +
                        "SET lastFlushFailed = 0, staleRecoveryCount = staleRecoveryCount + 1 " +
                        "WHERE vmInstanceUuid = :vmUuid")
                        .param("vmUuid", vmUuid)
                        .execute();
                requeued++;
            } else {
                // markDirty 失败 → 保留 lastFlushFailed=true，下轮重试
                logger.warn("[MetadataStaleRecovery] markDirty failed for vm={}, " +
                        "keeping lastFlushFailed=true for next retry cycle", vmUuid);
            }
        }

        logger.info("[MetadataStaleRecovery] processed {} stale VMs: requeued={}, circuitBroken={}",
                staleVms.size(), requeued, circuitBroken);
    }
}
