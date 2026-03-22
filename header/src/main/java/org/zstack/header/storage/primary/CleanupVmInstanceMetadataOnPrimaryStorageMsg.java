package org.zstack.header.storage.primary;

import org.zstack.header.message.NeedReplyMessage;

public class CleanupVmInstanceMetadataOnPrimaryStorageMsg extends NeedReplyMessage implements PrimaryStorageMessage {
    private String primaryStorageUuid;
    private String vmUuid;
    private String rootVolumeUuid;
    private String metadataPath;
    private String hostUuid;

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

    public String getRootVolumeUuid() {
        return rootVolumeUuid;
    }

    public void setRootVolumeUuid(String rootVolumeUuid) {
        this.rootVolumeUuid = rootVolumeUuid;
    }

    public String getMetadataPath() {
        return metadataPath;
    }

    public void setMetadataPath(String metadataPath) {
        this.metadataPath = metadataPath;
    }

    public String getHostUuid() {
        return hostUuid;
    }

    public void setHostUuid(String hostUuid) {
        this.hostUuid = hostUuid;
    }
}
