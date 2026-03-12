package org.zstack.header.vm.metadata;

import org.springframework.http.HttpMethod;
import org.zstack.header.message.APICreateMessage;
import org.zstack.header.message.APIParam;
import org.zstack.header.message.DefaultTimeout;
import org.zstack.header.rest.RestRequest;
import org.zstack.header.storage.primary.PrimaryStorageVO;
import org.zstack.header.vm.VmInstanceVO;
import org.zstack.header.zone.ZoneVO;
import org.zstack.header.cluster.ClusterVO;
import org.zstack.header.host.HostVO;
import org.zstack.header.tag.TagResourceType;

import java.util.concurrent.TimeUnit;

@TagResourceType(VmInstanceVO.class)
@RestRequest(
        path = "/vm-instances/metadata/register",
        method = HttpMethod.POST,
        responseClass = APIRegisterVmInstanceFromMetadataEvent.class,
        parameterName = "params"
)
@DefaultTimeout(timeunit = TimeUnit.HOURS, value = 3)
public class APIRegisterVmInstanceFromMetadataMsg extends APICreateMessage {
    @APIParam(required = false)
    private String metadataContent;

    @APIParam
    private String metadataPath;

    @APIParam(resourceType = PrimaryStorageVO.class)
    private String targetPrimaryStorageUuid;

    @APIParam(resourceType = ZoneVO.class)
    private String zoneUuid;

    @APIParam(resourceType = ClusterVO.class)
    private String clusterUuid;

    @APIParam(resourceType = HostVO.class)
    private String hostUuid;

    @APIParam(required = false, maxLength = 255)
    private String name;

    public String getMetadataContent() {
        return metadataContent;
    }

    public void setMetadataContent(String metadataContent) {
        this.metadataContent = metadataContent;
    }

    public String getMetadataPath() {
        return metadataPath;
    }

    public void setMetadataPath(String metadataPath) {
        this.metadataPath = metadataPath;
    }

    public String getTargetPrimaryStorageUuid() {
        return targetPrimaryStorageUuid;
    }

    public void setTargetPrimaryStorageUuid(String targetPrimaryStorageUuid) {
        this.targetPrimaryStorageUuid = targetPrimaryStorageUuid;
    }

    public String getZoneUuid() {
        return zoneUuid;
    }

    public void setZoneUuid(String zoneUuid) {
        this.zoneUuid = zoneUuid;
    }

    public String getClusterUuid() {
        return clusterUuid;
    }

    public void setClusterUuid(String clusterUuid) {
        this.clusterUuid = clusterUuid;
    }

    public String getHostUuid() {
        return hostUuid;
    }

    public void setHostUuid(String hostUuid) {
        this.hostUuid = hostUuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public static APIRegisterVmInstanceFromMetadataMsg __example__() {
        APIRegisterVmInstanceFromMetadataMsg msg = new APIRegisterVmInstanceFromMetadataMsg();
        msg.metadataPath = "/vm-metadata/vm-uuid/metadata.json";
        msg.targetPrimaryStorageUuid = uuid();
        msg.zoneUuid = uuid();
        msg.clusterUuid = uuid();
        msg.hostUuid = uuid();
        msg.name = "my-restored-vm";
        return msg;
    }
}
