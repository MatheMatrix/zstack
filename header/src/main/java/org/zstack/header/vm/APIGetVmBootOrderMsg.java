package org.zstack.header.vm;

import org.springframework.http.HttpMethod;
import org.zstack.header.message.APIParam;
import org.zstack.header.message.APISyncCallMessage;
import org.zstack.header.rest.RestRequest;
import org.zstack.header.vm.MetadataImpact;

/**
 * Created by frank on 11/22/2015.
 */
@RestRequest(
        path = "/vm-instances/{uuid}/boot-orders",
        method = HttpMethod.GET,
        responseClass = APIGetVmBootOrderReply.class
)
@MetadataImpact(MetadataImpact.Impact.NONE)
public class APIGetVmBootOrderMsg extends APISyncCallMessage implements VmInstanceMessage {
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
 
    public static APIGetVmBootOrderMsg __example__() {
        APIGetVmBootOrderMsg msg = new APIGetVmBootOrderMsg();
        msg.uuid = uuid();
        return msg;
    }

}
