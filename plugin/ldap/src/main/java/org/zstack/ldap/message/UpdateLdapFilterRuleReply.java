package org.zstack.ldap.message;

import org.zstack.header.message.MessageReply;
import org.zstack.ldap.entity.LdapFilterRuleInventory;

public class UpdateLdapFilterRuleReply extends MessageReply {
    private LdapFilterRuleInventory inventory;

    public LdapFilterRuleInventory getInventory() {
        return inventory;
    }

    public void setInventory(LdapFilterRuleInventory inventory) {
        this.inventory = inventory;
    }
}
