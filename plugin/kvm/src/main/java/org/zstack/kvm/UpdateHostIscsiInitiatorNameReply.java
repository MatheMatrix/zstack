package org.zstack.kvm;

import org.zstack.header.host.HostInventory;
import org.zstack.header.message.MessageReply;

public class UpdateHostIscsiInitiatorNameReply extends MessageReply {
    HostInventory inventory;

    public HostInventory getInventory() {
        return inventory;
    }

    public void setInventory(HostInventory inventory) {
        this.inventory = inventory;
    }
}
