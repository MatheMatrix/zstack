package org.zstack.ldap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ldap.filter.AndFilter;
import org.springframework.ldap.filter.HardcodedFilter;
import org.zstack.core.Platform;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.cloudbus.CloudBusCallBack;
import org.zstack.core.cloudbus.MessageSafe;
import org.zstack.core.componentloader.PluginRegistry;
import org.zstack.core.config.GlobalConfigException;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.Q;
import org.zstack.core.db.SimpleQuery;
import org.zstack.header.AbstractService;
import org.zstack.header.core.ReturnValueCompletion;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.errorcode.ErrorableValue;
import org.zstack.header.identity.*;
import org.zstack.header.identity.login.*;
import org.zstack.header.message.APIMessage;
import org.zstack.header.message.Message;
import org.zstack.header.message.MessageReply;
import org.zstack.identity.imports.AccountImportsConstant;
import org.zstack.identity.imports.entity.AccountThirdPartyAccountSourceRefVO;
import org.zstack.identity.imports.entity.AccountThirdPartyAccountSourceRefVO_;
import org.zstack.identity.imports.entity.SyncCreatedAccountStrategy;
import org.zstack.identity.imports.entity.SyncDeletedAccountStrategy;
import org.zstack.identity.imports.header.ImportAccountResult;
import org.zstack.identity.imports.header.UnbindThirdPartyAccountResult;
import org.zstack.identity.imports.header.UnbindThirdPartyAccountsSpec;
import org.zstack.identity.imports.message.BindThirdPartyAccountMsg;
import org.zstack.identity.imports.message.BindThirdPartyAccountReply;
import org.zstack.identity.imports.message.DestroyThirdPartyAccountSourceMsg;
import org.zstack.identity.imports.message.SyncThirdPartyAccountMsg;
import org.zstack.identity.imports.message.UnbindThirdPartyAccountMsg;
import org.zstack.identity.imports.message.UnbindThirdPartyAccountReply;
import org.zstack.ldap.api.*;
import org.zstack.ldap.driver.LdapSearchSpec;
import org.zstack.ldap.entity.LdapEncryptionType;
import org.zstack.ldap.entity.LdapServerInventory;
import org.zstack.ldap.entity.LdapServerType;
import org.zstack.ldap.entity.LdapServerVO;
import org.zstack.ldap.entity.LdapServerVO_;
import org.zstack.ldap.header.LdapAccountSourceSpec;
import org.zstack.ldap.message.CreateLdapAccountSourceMsg;
import org.zstack.ldap.message.UpdateLdapAccountSourceMsg;
import org.zstack.ldap.message.UpdateLdapAccountSourceReply;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.*;

import static org.zstack.core.Platform.err;
import static org.zstack.core.Platform.operr;
import static org.zstack.ldap.LdapConstant.CURRENT_LDAP_UUID_NONE;
import static org.zstack.ldap.LdapErrors.NONE_LDAP_SERVER_ENABLED;
import static org.zstack.ldap.LdapErrors.UNABLE_TO_FIND_LDAP_SERVER;
import static org.zstack.utils.CollectionDSL.list;

/**
 * Created by miao on 16-9-6.
 */
public class LdapManagerImpl extends AbstractService implements LdapManager, LoginBackend {
    private static final CLogger logger = Utils.getLogger(LdapManagerImpl.class);

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

    public String getId() {
        return bus.makeLocalServiceId(LdapConstant.SERVICE_ID);
    }

    public boolean start() {
        installGlobalConfigValidator();
        return true;
    }

    public boolean stop() {
        return true;
    }

    private void installGlobalConfigValidator() {
        LdapGlobalConfig.CURRENT_LDAP_SERVER_UUID.installValidateExtension((category, name, oldValue, newValue) -> {
            if (CURRENT_LDAP_UUID_NONE.equals(newValue)) {
                return;
            }

            boolean exists = Q.New(LdapServerVO.class)
                    .eq(LdapServerVO_.uuid, newValue)
                    .isExists();
            if (!exists) {
                throw new GlobalConfigException(String.format(
                        "failed to update GlobalConfig[%s]: invalid LdapServer[uuid=%s]",
                        LdapGlobalConfig.CURRENT_LDAP_SERVER_UUID.getName(), newValue));
            }
        });
    }

