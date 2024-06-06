package org.zstack.identity.imports.entity;

import org.zstack.header.vo.ResourceVO_;

import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;
import java.sql.Timestamp;

/**
 * Created by Wenhao.Zhang on 2024/05/31
 */
@StaticMetamodel(AccountImportSourceVO.class)
public class AccountImportSourceVO_ extends ResourceVO_ {
    public static volatile SingularAttribute<AccountImportSourceVO, String> description;
    public static volatile SingularAttribute<AccountImportSourceVO, String> type;
    public static volatile SingularAttribute<AccountImportSourceVO, Timestamp> createDate;
    public static volatile SingularAttribute<AccountImportSourceVO, Timestamp> lastOpDate;
}
