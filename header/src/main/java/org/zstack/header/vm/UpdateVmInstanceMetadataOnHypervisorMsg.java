package org.zstack.header.vm;

import org.zstack.header.host.HostMessage;
import org.zstack.header.message.NeedReplyMessage;

/**
 * 在 Hypervisor 上更新虚拟机元数据消息。
 *
 * <p>调用链第 3 步（可选）：发送到 Host Agent 执行实际的存储写入。</p>
 *
 * <h3>使用场景</h3>
 * <ul>
 *   <li>sblk：需要通过 Host Agent 操作 LV（activate → write → deactivate）</li>
 *   <li>local：数据在本地磁盘，需要通过 Host Agent 写入</li>
 *   <li>NFS：通常通过 PS Agent 直接操作，不使用此消息</li>
 * </ul>
 *
 * @see UpdateVmInstanceMetadataMsg
 * @see UpdateVmInstanceMetadataOnPrimaryStorageMsg
 */
public class UpdateVmInstanceMetadataOnHypervisorMsg extends NeedReplyMessage implements HostMessage {

    private String hostUuid;
    private String vmInstanceUuid;

    /**
     * 元数据文件在存储上的路径。
     *
     * <ul>
     *   <li>sblk：LV 设备路径，如 /dev/{vg_uuid}/{vm_uuid}_vmmeta</li>
     *   <li>local：本地文件路径，如 /path/to/vm/vm_metadata.json</li>
     * </ul>
     */
    private String metadataPath;

    /**
     * 元数据 JSON 字符串。
     */
    private String metadata;

    /**
     * 是否涉及存储结构变更（sblk 场景设置 pending_op=2）。
     */
    private boolean storageStructureChange;

    @Override
    public String getHostUuid() {
        return hostUuid;
    }

    public void setHostUuid(String hostUuid) {
        this.hostUuid = hostUuid;
    }

    public String getVmInstanceUuid() {
        return vmInstanceUuid;
    }

    public void setVmInstanceUuid(String vmInstanceUuid) {
        this.vmInstanceUuid = vmInstanceUuid;
    }

    public String getMetadataPath() {
        return metadataPath;
    }

    public void setMetadataPath(String metadataPath) {
        this.metadataPath = metadataPath;
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
