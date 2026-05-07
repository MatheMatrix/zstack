package org.zstack.test.integration.server

import org.zstack.sdk.ClusterInventory
import org.zstack.sdk.PhysicalServerInventory
import org.zstack.sdk.PhysicalServerProvisionNetworkInventory
import org.zstack.sdk.ServerPoolInventory
import org.zstack.sdk.ZoneInventory
import org.zstack.test.integration.baremetal2.BareMetal2Test
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.premium.PremiumSubCase

class ServerPoolCrudCase extends PremiumSubCase {
    EnvSpec env

    @Override
    void setup() {
        useSpring(BareMetal2Test.springSpec)
    }

    @Override
    void environment() {
        env = makeEnv {
            zone {
                name = "zone"
                cluster {
                    name = "cluster"
                }
            }
        }
    }

    @Override
    void test() {
        env.create {
            testCreateServerPool()
            testUpdateServerPool()
            testQueryServerPool()
            testDeleteServerPoolBlockedByPhysicalServer()
            testDeleteServerPool()
            testCreatePhysicalServer()
            testUpdatePhysicalServer()
            testQueryPhysicalServer()
            testDeletePhysicalServer()
            testChangePhysicalServerState()
            testChangeClusterServerPool()
            testDeleteServerPoolClearsClusterAssociation()
            testCreateProvisionNetwork()
            testDeleteProvisionNetwork()
            testAttachDetachProvisionNetworkToCluster()
            testCreatePhysicalServerDuplicateSerialNumber()
            testCreatePhysicalServerPoolZoneMismatch()
            testCreatePhysicalServerWithoutPoolUuid()
            testCreateProvisionNetworkGatewayPxe()
            testCreateProvisionNetworkWithDhcpFields()
            testOneClusterMultipleProvisionNetworks()
            testDeleteProvisionNetworkBlockedByCluster()
            testQueryProvisionNetwork()
            testUpdateServerPoolNetworkTopology()
            testUpdateProvisionNetwork()
            testUpdatePhysicalServerMoreFields()
            testChangePhysicalServerStateInvalidEvent()
            testMultipleClustersInOnePool()
            testOneProvisionNetworkMultipleClusters()
            testUpdatePhysicalServerPoolUuid()
        }
    }

    void testCreatePhysicalServerDuplicateSerialNumber() {
        def zone = env.inventoryByName("zone") as ZoneInventory
        def pool = createServerPool {
            name = "pool-dup-sn"
            zoneUuid = zone.uuid
        } as ServerPoolInventory

        def server1 = createPhysicalServer {
            name = "server-sn1"
            zoneUuid = zone.uuid
            poolUuid = pool.uuid
            managementIp = "192.168.10.1"
            serialNumber = "DUPE-SN-001"
        } as PhysicalServerInventory

        // Same serialNumber in same zone should fail
        expect(Throwable.class) {
            createPhysicalServer {
                name = "server-sn2"
                zoneUuid = zone.uuid
                poolUuid = pool.uuid
                managementIp = "192.168.10.2"
                serialNumber = "DUPE-SN-001"
            }
        }

        deletePhysicalServer { uuid = server1.uuid }
        deleteServerPool { uuid = pool.uuid }
    }

    void testCreatePhysicalServerPoolZoneMismatch() {
        def zone = env.inventoryByName("zone") as ZoneInventory
        def zone2 = createZone { name = "zone2" } as ZoneInventory

        def pool = createServerPool {
            name = "pool-zone-mismatch"
            zoneUuid = zone.uuid
        } as ServerPoolInventory

        // Create server in zone2 but reference pool in zone1 → should fail
        expect(Throwable.class) {
            createPhysicalServer {
                name = "server-mismatch"
                zoneUuid = zone2.uuid
                poolUuid = pool.uuid
                managementIp = "192.168.11.1"
            }
        }

        deleteServerPool { uuid = pool.uuid }
        deleteZone { uuid = zone2.uuid }
    }

    void testCreatePhysicalServerWithoutPoolUuid() {
        def zone = env.inventoryByName("zone") as ZoneInventory

        // poolUuid is required by @APIParam, should fail
        expect(Throwable.class) {
            createPhysicalServer {
                name = "server-no-pool"
                zoneUuid = zone.uuid
                managementIp = "192.168.12.1"
            }
        }
    }

