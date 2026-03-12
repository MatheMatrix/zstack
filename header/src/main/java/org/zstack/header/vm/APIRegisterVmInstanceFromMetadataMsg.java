package org.zstack.header.vm;

import org.springframework.http.HttpMethod;
import org.zstack.header.message.APICreateMessage;
import org.zstack.header.message.APIParam;
import org.zstack.header.message.DefaultTimeout;
import org.zstack.header.rest.RestRequest;
import org.zstack.header.storage.primary.PrimaryStorageVO;
import org.zstack.header.zone.ZoneVO;
import org.zstack.header.cluster.ClusterVO;
import org.zstack.header.tag.TagResourceType;

import java.util.concurrent.TimeUnit;
import org.zstack.header.vm.MetadataImpact;

@TagResourceType(VmInstanceVO.class)
@RestRequest(
        path = "/vm-instances/metadata/register",
        method = HttpMethod.POST,
        responseClass = APIRegisterVmInstanceFromMetadataEvent.class,
        parameterName = "params"
)
@DefaultTimeout(timeunit = TimeUnit.HOURS, value = 3)
@MetadataImpact(MetadataImpact.Impact.NONE)
public class APIRegisterVmInstanceFromMetadataMsg extends APICreateMessage {
    @APIParam
    private String metadataContent;

    @APIParam(resourceType = PrimaryStorageVO.class)
    private String targetPrimaryStorageUuid;

    @APIParam(resourceType = ZoneVO.class)
    private String zoneUuid;

    @APIParam(resourceType = ClusterVO.class)
    private String clusterUuid;

    @APIParam(required = false)
    private Boolean forceVersionMismatch;

    public String getMetadataContent() {
        return metadataContent;
    }

    public void setMetadataContent(String metadataContent) {
        this.metadataContent = metadataContent;
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

    public Boolean getForceVersionMismatch() {
        return forceVersionMismatch;
    }

    public void setForceVersionMismatch(Boolean forceVersionMismatch) {
        this.forceVersionMismatch = forceVersionMismatch;
    }

    public static APIRegisterVmInstanceFromMetadataMsg __example__() {
        APIRegisterVmInstanceFromMetadataMsg msg = new APIRegisterVmInstanceFromMetadataMsg();
        msg.metadataContent = "{\"schemaVersion\":\"1.0\",\"vmUuid\":\"...\"}";
        msg.targetPrimaryStorageUuid = uuid();
        msg.zoneUuid = uuid();
        msg.clusterUuid = uuid();
        msg.forceVersionMismatch = false;
        return msg;
    }
}
