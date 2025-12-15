package org.zstack.header.image;

import org.zstack.header.log.NoLogging;
import org.zstack.header.message.MessageReply;

import java.util.Map;

public class UploadFileToBackupStorageHostReply extends MessageReply {
    private String md5sum;
    private long size;
    @NoLogging(type = NoLogging.Type.Uri)
    private String directUploadUrl;
    private String unzipInstallPath;
    private Map<String, Long> filesSize;

    private String hostname;
    private String sshUsername;
    @NoLogging
    private String sshPassword;
    private Integer sshPort;

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

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getSshUsername() {
        return sshUsername;
    }

    public void setSshUsername(String sshUsername) {
        this.sshUsername = sshUsername;
    }

    public String getSshPassword() {
        return sshPassword;
    }

    public void setSshPassword(String sshPassword) {
        this.sshPassword = sshPassword;
    }

    public Integer getSshPort() {
        return sshPort;
    }

    public void setSshPort(Integer sshPort) {
        this.sshPort = sshPort;
    }
}
