package org.zstack.header.host;

import org.springframework.http.HttpMethod;
import org.zstack.header.message.APIMessage;
import org.zstack.header.message.APIParam;
import org.zstack.header.rest.RestRequest;

@RestRequest(
        path = "/hosts/hostname/{uuid}/actions",
        method = HttpMethod.PUT,
        responseClass = APIUpdateHostnameEvent.class,
        isAction = true
)
public class APIUpdateHostnameMsg extends APIMessage implements HostMessage {
    @APIParam(resourceType = HostVO.class)
    private String uuid;
    @APIParam(nonempty = true, emptyString = false)
    private String hostName;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    @Override
    public String getHostUuid() {
        return uuid;
    }

    public String getHostName() {
        return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    public static APIUpdateHostnameMsg __example__() {
        APIUpdateHostnameMsg msg = new APIUpdateHostnameMsg();
        msg.setUuid(uuid());
        msg.setHostName("user");
        return msg;
    }
}
