package org.zstack.sdnController.header;

import org.springframework.http.HttpMethod;
import org.zstack.header.cluster.ClusterVO;
import org.zstack.header.host.HostVO;
import org.zstack.header.identity.Action;
import org.zstack.header.message.APIMessage;
import org.zstack.header.message.APIParam;
import org.zstack.header.rest.RestRequest;

import java.util.List;

@Action(category = SdnControllerConstant.ACTION_CATEGORY)
@RestRequest(
        path = "/sdn-controllers/{sdnControllerUuid}/hosts/{hostUuid}",
        method = HttpMethod.POST,
        responseClass = APISdnControllerAddHostEvent.class,
        parameterName = "null"
)
public class APISdnControllerAddHostMsg extends APIMessage {
    /**
     * @desc l2Network uuid
     */
    @APIParam(resourceType = SdnControllerVO.class, checkAccount = true, operationTarget = true)
    private String sdnControllerUuid;
    /**
     * @desc cluster uuid. See :ref:`ClusterInventory`
     */
    @APIParam(resourceType = HostVO.class)
    private String hostUuid;

    /**
     * @desc vSwitch type
     */
    @APIParam(required = false, validValues = {"OvsKernel", "OvsDpdk", "sriov"})
    private String vSwitchType = "OvsDpdk";

    /**
     * @desc physical nics used by vSwitchType
     */
    @APIParam(nonempty = true)
    private List<String> nicNames;

    /**
     * @desc physical nics used by vSwitchType
     */
    @APIParam(required = false)
    private String vtepIp;


    public String getSdnControllerUuid() {
        return sdnControllerUuid;
    }

    public void setSdnControllerUuid(String sdnControllerUuid) {
        this.sdnControllerUuid = sdnControllerUuid;
    }

    public String getHostUuid() {
        return hostUuid;
    }

    public void setHostUuid(String hostUuid) {
        this.hostUuid = hostUuid;
    }

    public String getvSwitchType() {
        return vSwitchType;
    }

    public void setvSwitchType(String vSwitchType) {
        this.vSwitchType = vSwitchType;
    }

    public List<String> getNicNames() {
        return nicNames;
    }

    public void setNicNames(List<String> nicNames) {
        this.nicNames = nicNames;
    }

    public String getVtepIp() {
        return vtepIp;
    }

    public void setVtepIp(String vtepIp) {
        this.vtepIp = vtepIp;
    }

    public static APISdnControllerAddHostMsg __example__() {
        APISdnControllerAddHostMsg msg = new APISdnControllerAddHostMsg();

        msg.setSdnControllerUuid(uuid());
        msg.setHostUuid(uuid());

        return msg;
    }
}
