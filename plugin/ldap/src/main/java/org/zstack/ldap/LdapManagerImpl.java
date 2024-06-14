package org.zstack.ldap;

import java.sql.SQLIntegrityConstraintViolationException;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ldap.filter.AndFilter;
import org.springframework.ldap.filter.HardcodedFilter;
import org.springframework.transaction.annotation.Transactional;
import org.zstack.core.Platform;
import org.zstack.core.asyncbatch.While;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.cloudbus.CloudBusCallBack;
import org.zstack.core.cloudbus.MessageSafe;
import org.zstack.core.componentloader.PluginRegistry;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.Q;
import org.zstack.core.db.SQLBatch;
import org.zstack.core.db.SimpleQuery;
import org.zstack.core.workflow.SimpleFlowChain;
import org.zstack.header.AbstractService;
import org.zstack.header.core.NoErrorCompletion;
import org.zstack.header.core.ReturnValueCompletion;
import org.zstack.header.core.WhileDoneCompletion;
import org.zstack.header.core.workflow.*;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.errorcode.ErrorCodeList;
import org.zstack.header.errorcode.OperationFailureException;
import org.zstack.header.identity.*;
import org.zstack.header.identity.login.*;
import org.zstack.header.message.APIMessage;
import org.zstack.header.message.Message;
import org.zstack.header.message.MessageReply;
import org.zstack.identity.imports.AccountImportsConstant;
import org.zstack.identity.imports.entity.AccountThirdPartyAccountSourceRefInventory;
import org.zstack.identity.imports.entity.AccountThirdPartyAccountSourceRefVO;
import org.zstack.identity.imports.entity.AccountThirdPartyAccountSourceRefVO_;
import org.zstack.identity.imports.entity.SyncCreatedAccountStrategy;
import org.zstack.identity.imports.entity.SyncDeletedAccountStrategy;
import org.zstack.identity.imports.message.SyncThirdPartyAccountMsg;
import org.zstack.ldap.api.*;
import org.zstack.ldap.entity.LdapServerInventory;
import org.zstack.ldap.entity.LdapServerType;
import org.zstack.ldap.entity.LdapServerVO;
import org.zstack.ldap.entity.LdapServerVO_;
import org.zstack.tag.PatternedSystemTag;
import org.zstack.tag.SystemTagCreator;
import org.zstack.tag.SystemTagUtils;
import org.zstack.utils.CollectionDSL;
import org.zstack.utils.ExceptionDSL;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import javax.persistence.PersistenceException;
import javax.persistence.Query;
import java.sql.Timestamp;
import java.util.*;

import static org.zstack.core.Platform.err;
import static org.zstack.core.Platform.operr;
import static org.zstack.utils.CollectionDSL.map;

/**
 * Created by miao on 16-9-6.
 */
public class LdapManagerImpl extends AbstractService implements LdapManager, LoginBackend {
    private static final CLogger logger = Utils.getLogger(LdapManagerImpl.class);

    private static final LdapEffectiveScope scope = new LdapEffectiveScope(AccountConstant.LOGIN_TYPE);
    private static final LoginType loginType = new LoginType(LdapConstant.LOGIN_TYPE);

    @Autowired
    private DatabaseFacade dbf;
    @Autowired
    private CloudBus bus;
    @Autowired
    private PluginRegistry pluginRgty;

    @MessageSafe
    public void handleMessage(Message msg) {
        if (msg instanceof APIMessage) {
            handleApiMessage((APIMessage) msg);
        } else {
            handleLocalMessage(msg);
        }
    }

    private void handleLocalMessage(Message msg) {
        bus.dealWithUnknownMessage(msg);
    }

    private void handleApiMessage(APIMessage msg) {
        if (msg instanceof APIAddLdapServerMsg) {
            handle((APIAddLdapServerMsg) msg);
        } else if (msg instanceof APIDeleteLdapServerMsg) {
            handle((APIDeleteLdapServerMsg) msg);
        } else if (msg instanceof APIGetLdapEntryMsg) {
            handle((APIGetLdapEntryMsg) msg);
        } else if(msg instanceof APIGetCandidateLdapEntryForBindingMsg){
            handle((APIGetCandidateLdapEntryForBindingMsg) msg);
        } else if (msg instanceof APISyncAccountsFromLdapServerMsg) {
            handle((APISyncAccountsFromLdapServerMsg) msg);
        } else if (msg instanceof APICreateLdapBindingMsg) {
            handle((APICreateLdapBindingMsg) msg);
        } else if (msg instanceof APIDeleteLdapBindingMsg) {
            handle((APIDeleteLdapBindingMsg) msg);
        } else if (msg instanceof APIUpdateLdapServerMsg) {
            handle((APIUpdateLdapServerMsg) msg);
        } else {
            bus.dealWithUnknownMessage(msg);
        }
    }

