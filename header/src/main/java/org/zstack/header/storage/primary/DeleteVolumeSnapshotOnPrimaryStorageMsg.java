package org.zstack.header.storage.primary;

import org.zstack.header.message.NeedReplyMessage;
import org.zstack.header.volume.VolumeInventory;

import java.util.ArrayList;
import java.util.List;

/**
 * @ Author : yh.w
 * @ Date   : Created in 11:47 2023/8/21
 */
public class DeleteVolumeSnapshotOnPrimaryStorageMsg extends NeedReplyMessage implements PrimaryStorageMessage {
    private VolumeInventory volume;
    private String srcPath;
    private String dstPath;
    private String direction;
    private boolean online;
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

    @Override
    public String getPrimaryStorageUuid() {
        return volume.getPrimaryStorageUuid();
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
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
