package org.zstack.header.storage.snapshot.group;

import org.zstack.header.message.APIDeleteMessage;
import org.zstack.header.message.NeedReplyMessage;
import org.zstack.header.storage.snapshot.SnapshotBackendOperation;

import java.util.List;

public class BatchDeleteVolumeSnapshotGroupInnerMsg extends NeedReplyMessage {
    private List<VolumeSnapshotGroupInventory> volumeSnapshotGroupInventories;
    private String deletionMode;

    public List<VolumeSnapshotGroupInventory> getVolumeSnapshotGroupInventories() {
        return volumeSnapshotGroupInventories;
    }

    public void setVolumeSnapshotGroupInventories(List<VolumeSnapshotGroupInventory> volumeSnapshotGroupInventories) {
        this.volumeSnapshotGroupInventories = volumeSnapshotGroupInventories;
    }

    public void setDeletionMode(APIDeleteMessage.DeletionMode deletionMode) {
        this.deletionMode = deletionMode.toString();
    }

    public APIDeleteMessage.DeletionMode getDeletionMode() {
        return APIDeleteMessage.DeletionMode.valueOf(deletionMode);
    }
}
