package org.zstack.header.vm;


/**
 * @deprecated Use {@link org.zstack.header.vm.extensions.VmExpungeExtensionPoint#afterExpunge} instead.
 */
@Deprecated
public interface VmAfterExpungeExtensionPoint {
    void vmAfterExpunge(VmInstanceInventory inv);
}
