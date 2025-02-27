package org.zstack.header.host;

import org.zstack.header.message.NeedReplyMessage;
import org.zstack.header.volume.VolumeInventory;

public class PullVolumeSnapshotOnHypervisorMsg extends NeedReplyMessage implements HostMessage {
    VolumeInventory volume;
    private String basePath;
    private String hostUuid;

    public VolumeInventory getVolume() {
        return volume;
    }

    public void setVolume(VolumeInventory volume) {
        this.volume = volume;
    }

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    @Override
    public String getHostUuid() {
        return hostUuid;
    }

    public void setHostUuid(String hostUuid) {
        this.hostUuid = hostUuid;
    }
}
