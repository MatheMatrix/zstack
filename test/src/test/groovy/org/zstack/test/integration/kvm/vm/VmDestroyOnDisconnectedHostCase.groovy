package org.zstack.test.integration.kvm.vm

import org.zstack.core.db.DatabaseFacade
import org.zstack.header.host.HostStatus
import org.zstack.header.host.HostVO
import org.zstack.header.vm.VmCreationStrategy
import org.zstack.header.vm.VmInstanceState
import org.zstack.header.vm.VmInstanceVO
import org.zstack.sdk.VmInstanceInventory
import org.zstack.test.integration.kvm.Env
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.HostSpec
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
        DatabaseFacade dbf = bean(DatabaseFacade.class)
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
            strategy = VmCreationStrategy.JustCreate
        } as VmInstanceInventory

        VmInstanceVO vvo = dbf.findByUuid(vm.uuid, VmInstanceVO.class)
        vvo.hostUuid = hostUuid
        vvo.clusterUuid = hostSpec.inventory.clusterUuid
        dbf.updateAndRefresh(vvo)

        HostVO hvo = dbf.findByUuid(hostUuid, HostVO.class)
        hvo.status = HostStatus.Disconnected
        dbf.updateAndRefresh(hvo)
        assert dbf.findByUuid(hostUuid, HostVO.class).status == HostStatus.Disconnected

        boolean apiFailed = false
        try {
            destroyVmInstance {
                uuid = vm.uuid
            }
        } catch (Exception e) {
            apiFailed = true
        }
        assert apiFailed : "destroyVmInstance should fail when host is Disconnected"

        VmInstanceVO after = dbf.findByUuid(vm.uuid, VmInstanceVO.class)
        assert after != null : "VM should still exist after failed destroy"
        assert after.state == VmInstanceState.Created : "VM state should be Created, but was ${after.state}"
        assert after.hostUuid == hostUuid : "VM hostUuid should be preserved, but was ${after.hostUuid}"
    }

    void testRestoreOriginalStateOnDisconnectedHost() {
        DatabaseFacade dbf = bean(DatabaseFacade.class)
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
            strategy = VmCreationStrategy.JustCreate
        } as VmInstanceInventory

        VmInstanceVO vvo = dbf.findByUuid(vm.uuid, VmInstanceVO.class)
        vvo.hostUuid = hostUuid
        vvo.lastHostUuid = "previous-host-uuid-value"
        vvo.clusterUuid = hostSpec.inventory.clusterUuid
        dbf.updateAndRefresh(vvo)

        VmInstanceState origState = vvo.state
        String origHostUuid = vvo.hostUuid
        String origLastHostUuid = vvo.lastHostUuid

        HostVO hvo = dbf.findByUuid(hostUuid, HostVO.class)
        hvo.status = HostStatus.Disconnected
        dbf.updateAndRefresh(hvo)

        try {
            destroyVmInstance {
                uuid = vm.uuid
            }
        } catch (Exception ignored) {
        }

        VmInstanceVO after = dbf.findByUuid(vm.uuid, VmInstanceVO.class)
        assert after != null : "VM should not be deleted"
        assert after.state == origState : "VM state should be ${origState}, but was ${after.state}"
        assert after.hostUuid == origHostUuid : "hostUuid should be ${origHostUuid}, but was ${after.hostUuid}"
        assert after.lastHostUuid == origLastHostUuid : "lastHostUuid should be ${origLastHostUuid}, but was ${after.lastHostUuid}"
    }
}
