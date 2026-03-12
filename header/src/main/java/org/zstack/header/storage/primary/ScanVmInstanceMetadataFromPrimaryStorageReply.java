package org.zstack.header.storage.primary;

import org.zstack.header.message.MessageReply;

import java.util.ArrayList;
import java.util.List;

public class ScanVmInstanceMetadataFromPrimaryStorageReply extends MessageReply {
    private List<VmInstanceMetadataSummary> vmInstanceMetadata = new ArrayList<>();

    public List<VmInstanceMetadataSummary> getVmInstanceMetadata() {
        return vmInstanceMetadata;
    }

    public void setVmInstanceMetadata(List<VmInstanceMetadataSummary> vmInstanceMetadata) {
        this.vmInstanceMetadata = vmInstanceMetadata;
    }
}
