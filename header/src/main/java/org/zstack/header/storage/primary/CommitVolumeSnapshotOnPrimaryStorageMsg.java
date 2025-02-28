package org.zstack.header.storage.primary;

import org.zstack.header.message.NeedReplyMessage;
import org.zstack.header.storage.snapshot.VolumeSnapshotInventory;
import org.zstack.header.volume.VolumeInventory;

import java.util.ArrayList;
import java.util.List;

public class CommitVolumeSnapshotOnPrimaryStorageMsg extends NeedReplyMessage implements PrimaryStorageMessage {
    private VolumeInventory volume;
    private VolumeSnapshotInventory srcSnapshot;
    private VolumeSnapshotInventory dstSnapshot;
    List<String> srcChildrenInstallPathInDb = new ArrayList<>();
    private boolean online;

    public VolumeInventory getVolume() {
        return volume;
    }

    public void setVolume(VolumeInventory volume) {
        this.volume = volume;
    }

    public VolumeSnapshotInventory getSrcSnapshot() {
        return srcSnapshot;
    }

    public void setSrcSnapshot(VolumeSnapshotInventory srcSnapshot) {
        this.srcSnapshot = srcSnapshot;
    }

    public VolumeSnapshotInventory getDstSnapshot() {
        return dstSnapshot;
    }

    public void setDstSnapshot(VolumeSnapshotInventory dstSnapshot) {
        this.dstSnapshot = dstSnapshot;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    public List<String> getSrcChildrenInstallPathInDb() {
        return srcChildrenInstallPathInDb;
    }

    public void setSrcChildrenInstallPathInDb(List<String> srcChildrenInstallPathInDb) {
        this.srcChildrenInstallPathInDb = srcChildrenInstallPathInDb;
    }

    @Override
    public String getPrimaryStorageUuid() {
        return srcSnapshot.getPrimaryStorageUuid();
    }
}
