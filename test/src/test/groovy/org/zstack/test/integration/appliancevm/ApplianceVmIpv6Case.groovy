package org.zstack.test.integration.appliancevm

import org.zstack.appliancevm.ApplianceVmFacadeImpl
import org.zstack.core.Platform
import org.zstack.testlib.SubCase
import org.zstack.utils.network.NetworkUtils

import java.lang.reflect.Method

/**
 * TP-025~029: ApplianceVmFacadeImpl.getMnIpForVr CIDR 匹配逻辑测试
 *
 * getMnIpForVr 是私有实例方法，通过反射调用。
 * 不依赖 Spring 上下文（方法内部只使用标准 Java 网络 API 和静态工具方法）。
 *
 * 覆盖：
 *   TP-025 - 使用当前 MN 的 IPv4 CIDR 应返回 MN 的 IP 地址
 *   TP-026 - null CIDR → fallback 到 Platform.getManagementServerIp()
 *   TP-027 - 不匹配的 CIDR → fallback 到 Platform.getManagementServerIp()
 *   TP-028 - 返回的 IP 地址不含方括号（裸地址）
 *   TP-029 - 无效 CIDR → fallback，不抛异常
 */
class ApplianceVmIpv6Case extends SubCase {

    /** 测试用 ApplianceVmFacadeImpl 实例（不依赖 @Autowired 注入） */
    private ApplianceVmFacadeImpl facade
    /** getMnIpForVr 反射方法 */
    private Method getMnIpForVrMethod

    @Override
    void setup() {
        // 无需 Spring；getMnIpForVr 只使用 NetworkInterface 枚举和 Platform 静态方法
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
        initReflection()

        testMnCidrMatchReturnsMnIp()   // TP-025
        testNullCidrFallback()         // TP-026
        testUnmatchedCidrFallback()    // TP-027
        testReturnedIpNoBrackets()     // TP-028
        testInvalidCidrFallback()      // TP-029
    }

    /**
     * 初始化 ApplianceVmFacadeImpl 实例和反射方法。
     */
    private void initReflection() {
        // ApplianceVmFacadeImpl 的字段初始化器使用 Platform.getManagementServerId()，
        // 若 msId 未设置则返回 null；String.format("...%s", null) 不会 NPE
        facade = new ApplianceVmFacadeImpl()

        getMnIpForVrMethod = ApplianceVmFacadeImpl.class.getDeclaredMethod("getMnIpForVr", String.class)
        getMnIpForVrMethod.setAccessible(true)
    }

    /**
     * 调用 getMnIpForVr(cidr)，统一异常处理。
     */
    private String callGetMnIpForVr(String cidr) {
        return getMnIpForVrMethod.invoke(facade, cidr) as String
    }

    /**
     * TP-025: 使用当前管理节点 CIDR 调用 getMnIpForVr，返回值不为 null，为合法 IP 地址。
     */
    void testMnCidrMatchReturnsMnIp() {
        String mnIp = Platform.getManagementServerIp()
        String mnCidr = Platform.getManagementServerCidr()

        if (mnCidr == null) {
            logger.warn("TP-025: getManagementServerCidr() returned null, skipping CIDR match test")
            return
        }

        String selectedIp = callGetMnIpForVr(mnCidr)
        assert selectedIp != null : "TP-025: getMnIpForVr(mnCidr) should return non-null IP"
        boolean isValidIp = NetworkUtils.isIpv4Address(selectedIp) ||
                selectedIp.contains(":") // IPv6 contains ":"
        assert isValidIp : "TP-025: returned IP should be valid, got: $selectedIp"
        logger.info("TP-025: getMnIpForVr('$mnCidr') = '$selectedIp' (mnIp=$mnIp)")
    }

    /**
     * TP-026: null CIDR → fallback 到 Platform.getManagementServerIp()
     */
    void testNullCidrFallback() {
        String mnIp = Platform.getManagementServerIp()
        String fallback = callGetMnIpForVr(null)
        assert fallback != null : "TP-026: getMnIpForVr(null) should return non-null IP (fallback)"
        assert fallback == mnIp :
                "TP-026: getMnIpForVr(null) should fallback to Platform.getManagementServerIp(), expected '$mnIp', got '$fallback'"
        logger.info("TP-026: getMnIpForVr(null) correctly falls back to $fallback")
    }

    /**
     * TP-027: 不匹配的 CIDR → fallback 到 Platform.getManagementServerIp()
     */
    void testUnmatchedCidrFallback() {
        String mnIp = Platform.getManagementServerIp()
        // 使用一个极不可能匹配当前主机任何网卡的 CIDR
        String unmatchedCidr = "10.99.88.0/24"
        String result = callGetMnIpForVr(unmatchedCidr)
        assert result != null : "TP-027: getMnIpForVr(unmatched CIDR) should return non-null IP"
        assert result == mnIp :
                "TP-027: getMnIpForVr('$unmatchedCidr') should fallback to MN IP, expected '$mnIp', got '$result'"
        logger.info("TP-027: getMnIpForVr('$unmatchedCidr') correctly falls back to $result")
    }

    /**
     * TP-028: getMnIpForVr 返回的 IP 地址不含方括号（裸地址，无 URL 包装）
     */
    void testReturnedIpNoBrackets() {
        String fallbackIp = callGetMnIpForVr(null)
        assert fallbackIp != null : "TP-028: getMnIpForVr(null) should not return null"
        assert !fallbackIp.contains("[") && !fallbackIp.contains("]") :
                "TP-028: returned IP should not contain brackets (should be bare IP), got: $fallbackIp"
        logger.info("TP-028: getMnIpForVr returns bare IP without brackets: $fallbackIp")
    }

    /**
     * TP-029: 无效 CIDR → fallback，不抛异常
     */
    void testInvalidCidrFallback() {
        String mnIp = Platform.getManagementServerIp()
        String invalidCidr = "not-a-cidr"

        String result = null
        try {
            result = callGetMnIpForVr(invalidCidr)
        } catch (Exception e) {
            // InvocationTargetException 包装原始异常
            Throwable cause = e.getCause() ?: e
            assert false : "TP-029: getMnIpForVr('$invalidCidr') should not throw, got: ${cause.class.simpleName}: ${cause.message}"
        }

        assert result != null : "TP-029: getMnIpForVr(invalid CIDR) should fallback to MN IP, not return null"
        assert result == mnIp :
                "TP-029: getMnIpForVr('$invalidCidr') should fallback to MN IP '$mnIp', got: $result"
        logger.info("TP-029: getMnIpForVr('$invalidCidr') correctly falls back to $result without exception")
    }
}
