package org.zstack.header.core.external.plugin;

import org.springframework.http.HttpMethod;
import org.zstack.header.message.APIParam;
import org.zstack.header.message.APISyncCallMessage;
import org.zstack.header.rest.RestRequest;

@RestRequest(
        path = "/external/plugins/option/list",
        method = HttpMethod.GET,
        responseClass = APIGetPluginOptionTypeListReply.class
)
public class APIGetPluginOptionTypeListMsg extends APISyncCallMessage {
    @APIParam(required = false)
    private String pluginUuid;

    public String getPluginUuid() {
        return pluginUuid;
    }

    public void setPluginUuid(String pluginUuid) {
        this.pluginUuid = pluginUuid;
    }

    public static APIGetPluginOptionTypeListMsg __example__() {
        APIGetPluginOptionTypeListMsg msg = new APIGetPluginOptionTypeListMsg();
        msg.setPluginUuid(uuid());
        return msg;
    }
}
