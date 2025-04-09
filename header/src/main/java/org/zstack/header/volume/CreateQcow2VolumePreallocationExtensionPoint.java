package org.zstack.header.volume;

public interface CreateQcow2VolumePreallocationExtensionPoint {
    void saveQcow2VolumePreallocation(VolumeInventory volume, boolean hasBackingFile);
}
