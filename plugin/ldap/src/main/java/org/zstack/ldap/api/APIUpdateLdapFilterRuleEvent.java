package org.zstack.ldap.api;

import org.zstack.header.message.APIEvent;
import org.zstack.header.rest.RestResponse;
import org.zstack.ldap.entity.LdapFilterRuleInventory;
import org.zstack.ldap.entity.LdapFilterRulePolicy;
import org.zstack.ldap.entity.LdapFilterRuleTarget;

import java.sql.Timestamp;

@RestResponse(allTo = "inventory")
public class APIUpdateLdapFilterRuleEvent extends APIEvent {
    private LdapFilterRuleInventory inventory;

    public APIUpdateLdapFilterRuleEvent(String apiId) {
        super(apiId);
    }

    public APIUpdateLdapFilterRuleEvent() {
        super(null);
    }

    public LdapFilterRuleInventory getInventory() {
        return inventory;
    }

    public void setInventory(LdapFilterRuleInventory inventory) {
        this.inventory = inventory;
    }

    public static APIUpdateLdapFilterRuleEvent __example__() {
        APIUpdateLdapFilterRuleEvent event = new APIUpdateLdapFilterRuleEvent();
        LdapFilterRuleInventory inventory = new LdapFilterRuleInventory();
        inventory.setUuid(uuid());
        inventory.setLdapServerUuid(uuid());
        inventory.setRule("cn=Micha Kops");
        inventory.setPolicy(LdapFilterRulePolicy.ACCEPT.toString());
        inventory.setTarget(LdapFilterRuleTarget.AddNew.toString());
        inventory.setCreateDate(new Timestamp(System.currentTimeMillis()));
        inventory.setLastOpDate(new Timestamp(System.currentTimeMillis()));

        event.setInventory(inventory);
        return event;
    }
}
