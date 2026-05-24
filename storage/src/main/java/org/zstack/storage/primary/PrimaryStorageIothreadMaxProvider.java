package org.zstack.storage.primary;

public interface PrimaryStorageIothreadMaxProvider {
    boolean match(String primaryStorageUuid, String primaryStorageType, String primaryStorageIdentity);

    int getIothreadMax(String primaryStorageUuid);
}
