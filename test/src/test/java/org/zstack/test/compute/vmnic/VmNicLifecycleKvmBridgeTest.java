package org.zstack.test.compute.vmnic;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.zstack.core.componentloader.PluginRegistry;
import org.zstack.core.thread.ThreadFacade;
import org.zstack.header.core.NoErrorCompletion;
import org.zstack.header.vm.VmNicLifecycleExtensionPoint;
import org.zstack.kvm.KVMHostInventory;
import org.zstack.kvm.VmNicLifecycleKvmBridge;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * PH-4 TP-039: verify KvmBridge short-circuits when no extensions are
 * registered (no DB queries, no thread dispatch).
 *
 * Deeper reconcile paths (TP-040..TP-045) require Spring + simulated DB and
 * are covered by PH-5 integration tests.
 */
public class VmNicLifecycleKvmBridgeTest {

    private PluginRegistry pluginRgty;
    private ThreadFacade thdf;
    private VmNicLifecycleKvmBridge bridge;

    @Before
    public void setUp() {
        pluginRgty = Mockito.mock(PluginRegistry.class);
        thdf = Mockito.mock(ThreadFacade.class);
        bridge = new VmNicLifecycleKvmBridge();
        setField(bridge, "pluginRgty", pluginRgty);
        setField(bridge, "thdf", thdf);
    }

    private static void setField(Object t, String name, Object v) {
        try {
            Field f = t.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(t, v);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void kvmPingAgentNoFailure_noExtensions_shortCircuits() {
        Mockito.when(pluginRgty.getExtensionList(VmNicLifecycleExtensionPoint.class))
                .thenReturn(Collections.emptyList());

        KVMHostInventory host = Mockito.mock(KVMHostInventory.class);
        Mockito.when(host.getUuid()).thenReturn("host-1");

        AtomicBoolean done = new AtomicBoolean();
        bridge.kvmPingAgentNoFailure(host, new NoErrorCompletion() {
            @Override public void done() { done.set(true); }
        });

        Assert.assertTrue("must complete immediately when no extensions", done.get());
        // no DB call, no thread facade call
        Mockito.verifyNoInteractions(thdf);
    }
}
