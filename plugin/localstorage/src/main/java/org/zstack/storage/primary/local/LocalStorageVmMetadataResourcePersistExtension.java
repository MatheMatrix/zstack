package org.zstack.storage.primary.local;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.header.vm.metadata.VmMetadataResourcePersistExtensionPoint;

import java.sql.Timestamp;

@Component
public class LocalStorageVmMetadataResourcePersistExtension implements VmMetadataResourcePersistExtensionPoint {
    @Autowired
    private DatabaseFacade dbf;

    @Override
    public String getPrimaryStorageType() {
        return LocalStorageConstants.LOCAL_STORAGE_TYPE;
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
