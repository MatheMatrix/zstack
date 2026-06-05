package org.zstack.header.vm;

import org.zstack.header.candidate.CandidateDecisionSummary;
import org.zstack.header.candidate.CandidateRecord;
import org.zstack.header.message.APIReply;
import org.zstack.header.rest.RestResponse;

import java.util.List;

@RestResponse(fieldsTo = {"all"})
public class APIGetVmMigrationCandidatesReply extends APIReply {
    private List<CandidateRecord<?>> candidates;
    private CandidateDecisionSummary summary;
    private Boolean truncated;

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
