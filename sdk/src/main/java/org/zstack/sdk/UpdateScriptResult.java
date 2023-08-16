package org.zstack.sdk;

import org.zstack.sdk.ScriptInventory;

public class UpdateScriptResult {
    public ScriptInventory inventory;
    public void setInventory(ScriptInventory inventory) {
        this.inventory = inventory;
    }
    public ScriptInventory getInventory() {
        return this.inventory;
    }

}
