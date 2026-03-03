package org.zstack.header.vm.extensions;

import org.zstack.header.core.Completion;

/**
 * VM NIC Detachment Extension Point
 *
 * <p>Trigger: When user calls DetachL3NetworkFromVm API</p>
 * <p>Call site: VmInstanceBase.detachNic()</p>
 *
 * <h3>Phase Description:</h3>
 * <ul>
 *   <li>preDetachNic - Before detachment, can reject the operation</li>
 *   <li>beforeDetachNic - About to execute detachment FlowChain</li>
 *   <li>afterDetachNic - Detachment complete, NIC removed</li>
 *   <li>failedToDetachNic - Detachment failed, restore state</li>
 * </ul>
 *
 * <h3>Method Signatures:</h3>
 * <ul>
 *   <li>pre: Returns error message String, null means pass</li>
 *   <li>before: Synchronous void, for preparation</li>
 *   <li>after: Asynchronous with Completion, for post-processing</li>
 *   <li>failedTo: Synchronous void, for cleanup</li>
 * </ul>
 */
public interface VmInstanceDetachNicExtensionPoint {
    /**
     * Called before NIC detachment. Can reject the operation by returning an error message.
     *
     * @param ctx the detachment context
     * @return error message if rejected, null if passed
     */
    default String preDetachNic(VmDetachNicContext ctx) {
        return null;
    }

    /**
     * Called before executing the detachment FlowChain.
     *
     * @param ctx the detachment context
     */
    default void beforeDetachNic(VmDetachNicContext ctx) {
    }

    /**
     * Called after detachment is complete. Asynchronous for post-processing.
     *
     * @param ctx the detachment context
     * @param completion callback to signal completion
     */
    default void afterDetachNic(VmDetachNicContext ctx, Completion completion) {
        completion.success();
    }

    /**
     * Called when detachment fails. Restore any state changed in previous phases.
     *
     * @param ctx the detachment context (error field is populated)
     */
    default void failedToDetachNic(VmDetachNicContext ctx) {
    }
}
