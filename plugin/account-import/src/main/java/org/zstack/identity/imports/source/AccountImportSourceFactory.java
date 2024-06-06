package org.zstack.identity.imports.source;

import org.zstack.header.errorcode.ErrorableValue;
import org.zstack.identity.imports.entity.AbstractImportSourceSpec;
import org.zstack.identity.imports.entity.AccountImportSourceVO;

/**
 * Created by Wenhao.Zhang on 2024/05/31
 */
public interface AccountImportSourceFactory {
    String type();
    AbstractAccountImportSourceBase createBase();
    ErrorableValue<AccountImportSourceVO> createAccountImportSource(AbstractImportSourceSpec spec);
}
