package org.zstack.compute.allocator;

import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.zstack.compute.host.HostManager;
import org.zstack.core.Platform;
import org.zstack.core.db.Q;
import org.zstack.header.allocator.AbstractHostAllocatorFlow;
import org.zstack.header.allocator.HostAllocatorConstant;
import org.zstack.header.host.*;
import org.zstack.header.vm.VmInstanceInventory;
import org.zstack.header.vo.ResourceVO;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.*;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.zstack.utils.CollectionUtils.*;

/**
 * Filter out hosts that do not match the operating system of the specific host
 */
@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class HostOsVersionAllocatorFlow  extends AbstractHostAllocatorFlow {
    @Autowired
    private HostManager manager;
    private static CLogger logger = Utils.getLogger(HostOsVersionAllocatorFlow.class);

    @Override
    public void allocate() {
        if (HostAllocatorConstant.MIGRATE_VM_ALLOCATOR_TYPE.equals(spec.getAllocatorStrategy()) &&
                HostAllocatorGlobalConfig.MIGRATION_BETWEEN_DIFFERENT_OS.value(Boolean.class)) {
            logger.debug("allow migration between hosts with different os, skip checking the target host os");
            next(candidates);
            return;
        }
        throwExceptionIfIAmTheFirstFlow();

        Map<String, HostVO> hostMap = toMap(candidates, HostVO::getUuid, Function.identity());
        final VmInstanceInventory vm = spec.getVmInstance();
        String currentHostUuid = vm.getHostUuid() == null ? vm.getLastHostUuid() : vm.getHostUuid();
        if (currentHostUuid == null) {
            logger.debug(String.format("VM[uuid:%s] never started on any host, skip host OS checker", vm.getUuid()));
            next(candidates);
            return;
        }

        List<HostVO> allHostList = new ArrayList<>(candidates);
        final HostVO currentHost = Q.New(HostVO.class)
                .eq(HostVO_.uuid, currentHostUuid)
                .find();
        allHostList.add(currentHost);
        final Map<String, HostOperationSystem> hostOsMap = generateHostUuidOsMap(allHostList);

        final HostOperationSystem currentHostOs = hostOsMap.get(currentHostUuid);
        allHostList.remove(currentHost);

        List<HostVO> matchedHosts = allHostList.stream()
                .map(HostVO::getUuid)
                .filter(hostUuid -> {
                    final HostOperationSystem hostOs = hostOsMap.get(hostUuid);
                    return hostOs == null ? true : hostOs.equals(currentHostOs);
                })
                .map(hostMap::get)
                .collect(Collectors.toList());

        if (matchedHosts.isEmpty()) {
            fail(Platform.operr("no candidate host has version[%s]", currentHostOs));
        } else {
            next(matchedHosts);
        }
    }

    private Map<String, HostOperationSystem> generateHostUuidOsMap(List<HostVO> hostList) {
        final Map<String, String> hostHypervisorTypeMap = hostList.stream()
                .collect(Collectors.toMap(ResourceVO::getUuid, HostAO::getHypervisorType,
                        (existing, replacement) -> replacement));
        final Set<String> hypervisorTypeSet = new HashSet<>(hostHypervisorTypeMap.values());

        final Map<String, HostOperationSystem> results = new HashMap<>(hostList.size());
        for (String hypervisorTypeString : hypervisorTypeSet) {
            final HypervisorFactory factory = manager.getHypervisorFactory(
                    HypervisorType.valueOf(hypervisorTypeString));
            final List<String> hostsWithThisHypervisorType = hostHypervisorTypeMap.entrySet().stream()
                    .filter(entry -> hypervisorTypeString.equals(entry.getValue()))
                    .map(Entry::getKey)
                    .collect(Collectors.toList());

            if (factory.supportGetHostOs()) {
                results.putAll(factory.getHostOsMap(hostsWithThisHypervisorType));
            }
        }

        return results;
    }
}
