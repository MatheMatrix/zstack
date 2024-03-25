package org.zstack.header.vm;

import org.springframework.http.HttpMethod;
import org.zstack.header.cluster.ClusterVO;
import org.zstack.header.configuration.InstanceOfferingVO;
import org.zstack.header.host.HostVO;
import org.zstack.header.identity.Action;
import org.zstack.header.message.APICreateMessage;
import org.zstack.header.message.APIEvent;
import org.zstack.header.message.APIMessage;
import org.zstack.header.message.APIParam;
import org.zstack.header.network.l3.L3NetworkVO;
import org.zstack.header.other.APIAuditor;
import org.zstack.header.other.APIMultiAuditor;
import org.zstack.header.rest.APINoSee;
import org.zstack.header.rest.RestRequest;
import org.zstack.header.tag.TagResourceType;
import org.zstack.header.zone.ZoneVO;

import java.util.ArrayList;
import java.util.List;

import static org.zstack.utils.CollectionDSL.list;

@TagResourceType(VmInstanceVO.class)
@Action(category = VmInstanceConstant.ACTION_CATEGORY)
@RestRequest(
        path = "/vm-instances/{vmInstanceTemplateUuid}/create-vm-from-vmInstanceTemplate",
        method = HttpMethod.POST,
        responseClass = APICreateVmInstanceFromVmInstanceTemplateEvent.class,
        parameterName = "params"
)
public class APICreateVmInstanceFromVmInstanceTemplateMsg extends APICreateMessage implements APIMultiAuditor, NewVmInstanceMessage2 {
    @APIParam(resourceType = VmTemplateVO.class, checkAccount = true, operationTarget = true)
    private String vmInstanceTemplateUuid;

    @APIParam(maxLength = 255)
    private List<String> names;

    @APIParam(required = false, maxLength = 2048)
    private String description;

    @APIParam(required = false)
    private Integer cpuNum;

    @APIParam(required = false)
    private Long memorySize;

    @APIParam(required = false, numberRange = {0, Long.MAX_VALUE})
    private Long reservedMemorySize;

    @APIParam(required = false, resourceType = L3NetworkVO.class, checkAccount = true)
    private List<String> l3NetworkUuids;

    @APIParam(required = false)
    private String defaultL3NetworkUuid;

    @APIParam(required = false)
    private String vmNicParams;

    @APIParam(required = false, validValues = {"InstantStart", "CreateStopped"})
    private String strategy = VmCreationStrategy.InstantStart.toString();

    @APIParam(required = false)
    private List<APICreateVmInstanceMsg.DiskAO> diskAOs;

    @APIParam(required = false)
    private List<String> sshKeyPairUuids;

    @APIParam(required = false, resourceType = ZoneVO.class)
    private String zoneUuid;

    @APIParam(required = false, resourceType = ClusterVO.class)
    private String clusterUuid;

    @APIParam(required = false, resourceType = HostVO.class)
    private String hostUuid;

    @APIParam(required = false, validValues = {"UserVm", "ApplianceVm"})
    private String type;

    @APIParam(required = false, resourceType = InstanceOfferingVO.class, checkAccount = true)
    private String instanceOfferingUuid;

    @APINoSee
    private String name;

    @APINoSee
    VmTemplateVO vmTemplateVO;

    @APINoSee
    private VmInstanceInventory vmInstanceInventory;

    public String getVmInstanceTemplateUuid() {
        return vmInstanceTemplateUuid;
    }

    public void setVmInstanceTemplateUuid(String vmInstanceTemplateUuid) {
        this.vmInstanceTemplateUuid = vmInstanceTemplateUuid;
    }

    public List<String> getNames() {
        return names;
    }

    public void setNames(List<String> names) {
        this.names = names;
    }

    @Override
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public Integer getCpuNum() {
        return cpuNum;
    }

    @Override
    public void setCpuNum(Integer cpuNum) {
        this.cpuNum = cpuNum;
    }

