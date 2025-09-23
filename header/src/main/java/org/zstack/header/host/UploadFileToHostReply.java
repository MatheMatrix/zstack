package org.zstack.header.host;

import org.zstack.header.message.MessageReply;

public class UploadFileToHostReply extends MessageReply {
    private String md5sum;
    private String apiId;
    private String installPath;

    public String getMd5sum() {
        return md5sum;
    }

    public void setMd5sum(String md5sum) {
        this.md5sum = md5sum;
    }

    public String getApiId() {
        return apiId;
    }

    public void setApiId(String apiId) {
        this.apiId = apiId;
    }

    public String getInstallPath() {
        return installPath;
    }

    public void setInstallPath(String installPath) {
        this.installPath = installPath;
    }
}
