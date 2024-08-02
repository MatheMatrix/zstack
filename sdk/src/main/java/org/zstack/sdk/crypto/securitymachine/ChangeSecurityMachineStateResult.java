package org.zstack.sdk.crypto.securitymachine;

import org.zstack.sdk.crypto.securitymachine.SecurityMachineInventory;

public class ChangeSecurityMachineStateResult {
    public SecurityMachineInventory inventory;
    public void setInventory(SecurityMachineInventory inventory) {
        this.inventory = inventory;
    }
    public SecurityMachineInventory getInventory() {
        return this.inventory;
    }

}
