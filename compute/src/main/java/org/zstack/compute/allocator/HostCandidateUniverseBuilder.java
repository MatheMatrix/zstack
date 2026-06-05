package org.zstack.compute.allocator;

import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.SimpleQuery;
import org.zstack.header.allocator.HostAllocatorConstant;
import org.zstack.header.allocator.HostAllocatorSpec;
import org.zstack.header.candidate.CandidateDecisionContext;
import org.zstack.header.host.HostVO;
import org.zstack.header.host.HostVO_;
import org.zstack.header.identity.AccountConstant;
import org.zstack.identity.AccountManager;

import java.util.Comparator;
import java.util.Collections;
import java.util.List;

import static org.zstack.core.db.SimpleQuery.Op.EQ;
import static org.zstack.core.db.SimpleQuery.Op.IN;

public class HostCandidateUniverseBuilder {
    @Autowired
    private DatabaseFacade dbf;
    @Autowired
    private AccountManager acntMgr;

    @SuppressWarnings("unchecked")
    public List<HostVO> build(HostAllocatorSpec spec, CandidateDecisionContext ctx) {
        if (ctx == null || ctx.getAccountUuid() == null) {
            return Collections.emptyList();
        }

        SimpleQuery<HostVO> q = dbf.createQuery(HostVO.class);

        String zoneUuid = (String) spec.getExtraData().get(HostAllocatorConstant.LocationSelector.zone);
        List<String> clusterUuids = (List<String>) spec.getExtraData().get(HostAllocatorConstant.LocationSelector.cluster);
        String hostUuid = (String) spec.getExtraData().get(HostAllocatorConstant.LocationSelector.host);

        if (zoneUuid != null) {
            q.add(HostVO_.zoneUuid, EQ, zoneUuid);
        }
        if (CollectionUtils.isNotEmpty(clusterUuids)) {
            q.add(HostVO_.clusterUuid, IN, clusterUuids);
        }
        if (hostUuid != null) {
            q.add(HostVO_.uuid, EQ, hostUuid);
        }

        if (!AccountConstant.isAdminPermission(ctx.getAccountUuid())) {
            List<String> visibleHostUuids = acntMgr.getResourceUuidsCanAccessByAccount(ctx.getAccountUuid(), HostVO.class);
            if (CollectionUtils.isEmpty(visibleHostUuids)) {
                return Collections.emptyList();
            }
            q.add(HostVO_.uuid, IN, visibleHostUuids);
        }

        List<HostVO> hosts = q.list();
        hosts.sort(Comparator
                .comparing(HostVO::getZoneUuid, Comparator.nullsFirst(String::compareTo))
                .thenComparing(HostVO::getClusterUuid, Comparator.nullsFirst(String::compareTo))
                .thenComparing(HostVO::getName, Comparator.nullsFirst(String::compareTo))
                .thenComparing(HostVO::getUuid, Comparator.nullsFirst(String::compareTo)));
        return hosts;
    }
}
