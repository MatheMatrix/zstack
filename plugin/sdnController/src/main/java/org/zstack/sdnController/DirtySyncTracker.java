package org.zstack.sdnController;

/**
 * Tracks which SDN controllers need a full reconciliation sync.
 *
 * When external operations (VM NIC create/delete, migration rollback, etc.) encounter failures
 * that may leave Cloud and SDN controller out of sync, they call {@link #markNeedSync} to flag
 * the affected controller.
 *
 * The ping tracker checks this flag after consecutive successful pings and triggers a full sync
 * when needed.
 */
public interface DirtySyncTracker {
    /**
     * Mark a controller as needing a full sync.
     * Idempotent — calling multiple times has no additional effect.
     */
    void markNeedSync(String controllerUuid);

    /**
     * Check if a controller needs a sync.
     */
    boolean needsSync(String controllerUuid);

    /**
     * Atomically clear the needsSync flag and return its previous value.
     * @return true if the controller was marked as needing sync, false otherwise.
     */
    boolean clearNeedSync(String controllerUuid);
}
