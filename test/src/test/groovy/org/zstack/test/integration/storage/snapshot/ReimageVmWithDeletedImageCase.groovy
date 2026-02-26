package org.zstack.test.integration.storage.snapshot

import org.zstack.core.db.Q
import org.zstack.header.image.ImageVO
import org.zstack.header.image.ImageVO_
import org.zstack.sdk.*
import org.zstack.test.integration.storage.StorageTest
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.SubCase
import org.zstack.utils.data.SizeUnit

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
                    name = "image"
                    url = "http://zstack.org/download/test.qcow2"
                }
            }

            zone {
                name = "zone"
                description = "test"

                cluster {
                    name = "cluster"
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
                name = "vm"
                useInstanceOffering("instanceOffering")
                useImage("image")
                useL3Networks("l3")
                useRootDiskOffering("diskOffering")
                useHost("kvm1")
            }
        }
    }

    @Override
    void test() {
        env.create {
            testReimageAfterOriginImageDeleted()
        }
    }

    void testReimageAfterOriginImageDeleted() {
        def vm = env.inventoryByName("vm") as VmInstanceInventory
        def image = env.inventoryByName("image") as ImageInventory

        // stop vm
        stopVmInstance {
            uuid = vm.uuid
        }

        // delete and expunge the origin image
        deleteImage {
            uuid = image.uuid
        }
        expungeImage {
            imageUuid = image.uuid
        }

        // verify image is gone
        assert !Q.New(ImageVO.class).eq(ImageVO_.uuid, image.uuid).isExists()

        // reimage should fail with RE_IMAGE_ORIGIN_IMAGE_DELETED
        expect(AssertionError.class) {
            reimageVmInstance {
                vmInstanceUuid = vm.uuid
            }
        }
    }

    @Override
    void clean() {
        env.delete()
    }
}
