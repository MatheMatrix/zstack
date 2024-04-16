package org.zstack.header.vm;

import org.zstack.header.message.APIEvent;
import org.zstack.header.rest.RestResponse;
import static org.zstack.utils.CollectionDSL.list;

@RestResponse(allTo = "inventory")
public class APIUpdateVmInstanceTemplateEvent extends APIEvent {
    private VmInstanceTemplateInventory inventory;

    public APIUpdateVmInstanceTemplateEvent() {
    }

    public APIUpdateVmInstanceTemplateEvent(String apiId) {
        super(apiId);
    }

    public VmInstanceTemplateInventory getInventory() {
        return inventory;
    }

    public void setInventory(VmInstanceTemplateInventory inventory) {
        this.inventory = inventory;
    }

    public static APIUpdateVmInstanceTemplateEvent __example__() {
        APIUpdateVmInstanceTemplateEvent event = new APIUpdateVmInstanceTemplateEvent();
        VmInstanceTemplateInventory inventory = new VmInstanceTemplateInventory();
        inventory.setUuid(uuid());
        inventory.setName("vmInstanceTemplate");
        inventory.setVmInstanceUuid(uuid());
        inventory.setZoneUuid(uuid());
        inventory.setType(VmInstanceTemplateType.Template.toString());
        inventory.setTemplateCacheUuid(null);
        event.setInventory(inventory);
        return event;
    }

}
