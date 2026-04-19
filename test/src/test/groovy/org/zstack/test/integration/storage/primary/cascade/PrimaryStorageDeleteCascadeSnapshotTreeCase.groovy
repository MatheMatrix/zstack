package org.zstack.test.integration.storage.primary.cascade

import org.zstack.core.db.Q
import org.zstack.header.storage.snapshot.VolumeSnapshotTreeVO
import org.zstack.header.storage.snapshot.VolumeSnapshotTreeVO_
import org.zstack.header.storage.snapshot.VolumeSnapshotTreeEO
import org.zstack.header.storage.snapshot.VolumeSnapshotTreeEO_
import org.zstack.header.storage.snapshot.VolumeSnapshotVO
import org.zstack.header.storage.snapshot.VolumeSnapshotVO_
import org.zstack.header.vm.VmInstanceVO
import org.zstack.header.vo.ResourceVO
import org.zstack.header.vo.ResourceVO_
import org.zstack.sdk.ClusterInventory
import org.zstack.sdk.PrimaryStorageInventory
import org.zstack.sdk.VmInstanceInventory
import org.zstack.sdk.VolumeSnapshotInventory
import org.zstack.test.integration.storage.StorageTest
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.SubCase
import org.zstack.utils.data.SizeUnit

/**
 * Test that VolumeSnapshotTreeEO and ResourceVO are properly cleaned up
 * when deleting a primary storage via cascade.
 *
 * Steps:
 * 1. Create a VM on local storage
 * 2. Create a volume snapshot (which creates VolumeSnapshotTreeVO)
 * 3. Detach primary storage from cluster
 * 4. Delete primary storage
 * 5. Assert VolumeSnapshotTreeVO / VolumeSnapshotTreeEO / ResourceVO are all cleaned
 */
class PrimaryStorageDeleteCascadeSnapshotTreeCase extends SubCase {
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
        env = env {
            instanceOffering {
                name = "instanceOffering"
                memory = SizeUnit.GIGABYTE.toByte(8)
                cpu = 4
            }

            diskOffering {
                name = "diskOffering"
                diskSize = SizeUnit.GIGABYTE.toByte(20)
            }

            sftpBackupStorage {
                name = "sftp"
                url = "/sftp"
                username = "root"
                password = "password"
                hostname = "localhost"

                image {
                    name = "image1"
                    url = "http://zstack.org/download/test.qcow2"
                }
            }

            zone {
                name = "zone"
                description = "test"

                cluster {
                    name = "cluster1"
                    hypervisorType = "KVM"

                    kvm {
                        name = "kvm1"
                        managementIp = "localhost"
                        username = "root"
                        password = "password"
                    }

                    attachPrimaryStorage("local")
                    attachL2Network("l2")
                }

                localPrimaryStorage {
                    name = "local"
                    url = "/local_ps"
                }

                l2NoVlanNetwork {
                    name = "l2"
                    physicalInterface = "eth0"

                    l3Network {
                        name = "l3"

                        ip {
                            startIp = "192.168.100.10"
                            endIp = "192.168.100.100"
                            netmask = "255.255.255.0"
                            gateway = "192.168.100.1"
                        }
                    }
                }

                attachBackupStorage("sftp")
            }

            vm {
                name = "vm1"
                useInstanceOffering("instanceOffering")
                useImage("image1")
                useL3Networks("l3")
                useRootDiskOffering("diskOffering")
                useHost("kvm1")
            }
        }
    }

    @Override
    void test() {
        env.create {
            testDeletePrimaryStorageCascadeCleanupSnapshotTree()
        }
    }

    void testDeletePrimaryStorageCascadeCleanupSnapshotTree() {
        VmInstanceInventory vm = env.inventoryByName("vm1")
        PrimaryStorageInventory ps = env.inventoryByName("local")
        ClusterInventory cluster = env.inventoryByName("cluster1")

        // create a snapshot, which will also create a VolumeSnapshotTreeVO
        VolumeSnapshotInventory snapshot = createVolumeSnapshot {
            volumeUuid = vm.rootVolumeUuid
            name = "snapshot1"
        }

        String treeUuid = snapshot.treeUuid
        assert treeUuid != null

        // verify VolumeSnapshotTreeVO and ResourceVO exist
        assert Q.New(VolumeSnapshotTreeVO.class)
                .eq(VolumeSnapshotTreeVO_.uuid, treeUuid)
                .isExists()
        assert Q.New(ResourceVO.class)
                .eq(ResourceVO_.uuid, treeUuid)
                .isExists()

        // detach primary storage from cluster
        detachPrimaryStorageFromCluster {
            primaryStorageUuid = ps.uuid
            clusterUuid = cluster.uuid
        }

        // delete primary storage — triggers cascade deletion
        deletePrimaryStorage {
            uuid = ps.uuid
        }

        retryInSecs {
            // VM should be gone
            assert dbFindByUuid(vm.uuid, VmInstanceVO.class) == null

            // VolumeSnapshotTreeVO (view) should be gone
            assert !Q.New(VolumeSnapshotTreeVO.class)
                    .eq(VolumeSnapshotTreeVO_.uuid, treeUuid)
                    .isExists()

            // VolumeSnapshotTreeEO (soft-delete table) should be hard-deleted
            assert !Q.New(VolumeSnapshotTreeEO.class)
                    .eq(VolumeSnapshotTreeEO_.uuid, treeUuid)
                    .isExists()

            // ResourceVO should also be cleaned
            assert !Q.New(ResourceVO.class)
                    .eq(ResourceVO_.uuid, treeUuid)
                    .isExists()
        }
    }
}
