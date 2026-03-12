package org.zstack.header.vm;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * 虚拟机元数据 DTO。
 *
 * <p>存储在主存储上的元数据文件内容就是该 DTO 的 JSON 字符串经 Base64 编码后的结果。</p>
 *
 * <h3>编码策略</h3>
 * <p>DTO 内部所有字段均为明文 JSON。由存储写入层对整个 DTO 的 JSON 字符串做一次统一
 * Base64 编码后写入存储介质（sblk Slot Payload / local NFS 文件内容）。</p>
 *
 * <h3>Checksum</h3>
 * <p>Checksum 不作为 DTO 字段，由存储层保证：
 * <ul>
 *   <li>sblk: Slot 结构自带 Checksum 字段</li>
 *   <li>local/NFS: tmp + rename 原子写入保证完整性</li>
 * </ul>
 */
public class VmInstanceMetadataDTO {

    /**
     * 资源元数据子结构。
     *
     * <p>对于每种资源（VM、Volume、Nic），记录其 VO 全量 JSON 及关联的 SystemTag/ResourceConfig。</p>
     */
    public static class ResourceMetadata {
        /**
         * 资源 UUID。
         *
         * <p>冗余字段，反序列化时必须校验与 {@link #vo} 内部的 uuid 字段一致。</p>
         */
        @SerializedName("resourceUuid")
        public String resourceUuid;

        /**
         * VO 全量 JSON 明文。
         *
         * <ul>
         *   <li>{@link VmInstanceMetadataDTO#vm} → VmInstanceVO JSON</li>
         *   <li>{@link VmInstanceMetadataDTO#volumes} 元素 → VolumeVO JSON</li>
         *   <li>{@link VmInstanceMetadataDTO#nics} 元素 → VmNicVO JSON</li>
         * </ul>
         *
         * <p>序列化时由 Gson 自动处理嵌套 JSON 的转义；反序列化时需要二次反序列化为具体 VO 类。</p>
         */
        @SerializedName("vo")
        public String vo;

        /**
         * SystemTag 列表的 Base64 编码。
         *
         * <p>构建过程：SystemTagVO 列表 → 逐个 JSON 序列化 → 组成 JSON Array 字符串 → Base64 编码。
         * Base64 编码是为了保护可能包含的密码、密钥等敏感信息。</p>
         */
        @SerializedName("systemTags")
        public String systemTags;

        /**
         * ResourceConfig 列表的 Base64 编码。
         *
         * <p>构建过程与 systemTags 一致。</p>
         */
        @SerializedName("resourceConfigs")
        public String resourceConfigs;
    }

    /**
     * 元数据 schema 版本，与 ZStack 数据库版本（zsv）一致，如 "5.0.0"。
     *
     * <p>序列化时自动填充当前平台版本。注册时若版本不匹配则拒绝注册。
     * 升级后通过全量更新 GC 将所有 VM 的元数据刷新到新版本。</p>
     */
    @SerializedName("schemaVersion")
    public String schemaVersion;

    /**
     * 虚拟机分类。
     *
     * <p>标识本元数据所属 VM 的分类（普通 / 模板 / 模板缓存），
     * 注册恢复时按不同分类执行不同的恢复逻辑。</p>
     */
    @SerializedName("vmCategory")
    public VmMetadataCategory vmCategory;

    /**
     * 虚拟机自身的元数据。
     *
     * <p>{@link ResourceMetadata#vo} 为 VmInstanceVO 的 JSON。</p>
     */
    @SerializedName("vm")
    public ResourceMetadata vm;

    /**
     * 云盘元数据列表。
     *
     * <p>包含根盘与数据盘（挂载的 + 已卸载但 lastVmInstanceUuid 指向本 VM 的）。
     * 不包含共享盘（isShareable=true 的 Volume 被排除）。
     * {@link VolumeResourceMetadata#vo} 为 VolumeVO 的 JSON，
     * 每个 Volume 的快照引用数据内嵌在 {@link VolumeResourceMetadata} 中。</p>
     */
    @SerializedName("volumes")
    public List<VolumeResourceMetadata> volumes;

    /**
     * 网卡元数据列表。
     *
     * <p>仅记录，注册时不恢复。{@link ResourceMetadata#vo} 为 VmNicVO 的 JSON。</p>
     */
    @SerializedName("nics")
    public List<ResourceMetadata> nics;

    /**
     * 快照数据（扁平列表）。
     *
     * <p>所有 Volume 下的 VolumeSnapshotVO JSON 明文的扁平列表，
     * 按 BFS 拓扑序排列（父快照在子快照之前）。</p>
     */
    @SerializedName("snapshots")
    public List<String> snapshots;

    /**
     * 快照组列表。
     *
     * <p>每个元素是 VolumeSnapshotGroupVO 的 JSON 明文。</p>
     */
    @SerializedName("snapshotGroups")
    public List<String> snapshotGroups;

    /**
     * 快照组关联引用列表。
     *
     * <p>每个元素是 VolumeSnapshotGroupRefVO 的 JSON 明文。
     * 通过 {@code volumeSnapshotGroupUuid} 字段与 {@link #snapshotGroups} 关联。</p>
     */
    @SerializedName("snapshotGroupRefs")
    public List<String> snapshotGroupRefs;
}
