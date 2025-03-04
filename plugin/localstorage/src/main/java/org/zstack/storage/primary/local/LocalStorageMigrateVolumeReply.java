package org.zstack.storage.primary.local;

import org.zstack.header.message.MessageReply;

public class LocalStorageMigrateVolumeReply extends MessageReply {
    private LocalStorageResourceRefInventory inventory;

    public LocalStorageResourceRefInventory getInventory() {
        return inventory;
    }

    public void setInventory(LocalStorageResourceRefInventory inventory) {
        this.inventory = inventory;
    }
}
