package org.zstack.ldap.api;

import org.zstack.header.message.APIEvent;
import org.zstack.header.rest.RestResponse;

import java.util.List;

import static org.zstack.utils.CollectionDSL.list;

@RestResponse(allTo = "ldapServerUuidList")
public class APIRemoveLdapFilterRuleEvent extends APIEvent {
    private List<String> ldapServerUuidList;

    public APIRemoveLdapFilterRuleEvent(String apiId) {
        super(apiId);
    }

    public APIRemoveLdapFilterRuleEvent() {
        super(null);
    }

    public List<String> getLdapServerUuidList() {
        return ldapServerUuidList;
    }

    public void setLdapServerUuidList(List<String> ldapServerUuidList) {
        this.ldapServerUuidList = ldapServerUuidList;
    }

    public static APIRemoveLdapFilterRuleEvent __example__() {
        APIRemoveLdapFilterRuleEvent event = new APIRemoveLdapFilterRuleEvent();
        event.setLdapServerUuidList(list(uuid()));
        return event;
    }
}
