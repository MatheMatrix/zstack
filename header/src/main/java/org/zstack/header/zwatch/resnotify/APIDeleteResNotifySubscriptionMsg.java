package org.zstack.header.zwatch.resnotify;

import org.springframework.http.HttpMethod;
import org.zstack.header.message.APIDeleteMessage;
import org.zstack.header.message.APIEvent;
import org.zstack.header.message.APIMessage;
import org.zstack.header.message.APIParam;
import org.zstack.header.other.APIAuditor;
import org.zstack.header.rest.RestRequest;

@RestRequest(
        path = "/zwatch/resnotify/subscriptions/{uuid}",
        method = HttpMethod.DELETE,
        responseClass = APIDeleteResNotifySubscriptionEvent.class
)
public class APIDeleteResNotifySubscriptionMsg extends APIDeleteMessage implements APIAuditor {
    @APIParam(resourceType = ResNotifySubscriptionVO.class, successIfResourceNotExisting = true)
    private String uuid;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    @Override
    public Result audit(APIMessage msg, APIEvent rsp) {
        return new Result(((APIDeleteResNotifySubscriptionMsg) msg).getUuid(), ResNotifySubscriptionVO.class);
    }

    public static APIDeleteResNotifySubscriptionMsg __example__() {
        APIDeleteResNotifySubscriptionMsg msg = new APIDeleteResNotifySubscriptionMsg();
        msg.setUuid(uuid());
        return msg;
    }
}
