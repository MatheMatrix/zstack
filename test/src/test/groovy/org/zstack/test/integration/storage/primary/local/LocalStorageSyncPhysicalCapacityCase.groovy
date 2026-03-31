package org.zstack.test.integration.storage.primary.local

import org.zstack.core.db.Q
import org.zstack.sdk.PrimaryStorageInventory
import org.zstack.storage.primary.local.LocalStorageHostRefVO
import org.zstack.storage.primary.local.LocalStorageHostRefVO_
import org.zstack.test.integration.kvm.Env
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.LocalStorageSpec
import org.zstack.testlib.SubCase
import org.zstack.utils.data.SizeUnit

/**
 * Test for ZSTAC-82207:
 * After migrating snapshot group data away from a local storage PS,
 * syncPrimaryStorageCapacity should update availablePhysicalCapacity
 * to reflect the actual physical disk state (from agent), not stale
 * values from LocalStorageHostRefVO.
 *
 * Root cause: syncPhysicalCapacityInCluster did not update per-host
 * LocalStorageHostRefVO.availablePhysicalCapacity, so the subsequent
 * calculateTotalCapacity overwrote PrimaryStorageCapacityVO with stale values.
 */
class LocalStorageSyncPhysicalCapacityCase extends SubCase {
    EnvSpec env

    @Override
    void setup() {
        spring {
            sftpBackupStorage()
            localStorage()
            virtualRouter()
            securityGroup()
            kvm()
        }
    }

    @Override
    void environment() {
        env = Env.oneVmBasicEnv()
    }

    @Override
    void test() {
        env.create {
            testSyncPhysicalCapacityUpdatesHostRef()
        }
    }

    /**
     * Verify that syncPrimaryStorageCapacity updates both:
     * 1. PrimaryStorageCapacityVO.availablePhysicalCapacity (PS-level)
     * 2. LocalStorageHostRefVO.availablePhysicalCapacity (per-host)
     *
     * This ensures calculateTotalCapacity (step 2 of sync) uses fresh
     * physical values, not stale ones.
     */
    void testSyncPhysicalCapacityUpdatesHostRef() {
        PrimaryStorageInventory ps = env.inventoryByName("local")
        LocalStorageSpec lspec = env.specByName("local") as LocalStorageSpec

        // 1. Get initial physical capacity
        PrimaryStorageInventory psInv = queryPrimaryStorage {
            conditions = ["uuid=${ps.uuid}".toString()]
        }[0]
        long initialPhysicalAvail = psInv.availablePhysicalCapacity

        // Also check host ref
        long initialHostRefPhysicalAvail = Q.New(LocalStorageHostRefVO.class)
                .eq(LocalStorageHostRefVO_.primaryStorageUuid, ps.uuid)
                .select(LocalStorageHostRefVO_.availablePhysicalCapacity)
                .findValue() as long

        // 2. Simulate physical capacity change: e.g., snapshot data removed from disk,
        //    so physical available capacity increased by 2GB
        long increase = SizeUnit.GIGABYTE.toByte(2)
        long newAvail = initialPhysicalAvail + increase
        lspec.availableCapacity = newAvail

        // 3. Sync primary storage capacity
        syncPrimaryStorageCapacity {
            primaryStorageUuid = ps.uuid
        }

        // 4. Verify PS-level availablePhysicalCapacity is updated
        retryInSecs(3) {
            psInv = queryPrimaryStorage {
                conditions = ["uuid=${ps.uuid}".toString()]
            }[0]
            assert psInv.availablePhysicalCapacity == newAvail :
                    "PS availablePhysicalCapacity should be ${newAvail}, but got ${psInv.availablePhysicalCapacity}"
        }

        // 5. Verify host ref is also updated (this was the bug - host ref was stale)
        long updatedHostRefPhysicalAvail = Q.New(LocalStorageHostRefVO.class)
                .eq(LocalStorageHostRefVO_.primaryStorageUuid, ps.uuid)
                .select(LocalStorageHostRefVO_.availablePhysicalCapacity)
                .findValue() as long

        assert updatedHostRefPhysicalAvail == newAvail :
                "LocalStorageHostRefVO.availablePhysicalCapacity should be ${newAvail}, but got ${updatedHostRefPhysicalAvail}"
    }

    @Override
    void clean() {
        env.delete()
    }
}
