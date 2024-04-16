package org.zstack.header.vm;

import org.springframework.http.HttpMethod;
import org.zstack.header.identity.Action;
import org.zstack.header.message.APIDeleteMessage;
import org.zstack.header.message.APIParam;
import org.zstack.header.rest.RestRequest;

@Action(category = VmInstanceConstant.ACTION_CATEGORY)
@RestRequest(
        path = "/vm-instances/{uuid}",
        method = HttpMethod.DELETE,
        responseClass = APIDeleteVmInstanceTemplateEvent.class
)
public class APIDeleteVmInstanceTemplateMsg extends APIDeleteMessage implements VmInstanceMessage {
    @APIParam(resourceType = VmInstanceTemplateVO.class, checkAccount = true, operationTarget = true, successIfResourceNotExisting = true)
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

    public static APIDeleteVmInstanceTemplateMsg __example__() {
        APIDeleteVmInstanceTemplateMsg msg = new APIDeleteVmInstanceTemplateMsg();
        msg.setUuid(uuid());
        return msg;
    }
}
