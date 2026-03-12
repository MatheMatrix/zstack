package org.zstack.compute.vm.metadata;

import org.zstack.core.db.SQL;
import org.zstack.header.message.APIMessage;
import org.zstack.header.vm.VmUuidFromApiResolver;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

/**
 * NIC 关联 VM UUID 解析器：从携带 vmNicUuid 的 API 消息中查询 VmNicVO 得到 vmInstanceUuid。
 *
 * <p>覆盖的 API：</p>
 * <ul>
 *   <li>{@code APIChangeVmNicNetworkMsg} — vmNicUuid 字段，实现了 VmInstanceMessage 但 vmInstanceUuid 为 @APINoSee</li>
 *   <li>{@code APIChangeVmNicStateMsg} — vmNicUuid 字段，同上</li>
 *   <li>{@code APIDeleteVmNicMsg} — uuid 字段（即 nicUuid），继承 APIDeleteMessage，不实现 VmInstanceMessage</li>
 * </ul>
 *
 * <h3>解析链</h3>
 * <pre>
 *   vmNicUuid → VmNicVO.vmInstanceUuid
 * </pre>
 *
 * <h3>设计说明</h3>
 * <p>由于不存在统一的 VmNicMessage 接口，本解析器通过反射检测消息上的 {@code getVmNicUuid()} 方法获取 nicUuid。
 * 对于 {@code APIDeleteVmNicMsg}，其 uuid 字段即为 nicUuid，通过 {@code getUuid()} 获取。</p>
 *
 * <p>本解析器不处理 BondingMessage 类消息（{@code APIAttachNicToBondingMsg}、{@code APIDetachNicFromBondingMsg}），
 * 因为 bonding → VM 的解析链涉及 PCI 设备关联，复杂度过高且为边缘场景。
 * 这些消息由 {@link ReflectionBasedVmUuidFromApiResolver} 兜底处理。</p>
 *
 * <h3>解析时机</h3>
 * <p>在 API 执行前（BeforeDeliveryMessageInterceptor）调用。此时 VmNicVO 仍在数据库中，
 * 即使是 delete 场景也能查到关联的 vmInstanceUuid。</p>
 */
public class NicBasedVmUuidFromApiResolver implements VmUuidFromApiResolver {
    private static final CLogger logger = Utils.getLogger(NicBasedVmUuidFromApiResolver.class);

    @Override
    public boolean supports(APIMessage msg) {
        // 检测消息是否携带 vmNicUuid（通过反射，因无统一接口）
        return getVmNicUuid(msg) != null;
    }

    @Override
    public List<String> resolveVmUuids(APIMessage msg) {
        String nicUuid = getVmNicUuid(msg);
        if (nicUuid == null) {
            return Collections.emptyList();
        }

        return SQL.New(
                "SELECT n.vmInstanceUuid FROM VmNicVO n " +
                        "WHERE n.uuid = :nicUuid AND n.vmInstanceUuid IS NOT NULL",
                String.class
        ).param("nicUuid", nicUuid).list();
    }

    /**
     * 尝试从消息中提取 vmNicUuid。
     *
     * <p>按优先级尝试：</p>
     * <ol>
     *   <li>getVmNicUuid() — APIChangeVmNicNetworkMsg, APIChangeVmNicStateMsg 等</li>
     *   <li>getUuid() 且 resourceType 为 VmNicVO — APIDeleteVmNicMsg</li>
     * </ol>
     */
    private String getVmNicUuid(APIMessage msg) {
        // 1. 尝试 getVmNicUuid()
        try {
            Method method = msg.getClass().getMethod("getVmNicUuid");
            Object result = method.invoke(msg);
            if (result instanceof String) {
                return (String) result;
            }
        } catch (NoSuchMethodException ignored) {
            // 无此方法，继续尝试
        } catch (Exception e) {
            logger.trace(String.format("failed to invoke getVmNicUuid() on %s: %s",
                    msg.getClass().getSimpleName(), e.getMessage()));
        }

        // 2. 尝试 getUuid()：针对 APIDeleteVmNicMsg（@APIParam(resourceType = VmNicVO.class)）
        //    通过检查 APIParam 注解的 resourceType 确认 uuid 确实是 VmNicVO 的主键
        try {
            Method method = msg.getClass().getMethod("getUuid");
            // 检查 uuid 字段上的 @APIParam(resourceType = VmNicVO.class) 注解
            java.lang.reflect.Field field = findField(msg.getClass(), "uuid");
            if (field != null) {
                org.zstack.header.message.APIParam apiParam = field.getAnnotation(
                        org.zstack.header.message.APIParam.class);
                if (apiParam != null && "VmNicVO".equals(apiParam.resourceType().getSimpleName())) {
                    Object result = method.invoke(msg);
                    if (result instanceof String) {
                        return (String) result;
                    }
                }
            }
        } catch (NoSuchMethodException ignored) {
            // 无此方法
        } catch (Exception e) {
            logger.trace(String.format("failed to check uuid field on %s: %s",
                    msg.getClass().getSimpleName(), e.getMessage()));
        }

        return null;
    }

    /**
     * 在类层次结构中查找声明的字段（包括父类）。
     */
    private java.lang.reflect.Field findField(Class<?> clazz, String fieldName) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
