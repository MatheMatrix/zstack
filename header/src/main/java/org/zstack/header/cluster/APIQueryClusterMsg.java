package org.zstack.header.cluster;

import org.springframework.http.HttpMethod;
import org.zstack.header.query.APIQueryMessage;
import org.zstack.header.query.AutoQuery;
import org.zstack.header.rest.RestRequest;

import java.util.List;

import static java.util.Arrays.asList;
import org.zstack.header.vm.MetadataImpact;

@AutoQuery(replyClass = APIQueryClusterReply.class, inventoryClass = ClusterInventory.class)
@RestRequest(
        path = "/clusters",
        optionalPaths = {"/clusters/{uuid}"},
        responseClass = APIQueryClusterReply.class,
        method = HttpMethod.GET
)
@MetadataImpact(MetadataImpact.Impact.NONE)
public class APIQueryClusterMsg extends APIQueryMessage {

 
    public static List<String> __example__() {
            return asList("hypervisorType=KVM");
    }

}