    void testCreateProvisionNetworkGatewayPxe() {
        def zone = env.inventoryByName("zone") as ZoneInventory

        def net = createProvisionNetwork {
            name = "gateway-pxe-net"
            zoneUuid = zone.uuid
            type = "GATEWAY_PXE"
            dhcpInterface = "eth0"
            dhcpRangeStartIp = "192.168.20.100"
            dhcpRangeEndIp = "192.168.20.200"
            dhcpRangeNetmask = "255.255.255.0"
            dhcpRangeGateway = "192.168.20.1"
        } as PhysicalServerProvisionNetworkInventory

        assert net.type == "GATEWAY_PXE"
        assert net.dhcpInterface == "eth0"
        assert net.dhcpRangeStartIp == "192.168.20.100"
        assert net.dhcpRangeEndIp == "192.168.20.200"
        assert net.dhcpRangeNetmask == "255.255.255.0"
        assert net.dhcpRangeGateway == "192.168.20.1"

        deleteProvisionNetwork { uuid = net.uuid }
    }

    void testCreateProvisionNetworkWithDhcpFields() {
        def zone = env.inventoryByName("zone") as ZoneInventory

        def net = createProvisionNetwork {
            name = "pxe-dhcp"
            zoneUuid = zone.uuid
            type = "STANDALONE_PXE"
            dhcpInterface = "bond0"
            dhcpRangeStartIp = "10.0.0.100"
            dhcpRangeEndIp = "10.0.0.200"
            dhcpRangeNetmask = "255.255.255.0"
            dhcpRangeGateway = "10.0.0.1"
        } as PhysicalServerProvisionNetworkInventory

        assert net.dhcpInterface == "bond0"
        assert net.dhcpRangeStartIp == "10.0.0.100"
        assert net.dhcpRangeEndIp == "10.0.0.200"
        assert net.dhcpRangeNetmask == "255.255.255.0"
        assert net.dhcpRangeGateway == "10.0.0.1"

        deleteProvisionNetwork { uuid = net.uuid }
    }

    void testOneClusterMultipleProvisionNetworks() {
        def zone = env.inventoryByName("zone") as ZoneInventory
        def cluster = env.inventoryByName("cluster") as ClusterInventory

        def net1 = createProvisionNetwork {
            name = "pxe-net-multi-1"
            zoneUuid = zone.uuid
            type = "STANDALONE_PXE"
        } as PhysicalServerProvisionNetworkInventory

        def net2 = createProvisionNetwork {
            name = "pxe-net-multi-2"
            zoneUuid = zone.uuid
            type = "GATEWAY_PXE"
        } as PhysicalServerProvisionNetworkInventory

        // Attach both to same cluster
        attachProvisionNetworkToCluster {
            networkUuid = net1.uuid
            clusterUuid = cluster.uuid
        }

        attachProvisionNetworkToCluster {
            networkUuid = net2.uuid
            clusterUuid = cluster.uuid
        }

        // Both should succeed — detach and cleanup
        detachProvisionNetworkFromCluster {
            networkUuid = net1.uuid
            clusterUuid = cluster.uuid
        }
        detachProvisionNetworkFromCluster {
            networkUuid = net2.uuid
            clusterUuid = cluster.uuid
        }

        deleteProvisionNetwork { uuid = net1.uuid }
        deleteProvisionNetwork { uuid = net2.uuid }
    }

    void testMultipleClustersInOnePool() {
        def zone = env.inventoryByName("zone") as ZoneInventory
        def cluster1 = env.inventoryByName("cluster") as ClusterInventory

        // Create a second cluster for this test
        def cluster2 = createCluster {
            name = "cluster2-for-pool"
            zoneUuid = zone.uuid
            hypervisorType = "KVM"
        } as ClusterInventory

        def pool = createServerPool {
            name = "pool-multi-cluster"
            zoneUuid = zone.uuid
        } as ServerPoolInventory

        // Assign both clusters to same pool
        changeClusterServerPool {
            clusterUuid = cluster1.uuid
            serverPoolUuid = pool.uuid
        }

        changeClusterServerPool {
            clusterUuid = cluster2.uuid
            serverPoolUuid = pool.uuid
        }

        // Both should succeed — cleanup
        deleteServerPool { uuid = pool.uuid }
        deleteCluster { uuid = cluster2.uuid }
    }

