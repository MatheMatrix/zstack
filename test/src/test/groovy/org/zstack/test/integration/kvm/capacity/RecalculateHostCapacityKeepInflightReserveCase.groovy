package org.zstack.test.integration.kvm.capacity

import org.zstack.core.cloudbus.CloudBus
import org.zstack.core.db.DatabaseFacade
import org.zstack.core.db.Q
import org.zstack.core.db.SQL
import org.zstack.header.allocator.HostAllocatorConstant
import org.zstack.header.allocator.HostCapacityVO
import org.zstack.header.allocator.HostCapacityVO_
import org.zstack.header.host.RecalculateHostCapacityMsg
import org.zstack.header.vm.VmInstanceState
import org.zstack.header.vm.VmInstanceVO
import org.zstack.header.vm.VmInstanceVO_
import org.zstack.header.vm.VmSchedHistoryVO
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
    DatabaseFacade dbf

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
                    kvm {
                        name = "kvm2"
                        managementIp = "127.0.0.2"
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
            dbf = bean(DatabaseFacade.class)
            recalcMustKeepInflightMigrateReserve()
        }
    }

    void recalcMustKeepInflightMigrateReserve() {
        InstanceOfferingInventory offering = env.inventoryByName("instanceOffering")
        ImageInventory image = env.inventoryByName("image")
        L3NetworkInventory l3 = env.inventoryByName("l3")
        HostInventory destHost = env.inventoryByName("kvm")
        HostInventory otherHost = env.inventoryByName("kvm2")

        VmInstanceInventory vm = createVmInstance {
            name = "vm"
            instanceOfferingUuid = offering.uuid
            imageUuid = image.uuid
            l3NetworkUuids = [l3.uuid]
        }

        long inflightReserve = SizeUnit.GIGABYTE.toByte(32)

        // a big VM is mid live-migration toward destHost: dest already reserved
        // its memory and sched-history records the dest, but VM is Migrating
        VmSchedHistoryVO sched = new VmSchedHistoryVO()
        sched.setVmInstanceUuid(vm.uuid)
        sched.setDestHostUuid(destHost.uuid)
        dbf.persist(sched)
        SQL.New(VmInstanceVO.class)
                .eq(VmInstanceVO_.uuid, vm.uuid)
                .set(VmInstanceVO_.state, VmInstanceState.Migrating)
                .update()

        long destAvail = avail(destHost.uuid)
        SQL.New(HostCapacityVO.class)
                .eq(HostCapacityVO_.uuid, destHost.uuid)
                .set(HostCapacityVO_.availableMemory, destAvail - inflightReserve)
                .update()
        // otherHost is unrelated: pretend recalc would correct an under-count
        long otherTotal = Q.New(HostCapacityVO.class)
                .eq(HostCapacityVO_.uuid, otherHost.uuid)
                .select(HostCapacityVO_.totalMemory).findValue()
        SQL.New(HostCapacityVO.class)
                .eq(HostCapacityVO_.uuid, otherHost.uuid)
                .set(HostCapacityVO_.availableMemory, otherTotal - inflightReserve)
                .update()

        recalcZone()

        // dest host: reservation kept, not raised back to total
        assert avail(destHost.uuid) <= destAvail - inflightReserve
        // unrelated host: recalc restores its true available memory
        assert avail(otherHost.uuid) == otherTotal

        // migration finished: VM landed, no longer frozen, recalc restores dest
        SQL.New(VmInstanceVO.class)
                .eq(VmInstanceVO_.uuid, vm.uuid)
                .set(VmInstanceVO_.state, VmInstanceState.Running)
                .update()
        recalcZone()
        assert avail(destHost.uuid) == destAvail
    }

    private long avail(String hostUuid) {
        return Q.New(HostCapacityVO.class)
                .eq(HostCapacityVO_.uuid, hostUuid)
                .select(HostCapacityVO_.availableMemory).findValue()
    }

    private void recalcZone() {
        RecalculateHostCapacityMsg msg = new RecalculateHostCapacityMsg()
        msg.setZoneUuid(env.inventoryByName("zone").uuid)
        bus.makeLocalServiceId(msg, HostAllocatorConstant.SERVICE_ID)
        bus.call(msg)
    }

    @Override
    void clean() {
        env.delete()
    }
}
