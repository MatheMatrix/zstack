package org.zstack.identity.imports.entity;

import org.zstack.identity.imports.message.SyncThirdPartyAccountMsg;

/**
 * <p>When third party source syncing, how to deal with the deleted users
 *
 * <p>This enum is used by:
 * <li>{@link SyncThirdPartyAccountMsg#getDeleteAccountStrategy()}
 */
public enum SyncDeletedAccountStrategy {
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
