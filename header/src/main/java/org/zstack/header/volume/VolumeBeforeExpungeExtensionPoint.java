package org.zstack.header.volume;

import org.zstack.header.core.Completion;
import org.zstack.header.core.NoErrorCompletion;

/**
 * @deprecated Use {@link org.zstack.header.volume.extensions.VolumeExpungeExtPoint} instead.
 */
@Deprecated
public interface VolumeBeforeExpungeExtensionPoint {
    void volumePreExpunge(VolumeInventory volume);
    void volumeBeforeExpunge(VolumeInventory volume, NoErrorCompletion completion);

    default boolean skipExpungeVolume(VolumeInventory volume) {
        return false;
    }
}
