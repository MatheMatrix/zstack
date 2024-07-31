package org.zstack.header.storage.snapshot;

import org.zstack.header.message.MessageReply;

/**
 * @author Xingwei Yu
 * @date 2024/7/31 17:38
 */
public class SyncVolumeSnapshotSizeOnPrimaryStorageReply extends MessageReply {
    private long usedSize;

    public long getUsedSize() {
        return usedSize;
    }

    public void setUsedSize(long usedSize) {
        this.usedSize = usedSize;
    }
}
