package org.zstack.header.storage.backup;

import org.zstack.header.message.NeedReplyMessage;

import java.util.List;

public class DeleteFilesOnBackupStorageHostMsg extends NeedReplyMessage implements BackupStorageMessage {
    private String backupStorageUuid;
    private String backupStorageHostUuid;
    private List<String> filesPath;

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

    public List<String> getFilesPath() {
        return filesPath;
    }

    public void setFilesPath(List<String> filesPath) {
        this.filesPath = filesPath;
    }
}
