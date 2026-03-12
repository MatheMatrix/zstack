package org.zstack.network.service.portforwarding;

import org.springframework.http.HttpMethod;
import org.zstack.header.message.APIParam;
import org.zstack.header.message.APISyncCallMessage;
import org.zstack.header.rest.RestRequest;
import org.zstack.header.vm.MetadataImpact;

/**
 */
@RestRequest(
        path = "/port-forwarding/{ruleUuid}/vm-instances/candidate-nics",
        method = HttpMethod.GET,
        responseClass = APIGetPortForwardingAttachableVmNicsReply.class
)
@MetadataImpact(MetadataImpact.Impact.NONE)
public class APIGetPortForwardingAttachableVmNicsMsg extends APISyncCallMessage {
    @APIParam(resourceType = PortForwardingRuleVO.class)
    private String ruleUuid;

    public String getRuleUuid() {
        return ruleUuid;
    }

    public void setRuleUuid(String portForwardingRuleUuid) {
        this.ruleUuid = portForwardingRuleUuid;
    }
 
    public static APIGetPortForwardingAttachableVmNicsMsg __example__() {
        APIGetPortForwardingAttachableVmNicsMsg msg = new APIGetPortForwardingAttachableVmNicsMsg();
        msg.setRuleUuid(uuid());
        return msg;
    }

}
