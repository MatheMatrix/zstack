package org.zstack.header.vm;

import org.springframework.http.HttpMethod;
import org.zstack.header.message.APIMessage;
import org.zstack.header.message.APIParam;
import org.zstack.header.rest.RestRequest;

@RestRequest(
        path = "/vm-instances/{uuid}/cache/actions",
        method = HttpMethod.PUT,
        isAction = true,
        responseClass = APICleanUpTemplatedVmInstanceCacheEvent.class
)
public class APICleanUpTemplatedVmInstanceCacheMsg extends APIMessage implements VmInstanceMessage {
    @APIParam(resourceType = TemplatedVmInstanceVO.class, successIfResourceNotExisting = true)
    private String uuid;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    @Override
    public String getVmInstanceUuid() {
        return getUuid();
    }

    public static APICleanUpTemplatedVmInstanceCacheMsg __example__() {
        APICleanUpTemplatedVmInstanceCacheMsg msg = new APICleanUpTemplatedVmInstanceCacheMsg();
        msg.setUuid(uuid());
        return msg;
    }
}
