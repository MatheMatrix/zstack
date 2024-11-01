package org.zstack.header.volume;

import org.springframework.http.HttpMethod;
import org.zstack.header.cluster.ClusterVO;
import org.zstack.header.message.APIParam;
import org.zstack.header.message.APISyncCallMessage;
import org.zstack.header.rest.RestRequest;

@RestRequest(
        path = "/volumes/batch-sync-volumes",
        method = HttpMethod.POST,
        responseClass = APIBatchSyncVolumeSizeReply.class
)
public class APIBatchSyncVolumeSizeMsg extends APISyncCallMessage {
    @APIParam(resourceType = ClusterVO.class)
    private String clusterUuid;

    public void setClusterUuid(String clusterUuid) {
        this.clusterUuid = clusterUuid;
    }

    public String getClusterUuid() {
        return clusterUuid;
    }
}
