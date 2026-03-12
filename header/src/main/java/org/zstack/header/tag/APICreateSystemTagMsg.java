package org.zstack.header.tag;

import org.springframework.http.HttpMethod;
import org.zstack.header.rest.RestRequest;
import org.zstack.header.vm.MetadataImpact;

/**
 */
@RestRequest(
        path = "/system-tags",
        method = HttpMethod.POST,
        responseClass = APICreateSystemTagEvent.class,
        parameterName = "params"
)
@MetadataImpact(MetadataImpact.Impact.CONFIG)
public class APICreateSystemTagMsg extends APIAbstractCreateTagMsg {
 
    public static APICreateSystemTagMsg __example__() {
        APICreateSystemTagMsg msg = new APICreateSystemTagMsg();
        msg.setResourceType("HostVO");
        msg.setResourceUuid(uuid());
        msg.setTag("reservedMemory::1G");
        return msg;
    }

}
