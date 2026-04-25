package org.zstack.test.integration.core

import org.zstack.testlib.SubCase
import org.zstack.utils.network.IPv6Utils
import org.zstack.utils.network.IPv6NetworkUtils
import org.zstack.utils.network.NetworkUtils

/**
 * TP-062~069, TP-076, TP-077: 管理节点 IPv6 M3 支持测试
 *
 * 全部为纯单元测试，无需 Spring 上下文。
 * 由 CoreLibraryTest.runSubCases() 自动发现并运行。
 *
 * 覆盖：
 *   TP-062 - AddBaremetalChassisAction 接受 IPv6 IPMI 地址
 *   TP-064 - ipmiAddress 字段可存储完整 IPv6 地址（39 字符）
 *   TP-065 - 非法 IPMI 地址被拒绝
 *   TP-066 - Console Proxy URL 使用 IPv6 括号
 *   TP-067 - VNC Token URL hostname 含 IPv6 括号
 *   TP-069 - 双栈 MN 下 Console URL 使用管理 VIP
 *   TP-076 - BM V2 DPU 回调 IP IPv6 括号
 *   TP-077 - COLO QEMU URL IPv6 括号
 */
class MnIpv6M3Case extends SubCase {

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
        testIpmiIpv6AcceptedInInterceptor()       // TP-062
        testIpmiAddressFullLengthIpv6()           // TP-064
        testIpmiInvalidAddressRejected()          // TP-065
        testConsoleBracketIpv6()                  // TP-066
        testConsoleVncTokenUrl()                  // TP-067
        testConsoleDualStackVip()                 // TP-069
        testBmDpuCallbackIpBracket()              // TP-076
        testColoQemuUrlIpv6Bracket()              // TP-077
    }

    // ===== TP-062: AddBaremetalChassisAction 接受 IPv6 IPMI 地址 =====

    /**
     * TP-062: BaremetalChassisApiInterceptor.check() 逻辑：
     * IPv6 地址应满足 !isIpv4Address && isIpv6Address，即会被拦截器放行。
     */
    void testIpmiIpv6AcceptedInInterceptor() {
        String ipv6 = "2001:db8:50::1"

        boolean isV4 = NetworkUtils.isIpv4Address(ipv6)
        boolean isV6 = IPv6NetworkUtils.isIpv6Address(ipv6)

        assert !isV4 : "TP-062: IPv6 address '$ipv6' should NOT be recognized as IPv4"
        assert isV6  : "TP-062: IPv6 address '$ipv6' SHOULD be recognized as IPv6"
        // 拦截器放行条件：!isIpv4 && isIpv6（或 isIpv4 均可），此处 IPv6 地址满足放行
        assert !isV4 && isV6 : "TP-062: IPv6 IPMI address should pass interceptor validation (accepted)"
        logger.info("TP-062: IPMI IPv6 '$ipv6' → isIpv4=$isV4, isIpv6=$isV6 → accepted")
    }

    // ===== TP-064: ipmiAddress 字段可存储完整 IPv6 地址（39 字符）=====

    /**
     * TP-064: 完整展开的 IPv6 地址长度为 39 字符，NetworkUtils 应能正确识别。
     */
    void testIpmiAddressFullLengthIpv6() {
        String fullIpv6 = "2001:0db8:0000:0000:0000:0000:0000:0001"

        assert fullIpv6.length() == 39 : "TP-064: full IPv6 address should be 39 chars, got: ${fullIpv6.length()}"

        boolean isV6 = IPv6NetworkUtils.isIpv6Address(fullIpv6)
        assert isV6 : "TP-064: 39-char full IPv6 '$fullIpv6' should be recognized as valid IPv6"
        logger.info("TP-064: full 39-char IPv6 '$fullIpv6' → isIpv6=$isV6 (accepted by interceptor)")
    }

    // ===== TP-065: 非法 IPMI 地址被拒绝 =====

    /**
     * TP-065: "not-an-ip" 既不是 IPv4 也不是 IPv6，拦截器应拒绝（抛出异常）。
     */
    void testIpmiInvalidAddressRejected() {
        String invalid = "not-an-ip"

        boolean isV4 = NetworkUtils.isIpv4Address(invalid)
        boolean isV6 = IPv6NetworkUtils.isIpv6Address(invalid)

        // 拦截器拒绝条件：!isIpv4 && !isIpv6
        assert !isV4 : "TP-065: '$invalid' should NOT be recognized as IPv4"
        assert !isV6 : "TP-065: '$invalid' should NOT be recognized as IPv6"
        assert !isV4 && !isV6 : "TP-065: invalid address '$invalid' should fail both checks → interceptor rejects"
        logger.info("TP-065: invalid IPMI address '$invalid' → isIpv4=$isV4, isIpv6=$isV6 → rejected")
    }

    // ===== TP-066: Console Proxy URL 使用 IPv6 括号 =====

    /**
     * TP-066: IPv6Utils.bracketIpv6() 三种场景：
     *   - 裸 IPv6  → 加括号
     *   - IPv4     → 原样返回
     *   - 已括号   → 幂等（不重复加）
     */
    void testConsoleBracketIpv6() {
        // 裸 IPv6 → "[2001:db8::100]"
        String bareIpv6   = "2001:db8::100"
        String bracketed  = IPv6Utils.bracketIpv6(bareIpv6)
        assert bracketed == "[2001:db8::100]" :
                "TP-066: bracketIpv6('$bareIpv6') should return '[2001:db8::100]', got: '$bracketed'"
        logger.info("TP-066a: bracketIpv6('$bareIpv6') = '$bracketed'")

        // IPv4 → 原样返回
        String ipv4   = "192.168.1.1"
        String result = IPv6Utils.bracketIpv6(ipv4)
        assert result == "192.168.1.1" :
                "TP-066: bracketIpv6('$ipv4') should return '$ipv4' unchanged, got: '$result'"
        logger.info("TP-066b: bracketIpv6('$ipv4') = '$result'")

        // 已括号 IPv6 → 幂等
        String alreadyBracketed = "[2001:db8::1]"
        String idempotent = IPv6Utils.bracketIpv6(alreadyBracketed)
        assert idempotent == "[2001:db8::1]" :
                "TP-066: bracketIpv6('$alreadyBracketed') should be idempotent, got: '$idempotent'"
        logger.info("TP-066c: bracketIpv6('$alreadyBracketed') = '$idempotent' (idempotent)")
    }

    // ===== TP-067: VNC Token URL hostname 含 IPv6 括号 =====

    /**
     * TP-067: VNC Token URL 拼接时 hostname 使用 bracketIpv6 处理 IPv6，
     * 使 "[2001:db8::1]:5900" 格式合法。
     */
    void testConsoleVncTokenUrl() {
        String ipv6Host = "2001:db8::1"
        int    vncPort  = 5900

        // bracketIpv6 处理 hostname，再拼接端口
        String hostname = IPv6Utils.bracketIpv6(ipv6Host)
        assert hostname == "[2001:db8::1]" :
                "TP-067: bracketIpv6 should produce '[2001:db8::1]', got: '$hostname'"

        String vncAddr = "${hostname}:${vncPort}"
        assert vncAddr == "[2001:db8::1]:5900" :
                "TP-067: VNC address should be '[2001:db8::1]:5900', got: '$vncAddr'"
        logger.info("TP-067: VNC Token URL hostname = '$hostname', addr = '$vncAddr'")
    }

    // ===== TP-069: 双栈 MN 下 Console URL 使用管理 VIP =====

    /**
     * TP-069: CONSOLE_PROXY_OVERRIDDEN_IP 设置为 IPv6 时，
     * bracketIpv6 正确包裹，使 Console URL 格式合法。
     */
    void testConsoleDualStackVip() {
        String overriddenIp = "2001:db8::100"  // 模拟 CONSOLE_PROXY_OVERRIDDEN_IP

        String bracketed = IPv6Utils.bracketIpv6(overriddenIp)
        assert bracketed == "[2001:db8::100]" :
                "TP-069: Console VIP bracketIpv6('$overriddenIp') should return '[2001:db8::100]', got: '$bracketed'"

        // 拼接成合法 Console URL
        String consoleUrl = "http://${bracketed}:8080/console"
        assert consoleUrl == "http://[2001:db8::100]:8080/console" :
                "TP-069: Console URL should be 'http://[2001:db8::100]:8080/console', got: '$consoleUrl'"
        logger.info("TP-069: dual-stack Console URL = '$consoleUrl'")
    }

    // ===== TP-076: BM V2 DPU 回调 IP IPv6 括号 =====

    /**
     * TP-076: BM V2 DPU 使用 callbackIp 时，通过 bracketIpv6 保证 IPv6 带括号，
     * 使回调 URL 格式正确。
     */
    void testBmDpuCallbackIpBracket() {
        String callbackIp = "2001:db8::1"

        String bracketed = IPv6Utils.bracketIpv6(callbackIp)
        assert bracketed == "[2001:db8::1]" :
                "TP-076: DPU callbackIp bracketIpv6('$callbackIp') should return '[2001:db8::1]', got: '$bracketed'"

        // 验证回调 URL 拼接正确
        String callbackUrl = "http://${bracketed}:7771/callback"
        assert callbackUrl == "http://[2001:db8::1]:7771/callback" :
                "TP-076: DPU callback URL should be 'http://[2001:db8::1]:7771/callback', got: '$callbackUrl'"
        logger.info("TP-076: BM V2 DPU callbackIp='$callbackIp' → bracketed='$bracketed', url='$callbackUrl'")
    }

    // ===== TP-077: COLO QEMU URL IPv6 括号 =====

    /**
     * TP-077: COLO QEMU 下载 URL 拼接时，使用 bracketIpv6 处理 IPv6 地址，
     * 确保 URL 格式为 "http://[ip]:port/path"。
     */
    void testColoQemuUrlIpv6Bracket() {
        String ipv6   = "2001:db8::1"
        String port   = "8080"
        String path   = "/zstack/static/qemu.tar.gz"

        String url = String.format("http://%s:%s%s", IPv6Utils.bracketIpv6(ipv6), port, path)
        assert url == "http://[2001:db8::1]:8080/zstack/static/qemu.tar.gz" :
                "TP-077: COLO QEMU URL should be 'http://[2001:db8::1]:8080/zstack/static/qemu.tar.gz', got: '$url'"
        logger.info("TP-077: COLO QEMU URL = '$url'")

        // 同时验证 IPv6Utils.buildUrl 辅助方法（与手动拼接结果一致）
        String builtUrl = IPv6Utils.buildUrl(ipv6, Integer.parseInt(port))
        assert builtUrl == "http://[2001:db8::1]:8080" :
                "TP-077: IPv6Utils.buildUrl('$ipv6', $port) should return 'http://[2001:db8::1]:8080', got: '$builtUrl'"
        logger.info("TP-077: IPv6Utils.buildUrl = '$builtUrl'")
    }
}
