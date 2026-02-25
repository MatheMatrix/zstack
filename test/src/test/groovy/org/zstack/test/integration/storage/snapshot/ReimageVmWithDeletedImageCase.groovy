package org.zstack.test.integration.storage.snapshot

import org.zstack.core.db.Q
import org.zstack.core.db.SQL
import org.zstack.header.image.ImageVO
import org.zstack.header.image.ImageVO_
import org.zstack.header.storage.primary.ImageCacheVO
import org.zstack.header.storage.primary.ImageCacheVO_
import org.zstack.header.volume.VolumeVO
import org.zstack.header.volume.VolumeVO_
import org.zstack.sdk.*
import org.zstack.test.integration.storage.StorageTest
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.SubCase
import org.zstack.utils.data.SizeUnit

/**
 * Test reimage VM when root image has been deleted and no image cache
 * exists on the current primary storage (ZSTAC-46893).
 *
 * Scenario: clone VM -> storage migration -> source image deleted -> reimage fails
 */
class ReimageVmWithDeletedImageCase extends SubCase {

    EnvSpec env

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
                    name = "local-cluster"
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
                name = "local-vm"
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
            testReimageFailsWhenImageDeletedAndNoCacheOnCurrentPs()
            testReimageFailsWhenRootImageUuidIsNull()
        }
    }

    /**
     * Simulate the ZSTAC-46893 scenario:
     * - Cloned VM's rootImageUuid points to a deleted temporary image
     * - After storage migration, no ImageCache on current PS
     * - Reimage should fail with a friendly error message
     */
    void testReimageFailsWhenImageDeletedAndNoCacheOnCurrentPs() {
        def vm = env.inventoryByName("local-vm") as VmInstanceInventory

        stopVmInstance {
            uuid = vm.uuid
        }

        // Get root volume info
        def rootVolumeUuid = vm.rootVolumeUuid
        def rootVolume = Q.New(VolumeVO.class).eq(VolumeVO_.uuid, rootVolumeUuid).find() as VolumeVO
        def originalRootImageUuid = rootVolume.rootImageUuid
        def psUuid = rootVolume.primaryStorageUuid

        // Simulate cloned VM scenario: set rootImageUuid to a non-existent UUID
        // This mimics the case where clone creates a temporary image that gets deleted
        def fakeImageUuid = "fake-deleted-image-uuid-for-test"
        SQL.New(VolumeVO.class)
                .eq(VolumeVO_.uuid, rootVolumeUuid)
                .set(VolumeVO_.rootImageUuid, fakeImageUuid)
                .update()

        // Verify no ImageVO and no ImageCacheVO exist for the fake UUID
        assert !Q.New(ImageVO.class).eq(ImageVO_.uuid, fakeImageUuid).isExists()
        assert !Q.New(ImageCacheVO.class)
                .eq(ImageCacheVO_.imageUuid, fakeImageUuid)
                .eq(ImageCacheVO_.primaryStorageUuid, psUuid)
                .isExists()

        // Reimage should fail with friendly error
        expect(AssertionError.class) {
            reimageVmInstance {
                vmInstanceUuid = vm.uuid
            }
        }

        // Restore original rootImageUuid for cleanup
        SQL.New(VolumeVO.class)
                .eq(VolumeVO_.uuid, rootVolumeUuid)
                .set(VolumeVO_.rootImageUuid, originalRootImageUuid)
                .update()

        // Verify normal reimage still works after restoring
        reimageVmInstance {
            vmInstanceUuid = vm.uuid
        }
    }

    /**
     * Verify reimage fails gracefully when rootImageUuid is null.
     */
    void testReimageFailsWhenRootImageUuidIsNull() {
        def vm = env.inventoryByName("local-vm") as VmInstanceInventory

        stopVmInstance {
            uuid = vm.uuid
        }

        def rootVolumeUuid = vm.rootVolumeUuid
        def rootVolume = Q.New(VolumeVO.class).eq(VolumeVO_.uuid, rootVolumeUuid).find() as VolumeVO
        def originalRootImageUuid = rootVolume.rootImageUuid

        // Set rootImageUuid to null
        SQL.New(VolumeVO.class)
                .eq(VolumeVO_.uuid, rootVolumeUuid)
                .set(VolumeVO_.rootImageUuid, null)
                .update()

        // Reimage should fail
        expect(AssertionError.class) {
            reimageVmInstance {
                vmInstanceUuid = vm.uuid
            }
        }

        // Restore for cleanup
        SQL.New(VolumeVO.class)
                .eq(VolumeVO_.uuid, rootVolumeUuid)
                .set(VolumeVO_.rootImageUuid, originalRootImageUuid)
                .update()
    }

    @Override
    void clean() {
        env.delete()
    }
}
