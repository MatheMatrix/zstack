package org.zstack.header.vm;

import org.zstack.header.core.Completion;
import org.zstack.header.core.NoErrorCompletion;

/**
 * @deprecated Use {@link org.zstack.header.vm.extensions.VmInstanceAttachL3NetworkExtensionPoint#afterAttachL3Network} instead.
 */
@Deprecated
public interface VmAfterAttachNicExtensionPoint {
    void afterAttachNic(String nicUuid, VmInstanceInventory vmInstanceInventory, Completion completion);

    void afterAttachNicRollback(String nicUuid, VmInstanceInventory vmInstanceInventory, NoErrorCompletion completion);
}
