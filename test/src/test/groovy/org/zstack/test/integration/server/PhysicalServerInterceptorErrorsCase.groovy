package org.zstack.test.integration.server

import org.zstack.header.errorcode.OperationFailureException
import org.zstack.core.Platform
import org.zstack.core.db.DatabaseFacade
import org.zstack.header.server.PhysicalServerRoleVO
import org.zstack.header.server.SchedulingMode
import org.zstack.sdk.ClusterInventory
import org.zstack.sdk.PhysicalServerInventory
import org.zstack.sdk.PhysicalServerRoleInventory
import org.zstack.sdk.ServerPoolInventory
import org.zstack.sdk.ZoneInventory
import org.zstack.test.integration.kvm.KvmTest
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.SubCase

/**
 * Argument-validation regressions for the v5.5.18 hardware-unified APIs. Each test
 * pre-cooks the conflicting / under-specified state, then verifies the API fail-message
 * names the offending field instead of bubbling a generic internal/operation error:
 *
 * <ul>
 *   <li>CreatePhysicalServer with duplicate serialNumber in same zone</li>
 *   <li>AttachPhysicalServerRole binding the same roleType twice</li>
 *   <li>CreateProvisionNetwork type=GATEWAY_PXE without DHCP wiring</li>
 * </ul>
 */
class PhysicalServerInterceptorErrorsCase extends SubCase {
    EnvSpec env

    @Override
    void clean() {
        env.delete()
    }

    @Override
    void setup() {
        useSpring(KvmTest.springSpec)
    }

    @Override
    void environment() {
        env = makeEnv {
            zone {
                name = "zone"

                cluster {
                    name = "cluster-85190"
                    hypervisorType = "KVM"
                }
            }
        }
    }

    @Override
    void test() {
        env.create {
            testDuplicateSerialNumberInSameZoneFailsLoudly()
            testAttachSameRoleTwiceFailsLoudly()
            testGatewayPxeRequiresDhcpFields()
        }
    }

    private ServerPoolInventory createPool(String poolName) {
        def zone = env.inventoryByName("zone") as ZoneInventory
        return createServerPool {
            name = poolName
            zoneUuid = zone.uuid
        } as ServerPoolInventory
    }

    private void persistKvmRole(String serverUuid, String clusterUuid) {
        DatabaseFacade dbf = bean(DatabaseFacade.class)
        PhysicalServerRoleVO role = new PhysicalServerRoleVO()
        role.uuid = Platform.getUuid()
        role.serverUuid = serverUuid
        role.roleType = "KVM_HOST"
        role.roleUuid = Platform.getUuid()
        role.schedulingMode = SchedulingMode.INTERNAL_SHARED
        dbf.persist(role)
    }

    // ----------------------------------------------------------------
    // CreatePhysicalServer — duplicate serialNumber in same zone
    // ----------------------------------------------------------------

    void testDuplicateSerialNumberInSameZoneFailsLoudly() {
        def zone = env.inventoryByName("zone") as ZoneInventory
        def pool = createPool("pool-85184")

        createPhysicalServer {
            name = "ps-85184-first"
            zoneUuid = zone.uuid
            poolUuid = pool.uuid
            managementIp = "10.0.85.1"
            serialNumber = "TC-DUP-SN-01"
        } as PhysicalServerInventory

        expectApiFailure {
            createPhysicalServer {
                name = "ps-85184-dup"
                zoneUuid = zone.uuid
                poolUuid = pool.uuid
                managementIp = "10.0.85.2"
                serialNumber = "TC-DUP-SN-01"
            }
        } {
            assert details.contains("serialNumber") || details.contains("TC-DUP-SN-01") :
                    "error details should name the offending serialNumber, got: ${details}"
            assert !details.contains("could not execute statement") :
                    "user must not see Hibernate ConstraintViolationException leak; got: ${details}"
        }
    }

    // ----------------------------------------------------------------
    // AttachPhysicalServerRole — same roleType bound twice
    // ----------------------------------------------------------------

    void testAttachSameRoleTwiceFailsLoudly() {
        def zone = env.inventoryByName("zone") as ZoneInventory
        def pool = createPool("pool-85190")

        def server = createPhysicalServer {
            name = "ps-85190"
            zoneUuid = zone.uuid
            poolUuid = pool.uuid
            managementIp = "10.0.85.10"
            serialNumber = "TC-85190"
        } as PhysicalServerInventory

        def cluster = env.inventoryByName("cluster-85190") as ClusterInventory

        // Fixture-style seed of an existing KVM_HOST role row to bypass the real
        // connect flow (no live host on the test runner). The interceptor pre-check
        // we are exercising runs entirely off PhysicalServerRoleVO, so a persisted
        // row is all that is required to drive the error-path under test.
        persistKvmRole(server.uuid, cluster.uuid)

        expectApiFailure {
            attachPhysicalServerRole {
                delegate.serverUuid = server.uuid
                roleType = "KVM_HOST"
                clusterUuid = cluster.uuid
                roleConfig = [
                    username: "root",
                    password: "password"
                ]
            }
        } {
            assert details.contains("KVM_HOST") :
                    "error details should name the offending roleType, got: ${details}"
            assert details.contains("already has role") || details.contains("detach first") :
                    "error details should explain remedy (detach first), got: ${details}"
        }
    }

    // ----------------------------------------------------------------
    // CreateProvisionNetwork — GATEWAY_PXE without DHCP wiring
    // ----------------------------------------------------------------

    void testGatewayPxeRequiresDhcpFields() {
        def zone = env.inventoryByName("zone") as ZoneInventory

        expectApiFailure {
            createProvisionNetwork {
                name = "net-85350-bad"
                zoneUuid = zone.uuid
                type = "GATEWAY_PXE"
                // DHCP wiring intentionally omitted
            }
        } {
            assert details.contains("GATEWAY_PXE") :
                    "error details should name the offending type, got: ${details}"
            ["dhcpInterface", "dhcpRangeStartIp", "dhcpRangeEndIp", "dhcpRangeNetmask"].each { f ->
                assert details.contains(f) : "error details should list missing field ${f}, got: ${details}"
            }
        }

        // Sanity: with all DHCP fields supplied, the create succeeds.
        def good = createProvisionNetwork {
            name = "net-85350-good"
            zoneUuid = zone.uuid
            type = "GATEWAY_PXE"
            dhcpInterface = "eth0"
            dhcpRangeStartIp = "192.168.50.10"
            dhcpRangeEndIp = "192.168.50.100"
            dhcpRangeNetmask = "255.255.255.0"
            dhcpRangeGateway = "192.168.50.1"
        }
        assert good != null
        deleteProvisionNetwork { uuid = good.uuid }
    }
}
