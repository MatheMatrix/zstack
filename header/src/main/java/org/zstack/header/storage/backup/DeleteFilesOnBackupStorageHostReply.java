package org.zstack.header.storage.backup;

import org.zstack.header.log.NoLogging;
import org.zstack.header.message.MessageReply;

import java.util.Map;

public class DeleteFilesOnBackupStorageHostReply extends MessageReply {
    private String md5sum;
    private long size;
    @NoLogging(type = NoLogging.Type.Uri)
    private String directUploadUrl;
    private String unzipInstallPath;
    private Map<String, Long> filesSize;

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

    public String getUnzipInstallPath() {
        return unzipInstallPath;
    }

    public void setUnzipInstallPath(String unzipInstallPath) {
        this.unzipInstallPath = unzipInstallPath;
    }

    public Map<String, Long> getFilesSize() {
        return filesSize;
    }

    public void setFilesSize(Map<String, Long> filesSize) {
        this.filesSize = filesSize;
    }
}
