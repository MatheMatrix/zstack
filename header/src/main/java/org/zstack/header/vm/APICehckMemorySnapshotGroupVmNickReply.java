package org.zstack.header.vm;

import org.zstack.header.message.APIReply;
import org.zstack.header.rest.RestResponse;
import org.zstack.header.storage.snapshot.group.VolumeSnapshotGroupInventory;
import org.zstack.header.storage.snapshot.group.VolumeSnapshotGroupRefInventory;
import org.zstack.header.storage.snapshot.group.VolumeSnapshotGroupVO;
import org.zstack.header.volume.VolumeType;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;

/**
 * Created by LiangHanYu on 2022/7/5 17:45
 */
@RestResponse(fieldsTo = {"all"})
public class APICehckMemorySnapshotGroupVmNickReply extends APIReply {
    private List<VmNic> inventories;

    public APICehckMemorySnapshotGroupVmNickReply() {
    }

    public static class VmNic {
        public String ip;
        public String mac;
        public String vmInstanceUuid;
    }

    public List<VmNic> getInventories() {
        return inventories;
    }

    public void setInventories(List<VmNic> inventories) {
        this.inventories = inventories;
    }

    public static APICehckMemorySnapshotGroupVmNickReply __example__() {
        APICehckMemorySnapshotGroupVmNickReply reply = new APICehckMemorySnapshotGroupVmNickReply();
        VmNic inv = new VmNic();
        inv.ip = "127.0.0.1";
        inv.mac = "00:16:3e:00:00:01";
        inv.vmInstanceUuid = uuid(VolumeSnapshotGroupVO.class);
        reply.setInventories(Collections.singletonList(inv));
        return reply;
    }

}
