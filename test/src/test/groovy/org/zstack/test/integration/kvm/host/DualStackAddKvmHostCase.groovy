package org.zstack.test.integration.kvm.host

import org.zstack.core.Platform
import org.zstack.core.CoreGlobalProperty
import org.zstack.core.db.Q
import org.zstack.header.host.HostStatus
import org.zstack.header.host.HostVO
import org.zstack.header.host.HostVO_
import org.zstack.header.tag.SystemTagVO
import org.zstack.header.tag.SystemTagVO_
import org.zstack.header.zone.ZoneVO
import org.zstack.sdk.AddKVMHostAction
import org.zstack.sdk.ClusterInventory
import org.zstack.test.integration.kvm.KvmTest
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.SubCase
import org.zstack.core.ansible.RunAnsibleMsg
import org.zstack.core.ansible.RunAnsibleReply

import java.lang.reflect.Field

class DualStackAddKvmHostCase extends SubCase {
    private static final String UNIT_TEST_AGENT_HOST = "KVMHost.unitTestAgentHost"
    private static final String IPV4_MN = "127.0.0.1"
    private static final String IPV4_HOST = "192.168.1.20"
    private static final String IPV4_ONLY_HOST = "192.168.1.21"
    private static final String IPV6_MN = "::ffff:127.0.0.1"
    private static final String IPV6_HOST = "2001:db8::20"
    private static final String IPV6_ONLY_HOST = "2001:db8::21"

    EnvSpec env
    ClusterInventory cluster
    List<String> oldChronyServers

    @Override
    void setup() {
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
        env.delete()
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
                testAnsibleFailureRollsBackHost()
            } finally {
                System.clearProperty(UNIT_TEST_AGENT_HOST)
                CoreGlobalProperty.CHRONY_SERVERS = oldChronyServers
            }
        }
    }

    void testPrimaryIpv4AddIpv6Host() {
        withManagementServerIpProperties([
                "management.server.ip" : IPV4_MN,
                "management.server.ip6": IPV6_MN,
        ]) {
            updateZoneIpVersion("ipv6")
            RunAnsibleMsg msg = addHostAndCaptureAnsible(IPV6_HOST, "dual-stack-ipv6-host")

            assert msg.targetIp == IPV6_HOST
            assert msg.deployArguments.pipUrl.contains("[${IPV6_MN}]")
            assert msg.deployArguments.trustedHost == IPV6_MN
            assert msg.deployArguments.yumServer.startsWith("[${IPV6_MN}]:")
            deleteHost { uuid = msg.targetUuid }
        }
    }

    void testPrimaryIpv6AddIpv4Host() {
        withManagementServerIpProperties([
                "management.server.ip" : IPV6_MN,
                "management.server.ip4": IPV4_MN,
        ]) {
            updateZoneIpVersion("ipv4")
            RunAnsibleMsg msg = addHostAndCaptureAnsible(IPV4_HOST, "dual-stack-ipv4-host")

            assert msg.targetIp == IPV4_HOST
            assert msg.deployArguments.pipUrl.contains("//${IPV4_MN}:")
            assert msg.deployArguments.trustedHost == IPV4_MN
            assert msg.deployArguments.yumServer.startsWith("${IPV4_MN}:")
            deleteHost { uuid = msg.targetUuid }
        }
    }

    void testIpv4OnlyRegression() {
        withManagementServerIpProperties(["management.server.ip": IPV4_MN]) {
            updateZoneIpVersion("ipv4")
            RunAnsibleMsg msg = addHostAndCaptureAnsible(IPV4_ONLY_HOST, "ipv4-only-host")

            assert msg.deployArguments.trustedHost == IPV4_MN
            deleteHost { uuid = msg.targetUuid }
        }
    }

    void testIpv6OnlyRegression() {
        withManagementServerIpProperties(["management.server.ip": IPV6_MN]) {
            updateZoneIpVersion("ipv6")
            RunAnsibleMsg msg = addHostAndCaptureAnsible(IPV6_ONLY_HOST, "ipv6-only-host")

            assert msg.deployArguments.trustedHost == IPV6_MN
            assert msg.deployArguments.yumServer.startsWith("[${IPV6_MN}]:")
            deleteHost { uuid = msg.targetUuid }
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

            def result = addHost(hostUuid, "2001:db8::30", "ansible-failure-host")

            assert ansibleCalled
            assert result.error != null
            assert !Q.New(HostVO.class).eq(HostVO_.uuid, hostUuid).isExists()
        }
    }

    private RunAnsibleMsg addHostAndCaptureAnsible(String managementIp, String name) {
        RunAnsibleMsg[] captured = new RunAnsibleMsg[1]
        env.message(RunAnsibleMsg.class) { RunAnsibleMsg msg, bus ->
            captured[0] = msg
            bus.reply(msg, new RunAnsibleReply())
        }

        String hostUuid = Platform.uuid
        def result = addHost(hostUuid, managementIp, name)

        assert result.error == null: "${result.error?.code} ${result.error?.globalErrorCode} ${result.error?.details}"
        assert captured[0] != null
        HostVO host = Q.New(HostVO.class).eq(HostVO_.uuid, hostUuid).find()
        assert host != null
        assert host.status == HostStatus.Connected
        return captured[0]
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
}
