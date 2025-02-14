package org.zstack.header.volume;

public interface VolumeConfigsExtensionPoint {
    void setVolumeConfigs(VolumeConfigs vcs, String volUuid);
}
