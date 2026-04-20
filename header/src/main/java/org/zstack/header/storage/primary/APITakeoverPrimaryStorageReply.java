package org.zstack.header.storage.primary;

import org.zstack.header.message.APIReply;
import org.zstack.header.rest.RestResponse;

import java.util.Collections;

@RestResponse(fieldsTo = {"all"})
public class APITakeoverPrimaryStorageReply extends APIReply {
    private PrimaryStorageInventory inventory;

    public PrimaryStorageInventory getInventory() {
        return inventory;
    }

    public void setInventory(PrimaryStorageInventory inventory) {
        this.inventory = inventory;
    }

    public static APITakeoverPrimaryStorageReply __example__() {
        APITakeoverPrimaryStorageReply reply = new APITakeoverPrimaryStorageReply();

        PrimaryStorageInventory ps = new PrimaryStorageInventory();
        ps.setName("PS1");
        ps.setUrl("/zstack_ps");
        ps.setType("SharedBlock");
        ps.setAttachedClusterUuids(Collections.singletonList(uuid()));

        reply.setInventory(ps);
        return reply;
    }
}
