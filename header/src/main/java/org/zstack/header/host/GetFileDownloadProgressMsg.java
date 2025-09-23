package org.zstack.header.host;

import org.zstack.header.message.CancelMessage;

public class GetFileDownloadProgressMsg extends CancelMessage implements HostMessage {
    private String hostUuid;
    private String apiId;

    @Override
    public String getHostUuid() {
        return hostUuid;
    }

    public void setHostUuid(String hostUuid) {
        this.hostUuid = hostUuid;
    }

    public String getApiId() {
        return apiId;
    }

    public void setApiId(String apiId) {
        this.apiId = apiId;
    }
}
