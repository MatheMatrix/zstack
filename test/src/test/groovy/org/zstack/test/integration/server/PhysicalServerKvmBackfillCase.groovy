package org.zstack.test.integration.server

import org.zstack.core.db.Q
import org.zstack.header.server.PhysicalServerAO_
import org.zstack.header.server.PhysicalServerVO
import org.zstack.kvm.KVMAgentCommands
import org.zstack.sdk.ClusterInventory
import org.zstack.sdk.HostInventory
import org.zstack.sdk.PhysicalServerInventory
import org.zstack.sdk.ServerPoolInventory
import org.zstack.sdk.ZoneInventory
import org.zstack.test.integration.kvm.KvmTest
import org.zstack.test.integration.kvm.host.HostEnv
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.SubCase

import static org.zstack.kvm.KVMConstant.KVM_HOST_FACT_PATH

class PhysicalServerKvmBackfillCase extends SubCase {
    EnvSpec env

    static final String FAKE_SN = "SN-BACKFILL-1234"
    static final String FAKE_MFR = "Dell Inc."
    static final String FAKE_MODEL = "PowerEdge R750"

    @Override
    void setup() {
        useSpring(KvmTest.springSpec)
    }

    @Override
    void environment() {
        env = HostEnv.noHostBasicEnv()
    }

    @Override
    void test() {
        env.create {
            installHostFactSimulator()
            testTier3AutoCreatedPsBackfilled()
            testPreResolvedPsBackfilled()
            testUserSuppliedFieldNotOverwritten()
        }
    }

    @Override
    void clean() {
        env.delete()
    }

    private void installHostFactSimulator() {
        env.afterSimulator(KVM_HOST_FACT_PATH) { KVMAgentCommands.HostFactResponse rsp ->
            rsp.setSystemSerialNumber(FAKE_SN)
            rsp.setSystemManufacturer(FAKE_MFR)
            rsp.setSystemProductName(FAKE_MODEL)
            return rsp
        }
    }

    void testTier3AutoCreatedPsBackfilled() {
        def zone = env.inventoryByName("zone") as ZoneInventory
        def cluster = env.inventoryByName("cluster") as ClusterInventory

        def pool = createServerPool {
            name = "pool-backfill-auto"
            delegate.zoneUuid = zone.uuid
        } as ServerPoolInventory

        changeClusterServerPool {
            delegate.clusterUuid = cluster.uuid
            delegate.serverPoolUuid = pool.uuid
        }

        def host = addKVMHost {
            name = "host-backfill-auto"
            managementIp = "127.0.0.40"
            clusterUuid = cluster.uuid
            username = "root"
            password = "password"
        } as HostInventory

        PhysicalServerVO ps = Q.New(PhysicalServerVO.class)
                .eq(PhysicalServerAO_.managementIp, "127.0.0.40")
                .eq(PhysicalServerAO_.zoneUuid, zone.uuid)
                .find()
        assert ps != null : "auto-created PS not found"
        assert ps.serialNumber == FAKE_SN :
                "PS.serialNumber expected=${FAKE_SN}, got=${ps.serialNumber}"
        assert ps.manufacturer == FAKE_MFR :
                "PS.manufacturer expected=${FAKE_MFR}, got=${ps.manufacturer}"
        assert ps.model == FAKE_MODEL :
                "PS.model expected=${FAKE_MODEL}, got=${ps.model}"

        detachPhysicalServerRole { delegate.serverUuid = ps.uuid; roleType = "KVM_HOST" }
        deleteHost { uuid = host.uuid }
        deletePhysicalServer { uuid = ps.uuid }
        deleteServerPool { uuid = pool.uuid }
    }

    void testPreResolvedPsBackfilled() {
        def zone = env.inventoryByName("zone") as ZoneInventory
        def cluster = env.inventoryByName("cluster") as ClusterInventory

        def pool = createServerPool {
            name = "pool-backfill-pre"
            delegate.zoneUuid = zone.uuid
        } as ServerPoolInventory

        def ps = createPhysicalServer {
            name = "ps-backfill-pre"
            delegate.zoneUuid = zone.uuid
            delegate.poolUuid = pool.uuid
            managementIp = "127.0.0.41"
        } as PhysicalServerInventory

        // sanity: bare CreatePhysicalServer leaves identity fields null
        PhysicalServerVO before = Q.New(PhysicalServerVO.class)
                .eq(PhysicalServerAO_.uuid, ps.uuid)
                .find()
        assert before.serialNumber == null
        assert before.manufacturer == null
        assert before.model == null

        def host = addKVMHost {
            name = "host-backfill-pre"
            managementIp = "127.0.0.41"
            clusterUuid = cluster.uuid
            username = "root"
            password = "password"
            delegate.serverUuid = ps.uuid
        } as HostInventory

        PhysicalServerVO after = Q.New(PhysicalServerVO.class)
                .eq(PhysicalServerAO_.uuid, ps.uuid)
                .find()
        assert after.serialNumber == FAKE_SN :
                "(pre-resolved): PS.serialNumber expected=${FAKE_SN}, got=${after.serialNumber}"
        assert after.manufacturer == FAKE_MFR :
                "(pre-resolved): PS.manufacturer expected=${FAKE_MFR}, got=${after.manufacturer}"
        assert after.model == FAKE_MODEL :
                "(pre-resolved): PS.model expected=${FAKE_MODEL}, got=${after.model}"

        detachPhysicalServerRole { delegate.serverUuid = ps.uuid; roleType = "KVM_HOST" }
        deleteHost { uuid = host.uuid }
        deletePhysicalServer { uuid = ps.uuid }
        deleteServerPool { uuid = pool.uuid }
    }

    void testUserSuppliedFieldNotOverwritten() {
        def zone = env.inventoryByName("zone") as ZoneInventory
        def cluster = env.inventoryByName("cluster") as ClusterInventory

        def pool = createServerPool {
            name = "pool-backfill-keep"
            delegate.zoneUuid = zone.uuid
        } as ServerPoolInventory

        final String userMfr = "UserSetMfr"
        def ps = createPhysicalServer {
            name = "ps-backfill-keep"
            delegate.zoneUuid = zone.uuid
            delegate.poolUuid = pool.uuid
            managementIp = "127.0.0.42"
            delegate.manufacturer = userMfr
        } as PhysicalServerInventory

        def host = addKVMHost {
            name = "host-backfill-keep"
            managementIp = "127.0.0.42"
            clusterUuid = cluster.uuid
            username = "root"
            password = "password"
            delegate.serverUuid = ps.uuid
        } as HostInventory

        PhysicalServerVO after = Q.New(PhysicalServerVO.class)
                .eq(PhysicalServerAO_.uuid, ps.uuid)
                .find()
        // user-supplied manufacturer preserved (null-only update)
        assert after.manufacturer == userMfr :
                "(idempotent): user-supplied manufacturer overwritten, got=${after.manufacturer}"
        // null fields still backfilled
        assert after.serialNumber == FAKE_SN
        assert after.model == FAKE_MODEL

        detachPhysicalServerRole { delegate.serverUuid = ps.uuid; roleType = "KVM_HOST" }
        deleteHost { uuid = host.uuid }
        deletePhysicalServer { uuid = ps.uuid }
        deleteServerPool { uuid = pool.uuid }
    }
}
