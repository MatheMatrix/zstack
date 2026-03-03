package org.zstack.header.vm.extensions;

import org.zstack.header.core.Completion;

/**
 * VM Instance Expunge Extension Point (unified)
 *
 * <p>Trigger: When VM is expunged (after destroy + grace period)</p>
 * <p>Call site: VmInstanceBase.expunge()</p>
 *
 * <h3>Phase Description:</h3>
 * <ul>
 *   <li>preExpunge - Can reject the expunge operation</li>
 *   <li>beforeExpunge - Preparation before expunge</li>
 *   <li>afterExpunge - Post-processing after expunge</li>
 *   <li>failedToExpunge - Cleanup on failure</li>
 * </ul>
 */
public interface VmExpungeExtensionPoint {
    default String preExpunge(VmExpungeContext ctx) { return null; }
    default void beforeExpunge(VmExpungeContext ctx) {}
    default void afterExpunge(VmExpungeContext ctx, Completion completion) { completion.success(); }
    default void failedToExpunge(VmExpungeContext ctx) {}
}
