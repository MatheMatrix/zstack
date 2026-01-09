package org.zstack.compute.vm.metadata;

import org.zstack.header.storage.snapshot.VolumeSnapshotVO;
import org.zstack.header.volume.VolumeVO;
import org.zstack.utils.gson.JSONObjectUtil;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 构建 VM 存储路径指纹 JSON 的共享工具类。
 *
 * <p>同时被 {@link VmMetadataDirtyMarker#savePathFingerprint} 和
 * {@link MetadataPathDriftDetector#buildCurrentPathSnapshot} 使用，
 * 确保写入时记录的指纹与巡检时构建的指纹使用完全相同的逻辑。</p>
 *
 * <h3>JSON 格式</h3>
 * <pre>
 * {
 *   "volumes": [
 *     {"uuid": "vol-aaa", "installPath": "/dev/vg/vol-aaa"},
 *     {"uuid": "vol-bbb", "installPath": "/dev/vg/vol-bbb"}
 *   ],
 *   "snapshots": [
 *     {"uuid": "sp-001", "installPath": "/dev/vg/sp-001"},
 *     {"uuid": "sp-002", "installPath": "/dev/vg/sp-002"}
 *   ]
 * }
 * </pre>
 *
 * <h3>确定性保证</h3>
 * <ul>
 *   <li>列表层面：按 uuid ASC 排序（调用方负责传入已排序列表）</li>
 *   <li>字段层面：Gson 按 Java 字段声明顺序输出（uuid 在前, installPath 在后）</li>
 * </ul>
 */
public class MetadataPathSnapshotBuilder {

    /**
     * 构建路径指纹 JSON。
     *
     * @param volumes   已按 uuid ASC 排序的 VolumeVO 列表
     * @param snapshots 已按 uuid ASC 排序的 VolumeSnapshotVO 列表
     * @return 确定性 JSON 字符串
     */
    public static String buildPathJson(List<VolumeVO> volumes, List<VolumeSnapshotVO> snapshots) {
        List<PathEntry> volumeEntries = volumes.stream()
                .map(v -> new PathEntry(v.getUuid(), v.getInstallPath()))
                .collect(Collectors.toList());

        List<PathEntry> snapshotEntries = snapshots.stream()
                .map(s -> new PathEntry(s.getUuid(), s.getPrimaryStorageInstallPath()))
                .collect(Collectors.toList());

        PathSnapshot snapshot = new PathSnapshot(volumeEntries, snapshotEntries);
        return JSONObjectUtil.toJsonString(snapshot);
    }

    /**
     * 路径快照顶层结构。
     *
     * <p>Gson 按字段声明顺序序列化：volumes → snapshots。</p>
     */
    private static class PathSnapshot {
        final List<PathEntry> volumes;
        final List<PathEntry> snapshots;

        PathSnapshot(List<PathEntry> volumes, List<PathEntry> snapshots) {
            this.volumes = volumes;
            this.snapshots = snapshots;
        }
    }

    /**
     * 单条路径条目。
     *
     * <p>Gson 按字段声明顺序序列化：uuid → installPath。</p>
     */
    private static class PathEntry {
        final String uuid;
        final String installPath;

        PathEntry(String uuid, String installPath) {
            this.uuid = uuid;
            this.installPath = installPath;
        }
    }
}
