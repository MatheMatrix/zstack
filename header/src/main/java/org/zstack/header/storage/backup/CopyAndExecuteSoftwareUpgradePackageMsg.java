package org.zstack.header.storage.backup;

import org.zstack.header.log.NoLogging;
import org.zstack.header.message.NeedReplyMessage;

public class CopyAndExecuteSoftwareUpgradePackageMsg extends NeedReplyMessage implements BackupStorageMessage {
    private String backupStorageUuid;
    private String backupStorageHostUuid;
    private String targetFilePath;
    private String dstFilePath;
    private int dstHostSSHPort;
    private String dstHostSSHUser;
    @NoLogging
    private String dstHostSSHPasswd;
    private String dstHostIP;

    @Override
    public String getBackupStorageUuid() {
        return backupStorageUuid;
    }

    public void setBackupStorageUuid(String backupStorageUuid) {
        this.backupStorageUuid = backupStorageUuid;
    }

    public String getBackupStorageHostUuid() {
        return backupStorageHostUuid;
    }

    public void setBackupStorageHostUuid(String backupStorageHostUuid) {
        this.backupStorageHostUuid = backupStorageHostUuid;
    }

    public String getDstFilePath() {
        return dstFilePath;
    }

    public void setDstFilePath(String dstFilePath) {
        this.dstFilePath = dstFilePath;
    }

    public String getTargetFilePath() {
        return targetFilePath;
    }

    public void setTargetFilePath(String targetFilePath) {
        this.targetFilePath = targetFilePath;
    }

    public int getDstHostSSHPort() {
        return dstHostSSHPort;
    }

    public void setDstHostSSHPort(int dstHostSSHPort) {
        this.dstHostSSHPort = dstHostSSHPort;
    }

    public String getDstHostSSHUser() {
        return dstHostSSHUser;
    }

    public void setDstHostSSHUser(String dstHostSSHUser) {
        this.dstHostSSHUser = dstHostSSHUser;
    }

    public String getDstHostSSHPasswd() {
        return dstHostSSHPasswd;
    }

    public void setDstHostSSHPasswd(String dstHostSSHPasswd) {
        this.dstHostSSHPasswd = dstHostSSHPasswd;
    }

    public String getDstHostIP() {
        return dstHostIP;
    }

    public void setDstHostIP(String dstHostIP) {
        this.dstHostIP = dstHostIP;
    }
}
