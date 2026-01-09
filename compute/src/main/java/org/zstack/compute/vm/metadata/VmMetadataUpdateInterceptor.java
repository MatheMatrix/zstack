package org.zstack.compute.vm.metadata;


import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.compute.vm.VmGlobalConfig;
import org.zstack.core.Platform;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.cloudbus.CloudBusCallBack;
import org.zstack.core.gc.GCConstants;
import org.zstack.core.gc.SubmitTimeBasedGarbageCollectorMsg;
import org.zstack.header.Component;
import org.zstack.header.message.*;
import org.zstack.utils.BeanUtils;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 拦截标注了 {@link MetadataImpact} 的 API 消息，在 API 成功后提交元数据更新 GC 任务。
 *
 * <h3>工作流程</h3>
 * <pre>
 * API Message 投递                          Event 发布
 *       │                                      │
 *       ▼                                      ▼
 * BeforeDeliveryMessageInterceptor    BeforePublishEventInterceptor
 *       │                                      │
 *       │  检测 @MetadataImpact               │  通过 apiId 匹配
 *       │  提取 vmInstanceUuid                 │  检查 API 是否成功
 *       │  缓存到 pendingApis                  │  提交 GC 任务
 *       │  (key = apiId)                       │  清理 pendingApis
 *       ▼                                      ▼
 * </pre>
 *
 * <h3>GC 提交策略</h3>
 * <p>使用 {@code submit()}（非 {@code deduplicateSubmit}），每次 API 成功都创建新 GC 行。
 * 通过 ChainTask {@code maxPendingTasks=1} 控制同一 VM 的执行扩散。
 * 详见 {@link UpdateVmInstanceMetadataGC} Javadoc 中的设计说明。</p>
 */
public class VmMetadataUpdateInterceptor implements Component {
    private static final CLogger logger = Utils.getLogger(VmMetadataUpdateInterceptor.class);

    /**
     * 获取初始 GC 延迟（秒），从 GlobalConfig 读取。
     * <p>GC timer 到期后触发第一次元数据写入尝试。
     * 失败后由 UpdateVmInstanceMetadataGC 内部的指数退避接管。</p>
     */
    private static long getInitialGcDelaySec() {
        return VmGlobalConfig.VM_METADATA_GC_INITIAL_DELAY_SEC.value(Long.class);
    }

    @Autowired
    private CloudBus bus;

    // apiId -> MetadataImpactInfo 映射，在 API 投递时写入，在 Event 发布时消费
    private final Map<String, MetadataImpactInfo> pendingApis = new ConcurrentHashMap<>();

    // 标注了 @MetadataImpact 的 API 消息类集合
    private final Set<Class<? extends APIMessage>> impactApiClasses = ConcurrentHashMap.newKeySet();

