package org.zstack.storage.primary;

import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Configurable;
import org.zstack.header.core.workflow.FlowTrigger;
import org.zstack.header.core.workflow.NoRollbackFlow;
import org.zstack.header.storage.primary.PrimaryStorageAllocationSpec;
import org.zstack.header.storage.primary.PrimaryStorageConstant;
import org.zstack.header.storage.primary.PrimaryStorageVO;

import java.util.*;

@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class PrimaryStorageSortByAvailableVirtualCapacityFlow extends NoRollbackFlow {
    @Override
    public void run(FlowTrigger trigger, Map data) {
        List<PrimaryStorageVO> candidates = (List<PrimaryStorageVO>) data.get(PrimaryStorageConstant.AllocatorParams.CANDIDATES);
        if (candidates.size() < 2) {
            trigger.next();
            return;
        }

        PrimaryStorageAllocationSpec spec = (PrimaryStorageAllocationSpec) data.get(PrimaryStorageConstant.AllocatorParams.SPEC);
        /* sort ps by availableVirtualCapacity in desc order */
        Comparator<PrimaryStorageVO> comparator = (o1, o2) -> {
            if (o1.getCapacity().getAvailableCapacity() > o2.getCapacity().getAvailableCapacity()) {
                return -1;
            } else {
                return 1;
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
