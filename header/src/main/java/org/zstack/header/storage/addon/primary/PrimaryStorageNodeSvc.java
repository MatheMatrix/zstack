package org.zstack.header.storage.addon.primary;

import org.zstack.header.core.Completion;
import org.zstack.header.core.ReturnValueCompletion;
import org.zstack.header.host.HostInventory;
import org.zstack.header.volume.VolumeInventory;
import org.zstack.header.volume.VolumeStats;

import java.util.List;

public interface PrimaryStorageNodeSvc {
    void getVolumeStats(VolumeInventory vol, ReturnValueCompletion<VolumeStats> comp);

    void activate(BaseVolumeInfo v, HostInventory h, boolean shareable, ReturnValueCompletion<ActiveVolumeTO> comp);

    void deactivate(String installPath, String protocol, HostInventory h, Completion comp);

    String getActivePath(BaseVolumeInfo v, HostInventory h, boolean shareable);
    BaseVolumeInfo getActiveVolumeInfo(String activePath, HostInventory h, boolean shareable);

    // TODO use HostInventory
    List<ActiveVolumeClient> getActiveClients(String installPath, String protocol);

    List<String> getActiveVolumesLocation(HostInventory h);

    void activateHeartbeatVolume(HostInventory h, ReturnValueCompletion<HeartbeatVolumeTO> comp);

    void deactivateHeartbeatVolume(HostInventory h, Completion comp);

    String getIdentity();
}
