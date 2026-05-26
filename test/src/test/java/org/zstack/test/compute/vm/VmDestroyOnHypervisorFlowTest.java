package org.zstack.test.compute.vm;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.zstack.compute.vm.VmDestroyOnHypervisorFlow;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.header.core.workflow.FlowTrigger;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.host.HostStatus;
import org.zstack.header.host.HostVO;
import org.zstack.header.vm.VmInstanceConstant;
import org.zstack.header.vm.VmInstanceInventory;
import org.zstack.header.vm.VmInstanceSpec;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class VmDestroyOnHypervisorFlowTest {

    private DatabaseFacade mockDbf;
    private CloudBus mockBus;

    @Before
    public void setUp() {
        mockDbf = Mockito.mock(DatabaseFacade.class);
        mockBus = Mockito.mock(CloudBus.class);
    }

    private VmDestroyOnHypervisorFlow createFlow() throws Exception {
        VmDestroyOnHypervisorFlow flow = new VmDestroyOnHypervisorFlow();
        Field dbfField = VmDestroyOnHypervisorFlow.class.getDeclaredField("dbf");
        dbfField.setAccessible(true);
        dbfField.set(flow, mockDbf);
        Field busField = VmDestroyOnHypervisorFlow.class.getDeclaredField("bus");
        busField.setAccessible(true);
        busField.set(flow, mockBus);
        return flow;
    }

    private Map<String, Object> createData(String hostUuid, String vmUuid, String vmState) {
        VmInstanceInventory vmInv = new VmInstanceInventory();
        vmInv.setUuid(vmUuid);
        vmInv.setHostUuid(hostUuid);
        vmInv.setClusterUuid("some-cluster-uuid");
        vmInv.setState(vmState);

        VmInstanceSpec spec = new VmInstanceSpec();
        spec.setVmInventory(vmInv);

        Map<String, Object> data = new HashMap<>();
        data.put(VmInstanceConstant.Params.VmInstanceSpec.toString(), spec);
        return data;
    }

    @Test
    public void isHostDisconnectedTrueWhenDisconnected() throws Exception {
        VmDestroyOnHypervisorFlow flow = createFlow();
        HostVO host = new HostVO();
        host.setUuid("host-1");
        host.setStatus(HostStatus.Disconnected);
        Mockito.when(mockDbf.findByUuid("host-1", HostVO.class)).thenReturn(host);

        Method m = VmDestroyOnHypervisorFlow.class.getDeclaredMethod("isHostDisconnected", String.class);
        m.setAccessible(true);
        Assert.assertTrue((Boolean) m.invoke(flow, "host-1"));
    }

    @Test
    public void isHostDisconnectedFalseWhenConnected() throws Exception {
        VmDestroyOnHypervisorFlow flow = createFlow();
        HostVO host = new HostVO();
        host.setUuid("host-1");
        host.setStatus(HostStatus.Connected);
        Mockito.when(mockDbf.findByUuid("host-1", HostVO.class)).thenReturn(host);

        Method m = VmDestroyOnHypervisorFlow.class.getDeclaredMethod("isHostDisconnected", String.class);
        m.setAccessible(true);
        Assert.assertFalse((Boolean) m.invoke(flow, "host-1"));
    }

    @Test
    public void isHostDisconnectedFalseWhenHostNotFound() throws Exception {
        VmDestroyOnHypervisorFlow flow = createFlow();
        Mockito.when(mockDbf.findByUuid("host-1", HostVO.class)).thenReturn(null);

        Method m = VmDestroyOnHypervisorFlow.class.getDeclaredMethod("isHostDisconnected", String.class);
        m.setAccessible(true);
        Assert.assertFalse((Boolean) m.invoke(flow, "host-1"));
    }

    @Test
    public void isHostDisconnectedFalseWhenConnecting() throws Exception {
        VmDestroyOnHypervisorFlow flow = createFlow();
        HostVO host = new HostVO();
        host.setUuid("host-1");
        host.setStatus(HostStatus.Connecting);
        Mockito.when(mockDbf.findByUuid("host-1", HostVO.class)).thenReturn(host);

        Method m = VmDestroyOnHypervisorFlow.class.getDeclaredMethod("isHostDisconnected", String.class);
        m.setAccessible(true);
        Assert.assertFalse((Boolean) m.invoke(flow, "host-1"));
    }

    @Test
    public void stoppedVmSkipsDestroy() throws Exception {
        VmDestroyOnHypervisorFlow flow = createFlow();
        Map<String, Object> data = createData("host-1", "vm-1", "Stopped");

        AtomicReference<Boolean> wentNext = new AtomicReference<>(false);
        FlowTrigger trigger = new FlowTrigger() {
            @Override
            public void fail(ErrorCode errorCode) {
                Assert.fail("should skip destroy for Stopped VM");
            }

            @Override
            public void next() {
                wentNext.set(true);
            }

            @Override
            public void setError(ErrorCode errorCode) {
            }
        };

        flow.run(trigger, data);

        Assert.assertTrue("Stopped VM should skip destroy and go next", wentNext.get());
    }
}
