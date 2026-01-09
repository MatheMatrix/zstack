package org.zstack.header.vm;

import org.zstack.header.message.MessageReply;

import java.util.List;

public class RegisterVmFromMetadataInnerReply extends MessageReply {
    private VmInstanceInventory inventory;
    private List<String> warnings;

    public VmInstanceInventory getInventory() {
        return inventory;
    }

    public void setInventory(VmInstanceInventory inventory) {
        this.inventory = inventory;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }
}
