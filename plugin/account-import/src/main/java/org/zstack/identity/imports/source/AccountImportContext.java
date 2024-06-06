package org.zstack.identity.imports.source;

import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.identity.AccountInventory;
import org.zstack.identity.imports.entity.AccountSourceRefVO;
import org.zstack.identity.imports.entity.ImportAccountResult;
import org.zstack.identity.imports.entity.ImportAccountSpec;

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

    public ImportAccountResult makeResult() {
        ImportAccountResult result = new ImportAccountResult();
        result.setAccount(this.account);
        result.setRef(this.ref);
        result.setErrorForCreatingAccount(this.errorForCreatingAccount);
        return result;
    }
}
