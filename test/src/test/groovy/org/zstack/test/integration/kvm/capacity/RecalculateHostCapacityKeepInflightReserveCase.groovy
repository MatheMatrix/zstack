package org.zstack.test.integration.kvm.capacity

import org.zstack.core.cloudbus.CloudBus
import org.zstack.core.db.Q
import org.zstack.core.db.SQL
import org.zstack.header.allocator.HostAllocatorConstant
import org.zstack.header.allocator.HostCapacityVO
import org.zstack.header.allocator.HostCapacityVO_
import org.zstack.header.host.RecalculateHostCapacityMsg
import org.zstack.header.vm.VmInstanceState
import org.zstack.header.vm.VmInstanceVO
import org.zstack.header.vm.VmInstanceVO_
import org.zstack.sdk.HostInventory
import org.zstack.sdk.ImageInventory
import org.zstack.sdk.InstanceOfferingInventory
import org.zstack.sdk.L3NetworkInventory
import org.zstack.sdk.VmInstanceInventory
import org.zstack.test.integration.kvm.KvmTest
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.SubCase
import org.zstack.utils.data.SizeUnit

class RecalculateHostCapacityKeepInflightReserveCase extends SubCase {
    EnvSpec env
    CloudBus bus

    @Override
    void setup() {
        useSpring(KvmTest.springSpec)
    }

    @Override
    void environment() {
        env = env {
            instanceOffering {
                name = "instanceOffering"
                memory = SizeUnit.GIGABYTE.toByte(4)
                cpu = 2
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
                cluster {
                    name = "cluster"
                    hypervisorType = "KVM"
                    kvm {
                        name = "kvm"
                        managementIp = "127.0.0.1"
                        username = "root"
                        password = "password"
                        totalMem = SizeUnit.GIGABYTE.toByte(64)
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
        }
    }

    @Override
    void test() {
        env.create {
            bus = bean(CloudBus.class)
            recalcMustKeepInflightMigrateReserve()
        }
    }

    void recalcMustKeepInflightMigrateReserve() {
        InstanceOfferingInventory offering = env.inventoryByName("instanceOffering")
        ImageInventory image = env.inventoryByName("image")
        L3NetworkInventory l3 = env.inventoryByName("l3")
        HostInventory host = env.inventoryByName("kvm")

        VmInstanceInventory vm = createVmInstance {
            name = "vm"
            instanceOfferingUuid = offering.uuid
            imageUuid = image.uuid
            l3NetworkUuids = [l3.uuid]
        }

        long inflightReserve = SizeUnit.GIGABYTE.toByte(32)
        long availBefore = Q.New(HostCapacityVO.class)
                .eq(HostCapacityVO_.uuid, host.uuid)
                .select(HostCapacityVO_.availableMemory).findValue()

        // simulate a big VM mid-migration: dest host already reserved its memory,
        // but VM row still belongs to the source host (not yet running here)
        SQL.New(HostCapacityVO.class)
                .eq(HostCapacityVO_.uuid, host.uuid)
                .set(HostCapacityVO_.availableMemory, availBefore - inflightReserve)
                .update()
        SQL.New(VmInstanceVO.class)
                .eq(VmInstanceVO_.uuid, vm.uuid)
                .set(VmInstanceVO_.state, VmInstanceState.Migrating)
                .update()

        RecalculateHostCapacityMsg msg = new RecalculateHostCapacityMsg()
        msg.setHostUuid(host.uuid)
        bus.makeLocalServiceId(msg, HostAllocatorConstant.SERVICE_ID)
        bus.call(msg)

        long availAfter = Q.New(HostCapacityVO.class)
                .eq(HostCapacityVO_.uuid, host.uuid)
                .select(HostCapacityVO_.availableMemory).findValue()

        assert availAfter <= availBefore - inflightReserve
    }

    @Override
    void clean() {
        env.delete()
    }
}
