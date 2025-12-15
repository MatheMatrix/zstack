package org.zstack.header.image;

import org.zstack.header.log.NoLogging;
import org.zstack.header.message.MessageReply;

public class UploadFileToBackupStorageHostReply extends MessageReply {
    private String md5sum;
    private long size;
    @NoLogging(type = NoLogging.Type.Uri)
    private String directUploadUrl;

    public String getMd5sum() {
        return md5sum;
    }

    public void setMd5sum(String md5sum) {
        this.md5sum = md5sum;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getDirectUploadUrl() {
        return directUploadUrl;
    }

    public void setDirectUploadUrl(String directUploadUrl) {
        this.directUploadUrl = directUploadUrl;
    }
}
