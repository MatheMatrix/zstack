package org.zstack.header.storage.ceph;

import org.zstack.header.message.NeedReplyMessage;

public class SiblingFenceVmOnHostMsg extends NeedReplyMessage {
    private String failedHostUuid;
    private String vmUuid;
    private String clusterUuid;
    private String haTargetHostUuid;

    public String getFailedHostUuid() {
        return failedHostUuid;
    }

    public void setFailedHostUuid(String failedHostUuid) {
        this.failedHostUuid = failedHostUuid;
    }

    public String getVmUuid() {
        return vmUuid;
    }

    public void setVmUuid(String vmUuid) {
        this.vmUuid = vmUuid;
    }

    public String getClusterUuid() {
        return clusterUuid;
    }

    public void setClusterUuid(String clusterUuid) {
        this.clusterUuid = clusterUuid;
    }

    public String getHaTargetHostUuid() {
        return haTargetHostUuid;
    }

    public void setHaTargetHostUuid(String haTargetHostUuid) {
        this.haTargetHostUuid = haTargetHostUuid;
    }
}
