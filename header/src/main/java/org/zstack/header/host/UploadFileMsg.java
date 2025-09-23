package org.zstack.header.host;

import org.zstack.header.log.NoLogging;
import org.zstack.header.message.NeedReplyMessage;

public class UploadFileMsg extends NeedReplyMessage implements HostMessage {
    private String hostUuid;
    @NoLogging(type = NoLogging.Type.Uri)
    private String url;
    private String installPath;

    public boolean needTrack() {
        return url != null && url.startsWith("upload://");
    }

    @Override
    public String getHostUuid() {
        return hostUuid;
    }

    public void setHostUuid(String hostUuid) {
        this.hostUuid = hostUuid;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getInstallPath() {
        return installPath;
    }

    public void setInstallPath(String installPath) {
        this.installPath = installPath;
    }
}
