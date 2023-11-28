package org.zstack.storage.primary;

import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.zstack.header.core.workflow.FlowTrigger;
import org.zstack.header.core.workflow.NoRollbackFlow;
import org.zstack.header.storage.backup.PrimaryStoragePriorityGetter;
import org.zstack.header.storage.primary.PrimaryStorageAllocationSpec;
import org.zstack.header.storage.primary.PrimaryStorageConstant;
import org.zstack.header.storage.primary.PrimaryStorageVO;
import org.zstack.utils.Utils;
import org.zstack.utils.gson.JSONObjectUtil;
import org.zstack.utils.logging.CLogger;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by shixin.ruan on 2019/08/09.
 */

@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class PrimaryStorageSortByAvailablePhysicalCapacityFlow extends NoRollbackFlow {
    private static CLogger logger = Utils.getLogger(PrimaryStorageSortByAvailablePhysicalCapacityFlow.class);


    @Override
    public void run(FlowTrigger trigger, Map data) {
        List<PrimaryStorageVO> candidates = (List<PrimaryStorageVO>) data.get(PrimaryStorageConstant.AllocatorParams.CANDIDATES);
        if (candidates.size() < 2) {
            trigger.next();
            return;
        }

        /* we assume that, before this flow, candidate has been sort by priority like this:
        * ShareBlock1, ShareBlock2, Ceph
        * after this flow, ShareBlock1, ShareBlock2 will be sorted by availablePhysicalCapacity */
        Comparator<PrimaryStorageVO> comparator = (ps1, ps2) -> {
            if (ps1.getCapacity().getAvailablePhysicalCapacity() > ps2.getCapacity().getAvailablePhysicalCapacity()) {
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
