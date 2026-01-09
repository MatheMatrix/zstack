package org.zstack.header.vm;

import org.zstack.header.message.NeedReplyMessage;

/**
 * 更新虚拟机元数据消息（MN 内部）。
 *
 * <p>调用链第 1 步：由 API 完成后的拦截器发出，路由到 VM 所在的 MN 节点。
 * 接收方从 DB 构建 {@link VmInstanceMetadataDTO}，编码后发送
 * {@link UpdateVmInstanceMetadataOnPrimaryStorageMsg}。</p>
 *
 * @see UpdateVmInstanceMetadataOnPrimaryStorageMsg
 * @see UpdateVmInstanceMetadataOnHypervisorMsg
 */
public class UpdateVmInstanceMetadataMsg extends NeedReplyMessage implements VmInstanceMessage {

    private String vmInstanceUuid;

    /**
     * 是否涉及存储结构变更。
     *
     * <p>对应 {@link MetadataImpact.Impact#STORAGE} 类型的操作。
     * sblk 场景下会设置 pending_op=2。</p>
     */
    private boolean storageStructureChange;

    @Override
    public String getVmInstanceUuid() {
        return vmInstanceUuid;
    }

    public void setVmInstanceUuid(String vmInstanceUuid) {
        this.vmInstanceUuid = vmInstanceUuid;
    }

    public boolean isStorageStructureChange() {
        return storageStructureChange;
    }

    public void setStorageStructureChange(boolean storageStructureChange) {
        this.storageStructureChange = storageStructureChange;
    }
}
