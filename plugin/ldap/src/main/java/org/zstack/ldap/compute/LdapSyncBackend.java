package org.zstack.ldap.compute;

import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.ldap.core.NameAwareAttribute;
import org.springframework.ldap.filter.AndFilter;
import org.springframework.ldap.filter.Filter;
import org.springframework.ldap.filter.HardcodedFilter;
import org.springframework.ldap.filter.NotFilter;
import org.springframework.ldap.filter.OrFilter;
import org.zstack.core.Platform;
import org.zstack.core.asyncbatch.While;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.cloudbus.CloudBusCallBack;
import org.zstack.core.workflow.SimpleFlowChain;
import org.zstack.header.core.Completion;
import org.zstack.header.core.WhileDoneCompletion;
import org.zstack.header.core.workflow.FlowChain;
import org.zstack.header.core.workflow.FlowDoneHandler;
import org.zstack.header.core.workflow.FlowErrorHandler;
import org.zstack.header.core.workflow.FlowTrigger;
import org.zstack.header.core.workflow.NoRollbackFlow;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.errorcode.ErrorCodeList;
import org.zstack.header.identity.AccountType;
import org.zstack.header.message.MessageReply;
import org.zstack.identity.imports.AccountImportsConstant;
import org.zstack.identity.imports.header.CreateAccountSpec;
import org.zstack.identity.imports.header.ImportAccountSpec;
import org.zstack.identity.imports.message.ImportThirdPartyAccountMsg;
import org.zstack.ldap.LdapConstant;
import org.zstack.ldap.driver.LdapUtil;
import org.zstack.ldap.entity.LdapFilterRuleVO;
import org.zstack.ldap.header.LdapSyncTaskSpec;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.zstack.core.Platform.operr;
import static org.zstack.identity.imports.entity.SyncCreatedAccountStrategy.CreateAccount;
import static org.zstack.ldap.entity.LdapFilterRulePolicy.ACCEPT;
import static org.zstack.ldap.entity.LdapFilterRulePolicy.DENY;
import static org.zstack.ldap.entity.LdapFilterRuleTarget.AddNew;
import static org.zstack.utils.CollectionDSL.list;
import static org.zstack.utils.CollectionUtils.*;

