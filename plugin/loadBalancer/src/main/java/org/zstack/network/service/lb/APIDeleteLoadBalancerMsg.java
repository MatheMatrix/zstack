package org.zstack.network.service.lb;

import org.springframework.http.HttpMethod;
import org.zstack.header.message.APIDeleteMessage;
import org.zstack.header.message.APIParam;
import org.zstack.header.rest.RestRequest;
import org.zstack.header.vm.MetadataImpact;

/**
 * Created by frank on 8/8/2015.
 */
@RestRequest(
        path = "/load-balancers/{uuid}",
        method = HttpMethod.DELETE,
        responseClass = APIDeleteLoadBalancerEvent.class
)
@MetadataImpact(MetadataImpact.Impact.NONE)
public class APIDeleteLoadBalancerMsg extends APIDeleteMessage implements LoadBalancerMessage {
    @APIParam(resourceType = LoadBalancerVO.class, successIfResourceNotExisting = true)
    private String uuid;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    @Override
    public String getLoadBalancerUuid() {
        return uuid;
    }
 
    public static APIDeleteLoadBalancerMsg __example__() {
        APIDeleteLoadBalancerMsg msg = new APIDeleteLoadBalancerMsg();
        msg.setUuid(uuid());
        return msg;
    }
}
