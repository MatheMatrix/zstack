package org.zstack.network.l2.vxlan.vxlanNetwork;

import org.springframework.http.HttpMethod;
import org.zstack.header.query.APIQueryMessage;
import org.zstack.header.query.AutoQuery;
import org.zstack.header.rest.RestRequest;

import java.util.List;

import static java.util.Arrays.asList;
import org.zstack.header.vm.MetadataImpact;

/**
 * Created by weiwang on 15/03/2017.
 */

@AutoQuery(replyClass = APIQueryL2VxlanNetworkReply.class, inventoryClass = L2VxlanNetworkInventory.class)
@RestRequest(
        path = "/l2-networks/vxlan",
        optionalPaths = {"/l2-networks/vxlan/{uuid}"},
        method = HttpMethod.GET,
        responseClass = APIQueryL2VxlanNetworkReply.class
)
@MetadataImpact(MetadataImpact.Impact.NONE)
public class APIQueryL2VxlanNetworkMsg extends APIQueryMessage {

    public static List<String> __example__() {
        return asList();
    }
}
