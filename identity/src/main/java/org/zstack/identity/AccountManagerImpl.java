package org.zstack.identity;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.zstack.core.Platform;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.cloudbus.CloudBusCallBack;
import org.zstack.core.cloudbus.EventFacade;
import org.zstack.core.cloudbus.MessageSafe;
import org.zstack.core.componentloader.PluginRegistry;
import org.zstack.core.config.*;
import org.zstack.core.db.*;
import org.zstack.core.db.SimpleQuery.Op;
import org.zstack.core.thread.ChainTask;
import org.zstack.core.thread.SyncTask;
import org.zstack.core.thread.SyncTaskChain;
import org.zstack.core.thread.ThreadFacade;
import org.zstack.core.workflow.SimpleFlowChain;
import org.zstack.header.APIIsOpensourceVersionMsg;
import org.zstack.header.APIIsOpensourceVersionReply;
import org.zstack.header.AbstractService;
import org.zstack.header.apimediator.ApiMediatorConstant;
import org.zstack.header.apimediator.ApiMessageInterceptionException;
import org.zstack.header.apimediator.ApiMessageInterceptor;
import org.zstack.header.core.NoErrorCompletion;
import org.zstack.header.core.workflow.*;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.errorcode.OperationFailureException;
import org.zstack.header.errorcode.SysErrors;
import org.zstack.header.exception.CloudRuntimeException;
import org.zstack.header.identity.*;
import org.zstack.header.identity.Quota.QuotaPair;
import org.zstack.header.identity.quota.QuotaDefinition;
import org.zstack.header.identity.quota.QuotaMessageHandler;
import org.zstack.header.identity.login.LogInMsg;
import org.zstack.header.identity.login.LogInReply;
import org.zstack.header.identity.login.LoginManager;
import org.zstack.header.managementnode.PrepareDbInitialValueExtensionPoint;
import org.zstack.header.message.*;
import org.zstack.header.rest.RestAuthenticationBackend;
import org.zstack.header.rest.RestAuthenticationParams;
import org.zstack.header.rest.RestAuthenticationType;
import org.zstack.header.vo.*;
import org.zstack.identity.rbac.PolicyUtils;
import org.zstack.utils.*;
import org.zstack.utils.gson.JSONObjectUtil;
import org.zstack.utils.logging.CLogger;

import javax.persistence.Query;
import javax.persistence.Tuple;
import javax.persistence.TypedQuery;
import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static org.zstack.core.Platform.*;
import static org.zstack.header.identity.AccountConstant.ACCOUNT_REST_AUTHENTICATION_TYPE;
import static org.zstack.utils.CollectionUtils.isEmpty;

