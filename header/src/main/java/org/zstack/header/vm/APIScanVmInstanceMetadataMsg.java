package org.zstack.header.vm;

import org.springframework.http.HttpMethod;
import org.zstack.header.message.APIParam;
import org.zstack.header.message.APISyncCallMessage;
import org.zstack.header.rest.RestRequest;
import org.zstack.header.storage.primary.PrimaryStorageVO;

import java.util.List;

@RestRequest(
        path = "/vm-instances/metadata/scan",
        method = HttpMethod.GET,
        responseClass = APIScanVmInstanceMetadataReply.class
)
public class APIScanVmInstanceMetadataMsg extends APISyncCallMessage {
    @APIParam(required = false, resourceType = PrimaryStorageVO.class)
    private List<String> primaryStorageUuids;

    @APIParam(required = false, resourceType = VmInstanceVO.class)
    private List<String> vmUuids;

    public List<String> getPrimaryStorageUuids() {
        return primaryStorageUuids;
    }

    public void setPrimaryStorageUuids(List<String> primaryStorageUuids) {
        this.primaryStorageUuids = primaryStorageUuids;
    }

    public List<String> getVmUuids() {
        return vmUuids;
    }

    public void setVmUuids(List<String> vmUuids) {
        this.vmUuids = vmUuids;
    }

    public static APIScanVmInstanceMetadataMsg __example__() {
        APIScanVmInstanceMetadataMsg msg = new APIScanVmInstanceMetadataMsg();
        msg.primaryStorageUuids = null;
        msg.vmUuids = null;
        return msg;
    }
}
