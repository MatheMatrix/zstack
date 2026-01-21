package org.zstack.header.vm;

import org.zstack.header.message.MessageReply;

public class ReadVmInstanceMetadataOnHypervisorReply extends MessageReply {
    private String vmMetadata;

    public String getVmMetadata() {
        return vmMetadata;
    }

    public void setVmMetadata(String vmMetadata) {
        this.vmMetadata = vmMetadata;
    }
}