@SuppressWarnings({"rawtypes", "unchecked"})
public class AccountManagerImpl extends AbstractService implements AccountManager, SoftDeleteEntityExtensionPoint,
        HardDeleteEntityExtensionPoint, ApiMessageInterceptor, RestAuthenticationBackend, PrepareDbInitialValueExtensionPoint {
    private static final CLogger logger = Utils.getLogger(AccountManagerImpl.class);

    @Autowired
    private CloudBus bus;
    @Autowired
    private DatabaseFacade dbf;
    @Autowired
    private ThreadFacade thdf;
    @Autowired
    private PluginRegistry pluginRgty;
    @Autowired
    private EventFacade evtf;
    @Autowired
    private List<QuotaUpdateChecker> quotaChangeCheckers = Collections.emptyList();

    private final List<Class> resourceTypes = new ArrayList<>();
    private final Map<Class, List<Quota>> messageQuotaMap = new HashMap<>();
    private final Map<String, Quota> nameQuotaMap = new HashMap<>();

    private final Map<Class, List<QuotaMessageHandler<? extends Message>>> messageHandlerMap = new HashMap<>();

    private static final Map<String, QuotaDefinition> quotaDefinitionMap = new HashMap<>();

    @Override
    public void prepareDbInitialValue() {
        new SQLBatch() {
            @Override
            protected void scripts() {
                if (!q(AccountVO.class)
                        .eq(AccountVO_.name, AccountConstant.INITIAL_SYSTEM_ADMIN_NAME)
                        .eq(AccountVO_.type, AccountType.SystemAdmin).isExists()) {
                    AccountVO vo = new AccountVO();
                    vo.setUuid(AccountConstant.INITIAL_SYSTEM_ADMIN_UUID);
                    vo.setName(AccountConstant.INITIAL_SYSTEM_ADMIN_NAME);
                    vo.setPassword(AccountConstant.INITIAL_SYSTEM_ADMIN_PASSWORD);
                    vo.setType(AccountType.SystemAdmin);
                    vo.setState(AccountState.Enabled);
                    persist(vo);
                    flush();

                    logger.debug(String.format("Created initial system admin account[name:%s]", AccountConstant.INITIAL_SYSTEM_ADMIN_NAME));
                }
            }
        }.execute();
    }
    static class AccountCheckField {
        Field field;
        APIParam param;
    }

    private Future<Void> expiredSessionCollector;

    @Override
    @MessageSafe
    public void handleMessage(Message msg) {
        if (msg instanceof AccountMessage) {
            passThrough((AccountMessage) msg);
        } else if (msg instanceof APIMessage) {
            handleApiMessage((APIMessage) msg);
        } else {
            handleLocalMessage(msg);
        }
    }

    private void handleLocalMessage(Message msg) {
        if (msg instanceof CreateAccountMsg) {
            handle((CreateAccountMsg) msg);
        } else {
            bus.dealWithUnknownMessage(msg);
        }

    }

    @Override
    public Map<Class, List<Quota>> getMessageQuotaMap() {
        return messageQuotaMap;
    }

    public Map<Class, List<QuotaMessageHandler<? extends Message>>> getQuotaMessageHandlerMap() {
        return messageHandlerMap;
    }

    @Override
    public Map<String, QuotaDefinition> getQuotasDefinitions() {
        return quotaDefinitionMap;
    }

    @Override
    @Transactional
    public AccountResourceRefInventory changeResourceOwner(String resourceUuid, String newOwnerUuid) {
        String sql = "select ref from AccountResourceRefVO ref where ref.resourceUuid = :resUuid";
        TypedQuery<AccountResourceRefVO> q = dbf.getEntityManager().createQuery(sql, AccountResourceRefVO.class);
        q.setParameter("resUuid", resourceUuid);
        List<AccountResourceRefVO> refs = q.getResultList();
        if (refs.isEmpty()) {
            throw new OperationFailureException(argerr("cannot find the resource[uuid:%s]; wrong resourceUuid or the resource is admin resource",
                            resourceUuid));
        }

        AccountResourceRefVO ref = refs.get(0);
        final AccountResourceRefInventory origin = AccountResourceRefInventory.valueOf(ref);

        for (ResourceOwnerPreChangeExtensionPoint ext : pluginRgty.getExtensionList(ResourceOwnerPreChangeExtensionPoint.class)) {
            ext.resourceOwnerPreChange(origin, newOwnerUuid);
        }

        ref.setAccountUuid(newOwnerUuid);
        ref.setOwnerAccountUuid(newOwnerUuid);
        ref = dbf.getEntityManager().merge(ref);

        CollectionUtils.safeForEach(
                pluginRgty.getExtensionList(ResourceOwnerAfterChangeExtensionPoint.class),
                ext -> ext.resourceOwnerAfterChange(origin, newOwnerUuid));

        return AccountResourceRefInventory.valueOf(ref);
    }

    @Override
    public boolean isAdmin(SessionInventory session) {
        return AccountConstant.INITIAL_SYSTEM_ADMIN_UUID.equals(session.getAccountUuid());
    }

    private void passThrough(AccountMessage msg) {
        AccountVO vo = dbf.findByUuid(msg.getAccountUuid(), AccountVO.class);
        if (vo == null) {
            bus.replyErrorByMessageType((Message) msg, err(SysErrors.RESOURCE_NOT_FOUND, "unable to find account[uuid=%s]", msg.getAccountUuid()));
            return;
        }

        AccountBase base = new AccountBase(vo);
        base.handleMessage((Message) msg);
    }

    private void handleApiMessage(APIMessage msg) {
        if (msg instanceof APICreateAccountMsg) {
            handle((APICreateAccountMsg) msg);
        } else if (msg instanceof APILogInByAccountMsg) {
            handle((APILogInByAccountMsg) msg);
        } else if (msg instanceof APILogInByUserMsg) {
            handle((APILogInByUserMsg) msg);
        } else if (msg instanceof APILogOutMsg) {
            handle((APILogOutMsg) msg);
        } else if (msg instanceof APIValidateSessionMsg) {
            handle((APIValidateSessionMsg) msg);
        } else if (msg instanceof APIGetResourceAccountMsg) {
            handle((APIGetResourceAccountMsg) msg);
        } else if (msg instanceof APIChangeResourceOwnerMsg) {
            handle((APIChangeResourceOwnerMsg) msg);
        } else if (msg instanceof APIGetResourceNamesMsg) {
            handle((APIGetResourceNamesMsg) msg);
        } else if (msg instanceof APIIsOpensourceVersionMsg) {
            handle((APIIsOpensourceVersionMsg) msg);
        } else if (msg instanceof APIRenewSessionMsg) {
            handle((APIRenewSessionMsg) msg);
        } else if (msg instanceof APIGetSupportedIdentityModelsMsg) {
            handle((APIGetSupportedIdentityModelsMsg) msg);
        } else {
            bus.dealWithUnknownMessage(msg);
        }
    }

    private void handle(APIGetSupportedIdentityModelsMsg msg) {
        APIGetSupportedIdentityModelsReply reply = new APIGetSupportedIdentityModelsReply();
        reply.setConfigs(Arrays.asList(IdentityGlobalProperty.IDENTITY_INIT_TYPE.split(",")));
        bus.reply(msg, reply);
    }

    private void handle(APIIsOpensourceVersionMsg msg) {
        APIIsOpensourceVersionReply reply = new APIIsOpensourceVersionReply();
        reply.setOpensource(true);
        bus.reply(msg, reply);
    }

    private void handle(APIGetResourceNamesMsg msg) {
        List<ResourceInventory> invs = new SQLBatchWithReturn<List<ResourceInventory>>() {
            @Override
            protected List<ResourceInventory> scripts() {
                Query q = dbf.getEntityManager().createNativeQuery("select uuid, resourceName, resourceType from ResourceVO where uuid in (:uuids)");
                q.setParameter("uuids", msg.getUuids());
                List<Object[]> objs = q.getResultList();

                List<ResourceVO> vos = objs.stream().map(ResourceVO::new).collect(Collectors.toList());

                return ResourceInventory.valueOf(vos);
            }
        }.execute();

        APIGetResourceNamesReply reply = new APIGetResourceNamesReply();
        reply.setInventories(invs);
        bus.reply(msg, reply);
    }

    private void handle(final APIChangeResourceOwnerMsg msg) {
        thdf.chainSubmit(new ChainTask(msg) {
            @Override
            public String getSyncSignature() {
                return String.format("change-resource-owner-to-account-%s", msg.getAccountUuid());
            }

            @Override
            public void run(SyncTaskChain chain) {
                doChangeResourceOwnerInQueue(msg, new NoErrorCompletion(chain) {
                    @Override
                    public void done() {
                        chain.next();
                    }
                });
            }

            @Override
            public String getName() {
                return getSyncSignature();
            }
        });
    }

    private void doChangeResourceOwnerInQueue(APIChangeResourceOwnerMsg msg, NoErrorCompletion completion) {
        APIChangeResourceOwnerEvent evt = new APIChangeResourceOwnerEvent(msg.getId());

        FlowChain chain = new SimpleFlowChain();
        chain.then(new NoRollbackFlow() {
            @Override
            public void run(FlowTrigger trigger, Map data) {
                String currentAccountUuid = msg.getSession().getAccountUuid();
                String resourceTargetOwnerAccountUuid = msg.getAccountUuid();
                if (new QuotaUtil().isAdminAccount(resourceTargetOwnerAccountUuid)) {
                    trigger.next();
                    return;
                }
                // check if change resource owner to self
                SimpleQuery<AccountResourceRefVO> queryAccResRefVO = dbf.createQuery(AccountResourceRefVO.class);
                queryAccResRefVO.add(AccountResourceRefVO_.resourceUuid, Op.EQ, msg.getResourceUuid());
                AccountResourceRefVO accResRefVO = queryAccResRefVO.find();
                String resourceOriginalOwnerAccountUuid = accResRefVO.getOwnerAccountUuid();
                if (resourceTargetOwnerAccountUuid.equals(resourceOriginalOwnerAccountUuid)) {
                    trigger.fail(err(IdentityErrors.QUOTA_INVALID_OP,
                            "Invalid ChangeResourceOwner operation." +
                                    "Original owner is the same as target owner." +
                                    "Current account is [uuid: %s]." +
                                    "The resource target owner account[uuid: %s]." +
                                    "The resource original owner account[uuid:%s].",
                            currentAccountUuid, resourceTargetOwnerAccountUuid, resourceOriginalOwnerAccountUuid
                    ));
                    return;
                }
                // check quota
                new QuotaUtil().checkQuota(msg, resourceOriginalOwnerAccountUuid, msg.getAccountUuid());
                trigger.next();
            }
        }).then(new NoRollbackFlow() {
            @Override
            public void run(FlowTrigger trigger, Map data) {
                evt.setInventory(changeResourceOwner(msg.getResourceUuid(), msg.getAccountUuid()));
                trigger.next();
            }
        }).done(new FlowDoneHandler(msg) {
            @Override
            public void handle(Map data) {
                bus.publish(evt);
                completion.done();
            }
        }).error(new FlowErrorHandler(msg) {
            @Override
            public void handle(ErrorCode errCode, Map data) {
                evt.setError(errCode);
                bus.publish(evt);
                completion.done();
            }
        }).start();
    }


    @Transactional(readOnly = true)
    private void handle(APIGetResourceAccountMsg msg) {
        String sql = "select a, ref.resourceUuid" +
                " from AccountResourceRefVO ref, AccountVO a" +
                " where a.uuid = ref.accountUuid" +
                " and ref.resourceUuid in (:uuids)";
        TypedQuery<Tuple> q = dbf.getEntityManager().createQuery(sql, Tuple.class);
        q.setParameter("uuids", msg.getResourceUuids());
        List<Tuple> tuples = q.getResultList();
        Map<String, AccountInventory> ret = new HashMap<>();
        for (Tuple t : tuples) {
            String resUuid = t.get(1, String.class);
            AccountVO vo = t.get(0, AccountVO.class);
            ret.put(resUuid, AccountInventory.valueOf(vo));
        }

        AccountVO admin = dbf.findByUuid(AccountConstant.INITIAL_SYSTEM_ADMIN_UUID, AccountVO.class);
        AccountInventory adminInv = AccountInventory.valueOf(admin);
        for (String resUuid : msg.getResourceUuids()) {
            ret.putIfAbsent(resUuid, adminInv);
        }

        APIGetResourceAccountReply reply = new APIGetResourceAccountReply();
        reply.setInventories(ret);
        bus.reply(msg, reply);
    }

    private void handle(APIValidateSessionMsg msg) {
        APIValidateSessionReply reply = new APIValidateSessionReply();

        ErrorCode errorCode = Session.checkSessionExpired(msg.getSessionUuid());

        boolean valid = errorCode == null;

        reply.setValidSession(valid);

        bus.reply(msg, reply);
    }

    private void handle(APIRenewSessionMsg msg) {
        APIRenewSessionEvent evt = new APIRenewSessionEvent(msg.getId());
        evt.setInventory(Session.renewSession(msg.getSessionUuid(), msg.getDuration()));
        bus.publish(evt);
    }

    private void handle(APILogOutMsg msg) {
        APILogOutReply reply = new APILogOutReply();
        SessionInventory session = Session.getSession(msg.getSessionUuid());
        if (session == null) {
            SessionVO svo = dbf.findByUuid(msg.getSessionUuid(), SessionVO.class);
            session = svo == null ? null : SessionInventory.valueOf(svo);
        }
        msg.setSession(session);

        for (LogoutExtensionPoint ext : pluginRgty.getExtensionList(LogoutExtensionPoint.class)) {
            ext.beforeLogout(session);
        }

        logOutSession(msg.getSessionUuid());
        bus.reply(msg, reply);
    }

    private SessionInventory getSession(String accountUuid, String userUuid) {
        return Session.login(accountUuid, userUuid);
    }

    private void handle(APILogInByUserMsg msg) {
        APILogInReply reply = new APILogInReply();

        String accountUuid;
        if (msg.getAccountUuid() != null) {
            accountUuid = msg.getAccountUuid();
        } else {
            SimpleQuery<AccountVO> accountq = dbf.createQuery(AccountVO.class);
            accountq.select(AccountVO_.uuid);
            accountq.add(AccountVO_.name, Op.EQ, msg.getAccountName());
            accountUuid = accountq.findValue();
            if (accountUuid == null) {
                reply.setError(err(IdentityErrors.AUTHENTICATION_ERROR, "wrong account or username or password"));
                bus.reply(msg, reply);
                return;
            }
        }

        SimpleQuery<UserVO> q = dbf.createQuery(UserVO.class);
        q.add(UserVO_.accountUuid, Op.EQ, accountUuid);
        q.add(UserVO_.password, Op.EQ, msg.getPassword());
        q.add(UserVO_.name, Op.EQ, msg.getUserName());
        UserVO user = q.find();

        if (user == null) {
            reply.setError(err(IdentityErrors.AUTHENTICATION_ERROR,
                    "wrong account or username or password"
            ));
            bus.reply(msg, reply);
            return;
        }
        SessionInventory session = getSession(user.getAccountUuid(), user.getUuid());
        msg.setSession(session);
        reply.setInventory(session);
        bus.reply(msg, reply);
    }

    private void handle(APILogInByAccountMsg msg) {
        APILogInReply reply = new APILogInReply();

        LogInMsg logInMsg = new LogInMsg();
        logInMsg.setVerifyCode(msg.getVerifyCode());
        logInMsg.setCaptchaUuid(msg.getCaptchaUuid());
        logInMsg.setPassword(msg.getPassword());
        logInMsg.setUsername(msg.getAccountName());
        logInMsg.setLoginType(msg.getLoginType());
        logInMsg.setSystemTags(msg.getSystemTags());
        logInMsg.setClientInfo(msg.getClientInfo());
        logInMsg.getProperties().put(AccountConstant.ACCOUNT_TYPE, msg.getAccountType());
        bus.makeTargetServiceIdByResourceUuid(logInMsg, LoginManager.SERVICE_ID, logInMsg.getUsername());
        bus.send(logInMsg, new CloudBusCallBack(msg) {
            @Override
            public void run(MessageReply r) {
                if (!r.isSuccess()) {
                    reply.setError(r.getError());
                    bus.reply(msg, reply);
                    return;
                }

                LogInReply logInReply = r.castReply();
                IdentityCanonicalEvents.AccountLoginData data = new IdentityCanonicalEvents.AccountLoginData();
                data.setAccountUuid(logInReply.getSession().getAccountUuid());
                data.setUserUuid(logInReply.getSession().getUserUuid());
                evtf.fire(IdentityCanonicalEvents.ACCOUNT_LOGIN_PATH, data);

                reply.setInventory(logInReply.getSession());
                bus.reply(msg, reply);
            }
        });
    }

    private void handle(CreateAccountMsg msg) {
        AccountInventory inv = createAccount(msg);
        CreateAccountReply reply = new CreateAccountReply();
        reply.setInventory(inv);
        bus.reply(msg, reply);
    }

    public AccountInventory createAccount(CreateAccountMsg msg) {
        final AccountInventory inv = new SQLBatchWithReturn<AccountInventory>() {
            @Override
            protected AccountInventory scripts() {
                AccountVO vo = new AccountVO();
                if (msg.getUuid() != null) {
                    vo.setUuid(msg.getUuid());
                } else {
                    vo.setUuid(Platform.getUuid());
                }
                vo.setName(msg.getName());
                vo.setDescription(msg.getDescription());
                vo.setPassword(msg.getPassword());
                vo.setType(msg.getType() != null ? AccountType.valueOf(msg.getType()) : AccountType.Normal);
                vo.setState(msg.getState() == null ? AccountState.Enabled : msg.getState());
                persist(vo);
                reload(vo);

                PolicyVO p = new PolicyVO();
                p.setUuid(Platform.getUuid());
                p.setAccountUuid(vo.getUuid());
                p.setName("DEFAULT-READ");
                p.setData(IAMIdentityResourceGenerator.readAPIsForNormalAccountJSONStatement);
                persist(p);
                reload(p);

                List<Tuple> ts = Q.New(GlobalConfigVO.class).select(GlobalConfigVO_.name, GlobalConfigVO_.value)
                        .eq(GlobalConfigVO_.category, AccountConstant.QUOTA_GLOBAL_CONFIG_CATETORY).listTuple();

                for (Tuple t : ts) {
                    String rtype = t.get(0, String.class);
                    long quota = Long.parseLong(t.get(1, String.class));

                    QuotaVO qvo = new QuotaVO();
                    qvo.setUuid(Platform.getUuid());
                    qvo.setIdentityType(AccountVO.class.getSimpleName());
                    qvo.setIdentityUuid(vo.getUuid());
                    qvo.setName(rtype);
                    qvo.setValue(quota);
                    qvo.setAccountUuid(vo.getUuid());
                    persist(qvo);
                    reload(qvo);
                }

                reload(vo);
                return AccountInventory.valueOf(vo);
            }
        }.execute();

        CollectionUtils.safeForEach(pluginRgty.getExtensionList(BeforeCreateAccountExtensionPoint.class),
                arg -> arg.beforeCreateAccount(inv));

        CollectionUtils.safeForEach(pluginRgty.getExtensionList(AfterCreateAccountExtensionPoint.class),
                arg -> arg.afterCreateAccount(inv));

        return inv;
    }

    private void handle(APICreateAccountMsg msg) {
        CreateAccountMsg accountMsg = new CreateAccountMsg();
        if (msg.getResourceUuid() != null) {
            accountMsg.setUuid(msg.getResourceUuid());
        } else {
            accountMsg.setUuid(Platform.getUuid());
        }
        accountMsg.setName(msg.getName());
        accountMsg.setDescription(msg.getDescription());
        accountMsg.setPassword(msg.getPassword());
        accountMsg.setType(msg.getType());
        accountMsg.setState(AccountState.valueOf(msg.getState()));
        bus.makeTargetServiceIdByResourceUuid(accountMsg, AccountConstant.SERVICE_ID, accountMsg.getUuid());
        bus.send(accountMsg, new CloudBusCallBack(msg) {
            @Override
            public void run(MessageReply reply) {
                APICreateAccountEvent evt = new APICreateAccountEvent(msg.getId());
                if (!reply.isSuccess()) {
                    evt.setError(reply.getError());
                } else {
                    CreateAccountReply reply1 = reply.castReply();
                    evt.setInventory(reply1.getInventory());
                }
                bus.publish(evt);
            }
        });

    }

    @Override
    public String getId() {
        return bus.makeLocalServiceId(AccountConstant.SERVICE_ID);
    }

    private void buildResourceTypes() throws ClassNotFoundException {
        for (AddtionalResourceTypeExtensionPoint ext: pluginRgty.getExtensionList(AddtionalResourceTypeExtensionPoint.class)) {
            final List<String> typeNameList = ext.getAddtionalResourceType();
            if (isEmpty(typeNameList)) {
                continue;
            }
            for (String resourceTypeName : typeNameList) {
                resourceTypes.add(Class.forName(resourceTypeName));
            }
        }

        resourceTypes.addAll(getReflections().getSubTypesOf(OwnedByAccount.class));
    }

    @Override
    public boolean start() {
        try {
            buildResourceTypes();
            collectDefaultQuota();
            updateResourceVONameOnEntityUpdate();
            installNeedQuotaCheckMessageHandlers();
        } catch (Exception e) {
            throw new CloudRuntimeException(e);
        }
        return true;
    }

    private void installNeedQuotaCheckMessageHandlers() {
        bus.installBeforeDeliveryMessageInterceptor(new BeforeDeliveryMessageInterceptor() {
            @Override
            public int orderOfBeforeDeliveryMessageInterceptor() {
                return -100;
            }

            @Override
            public void beforeDeliveryMessage(Message msg) {
                if (msg.getServiceId().equals(ApiMediatorConstant.SERVICE_ID)) {
                    // the API message will be routed by ApiMediator,
                    // filter out this message to avoid reporting the same
                    // API message twice
                    return;
                }

                if (!(msg instanceof NeedQuotaCheckMessage)) {
                    return;
                }

                String accountUuid = ((NeedQuotaCheckMessage) msg).getAccountUuid();
                if (accountUuid == null) {
                    logger.warn(String.format("missing accountUuid of message[id: %s]," +
                            " skip quota check to keep compatible", msg.getId()));
                    return;
                }

                new QuotaUtil().checkQuota(msg, accountUuid, accountUuid);
            }
        }, new ArrayList<>());
    }

    private void updateResourceVONameOnEntityUpdate() {
        dbf.installEntityLifeCycleCallback(null, EntityEvent.PRE_UPDATE, (evt, o) -> {
            if (o instanceof ResourceVO) {
                ResourceVO rvo = (ResourceVO) o;
                if (rvo.hasNameField()) {
                    String name = rvo.getValueOfNameField();
                    if (name != null) {
                        rvo.setResourceName(name);
                    }
                }
            }
        });
    }

    private void collectDefaultQuota() {
        // Add quota definition and quota message checker
        for (ReportQuotaExtensionPoint ext : pluginRgty.getExtensionList(ReportQuotaExtensionPoint.class)) {
            List<Quota> quotas = ext.reportQuota();
            DebugUtils.Assert(quotas != null, String.format("%s.reportQuota() returns null", ext.getClass()));

            for (Quota quota : quotas) {
                DebugUtils.Assert(quota.getQuotaDefinitions() != null
                                || !quota.getQuotaMessageCheckerList().isEmpty(),
                        String.format("%s reports a quota containing a null quotaDefinitions and message handlers", ext.getClass()));
                collectQuotaDefinitions(quota, ext);
                checkDeprecatedQuotaPairs(quota);
                collectQuotaMessageCheckers(quota);
            }
        }

        repairAccountQuota();
    }

    private void collectQuotaMessageCheckers(Quota quota) {
        for (QuotaMessageHandler<? extends Message> checker : quota.getQuotaMessageCheckerList()) {
            if (messageHandlerMap.containsKey(checker.messageClass)) {
                messageHandlerMap.get(checker.messageClass).add(checker);
            } else {
                List<QuotaMessageHandler<? extends Message>> checkers = new ArrayList<>();
                checkers.add(checker);
                messageHandlerMap.put(checker.messageClass, checkers);
            }
        }
    }

    private void collectQuotaDefinitions(Quota quota, ReportQuotaExtensionPoint ext) {
        if (quota.getQuotaDefinitions() == null) {
            return;
        }

        for (QuotaDefinition d : quota.getQuotaDefinitions()) {
            if (nameQuotaMap.containsKey(d.getName())) {
                throw new CloudRuntimeException(String.format("duplicate DefaultQuota[resourceType: %s] reported by %s", d.getName(), ext.getClass()));
            }

            nameQuotaMap.put(d.getName(), quota);
            quotaDefinitionMap.put(d.getName(), d);
        }
    }

    private void checkDeprecatedQuotaPairs(Quota quota) {
        if (quota.getQuotaPairs() == null) {
            return;
        }

        for (QuotaPair d : quota.getQuotaPairs()) {
            logger.warn(String.format("Deprecated QuotaPair[name: %s, value: %d] is still used",
                    d.getName(), d.getValue()));
        }
        throw new CloudRuntimeException("QuotaPair is not supported now, use QuotaDefinition instead");
    }

    private void repairAccountQuota() {
        new SQLBatch() {
            @Override
            protected void scripts() {
                long count = q(AccountVO.class)
                        .eq(AccountVO_.type, AccountType.Normal)
                        .count();
                sql("select uuid from AccountVO account where account.type = :type", String.class)
                        .param("type", AccountType.Normal)
                        .limit(2000)
                        .paginate(count, (List<String> normalAccounts) -> {
                            ConcurrentLinkedQueue<QuotaVO> needPersistQuotas = new ConcurrentLinkedQueue<>();
                            normalAccounts.parallelStream().forEach(nA -> {
                                List<String> existingQuota = q(QuotaVO.class).select(QuotaVO_.name).eq(QuotaVO_.identityUuid, nA).listValues();
                                for (Map.Entry<String, QuotaDefinition> e : quotaDefinitionMap.entrySet()) {
                                    String rtype = e.getKey();
                                    Long value = e.getValue().getDefaultValue();
                                    if (existingQuota.contains(rtype)) {
                                        continue;
                                    }

                                    QuotaVO q = new QuotaVO();
                                    q.setUuid(Platform.getUuid());
                                    q.setAccountUuid(nA);
                                    q.setName(rtype);
                                    q.setIdentityUuid(nA);
                                    q.setIdentityType(AccountVO.class.getSimpleName());
                                    q.setValue(value);
                                    needPersistQuotas.add(q);

                                    if (logger.isTraceEnabled()) {
                                        logger.trace(String.format("create default quota[name: %s, value: %s] global config", rtype, value));
                                    }
                                }
                            });
                            
                            needPersistQuotas.forEach(this::persist);
                        });
            }
        }.execute();
    }

    private void doAdminAdoptResource(List<String> resourceUuids, String originAccountUuid) {
        List<String> orphanedResources = new ArrayList<>();
        final List<TakeOverResourceExtensionPoint> exts = pluginRgty.getExtensionList(TakeOverResourceExtensionPoint.class);

        new SQLBatch() {
            @Override
            protected void scripts() {
                // use native SQL instead of JPQL here,
                // JPQL will join all sub-tables of ResourceVO, which
                // exceeds the limit of max tables MySQL can join
                List rvos = databaseFacade.getEntityManager().createNativeQuery("select uuid, resourceType, concreteResourceType from ResourceVO where uuid not in (select resourceUuid from AccountResourceRefVO)" +
                        " and resourceType in (:rtypes)")
                        .setParameter("rtypes", ResourceTypeMetadata.getAllBaseTypes().stream().map(Class::getSimpleName).collect(Collectors.toList()))
                        .getResultList();

                rvos.forEach(obj -> {
                    Object[] values = (Object[]) obj;
                    String ruuid = values[0].toString();
                    String rtype = values[1].toString();
                    String crtype = values[2].toString();

                    AccountResourceRefVO ref = new AccountResourceRefVO();
                    ref.setAccountUuid(AccountConstant.INITIAL_SYSTEM_ADMIN_UUID);
                    ref.setResourceType(rtype);
                    ref.setConcreteResourceType(crtype);
                    ref.setResourceUuid(ruuid);
                    ref.setPermission(AccountConstant.RESOURCE_PERMISSION_WRITE);
                    ref.setOwnerAccountUuid(ref.getAccountUuid());
                    ref.setShared(false);
                    persist(ref);
                    orphanedResources.add(ruuid);
                });
            }
        }.execute();

        if (orphanedResources.isEmpty() || resourceUuids.isEmpty()) {
            return;
        }
        List<String> uuids = resourceUuids.stream().filter(orphanedResources::contains).collect(Collectors.toList());

        CollectionUtils.forEach(exts,
                ext -> ext.afterTakeOverResource(uuids, originAccountUuid, AccountConstant.INITIAL_SYSTEM_ADMIN_UUID));
    }

    public void adminAdoptAllOrphanedResource(List<String> resourceUuid, String originAccountUuid){
        thdf.syncSubmit(new SyncTask<Void>() {
            @Override
            public Void call() {
                doAdminAdoptResource(resourceUuid, originAccountUuid);
                return null;
            }

            @Override
            public String getName() {
                return "admin-adopt-all-orphaned-resource";
            }

            @Override
            public String getSyncSignature() {
                return "admin-adopt-all-orphaned-resource";
            }

            @Override
            public int getSyncLevel() {
                return 1;
            }
        });
    }

    @Override
    public Class getBaseResourceType(Class clz) {
        for (Class c : resourceTypes) {
            if (c.isAssignableFrom(clz)) {
                return c;
            }
        }

        return null;
    }

    @Override
    public boolean stop() {
        if (expiredSessionCollector != null) {
            expiredSessionCollector.cancel(true);
        }
        return true;
    }

    @Override
    public boolean isResourceHavingAccountReference(Class entityClass) {
        for (Class clz : resourceTypes) {
            if (clz.isAssignableFrom(entityClass)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public List<String> getResourceUuidsCanAccessByAccount(String accountUuid, Class resourceType) {
        return findAllAccessResources(accountUuid, resourceType, ShareResourcePermission.READ);
    }

    @Override
    public List<String> getResourceUuidsCanAccessByAccount(String accountUuid, Class resourceType, ShareResourcePermission permission) {
        return findAllAccessResources(accountUuid, resourceType, permission);
    }

    @Transactional(readOnly = true)
    private List<String> findAllAccessResources(String accountUuid, Class resourceType, ShareResourcePermission permission) {
        String sql = "select a.type from AccountVO a where a.uuid = :auuid";
        TypedQuery<AccountType> q = dbf.getEntityManager().createQuery(sql, AccountType.class);
        q.setParameter("auuid", accountUuid);
        List<AccountType> types = q.getResultList();
        if (types.isEmpty()) {
            throw new OperationFailureException(argerr("cannot find the account[uuid:%s]", accountUuid));
        }

        AccountType atype = types.get(0);
        if (AccountType.SystemAdmin == atype) {
            return null;
        }

        sql = "select r.resourceUuid from AccountResourceRefVO r where r.accountUuid = :auuid" +
                " and r.resourceType = :rtype";
        TypedQuery<String> rq = dbf.getEntityManager().createQuery(sql, String.class);
        rq.setParameter("auuid", accountUuid);
        rq.setParameter("rtype", resourceType.getSimpleName());
        List<String> ownResourceUuids = rq.getResultList();

        sql =   "select " +
                    "r.resourceUuid " +
                "from " +
                    "SharedResourceVO r " +
                "where " +
                    "(r.toPublic = :toPublic or r.receiverAccountUuid = :auuid) " +
                    "and r.resourceType = :rtype " +
                    "and r.permission >= :permissionCode";
        TypedQuery<String> srq = dbf.getEntityManager().createQuery(sql, String.class);
        srq.setParameter("toPublic", true);
        srq.setParameter("auuid", accountUuid);
        srq.setParameter("rtype", resourceType.getSimpleName());
        srq.setParameter("permissionCode", permission.code);
        List<String> shared = srq.getResultList();
        shared.addAll(ownResourceUuids);

        return shared;
    }

    @Override
    public String getOwnerAccountUuidOfResource(String resourceUuid) {
        try {
            SimpleQuery<AccountResourceRefVO> q = dbf.createQuery(AccountResourceRefVO.class);
            q.select(AccountResourceRefVO_.ownerAccountUuid);
            q.add(AccountResourceRefVO_.resourceUuid, Op.EQ, resourceUuid);
            String ownerUuid = q.findValue();
            DebugUtils.Assert(ownerUuid != null, String.format("cannot find owner uuid for resource[uuid:%s]", resourceUuid));
            return ownerUuid;
        } catch (Exception e) {
            throw new CloudRuntimeException(e);
        }
    }

    @Override
    public List<Class> getEntityClassForSoftDeleteEntityExtension() {
        return resourceTypes;
    }

    @Override
    @Transactional
    public void postSoftDelete(Collection entityIds, Class entityClass) {
        String sql = "delete from AccountResourceRefVO ref where ref.resourceUuid in (:uuids) and ref.resourceType = :resourceType";
        Query q = dbf.getEntityManager().createQuery(sql);
        q.setParameter("uuids", entityIds);
        q.setParameter("resourceType", entityClass.getSimpleName());
        q.executeUpdate();
    }

    @Override
    public List<Class> getEntityClassForHardDeleteEntityExtension() {
        return resourceTypes;
    }

    @Override
    public void postHardDelete(Collection entityIds, Class entityClass) {
        if (resourceTypes.contains(entityClass)) {
            postSoftDelete(entityIds, entityClass);
        }
    }

    private void logOutSession(String sessionUuid) {
        SessionInventory session = Session.getSession(sessionUuid);
        if (session == null) {
            SessionVO svo = dbf.findByUuid(sessionUuid, SessionVO.class);
            session = svo == null ? null : SessionInventory.valueOf(svo);
        }

        if (session == null) {
            return;
        }

        Session.logout(sessionUuid);
    }

    @Transactional(readOnly = true)
    private Timestamp getCurrentSqlDate() {
        Query query = dbf.getEntityManager().createNativeQuery("select current_timestamp()");
        return (Timestamp) query.getSingleResult();
    }

    @Override
    public APIMessage intercept(APIMessage msg) throws ApiMessageInterceptionException {
        if (msg instanceof APIUpdateAccountMsg) {
            validate((APIUpdateAccountMsg) msg);
        } else if (msg instanceof APICreatePolicyMsg) {
            validate((APICreatePolicyMsg) msg);
        } else if (msg instanceof APIAddUserToGroupMsg) {
            validate((APIAddUserToGroupMsg) msg);
        } else if (msg instanceof APIAttachPolicyToUserGroupMsg) {
            validate((APIAttachPolicyToUserGroupMsg) msg);
        } else if (msg instanceof APIAttachPolicyToUserMsg) {
            validate((APIAttachPolicyToUserMsg) msg);
        } else if (msg instanceof APIDetachPolicyFromUserGroupMsg) {
            validate((APIDetachPolicyFromUserGroupMsg) msg);
        } else if (msg instanceof APIDetachPolicyFromUserMsg) {
            validate((APIDetachPolicyFromUserMsg) msg);
        } else if (msg instanceof APIShareResourceMsg) {
            validate((APIShareResourceMsg) msg);
        } else if (msg instanceof APIRevokeResourceSharingMsg) {
            validate((APIRevokeResourceSharingMsg) msg);
        } else if (msg instanceof APIUpdateUserMsg) {
            validate((APIUpdateUserMsg) msg);
        } else if (msg instanceof APIDeleteAccountMsg) {
            validate((APIDeleteAccountMsg) msg);
        } else if (msg instanceof APICreateAccountMsg) {
            validate((APICreateAccountMsg) msg);
        } else if (msg instanceof APICreateUserMsg) {
            validate((APICreateUserMsg) msg);
        } else if (msg instanceof APICreateUserGroupMsg) {
            validate((APICreateUserGroupMsg) msg);
        } else if (msg instanceof APILogInByUserMsg) {
            validate((APILogInByUserMsg) msg);
        } else if (msg instanceof APIGetAccountQuotaUsageMsg) {
            validate((APIGetAccountQuotaUsageMsg) msg);
        } else if (msg instanceof APIUpdateQuotaMsg) {
            validate((APIUpdateQuotaMsg) msg);
        }

        setServiceId(msg);

        return msg;
    }

    private void validate(APIGetAccountQuotaUsageMsg msg) {
        if (msg.getUuid() == null) {
            msg.setUuid(msg.getSession().getAccountUuid());
        }
    }

    private void validate(APILogInByUserMsg msg) {
        if (msg.getAccountName() == null && msg.getAccountUuid() == null) {
            throw new ApiMessageInterceptionException(argerr(
                    "accountName and accountUuid cannot both be null, you must specify at least one"
            ));
        }
    }

    private void validate(APICreateUserGroupMsg msg) {
        SimpleQuery<UserGroupVO> q = dbf.createQuery(UserGroupVO.class);
        q.add(UserGroupVO_.accountUuid, Op.EQ, msg.getAccountUuid());
        q.add(UserGroupVO_.name, Op.EQ, msg.getName());
        if (q.isExists()) {
            throw new ApiMessageInterceptionException(argerr("unable to create a group. A group called %s is already under the account[uuid:%s]",
                            msg.getName(), msg.getAccountUuid()));
        }
    }

    private void validate(APICreateUserMsg msg) {
        SimpleQuery<UserVO> q = dbf.createQuery(UserVO.class);
        q.add(UserVO_.accountUuid, Op.EQ, msg.getAccountUuid());
        q.add(UserVO_.name, Op.EQ, msg.getName());
        if (q.isExists()) {
            throw new ApiMessageInterceptionException(argerr("unable to create a user. A user called %s is already under the account[uuid:%s]",
                            msg.getName(), msg.getAccountUuid()));
        }
    }

    private void validate(APICreateAccountMsg msg) {
        SimpleQuery<AccountVO> q = dbf.createQuery(AccountVO.class);
        q.add(AccountVO_.name, Op.EQ, msg.getName());
        if (q.isExists()) {
            throw new ApiMessageInterceptionException(argerr("unable to create an account. An account already called %s", msg.getName()));
        }
    }

    private void validate(APIDeleteAccountMsg msg) {
        if (new QuotaUtil().isAdminAccount(msg.getUuid())) {
            if (msg.getAccountUuid().equals(msg.getSession().getAccountUuid())) {
                throw new ApiMessageInterceptionException(argerr(
                        "account cannot delete itself"
                ));
            }

            if (msg.getAccountUuid().equals(AccountConstant.INITIAL_SYSTEM_ADMIN_UUID)) {
                throw new ApiMessageInterceptionException(argerr(
                        "cannot delete builtin admin account."
                ));
            }
        }
        if(!new QuotaUtil().isAdminAccount(msg.getSession().getAccountUuid())){
            throw new ApiMessageInterceptionException(argerr(
                    "Only admin can delete account."
            ));
        }
    }

    private void validate(APIUpdateUserMsg msg) {
        if (msg.getUuid() == null && msg.getSession().isAccountSession()) {
            throw new ApiMessageInterceptionException(argerr(
                    "the current session is an account session. You need to specify the field 'uuid' of the user" +
                            " you want to update"
            ));
        }

        if (msg.getSession().isAccountSession()) {
            return;
        }

        if (AccountConstant.isAdminPermission(msg.getSession())) {
            return;
        }

        if (msg.getUuid() != null && !msg.getSession().getUserUuid().equals(msg.getUuid())) {
            throw new ApiMessageInterceptionException(argerr("your are login as a user, you cannot another user[uuid:%s]", msg.getUuid()));
        }

        msg.setUuid(msg.getSession().getUserUuid());
    }

    private void validate(APIRevokeResourceSharingMsg msg) {
        if (!msg.isAll() && (msg.getAccountUuids() == null || msg.getAccountUuids().isEmpty())) {
            throw new ApiMessageInterceptionException(argerr(
                    "all is set to false, accountUuids cannot be null or empty"
            ));
        }
    }

    private void validate(APIShareResourceMsg msg) {
        if (!msg.isToPublic() && (msg.getAccountUuids() == null || msg.getAccountUuids().isEmpty())) {
            throw new ApiMessageInterceptionException(argerr(
                    "toPublic is set to false, accountUuids cannot be null or empty"
            ));
        }
    }

    private void validate(APIDetachPolicyFromUserMsg msg) {
        PolicyVO policy = dbf.findByUuid(msg.getPolicyUuid(), PolicyVO.class);
        UserVO user = dbf.findByUuid(msg.getUserUuid(), UserVO.class);
        if (!policy.getAccountUuid().equals(msg.getAccountUuid())) {
            throw new ApiMessageInterceptionException(argerr("policy[name: %s, uuid: %s] doesn't belong to the account[uuid: %s]",
                            policy.getName(), policy.getUuid(), msg.getSession().getAccountUuid()));
        }
        if (!user.getAccountUuid().equals(msg.getAccountUuid())) {
            throw new ApiMessageInterceptionException(argerr("user[name: %s, uuid: %s] doesn't belong to the account[uuid: %s]",
                            user.getName(), user.getUuid(), msg.getSession().getAccountUuid()));
        }
    }

    private void validate(APIDetachPolicyFromUserGroupMsg msg) {
        PolicyVO policy = dbf.findByUuid(msg.getPolicyUuid(), PolicyVO.class);
        UserGroupVO group = dbf.findByUuid(msg.getGroupUuid(), UserGroupVO.class);
        if (!policy.getAccountUuid().equals(msg.getAccountUuid())) {
            throw new ApiMessageInterceptionException(argerr("policy[name: %s, uuid: %s] doesn't belong to the account[uuid: %s]",
                            policy.getName(), policy.getUuid(), msg.getSession().getAccountUuid()));
        }
        if (!group.getAccountUuid().equals(msg.getAccountUuid())) {
            throw new ApiMessageInterceptionException(argerr("group[name: %s, uuid: %s] doesn't belong to the account[uuid: %s]",
                            group.getName(), group.getUuid(), msg.getSession().getAccountUuid()));
        }
    }

    private void validate(APIAttachPolicyToUserMsg msg) {
        PolicyVO policy = dbf.findByUuid(msg.getPolicyUuid(), PolicyVO.class);
        UserVO user = dbf.findByUuid(msg.getUserUuid(), UserVO.class);
        if (!policy.getAccountUuid().equals(msg.getAccountUuid())) {
            throw new ApiMessageInterceptionException(argerr("policy[name: %s, uuid: %s] doesn't belong to the account[uuid: %s]",
                            policy.getName(), policy.getUuid(), msg.getSession().getAccountUuid()));
        }
        if (!user.getAccountUuid().equals(msg.getAccountUuid())) {
            throw new ApiMessageInterceptionException(argerr("user[name: %s, uuid: %s] doesn't belong to the account[uuid: %s]",
                            user.getName(), user.getUuid(), msg.getSession().getAccountUuid()));
        }
    }

    private void validate(APIAttachPolicyToUserGroupMsg msg) {
        PolicyVO policy = dbf.findByUuid(msg.getPolicyUuid(), PolicyVO.class);
        UserGroupVO group = dbf.findByUuid(msg.getGroupUuid(), UserGroupVO.class);
        if (!policy.getAccountUuid().equals(msg.getAccountUuid())) {
            throw new ApiMessageInterceptionException(argerr("policy[name: %s, uuid: %s] doesn't belong to the account[uuid: %s]",
                            policy.getName(), policy.getUuid(), msg.getSession().getAccountUuid()));
        }

        if (!group.getAccountUuid().equals(msg.getAccountUuid())) {
            throw new ApiMessageInterceptionException(argerr("group[name: %s, uuid: %s] doesn't belong to the account[uuid: %s]",
                            group.getName(), group.getUuid(), msg.getSession().getAccountUuid()));
        }
    }

    private void validate(APIAddUserToGroupMsg msg) {
        UserVO user = dbf.findByUuid(msg.getUserUuid(), UserVO.class);
        UserGroupVO group = dbf.findByUuid(msg.getGroupUuid(), UserGroupVO.class);
        if (!user.getAccountUuid().equals(msg.getAccountUuid())) {
            throw new ApiMessageInterceptionException(argerr("user[name: %s, uuid: %s] doesn't belong to the account[uuid: %s]",
                            user.getName(), user.getUuid(), msg.getSession().getAccountUuid()));
        }
        if (!group.getAccountUuid().equals(msg.getSession().getAccountUuid())) {
            throw new ApiMessageInterceptionException(argerr("group[name: %s, uuid: %s] doesn't belong to the account[uuid: %s]",
                            group.getName(), group.getUuid(), msg.getSession().getAccountUuid()));
        }
    }

    private void validate(APICreatePolicyMsg msg) {
        boolean sessionAccessToAdminActions = new CheckIfSessionCanOperationAdminPermission().check(msg.getSession());

        for (PolicyStatement s : msg.getStatements()) {
            if (s.getEffect() == null) {
                throw new ApiMessageInterceptionException(argerr("a statement must have effect field. Invalid statement[%s]", JSONObjectUtil.toJsonString(s)));
            }
            if (s.getActions() == null) {
                throw new ApiMessageInterceptionException(argerr("a statement must have action field. Invalid statement[%s]", JSONObjectUtil.toJsonString(s)));
            }
            if (s.getActions().isEmpty()) {
                throw new ApiMessageInterceptionException(argerr("a statement must have a non-empty action field. Invalid statement[%s]",
                                JSONObjectUtil.toJsonString(s)));
            }

            if (sessionAccessToAdminActions) {
                continue;
            }

            if (s.getActions() != null) {
                s.getActions().forEach(as -> {
                    if (PolicyUtils.isAdminOnlyAction(as)) {
                        throw new OperationFailureException(err(IdentityErrors.PERMISSION_DENIED, "normal accounts can't create admin-only action polices[%s]", as));
                    }
                });
            }
        }
    }

    private void validate(APIUpdateAccountMsg msg) {
        AccountVO a = dbf.findByUuid(msg.getSession().getAccountUuid(), AccountVO.class);

        if (msg.getName() != null) {
            SimpleQuery<AccountVO> q = dbf.createQuery(AccountVO.class);
            q.add(AccountVO_.name, Op.EQ, msg.getName());
            if (q.isExists()) {
                throw new ApiMessageInterceptionException(argerr("unable to update name. An account already called %s", msg.getName()));
            }
        }

        if (msg.getUuid() == null) {
            msg.setUuid(msg.getSession().getAccountUuid());
        }
        AccountVO account = dbf.findByUuid(msg.getUuid(), AccountVO.class);


        if (msg.getOldPassword() != null && !msg.getOldPassword().equals(account.getPassword())) {
            throw new OperationFailureException(operr("old password is not equal to the original password, cannot update the password of account[uuid: %s]", msg.getUuid()));
        }

        if (a.getType() == AccountType.SystemAdmin) {
            if (msg.getName() != null && (msg.getUuid() == null || msg.getUuid().equals(AccountConstant.INITIAL_SYSTEM_ADMIN_UUID))) {
                throw new OperationFailureException(operr(
                        "the name of admin account cannot be updated"
                ));
            }

            if (msg.getState() != null && (msg.getUuid() == null || msg.getUuid().equals(AccountConstant.INITIAL_SYSTEM_ADMIN_UUID))) {
                throw new OperationFailureException(operr(
                        "the state of admin account cannot be updated"
                ));
            }

            if (msg.getPassword() != null && (!AccountConstant.isAdminPermission(msg.getSession()))) {
                throw new OperationFailureException(operr("only admin account can update it's password"));
            }

            return;
        }

        if (!account.getUuid().equals(a.getUuid())) {
            throw new OperationFailureException(operr("account[uuid: %s, name: %s] is a normal account, it cannot reset the password of another account[uuid: %s]",
                            account.getUuid(), account.getName(), msg.getUuid()));
        }
    }

    private void validate(APIUpdateQuotaMsg msg) {
        QuotaVO quota = Q.New(QuotaVO.class)
                .eq(QuotaVO_.identityUuid, msg.getIdentityUuid())
                .eq(QuotaVO_.name, msg.getName())
                .find();
        if (quota == null) {
            throw new OperationFailureException(argerr("cannot find Quota[name: %s] for the account[uuid: %s]", msg.getName(), msg.getIdentityUuid()));
        }

        List<QuotaUpdateChecker> checkers = quotaChangeCheckers.stream()
                .filter(checker -> checker.type().contains(quota.getIdentityType())).collect(Collectors.toList());
        if (checkers.isEmpty()) {
            throw new ApiMessageInterceptionException(
                    argerr("can not find quota update checker for quota[uuid:%s, type:%s]", quota.getIdentityUuid(), quota.getIdentityType()));
        }

        for (QuotaUpdateChecker checker : checkers) {
            logger.debug(String.format("check quota[uuid:%s, type:%s] updated by %s",
                    quota.getIdentityUuid(), quota.getIdentityType(), checker.getClass().getSimpleName()));
            ErrorCode errorCode = checker.check(quota, msg.getValue());
            if (errorCode != null) {
                throw new ApiMessageInterceptionException(
                        operr(errorCode, "cannot update Quota[name: %s] for the account[uuid: %s]", msg.getName(), msg.getIdentityUuid()));
            }
        }

        msg.setQuotaVO(quota);
    }

    private void setServiceId(APIMessage msg) {
        if (msg instanceof AccountMessage) {
            AccountMessage amsg = (AccountMessage) msg;
            bus.makeTargetServiceIdByResourceUuid(msg, AccountConstant.SERVICE_ID, amsg.getAccountUuid());
        }
    }

    public Map<String, SessionInventory> getSessionsCopy() {
        return Session.getSessionsCopy();
    }

    @Override
    public RestAuthenticationType getAuthenticationType() {
        return ACCOUNT_REST_AUTHENTICATION_TYPE;
    }

    @Override
    public SessionInventory doAuth(RestAuthenticationParams params) {
        SessionVO vo = Q.New(SessionVO.class).eq(SessionVO_.uuid, params.authKey).find();
        if (vo != null) {
            return SessionInventory.valueOf(vo);
        }

        /* invalid session error should be raised in ApiMessageProcessorImpl */
        SessionInventory session = new SessionInventory();
        session.setUuid(params.authKey);
        return session;
    }
}
