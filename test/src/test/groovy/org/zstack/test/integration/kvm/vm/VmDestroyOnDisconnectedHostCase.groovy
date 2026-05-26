package org.zstack.test.integration.kvm.vm

import org.zstack.core.db.SQL
import org.zstack.header.host.HostStatus
import org.zstack.header.host.HostVO
import org.zstack.header.host.HostVO_
import org.zstack.header.vm.VmCreationStrategy
import org.zstack.header.vm.VmInstanceState
import org.zstack.sdk.VmInstanceInventory
import org.zstack.test.integration.kvm.Env
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.HostSpec
import org.zstack.testlib.ImageSpec
import org.zstack.testlib.InstanceOfferingSpec
import org.zstack.testlib.L3NetworkSpec
import org.zstack.testlib.SubCase

class VmDestroyOnDisconnectedHostCase extends SubCase {
    EnvSpec env

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
        env = Env.oneVmBasicEnv()
    }

    @Override
    void test() {
        env.create {
            testDestroyVmOnDisconnectedHostRejected()
            testRestoreOriginalStateOnDisconnectedHost()
        }
    }

    @Override
    void clean() {
        env.delete()
    }

    void testDestroyVmOnDisconnectedHostRejected() {
        HostSpec hostSpec = env.specByName("kvm") as HostSpec
        String hostUuid = hostSpec.inventory.uuid

        def image = env.specByName("image1") as ImageSpec
        def offering = env.specByName("instanceOffering") as InstanceOfferingSpec
        def l3network = env.specByName("pubL3") as L3NetworkSpec

        def vm = createVmInstance {
            name = "vm-disconnect-test-1"
            instanceOfferingUuid = offering.inventory.uuid
            imageUuid = image.inventory.uuid
            l3NetworkUuids = [l3network.inventory.uuid]
            hostUuid = hostUuid
            strategy = VmCreationStrategy.JustCreate
        } as VmInstanceInventory

        SQL.New(HostVO.class).eq(HostVO_.uuid, hostUuid)
                .set(HostVO_.status, HostStatus.Disconnected).update()

        boolean apiFailed = false
        try {
            destroyVmInstance {
                uuid = vm.uuid
            }
        } catch (Exception e) {
            apiFailed = true
        }
        assert apiFailed : "destroyVmInstance should fail when host is Disconnected"

        VmInstanceInventory after = queryVmInstance {
            conditions = ["uuid=${vm.uuid}".toString()]
        }[0]
        assert after != null : "VM should still exist after failed destroy"
        assert after.state == VmInstanceState.Created.toString()
                : "VM state should be Created, but was ${after.state}"
        assert after.hostUuid == hostUuid
                : "VM hostUuid should be preserved, but was ${after.hostUuid}"
    }

    void testRestoreOriginalStateOnDisconnectedHost() {
        HostSpec hostSpec = env.specByName("kvm") as HostSpec
        String hostUuid = hostSpec.inventory.uuid

        def image = env.specByName("image1") as ImageSpec
        def offering = env.specByName("instanceOffering") as InstanceOfferingSpec
        def l3network = env.specByName("pubL3") as L3NetworkSpec

        def vm = createVmInstance {
            name = "vm-disconnect-test-2"
            instanceOfferingUuid = offering.inventory.uuid
            imageUuid = image.inventory.uuid
            l3NetworkUuids = [l3network.inventory.uuid]
            hostUuid = hostUuid
            strategy = VmCreationStrategy.JustCreate
        } as VmInstanceInventory

        VmInstanceState origState = vm.state as VmInstanceState
        String origHostUuid = vm.hostUuid
        String origLastHostUuid = vm.lastHostUuid

        SQL.New(HostVO.class).eq(HostVO_.uuid, hostUuid)
                .set(HostVO_.status, HostStatus.Disconnected).update()

        try {
            destroyVmInstance {
                uuid = vm.uuid
            }
        } catch (Exception ignored) {
        }

        VmInstanceInventory after = queryVmInstance {
            conditions = ["uuid=${vm.uuid}".toString()]
        }[0]
        assert after != null : "VM should not be deleted"
        assert after.state == origState.toString()
                : "VM state should be ${origState}, but was ${after.state}"
        assert after.hostUuid == origHostUuid
                : "hostUuid should be ${origHostUuid}, but was ${after.hostUuid}"
        assert after.lastHostUuid == origLastHostUuid
                : "lastHostUuid should be ${origLastHostUuid}, but was ${after.lastHostUuid}"
    }
}
