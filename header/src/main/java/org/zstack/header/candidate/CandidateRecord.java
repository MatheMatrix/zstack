package org.zstack.header.candidate;

public class CandidateRecord<T> {
    private String candidateType;
    private String candidateUuid;
    private String decision;
    private T inventory;
    private CandidateRejectReason reason;
    private CandidateRef parent;

    public String getCandidateType() {
        return candidateType;
    }

    public void setCandidateType(String candidateType) {
        this.candidateType = candidateType;
    }

    public String getCandidateUuid() {
        return candidateUuid;
    }

    public void setCandidateUuid(String candidateUuid) {
        this.candidateUuid = candidateUuid;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public T getInventory() {
        return inventory;
    }

    public void setInventory(T inventory) {
        this.inventory = inventory;
    }

    public CandidateRejectReason getReason() {
        return reason;
    }

    public void setReason(CandidateRejectReason reason) {
        this.reason = reason;
    }

    public CandidateRef getParent() {
        return parent;
    }

    public void setParent(CandidateRef parent) {
        this.parent = parent;
    }
}
