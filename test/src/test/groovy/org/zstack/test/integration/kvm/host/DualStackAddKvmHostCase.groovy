package org.zstack.test.integration.kvm.host

import org.zstack.core.Platform
import org.zstack.core.CoreGlobalProperty
import org.zstack.core.cloudbus.CloudBus
import org.zstack.core.db.DatabaseFacade
import org.zstack.core.db.Q
import org.zstack.header.host.HostStatus
import org.zstack.header.host.HostConstant
import org.zstack.header.host.PingHostMsg
import org.zstack.header.host.PingHostReply
import org.zstack.header.host.HostVO
import org.zstack.header.host.HostVO_
import org.zstack.header.rest.RESTFacade
import org.zstack.header.rest.RESTConstant
import org.zstack.header.tag.SystemTagVO
import org.zstack.header.tag.SystemTagVO_
import org.zstack.header.zone.ZoneVO
import org.zstack.sdk.AddKVMHostAction
import org.zstack.sdk.ClusterInventory
import org.zstack.header.vm.PauseVmOnHypervisorMsg
import org.zstack.header.vm.VmInstanceInventory
import org.zstack.test.integration.kvm.KvmTest
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.SubCase
import org.zstack.core.ansible.RunAnsibleMsg
import org.zstack.core.ansible.RunAnsibleReply
import org.zstack.kvm.KVMAgentCommands
import org.zstack.kvm.KVMConstant
import org.zstack.utils.ShellUtils
import org.zstack.utils.gson.JSONObjectUtil
import org.springframework.http.HttpEntity

import java.lang.reflect.Field
import java.util.concurrent.TimeUnit

class DualStackAddKvmHostCase extends SubCase {
    private static final String UNIT_TEST_AGENT_HOST = "KVMHost.unitTestAgentHost"
    private static final String TEST_INTERFACE = "z86567dummy"
    private static final String IPV4_MN = "10.254.86.1"
    private static final String IPV4_HOST = "10.254.86.2"
    private static final String IPV4_ONLY_HOST = "10.254.86.3"
    private static final String IPV6_MN = "fd86:567::1"
    private static final String IPV6_MN_ALT = "fd86:567::10"
    private static final String IPV6_HOST = "fd86:567::2"
    private static final String IPV6_ONLY_HOST = "fd86:567::3"
    private static final String IPV6_FAILURE_HOST = "fd86:567::30"
    private static final String IPV6_ROUTE_FAILURE_HOST = "fd86:568::2"

    EnvSpec env
    ClusterInventory cluster
    List<String> oldChronyServers

    @Override
    void setup() {
        deleteTestInterface()
        runNetworkCommand("ip link add ${TEST_INTERFACE} type dummy")
        runNetworkCommand("ip link set ${TEST_INTERFACE} up")
        runNetworkCommand("ip -4 addr add ${IPV4_MN}/24 dev ${TEST_INTERFACE}")
        runNetworkCommand("ip -6 addr add ${IPV6_MN}/64 dev ${TEST_INTERFACE}")
        useSpring(KvmTest.springSpec)
    }

    @Override
    void environment() {
        env = HostEnv.noHostBasicEnv()
    }

    @Override
    void clean() {
        System.clearProperty(UNIT_TEST_AGENT_HOST)
        CoreGlobalProperty.CHRONY_SERVERS = oldChronyServers
        try {
            if (env != null) {
                env.delete()
            }
        } finally {
            deleteTestInterface()
        }
    }

    @Override
    void test() {
        env.create {
            cluster = env.inventoryByName("cluster") as ClusterInventory
            oldChronyServers = CoreGlobalProperty.CHRONY_SERVERS
            try {
                System.setProperty(UNIT_TEST_AGENT_HOST, "127.0.0.1")
                CoreGlobalProperty.CHRONY_SERVERS = ["127.0.0.1"]

                testPrimaryIpv4AddIpv6Host()
                testPrimaryIpv6AddIpv4Host()
                testIpv4OnlyRegression()
                testIpv6OnlyRegression()
                testHostnameRegression()
                testPingReResolvesChangedRouteSource()
                testPingRouteLookupFailureDoesNotDisconnectHost()
                testAnsibleFailureRollsBackHost()
                testNoSameFamilyRollsBackWithoutChecker()
                testUnconfiguredRouteSourceRollsBackWithoutChecker()
            } finally {
                System.clearProperty(UNIT_TEST_AGENT_HOST)
                CoreGlobalProperty.CHRONY_SERVERS = oldChronyServers
                deleteTestInterface()
            }
        }
    }