    @Override
    public Long getMemorySize() {
        return memorySize;
    }

    @Override
    public void setMemorySize(Long memorySize) {
        this.memorySize = memorySize;
    }

    @Override
    public Long getReservedMemorySize() {
        return reservedMemorySize;
    }

    @Override
    public void setReservedMemorySize(Long reservedMemorySize) {
        this.reservedMemorySize = reservedMemorySize;
    }

    @Override
    public List<String> getL3NetworkUuids() {
        return l3NetworkUuids;
    }

    public void setL3NetworkUuids(List<String> l3NetworkUuids) {
        this.l3NetworkUuids = l3NetworkUuids;
    }

    @Override
    public String getDefaultL3NetworkUuid() {
        return defaultL3NetworkUuid;
    }

    @Override
    public void setDefaultL3NetworkUuid(String defaultL3NetworkUuid) {
        this.defaultL3NetworkUuid = defaultL3NetworkUuid;
    }

    @Override
    public String getVmNicParams() {
        return vmNicParams;
    }

    public void setVmNicParams(String vmNicParams) {
        this.vmNicParams = vmNicParams;
    }

    @Override
    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public List<APICreateVmInstanceMsg.DiskAO> getDiskAOs() {
        return diskAOs;
    }

    public void setDiskAOs(List<APICreateVmInstanceMsg.DiskAO> diskAOs) {
        this.diskAOs = diskAOs;
    }

    public List<String> getSshKeyPairUuids() {
        return sshKeyPairUuids;
    }

    public void setSshKeyPairUuids(List<String> sshKeyPairUuids) {
        this.sshKeyPairUuids = sshKeyPairUuids;
    }

    @Override
    public String getZoneUuid() {
        return zoneUuid;
    }

    @Override
    public void setZoneUuid(String zoneUuid) {
        this.zoneUuid = zoneUuid;
    }

    @Override
    public String getClusterUuid() {
        return clusterUuid;
    }

    @Override
    public void setClusterUuid(String clusterUuid) {
        this.clusterUuid = clusterUuid;
    }

    @Override
    public String getHostUuid() {
        return hostUuid;
    }

    public void setHostUuid(String hostUuid) {
        this.hostUuid = hostUuid;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public void setType(String type) {
        this.type = type;
    }

    @Override
    public String getInstanceOfferingUuid() {
        return instanceOfferingUuid;
    }

    public void setInstanceOfferingUuid(String instanceOfferingUuid) {
        this.instanceOfferingUuid = instanceOfferingUuid;
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public VmTemplateVO getVmTemplateVO() {
        return vmTemplateVO;
    }

    public void setVmTemplateVO(VmTemplateVO vmTemplateVO) {
        this.vmTemplateVO = vmTemplateVO;
    }

    public VmInstanceInventory getVmInstanceInventory() {
        return vmInstanceInventory;
    }

    public void setVmInstanceInventory(VmInstanceInventory vmInstanceInventory) {
        this.vmInstanceInventory = vmInstanceInventory;
    }

    @Override
    public List<APIAuditor.Result> multiAudit(APIMessage msg, APIEvent rsp) {
        if (!rsp.isSuccess()) {
            return null;
        }

        List<APIAuditor.Result> res = new ArrayList<>();
        APICreateVmInstanceFromVmInstanceTemplateEvent evt = (APICreateVmInstanceFromVmInstanceTemplateEvent) rsp;
//        evt.getResult().getInventories().stream().filter(i -> i.getInventory() != null)
//                .forEach(i -> res.add(new APIAuditor.Result(i.getInventory().getUuid(), VmInstanceVO.class)));
        return res;
    }

    public static APICreateVmInstanceFromVmInstanceTemplateMsg __example__() {
        APICreateVmInstanceFromVmInstanceTemplateMsg msg = new APICreateVmInstanceFromVmInstanceTemplateMsg();
        msg.setNames(list("vm1", "vm2"));
        msg.setStrategy("InstantStart");
        return msg;
    }
}
