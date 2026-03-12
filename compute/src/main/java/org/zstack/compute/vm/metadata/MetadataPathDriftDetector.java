package org.zstack.compute.vm.metadata;

import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.compute.vm.VmGlobalConfig;
import org.zstack.core.db.Q;
import org.zstack.core.db.SQL;
import org.zstack.core.db.SimpleQuery;
import org.zstack.core.thread.PeriodicTask;
import org.zstack.core.thread.ThreadFacade;
import org.zstack.header.Component;
import org.zstack.header.managementnode.ManagementNodeReadyExtensionPoint;
import org.zstack.header.storage.snapshot.VolumeSnapshotVO;
import org.zstack.header.storage.snapshot.VolumeSnapshotVO_;
import org.zstack.header.vm.VmMetadataPathFingerprintVO;
import org.zstack.header.volume.VolumeVO;
import org.zstack.header.volume.VolumeVO_;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 路径指纹巡检任务：周期性检测 VM 存储拓扑是否发生漂移。
 *
 * <h3>设计背景（Part 02b §8.2）</h3>
 * <p>当存储拓扑变更绕过了 {@code @MetadataImpact} 拦截器（例如底层存储迁移、
 * 手动数据库修改等），dirty mark 不会被触发，导致元数据与实际拓扑不一致。
 * 本巡检任务作为安全网，定期比对每个 VM 的当前路径快照与上次刷写时记录的
 * 路径指纹，发现漂移则调用 markDirty 触发重新刷写。</p>
 *
 * <h3>巡检策略</h3>
 * <ul>
 *   <li>C-02B-3: 禁止 listAll，必须使用 keyset 分页（vmInstanceUuid > lastUuid）</li>
 *   <li>零存储 I/O：纯 DB 查询比对，不涉及 agent 调用</li>
 *   <li>pathSnapshot JSON 格式与 {@link MetadataPathSnapshotBuilder#buildPathJson} 保持一致</li>
 *   <li>仅在上次成功刷写过的 VM（有指纹记录）上进行巡检，从未刷写过的 VM 自动跳过</li>
 * </ul>
 */
public class MetadataPathDriftDetector implements Component, ManagementNodeReadyExtensionPoint {
    private static final CLogger logger = Utils.getLogger(MetadataPathDriftDetector.class);

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
                return VmGlobalConfig.VM_METADATA_PATH_CHECK_INTERVAL.value(Long.class);
            }

            @Override
            public String getName() {
                return "vm-metadata-path-drift-detector";
            }

            @Override
            public void run() {
                detectPathDrift();
            }
        });
        logger.info("[MetadataPathDrift] task started (interval={}s)",
                VmGlobalConfig.VM_METADATA_PATH_CHECK_INTERVAL.value(Long.class));
    }

    private synchronized void stopTask() {
        if (taskFuture != null) {
            taskFuture.cancel(false);
            taskFuture = null;
            logger.info("[MetadataPathDrift] task stopped");
        }
    }

    // =====================================================================
    //  核心巡检逻辑
    // =====================================================================

    /**
     * 使用 keyset 分页遍历所有指纹记录，比对当前路径快照。
     *
     * <p>C-02B-3: 禁止 listAll，使用 {@code vmInstanceUuid > lastUuid} 分页。
     * 因 PK 为 vmInstanceUuid（非自增 id），keyset 分页天然适用。</p>
     */
    private void detectPathDrift() {
        if (!VmGlobalConfig.VM_METADATA.value(Boolean.class)) {
            return;
        }

        int batchSize = VmGlobalConfig.VM_METADATA_PATH_CHECK_BATCH_SIZE.value(Integer.class);
        String lastUuid = "";
        int driftCount = 0;
        int totalChecked = 0;

        while (true) {
            List<VmMetadataPathFingerprintVO> batch = SQL.New(
                    "SELECT fp FROM VmMetadataPathFingerprintVO fp " +
                    "WHERE fp.vmInstanceUuid > :lastUuid " +
                    "ORDER BY fp.vmInstanceUuid ASC",
                    VmMetadataPathFingerprintVO.class)
                    .param("lastUuid", lastUuid)
                    .limit(batchSize)
                    .list();

            if (batch.isEmpty()) {
                break;
            }

            for (VmMetadataPathFingerprintVO fp : batch) {
                String vmUuid = fp.getVmInstanceUuid();
                String currentSnapshot = buildCurrentPathSnapshot(vmUuid);

                // pathSnapshot 可能为 null（简化实现阶段的历史记录）
                String recordedSnapshot = fp.getPathSnapshot();
                if (recordedSnapshot == null) {
                    // 无历史指纹，跳过（等待下次刷写补充）
                    continue;
                }

                if (!recordedSnapshot.equals(currentSnapshot)) {
                    logger.warn("[MetadataPathDrift] drift detected for VM [{}], " +
                            "recorded: {}, current: {}", vmUuid, recordedSnapshot, currentSnapshot);
                    dirtyMarker.markDirty(vmUuid);
                    driftCount++;
                }
                totalChecked++;
            }

            lastUuid = batch.get(batch.size() - 1).getVmInstanceUuid();
        }

        if (driftCount > 0) {
            logger.info("[MetadataPathDrift] scan complete: checked={}, driftDetected={}",
                    totalChecked, driftCount);
        }
    }

    /**
     * 构建 VM 的当前路径快照 JSON。
     *
     * <p>与 {@link MetadataPathSnapshotBuilder#buildPathJson} 使用完全相同的逻辑，
     * 确保比对结果一致：</p>
     * <ul>
     *   <li>volumes: 按 uuid ASC 排序</li>
     *   <li>snapshots: 按 uuid ASC 排序，仅包含 volumes 关联的快照</li>
     *   <li>JSON 字段声明顺序固定（uuid, installPath），Gson 按声明顺序输出</li>
     * </ul>
     */
    private String buildCurrentPathSnapshot(String vmUuid) {
        List<VolumeVO> volumes = Q.New(VolumeVO.class)
                .eq(VolumeVO_.vmInstanceUuid, vmUuid)
                .orderBy(VolumeVO_.uuid, SimpleQuery.Od.ASC)
                .list();

        List<VolumeSnapshotVO> snapshots;
        if (volumes.isEmpty()) {
            snapshots = new ArrayList<>();
        } else {
            List<String> volumeUuids = volumes.stream()
                    .map(VolumeVO::getUuid)
                    .collect(Collectors.toList());
            snapshots = Q.New(VolumeSnapshotVO.class)
                    .in(VolumeSnapshotVO_.volumeUuid, volumeUuids)
                    .orderBy(VolumeSnapshotVO_.uuid, SimpleQuery.Od.ASC)
                    .list();
        }

        return MetadataPathSnapshotBuilder.buildPathJson(volumes, snapshots);
    }
}
