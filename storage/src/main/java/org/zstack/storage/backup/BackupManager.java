package org.zstack.storage.backup;

import org.zstack.header.message.NeedReplyMessage;
import org.zstack.header.storage.backup.BackupOperationConflictChecker;

public interface BackupManager {
    boolean routeToQueue(NeedReplyMessage msg, String vmUuid, String backendServiceId,
                         BackupOperationConflictChecker.Operation operation, String resourceUuid);
}
