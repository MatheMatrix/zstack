package org.zstack.sdk.crypto.secretresourcepool;

import org.zstack.sdk.crypto.securitymachine.SecretResourcePoolInventory;

public class ChangeSecretResourcePoolStateResult {
    public SecretResourcePoolInventory inventory;
    public void setInventory(SecretResourcePoolInventory inventory) {
        this.inventory = inventory;
    }
    public SecretResourcePoolInventory getInventory() {
        return this.inventory;
    }

}
