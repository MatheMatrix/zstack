package org.zstack.header.tag;

import org.springframework.http.HttpMethod;
import org.zstack.header.rest.RestRequest;

/**
 */
@RestRequest(
        path = "/user-tags",
        method = HttpMethod.POST,
        responseClass = APICreateUserTagEvent.class,
        parameterName = "params"
)
public class APICreateUserTagMsg extends APIAbstractCreateTagMsg {
 
    public static APICreateUserTagMsg __example__() {
        APICreateUserTagMsg msg = new APICreateUserTagMsg();
        msg.setResourceType("DiskOfferingVO");
        msg.setResourceUuid(uuid());
        msg.setTag("for-large-DB");
        return msg;
    }

}
