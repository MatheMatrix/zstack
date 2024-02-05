package org.zstack.header.storage.primary;

import org.zstack.header.message.NeedReplyMessage;
import org.zstack.header.storage.snapshot.VolumeSnapshotInventory;
import org.zstack.header.volume.VolumeInventory;

import java.util.List;

/**
 * @ Author : yh.w
 * @ Date   : Created in 11:47 2023/8/21
 */
public class DeleteVolumeSnapshotSelfOnPrimaryStorageMsg extends NeedReplyMessage implements PrimaryStorageMessage {
    private String vmUuid;
    private VolumeInventory volume;
    private String srcPath;
    private String dstPath;
    private String primaryStorageUuid;
    private VolumeSnapshotInventory snapshot;
    private long requiredExtraSize;
    private List<String> aliveChainInstallPathInDb;
    private List<String> srcChildrenInstallPathInDb;
    private List<String> srcAncestorsInstallPathInDb;

    public VolumeSnapshotInventory getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(VolumeSnapshotInventory snapshot) {
        this.snapshot = snapshot;
    }

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

    public void setPrimaryStorageUuid(String primaryStorageUuid) {
        this.primaryStorageUuid = primaryStorageUuid;
    }

    @Override
    public String getPrimaryStorageUuid() {
        return primaryStorageUuid;
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

    public List<String> getSrcAncestorsInstallPathInDb() {
        return srcAncestorsInstallPathInDb;
    }

    public void setSrcAncestorsInstallPathInDb(List<String> srcAncestorsInstallPathInDb) {
        this.srcAncestorsInstallPathInDb = srcAncestorsInstallPathInDb;
    }
}
