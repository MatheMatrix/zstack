package org.zstack.identity;

import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.componentloader.PluginRegistry;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.SQLBatch;
import org.zstack.core.db.HardDeleteEntityExtensionPoint;
import org.zstack.core.db.SoftDeleteEntityByEOExtensionPoint;
import org.zstack.header.Component;
import org.zstack.header.aspect.OwnedByAccountAspectHelper;
import org.zstack.header.identity.*;
import org.zstack.header.vo.ResourceVO;
import org.zstack.utils.CollectionUtils;
import org.zstack.utils.function.ForEachFunction;

import java.util.*;

public class ExternalTenantResourceTracker implements
        ResourceOwnershipCreatedExtensionPoint,
        HardDeleteEntityExtensionPoint,
        SoftDeleteEntityByEOExtensionPoint,
        Component,
        OwnedByAccountAspectHelper.ResourceOwnershipCreationNotifier {

    @Autowired
    private DatabaseFacade dbf;
    @Autowired
    private PluginRegistry pluginRgty;

    private Map<String, ExternalTenantProvider> providers = new HashMap<>();

    @Override
    public boolean start() {
        for (ExternalTenantProvider p : pluginRgty.getExtensionList(ExternalTenantProvider.class)) {
            providers.put(p.getSource(), p);
        }

        // 设置AOP层面的通知器，避免循环依赖
        OwnedByAccountAspectHelper.setResourceOwnershipCreationNotifier(this);

        return true;
    }

    @Override
    public boolean stop() {
        OwnedByAccountAspectHelper.setResourceOwnershipCreationNotifier(null);
        return true;
    }

    // --- AOP层面的资源创建通知 ---
    @Override
    public void notifyResourceOwnershipCreated(AccountResourceRefVO ref) {
        // AOP 层面无法通过方法参数获取 session，从 ThreadLocal 读取
        ExternalTenantContext ctx = ExternalTenantContext.getCurrent();
        if (ctx == null || ctx.getTenantId() == null) {
            return;
        }

        ExternalTenantProvider provider = providers.get(ctx.getSource());
        if (provider == null) {
            return;
        }

        if (!provider.shouldTrackResource(ref.getResourceType())) {
            return;
        }

        ExternalTenantResourceRefVO extRef = new ExternalTenantResourceRefVO();
        extRef.setSource(ctx.getSource());
        extRef.setTenantId(ctx.getTenantId());
        extRef.setUserId(ctx.getUserId());
        extRef.setResourceUuid(ref.getResourceUuid());
        extRef.setResourceType(ref.getResourceType());
        extRef.setAccountUuid(ref.getAccountUuid());
        dbf.persist(extRef);

        provider.onResourceBound(ctx, ref.getResourceUuid(), ref.getResourceType());
    }

    // --- 资源创建关联 ---
    @Override
    public void afterResourceOwnershipCreated(AccountResourceRefVO ref, SessionInventory session) {
        if (session == null || !session.hasExternalTenant()) {
            return;
        }

        ExternalTenantContext ctx = session.getExternalTenantContext();
        ExternalTenantProvider provider = providers.get(ctx.getSource());
        if (provider == null) {
            return;
        }

        if (!provider.shouldTrackResource(ref.getResourceType())) {
            return;
        }

        ExternalTenantResourceRefVO extRef = new ExternalTenantResourceRefVO();
        extRef.setSource(ctx.getSource());
        extRef.setTenantId(ctx.getTenantId());
        extRef.setUserId(ctx.getUserId());
        extRef.setResourceUuid(ref.getResourceUuid());
        extRef.setResourceType(ref.getResourceType());
        extRef.setAccountUuid(ref.getAccountUuid());
        dbf.persist(extRef);

        provider.onResourceBound(ctx, ref.getResourceUuid(), ref.getResourceType());
    }

    // --- 资源删除清理 ---
    @Override
    public List<Class> getEntityClassForHardDeleteEntityExtension() {
        return Collections.singletonList(ResourceVO.class);
    }

    @Override
    public void postHardDelete(Collection entityIds, Class entityClass) {
        cleanupTenantRefs(entityIds);
    }

    @Override
    public List<Class> getEOClassForSoftDeleteEntityExtension() {
        return Collections.singletonList(ResourceVO.class);
    }

    @Override
    public void postSoftDelete(Collection entityIds, Class EOClass) {
        cleanupTenantRefs(entityIds);
    }

    private void cleanupTenantRefs(Collection entityIds) {
        if (entityIds == null || entityIds.isEmpty()) {
            return;
        }

        new SQLBatch() {
            @Override
            protected void scripts() {
                sql("DELETE FROM ExternalTenantResourceRefVO WHERE resourceUuid IN (:uuids)")
                    .param("uuids", entityIds)
                    .execute();
            }
        }.execute();
    }
}