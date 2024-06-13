package org.zstack.sdk;

import org.zstack.sdk.SchedulerJobInventory;

public class UpdateOutOfBandCronSchedulerResult {
    public SchedulerJobInventory inventory;
    public void setInventory(SchedulerJobInventory inventory) {
        this.inventory = inventory;
    }
    public SchedulerJobInventory getInventory() {
        return this.inventory;
    }

}
