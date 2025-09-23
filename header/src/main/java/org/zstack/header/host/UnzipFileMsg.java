package org.zstack.header.host;

import org.zstack.header.message.CancelMessage;

public class UnzipFileMsg extends CancelMessage implements HostMessage {
    private String hostUuid;
    private String filePath;
    private String unzipFilePath;

    @Override
    public String getHostUuid() {
        return hostUuid;
    }

    public void setHostUuid(String hostUuid) {
        this.hostUuid = hostUuid;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getUnzipFilePath() {
        return unzipFilePath;
    }

    public void setUnzipFilePath(String unzipFilePath) {
        this.unzipFilePath = unzipFilePath;
    }
}
