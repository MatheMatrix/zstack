package org.zstack.header.volume.extensions;

import org.zstack.header.core.Completion;

/**
 * Volume Deletion Extension Point (unified)
 *
 * <p>Trigger: When user calls DeleteDataVolume API</p>
 * <p>Call site: VolumeBase.delete()</p>
 *
 * <h3>Phase Description:</h3>
 * <ul>
 *   <li>preDelete - Can reject the deletion</li>
 *   <li>beforeDelete - Preparation before deletion</li>
 *   <li>afterDelete - Post-processing after deletion</li>
 *   <li>failedToDelete - Cleanup on failure</li>
 * </ul>
 */
public interface VolumeDeletionExtPoint {
    default String preDelete(VolumeDeleteContext ctx) { return null; }
    default void beforeDelete(VolumeDeleteContext ctx) {}
    default void afterDelete(VolumeDeleteContext ctx, Completion completion) { completion.success(); }
    default void failedToDelete(VolumeDeleteContext ctx) {}
}
