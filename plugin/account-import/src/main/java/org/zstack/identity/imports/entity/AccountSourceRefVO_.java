package org.zstack.identity.imports.entity;

import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;
import java.sql.Timestamp;

/**
 * Created by Wenhao.Zhang on 2024/05/31
 */
@StaticMetamodel(AccountSourceRefVO.class)
public class AccountSourceRefVO_ {
    public static volatile SingularAttribute<AccountSourceRefVO, String> uuid;
    public static volatile SingularAttribute<AccountSourceRefVO, String> credentials;
    public static volatile SingularAttribute<AccountSourceRefVO, String> accountSourceUuid;
    public static volatile SingularAttribute<AccountSourceRefVO, String> accountUuid;
    public static volatile SingularAttribute<AccountSourceRefVO, Timestamp> createDate;
    public static volatile SingularAttribute<AccountSourceRefVO, Timestamp> lastOpDate;
}
