package org.zstack.header.storage.ceph;

import org.zstack.header.core.ReturnValueCompletion;

public interface CephSiblingFenceExtensionPoint {
    void fenceVmOnFailedHost(String failedHostUuid,
                             String vmUuid,
                             String clusterUuid,
                             String haTargetHostUuid,
                             ReturnValueCompletion<SiblingFenceVmOnHostReply> completion);
}
