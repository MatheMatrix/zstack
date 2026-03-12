package org.zstack.header.storage.primary;

import org.zstack.header.message.NeedReplyMessage;

public class CleanupVmInstanceMetadataOnPrimaryStorageMsg extends NeedReplyMessage implements PrimaryStorageMessage {
    private String primaryStorageUuid;
    private String vmUuid;

    @Override
    public String getPrimaryStorageUuid() {
        return primaryStorageUuid;
    }

    public void setPrimaryStorageUuid(String primaryStorageUuid) {
        this.primaryStorageUuid = primaryStorageUuid;
    }

    public String getVmUuid() {
        return vmUuid;
    }

    public void setVmUuid(String vmUuid) {
        this.vmUuid = vmUuid;
    }
}
