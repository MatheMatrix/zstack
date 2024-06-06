package org.zstack.ldap.sync;

import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.ldap.filter.AndFilter;
import org.springframework.ldap.filter.Filter;
import org.springframework.ldap.filter.HardcodedFilter;
import org.springframework.ldap.filter.NotFilter;
import org.springframework.ldap.filter.OrFilter;
import org.zstack.core.Platform;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.cloudbus.CloudBusCallBack;
import org.zstack.core.workflow.SimpleFlowChain;
import org.zstack.header.core.Completion;
import org.zstack.header.core.workflow.FlowChain;
import org.zstack.header.core.workflow.FlowDoneHandler;
import org.zstack.header.core.workflow.FlowErrorHandler;
import org.zstack.header.core.workflow.FlowTrigger;
import org.zstack.header.core.workflow.NoRollbackFlow;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.identity.AccountType;
import org.zstack.header.message.MessageReply;
import org.zstack.identity.imports.AccountImportsConstant;
import org.zstack.identity.imports.entity.ImportAccountBatch;
import org.zstack.identity.imports.entity.SyncNewcomersStrategy;
import org.zstack.identity.imports.message.ImportThirdPartyAccountMsg;
import org.zstack.ldap.LdapConstant;
import org.zstack.ldap.driver.LdapUtil;
import org.zstack.ldap.entity.LdapFilterRuleVO;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.zstack.ldap.entity.LdapFilterRulePolicy.ACCEPT;
import static org.zstack.ldap.entity.LdapFilterRulePolicy.DENY;
import static org.zstack.ldap.entity.LdapFilterRuleTarget.AddNew;
import static org.zstack.utils.CollectionUtils.*;

/**
 * Created by Wenhao.Zhang on 2024/06/06
 */
@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class LdapSyncBackend {
    private static final CLogger logger = Utils.getLogger(LdapSyncBackend.class);

    @Autowired
    private CloudBus bus;

    private LdapSyncAccountSpec spec;
    private final ImportAccountBatch batch;
    private LdapUtil ldapUtil;

    public LdapSyncBackend(LdapSyncAccountSpec spec) {
        batch = new ImportAccountBatch();
        batch.sourceType = LdapConstant.LOGIN_TYPE;

        ldapUtil = Platform.New(LdapUtil::new);

        this.spec = Objects.requireNonNull(spec);
        this.batch.sourceUuid = spec.sourceUuid;
    }

    private Filter buildFilter() {
        List<LdapFilterRuleVO> rules = filter(spec.rules, rule -> AddNew.equals(rule.getTarget()));
        if (isEmpty(rules)) {
            return new AndFilter();
        }

        List<LdapFilterRuleVO> accepts = filter(spec.rules, rule -> ACCEPT.equals(rule.getPolicy()));
        List<LdapFilterRuleVO> denies = filter(spec.rules, rule -> DENY.equals(rule.getPolicy()));
        if (isEmpty(denies)) {
            OrFilter filter = new OrFilter();
            accepts.forEach(rule -> filter.or(new HardcodedFilter(rule.getRule())));
            // format : ( ACCEPT or ACCEPT or ACCEPT )
            return filter;
        }

        AndFilter filter = new AndFilter();
        denies.forEach(rule -> filter.and(new NotFilter(new HardcodedFilter(rule.getRule()))));
        if (isEmpty(accepts)) {
            // format : not(DENY) and not(DENY) and not(DENY)
            return filter;
        }

        OrFilter orFilter = new OrFilter();
        accepts.forEach(rule -> orFilter.or(new HardcodedFilter(rule.getRule())));
        filter.and(orFilter);
        // format : not(DENY) and not(DENY) and not(DENY) and ( ACCEPT or ACCEPT or ACCEPT )
        return filter;
    }

    @SuppressWarnings({"rawtypes"})
    public void run(Completion completion) {
        FlowChain chain = new SimpleFlowChain();
        chain.setName(String.format("sync-ldap-server-%s", batch.sourceUuid));
        chain.then(new NoRollbackFlow() {
            String __name__ = "sync-uid";

            @Override
            public void run(FlowTrigger trigger, Map data) {
                Set<String> returnAttributeSet = new HashSet<>(Arrays.asList(LdapConstant.QUERY_LDAP_ENTRY_MUST_RETURN_ATTRIBUTES));
                returnAttributeSet.add(ldapUtil.getGlobalUuidKey());
                String[] returnAttributes = returnAttributeSet.toArray(new String[0]);

                Filter userFilter = buildFilter();
                logger.debug("user filter is " + userFilter.toString());

                final int maximumSyncUsers = 10000; // TODO from global config
                String globalUuidKey = ldapUtil.getGlobalUuidKey();

                List<Object> results = ldapUtil.searchLdapEntry(userFilter.toString(), maximumSyncUsers, returnAttributes, null, false);
                for (Object ldapEntry : results) {
                    try {
                        batch.accountList.add(generateAccountSpec(globalUuidKey, ldapEntry));
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
                ImportThirdPartyAccountMsg msg = new ImportThirdPartyAccountMsg();
                msg.setBatch(batch);
                bus.makeTargetServiceIdByResourceUuid(msg, AccountImportsConstant.SERVICE_ID, msg.getSourceUuid());
                bus.send(msg, new CloudBusCallBack(trigger) {
                    @Override
                    public void run(MessageReply reply) {
                        if (!reply.isSuccess()) {
                            trigger.fail(reply.getError());
                            return;
                        }
                        trigger.next();
                    }
                });
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
    private ImportAccountBatch.AccountSpec generateAccountSpec(String globalUuidKey, Object ldapEntry) {
        ImportAccountBatch.AccountSpec account = new ImportAccountBatch.AccountSpec();
        Map<String, Object> map = (Map<String, Object>) ldapEntry;

        String dn = (String) map.get(LdapConstant.LDAP_DN_KEY); // entryDN
        account.keyFromSource = dn;
        account.accountType = AccountType.Normal;
        account.username = dn; // TODO
        account.password = Platform.getUuid() + Platform.getUuid();
        account.createIfNotExist = spec.forNewcomers == SyncNewcomersStrategy.CreateAccount;
        return account;
    }
}
