package org.zstack.identity.imports;

import org.zstack.header.errorcode.ErrorCode;
import org.zstack.identity.imports.source.ImportThirdPartyAccountContext;
import org.zstack.identity.imports.header.ImportAccountSpec;

import java.util.List;

public interface AccountImportExtensionPoint {
    ErrorCode preAccountsImporting(ImportAccountSpec batch);

    default void afterAccountsImporting(List<ImportThirdPartyAccountContext> context) {}
}
