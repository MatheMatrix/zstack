package org.zstack.identity.imports;

import org.zstack.header.errorcode.ErrorCode;
import org.zstack.identity.imports.entity.AccountImportContext;
import org.zstack.identity.imports.entity.ImportAccountSpec;

import java.util.List;

public interface AccountImportExtensionPoint {
    ErrorCode preAccountsImporting(ImportAccountSpec batch);

    default void afterAccountsImporting(List<AccountImportContext> context) {}
}
