package org.zstack.ldap.compute;

import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.Platform;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.Q;
import org.zstack.core.db.SQL;
import org.zstack.core.workflow.FlowChainBuilder;
import org.zstack.header.core.ReturnValueCompletion;
import org.zstack.header.core.workflow.Flow;
import org.zstack.header.core.workflow.FlowChain;
import org.zstack.header.core.workflow.FlowDoneHandler;
import org.zstack.header.core.workflow.FlowErrorHandler;
import org.zstack.header.core.workflow.FlowRollback;
import org.zstack.header.core.workflow.FlowTrigger;
import org.zstack.header.core.workflow.NoRollbackFlow;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.identity.imports.header.AbstractAccountSourceSpec;
import org.zstack.identity.imports.entity.ThirdPartyAccountSourceVO;
import org.zstack.identity.imports.source.AccountSourceFactory;
import org.zstack.ldap.LdapConstant;
import org.zstack.ldap.LdapErrors;
import org.zstack.ldap.entity.LdapServerVO;
import org.zstack.ldap.entity.LdapServerVO_;
import org.zstack.ldap.header.LdapAccountSourceSpec;

import java.util.Map;

import static org.zstack.core.Platform.err;

/**
 * Created by Wenhao.Zhang on 2024/06/03
 */
public class LdapAccountSourceFactory implements AccountSourceFactory {
    @Autowired
    private DatabaseFacade databaseFacade;

    @Override
    public String type() {
        return LdapConstant.LOGIN_TYPE;
    }

    @Override
    public LdapAccountSource createBase(ThirdPartyAccountSourceVO vo) {
        final LdapServerVO ldapServer = (vo instanceof LdapServerVO) ?
                (LdapServerVO) vo :
                databaseFacade.findByUuid(vo.getUuid(), LdapServerVO.class);
        return new LdapAccountSource(ldapServer);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void createAccountSource(AbstractAccountSourceSpec rawSpec, ReturnValueCompletion<ThirdPartyAccountSourceVO> completion) {
        LdapAccountSourceSpec spec = ((LdapAccountSourceSpec) rawSpec);
        ThirdPartyAccountSourceVO[] results = new ThirdPartyAccountSourceVO[1];

        FlowChain chain = FlowChainBuilder.newSimpleFlowChain();
        chain.setName("create-ldap-server");
        chain.then(new NoRollbackFlow() {
            String __name__ = "pre-create-check";
            @Override
            public void run(FlowTrigger trigger, Map data) {
                if (Q.New(LdapServerVO.class).count() == 1) {
                    trigger.fail(err(LdapErrors.MORE_THAN_ONE_LDAP_SERVER,
                            "There has been a LDAP/AD server record. " +
                                    "You'd better remove it before adding a new one!"));
                    return;
                }
                if (spec.getUuid() == null) {
                    spec.setUuid(Platform.getUuid());
                }
                trigger.next();
            }
        }).then(new Flow() {
            String __name__ = "add-VO-in-db";
            @Override
            public void run(FlowTrigger trigger, Map data) {
                LdapServerVO ldapServerVO = new LdapServerVO();
                ldapServerVO.setUuid(spec.getUuid());
                ldapServerVO.setResourceName(spec.getServerName());
                ldapServerVO.setDescription(spec.getDescription());
                ldapServerVO.setType(LdapConstant.LOGIN_TYPE);
                ldapServerVO.setUrl(spec.getUrl());
                ldapServerVO.setBase(spec.getBaseDn());
                ldapServerVO.setUsername(spec.getLogInUserName());
                ldapServerVO.setPassword(spec.getLogInPassword());
                ldapServerVO.setEncryption(spec.getEncryption().toString());
                ldapServerVO.setServerType(spec.getServerType());
                ldapServerVO.setCreateAccountStrategy(spec.getCreateAccountStrategy());
                ldapServerVO.setDeleteAccountStrategy(spec.getDeleteAccountStrategy());

                ldapServerVO = databaseFacade.persistAndRefresh(ldapServerVO);
                results[0] = ldapServerVO;
                trigger.next();
            }

            @Override
            public void rollback(FlowRollback trigger, Map data) {
                SQL.New(LdapServerVO.class)
                        .eq(LdapServerVO_.uuid, spec.getUuid())
                        .delete();
                trigger.rollback();
            }
        }).then(new NoRollbackFlow() {
            String __name__ = "post-create-ldap-server";
            @Override
            public void run(FlowTrigger trigger, Map data) {
                final LdapAccountSource base = createBase(results[0]);
                base.saveLdapUsernamePropertySystemTags(spec.getUsernameProperty());

                trigger.next();
            }
        }).done(new FlowDoneHandler(completion) {
            @Override
            public void handle(Map data) {
                completion.success(results[0]);
            }
        }).error(new FlowErrorHandler(completion) {
            @Override
            public void handle(ErrorCode errCode, Map data) {
                completion.fail(errCode);
            }
        }).start();
    }
}
