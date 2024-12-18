package org.zstack.compute.allocator;

import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Configurable;
import org.zstack.header.allocator.AbstractHostAllocatorFlow;
import org.zstack.header.host.HostVO;
import org.zstack.header.vm.VmInstanceInventory;
import org.zstack.utils.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by mingjian.deng on 2017/11/8.
 *
 * Try to choose a host that the VM starts before
 */
@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class LastHostAllocatorFlow extends AbstractHostAllocatorFlow {
    @Override
    public void allocate() {
        throwExceptionIfIAmTheFirstFlow();

        if (spec.isListAllHosts()) {
            next(candidates);
            return;
        }

        final VmInstanceInventory vm = spec.getVmInstance();
        HostVO vo = CollectionUtils.findOneOrNull(candidates, arg -> arg.getUuid().equals(vm.getLastHostUuid()));

        if (vo != null) {
            List<HostVO> vos = new ArrayList<>();
            vos.add(vo);
            next(vos);
        } else {
            next(candidates);
        }
    }
}