    void testOneProvisionNetworkMultipleClusters() {
        def zone = env.inventoryByName("zone") as ZoneInventory
        def cluster1 = env.inventoryByName("cluster") as ClusterInventory

        def cluster2 = createCluster {
            name = "cluster2-for-pn"
            zoneUuid = zone.uuid
            hypervisorType = "KVM"
        } as ClusterInventory

        def net = createProvisionNetwork {
            name = "pxe-multi-cluster"
            zoneUuid = zone.uuid
            type = "STANDALONE_PXE"
        } as PhysicalServerProvisionNetworkInventory

        // Attach same PN to both clusters
        attachProvisionNetworkToCluster {
            networkUuid = net.uuid
            clusterUuid = cluster1.uuid
        }

        attachProvisionNetworkToCluster {
            networkUuid = net.uuid
            clusterUuid = cluster2.uuid
        }

        // Both should succeed — cleanup
        detachProvisionNetworkFromCluster {
            networkUuid = net.uuid
            clusterUuid = cluster1.uuid
        }
        detachProvisionNetworkFromCluster {
            networkUuid = net.uuid
            clusterUuid = cluster2.uuid
        }

        deleteProvisionNetwork { uuid = net.uuid }
        deleteCluster { uuid = cluster2.uuid }
    }

    @Override
    void clean() {
        env.delete()
    }

    void testCreateServerPool() {
        def zone = env.inventoryByName("zone") as ZoneInventory

        def pool = createServerPool {
            name = "test-pool"
            zoneUuid = zone.uuid
            description = "desc"
            physicalLocation = "rack-1"
            networkTopology = "spine-leaf"
        } as ServerPoolInventory

        assert pool.uuid != null
        assert pool.name == "test-pool"
        assert pool.description == "desc"
        assert pool.zoneUuid == zone.uuid
        assert pool.physicalLocation == "rack-1"
        assert pool.networkTopology == "spine-leaf"
        assert pool.state == "Enabled"
        assert pool.createDate != null
        assert pool.lastOpDate != null

        deleteServerPool { uuid = pool.uuid }
    }

    void testUpdateServerPool() {
        def zone = env.inventoryByName("zone") as ZoneInventory

        def pool = createServerPool {
            name = "pool-update"
            zoneUuid = zone.uuid
        } as ServerPoolInventory

        def updated = updateServerPool {
            uuid = pool.uuid
            name = "pool-updated"
            description = "new desc"
            physicalLocation = "rack-2"
        } as ServerPoolInventory

        assert updated.name == "pool-updated"
        assert updated.description == "new desc"
        assert updated.physicalLocation == "rack-2"

        deleteServerPool { uuid = pool.uuid }
    }

    void testQueryServerPool() {
        def zone = env.inventoryByName("zone") as ZoneInventory

        def pool = createServerPool {
            name = "pool-query"
            zoneUuid = zone.uuid
        } as ServerPoolInventory

        def pools = queryServerPool {
            conditions = ["name=pool-query"]
        }

        assert pools.size() == 1
        assert pools[0].uuid == pool.uuid

        deleteServerPool { uuid = pool.uuid }
    }

    void testDeleteServerPool() {
        def zone = env.inventoryByName("zone") as ZoneInventory

        def pool = createServerPool {
            name = "pool-delete"
            zoneUuid = zone.uuid
        } as ServerPoolInventory

        deleteServerPool { uuid = pool.uuid }

        def pools = queryServerPool {
            conditions = ["uuid=${pool.uuid}".toString()]
        }
        assert pools.size() == 0
    }

