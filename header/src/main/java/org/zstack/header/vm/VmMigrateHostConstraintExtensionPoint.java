package org.zstack.header.vm;

import java.util.List;

/**
 * Reports VMs that are pinned to their current host and cannot be
 * live-migrated because of attached passthrough devices, such as physical
 * PCI passthrough devices, dGPU and mediated (mdev) devices.
 *
 * It is queried by DRS scheduling so that automatic load balancing skips
 * such VMs instead of generating migration tasks that are bound to fail.
 *
 * VF / vDPA NIC devices do NOT pin a VM to its host (they have dedicated
 * migration support) and therefore must NOT be reported here.
 */
public interface VmMigrateHostConstraintExtensionPoint {
    /**
     * @param candidateVmUuids VM uuids being considered for migration
     * @return the subset of candidateVmUuids that cannot be migrated off
     *         their current host because of attached passthrough devices
     */
    List<String> getHostBoundVmUuids(List<String> candidateVmUuids);
}
