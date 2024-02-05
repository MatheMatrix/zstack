package org.zstack.header.volume;

import org.springframework.http.HttpMethod;
import org.zstack.header.identity.Action;
import org.zstack.header.message.APIMessage;
import org.zstack.header.message.APIParam;
import org.zstack.header.rest.RestRequest;
import org.zstack.header.storage.snapshot.group.VolumeSnapshotGroupVO;

/**
 * @ Author : yh.w
 * @ Date   : Created in 15:40 2023/8/21
 */
@Action(category = VolumeConstant.ACTION_CATEGORY)
@RestRequest(
        path = "/volumes/{uuid}/actions",
        isAction = true,
        method = HttpMethod.PUT,
        responseClass = APIUndoSnapshotGroupCreationEvent.class
)
public class APIUndoSnapshotGroupCreationMsg extends APIMessage implements VolumeMessage {

    @APIParam(resourceType = VolumeVO.class, checkAccount = true, operationTarget = true)
    private String uuid;
    @APIParam(resourceType = VolumeSnapshotGroupVO.class, checkAccount = true, operationTarget = true)
    private String snapShotGroupUuid;

    public String getSnapShotGroupUuid() {
        return snapShotGroupUuid;
    }

    public void setSnapShotGroupUuid(String snapShotGroupUuid) {
        this.snapShotGroupUuid = snapShotGroupUuid;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    @Override
    public String getVolumeUuid() {
        return uuid;
    }

    public static APIUndoSnapshotGroupCreationMsg __example__() {
        APIUndoSnapshotGroupCreationMsg msg = new APIUndoSnapshotGroupCreationMsg();
        msg.setUuid(uuid());
        msg.setSnapShotGroupUuid(uuid());
        return msg;
    }
}
