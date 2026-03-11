package org.zstack.header.zwatch.resnotify;

import org.springframework.http.HttpMethod;
import org.zstack.header.message.APICreateMessage;
import org.zstack.header.message.APIEvent;
import org.zstack.header.message.APIMessage;
import org.zstack.header.message.APIParam;
import org.zstack.header.other.APIAuditor;
import org.zstack.header.rest.RestRequest;

import java.util.List;

@RestRequest(
        path = "/zwatch/resnotify/subscriptions",
        method = HttpMethod.POST,
        parameterName = "params",
        responseClass = APISubscribeResNotifyEvent.class
)
public class APISubscribeResNotifyMsg extends APICreateMessage implements APIAuditor {
    @APIParam(maxLength = 255, required = false)
    private String name;

    @APIParam(maxLength = 1024, required = false)
    private String description;

    @APIParam(required = false, nonempty = false)
    private List<String> resourceTypes;

    @APIParam(required = false, nonempty = false)
    private List<String> eventTypes;

    @APIParam(validValues = {"WEBHOOK"}, required = false)
    private String type = "WEBHOOK";

    @APIParam(maxLength = 2048)
    private String webhookUrl;

    @APIParam(maxLength = 256, required = false)
    private String secret;

    @APIParam(maxLength = 2048, required = false)
    private String customHeaders;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getResourceTypes() {
        return resourceTypes;
    }

    public void setResourceTypes(List<String> resourceTypes) {
        this.resourceTypes = resourceTypes;
    }

    public List<String> getEventTypes() {
        return eventTypes;
    }

    public void setEventTypes(List<String> eventTypes) {
        this.eventTypes = eventTypes;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getCustomHeaders() {
        return customHeaders;
    }

    public void setCustomHeaders(String customHeaders) {
        this.customHeaders = customHeaders;
    }

    @Override
    public Result audit(APIMessage msg, APIEvent rsp) {
        APISubscribeResNotifyEvent evt = (APISubscribeResNotifyEvent) rsp;
        return new Result(rsp.isSuccess() ? evt.getInventory().getUuid() : "", ResNotifySubscriptionVO.class);
    }

    public static APISubscribeResNotifyMsg __example__() {
        APISubscribeResNotifyMsg msg = new APISubscribeResNotifyMsg();
        msg.setName("vm-lifecycle-notify");
        msg.setWebhookUrl("https://example.com/webhook");
        msg.setType("WEBHOOK");
        return msg;
    }
}