    @Override
    public boolean isValid(String uid, String password) {
        final ErrorableValue<LdapServerVO> ldap = findCurrentLdapServer();
        if (!ldap.isSuccess()) {
            return false;
        }
        return createDriver().isValid(uid, password, ldap.result);
    }

    private void handle(APIAddLdapServerMsg msg) {
        APIAddLdapServerEvent event = new APIAddLdapServerEvent(msg.getId());

        LdapAccountSourceSpec spec = new LdapAccountSourceSpec();
        spec.setUuid(Platform.getUuid());
        spec.setServerName(msg.getName());
        spec.setDescription(msg.getDescription());
        spec.setType(LdapConstant.LOGIN_TYPE);
        spec.setUrl(msg.getUrl());
        spec.setBaseDn(msg.getBase());
        spec.setLogInUserName(msg.getUsername());
        spec.setLogInPassword(msg.getPassword());
        spec.setEncryption(LdapEncryptionType.valueOf(msg.getEncryption()));
        spec.setServerType(LdapServerType.valueOf(msg.getServerType()));
        spec.setCreateAccountStrategy(SyncCreatedAccountStrategy.valueOf(msg.getSyncCreatedAccountStrategy()));
        spec.setDeleteAccountStrategy(SyncDeletedAccountStrategy.valueOf(msg.getSyncDeletedAccountStrategy()));
        spec.setUsernameProperty(msg.getUsernameProperty());
        if (msg.getFilter() != null) {
            spec.setFilter(msg.getFilter());
        }

        CreateLdapAccountSourceMsg innerMsg = new CreateLdapAccountSourceMsg();
        innerMsg.setSpec(spec);
        bus.makeTargetServiceIdByResourceUuid(innerMsg, AccountImportsConstant.SERVICE_ID, LdapServerVO.class.getName());
        bus.send(innerMsg, new CloudBusCallBack(msg) {
            @Override
            public void run(MessageReply reply) {
                if (reply.isSuccess()) {
                    final LdapServerVO server = dbf.findByUuid(spec.getUuid(), LdapServerVO.class);
                    event.setInventory(LdapServerInventory.valueOf(server));
                } else {
                    event.setError(reply.getError());
                }
                bus.publish(event);
            }
        });
    }

    private void handle(APIDeleteLdapServerMsg msg) {
        APIDeleteLdapServerEvent event = new APIDeleteLdapServerEvent(msg.getId());
        DestroyThirdPartyAccountSourceMsg innerMsg = new DestroyThirdPartyAccountSourceMsg();
        innerMsg.setUuid(msg.getUuid());
        bus.makeTargetServiceIdByResourceUuid(innerMsg, AccountImportsConstant.SERVICE_ID, msg.getUuid());
        bus.send(innerMsg, new CloudBusCallBack(msg) {
            @Override
            public void run(MessageReply reply) {
                if (!reply.isSuccess()) {
                    event.setError(reply.getError());
                }
                bus.publish(event);
            }
        });
    }

    private void handle(APIGetLdapEntryMsg msg) {
        APIGetLdapEntryReply reply = new APIGetLdapEntryReply();

        LdapSearchSpec spec = new LdapSearchSpec();
        spec.setLdapServerUuid(msg.getLdapServerUuid());
        spec.setFilter(msg.getLdapFilter());
        spec.setCount(msg.getLimit());
        reply.setInventories(createDriver().searchLdapEntry(spec));

        bus.reply(msg, reply);
    }

    private void handle(APIGetCandidateLdapEntryForBindingMsg msg) {
        APIGetLdapEntryReply reply = new APIGetLdapEntryReply();

        LdapSearchSpec spec = new LdapSearchSpec();
        spec.setLdapServerUuid(msg.getLdapServerUuid());

        List<String> boundLdapEntryList = Q.New(AccountThirdPartyAccountSourceRefVO.class)
                .eq(AccountThirdPartyAccountSourceRefVO_.accountSourceUuid, spec.getLdapServerUuid())
                .select(AccountThirdPartyAccountSourceRefVO_.credentials)
                .listValues();
        Set<String> boundLdapEntrySet = new HashSet<>(boundLdapEntryList);

        spec.setFilter(new AndFilter().and(new HardcodedFilter(msg.getLdapFilter())).toString());
        spec.setCount(msg.getLimit());
        spec.setResultFilter(dn -> !boundLdapEntrySet.contains(dn));
        reply.setInventories(createDriver().searchLdapEntry(spec));

        bus.reply(msg, reply);
    }

