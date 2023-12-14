package org.zstack.header.vm;

import org.springframework.http.HttpMethod;
import org.zstack.header.identity.Action;
import org.zstack.header.message.APIMessage;
import org.zstack.header.message.APIParam;
import org.zstack.header.rest.RestRequest;

@Action(category = VmInstanceConstant.ACTION_CATEGORY)
@RestRequest(
        path = "/vm-instances/{uuid}/process/identifier/createTime",
        method = HttpMethod.GET,
        responseClass = APITakeVmProcessIdentifierCreateTimeEvent.class
)
public class APITakeVmProcessIdentifierCreateTimeMsg extends APIMessage implements VmInstanceMessage {
    @APIParam(resourceType = VmInstanceVO.class, checkAccount = true)
    private String uuid;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    @Override
    public String getVmInstanceUuid() {
        return getUuid();
    }

    public static APITakeVmProcessIdentifierCreateTimeMsg __example__() {
        APITakeVmProcessIdentifierCreateTimeMsg msg = new APITakeVmProcessIdentifierCreateTimeMsg();
        msg.setUuid(uuid());
        return msg;
    }
}
