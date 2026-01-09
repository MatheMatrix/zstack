package org.zstack.header.vm;

import org.springframework.http.HttpMethod;
import org.zstack.header.message.APIParam;
import org.zstack.header.message.APISyncCallMessage;
import org.zstack.header.rest.RestRequest;
import org.zstack.header.cluster.ClusterVO;
import org.zstack.header.storage.primary.PrimaryStorageVO;
import org.zstack.header.zone.ZoneVO;

@RestRequest(
        path = "/vm-instances/metadata/pre-check",
        method = HttpMethod.PUT,
        responseClass = APIPreCheckVmMetadataRegistrationReply.class,
        isAction = true
)
public class APIPreCheckVmMetadataRegistrationMsg extends APISyncCallMessage {
    @APIParam
    private String metadataContent;

    @APIParam(resourceType = PrimaryStorageVO.class)
    private String targetPrimaryStorageUuid;

    @APIParam(required = false, resourceType = ZoneVO.class)
    private String zoneUuid;

    @APIParam(required = false, resourceType = ClusterVO.class)
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

    public static APIPreCheckVmMetadataRegistrationMsg __example__() {
        APIPreCheckVmMetadataRegistrationMsg msg = new APIPreCheckVmMetadataRegistrationMsg();
        msg.metadataContent = "{\"schemaVersion\":\"1.0\",\"vmUuid\":\"...\"}";
        msg.targetPrimaryStorageUuid = uuid();
        return msg;
    }
}
