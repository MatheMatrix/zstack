package org.zstack.header.aspect;

import org.zstack.header.identity.AccountConstant;
import org.zstack.header.identity.AccountResourceRefVO;
import org.zstack.header.identity.OwnedByAccount;
import org.zstack.header.vo.ResourceTypeMetadata;

import javax.persistence.EntityManager;

public class OwnedByAccountAspectHelper {

    // 用于回调资源创建扩展点的静态字段
    private static ResourceOwnershipCreationNotifier notifier;

    public static void setResourceOwnershipCreationNotifier(ResourceOwnershipCreationNotifier notifier) {
        OwnedByAccountAspectHelper.notifier = notifier;
    }

    public static void createAccountResourceRefVO(OwnedByAccount oa, EntityManager entityManager, Object entity) {
        AccountResourceRefVO ref = new AccountResourceRefVO();
        ref.setAccountUuid(oa.getAccountUuid());
        ref.setResourceType(ResourceTypeMetadata.getBaseResourceTypeFromConcreteType(entity.getClass()).getSimpleName());
        ref.setConcreteResourceType(entity.getClass().getName());
        ref.setResourceUuid(OwnedByAccount.getResourceUuid(entity));
        ref.setPermission(AccountConstant.RESOURCE_PERMISSION_WRITE);
        ref.setOwnerAccountUuid(oa.getAccountUuid());
        ref.setShared(false);

        entityManager.persist(ref);

        // 通知资源归属创建事件
        if (notifier != null) {
            try {
                notifier.notifyResourceOwnershipCreated(ref);
            } catch (Exception e) {
                // 扩展点调用失败不应影响主流程，静默忽略
            }
        }
    }

    /**
     * 资源归属创建通知器接口，避免循环依赖
     */
    public static interface ResourceOwnershipCreationNotifier {
        void notifyResourceOwnershipCreated(AccountResourceRefVO ref);
    }
}
