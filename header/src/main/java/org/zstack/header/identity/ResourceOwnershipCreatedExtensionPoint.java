package org.zstack.header.identity;

/**
 * 资源归属创建后扩展点。
 * 在 AccountManagerImpl 创建 AccountResourceRefVO 之后回调。
 * 用于在资源归属确定后执行附加操作（如外部租户关联）。
 */
public interface ResourceOwnershipCreatedExtensionPoint {
    /**
     * 资源归属创建后回调。
     *
     * @param ref     刚创建的资源归属记录
     * @param session 当前请求的 Session（可能为 null）
     */
    void afterResourceOwnershipCreated(AccountResourceRefVO ref, SessionInventory session);
}