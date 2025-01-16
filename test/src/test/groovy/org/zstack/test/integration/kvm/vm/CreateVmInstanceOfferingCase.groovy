package org.zstack.test.integration.kvm.vm

import org.zstack.compute.vm.VmSystemTags
import org.zstack.core.db.Q
import org.zstack.core.db.SQL
import org.zstack.header.configuration.InstanceOfferingStateEvent
import org.zstack.header.tag.SystemTagVO
import org.zstack.header.tag.SystemTagVO_
import org.zstack.sdk.*
import org.zstack.test.integration.kvm.Env
import org.zstack.test.integration.kvm.KvmTest
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.SubCase

/**
 * Created by MaJin on 2017-07-07.
 */
class CreateVmInstanceOfferingCase extends SubCase {
    EnvSpec env
    InstanceOfferingInventory userIns, vrIns
    ImageInventory img
    L3NetworkInventory l3

    @Override
    void setup() {
        useSpring(KvmTest.springSpec)
    }

    @Override
    void environment() {
        env = Env.noVmEnv()
    }

    @Override
    void test() {
        env.create {
            userIns = env.inventoryByName("instanceOffering") as InstanceOfferingInventory
            vrIns = env.inventoryByName("vr") as InstanceOfferingInventory
            img = env.inventoryByName("image1") as ImageInventory
            l3 = env.inventoryByName("l3") as L3NetworkInventory
            testDestroyMarketplaceVm()
            testCreateVmUseDisabledInstanceOffering()
            testCreateVmUseVRInstanceOffering()
        }
    }

    @Override
    void clean() {
        env.delete()
    }

    void testDestroyMarketplaceVm() {
        VmInstanceInventory markVm = createVmInstance {
            name = "marketplace"
            l3NetworkUuids = [l3.uuid]
            instanceOfferingUuid = userIns.uuid
            imageUuid = img.uuid
            systemTags = [VmSystemTags.CREATED_BY_MARKETPLACE.getTagFormat()]
        }

        assert Q.New(SystemTagVO.class).eq(SystemTagVO_.tag, VmSystemTags.CREATED_BY_MARKETPLACE.getTagFormat()).eq(SystemTagVO_.resourceUuid, markVm.uuid).count() == 1
        expect(AssertionError.class) {
            destroyVmInstance {
                uuid = markVm.uuid
            }
        }

        SQL.New(SystemTagVO.class).eq(SystemTagVO_.tag, VmSystemTags.CREATED_BY_MARKETPLACE.getTagFormat()).eq(SystemTagVO_.resourceUuid, markVm.uuid).delete()

        destroyVmInstance {
            uuid = markVm.uuid
        }

        expungeVmInstance {
            uuid = markVm.uuid
        }
    }


    void testCreateVmUseDisabledInstanceOffering() {
        changeInstanceOfferingState {
            uuid = userIns.uuid
            stateEvent = InstanceOfferingStateEvent.disable
        }
        CreateVmInstanceAction a = new CreateVmInstanceAction()
        a.name = "test"
        a.instanceOfferingUuid = userIns.uuid
        a.imageUuid = img.uuid
        a.l3NetworkUuids = [l3.uuid]
        a.sessionId = currentEnvSpec.session.uuid
        assert a.call().error != null
    }

    void testCreateVmUseVRInstanceOffering(){
        CreateVmInstanceAction a = new CreateVmInstanceAction()
        a.name = "test"
        a.instanceOfferingUuid = vrIns.uuid
        a.imageUuid = img.uuid
        a.l3NetworkUuids = [l3.uuid]
        a.sessionId = currentEnvSpec.session.uuid
        assert a.call().error != null
    }
}
