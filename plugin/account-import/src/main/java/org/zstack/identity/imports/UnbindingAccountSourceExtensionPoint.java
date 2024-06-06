package org.zstack.identity.imports;

import org.zstack.header.errorcode.ErrorCode;
import org.zstack.identity.imports.header.UnbindThirdPartyAccountsSpec;
import org.zstack.identity.imports.source.UnbindThirdPartyAccountsContext;

import java.util.List;

public interface UnbindingAccountSourceExtensionPoint {
    ErrorCode preUnbindingAccountSource(UnbindThirdPartyAccountsSpec spec);

    default void afterUnbindingAccountSource(List<UnbindThirdPartyAccountsContext> contexts) {}
}
