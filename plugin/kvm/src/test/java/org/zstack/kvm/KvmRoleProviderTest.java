package org.zstack.kvm;

import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.http.HttpEntity;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.cloudbus.CloudBusCallBack;
import org.zstack.core.cloudbus.CloudBusEventListener;
import org.zstack.core.cloudbus.CloudBusListCallBack;
import org.zstack.core.cloudbus.CloudBusSteppingCallback;
import org.zstack.core.cloudbus.EventSubscriberReceipt;
import org.zstack.core.db.Q;
import org.zstack.core.db.SQL;
import org.zstack.header.allocator.HostCapacityVO;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.errorcode.OperationFailureException;
import org.zstack.header.exception.CloudConfigureFailException;
import org.zstack.header.core.Completion;
import org.zstack.header.core.FutureCompletion;
import org.zstack.header.core.ReturnValueCompletion;
import org.zstack.header.host.AddHostReply;
import org.zstack.header.host.HostDeletionMsg;
import org.zstack.header.host.HostConstant;
import org.zstack.header.host.HostInventory;
import org.zstack.header.message.*;
import org.zstack.header.server.*;
import org.zstack.header.vm.VmInstanceState;
import org.zstack.header.vm.VmInstanceVO;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link KvmRoleProvider} covering Phase 2C U8 fix scope.
 *
 * <p>CloudBus mocking strategy: mockito-inline 4.11.0 cannot inline-mock
 * {@code CloudBus extends Component} on Java 8 (byte-buddy cannot instrument both
 * interfaces simultaneously when {@code javax.servlet} classes are absent at
 * instrumentation time). Instead a {@link FakeCloudBus} hand-written stub is used
 * for all tests that exercise {@code CloudBus} interactions. {@code mockito-inline}
 * is retained solely for {@code Mockito.mockStatic(Q.class)} and
 * {@code Mockito.mockStatic(SQL.class)}.
 */
public class KvmRoleProviderTest {

    private KvmRoleProvider provider;

    @Before
    public void setUp() throws Exception {
        provider = new KvmRoleProvider();
    }

    // -------------------------------------------------------------------------
    // Identity
    // -------------------------------------------------------------------------

    @Test
    public void getRoleType_returns_KVM_HOST() {
        assertEquals(ServerRoleType.KVM_HOST, provider.getRoleType());
    }

    @Test
    public void getSchedulingMode_returns_INTERNAL_SHARED() {
        assertEquals(SchedulingMode.INTERNAL_SHARED, provider.getSchedulingMode());
    }

    // -------------------------------------------------------------------------
    // createRoleEntity — validation edges (sync throw, no bus needed)
    // -------------------------------------------------------------------------

    // Validation throw routing: in production AspectJ-woven runtime, AsyncSafeAspect
    // (core/.../AsyncSafeAspect.aj) intercepts the sync throw and routes the ErrorCode
    // to completion.fail(). In the focused unit-test runtime the aspect may not be
    // wired, in which case the throw propagates raw. Either path is correct as long
    // as the same ErrorCode surfaces; we assert both shapes.

    @Test
    public void missing_username_carries_10165() {
        assertValidationCarriesCode(
                ctxWithCreds(null, "pass", null, "cluster-1", "10.0.0.1"),
                "ORG_ZSTACK_KVM_10165");
    }

    @Test
    public void empty_username_carries_10165() {
        assertValidationCarriesCode(
                ctxWithCreds("", "pass", null, "cluster-1", "10.0.0.1"),
                "ORG_ZSTACK_KVM_10165");
    }

    @Test
    public void missing_password_carries_10163() {
        assertValidationCarriesCode(
                ctxWithCreds("root", null, null, "cluster-1", "10.0.0.1"),
                "ORG_ZSTACK_KVM_10163");
    }

    @Test
    public void missing_clusterUuid_carries_10166() {
        assertValidationCarriesCode(
                ctxWithCreds("root", "pass", null, null, "10.0.0.1"),
                "ORG_ZSTACK_KVM_10166");
    }

    @Test
    public void missing_managementIp_carries_10166() {
        assertValidationCarriesCode(
                ctxWithCreds("root", "pass", null, "cluster-1", null),
                "ORG_ZSTACK_KVM_10166");
    }

