package org.zstack.header.vm;

import org.springframework.http.HttpMethod;
import org.zstack.header.message.APIParam;
import org.zstack.header.message.APISyncCallMessage;
import org.zstack.header.rest.RestRequest;
import org.zstack.header.storage.primary.PrimaryStorageVO;
import org.zstack.header.vm.MetadataImpact;

@RestRequest(
        path = "/vm-instances/{vmUuid}/metadata",
        method = HttpMethod.GET,
        responseClass = APIReadVmInstanceMetadataReply.class
)
@MetadataImpact(MetadataImpact.Impact.NONE)
public class APIReadVmInstanceMetadataMsg extends APISyncCallMessage implements VmInstanceMessage {
    @APIParam(resourceType = VmInstanceVO.class)
    private String vmUuid;

    @APIParam(resourceType = PrimaryStorageVO.class)
    private String primaryStorageUuid;

    public String getVmUuid() {
        return vmUuid;
    }

    public void setVmUuid(String vmUuid) {
        this.vmUuid = vmUuid;
    }

    public String getPrimaryStorageUuid() {
        return primaryStorageUuid;
    }

    public void setPrimaryStorageUuid(String primaryStorageUuid) {
        this.primaryStorageUuid = primaryStorageUuid;
    }

    @Override
    public String getVmInstanceUuid() {
        return vmUuid;
    }

    public static APIReadVmInstanceMetadataMsg __example__() {
        APIReadVmInstanceMetadataMsg msg = new APIReadVmInstanceMetadataMsg();
        msg.vmUuid = uuid();
        msg.primaryStorageUuid = uuid();
        return msg;
    }
}
