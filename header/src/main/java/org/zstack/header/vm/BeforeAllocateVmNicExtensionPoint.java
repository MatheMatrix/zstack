package org.zstack.header.vm;

import org.zstack.header.core.Completion;

/**
 * Extension point called before a VmNic is persisted to the database in VmAllocateNicFlow.
 * If the implementation fails, the NIC will NOT be saved to the database.
 * Use case: create SDN segment ports before cloud DB write, ensuring consistency.
 */
public interface BeforeAllocateVmNicExtensionPoint {
    void beforeAllocateVmNic(VmNicInventory nic, VmInstanceSpec spec, Completion completion);
}
