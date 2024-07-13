package org.zstack.sdk;

import org.zstack.sdk.OAuth2AccountClientInventory;

public class CreateOAuthClientResult {
    public OAuth2AccountClientInventory inventory;
    public void setInventory(OAuth2AccountClientInventory inventory) {
        this.inventory = inventory;
    }
    public OAuth2AccountClientInventory getInventory() {
        return this.inventory;
    }

}
