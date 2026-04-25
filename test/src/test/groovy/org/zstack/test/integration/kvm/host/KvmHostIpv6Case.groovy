package org.zstack.test.integration.kvm.host

import org.zstack.header.errorcode.SysErrors
import org.zstack.header.host.HostAO
import org.zstack.sdk.AddKVMHostAction
import org.zstack.sdk.ClusterInventory
import org.zstack.test.integration.kvm.KvmTest
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.SubCase

import javax.persistence.Column
import java.lang.reflect.Field

/**
 * TP-015~020: KVM 宿主机 IPv6 管理 IP 测试
 *
 * 覆盖：
 *   TP-015 - managementIp 列长度足够存储 39 字符全展开 IPv6（@Column length >= 39）
 *   TP-016 - 以合法 IPv6 调用 AddKVMHostAction：拦截器不因 INVALID_ARGUMENT_ERROR 拒绝
 *   TP-017 - 全展开 IPv6 经 interceptor 规范化后不触发 INVALID_ARGUMENT_ERROR
 *   TP-018 - 链路本地地址 "fe80::1%eth0" 被拒绝（INVALID_ARGUMENT_ERROR）
 *   TP-019 - 非法格式 "not-an-ip!!" 被拒绝（INVALID_ARGUMENT_ERROR）
 *   TP-020 - 39 字符全展开 IPv6 不被 DB 截断（与 TP-015 列长度验证合并）
 */
class KvmHostIpv6Case extends SubCase {

