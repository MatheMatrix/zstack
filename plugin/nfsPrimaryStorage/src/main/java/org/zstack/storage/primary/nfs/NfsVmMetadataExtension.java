package org.zstack.storage.primary.nfs;

import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Configurable;
import org.zstack.core.db.Q;
import org.zstack.header.exception.CloudRuntimeException;
import org.zstack.header.storage.primary.PrimaryStorageVO;
import org.zstack.header.storage.primary.PrimaryStorageVO_;
import org.zstack.header.vm.metadata.VmInstanceMetadataConstants;
import org.zstack.header.vm.metadata.VmMetadataPathBuildExtensionPoint;
import org.zstack.header.vm.metadata.VmMetadataPathReplacementExtensionPoint;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class NfsVmMetadataExtension implements VmMetadataPathBuildExtensionPoint, VmMetadataPathReplacementExtensionPoint {
    private static final CLogger logger = Utils.getLogger(NfsVmMetadataExtension.class);

    @Override
    public String getPrimaryStorageType() {
        return NfsPrimaryStorageConstant.NFS_PRIMARY_STORAGE_TYPE;
    }

    @Override
    public String buildMetadataDir(String primaryStorageUuid) {
        String mountPath = Q.New(PrimaryStorageVO.class).select(PrimaryStorageVO_.mountPath).eq(PrimaryStorageVO_.uuid, primaryStorageUuid).findValue();
        if (mountPath == null) {
            throw new CloudRuntimeException(String.format("cannot find mountPath for NFS primary storage[uuid:%s]", primaryStorageUuid));
        }
        return String.format("%s/%s", mountPath, VmInstanceMetadataConstants.METADATA_DIR_NAME);
    }

    @Override
    public String buildVmMetadataPath(String primaryStorageUuid, String vmInstanceUuid) {
        String mountPath = Q.New(PrimaryStorageVO.class).select(PrimaryStorageVO_.mountPath).eq(PrimaryStorageVO_.uuid, primaryStorageUuid).findValue();
        if (mountPath == null) {
            throw new CloudRuntimeException(String.format("cannot find mountPath for NFS primary storage[uuid:%s]", primaryStorageUuid));
        }
        return String.format("%s/%s/%s%s", mountPath, VmInstanceMetadataConstants.METADATA_DIR_NAME, vmInstanceUuid, VmInstanceMetadataConstants.FILE_METADATA_SUFFIX);
    }

    @Override
    public PathReplacementResult calculatePathReplacements(String targetPsUuid, List<String> allOldPaths) {
        String baseDir = Q.New(PrimaryStorageVO.class).select(PrimaryStorageVO_.mountPath).eq(PrimaryStorageVO_.uuid, targetPsUuid).findValue();
        if (baseDir == null) {
            baseDir = Q.New(PrimaryStorageVO.class).select(PrimaryStorageVO_.url).eq(PrimaryStorageVO_.uuid, targetPsUuid).findValue();
        }
        if (baseDir == null) {
            logger.warn(String.format("NFS PS[uuid:%s] has no mountPath or url, path replacement disabled", targetPsUuid));
            PathReplacementResult result = new PathReplacementResult();
            result.setMetadataToCurrentPathMap(Collections.emptyMap());
            return result;
        }
        String newPrefix = baseDir.endsWith("/") ? baseDir : baseDir + "/";

        // Extract old prefix from the first recognizable path
        String oldPrefix = null;
        for (String path : allOldPaths) {
            oldPrefix = extractOldPrefix(path);
            if (oldPrefix != null) break;
        }

        Map<String, String> pathMap = new LinkedHashMap<>();
        if (oldPrefix != null) {
            for (String oldPath : allOldPaths) {
                if (oldPath != null && oldPath.startsWith(oldPrefix)) {
                    pathMap.put(oldPath, newPrefix + oldPath.substring(oldPrefix.length()));
                }
            }
        } else {
            logger.warn(String.format("cannot extract old path prefix from any path for NFS PS[uuid:%s], " +
                    "path replacement disabled", targetPsUuid));
        }

        PathReplacementResult result = new PathReplacementResult();
        result.setMetadataToCurrentPathMap(pathMap);
        result.setOldPrefix(oldPrefix);
        result.setNewPrefix(newPrefix);
        return result;
    }

    private String extractOldPrefix(String path) {
        if (path == null || !path.startsWith("/")) {
            return null;
        }
        String[] markers = {"/rootVolumes/", "/dataVolumes/", "/volumeSnapshots/", "/memory/"};
        for (String marker : markers) {
            int idx = path.indexOf(marker);
            if (idx >= 0) {
                return path.substring(0, idx + 1);
            }
        }
        return null;
    }
}
