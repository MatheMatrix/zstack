package org.zstack.header.vm;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * 云盘资源元数据，扩展 {@link VmInstanceMetadataDTO.ResourceMetadata} 以包含
 * 快照引用（VolumeSnapshotReferenceVO）和快照引用树（VolumeSnapshotReferenceTreeVO）数据。
 *
 * <p>每个 Volume 的快照引用数据直接关联到对应的 VolumeResourceMetadata 中，
 * 而非放在 DTO 顶层的 Map 结构里，便于按卷维度整体操作。</p>
 */
public class VolumeResourceMetadata extends VmInstanceMetadataDTO.ResourceMetadata {
    /**
     * 该 Volume 关联的快照引用列表。
     *
     * <p>每个元素是 VolumeSnapshotReferenceVO 的 JSON 明文。
     * 通过 {@code referenceVolumeUuid} 查询关联到本 Volume。</p>
     */
    @SerializedName("snapshotReferences")
    public List<String> snapshotReferences;

    /**
     * 该 Volume 关联的快照引用树列表。
     *
     * <p>每个元素是 VolumeSnapshotReferenceTreeVO 的 JSON 明文。</p>
     */
    @SerializedName("snapshotReferenceTrees")
    public List<String> snapshotReferenceTrees;
}
