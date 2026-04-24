package org.zstack.utils.network;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * IPv6-aware URL / address utility methods.
 * <p>
 * Rules for embedding IP addresses in HTTP URLs (RFC 2732 / RFC 6874):
 *   - IPv6 addresses MUST be enclosed in square brackets, e.g. http://[::1]:8080
 *   - IPv4 addresses and hostnames are used as-is
 */
public class IPv6Utils {

    /**
     * 构造 HTTP URL。自动为 IPv6 地址加方括号。
     * <pre>
     * buildUrl("192.168.1.1", 8080)  → "http://192.168.1.1:8080"
     * buildUrl("2001:db8::1", 8080)  → "http://[2001:db8::1]:8080"
     * buildUrl("host.example", 8080) → "http://host.example:8080"
     * </pre>
     */
    public static String buildUrl(String ip, int port) {
        return "http://" + bracketIpv6(ip) + ":" + port;
    }

    /**
     * 构造 HTTPS URL，带路径。
     * <pre>
     * buildHttpsUrl("2001:db8::1", 443, "/api") → "https://[2001:db8::1]:443/api"
     * buildHttpsUrl("2001:db8::1", 443, "")     → "https://[2001:db8::1]:443"
     * </pre>
     */
    public static String buildHttpsUrl(String ip, int port, String path) {
        String base = "https://" + bracketIpv6(ip) + ":" + port;
        if (path == null || path.isEmpty()) {
            return base;
        }
        return base + path;
    }

    /**
     * 为 IPv6 地址加方括号（用于嵌入 URL host 部分）。
     * IPv4 / 域名原样返回。已有括号则不重复添加（幂等）。
     * <pre>
     * bracketIpv6("2001:db8::1")    → "[2001:db8::1]"
     * bracketIpv6("192.168.1.1")   → "192.168.1.1"
     * bracketIpv6("[2001:db8::1]") → "[2001:db8::1]"
     * </pre>
     */
    public static String bracketIpv6(String ip) {
        if (ip == null) {
            return ip;
        }
        // 已经有方括号，直接返回（幂等）
        if (ip.startsWith("[")) {
            return ip;
        }
        if (IPv6NetworkUtils.isIpv6Address(ip)) {
            return "[" + ip + "]";
        }
        return ip;
    }

    /**
     * 规范化 IPv6 地址为 RFC 5952 压缩格式（via InetAddress）。
     * IPv4 原样返回。去除 zone ID（%eth0）后再规范化。
     * <pre>
     * normalizeIpv6("2001:0db8:0000::0001") → "2001:db8::1"
     * normalizeIpv6("192.168.1.1")          → "192.168.1.1"
     * </pre>
     *
     * @throws IllegalArgumentException 当传入无效 IPv6 格式时
     */
    public static String normalizeIpv6(String ip) {
        if (ip == null || ip.isEmpty()) {
            throw new IllegalArgumentException("invalid IPv6 address: " + ip);
        }
        // IPv4 原样返回
        if (!ip.contains(":")) {
            return ip;
        }
        // 去除 zone ID（e.g. fe80::1%eth0）
        String stripped = ip.contains("%") ? ip.split("%")[0] : ip;
        try {
            InetAddress addr = InetAddress.getByName(stripped);
            return addr.getHostAddress();
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("invalid IPv6 address: " + ip, e);
        }
    }

    /**
     * 校验 IP 是否可用作管理 IP（IPv4 或 IPv6，拒绝链路本地和 loopback）。
     * <pre>
     * 合法 IPv4               → true
     * 合法 IPv6（非 fe80::，非 ::1）→ true
     * fe80:: / ::1 / 非法格式 / null / empty → false
     * </pre>
     */
    public static boolean isValidManagementIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        try {
            InetAddress addr = InetAddress.getByName(ip);
            return !addr.isLoopbackAddress() && !addr.isLinkLocalAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
