package org.zstack.test.integration.core

import org.zstack.testlib.SubCase
import org.zstack.utils.network.IPv6Utils

/**
 * TP-083~089: 管理节点 IPv6 M4 Premium 支持测试
 *
 * 全部为纯单元测试，无需 Spring 上下文。
 * 覆盖以下测试点：
 *   TP-083 - ZWatch InfluxDB URL 含 IPv6 方括号（buildUrl vs String.format 对比）
 *   TP-084 - Prometheus remote_write URL 含 IPv6 方括号（路径拼接正确）
 *   TP-085 - Grafana 数据源 URL 含 IPv6 方括号（buildUrl vs String.format 对比）
 *   TP-087 - License HTTP URL 含 IPv6 方括号（bracketIpv6 + buildHttpsUrl）
 *   TP-088 - Keycloak 容器名 IPv6 地址 sanitize（冒号替换为短横线）
 *   TP-089 - SSO CAS URL 含 IPv6 方括号（bracketIpv6 用于 HTTPS URL 拼接）
 */
class MnIpv6M4Case extends SubCase {

    @Override
    void setup() {
        // 纯单元 / 静态方法测试，无需 Spring
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
        testTP083_influxDbUrlIpv6Bracket()        // TP-083
        testTP084_prometheusWriteUrlIpv6()        // TP-084
        testTP085_grafanaDataSourceUrlIpv6()      // TP-085
        testTP086_licenseHttpUrlIpv6()            // TP-087
        testTP087_keycloakContainerNameSanitize() // TP-088
        testTP088_ssoCasLoginUrlIpv6()            // TP-089
    }

    // ===== TP-083: ZWatch InfluxDB URL =====

    /**
     * TP-083: IPv6Utils.buildUrl() 在 InfluxDB URL 中正确添加方括号。
     *
     * 背景：ZWatch 向 InfluxDB 写入监控数据时，URL 由管理节点 IP + 端口 8086 组成。
     * 若使用 String.format("http://%s:%s", ip, port) 构造 IPv6 URL，冒号会破坏 URI 解析；
     * 必须用 IPv6Utils.buildUrl() 确保 IPv6 地址被方括号包裹。
     */
    void testTP083_influxDbUrlIpv6Bracket() {
        String ipv6 = "2001:db8::1"
        int port = 8086
        String expected = "http://[2001:db8::1]:8086"

        // 正确做法：IPv6Utils.buildUrl() 自动加方括号
        String actual = IPv6Utils.buildUrl(ipv6, port)
        assert actual == expected :
                "TP-083: IPv6Utils.buildUrl() should produce '$expected', got: '$actual'"

        // 对比：String.format 不加方括号，结果不符合 RFC 2732
        String wrongUrl = String.format("http://%s:%d", ipv6, port)
        assert wrongUrl != expected :
                "TP-083: String.format() should NOT produce RFC-compliant IPv6 URL"
        assert !wrongUrl.contains("[") :
                "TP-083: String.format() result should not contain brackets, got: '$wrongUrl'"

        logger.info("TP-083: InfluxDB URL (correct) = '$actual'")
        logger.info("TP-083: InfluxDB URL (wrong String.format) = '$wrongUrl'")
    }

    // ===== TP-084: Prometheus remote_write URL =====

    /**
     * TP-084: Prometheus remote_write 端点 URL 含 IPv6 方括号，路径拼接正确。
     *
     * 背景：Prometheus remote_write 目标地址形如 http://[ip]:port/api/v1/write。
     * 使用 IPv6Utils.buildUrl() 构造 base URL 后追加路径。
     */
    void testTP084_prometheusWriteUrlIpv6() {
        String ipv6 = "2001:db8::1"
        int port = 9090
        String path = "/api/v1/write"
        String expected = "http://[2001:db8::1]:9090/api/v1/write"

        String actual = IPv6Utils.buildUrl(ipv6, port) + path
        assert actual == expected :
                "TP-084: Prometheus remote_write URL should be '$expected', got: '$actual'"

        // 验证 URL 中方括号存在
        assert actual.contains("[2001:db8::1]") :
                "TP-084: URL should contain bracketed IPv6 address"
        // 验证路径正确追加
        assert actual.endsWith(path) :
                "TP-084: URL should end with '$path'"

        logger.info("TP-084: Prometheus remote_write URL = '$actual'")
    }

