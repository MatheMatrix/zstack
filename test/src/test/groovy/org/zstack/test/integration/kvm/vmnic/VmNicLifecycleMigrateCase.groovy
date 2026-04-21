package org.zstack.test.integration.kvm.vmnic

import org.zstack.header.host.HostInventory
import org.zstack.header.vm.VmInstanceState
import org.zstack.header.vm.VmInstanceVO
import org.zstack.sdk.VmInstanceInventory
import org.zstack.test.compute.vmnic.TestVmNicLifecycleExtension
import org.zstack.test.integration.kvm.KvmTest
import org.zstack.test.integration.kvm.vm.migrate.VmMigrateEnv
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.SubCase

/**
 * PH-5 integration test: covers TP-032 + TP-033.
 *
 * TP-032: full migrate (pre -> post) routes preMigrate (dst) +
 *         postMigrate (src) through VmNicLifecycleManager.
 * TP-033: migrate failure at preMigrate -> routes failedMigrate (dst)
 *         and does NOT call postMigrate.
 */
class VmNicLifecycleMigrateCase extends SubCase {
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
        env = VmMigrateEnv.oneVmThreeHostsLocalStorage()
    }

    @Override
    void test() {
        env.create {
            testMigrateRoutesPreAndPost()
            testMigrateFailRoutesFailedMigrate()
        }
    }

    void testMigrateRoutesPreAndPost() {
        TestVmNicLifecycleExtension ext = bean(TestVmNicLifecycleExtension.class)
        ext.reset()
        ext.setSetupError(null)
        ext.setPreMigrateError(null)

        VmInstanceInventory vm = env.inventoryByName("vm") as VmInstanceInventory
        HostInventory destHost = env.inventoryByName("kvm2") as HostInventory
        String srcHostUuid = vm.hostUuid

        migrateVm {
            vmInstanceUuid = vm.uuid
            hostUuid = destHost.uuid
        }

        retryInSecs {
            VmInstanceVO vo = dbFindByUuid(vm.uuid, VmInstanceVO.class)
            assert vo.state == VmInstanceState.Running
            assert vo.hostUuid == destHost.uuid
        }

        def pre = ext.callsOf(TestVmNicLifecycleExtension.Op.PRE_MIGRATE)
        def post = ext.callsOf(TestVmNicLifecycleExtension.Op.POST_MIGRATE)
        assert !pre.isEmpty()
        assert !post.isEmpty()
        assert pre.last().hostUuid == destHost.uuid
        assert post.last().hostUuid == srcHostUuid
    }

    void testMigrateFailRoutesFailedMigrate() {
        TestVmNicLifecycleExtension ext = bean(TestVmNicLifecycleExtension.class)
        ext.reset()
        ext.setPreMigrateError(org.zstack.core.Platform.operr("injected preMigrate failure"))

        VmInstanceInventory vm = env.inventoryByName("vm") as VmInstanceInventory
        HostInventory destHost = env.inventoryByName("kvm1") as HostInventory

        expect(AssertionError.class) {
            migrateVm {
                vmInstanceUuid = vm.uuid
                hostUuid = destHost.uuid
            }
        }

        def failed = ext.callsOf(TestVmNicLifecycleExtension.Op.FAILED_MIGRATE)
        def post = ext.callsOf(TestVmNicLifecycleExtension.Op.POST_MIGRATE)
        assert !failed.isEmpty()
        assert post.isEmpty()
    }
}
