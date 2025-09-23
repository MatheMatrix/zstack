package org.zstack.header.host;

import org.zstack.header.message.CancelMessage;

public class GetSoftwarePackageDownloadProgressMsg extends CancelMessage implements HostMessage {
    private String hostUuid;
    private String hostname;
    private String taskUuid;

    @Override
    public String getHostUuid() {
        return hostUuid;
    }

    public void setHostUuid(String hostUuid) {
        this.hostUuid = hostUuid;
    }

    public String getTaskUuid() {
        return taskUuid;
    }

    public void setTaskUuid(String taskUuid) {
        this.taskUuid = taskUuid;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }
}
