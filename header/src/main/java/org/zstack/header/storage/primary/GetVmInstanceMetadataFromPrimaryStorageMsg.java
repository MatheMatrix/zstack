package org.zstack.header.storage.primary;

import org.zstack.header.message.NeedReplyMessage;


public class GetVmInstanceMetadataFromPrimaryStorageMsg extends NeedReplyMessage implements PrimaryStorageMessage {
    private String primaryStorageUuid;
    private String vmInstanceUuid;

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
}
