package org.zstack.header.storage.primary;

import org.zstack.header.message.APIReply;
import org.zstack.header.rest.RestResponse;

import java.util.ArrayList;
import java.util.List;


@RestResponse(allTo = "all")
public class APIGetVmInstanceMetadataFromPrimaryStorageReply extends APIReply {
    private List<String> vmInstanceMetadata = new ArrayList<>();

    public List<String> getVmInstanceMetadata() {
        return vmInstanceMetadata;
    }

    public void setVmInstanceMetadata(List<String> vmInstanceMetadata) {
        this.vmInstanceMetadata = vmInstanceMetadata;
    }

    public static APIGetVmInstanceMetadataFromPrimaryStorageReply __example__() {
        APIGetVmInstanceMetadataFromPrimaryStorageReply reply = new APIGetVmInstanceMetadataFromPrimaryStorageReply();
        return reply;
    }
}