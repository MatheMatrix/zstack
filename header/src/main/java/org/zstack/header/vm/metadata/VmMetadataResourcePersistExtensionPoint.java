package org.zstack.header.vm.metadata;

import java.sql.Timestamp;

public interface VmMetadataResourcePersistExtensionPoint {
    String getPrimaryStorageType();
    void afterVolumePersist(String primaryStorageUuid, String resourceUuid,
                            String resourceType, String hostUuid, long size, Timestamp now);
    void afterSnapshotPersist(String primaryStorageUuid, String resourceUuid,
                              String resourceType, String hostUuid, long size, Timestamp now);
}
