package org.zstack.header.storage.primary;

import org.zstack.header.message.APIReply;
import org.zstack.header.rest.RestResponse;

@RestResponse(fieldsTo = {"all"})
public class APICheckPrimaryStorageConsistencyReply extends APIReply {
    private boolean consistent;
    private ConsistencyCheckStatus reason;
    private String candidateVgUuid;

    public boolean isConsistent() {
        return consistent;
    }

    public void setConsistent(boolean consistent) {
        this.consistent = consistent;
    }

    public ConsistencyCheckStatus getReason() {
        return reason;
    }

    public void setReason(ConsistencyCheckStatus reason) {
        this.reason = reason;
    }

    public String getCandidateVgUuid() {
        return candidateVgUuid;
    }

    public void setCandidateVgUuid(String candidateVgUuid) {
        this.candidateVgUuid = candidateVgUuid;
    }

    public static APICheckPrimaryStorageConsistencyReply __example__() {
        APICheckPrimaryStorageConsistencyReply reply = new APICheckPrimaryStorageConsistencyReply();
        reply.setConsistent(true);
        reply.setReason(ConsistencyCheckStatus.CONSISTENT);
        return reply;
    }
}
