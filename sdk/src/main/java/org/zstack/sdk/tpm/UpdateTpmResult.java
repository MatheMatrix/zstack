package org.zstack.sdk.tpm;

import org.zstack.sdk.tpm.TpmInventory;

public class UpdateTpmResult {
    public TpmInventory inventory;
    public void setInventory(TpmInventory inventory) {
        this.inventory = inventory;
    }
    public TpmInventory getInventory() {
        return this.inventory;
    }

}
