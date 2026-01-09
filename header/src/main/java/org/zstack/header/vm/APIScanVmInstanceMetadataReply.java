package org.zstack.header.vm;

import org.zstack.header.message.APIReply;
import org.zstack.header.rest.RestResponse;

import java.util.List;

@RestResponse(fieldsTo = {"all"})
public class APIScanVmInstanceMetadataReply extends APIReply {
    private List<VmMetadataScanResult> metadataList;

    public List<VmMetadataScanResult> getMetadataList() {
        return metadataList;
    }

    public void setMetadataList(List<VmMetadataScanResult> metadataList) {
        this.metadataList = metadataList;
    }

    public static APIScanVmInstanceMetadataReply __example__() {
        APIScanVmInstanceMetadataReply reply = new APIScanVmInstanceMetadataReply();
        VmMetadataScanResult result = new VmMetadataScanResult();
        result.setVmUuid(uuid());
        result.setVmName("test-vm");
        result.setVmCategory("UserVm");
        result.setPrimaryStorageUuid(uuid());
        result.setPrimaryStorageType("SharedBlock");
        result.setSchemaVersion("1.0");
        result.setLastUpdateTime(System.currentTimeMillis());
        result.setMetadataPath("/dev/zvmdata-xxxxx/vm-metadata-xxxxx");
        result.setSizeBytes(4096L);
        reply.metadataList = java.util.Collections.singletonList(result);
        return reply;
    }
}
