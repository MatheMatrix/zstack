package org.zstack.header.allocator;

import org.zstack.header.candidate.CandidateDecisionResult;
import org.zstack.header.host.HostInventory;
import org.zstack.header.message.MessageReply;

import java.util.List;

public class AllocateHostDryRunReply extends MessageReply {
    private List<HostInventory> hosts;
    private CandidateDecisionResult candidateDecisionResult;

    public AllocateHostDryRunReply() {
    }

    public List<HostInventory> getHosts() {
        return hosts;
    }

    public void setHosts(List<HostInventory> hosts) {
        this.hosts = hosts;
    }

    public CandidateDecisionResult getCandidateDecisionResult() {
        return candidateDecisionResult;
    }

    public void setCandidateDecisionResult(CandidateDecisionResult candidateDecisionResult) {
        this.candidateDecisionResult = candidateDecisionResult;
    }
}
