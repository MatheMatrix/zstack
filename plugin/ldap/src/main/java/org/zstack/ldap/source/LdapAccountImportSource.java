package org.zstack.ldap.source;

import org.zstack.header.core.Completion;
import org.zstack.identity.imports.entity.AccountImportSourceVO;
import org.zstack.identity.imports.entity.SyncAccountSpec;
import org.zstack.identity.imports.source.AbstractAccountImportSourceBase;
import org.zstack.ldap.LdapConstant;

import static org.zstack.core.Platform.operr;

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

    @Override
    protected void syncAccountsFromSource(SyncAccountSpec spec, Completion completion) {
        completion.fail(operr("TODO"));
    }

    @Override
    protected void destroySource(Completion completion) {
        completion.fail(operr("TODO"));
    }
}
