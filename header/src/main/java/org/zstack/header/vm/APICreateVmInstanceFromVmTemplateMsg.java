package org.zstack.header.vm;

import org.springframework.http.HttpMethod;
import org.zstack.header.host.HostVO;
import org.zstack.header.message.APICreateMessage;
import org.zstack.header.message.APIEvent;
import org.zstack.header.message.APIMessage;
import org.zstack.header.message.APIParam;
import org.zstack.header.other.APIAuditor;
import org.zstack.header.rest.RestRequest;

@RestRequest(
        path = "/vm-instances/VmInstanceTemplate/{VmInstanceTemplateUuid}",
        method = HttpMethod.POST,
        responseClass = APICreateVmInstanceFromVmTemplateEvent.class,
        parameterName = "params"
)
public class APICreateVmInstanceFromVmTemplateMsg extends APICreateMessage implements APIAuditor {
    @APIParam(resourceType = VmInstanceTemplateVO.class, checkAccount = true)
    private String vmInstanceTemplateUuid;

    @APIParam(resourceType = HostVO.class, checkAccount = true)
    private String hostUuid;

    public String getVmInstanceTemplateUuid() {
        return vmInstanceTemplateUuid;
    }

    public void setVmInstanceTemplateUuid(String vmInstanceTemplateUuid) {
        this.vmInstanceTemplateUuid = vmInstanceTemplateUuid;
    }

    public String getHostUuid() {
        return hostUuid;
    }

    public void setHostUuid(String hostUuid) {
        this.hostUuid = hostUuid;
    }

    @Override
    public Result audit(APIMessage msg, APIEvent rsp) {
        return new Result(rsp.isSuccess() ? ((APICreateVmInstanceFromVmTemplateEvent) rsp).getInventory().getUuid() : "", VmInstanceVO.class);
    }

    public static APICreateVmInstanceFromVmTemplateMsg __example__() {
        APICreateVmInstanceFromVmTemplateMsg msg = new APICreateVmInstanceFromVmTemplateMsg();
        msg.setVmInstanceTemplateUuid(uuid());
        msg.setHostUuid(uuid());
        return msg;
    }
}
