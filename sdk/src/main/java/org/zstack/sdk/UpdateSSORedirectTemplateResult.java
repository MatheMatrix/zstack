package org.zstack.sdk;

import org.zstack.sdk.SSOUrlTemplateInventory;

public class UpdateSSORedirectTemplateResult {
    public SSOUrlTemplateInventory inventory;
    public void setInventory(SSOUrlTemplateInventory inventory) {
        this.inventory = inventory;
    }
    public SSOUrlTemplateInventory getInventory() {
        return this.inventory;
    }

}
