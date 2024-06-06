package org.zstack.identity.imports.entity;

import org.zstack.identity.imports.message.SyncThirdPartyAccountMsg;

/**
 * <p>When third party source syncing, how to deal with the deleted users
 *
 * <p>This enum is used by:
 * <li>{@link org.zstack.identity.imports.AccountImportsGlobalConfig#SYNC_RETIREES_STRATEGY}
 * <li>{@link SyncThirdPartyAccountMsg#getForRetirees()}
 */
public enum SyncRetireesStrategy {
    /**
     * Do not destroy AccountVO.
     */
    NoAction,
    /**
     * Destroy AccountVO.
     */
    DestroyAccount,
    /**
     * Only disable AccountVO
     */
    DisableAccount,
}
