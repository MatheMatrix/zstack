package org.zstack.compute.allocator;

import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.SimpleQuery;
import org.zstack.core.db.SimpleQuery.Op;
import org.zstack.header.allocator.AbstractHostAllocatorFlow;
import org.zstack.header.allocator.HostCandidate;
import org.zstack.header.host.HostState;
import org.zstack.header.host.HostStatus;
import org.zstack.header.host.HostVO;
import org.zstack.header.host.HostVO_;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class HostStateAndHypervisorAllocatorFlow extends AbstractHostAllocatorFlow {
    private static final CLogger logger = Utils.getLogger(HostStateAndHypervisorAllocatorFlow.class);

    @Autowired
    private DatabaseFacade dbf;

    private void produce(String hypervisorType) {
        SimpleQuery<HostVO> query = dbf.createQuery(HostVO.class);
        query.add(HostVO_.state, Op.EQ, HostState.Enabled);
        query.add(HostVO_.status, Op.EQ, HostStatus.Connected);
        if (hypervisorType != null) {
            query.add(HostVO_.hypervisorType, Op.EQ, hypervisorType);
        }

        if (usePagination()) {
            query.setStart(paginationInfo.getOffset());
            query.setLimit(paginationInfo.getLimit());
        }

        accept(query.list());
    }

    private void allocate(String hypervisorType) {
        for (HostCandidate candidate : candidates) {
            if (hypervisorType != null && !hypervisorType.equals(candidate.host.getHypervisorType())) {
                reject(candidate, hypervisorType + " hypervisorType required");
                continue;
            }

            if (candidate.host.getState() != HostState.Enabled) {
                reject(candidate, "host is not enabled");
                continue;
            }

            if (candidate.host.getStatus() != HostStatus.Connected) {
                reject(candidate, "host is not connected");
            }
        }
    }

    @Override
    public void allocate() {
        if (amITheFirstFlow()) {
            produce(spec.getHypervisorType());
        } else {
            allocate(spec.getHypervisorType());
        }

        next();
    }
}
