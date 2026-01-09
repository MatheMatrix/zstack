package org.zstack.header.storage.primary;

import org.springframework.http.HttpMethod;
import org.zstack.header.cluster.ClusterVO;
import org.zstack.header.host.HostVO;
import org.zstack.header.message.APIMessage;
import org.zstack.header.message.APIParam;
import org.zstack.header.rest.RestRequest;
import org.zstack.header.zone.ZoneVO;

@RestRequest(
        path = "/vm-instances/register",
        method = HttpMethod.POST,
        responseClass = APIRegisterVmInstanceEvent.class,
        parameterName = "params"
)
public class APIRegisterVmInstanceMsg extends APIMessage implements PrimaryStorageMessage {
    @APIParam(resourceType = ZoneVO.class)
    private String zoneUuid;
    @APIParam(resourceType = ClusterVO.class)
    private String clusterUuid;
    @APIParam(resourceType = PrimaryStorageVO.class)
    private String primaryStorageUuid;
    @APIParam(required = false, resourceType = HostVO.class)
    private String hostUuid;
    @APIParam()
    private String metadataPath;

    @Override
    public String getPrimaryStorageUuid() {
        return primaryStorageUuid;
    }

    public void setPrimaryStorageUuid(String primaryStorageUuid) {
        this.primaryStorageUuid = primaryStorageUuid;
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

    public String getMetadataPath() {
        return metadataPath;
    }

    public void setMetadataPath(String metadataPath) {
        this.metadataPath = metadataPath;
    }

    public static APIRegisterVmInstanceMsg __example__() {
        APIRegisterVmInstanceMsg msg = new APIRegisterVmInstanceMsg();
        return msg;
    }
}
