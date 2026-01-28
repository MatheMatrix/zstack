package org.zstack.header.vm;

import org.zstack.header.errorcode.ErrorCode;

/**
 * @deprecated Use {@link org.zstack.header.vm.extensions.VmInstanceDetachNicExtensionPoint} instead.
 */
@Deprecated
public interface VmDetachNicExtensionPoint {
    void preDetachNic(VmNicInventory nic);

    void beforeDetachNic(VmNicInventory nic);

    void afterDetachNic(VmNicInventory nic);

    void failedToDetachNic(VmNicInventory nic, ErrorCode error);
}
