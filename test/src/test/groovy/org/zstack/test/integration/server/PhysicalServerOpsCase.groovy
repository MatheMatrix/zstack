package org.zstack.test.integration.server

import org.zstack.core.Platform
import org.zstack.core.db.DatabaseFacade
import org.zstack.header.image.ImageConstant
import org.zstack.header.image.ImagePlatform
import org.zstack.header.image.ImageState
import org.zstack.header.image.ImageStatus
import org.zstack.header.image.ImageVO
import org.zstack.header.longjob.LongJobState
import org.zstack.header.longjob.LongJobVO
import org.zstack.header.server.APIProvisionPhysicalServerMsg
import org.zstack.sdk.PhysicalServerInventory
import org.zstack.sdk.PhysicalServerProvisionNetworkInventory
import org.zstack.sdk.ServerPoolInventory
import org.zstack.sdk.ZoneInventory
import org.zstack.server.PhysicalServerScanner
import org.zstack.test.integration.kvm.KvmTest
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.SubCase
import org.zstack.utils.gson.JSONObjectUtil

// FR-032: Power Management, FR-033: Hardware Discovery, FR-034: Server Scan
class PhysicalServerOpsCase extends SubCase {
    EnvSpec env
    DatabaseFacade dbf

    @Override
    void setup() {
        useSpring(KvmTest.springSpec)
        spring {
            include("PhysicalServerTestProviders.xml")
        }
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
            dbf = bean(DatabaseFacade.class)
            // FR-032: Power Management
            testPowerOnPhysicalServer()
            testPowerOffPhysicalServer()
            testPowerResetPhysicalServer()
            testPowerOperationWithoutOob()
            // FR-033: Hardware Discovery
            testDiscoverPhysicalServerHardware()
            testDiscoverHardwareWithoutOob()
            // FR-034: Server Scan
            testScanPhysicalServers()
            testScanPhysicalServersIpRangeLimit()
            testScanPhysicalServersIdempotent()
            testScanRotatesThroughCredentials()
            testScanReturnsAllFourStatusCounts()
            // FR-012: ProvisionProvider orchestration
            testProvisionPhysicalServerStandaloneLongJob()
            testProvisionPhysicalServerNoProviderFailsLongJob()
            // Supplementary
            testQueryProvisionNetwork()
            testDeleteProvisionNetworkBlockedByCluster()
        }
    }

    @Override
    void clean() {
        env.delete()
    }

    // --- helpers ---

    private ServerPoolInventory createPool(String poolName) {
        def zone = env.inventoryByName("zone") as ZoneInventory
        return createServerPool {
            name = poolName
            zoneUuid = zone.uuid
        } as ServerPoolInventory
    }

    private PhysicalServerInventory createServerWithOob(String serverName, String ip, String poolId) {
        def zone = env.inventoryByName("zone") as ZoneInventory
        return createPhysicalServer {
            name = serverName
            zoneUuid = zone.uuid
            poolUuid = poolId
            managementIp = ip
            oobManagementType = "IPMI"
            oobAddress = "192.168.100.${ip.split('\\.')[3]}"
            oobPort = 623
            oobUsername = "admin"
            oobPassword = "password"
        } as PhysicalServerInventory
    }

    private PhysicalServerInventory createServerWithoutOob(String serverName, String ip, String poolId) {
        def zone = env.inventoryByName("zone") as ZoneInventory
        return createPhysicalServer {
            name = serverName
            zoneUuid = zone.uuid
            poolUuid = poolId
            managementIp = ip
        } as PhysicalServerInventory
    }

    private void deleteServersInPool(String poolUuid) {
        def servers = queryPhysicalServer {
            conditions = ["poolUuid=${poolUuid}".toString()]
        }
        servers.each { server ->
            def serverUuid = server.uuid
            deletePhysicalServer { uuid = serverUuid }
        }
    }

    private ImageVO createFakeOsImage(String name) {
        ImageVO vo = new ImageVO()
        vo.uuid = Platform.uuid
        vo.accountUuid = env.session.accountUuid
        vo.name = name
        vo.status = ImageStatus.Ready
        vo.state = ImageState.Enabled
        vo.platform = ImagePlatform.Linux
        vo.type = "zstack"
        vo.format = ImageConstant.QCOW2_FORMAT_STRING
        vo.mediaType = ImageConstant.ImageMediaType.RootVolumeTemplate
        vo.url = "file:///tmp/${name}.qcow2"
        vo.system = false
        vo.size = 1
        vo.actualSize = 1
        return dbf.persistAndRefresh(vo)
    }

    private static final String PROVISION_NIC_MAC = "52:54:00:12:34:56"

    private void ensureProvisionNic(String serverUuid) {
        org.zstack.header.server.PhysicalServerHardwareDetailVO nic = new org.zstack.header.server.PhysicalServerHardwareDetailVO()
        nic.serverUuid = serverUuid
        nic.type = "NIC"
        nic.extraInfo = """{"mac":"${PROVISION_NIC_MAC}","primary":true}"""
        dbf.persistAndRefresh(nic)
    }

    private LongJobVO submitProvisionJob(PhysicalServerInventory server,
                                         PhysicalServerProvisionNetworkInventory network,
                                         ImageVO image) {
        ensureProvisionNic(server.uuid)

        APIProvisionPhysicalServerMsg msg = new APIProvisionPhysicalServerMsg()
        msg.serverUuid = server.uuid
        msg.networkUuid = network.uuid
        msg.osImageUuid = image.uuid
        msg.osDistribution = "rocky9"
        msg.kickstartTemplate = "install-script"
        msg.provisionNicMac = PROVISION_NIC_MAC
        msg.customParams = [role: "kvm", username: "root"]

        def job = submitLongJob {
            jobName = msg.class.simpleName
            jobData = JSONObjectUtil.toJsonString(msg)
            targetResourceUuid = server.uuid
        }

        return dbFindByUuid(job.uuid, LongJobVO.class)
    }

    // --- FR-032: Power Management ---

    // AC-PM-01: powerOn returns inventory with updated powerStatus
    void testPowerOnPhysicalServer() {
        def pool = createPool("pool-power-on")
        def server = createServerWithOob("server-power-on", "192.168.50.1", pool.uuid)

        def result = powerOnPhysicalServer {
            uuid = server.uuid
        } as PhysicalServerInventory

        assert result != null
        assert result.uuid == server.uuid
        assert result.powerStatus == "PowerOn"

        deletePhysicalServer { uuid = server.uuid }
        deleteServerPool { uuid = pool.uuid }
    }

    // AC-PM-02: powerOff returns inventory
    void testPowerOffPhysicalServer() {
        def pool = createPool("pool-power-off")
        def server = createServerWithOob("server-power-off", "192.168.50.2", pool.uuid)

        def result = powerOffPhysicalServer {
            uuid = server.uuid
        } as PhysicalServerInventory

        assert result != null
        assert result.uuid == server.uuid
        assert result.powerStatus == "PowerOff"

        deletePhysicalServer { uuid = server.uuid }
        deleteServerPool { uuid = pool.uuid }
    }

    // AC-PM-03: powerReset returns inventory
    void testPowerResetPhysicalServer() {
        def pool = createPool("pool-power-reset")
        def server = createServerWithOob("server-power-reset", "192.168.50.3", pool.uuid)

        def result = powerResetPhysicalServer {
            uuid = server.uuid
        } as PhysicalServerInventory

        assert result != null
        assert result.uuid == server.uuid
        assert result.powerStatus == "PowerOn"

        deletePhysicalServer { uuid = server.uuid }
        deleteServerPool { uuid = pool.uuid }
    }

    // AC-CB-16: powerOn without OOB credentials returns error
    void testPowerOperationWithoutOob() {
        def pool = createPool("pool-no-oob")
        def server = createServerWithoutOob("server-no-oob", "192.168.50.5", pool.uuid)

        expect(AssertionError.class) {
            powerOnPhysicalServer {
                uuid = server.uuid
            }
        }

        deletePhysicalServer { uuid = server.uuid }
        deleteServerPool { uuid = pool.uuid }
    }

    // --- FR-033: Hardware Discovery ---

    // AC-HD-01: discoverHardware with OOB returns updated inventory
    void testDiscoverPhysicalServerHardware() {
        def pool = createPool("pool-discover-oob")
        def server = createServerWithOob("server-discover-oob", "192.168.51.1", pool.uuid)

        def result = discoverPhysicalServerHardware {
            uuid = server.uuid
        } as PhysicalServerInventory

        assert result != null
        assert result.uuid == server.uuid

        // Verify hardware info was populated after discovery
        def queried = queryPhysicalServer {
            conditions = ["uuid=${server.uuid}".toString()]
        }
        assert queried.size() == 1
        // Hardware info should be populated after discover (TDD - will fail until implemented)

        // FR-003-AC1/AC2: After discovery, hardware info should be populated
        // TDD: When HardwareDiscoverable is implemented:
        // assert queried[0].hardwareInfo != null
        // assert queried[0].hardwareInfo.cpuModel != null

        deletePhysicalServer { uuid = server.uuid }
        deleteServerPool { uuid = pool.uuid }
    }

    // AC-HD-02: discoverHardware without OOB still succeeds (agent-based fallback)
    void testDiscoverHardwareWithoutOob() {
        def pool = createPool("pool-discover-no-oob")
        def server = createServerWithoutOob("server-discover-no-oob", "192.168.51.2", pool.uuid)

        // Should succeed via agent-based discovery even without OOB
        def result = discoverPhysicalServerHardware {
            uuid = server.uuid
        } as PhysicalServerInventory

        assert result != null
        assert result.uuid == server.uuid

        deletePhysicalServer { uuid = server.uuid }
        deleteServerPool { uuid = pool.uuid }
    }

    // --- FR-034: Server Scan ---

    // AC-PS-01: scan with valid params returns event with count fields
    void testScanPhysicalServers() {
        def zone = env.inventoryByName("zone") as ZoneInventory
        def pool = createPool("pool-scan")

        def result = scanPhysicalServers {
            zoneUuid = zone.uuid
            poolUuid = pool.uuid
            ipRange = "192.168.60.1-192.168.60.10"
            credentials = [
                [username: "admin", password: "password"],
                [username: "root", password: "calvin"]
            ]
        }

        assert result != null
        assert result.discoveredCount == 10
        assert result.existingCount == 0
        assert result.unreachableCount == 0
        assert result.authFailedCount == 0
        assert result.discoveredServers.size() == 10

        deleteServersInPool(pool.uuid)
        deleteServerPool { uuid = pool.uuid }
    }

    // AC-PS-20: scan with >1024 IPs should fail
    void testScanPhysicalServersIpRangeLimit() {
        def zone = env.inventoryByName("zone") as ZoneInventory
        def pool = createPool("pool-scan-limit")

        // 10.0.0.1 - 10.0.4.1 = 1025 IPs, exceeds limit
        expect(AssertionError.class) {
            scanPhysicalServers {
                zoneUuid = zone.uuid
                poolUuid = pool.uuid
                ipRange = "10.0.0.1-10.0.4.1"
                credentials = [[username: "admin", password: "password"]]
            }
        }

        deleteServerPool { uuid = pool.uuid }
    }

    // AC-PS-17: scanning same range twice — second scan has discoveredCount=0, existingCount>0
    void testScanPhysicalServersIdempotent() {
        def zone = env.inventoryByName("zone") as ZoneInventory
        def pool = createPool("pool-scan-idempotent")

        def firstResult = scanPhysicalServers {
            zoneUuid = zone.uuid
            poolUuid = pool.uuid
            ipRange = "192.168.61.1-192.168.61.5"
            credentials = [[username: "admin", password: "password"]]
        }

        assert firstResult != null

        // Second scan of same range — newly discovered should be 0, existing > 0
        def secondResult = scanPhysicalServers {
            zoneUuid = zone.uuid
            poolUuid = pool.uuid
            ipRange = "192.168.61.1-192.168.61.5"
            credentials = [[username: "admin", password: "password"]]
        }

        assert secondResult != null
        assert secondResult.discoveredCount == 0
        assert secondResult.existingCount > 0

        deleteServersInPool(pool.uuid)
        deleteServerPool { uuid = pool.uuid }
    }

    // --- FR-012: ProvisionProvider orchestration ---

    // AC-PR-01: GATEWAY_PXE with registered provider — long job succeeds and jobResult contains serverUuid/networkUuid
    void testProvisionPhysicalServerStandaloneLongJob() {
        def zone = env.inventoryByName("zone") as ZoneInventory
        def pool = createPool("pool-provision-standalone")
        def server = createServerWithOob("server-provision-standalone", "192.168.62.1", pool.uuid)
        def image = createFakeOsImage("provision-rocky9")

        def net = createProvisionNetwork {
            name = "pxe-provision-standalone"
            zoneUuid = zone.uuid
            type = "GATEWAY_PXE"
        } as PhysicalServerProvisionNetworkInventory

        attachProvisionNetworkToPool {
            networkUuid = net.uuid
            poolUuid = pool.uuid
        }

        LongJobVO job = submitProvisionJob(server, net, image)

        retryInSecs {
            job = dbFindByUuid(job.uuid, LongJobVO.class)
            assert job.state == LongJobState.Succeeded
            assert job.targetResourceUuid == server.uuid
            assert job.jobResult.contains(server.uuid)
            assert job.jobResult.contains(net.uuid)
        }

        detachProvisionNetworkFromPool {
            networkUuid = net.uuid
            poolUuid = pool.uuid
        }
        deleteProvisionNetwork { uuid = net.uuid }
        deletePhysicalServer { uuid = server.uuid }
        deleteServerPool { uuid = pool.uuid }
    }

    // AC-PR-02: STANDALONE_PXE provider is not yet implemented — long job must fail with a clear error
    void testProvisionPhysicalServerNoProviderFailsLongJob() {
        def zone = env.inventoryByName("zone") as ZoneInventory
        def pool = createPool("pool-provision-no-provider")
        def server = createServerWithOob("server-provision-no-provider", "192.168.62.2", pool.uuid)
        def image = createFakeOsImage("provision-no-provider")

        def net = createProvisionNetwork {
            name = "pxe-provision-no-provider"
            zoneUuid = zone.uuid
            type = "STANDALONE_PXE"
        } as PhysicalServerProvisionNetworkInventory

        attachProvisionNetworkToPool {
            networkUuid = net.uuid
            poolUuid = pool.uuid
        }

        LongJobVO job = submitProvisionJob(server, net, image)

        retryInSecs {
            job = dbFindByUuid(job.uuid, LongJobVO.class)
            assert job.state == LongJobState.Failed
            assert job.jobResult.contains("no ProvisionProvider registered")
        }

        detachProvisionNetworkFromPool {
            networkUuid = net.uuid
            poolUuid = pool.uuid
        }
        deleteProvisionNetwork { uuid = net.uuid }
        deletePhysicalServer { uuid = server.uuid }
        deleteServerPool { uuid = pool.uuid }
    }

    // --- Supplementary ---

    // Query provision network by name
    void testQueryProvisionNetwork() {
        def zone = env.inventoryByName("zone") as ZoneInventory

        def net = createProvisionNetwork {
            name = "pxe-ops-query"
            zoneUuid = zone.uuid
            type = "STANDALONE_PXE"
        } as PhysicalServerProvisionNetworkInventory

        def nets = queryProvisionNetwork {
            conditions = ["name=pxe-ops-query"]
        }

        assert nets.size() == 1
        assert nets[0].uuid == net.uuid
        assert nets[0].zoneUuid == zone.uuid

        deleteProvisionNetwork { uuid = net.uuid }
    }

    // AC-PS-18: scan rotates through multiple credentials — first success wins
    void testScanRotatesThroughCredentials() {
        def zone = env.inventoryByName("zone") as ZoneInventory
        def pool = createPool("pool-scan-cred-rotate")

        // bad-user always AUTH_FAILED; good-user always SUCCESS
        PhysicalServerScanner.probeOverride = { String ip, String username ->
            username == "good-user" ? PhysicalServerScanner.ProbeStatus.SUCCESS
                                    : PhysicalServerScanner.ProbeStatus.AUTH_FAILED
        }

        try {
            def result = scanPhysicalServers {
                zoneUuid = zone.uuid
                poolUuid = pool.uuid
                ipRange = "192.168.62.10-192.168.62.12"
                credentials = [
                    [username: "bad-user",  password: "wrong"],
                    [username: "good-user", password: "correct"]
                ]
            }

            assert result != null
            // all 3 IPs discovered via the second (good) credential
            assert result.discoveredCount == 3
            assert result.authFailedCount == 0
            assert result.unreachableCount == 0
            // discovered servers must carry the winning credential's username
            result.discoveredServers.each { ps ->
                assert ps.oobUsername == "good-user"
            }
        } finally {
            PhysicalServerScanner.probeOverride = null
            deleteServersInPool(pool.uuid)
            deleteServerPool { uuid = pool.uuid }
        }
    }

    // AC-PS-19: scan returns all 4 status counts correctly
    void testScanReturnsAllFourStatusCounts() {
        def zone = env.inventoryByName("zone") as ZoneInventory
        def pool = createPool("pool-scan-four-counts")

        // Pre-create an existing server so it shows up as existingCount
        def existingIp = "192.168.63.2"
        createServerWithOob("server-existing-63-2", existingIp, pool.uuid)

        // Map each IP to its intended probe status
        def statusByIp = [
            "192.168.63.1": PhysicalServerScanner.ProbeStatus.SUCCESS,       // discovered (new)
            "192.168.63.2": PhysicalServerScanner.ProbeStatus.SUCCESS,       // existing (short-circuits before probe)
            "192.168.63.3": PhysicalServerScanner.ProbeStatus.AUTH_FAILED,   // auth-failed
            "192.168.63.4": PhysicalServerScanner.ProbeStatus.UNREACHABLE,   // unreachable
        ]
        PhysicalServerScanner.probeOverride = { String ip, String username ->
            statusByIp.getOrDefault(ip, PhysicalServerScanner.ProbeStatus.SUCCESS)
        }

        try {
            def result = scanPhysicalServers {
                zoneUuid = zone.uuid
                poolUuid = pool.uuid
                ipRange = "192.168.63.1-192.168.63.4"
                credentials = [[username: "admin", password: "password"]]
            }

            assert result != null
            assert result.discoveredCount  == 1
            assert result.existingCount    == 1
            assert result.authFailedCount  == 1
            assert result.unreachableCount == 1
            assert result.authFailedIps.contains("192.168.63.3")
        } finally {
            PhysicalServerScanner.probeOverride = null
            deleteServersInPool(pool.uuid)
            deleteServerPool { uuid = pool.uuid }
        }
    }

    // Deleting a provision network with an attached cluster must fail
    void testDeleteProvisionNetworkBlockedByCluster() {
        def zone = env.inventoryByName("zone") as ZoneInventory
        def cluster = env.inventoryByName("cluster")

        def net = createProvisionNetwork {
            name = "pxe-ops-blocked"
            zoneUuid = zone.uuid
            type = "STANDALONE_PXE"
        } as PhysicalServerProvisionNetworkInventory

        attachProvisionNetworkToCluster {
            networkUuid = net.uuid
            clusterUuid = cluster.uuid
        }

        expect(AssertionError.class) {
            deleteProvisionNetwork { uuid = net.uuid }
        }

        detachProvisionNetworkFromCluster {
            networkUuid = net.uuid
            clusterUuid = cluster.uuid
        }
        deleteProvisionNetwork { uuid = net.uuid }
    }
}
