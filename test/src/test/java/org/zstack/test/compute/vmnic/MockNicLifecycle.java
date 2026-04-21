package org.zstack.test.compute.vmnic;

import org.zstack.header.core.Completion;
import org.zstack.header.core.NoErrorCompletion;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.vm.VmNicInventory;
import org.zstack.header.vm.VmNicLifecycleExtensionPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * Testing stub for {@link VmNicLifecycleExtensionPoint}.
 * Records every call and allows scripted success / failure / throw.
 */
public class MockNicLifecycle implements VmNicLifecycleExtensionPoint {

    public final List<String> setupCalls = new ArrayList<>();
    public final List<String> cleanupCalls = new ArrayList<>();
    public final List<String> preMigrateCalls = new ArrayList<>();
    public final List<String> postMigrateCalls = new ArrayList<>();
    public final List<String> failedMigrateCalls = new ArrayList<>();
    public final List<String> cleanupStaleCalls = new ArrayList<>();
    public final List<String> reconcileCalls = new ArrayList<>();
    public final List<List<VmNicInventory>> setupNicArgs = new ArrayList<>();

    public Predicate<VmNicInventory> applicableFilter = nic -> true;
    public boolean isApplicableThrows = false;

    public ErrorCode setupError = null;
    public boolean setupThrows = false;
    public boolean cleanupThrows = false;
    public ErrorCode preMigrateError = null;
    public ErrorCode postMigrateError = null;
    public boolean reconcileDelayed = false;

    public final AtomicInteger invocationOrder = new AtomicInteger();
    public int setupOrder = -1;
    public int cleanupOrder = -1;

    private final AtomicInteger seq;

    public MockNicLifecycle() {
        this(new AtomicInteger());
    }

    public MockNicLifecycle(AtomicInteger seq) {
        this.seq = seq;
    }

    @Override
    public boolean isApplicable(VmNicInventory nic) {
        if (isApplicableThrows) {
            throw new RuntimeException("isApplicable boom");
        }
        return applicableFilter.test(nic);
    }

    @Override
    public void setupOnHost(String hostUuid, List<VmNicInventory> nics, Completion completion) {
        setupCalls.add(hostUuid);
        setupNicArgs.add(new ArrayList<>(nics));
        setupOrder = seq.incrementAndGet();
        if (setupThrows) {
            throw new RuntimeException("setup boom");
        }
        if (setupError != null) {
            completion.fail(setupError);
            return;
        }
        completion.success();
    }

    @Override
    public void cleanupFromHost(String hostUuid, List<VmNicInventory> nics,
                                NoErrorCompletion completion) {
        cleanupCalls.add(hostUuid);
        cleanupOrder = seq.incrementAndGet();
        if (cleanupThrows) {
            throw new RuntimeException("cleanup boom");
        }
        completion.done();
    }

    @Override
    public void preMigrate(String src, String dst, List<VmNicInventory> nics,
                           Completion completion) {
        preMigrateCalls.add(dst);
        if (preMigrateError != null) {
            completion.fail(preMigrateError);
            return;
        }
        // Delegate to default chain for coverage of default wiring? No, keep direct.
        completion.success();
    }

    @Override
    public void postMigrate(String src, String dst, List<VmNicInventory> nics,
                            Completion completion) {
        postMigrateCalls.add(src);
        if (postMigrateError != null) {
            completion.fail(postMigrateError);
            return;
        }
        completion.success();
    }

    @Override
    public void failedMigrate(String src, String dst, List<VmNicInventory> nics,
                              NoErrorCompletion completion) {
        failedMigrateCalls.add(dst);
        completion.done();
    }

    @Override
    public void cleanupStaleResource(String lastHost, List<VmNicInventory> nics,
                                     NoErrorCompletion completion) {
        cleanupStaleCalls.add(lastHost);
        completion.done();
    }

    @Override
    public void reconcileOnHost(String hostUuid, List<VmNicInventory> expectedNics,
                                NoErrorCompletion completion) {
        reconcileCalls.add(hostUuid);
        if (!reconcileDelayed) {
            completion.done();
        }
        // if delayed, test should drive completion explicitly
    }
}
