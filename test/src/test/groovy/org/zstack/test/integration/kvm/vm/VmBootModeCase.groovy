package org.zstack.test.integration.kvm.vm

import org.zstack.compute.vm.VmSystemTags
import org.zstack.core.db.Q
import org.zstack.header.image.ImageVO
import org.zstack.header.tag.SystemTagVO
import org.zstack.header.tag.SystemTagVO_
import org.zstack.header.vm.devices.VmInstanceDeviceAddressVO
import org.zstack.header.vm.devices.VmInstanceDeviceAddressVO_
import org.zstack.image.ImageSystemTags
import org.zstack.sdk.ImageInventory
import org.zstack.sdk.InstanceOfferingInventory
import org.zstack.sdk.L3NetworkInventory
import org.zstack.sdk.VmInstanceInventory
import org.zstack.test.integration.kvm.Env
import org.zstack.test.integration.kvm.KvmTest
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.SubCase

class VmBootModeCase extends SubCase{
    EnvSpec env

    @Override
    void clean() {
        env.delete()
    }

    @Override
    void setup() {
        useSpring(KvmTest.springSpec)
    }

    @Override
    void environment() {
        env = Env.oneVmBasicEnv()
    }

    @Override
    void test() {
        env.create {
            testCreateVmWithBootMode()
            testSetVmBootMode()
            testCreateVmWithDifferentBootModeThanImage()
            testCreateVmInheritsBootModeFromImage()
        }
    }

    void testSetVmBootMode() {
        def vm = env.inventoryByName("vm") as VmInstanceInventory

        assert Q.New(VmInstanceDeviceAddressVO.class)
                .eq(VmInstanceDeviceAddressVO_.vmInstanceUuid, vm.uuid)
                .count() != 0

        assert !VmSystemTags.BOOT_MODE.hasTag(vm.uuid)

        setVmBootMode {
            uuid = vm.uuid
            bootMode = "Legacy"
        }

        assert VmSystemTags.BOOT_MODE.hasTag(vm.uuid)
        assert Q.New(VmInstanceDeviceAddressVO.class)
                .eq(VmInstanceDeviceAddressVO_.vmInstanceUuid, vm.uuid)
                .count() == 0

        setVmBootMode {
            uuid = vm.uuid
            bootMode = "UEFI"
        }

        assert VmSystemTags.BOOT_MODE.hasTag(vm.uuid)
        assert Q.New(SystemTagVO.class)
                .eq(SystemTagVO_.resourceUuid, vm.uuid)
                .like(SystemTagVO_.tag, "%bootMode%").count() == 1

        deleteVmBootMode {
            uuid = vm.uuid
        }

        assert !VmSystemTags.BOOT_MODE.hasTag(vm.uuid)
    }

    void testCreateVmWithBootMode() {
        def instanceOffering = env.inventoryByName("instanceOffering") as InstanceOfferingInventory
        def image = env.inventoryByName("image1") as ImageInventory
        def l3 = env.inventoryByName("l3") as L3NetworkInventory

        createVmInstance {
            name = "test-1"
            instanceOfferingUuid = instanceOffering.uuid
            imageUuid = image.uuid
            l3NetworkUuids = [l3.uuid]
            systemTags = ["bootMode::Legacy"]
        }

        expect(AssertionError.class){
            createVmInstance {
                name = "test-1"
                instanceOfferingUuid = instanceOffering.uuid
                imageUuid = image.uuid
                l3NetworkUuids = [l3.uuid]
                systemTags = ["bootMode::testhost"]
            }
        }

        List<VmInstanceInventory> vms =  queryVmInstance {
            conditions = ["type=UserVm"]
        }
        assert vms.size() == 2
    }

