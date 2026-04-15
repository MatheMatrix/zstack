package org.zstack.test.integration.storage.primary.nfs.capacity

import org.zstack.core.db.DatabaseFacade
import org.zstack.header.storage.primary.PrimaryStorageCapacityVO
import org.zstack.header.volume.VolumeVO
import org.zstack.sdk.GetPrimaryStorageCapacityResult
import org.zstack.sdk.PrimaryStorageInventory
import org.zstack.sdk.VmInstanceInventory
import org.zstack.test.integration.storage.StorageTest
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.SubCase
import org.zstack.storage.primary.nfs.NfsPrimaryStorageKVMBackend
import org.zstack.utils.data.SizeUnit
import org.zstack.header.network.service.NetworkServiceType
import org.zstack.network.securitygroup.SecurityGroupConstant
import org.zstack.network.service.virtualrouter.VirtualRouterConstant

/**
 * ZSTAC-80937: syncVolumeSize updates VolumeVO.size without adjusting
 * PrimaryStorage availableCapacity, causing allocation rate drift.
 *
 * Test strategy: mock GET_VOLUME_SIZE_PATH to return a larger size,
 * call APISyncVolumeSizeMsg, verify that availableCapacity is adjusted.
 */
class NfsSyncVolumeSizeCapacityCase extends SubCase {
    EnvSpec env
    DatabaseFacade dbf

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

                image {
                    name = "vr"
                    url = "http://zstack.org/download/vr.qcow2"
                }
            }

            zone {
                name = "zone"
                description = "test"

                cluster {
                    name = "cluster"
                    hypervisorType = "KVM"

                    kvm {
                        name = "kvm"
                        managementIp = "localhost"
                        username = "root"
                        password = "password"
                    }

                    attachPrimaryStorage("nfs")
                    attachL2Network("l2")
                }

                nfsPrimaryStorage {
                    name = "nfs"
                    url = "localhost:/nfs"
                }

                l2NoVlanNetwork {
                    name = "l2"
                    physicalInterface = "eth0"

                    l3Network {
                        name = "l3"

                        service {
                            provider = VirtualRouterConstant.PROVIDER_TYPE
                            types = [NetworkServiceType.DHCP.toString(), NetworkServiceType.DNS.toString()]
                        }

                        service {
                            provider = SecurityGroupConstant.SECURITY_GROUP_PROVIDER_TYPE
                            types = [SecurityGroupConstant.SECURITY_GROUP_NETWORK_SERVICE_TYPE]
                        }

                        ip {
                            startIp = "192.168.100.10"
                            endIp = "192.168.100.100"
                            netmask = "255.255.255.0"
                            gateway = "192.168.100.1"
                        }
                    }

                    l3Network {
                        name = "pubL3"

                        ip {
                            startIp = "12.16.10.10"
                            endIp = "12.16.10.100"
                            netmask = "255.255.255.0"
                            gateway = "12.16.10.1"
                        }
                    }
                }

                virtualRouterOffering {
                    name = "vr"
                    memory = SizeUnit.MEGABYTE.toByte(512)
                    cpu = 2
                    useManagementL3Network("pubL3")
                    usePublicL3Network("pubL3")
                    useImage("vr")
                }

                attachBackupStorage("sftp")
            }

            vm {
                name = "vm"
                useInstanceOffering("instanceOffering")
                useImage("image1")
                useL3Networks("l3")
                useRootDiskOffering("diskOffering")
                useHost("kvm")
            }
        }
    }

    @Override
    void test() {
        env.create {
            dbf = bean(DatabaseFacade.class)
            testSyncVolumeSizeAdjustsCapacity()
        }
    }

    /**
     * Core test: when APISyncVolumeSizeMsg returns a larger size,
     * the primary storage availableCapacity should decrease accordingly.
     *
     * Before fix: availableCapacity stayed the same after sync
     * (causing drift between incremental tracking and recalculation).
     * After fix: availableCapacity is properly adjusted.
     */
    void testSyncVolumeSizeAdjustsCapacity() {
        PrimaryStorageInventory ps = env.inventoryByName("nfs")
        VmInstanceInventory vm = env.inventoryByName("vm")
        String rootVolumeUuid = vm.rootVolumeUuid

        long sizeIncrease = SizeUnit.GIGABYTE.toByte(10)

        // Step 1: Baseline — reconnect to establish consistent capacity
        reconnectPrimaryStorage {
            uuid = ps.uuid
        }

        VolumeVO volBefore = dbf.findByUuid(rootVolumeUuid, VolumeVO.class)
        long initialSize = volBefore.getSize()
        long newSize = initialSize + sizeIncrease

        GetPrimaryStorageCapacityResult baseline = getPrimaryStorageCapacity {
            primaryStorageUuids = [ps.uuid]
        }

        PrimaryStorageCapacityVO capBefore = dbf.findByUuid(ps.uuid, PrimaryStorageCapacityVO.class)
        logger.info("BASELINE: vol.size=${initialSize}, ps.available=${capBefore.availableCapacity}")

        // Step 2: hijack the NFS agent response to report larger volume size
        env.hijackSimulator(NfsPrimaryStorageKVMBackend.GET_VOLUME_SIZE_PATH) { rsp ->
            rsp.size = newSize
            rsp.actualSize = newSize
            return rsp
        }

        // Step 3: Trigger volume size sync — agent reports larger size
        syncVolumeSize {
            uuid = rootVolumeUuid
        }

        // Verify volume size was updated in DB
        VolumeVO volAfter = dbf.findByUuid(rootVolumeUuid, VolumeVO.class)
        assert volAfter.getSize() == newSize :
                "syncVolumeSize should update VolumeVO.size to the value from agent. " +
                "expected=${newSize}, actual=${volAfter.getSize()}"

        PrimaryStorageCapacityVO capAfter = dbf.findByUuid(ps.uuid, PrimaryStorageCapacityVO.class)
        logger.info("AFTER SYNC: vol.size=${volAfter.getSize()}, ps.available=${capAfter.availableCapacity}")
        logger.info("DIFF: sizeIncrease=${sizeIncrease}, capacityDrop=${capBefore.availableCapacity - capAfter.availableCapacity}")

        // Core assertion: availableCapacity must decrease by the size increase
        // Before fix: capAfter.availableCapacity == capBefore.availableCapacity (no adjustment)
        // After fix: capAfter.availableCapacity == capBefore.availableCapacity - sizeIncrease
        assert capAfter.availableCapacity < capBefore.availableCapacity :
                "ZSTAC-80937: availableCapacity should decrease when volume size increases via syncVolumeSize. " +
                "before=${capBefore.availableCapacity}, after=${capAfter.availableCapacity}, " +
                "sizeIncrease=${sizeIncrease}"

        assert capAfter.availableCapacity == capBefore.availableCapacity - sizeIncrease :
                "ZSTAC-80937: availableCapacity should decrease by exactly the size increase. " +
                "expected=${capBefore.availableCapacity - sizeIncrease}, actual=${capAfter.availableCapacity}"

        logger.info("VERIFIED: capacity dropped from ${capBefore.availableCapacity} to ${capAfter.availableCapacity} (delta=${sizeIncrease})")
    }
}
