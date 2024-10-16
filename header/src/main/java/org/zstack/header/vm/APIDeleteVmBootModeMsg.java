package org.zstack.header.vm;

import org.springframework.http.HttpMethod;
import org.zstack.header.identity.Action;
import org.zstack.header.message.APIDeleteMessage;
import org.zstack.header.message.APIParam;
import org.zstack.header.rest.RestRequest;

@Action(category = VmInstanceConstant.ACTION_CATEGORY)
@RestRequest(
        path = "/vm-instances/{uuid}/bootmode",
        method = HttpMethod.DELETE,
        responseClass = APIDeleteVmBootModeEvent.class
)
public class APIDeleteVmBootModeMsg extends APIDeleteMessage implements VmInstanceMessage {
    @APIParam(resourceType = VmInstanceVO.class)
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

    public static APIDeleteVmBootModeMsg __example__() {
        APIDeleteVmBootModeMsg msg = new APIDeleteVmBootModeMsg();
        msg.uuid = uuid();
        return msg;
    }
}
