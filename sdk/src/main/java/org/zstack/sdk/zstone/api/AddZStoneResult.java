package org.zstack.sdk.zstone.api;

import org.zstack.sdk.zstone.entity.ZStoneInventory;

public class AddZStoneResult {
    public ZStoneInventory inventory;
    public void setInventory(ZStoneInventory inventory) {
        this.inventory = inventory;
    }
    public ZStoneInventory getInventory() {
        return this.inventory;
    }

}
