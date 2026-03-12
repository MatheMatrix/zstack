package org.zstack.header.vm;

import org.springframework.http.HttpMethod;
import org.zstack.header.message.APIMessage;
import org.zstack.header.message.APIParam;
import org.zstack.header.rest.RestRequest;
import org.zstack.header.vm.MetadataImpact;

/**
 * Created by root on 10/29/16.
 */
@RestRequest(
        path = "/vm-instances/{uuid}/actions",
        isAction = true,
        method = HttpMethod.PUT,
        responseClass = APIPauseVmInstanceEvent.class
)
@SkipVmTracer(replyClass = APIPauseVmInstanceEvent.class)
@MetadataImpact(MetadataImpact.Impact.NONE)
public class APIPauseVmInstanceMsg extends APIMessage implements VmInstanceMessage {
    @APIParam(resourceType = VmInstanceVO.class)
    private String uuid;

    @Override
    public String getVmInstanceUuid() {
        return getUuid();
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

 
    public static APIPauseVmInstanceMsg __example__() {
        APIPauseVmInstanceMsg msg = new APIPauseVmInstanceMsg();
        msg.uuid = uuid();
        return msg;
    }
}
