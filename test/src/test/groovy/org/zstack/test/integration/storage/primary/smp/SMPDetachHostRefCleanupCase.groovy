package org.zstack.test.integration.storage.primary.smp

import org.zstack.core.db.Q
import org.zstack.header.storage.primary.PrimaryStorageHostRefVO
import org.zstack.header.storage.primary.PrimaryStorageHostRefVO_
import org.zstack.sdk.ClusterInventory
import org.zstack.sdk.HostInventory
import org.zstack.sdk.PrimaryStorageInventory
import org.zstack.test.integration.storage.SMPEnv
import org.zstack.test.integration.storage.StorageTest
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.SubCase

class SMPDetachHostRefCleanupCase extends SubCase {
    EnvSpec env

    @Override
    void clean() {
        env.delete()
    }

    @Override
    void setup() {
        useSpring(StorageTest.springSpec)
    }

    @Override
    void environment() {
        env = SMPEnv.threeHostsNoVmBasicEnv()
    }

    @Override
    void test() {
        env.create {
            testDetachCleansUpHostRefVO()
        }
    }

    void testDetachCleansUpHostRefVO() {
        PrimaryStorageInventory ps = env.inventoryByName("smp")
        ClusterInventory cluster = env.inventoryByName("cluster")
        HostInventory kvm1 = env.inventoryByName("kvm1")
        HostInventory kvm2 = env.inventoryByName("kvm2")
        HostInventory kvm3 = env.inventoryByName("kvm3")

        List<String> hostUuids = [kvm1.uuid, kvm2.uuid, kvm3.uuid]

        // Verify HostRefVO records exist before detach (status Connected after attach)
        long before = Q.New(PrimaryStorageHostRefVO.class)
                .eq(PrimaryStorageHostRefVO_.primaryStorageUuid, ps.uuid)
                .in(PrimaryStorageHostRefVO_.hostUuid, hostUuids)
                .count()
        assert before == 3

        detachPrimaryStorageFromCluster {
            primaryStorageUuid = ps.uuid
            clusterUuid = cluster.uuid
        }

        // After detach, all HostRefVO records for this PS in the cluster should be cleaned up
        long after = Q.New(PrimaryStorageHostRefVO.class)
                .eq(PrimaryStorageHostRefVO_.primaryStorageUuid, ps.uuid)
                .in(PrimaryStorageHostRefVO_.hostUuid, hostUuids)
                .count()
        assert after == 0
    }
}
