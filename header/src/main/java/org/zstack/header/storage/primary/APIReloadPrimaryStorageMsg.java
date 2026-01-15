package org.zstack.header.storage.primary;

import org.springframework.http.HttpMethod;
import org.zstack.header.message.APIMessage;
import org.zstack.header.message.APIParam;
import org.zstack.header.rest.RestRequest;

@RestRequest(
        path = "/primary-storage/{uuid}/reload",
        responseClass = APIReloadPrimaryStorageEvent.class,
        method = HttpMethod.PUT,
        isAction = true
)
public class APIReloadPrimaryStorageMsg extends APIMessage implements PrimaryStorageMessage {
    @APIParam(resourceType = PrimaryStorageVO.class)
    private String uuid;

    @Override
    public String getPrimaryStorageUuid() {
        return uuid;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }
 
    public static APIReloadPrimaryStorageMsg __example__() {
        APIReloadPrimaryStorageMsg msg = new APIReloadPrimaryStorageMsg();

        msg.setUuid(uuid(PrimaryStorageVO.class));

        return msg;
    }
}
