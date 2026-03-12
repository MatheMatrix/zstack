package org.zstack.header.storage.primary;

import org.zstack.header.message.NeedReplyMessage;

public class ReadVmInstanceMetadataOnPrimaryStorageMsg extends NeedReplyMessage implements PrimaryStorageMessage {
    private String primaryStorageUuid;
    private String hostUuid;
    private String metadataPath;

    @Override
    public String getPrimaryStorageUuid() {
        return primaryStorageUuid;
    }

    public void setPrimaryStorageUuid(String primaryStorageUuid) {
        this.primaryStorageUuid = primaryStorageUuid;
    }

    public String getHostUuid() {
        return hostUuid;
    }

    public void setHostUuid(String hostUuid) {
        this.hostUuid = hostUuid;
    }

    public String getMetadataPath() {
        return metadataPath;
    }

    public void setMetadataPath(String metadataPath) {
        this.metadataPath = metadataPath;
    }
}
