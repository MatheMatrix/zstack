package org.zstack.test.integration.utils

import org.zstack.testlib.SubCase
import org.zstack.utils.network.IPv6Utils

/**
 * TP-008~014: IPv6Utils 纯单元测试
 * 无需 Spring 上下文，直接测试静态工具方法。
 */
class IPv6UtilsCase extends SubCase {

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
        testBuildUrlIpv4()          // TP-008
        testBuildUrlIpv6()          // TP-009
        testBracketIpv6Idempotent() // TP-010
        testNormalizeIpv6()         // TP-011
        testIsValidMnIpLinkLocal()  // TP-012
        testIsValidMnIpInvalid()    // TP-013
        testIsValidMnIpValid()      // TP-014
    }

    /**
     * TP-008: buildUrl IPv4 → "http://192.168.1.1:8080"（无括号）
     */
    void testBuildUrlIpv4() {
        String url = IPv6Utils.buildUrl("192.168.1.1", 8080)
        assert url == "http://192.168.1.1:8080" : "TP-008: IPv4 URL should have no brackets, got: $url"
    }

    /**
     * TP-009: buildUrl IPv6 → "http://[2001:db8::1]:8080"（含括号）
     */
    void testBuildUrlIpv6() {
        String url = IPv6Utils.buildUrl("2001:db8::1", 8080)
        assert url == "http://[2001:db8::1]:8080" : "TP-009: IPv6 URL should be bracket-wrapped, got: $url"
    }

    /**
     * TP-010: bracketIpv6 幂等——已有括号不重复加，结果仍为 "[2001:db8::1]"
     */
    void testBracketIpv6Idempotent() {
        // 已有括号时，结果不变（幂等）
        String result = IPv6Utils.bracketIpv6("[2001:db8::1]")
        assert result == "[2001:db8::1]" : "TP-010: bracketIpv6 should be idempotent for already-bracketed address, got: $result"
        // 额外验证：无括号输入正确加括号
        String withBracket = IPv6Utils.bracketIpv6("2001:db8::1")
        assert withBracket == "[2001:db8::1]" : "TP-010: bracketIpv6 should add brackets to bare IPv6, got: $withBracket"
    }

    /**
     * TP-011: normalizeIpv6 全展开 "2001:0db8:0000:0000:0000:0000:0000:0001" → "2001:db8::1"
     */
    void testNormalizeIpv6() {
        String normalized = IPv6Utils.normalizeIpv6("2001:0db8:0000:0000:0000:0000:0000:0001")
        assert normalized == "2001:db8::1" : "TP-011: full-expanded IPv6 should normalize to compressed form, got: $normalized"
    }

    /**
     * TP-012: isValidManagementIp("fe80::1") → false（链路本地地址）
     */
    void testIsValidMnIpLinkLocal() {
        boolean result = IPv6Utils.isValidManagementIp("fe80::1")
        assert !result : "TP-012: fe80::1 (link-local) should not be a valid management IP"
    }

    /**
     * TP-013: isValidManagementIp("not-an-ip!!") → false（非法格式）
     */
    void testIsValidMnIpInvalid() {
        boolean result = IPv6Utils.isValidManagementIp("not-an-ip!!")
        assert !result : "TP-013: invalid IP string should not be a valid management IP"
    }

    /**
     * TP-014: isValidManagementIp("2001:db8::1") → true（合法全球单播 IPv6）
     */
    void testIsValidMnIpValid() {
        boolean result = IPv6Utils.isValidManagementIp("2001:db8::1")
        assert result : "TP-014: 2001:db8::1 should be a valid management IP"
    }
}
