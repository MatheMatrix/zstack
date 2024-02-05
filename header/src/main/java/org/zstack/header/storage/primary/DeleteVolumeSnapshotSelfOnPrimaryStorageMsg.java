package org.zstack.header.storage.primary;

import org.zstack.header.message.NeedReplyMessage;
import org.zstack.header.volume.VolumeInventory;

import java.util.ArrayList;
import java.util.List;

/**
 * @ Author : yh.w
 * @ Date   : Created in 11:47 2023/8/21
 */
public class DeleteVolumeSnapshotSelfOnPrimaryStorageMsg extends NeedReplyMessage implements PrimaryStorageMessage {
    private String vmUuid;
    private VolumeInventory volume;
    private String srcSnapshotPath;
    private String dstSnapshotPath;
    private boolean deleteByBlockCommit;
    private long srcSnapshotSize;
    private long dstSnapshotSize;
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

    @Override
    public String getPrimaryStorageUuid() {
        return volume.getPrimaryStorageUuid();
    }

    public boolean isDeleteByBlockCommit() {
        return deleteByBlockCommit;
    }

    public void setDeleteByBlockCommit(boolean deleteByBlockCommit) {
        this.deleteByBlockCommit = deleteByBlockCommit;
    }

    public long getSrcSnapshotSize() {
        return srcSnapshotSize;
    }

    public void setSrcSnapshotSize(long srcSnapshotSize) {
        this.srcSnapshotSize = srcSnapshotSize;
    }

    public long getDstSnapshotSize() {
        return dstSnapshotSize;
    }

    public void setDstSnapshotSize(long dstSnapshotSize) {
        this.dstSnapshotSize = dstSnapshotSize;
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
