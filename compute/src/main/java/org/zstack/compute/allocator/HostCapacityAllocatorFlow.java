package org.zstack.compute.allocator;

import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.zstack.core.Platform;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.header.allocator.AbstractHostAllocatorFlow;
import org.zstack.header.allocator.HostCapacityOverProvisioningManager;
import org.zstack.header.allocator.HostCpuOverProvisioningManager;
import org.zstack.header.candidate.CandidateCategories;
import org.zstack.header.candidate.CandidateReasonCodes;
import org.zstack.header.candidate.CandidateRejectReason;
import org.zstack.header.host.HostVO;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import static org.zstack.utils.clouderrorcode.CloudOperationsErrorCode.*;

@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class HostCapacityAllocatorFlow extends AbstractHostAllocatorFlow {
    private static final CLogger logger = Utils.getLogger(HostCapacityAllocatorFlow.class);

    @Autowired
    private DatabaseFacade dbf;
    @Autowired
    private HostCapacityReserveManager reserveMgr;
    @Autowired
    private HostCapacityOverProvisioningManager ratioMgr;
    @Autowired
    private HostCpuOverProvisioningManager cpuRatioMgr;

    private boolean memoryCheck(long vmMemSize, long oldMemory, HostVO hvo) {
        if (HostAllocatorGlobalConfig.HOST_ALLOCATOR_MAX_MEMORY.value(Boolean.class)) {
            if ((vmMemSize + oldMemory) >= hvo.getCapacity().getTotalPhysicalMemory()) {
                return false;
            }
        }

        return ratioMgr.calculateHostAvailableMemoryByRatio(hvo.getUuid(), hvo.getCapacity().getAvailableMemory()) >= vmMemSize;
    }


    private List<HostVO> allocate(List<HostVO> vos, long cpu, long memory, long oldMemory) {
        return vos.parallelStream()
                .filter(hvo -> (cpu == 0 || hvo.getCapacity().getAvailableCpu() >= cpu)
                        && (memory == 0 || memoryCheck(memory, oldMemory, hvo))).collect(Collectors.toList());
    }

    private List<HostVO> allocateWithDecision(List<HostVO> vos, long cpu, long memory, long oldMemory) {
        List<HostVO> ret = new ArrayList<>(vos.size());
        for (HostVO host : vos) {
            if (cpu != 0 && host.getCapacity().getAvailableCpu() < cpu) {
                reject(host, CandidateRejectReason.of(
                        CandidateReasonCodes.HOST_CPU_NOT_ENOUGH,
                        CandidateCategories.CAPACITY,
                        "available cpu is not enough"
                ).detail("stage", "capacity")
                 .detail("requiredCpu", cpu)
                 .detail("availableCpu", host.getCapacity().getAvailableCpu()));
                continue;
            }

            long availableMemory = ratioMgr.calculateHostAvailableMemoryByRatio(host.getUuid(), host.getCapacity().getAvailableMemory());
            if (memory != 0 && !memoryCheck(memory, oldMemory, host)) {
                reject(host, CandidateRejectReason.of(
                        CandidateReasonCodes.HOST_MEMORY_NOT_ENOUGH,
                        CandidateCategories.CAPACITY,
                        "available memory is not enough"
                ).detail("stage", "capacity")
                 .detail("requiredMemory", memory)
                 .detail("availableMemory", availableMemory)
                 .detail("totalPhysicalMemory", host.getCapacity().getTotalPhysicalMemory()));
                continue;
            }

            pass(host);
            ret.add(host);
        }

        List<HostVO> afterReservedCapacity = reserveMgr.filterOutHostsByReservedCapacity(ret, cpu, memory);
        Set<String> passed = new HashSet<>();
        for (HostVO host : afterReservedCapacity) {
            passed.add(host.getUuid());
        }
        for (HostVO host : ret) {
            if (!passed.contains(host.getUuid())) {
                reject(host, CandidateRejectReason.of(
                        CandidateReasonCodes.HOST_RESERVED_CAPACITY_NOT_ENOUGH,
                        CandidateCategories.CAPACITY,
                        "host capacity is reserved by global configuration"
                ).detail("stage", "capacity"));
            }
        }
        return afterReservedCapacity;
    }

    private boolean isNoCpu(int cpu) {
        return !candidates.stream().anyMatch(vo -> vo.getCapacity().getCpuNum() >= cpu);
    }

    private boolean isNoMemory(long mem) {
        return !candidates.stream().anyMatch(vo -> ratioMgr.calculateHostAvailableMemoryByRatio(vo.getUuid(), vo.getCapacity().getAvailableMemory()) >= mem);
    }

    @Override
    public void allocate() {
        throwExceptionIfIAmTheFirstFlow();

        List<HostVO> ret;
        if (isCandidateDecisionEnabled()) {
            ret = allocateWithDecision(candidates, spec.getCpuCapacity(), spec.getMemoryCapacity(), spec.getOldMemoryCapacity());
        } else {
            ret = allocate(candidates, spec.getCpuCapacity(), spec.getMemoryCapacity(), spec.getOldMemoryCapacity());
            ret = reserveMgr.filterOutHostsByReservedCapacity(ret, spec.getCpuCapacity(), spec.getMemoryCapacity());
        }

        if (ret.isEmpty()) {
            fail(Platform.operr(ORG_ZSTACK_COMPUTE_ALLOCATOR_10021, "no host having cpu[%s], memory[%s bytes] found",
                    spec.getCpuCapacity(), spec.getMemoryCapacity()));
        } else {
            next(ret);
        }
    }
}