    private void assertValidationCarriesCode(CreateRoleEntityContext ctx, String expectedCode) {
        CapturingRvComp comp = new CapturingRvComp();
        try {
            provider.createRoleEntity(ctx, comp);
            // Aspect-routed path
            assertTrue("expected fail() invoked or throw", comp.failCalled);
            assertEquals(expectedCode, comp.errorCode.getCode());
        } catch (OperationFailureException e) {
            // Raw-throw path
            assertEquals(expectedCode, e.getErrorCode().getCode());
        }
    }

    // -------------------------------------------------------------------------
    // createRoleEntity — async happy / failure paths
    // -------------------------------------------------------------------------

    @Test
    public void builds_AddKVMHostMsg_with_expected_fields() throws Exception {
        AddHostReply okReply = new AddHostReply();
        HostInventory inv = new HostInventory();
        inv.setUuid("h1");
        okReply.setInventory(inv);

        FakeCloudBus bus = new FakeCloudBus(okReply);
        injectField(provider, "bus", bus);

        CreateRoleEntityContext ctx = new CreateRoleEntityContext()
                .setServerUuid("server-uuid-1")
                .setClusterUuid("cluster-uuid-1")
                .setManagementIp("192.168.1.10")
                .setAccountUuid("account-uuid-1")
                .setRoleConfig(roleConfig("root", "secret", "2222"));

        CapturingRvComp comp = new CapturingRvComp();
        provider.createRoleEntity(ctx, comp);

        assertTrue("expected success() invoked", comp.successCalled);
        assertEquals("h1", comp.successValue);
        assertNotNull(bus.lastSentMsg);
        assertTrue("Expected AddKVMHostMsg", bus.lastSentMsg instanceof AddKVMHostMsg);
        AddKVMHostMsg sent = (AddKVMHostMsg) bus.lastSentMsg;
        assertEquals("root", sent.getUsername());
        assertEquals("secret", sent.getPassword());
        assertEquals(2222, sent.getSshPort());
        assertEquals("192.168.1.10", sent.getManagementIp());
        assertEquals("cluster-uuid-1", sent.getClusterUuid());
        assertEquals("server-uuid-1", sent.getServerUuid());
        assertEquals("account-uuid-1", sent.getAccountUuid());
        // makeLocalServiceId was invoked on the same message
        assertEquals(bus.lastLocalServiceIdMsg, sent);
        assertEquals(HostConstant.SERVICE_ID, bus.lastLocalServiceId);
    }

    @Test
    public void bus_send_failure_propagates_same_errorCode() throws Exception {
        ErrorCode errCode = new ErrorCode();
        errCode.setCode("ORG_ZSTACK_KVM_10161");
        errCode.setDescription("ssh connect failed");

        MessageReply failReply = new MessageReply();
        failReply.setError(errCode);

        FakeCloudBus bus = new FakeCloudBus(failReply);
        injectField(provider, "bus", bus);

        CreateRoleEntityContext ctx = new CreateRoleEntityContext()
                .setServerUuid("s1")
                .setClusterUuid("c1")
                .setManagementIp("10.0.0.1")
                .setRoleConfig(roleConfig("root", "pass", null));

        CapturingRvComp comp = new CapturingRvComp();
        provider.createRoleEntity(ctx, comp);

        assertTrue("expected fail() invoked", comp.failCalled);
        assertSame(errCode, comp.errorCode);
    }

    // -------------------------------------------------------------------------
    // deleteRoleEntity — async
    // -------------------------------------------------------------------------

    @Test
    public void bus_send_with_HostDeletionMsg_correct_uuid_and_routing() throws Exception {
        FakeCloudBus bus = new FakeCloudBus(new MessageReply());
        injectField(provider, "bus", bus);

        CapturingComp comp = new CapturingComp();
        provider.deleteRoleEntity("role-uuid-7", comp);

        assertTrue("expected success() invoked", comp.successCalled);
        assertNotNull(bus.lastSentMsg);
        assertTrue("Expected HostDeletionMsg", bus.lastSentMsg instanceof HostDeletionMsg);
        HostDeletionMsg sent = (HostDeletionMsg) bus.lastSentMsg;
        assertEquals("role-uuid-7", sent.getHostUuid());
        // routing: makeTargetServiceIdByResourceUuid(msg, HostConstant.SERVICE_ID, roleUuid)
        assertEquals(bus.lastTargetServiceIdMsg, sent);
        assertEquals(HostConstant.SERVICE_ID, bus.lastTargetServiceId);
        assertEquals("role-uuid-7", bus.lastTargetServiceIdResourceUuid);
    }

