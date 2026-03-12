package org.zstack.header.storage.primary;

import org.springframework.http.HttpMethod;
import org.zstack.header.message.APIParam;
import org.zstack.header.message.APISyncCallMessage;
import org.zstack.header.rest.RestRequest;
import org.zstack.header.vm.MetadataImpact;

@RestRequest(
        path = "/primary-storage/{uuid}/license",
        method = HttpMethod.GET,
        responseClass = APIGetPrimaryStorageLicenseInfoReply.class
)
@MetadataImpact(MetadataImpact.Impact.NONE)
public class APIGetPrimaryStorageLicenseInfoMsg extends APISyncCallMessage {
    @APIParam(resourceType = PrimaryStorageVO.class)
    private String uuid;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public static APIGetPrimaryStorageLicenseInfoMsg __example__() {
        APIGetPrimaryStorageLicenseInfoMsg msg = new APIGetPrimaryStorageLicenseInfoMsg();
        msg.setUuid(uuid());
        return msg;
    }
}