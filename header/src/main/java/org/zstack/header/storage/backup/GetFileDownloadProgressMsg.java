package org.zstack.header.storage.backup;

import org.zstack.header.message.NeedReplyMessage;

public class GetFileDownloadProgressMsg extends NeedReplyMessage implements BackupStorageMessage {
    private String backupStorageUuid;
    private String backupStorageHostUuid;
    private String taskUuid;
    private String hostname;

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