    void testPrimaryIpv4AddIpv6Host() {
        withManagementServerIpProperties([
                "management.server.ip" : IPV4_MN,
                "management.server.ip6": IPV6_MN,
        ]) {
            updateZoneIpVersion("ipv6")
            def captured = addHostAndCaptureCommands(IPV6_HOST, "dual-stack-ipv6-host")
            RunAnsibleMsg msg = captured.ansible

            assert msg.targetIp == IPV6_HOST
            assert msg.deployArguments.pipUrl.contains("[${IPV6_MN}]")
            assert msg.deployArguments.trustedHost == IPV6_MN
            assert msg.deployArguments.yumServer.startsWith("[${IPV6_MN}]:")
            assert captured.connect.sendCommandUrl == bean(RESTFacade.class).buildSendCommandUrl(IPV6_HOST)
            assertPingRepairsTargetAwareSendCommandUrl(msg.targetUuid, IPV6_MN)
            deleteHost { uuid = msg.targetUuid }
        }
    }

    void testPrimaryIpv6AddIpv4Host() {
        withManagementServerIpProperties([
                "management.server.ip" : IPV6_MN,
                "management.server.ip4": IPV4_MN,
        ]) {
            updateZoneIpVersion("ipv4")
            def captured = addHostAndCaptureCommands(IPV4_HOST, "dual-stack-ipv4-host")
            RunAnsibleMsg msg = captured.ansible

            assert msg.targetIp == IPV4_HOST
            assert msg.deployArguments.pipUrl.contains("//${IPV4_MN}:")
            assert msg.deployArguments.trustedHost == IPV4_MN
            assert msg.deployArguments.yumServer.startsWith("${IPV4_MN}:")
            assert captured.connect.sendCommandUrl == bean(RESTFacade.class).buildSendCommandUrl(IPV4_HOST)
            deleteHost { uuid = msg.targetUuid }
        }
    }

    void testIpv4OnlyRegression() {
        withManagementServerIpProperties(["management.server.ip": IPV4_MN]) {
            updateZoneIpVersion("ipv4")
            def captured = addHostAndCaptureCommands(IPV4_ONLY_HOST, "ipv4-only-host")
            RunAnsibleMsg msg = captured.ansible

            assert msg.deployArguments.trustedHost == IPV4_MN
            assert captured.connect.sendCommandUrl == bean(RESTFacade.class).buildSendCommandUrl(IPV4_ONLY_HOST)
            deleteHost { uuid = msg.targetUuid }
        }
    }

    void testIpv6OnlyRegression() {
        withManagementServerIpProperties(["management.server.ip": IPV6_MN]) {
            updateZoneIpVersion("ipv6")
            def captured = addHostAndCaptureCommands(IPV6_ONLY_HOST, "ipv6-only-host")
            RunAnsibleMsg msg = captured.ansible

            assert msg.deployArguments.trustedHost == IPV6_MN
            assert msg.deployArguments.yumServer.startsWith("[${IPV6_MN}]:")
            assert captured.connect.sendCommandUrl == bean(RESTFacade.class).buildSendCommandUrl(IPV6_ONLY_HOST)
            deleteHost { uuid = msg.targetUuid }
        }
    }

    void testHostnameRegression() {
        withManagementServerIpProperties(["management.server.ip": IPV4_MN]) {
            updateZoneIpVersion("ipv4")
            def captured = addHostAndCaptureCommands("localhost", "hostname-host")
            RunAnsibleMsg msg = captured.ansible

            assert msg.targetIp == "localhost"
            assert msg.deployArguments.trustedHost == bean(RESTFacade.class).hostName
            assert captured.connect.sendCommandUrl == bean(RESTFacade.class).buildSendCommandUrl("localhost")
            deleteHost { uuid = msg.targetUuid }
        }
    }

