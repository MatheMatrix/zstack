package org.zstack.identity.imports.source;

import org.zstack.header.errorcode.ErrorCode;
import org.zstack.identity.imports.header.DeleteAccountSpec;
import org.zstack.identity.imports.header.UnbindThirdPartyAccountResult;

public class UnbindThirdPartyAccountsContext {
    public DeleteAccountSpec spec;
    public ErrorCode errorForDeleteAccount;
    public String sourceUuid;

    public UnbindThirdPartyAccountResult makeResult() {
        UnbindThirdPartyAccountResult result = new UnbindThirdPartyAccountResult();
        result.setAccountUuid(spec.getAccountUuid());
        result.setErrorForDeleteAccount(errorForDeleteAccount);
        return result;
    }
}