    @Transactional
    private AccountThirdPartyAccountSourceRefInventory bindLdapAccount(String accountUuid, String ldapUid) {
        AccountThirdPartyAccountSourceRefVO ref = new AccountThirdPartyAccountSourceRefVO();
        ref.setAccountUuid(accountUuid);
        ref.setAccountSourceUuid(ldapUtil.getLdapServer().getUuid());
        ref.setCredentials(ldapUid);
        ref = dbf.persistAndRefresh(ref);
        return AccountThirdPartyAccountSourceRefInventory.valueOf(ref);
    }


    public String getId() {
        return bus.makeLocalServiceId(LdapConstant.SERVICE_ID);
    }

    @Transactional(readOnly = true)
    private Timestamp getCurrentSqlDate() {
        Query query = dbf.getEntityManager().createNativeQuery("select current_timestamp()");
        return (Timestamp) query.getSingleResult();
    }

    public boolean start() {
        return true;
    }

    public boolean stop() {
        return true;
    }

    @Override
    public boolean isValid(String uid, String password) {
        return ldapUtil.isValid(uid, password);
    }

    private void handle(APIAddLdapServerMsg msg) {
        APIAddLdapServerEvent evt = new APIAddLdapServerEvent(msg.getId());

        if (Q.New(LdapServerVO.class).count() == 1) {
            evt.setError(err(LdapErrors.MORE_THAN_ONE_LDAP_SERVER,
                    "There has been a LDAP/AD server record. " +
                            "You'd better remove it before adding a new one!"));
            bus.publish(evt);
            return;
        }

        LdapServerVO ldapServerVO = new LdapServerVO();
        ldapServerVO.setUuid(Platform.getUuid());
        ldapServerVO.setResourceName(msg.getName());
        ldapServerVO.setDescription(msg.getDescription());
        ldapServerVO.setType(LdapConstant.LOGIN_TYPE);
        ldapServerVO.setUrl(msg.getUrl());
        ldapServerVO.setBase(msg.getBase());
        ldapServerVO.setUsername(msg.getUsername());
        ldapServerVO.setPassword(msg.getPassword());
        ldapServerVO.setEncryption(msg.getEncryption());
        ldapServerVO.setServerType(LdapServerType.valueOf(msg.getServerType()));
        ldapServerVO.setCreateAccountStrategy(SyncCreatedAccountStrategy.valueOf(msg.getSyncCreatedAccountStrategy()));
        ldapServerVO.setDeleteAccountStrategy(SyncDeletedAccountStrategy.valueOf(msg.getSyncDeletedAccountStrategy()));

        ldapServerVO = dbf.persistAndRefresh(ldapServerVO);
        LdapServerInventory inv = LdapServerInventory.valueOf(ldapServerVO);
        evt.setInventory(inv);

        this.saveLdapCleanBindingFilterTag(msg.getSystemTags(), ldapServerVO.getUuid());
        this.saveLdapAllowListFilterTag(msg.getSystemTags(), ldapServerVO.getUuid());
        this.saveLdapServerTypeTag(msg.getSystemTags(), ldapServerVO.getUuid());
        this.saveLdapUseAsLoginNameTag(msg.getSystemTags(), ldapServerVO.getUuid());

        for (AddLdapExtensionPoint ext : pluginRgty.getExtensionList(AddLdapExtensionPoint.class)) {
            ext.afterAddLdapServer(msg, ldapServerVO.getUuid());
        }

        bus.publish(evt);
    }

    private void saveLdapCleanBindingFilterTag(List<String> systemTags, String uuid) {
        if(systemTags == null || systemTags.isEmpty()) {
            return;
        }

        PatternedSystemTag tag =  LdapSystemTags.LDAP_CLEAN_BINDING_FILTER;
        String token = LdapSystemTags.LDAP_CLEAN_BINDING_FILTER_TOKEN;

        String tagValue = SystemTagUtils.findTagValue(systemTags, tag, token);
        if(StringUtils.isEmpty(tagValue)){
            return;
        }

        SystemTagCreator creator = tag.newSystemTagCreator(uuid);
        creator.recreate = true;
        creator.setTagByTokens(map(CollectionDSL.e(token, tagValue)));
        creator.create();
    }

