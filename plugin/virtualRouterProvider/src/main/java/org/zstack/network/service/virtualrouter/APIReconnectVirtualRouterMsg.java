package org.zstack.network.service.virtualrouter;

import org.springframework.http.HttpMethod;
import org.zstack.header.message.APIMessage;
import org.zstack.header.message.APIParam;
import org.zstack.header.rest.RestRequest;
import org.zstack.header.vm.VmInstanceMessage;
import org.zstack.header.vm.MetadataImpact;

/**
 */
@RestRequest(
        path = "/vm-instances/appliances/virtual-routers/{vmInstanceUuid}/actions",
        method = HttpMethod.PUT,
        responseClass = APIReconnectVirtualRouterEvent.class,
        isAction = true
)
@MetadataImpact(MetadataImpact.Impact.NONE)
public class APIReconnectVirtualRouterMsg extends APIMessage implements VmInstanceMessage {
    @APIParam(resourceType = VirtualRouterVmVO.class)
    private String vmInstanceUuid;

    @Override
    public String getVmInstanceUuid() {
        return vmInstanceUuid;
    }

    public void setVmInstanceUuid(String vmInstanceUuid) {
        this.vmInstanceUuid = vmInstanceUuid;
    }
 
    public static APIReconnectVirtualRouterMsg __example__() {
        APIReconnectVirtualRouterMsg msg = new APIReconnectVirtualRouterMsg();

        msg.setVmInstanceUuid(uuid());

        return msg;
    }
}
