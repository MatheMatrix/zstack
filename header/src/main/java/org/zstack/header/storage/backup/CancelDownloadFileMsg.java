package org.zstack.header.storage.backup;

import org.zstack.header.message.CancelMessage;

public class CancelDownloadFileMsg extends CancelMessage implements BackupStorageMessage {
    private String backupStorageUuid;
    private String taskUuid;

    @Override
    public String getBackupStorageUuid() {
        return backupStorageUuid;
    }

    public void setBackupStorageUuid(String backupStorageUuid) {
        this.backupStorageUuid = backupStorageUuid;
    }

    public String getTaskUuid() {
        return taskUuid;
    }

    public void setTaskUuid(String taskUuid) {
        this.taskUuid = taskUuid;
    }
}
