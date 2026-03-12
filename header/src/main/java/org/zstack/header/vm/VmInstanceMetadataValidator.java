package org.zstack.header.vm;

import org.zstack.header.exception.CloudRuntimeException;
import org.zstack.utils.gson.JSONObjectUtil;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 虚拟机元数据校验器。
 *
 * <p>在反序列化后、注册前执行校验，确保元数据完整性和一致性。</p>
 *
 * <p>校验项：
 * <ul>
 *   <li>schemaVersion 与当前平台版本匹配</li>
 *   <li>ResourceMetadata.resourceUuid 与 vo 内部 uuid 一致</li>
 *   <li>snapshotGroupRefs 引用的 groupUuid 必须存在于 snapshotGroups 中</li>
 * </ul>
 */
public class VmInstanceMetadataValidator {

    private VmInstanceMetadataValidator() {
    }

    /**
     * 执行全量校验。
     *
     * @param dto            待校验的元数据 DTO
     * @param currentVersion 当前平台 schema 版本
     * @throws CloudRuntimeException 校验失败时抛出
     */
    public static void validate(VmInstanceMetadataDTO dto, String currentVersion) {
        validateSchemaVersion(dto, currentVersion);
        validateResourceUuidConsistency(dto);
        validateSnapshotGroupIntegrity(dto);
    }

    /**
     * 校验 schema 版本是否匹配当前平台版本。
     *
     * @param dto            待校验的元数据 DTO
     * @param currentVersion 当前平台 schema 版本
     * @throws CloudRuntimeException 版本缺失或不匹配时抛出
     */
    public static void validateSchemaVersion(VmInstanceMetadataDTO dto, String currentVersion) {
        if (dto.schemaVersion == null || dto.schemaVersion.isEmpty()) {
            throw new CloudRuntimeException("metadata schemaVersion is missing");
        }
        if (!dto.schemaVersion.equals(currentVersion)) {
            throw new CloudRuntimeException(String.format(
                    "metadata schemaVersion[%s] does not match current platform version[%s]," +
                            " please upgrade metadata first",
                    dto.schemaVersion, currentVersion));
        }
    }

    /**
     * 校验所有 ResourceMetadata 的 resourceUuid 与 vo 内部 uuid 一致。
     *
     * @param dto 待校验的元数据 DTO
     * @throws CloudRuntimeException resourceUuid 缺失或与 vo.uuid 不一致时抛出
     */
    public static void validateResourceUuidConsistency(VmInstanceMetadataDTO dto) {
        if (dto.vm != null) {
            validateSingleResourceUuid(dto.vm, "vm");
        }
        if (dto.volumes != null) {
            for (int i = 0; i < dto.volumes.size(); i++) {
                validateSingleResourceUuid(dto.volumes.get(i), "volumes[" + i + "]");
            }
        }
        if (dto.nics != null) {
            for (int i = 0; i < dto.nics.size(); i++) {
                validateSingleResourceUuid(dto.nics.get(i), "nics[" + i + "]");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void validateSingleResourceUuid(VmInstanceMetadataDTO.ResourceMetadata rm, String path) {
        if (rm.resourceUuid == null) {
            throw new CloudRuntimeException(String.format(
                    "metadata %s.resourceUuid is null", path));
        }
        if (rm.vo == null) {
            throw new CloudRuntimeException(String.format(
                    "metadata %s.vo is null", path));
        }

        Map<String, Object> voMap = JSONObjectUtil.toObject(rm.vo, Map.class);
        Object voUuid = voMap.get("uuid");
        if (voUuid == null) {
            throw new CloudRuntimeException(String.format(
                    "metadata %s.vo does not contain uuid field", path));
        }
        if (!rm.resourceUuid.equals(voUuid.toString())) {
            throw new CloudRuntimeException(String.format(
                    "metadata %s.resourceUuid[%s] does not match vo.uuid[%s]",
                    path, rm.resourceUuid, voUuid));
        }
    }

    /**
     * 校验快照组引用的完整性。
     *
     * <p>snapshotGroupRefs 中引用的 volumeSnapshotGroupUuid
     * 必须存在于 snapshotGroups 中。</p>
     *
     * @param dto 待校验的元数据 DTO
     * @throws CloudRuntimeException 引用了不存在的 group 时抛出
     */
    @SuppressWarnings("unchecked")
    public static void validateSnapshotGroupIntegrity(VmInstanceMetadataDTO dto) {
        if (dto.snapshotGroupRefs == null || dto.snapshotGroupRefs.isEmpty()) {
            return;
        }
        if (dto.snapshotGroups == null || dto.snapshotGroups.isEmpty()) {
            throw new CloudRuntimeException(
                    "metadata has snapshotGroupRefs but no snapshotGroups");
        }

        Set<String> groupUuids = new HashSet<>();
        for (String groupJson : dto.snapshotGroups) {
            Map<String, Object> groupMap = JSONObjectUtil.toObject(groupJson, Map.class);
            Object uuid = groupMap.get("uuid");
            if (uuid != null) {
                groupUuids.add(uuid.toString());
            }
        }

        for (String refJson : dto.snapshotGroupRefs) {
            Map<String, Object> refMap = JSONObjectUtil.toObject(refJson, Map.class);
            Object groupUuid = refMap.get("volumeSnapshotGroupUuid");
            if (groupUuid != null && !groupUuids.contains(groupUuid.toString())) {
                throw new CloudRuntimeException(String.format(
                        "metadata snapshotGroupRef references non-existent group[uuid:%s]",
                        groupUuid));
            }
        }
    }
}