package org.zstack.header.host;

import org.zstack.header.message.NeedReplyMessage;
import org.zstack.header.volume.VolumeInventory;

import java.util.ArrayList;
import java.util.List;

public class DeleteVolumeSnapshotSelfOnHypervisorMsg extends NeedReplyMessage implements HostMessage {
    private String hostUuid;
    private String vmUuid;
    private VolumeInventory volume;
    private String srcSnapshotPath;
    private String dstSnapshotPath;
    private boolean deleteByBlockCommit;
    private long requiredExtraSize;
    private List<String> aliveChainInstallPathInDb = new ArrayList<>();
    private List<String> srcChildrenInstallPathInDb = new ArrayList<>();
    private List<String> snapshotChainFromSrcToDst = new ArrayList<>();

    public VolumeInventory getVolume() {
        return volume;
    }

    public void setVolume(VolumeInventory volume) {
        this.volume = volume;
    }

    @Override
    public String getHostUuid() {
        return hostUuid;
    }

    public void setHostUuid(String hostUuid) {
        this.hostUuid = hostUuid;
    }

    public String getVmUuid() {
        return vmUuid;
    }

    public void setVmUuid(String vmUuid) {
        this.vmUuid = vmUuid;
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

    public boolean isDeleteByBlockCommit() {
        return deleteByBlockCommit;
    }

    public void setDeleteByBlockCommit(boolean deleteByBlockCommit) {
        this.deleteByBlockCommit = deleteByBlockCommit;
    }

    public long getRequiredExtraSize() {
        return requiredExtraSize;
    }

    public void setRequiredExtraSize(long requiredExtraSize) {
        this.requiredExtraSize = requiredExtraSize;
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