    private void saveLdapAllowListFilterTag(List<String> systemTags, String uuid){
        if(systemTags == null || systemTags.isEmpty()){
            return;
        }

        PatternedSystemTag tag = LdapSystemTags.LDAP_ALLOW_LIST_FILTER;
        String token = LdapSystemTags.LDAP_ALLOW_LIST_FILTER_TOKEN;

        String tagValue = SystemTagUtils.findTagValue(systemTags, tag, token);
        if(StringUtils.isEmpty(tagValue)){
            return;
        }

        SystemTagCreator creator = tag.newSystemTagCreator(uuid);
        creator.recreate = true;
        creator.setTagByTokens(map(CollectionDSL.e(token, tagValue)));
        creator.create();
    }

    private void saveLdapServerTypeTag(List<String> systemTags, String uuid) {
        if(systemTags == null || systemTags.isEmpty()) {
            return;
        }

        PatternedSystemTag tag =  LdapSystemTags.LDAP_SERVER_TYPE;
        String token = LdapSystemTags.LDAP_SERVER_TYPE_TOKEN;

        String tagValue = SystemTagUtils.findTagValue(systemTags, tag, token);
        if(StringUtils.isEmpty(tagValue)){
            return;
        }

        SystemTagCreator creator = tag.newSystemTagCreator(uuid);
        creator.recreate = true;
        creator.setTagByTokens(map(CollectionDSL.e(token, tagValue)));
        creator.create();
    }

    private void saveLdapUseAsLoginNameTag(List<String> systemTags, String uuid) {
        if(systemTags == null || systemTags.isEmpty()) {
            return;
        }

        PatternedSystemTag tag =  LdapSystemTags.LDAP_USE_AS_LOGIN_NAME;
        String token = LdapSystemTags.LDAP_USE_AS_LOGIN_NAME_TOKEN;

        String tagValue = SystemTagUtils.findTagValue(systemTags, tag, token);
        if(StringUtils.isEmpty(tagValue)){
            return;
        }

        SystemTagCreator creator = tag.newSystemTagCreator(uuid);
        creator.recreate = true;
        creator.setTagByTokens(map(CollectionDSL.e(token, tagValue)));
        creator.create();
    }

    private void handle(APIDeleteLdapServerMsg msg) {
        APIDeleteLdapServerEvent evt = new APIDeleteLdapServerEvent(msg.getId());

        FlowChain chain = new SimpleFlowChain();
        chain.setName("delete-ldap-server");
        chain.then(new NoRollbackFlow() {
            String __name__ = "before-delete-ldap-server";

            @Override
            public void run(FlowTrigger trigger, Map data) {
                new While<>(pluginRgty.getExtensionList(DeleteLdapServerExtensionPoint.class)).each((ext, whileCompletion) -> {
                    ext.beforeDeleteLdapServer(msg.getUuid(), new NoErrorCompletion(whileCompletion) {
                        @Override
                        public void done() {
                            whileCompletion.done();
                        }
                    });
                }).run(new WhileDoneCompletion(trigger) {
                    @Override
                    public void done(ErrorCodeList errorCodeList) {
                        trigger.next();
                    }
                });
            }
        }).done(new FlowDoneHandler(msg) {
            @Override
            public void handle(Map data) {
                new SQLBatch() {
                    @Override
                    protected void scripts() {
                        if (!q(LdapServerVO.class).eq(LdapServerVO_.uuid, msg.getUuid()).isExists()) {
                            return;
                        }

                        LdapServerVO vo = q(LdapServerVO.class).eq(LdapServerVO_.uuid, msg.getUuid()).find();
                        remove(vo);
                        flush();
                    }
                }.execute();

                bus.publish(evt);
            }
        }).error(new FlowErrorHandler(msg) {
            @Override
            public void handle(ErrorCode errCode, Map data) {
                evt.setError(errCode);
                bus.publish(evt);
            }
        }).start();
    }

