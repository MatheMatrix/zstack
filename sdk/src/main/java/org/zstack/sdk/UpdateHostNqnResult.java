package org.zstack.sdk;

import org.zstack.sdk.HostInventory;

public class UpdateHostNqnResult {
    public HostInventory inventory;
    public void setInventory(HostInventory inventory) {
        this.inventory = inventory;
    }
    public HostInventory getInventory() {
        return this.inventory;
    }

}
