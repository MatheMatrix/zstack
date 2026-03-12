package org.zstack.core.config;

import org.springframework.http.HttpMethod;
import org.zstack.header.message.APIEvent;
import org.zstack.header.message.APIMessage;
import org.zstack.header.rest.RestRequest;
import org.zstack.header.vm.MetadataImpact;

@RestRequest(
        path = "/global-configurations/actions",
        method = HttpMethod.PUT,
        isAction = true,
        responseClass = APIResetGlobalConfigEvent.class
)
@MetadataImpact(MetadataImpact.Impact.NONE)
public class APIResetGlobalConfigMsg extends APIMessage {
    public static APIResetGlobalConfigMsg __example__() {
        APIResetGlobalConfigMsg msg = new APIResetGlobalConfigMsg();
        return msg;
    }
}
