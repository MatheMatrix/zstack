package org.zstack.sdk.network.zns;

import org.zstack.sdk.network.zns.ZnsSegmentSyncOperationInventory;
import org.zstack.sdk.ErrorCode;

public class GetZnsSegmentSyncStatusResult {
    public java.lang.String state;
    public void setState(java.lang.String state) {
        this.state = state;
    }
    public java.lang.String getState() {
        return this.state;
    }

    public java.lang.Long currentConfigVersion;
    public void setCurrentConfigVersion(java.lang.Long currentConfigVersion) {
        this.currentConfigVersion = currentConfigVersion;
    }
    public java.lang.Long getCurrentConfigVersion() {
        return this.currentConfigVersion;
    }

    public java.lang.Long appliedConfigVersion;
    public void setAppliedConfigVersion(java.lang.Long appliedConfigVersion) {
        this.appliedConfigVersion = appliedConfigVersion;
    }
    public java.lang.Long getAppliedConfigVersion() {
        return this.appliedConfigVersion;
    }

    public ZnsSegmentSyncOperationInventory operation;
    public void setOperation(ZnsSegmentSyncOperationInventory operation) {
        this.operation = operation;
    }
    public ZnsSegmentSyncOperationInventory getOperation() {
        return this.operation;
    }

    public ErrorCode error;
    public void setError(ErrorCode error) {
        this.error = error;
    }
    public ErrorCode getError() {
        return this.error;
    }

}
