package org.zstack.header.vm;

import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.network.l3.L3NetworkInventory;

/**
 * @deprecated Use {@link org.zstack.header.vm.extensions.VmInstanceAttachL3NetworkExtensionPoint#failedToAttachL3Network} instead.
 */
@Deprecated
public interface VmFailToAttachL3NetworkExtensionPoint {
    void vmFailToAttachL3Network(VmInstanceInventory vm, L3NetworkInventory l3, ErrorCode error);
}
