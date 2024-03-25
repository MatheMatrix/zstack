package org.zstack.header.vm;

import org.zstack.header.message.NeedReplyMessage;

public class CreateVmInstanceFromVmInstanceTemplateMsg extends NeedReplyMessage implements VmInstanceMessage {
    CreateVmFromVmTemplateResourceSpec spec;

    public CreateVmFromVmTemplateResourceSpec getSpec() {
        return spec;
    }

    public void setSpec(CreateVmFromVmTemplateResourceSpec spec) {
        this.spec = spec;
    }

    @Override
    public String getVmInstanceUuid() {
        return spec.getVmInstanceInventory().getUuid();
    }
}
