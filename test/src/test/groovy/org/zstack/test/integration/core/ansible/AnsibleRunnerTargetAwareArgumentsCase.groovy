package org.zstack.test.integration.core.ansible

import org.junit.Test
import org.zstack.core.CoreGlobalProperty
import org.zstack.core.Platform
import org.zstack.core.ansible.AnsibleRunner
import org.zstack.core.ansible.RunAnsibleMsg
import org.zstack.core.cloudbus.CloudBus
import org.zstack.header.core.ReturnValueCompletion
import org.zstack.header.errorcode.ErrorCode
import org.zstack.header.rest.RESTFacade

import java.lang.reflect.Field
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class AnsibleRunnerTargetAwareArgumentsCase {
    private static final String IPV4 = "192.168.1.10"
    private static final String IPV4_TARGET = "192.168.1.20"
    private static final String IPV6 = "2001:db8::10"
    private static final String IPV6_TARGET = "2001:db8::20"
    private static final int REST_PORT = 8080

    @Test
    void test() {
        String oldUserHome = System.getProperty("user.home")
        File testHome = File.createTempDir("zstack-ansible-runner", "")
        try {
            System.setProperty("user.home", testHome.absolutePath)
            testPrimaryIpv4TargetIpv6()
            testPrimaryIpv6TargetIpv4()
            testHostnameTargetKeepsRestFacadeHost()
        } finally {
            System.setProperty("user.home", oldUserHome)
            testHome.deleteDir()
        }
    }

    void testPrimaryIpv4TargetIpv6() {
        withManagementServerIpProperties([
                "management.server.ip" : IPV4,
                "management.server.ip6": IPV6,
        ]) {
            RunAnsibleMsg msg = runAnsible(IPV6_TARGET, IPV4, "http://${IPV4}:${REST_PORT}")

            assert msg.targetIp == IPV6_TARGET
            assert msg.deployArguments.pipUrl == "http://[${IPV6}]:${REST_PORT}/zstack/static/pypi/simple"
            assert msg.deployArguments.trustedHost == IPV6
            assert msg.deployArguments.yumServer == "[${IPV6}]:${REST_PORT}"
        }
    }

    void testPrimaryIpv6TargetIpv4() {
        withManagementServerIpProperties([
                "management.server.ip" : IPV6,
                "management.server.ip4": IPV4,
        ]) {
            RunAnsibleMsg msg = runAnsible(IPV4_TARGET, IPV6, "http://[${IPV6}]:${REST_PORT}")

            assert msg.targetIp == IPV4_TARGET
            assert msg.deployArguments.pipUrl == "http://${IPV4}:${REST_PORT}/zstack/static/pypi/simple"
            assert msg.deployArguments.trustedHost == IPV4
            assert msg.deployArguments.yumServer == "${IPV4}:${REST_PORT}"
        }
    }

    void testHostnameTargetKeepsRestFacadeHost() {
        withManagementServerIpProperties([
                "management.server.ip" : IPV4,
                "management.server.ip6": IPV6,
        ]) {
            String restHost = "mn.example.com"
            RunAnsibleMsg msg = runAnsible("host.example.com", restHost, "http://${restHost}:${REST_PORT}")

            assert msg.deployArguments.pipUrl == "http://${restHost}:${REST_PORT}/zstack/static/pypi/simple"
            assert msg.deployArguments.trustedHost == restHost
            assert msg.deployArguments.yumServer == "${restHost}:${REST_PORT}"
        }
    }

    private RunAnsibleMsg runAnsible(String targetIp, String restHost, String baseUrl) {
        RunAnsibleMsg[] captured = new RunAnsibleMsg[1]
        CloudBus bus = proxy(CloudBus.class) { Method method, Object[] args ->
            if (method.name == "makeTargetServiceIdByResourceUuid") {
                return null
            }
            if (method.name == "send" && args?.length == 2 && args[0] instanceof RunAnsibleMsg) {
                captured[0] = args[0] as RunAnsibleMsg
                return null
            }
            return defaultValue(method.returnType)
        }
        RESTFacade restf = proxy(RESTFacade.class) { Method method, Object[] args ->
            switch (method.name) {
                case "getBaseUrl":
                    return baseUrl
                case "getHostName":
                    return restHost
                case "getPort":
                    return REST_PORT
                default:
                    return defaultValue(method.returnType)
            }
        }

        boolean oldUnitTestOn = CoreGlobalProperty.UNIT_TEST_ON
        try {
            CoreGlobalProperty.UNIT_TEST_ON = true
            AnsibleRunner runner = new AnsibleRunner()
            setField(runner, "bus", bus)
            setField(runner, "restf", restf)
            runner.setForceRun(true)
            runner.setTargetIp(targetIp)
            runner.setTargetUuid(Platform.uuid)
            runner.setPlayBookPath("/tmp/zstack-test-playbook.yaml")
            runner.setUsername("root")
            runner.setPassword("password")

            ReturnValueCompletion<Boolean> completion = new ReturnValueCompletion<Boolean>(null) {
                @Override
                void success(Boolean returnValue) {
                }

                @Override
                void fail(ErrorCode errorCode) {
                    assert false: errorCode
                }
            }
            runner.run(completion)

            assert captured[0] != null
            return captured[0]
        } finally {
            CoreGlobalProperty.UNIT_TEST_ON = oldUnitTestOn
        }
    }

    private static <T> T proxy(Class<T> type, Closure handler) {
        InvocationHandler invocationHandler = { Object ignored, Method method, Object[] args ->
            handler.call(method, args)
        } as InvocationHandler
        return Proxy.newProxyInstance(type.classLoader, [type] as Class[], invocationHandler) as T
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null
        }
        if (returnType == boolean.class) {
            return false
        }
        if (returnType == char.class) {
            return (char) 0
        }
        return 0
    }

    private static void setField(Object target, String name, Object value) {
        Field field = target.class.getDeclaredField(name)
        field.setAccessible(true)
        field.set(target, value)
    }

    private void withManagementServerIpProperties(Map<String, String> properties, Closure closure) {
        List<String> managedKeys = [
                "management.server.ip",
                "management.server.ip4",
                "management.server.ip6",
        ]
        Map<String, String> oldValues = [:]
        managedKeys.each { key -> oldValues[key] = System.getProperty(key) }

        try {
            resetCachedManagementServerIp()
            managedKeys.each { System.clearProperty(it) }
            properties.each { key, value -> System.setProperty(key, value) }
            closure.call()
        } finally {
            managedKeys.each { key ->
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
        field.setAccessible(true)
        field.set(null, null)
    }
}
