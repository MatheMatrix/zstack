package org.zstack.sdk.identity.ldap.entity;



public class LdapFilterRuleInventory  {

    public java.lang.String uuid;
    public void setUuid(java.lang.String uuid) {
        this.uuid = uuid;
    }
    public java.lang.String getUuid() {
        return this.uuid;
    }

    public java.lang.String ldapServerUuid;
    public void setLdapServerUuid(java.lang.String ldapServerUuid) {
        this.ldapServerUuid = ldapServerUuid;
    }
    public java.lang.String getLdapServerUuid() {
        return this.ldapServerUuid;
    }

    public java.lang.String rule;
    public void setRule(java.lang.String rule) {
        this.rule = rule;
    }
    public java.lang.String getRule() {
        return this.rule;
    }

    public java.lang.String policy;
    public void setPolicy(java.lang.String policy) {
        this.policy = policy;
    }
    public java.lang.String getPolicy() {
        return this.policy;
    }

    public java.lang.String target;
    public void setTarget(java.lang.String target) {
        this.target = target;
    }
    public java.lang.String getTarget() {
        return this.target;
    }

    public java.sql.Timestamp createDate;
    public void setCreateDate(java.sql.Timestamp createDate) {
        this.createDate = createDate;
    }
    public java.sql.Timestamp getCreateDate() {
        return this.createDate;
    }

    public java.sql.Timestamp lastOpDate;
    public void setLastOpDate(java.sql.Timestamp lastOpDate) {
        this.lastOpDate = lastOpDate;
    }
    public java.sql.Timestamp getLastOpDate() {
        return this.lastOpDate;
    }

}