    @Test
    public void deleteRoleEntity_bus_send_failure_propagates_same_errorCode() throws Exception {
        ErrorCode errCode = new ErrorCode();
        errCode.setCode("ORG_ZSTACK_KVM_10160");
        errCode.setDescription("host not found");

        MessageReply failReply = new MessageReply();
        failReply.setError(errCode);

        FakeCloudBus bus = new FakeCloudBus(failReply);
        injectField(provider, "bus", bus);

        CapturingComp comp = new CapturingComp();
        provider.deleteRoleEntity("role-uuid-x", comp);

        assertTrue("expected fail() invoked", comp.failCalled);
        assertSame(errCode, comp.errorCode);
    }

    // -------------------------------------------------------------------------
    // getCapacityConsumption
    // -------------------------------------------------------------------------

    @Test
    public void hcv_missing_returns_zero_and_non_exclusive() {
        try (MockedStatic<Q> qStatic = Mockito.mockStatic(Q.class)) {
            Q mockQ = mock(Q.class);
            qStatic.when(() -> Q.New(HostCapacityVO.class)).thenReturn(mockQ);
            when(mockQ.eq(any(), any())).thenReturn(mockQ);
            when(mockQ.find()).thenReturn(null);

            CapacityUsage usage = provider.getCapacityConsumption("server-1", "role-1");

            assertEquals(0L, usage.getUsedCpu());
            assertEquals(0L, usage.getUsedMemory());
            assertFalse(usage.isExclusive());
        }
    }

    @Test
    public void hcv_present_returns_used_values() {
        long expectedCpu = 8L;
        long expectedMem = 17179869184L; // 16 GB

        // getUsedCpu() = totalCpu - availableCpu; getUsedMemory() = totalMemory - availableMemory
        HostCapacityVO hcv = new HostCapacityVO();
        hcv.setTotalCpu(10L);
        hcv.setAvailableCpu(2L);            // usedCpu = 10 - 2 = 8
        hcv.setTotalMemory(expectedMem * 2);
        hcv.setAvailableMemory(expectedMem); // usedMemory = 32GB - 16GB = 16GB

        try (MockedStatic<Q> qStatic = Mockito.mockStatic(Q.class)) {
            Q mockQ = mock(Q.class);
            qStatic.when(() -> Q.New(HostCapacityVO.class)).thenReturn(mockQ);
            when(mockQ.eq(any(), any())).thenReturn(mockQ);
            when(mockQ.find()).thenReturn(hcv);

            CapacityUsage usage = provider.getCapacityConsumption("server-1", "role-1");

            assertEquals(expectedCpu, usage.getUsedCpu());
            assertEquals(expectedMem, usage.getUsedMemory());
        }
    }

    // -------------------------------------------------------------------------
    // getWorkloadStatus
    // -------------------------------------------------------------------------

    @Test
    public void no_active_vms_all_block_reasons_null_and_count_zero() {
        try (MockedStatic<SQL> sqlStatic = Mockito.mockStatic(SQL.class)) {
            stubSqlReturning(sqlStatic, Collections.<VmInstanceVO>emptyList());

            RoleWorkloadStatus status = provider.getWorkloadStatus("server-1", "role-1");

            assertEquals(0, status.getActiveWorkloadCount());
            assertNull(status.getDetachBlockReason());
            assertNull(status.getPowerOffBlockReason());
            assertNull(status.getPowerResetBlockReason());
            assertNull(status.getMigrationBlockReason());
            assertNull(status.getMaintenanceBlockReason());
        }
    }

