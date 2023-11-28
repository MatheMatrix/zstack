package org.zstack.storage.primary;


import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Configurable;
import org.zstack.core.db.Q;
import org.zstack.header.core.workflow.FlowTrigger;
import org.zstack.header.core.workflow.NoRollbackFlow;
import org.zstack.header.storage.primary.PrimaryStorageAllocationSpec;
import org.zstack.header.storage.primary.PrimaryStorageConstant;
import org.zstack.header.storage.primary.PrimaryStorageVO;
import org.zstack.header.volume.VolumeVO;
import org.zstack.header.volume.VolumeVO_;

import java.util.*;

@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class PrimaryStorageSortByVolumeQuantityFlow extends NoRollbackFlow {
    @Override
    public void run(FlowTrigger trigger, Map data) {
        List<PrimaryStorageVO> candidates = (List<PrimaryStorageVO>) data.get(PrimaryStorageConstant.AllocatorParams.CANDIDATES);
        if (candidates.size() < 2) {
            trigger.next();
            return;
        }

        PrimaryStorageAllocationSpec spec = (PrimaryStorageAllocationSpec) data.get(PrimaryStorageConstant.AllocatorParams.SPEC);
        /* sort ps by volume quantity in asc order */
        Comparator<PrimaryStorageVO> comparator = (ps1, ps2) -> {
            Long volumeQuantityInPs1 = Q.New(VolumeVO.class).eq(VolumeVO_.primaryStorageUuid, ps1.getUuid()).count();
            Long volumeQuantityInPs2 = Q.New(VolumeVO.class).eq(VolumeVO_.primaryStorageUuid, ps2.getUuid()).count();
            if (volumeQuantityInPs1 > volumeQuantityInPs2) {
                return 1;
            } else {
                return -1;
            }
        };

        // no group
        List<List<PrimaryStorageVO>> candidatesGroup = (List<List<PrimaryStorageVO>>)data.get(PrimaryStorageConstant.AllocatorParams.GROUP_CANDIDATES);
        if (candidatesGroup == null) {
            candidates.sort(comparator);
            trigger.next();
            return;
        }

        List<PrimaryStorageVO> ret = new ArrayList<>();
        for (List<PrimaryStorageVO> primaryStorageVOS : candidatesGroup) {
            primaryStorageVOS.sort(comparator);
            ret.addAll(primaryStorageVOS);
        }

        data.put(PrimaryStorageConstant.AllocatorParams.CANDIDATES, ret);

        trigger.next();
    }
}
