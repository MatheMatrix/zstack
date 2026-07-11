package org.zstack.test.integration.core.salt

import org.zstack.core.Platform
import org.zstack.core.salt.SaltFacadeImpl
import org.zstack.core.salt.SaltSetupMinionJob
import org.zstack.header.core.ReturnValueCompletion
import org.zstack.header.errorcode.ErrorCode
import org.zstack.testlib.SubCase
import org.zstack.utils.path.PathUtil

import java.lang.reflect.Field

class SaltDualStackConfigCase extends SubCase {
    @Override
    void setup() {
    }

    @Override
    void environment() {
    }

    @Override
    void clean() {
    }

    @Override
    void test() {
        testMasterListensOnDualStackSocket()
        testMinionSelectsReachableSaltMasterFamily()
        testStrictSelectionFailure()
    }

    void testMasterListensOnDualStackSocket() {
        String masterConfig = PathUtil.findFileOnClassPath("salt/master", true).text
        String dualStack = SaltFacadeImpl.renderMasterConfig(masterConfig, 1024, 10, true)
        assert dualStack.readLines().contains("interface: '::'")
        assert dualStack.readLines().contains("ipv6: true")

        String ipv4Only = SaltFacadeImpl.renderMasterConfig(masterConfig, 1024, 10, false)
        assert ipv4Only.readLines().contains("interface: 0.0.0.0")
        assert ipv4Only.readLines().contains("ipv6: false")
    }

    void testMinionSelectsReachableSaltMasterFamily() {
        withManagementServerIpProperties([
                "management.server.ip" : "192.168.1.10",
                "management.server.ip6": "2001:db8::10",
        ]) {
            String template = "master: {managementNodeIp}\nipv6: {ipv6Enabled}\nid: {minionId}\n"

            assert SaltSetupMinionJob.renderMinionConfig(template, "2001:db8::10", "ipv6-minion") ==
                    "master: 2001:db8::10\nipv6: true\nid: ipv6-minion\n"
            assert SaltSetupMinionJob.renderMinionConfig(template, "192.168.1.10", "ipv4-minion") ==
                    "master: 192.168.1.10\nipv6: false\nid: ipv4-minion\n"
        }
    }

    void testStrictSelectionFailure() {
        withManagementServerIpProperties([
                "management.server.ip": "127.0.0.1",
        ]) {
            def selection = Platform.selectManagementServerIpForRemoteStrict(
                    "::1", "::1", null)
            SaltSetupMinionJob job = new SaltSetupMinionJob()
            job.setTargetIp("::1")
            job.setManagementServerIpSelection(selection)
            ErrorCode[] captured = new ErrorCode[1]

            job.run(new ReturnValueCompletion<Object>(null) {
                @Override
                void success(Object ignored) {
                    assert false: "selection failure must not succeed"
                }

                @Override
                void fail(ErrorCode errorCode) {
                    captured[0] = errorCode
                }
            })

            assert captured[0] != null
            assert captured[0].globalErrorCode ==
                    "ORG_ZSTACK_CORE_MANAGEMENT_SERVER_IP_10000"
        }
    }

    private static void withManagementServerIpProperties(Map<String, String> properties, Closure closure) {
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