    void testPingReResolvesChangedRouteSource() {
        withManagementServerIpProperties([
                "management.server.ip" : IPV6_MN,
                "management.server.ip6": IPV6_MN_ALT,
        ]) {
            updateZoneIpVersion("ipv6")
            runNetworkCommand("ip -6 addr add ${IPV6_MN_ALT}/64 dev ${TEST_INTERFACE}")
            runNetworkCommand("ip -6 route replace ${IPV6_HOST}/128 dev ${TEST_INTERFACE} src ${IPV6_MN}")
            try {
                def captured = addHostAndCaptureCommands(IPV6_HOST, "route-switch-host")
                RunAnsibleMsg msg = captured.ansible
                assert msg.deployArguments.trustedHost == IPV6_MN

                runNetworkCommand("ip -6 route replace ${IPV6_HOST}/128 dev ${TEST_INTERFACE} src ${IPV6_MN_ALT}")
                assertPingRepairsTargetAwareSendCommandUrl(msg.targetUuid, IPV6_MN_ALT)
                assertKvmAsyncCallbackUsesManagementHost(msg.targetUuid, IPV6_MN_ALT)
                deleteHost { uuid = msg.targetUuid }
            } finally {
                ShellUtils.runAndReturn("ip -6 route delete ${IPV6_HOST}/128 dev ${TEST_INTERFACE}", true)
                ShellUtils.runAndReturn("ip -6 addr delete ${IPV6_MN_ALT}/64 dev ${TEST_INTERFACE}", true)
            }
        }
    }

    void testAnsibleFailureRollsBackHost() {
        withManagementServerIpProperties([
                "management.server.ip" : IPV4_MN,
                "management.server.ip6": IPV6_MN,
        ]) {
            updateZoneIpVersion("ipv6")
            String hostUuid = Platform.uuid
            boolean ansibleCalled = false
            env.message(RunAnsibleMsg.class) { RunAnsibleMsg msg, bus ->
                ansibleCalled = true
                bus.replyErrorByMessageType(msg, "simulated ansible failure")
            }

            def result = addHost(hostUuid, IPV6_FAILURE_HOST, "ansible-failure-host")

            assert ansibleCalled
            assert result.error != null
            assert !Q.New(HostVO.class).eq(HostVO_.uuid, hostUuid).isExists()
        }
    }

    void testPingRouteLookupFailureDoesNotDisconnectHost() {
        withManagementServerIpProperties([
                "management.server.ip": IPV6_MN,
        ]) {
            updateZoneIpVersion("ipv6")
            runNetworkCommand("ip -6 route replace ${IPV6_ROUTE_FAILURE_HOST}/128 " +
                    "dev ${TEST_INTERFACE} src ${IPV6_MN}")
            try {
                def captured = addHostAndCaptureCommands(
                        IPV6_ROUTE_FAILURE_HOST, "transient-route-failure-host")
                String hostUuid = captured.ansible.targetUuid
                env.afterSimulator(KVMConstant.KVM_PING_PATH) {
                    KVMAgentCommands.PingResponse rsp, HttpEntity<String> entity ->
                        KVMAgentCommands.PingCmd cmd = JSONObjectUtil.toObject(
                                entity.body, KVMAgentCommands.PingCmd.class)
                        if (cmd.hostUuid == hostUuid) {
                            rsp.hostUuid = cmd.hostUuid
                            rsp.version = bean(DatabaseFacade.class).dbVersion
                            rsp.sendCommandUrl = "http://stale.example.com/command"
                        }
                        return rsp
                }
                runNetworkCommand("ip -6 route replace unreachable ${IPV6_ROUTE_FAILURE_HOST}/128")

                PingHostMsg msg = new PingHostMsg()
                msg.hostUuid = hostUuid
                CloudBus bus = bean(CloudBus.class)
                bus.makeTargetServiceIdByResourceUuid(msg, HostConstant.SERVICE_ID, hostUuid)
                PingHostReply reply = bus.call(msg) as PingHostReply

                assert reply.success
                TimeUnit.MILLISECONDS.sleep(500)
                HostVO host = Q.New(HostVO.class).eq(HostVO_.uuid, hostUuid).find()
                assert host.status == HostStatus.Connected
                deleteHost { uuid = hostUuid }
            } finally {
                ShellUtils.runAndReturn(
                        "ip -6 route delete ${IPV6_ROUTE_FAILURE_HOST}/128", true)
            }
        }
    }