    void testDeleteServerPoolClearsClusterAssociation() {
        def zone = env.inventoryByName("zone") as ZoneInventory
        def cluster = env.inventoryByName("cluster") as ClusterInventory

        def pool = createServerPool {
            name = "pool-delete-cluster-link"
            zoneUuid = zone.uuid
        } as ServerPoolInventory

        changeClusterServerPool {
            clusterUuid = cluster.uuid
            serverPoolUuid = pool.uuid
        }

        deleteServerPool { uuid = pool.uuid }

        def clusters = queryCluster {
            conditions = ["uuid=${cluster.uuid}".toString()]
        }
        assert clusters[0].serverPoolUuid == null
    }

    void testDeleteServerPoolBlockedByPhysicalServer() {
        def zone = env.inventoryByName("zone") as ZoneInventory

        def pool = createServerPool {
            name = "pool-blocked"
            zoneUuid = zone.uuid
        } as ServerPoolInventory

        def server = createPhysicalServer {
            name = "server-in-pool"
            zoneUuid = zone.uuid
            poolUuid = pool.uuid
            managementIp = "192.168.100.10"
        } as PhysicalServerInventory

        expect(Throwable.class) {
            deleteServerPool { uuid = pool.uuid }
        }

        deletePhysicalServer { uuid = server.uuid }
        deleteServerPool { uuid = pool.uuid }

        def pools = queryServerPool {
            conditions = ["uuid=${pool.uuid}".toString()]
        }
        assert pools.size() == 0
    }

    void testCreatePhysicalServer() {
        def zone = env.inventoryByName("zone") as ZoneInventory

        def pool = createServerPool {
            name = "pool-for-server"
            zoneUuid = zone.uuid
        } as ServerPoolInventory

        def server = createPhysicalServer {
            name = "server-1"
            zoneUuid = zone.uuid
            poolUuid = pool.uuid
            managementIp = "192.168.1.10"
            description = "test server"
            architecture = "x86_64"
            serialNumber = "SN001"
            manufacturer = "Dell"
            model = "PowerEdge R740"
            oobManagementType = "IPMI"
            oobAddress = "192.168.1.100"
            oobPort = 623
            oobUsername = "admin"
        } as PhysicalServerInventory

        assert server.uuid != null
        assert server.name == "server-1"
        assert server.zoneUuid == zone.uuid
        assert server.poolUuid == pool.uuid
        assert server.managementIp == "192.168.1.10"
        assert server.description == "test server"
        assert server.architecture == "x86_64"
        assert server.serialNumber == "SN001"
        assert server.manufacturer == "Dell"
        assert server.model == "PowerEdge R740"
        assert server.oobManagementType == "IPMI"
        assert server.oobAddress == "192.168.1.100"
        assert server.oobPort == 623
        assert server.oobUsername == "admin"
        assert server.state == "Enabled"
        assert server.powerStatus == "Unknown"
        assert server.createDate != null
        assert server.lastOpDate != null

        deletePhysicalServer { uuid = server.uuid }
        deleteServerPool { uuid = pool.uuid }
    }

    void testUpdatePhysicalServer() {
        def zone = env.inventoryByName("zone") as ZoneInventory

        def pool = createServerPool {
            name = "pool-for-update-server"
            zoneUuid = zone.uuid
        } as ServerPoolInventory

        def server = createPhysicalServer {
            name = "server-update"
            zoneUuid = zone.uuid
            poolUuid = pool.uuid
            managementIp = "192.168.2.10"
        } as PhysicalServerInventory

        def updated = updatePhysicalServer {
            uuid = server.uuid
            name = "server-updated"
            managementIp = "192.168.2.20"
            description = "updated desc"
        } as PhysicalServerInventory

        assert updated.name == "server-updated"
        assert updated.managementIp == "192.168.2.20"
        assert updated.description == "updated desc"

        deletePhysicalServer { uuid = server.uuid }
        deleteServerPool { uuid = pool.uuid }
    }

    void testQueryPhysicalServer() {
        def zone = env.inventoryByName("zone") as ZoneInventory

        def pool = createServerPool {
            name = "pool-for-query-server"
            zoneUuid = zone.uuid
        } as ServerPoolInventory

        def server = createPhysicalServer {
            name = "server-query"
            zoneUuid = zone.uuid
            poolUuid = pool.uuid
            managementIp = "192.168.3.10"
        } as PhysicalServerInventory

        def servers = queryPhysicalServer {
            conditions = ["name=server-query"]
        }

        assert servers.size() == 1
        assert servers[0].uuid == server.uuid

        deletePhysicalServer { uuid = server.uuid }
        deleteServerPool { uuid = pool.uuid }
    }

