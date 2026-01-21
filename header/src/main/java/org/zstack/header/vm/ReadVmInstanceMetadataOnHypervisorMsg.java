package org.zstack.header.vm;

import org.zstack.header.message.NeedReplyMessage;

public class ReadVmInstanceMetadataOnHypervisorMsg extends NeedReplyMessage implements VmInstanceMessage {
    private String uuid;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    @Override
    public String getVmInstanceUuid() {
        return uuid;
    }
}
