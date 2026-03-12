package org.zstack.compute.vm;

import org.zstack.header.vm.VmInstanceMetadataDTO;
import org.zstack.header.vm.VmInstanceMetadataRegistrationSpec;
import org.zstack.utils.gson.JSONObjectUtil;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 虚拟机元数据注册时的字段处理器。
 *
 * <p>根据"注册字段处理矩阵"的规则，对反序列化后的 VO JSON 字段执行：
 * 保留 / 替换 / 设 null / 重新生成 / 硬编码 等操作。</p>
 *
 * <p>处理采用 Map 操作方式（而非反序列化为具体 VO 类），
 * 避免字段类型变更导致的兼容性问题。</p>
 *
 * @see VmInstanceMetadataRegistrationSpec
 */
public class VmInstanceMetadataFieldProcessor {

    private VmInstanceMetadataFieldProcessor() {
    }

    // ================================================================
    // VmInstanceVO
    // ================================================================

    /**
     * VmInstanceVO 中注册时需要设为 null 的字段。
     */
    private static final Set<String> VM_NULL_FIELDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "clusterUuid",
            "hostUuid",
            "lastHostUuid",
            "instanceOfferingUuid",
            "defaultL3NetworkUuid",
            "managementNetworkUuid"
    )));

    /**
     * 处理 VmInstanceVO JSON。
     *
     * <p>处理规则：
     * <ul>
     *   <li>uuid/name/description/cpuNum/memorySize/platform/architecture/hypervisorType/imageUuid → 保留</li>
     *   <li>zoneUuid → 替换为 spec 中的新值</li>
     *   <li>clusterUuid/hostUuid/lastHostUuid/instanceOfferingUuid/defaultL3NetworkUuid/managementNetworkUuid → 设 null</li>
     *   <li>state → 硬编码为 Stopped</li>
     *   <li>accountUuid → 替换为 spec 中的调用者</li>
     * </ul>
     *
     * @param vmVoJson 原始 VmInstanceVO JSON
     * @param spec     注册参数
     * @return 处理后的 VmInstanceVO JSON
     */
    @SuppressWarnings("unchecked")
    public static String processVmInstanceVO(String vmVoJson, VmInstanceMetadataRegistrationSpec spec) {
        Map<String, Object> voMap = JSONObjectUtil.toObject(vmVoJson, LinkedHashMap.class);

        for (String field : VM_NULL_FIELDS) {
            voMap.put(field, null);
        }

        voMap.put("zoneUuid", spec.getZoneUuid());
        voMap.put("accountUuid", spec.getAccountUuid());
        voMap.put("state", "Stopped");

        return JSONObjectUtil.toJsonString(voMap);
    }

    // ================================================================
    // VolumeVO
    // ================================================================

    /**
     * VolumeVO 中注册时需要设为 null 的字段。
     */
    private static final Set<String> VOLUME_NULL_FIELDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "diskOfferingUuid"
    )));

    /**
     * 处理 VolumeVO JSON。
     *
     * <p>处理规则：
     * <ul>
     *   <li>uuid/vmInstanceUuid/name/size/type/format → 保留</li>
     *   <li>primaryStorageUuid → 替换为 spec 中的新主存储 UUID</li>
     *   <li>installPath → 路径标识符替换</li>
     *   <li>diskOfferingUuid → 设 null</li>
     *   <li>accountUuid → 替换为 spec 中的调用者</li>
     * </ul>
     *
     * @param volumeVoJson 原始 VolumeVO JSON
     * @param spec         注册参数
     * @return 处理后的 VolumeVO JSON
     */
    @SuppressWarnings("unchecked")
    public static String processVolumeVO(String volumeVoJson, VmInstanceMetadataRegistrationSpec spec) {
        Map<String, Object> voMap = JSONObjectUtil.toObject(volumeVoJson, LinkedHashMap.class);

        for (String field : VOLUME_NULL_FIELDS) {
            voMap.put(field, null);
        }

        voMap.put("primaryStorageUuid", spec.getPrimaryStorageUuid());
        voMap.put("accountUuid", spec.getAccountUuid());

        replaceInstallPath(voMap, "installPath", spec);

        return JSONObjectUtil.toJsonString(voMap);
    }

    // ================================================================
    // VolumeSnapshotVO
    // ================================================================

    /**
     * 处理 VolumeSnapshotVO JSON。
     *
     * <p>处理规则：
     * <ul>
     *   <li>uuid/volumeUuid/parentUuid/treeUuid/latest → 保留</li>
     *   <li>primaryStorageUuid → 替换为 spec 中的新主存储 UUID</li>
     *   <li>primaryStorageInstallPath → 路径标识符替换</li>
     * </ul>
     *
     * @param snapshotVoJson 原始 VolumeSnapshotVO JSON
     * @param spec           注册参数
     * @return 处理后的 VolumeSnapshotVO JSON
     */
    @SuppressWarnings("unchecked")
    public static String processVolumeSnapshotVO(String snapshotVoJson, VmInstanceMetadataRegistrationSpec spec) {
        Map<String, Object> voMap = JSONObjectUtil.toObject(snapshotVoJson, LinkedHashMap.class);

        voMap.put("primaryStorageUuid", spec.getPrimaryStorageUuid());

        replaceInstallPath(voMap, "primaryStorageInstallPath", spec);

        return JSONObjectUtil.toJsonString(voMap);
    }

    // ================================================================
    // SystemTagVO / ResourceConfigVO
    // ================================================================

    /**
     * 处理 SystemTagVO JSON：为 uuid 生成新值，移除自增 id。
     *
     * @param tagJson      原始 SystemTagVO JSON
     * @param uuidSupplier UUID 生成器（通常为 Platform::getUuid）
     * @return 处理后的 SystemTagVO JSON
     */
    @SuppressWarnings("unchecked")
    public static String processSystemTagVO(String tagJson, Supplier<String> uuidSupplier) {
        Map<String, Object> tagMap = JSONObjectUtil.toObject(tagJson, LinkedHashMap.class);

        tagMap.put("uuid", uuidSupplier.get());
        tagMap.remove("id");

        return JSONObjectUtil.toJsonString(tagMap);
    }

    /**
     * 处理 ResourceConfigVO JSON：为 uuid 生成新值，移除自增 id。
     *
     * @param configJson   原始 ResourceConfigVO JSON
     * @param uuidSupplier UUID 生成器（通常为 Platform::getUuid）
     * @return 处理后的 ResourceConfigVO JSON
     */
    @SuppressWarnings("unchecked")
    public static String processResourceConfigVO(String configJson, Supplier<String> uuidSupplier) {
        Map<String, Object> configMap = JSONObjectUtil.toObject(configJson, LinkedHashMap.class);

        configMap.put("uuid", uuidSupplier.get());
        configMap.remove("id");

        return JSONObjectUtil.toJsonString(configMap);
    }

    // ================================================================
    // 跨存储过滤
    // ================================================================

    /**
     * 判断 volume 的 installPath 是否属于指定主存储。
     *
     * @param volumeVoJson   VolumeVO JSON
     * @param pathIdentifier 存储路径标识符（如 vg uuid 或挂载路径前缀）
     * @return true 表示属于该主存储
     */
    @SuppressWarnings("unchecked")
    public static boolean belongsToPrimaryStorage(String volumeVoJson, String pathIdentifier) {
        Map<String, Object> voMap = JSONObjectUtil.toObject(volumeVoJson, LinkedHashMap.class);
        String installPath = (String) voMap.get("installPath");
        return installPath != null && installPath.contains(pathIdentifier);
    }

    /**
     * 过滤出属于指定主存储的 volume UUID 集合。
     *
     * <p>注册时，仅处理属于当前存储的 volume 及其关联快照。
     * 不属于当前存储的 volume 跳过。</p>
     *
     * @param dto            完整元数据 DTO
     * @param pathIdentifier 旧存储路径标识符
     * @return 属于该存储的 volume resourceUuid 集合
     */
    public static Set<String> filterVolumesByStorage(VmInstanceMetadataDTO dto, String pathIdentifier) {
        if (dto.volumes == null) {
            return Collections.emptySet();
        }
        return dto.volumes.stream()
                .filter(rm -> belongsToPrimaryStorage(rm.vo, pathIdentifier))
                .map(rm -> rm.resourceUuid)
                .collect(Collectors.toSet());
    }

    // ================================================================
    // 内部工具
    // ================================================================

    private static void replaceInstallPath(Map<String, Object> voMap, String fieldName,
                                           VmInstanceMetadataRegistrationSpec spec) {
        String path = (String) voMap.get(fieldName);
        if (path != null && spec.getOldPathIdentifier() != null && spec.getNewPathIdentifier() != null) {
            voMap.put(fieldName, path.replace(spec.getOldPathIdentifier(), spec.getNewPathIdentifier()));
        }
    }
}