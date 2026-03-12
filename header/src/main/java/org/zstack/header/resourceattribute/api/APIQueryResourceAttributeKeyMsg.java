package org.zstack.header.resourceattribute.api;

import org.springframework.http.HttpMethod;
import org.zstack.header.resourceattribute.entity.ResourceAttributeKeyInventory;
import org.zstack.header.query.APIQueryMessage;
import org.zstack.header.query.AutoQuery;
import org.zstack.header.rest.RestRequest;

import java.util.List;

import static org.zstack.utils.CollectionDSL.list;
import org.zstack.header.vm.MetadataImpact;

@AutoQuery(replyClass = APIQueryResourceAttributeKeyReply.class, inventoryClass = ResourceAttributeKeyInventory.class)
@RestRequest(
        path = "/resource-attributes/keys",
        optionalPaths = {"/resource-attributes/keys/{uuid}"},
        responseClass = APIQueryResourceAttributeKeyReply.class,
        method = HttpMethod.GET
)
@MetadataImpact(MetadataImpact.Impact.NONE)
public class APIQueryResourceAttributeKeyMsg extends APIQueryMessage {
    public static List<String> __example__() {
        return list("name=OperationsPersonnel");
    }
}
