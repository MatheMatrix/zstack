package org.zstack.compute.vm.metadata;

import org.zstack.core.db.SQL;
import org.zstack.header.message.APIMessage;
import org.zstack.header.storage.snapshot.group.VolumeSnapshotGroupMessage;
import org.zstack.header.vm.VmUuidFromApiResolver;

import java.util.Collections;
import java.util.List;

/**
 * 快照组关联 VM UUID 解析器：从实现 {@link VolumeSnapshotGroupMessage} 接口的 API 消息中获取 groupUuid，
 * 通过 VolumeSnapshotGroupRefVO 查询关联的所有 volumeUuid，再查询 VolumeVO 得到 vmInstanceUuid。
 *
 * <p>覆盖的 API：</p>
 * <ul>
 *   <li>{@code APIDeleteVolumeSnapshotGroupMsg}（STORAGE, updateOnFailure=true）</li>
 *   <li>{@code APICreateVolumeSnapshotGroupMsg}（STORAGE, updateOnFailure=true）</li>
 * </ul>
 *
 * <h3>解析链</h3>
 * <pre>
 *   groupUuid → VolumeSnapshotGroupRefVO.volumeUuid（多个） → VolumeVO.vmInstanceUuid（去重）
 * </pre>
 *
 * <h3>注意</h3>
 * <p>一个快照组可能关联多个 Volume（根盘 + 数据盘），这些 Volume 可能分属不同 VM（虽然通常同属一个 VM）。
 * 本解析器返回所有关联 VM 的 uuid 列表。</p>
 */
public class SnapshotGroupBasedVmUuidFromApiResolver implements VmUuidFromApiResolver {

    @Override
    public boolean supports(APIMessage msg) {
        return msg instanceof VolumeSnapshotGroupMessage;
    }

    @Override
    public List<String> resolveVmUuids(APIMessage msg) {
        String groupUuid = ((VolumeSnapshotGroupMessage) msg).getGroupUuid();
        if (groupUuid == null) {
            return Collections.emptyList();
        }

        // groupUuid → VolumeSnapshotGroupRefVO → volumeUuid 列表
        List<String> volumeUuids = SQL.New(
                "SELECT DISTINCT ref.volumeUuid FROM VolumeSnapshotGroupRefVO ref " +
                        "WHERE ref.volumeSnapshotGroupUuid = :groupUuid",
                String.class
        ).param("groupUuid", groupUuid).list();

        if (volumeUuids.isEmpty()) {
            return Collections.emptyList();
        }

        // volumeUuid 列表 → VolumeVO.vmInstanceUuid（去重，排除 NULL）
        return SQL.New(
                "SELECT DISTINCT v.vmInstanceUuid FROM VolumeVO v " +
                        "WHERE v.uuid IN (:volumeUuids) AND v.vmInstanceUuid IS NOT NULL",
                String.class
        ).param("volumeUuids", volumeUuids).list();
    }
}
