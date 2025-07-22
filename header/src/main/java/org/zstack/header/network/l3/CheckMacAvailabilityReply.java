package org.zstack.header.network.l3;

import org.zstack.header.message.MessageReply;

/**
 * Created by frank on 1/21/2016.
 */
public class CheckMacAvailabilityReply extends MessageReply {
    private boolean available;
    private String reason;
    private UsedIpInventory usedIpInventory;

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public UsedIpInventory getUsedIpInventory() {
        return usedIpInventory;
    }

    public void setUsedIpInventory(UsedIpInventory usedIpInventory) {
        this.usedIpInventory = usedIpInventory;
    }
}
