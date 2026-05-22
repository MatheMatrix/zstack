package org.zstack.kvm;

import org.zstack.header.vm.ArchiveResourceConfigBundle;
import org.zstack.header.vm.ResourceConfigMemorySnapshotExtensionPoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.resourceconfig.ResourceConfig;
import org.zstack.resourceconfig.ResourceConfigFacade;
import org.zstack.resourceconfig.ResourceConfigInventory;

import java.util.ArrayList;
import java.util.List;

public class KvmResourceConfigExtension implements ResourceConfigMemorySnapshotExtensionPoint {
    @Autowired
    private ResourceConfigFacade rcf;

    @Override
    public List<ArchiveResourceConfigBundle.ResourceConfigBundle> getNeedToArchiveResourceConfig(String resourceUuid) {
        List<ArchiveResourceConfigBundle.ResourceConfigBundle> bundleList = new ArrayList<>();
        ArchiveResourceConfigBundle.ResourceConfigBundle bundle = new ArchiveResourceConfigBundle.ResourceConfigBundle();
        bundle.setResourceUuid(resourceUuid);
        bundle.setIdentity(KVMGlobalConfig.NESTED_VIRTUALIZATION.getIdentity());
        bundle.setValue(rcf.getResourceConfigValue(KVMGlobalConfig.NESTED_VIRTUALIZATION, resourceUuid, String.class));
        bundleList.add(bundle);
        addExplicitResourceConfigBundle(bundleList, resourceUuid, KVMGlobalConfig.VM_CPU_HARDWARE_VIRTUALIZATION.getIdentity());
        return bundleList;
    }

    private void addExplicitResourceConfigBundle(List<ArchiveResourceConfigBundle.ResourceConfigBundle> bundleList,
                                                 String resourceUuid,
                                                 String resourceConfigIdentity) {
        ResourceConfig resourceConfig = rcf.getResourceConfig(resourceConfigIdentity);
        if (!resourceConfig.resourceConfigCreated(resourceUuid)) {
            return;
        }

        ResourceConfigInventory explicitConfig = resourceConfig.getEffectiveResourceConfigs(resourceUuid).stream()
                .filter(config -> resourceUuid.equals(config.getResourceUuid()))
                .findFirst()
                .orElse(null);
        if (explicitConfig == null) {
            return;
        }

        ArchiveResourceConfigBundle.ResourceConfigBundle bundle = new ArchiveResourceConfigBundle.ResourceConfigBundle();
        bundle.setResourceUuid(resourceUuid);
        bundle.setIdentity(resourceConfigIdentity);
        bundle.setValue(explicitConfig.getValue());
        bundleList.add(bundle);
    }
}
