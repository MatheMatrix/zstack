package org.zstack.identity.imports.entity;

import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.identity.AccountInventory;

public class ImportAccountResult {
    private AccountInventory account;
    private AccountSourceRefVO ref;
    private ErrorCode errorForCreatingAccount;

    public AccountInventory getAccount() {
        return account;
    }

    public void setAccount(AccountInventory account) {
        this.account = account;
    }

    public AccountSourceRefVO getRef() {
        return ref;
    }

    public void setRef(AccountSourceRefVO ref) {
        this.ref = ref;
    }

    public ErrorCode getErrorForCreatingAccount() {
        return errorForCreatingAccount;
    }

    public void setErrorForCreatingAccount(ErrorCode errorForCreatingAccount) {
        this.errorForCreatingAccount = errorForCreatingAccount;
    }
}
