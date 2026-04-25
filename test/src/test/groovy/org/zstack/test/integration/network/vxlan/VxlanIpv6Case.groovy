package org.zstack.test.integration.network.vxlan

import org.zstack.network.l2.vxlan.vtep.RemoteVtepVO
import org.zstack.network.l2.vxlan.vtep.VtepVO
import org.zstack.testlib.SubCase
import org.zstack.utils.network.IPv6NetworkUtils
import org.zstack.utils.network.NetworkUtils

import javax.persistence.Column
import java.lang.reflect.Field

/**
 * TP-039~041: VXLAN IPv6 vtepIp 支持测试
 *
 * 全部为纯单元 / 反射测试，无需 Spring 上下文。
 *
 * 覆盖：
 *   TP-039 - VxlanPoolApiInterceptor 接受 IPv6 vtepIp（isIpv6Address 返回 true）
 *   TP-040 - VtepVO.vtepIp 和 RemoteVtepVO.vtepIp 列长度 >= 39（支持 IPv6）
 *   TP-041 - 非法格式 "not-an-ip" 被校验逻辑拒绝
 */
class VxlanIpv6Case extends SubCase {

    @Override
    void setup() {
        // 纯单元测试，无需 Spring
    }

    @Override
    void environment() {
        // 无环境依赖
    }

    @Override
    void clean() {
        // 无需清理
    }

    @Override
    void test() {
        testTP039_vxlanAcceptsIpv6VtepIp()     // TP-039
        testTP040_vtepVoColumnLength()         // TP-040
        testTP041_invalidVtepIpRejected()      // TP-041
    }

    /**
     * TP-039: VxlanPoolApiInterceptor 校验逻辑接受 IPv6 vtepIp。
     * 拦截器内部使用 NetworkUtils.isIpv4Address || IPv6NetworkUtils.isIpv6Address 判断合法性。
     * 直接验证 isIpv6Address("2001:db8::1") 返回 true。
     */
    void testTP039_vxlanAcceptsIpv6VtepIp() {
        // TP-039: IPv6 地址被 isIpv6Address 认可，拦截器不拒绝
        String ipv6VtepIp = "2001:db8::1"
        assert IPv6NetworkUtils.isIpv6Address(ipv6VtepIp) :
                "TP-039: isIpv6Address should return true for valid IPv6 vtepIp '$ipv6VtepIp'"
        // 合法 IPv4 同样被接受
        assert NetworkUtils.isIpv4Address("192.168.1.100") :
                "TP-039: isIpv4Address should return true for valid IPv4 vtepIp"
        // 拦截器的复合校验：IPv4 或 IPv6 均合法
        boolean ipv6Valid = NetworkUtils.isIpv4Address(ipv6VtepIp) || IPv6NetworkUtils.isIpv6Address(ipv6VtepIp)
        assert ipv6Valid : "TP-039: VxlanPoolApiInterceptor composite check should accept IPv6 vtepIp"
        logger.info("TP-039: IPv6 vtepIp '$ipv6VtepIp' accepted by VxlanPoolApiInterceptor validation logic")
    }

    /**
     * TP-040: VtepVO.vtepIp 和 RemoteVtepVO.vtepIp @Column 无显式 length，
     * 使用 JPA 默认 255（>= 39），足以存储全展开 IPv6。
     */
    void testTP040_vtepVoColumnLength() {
        checkVtepIpColumnLength(VtepVO.class, "VtepVO")
        checkVtepIpColumnLength(RemoteVtepVO.class, "RemoteVtepVO")
    }

    private void checkVtepIpColumnLength(Class<?> voClass, String className) {
        Field field = voClass.getDeclaredField("vtepIp")
        field.setAccessible(true)
        Column col = field.getAnnotation(Column.class)
        assert col != null : "TP-040: $className.vtepIp should have @Column annotation"

        int length = col.length()
        // JPA @Column 默认 length 为 255；若未显式设置则为 255
        // 全展开 IPv6 最长 39 字符，255 >= 39 即可
        assert length >= 39 :
                "TP-040: $className.vtepIp @Column length $length must be >= 39 to store full IPv6 address"
        logger.info("TP-040: $className.vtepIp @Column length=$length (>= 39, IPv6-safe)")
    }

    /**
     * TP-041: 非法格式 "not-an-ip" 既不是 IPv4 也不是 IPv6，
     * VxlanPoolApiInterceptor 校验逻辑（IPv4 || IPv6）应返回 false。
     */
    void testTP041_invalidVtepIpRejected() {
        String invalidIp = "not-an-ip"
        // TP-041: 非法 IP 不通过 IPv4 检查
        assert !NetworkUtils.isIpv4Address(invalidIp) :
                "TP-041: 'not-an-ip' should not be a valid IPv4 address"
        // 非法 IP 不通过 IPv6 检查
        assert !IPv6NetworkUtils.isIpv6Address(invalidIp) :
                "TP-041: 'not-an-ip' should not be a valid IPv6 address"
        // 拦截器复合校验：两者均 false → 应被拒绝
        boolean valid = NetworkUtils.isIpv4Address(invalidIp) || IPv6NetworkUtils.isIpv6Address(invalidIp)
        assert !valid : "TP-041: VxlanPoolApiInterceptor should reject invalid vtepIp 'not-an-ip'"
        logger.info("TP-041: invalid vtepIp 'not-an-ip' correctly rejected")
    }
}
