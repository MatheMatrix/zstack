package org.zstack.kvm.xmlhook;

import org.springframework.http.HttpMethod;
import org.zstack.header.message.APIEvent;
import org.zstack.header.message.APIMessage;
import org.zstack.header.message.APIParam;
import org.zstack.header.message.DefaultTimeout;
import org.zstack.header.other.APIAuditor;
import org.zstack.header.rest.RestRequest;

import java.util.concurrent.TimeUnit;

@RestRequest(
        path = "/vm-instances/xml-hook-script",
        method = HttpMethod.PUT,
        isAction = true,
        responseClass = APIUpdateVmUserDefinedXmlHookScriptEvent.class
)
@DefaultTimeout(timeunit = TimeUnit.HOURS, value = 12)
public class APIUpdateVmUserDefinedXmlHookScriptMsg extends APIMessage implements APIAuditor, XmlHookMessage {
    @APIParam(resourceType = XmlHookVO.class)
    private String uuid;

    @APIParam(required = false, maxLength = 255)
    private String name;

    @APIParam(required = false, maxLength = 2048)
    private String description;

    @APIParam(required = false)
    private String hookScript;

    @APIParam(validValues = {"Reboot", "None"}, required = false)
    private String startupStrategy;

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

    public String getHookScript() {
        return hookScript;
    }

    public void setHookScript(String hookScript) {
        this.hookScript = hookScript;
    }

    public static APIUpdateVmUserDefinedXmlHookScriptMsg __example__() {
        APIUpdateVmUserDefinedXmlHookScriptMsg msg = new APIUpdateVmUserDefinedXmlHookScriptMsg();
        msg.setUuid(uuid());
        msg.setName("example");
        return msg;
    }

    @Override
    public String getXmlHookUuid() {
        return uuid;
    }

    public String getStartupStrategy() {
        return startupStrategy;
    }

    public void setStartupStrategy(String startupStrategy) {
        this.startupStrategy = startupStrategy;
    }

    @Override
    public Result audit(APIMessage msg, APIEvent rsp) {
        return new Result(((APIUpdateVmUserDefinedXmlHookScriptMsg) msg).getUuid(), XmlHookVO.class);
    }
}
