package org.zstack.compute.allocator.preset;

import org.zstack.core.db.Q;
import org.zstack.header.allocator.BeforeAllocateHostExtensionPoint;
import org.zstack.header.allocator.HostAllocatorSpec;
import org.zstack.header.cluster.ClusterVO;
import org.zstack.header.cluster.ClusterVO_;
import org.zstack.header.host.HostVO;
import org.zstack.header.host.HostVO_;
import org.zstack.header.host.HypervisorType;
import org.zstack.header.volume.VolumeFormat;
import org.zstack.utils.CollectionUtils;

import java.util.List;

/**
 * Created by Wenhao.Zhang on 24-01-19
 */
public class CloudHostAllocator implements BeforeAllocateHostExtensionPoint {
    @Override
    public void beforeAllocateHost(HostAllocatorSpec spec) {
        String hvType = spec.getHypervisorType();
        if (hvType == null && spec.isDesignated()) {
            if (spec.getDesignatedHostUuid() != null) {
                hvType = Q.New(HostVO.class)
                        .eq(HostVO_.uuid, spec.getDesignatedHostUuid())
                        .select(HostVO_.hypervisorType)
                        .findValue();
            } else if (!CollectionUtils.isEmpty(spec.getDesignatedClusterUuids())) {
                List<String> hvTypes = Q.New(ClusterVO.class)
                        .in(ClusterVO_.uuid, spec.getDesignatedClusterUuids())
                        .groupBy(ClusterVO_.hypervisorType)
                        .select(ClusterVO_.hypervisorType)
                        .listValues();
                hvType = hvTypes.size() == 1 ? hvTypes.get(0) : null;
            }
        }

        if (hvType == null && spec.getImage() != null && !spec.isDryRun()) {
            HypervisorType type = VolumeFormat.getMasterHypervisorTypeByVolumeFormat(spec.getImage().getFormat());
            if (type != null) {
                hvType = type.toString();
            }
        }
        spec.setHypervisorType(hvType);
    }
}
