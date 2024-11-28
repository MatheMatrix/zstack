package org.zstack.identity;

/**
 * @author shenjin
 * @date 2024/11/28
 */
public interface RemoveResourceExtensionPoint {
    void removeAssociatedResourceConfig(String accountUuid);
}
