package org.zstack.header.host;

import org.zstack.header.message.NeedReplyMessage;
import org.zstack.header.volume.VolumeInventory;

import java.util.ArrayList;
import java.util.List;

public class CommitVolumeSnapshotOnHypervisorMsg extends NeedReplyMessage implements HostMessage {
    private String hostUuid;
    private VolumeInventory volume;
    private String srcPath;
    private String dstPath;
    List<String> srcChildrenInstallPathInDb = new ArrayList<>();

    @Override
    public String getHostUuid() {
        return hostUuid;
    }

    public void setHostUuid(String hostUuid) {
        this.hostUuid = hostUuid;
    }

    public VolumeInventory getVolume() {
        return volume;
    }

    public void setVolume(VolumeInventory volume) {
        this.volume = volume;
    }


    public String getSrcPath() {
        return srcPath;
    }

    public void setSrcPath(String srcPath) {
        this.srcPath = srcPath;
    }

    public String getDstPath() {
        return dstPath;
    }

    public void setDstPath(String dstPath) {
        this.dstPath = dstPath;
    }

    public List<String> getSrcChildrenInstallPathInDb() {
        return srcChildrenInstallPathInDb;
    }

    public void setSrcChildrenInstallPathInDb(List<String> srcChildrenInstallPathInDb) {
        this.srcChildrenInstallPathInDb = srcChildrenInstallPathInDb;
    }
}
