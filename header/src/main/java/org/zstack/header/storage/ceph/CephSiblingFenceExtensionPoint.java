package org.zstack.header.storage.ceph;

import org.zstack.header.core.ReturnValueCompletion;

/**
 * SPI for Ceph HA sibling-fence (ZSTAC-83890).
 * Implementation lives in premium {@code storage-ha-plugin}.
 * Called from {@code CephPrimaryStorageFactory.preInstantiateVmResource} when
 * Ceph watcher list is empty — SSH-kills stale QEMU on the failed host before
 * HA starts the VM to prevent split-brain.
 */
public interface CephSiblingFenceExtensionPoint {
    void fenceVmOnFailedHost(String failedHostUuid,
                             String vmUuid,
                             String clusterUuid,
                             String haTargetHostUuid,
                             ReturnValueCompletion<SiblingFenceVmOnHostReply> completion);
}
