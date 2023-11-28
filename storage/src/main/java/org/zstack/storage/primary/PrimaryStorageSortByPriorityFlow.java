package org.zstack.storage.primary;

import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.zstack.header.core.workflow.FlowTrigger;
import org.zstack.header.core.workflow.NoRollbackFlow;
import org.zstack.header.storage.backup.*;
import org.zstack.header.storage.primary.PrimaryStorageAllocationSpec;
import org.zstack.header.storage.primary.PrimaryStorageConstant;
import org.zstack.header.storage.primary.PrimaryStorageVO;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by MaJin on 2019/3/4.
 */

@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class PrimaryStorageSortByPriorityFlow extends NoRollbackFlow {
    private static CLogger logger = Utils.getLogger(PrimaryStorageSortByPriorityFlow.class);

    @Autowired
    protected PrimaryStoragePriorityGetter priorityGetter;

    @Override
    public void run(FlowTrigger trigger, Map data) {
        PrimaryStorageAllocationSpec spec = (PrimaryStorageAllocationSpec) data.get(PrimaryStorageConstant.AllocatorParams.SPEC);
        if (spec.getImageUuid() == null) {
            trigger.next();
            return;
        }

        List<PrimaryStorageVO> candidates = (List<PrimaryStorageVO>) data.get(PrimaryStorageConstant.AllocatorParams.CANDIDATES);

        if (candidates.size() == 1) {
            trigger.next();
            return;
        }

        PrimaryStoragePriorityGetter.PrimaryStoragePriority result = priorityGetter
                .getPrimaryStoragePriority(spec.getImageUuid(), spec.getBackupStorageUuid());
        Map<String, Integer> priority = result.psPriority.stream().collect(Collectors.toMap(it -> it.PS, it -> it.priority));
        List<List<PrimaryStorageVO>> candidatesGroupByPriority = candidates.stream()
                .collect(Collectors.groupingBy(it -> priority.getOrDefault(it.getType(), result.defaultPriority)))
                .entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue).collect(Collectors.toList());
        data.put(PrimaryStorageConstant.AllocatorParams.GROUP_CANDIDATES, candidatesGroupByPriority);
        trigger.next();
    }
}
