package org.zstack.header.vm;

import org.zstack.header.host.HostMessage;
import org.zstack.header.message.NeedReplyMessage;

public class FenceVmOnHostMsg extends NeedReplyMessage implements HostMessage {
    private String hostUuid;
    private String suspectHostUuid;
    private String vmUuid;

    @Override
    public String getHostUuid() {
        return hostUuid;
    }

    public void setHostUuid(String hostUuid) {
        this.hostUuid = hostUuid;
    }

    public String getSuspectHostUuid() {
        return suspectHostUuid;
    }

    public void setSuspectHostUuid(String suspectHostUuid) {
        this.suspectHostUuid = suspectHostUuid;
    }

    public String getVmUuid() {
        return vmUuid;
    }

    public void setVmUuid(String vmUuid) {
        this.vmUuid = vmUuid;
    }
}
