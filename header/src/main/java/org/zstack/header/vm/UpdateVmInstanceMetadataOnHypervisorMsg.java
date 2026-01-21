package org.zstack.header.vm;

import org.zstack.header.host.HostMessage;
import org.zstack.header.message.NeedReplyMessage;

public class UpdateVmInstanceMetadataOnHypervisorMsg extends NeedReplyMessage implements HostMessage {
    private String vmInstanceMetadata;
    private String hostUuid;

    public String getVmInstanceMetadata() {
        return vmInstanceMetadata;
    }

    public void setVmInstanceMetadata(String vmInstanceMetadata) {
        this.vmInstanceMetadata = vmInstanceMetadata;
    }

    @Override
    public String getHostUuid() {
        return hostUuid;
    }

    public void setHostUuid(String hostUuid) {
        this.hostUuid = hostUuid;
    }
}
