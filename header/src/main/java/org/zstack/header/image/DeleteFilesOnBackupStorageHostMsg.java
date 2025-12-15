package org.zstack.header.image;

import org.zstack.header.message.NeedReplyMessage;
import org.zstack.header.storage.backup.BackupStorageMessage;

import java.util.List;

public class DeleteFilesOnBackupStorageHostMsg extends NeedReplyMessage implements BackupStorageMessage {
    private String backupStorageUuid;
    private List<String> filesPath;

    @Override
    public String getBackupStorageUuid() {
        return backupStorageUuid;
    }

    public void setBackupStorageUuid(String backupStorageUuid) {
        this.backupStorageUuid = backupStorageUuid;
    }

    public List<String> getFilesPath() {
        return filesPath;
    }

    public void setFilesPath(List<String> filesPath) {
        this.filesPath = filesPath;
    }
}
