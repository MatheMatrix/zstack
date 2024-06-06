package org.zstack.identity.imports.entity;

import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;
import java.sql.Timestamp;

/**
 * Created by Wenhao.Zhang on 2024/05/31
 */
@StaticMetamodel(ImportAccountRefVO.class)
public class ImportAccountRefVO_ {
    public static volatile SingularAttribute<ImportAccountRefVO, String> uuid;
    public static volatile SingularAttribute<ImportAccountRefVO, String> keyFromImportSource;
    public static volatile SingularAttribute<ImportAccountRefVO, String> importSourceUuid;
    public static volatile SingularAttribute<ImportAccountRefVO, String> accountUuid;
    public static volatile SingularAttribute<ImportAccountRefVO, Timestamp> createDate;
    public static volatile SingularAttribute<ImportAccountRefVO, Timestamp> lastOpDate;
}
