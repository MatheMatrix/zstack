package org.zstack.header.host;

import org.springframework.http.HttpMethod;
import org.zstack.header.message.APIMessage;
import org.zstack.header.message.APIParam;
import org.zstack.header.rest.RestRequest;

@RestRequest(
        path = "/hosts/kvm/iscsiInitiatorName/{uuid}/actions",
        method = HttpMethod.PUT,
        responseClass = APIUpdateHostIscsiInitiatorNameEvent.class,
        isAction = true
)
public class APIUpdateHostIscsiInitiatorNameMsg extends APIMessage implements HostMessage {
    @APIParam(resourceType = KVMHostVO.class)
    private String uuid;
    @APIParam(nonempty = true, emptyString = false, validRegexValues = "^iqn\\.[0-9]{4}-[0-9]{2}\\.[^:]+:.+")
    private String iscsiInitiatorName;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getIscsiInitiatorName() {
        return iscsiInitiatorName;
    }

    public void setIscsiInitiatorName(String iscsiInitiatorName) {
        this.iscsiInitiatorName = iscsiInitiatorName;
    }

    public static APIUpdateHostIscsiInitiatorNameMsg __example__() {
        APIUpdateHostIscsiInitiatorNameMsg msg = new APIUpdateHostIscsiInitiatorNameMsg();
        msg.setUuid(uuid());
        msg.setIscsiInitiatorName("iqn.2015-01.io.helix:a6e4508d2378");
        return msg;
    }

    @Override
    public String getHostUuid() {
        return uuid;
    }
}
