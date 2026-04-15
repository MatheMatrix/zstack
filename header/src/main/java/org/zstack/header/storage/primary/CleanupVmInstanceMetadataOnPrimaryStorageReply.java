package org.zstack.header.storage.primary;

import org.zstack.header.message.MessageReply;

public class CleanupVmInstanceMetadataOnPrimaryStorageReply extends MessageReply {
    private int cleanedCount;

    public int getCleanedCount() {
        return cleanedCount;
    }

    public void setCleanedCount(int cleanedCount) {
        this.cleanedCount = cleanedCount;
    }
}
