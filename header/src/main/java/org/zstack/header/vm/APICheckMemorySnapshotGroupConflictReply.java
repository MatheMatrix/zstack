package org.zstack.header.vm;

import org.zstack.header.message.APIReply;
import org.zstack.header.rest.RestResponse;
import org.zstack.header.storage.snapshot.group.VolumeSnapshotGroupVO;

import java.util.Collections;
import java.util.List;

@RestResponse(fieldsTo = {"all"})
public class APICheckMemorySnapshotGroupConflictReply extends APIReply {
    private List<NetworkConflict> networksConflict;

    public APICheckMemorySnapshotGroupConflictReply() {
    }

    public List<NetworkConflict> getNetworksConflict() {
        return networksConflict;
    }

    public void setNetworksConflict(List<NetworkConflict> networksConflict) {
        this.networksConflict = networksConflict;
    }

    public static APICheckMemorySnapshotGroupConflictReply __example__() {
        APICheckMemorySnapshotGroupConflictReply reply = new APICheckMemorySnapshotGroupConflictReply();
        NetworkConflict inv = new NetworkConflict();
        inv.ip = "127.0.0.1";
        inv.mac = "00:16:3e:00:00:01";
        inv.vmInstanceUuid = uuid(VolumeSnapshotGroupVO.class);
        reply.setNetworksConflict(Collections.singletonList(inv));
        return reply;
    }
}