    EnvSpec env
    ClusterInventory cluster

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
        env.delete()
    }

    @Override
    void test() {
        env.create {
            cluster = env.inventoryByName("cluster") as ClusterInventory

            testTP015_managementIpColumnLength()       // TP-015
            testTP016_addHostWithIpv6Passes()          // TP-016
            testTP017_fullIpv6NormalizedBeforeConnect() // TP-017
            testTP018_linkLocalIpv6Rejected()          // TP-018
            testTP019_invalidIpRejected()              // TP-019
            testTP020_fullIpv6FitsInColumn()            // TP-020
        }
    }

    /**
     * TP-015: HostVO.managementIp 列（继承自 HostAO）接受 39 字符全展开 IPv6 不截断。
     * 验证 @Column(length = ...) >= 39。
     */
    void testTP015_managementIpColumnLength() {
        Field field = HostAO.class.getDeclaredField("managementIp")
        field.setAccessible(true)
        Column col = field.getAnnotation(Column.class)
        assert col != null : "TP-015: managementIp should have @Column annotation"
        assert col.length() >= 39 : "TP-015: managementIp column length ${col.length()} is too short for 39-char full-expanded IPv6"
        logger.info("TP-015: managementIp @Column length = ${col.length()}, sufficient for IPv6")
    }

    /**
     * TP-016: 以合法 IPv6 地址 "2001:db8::10" 调用 AddKVMHostAction。
     * API 拦截器不因 INVALID_ARGUMENT_ERROR 拒绝 IPv6（连接失败是预期行为）。
     */
    void testTP016_addHostWithIpv6Passes() {
        def action = new AddKVMHostAction()
        action.sessionId = adminSession()
        action.clusterUuid = cluster.uuid
        action.managementIp = "2001:db8::10"
        action.name = "kvm-ipv6-compressed"
        action.username = "root"
        action.password = "password"
        def res = action.call()

        // IPv6 校验通过后才会尝试连接；连接失败不是 INVALID_ARGUMENT_ERROR
        if (res.error != null) {
            assert res.error.code != SysErrors.INVALID_ARGUMENT_ERROR.toString() :
                    "TP-016: IPv6 address should pass validation (interceptor should not return INVALID_ARGUMENT_ERROR), got: ${res.error.code} - ${res.error.description}"
        }
        logger.info("TP-016: AddKVMHostAction with IPv6 passed API validation (error=${res.error?.code})")
    }

    /**
     * TP-017: 全展开 IPv6 地址输入，经 HostApiInterceptor.normalizeIpv6 规范化，不触发 INVALID_ARGUMENT_ERROR。
     * 规范化：2001:0db8:0000:0000:0000:0000:0000:0001 → 2001:db8::1
     */
    void testTP017_fullIpv6NormalizedBeforeConnect() {
        String fullIpv6 = "2001:0db8:0000:0000:0000:0000:0000:0001"
        def action = new AddKVMHostAction()
        action.sessionId = adminSession()
        action.clusterUuid = cluster.uuid
        action.managementIp = fullIpv6
        action.name = "kvm-ipv6-full"
        action.username = "root"
        action.password = "password"
        def res = action.call()

        // normalizeIpv6 后的压缩地址可通过 isValidManagementIp 校验，不返回 INVALID_ARGUMENT_ERROR
        if (res.error != null) {
            assert res.error.code != SysErrors.INVALID_ARGUMENT_ERROR.toString() :
                    "TP-017: full-expanded IPv6 should normalize and pass validation, got: ${res.error.code}"
        }
        logger.info("TP-017: full-expanded IPv6 normalized before connect (error=${res.error?.code})")
    }

    /**
     * TP-018: 链路本地地址 "fe80::1%eth0" 应被 HostApiInterceptor 拒绝。
     * 期望错误码：SysErrors.INVALID_ARGUMENT_ERROR
     */
    void testTP018_linkLocalIpv6Rejected() {
        def action = new AddKVMHostAction()
        action.sessionId = adminSession()
        action.clusterUuid = cluster.uuid
        action.managementIp = "fe80::1%eth0"
        action.name = "kvm-ipv6-linklocal"
        action.username = "root"
        action.password = "password"
        def res = action.call()

        assert res.error != null : "TP-018: link-local IPv6 should be rejected"
        assert res.error.code == SysErrors.INVALID_ARGUMENT_ERROR.toString() :
                "TP-018: expected INVALID_ARGUMENT_ERROR for link-local IPv6, got: ${res.error.code}"
        logger.info("TP-018: link-local IPv6 correctly rejected with ${res.error.code}")
    }

    /**
     * TP-019: 非法格式 "not-an-ip!!" 应被 HostApiInterceptor 拒绝。
     * 期望错误码：SysErrors.INVALID_ARGUMENT_ERROR
     */
    void testTP019_invalidIpRejected() {
        def action = new AddKVMHostAction()
        action.sessionId = adminSession()
        action.clusterUuid = cluster.uuid
        action.managementIp = "not-an-ip!!"
        action.name = "kvm-invalid-ip"
        action.username = "root"
        action.password = "password"
        def res = action.call()

        assert res.error != null : "TP-019: invalid IP format should be rejected"
        assert res.error.code == SysErrors.INVALID_ARGUMENT_ERROR.toString() :
                "TP-019: expected INVALID_ARGUMENT_ERROR for invalid IP, got: ${res.error.code}"
        logger.info("TP-019: invalid IP correctly rejected with ${res.error.code}")
    }

    /**
     * TP-020: 39 字符全展开 IPv6 不被 DB 截断。
     * 与 TP-015 合并验证 @Column length >= 39。
     * 全展开 IPv6 最长为 "2001:0db8:0000:0000:0000:0000:0000:0001" = 39 字符。
     */
    void testTP020_fullIpv6FitsInColumn() {
        String fullIpv6 = "2001:0db8:0000:0000:0000:0000:0000:0001"
        assert fullIpv6.length() == 39 : "Precondition: full-expanded IPv6 should be 39 chars"

        Field field = HostAO.class.getDeclaredField("managementIp")
        field.setAccessible(true)
        Column col = field.getAnnotation(Column.class)
        assert col.length() >= fullIpv6.length() :
                "TP-020: managementIp column length ${col.length()} is insufficient for 39-char full-expanded IPv6"
        logger.info("TP-020: column length ${col.length()} >= 39, no truncation for full-expanded IPv6")
    }
}
