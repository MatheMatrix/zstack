package org.zstack.sdnController.header;

import org.zstack.header.message.MessageReply;

public class SyncSdnControllerDataReply extends MessageReply {
    private int toCreateCount;
    private int toDeleteCount;
    private int toUpdateCount;
    private int failedCount;
    private boolean dryRun;

    public int getToCreateCount() {
        return toCreateCount;
    }

    public void setToCreateCount(int toCreateCount) {
        this.toCreateCount = toCreateCount;
    }

    public int getToDeleteCount() {
        return toDeleteCount;
    }

    public void setToDeleteCount(int toDeleteCount) {
        this.toDeleteCount = toDeleteCount;
    }

    public int getToUpdateCount() {
        return toUpdateCount;
    }

    public void setToUpdateCount(int toUpdateCount) {
        this.toUpdateCount = toUpdateCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(int failedCount) {
        this.failedCount = failedCount;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }
}

