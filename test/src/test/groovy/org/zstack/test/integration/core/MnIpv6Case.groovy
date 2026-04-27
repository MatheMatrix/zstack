package org.zstack.test.integration.core

import org.zstack.core.Platform
import org.zstack.core.config.GlobalConfigDef
import org.zstack.core.config.NetworkGlobalConfig
import org.zstack.core.rest.RESTFacadeImpl
import org.zstack.testlib.SubCase
import org.zstack.utils.network.IPv6NetworkUtils
import org.zstack.utils.network.NetworkUtils

import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * TP-001~007, TP-021~024, TP-030~031: Management node IPv6 support core tests
 *
 * All tests are pure unit / reflection tests requiring no Spring context.
 * Discovered and run automatically by CoreLibraryTest.runSubCases().
 *
 * Coverage:
 *   TP-001 - NetworkGlobalConfig.PREFER_IPV6 default value is false
 *   TP-002 - NetworkGlobalConfig.PREFER_IPV6 category and name are correct
 *   TP-003 - Platform.getManagementServerIp() returns non-null IP in IPv4-only environment
 *   TP-004 - PREFER_IPV6=false (default) returns IPv4 (CI environment validation)
 *   TP-005 - PREFER_IPV6=true falls back to IPv4 gracefully (no exception when no IPv6 interface)
 *   TP-006 - getManagementServerCidr() does not throw (returns CIDR format string in IPv4 environment)
 *   TP-007 - CIDR format is valid (contains "/", prefix <= 32/128)
 *   TP-021 - sanitizeCallbackUrl(IPv4 URL) returns unchanged
 *   TP-022 - sanitizeCallbackUrl(bare IPv6 URL) corrected to bracketed format or preserved as-is
 *   TP-023 - Platform.getManagementServerId() returns non-null UUID format string
 *   TP-024 - two successive calls return the same UUID (persisted)
 *   TP-030 - jgroupsAddr(IPv6, port) → "[ip][port]" format
 *   TP-031 - jgroupsAddr(IPv4, port) → "ip[port]" format
 */
class MnIpv6Case extends SubCase {

    @Override
    void setup() {
        // pure unit / static method tests, no Spring required
    }

    @Override
    void environment() {
        // no environment dependencies
    }

    @Override
    void clean() {
        // no cleanup needed
    }

    @Override
    void test() {
        testTP001_preferIpv6DefaultValue()          // TP-001
        testTP002_preferIpv6CategoryAndName()       // TP-002
        testTP003_getManagementServerIpNonNull()    // TP-003
        testTP004_getManagementServerIpIpv4()       // TP-004
        testTP005_getManagementServerIpFallback()   // TP-005
        testTP006_getManagementServerCidrFormat()   // TP-006
        testTP007_getManagementServerCidrValid()    // TP-007
        testTP021_sanitizeCallbackUrlIpv4()         // TP-021
        testTP022_sanitizeCallbackUrlBareIpv6()     // TP-022
        testTP023_getManagementServerIdNonNull()    // TP-023
        testTP024_getManagementServerIdStable()     // TP-024
        testTP030_jgroupsAddrIpv6()                 // TP-030
        testTP031_jgroupsAddrIpv4()                 // TP-031
    }

    // ===== F-001: GlobalConfig PREFER_IPV6 =====

    /**
     * TP-001: NetworkGlobalConfig.PREFER_IPV6 default value annotation is "false"
     */
    void testTP001_preferIpv6DefaultValue() {
        Field field = NetworkGlobalConfig.class.getDeclaredField("PREFER_IPV6")
        GlobalConfigDef annotation = field.getAnnotation(GlobalConfigDef.class)
        assert annotation != null : "TP-001: PREFER_IPV6 should have @GlobalConfigDef annotation"
        assert annotation.defaultValue() == "false" : "TP-001: PREFER_IPV6 defaultValue should be 'false', got: ${annotation.defaultValue()}"
        logger.info("TP-001: PREFER_IPV6 defaultValue = '${annotation.defaultValue()}'")
    }

    /**
     * TP-002: NetworkGlobalConfig.PREFER_IPV6 category and name are correct
     */
    void testTP002_preferIpv6CategoryAndName() {
        String category = NetworkGlobalConfig.PREFER_IPV6.getCategory()
        String name = NetworkGlobalConfig.PREFER_IPV6.getName()
        assert category == "network" : "TP-002: PREFER_IPV6 category should be 'network', got: $category"
        assert name == "management.server.prefer.ipv6" :
                "TP-002: PREFER_IPV6 name should be 'management.server.prefer.ipv6', got: $name"
        logger.info("TP-002: PREFER_IPV6 category='$category', name='$name'")
    }

