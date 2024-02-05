package org.zstack.header.storage.primary;

import org.zstack.header.message.MessageReply;

public class DeleteVolumeSnapshotSelfOnPrimaryStorageReply extends MessageReply {
    private String newInstallPath;
    private long size;

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getNewInstallPath() {
        return newInstallPath;
    }

    public void setNewInstallPath(String newInstallPath) {
        this.newInstallPath = newInstallPath;
    }
}
