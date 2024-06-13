package org.zstack.ldap.entity;

import org.zstack.header.configuration.PythonClassInventory;
import org.zstack.header.search.Inventory;
import org.zstack.utils.CollectionUtils;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;

@Inventory(mappingVOClass = LdapFilterRuleVO.class)
@PythonClassInventory
public class LdapFilterRuleInventory implements Serializable {
    private String uuid;
    private String ldapServerUuid;
    private String rule;
    private String policy;
    private String target;
    private Timestamp createDate;
    private Timestamp lastOpDate;

    public static LdapFilterRuleInventory valueOf(LdapFilterRuleVO vo) {
        LdapFilterRuleInventory inv = new LdapFilterRuleInventory();
        inv.setUuid(vo.getUuid());
        inv.setLdapServerUuid(vo.getLdapServerUuid());
        inv.setRule(vo.getRule());
        inv.setPolicy(vo.getPolicy().toString());
        inv.setTarget(vo.getTarget().toString());
        inv.setCreateDate(vo.getCreateDate());
        inv.setLastOpDate(vo.getLastOpDate());
        return inv;
    }

    public static List<LdapFilterRuleInventory> valueOf(Collection<LdapFilterRuleVO> vos) {
        return CollectionUtils.transform(vos, LdapFilterRuleInventory::valueOf);
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getLdapServerUuid() {
        return ldapServerUuid;
    }

    public void setLdapServerUuid(String ldapServerUuid) {
        this.ldapServerUuid = ldapServerUuid;
    }

    public String getRule() {
        return rule;
    }

    public void setRule(String rule) {
        this.rule = rule;
    }

    public String getPolicy() {
        return policy;
    }

    public void setPolicy(String policy) {
        this.policy = policy;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public Timestamp getCreateDate() {
        return createDate;
    }

    public void setCreateDate(Timestamp createDate) {
        this.createDate = createDate;
    }

    public Timestamp getLastOpDate() {
        return lastOpDate;
    }

    public void setLastOpDate(Timestamp lastOpDate) {
        this.lastOpDate = lastOpDate;
    }
}