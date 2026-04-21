package org.zstack.test.integration.kvm.vmnic

import org.zstack.sdk.VmInstanceInventory
import org.zstack.test.compute.vmnic.TestVmNicLifecycleExtension
import org.zstack.test.integration.kvm.Env
import org.zstack.test.integration.kvm.KvmTest
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.SubCase

/**
 * PH-5 integration test: covers TP-020.
 *
 * Verifies that VmNicLifecycleManager routes setup / cleanup into the
 * registered VmNicLifecycleExtensionPoint over a full VM
 * Start / Stop lifecycle driven through ZStack APIs.
 */
class VmNicLifecycleStartStopCase extends SubCase {
    EnvSpec env

    @Override
    void clean() { env.delete() }

    @Override
    void setup() {
        useSpring(KvmTest.springSpec)
        spring {
            include("VmNicLifecycleExtension.xml")
        }
    }

    @Override
    void environment() {
        env = Env.oneVmBasicEnv()
    }

    @Override
    void test() {
        env.create {
            testStartStopRoutesToManager()
        }
    }

    void testStartStopRoutesToManager() {
        TestVmNicLifecycleExtension ext = bean(TestVmNicLifecycleExtension.class)
        assert ext != null
        ext.reset()

        VmInstanceInventory vm = env.inventoryByName("vm") as VmInstanceInventory

        stopVmInstance { uuid = vm.uuid }
        def afterStop = ext.callsOf(TestVmNicLifecycleExtension.Op.CLEANUP)
        assert !afterStop.isEmpty()
        assert afterStop[0].nicUuids.size() == vm.vmNics.size()

        startVmInstance { uuid = vm.uuid }
        def afterStart = ext.callsOf(TestVmNicLifecycleExtension.Op.SETUP)
        assert !afterStart.isEmpty()
        assert afterStart.last().nicUuids.size() == vm.vmNics.size()
    }
}
