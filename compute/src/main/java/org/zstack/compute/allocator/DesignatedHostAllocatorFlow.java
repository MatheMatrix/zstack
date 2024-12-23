package org.zstack.compute.allocator;

import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.header.allocator.AbstractHostAllocatorFlow;
import org.zstack.header.allocator.HostAllocatorConstant;
import org.zstack.header.allocator.HostCandidate;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.List;

@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class DesignatedHostAllocatorFlow extends AbstractHostAllocatorFlow {
    private static final CLogger logger = Utils.getLogger(DesignatedHostAllocatorFlow.class);

    @Autowired
    private DatabaseFacade dbf;

    private void allocate(List<HostCandidate> candidates,
                          String zoneUuid,
                          List<String> clusterUuids,
                          String hostUuid,
                          String hypervisorType) {
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
            next();
            return;
        }

        allocate(candidates, zoneUuid, clusterUuids, hostUuid, spec.getHypervisorType());
        next();
    }
}
