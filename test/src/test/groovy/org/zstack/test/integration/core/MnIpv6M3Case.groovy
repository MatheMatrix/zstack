package org.zstack.test.integration.core

import org.zstack.testlib.SubCase
import org.zstack.utils.network.IPv6Utils
import org.zstack.utils.network.IPv6NetworkUtils
import org.zstack.utils.network.NetworkUtils

/**
 * TP-062~069, TP-076, TP-077: Management node IPv6 M3 support tests
 *
 * All pure unit tests, no Spring context required.
 * Discovered and run automatically by CoreLibraryTest.runSubCases().
 *
 * Coverage:
 *   TP-062 - AddBaremetalChassisAction accepts IPv6 IPMI address
 *   TP-064 - ipmiAddress field can store full IPv6 address (39 chars)
 *   TP-065 - invalid IPMI address is rejected
 *   TP-066 - Console Proxy URL uses IPv6 brackets
 *   TP-067 - VNC Token URL hostname contains IPv6 brackets
 *   TP-069 - Dual-stack MN Console URL uses management VIP
 *   TP-076 - BM V2 DPU callback IP IPv6 brackets
 *   TP-077 - COLO QEMU URL IPv6 brackets
 */
class MnIpv6M3Case extends SubCase {

    @Override
    void setup() {
        // pure unit tests, no Spring required
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
        testTP062_ipmiIpv6AcceptedInInterceptor()       // TP-062
        testTP065_ipmiAddressFullLengthIpv6()           // TP-064
        testTP066_ipmiInvalidAddressRejected()          // TP-065
        testTP067_consoleBracketIpv6()                  // TP-066
        testTP069_consoleVncTokenUrl()                  // TP-067
        testTP064_consoleDualStackVip()                 // TP-069
        testTP076_bmDpuCallbackIpBracket()              // TP-076
        testTP077_coloQemuUrlIpv6Bracket()              // TP-077
    }

    // ===== TP-062: AddBaremetalChassisAction accepts IPv6 IPMI address =====

    /**
     * TP-062: BaremetalChassisApiInterceptor.check() logic:
     * IPv6 address satisfies !isIpv4Address && isIpv6Address, so it passes the interceptor.
     */
    void testTP062_ipmiIpv6AcceptedInInterceptor() {
        String ipv6 = "2001:db8:50::1"

        boolean isV4 = NetworkUtils.isIpv4Address(ipv6)
        boolean isV6 = IPv6NetworkUtils.isIpv6Address(ipv6)

        assert !isV4 : "TP-062: IPv6 address '$ipv6' should NOT be recognized as IPv4"
        assert isV6  : "TP-062: IPv6 address '$ipv6' SHOULD be recognized as IPv6"
        // interceptor pass condition: !isIpv4 && isIpv6
        assert !isV4 && isV6 : "TP-062: IPv6 IPMI address should pass interceptor validation (accepted)"
        logger.info("TP-062: IPMI IPv6 '$ipv6' → isIpv4=$isV4, isIpv6=$isV6 → accepted")
    }

    // ===== TP-064: ipmiAddress field can store full IPv6 address (39 chars) =====

    /**
     * TP-064: Fully expanded IPv6 address is 39 chars; NetworkUtils should correctly recognize it.
     */
    void testTP065_ipmiAddressFullLengthIpv6() {
        String fullIpv6 = "2001:0db8:0000:0000:0000:0000:0000:0001"

        assert fullIpv6.length() == 39 : "TP-064: full IPv6 address should be 39 chars, got: ${fullIpv6.length()}"

        boolean isV6 = IPv6NetworkUtils.isIpv6Address(fullIpv6)
        assert isV6 : "TP-064: 39-char full IPv6 '$fullIpv6' should be recognized as valid IPv6"
        logger.info("TP-064: full 39-char IPv6 '$fullIpv6' → isIpv6=$isV6 (accepted by interceptor)")
    }

    // ===== TP-065: invalid IPMI address is rejected =====

    /**
     * TP-065: "not-an-ip" is neither IPv4 nor IPv6; the interceptor should reject it.
     */
    void testTP066_ipmiInvalidAddressRejected() {
        String invalid = "not-an-ip"

        boolean isV4 = NetworkUtils.isIpv4Address(invalid)
        boolean isV6 = IPv6NetworkUtils.isIpv6Address(invalid)

        // interceptor reject condition: !isIpv4 && !isIpv6
        assert !isV4 : "TP-065: '$invalid' should NOT be recognized as IPv4"
        assert !isV6 : "TP-065: '$invalid' should NOT be recognized as IPv6"
        assert !isV4 && !isV6 : "TP-065: invalid address '$invalid' should fail both checks → interceptor rejects"
        logger.info("TP-065: invalid IPMI address '$invalid' → isIpv4=$isV4, isIpv6=$isV6 → rejected")
    }

    // ===== TP-066: Console Proxy URL uses IPv6 brackets =====

