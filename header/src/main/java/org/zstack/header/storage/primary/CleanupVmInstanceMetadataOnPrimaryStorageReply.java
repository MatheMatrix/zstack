package org.zstack.header.storage.primary;

import org.zstack.header.message.MessageReply;

import java.util.ArrayList;
import java.util.List;

public class CleanupVmInstanceMetadataOnPrimaryStorageReply extends MessageReply {
    private int totalCleaned;
    private int totalFailed;
    private List<String> failedVmUuids = new ArrayList<>();

    public int getTotalCleaned() {
        return totalCleaned;
    }

    public void setTotalCleaned(int totalCleaned) {
        this.totalCleaned = totalCleaned;
    }

    public int getTotalFailed() {
        return totalFailed;
    }

    public void setTotalFailed(int totalFailed) {
        this.totalFailed = totalFailed;
    }

    public List<String> getFailedVmUuids() {
        return failedVmUuids;
    }

    public void setFailedVmUuids(List<String> failedVmUuids) {
        this.failedVmUuids = failedVmUuids;
    }
}
