package org.zstack.header.vm;

import java.util.List;
import java.util.Map;

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
        public String vo;

        /**
         * 影响虚拟机 xml 的 SystemTag 列表。
         *
         * <p>每个元素是 SystemTagVO 的 JSON 明文字符串。
         * 在构建元数据时已按白名单过滤，仅包含影响 VM xml 生成的 tag。</p>
         */
        public List<String> systemTags;

        /**
         * 影响虚拟机 xml 的 ResourceConfig 列表。
         *
         * <p>每个元素是 ResourceConfigVO 的 JSON 明文字符串。
         * 在构建元数据时已按白名单过滤，仅包含影响 VM xml 生成的 config。</p>
         */
        public List<String> resourceConfigs;
    }

    /**
     * 元数据 schema 版本，与 ZStack 数据库版本（zsv）一致，如 "5.0.0"。
     *
     * <p>序列化时自动填充当前平台版本。注册时若版本不匹配则拒绝注册。
     * 升级后通过全量更新 GC 将所有 VM 的元数据刷新到新版本。</p>
     */
    public String schemaVersion;

    /**
     * 虚拟机自身的元数据。
     *
     * <p>{@link ResourceMetadata#vo} 为 VmInstanceVO 的 JSON。</p>
     */
    public ResourceMetadata vm;

    /**
     * 云盘元数据列表。
     *
     * <p>包含根盘与数据盘（挂载的 + 已卸载但 lastVmInstanceUuid 指向本 VM 的）。
     * {@link ResourceMetadata#vo} 为 VolumeVO 的 JSON。</p>
     */
    public List<ResourceMetadata> volumes;

    /**
     * 网卡元数据列表。
     *
     * <p>仅记录，注册时不恢复。{@link ResourceMetadata#vo} 为 VmNicVO 的 JSON。</p>
     */
    public List<ResourceMetadata> nics;

    /**
     * 快照数据。
     *
     * <p>Key 为 volumeUuid，Value 为该 volume 下所有 VolumeSnapshotVO 的 JSON 明文列表。</p>
     */
    public Map<String, List<String>> snapshots;

    /**
     * 快照组列表。
     *
     * <p>每个元素是 VolumeSnapshotGroupVO 的 JSON 明文。</p>
     */
    public List<String> snapshotGroups;

    /**
     * 快照组关联引用列表。
     *
     * <p>每个元素是 VolumeSnapshotGroupRefVO 的 JSON 明文。
     * 通过 {@code volumeSnapshotGroupUuid} 字段与 {@link #snapshotGroups} 关联。</p>
     */
    public List<String> snapshotGroupRefs;

    /**
     * 快照引用数据。
     *
     * <p>Key 为 volumeUuid，Value 为该 volume 下所有 VolumeSnapshotReferenceVO 的 JSON 明文列表。
     * 使用 {@code List<String>} 而非单值，因为同一 volume 可能存在多条引用记录。</p>
     */
    public Map<String, List<String>> snapshotReferences;

    /**
     * 快照引用树数据。
     *
     * <p>Key 为 volumeUuid，Value 为该 volume 下所有 VolumeSnapshotReferenceTreeVO 的 JSON 明文列表。</p>
     */
    public Map<String, List<String>> snapshotReferenceTrees;
}
