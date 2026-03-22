package org.zstack.header.vm;

import org.springframework.http.HttpMethod;
import org.zstack.header.message.APIDeleteMessage;
import org.zstack.header.message.APIParam;
import org.zstack.header.rest.RestRequest;
import org.zstack.header.vm.metadata.MetadataImpact;

@RestRequest(
        path = "/nics/{uuid}",
        method = HttpMethod.DELETE,
        responseClass = APIDeleteVmNicEvent.class
)
@MetadataImpact(value = MetadataImpact.Impact.CONFIG, resolver = "PreCaptureNicBasedVmUuidFromApiResolver")
public class APIDeleteVmNicMsg extends APIDeleteMessage {

    @APIParam(resourceType = VmNicVO.class, successIfResourceNotExisting = true)
    private String uuid;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public static APIDeleteVmNicMsg __example__() {
        APIDeleteVmNicMsg msg = new APIDeleteVmNicMsg();
        msg.setUuid(uuid());
        msg.setDeletionMode(DeletionMode.Permissive);

        return msg;
    }
}
