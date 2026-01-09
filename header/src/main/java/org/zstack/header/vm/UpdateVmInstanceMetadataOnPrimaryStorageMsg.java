package org.zstack.header.vm;

import org.zstack.header.message.NeedReplyMessage;
import org.zstack.header.storage.primary.PrimaryStorageMessage;

public class UpdateVmInstanceMetadataOnPrimaryStorageMsg extends NeedReplyMessage implements PrimaryStorageMessage {
    private String metadata;
    private String rootVolumeUuid;
    private String primaryStorageUuid;

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public String getRootVolumeUuid() {
        return rootVolumeUuid;
    }

    public void setRootVolumeUuid(String rootVolumeUuid) {
        this.rootVolumeUuid = rootVolumeUuid;
    }

    @Override
    public String getPrimaryStorageUuid() {
        return primaryStorageUuid;
    }

    public void setPrimaryStorageUuid(String primaryStorageUuid) {
        this.primaryStorageUuid = primaryStorageUuid;
    }
}
