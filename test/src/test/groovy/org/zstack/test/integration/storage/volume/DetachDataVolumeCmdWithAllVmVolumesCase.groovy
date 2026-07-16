package org.zstack.test.integration.storage.volume

import org.springframework.http.HttpEntity
import org.zstack.header.image.ImagePlatform
import org.zstack.kvm.KVMConstant
import org.zstack.sdk.DiskOfferingInventory
import org.zstack.sdk.VmInstanceInventory
import org.zstack.sdk.VolumeInventory
import org.zstack.test.integration.storage.Env
import org.zstack.test.integration.storage.StorageTest
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.SubCase

class DetachDataVolumeCmdWithAllVmVolumesCase extends SubCase {
    EnvSpec env

    @Override
    void setup() {
        useSpring(StorageTest.springSpec)
    }

    @Override
    void environment() {
        env = Env.localStorageOneVmWithOneDataVolumeEnv()
    }

    @Override
    void test() {
        env.create {
            testDetachCommandContainsAllVmVolumes()
        }
    }

    void testDetachCommandContainsAllVmVolumes() {
        VmInstanceInventory vm = env.inventoryByName("vm") as VmInstanceInventory
        vm = queryVmInstance {
            conditions = ["uuid=${vm.uuid}"]
        }[0] as VmInstanceInventory

        VolumeInventory target = vm.allVolumes.find {
            it.uuid != vm.rootVolumeUuid
        } as VolumeInventory
        assert target != null

        DiskOfferingInventory diskOffering =
                env.inventoryByName("diskOffering") as DiskOfferingInventory
        VolumeInventory otherData = createDataVolume {
            name = "other-data-volume"
            diskOfferingUuid = diskOffering.uuid
        } as VolumeInventory

        attachDataVolumeToVm {
            volumeUuid = otherData.uuid
            vmInstanceUuid = vm.uuid
        }

        updateVmInstance {
            uuid = vm.uuid
            platform = ImagePlatform.Other.toString()
        }

        VmInstanceInventory vmBeforeDetach = queryVmInstance {
            conditions = ["uuid=${vm.uuid}"]
        }[0] as VmInstanceInventory

        List<String> expectedVolumeUuids =
                vmBeforeDetach.allVolumes.collect { it.uuid as String }.sort()
        List<String> requiredVolumeUuids =
                [vmBeforeDetach.rootVolumeUuid, target.uuid, otherData.uuid].sort()
        assert expectedVolumeUuids == requiredVolumeUuids

        Map detachCmd
        env.afterSimulator(KVMConstant.KVM_DETACH_VOLUME) { rsp, HttpEntity<String> e ->
            detachCmd = json(e.body, LinkedHashMap.class)
            return rsp
        }

        detachDataVolumeFromVm {
            uuid = target.uuid
        }

        assert detachCmd != null
        assert detachCmd.volume?.volumeUuid == target.uuid

        List expectedVolumes = (detachCmd.expectedVolumes ?: []) as List
        List<String> actualExpectedVolumeUuids = expectedVolumes
                        .collect { it.volumeUuid as String }
                        .sort()
        assert actualExpectedVolumeUuids == expectedVolumeUuids
        assert expectedVolumes.every { it.useVirtio == false }
    }

    @Override
    void clean() {
        env.delete()
    }
}
