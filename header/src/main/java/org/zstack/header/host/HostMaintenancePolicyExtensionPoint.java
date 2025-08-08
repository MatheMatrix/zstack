package org.zstack.header.host;

import java.util.Map;

/**
 * Created by frank on 10/25/2015.
 */
public interface HostMaintenancePolicyExtensionPoint {
    public static enum HostMaintenanceVmOperationPolicy {
        MigrateVm,
        StopVm
    }

    Map<String, HostMaintenanceVmOperationPolicy> getHostMaintenanceVmOperationPolicy(HostInventory host);
}
