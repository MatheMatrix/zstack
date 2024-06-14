package org.zstack.ldap.compute;

import org.springframework.beans.factory.annotation.Autowired;
import org.apache.commons.lang.StringUtils;
import org.zstack.core.Platform;
import org.zstack.core.db.Q;
import org.zstack.core.db.SQL;
import org.zstack.header.core.Completion;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.message.Message;
import org.zstack.identity.imports.header.SyncTaskSpec;
import org.zstack.identity.imports.source.AbstractAccountSourceBase;
import org.zstack.ldap.LdapConstant;
import org.zstack.ldap.LdapSystemTags;
import org.zstack.ldap.entity.LdapFilterRuleInventory;
import org.zstack.ldap.entity.LdapFilterRuleVO;
import org.zstack.ldap.entity.LdapFilterRuleVO_;
import org.zstack.ldap.entity.LdapServerVO;
import org.zstack.ldap.header.LdapSyncTaskSpec;
import org.zstack.resourceconfig.ResourceConfigFacade;
import org.zstack.ldap.message.AddLdapFilterRuleMsg;
import org.zstack.ldap.message.AddLdapFilterRuleReply;
import org.zstack.ldap.message.RemoveLdapFilterRuleMsg;
import org.zstack.ldap.message.RemoveLdapFilterRuleReply;
import org.zstack.ldap.message.UpdateLdapFilterRuleMsg;
import org.zstack.ldap.message.UpdateLdapFilterRuleReply;
import org.zstack.tag.PatternedSystemTag;
import org.zstack.tag.SystemTagCreator;
import org.zstack.utils.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

import static org.zstack.core.Platform.operr;
import static org.zstack.ldap.LdapGlobalConfig.*;
import static org.zstack.ldap.LdapSystemTags.*;
import static org.zstack.utils.CollectionDSL.e;
import static org.zstack.utils.CollectionDSL.map;

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
    public void handleMessage(Message msg) {
        if (msg instanceof AddLdapFilterRuleMsg) {
            handle((AddLdapFilterRuleMsg) msg);
        } else if (msg instanceof UpdateLdapFilterRuleMsg) {
            handle((UpdateLdapFilterRuleMsg) msg);
        } else if (msg instanceof RemoveLdapFilterRuleMsg) {
            handle((RemoveLdapFilterRuleMsg) msg);
        } else {
            super.handleMessage(msg);
        }
    }

    private void handle(AddLdapFilterRuleMsg msg) {
        List<LdapFilterRuleVO> list = new ArrayList<>();

        for (String rule : msg.getRules()) {
            LdapFilterRuleVO vo = new LdapFilterRuleVO();
            list.add(vo);

            vo.setRule(rule);
            vo.setUuid(Platform.getUuid());
            vo.setPolicy(msg.getPolicy());
            vo.setTarget(msg.getTarget());
            vo.setLdapServerUuid(msg.getLdapServerUuid());
        }

        databaseFacade.persistCollection(list);

        AddLdapFilterRuleReply reply = new AddLdapFilterRuleReply();
        reply.setInventories(CollectionUtils.transform(list, LdapFilterRuleInventory::valueOf));
        bus.reply(msg, reply);
    }

    private void handle(UpdateLdapFilterRuleMsg msg) {
        LdapFilterRuleVO ruleVO = Q.New(LdapFilterRuleVO.class)
                .eq(LdapFilterRuleVO_.uuid, msg.getRuleUuid())
                .find();
        boolean updated = false;

        if (msg.getRule() != null) {
            ruleVO.setRule(msg.getRule());
            updated = true;
        }
        if (msg.getPolicy() != null) {
            ruleVO.setPolicy(msg.getPolicy());
            updated = true;
        }
        if (msg.getTarget() != null) {
            ruleVO.setTarget(msg.getTarget());
            updated = true;
        }

        if (updated) {
            databaseFacade.update(ruleVO);
        }

        UpdateLdapFilterRuleReply reply = new UpdateLdapFilterRuleReply();
        reply.setInventory(LdapFilterRuleInventory.valueOf(ruleVO));
        bus.reply(msg, reply);
    }

    private void handle(RemoveLdapFilterRuleMsg msg) {
        SQL.New(LdapFilterRuleVO.class)
                .eq(LdapFilterRuleVO_.ldapServerUuid, self.getUuid())
                .in(LdapFilterRuleVO_.uuid, msg.getRuleUuidList())
                .delete();
        bus.reply(msg, new RemoveLdapFilterRuleReply());
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

    @SuppressWarnings("unchecked")
    void saveLdapUsernamePropertySystemTags(String usernameProperty) {
        if (StringUtils.isEmpty(usernameProperty)) {
            return;
        }

        PatternedSystemTag tag = LdapSystemTags.LDAP_USERNAME_PROPERTY;
        String token = LdapSystemTags.LDAP_USERNAME_PROPERTY_TOKEN;

        SystemTagCreator creator = tag.newSystemTagCreator(self.getUuid());
        creator.recreate = true;
        creator.setTagByTokens(map(e(token, usernameProperty)));
        creator.create();
    }
}
