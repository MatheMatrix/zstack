package org.zstack.header.vm;

/**
 * @deprecated This interface has no implementations and should be removed.
 *             Use {@link org.zstack.header.vm.extensions.VmInstanceAttachL3NetworkExtensionPoint} instead.
 */
@Deprecated
public interface VmInstanceAttachNicExtensionPoint {
    void afterAttachNicToVm(VmNicInventory nic);
}