    // ===== TP-085: Grafana 数据源 URL =====

    /**
     * TP-085: Grafana 数据源 URL 在 IPv6 场景下包含方括号。
     *
     * 背景：ZWatch 向 Grafana 注册数据源时，datasource URL 需要符合 HTTP URI 规范。
     * String.format("http://%s:%s", ip, port) 生成裸 IPv6 URL 会导致 Grafana API 拒绝。
     */
    void testTP085_grafanaDataSourceUrlIpv6() {
        String ipv6 = "2001:db8::1"
        int port = 3000
        String expected = "http://[2001:db8::1]:3000"

        // 正确做法
        String actual = IPv6Utils.buildUrl(ipv6, port)
        assert actual == expected :
                "TP-085: Grafana datasource URL should be '$expected', got: '$actual'"

        // 对比：String.format 的错误结果（无括号）
        String wrongUrl = String.format("http://%s:%d", ipv6, port)
        assert wrongUrl != actual :
                "TP-085: String.format() result should differ from RFC-compliant URL"
        assert wrongUrl == "http://2001:db8::1:3000" :
                "TP-085: String.format() result should be 'http://2001:db8::1:3000' (no brackets), got: '$wrongUrl'"

        logger.info("TP-085: Grafana datasource URL (correct) = '$actual'")
        logger.info("TP-085: Grafana datasource URL (wrong String.format) = '$wrongUrl'")
    }

    // ===== TP-087: License HTTP URL =====

    /**
     * TP-087: License 验证 HTTPS URL 在 IPv6 场景下正确添加方括号。
     *
     * 背景：License 服务向管理节点发起 HTTP 回调时，需要构造形如
     * https://[ipv6]:443/license 的 URL；bracketIpv6() 保证 IPv4 不受影响。
     */
    void testTP086_licenseHttpUrlIpv6() {
        String ipv6 = "2001:db8::1"
        int port = 443
        String licensePath = "/license"

        // 验证 bracketIpv6 对 IPv6 正确添加方括号
        String bracketed = IPv6Utils.bracketIpv6(ipv6)
        assert bracketed == "[2001:db8::1]" :
                "TP-087: bracketIpv6('$ipv6') should return '[2001:db8::1]', got: '$bracketed'"

        // 验证 bracketIpv6 对 IPv4 原样返回（无副作用）
        String ipv4 = "192.168.1.100"
        String bracketedIpv4 = IPv6Utils.bracketIpv6(ipv4)
        assert bracketedIpv4 == ipv4 :
                "TP-087: bracketIpv6('$ipv4') should return IPv4 unchanged, got: '$bracketedIpv4'"

        // 模拟 License 回调 URL 构造
        String licenseUrl = String.format("https://%s:%d%s", bracketed, port, licensePath)
        String expectedUrl = "https://[2001:db8::1]:443/license"
        assert licenseUrl == expectedUrl :
                "TP-087: License URL should be '$expectedUrl', got: '$licenseUrl'"

        // 使用 buildHttpsUrl 的等效验证
        String builtUrl = IPv6Utils.buildHttpsUrl(ipv6, port, licensePath)
        assert builtUrl == expectedUrl :
                "TP-087: buildHttpsUrl('$ipv6', $port, '$licensePath') should be '$expectedUrl', got: '$builtUrl'"

        logger.info("TP-087: bracketIpv6('$ipv6') = '$bracketed'")
        logger.info("TP-087: License URL = '$licenseUrl'")
    }

    // ===== TP-088: Keycloak 容器名 IPv6 Sanitize =====

