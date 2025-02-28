package org.zstack.kvm;

import org.zstack.header.core.Completion;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.host.PullVolumeSnapshotOnHypervisorMsg;
import org.zstack.header.host.PullVolumeSnapshotOnHypervisorReply;


public interface KVMBlockPullExtensionPoint {
    void beforePullVolume(KVMHostInventory host, PullVolumeSnapshotOnHypervisorMsg msg, Completion completion);

    void afterPullVolume(KVMHostInventory host, PullVolumeSnapshotOnHypervisorMsg msg, PullVolumeSnapshotOnHypervisorReply reply, Completion completion);

    void failedToPullVolume(KVMHostInventory host, PullVolumeSnapshotOnHypervisorMsg msg, KVMAgentCommands.BlockPullResponse rsp, ErrorCode err);
}
