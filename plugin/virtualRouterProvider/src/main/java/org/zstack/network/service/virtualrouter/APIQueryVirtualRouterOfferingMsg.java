package org.zstack.network.service.virtualrouter;

import org.springframework.http.HttpMethod;
import org.zstack.header.query.APIQueryMessage;
import org.zstack.header.query.AutoQuery;
import org.zstack.header.rest.RestRequest;

import java.util.List;

import static java.util.Arrays.asList;
import org.zstack.header.vm.MetadataImpact;

@AutoQuery(replyClass = APIQueryVirtualRouterOfferingReply.class, inventoryClass = VirtualRouterOfferingInventory.class)
@RestRequest(
        path = "/instance-offerings/virtual-routers",
        optionalPaths = {"/instance-offerings/virtual-routers/{uuid}"},
        responseClass = APIQueryVirtualRouterOfferingReply.class,
        method = HttpMethod.GET
)
@MetadataImpact(MetadataImpact.Impact.NONE)
public class APIQueryVirtualRouterOfferingMsg extends APIQueryMessage {


    public static List<String> __example__() {
        return asList();
    }

}
