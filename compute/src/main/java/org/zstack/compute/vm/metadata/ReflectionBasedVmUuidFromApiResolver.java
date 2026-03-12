package org.zstack.compute.vm.metadata;

import org.zstack.header.message.APIMessage;
import org.zstack.header.vm.VmUuidFromApiResolver;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

/**
 * 反射兜底解析器：通过反射调用 API 消息的 getVmInstanceUuid() / getResourceUuid() 方法。
 *
 * <p>此解析器作为所有其他 Resolver 的兜底，处理未被显式 Resolver 覆盖但仍然携带
 * vmInstanceUuid 或 resourceUuid 的 API 消息。</p>
 *
 * <p>注册顺序必须排在所有显式 Resolver 之后（在 XML 中排最后）。</p>
 */
public class ReflectionBasedVmUuidFromApiResolver implements VmUuidFromApiResolver {
    private static final CLogger logger = Utils.getLogger(ReflectionBasedVmUuidFromApiResolver.class);

    @Override
    public boolean supports(APIMessage msg) {
        // 兜底：对所有消息返回 true，但 resolveVmUuids 可能返回空
        return true;
    }

    @Override
    public List<String> resolveVmUuids(APIMessage msg) {
        // 优先尝试 getVmInstanceUuid()
        String vmUuid = invokeGetter(msg, "getVmInstanceUuid");
        if (vmUuid != null) {
            return Collections.singletonList(vmUuid);
        }

        // fallback: getResourceUuid()
        String resourceUuid = invokeGetter(msg, "getResourceUuid");
        if (resourceUuid != null) {
            return Collections.singletonList(resourceUuid);
        }

        logger.debug(String.format("cannot extract vmInstanceUuid from %s via reflection", msg.getClass().getName()));
        return Collections.emptyList();
    }

    private String invokeGetter(Object obj, String methodName) {
        try {
            Method method = obj.getClass().getMethod(methodName);
            return (String) method.invoke(obj);
        } catch (NoSuchMethodException e) {
            return null;
        } catch (Exception e) {
            logger.warn(String.format("failed to invoke %s on %s: %s",
                    methodName, obj.getClass().getName(), e.getMessage()));
            return null;
        }
    }
}
