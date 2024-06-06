package org.zstack.identity.imports.entity;

import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.identity.AccountInventory;

/**
 * Created by Wenhao.Zhang on 2024/05/31
 */
public class AccountImportChunk {
    public AccountInventory account;
    public transient ImportAccountBatch.AccountSpec spec;
    public ImportAccountRefVO ref;
    public ErrorCode errorForCreatingAccount;
    public boolean accountAlreadyExists;

    public AccountImportChunk withAccount(AccountInventory account) {
        this.account = account;
        return this;
    }

    public AccountImportChunk withAlreadyExistsAccount(AccountInventory account) {
        this.account = account;
        this.accountAlreadyExists = true;
        return this;
    }

    public AccountImportChunk withSpec(ImportAccountBatch.AccountSpec spec) {
        this.spec = spec;
        return this;
    }

    public AccountImportChunk withRef(ImportAccountRefVO ref) {
        this.ref = ref;
        return this;
    }
}
