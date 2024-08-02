package org.zstack.sdk;

import org.zstack.sdk.RoleInventory;

public class UpdateRoleResult {
    public RoleInventory inventory;
    public void setInventory(RoleInventory inventory) {
        this.inventory = inventory;
    }
    public RoleInventory getInventory() {
        return this.inventory;
    }

}
