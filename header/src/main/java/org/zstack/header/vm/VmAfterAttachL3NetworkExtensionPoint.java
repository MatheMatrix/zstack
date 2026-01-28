package org.zstack.header.vm;

import org.zstack.header.network.l3.L3NetworkInventory;

/**
 * @deprecated Use {@link org.zstack.header.vm.extensions.VmInstanceAttachL3NetworkExtensionPoint#afterAttachL3Network} instead.
 */
@Deprecated
public interface VmAfterAttachL3NetworkExtensionPoint {
    void vmAfterAttachL3Network(VmInstanceInventory vm, L3NetworkInventory l3);
}
