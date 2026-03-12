package org.zstack.header.managementnode;

import org.springframework.http.HttpMethod;
import org.zstack.header.identity.SuppressCredentialCheck;
import org.zstack.header.message.APISyncCallMessage;
import org.zstack.header.rest.RestRequest;
import org.zstack.header.vm.MetadataImpact;

@SuppressCredentialCheck
@RestRequest(
        path = "/management-nodes/actions",
        isAction = true,
        method = HttpMethod.PUT,
        responseClass = APIGetSupportAPIsReply.class
)
@MetadataImpact(MetadataImpact.Impact.NONE)
public class APIGetSupportAPIsMsg extends APISyncCallMessage implements APIManagementNodeMessage {

    public static APIGetSupportAPIsMsg __example__() {
        return new APIGetSupportAPIsMsg();
    }
}
