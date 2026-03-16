package org.zstack.header.identity;

import java.io.Serializable;

/**
 * 外部租户上下文 DTO。
 * 由外部服务（如 ZCF、AIOS 等）通过 HTTP Header 传递，
 * 附加在 SessionInventory 上贯穿整个请求链路。
 */
public class ExternalTenantContext implements Serializable {
    private static final long serialVersionUID = 1L;

    // ThreadLocal 用于在 AOP 层面传递当前请求的外部租户上下文
    // 由 RestServer 在 Header 解析后设置，请求结束后清理
    private static final ThreadLocal<ExternalTenantContext> current = new ThreadLocal<>();

    public static void setCurrent(ExternalTenantContext ctx) {
        current.set(ctx);
    }

    public static ExternalTenantContext getCurrent() {
        return current.get();
    }

    public static void clearCurrent() {
        current.remove();
    }

    private String source;      // 来源服务标识，如 "zcf", "svcX"
    private String tenantId;    // 外部租户标识
    private String userId;      // 外部用户标识（可选）

    public ExternalTenantContext() {
    }

    public ExternalTenantContext(String source, String tenantId, String userId) {
        this.source = source;
        this.tenantId = tenantId;
        this.userId = userId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    @Override
    public String toString() {
        return String.format("ExternalTenantContext{source='%s', tenantId='%s', userId='%s'}", source, tenantId, userId);
    }
}