package org.zstack.header.storage.primary;

public interface RegisterVmInstanceException {
    String updateVolumeInstallPath(String installPath);

    String updateVolumeSnapshotInstallPath(String installPath);

    PrimaryStorageType getPrimaryStorageType();
}
