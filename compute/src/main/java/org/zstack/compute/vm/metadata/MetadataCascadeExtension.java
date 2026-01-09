package org.zstack.compute.vm.metadata;

import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.compute.vm.VmGlobalConfig;
import org.zstack.core.cascade.AbstractAsyncCascadeExtension;
import org.zstack.core.cascade.CascadeAction;
import org.zstack.core.cascade.CascadeConstant;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.header.core.Completion;
import org.zstack.header.volume.VolumeDeletionStruct;
import org.zstack.header.volume.VolumeVO;
import org.zstack.header.vm.VmInstanceVO;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 监听 Volume 级联删除事件，为受影响的 VM 触发元数据更新 GC。
 *
 * <h3>设计背景（§5.4）</h3>
 * <p>{@code @MetadataImpact} 注解仅标注在 {@code APIMessage} 子类上，
 * 通过 {@link VmMetadataUpdateInterceptor} 自动触发 GC。
 * 但系统中存在不经过 API 拦截器的级联删除操作也会修改 VM 存储拓扑，
 * 例如：删除 PrimaryStorage → 级联删除 Volume → VM 失去数据卷。
 * 本扩展在级联清理阶段（{@code DELETION_CLEANUP_CODE}）捕获这些事件，
 * 为受影响的 VM 提交元数据更新 GC。</p>
 *
 * <h3>Cascade 图位置</h3>
 * <pre>
 *   ... → PrimaryStorageVO → VolumeVO → VmInstanceMetadata (本扩展)
 *                                     → VolumeSnapshotVO → ...
 * </pre>
 *
 * <h3>两道防线</h3>
 * <ol>
 *   <li>本扩展 + {@code @MetadataImpact} 拦截器覆盖大部分场景</li>
 *   <li>健康巡检兜底：24h 周期全量比对 DB vs 存储元数据（§11）</li>
 * </ol>
 */
public class MetadataCascadeExtension extends AbstractAsyncCascadeExtension {
    private static final CLogger logger = Utils.getLogger(MetadataCascadeExtension.class);

    private static final String NAME = "VmInstanceMetadata";

    @Autowired
    private VmMetadataUpdateInterceptor interceptor;

    @Autowired
    private DatabaseFacade dbf;

    @Override
    public void asyncCascade(CascadeAction action, Completion completion) {
        if (!action.isActionCode(CascadeConstant.DELETION_CLEANUP_CODE)) {
            completion.success();
            return;
        }

        if (!VmGlobalConfig.VM_METADATA.value(Boolean.class)) {
            completion.success();
            return;
        }

        List<String> vmUuids = extractAffectedVmUuids(action);
        if (vmUuids.isEmpty()) {
            completion.success();
            return;
        }

        for (String vmUuid : vmUuids) {
            // 检查 VM 是否仍然存在（级联删除 VM 时不需要更新元数据）
            if (dbf.isExist(vmUuid, VmInstanceVO.class)) {
                logger.debug(String.format("[MetadataCascade] volume cascade cleanup affected "
                        + "vm[uuid:%s], submitting metadata update GC", vmUuid));
                interceptor.submitUpdateVmInstanceMetadataGC(vmUuid);
            }
        }

        completion.success();
    }

    /**
     * 从 CascadeAction 上下文中提取受影响的 VM UUID 列表。
     *
     * <p>当前支持的 parentIssuer：</p>
     * <ul>
     *   <li>{@code VolumeVO} → 从 {@link VolumeDeletionStruct} 中获取 vmInstanceUuid</li>
     * </ul>
     */
    private List<String> extractAffectedVmUuids(CascadeAction action) {
        if (VolumeVO.class.getSimpleName().equals(action.getParentIssuer())) {
            List<VolumeDeletionStruct> structs = action.getParentIssuerContext();
            if (structs == null || structs.isEmpty()) {
                return Collections.emptyList();
            }

            return structs.stream()
                    .map(s -> s.getInventory().getVmInstanceUuid())
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    @Override
    public List<String> getEdgeNames() {
        return Arrays.asList(VolumeVO.class.getSimpleName());
    }

    @Override
    public String getCascadeResourceName() {
        return NAME;
    }

    @Override
    public CascadeAction createActionForChildResource(CascadeAction action) {
        // 叶子节点，不向下传播级联
        return null;
    }
}
