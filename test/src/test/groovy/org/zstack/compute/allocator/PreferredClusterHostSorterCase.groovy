package org.zstack.compute.allocator

import org.junit.Test
import org.zstack.header.allocator.HostAllocatorSpec
import org.zstack.header.host.HostInventory

class PreferredClusterHostSorterCase {
    @Test
    void testNoPreferClusterDoesNotChangeHostOrder() {
        HostAllocatorSpec spec = new HostAllocatorSpec()
        List<HostInventory> hosts = [
                host("host1", "cluster1"),
                host("host2", "cluster2")
        ]

        boolean sorted = new PreferredClusterHostSorter().sort(spec, hosts)

        assert !sorted
        assert hostUuids(hosts) == ["host1", "host2"]
    }

    @Test
    void testMissingPreferClusterDoesNotChangeHostOrder() {
        HostAllocatorSpec spec = new HostAllocatorSpec()
        spec.setPreferClusterUuid("cluster3")
        List<HostInventory> hosts = [
                host("host1", "cluster1"),
                host("host2", "cluster2")
        ]

        boolean sorted = new PreferredClusterHostSorter().sort(spec, hosts)

        assert !sorted
        assert hostUuids(hosts) == ["host1", "host2"]
    }

    @Test
    void testPreferClusterSortsHostOrder() {
        HostAllocatorSpec spec = new HostAllocatorSpec()
        spec.setPreferClusterUuid("cluster1")
        List<HostInventory> hosts = [
                host("host1", "cluster2"),
                host("host2", "cluster1")
        ]

        boolean sorted = new PreferredClusterHostSorter().sort(spec, hosts)

        assert sorted
        assert hostUuids(hosts) == ["host2", "host1"]
    }

    private static HostInventory host(String uuid, String clusterUuid) {
        HostInventory host = new HostInventory()
        host.setUuid(uuid)
        host.setClusterUuid(clusterUuid)
        return host
    }

    private static List<String> hostUuids(List<HostInventory> hosts) {
        return hosts.collect { it.uuid }
    }
}
