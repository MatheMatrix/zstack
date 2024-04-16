package org.zstack.sdk;

import org.zstack.sdk.VmInstanceTemplateInventory;

public class UpdateVmInstanceTemplateResult {
    public VmInstanceTemplateInventory inventory;
    public void setInventory(VmInstanceTemplateInventory inventory) {
        this.inventory = inventory;
    }
    public VmInstanceTemplateInventory getInventory() {
        return this.inventory;
    }

}
