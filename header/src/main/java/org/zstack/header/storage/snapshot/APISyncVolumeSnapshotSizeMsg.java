package org.zstack.header.storage.snapshot;

import org.springframework.http.HttpMethod;
import org.zstack.header.identity.Action;
import org.zstack.header.message.APIMessage;
import org.zstack.header.message.APIParam;
import org.zstack.header.rest.APINoSee;
import org.zstack.header.rest.RestRequest;

/**
 * @author Xingwei Yu
 * @date 2024/7/31 16:53
 */
@Action(category = VolumeSnapshotConstant.ACTION_CATEGORY)
@RestRequest(
        path = "/volume-snapshots/{uuid}/actions",
        method = HttpMethod.PUT,
        isAction = true,
        responseClass = APISyncVolumeSnapshotSizeEvent.class
)
public class APISyncVolumeSnapshotSizeMsg extends APIMessage implements VolumeSnapshotMessage {
    @APIParam(resourceType = VolumeSnapshotVO.class, successIfResourceNotExisting = true)
    private String uuid;

    @APINoSee
    private String volumeUuid;

    @APINoSee
    private String treeUuid;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    @Override
    public String getSnapshotUuid() {
        return uuid;
    }

    @Override
    public String getVolumeUuid() {
        return volumeUuid;
    }

    @Override
    public void setVolumeUuid(String volumeUuid) {
        this.volumeUuid = volumeUuid;
    }

    @Override
    public void setTreeUuid(String treeUuid) {
        this.treeUuid = treeUuid;
    }

    @Override
    public String getTreeUuid() {
        return treeUuid;
    }

    public static APISyncVolumeSnapshotSizeMsg __example__() {
        APISyncVolumeSnapshotSizeMsg msg = new APISyncVolumeSnapshotSizeMsg();

        msg.setUuid(uuid());

        return msg;
    }
}
