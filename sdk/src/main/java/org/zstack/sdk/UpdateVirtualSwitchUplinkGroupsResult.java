package org.zstack.sdk;

import org.zstack.sdk.UplinkGroupInventory;

public class UpdateVirtualSwitchUplinkGroupsResult {
    public UplinkGroupInventory inventory;
    public void setInventory(UplinkGroupInventory inventory) {
        this.inventory = inventory;
    }
    public UplinkGroupInventory getInventory() {
        return this.inventory;
    }

}
