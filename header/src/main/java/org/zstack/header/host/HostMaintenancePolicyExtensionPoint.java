package org.zstack.header.host;

import java.util.Map;

/**
 * Created by frank on 10/25/2015.
 */
public interface HostMaintenancePolicyExtensionPoint {
    public static enum ExtensionHostMaintenancePolicy {
        MigrateVm,
        StopVm
    }

    Map<String, ExtensionHostMaintenancePolicy> getHostMaintenanceVmOperationPolicy(HostInventory host);
}
