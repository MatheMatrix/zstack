package org.zstack.header.storage.primary;

import org.zstack.header.host.HostMessage;
import org.zstack.header.message.NeedReplyMessage;

public class ReadVmInstanceMetadataOnHypervisorMsg extends NeedReplyMessage implements HostMessage {
    private String hostUuid;
    private String metadataPath;

    public void setHostUuid(String hostUuid) {
        this.hostUuid = hostUuid;
    }

    public String getMetadataPath() {
        return metadataPath;
    }

    public void setMetadataPath(String metadataPath) {
        this.metadataPath = metadataPath;
    }

    @Override
    public String getHostUuid() {
        return hostUuid;
    }
}
