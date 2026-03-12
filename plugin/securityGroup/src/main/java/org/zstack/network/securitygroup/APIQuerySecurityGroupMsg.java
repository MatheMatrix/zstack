package org.zstack.network.securitygroup;

import org.springframework.http.HttpMethod;
import org.zstack.header.query.APIQueryMessage;
import org.zstack.header.query.AutoQuery;
import org.zstack.header.rest.RestRequest;

import java.util.List;

import static java.util.Arrays.asList;
import org.zstack.header.vm.MetadataImpact;

@AutoQuery(replyClass = APIQuerySecurityGroupReply.class, inventoryClass = SecurityGroupInventory.class)
@RestRequest(
        path = "/security-groups",
        optionalPaths = {"/security-groups/{uuid}"},
        method = HttpMethod.GET,
        responseClass = APIQuerySecurityGroupReply.class
)
@MetadataImpact(MetadataImpact.Impact.NONE)
public class APIQuerySecurityGroupMsg extends APIQueryMessage {

    public static List<String> __example__() {
        return asList("name=web", "state=Enabled");
    }

}
