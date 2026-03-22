package org.zstack.storage.primary.local;

import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.Q;
import org.zstack.header.exception.CloudRuntimeException;
import org.zstack.header.storage.primary.PrimaryStorageVO;
import org.zstack.header.storage.primary.PrimaryStorageVO_;
import org.zstack.header.vm.metadata.VmInstanceMetadataConstants;
import org.zstack.header.vm.metadata.VmMetadataPathBuildExtensionPoint;
import org.zstack.header.vm.metadata.VmMetadataPathReplacementExtensionPoint;
import org.zstack.header.vm.metadata.VmMetadataResourcePersistExtensionPoint;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class LocalStorageVmMetadataExtension implements VmMetadataPathBuildExtensionPoint,
        VmMetadataPathReplacementExtensionPoint, VmMetadataResourcePersistExtensionPoint {
    private static final CLogger logger = Utils.getLogger(LocalStorageVmMetadataExtension.class);

    @Autowired
    private DatabaseFacade dbf;

    @Override
    public String getPrimaryStorageType() {
        return LocalStorageConstants.LOCAL_STORAGE_TYPE;
    }

    @Override
    public String buildMetadataDir(String primaryStorageUuid) {
        String url = Q.New(PrimaryStorageVO.class).select(PrimaryStorageVO_.url).eq(PrimaryStorageVO_.uuid, primaryStorageUuid).findValue();
        if (url == null) {
            throw new CloudRuntimeException(String.format("cannot find url for primary storage[uuid:%s]", primaryStorageUuid));
        }
        return String.format("%s/%s", normalizeBaseDir(url), VmInstanceMetadataConstants.METADATA_DIR_NAME);
    }

    @Override
    public String buildVmMetadataPath(String primaryStorageUuid, String vmInstanceUuid) {
        String url = Q.New(PrimaryStorageVO.class).select(PrimaryStorageVO_.url).eq(PrimaryStorageVO_.uuid, primaryStorageUuid).findValue();
        if (url == null) {
            throw new CloudRuntimeException(String.format("cannot find url for primary storage[uuid:%s]", primaryStorageUuid));
        }
        return String.format("%s/%s/%s%s", normalizeBaseDir(url), VmInstanceMetadataConstants.METADATA_DIR_NAME, vmInstanceUuid, VmInstanceMetadataConstants.FILE_METADATA_SUFFIX);
    }

    @Override
    public PathReplacementResult calculatePathReplacements(String targetPsUuid, List<String> allOldPaths) {
        String baseDir = Q.New(PrimaryStorageVO.class).select(PrimaryStorageVO_.url).eq(PrimaryStorageVO_.uuid, targetPsUuid).findValue();
        if (baseDir == null) {
            logger.warn(String.format("LocalStorage PS[uuid:%s] has no url, path replacement disabled", targetPsUuid));
            PathReplacementResult result = new PathReplacementResult();
            result.setMetadataToCurrentPathMap(Collections.emptyMap());
            return result;
        }
        String newPrefix = normalizeBaseDir(baseDir) + "/";

        // Extract old prefix from the first recognizable path
        String oldPrefix = null;
        for (String path : allOldPaths) {
            oldPrefix = VmInstanceMetadataConstants.extractOldPrefix(path);
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
            logger.warn(String.format("cannot extract old path prefix from any path for LocalStorage PS[uuid:%s], " +
                    "path replacement disabled", targetPsUuid));
        }

        PathReplacementResult result = new PathReplacementResult();
        result.setMetadataToCurrentPathMap(pathMap);
        result.setOldPrefix(oldPrefix);
        result.setNewPrefix(newPrefix);
        return result;
    }

    private String normalizeBaseDir(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    @Override
    public void afterVolumePersist(String primaryStorageUuid, String resourceUuid,
                                   String resourceType, String hostUuid, long size, Timestamp now) {
        createResourceRef(primaryStorageUuid, resourceUuid, resourceType, hostUuid, size, now);
    }

    @Override
    public void afterSnapshotPersist(String primaryStorageUuid, String resourceUuid,
                                     String resourceType, String hostUuid, long size, Timestamp now) {
        createResourceRef(primaryStorageUuid, resourceUuid, resourceType, hostUuid, size, now);
    }

    private void createResourceRef(String primaryStorageUuid, String resourceUuid,
                                   String resourceType, String hostUuid, long size, Timestamp now) {
        LocalStorageResourceRefVO ref = new LocalStorageResourceRefVO();
        ref.setPrimaryStorageUuid(primaryStorageUuid);
        ref.setResourceUuid(resourceUuid);
        ref.setResourceType(resourceType);
        ref.setHostUuid(hostUuid);
        ref.setSize(size);
        ref.setCreateDate(now);
        ref.setLastOpDate(now);
        dbf.persist(ref);
    }
}