    void testDeletePhysicalServer() {
        def zone = env.inventoryByName("zone") as ZoneInventory

        def pool = createServerPool {
            name = "pool-for-del-server"
            zoneUuid = zone.uuid
        } as ServerPoolInventory

        def server = createPhysicalServer {
            name = "server-del"
            zoneUuid = zone.uuid
            poolUuid = pool.uuid
            managementIp = "192.168.4.10"
        } as PhysicalServerInventory

        deletePhysicalServer { uuid = server.uuid }

        def servers = queryPhysicalServer {
            conditions = ["uuid=${server.uuid}".toString()]
        }
        assert servers.size() == 0

        deleteServerPool { uuid = pool.uuid }
    }

    void testChangePhysicalServerState() {
        def zone = env.inventoryByName("zone") as ZoneInventory

        def pool = createServerPool {
            name = "pool-for-state"
            zoneUuid = zone.uuid
        } as ServerPoolInventory

        def server = createPhysicalServer {
            name = "server-state"
            zoneUuid = zone.uuid
            poolUuid = pool.uuid
            managementIp = "192.168.5.10"
        } as PhysicalServerInventory

        assert server.state == "Enabled"

        def disabled = changePhysicalServerState {
            uuid = server.uuid
            stateEvent = "disable"
        } as PhysicalServerInventory
        assert disabled.state == "Disabled"

        def maintained = changePhysicalServerState {
            uuid = server.uuid
            stateEvent = "maintain"
        } as PhysicalServerInventory
        assert maintained.state == "Maintenance"

        def enabled = changePhysicalServerState {
            uuid = server.uuid
            stateEvent = "enable"
        } as PhysicalServerInventory
        assert enabled.state == "Enabled"

        deletePhysicalServer { uuid = server.uuid }
        deleteServerPool { uuid = pool.uuid }
    }

    void testChangeClusterServerPool() {
        def zone = env.inventoryByName("zone") as ZoneInventory
        def cluster = env.inventoryByName("cluster") as ClusterInventory

        def pool1 = createServerPool {
            name = "pool-change-1"
            zoneUuid = zone.uuid
        } as ServerPoolInventory

        def pool2 = createServerPool {
            name = "pool-change-2"
            zoneUuid = zone.uuid
        } as ServerPoolInventory

        // First assignment
        def result = changeClusterServerPool {
            clusterUuid = cluster.uuid
            serverPoolUuid = pool1.uuid
        } as ServerPoolInventory

        assert result.uuid == pool1.uuid

        // Verify cluster now has serverPoolUuid set
        def clusters1 = queryCluster {
            conditions = ["uuid=${cluster.uuid}".toString()]
        }
        assert clusters1[0].serverPoolUuid == pool1.uuid

        // Change to another pool (idempotent update, not error)
        def result2 = changeClusterServerPool {
            clusterUuid = cluster.uuid
            serverPoolUuid = pool2.uuid
        } as ServerPoolInventory

        assert result2.uuid == pool2.uuid

        // Verify cluster now points to pool2
        def clusters2 = queryCluster {
            conditions = ["uuid=${cluster.uuid}".toString()]
        }
        assert clusters2[0].serverPoolUuid == pool2.uuid

        deleteServerPool { uuid = pool1.uuid }
        deleteServerPool { uuid = pool2.uuid }
    }

    void testCreateProvisionNetwork() {
        def zone = env.inventoryByName("zone") as ZoneInventory

        def net = createProvisionNetwork {
            name = "pxe-net"
            zoneUuid = zone.uuid
            type = "STANDALONE_PXE"
            description = "pxe network"
        } as PhysicalServerProvisionNetworkInventory

        assert net.uuid != null
        assert net.name == "pxe-net"
        assert net.zoneUuid == zone.uuid
        assert net.type == "STANDALONE_PXE"
        assert net.description == "pxe network"
        assert net.createDate != null
        assert net.lastOpDate != null

        deleteProvisionNetwork { uuid = net.uuid }
    }

