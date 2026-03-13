package org.zstack.header.vm;

import org.zstack.header.core.Completion;

/**
 * Extension point called after a VmNic is allocated in VmAllocateNicFlow.
 * Implementations can perform additional setup (e.g., creating SDN segment ports).
 */
public interface AfterAllocateVmNicExtensionPoint {
    void afterAllocateVmNic(VmNicInventory nic, VmInstanceSpec spec, Completion completion);
}
