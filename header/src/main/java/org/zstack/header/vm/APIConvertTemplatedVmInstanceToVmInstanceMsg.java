package org.zstack.header.vm;

import org.springframework.http.HttpMethod;
import org.zstack.header.identity.Action;
import org.zstack.header.message.APIEvent;
import org.zstack.header.message.APIMessage;
import org.zstack.header.message.APIParam;
import org.zstack.header.other.APIAuditor;
import org.zstack.header.rest.APINoSee;
import org.zstack.header.rest.RestRequest;

@Action(category = VmInstanceConstant.ACTION_CATEGORY)
@RestRequest(
        path = "/vm-instances/{templatedVmInstanceUuid}/convert-to-vmInstance",
        method = HttpMethod.POST,
        responseClass = APIConvertTemplatedVmInstanceToVmInstanceEvent.class,
        parameterName = "params"
)
public class APIConvertTemplatedVmInstanceToVmInstanceMsg extends APIMessage implements VmInstanceMessage, APIAuditor {
    @APIParam(resourceType = TemplatedVmInstanceVO.class)
    private String templatedVmInstanceUuid;

    @APIParam(maxLength = 255, required = true)
    private String name;

    @APINoSee
    private String vmInstanceUuid;

    public String getTemplatedVmInstanceUuid() {
        return templatedVmInstanceUuid;
    }

    public void setTemplatedVmInstanceUuid(String templatedVmInstanceUuid) {
        this.templatedVmInstanceUuid = templatedVmInstanceUuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getVmInstanceUuid() {
        return vmInstanceUuid;
    }

    public void setVmInstanceUuid(String vmInstanceUuid) {
        this.vmInstanceUuid = vmInstanceUuid;
    }

    public static APIConvertTemplatedVmInstanceToVmInstanceMsg __example__() {
        APIConvertTemplatedVmInstanceToVmInstanceMsg msg = new APIConvertTemplatedVmInstanceToVmInstanceMsg();
        msg.setTemplatedVmInstanceUuid(uuid());
        msg.setName("test-vm");
        return msg;
    }

    @Override
    public Result audit(APIMessage msg, APIEvent rsp) {
        return new Result(rsp.isSuccess() ? ((APIConvertTemplatedVmInstanceToVmInstanceEvent)rsp).getInventory().getUuid() : "", VmInstanceVO.class);
    }
}
