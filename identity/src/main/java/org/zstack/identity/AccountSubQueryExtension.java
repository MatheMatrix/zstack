package org.zstack.identity;

import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.header.identity.AccountConstant;
import org.zstack.header.query.APIQueryMessage;
import org.zstack.header.search.Inventory;
import org.zstack.query.AbstractMysqlQuerySubQueryExtension;
import org.zstack.query.MysqlQuerySubQueryExtension;
import org.zstack.query.QueryUtils;
import org.zstack.utils.FieldUtils;

import javax.persistence.Id;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 */
public class AccountSubQueryExtension extends AbstractMysqlQuerySubQueryExtension {
    @Autowired
    private AccountManager acntMgr;

    @Override
    public String makeSubquery(APIQueryMessage msg, Class inventoryClass) {
        if (AccountConstant.INITIAL_SYSTEM_ADMIN_UUID.equals(msg.getSession().getAccountUuid())
                && IdentityGlobalConfig.SHOW_ALL_RESOURCE_TO_ADMIN.value(Boolean.class))  {
            return null;
        }

        Class entityClass = QueryUtils.getEntityClassFromInventoryClass(inventoryClass);
        if (!acntMgr.isResourceHavingAccountReference(entityClass)) {
            return null;
        }

        String priKey = QueryUtils.getPrimaryKeyNameFromEntityClass(entityClass);

        String resourceType = acntMgr.getBaseResourceType(entityClass).getSimpleName();
        return String.format("(%s.%s in (select accountresourcerefvo.resourceUuid from AccountResourceRefVO accountresourcerefvo where accountresourcerefvo.accountUuid = '%s'" +
                " and accountresourcerefvo.resourceType = '%s' and accountresourcerefvo.type = 'Own') or %s.%s in (select sharedresourcevo.resourceUuid from SharedResourceVO sharedresourcevo where" +
                " (sharedresourcevo.receiverAccountUuid = '%s' or sharedresourcevo.toPublic = 1) and sharedresourcevo.resourceType = '%s'))",
        inventoryClass.getSimpleName().toLowerCase(), priKey, msg.getSession().getAccountUuid(), resourceType,
        inventoryClass.getSimpleName().toLowerCase(), priKey, msg.getSession().getAccountUuid(), resourceType);
    }
}
