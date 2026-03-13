package org.zstack.header.vm;

import org.zstack.header.core.Completion;

/**
 * Extension point called before a VmNic is released in VmReturnReleaseNicFlow / VmDetachNicFlow.
 * Implementations can perform cleanup (e.g., deleting SDN segment ports).
 */
public interface BeforeReleaseVmNicExtensionPoint {
    void beforeReleaseVmNic(VmNicInventory nic, Completion completion);
}
