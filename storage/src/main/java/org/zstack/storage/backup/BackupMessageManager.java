package org.zstack.storage.backup;

import org.zstack.header.message.NeedReplyMessage;

public interface BackupMessageManager {
    boolean routeToQueue(NeedReplyMessage msg, String vmUuid, String targetServiceId);
}
