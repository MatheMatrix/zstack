package org.zstack.kvm;

import org.zstack.header.host.HostMessage;
import org.zstack.header.message.NeedReplyMessage;
import org.zstack.header.volume.VolumeInventory;

public class PullVolumeSnapshotSelfOnHypervisorMsg extends NeedReplyMessage implements HostMessage {
    VolumeInventory volume;
    private String base;
    private String hostUuid;

    public VolumeInventory getVolume() {
        return volume;
    }

    public void setVolume(VolumeInventory volume) {
        this.volume = volume;
    }

    public String getBase() {
        return base;
    }

    public void setBase(String base) {
        this.base = base;
    }

    @Override
    public String getHostUuid() {
        return hostUuid;
    }

    public void setHostUuid(String hostUuid) {
        this.hostUuid = hostUuid;
    }
}
