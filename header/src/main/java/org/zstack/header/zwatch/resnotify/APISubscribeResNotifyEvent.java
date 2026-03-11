package org.zstack.header.zwatch.resnotify;

import org.zstack.header.message.APIEvent;
import org.zstack.header.rest.RestResponse;

@RestResponse(allTo = "inventory")
public class APISubscribeResNotifyEvent extends APIEvent {
    private ResNotifySubscriptionInventory inventory;

    public APISubscribeResNotifyEvent() {
    }

    public APISubscribeResNotifyEvent(String apiId) {
        super(apiId);
    }

    public ResNotifySubscriptionInventory getInventory() {
        return inventory;
    }

    public void setInventory(ResNotifySubscriptionInventory inventory) {
        this.inventory = inventory;
    }

    public static APISubscribeResNotifyEvent __example__() {
        APISubscribeResNotifyEvent evt = new APISubscribeResNotifyEvent();
        return evt;
    }
}
