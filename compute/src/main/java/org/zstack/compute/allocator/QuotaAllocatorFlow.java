package org.zstack.compute.allocator;

import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Configurable;
import org.zstack.compute.vm.VmQuotaOperator;
import org.zstack.core.db.Q;
import org.zstack.header.allocator.AbstractHostAllocatorFlow;
import org.zstack.identity.Account;
import org.zstack.identity.QuotaUtil;

import org.zstack.core.CoreGlobalProperty;
import org.zstack.core.db.DatabaseFacade;
import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.utils.logging.CLogger;
import org.zstack.header.host.HostVO;
import org.zstack.utils.Utils;

import static org.zstack.utils.CollectionUtils.isEmpty;


@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class QuotaAllocatorFlow extends AbstractHostAllocatorFlow {
    private static final CLogger logger = Utils.getLogger(QuotaAllocatorFlow.class);

    @Autowired
    private DatabaseFacade dbf;

    @Override
    public void allocate() {
        if (CoreGlobalProperty.UNIT_TEST_ON) {
            if (isEmpty(candidates)) {
                // all hosts (only in UT)
                accept(Q.New(HostVO.class).list());
                next();
                return;
            }
        }

        throwExceptionIfIAmTheFirstFlow();

        final String vmInstanceUuid = spec.getVmInstance().getUuid();
        final String accountUuid = Account.getAccountUuidOfResource(vmInstanceUuid);
        if (accountUuid == null || Account.isAdminPermission(accountUuid)) {
            next();
            return;
        }

        if (!spec.isFullAllocate()) {
            new VmQuotaOperator().checkVmCupAndMemoryCapacity(accountUuid,
                    accountUuid,
                    spec.getCpuCapacity(),
                    spec.getMemoryCapacity(),
                    new QuotaUtil().makeQuotaPairs(accountUuid));

            next();
            return;
        }

        new VmQuotaOperator().checkVmInstanceQuota(
                accountUuid,
                accountUuid,
                vmInstanceUuid,
                new QuotaUtil().makeQuotaPairs(accountUuid));
        next();
    }
}
