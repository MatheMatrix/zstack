package org.zstack.header.zwatch.resnotify;

import org.zstack.header.query.APIQueryReply;
import org.zstack.header.rest.RestResponse;

import java.util.List;

@RestResponse(allTo = "inventories")
public class APIQueryResNotifySubscriptionReply extends APIQueryReply {
    private List<ResNotifySubscriptionInventory> inventories;

    public List<ResNotifySubscriptionInventory> getInventories() {
        return inventories;
    }

    public void setInventories(List<ResNotifySubscriptionInventory> inventories) {
        this.inventories = inventories;
    }
}
