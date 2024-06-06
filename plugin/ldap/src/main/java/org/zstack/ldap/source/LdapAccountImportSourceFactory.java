package org.zstack.ldap.source;

import org.zstack.header.errorcode.ErrorableValue;
import org.zstack.identity.imports.entity.AbstractImportSourceSpec;
import org.zstack.identity.imports.entity.AccountImportSourceVO;
import org.zstack.identity.imports.source.AbstractAccountImportSourceBase;
import org.zstack.identity.imports.source.AccountImportSourceFactory;
import org.zstack.ldap.LdapConstant;

/**
 * Created by Wenhao.Zhang on 2024/06/03
 */
public class LdapAccountImportSourceFactory implements AccountImportSourceFactory {
    @Override
    public String type() {
        return LdapConstant.LOGIN_TYPE;
    }

    @Override
    public AbstractAccountImportSourceBase createBase() {
        return null;
    }

    @Override
    public ErrorableValue<AccountImportSourceVO> createAccountImportSource(AbstractImportSourceSpec spec) {
        return null;
    }
}
