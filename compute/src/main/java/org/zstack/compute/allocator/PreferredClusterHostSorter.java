package org.zstack.compute.allocator;

import org.zstack.header.allocator.HostAllocatorSpec;
import org.zstack.header.host.HostInventory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class PreferredClusterHostSorter {
    boolean sort(HostAllocatorSpec spec, List<HostInventory> hosts) {
        String preferClusterUuid = spec.getPreferClusterUuid();
        if (preferClusterUuid == null) {
            return false;
        }

        List<String> softAvoidHostUuids = spec.getSoftAvoidHostUuids();
        Set<String> softAvoidHostUuidSet = softAvoidHostUuids == null ?
                Collections.emptySet() : new HashSet<>(softAvoidHostUuids);
        List<HostInventory> preferredHosts = new ArrayList<>();
        List<HostInventory> otherHosts = new ArrayList<>();
        List<HostInventory> preferredSoftAvoidHosts = new ArrayList<>();
        List<HostInventory> otherSoftAvoidHosts = new ArrayList<>();

        for (HostInventory host : hosts) {
            boolean preferCluster = preferClusterUuid.equals(host.getClusterUuid());
            boolean softAvoid = softAvoidHostUuidSet.contains(host.getUuid());

            if (preferCluster && !softAvoid) {
                preferredHosts.add(host);
            } else if (!preferCluster && !softAvoid) {
                otherHosts.add(host);
            } else if (preferCluster) {
                preferredSoftAvoidHosts.add(host);
            } else {
                otherSoftAvoidHosts.add(host);
            }
        }

        if (preferredHosts.isEmpty() && preferredSoftAvoidHosts.isEmpty()) {
            return false;
        }

        hosts.clear();
        hosts.addAll(preferredHosts);
        hosts.addAll(otherHosts);
        hosts.addAll(preferredSoftAvoidHosts);
        hosts.addAll(otherSoftAvoidHosts);
        return true;
    }
}
