package org.zstack.header.vm;

import org.zstack.header.message.NeedReplyMessage;
import org.zstack.header.storage.primary.PrimaryStorageMessage;

/**
 * 在主存储上更新虚拟机元数据消息。
 *
 * <p>调用链第 2 步：发送到主存储服务，由主存储根据自身类型决定写入方式：
 * <ul>
 *   <li>sblk/local：进一步发送 {@link UpdateVmInstanceMetadataOnHypervisorMsg} 到 Host Agent</li>
 *   <li>NFS：直接通过 PS Agent 写入</li>
 * </ul>
 *
 * @see UpdateVmInstanceMetadataMsg
 * @see UpdateVmInstanceMetadataOnHypervisorMsg
 */
public class UpdateVmInstanceMetadataOnPrimaryStorageMsg extends NeedReplyMessage implements PrimaryStorageMessage {

    private String primaryStorageUuid;
    private String vmInstanceUuid;

    /**
     * 根盘 UUID，用于 PS handler 定位元数据写入路径。
     *
     * <p>LocalStorage 通过根盘 installPath 推导元数据文件路径；
     * NFS 通过根盘关联的 Host 确定转发目标。</p>
     */
    private String rootVolumeUuid;

    /**
     * 元数据 JSON 字符串。
     *
     * <p>由 {@code VmInstanceBase.buildVmInstanceMetadata()} 从 DB 全量构建，
     * 为 {@link VmInstanceMetadataDTO} 的 JSON 序列化结果。</p>
     */
    private String metadata;

    /**
     * 是否涉及存储结构变更（sblk 场景设置 pending_op=2）。
     */
    private boolean storageStructureChange;

    @Override
    public String getPrimaryStorageUuid() {
        return primaryStorageUuid;
    }

    public void setPrimaryStorageUuid(String primaryStorageUuid) {
        this.primaryStorageUuid = primaryStorageUuid;
    }

    public String getVmInstanceUuid() {
        return vmInstanceUuid;
    }

    public void setVmInstanceUuid(String vmInstanceUuid) {
        this.vmInstanceUuid = vmInstanceUuid;
    }

    public String getRootVolumeUuid() {
        return rootVolumeUuid;
    }

    public void setRootVolumeUuid(String rootVolumeUuid) {
        this.rootVolumeUuid = rootVolumeUuid;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public boolean isStorageStructureChange() {
        return storageStructureChange;
    }

    public void setStorageStructureChange(boolean storageStructureChange) {
        this.storageStructureChange = storageStructureChange;
    }
}