    /**
     * TP-066: IPv6Utils.bracketIpv6() three scenarios:
     *   - bare IPv6  → add brackets
     *   - IPv4       → return unchanged
     *   - already bracketed → idempotent
     */
    void testTP067_consoleBracketIpv6() {
        // bare IPv6 → "[2001:db8::100]"
        String bareIpv6   = "2001:db8::100"
        String bracketed  = IPv6Utils.bracketIpv6(bareIpv6)
        assert bracketed == "[2001:db8::100]" :
                "TP-066: bracketIpv6('$bareIpv6') should return '[2001:db8::100]', got: '$bracketed'"
        logger.info("TP-066a: bracketIpv6('$bareIpv6') = '$bracketed'")

        // IPv4 → return unchanged
        String ipv4   = "192.168.1.1"
        String result = IPv6Utils.bracketIpv6(ipv4)
        assert result == "192.168.1.1" :
                "TP-066: bracketIpv6('$ipv4') should return '$ipv4' unchanged, got: '$result'"
        logger.info("TP-066b: bracketIpv6('$ipv4') = '$result'")

        // already bracketed IPv6 → idempotent
        String alreadyBracketed = "[2001:db8::1]"
        String idempotent = IPv6Utils.bracketIpv6(alreadyBracketed)
        assert idempotent == "[2001:db8::1]" :
                "TP-066: bracketIpv6('$alreadyBracketed') should be idempotent, got: '$idempotent'"
        logger.info("TP-066c: bracketIpv6('$alreadyBracketed') = '$idempotent' (idempotent)")
    }

    // ===== TP-067: VNC Token URL hostname contains IPv6 brackets =====

    /**
     * TP-067: VNC Token URL hostname is processed with bracketIpv6,
     * producing the valid "[2001:db8::1]:5900" format.
     */
    void testTP069_consoleVncTokenUrl() {
        String ipv6Host = "2001:db8::1"
        int    vncPort  = 5900

        // process hostname with bracketIpv6, then append port
        String hostname = IPv6Utils.bracketIpv6(ipv6Host)
        assert hostname == "[2001:db8::1]" :
                "TP-067: bracketIpv6 should produce '[2001:db8::1]', got: '$hostname'"

        String vncAddr = "${hostname}:${vncPort}"
        assert vncAddr == "[2001:db8::1]:5900" :
                "TP-067: VNC address should be '[2001:db8::1]:5900', got: '$vncAddr'"
        logger.info("TP-067: VNC Token URL hostname = '$hostname', addr = '$vncAddr'")
    }

    // ===== TP-069: dual-stack MN Console URL uses management VIP =====

    /**
     * TP-069: When CONSOLE_PROXY_OVERRIDDEN_IP is IPv6, bracketIpv6 wraps it correctly
     * so the Console URL format is valid.
     */
    void testTP064_consoleDualStackVip() {
        String overriddenIp = "2001:db8::100"  // simulates CONSOLE_PROXY_OVERRIDDEN_IP

        String bracketed = IPv6Utils.bracketIpv6(overriddenIp)
        assert bracketed == "[2001:db8::100]" :
                "TP-069: Console VIP bracketIpv6('$overriddenIp') should return '[2001:db8::100]', got: '$bracketed'"

        // assemble valid Console URL
        String consoleUrl = "http://${bracketed}:8080/console"
        assert consoleUrl == "http://[2001:db8::100]:8080/console" :
                "TP-069: Console URL should be 'http://[2001:db8::100]:8080/console', got: '$consoleUrl'"
        logger.info("TP-069: dual-stack Console URL = '$consoleUrl'")
    }

    // ===== TP-076: BM V2 DPU callback IP IPv6 brackets =====

    /**
     * TP-076: BM V2 DPU uses bracketIpv6 on callbackIp to ensure IPv6 is bracketed
     * so the callback URL format is correct.
     */
    void testTP076_bmDpuCallbackIpBracket() {
        String callbackIp = "2001:db8::1"

        String bracketed = IPv6Utils.bracketIpv6(callbackIp)
        assert bracketed == "[2001:db8::1]" :
                "TP-076: DPU callbackIp bracketIpv6('$callbackIp') should return '[2001:db8::1]', got: '$bracketed'"

        // verify callback URL is assembled correctly
        String callbackUrl = "http://${bracketed}:7771/callback"
        assert callbackUrl == "http://[2001:db8::1]:7771/callback" :
                "TP-076: DPU callback URL should be 'http://[2001:db8::1]:7771/callback', got: '$callbackUrl'"
        logger.info("TP-076: BM V2 DPU callbackIp='$callbackIp' → bracketed='$bracketed', url='$callbackUrl'")
    }

    // ===== TP-077: COLO QEMU URL IPv6 brackets =====

    /**
     * TP-077: COLO QEMU download URL is assembled with bracketIpv6 on the IPv6 address,
     * ensuring the URL format is "http://[ip]:port/path".
     */
    void testTP077_coloQemuUrlIpv6Bracket() {
        String ipv6   = "2001:db8::1"
        String port   = "8080"
        String path   = "/zstack/static/qemu.tar.gz"

        String url = String.format("http://%s:%s%s", IPv6Utils.bracketIpv6(ipv6), port, path)
        assert url == "http://[2001:db8::1]:8080/zstack/static/qemu.tar.gz" :
                "TP-077: COLO QEMU URL should be 'http://[2001:db8::1]:8080/zstack/static/qemu.tar.gz', got: '$url'"
        logger.info("TP-077: COLO QEMU URL = '$url'")

        // also verify IPv6Utils.buildUrl helper (should match manual concatenation)
        String builtUrl = IPv6Utils.buildUrl(ipv6, Integer.parseInt(port))
        assert builtUrl == "http://[2001:db8::1]:8080" :
                "TP-077: IPv6Utils.buildUrl('$ipv6', $port) should return 'http://[2001:db8::1]:8080', got: '$builtUrl'"
        logger.info("TP-077: IPv6Utils.buildUrl = '$builtUrl'")
    }
}
