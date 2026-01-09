package org.zstack.compute.vm.metadata;

import org.codehaus.plexus.component.annotations.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.header.message.APIMessage;
import org.zstack.header.vm.VmInstanceMessage;
import org.zstack.header.volume.APICreateVolumeSnapshotGroupMsg;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Collections;
import java.util.List;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface MetadataImpact {
    /**
     * 影响级别
     */
    MetadataImpactLevel value() default MetadataImpactLevel.CONFIG;

    /**
     * 失败时是否也更新
     */
    boolean updateOnFailure() default false;

    /**
     * 自定义 VmUuid 解析器（可选）
     * 不指定时使用注册表中的默认解析器
     */
    Class<? extends VmUuidResolver> resolver() default VmUuidResolver.class;

    /**
     * VM UUID 解析器接口
     */
    public interface VmUuidResolver {
        /**
         * 从 API 消息解析出需要更新元数据的 vmUuid 列表
         *
         * @param msg API 消息
         * @return vmUuid 列表（可能为空，可能多个）
         */
        List<String> resolve(APIMessage msg);
    }

    /**
     * 默认解析器：从 VmInstanceMessage 接口获取
     */
    public class DefaultVmUuidResolver implements VmUuidResolver {
        @Override
        public List<String> resolve(APIMessage msg) {
            if (msg instanceof VmInstanceMessage) {
                String vmUuid = ((VmInstanceMessage) msg).getVmInstanceUuid();
                if (vmUuid != null) {
                    return Collections.singletonList(vmUuid);
                }
            }
            return Collections.emptyList();
        }
    }

    /**
     * Volume 相关 API 解析器
     */
    public class VolumeBasedVmUuidResolver implements VmUuidResolver {
        @Autowired
        private DatabaseFacade dbf;

        @Override
        public List<String> resolve(APIMessage msg) {
            List<String> volumeUuids = extractVolumeUuids(msg);
            if (volumeUuids.isEmpty()) {
                return Collections.emptyList();
            }

            // 查询这些 Volume 关联的 VM
            List<String> vmUuids = SQL.New(
                            "SELECT DISTINCT v.vmInstanceUuid FROM VolumeVO v " +
                                    "WHERE v.uuid IN (:uuids) AND v.vmInstanceUuid IS NOT NULL", String.class)
                    .param("uuids", volumeUuids)
                    .list();

            return vmUuids;
        }

        private List<String> extractVolumeUuids(APIMessage msg) {
            if (msg instanceof APICreateVolumesSnapshotMsg) {
                return ((APICreateVolumesSnapshotMsg) msg).getVolumeUuids();
            } else if (msg instanceof APICreateVolumeSnapshotGroupMsg) {
                return Collections.singletonList(
                        ((APICreateVolumeSnapshotGroupMsg) msg).getRootVolumeUuid());
            } else if (msg instanceof VolumeMessage) {
                return Collections.singletonList(((VolumeMessage) msg).getVolumeUuid());
            }
            return Collections.emptyList();
        }
    }

    /**
     * SystemTag/ResourceConfig 动态解析器
     */
    public class ResourceBasedVmUuidResolver implements VmUuidResolver {
        @Autowired
        private DatabaseFacade dbf;

        @Override
        public List<String> resolve(APIMessage msg) {
            String resourceType = null;
            String resourceUuid = null;

            if (msg instanceof APIAbstractCreateTagMsg) {
                resourceType = ((APIAbstractCreateTagMsg) msg).getResourceType();
                resourceUuid = ((APIAbstractCreateTagMsg) msg).getResourceUuid();
            } else if (msg instanceof APIDeleteTagMsg) {
                // 需要先查询 Tag 获取 resourceType 和 resourceUuid
                TagVO tag = dbf.findByUuid(((APIDeleteTagMsg) msg).getUuid(), TagVO.class);
                if (tag != null) {
                    resourceType = tag.getResourceType();
                    resourceUuid = tag.getResourceUuid();
                }
            }

            if (resourceType == null || resourceUuid == null) {
                return Collections.emptyList();
            }

            return resolveByResourceType(resourceType, resourceUuid);
        }

        private List<String> resolveByResourceType(String resourceType, String resourceUuid) {
            // VmInstanceVO 直接返回
            if ("VmInstanceVO".equals(resourceType)) {
                return Collections.singletonList(resourceUuid);
            }

            // VolumeVO 查询关联的 VM
            if ("VolumeVO".equals(resourceType)) {
                VolumeVO vol = dbf.findByUuid(resourceUuid, VolumeVO.class);
                if (vol != null && vol.getVmInstanceUuid() != null) {
                    return Collections.singletonList(vol.getVmInstanceUuid());
                }
                return Collections.emptyList();
            }

            // VmNicVO 查询关联的 VM
            if ("VmNicVO".equals(resourceType)) {
                VmNicVO nic = dbf.findByUuid(resourceUuid, VmNicVO.class);
                if (nic != null && nic.getVmInstanceUuid() != null) {
                    return Collections.singletonList(nic.getVmInstanceUuid());
                }
                return Collections.emptyList();
            }

            // VolumeSnapshotVO 需要查询 Volume 再查询 VM
            if ("VolumeSnapshotVO".equals(resourceType)) {
                VolumeSnapshotVO snap = dbf.findByUuid(resourceUuid, VolumeSnapshotVO.class);
                if (snap != null && snap.getVolumeUuid() != null) {
                    VolumeVO vol = dbf.findByUuid(snap.getVolumeUuid(), VolumeVO.class);
                    if (vol != null && vol.getVmInstanceUuid() != null) {
                        return Collections.singletonList(vol.getVmInstanceUuid());
                    }
                }
                return Collections.emptyList();
            }

            // 其他类型不影响 VM 元数据
            return Collections.emptyList();
        }
    }

    /**
     * 解析器注册表
     */
    @Component
    public class VmUuidResolverRegistry {
        private Map<Class<?>, VmUuidResolver> resolvers = new HashMap<>();
        private VmUuidResolver defaultResolver = new DefaultVmUuidResolver();

        @PostConstruct
        public void init() {
            // 注册特定 API 的解析器
            VolumeBasedVmUuidResolver volumeResolver = new VolumeBasedVmUuidResolver();
            resolvers.put(APICreateVolumesSnapshotMsg.class, volumeResolver);
            resolvers.put(APICreateVolumeSnapshotGroupMsg.class, volumeResolver);
            resolvers.put(APIAttachDataVolumeToVmMsg.class, volumeResolver);
            resolvers.put(APIDetachDataVolumeFromVmMsg.class, volumeResolver);

            ResourceBasedVmUuidResolver resourceResolver = new ResourceBasedVmUuidResolver();
            resolvers.put(APICreateSystemTagMsg.class, resourceResolver);
            resolvers.put(APIDeleteTagMsg.class, resourceResolver);
            resolvers.put(APIUpdateSystemTagMsg.class, resourceResolver);
            resolvers.put(APIUpdateResourceConfigMsg.class, resourceResolver);
            resolvers.put(APIDeleteResourceConfigMsg.class, resourceResolver);
        }

        public List<String> resolve(APIMessage msg) {
            VmUuidResolver resolver = resolvers.getOrDefault(msg.getClass(), defaultResolver);
            return resolver.resolve(msg);
        }
    }
}
