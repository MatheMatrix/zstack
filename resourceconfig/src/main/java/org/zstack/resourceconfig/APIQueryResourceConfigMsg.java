package org.zstack.resourceconfig;

import org.springframework.http.HttpMethod;
import org.zstack.header.query.APIQueryMessage;
import org.zstack.header.query.AutoQuery;
import org.zstack.header.rest.RestRequest;

import java.util.Collections;
import java.util.List;
import org.zstack.header.vm.MetadataImpact;

@RestRequest(path = "/resource-configurations", method = HttpMethod.GET, responseClass = APIQueryResourceConfigReply.class)
@AutoQuery(replyClass = APIQueryResourceConfigReply.class, inventoryClass = ResourceConfigInventory.class)
@MetadataImpact(MetadataImpact.Impact.NONE)
public class APIQueryResourceConfigMsg extends APIQueryMessage {
    public static List<String> __example__() {
        return Collections.singletonList("category=host");
    }
}
