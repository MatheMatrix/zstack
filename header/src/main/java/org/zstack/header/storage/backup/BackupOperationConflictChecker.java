package org.zstack.header.storage.backup;

import org.zstack.header.errorcode.ErrorCode;

public interface BackupOperationConflictChecker {
    enum Operation {
        CREATE_CBT,
        ENABLE_CBT,
        CREATE_CDP,
        ENABLE_CDP,
        CREATE_VM_BACKUP,
        CREATE_VOLUME_BACKUP,
        CREATE_BACKUP_SCHEDULER,
        CREATE_SNAPSHOT_SCHEDULER,
        CREATE_CHAIN_SNAPSHOT,
        DELETE_CHAIN_SNAPSHOT,
        RESIZE_VOLUME
    }

    ErrorCode check(Operation operation, String vmUuid, String resourceUuid);
}
