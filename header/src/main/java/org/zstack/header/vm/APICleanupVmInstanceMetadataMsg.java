package org.zstack.header.vm;

import org.springframework.http.HttpMethod;
import org.zstack.header.message.APIMessage;
import org.zstack.header.message.APIParam;
import org.zstack.header.rest.RestRequest;
import org.zstack.header.storage.primary.PrimaryStorageVO;

import java.util.List;
import org.zstack.header.vm.MetadataImpact;

@RestRequest(
        path = "/vm-instances/metadata/cleanup",
        method = HttpMethod.PUT,
        responseClass = APICleanupVmInstanceMetadataEvent.class,
        isAction = true
)
@MetadataImpact(MetadataImpact.Impact.NONE)
public class APICleanupVmInstanceMetadataMsg extends APIMessage {
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

    public static APICleanupVmInstanceMetadataMsg __example__() {
        APICleanupVmInstanceMetadataMsg msg = new APICleanupVmInstanceMetadataMsg();
        return msg;
    }
}
