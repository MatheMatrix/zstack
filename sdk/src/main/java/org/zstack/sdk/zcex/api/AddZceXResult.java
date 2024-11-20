package org.zstack.sdk.zcex.api;

import org.zstack.sdk.zcex.entity.ZceXInventory;

public class AddZceXResult {
    public ZceXInventory inventory;
    public void setInventory(ZceXInventory inventory) {
        this.inventory = inventory;
    }
    public ZceXInventory getInventory() {
        return this.inventory;
    }

}
