package org.zstack.ldap.entity;

import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;
import java.sql.Timestamp;

@StaticMetamodel(LdapFilterRuleVO.class)
public class LdapFilterRuleVO_ {
    public static volatile SingularAttribute<LdapFilterRuleVO, String> uuid;
    public static volatile SingularAttribute<LdapFilterRuleVO, String> ldapServerUuid;
    public static volatile SingularAttribute<LdapFilterRuleVO, String> rule;
    public static volatile SingularAttribute<LdapFilterRuleVO, LdapFilterRulePolicy> policy;
    public static volatile SingularAttribute<LdapFilterRuleVO, LdapFilterRuleTarget> target;
    public static volatile SingularAttribute<LdapFilterRuleVO, Timestamp> createDate;
    public static volatile SingularAttribute<LdapFilterRuleVO, Timestamp> lastOpDate;
}
