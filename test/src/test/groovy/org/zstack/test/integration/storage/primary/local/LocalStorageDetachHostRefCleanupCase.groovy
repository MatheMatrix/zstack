package org.zstack.test.integration.storage.primary.local

import org.zstack.core.db.Q
import org.zstack.header.storage.primary.PrimaryStorageHostRefVO
import org.zstack.header.storage.primary.PrimaryStorageHostRefVO_
import org.zstack.sdk.ClusterInventory
import org.zstack.sdk.HostInventory
import org.zstack.sdk.PrimaryStorageInventory
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.SubCase
import org.zstack.testlib.Test
import org.zstack.utils.data.SizeUnit

class LocalStorageDetachHostRefCleanupCase extends SubCase {
    EnvSpec env

    @Override
    void clean() {
        env.delete()
    }

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
        env = Test.makeEnv {
            instanceOffering {
                name = "instanceOffering"
                memory = SizeUnit.GIGABYTE.toByte(8)
                cpu = 4
            }

            diskOffering {
                name = 'diskOffering'
                diskSize = SizeUnit.GIGABYTE.toByte(10)
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
                        name = "kvm1"
                        managementIp = "localhost"
                        username = "root"
                        password = "password"
                    }

                    kvm {
                        name = "kvm2"
                        managementIp = "127.0.0.2"
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

                        service {
                            provider = "VirtualRouter"
                            types = ["DHCP", "DNS"]
                        }

                        service {
                            provider = "SecurityGroup"
                            types = ["SecurityGroup"]
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
        }
    }

    @Override
    void test() {
        env.create {
            testDetachCleansUpHostRefVO()
        }
    }

    void testDetachCleansUpHostRefVO() {
        PrimaryStorageInventory ps = env.inventoryByName("local")
        ClusterInventory cluster = env.inventoryByName("cluster")
        HostInventory kvm1 = env.inventoryByName("kvm1")
        HostInventory kvm2 = env.inventoryByName("kvm2")

        List<String> hostUuids = [kvm1.uuid, kvm2.uuid]

        long before = Q.New(PrimaryStorageHostRefVO.class)
                .eq(PrimaryStorageHostRefVO_.primaryStorageUuid, ps.uuid)
                .in(PrimaryStorageHostRefVO_.hostUuid, hostUuids)
                .count()
        assert before == 2

        detachPrimaryStorageFromCluster {
            primaryStorageUuid = ps.uuid
            clusterUuid = cluster.uuid
        }

        long after = Q.New(PrimaryStorageHostRefVO.class)
                .eq(PrimaryStorageHostRefVO_.primaryStorageUuid, ps.uuid)
                .in(PrimaryStorageHostRefVO_.hostUuid, hostUuids)
                .count()
        assert after == 0
    }
}