    void testNoSameFamilyRollsBackWithoutChecker() {
        withManagementServerIpProperties([
                "management.server.ip": "127.0.0.1",
        ]) {
            updateZoneIpVersion("ipv6")
            assertSelectionFailureRollsBackWithoutAnsible(
                    IPV6_HOST,
                    "no-same-family-host",
                    "ORG_ZSTACK_CORE_MANAGEMENT_SERVER_IP_10000")
        }
    }

    void testUnconfiguredRouteSourceRollsBackWithoutChecker() {
        withManagementServerIpProperties([
                "management.server.ip" : IPV4_MN,
                "management.server.ip6": "fd86:567::10",
        ]) {
            updateZoneIpVersion("ipv6")
            assertSelectionFailureRollsBackWithoutAnsible(
                    IPV6_HOST,
                    "unconfigured-route-source-host",
                    "ORG_ZSTACK_CORE_MANAGEMENT_SERVER_IP_10002")
        }
    }

    private void assertSelectionFailureRollsBackWithoutAnsible(String managementIp,
                                                               String name,
                                                               String globalErrorCode) {
        String hostUuid = Platform.uuid
        boolean ansibleCalled = false
        env.message(RunAnsibleMsg.class) { RunAnsibleMsg msg, bus ->
            ansibleCalled = true
            bus.reply(msg, new RunAnsibleReply())
        }

        def result = addHost(hostUuid, managementIp, name)

        assert !ansibleCalled
        assert result.error != null
        assert result.error.globalErrorCode == globalErrorCode
        assert !Q.New(HostVO.class).eq(HostVO_.uuid, hostUuid).isExists()
    }

    private Map addHostAndCaptureCommands(String managementIp, String name) {
        String hostUuid = Platform.uuid
        RunAnsibleMsg[] capturedAnsible = new RunAnsibleMsg[1]
        KVMAgentCommands.ConnectCmd[] capturedConnect = new KVMAgentCommands.ConnectCmd[1]
        env.message(RunAnsibleMsg.class) { RunAnsibleMsg msg, bus ->
            capturedAnsible[0] = msg
            bus.reply(msg, new RunAnsibleReply())
        }
        env.afterSimulator(KVMConstant.KVM_CONNECT_PATH) { rsp, HttpEntity<String> entity ->
            KVMAgentCommands.ConnectCmd cmd = JSONObjectUtil.toObject(
                    entity.body, KVMAgentCommands.ConnectCmd.class)
            if (cmd.hostUuid == hostUuid) {
                capturedConnect[0] = cmd
            }
            return rsp
        }

        def result = addHost(hostUuid, managementIp, name)

        assert result.error == null: "${result.error?.code} ${result.error?.globalErrorCode} ${result.error?.details}"
        assert capturedAnsible[0] != null
        assert capturedConnect[0] != null
        HostVO host = Q.New(HostVO.class).eq(HostVO_.uuid, hostUuid).find()
        assert host != null
        assert host.status == HostStatus.Connected
        return [ansible: capturedAnsible[0], connect: capturedConnect[0]]
    }

