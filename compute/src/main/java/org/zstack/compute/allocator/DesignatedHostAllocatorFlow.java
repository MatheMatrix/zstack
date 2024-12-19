package org.zstack.compute.allocator;

import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.transaction.annotation.Transactional;
import org.zstack.core.Platform;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.header.allocator.AbstractHostAllocatorFlow;
import org.zstack.header.allocator.HostAllocatorConstant;
import org.zstack.header.allocator.HostCandidate;
import org.zstack.header.host.HostState;
import org.zstack.header.host.HostStatus;
import org.zstack.header.host.HostVO;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import javax.persistence.TypedQuery;
import java.util.List;

@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class DesignatedHostAllocatorFlow extends AbstractHostAllocatorFlow {
    private static final CLogger logger = Utils.getLogger(DesignatedHostAllocatorFlow.class);

    @Autowired
    private DatabaseFacade dbf;

    @Transactional(readOnly = true)
    private List<HostVO> allocate(String zoneUuid, List<String> clusterUuids, String hostUuid, String hypervisorType) {
        StringBuilder sql = new StringBuilder();
        sql.append("select h from HostVO h where ");
        if (zoneUuid != null) {
            sql.append(String.format("h.zoneUuid = '%s' and ", zoneUuid));
        }
        if (!CollectionUtils.isEmpty(clusterUuids)) {
            sql.append(String.format("h.clusterUuid in ('%s') and ", String.join("','", clusterUuids)));
        }
        if (hostUuid != null) {
            sql.append(String.format("h.uuid = '%s' and ", hostUuid));
        }
        if (hypervisorType != null) {
            sql.append(String.format("h.hypervisorType = '%s' and ", hypervisorType));
        }
        sql.append(String.format("h.status = '%s' and h.state = '%s'", HostStatus.Connected, HostState.Enabled));
        logger.debug("DesignatedHostAllocatorFlow sql: " + sql);
        TypedQuery<HostVO> query = dbf.getEntityManager().createQuery(sql.toString(), HostVO.class);

        if (usePagination()) {
            query.setFirstResult(paginationInfo.getOffset());
            query.setMaxResults(paginationInfo.getLimit());
        }

        return query.getResultList();
    }
    
    
    private void allocate(List<HostCandidate> candidates, String zoneUuid, List<String> clusterUuids, String hostUuid, String hypervisorType) {
        for (HostCandidate candidate : candidates) {
            if (zoneUuid != null && !candidate.host.getZoneUuid().equals(zoneUuid)) {
                reject(candidate, String.format("not in zone[uuid:%s]", zoneUuid));
                continue;
            }
            if (!CollectionUtils.isEmpty(clusterUuids) && !clusterUuids.contains(candidate.host.getClusterUuid())) {
                reject(candidate, String.format("not in cluster[uuid:%s]", clusterUuids));
                continue;
            }
            if (hostUuid != null && !candidate.getUuid().equals(hostUuid)) {
                reject(candidate, String.format("must be host[uuid:%s]", hostUuid));
                continue;
            }
            if (hypervisorType != null && !candidate.host.getHypervisorType().equals(hypervisorType)) {
                reject(candidate, String.format("not with type[%s]", hypervisorType));
            }
        }
    }

    @Override
    public void allocate() {
        String zoneUuid = (String) spec.getExtraData().get(HostAllocatorConstant.LocationSelector.zone);
        List<String> clusterUuids = (List<String>) spec.getExtraData().get(HostAllocatorConstant.LocationSelector.cluster);
        String hostUuid = (String) spec.getExtraData().get(HostAllocatorConstant.LocationSelector.host);

        if (zoneUuid == null && CollectionUtils.isEmpty(clusterUuids) && hostUuid == null && spec.getHypervisorType() == null) {
            skip();
            return;
        }

        if (amITheFirstFlow()) {
            List<HostVO> results = allocate(zoneUuid, clusterUuids, hostUuid, spec.getHypervisorType());
            if (results.isEmpty()) {
                reportNoHostFound(zoneUuid, clusterUuids, hostUuid);
                return;
            }

            accept(results);
            next();
            return;
        }

        allocate(candidates, zoneUuid, clusterUuids, hostUuid, spec.getHypervisorType());
        next();
    }

    private void reportNoHostFound(String zoneUuid, List<String> clusterUuids, String hostUuid) {
        StringBuilder args = new StringBuilder();
        if (zoneUuid != null) {
            args.append(String.format("zoneUuid=%s", zoneUuid)).append(" ");
        }
        if (!clusterUuids.isEmpty()) {
            args.append(String.format("clusterUuid in %s", clusterUuids)).append(" ");
        }
        if (hostUuid != null) {
            args.append(String.format("hostUuid=%s", hostUuid)).append(" ");
        }
        if (spec.getHypervisorType() != null) {
            args.append(String.format("hypervisorType=%s", spec.getHypervisorType())).append(" ");
        }
        if (args.length() == 0) {
            args.append("no conditions");
        }

        fail(Platform.operr("No host with %s found", args));
    }
}
