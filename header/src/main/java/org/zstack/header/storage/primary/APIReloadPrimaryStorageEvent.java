package org.zstack.header.storage.primary;

import org.zstack.header.message.APIEvent;
import org.zstack.header.rest.RestResponse;

import java.util.Collections;

/**
 * Created by frank on 4/23/2015.
 */
@RestResponse(allTo = "inventory")
public class APIReloadPrimaryStorageEvent extends APIEvent {
    private PrimaryStorageInventory inventory;

    public APIReloadPrimaryStorageEvent() {
    }

    public APIReloadPrimaryStorageEvent(String apiId) {
        super(apiId);
    }

    public PrimaryStorageInventory getInventory() {
        return inventory;
    }

    public void setInventory(PrimaryStorageInventory inventory) {
        this.inventory = inventory;
    }
 
    public static APIReloadPrimaryStorageEvent __example__() {
        APIReloadPrimaryStorageEvent event = new APIReloadPrimaryStorageEvent();

        PrimaryStorageInventory ps = new PrimaryStorageInventory();
        ps.setName("PS1");
        ps.setUrl("/zstack_ps");
        ps.setType("LocalStorage");
        ps.setAttachedClusterUuids(Collections.singletonList(uuid()));
        ps.setState(PrimaryStorageState.Enabled.toString());
        ps.setStatus(PrimaryStorageStatus.Connected.toString());

        event.setInventory(ps);
        return event;
    }

}
