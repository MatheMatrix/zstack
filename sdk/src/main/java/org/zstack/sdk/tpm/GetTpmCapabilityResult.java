package org.zstack.sdk.tpm;

import org.zstack.sdk.tpm.TpmCapabilityView;

public class GetTpmCapabilityResult {
    public TpmCapabilityView inventory;
    public void setInventory(TpmCapabilityView inventory) {
        this.inventory = inventory;
    }
    public TpmCapabilityView getInventory() {
        return this.inventory;
    }

}