    // ===== F-002: Platform.getManagementServerIp =====

    /**
     * TP-003: Platform.getManagementServerIp() returns non-null address in IPv4-only environment
     */
    void testTP003_getManagementServerIpNonNull() {
        String ip = Platform.getManagementServerIp()
        assert ip != null : "TP-003: getManagementServerIp() should return non-null"
        boolean isIp = NetworkUtils.isIpv4Address(ip) || IPv6NetworkUtils.isIpv6Address(ip)
        assert isIp : "TP-003: getManagementServerIp() should return valid IP, got: $ip"
        logger.info("TP-003: getManagementServerIp() = $ip")
    }

    /**
     * TP-004: PREFER_IPV6=false (default) — CI IPv4 environment returns IPv4 address
     */
    void testTP004_getManagementServerIpIpv4() {
        String ip = Platform.getManagementServerIp()
        assert ip != null : "TP-004: getManagementServerIp() should not be null"
        // CI environment is IPv4-only; PREFER_IPV6 defaults to false → must return IPv4
        assert NetworkUtils.isIpv4Address(ip) : "TP-004: CI is IPv4-only with PREFER_IPV6=false, expected IPv4 address, got: $ip"
        logger.info("TP-004: management server IP = $ip (preferIpv6=false default)")
    }

    /**
     * TP-005: PREFER_IPV6=true with no IPv6 interface — gracefully falls back to IPv4, no exception.
     * Clears the static managementServerIp cache first to ensure the code path is re-executed.
     */
    void testTP005_getManagementServerIpFallback() {
        def original = NetworkGlobalConfig.PREFER_IPV6.value(Boolean.class)
        try {
            // Clear the static cache so getManagementServerIp() re-evaluates with the new config.
            Field cacheField = Platform.class.getDeclaredField("managementServerIp")
            cacheField.setAccessible(true)
            cacheField.set(null, null)

            NetworkGlobalConfig.PREFER_IPV6.updateValue(true)
            String ip = null
            try {
                ip = Platform.getManagementServerIp()
            } catch (Exception e) {
                assert false : "TP-005: getManagementServerIp() should not throw exception even when PREFER_IPV6=true with no IPv6, got: ${e.message}"
            }
            assert ip != null : "TP-005: getManagementServerIp() should return fallback IP, not null"
            logger.info("TP-005: PREFER_IPV6 fallback returns $ip")
        } finally {
            NetworkGlobalConfig.PREFER_IPV6.updateValue(original)
            // Restore cache to avoid affecting subsequent tests.
            Field cacheField = Platform.class.getDeclaredField("managementServerIp")
            cacheField.setAccessible(true)
            cacheField.set(null, null)
        }
    }

    // ===== F-003: getManagementServerCidr =====

    /**
     * TP-006: getManagementServerCidr() does not throw (should return CIDR format string in IPv4 environment)
     */
    void testTP006_getManagementServerCidrFormat() {
        String cidr = null
        try {
            cidr = Platform.getManagementServerCidr()
        } catch (Exception e) {
            assert false : "TP-006: getManagementServerCidr() should not throw, got: ${e.message}"
        }
        // cidr may be null in CI environment (when management IP is not listed in ip addr output)
        if (cidr != null) {
            assert cidr.contains("/") : "TP-006: CIDR should contain '/', got: $cidr"
        }
        logger.info("TP-006: getManagementServerCidr() = $cidr")
    }

    /**
     * TP-007: CIDR format is valid (contains "/", prefix <= 32 for IPv4 / <= 128 for IPv6)
     */
    void testTP007_getManagementServerCidrValid() {
        String cidr = Platform.getManagementServerCidr()
        if (cidr == null) {
            logger.warn("TP-007: getManagementServerCidr() returned null in this environment, skipping prefix validation")
            return
        }
        assert cidr.contains("/") : "TP-007: CIDR should contain '/', got: $cidr"
        String[] parts = cidr.split("/")
        assert parts.length == 2 : "TP-007: CIDR should have exactly 2 parts, got: $cidr"
        int prefix = Integer.parseInt(parts[1].trim())
        String network = parts[0]
        if (NetworkUtils.isIpv4Address(network)) {
            assert prefix >= 0 && prefix <= 32 : "TP-007: IPv4 prefix should be 0-32, got: $prefix"
        } else {
            assert prefix >= 0 && prefix <= 128 : "TP-007: IPv6 prefix should be 0-128, got: $prefix"
        }
        logger.info("TP-007: CIDR '$cidr' is valid (prefix=$prefix)")
    }

