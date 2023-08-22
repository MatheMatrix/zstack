package org.zstack.sdk;

import org.zstack.sdk.SchedulerJobGroupZoneRefInventory;

public class AttachSchedulerJobGroupToZoneResult {
    public SchedulerJobGroupZoneRefInventory inventory;
    public void setInventory(SchedulerJobGroupZoneRefInventory inventory) {
        this.inventory = inventory;
    }
    public SchedulerJobGroupZoneRefInventory getInventory() {
        return this.inventory;
    }

}
