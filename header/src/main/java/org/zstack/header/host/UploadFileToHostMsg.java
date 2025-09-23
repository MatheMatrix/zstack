package org.zstack.header.host;

import org.zstack.header.log.NoLogging;
import org.zstack.header.message.NeedReplyMessage;

public class UploadFileToHostMsg extends NeedReplyMessage implements HostMessage {
    private String uuid;
    @NoLogging(type = NoLogging.Type.Uri)
    private String url;
    private String installPath;

    public boolean needTrack() {
        return url != null && url.startsWith("upload://");
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
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

    @Override
    public String getHostUuid() {
        return uuid;
    }
}
