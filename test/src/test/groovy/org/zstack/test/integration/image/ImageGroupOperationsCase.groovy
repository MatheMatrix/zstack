package org.zstack.test.integration.image

import org.zstack.core.db.DatabaseFacade
import org.zstack.core.db.Q
import org.zstack.header.vm.VmInstanceState
import org.zstack.header.vm.VmInstanceVO
import org.zstack.header.vm.VmInstanceVO_
import org.zstack.sdk.DiskOfferingInventory
import org.zstack.sdk.HostInventory
import org.zstack.sdk.SftpBackupStorageInventory
import org.zstack.sdk.VmInstanceInventory
import org.zstack.sdk.VolumeInventory
import org.zstack.sdk.ImageGroupInventory
import org.zstack.test.integration.ZStackTest
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.SubCase
import org.zstack.utils.data.SizeUnit

class ImageGroupOperationsCase extends SubCase {
    EnvSpec env
    DatabaseFacade dbf
    VmInstanceInventory vm
    org.zstack.sdk.BackupStorageInventory bs
    DiskOfferingInventory diskOffering
    SftpBackupStorageInventory ps
    HostInventory host

    @Override
    void setup() {
        useSpring(ZStackTest.springSpec)
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
                diskSize = SizeUnit.GIGABYTE.toByte(2)
            }

            sftpBackupStorage {
                name = "sftp"
                url = "/sftp"
                username = "username"
                password = "password"
                hostname = "hostname"

                image {
                    name = "image"
                    url = "http://somehost/boot.iso"
                    format = "iso"
                }

                image {
                    name = "image1"
                    url = "http://somehost/boot.iso"
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
                name = "vm"
                useInstanceOffering("instanceOffering")
                useImage("image1")
                useL3Networks("l3")
            }
        }
    }

    @Override
    void test() {
        env.create {
            vm = env.inventoryByName("vm") as VmInstanceInventory
            diskOffering = env.inventoryByName("diskOffering") as DiskOfferingInventory
            ps = env.inventoryByName("sftp") as SftpBackupStorageInventory
            host = env.inventoryByName("kvm") as HostInventory

            testCreateImageGroup()
        }
    }

    void testCreateImageGroup() {
        VolumeInventory volume = createDataVolume {
            name = "data"
            diskOfferingUuid = diskOffering.uuid
        } as VolumeInventory

        attachDataVolumeToVm {
            volumeUuid = volume.uuid
            vmInstanceUuid = vm.uuid
        }

        stopVmInstance {
            uuid = vm.uuid
        }
        assert Q.New(VmInstanceVO.class).eq(VmInstanceVO_.state, VmInstanceState.Stopped).eq(VmInstanceVO_.uuid, vm.uuid).isExists()

        ImageGroupInventory group = createImageGroupFromVmInstance{
            vmInstanceUuid = vm.uuid
            name = "imageGroup"
        } as ImageGroupInventory

        queryImageGroup {
        }

        queryImageGroupRef {

        }

        expungeImageGroup {
            uuid = group.uuid
        }
    }

    @Override
    void clean() {
        env.delete()
    }
}
