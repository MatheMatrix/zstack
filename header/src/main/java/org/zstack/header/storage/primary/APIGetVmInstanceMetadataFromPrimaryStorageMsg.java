package org.zstack.header.storage.primary;

import org.springframework.http.HttpMethod;
import org.zstack.header.message.APIParam;
import org.zstack.header.message.APISyncCallMessage;
import org.zstack.header.rest.RestRequest;
import org.zstack.header.vm.MetadataImpact;


@RestRequest(
        path = "/primary-storage/vm-instances/metadata",
        method = HttpMethod.GET,
        responseClass = APIGetVmInstanceMetadataFromPrimaryStorageReply.class
)
@MetadataImpact(MetadataImpact.Impact.NONE)
public class APIGetVmInstanceMetadataFromPrimaryStorageMsg extends APISyncCallMessage implements PrimaryStorageMessage {
    @APIParam(resourceType = PrimaryStorageVO.class)
    private String uuid;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    @Override
    public String getPrimaryStorageUuid() {
        return uuid;
    }

    public static APIGetVmInstanceMetadataFromPrimaryStorageMsg __example__() {
        APIGetVmInstanceMetadataFromPrimaryStorageMsg msg = new APIGetVmInstanceMetadataFromPrimaryStorageMsg();
        msg.setUuid(uuid());
        return msg;
    }
}
