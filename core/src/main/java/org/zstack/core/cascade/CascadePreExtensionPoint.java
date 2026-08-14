package org.zstack.core.cascade;

import org.zstack.header.errorcode.ErrorCode;

public interface CascadePreExtensionPoint {
    ErrorCode beforeCascade(CascadeAction action);

    void afterCascadeFailure(CascadeAction action);
}
