package org.zstack.ldap.api;

import org.zstack.header.query.APIQueryReply;
import org.zstack.header.rest.RestResponse;
import org.zstack.identity.imports.entity.ImportAccountRefInventory;

import java.util.List;

import static org.zstack.utils.CollectionDSL.list;

@RestResponse(allTo = "inventories")
public class APIQueryLdapBindingReply extends APIQueryReply {
    private List<ImportAccountRefInventory> inventories;

    public List<ImportAccountRefInventory> getInventories() {
        return inventories;
    }

    public void setInventories(List<ImportAccountRefInventory> inventories) {
        this.inventories = inventories;
    }
 
    public static APIQueryLdapBindingReply __example__() {
        APIQueryLdapBindingReply reply = new APIQueryLdapBindingReply();

        ImportAccountRefInventory inventory = new ImportAccountRefInventory();
        inventory.setUuid(uuid());
        inventory.setKeyFromImportSource("ou=Employee,uid=test");
        inventory.setAccountUuid(uuid());
        inventory.setImportSourceUuid(uuid());

        reply.setInventories(list(inventory));
        return reply;
    }

}
