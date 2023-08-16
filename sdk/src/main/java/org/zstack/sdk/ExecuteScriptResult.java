package org.zstack.sdk;

import org.zstack.sdk.ScriptExecutedRecordInventory;

public class ExecuteScriptResult {
    public ScriptExecutedRecordInventory inventory;
    public void setInventory(ScriptExecutedRecordInventory inventory) {
        this.inventory = inventory;
    }
    public ScriptExecutedRecordInventory getInventory() {
        return this.inventory;
    }

}
