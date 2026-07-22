package org.zstack.header.vm;

import org.springframework.http.HttpMethod;
import org.zstack.header.candidate.CandidateDecisionRequest;
import org.zstack.header.identity.Action;
import org.zstack.header.message.APIParam;
import org.zstack.header.message.APISyncCallMessage;
import org.zstack.header.rest.RestRequest;

@Action(category = VmInstanceConstant.ACTION_CATEGORY, names = {"read"})
@RestRequest(
        path = "/vm-instances/{vmInstanceUuid}/migration-candidates",
        method = HttpMethod.GET,
        responseClass = APIGetVmMigrationCandidatesReply.class
)
public class APIGetVmMigrationCandidatesMsg extends APISyncCallMessage implements VmInstanceMessage, CandidateDecisionRequest {
    @APIParam(resourceType = VmInstanceVO.class, checkAccount = true)
    private String vmInstanceUuid;

    @Override
    public String getVmInstanceUuid() {
        return vmInstanceUuid;
    }

    public void setVmInstanceUuid(String vmInstanceUuid) {
        this.vmInstanceUuid = vmInstanceUuid;
    }

    public static APIGetVmMigrationCandidatesMsg __example__() {
        APIGetVmMigrationCandidatesMsg msg = new APIGetVmMigrationCandidatesMsg();
        msg.vmInstanceUuid = uuid();
        return msg;
    }
}
