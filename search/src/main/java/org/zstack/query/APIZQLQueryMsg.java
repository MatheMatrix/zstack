package org.zstack.query;

import org.springframework.http.HttpMethod;
import org.zstack.header.message.APISyncCallMessage;
import org.zstack.header.rest.RestRequest;
import org.zstack.header.vm.MetadataImpact;

@RestRequest(path = "/zql", method = HttpMethod.GET, responseClass = APIZQLQueryReply.class)
@MetadataImpact(MetadataImpact.Impact.NONE)
public class APIZQLQueryMsg extends APISyncCallMessage {
    private String zql;

    public static APIZQLQueryMsg __example__() {
        APIZQLQueryMsg ret = new APIZQLQueryMsg();
        return ret;
    }

    public String getZql() {
        return zql;
    }

    public void setZql(String zql) {
        this.zql = zql;
    }
}