@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class LdapSyncBackend {
    private static final CLogger logger = Utils.getLogger(LdapSyncBackend.class);

    @Autowired
    private CloudBus bus;

    private LdapSyncTaskSpec taskSpec;
    private final ImportAccountSpec importSpec;
    private LdapUtil ldapUtil;

    public static final String DEFAULT_FILTER = "(objectClass=person)";

    public LdapSyncBackend(LdapSyncTaskSpec spec) {
        this.taskSpec = Objects.requireNonNull(spec);

        importSpec = new ImportAccountSpec();
        importSpec.setSourceType(LdapConstant.LOGIN_TYPE);
        importSpec.setSourceUuid(spec.sourceUuid);

        ldapUtil = Platform.New(LdapUtil::new);
    }

    private Filter buildFilter() {
        List<LdapFilterRuleVO> rules = filter(taskSpec.getRules(), rule -> AddNew.equals(rule.getTarget()));
        if (isEmpty(rules)) {
            return new HardcodedFilter(taskSpec.getDefaultFilter());
        }

        List<LdapFilterRuleVO> accepts = filter(taskSpec.getRules(), rule -> ACCEPT.equals(rule.getPolicy()));
        List<LdapFilterRuleVO> denies = filter(taskSpec.getRules(), rule -> DENY.equals(rule.getPolicy()));
        if (isEmpty(denies)) {
            OrFilter filter = new OrFilter();
            accepts.forEach(rule -> filter.or(new HardcodedFilter(rule.getRule())));
            // format : defaults and ( ACCEPT or ACCEPT or ACCEPT )
            return new AndFilter()
                    .and(new HardcodedFilter(taskSpec.getDefaultFilter()))
                    .and(filter);
        }

        AndFilter filter = new AndFilter();
        filter.and(new HardcodedFilter(taskSpec.getDefaultFilter()));
        denies.forEach(rule -> filter.and(new NotFilter(new HardcodedFilter(rule.getRule()))));
        if (isEmpty(accepts)) {
            // format : defaults and not(DENY) and not(DENY) and not(DENY)
            return filter;
        }

        OrFilter orFilter = new OrFilter();
        accepts.forEach(rule -> orFilter.or(new HardcodedFilter(rule.getRule())));
        filter.and(orFilter);
        // format : defaults and not(DENY) and not(DENY) and not(DENY) and ( ACCEPT or ACCEPT or ACCEPT )
        return filter;
    }

    @SuppressWarnings({"rawtypes"})
    public void run(Completion completion) {
        FlowChain chain = new SimpleFlowChain();
        chain.setName(String.format("sync-ldap-server-%s", importSpec.getSourceUuid()));
        chain.then(new NoRollbackFlow() {
            String __name__ = "sync-uid";

            @Override
            public void run(FlowTrigger trigger, Map data) {
                Set<String> returnAttributeSet = new HashSet<>(Arrays.asList(LdapConstant.QUERY_LDAP_ENTRY_MUST_RETURN_ATTRIBUTES));
                returnAttributeSet.add(ldapUtil.getGlobalUuidKey());
                String[] returnAttributes = returnAttributeSet.toArray(new String[0]);

                Filter userFilter = buildFilter();
                logger.debug("user filter is " + userFilter.toString());

                String globalUuidKey = ldapUtil.getGlobalUuidKey();

                List<Object> results = ldapUtil.searchLdapEntry(userFilter.toString(), taskSpec.getMaxAccountCount(), returnAttributes, null, false);
                for (Object ldapEntry : results) {
                    try {
                        importSpec.accountList.add(generateAccountSpec(globalUuidKey, ldapEntry));
                    } catch (Exception e) {
                        logger.warn("failed to sync ldap entry[], ignore this account", e);
                    }
                }
                trigger.next();
            }
        }).then(new NoRollbackFlow() {
            String __name__ = "import-accounts";

            @Override
            public void run(FlowTrigger trigger, Map data) {
                AtomicBoolean anySuccess = new AtomicBoolean(false);

                new While<>(splitMessageFromImportSpec()).each((msg, whileCompletion) -> {
                    bus.makeTargetServiceIdByResourceUuid(msg, AccountImportsConstant.SERVICE_ID, msg.getSourceUuid());
                    bus.send(msg, new CloudBusCallBack(trigger) {
                        @Override
                        public void run(MessageReply reply) {
                            if (reply.isSuccess()) {
                                anySuccess.set(true);
                            } else {
                                whileCompletion.addError(reply.getError());
                            }
                            whileCompletion.done();
                        }
                    });
                }).run(new WhileDoneCompletion(trigger) {
                    @Override
                    public void done(ErrorCodeList errorCodeList) {
                        if (!anySuccess.get()) {
                            trigger.fail(operr(errorCodeList, "all ldap account importing attempt is failed. ldapServerUuid=%s",
                                    importSpec.getSourceUuid()));
                            return;
                        } else if (!errorCodeList.getCauses().isEmpty()) {
                            logger.warn("ldap account importing occur some errors: " +
                                    errorCodeList.getCauses().get(0).getDetails());
                        }
                        trigger.next();
                    }
                });
            }

            private List<ImportThirdPartyAccountMsg> splitMessageFromImportSpec() {
                int accountCount = importSpec.accountList.size();

                if (accountCount <= 100) {
                    ImportThirdPartyAccountMsg msg = new ImportThirdPartyAccountMsg();
                    msg.setSpec(importSpec);
                    return list(msg);
                }

                int count = 0;
                List<ImportThirdPartyAccountMsg> list = new ArrayList<>();
                while (count < accountCount) {
                    int toIndexExclude = Math.min(accountCount, count + 100);

                    ImportAccountSpec splitSpec = new ImportAccountSpec();
                    splitSpec.setSourceUuid(importSpec.getSourceUuid());
                    splitSpec.setSourceType(importSpec.getSourceType());
                    splitSpec.setAccountList(importSpec.getAccountList().subList(count, toIndexExclude));

                    ImportThirdPartyAccountMsg msg = new ImportThirdPartyAccountMsg();
                    msg.setSpec(splitSpec);
                    list.add(msg);

                    count += 100;
                }
                return list;
            }
        }).then(new NoRollbackFlow() {
            String __name__ = "clean-stale-ldap-entry";

            @Override
            public void run(FlowTrigger trigger, Map data) {
//                doCleanInvalidLdapIAM2VirtualIDBindings();
//                doCleanupInvalidLdapIAM2OrganizationRefs();
                trigger.next();
            }
        }).error(new FlowErrorHandler(completion) {
            @Override
            public void handle(ErrorCode errCode, Map data) {
                completion.fail(errCode);
            }
        }).done(new FlowDoneHandler(completion) {
            @Override
            public void handle(Map data) {
                completion.success();
            }
        }).start();
    }

    @SuppressWarnings({"unchecked"})
    private CreateAccountSpec generateAccountSpec(String globalUuidKey, Object ldapEntry) {
        CreateAccountSpec account = new CreateAccountSpec();
        Map<String, Object> map = (Map<String, Object>) ldapEntry;

        List<NameAwareAttribute> attributes = (List<NameAwareAttribute>) map.get("attributes");
        String usernameProperty = taskSpec.getUsernameProperty();
        NameAwareAttribute usernameAttribute = findOneOrNull(attributes,
                attribute -> Objects.equals(attribute.getID(), usernameProperty));

        String dn = (String) map.get(LdapConstant.LDAP_DN_KEY); // entryDN
        final String username;
        if (usernameAttribute == null) {
            username = dn;
        } else {
            Object value = usernameAttribute.get();
            username = value == null ? dn : value.toString();
        }

        account.setCredentials(dn);
        account.setAccountType(AccountType.Normal);
        account.setUsername(username);
        account.setPassword(Platform.getUuid() + Platform.getUuid());
        account.setCreateIfNotExist(taskSpec.getCreateAccountStrategy() == CreateAccount);
        return account;
    }
}