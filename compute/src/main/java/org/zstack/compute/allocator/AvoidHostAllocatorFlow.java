package org.zstack.compute.allocator;

import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Configurable;
import org.zstack.core.Platform;
import org.zstack.header.allocator.AbstractHostAllocatorFlow;
import org.zstack.header.host.HostVO;
import org.zstack.header.vm.VmLocationSpec;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.zstack.utils.CollectionUtils.*;

@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class AvoidHostAllocatorFlow extends AbstractHostAllocatorFlow {
    @Override
    public void allocate() {
        throwExceptionIfIAmTheFirstFlow();

        final Set<String> avoidHostUuids = avoidHostUuids();
        List<HostVO> ret = filter(candidates, arg -> !avoidHostUuids.contains(arg.getUuid()));

        if (ret.isEmpty()) {
            fail(Platform.operr("after rule out avoided host%s, there is no host left in candidates", avoidHostUuids));
        } else {
            next(ret);
        }
    }

    private Set<String> avoidHostUuids() {
        if (isEmpty(spec.getLocationSpecs())) {
            return new HashSet<>();
        }

        return spec.getLocationSpecs().stream()
                .filter(spec -> HostVO.class.getSimpleName().equals(spec.getResourceType()))
                .filter(VmLocationSpec::avoid)
                .flatMap(spec -> spec.getUuids().stream())
                .collect(Collectors.toSet());
    }
}