    private void assertPingRepairsTargetAwareSendCommandUrl(String hostUuid, String managementHost) {
        RESTFacade restf = bean(RESTFacade.class)
        DatabaseFacade dbf = bean(DatabaseFacade.class)
        KVMAgentCommands.UpdateHostConfigurationCmd[] captured =
                new KVMAgentCommands.UpdateHostConfigurationCmd[1]

        env.afterSimulator(KVMConstant.KVM_PING_PATH) {
            KVMAgentCommands.PingResponse rsp, HttpEntity<String> entity ->
            KVMAgentCommands.PingCmd cmd = JSONObjectUtil.toObject(entity.body, KVMAgentCommands.PingCmd.class)
            if (cmd.hostUuid == hostUuid) {
                rsp.hostUuid = cmd.hostUuid
                rsp.version = dbf.dbVersion
                rsp.sendCommandUrl = "http://stale.example.com/command"
            }
            return rsp
        }
        env.afterSimulator(KVMConstant.KVM_UPDATE_HOST_CONFIGURATION_PATH) {
            KVMAgentCommands.UpdateHostConfigurationResponse rsp, HttpEntity<String> entity ->
                KVMAgentCommands.UpdateHostConfigurationCmd cmd = JSONObjectUtil.toObject(
                        entity.body, KVMAgentCommands.UpdateHostConfigurationCmd.class)
                if (cmd.hostUuid == hostUuid) {
                    captured[0] = cmd
                }
                return rsp
        }

        PingHostMsg msg = new PingHostMsg()
        msg.hostUuid = hostUuid
        CloudBus bus = bean(CloudBus.class)
        bus.makeTargetServiceIdByResourceUuid(msg, HostConstant.SERVICE_ID, hostUuid)
        PingHostReply reply = bus.call(msg) as PingHostReply

        assert reply.success
        retryInSecs {
            assert captured[0] != null
        }
        assert captured[0].sendCommandUrl ==
                restf.buildSendCommandUrlForManagementHost(managementHost)
    }

    private void assertKvmAsyncCallbackUsesManagementHost(String hostUuid, String managementHost) {
        String[] callbackUrl = new String[1]
        env.afterSimulator(KVMConstant.KVM_PAUSE_VM_PATH) { rsp, HttpEntity<String> entity ->
            callbackUrl[0] = entity.headers.getFirst(RESTConstant.CALLBACK_URL)
            return rsp
        }

        VmInstanceInventory vm = new VmInstanceInventory()
        vm.uuid = Platform.uuid
        vm.name = "target-aware-callback-vm"
        vm.hostUuid = hostUuid
        PauseVmOnHypervisorMsg msg = new PauseVmOnHypervisorMsg()
        msg.vmInventory = vm
        CloudBus bus = bean(CloudBus.class)
        bus.makeTargetServiceIdByResourceUuid(msg, HostConstant.SERVICE_ID, hostUuid)
        def reply = bus.call(msg)

        assert reply.success
        assert callbackUrl[0] == bean(RESTFacade.class).buildCallbackUrl(managementHost)
    }

    private def addHost(String hostUuid, String managementIp, String name) {
        AddKVMHostAction action = new AddKVMHostAction()
        action.sessionId = adminSession()
        action.resourceUuid = hostUuid
        action.clusterUuid = cluster.uuid
        action.managementIp = managementIp
        action.name = name
        action.username = "root"
        action.password = "password"
        return action.call()
    }

    private void updateZoneIpVersion(String version) {
        SystemTagVO currentTag = Q.New(SystemTagVO.class)
                .eq(SystemTagVO_.resourceUuid, cluster.zoneUuid)
                .eq(SystemTagVO_.resourceType, ZoneVO.simpleName)
                .like(SystemTagVO_.tag, "managementNetwork::ipVersion::%")
                .find()
        assert currentTag != null

        updateSystemTag {
            uuid = currentTag.uuid
            tag = "managementNetwork::ipVersion::${version}"
        }
    }

    private void withManagementServerIpProperties(Map<String, String> properties, Closure closure) {
        List<String> keys = ["management.server.ip", "management.server.ip4", "management.server.ip6"]
        Map<String, String> oldValues = keys.collectEntries { [(it): System.getProperty(it)] }
        try {
            keys.each { System.clearProperty(it) }
            properties.each { key, value -> System.setProperty(key, value) }
            resetCachedManagementServerIp()
            closure.call()
        } finally {
            keys.each { key ->
                if (oldValues[key] == null) {
                    System.clearProperty(key)
                } else {
                    System.setProperty(key, oldValues[key])
                }
            }
            resetCachedManagementServerIp()
        }
    }

    private static void resetCachedManagementServerIp() {
        Field field = Platform.class.getDeclaredField("managementServerIp")
        field.accessible = true
        field.set(null, null)
    }

    private static void runNetworkCommand(String command) {
        def result = ShellUtils.runAndReturn(command, true)
        assert result.retCode == 0: "${command}: ${result.stderr}"
    }

    private static void deleteTestInterface() {
        ShellUtils.runAndReturn("ip link delete ${TEST_INTERFACE}", true)
    }
}
