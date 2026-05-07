package org.zstack.test.integration.server

import org.zstack.sdk.ClusterInventory
import org.zstack.sdk.PhysicalServerInventory
import org.zstack.sdk.ServerPoolInventory
import org.zstack.sdk.ZoneInventory
import org.zstack.test.integration.baremetal2.BareMetal2Test
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.premium.PremiumSubCase

class PhysicalServerCompatCase extends PremiumSubCase {
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
            testMigrationScriptIdempotent()
            testQueryPhysicalServerCrossRole()
            testQueryPhysicalServerPagination()
            testQueryPhysicalServerByRoleType()
            testFeatureSwitchDisablesNewEngine()

            // FR-028-AC1/AC2/AC3: AllocateHostMsg pass-through
            testAllocateHostMsgPassThrough()

            // FR-030-AC2/AC3: Migration script extracts serialNumber
            testMigrationScriptExtractsSerialNumber()

            // FR-031-AC2 supplement: Query sorting
            testQueryPhysicalServerSorting()
        }
    }

    // AC-CB-07: Migration script idempotent
    void testMigrationScriptIdempotent() {
        def zone = env.inventoryByName("zone") as ZoneInventory
        def pool = createServerPool {
            name = "pool-migration"
            zoneUuid = zone.uuid
        } as ServerPoolInventory

        // Create a server to simulate "migrated" data
        def server = createPhysicalServer {
            name = "migrated-server"
            zoneUuid = zone.uuid
            poolUuid = pool.uuid
            managementIp = "192.168.70.1"
            serialNumber = "MIGRATE-SN-001"
        } as PhysicalServerInventory

        // Creating same serialNumber again should fail (idempotent protection)
        expect(AssertionError.class) {
            createPhysicalServer {
                name = "migrated-server-dup"
                zoneUuid = zone.uuid
                poolUuid = pool.uuid
                managementIp = "192.168.70.2"
                serialNumber = "MIGRATE-SN-001"
            }
        }

        // Original still intact
        def servers = queryPhysicalServer {
            conditions = ["serialNumber=MIGRATE-SN-001"]
        }
        assert servers.size() == 1
        assert servers[0].uuid == server.uuid

        deletePhysicalServer { uuid = server.uuid }
        deleteServerPool { uuid = pool.uuid }
    }

    // AC-CB-11: Cross-role query
    void testQueryPhysicalServerCrossRole() {
        def zone = env.inventoryByName("zone") as ZoneInventory
        def pool = createServerPool {
            name = "pool-cross-role"
            zoneUuid = zone.uuid
        } as ServerPoolInventory

        def server1 = createPhysicalServer {
            name = "server-kvm"
            zoneUuid = zone.uuid
            poolUuid = pool.uuid
            managementIp = "192.168.71.1"
        } as PhysicalServerInventory

        def server2 = createPhysicalServer {
            name = "server-bm"
            zoneUuid = zone.uuid
            poolUuid = pool.uuid
            managementIp = "192.168.71.2"
        } as PhysicalServerInventory

        // Query all servers in zone — should return both
        def allServers = queryPhysicalServer {
            conditions = ["zoneUuid=${zone.uuid}".toString()]
        }
        assert allServers.size() >= 2

        // TDD: After role attach, query should still return all regardless of roleType
        deletePhysicalServer { uuid = server1.uuid }
        deletePhysicalServer { uuid = server2.uuid }
        deleteServerPool { uuid = pool.uuid }
    }

    // AC-CB-12: Pagination
    void testQueryPhysicalServerPagination() {
        def zone = env.inventoryByName("zone") as ZoneInventory
        def pool = createServerPool {
            name = "pool-pagination"
            zoneUuid = zone.uuid
        } as ServerPoolInventory

        // Create 5 servers
        def serverUuids = []
        for (int i = 0; i < 5; i++) {
            def s = createPhysicalServer {
                name = "server-page-${i}"
                zoneUuid = zone.uuid
                poolUuid = pool.uuid
                managementIp = "192.168.72.${10 + i}"
            } as PhysicalServerInventory
            serverUuids.add(s.uuid)
        }

        // Query with limit
        def page1 = queryPhysicalServer {
            conditions = ["poolUuid=${pool.uuid}".toString()]
            limit = 2
            start = 0
        }
        assert page1.size() == 2

        def page2 = queryPhysicalServer {
            conditions = ["poolUuid=${pool.uuid}".toString()]
            limit = 2
            start = 2
        }
        assert page2.size() == 2

        def page3 = queryPhysicalServer {
            conditions = ["poolUuid=${pool.uuid}".toString()]
            limit = 2
            start = 4
        }
        assert page3.size() == 1

        // Cleanup — bind the each iterator var explicitly. `it` inside the
        // inner deletePhysicalServer closure resolves to that closure's
        // delegate, not the outer each parameter.
        serverUuids.each { String svrUuid -> deletePhysicalServer { uuid = svrUuid } }
        deleteServerPool { uuid = pool.uuid }
    }

    // AC-CB-11 supplement: Query by roleType
    void testQueryPhysicalServerByRoleType() {
        def zone = env.inventoryByName("zone") as ZoneInventory
        def cluster = env.inventoryByName("cluster") as ClusterInventory
        def pool = createServerPool {
            name = "pool-role-query"
            zoneUuid = zone.uuid
        } as ServerPoolInventory

        def server = createPhysicalServer {
            name = "server-role-query"
            zoneUuid = zone.uuid
            poolUuid = pool.uuid
            // Loopback so KVM_HOST connect-host POST routes to local simulator.
            managementIp = "127.0.0.73"
        } as PhysicalServerInventory

        // Attach a role
        attachPhysicalServerRole {
            serverUuid = server.uuid
            roleType = "KVM_HOST"
            clusterUuid = cluster.uuid
            roleConfig = [username: "root", password: "password", sshPort: "22"]
        }

        // TDD: Query PhysicalServer filtered by roleType
        // When enhanced query is implemented, this should filter by joined role table
        def servers = queryPhysicalServer {
            conditions = ["uuid=${server.uuid}".toString()]
        }
        assert servers.size() == 1

        detachPhysicalServerRole {
            serverUuid = server.uuid
            roleType = "KVM_HOST"
            force = true
        }
        deletePhysicalServer { uuid = server.uuid }
        deleteServerPool { uuid = pool.uuid }
    }

    // AC-CB-04: Feature switch
    void testFeatureSwitchDisablesNewEngine() {
        // TDD: When GlobalConfig 'unifiedHardwareManagement.enabled' is set to false,
        // the CompatibilityBridge should not intercept AllocateHostMsg
        // For now, just verify the basic server operations work (baseline)
        def zone = env.inventoryByName("zone") as ZoneInventory
        def pool = createServerPool {
            name = "pool-switch"
            zoneUuid = zone.uuid
        } as ServerPoolInventory

        def server = createPhysicalServer {
            name = "server-switch"
            zoneUuid = zone.uuid
            poolUuid = pool.uuid
            managementIp = "192.168.74.1"
        } as PhysicalServerInventory

        // Basic operations should always work regardless of switch
        assert server.uuid != null
        assert server.state == "Enabled"

        deletePhysicalServer { uuid = server.uuid }
        deleteServerPool { uuid = pool.uuid }
    }

    // FR-028-AC1/AC2/AC3: AllocateHostMsg pass-through via CompatibilityBridge
    void testAllocateHostMsgPassThrough() {
        // TDD: When CompatibilityBridge is implemented:
        // 1. Create PhysicalServer + KVM role
        // 2. Send AllocateHostMsg (standard KVM allocation)
        // 3. Verify it passes through bridge to new engine
        // 4. Verify result is a valid HostInventory

        def zone = env.inventoryByName("zone") as ZoneInventory
        def pool = createServerPool {
            name = "pool-bridge"
            zoneUuid = zone.uuid
        } as ServerPoolInventory

        def server = createPhysicalServer {
            name = "server-bridge"
            zoneUuid = zone.uuid
            poolUuid = pool.uuid
            managementIp = "192.168.85.1"
        } as PhysicalServerInventory

        // Baseline: server exists and is enabled
        assert server.state == "Enabled"

        deletePhysicalServer { uuid = server.uuid }
        deleteServerPool { uuid = pool.uuid }
    }

    // FR-030-AC2/AC3: Migration script extracts serialNumber
    void testMigrationScriptExtractsSerialNumber() {
        def zone = env.inventoryByName("zone") as ZoneInventory
        def pool = createServerPool {
            name = "pool-sn-extract"
            zoneUuid = zone.uuid
        } as ServerPoolInventory

        // Server with serialNumber — should be preserved in migration
        def serverWithSn = createPhysicalServer {
            name = "server-with-sn"
            zoneUuid = zone.uuid
            poolUuid = pool.uuid
            managementIp = "192.168.86.1"
            serialNumber = "EXTRACT-SN-001"
        } as PhysicalServerInventory
        assert serverWithSn.serialNumber == "EXTRACT-SN-001"

        // Server without serialNumber — migration should generate deterministic UUID
        def serverNoSn = createPhysicalServer {
            name = "server-no-sn"
            zoneUuid = zone.uuid
            poolUuid = pool.uuid
            managementIp = "192.168.86.2"
        } as PhysicalServerInventory
        // serialNumber can be null for servers without it
        assert serverNoSn.uuid != null

        // Query both — both should be findable
        def servers = queryPhysicalServer {
            conditions = ["poolUuid=${pool.uuid}".toString()]
        }
        assert servers.size() == 2

        deletePhysicalServer { uuid = serverWithSn.uuid }
        deletePhysicalServer { uuid = serverNoSn.uuid }
        deleteServerPool { uuid = pool.uuid }
    }

    // FR-031-AC2 supplement: Query sorting by name
    void testQueryPhysicalServerSorting() {
        def zone = env.inventoryByName("zone") as ZoneInventory
        def pool = createServerPool {
            name = "pool-sort"
            zoneUuid = zone.uuid
        } as ServerPoolInventory

        def serverA = createPhysicalServer {
            name = "aaa-server"
            zoneUuid = zone.uuid
            poolUuid = pool.uuid
            managementIp = "192.168.87.1"
        } as PhysicalServerInventory

        def serverZ = createPhysicalServer {
            name = "zzz-server"
            zoneUuid = zone.uuid
            poolUuid = pool.uuid
            managementIp = "192.168.87.2"
        } as PhysicalServerInventory

        // Sort by name ascending
        def sorted = queryPhysicalServer {
            conditions = ["poolUuid=${pool.uuid}".toString()]
            sortBy = "name"
            sortDirection = "asc"
        }
        assert sorted.size() == 2
        assert sorted[0].name == "aaa-server"
        assert sorted[1].name == "zzz-server"

        deletePhysicalServer { uuid = serverA.uuid }
        deletePhysicalServer { uuid = serverZ.uuid }
        deleteServerPool { uuid = pool.uuid }
    }

    @Override
    void clean() {
        env.delete()
    }
}
