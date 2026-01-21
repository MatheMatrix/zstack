package org.zstack.header.vm;

import org.springframework.http.HttpMethod;
import org.zstack.header.cluster.ClusterVO;
import org.zstack.header.host.HostVO;
import org.zstack.header.message.APIMessage;
import org.zstack.header.message.APIParam;
import org.zstack.header.network.l3.L3NetworkVO;
import org.zstack.header.rest.RestRequest;

import java.util.List;

/**
 * Created by frank on 11/12/2015.
 */
@RestRequest(
        path = "/vm-instances/{uuid}/actions",
        isAction = true,
        method = HttpMethod.PUT,
        responseClass = APIRecoverVmInstanceEvent.class
)
public class APIRegVmInstanceMsg extends APIMessage {
    @APIParam(resourceType = ClusterVO.class)
    private String clusterUuid;
    @APIParam(resourceType = HostVO.class)
    private String hostUuid;



    private String defaultL3NetworkUuid;

    @APIParam(resourceType = L3NetworkVO.class, required = false)
    private List<String> l3NetworkUuids;

    @APIParam(required = false)
    private String vmNicParams;
 
    public static APIRegVmInstanceMsg __example__() {
        APIRegVmInstanceMsg msg = new APIRegVmInstanceMsg();
        return msg;
    }
}
