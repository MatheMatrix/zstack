package org.zstack.header.vm;

import org.springframework.http.HttpMethod;
import org.zstack.header.message.APIMessage;
import org.zstack.header.message.APIParam;
import org.zstack.header.rest.RestRequest;

/**
 * Created by frank on 11/12/2015.
 */
@RestRequest(
        path = "/vm-instances/{uuid}/actions",
        isAction = true,
        method = HttpMethod.PUT,
        responseClass = APIRecoverVmInstanceEvent.class
)
public class APIRecoverVmInstanceMsg extends APIMessage implements VmInstanceMessage {
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
        return uuid;
    }
 
    public static APIRecoverVmInstanceMsg __example__() {
        APIRecoverVmInstanceMsg msg = new APIRecoverVmInstanceMsg();
        msg.uuid = uuid();
        return msg;
    }
}
