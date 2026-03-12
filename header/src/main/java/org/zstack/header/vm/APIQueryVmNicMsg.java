package org.zstack.header.vm;

import org.springframework.http.HttpMethod;
import org.zstack.header.query.APIQueryMessage;
import org.zstack.header.query.AutoQuery;
import org.zstack.header.rest.RestRequest;

import java.util.List;

import static java.util.Arrays.asList;
import org.zstack.header.vm.MetadataImpact;

@AutoQuery(replyClass = APIQueryVmNicReply.class, inventoryClass = VmNicInventory.class)
@RestRequest(
        path = "/vm-instances/nics",
        optionalPaths = {"/vm-instances/nics/{uuid}"},
        method = HttpMethod.GET,
        responseClass = APIQueryVmNicReply.class
)
@MetadataImpact(MetadataImpact.Impact.NONE)
public class APIQueryVmNicMsg extends APIQueryMessage {
    public static List<String> __example__() {
        return asList("ip=172.20.100.100");
    }
}
