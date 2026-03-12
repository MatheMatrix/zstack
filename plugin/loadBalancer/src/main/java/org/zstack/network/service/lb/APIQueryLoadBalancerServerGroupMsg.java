package org.zstack.network.service.lb;

import org.springframework.http.HttpMethod;
import org.zstack.header.query.APIQueryMessage;
import org.zstack.header.query.AutoQuery;
import org.zstack.header.rest.RestRequest;

import java.util.List;

import static java.util.Arrays.asList;
import org.zstack.header.vm.MetadataImpact;
@AutoQuery(replyClass = APIQueryLoadBalancerServerGroupReply.class, inventoryClass = LoadBalancerServerGroupInventory.class)
@RestRequest(
        path = "/load-balancers/servergroups",
        optionalPaths = {"/load-balancers/servergroups/{uuid}"},
        method = HttpMethod.GET,
        responseClass = APIQueryLoadBalancerServerGroupReply.class
)
@MetadataImpact(MetadataImpact.Impact.NONE)
public class APIQueryLoadBalancerServerGroupMsg extends APIQueryMessage{
    public static List<String> __example__() {
        return asList();
    }

}