    private void handle(APIGetLdapEntryMsg msg) {
        APIGetLdapEntryReply reply = new APIGetLdapEntryReply();

        List<Object> result;
        if (msg.getLdapServerUuid() != null) {
            result = ldapUtil.searchLdapEntry(msg.getLdapServerUuid(), msg.getLdapFilter(), msg.getLimit(), null);
        } else {
            result = ldapUtil.searchLdapEntry(msg.getLdapFilter(), msg.getLimit(), null);
        }
        reply.setInventories(result);

        bus.reply(msg, reply);
    }

    private void handle(APIGetCandidateLdapEntryForBindingMsg msg) {
        APIGetLdapEntryReply reply = new APIGetLdapEntryReply();

        AndFilter andFilter = new AndFilter();
        andFilter.and(new HardcodedFilter(msg.getLdapFilter()));

        List<String> boundLdapEntryList = Q.New(AccountThirdPartyAccountSourceRefVO.class)
                .select(AccountThirdPartyAccountSourceRefVO_.credentials)
                .listValues();

        List<Object> result = ldapUtil.searchLdapEntry(andFilter.toString(), msg.getLimit(), new ResultFilter() {
            @Override
            public boolean needSelect(String dn) {
                return !boundLdapEntryList.contains(dn);
            }
        });

        reply.setInventories(result);

        bus.reply(msg, reply);
    }

    private void handle(APICreateLdapBindingMsg msg) {
        APICreateLdapBindingEvent evt = new APICreateLdapBindingEvent(msg.getId());

        // account check
        SimpleQuery<AccountVO> sq = dbf.createQuery(AccountVO.class);
        sq.add(AccountVO_.uuid, SimpleQuery.Op.EQ, msg.getAccountUuid());
        AccountVO avo = sq.find();
        if (avo == null) {
            evt.setError(err(LdapErrors.CANNOT_FIND_ACCOUNT,
                    String.format("cannot find the specified account[uuid:%s]", msg.getAccountUuid())));
            bus.publish(evt);
            return;
        }

        // bind op
        LdapTemplateContextSource ldapTemplateContextSource = ldapUtil.readLdapServerConfiguration();
        String fullDn = msg.getLdapUid();
        if (!ldapUtil.validateDnExist(ldapTemplateContextSource, fullDn)) {
            throw new OperationFailureException(err(LdapErrors.UNABLE_TO_GET_SPECIFIED_LDAP_UID,
                    "cannot find dn[%s] on LDAP/AD server[Address:%s, BaseDN:%s].", fullDn,
                    String.join(", ", ldapTemplateContextSource.getLdapContextSource().getUrls()),
                    ldapTemplateContextSource.getLdapContextSource().getBaseLdapPathAsString()));
        }
        try {
            evt.setInventory(bindLdapAccount(msg.getAccountUuid(), fullDn));
            logger.info(String.format("create ldap binding[ldapUid=%s, account=%s] success", fullDn, msg.getAccountUuid()));
        } catch (PersistenceException e) {
            if (ExceptionDSL.isCausedBy(e, SQLIntegrityConstraintViolationException.class)) {
                evt.setError(err(LdapErrors.BIND_SAME_LDAP_UID_TO_MULTI_ACCOUNT,
                        "The ldap uid has been bound to an account. "));
            } else {
                throw e;
            }
        }
        bus.publish(evt);
    }

    private void handle(APIDeleteLdapBindingMsg msg) {
        APIDeleteLdapBindingEvent evt = new APIDeleteLdapBindingEvent(msg.getId());

        dbf.removeByPrimaryKey(msg.getUuid(), AccountThirdPartyAccountSourceRefVO.class);

        bus.publish(evt);
    }

