package org.zstack.header.storage.snapshot;

import org.zstack.header.message.APIEvent;
import org.zstack.header.rest.RestResponse;
import org.zstack.header.volume.*;

/**
 * @author Xingwei Yu
 * @date 2024/7/31 17:03
 */
@RestResponse(allTo = "inventory")
public class APISyncVolumeSnapshotSizeEvent extends APIEvent {
    private VolumeSnapshotInventory inventory;

    public APISyncVolumeSnapshotSizeEvent() {
    }

    public APISyncVolumeSnapshotSizeEvent(String apiId) {
        super(apiId);
    }

    public VolumeSnapshotInventory getInventory() {
        return inventory;
    }

    public void setInventory(VolumeSnapshotInventory inventory) {
        this.inventory = inventory;
    }

    public static APISyncVolumeSnapshotSizeEvent __example__() {
        APISyncVolumeSnapshotSizeEvent event = new APISyncVolumeSnapshotSizeEvent();

        VolumeSnapshotInventory inv = new VolumeSnapshotInventory();
        inv.setUuid(uuid());
        inv.setName("My Snapshot");
        inv.setPrimaryStorageUuid(uuid());
        inv.setFormat("qcow2");
        inv.setLatest(false);
        inv.setPrimaryStorageUuid("/zstack_ps/rootVolumes/acct-e77f16d460ea46e18262547b56972273/vol-13c66bb52d0949398e520183b917f813/snapshots/2fa6979af5c6479fa98f37d316f44b5f.qcow2");
        inv.setSize(1310720);
        inv.setStatus(VolumeSnapshotStatus.Ready.toString());
        inv.setState(VolumeSnapshotState.Enabled.toString());
        inv.setVolumeType(VolumeType.Root.toString());

        event.setInventory(inv);

        return event;
    }
}
