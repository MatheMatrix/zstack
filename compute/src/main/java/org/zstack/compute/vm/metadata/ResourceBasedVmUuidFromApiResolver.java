package org.zstack.compute.vm.metadata;

import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.SQL;
import org.zstack.header.message.APIMessage;
import org.zstack.header.tag.APIAbstractCreateTagMsg;
import org.zstack.header.tag.APIDeleteTagMsg;
import org.zstack.header.tag.SystemTagVO;
import org.zstack.header.vm.VmInstanceVO;
import org.zstack.header.vm.VmUuidFromApiResolver;
import org.zstack.resourceconfig.APIDeleteResourceConfigMsg;
import org.zstack.resourceconfig.APIUpdateResourceConfigMsg;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.Collections;
import java.util.List;

/**
 * 资源关联 VM UUID 解析器：从 SystemTag / ResourceConfig 类 API 消息中解析出关联的 vmInstanceUuid。
 *
 * <p>通过 resourceType + resourceUuid 判断资源所属 VM：</p>
 * <ul>
 *   <li>resourceType=VmInstanceVO → 直接返回 resourceUuid</li>
 *   <li>resourceType=VolumeVO → 查询 VolumeVO.vmInstanceUuid</li>
 *   <li>resourceType=VmNicVO → 查询 VmNicVO.vmInstanceUuid</li>
 *   <li>resourceType=VolumeSnapshotVO → VolumeSnapshotVO.volumeUuid → VolumeVO.vmInstanceUuid</li>
 *   <li>其他类型 → 不影响 VM 元数据，返回空</li>
 * </ul>
 *
 * <h3>注意</h3>
 * <p>APIDeleteTagMsg 需要先查询 Tag 获取 resourceType/resourceUuid，
 * 因此必须在 API 执行前（beforeDeliveryMessage）调用。</p>
 */
public class ResourceBasedVmUuidFromApiResolver implements VmUuidFromApiResolver {
    private static final CLogger logger = Utils.getLogger(ResourceBasedVmUuidFromApiResolver.class);

    @Autowired
    private DatabaseFacade dbf;

    @Override
    public boolean supports(APIMessage msg) {
        return msg instanceof APIAbstractCreateTagMsg
                || msg instanceof APIDeleteTagMsg
                || msg instanceof APIUpdateResourceConfigMsg
                || msg instanceof APIDeleteResourceConfigMsg;
    }

    @Override
    public List<String> resolveVmUuids(APIMessage msg) {
        String resourceType = null;
        String resourceUuid = null;

        if (msg instanceof APIAbstractCreateTagMsg) {
            resourceType = ((APIAbstractCreateTagMsg) msg).getResourceType();
            resourceUuid = ((APIAbstractCreateTagMsg) msg).getResourceUuid();
        } else if (msg instanceof APIDeleteTagMsg) {
            // 查询 Tag 获取 resourceType 和 resourceUuid
            SystemTagVO tag = dbf.findByUuid(((APIDeleteTagMsg) msg).getUuid(), SystemTagVO.class);
            if (tag != null) {
                resourceType = tag.getResourceType();
                resourceUuid = tag.getResourceUuid();
            }
        } else if (msg instanceof APIUpdateResourceConfigMsg) {
            // ResourceConfig API: resourceType 为 ResourceVO（通用基类），需通过 resourceUuid 逐一探测
            return resolveByResourceUuid(((APIUpdateResourceConfigMsg) msg).getResourceUuid());
        } else if (msg instanceof APIDeleteResourceConfigMsg) {
            return resolveByResourceUuid(((APIDeleteResourceConfigMsg) msg).getResourceUuid());
        }

        if (resourceType == null || resourceUuid == null) {
            return Collections.emptyList();
        }

        return resolveByResourceType(resourceType, resourceUuid);
    }

    private List<String> resolveByResourceType(String resourceType, String resourceUuid) {
        // 直接关联 VM
        if ("VmInstanceVO".equals(resourceType)) {
            return Collections.singletonList(resourceUuid);
        }

        // Volume → VM
        if ("VolumeVO".equals(resourceType)) {
            return SQL.New(
                    "SELECT v.vmInstanceUuid FROM VolumeVO v " +
                            "WHERE v.uuid = :uuid AND v.vmInstanceUuid IS NOT NULL",
                    String.class
            ).param("uuid", resourceUuid).list();
        }

        // VmNic → VM
        if ("VmNicVO".equals(resourceType)) {
            return SQL.New(
                    "SELECT n.vmInstanceUuid FROM VmNicVO n " +
                            "WHERE n.uuid = :uuid AND n.vmInstanceUuid IS NOT NULL",
                    String.class
            ).param("uuid", resourceUuid).list();
        }

        // VolumeSnapshot → Volume → VM
        if ("VolumeSnapshotVO".equals(resourceType)) {
            return SQL.New(
                    "SELECT v.vmInstanceUuid FROM VolumeVO v " +
                            "WHERE v.uuid = (SELECT s.volumeUuid FROM VolumeSnapshotVO s WHERE s.uuid = :uuid) " +
                            "AND v.vmInstanceUuid IS NOT NULL",
                    String.class
            ).param("uuid", resourceUuid).list();
        }

        // 其他资源类型不影响 VM 元数据
        logger.trace(String.format("resourceType[%s] does not map to VM metadata, skipping", resourceType));
        return Collections.emptyList();
    }

    /**
     * ResourceConfig API 的 resourceUuid 类型为 ResourceVO（通用基类），无法从消息中获取具体资源类型。
     * 按优先级逐一探测：VmInstanceVO → VolumeVO → VmNicVO → 空（非 VM 关联资源）。
     * 每步查询命中主键索引，开销 < 1ms。
     */
    private List<String> resolveByResourceUuid(String resourceUuid) {
        if (resourceUuid == null) {
            return Collections.emptyList();
        }

        // 1. 直接关联 VM
        VmInstanceVO vm = dbf.findByUuid(resourceUuid, VmInstanceVO.class);
        if (vm != null) {
            return Collections.singletonList(resourceUuid);
        }

        // 2. Volume → VM
        List<String> vmUuids = SQL.New(
                "SELECT v.vmInstanceUuid FROM VolumeVO v " +
                        "WHERE v.uuid = :uuid AND v.vmInstanceUuid IS NOT NULL",
                String.class
        ).param("uuid", resourceUuid).list();
        if (!vmUuids.isEmpty()) {
            return vmUuids;
        }

        // 3. VmNic → VM
        vmUuids = SQL.New(
                "SELECT n.vmInstanceUuid FROM VmNicVO n " +
                        "WHERE n.uuid = :uuid AND n.vmInstanceUuid IS NOT NULL",
                String.class
        ).param("uuid", resourceUuid).list();
        if (!vmUuids.isEmpty()) {
            return vmUuids;
        }

        // 非 VM 关联资源（如 Host、Cluster 等），不触发元数据操作
        logger.trace(String.format("resourceUuid[%s] does not map to any VM, skipping", resourceUuid));
        return Collections.emptyList();
    }
}
