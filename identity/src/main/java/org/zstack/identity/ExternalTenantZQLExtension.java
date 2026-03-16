package org.zstack.identity;

import org.zstack.core.db.EntityMetadata;
import org.zstack.header.identity.ExternalTenantContext;
import org.zstack.header.identity.SessionInventory;
import org.zstack.header.zql.ASTNode;
import org.zstack.header.zql.MarshalZQLASTTreeExtensionPoint;
import org.zstack.header.zql.RestrictByExprExtensionPoint;
import org.zstack.header.zql.ZQLExtensionContext;
import org.zstack.zql.ZQLContext;
import org.zstack.zql.ast.ZQLMetadata;

/**
 * ZQL 扩展：当请求携带外部租户上下文时，自动注入资源过滤条件。
 *
 * 工作原理（与 IdentityZQLExtension 相同的两阶段模式）：
 * 1. marshalZQLASTTree() —— 在 AST 树上插入一个占位 RestrictExpr
 * 2. restrictByExpr()    —— 将占位符展开为实际的 SQL 子查询
 *
 * 过滤 SQL 形如：
 *   entity.uuid IN (SELECT ref.resourceUuid FROM ExternalTenantResourceRefVO ref
 *                   WHERE ref.source = :source AND ref.tenantId = :tenantId)
 */
public class ExternalTenantZQLExtension implements MarshalZQLASTTreeExtensionPoint, RestrictByExprExtensionPoint {

    private static final String ENTITY_NAME = "__EXTERNAL_TENANT_FILTER__";
    private static final String ENTITY_FIELD = "__EXTERNAL_TENANT_FILTER_FIELD__";

    @Override
    public void marshalZQLASTTree(ASTNode.Query node) {
        SessionInventory session = ZQLContext.getAPISession();
        if (session == null || !session.hasExternalTenant()) {
            return;
        }

        ASTNode.RestrictExpr expr = new ASTNode.RestrictExpr();
        expr.setEntity(ENTITY_NAME);
        expr.setField(ENTITY_FIELD);

        node.addRestrictExpr(expr);
    }

    @Override
    public String restrictByExpr(ZQLExtensionContext context, ASTNode.RestrictExpr expr) {
        if (!ENTITY_NAME.equals(expr.getEntity()) || !ENTITY_FIELD.equals(expr.getField())) {
            return null;
        }

        SessionInventory session = context.getAPISession();
        if (session == null || !session.hasExternalTenant()) {
            throw new SkipThisRestrictExprException();
        }

        ExternalTenantContext tenantCtx = session.getExternalTenantContext();
        if (tenantCtx == null || tenantCtx.getSource() == null || tenantCtx.getTenantId() == null) {
            throw new SkipThisRestrictExprException();
        }

        ZQLMetadata.InventoryMetadata src = ZQLMetadata.getInventoryMetadataByName(context.getQueryTargetInventoryName());
        String primaryKey = EntityMetadata.getPrimaryKeyField(src.inventoryAnnotation.mappingVOClass()).getName();
        String inventoryAlias = src.simpleInventoryName();

        // 生成子查询，按 source + tenantId 过滤关联资源
        return String.format(
                "(%s.%s IN (SELECT etref.resourceUuid FROM ExternalTenantResourceRefVO etref" +
                " WHERE etref.source = '%s' AND etref.tenantId = '%s'))",
                inventoryAlias,
                primaryKey,
                escapeSql(tenantCtx.getSource()),
                escapeSql(tenantCtx.getTenantId())
        );
    }

    /**
     * 简单 SQL 转义，防止注入。
     * 外部租户信息已经在 RestServer 中经过 Provider.validateTenant() 校验，
     * 这里做二次保险。
     */
    private static String escapeSql(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("'", "''").replace("\\", "\\\\");
    }
}
