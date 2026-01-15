package org.zstack.header.storage.primary;

import org.springframework.http.HttpMethod;
import org.zstack.header.message.APIMessage;
import org.zstack.header.message.APIParam;
import org.zstack.header.rest.RestRequest;

@RestRequest(
        path = "/primary-storage/{uuid}/consistency",
        responseClass = APICheckPrimaryStorageConsistencyEvent.class,
        method = HttpMethod.PUT,
        isAction = true
)
public class APICheckPrimaryStorageConsistencyMsg extends APIMessage implements PrimaryStorageMessage {
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

    public static APICheckPrimaryStorageConsistencyMsg __example__() {
        APICheckPrimaryStorageConsistencyMsg msg = new APICheckPrimaryStorageConsistencyMsg();
        msg.setUuid(uuid(PrimaryStorageVO.class));
        return msg;
    }
}