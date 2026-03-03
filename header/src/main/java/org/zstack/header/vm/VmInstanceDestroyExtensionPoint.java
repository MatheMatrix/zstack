package org.zstack.header.vm;

import org.zstack.header.errorcode.ErrorCode;

/**
 * @deprecated Use {@link org.zstack.header.vm.extensions.VmDestroyExtensionPoint} instead.
 */
@Deprecated
public interface VmInstanceDestroyExtensionPoint {
    String preDestroyVm(VmInstanceInventory inv);

    void beforeDestroyVm(VmInstanceInventory inv);

    void afterDestroyVm(VmInstanceInventory inv);

    void failedToDestroyVm(VmInstanceInventory inv, ErrorCode reason);
}
