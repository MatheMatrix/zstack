package org.zstack.header.vm;

import org.springframework.http.HttpMethod;
import org.zstack.header.message.APIParam;
import org.zstack.header.message.APISyncCallMessage;
import org.zstack.header.rest.RestRequest;
import org.zstack.header.storage.primary.PrimaryStorageVO;

import java.util.List;

@RestRequest(
        path = "/vm-instances/metadata/consistency-check",
        method = HttpMethod.PUT,
        responseClass = APICheckVmInstanceMetadataConsistencyReply.class,
        isAction = true
)
public class APICheckVmInstanceMetadataConsistencyMsg extends APISyncCallMessage {
    @APIParam(required = false, resourceType = VmInstanceVO.class)
    private List<String> vmUuids;

    @APIParam(required = false, resourceType = PrimaryStorageVO.class)
    private String primaryStorageUuid;

    @APIParam(required = false)
    private Boolean autoRepair;

    public List<String> getVmUuids() {
        return vmUuids;
    }

    public void setVmUuids(List<String> vmUuids) {
        this.vmUuids = vmUuids;
    }

    public String getPrimaryStorageUuid() {
        return primaryStorageUuid;
    }

    public void setPrimaryStorageUuid(String primaryStorageUuid) {
        this.primaryStorageUuid = primaryStorageUuid;
    }

    public Boolean getAutoRepair() {
        return autoRepair;
    }

    public void setAutoRepair(Boolean autoRepair) {
        this.autoRepair = autoRepair;
    }

    public static APICheckVmInstanceMetadataConsistencyMsg __example__() {
        APICheckVmInstanceMetadataConsistencyMsg msg = new APICheckVmInstanceMetadataConsistencyMsg();
        msg.autoRepair = false;
        return msg;
    }
}
