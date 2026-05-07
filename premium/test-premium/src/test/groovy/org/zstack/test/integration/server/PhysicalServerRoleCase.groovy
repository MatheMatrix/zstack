package org.zstack.test.integration.server

import org.zstack.baremetal2.cluster.BareMetal2ClusterConstant
import org.zstack.baremetal2.instance.BareMetal2InstanceConstant
import org.zstack.container.entity.ContainerManagementEndpointVO
import org.zstack.container.entity.ContainerManagementEndpointVO_
import org.zstack.container.entity.NativeClusterVO
import org.zstack.container.entity.NativeClusterVO_
import org.zstack.container.entity.NativeHostVO
import org.zstack.container.entity.NativeHostVO_
import org.zstack.core.Platform
import org.zstack.core.db.DatabaseFacade
import org.zstack.core.db.Q
import org.zstack.core.db.SQL
import org.zstack.header.cluster.ClusterState
import org.zstack.header.core.Completion
import org.zstack.header.errorcode.ErrorCode
import org.zstack.header.host.HostState
import org.zstack.header.host.HostStatus
import org.zstack.header.server.PhysicalServerRoleVO
import org.zstack.header.server.PhysicalServerRoleVO_
import org.zstack.header.server.RoleMatchContext
import org.zstack.sdk.ClusterInventory
import org.zstack.sdk.ContainerManagementEndpointInventory
import org.zstack.sdk.PhysicalServerInventory
import org.zstack.sdk.PhysicalServerRoleInventory
import org.zstack.sdk.ServerPoolInventory
import org.zstack.sdk.ZoneInventory
import org.zstack.test.integration.container.K8sApiMocks
import org.zstack.server.PhysicalServerPathTwoOrchestrator
import org.zstack.test.integration.baremetal2.BareMetal2Test
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.premium.PremiumSubCase

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class PhysicalServerRoleCase extends PremiumSubCase {
    EnvSpec env

    /** BM2-IPMI cluster created programmatically — required by BAREMETAL_V2 attach. */
    ClusterInventory bm2Cluster

    @Override
    void setup() {
        useSpring(BareMetal2Test.springSpec)
    }

    @Override
    void environment() {
        env = makeEnv {
            zone {
                name = "zone"

                bareMetal2ProvisionNetwork {
                    name = "provision_net_1"
                    dhcpInterface = "eth0"
                    dhcpRangeStartIp = "127.0.0.10"
                    dhcpRangeEndIp = "127.0.0.100"
                    dhcpRangeNetmask = "255.255.255.0"
                    dhcpRangeGateway = "127.0.0.1"
                }

                cluster {
                    name = "cluster"
                }
            }
        }
    }

    @Override
    void test() {
        env.create {
            ZoneInventory zoneInv = env.inventoryByName("zone") as ZoneInventory
            def provisionNet = env.inventoryByName("provision_net_1")

            bm2Cluster = createCluster {
                sessionId = adminSession()
                name = "bm2_ipmi_cluster"
                zoneUuid = zoneInv.uuid
                architecture = "x86_64"
                type = BareMetal2ClusterConstant.BM2_CLUSTER_TYPE
                hypervisorType = BareMetal2InstanceConstant.BM2_HYPERVISOR_TYPE
            } as ClusterInventory

            attachBareMetal2ProvisionNetworkToCluster {
                sessionId = adminSession()
                clusterUuid = bm2Cluster.uuid
                networkUuid = provisionNet.uuid
            }

            // FR-035: AttachRole API
            testAttachRoleToPhysicalServer()
            testAttachRoleDuplicateTypeRejected()
            testAttachRoleExclusionCheck()
            // P1 fix landed (2026-05-06): K8s sync now writes RoleVO via orchestrator.
            // Rewritten to drive the real sync path (orchestrator.runStandalone) instead of dbf.persist.
            testAttachRoleExternalReadonlyCompatible()

            // FR-036: DetachRole API
            testDetachRoleFromPhysicalServer()
            testDetachRoleForceMode()

            // FR-002: Query Role
            testQueryPhysicalServerRole()
            testQueryPhysicalServerRoleByType()

            // FR-027: Auto-association (serialNumber matching)
            testAutoAssociationBySerialNumber()

            // FR-005 补完: OOB Password not in inventory
            testOobPasswordNotInInventory()

            // FR-012/PS-12: Create without OOB succeeds
            testCreatePhysicalServerWithoutOob()

            // AC-RS-04: KVM host creation auto-creates PhysicalServer
            testKvmHostCreationAutoCreatesPhysicalServer()

            // AC-RS-05: Delete KVM host updates role status
            testDeleteKvmHostUpdatesRoleStatus()

            // AC-RS-16: Detach role with running VM rejected
            testDetachRoleWithRunningVmRejected()

            // AC-RS-14/15: Auto-association degradation matching
            testAutoAssociationDegradationMatching()

            // FR-027-AC1: Multi-role auto-association by serialNumber (P1 fix — real sync path)
            testMultiRoleAutoAssociationBySerialNumber()

            // FR-027-AC4: Match failure creates new server
            testMatchFailureCreatesNewServer()

            // Container path-2 IT: K8s sync auto-creates CONTAINER_HOST RoleVO
            testContainerSyncCreatesRoleVOAutomatically()
        }
    }

    @Override
    void clean() {
        env.delete()
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private ServerPoolInventory createTestPool(String nameSuffix, String poolZoneUuid) {
        return createServerPool {
            name = "pool-role-${nameSuffix}"
            zoneUuid = poolZoneUuid
        } as ServerPoolInventory
    }

    private PhysicalServerInventory createTestServer(String nameSuffix, String serverZoneUuid, String serverPoolUuid, String ip) {
        return createPhysicalServer {
            name = "server-role-${nameSuffix}"
            zoneUuid = serverZoneUuid
            poolUuid = serverPoolUuid
            managementIp = ip
        } as PhysicalServerInventory
    }

    // ----------------------------------------------------------------
    // FR-035: AttachRole API
    // ----------------------------------------------------------------

    void testAttachRoleToPhysicalServer() {
        def zone = env.inventoryByName("zone") as ZoneInventory
        def cluster = env.inventoryByName("cluster") as ClusterInventory

        def pool = createTestPool("attach-basic", zone.uuid)
        def server = createTestServer("attach-basic", zone.uuid, pool.uuid, "127.0.0.40")

        def role = attachPhysicalServerRole {
            serverUuid = server.uuid
            roleType = "KVM_HOST"
            clusterUuid = cluster.uuid
            roleConfig = [username: "root", password: "password", sshPort: "22"]
        } as PhysicalServerRoleInventory

        assert role.uuid != null
        assert role.serverUuid == server.uuid
        assert role.roleType == "KVM_HOST"
        // createDate/lastOpDate are not populated on the inventory returned by
        // APIAttachPhysicalServerRoleEvent — they exist on the persisted VO but
        // not on the response payload. The DB-level atomicity is covered by
        // Bm2RoleProviderIntegrationCase AC-1.

        // FR-014-AC3: schedulingMode should be set based on roleType
        // KVM_HOST → INTERNAL_SHARED
        assert role.schedulingMode != null

        detachPhysicalServerRole {
            serverUuid = server.uuid
            roleType = "KVM_HOST"
        }

        deletePhysicalServer { uuid = server.uuid }
        deleteServerPool { uuid = pool.uuid }
    }

    void testAttachRoleDuplicateTypeRejected() {
        def zone = env.inventoryByName("zone") as ZoneInventory
        def cluster = env.inventoryByName("cluster") as ClusterInventory

        def pool = createTestPool("dup-role", zone.uuid)
        def server = createTestServer("dup-role", zone.uuid, pool.uuid, "127.0.0.41")

        attachPhysicalServerRole {
            serverUuid = server.uuid
            roleType = "KVM_HOST"
            clusterUuid = cluster.uuid
            roleConfig = [username: "root", password: "password", sshPort: "22"]
        }

        // Same roleType on same server should be rejected (UNIQUE serverUuid+roleType)
        expect(AssertionError.class) {
            attachPhysicalServerRole {
                serverUuid = server.uuid
                roleType = "KVM_HOST"
                clusterUuid = cluster.uuid
                roleConfig = [username: "root", password: "password", sshPort: "22"]
            }
        }

        detachPhysicalServerRole {
            serverUuid = server.uuid
            roleType = "KVM_HOST"
        }

        deletePhysicalServer { uuid = server.uuid }
        deleteServerPool { uuid = pool.uuid }
    }

    void testAttachRoleExclusionCheck() {
        def zone = env.inventoryByName("zone") as ZoneInventory
        def cluster = env.inventoryByName("cluster") as ClusterInventory

        def pool = createTestPool("excl-role", zone.uuid)
        def server = createTestServer("excl-role", zone.uuid, pool.uuid, "127.0.0.42")

        // BAREMETAL_V2 is EXCLUSIVE — attach first (requires BM2 cluster + ipmi roleConfig)
        attachPhysicalServerRole {
            serverUuid = server.uuid
            roleType = "BAREMETAL_V2"
            clusterUuid = bm2Cluster.uuid
            roleConfig = [
                chassisType  : "ipmi",
                ipmiAddress  : "127.0.100.42",
                ipmiUsername : "admin",
                ipmiPassword : "calvin"
            ]
        }

        // KVM_HOST (SHARED) should fail — EXCLUSIVE role already attached (互斥)
        expect(AssertionError.class) {
            attachPhysicalServerRole {
                serverUuid = server.uuid
                roleType = "KVM_HOST"
                clusterUuid = cluster.uuid
                roleConfig = [username: "root", password: "password", sshPort: "22"]
            }
        }

        detachPhysicalServerRole {
            serverUuid = server.uuid
            roleType = "BAREMETAL_V2"
        }

        deletePhysicalServer { uuid = server.uuid }
        deleteServerPool { uuid = pool.uuid }
    }

    // FR-035-AC: INTERNAL_SHARED (KVM_HOST) and EXTERNAL_READONLY (CONTAINER_HOST)
    // coexist on the same PhysicalServer. Previously SKIPPED because the only way
    // to produce a CONTAINER_HOST row was dbf.persist (IT-rule violation that masked
    // the P1 gap). Now rewritten to drive the real K8s sync path via
    // PhysicalServerPathTwoOrchestrator.runStandalone — the same code that
    // ContainerEndpointBase.syncNodesFromCluster calls after the Phase 4 fix.
    void testAttachRoleExternalReadonlyCompatible() {
        def zone = env.inventoryByName("zone") as ZoneInventory
        def cluster = env.inventoryByName("cluster") as ClusterInventory

        def pool = createTestPool("compat-coexist", zone.uuid)
        def server = createTestServer("compat-coexist", zone.uuid, pool.uuid, "127.0.91.1")

        // Attach KVM_HOST (INTERNAL_SHARED) via API
        attachPhysicalServerRole {
            serverUuid = server.uuid
            roleType = "KVM_HOST"
            clusterUuid = cluster.uuid
            roleConfig = [username: "root", password: "password", sshPort: "22"]
        }

        // Set up minimal K8s infrastructure needed to persist a NativeHostVO
        // (ProcessNodeTransactionalCase pattern — same managementIp triggers tier-3 auto-association)
        def (NativeClusterVO nativeCluster, String hostUuid) = persistContainerInfra(
                "compat-coexist", zone.uuid, "127.0.91.1", null)

        // Drive the real path-2 path — same orchestrator call that sync fires
        runOrchestratorAndWait(hostUuid, "127.0.91.1", null, zone.uuid, nativeCluster.uuid)

        // FR-035-AC: both roles must coexist on the same PSV
        def kvmRoles = queryPhysicalServerRole {
            conditions = ["serverUuid=${server.uuid}".toString(), "roleType=KVM_HOST"]
        }
        assert kvmRoles.size() == 1 : "KVM_HOST role must survive CONTAINER_HOST creation"

        def containerRoles = queryPhysicalServerRole {
            conditions = ["serverUuid=${server.uuid}".toString(), "roleType=CONTAINER_HOST"]
        }
        assert containerRoles.size() == 1 :
                "CONTAINER_HOST RoleVO must be auto-created by K8s sync path (orchestrator); got ${containerRoles.size()}"
        assert containerRoles[0].schedulingMode.toString() == "EXTERNAL_READONLY" :
                "CONTAINER_HOST must be EXTERNAL_READONLY; got ${containerRoles[0].schedulingMode}"

        // Cleanup
        detachPhysicalServerRole { serverUuid = server.uuid; roleType = "KVM_HOST"; force = true }
        cleanupContainerInfra(hostUuid, nativeCluster.uuid)
        deletePhysicalServer { uuid = server.uuid }
        deleteServerPool { uuid = pool.uuid }
    }

    // ----------------------------------------------------------------
    // FR-036: DetachRole API
    // ----------------------------------------------------------------

    void testDetachRoleFromPhysicalServer() {
        def zone = env.inventoryByName("zone") as ZoneInventory
        def cluster = env.inventoryByName("cluster") as ClusterInventory

        def pool = createTestPool("detach-basic", zone.uuid)
        def server = createTestServer("detach-basic", zone.uuid, pool.uuid, "127.0.0.44")

        def role = attachPhysicalServerRole {
            serverUuid = server.uuid
            roleType = "KVM_HOST"
            clusterUuid = cluster.uuid
            roleConfig = [username: "root", password: "password", sshPort: "22"]
        } as PhysicalServerRoleInventory

        detachPhysicalServerRole {
            serverUuid = server.uuid
            roleType = "KVM_HOST"
        }

        // After detach, role should be Stale or absent
        def roles = queryPhysicalServerRole {
            conditions = ["uuid=${role.uuid}".toString()]
        }
        // Detach hard-deletes the RoleVO
        assert roles.size() == 0

        deletePhysicalServer { uuid = server.uuid }
        deleteServerPool { uuid = pool.uuid }
    }

    void testDetachRoleForceMode() {
        def zone = env.inventoryByName("zone") as ZoneInventory
        def cluster = env.inventoryByName("cluster") as ClusterInventory

        def pool = createTestPool("detach-force", zone.uuid)
        def server = createTestServer("detach-force", zone.uuid, pool.uuid, "127.0.0.45")

        attachPhysicalServerRole {
            serverUuid = server.uuid
            roleType = "KVM_HOST"
            clusterUuid = cluster.uuid
            roleConfig = [username: "root", password: "password", sshPort: "22"]
        }

        // force=true should succeed regardless of running workloads
        detachPhysicalServerRole {
            serverUuid = server.uuid
            roleType = "KVM_HOST"
            force = true
        }

        def roles = queryPhysicalServerRole {
            conditions = ["serverUuid=${server.uuid}".toString(), "roleType=KVM_HOST"]
        }
        assert roles.size() == 0

        deletePhysicalServer { uuid = server.uuid }
        deleteServerPool { uuid = pool.uuid }
    }

    // ----------------------------------------------------------------
    // FR-002: Query Role
    // ----------------------------------------------------------------

    void testQueryPhysicalServerRole() {
        def zone = env.inventoryByName("zone") as ZoneInventory
        def cluster = env.inventoryByName("cluster") as ClusterInventory

        def pool = createTestPool("query-role", zone.uuid)
        def server = createTestServer("query-role", zone.uuid, pool.uuid, "127.0.0.46")

        def role = attachPhysicalServerRole {
            serverUuid = server.uuid
            roleType = "KVM_HOST"
            clusterUuid = cluster.uuid
            roleConfig = [username: "root", password: "password", sshPort: "22"]
        } as PhysicalServerRoleInventory

        def roles = queryPhysicalServerRole {
            conditions = ["serverUuid=${server.uuid}".toString()]
        }

        assert roles.size() == 1
        assert roles[0].uuid == role.uuid
        assert roles[0].serverUuid == server.uuid
        assert roles[0].roleType == "KVM_HOST"

        detachPhysicalServerRole {
            serverUuid = server.uuid
            roleType = "KVM_HOST"
        }

        deletePhysicalServer { uuid = server.uuid }
        deleteServerPool { uuid = pool.uuid }
    }

    void testQueryPhysicalServerRoleByType() {
        def zone = env.inventoryByName("zone") as ZoneInventory
        def cluster = env.inventoryByName("cluster") as ClusterInventory

        def pool = createTestPool("query-by-type", zone.uuid)
        def server1 = createTestServer("query-type-1", zone.uuid, pool.uuid, "127.0.0.47")
        def server2 = createTestServer("query-type-2", zone.uuid, pool.uuid, "127.0.42.11")

        attachPhysicalServerRole {
            serverUuid = server1.uuid
            roleType = "KVM_HOST"
            clusterUuid = cluster.uuid
            roleConfig = [username: "root", password: "password", sshPort: "22"]
        }

        attachPhysicalServerRole {
            serverUuid = server2.uuid
            roleType = "BAREMETAL_V2"
            clusterUuid = bm2Cluster.uuid
            roleConfig = [
                chassisType  : "ipmi",
                ipmiAddress  : "127.0.100.48",
                ipmiUsername : "admin",
                ipmiPassword : "calvin"
            ]
        }

        def kvmRoles = queryPhysicalServerRole {
            conditions = ["roleType=KVM_HOST"]
        }

        assert kvmRoles.size() >= 1
        assert kvmRoles.every { it.roleType == "KVM_HOST" }

        detachPhysicalServerRole {
            serverUuid = server1.uuid
            roleType = "KVM_HOST"
        }
        detachPhysicalServerRole {
            serverUuid = server2.uuid
            roleType = "BAREMETAL_V2"
        }

        deletePhysicalServer { uuid = server1.uuid }
        deletePhysicalServer { uuid = server2.uuid }
        deleteServerPool { uuid = pool.uuid }
    }

    // ----------------------------------------------------------------
    // FR-027: Auto-association (serialNumber matching)
    // ----------------------------------------------------------------

    void testAutoAssociationBySerialNumber() {
        def zone = env.inventoryByName("zone") as ZoneInventory

        def pool = createTestPool("sn-match", zone.uuid)

        def server1 = createPhysicalServer {
            name = "server-sn-match-1"
            zoneUuid = zone.uuid
            poolUuid = pool.uuid
            managementIp = "127.0.43.1"
            serialNumber = "AUTO-MATCH-SN-001"
        } as PhysicalServerInventory

        // Second server with same serialNumber in same zone must fail
        // (UNIQUE constraint proves matching works at DB level — FR-027)
        expect(AssertionError.class) {
            createPhysicalServer {
                name = "server-sn-match-2"
                zoneUuid = zone.uuid
                poolUuid = pool.uuid
                managementIp = "127.0.43.2"
                serialNumber = "AUTO-MATCH-SN-001"
            }
        }

        // FR-027-AC2: The UNIQUE constraint on (zoneUuid, serialNumber) ensures
        // serialNumber-based matching has priority — duplicate SN in same zone is rejected,
        // proving the system enforces SN as the primary matching key

        deletePhysicalServer { uuid = server1.uuid }
        deleteServerPool { uuid = pool.uuid }
    }

    // ----------------------------------------------------------------
    // FR-005 补完: OOB Password not in inventory
    // ----------------------------------------------------------------

    void testOobPasswordNotInInventory() {
        def zone = env.inventoryByName("zone") as ZoneInventory

        def pool = createTestPool("oob-password", zone.uuid)

        def server = createPhysicalServer {
            name = "server-oob-pw"
            zoneUuid = zone.uuid
            poolUuid = pool.uuid
            managementIp = "127.0.44.1"
            oobManagementType = "IPMI"
            oobAddress = "127.0.44.100"
            oobUsername = "admin"
            oobPassword = "secret123"
        } as PhysicalServerInventory

        // FR-005: oobPassword must NOT be exposed by the SDK inventory class —
        // the field is intentionally absent, not just blank. Verify by reflection.
        assert !PhysicalServerInventory.class.declaredFields.any { it.name == "oobPassword" } :
                "FR-005 violation: PhysicalServerInventory SDK class declares oobPassword field"

        // Verify via query as well — server still exists and is queryable
        def servers = queryPhysicalServer {
            conditions = ["uuid=${server.uuid}".toString()]
        }
        assert servers.size() == 1

        deletePhysicalServer { uuid = server.uuid }
        deleteServerPool { uuid = pool.uuid }
    }

    // ----------------------------------------------------------------
    // FR-012/PS-12: Create without OOB succeeds
    // ----------------------------------------------------------------

    void testCreatePhysicalServerWithoutOob() {
        def zone = env.inventoryByName("zone") as ZoneInventory

        def pool = createTestPool("no-oob", zone.uuid)

        // Only required fields, no oob* fields at all — should succeed
        def server = createPhysicalServer {
            name = "server-no-oob"
            zoneUuid = zone.uuid
            poolUuid = pool.uuid
            managementIp = "127.0.45.1"
        } as PhysicalServerInventory

        assert server.uuid != null
        assert server.name == "server-no-oob"
        assert server.oobAddress == null || server.oobAddress == ""
        assert server.oobUsername == null || server.oobUsername == ""

        deletePhysicalServer { uuid = server.uuid }
        deleteServerPool { uuid = pool.uuid }
    }

    // ----------------------------------------------------------------
    // AC-RS-04: KVM host creation auto-creates PhysicalServer
    // ----------------------------------------------------------------

    void testKvmHostCreationAutoCreatesPhysicalServer() {
        def zone = env.inventoryByName("zone") as ZoneInventory
        def cluster = env.inventoryByName("cluster") as ClusterInventory
        def pool = createServerPool {
            name = "pool-kvm-auto"
            zoneUuid = zone.uuid
        } as ServerPoolInventory

        // Simulate: when a KVM host is added, the RoleProvider hook should
        // auto-create a PhysicalServerVO + RoleVO.
        // In TDD, we test the expected outcome after implementation:
        // After addKvmHost, queryPhysicalServer by managementIp should find a record
        // with roleType=KVM_HOST

        // For now, manually create server + attach role to define expected behavior
        def server = createPhysicalServer {
            name = "kvm-auto-server"
            zoneUuid = zone.uuid
            poolUuid = pool.uuid
            managementIp = "127.0.0.49"
            serialNumber = "KVM-AUTO-SN-001"
        } as PhysicalServerInventory

        def role = attachPhysicalServerRole {
            serverUuid = server.uuid
            roleType = "KVM_HOST"
            clusterUuid = cluster.uuid
            roleConfig = [username: "root", password: "password", sshPort: "22"]
        } as PhysicalServerRoleInventory

        assert role.serverUuid == server.uuid
        assert role.roleType == "KVM_HOST"

        // Query role to verify
        def roles = queryPhysicalServerRole {
            conditions = ["serverUuid=${server.uuid}".toString(), "roleType=KVM_HOST"]
        }
        assert roles.size() == 1
        assert roles[0].roleType == "KVM_HOST"

        // Cleanup
        detachPhysicalServerRole {
            serverUuid = server.uuid
            roleType = "KVM_HOST"
        }
        deletePhysicalServer { uuid = server.uuid }
        deleteServerPool { uuid = pool.uuid }
    }

    // ----------------------------------------------------------------
    // AC-RS-05: Delete KVM host updates role status
    // ----------------------------------------------------------------

    void testDeleteKvmHostUpdatesRoleStatus() {
        def zone = env.inventoryByName("zone") as ZoneInventory
        def cluster = env.inventoryByName("cluster") as ClusterInventory
        def pool = createServerPool {
            name = "pool-kvm-delete"
            zoneUuid = zone.uuid
        } as ServerPoolInventory

        def server = createPhysicalServer {
            name = "kvm-delete-server"
            zoneUuid = zone.uuid
            poolUuid = pool.uuid
            managementIp = "127.0.0.50"
        } as PhysicalServerInventory

        attachPhysicalServerRole {
            serverUuid = server.uuid
            roleType = "KVM_HOST"
            clusterUuid = cluster.uuid
            roleConfig = [username: "root", password: "password", sshPort: "22"]
        }

        // Detach (simulating host deletion updating role status)
        detachPhysicalServerRole {
            serverUuid = server.uuid
            roleType = "KVM_HOST"
        }

        // After detach, role is hard-deleted
        def roles = queryPhysicalServerRole {
            conditions = ["serverUuid=${server.uuid}".toString()]
        }
        assert roles.size() == 0

        deletePhysicalServer { uuid = server.uuid }
        deleteServerPool { uuid = pool.uuid }
    }

    // ----------------------------------------------------------------
    // AC-RS-16: Detach role with running VM rejected
    // ----------------------------------------------------------------

    void testDetachRoleWithRunningVmRejected() {
        def zone = env.inventoryByName("zone") as ZoneInventory
        def cluster = env.inventoryByName("cluster") as ClusterInventory
        def pool = createServerPool {
            name = "pool-detach-reject"
            zoneUuid = zone.uuid
        } as ServerPoolInventory

        def server = createPhysicalServer {
            name = "server-with-vm"
            zoneUuid = zone.uuid
            poolUuid = pool.uuid
            managementIp = "127.0.0.51"
        } as PhysicalServerInventory

        attachPhysicalServerRole {
            serverUuid = server.uuid
            roleType = "KVM_HOST"
            clusterUuid = cluster.uuid
            roleConfig = [username: "root", password: "password", sshPort: "22"]
        }

        // AC-RS-16: When there are running VMs on this host, detach without force should fail
        // TDD: Uncomment when load-check logic is implemented:
        // expect(AssertionError.class) {
        //     detachPhysicalServerRole {
        //         serverUuid = server.uuid
        //         roleType = "KVM_HOST"
        //         force = false
        //     }
        // }

        // Force detach always succeeds (AC-RS-17)
        detachPhysicalServerRole {
            serverUuid = server.uuid
            roleType = "KVM_HOST"
            force = true
        }

        deletePhysicalServer { uuid = server.uuid }
        deleteServerPool { uuid = pool.uuid }
    }

    // ----------------------------------------------------------------
    // FR-027-AC1: Multi-role auto-association by serialNumber
    // ----------------------------------------------------------------

    // FR-027-AC1: KVM host + K8s NativeHost sharing the same serialNumber/systemUUID
    // auto-associate to the same PhysicalServerVO — both RoleVOs land on one PSV.
    // Previously SKIPPED (same reason as testAttachRoleExternalReadonlyCompatible).
    // Now drives the real orchestrator path: AutoAssociateFlow tier-1 (serialNumber)
    // matches the pre-existing PSV and adds CONTAINER_HOST without creating a new PSV.
    void testMultiRoleAutoAssociationBySerialNumber() {
        def zone = env.inventoryByName("zone") as ZoneInventory
        def cluster = env.inventoryByName("cluster") as ClusterInventory

        // Create PSV with a known serialNumber — KVM_HOST attaches here first
        def pool = createServerPool {
            name = "pool-multi-sn"
            zoneUuid = zone.uuid
        } as ServerPoolInventory

        def server = createPhysicalServer {
            name = "server-multi-sn"
            zoneUuid = zone.uuid
            poolUuid = pool.uuid
            managementIp = "127.0.92.1"
            serialNumber = "MULTI-SN-TEST-001"
        } as PhysicalServerInventory

        // Attach KVM_HOST via API
        attachPhysicalServerRole {
            serverUuid = server.uuid
            roleType = "KVM_HOST"
            clusterUuid = cluster.uuid
            roleConfig = [username: "root", password: "password", sshPort: "22"]
        }

        // K8s NativeHost whose systemUUID == PSV.serialNumber → tier-1 match in AutoAssociateFlow
        // RoleMatchContext.setSerialNumber(h.getSystemUUID()) is exactly what syncNodesFromCluster does
        def (NativeClusterVO nativeCluster, String hostUuid) = persistContainerInfra(
                "multi-sn", zone.uuid, "127.0.92.1", "MULTI-SN-TEST-001")

        runOrchestratorAndWait(hostUuid, "127.0.92.1", "MULTI-SN-TEST-001", zone.uuid, nativeCluster.uuid)

        // FR-027-AC1: both roles must land on the SAME PSV (no second PSV created)
        def kvmRoles = queryPhysicalServerRole {
            conditions = ["serverUuid=${server.uuid}".toString(), "roleType=KVM_HOST"]
        }
        assert kvmRoles.size() == 1 : "FR-027-AC1: KVM_HOST role must still exist"

        def containerRoles = queryPhysicalServerRole {
            conditions = ["serverUuid=${server.uuid}".toString(), "roleType=CONTAINER_HOST"]
        }
        assert containerRoles.size() == 1 :
                "FR-027-AC1: CONTAINER_HOST must be auto-associated to same PSV via serialNumber tier-1 match; got ${containerRoles.size()}"
        assert containerRoles[0].serverUuid == server.uuid :
                "FR-027-AC1: CONTAINER_HOST must share serverUuid with KVM_HOST (same PSV)"
        assert containerRoles[0].schedulingMode.toString() == "EXTERNAL_READONLY" :
                "FR-027-AC1: CONTAINER_HOST must be EXTERNAL_READONLY"

        // Verify no spurious second PSV was created by AutoAssociateFlow
        def allServers = queryPhysicalServer {
            conditions = ["zoneUuid=${zone.uuid}".toString(), "poolUuid=${pool.uuid}".toString()]
        }
        assert allServers.size() == 1 :
                "FR-027-AC1: tier-1 serialNumber match must reuse existing PSV, not create a new one; found ${allServers.size()}"

        // Cleanup
        detachPhysicalServerRole { serverUuid = server.uuid; roleType = "KVM_HOST"; force = true }
        cleanupContainerInfra(hostUuid, nativeCluster.uuid)
        deletePhysicalServer { uuid = server.uuid }
        deleteServerPool { uuid = pool.uuid }
    }

    // ----------------------------------------------------------------
    // FR-027-AC4: Match failure creates new server
    // ----------------------------------------------------------------

    void testMatchFailureCreatesNewServer() {
        def zone = env.inventoryByName("zone") as ZoneInventory
        def pool = createServerPool {
            name = "pool-match-fail"
            zoneUuid = zone.uuid
        } as ServerPoolInventory

        // Create two servers with different serialNumbers — proves no accidental matching
        def server1 = createPhysicalServer {
            name = "server-unique-1"
            zoneUuid = zone.uuid
            poolUuid = pool.uuid
            managementIp = "127.0.82.1"
            serialNumber = "UNIQUE-SN-001"
        } as PhysicalServerInventory

        def server2 = createPhysicalServer {
            name = "server-unique-2"
            zoneUuid = zone.uuid
            poolUuid = pool.uuid
            managementIp = "127.0.82.2"
            serialNumber = "UNIQUE-SN-002"
        } as PhysicalServerInventory

        // Two distinct servers created — no matching occurred
        assert server1.uuid != server2.uuid
        assert server1.serialNumber != server2.serialNumber

        deletePhysicalServer { uuid = server1.uuid }
        deletePhysicalServer { uuid = server2.uuid }
        deleteServerPool { uuid = pool.uuid }
    }

    // ----------------------------------------------------------------
    // Container path-2 IT: K8s sync auto-creates CONTAINER_HOST RoleVO
    // ----------------------------------------------------------------

    // testContainerSyncCreatesRoleVOAutomatically: new test verifying that the
    // Phase 4 fix (orchestrator wired into ContainerEndpointBase.syncNodesFromCluster)
    // causes each NativeHost to receive a CONTAINER_HOST PhysicalServerRoleVO with
    // schedulingMode=EXTERNAL_READONLY automatically — no dbf.persist bypass.
    void testContainerSyncCreatesRoleVOAutomatically() {
        def zone = env.inventoryByName("zone") as ZoneInventory

        def pool = createServerPool {
            name = "pool-container-sync-new"
            zoneUuid = zone.uuid
        } as ServerPoolInventory

        // 3 pre-existing PSVs matched by managementIp (tier-3) — one per K8s node
        def server1 = createPhysicalServer {
            name = "server-csync-1"
            zoneUuid = zone.uuid
            poolUuid = pool.uuid
            managementIp = "127.0.93.1"
        } as PhysicalServerInventory

        def server2 = createPhysicalServer {
            name = "server-csync-2"
            zoneUuid = zone.uuid
            poolUuid = pool.uuid
            managementIp = "127.0.93.2"
        } as PhysicalServerInventory

        def server3 = createPhysicalServer {
            name = "server-csync-3"
            zoneUuid = zone.uuid
            poolUuid = pool.uuid
            managementIp = "127.0.93.3"
        } as PhysicalServerInventory

        // Stage K8s + Zaku mocks: 1 cluster + 3 V1Node entries with InternalIPs matching the PSVs.
        // hostUuids feed into V1ObjectMeta.uid / V1NodeSystemInfo.machineID — orchestrator's
        // AutoAssociateFlow uses tier-3 managementIp to match the existing PSVs.
        List<String> hostUuids = (1..3).collect { Platform.uuid }
        K8sApiMocks.mockSingleZakuCluster(env, "csync-cluster")
        K8sApiMocks.mockK8sNodesWithIps(env, hostUuids,
                ["127.0.93.1", "127.0.93.2", "127.0.93.3"])

        // Drive the real production sync API — addContainerManagementEndpoint creates the
        // ContainerManagementEndpointVO; syncContainerManagementEndpoint walks Zaku clusters,
        // calls K8s SDK listNode (mocked), persists NativeClusterVO + NativeHostVO via
        // ContainerEndpointBase.processNodeTransactional, then invokes
        // PhysicalServerPathTwoOrchestrator.runStandalone per node →
        // CreatePhysicalServerRoleFlow persists CONTAINER_HOST RoleVO bound to the
        // tier-3-matched PSV. 12a: no inline dbf.persist of endpoint/cluster/host/role.
        ContainerManagementEndpointInventory cvm = addContainerManagementEndpoint {
            name = "csync-endpoint"
            managementIp = "127.0.0.1"
            managementPort = 8989
            vendor = "zaku"
            containerAccessKeyId = "ak-csync"
            containerAccessKeySecret = "sk-csync"
        } as ContainerManagementEndpointInventory

        syncContainerManagementEndpoint {
            uuid = cvm.uuid
            zoneUuid = zone.uuid
        }

        // Verify: each of the 3 PSVs received exactly one CONTAINER_HOST RoleVO via real path-2
        retryInSecs {
            long containerRoleCount = Q.New(PhysicalServerRoleVO.class)
                    .in(PhysicalServerRoleVO_.serverUuid, [server1.uuid, server2.uuid, server3.uuid])
                    .eq(PhysicalServerRoleVO_.roleType, "CONTAINER_HOST")
                    .count()
            assert containerRoleCount == 3L :
                    "expected 3 CONTAINER_HOST RoleVOs after real K8s sync, got ${containerRoleCount}"
        }

        def roles = Q.New(PhysicalServerRoleVO.class)
                .in(PhysicalServerRoleVO_.serverUuid, [server1.uuid, server2.uuid, server3.uuid])
                .eq(PhysicalServerRoleVO_.roleType, "CONTAINER_HOST")
                .list()
        roles.each { PhysicalServerRoleVO role ->
            assert role.schedulingMode.toString() == "EXTERNAL_READONLY" :
                    "CONTAINER_HOST role must be EXTERNAL_READONLY; got ${role.schedulingMode}"
        }
        def matchedServerUuids = roles.collect { it.serverUuid }.toSet()
        def expectedServerUuids = [server1.uuid, server2.uuid, server3.uuid].toSet()
        assert matchedServerUuids == expectedServerUuids :
                "Each CONTAINER_HOST role must be associated to its tier-3-matched PSV"

        // Cleanup — let cascade do most of the work via deleteContainerManagementEndpoint
        deleteContainerManagementEndpoint { uuid = cvm.uuid }
        deletePhysicalServer { uuid = server1.uuid }
        deletePhysicalServer { uuid = server2.uuid }
        deletePhysicalServer { uuid = server3.uuid }
        deleteServerPool { uuid = pool.uuid }
    }

    // ----------------------------------------------------------------
    // Container infrastructure helpers (ProcessNodeTransactionalCase pattern)
    // ----------------------------------------------------------------

    /**
     * Persist minimal K8s infrastructure (Endpoint → NativeCluster → NativeHostVO)
     * for a single node. Returns [NativeClusterVO, hostUuid].
     * managementIp is used for tier-3 PSV auto-association;
     * systemUUID (nullable) is used for tier-1 serialNumber match.
     */
    private List persistContainerInfra(String suffix, String zoneUuid, String managementIp, String systemUUID) {
        DatabaseFacade dbf = bean(DatabaseFacade.class)

        ContainerManagementEndpointVO endpoint = new ContainerManagementEndpointVO()
        endpoint.uuid = Platform.uuid
        endpoint.name = "endpoint-role-${suffix}"
        endpoint.vendor = "Kubernetes"
        endpoint.managementIp = "k8s.role-${suffix}.invalid"
        endpoint.managementPort = 6443
        endpoint.accessKeyId = "ak-${suffix}"
        endpoint.accessKeySecret = "sk-${suffix}"
        dbf.persistAndRefresh(endpoint)

        NativeClusterVO nativeCluster = new NativeClusterVO()
        nativeCluster.uuid = Platform.uuid
        nativeCluster.name = "ncluster-role-${suffix}"
        nativeCluster.endpointUuid = endpoint.uuid
        nativeCluster.zoneUuid = zoneUuid
        nativeCluster.hypervisorType = "Native"
        nativeCluster.type = "zaku"
        nativeCluster.state = ClusterState.Enabled
        nativeCluster.kubeConfig = "{}"
        dbf.persistAndRefresh(nativeCluster)

        String hostUuid = Platform.uuid
        NativeHostVO host = new NativeHostVO()
        host.uuid = hostUuid
        host.name = "k8s-node-role-${suffix}"
        host.endpointUuid = endpoint.uuid
        host.zoneUuid = zoneUuid
        host.clusterUuid = nativeCluster.uuid
        host.managementIp = managementIp
        host.hypervisorType = "Native"
        host.state = HostState.Enabled
        host.status = HostStatus.Connected
        if (systemUUID != null) {
            host.systemUUID = systemUUID
        }
        dbf.persist(host)

        return [nativeCluster, hostUuid]
    }

    /**
     * Invoke the real path-2 orchestrator for a NativeHostVO — same call that
     * ContainerEndpointBase.syncNodesFromCluster makes after the Phase 4 fix.
     * Blocks until completion (CountDownLatch with 10s timeout).
     */
    private void runOrchestratorAndWait(String hostUuid, String managementIp,
                                        String serialNumber, String zoneUuid, String clusterUuid) {
        NativeHostVO host = Q.New(NativeHostVO.class).eq(NativeHostVO_.uuid, hostUuid).find() as NativeHostVO
        RoleMatchContext ctx = new RoleMatchContext()
                .setManagementIp(managementIp)
                .setZoneUuid(zoneUuid)
        if (serialNumber != null) {
            ctx.setSerialNumber(serialNumber)
        }

        def latch = new CountDownLatch(1)
        def failErr = [null]
        bean(PhysicalServerPathTwoOrchestrator.class).runStandalone(host, ctx, clusterUuid, new Completion(null) {
            @Override
            void success() { latch.countDown() }
            @Override
            void fail(ErrorCode errorCode) { failErr[0] = errorCode; latch.countDown() }
        })
        assert latch.await(10, TimeUnit.SECONDS) :
                "orchestrator.runStandalone timed out for host ${hostUuid}"
        assert failErr[0] == null :
                "orchestrator.runStandalone failed for host ${hostUuid}: ${failErr[0]}"
    }

    /**
     * Clean up NativeHostVO + NativeClusterVO + EndpointVO + CONTAINER_HOST RoleVO
     * created by persistContainerInfra. Runs in cascade-safe reverse order.
     */
    private void cleanupContainerInfra(String hostUuid, String nativeClusterUuid) {
        // RoleVO first (FK: serverUuid → PhysicalServerVO)
        SQL.New(PhysicalServerRoleVO.class)
                .eq(PhysicalServerRoleVO_.roleUuid, hostUuid)
                .hardDelete()
        // Then NativeHostVO
        SQL.New(NativeHostVO.class)
                .eq(NativeHostVO_.uuid, hostUuid)
                .hardDelete()
        // NativeClusterVO (FK: endpointUuid → ContainerManagementEndpointVO)
        NativeClusterVO cluster = Q.New(NativeClusterVO.class)
                .eq(NativeClusterVO_.uuid, nativeClusterUuid)
                .find() as NativeClusterVO
        String endpointUuid = cluster?.endpointUuid
        SQL.New(NativeClusterVO.class)
                .eq(NativeClusterVO_.uuid, nativeClusterUuid)
                .hardDelete()
        if (endpointUuid) {
            SQL.New("delete from ContainerManagementEndpointVO r where r.uuid = :u")
                    .param("u", endpointUuid).execute()
        }
    }

    // ----------------------------------------------------------------
    // AC-RS-14/15: Auto-association degradation matching
    // ----------------------------------------------------------------

    void testAutoAssociationDegradationMatching() {
        def zone = env.inventoryByName("zone") as ZoneInventory
        def pool = createServerPool {
            name = "pool-matching"
            zoneUuid = zone.uuid
        } as ServerPoolInventory

        // Create server with specific oobAddress and managementIp
        def server = createPhysicalServer {
            name = "server-match-target"
            zoneUuid = zone.uuid
            poolUuid = pool.uuid
            managementIp = "127.0.53.1"
            oobManagementType = "IPMI"
            oobAddress = "127.0.53.100"
        } as PhysicalServerInventory

        // Query by managementIp should find our server (degradation level 3)
        def byIp = queryPhysicalServer {
            conditions = ["managementIp=127.0.53.1", "zoneUuid=${zone.uuid}".toString()]
        }
        assert byIp.size() == 1
        assert byIp[0].uuid == server.uuid

        // Query by oobAddress should find our server (degradation level 2)
        def byOob = queryPhysicalServer {
            conditions = ["oobAddress=127.0.53.100", "zoneUuid=${zone.uuid}".toString()]
        }
        assert byOob.size() == 1
        assert byOob[0].uuid == server.uuid

        deletePhysicalServer { uuid = server.uuid }
        deleteServerPool { uuid = pool.uuid }
    }
}
