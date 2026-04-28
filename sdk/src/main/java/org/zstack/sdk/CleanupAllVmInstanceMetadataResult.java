package org.zstack.sdk;



public class CleanupAllVmInstanceMetadataResult {
    public java.lang.Integer totalCleaned;
    public void setTotalCleaned(java.lang.Integer totalCleaned) {
        this.totalCleaned = totalCleaned;
    }
    public java.lang.Integer getTotalCleaned() {
        return this.totalCleaned;
    }

    public java.lang.Integer totalFailed;
    public void setTotalFailed(java.lang.Integer totalFailed) {
        this.totalFailed = totalFailed;
    }
    public java.lang.Integer getTotalFailed() {
        return this.totalFailed;
    }

    public java.util.List failedPrimaryStorageUuids;
    public void setFailedPrimaryStorageUuids(java.util.List failedPrimaryStorageUuids) {
        this.failedPrimaryStorageUuids = failedPrimaryStorageUuids;
    }
    public java.util.List getFailedPrimaryStorageUuids() {
        return this.failedPrimaryStorageUuids;
    }

}
