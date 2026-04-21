package org.zstack.test.compute.vmnic;

import org.junit.Assert;
import org.junit.Test;
import org.zstack.header.core.Completion;
import org.zstack.header.core.NoErrorCompletion;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.vm.VmNicInventory;
import org.zstack.header.vm.VmNicLifecycleExtensionPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * PH-4 TP-001 ~ TP-005: verify the 5 default methods on
 * {@link VmNicLifecycleExtensionPoint} chain to setupOnHost / cleanupFromHost
 * with the expected host argument and fail-logged semantics.
 */
public class VmNicLifecycleExtensionPointDefaultTest {

    private static final org.zstack.header.core.AsyncBackup BK =
            new org.zstack.header.core.AsyncBackup() {};

    /** Minimum impl exposing only the two mandatory methods. */
    static class MinimalImpl implements VmNicLifecycleExtensionPoint {
        String lastSetupHost;
        String lastCleanupHost;
        boolean cleanupShouldFail;  // cleanup itself never signals failure (NoErrorCompletion)
        ErrorCode setupError;

        @Override
        public boolean isApplicable(VmNicInventory nic) { return true; }

        @Override
        public void setupOnHost(String hostUuid, List<VmNicInventory> nics,
                                Completion completion) {
            lastSetupHost = hostUuid;
            if (setupError != null) {
                completion.fail(setupError);
                return;
            }
            completion.success();
        }

        @Override
        public void cleanupFromHost(String hostUuid, List<VmNicInventory> nics,
                                    NoErrorCompletion completion) {
            lastCleanupHost = hostUuid;
            completion.done();
        }
    }

    private static Completion successExpected(AtomicBoolean flag) {
        return new Completion(BK) {
            @Override public void success() { flag.set(true); }
            @Override public void fail(ErrorCode errorCode) {
                Assert.fail("unexpected fail: " + errorCode);
            }
        };
    }

    private static NoErrorCompletion doneExpected(AtomicBoolean flag) {
        return new NoErrorCompletion() {
            @Override public void done() { flag.set(true); }
        };
    }

    @Test
    public void preMigrate_default_delegates_to_setup_on_dest_host() {
        MinimalImpl impl = new MinimalImpl();
        AtomicBoolean ok = new AtomicBoolean();
        impl.preMigrate("src-1", "dest-1", new ArrayList<>(), successExpected(ok));
        Assert.assertTrue(ok.get());
        Assert.assertEquals("dest-1", impl.lastSetupHost);
        Assert.assertNull(impl.lastCleanupHost);
    }

    @Test
    public void postMigrate_default_delegates_to_cleanup_on_src_host_and_succeeds() {
        MinimalImpl impl = new MinimalImpl();
        AtomicBoolean ok = new AtomicBoolean();
        impl.postMigrate("src-2", "dest-2", new ArrayList<>(), successExpected(ok));
        Assert.assertTrue(ok.get());
        Assert.assertEquals("src-2", impl.lastCleanupHost);
    }

    @Test
    public void failedMigrate_default_delegates_to_cleanup_on_dest_host() {
        MinimalImpl impl = new MinimalImpl();
        AtomicBoolean ok = new AtomicBoolean();
        impl.failedMigrate("src-3", "dest-3", new ArrayList<>(), doneExpected(ok));
        Assert.assertTrue(ok.get());
        Assert.assertEquals("dest-3", impl.lastCleanupHost);
    }

    @Test
    public void cleanupStaleResource_default_delegates_to_cleanup_on_last_host() {
        MinimalImpl impl = new MinimalImpl();
        AtomicBoolean ok = new AtomicBoolean();
        impl.cleanupStaleResource("last-host-4", new ArrayList<>(), doneExpected(ok));
        Assert.assertTrue(ok.get());
        Assert.assertEquals("last-host-4", impl.lastCleanupHost);
    }

    @Test
    public void reconcileOnHost_default_is_noop() {
        MinimalImpl impl = new MinimalImpl();
        AtomicBoolean ok = new AtomicBoolean();
        impl.reconcileOnHost("host-5", new ArrayList<>(), doneExpected(ok));
        Assert.assertTrue(ok.get());
        // default must NOT mistakenly invoke setup or cleanup
        Assert.assertNull(impl.lastSetupHost);
        Assert.assertNull(impl.lastCleanupHost);
    }

    @Test
    public void preMigrate_default_propagates_setup_failure() {
        MinimalImpl impl = new MinimalImpl();
        impl.setupError = new ErrorCode("unit-test-code", "unit-test-desc", "unit");
        AtomicReference<ErrorCode> captured = new AtomicReference<>();
        impl.preMigrate("src-6", "dest-6", new ArrayList<>(), new Completion(BK) {
            @Override public void success() { Assert.fail("expected fail"); }
            @Override public void fail(ErrorCode errorCode) { captured.set(errorCode); }
        });
        Assert.assertNotNull(captured.get());
        Assert.assertEquals("unit-test-code", captured.get().getCode());
    }
}
