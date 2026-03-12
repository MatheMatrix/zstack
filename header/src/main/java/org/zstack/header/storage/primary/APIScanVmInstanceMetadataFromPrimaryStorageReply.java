package org.zstack.header.storage.primary;

import org.zstack.header.message.APIReply;
import org.zstack.header.rest.RestResponse;

import java.util.ArrayList;
import java.util.List;


@RestResponse(fieldsTo = {"all"})
public class APIScanVmInstanceMetadataFromPrimaryStorageReply extends APIReply {
    private List<VmInstanceMetadataSummary> vmInstanceMetadata = new ArrayList<>();

    public List<VmInstanceMetadataSummary> getVmInstanceMetadata() {
        return vmInstanceMetadata;
    }

    public void setVmInstanceMetadata(List<VmInstanceMetadataSummary> vmInstanceMetadata) {
        this.vmInstanceMetadata = vmInstanceMetadata;
    }

    public static APIScanVmInstanceMetadataFromPrimaryStorageReply __example__() {
        APIScanVmInstanceMetadataFromPrimaryStorageReply reply = new APIScanVmInstanceMetadataFromPrimaryStorageReply();
        return reply;
    }
}
