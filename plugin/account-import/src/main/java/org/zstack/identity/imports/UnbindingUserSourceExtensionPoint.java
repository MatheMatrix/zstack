package org.zstack.identity.imports;

import org.zstack.header.errorcode.ErrorCode;

public interface UnbindingUserSourceExtensionPoint {
    ErrorCode preUnbindingUserSource(String userSourceUuid, String accountUuid);

    default void afterUnbindingUserSource(String userSourceUuid, String accountUuid) {}
}
