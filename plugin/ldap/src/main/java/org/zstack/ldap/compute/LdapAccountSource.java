package org.zstack.ldap.compute;

import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.db.Q;
import org.zstack.header.core.Completion;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.identity.imports.header.SyncTaskSpec;
import org.zstack.identity.imports.source.AbstractAccountSourceBase;
import org.zstack.ldap.LdapConstant;
import org.zstack.ldap.entity.LdapFilterRuleVO;
import org.zstack.ldap.entity.LdapFilterRuleVO_;
import org.zstack.ldap.entity.LdapServerVO;
import org.zstack.ldap.header.LdapSyncTaskSpec;
import org.zstack.resourceconfig.ResourceConfigFacade;

import java.util.List;

import static org.zstack.core.Platform.operr;
import static org.zstack.ldap.LdapGlobalConfig.*;
import static org.zstack.ldap.LdapSystemTags.*;

/**
 * Created by Wenhao.Zhang on 2024/06/03
 */
public class LdapAccountSource extends AbstractAccountSourceBase {
    protected LdapAccountSource(LdapServerVO self) {
        super(self);
    }

    @Autowired
    private ResourceConfigFacade resourceConfigFacade;

    @Override
    public String type() {
        return LdapConstant.LOGIN_TYPE;
    }

    @Override
    protected void syncAccountsFromSource(SyncTaskSpec spec, Completion completion) {
        final List<LdapFilterRuleVO> rules = Q.New(LdapFilterRuleVO.class)
                .eq(LdapFilterRuleVO_.ldapServerUuid, self.getUuid())
                .list();

        final LdapSyncTaskSpec ldapSpec = new LdapSyncTaskSpec(spec);
        ldapSpec.setRules(rules);
        ldapSpec.setDefaultFilter(resourceConfigFacade.getResourceConfigValue(
                LDAP_USER_SYNC_FILTER, self.getUuid(), String.class));
        ldapSpec.setMaxAccountCount(resourceConfigFacade.getResourceConfigValue(
                LDAP_MAXIMUM_SYNC_USERS, self.getUuid(), Integer.class));

        String property = LDAP_USERNAME_PROPERTY.getTokenByResourceUuid(self.getUuid(), LDAP_USERNAME_PROPERTY_TOKEN);
        ldapSpec.setUsernameProperty(property == null ? LdapConstant.LDAP_UID_KEY : property);

        new LdapSyncBackend(ldapSpec).run(new Completion(completion) {
            @Override
            public void success() {
                completion.success();
            }

            @Override
            public void fail(ErrorCode errorCode) {
                completion.fail(errorCode);
            }
        });
    }

    @Override
    protected void destroySource(Completion completion) {
        completion.fail(operr("TODO"));
    }
}
