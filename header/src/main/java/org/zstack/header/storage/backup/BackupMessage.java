package org.zstack.header.storage.backup;

import org.zstack.header.message.NeedReplyMessage;
import org.zstack.header.message.OverlayMessage;
import org.zstack.header.vm.VmInstanceMessage;

public class BackupMessage extends OverlayMessage implements VmInstanceMessage {
    public static final String SERVICE_ID = "backup-operation";
    public static final String SYNC_SIGNATURE = "vm-%s-backup";
    private static final String QUEUED_HEADER = BackupMessage.class.getName() + ".queued";

    private String vmInstanceUuid;
    private String backendServiceId;
    private String resourceUuid;
    private BackupOperationConflictChecker.Operation operation;

    public static BackupMessage valueOf(String vmUuid, String backendServiceId,
                                        BackupOperationConflictChecker.Operation operation,
                                        String resourceUuid, NeedReplyMessage innerMessage) {
        innerMessage.putHeaderEntry(QUEUED_HEADER, true);

        BackupMessage msg = new BackupMessage();
        msg.setVmInstanceUuid(vmUuid);
        msg.setBackendServiceId(backendServiceId);
        msg.setOperation(operation);
        msg.setResourceUuid(resourceUuid);
        msg.setMessage(innerMessage);
        msg.setMessageDeadline(innerMessage.getMessageDeadline());
        msg.setTimeout(innerMessage.getMessageDeadline() == -1 ? innerMessage.getTimeout() :
                Math.max(1L, innerMessage.getMessageDeadline() - System.currentTimeMillis()));
        return msg;
    }

    public static boolean hasQueued(NeedReplyMessage msg) {
        return Boolean.TRUE.equals(msg.getHeaderEntry(QUEUED_HEADER));
    }

    public static void inheritQueued(NeedReplyMessage source, NeedReplyMessage target) {
        if (hasQueued(source)) {
            target.putHeaderEntry(QUEUED_HEADER, true);
        }
    }

    @Override
    public String getVmInstanceUuid() {
        return vmInstanceUuid;
    }

    public void setVmInstanceUuid(String vmInstanceUuid) {
        this.vmInstanceUuid = vmInstanceUuid;
    }

    public String getBackendServiceId() {
        return backendServiceId;
    }

    public void setBackendServiceId(String backendServiceId) {
        this.backendServiceId = backendServiceId;
    }

    public String getResourceUuid() {
        return resourceUuid;
    }

    public void setResourceUuid(String resourceUuid) {
        this.resourceUuid = resourceUuid;
    }

    public BackupOperationConflictChecker.Operation getOperation() {
        return operation;
    }

    public void setOperation(BackupOperationConflictChecker.Operation operation) {
        this.operation = operation;
    }
}
