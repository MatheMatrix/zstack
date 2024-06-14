package org.zstack.ldap.message;

import org.zstack.header.message.NeedReplyMessage;
import org.zstack.identity.imports.message.AccountSourceMessage;

import java.util.List;

public class RemoveLdapFilterRuleMsg extends NeedReplyMessage implements AccountSourceMessage {
    private String ldapServerUuid;
    private List<String> ruleUuidList;

    public String getLdapServerUuid() {
        return ldapServerUuid;
    }

    public void setLdapServerUuid(String ldapServerUuid) {
        this.ldapServerUuid = ldapServerUuid;
    }

    public List<String> getRuleUuidList() {
        return ruleUuidList;
    }

    public void setRuleUuidList(List<String> ruleUuidList) {
        this.ruleUuidList = ruleUuidList;
    }

    @Override
    public String getSourceUuid() {
        return getLdapServerUuid();
    }
}
