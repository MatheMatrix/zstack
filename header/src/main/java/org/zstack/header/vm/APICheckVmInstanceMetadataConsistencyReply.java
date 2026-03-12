package org.zstack.header.vm;

import org.zstack.header.message.APIReply;
import org.zstack.header.rest.RestResponse;

import java.util.Collections;
import java.util.List;

@RestResponse(fieldsTo = {"all"})
public class APICheckVmInstanceMetadataConsistencyReply extends APIReply {
    private List<ConsistencyCheckResult> results;

    public List<ConsistencyCheckResult> getResults() {
        return results;
    }

    public void setResults(List<ConsistencyCheckResult> results) {
        this.results = results;
    }

    public static APICheckVmInstanceMetadataConsistencyReply __example__() {
        APICheckVmInstanceMetadataConsistencyReply reply = new APICheckVmInstanceMetadataConsistencyReply();
        ConsistencyCheckResult result = new ConsistencyCheckResult();
        result.setVmUuid(uuid());
        result.setConsistent(true);
        result.setDiffs(Collections.emptyList());
        result.setAction("NONE");
        reply.results = Collections.singletonList(result);
        return reply;
    }
}
