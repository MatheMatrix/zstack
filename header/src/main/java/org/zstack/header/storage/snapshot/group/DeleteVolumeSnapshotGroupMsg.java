package org.zstack.header.storage.snapshot.group;

import org.zstack.header.message.APIDeleteMessage;
import org.zstack.header.message.NeedReplyMessage;
import org.zstack.header.storage.snapshot.SnapshotBackendOperation;

public class DeleteVolumeSnapshotGroupMsg extends NeedReplyMessage implements VolumeSnapshotGroupMessage {
    private String uuid;
    private String deletionMode;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    @Override
    public String getGroupUuid() {
        return uuid;
    }

    @Override
    public SnapshotBackendOperation getBackendOperation() {
        return SnapshotBackendOperation.FILE_DELETION;
    }

    public void setDeletionMode(APIDeleteMessage.DeletionMode deletionMode) {
        this.deletionMode = deletionMode.toString();
    }

    public APIDeleteMessage.DeletionMode getDeletionMode() {
        return APIDeleteMessage.DeletionMode.valueOf(deletionMode);
    }
}
