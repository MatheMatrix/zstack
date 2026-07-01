package org.zstack.sdk.zwatch.liverecovery.api;

import org.zstack.sdk.zwatch.liverecovery.entity.LiveRecoveryAlertSourceInventory;

public class RegisterLiveRecoveryAlertSourceResult {
    public LiveRecoveryAlertSourceInventory inventory;
    public void setInventory(LiveRecoveryAlertSourceInventory inventory) {
        this.inventory = inventory;
    }
    public LiveRecoveryAlertSourceInventory getInventory() {
        return this.inventory;
    }

}
