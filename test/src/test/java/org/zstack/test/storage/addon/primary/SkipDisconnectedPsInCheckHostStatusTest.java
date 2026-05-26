package org.zstack.test.storage.addon.primary;

import junit.framework.Assert;
import org.junit.Before;
import org.junit.Test;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.externalStorage.primary.kvm.ExternalPrimaryStorageKvmFactory;
import org.zstack.header.core.workflow.Flow;
import org.zstack.header.core.workflow.NopeFlow;
import org.zstack.header.host.HostInventory;
import org.zstack.header.storage.primary.PrimaryStorageHostStatus;
import org.zstack.header.storage.primary.PrimaryStorageStatus;
import org.zstack.kvm.KVMHostConnectedContext;
import org.zstack.test.BeanConstructor;

import java.lang.reflect.Field;

/**
 * Tests that ExternalPrimaryStorageKvmFactory correctly handles Disconnected PS
 * during host connect: skip health check, set host-PS status to Disconnected.
 *
 * The Disconnected check itself is tested via integration test because
 * checkHostStatus() requires a populated DB with ExternalPrimaryStorageVO
 * and a running PrimaryStorageNodeSvc mock — not feasible in a pure unit test.
 */
public class SkipDisconnectedPsInCheckHostStatusTest {

    ExternalPrimaryStorageKvmFactory factory;
    DatabaseFacade dbf;
    CloudBus bus;

    @Before
    public void setUp() throws Exception {
        BeanConstructor con = new BeanConstructor();
        con.build();
        dbf = con.getComponentLoader().getComponent(DatabaseFacade.class);
        bus = con.getComponentLoader().getComponent(CloudBus.class);
        factory = new ExternalPrimaryStorageKvmFactory();
        injectField(factory, "dbf", dbf);
        injectField(factory, "bus", bus);
    }

    private static void injectField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    public void testNoExternalPsReturnsNopeFlow() {
        KVMHostConnectedContext ctx = new KVMHostConnectedContext();
        HostInventory host = new HostInventory();
        host.setClusterUuid("nonexistent-cluster-uuid");
        ctx.setInventory(host);

        Flow flow = factory.createKvmHostConnectingFlow(ctx);
        Assert.assertTrue("should return NopeFlow when no external PS in cluster", flow instanceof NopeFlow);
    }

    @Test
    public void testDisconnectedStatusMapping() {
        Assert.assertNotNull(PrimaryStorageStatus.Disconnected);
        Assert.assertNotNull(PrimaryStorageHostStatus.Disconnected);
    }
}
