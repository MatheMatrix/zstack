package org.zstack.kvm;

import org.zstack.header.core.Completion;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.host.DeleteVolumeSnapshotSelfOnHypervisorMsg;
import org.zstack.header.host.DeleteVolumeSnapshotSelfOnHypervisorReply;

import java.util.Map;


public interface KVMDeleteVolumeSnapshotSelfExtensionPoint {
    void beforeDeleteVolumeSnapshotSelf(KVMHostInventory host, DeleteVolumeSnapshotSelfOnHypervisorMsg msg, Map context, Completion completion);

    void afterDeleteVolumeSnapshotSelf(KVMHostInventory host, DeleteVolumeSnapshotSelfOnHypervisorMsg msg, DeleteVolumeSnapshotSelfOnHypervisorReply reply, Map context, Completion completion);

    void failedToDeleteVolumeSnapshotSelf(KVMHostInventory host, DeleteVolumeSnapshotSelfOnHypervisorMsg msg, KVMAgentCommands.AgentResponse rsp, Map context, ErrorCode err);
}
