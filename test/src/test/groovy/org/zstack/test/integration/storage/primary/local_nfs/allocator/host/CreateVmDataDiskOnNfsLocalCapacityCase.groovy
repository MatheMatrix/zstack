package org.zstack.test.integration.storage.primary.local_nfs.allocator.host

import org.zstack.compute.vm.VmSystemTags
import org.zstack.sdk.*
import org.zstack.test.integration.storage.StorageTest
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.SubCase
import org.zstack.utils.data.SizeUnit

/**
 * Regression: mixed storage. Root volume on local PS (fits), data volumes on NFS.
 * Local PS host capacity only fits root; LocalStorageAllocatorFactory must NOT
 * sum root+data (200G) against local host ref, otherwise NO_AVAILABLE_HOST is
 * wrongly raised. See "no hosts with enough disk capacity[214748364800 bytes]".
 */
class CreateVmDataDiskOnNfsLocalCapacityCase extends SubCase {
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
                memory = SizeUnit.GIGABYTE.toByte(1)
                cpu = 1
            }

            diskOffering {
                name = "rootDisk"
                diskSize = SizeUnit.GIGABYTE.toByte(40)
            }

            diskOffering {
                name = "dataDisk"
                diskSize = SizeUnit.GIGABYTE.toByte(80)
            }

            sftpBackupStorage {
                name = "sftp"
                url = "/sftp"
                username = "root"
                password = "password"
                hostname = "localhost"

                image {
                    name = "image"
                    url = "http://zstack.org/download/test.qcow2"
                    format = "raw"
                    mediaType = "ISO"
                    size = 0
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
                        totalCpu = 88
                        totalMem = SizeUnit.GIGABYTE.toByte(100)
                    }

                    attachPrimaryStorage("local")
                    attachPrimaryStorage("nfs")
                    attachL2Network("l2")
                }

                nfsPrimaryStorage {
                    name = "nfs"
                    url = "172.20.0.1:/nfs_root"
                    totalCapacity = SizeUnit.GIGABYTE.toByte(1000)
                    availableCapacity = SizeUnit.GIGABYTE.toByte(1000)
                }

                localPrimaryStorage {
                    name = "local"
                    url = "/local_ps"
                    totalCapacity = SizeUnit.GIGABYTE.toByte(50)
                    availableCapacity = SizeUnit.GIGABYTE.toByte(50)
                }

                l2NoVlanNetwork {
                    name = "l2"
                    physicalInterface = "eth0"

                    l3Network {
                        name = "l3"

                        ip {
                            startIp = "12.16.10.10"
                            endIp = "12.16.10.100"
                            netmask = "255.255.255.0"
                            gateway = "12.16.10.1"
                        }
                    }
                }

                attachBackupStorage("sftp")
            }
        }
    }

    @Override
    void test() {
        env.create {
            testCreateVmRootLocalDataNfs()
        }
    }

    @Override
    void clean() {
        env.delete()
    }

    void testCreateVmRootLocalDataNfs() {
        InstanceOfferingInventory instanceOffering = env.inventoryByName("instanceOffering") as InstanceOfferingInventory
        DiskOfferingInventory rootDisk = env.inventoryByName("rootDisk") as DiskOfferingInventory
        DiskOfferingInventory dataDisk = env.inventoryByName("dataDisk") as DiskOfferingInventory
        ImageInventory image = env.inventoryByName("image") as ImageInventory
        L3NetworkInventory l3 = env.inventoryByName("l3") as L3NetworkInventory
        HostInventory host = env.inventoryByName("kvm")
        PrimaryStorageInventory nfs = env.inventoryByName("nfs")
        PrimaryStorageInventory local = env.inventoryByName("local")

        CreateVmInstanceAction a = new CreateVmInstanceAction(
                name: "mixed",
                instanceOfferingUuid: instanceOffering.uuid,
                imageUuid: image.uuid,
                l3NetworkUuids: [l3.uuid],
                hostUuid: host.uuid,
                rootDiskOfferingUuid: rootDisk.uuid,
                dataDiskOfferingUuids: [dataDisk.uuid, dataDisk.uuid],
                primaryStorageUuidForRootVolume: local.uuid,
                systemTags: [VmSystemTags.PRIMARY_STORAGE_UUID_FOR_DATA_VOLUME.instantiateTag(
                        [(VmSystemTags.PRIMARY_STORAGE_UUID_FOR_DATA_VOLUME_TOKEN): nfs.uuid])],
                sessionId: currentEnvSpec.session.uuid
        )

        CreateVmInstanceAction.Result r = a.call()
        assert r.error == null:
                "root(40G) on local(50G), data(160G) on nfs: local capacity check wrongly summed root+data " +
                "and raised NO_AVAILABLE_HOST. error=${r.error}"
    }
}
