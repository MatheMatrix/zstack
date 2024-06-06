package org.zstack.identity.imports.entity;

import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.identity.AccountInventory;

/**
 * Created by Wenhao.Zhang on 2024/05/31
 */
public class AccountImportContext {
    public AccountInventory account;
    public ImportAccountSpec.AccountSpec spec;
    public AccountSourceRefVO ref;
    public ErrorCode errorForCreatingAccount;

    public AccountImportContext withAccount(AccountInventory account) {
        this.account = account;
        return this;
    }

    public AccountImportContext withSpec(ImportAccountSpec.AccountSpec spec) {
        this.spec = spec;
        return this;
    }

    public AccountImportContext withRef(AccountSourceRefVO ref) {
        this.ref = ref;
        return this;
    }

    public Result generateResult() {
        Result result = new Result();
        result.account = this.account;
        result.ref = this.ref;
        result.errorForCreatingAccount = this.errorForCreatingAccount;
        return result;
    }

    public static class Result {
        public AccountInventory account;
        public AccountSourceRefVO ref;
        public ErrorCode errorForCreatingAccount;
    }
}
