package org.zstack.header.vm.metadata;

import org.springframework.http.HttpMethod;
import org.zstack.header.message.APIParam;
import org.zstack.header.message.APISyncCallMessage;
import org.zstack.header.rest.RestRequest;
import org.zstack.header.vm.VmInstanceMessage;
import org.zstack.header.vm.VmInstanceVO;

@RestRequest(
        path = "/primary-storage/vm-instances/metadata",
        method = HttpMethod.GET,
        responseClass = APIGetVmInstanceMetadataFromPrimaryStorageReply.class
)
public class APIGetVmInstanceMetadataFromPrimaryStorageMsg extends APISyncCallMessage implements VmInstanceMessage {
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

    public static APIGetVmInstanceMetadataFromPrimaryStorageMsg __example__() {
        APIGetVmInstanceMetadataFromPrimaryStorageMsg msg = new APIGetVmInstanceMetadataFromPrimaryStorageMsg();
        msg.setUuid(uuid(VmInstanceVO.class));
        return msg;
    }
}