    private void handle(APICreateLdapBindingMsg msg) {
        APICreateLdapBindingEvent event = new APICreateLdapBindingEvent(msg.getId());
        LdapServerVO ldap = dbf.findByUuid(msg.getLdapServerUuid(), LdapServerVO.class);

        final ErrorCode errorCode = createDriver().validateDnExist(msg.getLdapUid(), ldap);
        if (errorCode != null) {
            event.setError(errorCode);
            bus.publish(event);
            return;
        }

        BindThirdPartyAccountMsg innerMsg = new BindThirdPartyAccountMsg();
        innerMsg.setSourceUuid(msg.getLdapServerUuid());
        innerMsg.setAccountUuid(msg.getAccountUuid());
        innerMsg.setCredentials(msg.getLdapUid());
        bus.makeTargetServiceIdByResourceUuid(innerMsg, AccountImportsConstant.SERVICE_ID, msg.getLdapServerUuid());
        bus.send(innerMsg, new CloudBusCallBack(msg) {
            @Override
            public void run(MessageReply reply) {
                if (!reply.isSuccess()) {
                    event.setError(reply.getError());
                    bus.publish(event);
                    return;
                }

                final ImportAccountResult result = ((BindThirdPartyAccountReply) reply).getResults().get(0);
                if (result.getRef() != null) {
                    event.setInventory(result.getRef());
                    bus.publish(event);
                    return;
                }

                event.setError(result.getError());
                bus.publish(event);
            }
        });
    }

    private void handle(APIDeleteLdapBindingMsg msg) {
        APIDeleteLdapBindingEvent event = new APIDeleteLdapBindingEvent(msg.getId());

        if (msg.getLdapServerUuid() == null) {
            bus.publish(event);
            return;
        }

        UnbindThirdPartyAccountsSpec spec = new UnbindThirdPartyAccountsSpec();
        spec.setSourceUuid(msg.getLdapServerUuid());
        spec.setSourceType(LdapConstant.LOGIN_TYPE);
        spec.setRemoveBindingOnly(true);
        spec.setSyncDeleteStrategy(SyncDeletedAccountStrategy.NoAction);
        spec.setAccountUuidList(list(msg.getAccountUuid()));

        UnbindThirdPartyAccountMsg innerMsg = new UnbindThirdPartyAccountMsg();
        innerMsg.setSpec(spec);
        bus.makeTargetServiceIdByResourceUuid(innerMsg, AccountImportsConstant.SERVICE_ID, msg.getLdapServerUuid());
        bus.send(innerMsg, new CloudBusCallBack(msg) {
            @Override
            public void run(MessageReply reply) {
                if (!reply.isSuccess()) {
                    event.setError(reply.getError());
                    bus.publish(event);
                    return;
                }

                final UnbindThirdPartyAccountResult result = ((UnbindThirdPartyAccountReply) reply).getResults().get(0);
                if (result.getError() != null) {
                    event.setError(result.getError());
                }
                bus.publish(event);
            }
        });
    }

    private void handle(APIUpdateLdapServerMsg msg) {
        APIUpdateLdapServerEvent event = new APIUpdateLdapServerEvent(msg.getId());

        LdapAccountSourceSpec spec = new LdapAccountSourceSpec();
        spec.setUuid(msg.getLdapServerUuid());
        spec.setServerName(msg.getName());
        spec.setDescription(msg.getDescription());
        spec.setType(LdapConstant.LOGIN_TYPE);
        spec.setUrl(msg.getUrl());
        spec.setBaseDn(msg.getBase());
        spec.setLogInUserName(msg.getUsername());
        spec.setLogInPassword(msg.getPassword());
        spec.setEncryption(msg.getEncryption() == null ? null : LdapEncryptionType.valueOf(msg.getEncryption()));
        spec.setServerType(msg.getServerType() == null ? null : LdapServerType.valueOf(msg.getServerType()));
        spec.setCreateAccountStrategy(msg.getSyncCreatedAccountStrategy() == null ? null :
                SyncCreatedAccountStrategy.valueOf(msg.getSyncCreatedAccountStrategy()));
        spec.setDeleteAccountStrategy(msg.getSyncDeletedAccountStrategy() == null ? null :
                SyncDeletedAccountStrategy.valueOf(msg.getSyncDeletedAccountStrategy()));
        spec.setUsernameProperty(msg.getUsernameProperty());
        spec.setFilter(msg.getFilter());

        UpdateLdapAccountSourceMsg innerMsg = new UpdateLdapAccountSourceMsg();
        innerMsg.setSpec(spec);
        bus.makeTargetServiceIdByResourceUuid(innerMsg, AccountImportsConstant.SERVICE_ID, spec.getUuid());
        bus.send(innerMsg, new CloudBusCallBack(msg) {
            @Override
            public void run(MessageReply reply) {
                if (reply.isSuccess()) {
                    event.setInventory(((UpdateLdapAccountSourceReply) reply).getInventory());
                } else {
                    event.setError(reply.getError());
                }
                bus.publish(event);
            }
        });
    }

