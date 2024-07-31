package org.zstack.header.storage.snapshot;

import org.zstack.header.message.NeedReplyMessage;
import org.zstack.header.storage.primary.PrimaryStorageMessage;

/**
 * @author Xingwei Yu
 * @date 2024/7/31 17:36
 */
public class SyncVolumeSnapshotSizeOnPrimaryStorageMsg extends NeedReplyMessage implements PrimaryStorageMessage {
    private String volumeSnapshotUuid;
    private String primaryStorageUuid;
    private String installPath;

    public String getVolumeSnapshotUuid() {
        return volumeSnapshotUuid;
    }

    public void setVolumeSnapshotUuid(String volumeSnapshotUuid) {
        this.volumeSnapshotUuid = volumeSnapshotUuid;
    }

    @Override
    public String getPrimaryStorageUuid() {
        return primaryStorageUuid;
    }

    public void setPrimaryStorageUuid(String primaryStorageUuid) {
        this.primaryStorageUuid = primaryStorageUuid;
    }

    public String getInstallPath() {
        return installPath;
    }

    public void setInstallPath(String installPath) {
        this.installPath = installPath;
    }
}
