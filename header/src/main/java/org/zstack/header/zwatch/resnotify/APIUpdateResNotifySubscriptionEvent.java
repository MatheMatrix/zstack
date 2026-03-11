package org.zstack.header.zwatch.resnotify;

import org.zstack.header.message.APIEvent;
import org.zstack.header.rest.RestResponse;

@RestResponse(allTo = "inventory")
public class APIUpdateResNotifySubscriptionEvent extends APIEvent {
    private ResNotifySubscriptionInventory inventory;

    public APIUpdateResNotifySubscriptionEvent() {
    }

    public APIUpdateResNotifySubscriptionEvent(String apiId) {
        super(apiId);
    }

    public ResNotifySubscriptionInventory getInventory() {
        return inventory;
    }

    public void setInventory(ResNotifySubscriptionInventory inventory) {
        this.inventory = inventory;
    }

    public static APIUpdateResNotifySubscriptionEvent __example__() {
        return new APIUpdateResNotifySubscriptionEvent();
    }
}
