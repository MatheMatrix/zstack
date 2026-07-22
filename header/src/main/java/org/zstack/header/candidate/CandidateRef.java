package org.zstack.header.candidate;

public class CandidateRef {
    private String candidateType;
    private String candidateUuid;

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
}
