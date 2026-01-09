package org.zstack.header.storage.primary;

import org.zstack.header.message.NeedReplyMessage;

import java.util.List;

public class CleanupVmInstanceMetadataOnPrimaryStorageMsg extends NeedReplyMessage implements PrimaryStorageMessage {
    private String primaryStorageUuid;
    private List<String> vmUuids;

    @Override
    public String getPrimaryStorageUuid() {
        return primaryStorageUuid;
    }

    public void setPrimaryStorageUuid(String primaryStorageUuid) {
        this.primaryStorageUuid = primaryStorageUuid;
    }

    public List<String> getVmUuids() {
        return vmUuids;
    }

    public void setVmUuids(List<String> vmUuids) {
        this.vmUuids = vmUuids;
    }
}
