package org.zstack.kvm;

import org.zstack.header.core.Completion;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.host.CommitVolumeSnapshotSelfOnHypervisorMsg;
import org.zstack.header.host.CommitVolumeSnapshotSelfOnHypervisorReply;


public interface KVMDeleteVolumeSnapshotSelfExtensionPoint {
    void beforeCommitVolumeSnapshot(KVMHostInventory host, CommitVolumeSnapshotSelfOnHypervisorMsg msg, Completion completion);

    void afterCommitVolumeSnapshot(KVMHostInventory host, CommitVolumeSnapshotSelfOnHypervisorMsg msg, CommitVolumeSnapshotSelfOnHypervisorReply reply, Completion completion);

    void failedToCommitVolumeSnapshot(KVMHostInventory host, CommitVolumeSnapshotSelfOnHypervisorMsg msg, KVMAgentCommands.AgentResponse rsp, ErrorCode err);

    void beforePullVolumeSnapshot(KVMHostInventory host, PullVolumeSnapshotSelfOnHypervisorMsg msg, Completion completion);

    void afterPullVolumeSnapshot(KVMHostInventory host, PullVolumeSnapshotSelfOnHypervisorMsg msg, PullVolumeSnapshotSelfOnHypervisorReply reply, Completion completion);

    void failedToPullVolumeSnapshot(KVMHostInventory host, PullVolumeSnapshotSelfOnHypervisorMsg msg, KVMAgentCommands.AgentResponse rsp, ErrorCode err);
}
