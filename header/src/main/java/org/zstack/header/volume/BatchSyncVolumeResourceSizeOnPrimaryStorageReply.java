package org.zstack.header.volume;

import org.zstack.header.message.MessageReply;

import java.util.HashMap;
import java.util.Map;

public class BatchSyncVolumeResourceSizeOnPrimaryStorageReply extends MessageReply {
    private Map<String, Long> volumeActualSizes = new HashMap<>();

    private Map<String, Long> snapshotActualSizes = new HashMap<>();

    public void setVolumeActualSizes(Map<String, Long> actualSizes) {
        this.volumeActualSizes = actualSizes;
    }

    public Map<String, Long> getVolumeActualSizes() {
        return volumeActualSizes;
    }

    public Map<String, Long> getSnapshotActualSizes() {
        return snapshotActualSizes;
    }

    public void setSnapshotActualSizes(Map<String, Long> snapshotActualSizes) {
        this.snapshotActualSizes = snapshotActualSizes;
    }
}
