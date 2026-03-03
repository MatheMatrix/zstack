package org.zstack.header.volume.extensions;

import org.zstack.header.core.Completion;

/**
 * Volume Expunge Extension Point (unified)
 *
 * <p>Trigger: When volume is expunged (after delete + grace period)</p>
 * <p>Call site: VolumeBase.expunge()</p>
 *
 * <h3>Phase Description:</h3>
 * <ul>
 *   <li>preExpunge - Can reject the expunge (or skip it)</li>
 *   <li>beforeExpunge - Preparation before expunge</li>
 *   <li>afterExpunge - Post-processing after expunge</li>
 *   <li>failedToExpunge - Cleanup on failure</li>
 * </ul>
 */
public interface VolumeExpungeExtPoint {
    default String preExpunge(VolumeExpungeContext ctx) { return null; }
    default void beforeExpunge(VolumeExpungeContext ctx) {}
    default void afterExpunge(VolumeExpungeContext ctx, Completion completion) { completion.success(); }
    default void failedToExpunge(VolumeExpungeContext ctx) {}
}
