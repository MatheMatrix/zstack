package org.zstack.sdk;

public class CheckPrimaryStorageConsistencyResult {
    public boolean consistent;
    public void setConsistent(boolean consistent) {
        this.consistent = consistent;
    }
    public boolean getConsistent() {
        return this.consistent;
    }

    public ConsistencyCheckStatus reason;
    public void setReason(ConsistencyCheckStatus reason) {
        this.reason = reason;
    }
    public ConsistencyCheckStatus getReason() {
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
