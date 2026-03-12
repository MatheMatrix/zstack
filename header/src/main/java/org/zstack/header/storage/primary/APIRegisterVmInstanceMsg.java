package org.zstack.header.storage.primary;

import org.springframework.http.HttpMethod;
import org.zstack.header.cluster.ClusterVO;
import org.zstack.header.host.HostVO;
import org.zstack.header.message.APIMessage;
import org.zstack.header.message.APIParam;
import org.zstack.header.rest.RestRequest;
import org.zstack.header.vm.MetadataImpact;

@RestRequest(
        path = "/vm-instances/register",
        method = HttpMethod.POST,
        responseClass = APIRegisterVmInstanceReply.class,
        parameterName = "params"
)
@MetadataImpact(MetadataImpact.Impact.NONE)
public class APIRegisterVmInstanceMsg extends APIMessage implements PrimaryStorageMessage {
    @APIParam()
    private String metadataPath;
    @APIParam(resourceType = PrimaryStorageVO.class)
    private String primaryStorageUuid;
    @APIParam(resourceType = ClusterVO.class)
    private String clusterUuid;
    @APIParam(required = false, resourceType = HostVO.class)
    private String hostUuid;

    @Override
    public String getPrimaryStorageUuid() {
        return primaryStorageUuid;
    }

    public void setPrimaryStorageUuid(String primaryStorageUuid) {
        this.primaryStorageUuid = primaryStorageUuid;
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
