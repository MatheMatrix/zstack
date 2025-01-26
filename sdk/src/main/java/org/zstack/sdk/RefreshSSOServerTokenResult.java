package org.zstack.sdk;

import org.zstack.sdk.SSOServerTokenInventory;

public class RefreshSSOServerTokenResult {
    public SSOServerTokenInventory inventory;
    public void setInventory(SSOServerTokenInventory inventory) {
        this.inventory = inventory;
    }
    public SSOServerTokenInventory getInventory() {
        return this.inventory;
    }

}
