package org.zstack.compute.vm.metadata;

import org.zstack.core.db.SQL;
import org.zstack.header.message.APIMessage;
import org.zstack.header.storage.snapshot.VolumeSnapshotMessage;
import org.zstack.header.vm.VmUuidFromApiResolver;

import java.util.Collections;
import java.util.List;

/**
 * 快照关联 VM UUID 解析器：从实现 {@link VolumeSnapshotMessage} 接口的 API 消息中获取 snapshotUuid，
 * 查询 VolumeSnapshotVO → VolumeVO 得到关联的 vmInstanceUuid。
 *
 * <p>覆盖的 API：</p>
 * <ul>
 *   <li>{@code APIDeleteVolumeSnapshotMsg}（implements DeleteVolumeSnapshotMessage → VolumeSnapshotMessage）</li>
 *   <li>{@code APIRevertVolumeFromSnapshotMsg}（implements RevertVolumeSnapshotMessage → VolumeSnapshotMessage）</li>
 *   <li>{@code APIFlattenVolumeMsg} 等直接携带 volumeUuid 的不经过本解析器</li>
 * </ul>
 *
 * <h3>解析链</h3>
 * <pre>
 *   snapshotUuid → VolumeSnapshotVO.volumeUuid → VolumeVO.vmInstanceUuid
 * </pre>
 *
 * <h3>解析时机</h3>
 * <p>在 API 执行前（BeforeDeliveryMessageInterceptor）调用。此时快照和关联的 Volume 仍存在于数据库中，
 * 因此无需 pre-capture 机制。对于 delete 场景，快照本身被删除但 Volume 仍在；对于 revert 场景，
 * Volume 关联关系不变。</p>
 */
public class SnapshotBasedVmUuidFromApiResolver implements VmUuidFromApiResolver {

    @Override
    public boolean supports(APIMessage msg) {
        return msg instanceof VolumeSnapshotMessage;
    }

    @Override
    public List<String> resolveVmUuids(APIMessage msg) {
        String snapshotUuid = ((VolumeSnapshotMessage) msg).getSnapshotUuid();
        if (snapshotUuid == null) {
            return Collections.emptyList();
        }

        // snapshotUuid → VolumeSnapshotVO.volumeUuid → VolumeVO.vmInstanceUuid
        return SQL.New(
                "SELECT v.vmInstanceUuid FROM VolumeVO v " +
                        "WHERE v.uuid = (SELECT s.volumeUuid FROM VolumeSnapshotVO s WHERE s.uuid = :snapshotUuid) " +
                        "AND v.vmInstanceUuid IS NOT NULL",
                String.class
        ).param("snapshotUuid", snapshotUuid).list();
    }
}
