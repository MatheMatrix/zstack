package org.zstack.header.storage.backup;

import org.zstack.header.message.NeedReplyMessage;
import org.zstack.header.message.OverlayMessage;
import org.zstack.header.vm.VmInstanceMessage;

public class BackupMessage extends OverlayMessage implements VmInstanceMessage {
    public static final String SERVICE_ID = "backup-message";
    private static final String QUEUED_HEADER = BackupMessage.class.getName() + ".queued";
    public static final String SYNC_SIGNATURE = "backup-operation-vm-%s";

    private String vmInstanceUuid;
    private String targetServiceId;

    public static BackupMessage valueOf(String vmUuid, String targetServiceId, NeedReplyMessage innerMessage) {
        innerMessage.putHeaderEntry(QUEUED_HEADER, true);

        BackupMessage msg = new BackupMessage();
        msg.setVmInstanceUuid(vmUuid);
        msg.setTargetServiceId(targetServiceId);
        msg.setMessage(innerMessage);
        msg.setMessageDeadline(innerMessage.getMessageDeadline());
        msg.setTimeout(innerMessage.getMessageDeadline() == -1 ? innerMessage.getTimeout() :
                Math.max(1L, innerMessage.getMessageDeadline() - System.currentTimeMillis()));
        return msg;
    }

    public static boolean hasQueued(NeedReplyMessage msg) {
        return Boolean.TRUE.equals(msg.getHeaderEntry(QUEUED_HEADER));
    }

    @Override
    public String getVmInstanceUuid() {
        return vmInstanceUuid;
    }

    public void setVmInstanceUuid(String vmInstanceUuid) {
        this.vmInstanceUuid = vmInstanceUuid;
    }

    public String getTargetServiceId() {
        return targetServiceId;
    }

    public void setTargetServiceId(String targetServiceId) {
        this.targetServiceId = targetServiceId;
    }
}
