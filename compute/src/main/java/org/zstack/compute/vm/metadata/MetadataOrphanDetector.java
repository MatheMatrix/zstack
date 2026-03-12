package org.zstack.compute.vm.metadata;

import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.compute.vm.VmGlobalConfig;
import org.zstack.core.db.Q;
import org.zstack.core.thread.PeriodicTask;
import org.zstack.core.thread.ThreadFacade;
import org.zstack.header.Component;
import org.zstack.header.managementnode.ManagementNodeReadyExtensionPoint;
import org.zstack.header.storage.primary.PrimaryStorageState;
import org.zstack.header.storage.primary.PrimaryStorageVO;
import org.zstack.header.storage.primary.PrimaryStorageVO_;
import org.zstack.header.vm.VmInstanceVO;
import org.zstack.header.vm.VmInstanceVO_;
import org.zstack.header.volume.VolumeType;
import org.zstack.header.volume.VolumeVO;
import org.zstack.header.volume.VolumeVO_;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 孤儿元数据检测器：周期性扫描各 PS 上残留的元数据条目，检测并报告孤儿。
 *
 * <h3>设计背景（Part 01c §1.3, Part 02b C-02B-14）</h3>
 * <p>VM 删除时元数据同步清理可能因 IO 错误失败（3 次重试后放弃），
 * 或 VM 创建失败导致残留。本检测器作为安全网，周期性地扫描每个
 * 支持元数据的 PS，比对存储侧 vmUuid 列表与 DB 中实际存在的 VM，
 * 发现孤儿后仅记录日志告警，<b>不执行自动删除</b>。</p>
 *
 * <h3>孤儿判定条件</h3>
 * <ul>
 *   <li>存储侧有元数据但 DB 中 VM 不存在（已彻底 Expunge）</li>
 *   <li>存储侧有元数据但该 VM 的 Root Volume 不在此 PS 上（迁移残留）</li>
 * </ul>
 *
 * <h3>约束</h3>
 * <ul>
 *   <li>C-02B-14: 仅报告不自动删除，避免与进行中的存储迁移竞态导致误删</li>
 *   <li>仅扫描 {@code PrimaryStorageState.Enabled} 的 PS</li>
 *   <li>依赖 {@code MetadataStorageHandler.scanMetadataVmUuids()} — 当前为骨架实现</li>
 * </ul>
 *
 * <h3>TODO</h3>
 * <p>{@code MetadataStorageHandler} 接口及其实现（SblkMetadataStorageHandler,
 * LocalNfsMetadataStorageHandler）尚未创建。本类在 scanMetadataVmUuids 可用后
 * 需取消 TODO 标记并完成 Agent 调用接入。</p>
 */
public class MetadataOrphanDetector implements Component, ManagementNodeReadyExtensionPoint {
    private static final CLogger logger = Utils.getLogger(MetadataOrphanDetector.class);

    // TODO: 待 MetadataStorageHandler 接口创建后注入
    // @Autowired
    // private List<MetadataStorageHandler> metadataStorageHandlers;

    @Autowired
    private ThreadFacade thdf;

    private Future<Void> taskFuture;

    // =====================================================================
    //  支持元数据的 PS 类型（与 MetadataStorageHandler.isMetadataSupported 对齐）
    // =====================================================================

