package org.zstack.header.storage.primary;

import org.springframework.http.HttpMethod;
import org.zstack.header.message.APIMessage;
import org.zstack.header.message.APIParam;
import org.zstack.header.rest.RestRequest;
import org.zstack.header.vm.MetadataImpact;

/**
 * Created by frank on 6/18/2015.
 */
@RestRequest(
        path = "/primary-storage/{primaryStorageUuid}/actions",
        responseClass = APISyncPrimaryStorageCapacityEvent.class,
        method = HttpMethod.PUT,
        isAction = true
)
@MetadataImpact(MetadataImpact.Impact.NONE)
public class APISyncPrimaryStorageCapacityMsg extends APIMessage implements PrimaryStorageMessage {
    @APIParam(resourceType = PrimaryStorageVO.class)
    private String primaryStorageUuid;

    @Override
    public String getPrimaryStorageUuid() {
        return primaryStorageUuid;
    }

    public void setPrimaryStorageUuid(String primaryStorageUuid) {
        this.primaryStorageUuid = primaryStorageUuid;
    }
 
    public static APISyncPrimaryStorageCapacityMsg __example__() {
        APISyncPrimaryStorageCapacityMsg msg = new APISyncPrimaryStorageCapacityMsg();

        msg.setPrimaryStorageUuid(uuid());

        return msg;
    }

}
