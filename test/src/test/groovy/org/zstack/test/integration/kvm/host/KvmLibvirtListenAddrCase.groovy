package org.zstack.test.integration.kvm.host

import org.zstack.kvm.KVMSystemTags
import org.zstack.sdk.HostInventory
import org.zstack.test.integration.storage.StorageTest
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.SubCase
import org.zstack.utils.data.SizeUnit

import static org.zstack.kvm.KVMConstant.*
import static org.zstack.kvm.KVMAgentCommands.*
import static org.zstack.utils.CollectionDSL.e
import static org.zstack.utils.CollectionDSL.map

class KvmLibvirtListenAddrCase extends SubCase {
    EnvSpec env
    def libvirtVersion = "4.9.0"
    def qemuImgVersion = "4.2.0"

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
                        name = "host1"
                        managementIp = "192.168.1.100"
                        username = "root"
                        password = "password"
                        systemTags = [
                                KVMSystemTags.QEMU_IMG_VERSION.instantiateTag(map(e(KVMSystemTags.QEMU_IMG_VERSION_TOKEN, qemuImgVersion))),
                                KVMSystemTags.LIBVIRT_VERSION.instantiateTag(map(e(KVMSystemTags.LIBVIRT_VERSION_TOKEN, libvirtVersion))),
                        ]
                    }

                    attachPrimaryStorage("nfs")
                    attachL2Network("l2")
                }

                nfsPrimaryStorage {
                    name = "nfs"
                    url = "localhost:/nfs_ps"
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
        }
    }

    @Override
    void test() {
        env.create {
            testConnectCmdContainsLibvirtListenAddr()
        }
    }

    void testConnectCmdContainsLibvirtListenAddr() {
        HostInventory host1 = env.inventoryByName("host1") as HostInventory

        ConnectCmd connectCmd = null
        env.afterSimulator(KVM_CONNECT_PATH) { ConnectResponse rsp, HttpEntity<String> e ->
            connectCmd = json(e.body, ConnectCmd.class)
            rsp.libvirtVersion = libvirtVersion
            rsp.qemuVersion = qemuImgVersion
            return rsp
        }

        reconnectHost {
            uuid = host1.uuid
        }

        assert connectCmd != null : "ConnectCmd should have been captured"
        assert connectCmd.libvirtListenAddr != null : "libvirtListenAddr should be set in ConnectCmd"
        assert connectCmd.libvirtListenAddr == host1.managementIp :
                "libvirtListenAddr should be the host management IP, " +
                "expected: ${host1.managementIp}, actual: ${connectCmd.libvirtListenAddr}"
    }
}
