package org.zstack.ldap.message;

import org.zstack.header.message.MessageReply;
import org.zstack.ldap.entity.LdapFilterRuleInventory;

import java.util.List;

public class AddLdapFilterRuleReply extends MessageReply {
    private List<LdapFilterRuleInventory> inventories;

    public List<LdapFilterRuleInventory> getInventories() {
        return inventories;
    }

    public void setInventories(List<LdapFilterRuleInventory> inventories) {
        this.inventories = inventories;
    }
}
