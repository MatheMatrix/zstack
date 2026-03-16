package org.zstack.header.identity;

/**
 * 外部租户 Provider SPI。
 * 每个外部服务（ZCF、AIOS 等）实现此接口来接入通用租户资源隔离框架。
 *
 * 框架通过 {@link org.zstack.core.componentloader.PluginRegistry} 自动收集所有实现。
 * 每个 Provider 通过 {@link #getSource()} 返回唯一的来源标识（如 "zcf"），
 * 对应 HTTP Header X-Tenant-Source 的值。
 */
public interface ExternalTenantProvider {
    /**
     * 来源标识，如 "zcf", "svcX"。
     * 对应 X-Tenant-Source header 值。
     * 必须全局唯一。
     */
    String getSource();

    /**
     * 校验 tenant 上下文合法性。
     * 在 RestServer 解析 Header 后、注入 Session 前调用。
     * 抛出异常表示校验失败，请求将被拒绝。
     *
     * @param ctx 外部租户上下文（已从 Header 解析）
     */
    void validateTenant(ExternalTenantContext ctx);

    /**
     * 是否需要跟踪此类资源。
     * 资源创建后框架调用此方法决定是否写入 ExternalTenantResourceRefVO。
     * 返回 false 表示该资源类型不需要关联 tenant。
     * 默认 true（全部跟踪）。
     *
     * @param resourceType 资源类型（VO SimpleName，如 "VmInstanceVO"）
     */
    default boolean shouldTrackResource(String resourceType) {
        return true;
    }

    /**
     * 资源关联后回调（可选）。
     * 在 ExternalTenantResourceRefVO 写入后调用，
     * Provider 可用于自定义逻辑如发通知、写审计日志。
     *
     * @param ctx          外部租户上下文
     * @param resourceUuid 资源 UUID
     * @param resourceType 资源类型（VO SimpleName）
     */
    default void onResourceBound(ExternalTenantContext ctx,
                                  String resourceUuid, String resourceType) {
    }
}