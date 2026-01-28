package org.zstack.header.vm;

import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.network.l3.L3NetworkInventory;

/**
 * @deprecated This interface has no implementations and should be removed.
 *             Use {@link org.zstack.header.vm.extensions.VmInstanceDetachNicExtensionPoint} instead.
 */
@Deprecated
public interface DetachNicExtensionPoint {
    ErrorCode validateDetachNicByDriverTypeAndClusterType(L3NetworkInventory l3, VmInstanceInventory vm);
}
