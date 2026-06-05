package org.zstack.header.vm;

import org.springframework.http.HttpMethod;
import org.zstack.header.candidate.CandidateDecisionRequest;
import org.zstack.header.identity.Action;
import org.zstack.header.message.APIParam;
import org.zstack.header.message.APISyncCallMessage;
import org.zstack.header.rest.RestRequest;

@Action(category = VmInstanceConstant.ACTION_CATEGORY, names = {"read"})
@RestRequest(
        path = "/vm-instances/{uuid}/starting-candidates",
        method = HttpMethod.GET,
        responseClass = APIGetVmStartingCandidatesReply.class
)
public class APIGetVmStartingCandidatesMsg extends APISyncCallMessage implements VmInstanceMessage, CandidateDecisionRequest {
    @APIParam(resourceType = VmInstanceVO.class, checkAccount = true, operationTarget = true)
    private String uuid;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    @Override
    public String getVmInstanceUuid() {
        return uuid;
    }

    public static APIGetVmStartingCandidatesMsg __example__() {
        APIGetVmStartingCandidatesMsg msg = new APIGetVmStartingCandidatesMsg();
        msg.uuid = uuid();
        return msg;
    }
}
