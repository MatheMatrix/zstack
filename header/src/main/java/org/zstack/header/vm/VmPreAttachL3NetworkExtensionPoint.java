package org.zstack.header.vm;

import org.zstack.header.network.l3.L3NetworkInventory;

/**
 * @deprecated Use {@link org.zstack.header.vm.extensions.VmInstanceAttachL3NetworkExtensionPoint#preAttachL3Network} instead.
 */
@Deprecated
public interface VmPreAttachL3NetworkExtensionPoint {
    void vmPreAttachL3Network(VmInstanceInventory vm, L3NetworkInventory l3);
}
