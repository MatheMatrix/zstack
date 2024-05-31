package org.zstack.identity.imports;

import org.zstack.header.errorcode.ErrorCode;
import org.zstack.identity.imports.entity.AccountImportChunk;
import org.zstack.identity.imports.entity.ImportAccountBatch;

import java.util.List;

public interface AccountImportExtensionPoint {
    ErrorCode preAccountsImporting(ImportAccountBatch batch);

    default void afterAccountsImporting(List<AccountImportChunk> chunks) {}
}
