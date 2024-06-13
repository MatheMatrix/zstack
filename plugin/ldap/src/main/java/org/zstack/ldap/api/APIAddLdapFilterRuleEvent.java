package org.zstack.ldap.api;

import org.zstack.header.message.APIEvent;
import org.zstack.header.rest.RestResponse;
import org.zstack.ldap.entity.LdapFilterRuleInventory;
import org.zstack.ldap.entity.LdapFilterRulePolicy;
import org.zstack.ldap.entity.LdapFilterRuleTarget;

import java.sql.Timestamp;
import java.util.List;

import static org.zstack.utils.CollectionDSL.list;

@RestResponse(allTo = "inventories")
public class APIAddLdapFilterRuleEvent extends APIEvent {
    private List<LdapFilterRuleInventory> inventories;

    public APIAddLdapFilterRuleEvent(String apiId) {
        super(apiId);
    }

    public APIAddLdapFilterRuleEvent() {
        super(null);
    }

    public List<LdapFilterRuleInventory> getInventories() {
        return inventories;
    }

    public void setInventories(List<LdapFilterRuleInventory> inventories) {
        this.inventories = inventories;
    }

    public static APIAddLdapFilterRuleEvent __example__() {
        APIAddLdapFilterRuleEvent event = new APIAddLdapFilterRuleEvent();
        LdapFilterRuleInventory inventory = new LdapFilterRuleInventory();
        inventory.setUuid(uuid());
        inventory.setLdapServerUuid(uuid());
        inventory.setRule("cn=Micha Kops");
        inventory.setPolicy(LdapFilterRulePolicy.ACCEPT.toString());
        inventory.setTarget(LdapFilterRuleTarget.AddNew.toString());
        inventory.setCreateDate(new Timestamp(System.currentTimeMillis()));
        inventory.setLastOpDate(new Timestamp(System.currentTimeMillis()));

        event.setInventories(list(inventory));
        return event;
    }
}
