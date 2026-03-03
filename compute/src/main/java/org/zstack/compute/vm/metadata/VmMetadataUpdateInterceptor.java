package org.zstack.compute.vm.metadata;


import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.compute.vm.VmGlobalConfig;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.header.Component;
import org.zstack.header.message.*;
import org.zstack.header.vm.MetadataImpact;
import org.zstack.header.vm.VmUuidFromApiResolver;
import org.zstack.utils.BeanUtils;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 拦截标注了 {@link MetadataImpact} 的 API 消息，在 API 成功后调用 markDirty 触发元数据更新。
 *
 * <h3>工作流程</h3>
 * <pre>
 * API Message 投递                          Event 发布
 *       │                                      │
 *       ▼                                      ▼
 * BeforeDeliveryMessageInterceptor    BeforePublishEventInterceptor
 *       │                                      │
 *       │  检测 @MetadataImpact               │  通过 apiId 匹配
 *       │  通过 VmUuidFromApiResolver          │  检查 API 是否成功
 *       │  解析 vmUuid 列表                    │  调用 markDirty()
 *       │  缓存到 pendingApis                  │  清理 pendingApis
 *       │  (key = apiId)                       │
 *       ▼                                      ▼
 * </pre>
 *
 * <h3>VM UUID 解析链</h3>
 * <p>通过注入的 {@link VmUuidFromApiResolver} 列表按顺序解析 vmUuid：</p>
 * <ol>
 *   <li>{@link DefaultVmUuidFromApiResolver} — VmInstanceMessage 接口</li>
 *   <li>{@link VolumeBasedVmUuidFromApiResolver} — VolumeMessage → 查库</li>
 *   <li>{@link ResourceBasedVmUuidFromApiResolver} — Tag/Config API → resourceType 查库</li>
 *   <li>{@link ReflectionBasedVmUuidFromApiResolver} — 反射兜底</li>
 * </ol>
 *
 * <h3>标脏策略</h3>
 * <p>使用 {@link VmMetadataDirtyMarker#markDirty(String, boolean)} 将 VM 标记为脏，
 * INSERT ON DUPLICATE KEY UPDATE 天然去重，100 个 API 只产生 1 行 dirty 行。
 * markDirty 后立即尝试认领并刷写，Poller 作为安全网处理退避和异常场景。</p>
 */
public class VmMetadataUpdateInterceptor implements Component {
    private static final CLogger logger = Utils.getLogger(VmMetadataUpdateInterceptor.class);

    @Autowired
    private CloudBus bus;

    @Autowired
    private VmMetadataDirtyMarker dirtyMarker;

    /**
     * VM UUID 解析器链，按注册顺序尝试（在 VmInstanceManager.xml 中注册）。
     */
    @Autowired(required = false)
    private List<VmUuidFromApiResolver> resolvers = Collections.emptyList();

    // apiId -> MetadataImpactInfo 映射，在 API 投递时写入，在 Event 发布时消费
    private final Map<String, MetadataImpactInfo> pendingApis = new ConcurrentHashMap<>();

    // 标注了 @MetadataImpact 的 API 消息类集合
    private final Set<Class<? extends APIMessage>> impactApiClasses = ConcurrentHashMap.newKeySet();

    @Override
    public boolean start() {
        // 1. 扫描所有标注了 @MetadataImpact 的 API 消息类
        scanMetadataImpactApis();

        // 2. 注册 BeforeDeliveryMessageInterceptor，在 API 消息被投递处理前，
        //    通过 Resolver 链解析 vmInstanceUuid 并缓存
        bus.installBeforeDeliveryMessageInterceptor(new AbstractBeforeDeliveryMessageInterceptor() {
            @Override
            public void beforeDeliveryMessage(Message msg) {
                if (!(msg instanceof APIMessage)) {
                    return;
                }
                if (!impactApiClasses.contains(msg.getClass())) {
                    return;
                }

                // vm.metadata 功能总开关，关闭时跳过所有元数据更新
                if (!VmGlobalConfig.VM_METADATA.value(Boolean.class)) {
                    return;
                }

                APIMessage apiMsg = (APIMessage) msg;
                List<String> vmUuids = resolveVmUuids(apiMsg);
                if (vmUuids.isEmpty()) {
                    return;
                }

                MetadataImpact impact = msg.getClass().getAnnotation(MetadataImpact.class);
                pendingApis.put(apiMsg.getId(), new MetadataImpactInfo(
                        vmUuids, impact.value(), impact.updateOnFailure()));
            }
        }, impactApiClasses.toArray(new Class[0]));

        // 3. 注册 BeforePublishEventInterceptor，在 Event 发布前检查并标脏
        bus.installBeforePublishEventInterceptor(new AbstractBeforePublishEventInterceptor() {
            @Override
            public void beforePublishEvent(Event evt) {
                if (!(evt instanceof APIEvent)) {
                    return;
                }

                APIEvent apiEvent = (APIEvent) evt;
                MetadataImpactInfo info = pendingApis.remove(apiEvent.getApiId());
                if (info == null) {
                    return;
                }

                // API 失败则跳过（除非 @MetadataImpact(updateOnFailure=true)）
                if (apiEvent.getError() != null && !info.updateOnFailure) {
                    return;
                }

                for (String vmUuid : info.vmUuids) {
                    submitMarkDirty(vmUuid, info.impact);
                }
            }
        });

        return true;
    }

    private void scanMetadataImpactApis() {
        // 利用 ZStack 的反射工具扫描所有带 @MetadataImpact 注解的 APIMessage 子类
        BeanUtils.reflections.getTypesAnnotatedWith(MetadataImpact.class).forEach(clz -> {
            if (APIMessage.class.isAssignableFrom(clz)) {
                impactApiClasses.add((Class<? extends APIMessage>) clz);
                logger.debug(String.format("detected @MetadataImpact API: %s", clz.getName()));
            }
        });
    }

    /**
     * 通过 Resolver 链解析 API 消息关联的 vmInstanceUuid 列表。
     *
     * <p>按注册顺序遍历 resolvers，使用第一个 supports() 返回 true 的 Resolver 进行解析。
     * 如果所有 Resolver 都不支持或返回空列表，则返回空。</p>
     */
    private List<String> resolveVmUuids(APIMessage msg) {
        for (VmUuidFromApiResolver resolver : resolvers) {
            if (resolver.supports(msg)) {
                List<String> vmUuids = resolver.resolveVmUuids(msg);
                if (vmUuids != null && !vmUuids.isEmpty()) {
                    return vmUuids;
                }
            }
        }
        logger.debug(String.format("no resolver could extract vmUuids from %s", msg.getClass().getName()));
        return Collections.emptyList();
    }

    /**
     * 提交元数据标脏。
     *
     * <p>根据 {@link MetadataImpact.Impact} 决定 OP type：</p>
     * <ul>
     *   <li>{@code CONFIG} → storageStructureChange=false（OP type 1）</li>
     *   <li>{@code STORAGE} → storageStructureChange=true（OP type 2）</li>
     * </ul>
     *
     * @param vmInstanceUuid 目标虚拟机 UUID
     * @param impact         影响级别（来自 {@code @MetadataImpact} 注解）
     */
    void submitMarkDirty(String vmInstanceUuid, MetadataImpact.Impact impact) {
        boolean storageStructureChange = (impact == MetadataImpact.Impact.STORAGE);
        logger.debug(String.format("[MetadataDirty] API succeeded, marking dirty "
                + "for vm[uuid:%s], impact=%s, storageStructureChange=%s",
                vmInstanceUuid, impact, storageStructureChange));
        dirtyMarker.markDirty(vmInstanceUuid, storageStructureChange);
    }

    @Override
    public boolean stop() {
        pendingApis.clear();
        return true;
    }

    /**
     * 缓存 API 投递时提取的元数据影响信息，供 Event 发布时匹配使用。
     */
    private static class MetadataImpactInfo {
        final List<String> vmUuids;
        final MetadataImpact.Impact impact;
        final boolean updateOnFailure;

        MetadataImpactInfo(List<String> vmUuids, MetadataImpact.Impact impact, boolean updateOnFailure) {
            this.vmUuids = vmUuids;
            this.impact = impact;
            this.updateOnFailure = updateOnFailure;
        }
    }
}