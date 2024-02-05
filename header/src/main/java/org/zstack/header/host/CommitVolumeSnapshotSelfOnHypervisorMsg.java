package org.zstack.header.host;

import org.zstack.header.message.NeedReplyMessage;
import org.zstack.header.volume.VolumeInventory;

import java.util.ArrayList;
import java.util.List;

public class CommitVolumeSnapshotSelfOnHypervisorMsg extends NeedReplyMessage implements HostMessage {
    private String hostUuid;
    private VolumeInventory volume;
    private String srcSnapshotPath;
    private String dstSnapshotPath;
    private List<String> aliveChainInstallPathInDb = new ArrayList<>();
    private List<String> srcChildrenInstallPathInDb = new ArrayList<>();
    private List<String> snapshotChainFromSrcToDst = new ArrayList<>();

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


    public String getSrcSnapshotPath() {
        return srcSnapshotPath;
    }

    public void setSrcSnapshotPath(String srcSnapshotPath) {
        this.srcSnapshotPath = srcSnapshotPath;
    }

    public String getDstSnapshotPath() {
        return dstSnapshotPath;
    }

    public void setDstSnapshotPath(String dstSnapshotPath) {
        this.dstSnapshotPath = dstSnapshotPath;
    }

    public List<String> getAliveChainInstallPathInDb() {
        return aliveChainInstallPathInDb;
    }

    public void setAliveChainInstallPathInDb(List<String> aliveChainInstallPathInDb) {
        this.aliveChainInstallPathInDb = aliveChainInstallPathInDb;
    }

    public List<String> getSrcChildrenInstallPathInDb() {
        return srcChildrenInstallPathInDb;
    }

    public void setSrcChildrenInstallPathInDb(List<String> srcChildrenInstallPathInDb) {
        this.srcChildrenInstallPathInDb = srcChildrenInstallPathInDb;
    }

    public List<String> getSnapshotChainFromSrcToDst() {
        return snapshotChainFromSrcToDst;
    }

    public void setSnapshotChainFromSrcToDst(List<String> snapshotChainFromSrcToDst) {
        this.snapshotChainFromSrcToDst = snapshotChainFromSrcToDst;
    }
}
