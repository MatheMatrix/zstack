package org.zstack.sdk.identity.ldap.api;

import org.zstack.sdk.identity.ldap.entity.LdapFilterRuleInventory;

public class UpdateLdapFilterRuleResult {
    public LdapFilterRuleInventory inventory;
    public void setInventory(LdapFilterRuleInventory inventory) {
        this.inventory = inventory;
    }
    public LdapFilterRuleInventory getInventory() {
        return this.inventory;
    }

}