    private void handle(APISyncAccountsFromLdapServerMsg msg) {
        SyncThirdPartyAccountMsg innerMsg = new SyncThirdPartyAccountMsg();
        innerMsg.setSourceUuid(msg.getUuid());

        if (msg.getCreateAccountStrategy() != null) {
            innerMsg.setCreateAccountStrategy(SyncCreatedAccountStrategy.valueOf(msg.getCreateAccountStrategy()));
        }
        if (msg.getDeleteAccountStrategy() != null) {
            innerMsg.setDeleteAccountStrategy(SyncDeletedAccountStrategy.valueOf(msg.getDeleteAccountStrategy()));
        }

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
    public ErrorableValue<String> findCurrentLdapServerUuid() {
        final String currentLdapUuid = LdapGlobalConfig.CURRENT_LDAP_SERVER_UUID.value(String.class);
        if (CURRENT_LDAP_UUID_NONE.equals(currentLdapUuid)) {
            return ErrorableValue.ofErrorCode(err(NONE_LDAP_SERVER_ENABLED, "No LDAP server is currently enabled"));
        }
        return ErrorableValue.of(currentLdapUuid);
    }

    @Override
    public ErrorableValue<LdapServerVO> findCurrentLdapServer() {
        ErrorableValue<String> errorableValue = findCurrentLdapServerUuid();
        if (!errorableValue.isSuccess()) {
            return ErrorableValue.ofErrorCode(errorableValue.error);
        }

        LdapServerVO ldap = dbf.findByUuid(errorableValue.result, LdapServerVO.class);
        if (ldap == null) {
            return ErrorableValue.ofErrorCode(
                    err(UNABLE_TO_FIND_LDAP_SERVER, "GlobalConfig[%s] is invalid: LdapServer[uuid=%s] is not exists",
                            LdapGlobalConfig.CURRENT_LDAP_SERVER_UUID.getName(),
                            errorableValue.result));
        }
        return ErrorableValue.of(ldap);
    }

    @Override
    public LoginType getLoginType() {
        return loginType;
    }

    @Override
    public void login(LoginContext loginContext, ReturnValueCompletion<LoginSessionInfo> completion) {
        final ErrorableValue<LdapServerVO> currentLdapServer = findCurrentLdapServer();
        if (!currentLdapServer.isSuccess()) {
            logger.debug("failed to login by LDAP: failed to find current LdapServer: " + currentLdapServer.error.getDetails());
            completion.fail(err(IdentityErrors.AUTHENTICATION_ERROR,
                    "Login validation failed in LDAP"));
            return;
        }
        final LdapServerVO ldap = currentLdapServer.result;

        String ldapLoginName = loginContext.getUsername();
        if (!isValid(ldapLoginName, loginContext.getPassword())) {
            completion.fail(err(IdentityErrors.AUTHENTICATION_ERROR,
                    "Login validation failed in LDAP"));
            return;
        }

        String dn = createDriver().getFullUserDn(ldap, ldap.getUsernameProperty(), ldapLoginName);
        AccountThirdPartyAccountSourceRefVO vo = Q.New(AccountThirdPartyAccountSourceRefVO.class)
                .eq(AccountThirdPartyAccountSourceRefVO_.credentials, dn)
                .eq(AccountThirdPartyAccountSourceRefVO_.accountSourceUuid, ldap.getUuid())
                .find();

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
        final ErrorableValue<LdapServerVO> ldap = findCurrentLdapServer();
        if (ldap.isSuccess()) {
            return createDriver().isValid(username, password, ldap.result);
        }
        return false;
    }

    @Override
    public String getUserIdByName(String username) {
        final ErrorableValue<LdapServerVO> property = findCurrentLdapServer();
        if (property.isSuccess()) {
            return createDriver().getFullUserDn(username, property.result);
        }
        return null;
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
