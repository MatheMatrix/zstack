package org.zstack.sdk;

import org.zstack.sdk.PrimaryStorageInventory;

public class TakeoverPrimaryStorageResult {
    public PrimaryStorageInventory inventory;
    public void setInventory(PrimaryStorageInventory inventory) {
        this.inventory = inventory;
    }
    public PrimaryStorageInventory getInventory() {
        return this.inventory;
    }

    public java.lang.String reconnectResult;
    public void setReconnectResult(java.lang.String reconnectResult) {
        this.reconnectResult = reconnectResult;
    }
    public java.lang.String getReconnectResult() {
        return this.reconnectResult;
    }

    public java.lang.String reconnectError;
    public void setReconnectError(java.lang.String reconnectError) {
        this.reconnectError = reconnectError;
    }
    public java.lang.String getReconnectError() {
        return this.reconnectError;
    }

}
