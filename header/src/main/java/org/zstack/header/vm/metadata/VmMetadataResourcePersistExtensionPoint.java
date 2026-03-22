package org.zstack.header.vm.metadata;

import java.sql.Timestamp;
import java.util.List;

public interface VmMetadataResourcePersistExtensionPoint {
    String getPrimaryStorageType();
    void afterVolumePersist(String primaryStorageUuid, String resourceUuid,
                            String resourceType, String hostUuid, long size, Timestamp now);
    void afterSnapshotPersist(String primaryStorageUuid, String resourceUuid,
                              String resourceType, String hostUuid, long size, Timestamp now);

    /**
     * Called during rollback to clean up storage-specific resource references
     * (e.g. LocalStorageResourceRefVO) created by afterVolumePersist/afterSnapshotPersist.
     *
     * @param resourceUuids all resource UUIDs (volumes + snapshots) that were persisted
     */
    default void afterRegistrationRollback(List<String> resourceUuids) {
    }
}
