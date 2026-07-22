package org.zstack.header.vm;

import org.zstack.header.candidate.CandidateDecisionSummary;
import org.zstack.header.candidate.CandidateRecord;
import org.zstack.header.cluster.ClusterInventory;
import org.zstack.header.message.APIReply;
import org.zstack.header.rest.RestResponse;
import org.zstack.header.zone.ZoneInventory;

import java.util.List;

@RestResponse(fieldsTo = {"all"})
public class APIGetVmCreationCandidatesReply extends APIReply {
    private List<ZoneInventory> zones;
    private List<ClusterInventory> clusters;
    private List<CandidateRecord<?>> candidates;
    private CandidateDecisionSummary summary;
    private Boolean truncated;

    public List<ZoneInventory> getZones() {
        return zones;
    }

    public void setZones(List<ZoneInventory> zones) {
        this.zones = zones;
    }

    public List<ClusterInventory> getClusters() {
        return clusters;
    }

    public void setClusters(List<ClusterInventory> clusters) {
        this.clusters = clusters;
    }

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
