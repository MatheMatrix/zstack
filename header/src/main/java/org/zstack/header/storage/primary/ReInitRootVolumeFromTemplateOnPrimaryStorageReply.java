package org.zstack.header.storage.primary;

import org.zstack.header.message.MessageReply;

/**
 */
public class ReInitRootVolumeFromTemplateOnPrimaryStorageReply extends MessageReply {
    private String newVolumeInstallPath;
    private long newVolumeSize;

    public String getNewVolumeInstallPath() {
        return newVolumeInstallPath;
    }

    public void setNewVolumeInstallPath(String newVolumeInstallPath) {
        this.newVolumeInstallPath = newVolumeInstallPath;
    }

    public long getNewVolumeSize() {
        return newVolumeSize;
    }

    public void setNewVolumeSize(long newVolumeSize) {
        this.newVolumeSize = newVolumeSize;
    }
}
