package org.zstack.ldap.message;

import org.zstack.header.message.NeedReplyMessage;
import org.zstack.identity.imports.message.AccountSourceMessage;
import org.zstack.ldap.entity.LdapFilterRulePolicy;
import org.zstack.ldap.entity.LdapFilterRuleTarget;

import java.util.List;

public class AddLdapFilterRuleMsg extends NeedReplyMessage implements AccountSourceMessage {
    private String ldapServerUuid;
    private List<String> rules;
    private LdapFilterRulePolicy policy;
    private LdapFilterRuleTarget target;

    public String getLdapServerUuid() {
        return ldapServerUuid;
    }

    public void setLdapServerUuid(String ldapServerUuid) {
        this.ldapServerUuid = ldapServerUuid;
    }

    public List<String> getRules() {
        return rules;
    }

    public void setRules(List<String> rules) {
        this.rules = rules;
    }

    public LdapFilterRulePolicy getPolicy() {
        return policy;
    }

    public void setPolicy(LdapFilterRulePolicy policy) {
        this.policy = policy;
    }

    public LdapFilterRuleTarget getTarget() {
        return target;
    }

    public void setTarget(LdapFilterRuleTarget target) {
        this.target = target;
    }

    @Override
    public String getSourceUuid() {
        return getLdapServerUuid();
    }
}
