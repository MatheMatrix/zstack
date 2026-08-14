package org.zstack.header.network.l2;

import org.zstack.header.errorcode.ErrorCode;

/** Provider hook for confirmed remote-first L2 deletion. */
public interface L2DeleteConfirmExtensionPoint {
    boolean supports(L2NetworkInventory inventory);
    ErrorCode begin(L2NetworkInventory inventory);
    default ErrorCode begin(L2NetworkInventory inventory, NetworkDeletionContext context) {
        return begin(inventory);
    }
    ErrorCode check(L2NetworkInventory inventory);
    default ErrorCode check(L2NetworkInventory inventory, NetworkDeletionContext context) {
        return check(inventory);
    }
    ErrorCode delete(L2NetworkInventory inventory);
    default ErrorCode delete(L2NetworkInventory inventory, NetworkDeletionContext context) {
        return delete(inventory);
    }
    ErrorCode cancel(L2NetworkInventory inventory);
    default ErrorCode cancel(L2NetworkInventory inventory, NetworkDeletionContext context) {
        return cancel(inventory);
    }
    void deleteLocalMetadata(L2NetworkInventory inventory);
    default void deleteLocalMetadata(L2NetworkInventory inventory, NetworkDeletionContext context) {
        deleteLocalMetadata(inventory);
    }
}
