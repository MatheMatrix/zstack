package org.zstack.storage.snapshot;

import org.zstack.header.storage.snapshot.VolumeSnapshotInventory;

public interface MarkSnapshotAsVolumeExtension {
     void afterMarkSnapshotAsVolume(VolumeSnapshotInventory snapshot);
}
