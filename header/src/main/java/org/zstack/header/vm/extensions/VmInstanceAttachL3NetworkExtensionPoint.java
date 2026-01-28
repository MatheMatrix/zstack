package org.zstack.header.vm.extensions;

import org.zstack.header.core.Completion;

/**
 * VM L3 Network Attachment Extension Point
 *
 * <p>Trigger: When user calls AttachL3NetworkToVm API</p>
 * <p>Call site: VmInstanceBase.attachL3Network()</p>
 *
 * <h3>Phase Description:</h3>
 * <ul>
 *   <li>preAttachL3Network - Before NIC creation, can reject the attachment</li>
 *   <li>beforeAttachL3Network - NIC prepared, about to execute FlowChain</li>
 *   <li>afterAttachL3Network - Attachment complete, network services configured</li>
 *   <li>failedToAttachL3Network - Attachment failed, cleanup resources</li>
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
public interface VmInstanceAttachL3NetworkExtensionPoint {
    /**
     * Called before NIC creation. Can reject the attachment by returning an error message.
     *
     * @param ctx the attachment context
     * @return error message if rejected, null if passed
     */
    default String preAttachL3Network(VmAttachL3NetworkContext ctx) {
        return null;
    }

    /**
     * Called after NIC is prepared, before executing the attachment FlowChain.
     *
     * @param ctx the attachment context
     */
    default void beforeAttachL3Network(VmAttachL3NetworkContext ctx) {
    }

    /**
     * Called after attachment is complete. Asynchronous for post-processing.
     *
     * @param ctx the attachment context (nic field is populated)
     * @param completion callback to signal completion
     */
    default void afterAttachL3Network(VmAttachL3NetworkContext ctx, Completion completion) {
        completion.success();
    }

    /**
     * Called when attachment fails. Cleanup any resources allocated in previous phases.
     *
     * @param ctx the attachment context
     */
    default void failedToAttachL3Network(VmAttachL3NetworkContext ctx) {
    }
}
