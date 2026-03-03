package org.zstack.header.vm;

import org.zstack.header.network.l3.L3NetworkInventory;

/**
 * @deprecated Use {@link org.zstack.header.vm.extensions.VmInstanceAttachL3NetworkExtensionPoint#beforeAttachL3Network} instead.
 */
@Deprecated
public interface VmBeforeAttachL3NetworkExtensionPoint {
    void vmBeforeAttachL3Network(VmInstanceInventory vm, L3NetworkInventory l3);
}