    @Override
    public boolean start() {
        // 1. 扫描所有标注了 @MetadataImpact 的 API 消息类
        scanMetadataImpactApis();

        // 2. 注册 BeforeDeliveryMessageInterceptor，在 API 消息被投递处理前，
        //    提取 vmInstanceUuid 并缓存
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
                String vmUuid = extractVmInstanceUuid(apiMsg);
                if (vmUuid == null) {
                    return;
                }

                MetadataImpact impact = msg.getClass().getAnnotation(MetadataImpact.class);
                pendingApis.put(apiMsg.getId(), new MetadataImpactInfo(vmUuid, impact.value()));
            }
        }, impactApiClasses.toArray(new Class[0]));

        // 3. 注册 BeforePublishEventInterceptor，在 Event 发布前检查并提交 GC
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
                if (apiEvent.getError() != null) {
                    return;
                }

                submitUpdateVmInstanceMetadataGC(info.vmInstanceUuid);
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
     * 从 API 消息中提取 vmInstanceUuid。
     * <p>约定：标注 @MetadataImpact 的 API 消息必须有 getVmInstanceUuid() 方法，
     * fallback 到 getResourceUuid()。</p>
     */
    private String extractVmInstanceUuid(APIMessage msg) {
        try {
            Method method = msg.getClass().getMethod("getVmInstanceUuid");
            return (String) method.invoke(msg);
        } catch (NoSuchMethodException e) {
            // 尝试 getResourceUuid 作为 fallback
            try {
                Method method = msg.getClass().getMethod("getResourceUuid");
                return (String) method.invoke(msg);
            } catch (Exception ex) {
                logger.warn(String.format("cannot extract vmInstanceUuid from %s", msg.getClass().getName()));
                return null;
            }
        } catch (Exception e) {
            logger.warn(String.format("failed to extract vmInstanceUuid from %s: %s",
                    msg.getClass().getName(), e.getMessage()));
            return null;
        }
    }

    /**
     * 通过 SubmitTimeBasedGarbageCollectorMsg 将 GC 提交到 hash 环拥有 vmInstanceUuid 的 MN。
     *
     * <h3>方案 B：远程提交 + reply 回退</h3>
     * <p>正常情况下，GC 通过 hash 环路由到 owner MN 完成提交，保证同一 VM 的 GC 归集到一个 MN。</p>
     * <p>如果 owner MN 不可达（宕机/网络故障），reply 超时后回退到本地 submit，确保 GC 不丢失。</p>
     *
     * <p>使用 submit()（非 deduplicateSubmit），每次创建新 GC 行。
     * GarbageCollectorManagerImpl.handle(SubmitTimeBasedGarbageCollectorMsg) 调用 gc.submit()
     * 完成持久化 + timer 注册。</p>
     *
     * <p>同一 VM 的并发控制由 UpdateVmInstanceMetadataGC.triggerNow() 中的
     * ChainTask maxPendingTasks=1 保证，超出的 GC 立即标记 Done。</p>
     *
     * <p>极小概率场景（MN 加入时 hash 环瞬时不一致）可能导致两个 MN 各有 GC，
     * 由 triggerNow() 中的 owner 检查 + delegate 机制（方案 C）兜底归集。</p>
     *
     * @param vmInstanceUuid 目标虚拟机 UUID
     */
    /**
     * 提交元数据更新 GC。包内可访问，供 MetadataCascadeExtension 等内部调用方使用。
     */
    void submitUpdateVmInstanceMetadataGC(String vmInstanceUuid) {
        logger.debug(String.format("[MetadataGC] API succeeded, submitting metadata update GC "
                + "for vm[uuid:%s]", vmInstanceUuid));

        UpdateVmInstanceMetadataGC gc = new UpdateVmInstanceMetadataGC();
        gc.vmInstanceUuid = vmInstanceUuid;
        gc.NAME = UpdateVmInstanceMetadataGC.getGCName(vmInstanceUuid);

        SubmitTimeBasedGarbageCollectorMsg gcmsg = new SubmitTimeBasedGarbageCollectorMsg();
        gcmsg.setGc(gc);
        long delaySec = getInitialGcDelaySec();
        gcmsg.setGcInterval(delaySec);
        gcmsg.setUnit(TimeUnit.SECONDS);

        // 路由到 hash 环拥有 vmInstanceUuid 的 MN
        bus.makeTargetServiceIdByResourceUuid(gcmsg, GCConstants.SERVICE_ID, vmInstanceUuid);
        bus.send(gcmsg, new CloudBusCallBack(null) {
            @Override
            public void run(MessageReply reply) {
                if (!reply.isSuccess()) {
                    // owner MN 不可达（宕机/网络故障）→ 回退到本地 submit，确保 GC 不丢失。
                    // 本地 GC 在 triggerNow() 时如果发现自己不是 owner，会通过 delegate 机制转移。
                    logger.warn(String.format("[MetadataGC] failed to submit GC to owner MN "
                            + "for vm[uuid:%s], fallback to local submit: %s",
                            vmInstanceUuid, reply.getError()));
                    gc.submit(delaySec, TimeUnit.SECONDS);
                }
            }
        });
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
        final String vmInstanceUuid;
        final MetadataImpactLevel level;

        MetadataImpactInfo(String vmInstanceUuid, MetadataImpactLevel level) {
            this.vmInstanceUuid = vmInstanceUuid;
            this.level = level;
        }
    }
}