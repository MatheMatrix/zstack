package org.zstack.header.network.service;

import org.springframework.http.HttpMethod;
import org.zstack.header.query.APIQueryMessage;
import org.zstack.header.query.AutoQuery;
import org.zstack.header.rest.RestRequest;

import java.util.List;

import static java.util.Arrays.asList;
import org.zstack.header.vm.MetadataImpact;

@AutoQuery(replyClass = APIQueryNetworkServiceProviderReply.class, inventoryClass = NetworkServiceProviderInventory.class)
@RestRequest(
        path = "/network-services/providers",
        method = HttpMethod.GET,
        responseClass = APIQueryNetworkServiceProviderReply.class
)
@MetadataImpact(MetadataImpact.Impact.NONE)
public class APIQueryNetworkServiceProviderMsg extends APIQueryMessage {

 
    public static List<String> __example__() {
        return asList();
    }

}
