package org.zstack.compute.vm.quota;

import org.zstack.compute.vm.VmQuotaConstant;
import org.zstack.compute.vm.VmQuotaGlobalConfig;
import org.zstack.header.identity.quota.QuotaDefinition;
import org.zstack.header.vm.VmInstanceState;
import org.zstack.header.vm.VmInstanceVO;
import org.zstack.header.vm.VmInstanceVO_;
import org.zstack.identity.ResourceHelper;

import javax.persistence.Tuple;
import java.util.List;

public class VmTotalNumQuotaDefinition implements QuotaDefinition {
    @Override
    public String getName() {
        return VmQuotaConstant.VM_TOTAL_NUM;
    }

    @Override
    public Long getDefaultValue() {
        return VmQuotaGlobalConfig.VM_TOTAL_NUM.defaultValue(Long.class);
    }

    @Override
    public Long getQuotaUsage(String accountUuid) {
        final List<Tuple> tuples = ResourceHelper.findOwnResourceTuples(VmInstanceVO.class, accountUuid,
                VmInstanceVO_.hostUuid,
                VmInstanceVO_.lastHostUuid,
                VmInstanceVO_.rootVolumeUuid,
                VmInstanceVO_.state,
                VmInstanceVO_.type);
        tuples.removeIf(tuple -> {
            if ("baremetal2".equals(tuple.get(4, String.class))) {
                return true;
            }
            if (VmInstanceState.Destroyed.equals(tuple.get(3, VmInstanceState.class))) {
                return true;
            }
            String hostUuid = tuple.get(2, String.class);
            String lastHostUuid = tuple.get(1, String.class);
            String rootVolumeUuid = tuple.get(0, String.class);
            return (hostUuid == null && lastHostUuid == null && rootVolumeUuid == null);
        });
        return (long) tuples.size();
    }
}
