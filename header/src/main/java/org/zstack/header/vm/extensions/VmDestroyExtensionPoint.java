package org.zstack.header.vm.extensions;

import org.zstack.header.core.Completion;

/**
 * VM Instance Destroy Extension Point (unified)
 *
 * <p>Trigger: When user calls DestroyVmInstance API</p>
 * <p>Call site: VmInstanceBase via VmInstanceExtensionPointEmitter</p>
 *
 * <h3>Phase Description:</h3>
 * <ul>
 *   <li>preDestroy - Can reject the destroy operation</li>
 *   <li>beforeDestroy - Preparation before destroy</li>
 *   <li>afterDestroy - Post-processing after destroy</li>
 *   <li>failedToDestroy - Cleanup on failure</li>
 * </ul>
 */
public interface VmDestroyExtensionPoint {
    default String preDestroy(VmDestroyContext ctx) { return null; }
    default void beforeDestroy(VmDestroyContext ctx) {}
    default void afterDestroy(VmDestroyContext ctx, Completion completion) { completion.success(); }
    default void failedToDestroy(VmDestroyContext ctx) {}
}
