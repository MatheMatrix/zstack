package org.zstack.header.volume;

import org.springframework.http.HttpMethod;
import org.zstack.header.message.APISyncCallMessage;
import org.zstack.header.rest.RestRequest;
import org.zstack.header.vm.MetadataImpact;

/**
 */
@RestRequest(
        path = "/volumes/formats",
        method = HttpMethod.GET,
        responseClass = APIGetVolumeFormatReply.class
)
@MetadataImpact(MetadataImpact.Impact.NONE)
public class APIGetVolumeFormatMsg extends APISyncCallMessage {
 
    public static APIGetVolumeFormatMsg __example__() {
        APIGetVolumeFormatMsg msg = new APIGetVolumeFormatMsg();

        return msg;
    }

}
