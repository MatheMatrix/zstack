package org.zstack.header.volume;

import org.springframework.http.HttpMethod;
import org.zstack.header.message.APIParam;
import org.zstack.header.message.APISyncCallMessage;
import org.zstack.header.rest.RestRequest;
import org.zstack.header.vm.MetadataImpact;

/**
 * Created by xing5 on 2016/5/19.
 */
@RestRequest(
        path = "/volumes/{uuid}/capabilities",
        method = HttpMethod.GET,
        responseClass = APIGetVolumeCapabilitiesReply.class
)
@MetadataImpact(MetadataImpact.Impact.NONE)
public class APIGetVolumeCapabilitiesMsg extends APISyncCallMessage implements VolumeMessage {
    @APIParam(resourceType = VolumeVO.class)
    private String uuid;

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
 
    public static APIGetVolumeCapabilitiesMsg __example__() {
        APIGetVolumeCapabilitiesMsg msg = new APIGetVolumeCapabilitiesMsg();
        msg.setUuid(uuid());

        return msg;
    }

}
