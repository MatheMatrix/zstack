package org.zstack.header.vhost;

import org.zstack.header.volume.VolumeInventory;

public interface VhostBeforeStartVmOnKvmExtensionPoint {
    void beforeStartVmOnKvmWithVhost(VolumeInventory volume);
}
