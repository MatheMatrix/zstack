package org.zstack.header.candidate;

import java.util.ArrayList;
import java.util.List;

public class CandidateDecisionResult {
    private List<CandidateRecord<?>> candidates = new ArrayList<>();
    private CandidateDecisionSummary summary;
    private Boolean truncated = false;

    public List<CandidateRecord<?>> getCandidates() {
        return candidates;
    }

    public void setCandidates(List<CandidateRecord<?>> candidates) {
        this.candidates = candidates;
    }

    public CandidateDecisionSummary getSummary() {
        return summary;
    }

    public void setSummary(CandidateDecisionSummary summary) {
        this.summary = summary;
    }

    public Boolean getTruncated() {
        return truncated;
    }

    public void setTruncated(Boolean truncated) {
        this.truncated = truncated;
    }
}