    private void handle(APIUpdateLdapServerMsg msg) {
        APIUpdateLdapServerEvent evt = new APIUpdateLdapServerEvent(msg.getId());

        LdapServerVO ldapServerVO = dbf.findByUuid(msg.getLdapServerUuid(), LdapServerVO.class);
        if (ldapServerVO == null) {
            evt.setError(err(LdapErrors.UNABLE_TO_GET_SPECIFIED_LDAP_SERVER_RECORD,
                    "Cannot find the specified LDAP/AD server[uuid:%s] in database.",
                    msg.getLdapServerUuid()));
            bus.publish(evt);
            return;
        }

        //
        if (msg.getName() != null) {
            ldapServerVO.setResourceName(msg.getName());
        }
        if (msg.getDescription() != null) {
            ldapServerVO.setDescription(msg.getDescription());
        }
        if (msg.getUrl() != null) {
            ldapServerVO.setUrl(msg.getUrl());
        }
        if (msg.getBase() != null) {
            ldapServerVO.setBase(msg.getBase());
        }
        if (msg.getUsername() != null) {
            ldapServerVO.setUsername(msg.getUsername());
        }
        if (msg.getPassword() != null) {
            ldapServerVO.setPassword(msg.getPassword());
        }
        if (msg.getEncryption() != null) {
            ldapServerVO.setEncryption(msg.getEncryption());
        }

        ldapServerVO = dbf.updateAndRefresh(ldapServerVO);
        evt.setInventory(LdapServerInventory.valueOf(ldapServerVO));

        this.saveLdapCleanBindingFilterTag(msg.getSystemTags(), ldapServerVO.getUuid());
        this.saveLdapAllowListFilterTag(msg.getSystemTags(), ldapServerVO.getUuid());
        this.saveLdapServerTypeTag(msg.getSystemTags(), ldapServerVO.getUuid());
        this.saveLdapUseAsLoginNameTag(msg.getSystemTags(), ldapServerVO.getUuid());
        for (UpdateLdapServerExtensionPoint ext : pluginRgty.getExtensionList(UpdateLdapServerExtensionPoint.class)) {
            ext.afterUpdateLdapServer(msg);
        }

        bus.publish(evt);
    }

    private void handle(APISyncAccountsFromLdapServerMsg msg) {
        SyncThirdPartyAccountMsg innerMsg = new SyncThirdPartyAccountMsg();
        innerMsg.setSourceUuid(msg.getUuid());
        bus.makeTargetServiceIdByResourceUuid(innerMsg, AccountImportsConstant.SERVICE_ID, msg.getUuid());
        bus.send(innerMsg, new CloudBusCallBack(msg) {
            @Override
            public void run(MessageReply reply) {
                APISyncAccountsFromLdapServerEvent event = new APISyncAccountsFromLdapServerEvent(msg.getId());
                if (!reply.isSuccess()) {
                    event.setError(reply.getError());
                }
                bus.publish(event);
            }
        });
    }
    @Override
    public LoginType getLoginType() {
        return loginType;
    }

    @Override
    public void login(LoginContext loginContext, ReturnValueCompletion<LoginSessionInfo> completion) {
        String ldapLoginName = loginContext.getUsername();
        if (!isValid(ldapLoginName, loginContext.getPassword())) {
            completion.fail(err(IdentityErrors.AUTHENTICATION_ERROR,
                    "Login validation failed in LDAP"));
            return;
        }

        LdapTemplateContextSource ldapTemplateContextSource = ldapUtil.readLdapServerConfiguration();
        String dn = ldapUtil.getFullUserDn(ldapTemplateContextSource.getLdapTemplate(), ldapUtil.getLdapUseAsLoginName(), ldapLoginName);
        AccountThirdPartyAccountSourceRefVO vo = ldapUtil.findLdapAccountRefVO(dn);

        if (vo == null) {
            completion.fail(err(IdentityErrors.AUTHENTICATION_ERROR,
                    "The ldapUid does not have a binding account."));
            return;
        }

        SimpleQuery<AccountVO> sq = dbf.createQuery(AccountVO.class);
        sq.add(AccountVO_.uuid, SimpleQuery.Op.EQ, vo.getAccountUuid());
        AccountVO avo = sq.find();
        if (avo == null) {
            completion.fail(operr(
                    "Account[uuid:%s] Not Found!!!", vo.getAccountUuid()));
            return;
        }

        LoginSessionInfo info = new LoginSessionInfo();
        info.setUserUuid(vo.getAccountUuid());
        info.setAccountUuid(vo.getAccountUuid());
        info.setUserType(AccountVO.class.getSimpleName());
        completion.success(info);
    }

    @Override
    public boolean authenticate(String username, String password) {
        return ldapUtil.isValid(username, password);
    }

    @Override
    public String getUserIdByName(String username) {
        return ldapUtil.getFullUserDn(username);
    }

    @Override
    public void collectUserInfoIntoContext(LoginContext loginContext) {
        loginContext.setUserUuid(getUserIdByName(loginContext.getUsername()));
    }

    @Override
    public List<AdditionalAuthFeature> getRequiredAdditionalAuthFeature() {
        return Collections.singletonList(LoginAuthConstant.basicLoginControl);
    }
}