    @Test
    public void running_vms_set_destructive_reasons_not_maintenance_or_migration() {
        List<VmInstanceVO> vms = Arrays.asList(
                makeVm("vm-1", "vm-one",   VmInstanceState.Running),
                makeVm("vm-2", "vm-two",   VmInstanceState.Running),
                makeVm("vm-3", "vm-three", VmInstanceState.Running)
        );

        try (MockedStatic<SQL> sqlStatic = Mockito.mockStatic(SQL.class)) {
            stubSqlReturning(sqlStatic, vms);

            RoleWorkloadStatus status = provider.getWorkloadStatus("server-1", "role-1");

            assertNotNull(status.getDetachBlockReason());
            assertNotNull(status.getPowerOffBlockReason());
            assertNotNull(status.getPowerResetBlockReason());
            assertNull(status.getMigrationBlockReason());
            assertNull(status.getMaintenanceBlockReason());
        }
    }

    @Test
    public void migrating_vm_sets_migration_block_reason() {
        List<VmInstanceVO> vms = Collections.singletonList(
                makeVm("vm-1", "vm-one", VmInstanceState.Migrating)
        );

        try (MockedStatic<SQL> sqlStatic = Mockito.mockStatic(SQL.class)) {
            stubSqlReturning(sqlStatic, vms);

            RoleWorkloadStatus status = provider.getWorkloadStatus("server-1", "role-1");

            assertNotNull(status.getMigrationBlockReason());
        }
    }

    @Test
    public void paused_vm_sets_maintenance_block_reason() {
        List<VmInstanceVO> vms = Collections.singletonList(
                makeVm("vm-1", "vm-one", VmInstanceState.Paused)
        );

        try (MockedStatic<SQL> sqlStatic = Mockito.mockStatic(SQL.class)) {
            stubSqlReturning(sqlStatic, vms);

            RoleWorkloadStatus status = provider.getWorkloadStatus("server-1", "role-1");

            assertNotNull(status.getMaintenanceBlockReason());
        }
    }

    @Test
    public void unknown_vm_sets_maintenance_block_reason() {
        List<VmInstanceVO> vms = Collections.singletonList(
                makeVm("vm-1", "vm-one", VmInstanceState.Unknown)
        );

        try (MockedStatic<SQL> sqlStatic = Mockito.mockStatic(SQL.class)) {
            stubSqlReturning(sqlStatic, vms);

            RoleWorkloadStatus status = provider.getWorkloadStatus("server-1", "role-1");

            assertNotNull(status.getMaintenanceBlockReason());
        }
    }

