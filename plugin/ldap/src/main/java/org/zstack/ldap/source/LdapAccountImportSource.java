package org.zstack.ldap.source;

import org.zstack.identity.imports.entity.AccountImportSourceVO;
import org.zstack.identity.imports.source.AbstractAccountImportSourceBase;
import org.zstack.ldap.LdapConstant;

/**
 * Created by Wenhao.Zhang on 2024/06/03
 */
public class LdapAccountImportSource extends AbstractAccountImportSourceBase {
    protected LdapAccountImportSource(AccountImportSourceVO self) {
        super(self);
    }

    @Override
    public String type() {
        return LdapConstant.LOGIN_TYPE;
    }
}