    /**
     * ZSTAC-83991: When API specifies a bootMode different from image's
     * bootMode, the VM should have exactly one bootMode tag with the
     * API-specified value (not duplicates from both API and image).
     */
    void testCreateVmWithDifferentBootModeThanImage() {
        def instanceOffering = env.inventoryByName("instanceOffering") as InstanceOfferingInventory
        def image = env.inventoryByName("image1") as ImageInventory
        def l3 = env.inventoryByName("l3") as L3NetworkInventory

        // Set bootMode on image to Legacy
        createSystemTag {
            resourceUuid = image.uuid
            resourceType = ImageVO.getSimpleName()
            tag = "bootMode::Legacy"
        }

        // Create VM specifying UEFI bootMode (different from image's Legacy)
        def vm = createVmInstance {
            name = "test-bootmode-dedup"
            instanceOfferingUuid = instanceOffering.uuid
            imageUuid = image.uuid
            l3NetworkUuids = [l3.uuid]
            systemTags = ["bootMode::UEFI"]
        } as VmInstanceInventory

        // ZSTAC-83991: Assert only ONE bootMode tag exists (no duplicates)
        long bootModeCount = Q.New(SystemTagVO.class)
                .eq(SystemTagVO_.resourceUuid, vm.uuid)
                .like(SystemTagVO_.tag, "bootMode%")
                .count()
        assert bootModeCount == 1 :
                "Expected 1 bootMode tag but found ${bootModeCount}"

        // Assert the API-specified bootMode (UEFI) wins over image's (Legacy)
        String bootModeTag = Q.New(SystemTagVO.class)
                .eq(SystemTagVO_.resourceUuid, vm.uuid)
                .like(SystemTagVO_.tag, "bootMode%")
                .select(SystemTagVO_.tag)
                .findValue()
        assert bootModeTag == "bootMode::UEFI" :
                "Expected bootMode::UEFI but got ${bootModeTag}"

        // Also verify when API bootMode is same as image bootMode,
        // still only one tag exists
        def vm2 = createVmInstance {
            name = "test-bootmode-same"
            instanceOfferingUuid = instanceOffering.uuid
            imageUuid = image.uuid
            l3NetworkUuids = [l3.uuid]
            systemTags = ["bootMode::Legacy"]
        } as VmInstanceInventory

        long sameBootModeCount = Q.New(SystemTagVO.class)
                .eq(SystemTagVO_.resourceUuid, vm2.uuid)
                .like(SystemTagVO_.tag, "bootMode%")
                .count()
        assert sameBootModeCount == 1 :
                "Expected 1 bootMode tag but found ${sameBootModeCount}"

        // Cleanup: remove the bootMode tag from image to avoid
        // polluting subsequent tests (e.g. testCreateVmInheritsBootModeFromImage)
        ImageSystemTags.BOOT_MODE.delete(image.uuid)
    }

    /**
     * ZSTAC-83991: Defensive regression test — when API specifies NO
     * bootMode but the image has one, the VM should inherit exactly
     * one bootMode tag from the image.
     */
    void testCreateVmInheritsBootModeFromImage() {
        def instanceOffering = env.inventoryByName("instanceOffering") as InstanceOfferingInventory
        def image = env.inventoryByName("image1") as ImageInventory
        def l3 = env.inventoryByName("l3") as L3NetworkInventory

        // Ensure image has bootMode::Legacy (self-contained, no
        // dependency on previous test execution order)
        if (!ImageSystemTags.BOOT_MODE.hasTag(image.uuid)) {
            createSystemTag {
                resourceUuid = image.uuid
                resourceType = ImageVO.getSimpleName()
                tag = "bootMode::Legacy"
            }
        }
        assert ImageSystemTags.BOOT_MODE.hasTag(image.uuid)

        // Create VM WITHOUT specifying bootMode in API
        def vm = createVmInstance {
            name = "test-bootmode-inherit"
            instanceOfferingUuid = instanceOffering.uuid
            imageUuid = image.uuid
            l3NetworkUuids = [l3.uuid]
        } as VmInstanceInventory

        // VM should have exactly one bootMode tag, inherited from image
        long bootModeCount = Q.New(SystemTagVO.class)
                .eq(SystemTagVO_.resourceUuid, vm.uuid)
                .like(SystemTagVO_.tag, "bootMode%")
                .count()
        assert bootModeCount == 1 :
                "Expected 1 inherited bootMode tag but found ${bootModeCount}"

        // The inherited value should be the image's Legacy
        String bootModeTag = Q.New(SystemTagVO.class)
                .eq(SystemTagVO_.resourceUuid, vm.uuid)
                .like(SystemTagVO_.tag, "bootMode%")
                .select(SystemTagVO_.tag)
                .findValue()
        assert bootModeTag == "bootMode::Legacy" :
                "Expected bootMode::Legacy (from image) but got ${bootModeTag}"
    }
}
