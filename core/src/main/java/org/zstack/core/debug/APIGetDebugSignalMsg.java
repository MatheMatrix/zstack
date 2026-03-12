package org.zstack.core.debug;

import org.springframework.http.HttpMethod;
import org.zstack.header.message.APISyncCallMessage;
import org.zstack.header.rest.RestRequest;
import org.zstack.header.vm.MetadataImpact;

@RestRequest(
        path = "/debug",
        method = HttpMethod.GET,
        responseClass = APIGetDebugSignalReply.class)
@MetadataImpact(MetadataImpact.Impact.NONE)
public class APIGetDebugSignalMsg extends APISyncCallMessage {
    public static APIGetDebugSignalMsg __example__() {
        APIGetDebugSignalMsg msg = new APIGetDebugSignalMsg();
        return msg;
    }
}
