package org.zstack.header.volume;

import org.zstack.header.core.Completion;

public interface VolumeBeforeFinalDeleteExtensionPoint {
    void volumeBeforeFinalDelete(VolumeInventory volume, boolean bestEffort, Completion completion);
}
