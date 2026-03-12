package org.zstack.network.service.lb;

import org.springframework.http.HttpMethod;
import org.zstack.header.query.APIQueryMessage;
import org.zstack.header.query.AutoQuery;
import org.zstack.header.rest.RestRequest;

import java.util.List;

import static java.util.Arrays.asList;
import org.zstack.header.vm.MetadataImpact;

/**
 * Created by frank on 8/18/2015.
 */
@AutoQuery(replyClass = APIQueryLoadBalancerListenerReply.class, inventoryClass = LoadBalancerListenerInventory.class)
@RestRequest(
        path = "/load-balancers/listeners",
        optionalPaths = { "/load-balancers/listeners/{uuid}"},
        method = HttpMethod.GET,
        responseClass = APIQueryLoadBalancerListenerReply.class
)
@MetadataImpact(MetadataImpact.Impact.NONE)
public class APIQueryLoadBalancerListenerMsg extends APIQueryMessage {

    public static List<String> __example__() {
        return asList();
    }

}
