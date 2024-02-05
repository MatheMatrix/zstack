package org.zstack.kvm;

import org.zstack.header.core.Completion;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.host.PullVolumeSnapshotOnHypervisorMsg;
import org.zstack.header.host.PullVolumeSnapshotOnHypervisorReply;


public interface KVMBlockPullExtensionPoint {
    void beforePull(KVMHostInventory host, PullVolumeSnapshotOnHypervisorMsg msg, Completion completion);

    void afterPull(KVMHostInventory host, PullVolumeSnapshotOnHypervisorMsg msg, PullVolumeSnapshotOnHypervisorReply reply, Completion completion);

    void failedToPull(KVMHostInventory host, PullVolumeSnapshotOnHypervisorMsg msg, KVMAgentCommands.BlockPullResponse rsp, ErrorCode err);
}
