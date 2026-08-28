package org.zstack.storage.backup;

import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.componentloader.PluginRegistry;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.storage.backup.BackupOperationConflictChecker;

public class BackupOperationConflictManager {
    @Autowired
    private PluginRegistry pluginRgty;

    public ErrorCode check(BackupOperationConflictChecker.Operation operation,
                           String vmUuid, String resourceUuid) {
        if (operation == null || vmUuid == null) {
            return null;
        }

        for (BackupOperationConflictChecker checker :
                pluginRgty.getExtensionList(BackupOperationConflictChecker.class)) {
            ErrorCode error = checker.check(operation, vmUuid, resourceUuid);
            if (error != null) {
                return error;
            }
        }
        return null;
    }
}
