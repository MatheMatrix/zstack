package org.zstack.header.vm;

import org.springframework.http.HttpMethod;
import org.zstack.header.message.APIMessage;
import org.zstack.header.message.APIParam;
import org.zstack.header.rest.RestRequest;
import org.zstack.header.vm.MetadataImpact;

/**
 * Created by root on 11/2/16.
 */
@RestRequest(
        path = "/vm-instances/{uuid}/actions",
        isAction = true,
        method = HttpMethod.PUT,
        responseClass = APIResumeVmInstanceEvent.class
)
@MetadataImpact(MetadataImpact.Impact.NONE)
public class APIResumeVmInstanceMsg extends APIMessage implements VmInstanceMessage {
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
 
    public static APIResumeVmInstanceMsg __example__() {
        APIResumeVmInstanceMsg msg = new APIResumeVmInstanceMsg();
        msg.uuid = uuid();
        return msg;
    }
}
