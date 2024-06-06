package org.zstack.identity.imports.header;

import org.zstack.header.errorcode.ErrorCode;
import org.zstack.identity.imports.entity.AccountThirdPartyAccountSourceRefInventory;

public class ImportAccountResult {
    private AccountThirdPartyAccountSourceRefInventory ref;
    private ErrorCode errorForCreatingAccount;

    public AccountThirdPartyAccountSourceRefInventory getRef() {
        return ref;
    }

    public void setRef(AccountThirdPartyAccountSourceRefInventory ref) {
        this.ref = ref;
    }

    public ErrorCode getErrorForCreatingAccount() {
        return errorForCreatingAccount;
    }

    public void setErrorForCreatingAccount(ErrorCode errorForCreatingAccount) {
        this.errorForCreatingAccount = errorForCreatingAccount;
    }
}