    // ===== F-007: RESTFacadeImpl.sanitizeCallbackUrl =====

    /**
     * TP-021: sanitizeCallbackUrl(IPv4 URL) returns unchanged (no bracket changes for IPv4)
     */
    void testTP021_sanitizeCallbackUrlIpv4() {
        Method method = RESTFacadeImpl.class.getDeclaredMethod("sanitizeCallbackUrl", String.class)
        method.setAccessible(true)

        String ipv4Url = "http://192.168.1.1:8080/callback"
        String result = method.invoke(null, ipv4Url) as String
        assert result == ipv4Url : "TP-021: IPv4 callback URL should be returned unchanged, got: $result"
        logger.info("TP-021: sanitizeCallbackUrl('$ipv4Url') = '$result'")
    }

    /**
     * TP-022: sanitizeCallbackUrl(bare IPv6 URL) detects and brackets the IPv6 address (or preserves + WARN)
     */
    void testTP022_sanitizeCallbackUrlBareIpv6() {
        Method method = RESTFacadeImpl.class.getDeclaredMethod("sanitizeCallbackUrl", String.class)
        method.setAccessible(true)

        String bareIpv6Url = "http://2001:db8::1:8080/callback"
        String result = method.invoke(null, bareIpv6Url) as String
        assert result != null : "TP-022: sanitizeCallbackUrl should not return null for bare IPv6 URL"
        assert result.contains('[2001:db8::1]') : "TP-022: sanitizeCallbackUrl should bracket the IPv6 address, got: $result"
        logger.info("TP-022: sanitizeCallbackUrl('$bareIpv6Url') = '$result'")
    }

    // ===== F-008: UUID persistence =====

    /**
     * TP-023: Platform.getManagementServerId() returns non-null 32-char hex UUID string
     */
    void testTP023_getManagementServerIdNonNull() {
        String msId = Platform.getManagementServerId()
        assert msId != null : "TP-023: getManagementServerId() should not return null (Platform not fully initialized?)"
        assert msId.length() == 32 : "TP-023: management server ID should be 32-char hex UUID, got length: ${msId.length()}"
        assert msId.matches("[0-9a-f]+") : "TP-023: management server ID should be lowercase hex, got: $msId"
        logger.info("TP-023: getManagementServerId() = $msId")
    }

    /**
     * TP-024: two successive calls to getManagementServerId() return the same UUID (persisted)
     */
    void testTP024_getManagementServerIdStable() {
        String id1 = Platform.getManagementServerId()
        String id2 = Platform.getManagementServerId()
        assert id1 != null : "TP-024: getManagementServerId() should not return null"
        assert id1 == id2 : "TP-024: getManagementServerId() should return stable UUID, got: '$id1' vs '$id2'"
        logger.info("TP-024: getManagementServerId() is stable: $id1")
    }

    // ===== F-010: JGroups IPv6 bracket fix =====

    /**
     * TP-030: jgroupsAddr(IPv6, port) → "[2001:db8::1][7805]"
     */
    void testTP030_jgroupsAddrIpv6() {
        Method method = Platform.class.getDeclaredMethod("jgroupsAddr", String.class, String.class)
        method.setAccessible(true)

        String result = method.invoke(null, "2001:db8::1", "7805") as String
        assert result == "[2001:db8::1][7805]" :
                "TP-030: IPv6 jgroupsAddr should use [addr][port] format, got: $result"
        logger.info("TP-030: jgroupsAddr('2001:db8::1', '7805') = '$result'")
    }

    /**
     * TP-031: jgroupsAddr(IPv4, port) → "192.168.1.1[7805]" (IPv4 without brackets)
     */
    void testTP031_jgroupsAddrIpv4() {
        Method method = Platform.class.getDeclaredMethod("jgroupsAddr", String.class, String.class)
        method.setAccessible(true)

        String result = method.invoke(null, "192.168.1.1", "7805") as String
        assert result == "192.168.1.1[7805]" :
                "TP-031: IPv4 jgroupsAddr should use addr[port] format, got: $result"
        logger.info("TP-031: jgroupsAddr('192.168.1.1', '7805') = '$result'")
    }
}