    @Test
    public void active_list_populated_with_uuid_name_type_state() {
        List<VmInstanceVO> vms = Arrays.asList(
                makeVm("vm-a", "alpha", VmInstanceState.Running),
                makeVm("vm-b", "beta",  VmInstanceState.Running)
        );

        try (MockedStatic<SQL> sqlStatic = Mockito.mockStatic(SQL.class)) {
            stubSqlReturning(sqlStatic, vms);

            RoleWorkloadStatus status = provider.getWorkloadStatus("server-1", "role-1");

            assertEquals(2, status.getActiveWorkloads().size());
            for (WorkloadRef ref : status.getActiveWorkloads()) {
                assertNotNull(ref.getUuid());
                assertNotNull(ref.getName());
                assertEquals("VM", ref.getType());
                assertEquals(VmInstanceState.Running.toString(), ref.getState());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static CreateRoleEntityContext ctxWithCreds(
            String username, String password, String sshPort,
            String clusterUuid, String managementIp) {
        Map<String, String> cfg = new HashMap<String, String>();
        if (username != null) cfg.put("username", username);
        if (password != null) cfg.put("password", password);
        if (sshPort  != null) cfg.put("sshPort",  sshPort);

        return new CreateRoleEntityContext()
                .setServerUuid("server-uuid-x")
                .setClusterUuid(clusterUuid)
                .setManagementIp(managementIp)
                .setRoleConfig(cfg);
    }

    private static Map<String, String> roleConfig(String username, String password, String sshPort) {
        Map<String, String> cfg = new HashMap<String, String>();
        if (username != null) cfg.put("username", username);
        if (password != null) cfg.put("password", password);
        if (sshPort  != null) cfg.put("sshPort",  sshPort);
        return cfg;
    }

    private static VmInstanceVO makeVm(String uuid, String name, VmInstanceState state) {
        VmInstanceVO vm = new VmInstanceVO();
        setField(vm, "uuid",  uuid);
        setField(vm, "name",  name);
        setField(vm, "state", state);
        return vm;
    }

    @SuppressWarnings("unchecked")
    private static void stubSqlReturning(MockedStatic<SQL> sqlStatic, List<VmInstanceVO> returnedVms) {
        SQL mockSql = mock(SQL.class);
        sqlStatic.when(() -> SQL.New(anyString(), eq(VmInstanceVO.class))).thenReturn(mockSql);
        when(mockSql.param(anyString(), any())).thenReturn(mockSql);
        when(mockSql.list()).thenReturn((List) returnedVms);
    }

    private static void injectField(Object target, String fieldName, Object value) throws Exception {
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                Field f = clazz.getDeclaredField(fieldName);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException("Field '" + fieldName + "' not found in " + target.getClass());
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            injectField(target, fieldName, value);
        } catch (Exception e) {
            throw new RuntimeException("Cannot set field '" + fieldName + "' on " + target.getClass(), e);
        }
    }

    // No-op completion for validation-edge tests where the throw happens before bus.send.
    private static ReturnValueCompletion<String> noopRvComp() {
        return new ReturnValueCompletion<String>((Message) null) {
            @Override public void success(String value) {}
            @Override public void fail(ErrorCode errorCode) {}
        };
    }

    // Capturing completion for createRoleEntity (carries a String success value).
    private static class CapturingRvComp extends ReturnValueCompletion<String> {
        boolean successCalled;
        boolean failCalled;
        String successValue;
        ErrorCode errorCode;

        CapturingRvComp() { super((Message) null); }

        @Override public void success(String value) {
            this.successCalled = true;
            this.successValue = value;
        }

        @Override public void fail(ErrorCode errorCode) {
            this.failCalled = true;
            this.errorCode = errorCode;
        }
    }

    // Capturing completion for deleteRoleEntity (no value).
    private static class CapturingComp extends Completion {
        boolean successCalled;
        boolean failCalled;
        ErrorCode errorCode;

        CapturingComp() { super((Message) null); }

        @Override public void success() {
            this.successCalled = true;
        }

        @Override public void fail(ErrorCode errorCode) {
            this.failCalled = true;
            this.errorCode = errorCode;
        }
    }

    // -------------------------------------------------------------------------
    // Hand-written CloudBus stub
    //
    // mockito-inline on Java 8 cannot inline-mock interfaces whose hierarchy
    // causes javax.servlet to be loaded (CloudBus → handleHttpRequest param).
    // This stub implements only the methods KvmRoleProvider actually calls
    // and records the arguments so tests can assert on them.
    // -------------------------------------------------------------------------

    private static class FakeCloudBus implements CloudBus {

        private final MessageReply replyToReturn;

        // Captured state ---- makeLocalServiceId
        Message lastLocalServiceIdMsg;
        String  lastLocalServiceId;

        // Captured state ---- makeTargetServiceIdByResourceUuid
        Message lastTargetServiceIdMsg;
        String  lastTargetServiceId;
        String  lastTargetServiceIdResourceUuid;

        // Captured state ---- send(NeedReplyMessage, CloudBusCallBack)
        NeedReplyMessage lastSentMsg;

        FakeCloudBus(MessageReply replyToReturn) {
            this.replyToReturn = replyToReturn;
        }

        @Override
        public void makeLocalServiceId(Message msg, String serviceId) {
            this.lastLocalServiceIdMsg = msg;
            this.lastLocalServiceId    = serviceId;
        }

        @Override
        public void makeTargetServiceIdByResourceUuid(Message msg, String serviceId, String resourceUuid) {
            this.lastTargetServiceIdMsg           = msg;
            this.lastTargetServiceId              = serviceId;
            this.lastTargetServiceIdResourceUuid  = resourceUuid;
        }

        @Override
        public FutureCompletion send(NeedReplyMessage msg, CloudBusCallBack callback) {
            this.lastSentMsg = msg;
            // Invoke callback synchronously so tests can assert on completion state immediately.
            callback.run(replyToReturn);
            return null;
        }

        // ---- unused CloudBus methods ----

        @Override public boolean start() { throw new UnsupportedOperationException(); }
        @Override public boolean stop()  { throw new UnsupportedOperationException(); }
        @Override public FutureCompletion send(Message msg) { throw new UnsupportedOperationException(); }
        @Override public <T extends Message> void send(List<T> msgs) { throw new UnsupportedOperationException(); }
        @Override public void send(APIMessage msg, java.util.function.Consumer<APIEvent> consumer) { throw new UnsupportedOperationException(); }
        @Override public void send(List<? extends NeedReplyMessage> msgs, CloudBusListCallBack callBack) { throw new UnsupportedOperationException(); }
        @Override public void send(List<? extends NeedReplyMessage> msgs, int parallelLevel, CloudBusListCallBack callBack) { throw new UnsupportedOperationException(); }
        @Override public void send(List<? extends NeedReplyMessage> msgs, int parallelLevel, CloudBusSteppingCallback callback) { throw new UnsupportedOperationException(); }
        @Override public void route(List<Message> msgs) { throw new UnsupportedOperationException(); }
        @Override public void route(Message msg) { throw new UnsupportedOperationException(); }
        @Override public void reply(Message request, MessageReply reply) { throw new UnsupportedOperationException(); }
        @Override public void cancel(String correlationId, String error) { throw new UnsupportedOperationException(); }
        @Override public void publish(List<Event> events) { throw new UnsupportedOperationException(); }
        @Override public void publish(Event event) { throw new UnsupportedOperationException(); }
        @Override public MessageReply call(NeedReplyMessage msg) { throw new UnsupportedOperationException(); }
        @Override public <T extends NeedReplyMessage> List<MessageReply> call(List<T> msg) { throw new UnsupportedOperationException(); }
        @Override public void registerService(org.zstack.header.Service serv) throws CloudConfigureFailException { throw new UnsupportedOperationException(); }
        @Override public void unregisterService(org.zstack.header.Service serv) { throw new UnsupportedOperationException(); }
        @Override public EventSubscriberReceipt subscribeEvent(CloudBusEventListener listener, Event... events) { throw new UnsupportedOperationException(); }
        @Override public void dealWithUnknownMessage(Message msg) { throw new UnsupportedOperationException(); }
        @Override public void replyErrorByMessageType(Message msg, Exception e) { throw new UnsupportedOperationException(); }
        @Override public void replyErrorByMessageType(Message msg, String err) { throw new UnsupportedOperationException(); }
        @Override public void replyErrorByMessageType(Message msg, ErrorCode err) { throw new UnsupportedOperationException(); }
        @Override public void logExceptionWithMessageDump(Message msg, Throwable e) { throw new UnsupportedOperationException(); }
        @Override public String getServiceId(String targetServiceId) { throw new UnsupportedOperationException(); }
        @Override public String makeLocalServiceId(String serviceId) { throw new UnsupportedOperationException(); }
        @Override public String makeServiceIdByManagementNodeId(String serviceId, String managementNodeId) { throw new UnsupportedOperationException(); }
        @Override public void makeServiceIdByManagementNodeId(Message msg, String serviceId, String managementNodeId) { throw new UnsupportedOperationException(); }
        @Override public String makeTargetServiceIdByResourceUuid(String serviceId, String resourceUuid) { throw new UnsupportedOperationException(); }
        @Override public void installBeforeDeliveryMessageInterceptor(BeforeDeliveryMessageInterceptor interceptor, List<Class<? extends Message>> classes) { throw new UnsupportedOperationException(); }
        @Override public void installBeforeDeliveryMessageInterceptor(BeforeDeliveryMessageInterceptor interceptor, Class<? extends Message>... classes) { throw new UnsupportedOperationException(); }
        @Override public void installBeforeSendMessageInterceptor(BeforeSendMessageInterceptor interceptor, Class<? extends Message>... classes) { throw new UnsupportedOperationException(); }
        @Override public void installBeforePublishEventInterceptor(BeforePublishEventInterceptor interceptor, Class<? extends Event>... classes) { throw new UnsupportedOperationException(); }
    }
}
