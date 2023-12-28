package org.zstack.network.hostNetworkInterface.lldp.api;

import org.springframework.http.HttpMethod;
import org.zstack.header.identity.Action;
import org.zstack.header.query.APIQueryMessage;
import org.zstack.header.query.AutoQuery;
import org.zstack.header.rest.RestRequest;
import org.zstack.network.hostNetworkInterface.lldp.entity.HostNetworkInterfaceLldpInventory;
import org.zstack.network.hostNetworkInterface.lldp.LldpConstant;

import java.util.List;

import static java.util.Arrays.asList;

@AutoQuery(replyClass = APIQueryHostNetworkInterfaceLldpReply.class, inventoryClass = HostNetworkInterfaceLldpInventory.class)
@Action(category = LldpConstant.ACTION_CATEGORY, names = {"read"})
@RestRequest(
        path = "/hostNetworkInterface/lldp/all",
        optionalPaths = {"/hostNetworkInterface/lldp/{interfaceUuid}"},
        method = HttpMethod.GET,
        responseClass = APIQueryHostNetworkInterfaceLldpReply.class
)
public class APIQueryHostNetworkInterfaceLldpMsg extends APIQueryMessage {
    public static List<String> __example__() {
        return asList();
    }

}