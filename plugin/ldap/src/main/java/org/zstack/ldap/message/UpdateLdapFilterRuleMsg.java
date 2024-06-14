package org.zstack.ldap.message;

import org.zstack.header.message.NeedReplyMessage;
import org.zstack.identity.imports.message.AccountSourceMessage;
import org.zstack.ldap.entity.LdapFilterRulePolicy;
import org.zstack.ldap.entity.LdapFilterRuleTarget;

public class UpdateLdapFilterRuleMsg extends NeedReplyMessage implements AccountSourceMessage {
    private String ldapServerUuid;
    private String ruleUuid;
    private String rule;
    private LdapFilterRulePolicy policy;
    private LdapFilterRuleTarget target;

    public String getLdapServerUuid() {
        return ldapServerUuid;
    }

    public void setLdapServerUuid(String ldapServerUuid) {
        this.ldapServerUuid = ldapServerUuid;
    }

    public String getRuleUuid() {
        return ruleUuid;
    }

    public void setRuleUuid(String ruleUuid) {
        this.ruleUuid = ruleUuid;
    }

    public String getRule() {
        return rule;
    }

    public void setRule(String rule) {
        this.rule = rule;
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
