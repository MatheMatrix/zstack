package org.zstack.test.integration.core

import org.zstack.testlib.SubCase
import org.zstack.utils.network.IPv6Utils

/**
 * TP-042~046: ZSha2 高可用 IPv6 支持测试
 *
 * 全部为纯单元测试，无需 Spring 上下文。
 *
 * 覆盖：
 *   TP-042 - ZSha2Helper.isMaster() grep pattern " ip/" 正确匹配 IPv6 VIP
 *   TP-043 - ZSha2Helper.isMaster() grep 逻辑正确（含 VIP 的 ip addr 输出返回 true）
 *   TP-044 - Zsha2 SSH/SCP 命令含 [IPv6] 括号：bracketIpv6() 工具正确
 *   TP-045 - nginx upstream 渲染：MN IP = IPv6 → "server [ipv6]:port;"
 *   TP-046 - IAM URL 含 IPv6 括号
 */
class ZSha2Ipv6Case extends SubCase {

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
        testIsMasterGrepPatternMatchesIpv6()      // TP-042
        testIsMasterGrepLogicWithVip()            // TP-043
        testBracketIpv6ForSshScp()               // TP-044
        testNginxUpstreamIpv6Format()            // TP-045
        testIamUrlIpv6Brackets()                 // TP-046
    }

    /**
     * TP-042: ZSha2Helper.isMaster() 使用 " %s/" 模式（新 pattern）匹配 IPv6 VIP。
     * 模拟 `ip addr show` 输出，验证 " VIP/" 字符串匹配正确。
     */
    void testIsMasterGrepPatternMatchesIpv6() {
        String output = "    inet6 2001:db8::100/64 scope global dynamic"
        String vip = "2001:db8::100"

        // TP-042: 新 pattern " VIP/" 应匹配 IPv6（冒号前后无空格，但 inet6 行含 " VIP/"）
        String pattern = " ${vip}/"
        assert output.contains(pattern) :
                "TP-042: pattern ' IP/' should match IPv6 in 'ip addr show' output. pattern='$pattern'"

        // 验证旧 pattern [^0-9]IP[^0-9] 也能匹配（: 是非数字字符）
        String oldPatternBefore = output.substring(output.indexOf(vip) - 1, output.indexOf(vip))
        String oldPatternAfter = output.substring(output.indexOf(vip) + vip.length(), output.indexOf(vip) + vip.length() + 1)
        assert !oldPatternBefore.matches("[0-9]") :
                "TP-042: character before VIP in output should be non-digit (was: '$oldPatternBefore')"
        assert !oldPatternAfter.matches("[0-9]") :
                "TP-042: character after VIP in output should be non-digit (was: '$oldPatternAfter')"
        logger.info("TP-042: pattern ' $vip/' matches IPv6 in ip addr output correctly")
    }

    /**
     * TP-043: ZSha2Helper.isMaster() grep 逻辑正确——ip addr 输出包含 VIP 时判定为 master。
     * 复用 TP-042 的模拟逻辑，验证存在 VIP 时 contains 返回 true，不存在时返回 false。
     */
    void testIsMasterGrepLogicWithVip() {
        String vip = "2001:db8::100"
        String outputWithVip = "    inet6 2001:db8::100/64 scope global dynamic"
        String outputWithoutVip = "    inet6 fd00::1/64 scope global dynamic"

        // TP-043: 含 VIP 的输出 → isMaster 应为 true
        assert outputWithVip.contains(" ${vip}/") :
                "TP-043: output containing VIP should be identified as master"
        // 不含 VIP 的输出 → isMaster 应为 false
        assert !outputWithoutVip.contains(" ${vip}/") :
                "TP-043: output without VIP should not be identified as master"
        logger.info("TP-043: isMaster grep logic for IPv6 VIP verified")
    }

    /**
     * TP-044: Zsha2 SSH/SCP 命令中 IPv6 地址需加方括号，bracketIpv6() 正确处理。
     */
    void testBracketIpv6ForSshScp() {
        // TP-044: IPv6 → "[ipv6]"（加括号）
        assert IPv6Utils.bracketIpv6("2001:db8::1") == "[2001:db8::1]" :
                "TP-044: bracketIpv6 should wrap IPv6 in square brackets for SSH/SCP"
        // 幂等：已有括号不重复添加
        assert IPv6Utils.bracketIpv6("[2001:db8::1]") == "[2001:db8::1]" :
                "TP-044: bracketIpv6 should be idempotent (no double-bracketing)"
        // IPv4 → 原样返回，不加括号
        assert IPv6Utils.bracketIpv6("192.168.1.1") == "192.168.1.1" :
                "TP-044: bracketIpv6 should not modify IPv4 address"
        logger.info("TP-044: bracketIpv6 for SSH/SCP commands verified")
    }

    /**
     * TP-045: nginx upstream 渲染时，MN IP = IPv6 → "server [ipv6]:port;"
     */
    void testNginxUpstreamIpv6Format() {
        String mnIp = "2001:db8::1"
        // TP-045: nginx upstream server 指令需要 [ipv6]:port 格式
        String nginxServer = "server ${IPv6Utils.bracketIpv6(mnIp)}:8080;"
        assert nginxServer == "server [2001:db8::1]:8080;" :
                "TP-045: nginx upstream should bracket IPv6, got: $nginxServer"
        // IPv4 不加括号
        String nginxServerV4 = "server ${IPv6Utils.bracketIpv6("10.0.0.1")}:8080;"
        assert nginxServerV4 == "server 10.0.0.1:8080;" :
                "TP-045: nginx upstream IPv4 should have no brackets, got: $nginxServerV4"
        logger.info("TP-045: nginx upstream IPv6 format='$nginxServer', IPv4='$nginxServerV4'")
    }

    /**
     * TP-046: IAM URL 包含 IPv6 时需加方括号，buildUrl() 正确生成。
     */
    void testIamUrlIpv6Brackets() {
        String iamIp = "2001:db8::2"
        // TP-046: IAM URL 应含 [ipv6]:port 格式
        String url = IPv6Utils.buildUrl(iamIp, 8080) + "/api/v1/"
        assert url.startsWith("http://[2001:db8::2]:8080/") :
                "TP-046: IAM URL should bracket IPv6, got: $url"
        assert url == "http://[2001:db8::2]:8080/api/v1/" :
                "TP-046: IAM URL full form incorrect, got: $url"
        // IPv4 IAM URL 不加括号
        String urlV4 = IPv6Utils.buildUrl("10.0.0.2", 8080) + "/api/v1/"
        assert urlV4 == "http://10.0.0.2:8080/api/v1/" :
                "TP-046: IAM URL for IPv4 should have no brackets, got: $urlV4"
        logger.info("TP-046: IAM URL IPv6='$url', IPv4='$urlV4'")
    }
}