    void testDeleteProvisionNetwork() {
        def zone = env.inventoryByName("zone") as ZoneInventory

        def net = createProvisionNetwork {
            name = "pxe-net-del"
            zoneUuid = zone.uuid
            type = "STANDALONE_PXE"
        } as PhysicalServerProvisionNetworkInventory

        deleteProvisionNetwork { uuid = net.uuid }

        def nets = queryProvisionNetwork {
            conditions = ["uuid=${net.uuid}".toString()]
        }
        assert nets.size() == 0
    }

    void testAttachDetachProvisionNetworkToCluster() {
        def zone = env.inventoryByName("zone") as ZoneInventory
        def cluster = env.inventoryByName("cluster") as ClusterInventory

        def net = createProvisionNetwork {
            name = "pxe-net-attach"
            zoneUuid = zone.uuid
            type = "STANDALONE_PXE"
        } as PhysicalServerProvisionNetworkInventory

        attachProvisionNetworkToCluster {
            networkUuid = net.uuid
            clusterUuid = cluster.uuid
        }

        expect(Throwable.class) {
            attachProvisionNetworkToCluster {
                networkUuid = net.uuid
                clusterUuid = cluster.uuid
            }
        }

        detachProvisionNetworkFromCluster {
            networkUuid = net.uuid
            clusterUuid = cluster.uuid
        }

        deleteProvisionNetwork { uuid = net.uuid }
    }

    void testDeleteProvisionNetworkBlockedByCluster() {
        def zone = env.inventoryByName("zone") as ZoneInventory
        def cluster = env.inventoryByName("cluster") as ClusterInventory

        def net = createProvisionNetwork {
            name = "pxe-delete-blocked"
            zoneUuid = zone.uuid
            type = "STANDALONE_PXE"
        } as PhysicalServerProvisionNetworkInventory

        attachProvisionNetworkToCluster {
            networkUuid = net.uuid
            clusterUuid = cluster.uuid
        }

        // Delete should fail — cluster still attached
        expect(Throwable.class) {
            deleteProvisionNetwork { uuid = net.uuid }
        }

        // Detach first, then delete succeeds
        detachProvisionNetworkFromCluster {
            networkUuid = net.uuid
            clusterUuid = cluster.uuid
        }
        deleteProvisionNetwork { uuid = net.uuid }
    }

    void testQueryProvisionNetwork() {
        def zone = env.inventoryByName("zone") as ZoneInventory

        def net = createProvisionNetwork {
            name = "pxe-query-test"
            zoneUuid = zone.uuid
            type = "STANDALONE_PXE"
        } as PhysicalServerProvisionNetworkInventory

        def nets = queryProvisionNetwork {
            conditions = ["name=pxe-query-test"]
        }

        assert nets.size() == 1
        assert nets[0].uuid == net.uuid
        assert nets[0].type == "STANDALONE_PXE"

        deleteProvisionNetwork { uuid = net.uuid }
    }

    void testUpdateServerPoolNetworkTopology() {
        def zone = env.inventoryByName("zone") as ZoneInventory

        def pool = createServerPool {
            name = "pool-topo"
            zoneUuid = zone.uuid
            networkTopology = "flat"
        } as ServerPoolInventory

        assert pool.networkTopology == "flat"

        def updated = updateServerPool {
            uuid = pool.uuid
            networkTopology = "spine-leaf"
        } as ServerPoolInventory

        assert updated.networkTopology == "spine-leaf"

        deleteServerPool { uuid = pool.uuid }
    }

