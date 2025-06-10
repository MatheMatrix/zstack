package org.zstack.header.sshkeypair;

import org.zstack.header.message.NeedReplyMessage;

/**
 * Created by boce.wang on 06/10/2025.
 */
public class AttachSshKeyPairToVmInstanceMsg extends NeedReplyMessage implements SshKeyPairMessage {
    private String sshKeyPairUuid;
    private String vmInstanceUuid;

    @Override
    public String getSshKeyPairUuid() {
        return sshKeyPairUuid;
    }

    public void setSshKeyPairUuid(String sshKeyPairUuid) {
        this.sshKeyPairUuid = sshKeyPairUuid;
    }

    public String getVmInstanceUuid() {
        return vmInstanceUuid;
    }

    public void setVmInstanceUuid(String vmInstanceUuid) {
        this.vmInstanceUuid = vmInstanceUuid;
    }
}
