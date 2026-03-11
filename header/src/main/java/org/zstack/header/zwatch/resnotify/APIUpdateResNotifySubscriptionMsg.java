package org.zstack.header.zwatch.resnotify;

import org.springframework.http.HttpMethod;
import org.zstack.header.message.APIEvent;
import org.zstack.header.message.APIMessage;
import org.zstack.header.message.APIParam;
import org.zstack.header.other.APIAuditor;
import org.zstack.header.rest.RestRequest;

import java.util.List;

@RestRequest(
        path = "/zwatch/resnotify/subscriptions/{uuid}",
        isAction = true,
        method = HttpMethod.PUT,
        responseClass = APIUpdateResNotifySubscriptionEvent.class
)
public class APIUpdateResNotifySubscriptionMsg extends APIMessage implements APIAuditor {
    @APIParam(resourceType = ResNotifySubscriptionVO.class, operationTarget = true)
    private String uuid;

    @APIParam(maxLength = 255, required = false)
    private String name;

    @APIParam(maxLength = 1024, required = false)
    private String description;

    @APIParam(required = false, nonempty = false)
    private List<String> resourceTypes;

    @APIParam(required = false, nonempty = false)
    private List<String> eventTypes;

    @APIParam(validValues = {"Enabled", "Disabled"}, required = false)
    private String state;

    @APIParam(maxLength = 2048, required = false)
    private String webhookUrl;

    @APIParam(maxLength = 256, required = false)
    private String secret;

    @APIParam(maxLength = 2048, required = false)
    private String customHeaders;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

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

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
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
        return new Result(((APIUpdateResNotifySubscriptionMsg) msg).getUuid(), ResNotifySubscriptionVO.class);
    }

    public static APIUpdateResNotifySubscriptionMsg __example__() {
        APIUpdateResNotifySubscriptionMsg msg = new APIUpdateResNotifySubscriptionMsg();
        msg.setUuid(uuid());
        msg.setName("updated-name");
        msg.setState("Enabled");
        return msg;
    }
}
