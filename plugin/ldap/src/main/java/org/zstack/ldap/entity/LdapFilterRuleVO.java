package org.zstack.ldap.entity;

import org.zstack.header.vo.EntityGraph;
import org.zstack.header.vo.ForeignKey;
import org.zstack.header.vo.Index;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import java.sql.Timestamp;

/**
 * Created by Wenhao.Zhang on 2024/06/05
 */
@Entity
@Table
@EntityGraph(
    parents = {
        @EntityGraph.Neighbour(type = LdapServerVO.class, myField = "ldapServerUuid", targetField = "uuid")
    }
)
public class LdapFilterRuleVO {
    @Id
    @Column
    @Index
    private String uuid;

    @Column
    @ForeignKey(parentEntityClass = LdapServerVO.class, onDeleteAction = ForeignKey.ReferenceOption.CASCADE)
    private String ldapServerUuid;

    @Column
    private String rule;

    @Column
    @Enumerated(EnumType.STRING)
    private LdapFilterRulePolicy policy;

    @Column
    @Enumerated(EnumType.STRING)
    private LdapFilterRuleTarget target;

    @Column
    private Timestamp createDate;

    @Column
    private Timestamp lastOpDate;

    @PreUpdate
    private void preUpdate() {
        lastOpDate = null;
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