    /**
     * 当前支持元数据的存储类型。
     * 待 MetadataStorageHandler 接口就绪后，应通过 handler.isMetadataSupported() 动态判断。
     */
    private static final List<String> SUPPORTED_PS_TYPES = List.of(
            "SharedBlock",
            "LocalStorage",
            "NFS"
    );

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
                return VmGlobalConfig.VM_METADATA_ORPHAN_CHECK_INTERVAL.value(Long.class);
            }

            @Override
            public String getName() {
                return "vm-metadata-orphan-detector";
            }

            @Override
            public void run() {
                detectOrphans();
            }
        });
        logger.info("[MetadataOrphanDetector] task started (interval={}s)",
                VmGlobalConfig.VM_METADATA_ORPHAN_CHECK_INTERVAL.value(Long.class));
    }

    private synchronized void stopTask() {
        if (taskFuture != null) {
            taskFuture.cancel(false);
            taskFuture = null;
            logger.info("[MetadataOrphanDetector] task stopped");
        }
    }

    // =====================================================================
    //  核心检测逻辑
    // =====================================================================

    /**
     * 扫描所有支持元数据的已启用 PS，对比存储侧 vmUuid 列表与 DB 状态，
     * 检测并报告孤儿元数据。
     *
     * <p>C-02B-14: 仅报告（WARN 日志），不执行 deleteMetadata。</p>
     */
    private void detectOrphans() {
        if (!VmGlobalConfig.VM_METADATA.value(Boolean.class)) {
            return;
        }

        // 查询所有已启用且支持元数据的 PS
        List<PrimaryStorageVO> psList = Q.New(PrimaryStorageVO.class)
                .eq(PrimaryStorageVO_.state, PrimaryStorageState.Enabled)
                .in(PrimaryStorageVO_.type, SUPPORTED_PS_TYPES)
                .list();

        if (psList.isEmpty()) {
            return;
        }

        int totalOrphans = 0;

        for (PrimaryStorageVO ps : psList) {
            try {
                int orphanCount = scanPsForOrphans(ps);
                totalOrphans += orphanCount;
            } catch (Exception e) {
                logger.warn("[MetadataOrphanDetector] failed to scan PS [{}] (type={}): {}",
                        ps.getUuid(), ps.getType(), e.getMessage());
            }
        }

        if (totalOrphans > 0) {
            logger.warn("[MetadataOrphanDetector] scan complete: {} orphan(s) detected across {} PS(es). " +
                    "Use APICleanupVmInstanceMetadataMsg to clean up manually.", totalOrphans, psList.size());
        }
    }

    /**
     * 扫描单个 PS 上的元数据条目，识别孤儿。
     *
     * <p>TODO: 当前为骨架实现。待 MetadataStorageHandler.scanMetadataVmUuids() 接口
     * 就绪后，替换下方 TODO 块为实际 agent 调用。</p>
     *
     * @param ps 目标 PrimaryStorageVO
     * @return 检测到的孤儿数量
     */
    private int scanPsForOrphans(PrimaryStorageVO ps) {
        String psUuid = ps.getUuid();
        String psType = ps.getType();

        // ===================================================================
        // TODO: 替换为 MetadataStorageHandler.scanMetadataVmUuids(psUuid) 调用
        //
        // 预期调用模式：
        //   MetadataStorageHandler handler = findHandler(psType);
        //   handler.scanMetadataVmUuids(psUuid, new ReturnValueCompletion<List<VmMetadataEntry>>(null) {
        //       @Override
        //       public void success(List<VmMetadataEntry> entries) {
        //           int orphans = checkOrphanEntries(psUuid, entries);
        //           // ...
        //       }
        //       @Override
        //       public void fail(ErrorCode errorCode) {
        //           logger.warn("scan failed for PS [{}]: {}", psUuid, errorCode);
        //       }
        //   });
        //
        // VmMetadataEntry 结构（Part 01c §1.3）:
        //   - vmUuid: String
        //   - hostUuid: String (nullable, 仅 LocalStorage 场景有值)
        // ===================================================================

        logger.debug("[MetadataOrphanDetector] scanning PS [{}] (type={}) — skipped: " +
                "MetadataStorageHandler not yet implemented", psUuid, psType);
        return 0;
    }

    /**
     * 检查从 agent 扫描返回的 vmUuid 列表，识别孤儿。
     *
     * <p>孤儿条件：</p>
     * <ol>
     *   <li>vmUuid 在 VmInstanceVO 中不存在（已彻底 Expunge）</li>
     *   <li>vmUuid 存在但其 Root Volume 的 primaryStorageUuid 不等于当前 PS（迁移残留）</li>
     * </ol>
     *
     * <p>C-02B-14: 仅报告（WARN 日志），不执行自动删除。</p>
     *
     * @param psUuid 当前扫描的 PS UUID
     * @param metadataVmUuids agent 扫描返回的 vmUuid 列表
     * @return 检测到的孤儿数量
     */
    int checkOrphanEntries(String psUuid, List<String> metadataVmUuids) {
        if (metadataVmUuids == null || metadataVmUuids.isEmpty()) {
            return 0;
        }

        // 批量查询 DB 中存在的 VM UUIDs
        List<String> existingVmUuids = Q.New(VmInstanceVO.class)
                .select(VmInstanceVO_.uuid)
                .in(VmInstanceVO_.uuid, metadataVmUuids)
                .listValues();

        int orphanCount = 0;

        // 类型 1: VM 不存在（已 Expunge）
        List<String> expungedVmUuids = metadataVmUuids.stream()
                .filter(uuid -> !existingVmUuids.contains(uuid))
                .collect(Collectors.toList());

        for (String vmUuid : expungedVmUuids) {
            logger.warn("[MetadataOrphanDetector] orphan detected: VM [{}] on PS [{}] — " +
                    "VM no longer exists in DB (expunged)", vmUuid, psUuid);
            orphanCount++;
        }

        // 类型 2: VM 存在但 Root Volume 不在此 PS 上（迁移残留）
        if (!existingVmUuids.isEmpty()) {
            // 查询这些 VM 的 Root Volume 所在 PS
            List<VolumeVO> rootVolumes = Q.New(VolumeVO.class)
                    .eq(VolumeVO_.type, VolumeType.Root)
                    .in(VolumeVO_.vmInstanceUuid, existingVmUuids)
                    .list();

            for (VolumeVO rootVol : rootVolumes) {
                if (rootVol.getPrimaryStorageUuid() != null
                        && !rootVol.getPrimaryStorageUuid().equals(psUuid)) {
                    logger.warn("[MetadataOrphanDetector] orphan detected: VM [{}] on PS [{}] — " +
                            "root volume is on PS [{}] (migration residue)",
                            rootVol.getVmInstanceUuid(), psUuid, rootVol.getPrimaryStorageUuid());
                    orphanCount++;
                }
            }
        }

        return orphanCount;
    }
}
