package org.zstack.test.compute.vmnic;

import org.zstack.header.core.Completion;
import org.zstack.header.core.NoErrorCompletion;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.vm.VmNicInventory;
import org.zstack.header.vm.VmNicLifecycleExtensionPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Integration-test stub for {@link VmNicLifecycleExtensionPoint}.
 *
 * Registered via {@code springConfigXml/VmNicLifecycleExtension.xml}.
 * Each PH-5 integration Case obtains the instance via {@code bean(...)},
 * scripts behaviour (filter, errors), runs a VM lifecycle scenario, then
 * asserts the recorded call log.
 */
public class TestVmNicLifecycleExtension implements VmNicLifecycleExtensionPoint {

    public enum Op { SETUP, CLEANUP, PRE_MIGRATE, POST_MIGRATE, FAILED_MIGRATE, CLEANUP_STALE, RECONCILE }

    public static class Call {
        public final Op op;
        public final String hostUuid;
        public final List<String> nicUuids;
        public Call(Op op, String hostUuid, List<VmNicInventory> nics) {
            this.op = op;
            this.hostUuid = hostUuid;
            List<String> ids = new ArrayList<>();
            if (nics != null) {
                for (VmNicInventory n : nics) { ids.add(n.getUuid()); }
            }
            this.nicUuids = ids;
        }
    }

    private final List<Call> calls = new CopyOnWriteArrayList<>();

    // Default false so this stub is inert in tests that don't explicitly enable it.
    // Tests call reset() which restores applicable=true before each scenario.
    private volatile boolean applicable = false;
    private volatile ErrorCode setupError;
    private volatile ErrorCode preMigrateError;

    public List<Call> getCalls() { return calls; }

    public void reset() {
        calls.clear();
        setupError = null;
        preMigrateError = null;
        applicable = true;
    }

    public void setApplicable(boolean a) { this.applicable = a; }
    public void setSetupError(ErrorCode e) { this.setupError = e; }
    public void setPreMigrateError(ErrorCode e) { this.preMigrateError = e; }

    public List<Call> callsOf(Op op) {
        List<Call> r = new ArrayList<>();
        for (Call c : calls) { if (c.op == op) r.add(c); }
        return r;
    }

    @Override
    public boolean isApplicable(VmNicInventory nic) { return applicable; }

    @Override
    public void setupOnHost(String hostUuid, List<VmNicInventory> nics, Completion completion) {
        calls.add(new Call(Op.SETUP, hostUuid, nics));
        if (setupError != null) { completion.fail(setupError); return; }
        completion.success();
    }

    @Override
    public void cleanupFromHost(String hostUuid, List<VmNicInventory> nics, NoErrorCompletion completion) {
        calls.add(new Call(Op.CLEANUP, hostUuid, nics));
        completion.done();
    }

    @Override
    public void preMigrate(String srcHostUuid, String destHostUuid,
                           List<VmNicInventory> nics, Completion completion) {
        calls.add(new Call(Op.PRE_MIGRATE, destHostUuid, nics));
        if (preMigrateError != null) { completion.fail(preMigrateError); return; }
        completion.success();
    }

    @Override
    public void postMigrate(String srcHostUuid, String destHostUuid,
                            List<VmNicInventory> nics, Completion completion) {
        calls.add(new Call(Op.POST_MIGRATE, srcHostUuid, nics));
        completion.success();
    }

    @Override
    public void failedMigrate(String srcHostUuid, String destHostUuid,
                              List<VmNicInventory> nics, NoErrorCompletion completion) {
        calls.add(new Call(Op.FAILED_MIGRATE, destHostUuid, nics));
        completion.done();
    }

    @Override
    public void cleanupStaleResource(String lastHostUuid, List<VmNicInventory> nics,
                                     NoErrorCompletion completion) {
        calls.add(new Call(Op.CLEANUP_STALE, lastHostUuid, nics));
        completion.done();
    }

    @Override
    public void reconcileOnHost(String hostUuid, List<VmNicInventory> expectedNics,
                                NoErrorCompletion completion) {
        calls.add(new Call(Op.RECONCILE, hostUuid, expectedNics));
        completion.done();
    }
}
