package org.zstack.sdk;

import org.zstack.sdk.ConsistencyCheckReason;

public class CheckPrimaryStorageConsistencyResult {
    public boolean consistent;
    public void setConsistent(boolean consistent) {
        this.consistent = consistent;
    }
    public boolean getConsistent() {
        return this.consistent;
    }

    public ConsistencyCheckReason reason;
    public void setReason(ConsistencyCheckReason reason) {
        this.reason = reason;
    }
    public ConsistencyCheckReason getReason() {
        return this.reason;
    }

    public java.lang.String candidateVgUuid;
    public void setCandidateVgUuid(java.lang.String candidateVgUuid) {
        this.candidateVgUuid = candidateVgUuid;
    }
    public java.lang.String getCandidateVgUuid() {
        return this.candidateVgUuid;
    }

}
