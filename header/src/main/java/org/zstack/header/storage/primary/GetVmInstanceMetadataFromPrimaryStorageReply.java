package org.zstack.header.storage.primary;

import org.zstack.header.message.MessageReply;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GetVmInstanceMetadataFromPrimaryStorageReply extends MessageReply {
    private List<String> vmInstanceMetadata = new ArrayList<>();

    public List<String> getVmInstanceMetadata() {
        return vmInstanceMetadata;
    }

    public void setVmInstanceMetadata(List<String> vmInstanceMetadata) {
        this.vmInstanceMetadata = vmInstanceMetadata;
    }
}
