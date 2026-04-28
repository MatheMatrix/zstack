package org.zstack.header.storage.primary;

import org.zstack.header.message.MessageReply;

public class CleanupAllVmMetadataOnPrimaryStorageReply extends MessageReply {
    private int cleanedCount;
    private int failedCount;

    public int getCleanedCount() {
        return cleanedCount;
    }

    public void setCleanedCount(int cleanedCount) {
        this.cleanedCount = cleanedCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(int failedCount) {
        this.failedCount = failedCount;
    }
}
