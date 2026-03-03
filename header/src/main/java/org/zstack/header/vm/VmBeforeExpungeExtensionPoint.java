package org.zstack.header.vm;


/**
 * @deprecated Use {@link org.zstack.header.vm.extensions.VmExpungeExtensionPoint#beforeExpunge} instead.
 */
@Deprecated
public interface VmBeforeExpungeExtensionPoint {
    void vmBeforeExpunge(VmInstanceInventory inv);
}
