package org.zstack.storage.volume;

import org.zstack.header.volume.VolumeDeletionPolicyManager;
import org.zstack.header.volume.VolumeInventory;
import org.zstack.header.volume.VolumeVO;

public interface ChangeVolumeProcessingMethodExtensionPoint {
    VolumeDeletionPolicyManager.VolumeDeletionPolicy getTransientVolumeDeletionPolicy(VolumeInventory volumeInventory);
}
