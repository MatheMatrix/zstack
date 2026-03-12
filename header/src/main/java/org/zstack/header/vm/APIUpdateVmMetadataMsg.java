package org.zstack.header.vm;

import org.springframework.http.HttpMethod;
import org.zstack.header.message.APIMessage;
import org.zstack.header.message.APIParam;
import org.zstack.header.rest.RestRequest;
import org.zstack.header.vm.MetadataImpact;

@RestRequest(
        path = "/vm-instances/{vmUuid}/metadata/actions",
        method = HttpMethod.PUT,
        responseClass = APIUpdateVmMetadataEvent.class,
        isAction = true
)
@MetadataImpact(MetadataImpact.Impact.NONE)
public class APIUpdateVmMetadataMsg extends APIMessage implements VmInstanceMessage {
    @APIParam(resourceType = VmInstanceVO.class)
    private String vmUuid;

    public String getVmUuid() {
        return vmUuid;
    }

    public void setVmUuid(String vmUuid) {
        this.vmUuid = vmUuid;
    }

    @Override
    public String getVmInstanceUuid() {
        return vmUuid;
    }

    public static APIUpdateVmMetadataMsg __example__() {
        APIUpdateVmMetadataMsg msg = new APIUpdateVmMetadataMsg();
        msg.vmUuid = uuid();
        return msg;
    }
}