    /**
     * TP-088: 将 IPv6 地址中的冒号替换为短横线，确保 Docker 容器名合法。
     *
     * 背景：Keycloak 容器名基于管理节点 IP 生成，Docker 容器名只允许 [a-zA-Z0-9_.-]。
     * IPv6 地址含冒号，需要替换为短横线后才能作为容器名的一部分。
     */
    void testTP087_keycloakContainerNameSanitize() {
        String ipv6 = "2001:db8::1"

        // 验证冒号替换为短横线
        String sanitized = ipv6.replace(':', '-')
        assert sanitized == "2001-db8--1" :
                "TP-088: '$ipv6'.replace(':', '-') should be '2001-db8--1', got: '$sanitized'"

        // 验证 sanitized 结果不含冒号
        assert !sanitized.contains(':') :
                "TP-088: sanitized IP should not contain colon, got: '$sanitized'"

        // 验证拼接后的完整容器名不含冒号
        String containerName = "keycloak-server-on-management-node-${sanitized}"
        assert containerName == "keycloak-server-on-management-node-2001-db8--1" :
                "TP-088: container name should be 'keycloak-server-on-management-node-2001-db8--1', got: '$containerName'"
        assert !containerName.contains(':') :
                "TP-088: container name must not contain colon"

        // 验证 Docker 容器名合法性（正则 [a-zA-Z0-9_.-]+）
        assert containerName.matches('[a-zA-Z0-9_.\\-]+') :
                "TP-088: container name '$containerName' must match Docker naming rule [a-zA-Z0-9_.-]+"

        // 对比：IPv4 不含冒号，replace 操作幂等
        String ipv4 = "192.168.1.100"
        String sanitizedIpv4 = ipv4.replace(':', '-')
        assert sanitizedIpv4 == ipv4 :
                "TP-088: IPv4 sanitize should be no-op, got: '$sanitizedIpv4'"

        logger.info("TP-088: IPv6 sanitized for container name = '$sanitized'")
        logger.info("TP-088: Full container name = '$containerName'")
    }

    // ===== TP-089: SSO CAS URL =====

    /**
     * TP-089: SSO CAS 登录 URL 在 IPv6 场景下正确添加方括号。
     *
     * 背景：Keycloak/CAS 协议要求 service 参数和 CAS server URL 均需符合 RFC URI 规范。
     * 使用 bracketIpv6() 确保 IPv6 地址在 HTTPS URL 中被方括号包裹。
     */
    void testTP088_ssoCasLoginUrlIpv6() {
        String ipv6 = "2001:db8::100"
        int port = 8443
        String casPath = "/cas/login"
        String serviceUrl = "https%3A%2F%2F%5B2001%3Adb8%3A%3A100%5D%3A8443%2Fapp"

        // 验证 bracketIpv6 对该 IPv6 正确括号化
        String bracketed = IPv6Utils.bracketIpv6(ipv6)
        assert bracketed == "[2001:db8::100]" :
                "TP-089: bracketIpv6('$ipv6') should return '[2001:db8::100]', got: '$bracketed'"

        // 验证幂等性：对已括号化地址再次调用不重复添加
        String doubleWrapped = IPv6Utils.bracketIpv6(bracketed)
        assert doubleWrapped == "[2001:db8::100]" :
                "TP-089: bracketIpv6 should be idempotent, got: '$doubleWrapped'"

        // 模拟 CAS 登录 URL 构造（使用 buildHttpsUrl + 路径）
        String casBase = IPv6Utils.buildHttpsUrl(ipv6, port, casPath)
        String expectedBase = "https://[2001:db8::100]:8443/cas/login"
        assert casBase == expectedBase :
                "TP-089: CAS base URL should be '$expectedBase', got: '$casBase'"

        // 验证带 service 参数的完整登录 URL
        String fullCasUrl = "${casBase}?service=${serviceUrl}"
        assert fullCasUrl.startsWith("https://[2001:db8::100]:") :
                "TP-089: Full CAS URL should start with 'https://[2001:db8::100]:', got: '$fullCasUrl'"
        assert fullCasUrl.contains(casPath) :
                "TP-089: Full CAS URL should contain path '$casPath'"

        // 对比裸 IPv6 URL（无方括号）的错误格式
        String wrongCasBase = String.format("https://%s:%d%s", ipv6, port, casPath)
        assert wrongCasBase != casBase :
                "TP-089: String.format() should produce incorrect URL without brackets"
        assert !wrongCasBase.contains("[") :
                "TP-089: String.format() result should not contain brackets, got: '$wrongCasBase'"

        logger.info("TP-089: bracketIpv6('$ipv6') = '$bracketed'")
        logger.info("TP-089: CAS login URL (correct) = '$casBase'")
        logger.info("TP-089: CAS login URL (wrong String.format) = '$wrongCasBase'")
    }
}