    void testUpdatePhysicalServerMoreFields() {
        def zone = env.inventoryByName("zone") as ZoneInventory

        def pool = createServerPool {
            name = "pool-update-fields"
            zoneUuid = zone.uuid
        } as ServerPoolInventory

        def server = createPhysicalServer {
            name = "server-fields"
            zoneUuid = zone.uuid
            poolUuid = pool.uuid
            managementIp = "192.168.30.1"
            architecture = "x86_64"
            oobManagementType = "IPMI"
            oobAddress = "192.168.30.100"
        } as PhysicalServerInventory

        def updated = updatePhysicalServer {
            uuid = server.uuid
            architecture = "aarch64"
            serialNumber = "NEW-SN-001"
            manufacturer = "Huawei"
            model = "TaiShan 2280"
            // NB-12: oobManagementType locked to "ipmi"; REDFISH is rejected
            // by SDK pre-validation. Keep IPMI on the update.
            oobAddress = "192.168.30.200"
        } as PhysicalServerInventory

        assert updated.architecture == "aarch64"
        assert updated.serialNumber == "NEW-SN-001"
        assert updated.manufacturer == "Huawei"
        assert updated.model == "TaiShan 2280"
        assert updated.oobManagementType == "IPMI"
        assert updated.oobAddress == "192.168.30.200"

        deletePhysicalServer { uuid = server.uuid }
        deleteServerPool { uuid = pool.uuid }
    }

    void testUpdatePhysicalServerPoolUuid() {
        def zone = env.inventoryByName("zone") as ZoneInventory

        def pool1 = createServerPool {
            name = "pool-orig"
            zoneUuid = zone.uuid
        } as ServerPoolInventory

        def pool2 = createServerPool {
            name = "pool-target"
            zoneUuid = zone.uuid
        } as ServerPoolInventory

        def server = createPhysicalServer {
            name = "server-move"
            zoneUuid = zone.uuid
            poolUuid = pool1.uuid
            managementIp = "192.168.80.1"
        } as PhysicalServerInventory

        assert server.poolUuid == pool1.uuid

        // Move server to different pool
        def updated = updatePhysicalServer {
            uuid = server.uuid
            poolUuid = pool2.uuid
        } as PhysicalServerInventory

        assert updated.poolUuid == pool2.uuid

        deletePhysicalServer { uuid = server.uuid }
        deleteServerPool { uuid = pool1.uuid }
        deleteServerPool { uuid = pool2.uuid }
    }

    void testChangePhysicalServerStateInvalidEvent() {
        def zone = env.inventoryByName("zone") as ZoneInventory

        def pool = createServerPool {
            name = "pool-invalid-state"
            zoneUuid = zone.uuid
        } as ServerPoolInventory

        def server = createPhysicalServer {
            name = "server-invalid"
            zoneUuid = zone.uuid
            poolUuid = pool.uuid
            managementIp = "192.168.31.1"
        } as PhysicalServerInventory

        // Invalid state event should fail
        expect(Throwable.class) {
            changePhysicalServerState {
                uuid = server.uuid
                stateEvent = "invalid_event"
            }
        }

        deletePhysicalServer { uuid = server.uuid }
        deleteServerPool { uuid = pool.uuid }
    }

    void testUpdateProvisionNetwork() {
        def zone = env.inventoryByName("zone") as ZoneInventory

        def net = createProvisionNetwork {
            name = "pxe-update"
            zoneUuid = zone.uuid
            type = "STANDALONE_PXE"
            description = "original desc"
            dhcpInterface = "eth0"
            dhcpRangeStartIp = "10.0.0.100"
            dhcpRangeEndIp = "10.0.0.200"
            dhcpRangeNetmask = "255.255.255.0"
            dhcpRangeGateway = "10.0.0.1"
        } as PhysicalServerProvisionNetworkInventory

        assert net.name == "pxe-update"
        assert net.description == "original desc"

        def updated = updateProvisionNetwork {
            uuid = net.uuid
            name = "pxe-updated"
            description = "updated desc"
            dhcpInterface = "bond0"
            dhcpRangeStartIp = "10.0.1.100"
            dhcpRangeEndIp = "10.0.1.200"
        } as PhysicalServerProvisionNetworkInventory

        assert updated.name == "pxe-updated"
        assert updated.description == "updated desc"
        assert updated.dhcpInterface == "bond0"
        assert updated.dhcpRangeStartIp == "10.0.1.100"
        assert updated.dhcpRangeEndIp == "10.0.1.200"
        // Unchanged fields preserved
        assert updated.dhcpRangeNetmask == "255.255.255.0"
        assert updated.dhcpRangeGateway == "10.0.0.1"
        assert updated.type == "STANDALONE_PXE"

        deleteProvisionNetwork { uuid = net.uuid }
    }
}
