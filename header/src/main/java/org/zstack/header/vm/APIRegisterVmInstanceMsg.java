package org.zstack.header.vm;

import org.springframework.http.HttpMethod;
import org.zstack.header.message.APIMessage;
import org.zstack.header.rest.RestRequest;

@RestRequest(
        path = "/vm-instances/{uuid}/register",
        method = HttpMethod.POST,
        responseClass = APIRegisterVmInstanceEvent.class
)
public class APIRegisterVmInstanceMsg extends APIMessage {
    private String primaryStorageUuid;
    private String clusterUuid;
    private String hostUuid;
    private String metadataPath;

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
