package org.zstack.header.vm;

import org.springframework.http.HttpMethod;
import org.zstack.header.message.APISyncCallMessage;
import org.zstack.header.rest.RestRequest;
import org.zstack.header.vm.MetadataImpact;

@RestRequest(
        path = "/spice/certificates",
        method = HttpMethod.GET,
        responseClass = APIGetSpiceCertificatesReply.class
)
@MetadataImpact(MetadataImpact.Impact.NONE)
public class APIGetSpiceCertificatesMsg extends APISyncCallMessage {

    public static APIGetSpiceCertificatesMsg __example__() {
        APIGetSpiceCertificatesMsg msg = new APIGetSpiceCertificatesMsg();
        return msg;
    }
}
