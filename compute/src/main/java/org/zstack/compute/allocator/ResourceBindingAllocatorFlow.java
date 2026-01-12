package org.zstack.compute.allocator;

import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.util.StringUtils;
import org.zstack.compute.vm.VmGlobalConfig;
import org.zstack.compute.vm.VmSystemTags;
import org.zstack.core.Platform;
import org.zstack.core.componentloader.PluginRegistry;
import org.zstack.header.allocator.AbstractHostAllocatorFlow;
import org.zstack.header.allocator.AllocationScene;
import org.zstack.header.allocator.HostAllocatorConstant;
import org.zstack.header.allocator.ResourceBindingCollector;
import org.zstack.header.allocator.ResourceBindingStrategy;
import org.zstack.header.host.HostVO;
import org.zstack.header.vm.VmInstanceConstant.VmOperation;
import org.zstack.resourceconfig.ResourceConfigFacade;

import java.util.*;
import java.util.stream.Collectors;
import static org.zstack.utils.clouderrorcode.CloudOperationsErrorCode.*;

/**
 * @ Author : yh.w
 * @ Date   : Created in 18:11 2019/11/26
 */
@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class ResourceBindingAllocatorFlow extends AbstractHostAllocatorFlow {

    @Autowired
    protected PluginRegistry pluginRgty;
    @Autowired
    private ResourceConfigFacade rcf;

    private static Map<String, ResourceBindingCollector> collectors = Collections.synchronizedMap(new HashMap<>());

    private static String SPLIT = ",";

    {
        List<ResourceBindingCollector> cs = pluginRgty.getExtensionList(ResourceBindingCollector.class);
        for (ResourceBindingCollector collector : cs) {
            collectors.put(collector.getType(), collector);
        }
    }

    private Map<String, List<String>> getBindedResourcesFromTag() {
        String resources = VmSystemTags.VM_RESOURCE_BINGDING
                .getTokenByResourceUuid(spec.getVmInstance().getUuid(), VmSystemTags.VM_RESOURCE_BINGDING_TOKEN);

        if (StringUtils.isEmpty(resources)) {
            return null;
        }

        Map<String, List<String>> resourceMap = new HashMap<>();
        for (String resource : resources.split(SPLIT)) {
            String type = resource.split(":")[0];
            String uuid = resource.split(":")[1];
            List<String> resourceList = resourceMap.computeIfAbsent(type, k -> new ArrayList<>());
            resourceList.add(uuid);
        }

        return resourceMap;
    }

    private boolean validateAllocationScene() {
        String as = rcf.getResourceConfigValue(VmGlobalConfig.RESOURCE_BINDING_SCENE, spec.getVmInstance().getUuid(), String.class);
        if (as.equals(AllocationScene.All.toString())) {
            return true;
        }

        if (spec.getAllocationScene() != null) {
            return as.equals(spec.getAllocationScene().toString());
        }

        return false;
    }

    @Override
    public void allocate() {
        throwExceptionIfIAmTheFirstFlow();

        Boolean resourceConfig = rcf.getResourceConfigValue(VmGlobalConfig.VM_HA_ACROSS_CLUSTERS, spec.getVmInstance().getUuid(), Boolean.class);
        if (!validateAllocationScene() || (!VmSystemTags.VM_RESOURCE_BINGDING.hasTag(spec.getVmInstance().getUuid()) && resourceConfig)) {
            next(candidates);
            return;
        }

        // get bind resources from system tag
        Map<String, List<String>> resources = getBindedResourcesFromTag();
        resources = resources != null ? resources : new HashMap<>();
        // get bind resources from config
        ResourceBindingClusterCollector clusterCollector = new ResourceBindingClusterCollector();
        if (!resourceConfig) {
            //remove bind cluster uuid from system tag, use current cluster uuid from config
            if (resources.containsKey(clusterCollector.getType())) {
                List<String> uuids = resources.get(clusterCollector.getType());
                if (!uuids.contains(spec.getVmInstance().getClusterUuid())) {
                    String tag = String.format("Cluster:%s", spec.getVmInstance().getClusterUuid());
                    VmSystemTags.VM_RESOURCE_BINGDING.updateTagByToken(spec.getVmInstance().getUuid(),
                            VmSystemTags.VM_RESOURCE_BINGDING_TOKEN, tag);
                }
                resources.remove(clusterCollector.getType());
            }

            resources.computeIfAbsent(clusterCollector.getType(), k -> new ArrayList<>()).add(spec.getVmInstance().getClusterUuid());
        }

        if (resources.isEmpty()) {
            next(candidates);
            return;
        }

        List<HostVO> availableHost = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : resources.entrySet()) {
            ResourceBindingCollector collector = collectors.get(entry.getKey());
            if (collector == null) {
                fail(Platform.operr(ORG_ZSTACK_COMPUTE_ALLOCATOR_10004, "resource binding not support type %s yet", entry.getKey()));
                return;
            }
            availableHost.addAll(collector.collect(entry.getValue()));
        }

        List<HostVO> filteredHost = candidates.stream()
                .filter(v -> availableHost.stream().anyMatch(h -> h.getUuid().equals(v.getUuid())))
                .collect(Collectors.toList());

        if (CollectionUtils.isNotEmpty(filteredHost)) {
            next(filteredHost);
            return;
        }

        // Check if user explicitly designated a target host for migration
        // Only migration operation with a designated host should be rejected for cross-cluster
        // Other scenarios (e.g., online CPU/memory change) may set hostUuid but it's not user-designated migration target
        String designatedHostUuid = (String) spec.getExtraData().get(HostAllocatorConstant.LocationSelector.host);
        boolean isUserDesignatedMigration = VmOperation.Migrate.toString().equals(spec.getVmOperation())
                && designatedHostUuid != null;

        if (isUserDesignatedMigration) {
            // User explicitly designated a migration target host, but that host is not in bound resources
            // This should fail even if strategy is Soft
            fail(Platform.operr(ORG_ZSTACK_COMPUTE_ALLOCATOR_10038,"designated host[uuid:%s] is not in bound resource %s, " +
                    "vm bindingStrategy is %s, vm bindingScene is %s, vm.ha" +
                    ".across.clusters is %s",
                    designatedHostUuid, resources,
                    rcf.getResourceConfigValue(VmGlobalConfig.RESOURCE_BINDING_STRATEGY, spec.getVmInstance().getUuid(), String.class),
                    rcf.getResourceConfigValue(VmGlobalConfig.RESOURCE_BINDING_SCENE, spec.getVmInstance().getUuid(), String.class),
                    rcf.getResourceConfigValue(VmGlobalConfig.VM_HA_ACROSS_CLUSTERS, spec.getVmInstance().getUuid(), Boolean.class)));
            return;
        }

        // No designated host, system is auto-allocating
        // Apply Soft strategy only in this case
        if (rcf.getResourceConfigValue(VmGlobalConfig.RESOURCE_BINDING_STRATEGY, spec.getVmInstance().getUuid(), String.class)
                .equals(ResourceBindingStrategy.Soft.toString())) {
            next(candidates);
        } else {
            fail(Platform.operr(ORG_ZSTACK_COMPUTE_ALLOCATOR_10005, "no available host found with bound resource %s", resources));
        }
    }
}
