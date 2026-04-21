package org.zstack.header.vm;

import org.zstack.header.core.Completion;
import org.zstack.header.core.NoErrorCompletion;

import java.util.List;

/**
 * Unified abstraction for the lifecycle of host-bound VM NIC resources
 * (e.g., OVS DPDK ports, security-group rules).
 *
 * <p>Implementers only need to describe <em>how to create, delete, and
 * reconcile</em> NIC resources on a single host.
 * {@code VmNicLifecycleManager} then routes VM start/stop, migration
 * (pre/post/failed), HA failover, and host-reconnect events from 6+
 * scattered extension points down to this interface automatically.
 *
 * <p>See docs/plans/vmnic-lifecycle-resource-abstraction-func-spec.md
 * section F-001 for the full design.
 */
public interface VmNicLifecycleExtensionPoint {

    /**
     * Returns {@code true} if this implementation cares about the given NIC.
     * NICs that return {@code false} are skipped entirely by the Manager.
     */
    boolean isApplicable(VmNicInventory nic);

    /**
     * Creates resources for a batch of NICs on the specified host.
     * The Manager invokes this with fail-fast semantics: the first failure
     * is propagated upward and blocks any subsequent work.
     */
    void setupOnHost(String hostUuid, List<VmNicInventory> nics, Completion completion);

    /**
     * Removes resources for a batch of NICs from the specified host.
     * The Manager invokes this with fail-logged semantics: a single item
     * failure is logged but does not block the remaining cleanup.
     */
    void cleanupFromHost(String hostUuid, List<VmNicInventory> nics, NoErrorCompletion completion);

    /**
     * Pre-migration: pre-creates resources on the destination host before
     * the VM moves. Defaults to {@code setupOnHost(destHostUuid, nics)}.
     */
    default void preMigrate(String srcHostUuid, String destHostUuid,
                            List<VmNicInventory> nics, Completion completion) {
        setupOnHost(destHostUuid, nics, completion);
    }

    /**
     * Post-migration: cleans up resources on the source host after a
     * successful migration. The VM is already running on the destination at
     * this point, so any failure must only be logged (fail-logged) and must
     * not be propagated upward.
     */
    default void postMigrate(String srcHostUuid, String destHostUuid,
                             List<VmNicInventory> nics, Completion completion) {
        cleanupFromHost(srcHostUuid, nics, new NoErrorCompletion(completion) {
            @Override
            public void done() {
                completion.success();
            }
        });
    }

    /**
     * Migration rollback: cleans up resources that were pre-created on the
     * destination host when the migration fails. Defaults to
     * {@code cleanupFromHost(destHostUuid, nics)}.
     */
    default void failedMigrate(String srcHostUuid, String destHostUuid,
                               List<VmNicInventory> nics, NoErrorCompletion completion) {
        cleanupFromHost(destHostUuid, nics, completion);
    }

    /**
     * Cleans up stale resources left on a host after an abnormal VM
     * transition (e.g., HA failover). Defaults to
     * {@code cleanupFromHost(lastHostUuid, nics)}.
     */
    default void cleanupStaleResource(String lastHostUuid, List<VmNicInventory> nics,
                                      NoErrorCompletion completion) {
        cleanupFromHost(lastHostUuid, nics, completion);
    }

    /**
     * Host heartbeat reconciliation: compares actual state on the host
     * against {@code expectedNics} and repairs any drift. The default
     * implementation is a no-op (opt-in). The Manager invokes this
     * concurrently with a per-item timeout.
     */
    default void reconcileOnHost(String hostUuid, List<VmNicInventory> expectedNics,
                                 NoErrorCompletion completion) {
        completion.done();
    }
}
