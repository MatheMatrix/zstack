package org.zstack.kvm;

import org.zstack.header.core.Completion;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.host.CommitVolumeSnapshotOnHypervisorMsg;
import org.zstack.header.host.CommitVolumeSnapshotOnHypervisorReply;


public interface KVMBlockCommitExtensionPoint {
    void beforeCommit(KVMHostInventory host, CommitVolumeSnapshotOnHypervisorMsg msg, KVMAgentCommands.BlockCommitCmd cmd, Completion completion);

    void afterCommit(KVMHostInventory host, CommitVolumeSnapshotOnHypervisorMsg msg, KVMAgentCommands.BlockCommitCmd cmd, CommitVolumeSnapshotOnHypervisorReply reply, Completion completion);

    void failedToCommit(KVMHostInventory host, CommitVolumeSnapshotOnHypervisorMsg msg, KVMAgentCommands.BlockCommitCmd cmd, KVMAgentCommands.BlockCommitResponse rsp, ErrorCode err);
}
