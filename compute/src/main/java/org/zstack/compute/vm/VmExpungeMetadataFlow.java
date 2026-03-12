package org.zstack.compute.vm;

import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.zstack.core.db.Q;
import org.zstack.header.core.Completion;
import org.zstack.header.core.workflow.FlowTrigger;
import org.zstack.header.core.workflow.NoRollbackFlow;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.vm.MetadataStorageHandler;
import org.zstack.header.vm.VmInstanceConstant;
import org.zstack.header.vm.VmInstanceSpec;
import org.zstack.header.volume.VolumeVO;
import org.zstack.header.volume.VolumeVO_;
import org.zstack.header.volume.VolumeType;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.Map;

/**
 * VM 彻底删除（Expunge）时清理主存储上的元数据文件。
 *
 * <p>设计要点（Part 02b §8.3）：</p>
 * <ul>
 *   <li>在 ExpungeVm 流程链中执行，位于 Root/Memory/Cache Volume 删除之后</li>
 *   <li>通过根卷所在 PS 定位元数据位置</li>
 *   <li><b>best-effort</b>：删除失败仅 WARN 日志，不阻塞 VM 物理清除</li>
 *   <li>dirty 行由 FK CASCADE 自动清理，本 Flow 不处理</li>
 * </ul>
 *
 * <p>删除时机说明（Δ-5）：元数据在 Expunge（物理删除）而非 Destroy（软删除）
 * 阶段清理。Destroy 时 VM 可通过 Recover 恢复，过早删除会导致恢复后元数据丢失。</p>
 */
@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class VmExpungeMetadataFlow extends NoRollbackFlow {
    private static final CLogger logger = Utils.getLogger(VmExpungeMetadataFlow.class);

    @Autowired
    private MetadataStorageHandler metadataStorageHandler;

    @Override
    public void run(FlowTrigger trigger, Map data) {
        final VmInstanceSpec spec = (VmInstanceSpec) data.get(VmInstanceConstant.Params.VmInstanceSpec.toString());
        final String vmUuid = spec.getVmInventory().getUuid();

        // 功能开关检查：即使功能关闭，也尝试清理已有的元数据文件（best-effort）
        // 不检查 VM_METADATA 开关——Expunge 是不可逆操作，应始终尝试清理残留

        // 通过根卷查找 PS UUID
        String rootVolumeUuid = spec.getVmInventory().getRootVolumeUuid();
        if (rootVolumeUuid == null) {
            // VM 处于中间状态，无根卷，跳过
            logger.debug(String.format("[MetadataExpunge] vm[uuid:%s] has no root volume, skipping metadata cleanup", vmUuid));
            trigger.next();
            return;
        }

        String psUuid = Q.New(VolumeVO.class)
                .eq(VolumeVO_.uuid, rootVolumeUuid)
                .select(VolumeVO_.primaryStorageUuid)
                .findValue();

        if (psUuid == null) {
            // 根卷已被删除或无 PS 信息，跳过
            logger.debug(String.format("[MetadataExpunge] vm[uuid:%s] root volume[uuid:%s] has no primaryStorageUuid, " +
                    "skipping metadata cleanup", vmUuid, rootVolumeUuid));
            trigger.next();
            return;
        }

        logger.info(String.format("[MetadataExpunge] deleting metadata for vm[uuid:%s] on ps[uuid:%s]", vmUuid, psUuid));

        metadataStorageHandler.deleteMetadata(psUuid, vmUuid, new Completion(trigger) {
            @Override
            public void success() {
                logger.info(String.format("[MetadataExpunge] metadata deleted for vm[uuid:%s] on ps[uuid:%s]", vmUuid, psUuid));
                trigger.next();
            }

            @Override
            public void fail(ErrorCode errorCode) {
                // best-effort：失败不阻塞 VM 物理清除
                logger.warn(String.format("[MetadataExpunge] failed to delete metadata for vm[uuid:%s] on ps[uuid:%s], " +
                        "continuing expunge. Error: %s", vmUuid, psUuid, errorCode));
                trigger.next();
            }
        });
    }
}
