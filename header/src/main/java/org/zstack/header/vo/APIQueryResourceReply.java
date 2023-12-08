package org.zstack.header.vo;

import org.zstack.header.message.APIReply;
import org.zstack.header.rest.RestResponse;

import java.util.List;

import static org.zstack.utils.CollectionDSL.list;

/**
 * author:kaicai.hu
 * Date:2023/12/8
 */
@RestResponse(allTo = "inventories")
public class APIQueryResourceReply extends APIReply {
    private List<ResourceInventory> inventories;

    public List<ResourceInventory> getInventories() {
        return inventories;
    }

    public void setInventories(List<ResourceInventory> inventories) {
        this.inventories = inventories;
    }

    public static APIQueryResourceReply __example__() {
        APIQueryResourceReply reply = new APIQueryResourceReply();
        ResourceInventory inventory = new ResourceInventory();
        inventory.setUuid(uuid());
        inventory.setResourceName("test");
        inventory.setResourceType("HostVO");
        reply.setInventories(list(inventory));
        return reply;
    }
}
