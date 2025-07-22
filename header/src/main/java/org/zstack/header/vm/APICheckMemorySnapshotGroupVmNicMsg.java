package org.zstack.header.vm;

import org.springframework.http.HttpMethod;
import org.zstack.header.message.APIParam;
import org.zstack.header.message.APISyncCallMessage;
import org.zstack.header.rest.APINoSee;
import org.zstack.header.rest.RestRequest;
import org.zstack.header.storage.snapshot.group.VolumeSnapshotGroupVO;

@RestRequest(
        path = "/memory-snapshots/group/reference",
        method = HttpMethod.GET,
        responseClass = APIGetMemorySnapshotGroupReferenceReply.class
)
public class APICheckMemorySnapshotGroupVmNicMsg extends APISyncCallMessage {
    @APIParam(resourceType = VolumeSnapshotGroupVO.class)
    private String uuid;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public static APICheckMemorySnapshotGroupVmNicMsg __example__() {
        APICheckMemorySnapshotGroupVmNicMsg msg = new APICheckMemorySnapshotGroupVmNicMsg();
        msg.setUuid(uuid(VolumeSnapshotGroupVO.class));
        return msg;
    }
}
