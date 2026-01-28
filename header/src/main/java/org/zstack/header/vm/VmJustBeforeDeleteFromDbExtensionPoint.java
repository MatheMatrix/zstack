package org.zstack.header.vm;

/**
 * @deprecated Use {@link org.zstack.header.vm.extensions.VmDbDeleteContext} with a single-phase extension point instead.
 */
@Deprecated
public interface VmJustBeforeDeleteFromDbExtensionPoint {
    void vmJustBeforeDeleteFromDb(VmInstanceInventory inv);
}
