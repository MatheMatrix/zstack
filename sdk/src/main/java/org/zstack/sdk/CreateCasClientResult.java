package org.zstack.sdk;

import org.zstack.sdk.CasAccountClientInventory;

public class CreateCasClientResult {
    public CasAccountClientInventory inventory;
    public void setInventory(CasAccountClientInventory inventory) {
        this.inventory = inventory;
    }
    public CasAccountClientInventory getInventory() {
        return this.inventory;
    }

}
