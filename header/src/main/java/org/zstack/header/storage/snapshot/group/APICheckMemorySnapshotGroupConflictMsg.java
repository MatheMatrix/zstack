package org.zstack.header.storage.snapshot.group;

import org.springframework.http.HttpMethod;
import org.zstack.header.message.APIParam;
import org.zstack.header.message.APISyncCallMessage;
import org.zstack.header.rest.RestRequest;
import org.zstack.header.vm.MetadataImpact;

@RestRequest(
        path = "/memory-snapshots/groups/{uuid}/conflict-detection",
        method = HttpMethod.GET,
        responseClass = APICheckMemorySnapshotGroupConflictReply.class
)
@MetadataImpact(MetadataImpact.Impact.NONE)
public class APICheckMemorySnapshotGroupConflictMsg extends APISyncCallMessage {
    @APIParam(resourceType = VolumeSnapshotGroupVO.class)
    private String uuid;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public static APICheckMemorySnapshotGroupConflictMsg __example__() {
        APICheckMemorySnapshotGroupConflictMsg msg = new APICheckMemorySnapshotGroupConflictMsg();
        msg.setUuid(uuid(VolumeSnapshotGroupVO.class));
        return msg;
    }
}
