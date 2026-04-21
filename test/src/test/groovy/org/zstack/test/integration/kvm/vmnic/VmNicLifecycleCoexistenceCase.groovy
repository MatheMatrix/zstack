package org.zstack.test.integration.kvm.vmnic

import org.zstack.sdk.VmInstanceInventory
import org.zstack.test.compute.vmnic.TestVmNicLifecycleExtension
import org.zstack.test.integration.kvm.Env
import org.zstack.test.integration.kvm.KvmTest
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.SubCase

/**
 * PH-5 integration test: covers TP-049 + TP-050 + F-009.
 *
 * Verifies zero-invasion coexistence:
 *   - when the extension is applicable it gets invoked,
 *   - when the extension opts out via isApplicable=false the rest of the
 *     VM lifecycle (including pre-existing backends like OvsKvmBackend)
 *     is entirely unaffected.
 *
 * This is a smoke test relying on the fact that the oneVmBasicEnv VM
 * successfully completes start / stop regardless of the new framework.
 */
class VmNicLifecycleCoexistenceCase extends SubCase {
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
            testApplicableCoexistsWithLegacyPath()
            testNotApplicableIsTransparent()
        }
    }

    void testApplicableCoexistsWithLegacyPath() {
        TestVmNicLifecycleExtension ext = bean(TestVmNicLifecycleExtension.class)
        ext.reset()
        ext.setApplicable(true)

        VmInstanceInventory vm = env.inventoryByName("vm") as VmInstanceInventory
        stopVmInstance { uuid = vm.uuid }
        startVmInstance { uuid = vm.uuid }

        assert !ext.callsOf(TestVmNicLifecycleExtension.Op.CLEANUP).isEmpty()
        assert !ext.callsOf(TestVmNicLifecycleExtension.Op.SETUP).isEmpty()
    }

    void testNotApplicableIsTransparent() {
        TestVmNicLifecycleExtension ext = bean(TestVmNicLifecycleExtension.class)
        ext.reset()
        ext.setApplicable(false)

        VmInstanceInventory vm = env.inventoryByName("vm") as VmInstanceInventory
        stopVmInstance { uuid = vm.uuid }
        startVmInstance { uuid = vm.uuid }

        assert ext.callsOf(TestVmNicLifecycleExtension.Op.CLEANUP).isEmpty()
        assert ext.callsOf(TestVmNicLifecycleExtension.Op.SETUP).isEmpty()
    }
}
