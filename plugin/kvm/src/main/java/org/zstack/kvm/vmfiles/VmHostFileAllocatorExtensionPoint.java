package org.zstack.kvm.vmfiles;

import org.zstack.header.allocator.HostAllocatorFilterExtensionPoint;
import org.zstack.header.allocator.HostAllocatorSpec;
import org.zstack.header.host.HostVO;
import org.zstack.header.vm.VmInstanceConstant;
import org.zstack.header.vm.additions.VmHostFileType;
import org.zstack.header.vm.additions.VmHostFileVO;
import org.zstack.header.vm.additions.VmHostFileVO_;
import org.zstack.core.db.Q;

import com.google.common.base.Objects;

import java.util.List;
import java.util.stream.Collectors;

public class VmHostFileAllocatorExtensionPoint implements HostAllocatorFilterExtensionPoint {
    private String filterErrorReason;

    @Override
    public List<HostVO> filterHostCandidates(List<HostVO> candidates, HostAllocatorSpec spec) {
        filterErrorReason = null;
        if (spec == null) {
            return candidates;
        }

        String vmOperation = spec.getVmOperation();
        if (vmOperation == null || !VmInstanceConstant.VmOperation.Start.toString().equals(vmOperation)) {
            return candidates;
        }

        if (spec.getVmInstance() == null) {
            return candidates;
        }

        String vmUuid = spec.getVmInstance().getUuid();
        String lastHostUuid = spec.getVmInstance().getLastHostUuid();

        List<VmHostFileType> files = Q.New(VmHostFileVO.class)
                .eq(VmHostFileVO_.vmInstanceUuid, vmUuid)
                .eq(VmHostFileVO_.hostUuid, lastHostUuid)
                .select(VmHostFileVO_.type)
                .notNull(VmHostFileVO_.changeDate)
                .listValues();
        if (files.isEmpty()) {
            return candidates;
        }

        String types = files.stream()
                .map(Enum::name)
                .collect(Collectors.joining(","));

        filterErrorReason = String.format("only allowed start on last host: unsynchronized %s VM host files exist", types);
        return candidates.stream()
                .filter(host -> Objects.equal(lastHostUuid, host.getUuid()))
                .collect(Collectors.toList());
    }

    @Override
    public String filterErrorReason() {
        return filterErrorReason;
    }
}
