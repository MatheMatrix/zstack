package org.zstack.header.storage.snapshot;

import org.zstack.header.storage.addon.StorageResource;

public class VolumeSnapshotStats extends StorageResource {
    public long getActualSize() {
        return actualSize == null ? 0L : actualSize;
    }
}
