package org.zstack.header.vm.metadata;

import org.zstack.header.exception.CloudRuntimeException;
import org.zstack.utils.gson.JSONObjectUtil;
import org.zstack.utils.logging.CLogger;
import org.zstack.utils.logging.CLoggerImpl;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class VmInstanceMetadataValidator {
    private static final CLogger logger = CLoggerImpl.getLogger(VmInstanceMetadataValidator.class);

    private VmInstanceMetadataValidator() {
    }

    public static void validate(VmInstanceMetadataDTO dto, String currentVersion) {
        validateSchemaVersion(dto, currentVersion);
        validateResourceUuidConsistency(dto);
        validateSnapshotGroupIntegrity(dto);
    }

    public static void validateSchemaVersion(VmInstanceMetadataDTO dto, String currentVersion) {
        if (dto.schemaVersion == null || dto.schemaVersion.isEmpty()) {
            logger.warn("metadata schemaVersion is missing");
            return;
        }
        if (!dto.schemaVersion.equals(currentVersion)) {
            logger.warn(String.format(
                    "metadata schemaVersion[%s] does not match current platform version[%s], please upgrade metadata first",
                    dto.schemaVersion, currentVersion));
        }
    }

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

    private static void validateSingleResourceUuid(ResourceMetadata rm, String path) {
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