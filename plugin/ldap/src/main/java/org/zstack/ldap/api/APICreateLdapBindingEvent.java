package org.zstack.ldap.api;

import org.zstack.header.message.APIEvent;
import org.zstack.header.rest.RestResponse;
import org.zstack.identity.imports.entity.ImportAccountRefInventory;

@RestResponse(allTo = "inventory")
public class APICreateLdapBindingEvent extends APIEvent {
    private ImportAccountRefInventory inventory;

    public APICreateLdapBindingEvent(String apiId) {
        super(apiId);
    }

    public APICreateLdapBindingEvent() {
        super(null);
    }

    public ImportAccountRefInventory getInventory() {
        return inventory;
    }

    public void setInventory(ImportAccountRefInventory inventory) {
        this.inventory = inventory;
    }
 
    public static APICreateLdapBindingEvent __example__() {
        APICreateLdapBindingEvent event = new APICreateLdapBindingEvent();
        ImportAccountRefInventory inventory = new ImportAccountRefInventory();
        inventory.setUuid(uuid());
        inventory.setKeyFromImportSource("ou=Employee,uid=test");
        inventory.setAccountUuid(uuid());
        inventory.setImportSourceUuid(uuid());

        event.setInventory(inventory);
        return event;
    }

}
