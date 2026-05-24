package org.zstack.storage.primary;

public interface PrimaryStorageIothreadMaxProvider {
    String getPrimaryStorageIdentityForIothreadMax();

    int getIothreadMax(String primaryStorageUuid);
}
