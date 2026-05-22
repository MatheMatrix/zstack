package org.zstack.test.integration.kvm.vm

import org.springframework.http.HttpEntity
import org.zstack.core.db.Q
import org.zstack.header.image.ImagePlatform
import org.zstack.kvm.KVMAgentCommands
import org.zstack.kvm.KVMConstant
import org.zstack.kvm.KVMGlobalConfig
import org.zstack.resourceconfig.ResourceConfigVO
import org.zstack.resourceconfig.ResourceConfigVO_
import org.zstack.header.vm.VmCreationStrategy
import org.zstack.sdk.VmInstanceInventory
import org.zstack.test.integration.kvm.KvmTest
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.SubCase
import org.zstack.utils.data.SizeUnit
import org.zstack.utils.gson.JSONObjectUtil

class VmCpuHardwareVirtualizationCase extends SubCase {
    private static final String CPU_MODE_HOST_MODEL_TAG = "resourceConfig::kvm::vm.cpuMode::host-model"

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
        env = env {
            instanceOffering {
                name = "instanceOffering"
                memory = SizeUnit.GIGABYTE.toByte(2)
                cpu = 2
            }

            sftpBackupStorage {
                name = "sftp"
                url = "/sftp"
                username = "root"
                password = "password"
                hostname = "localhost"

                image {
                    name = "windows-image"
                    url = "http://zstack.org/download/windows.qcow2"
                    platform = ImagePlatform.Windows
                    guestOsType = "Windows Server 2025"
                    architecture = "x86_64"
                }

                image {
                    name = "windows-guest-os-image"
                    url = "http://zstack.org/download/windows-guest-os.qcow2"
                    platform = ImagePlatform.Linux
                    guestOsType = "Windows Server 2022"
                    architecture = "x86_64"
                }

                image {
                    name = "linux-image"
                    url = "http://zstack.org/download/linux.qcow2"
                    platform = ImagePlatform.Linux
                    guestOsType = "Linux"
                    architecture = "x86_64"
                }
            }

            zone {
                name = "zone"
                description = "test"

                cluster {
                    name = "cluster"
                    hypervisorType = "KVM"

                    kvm {
                        name = "kvm"
                        managementIp = "localhost"
                        username = "root"
                        password = "password"
                    }

                    attachPrimaryStorage("local")
                    attachL2Network("l2")
                }

                attachBackupStorage("sftp")

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
            }
        }
    }

    @Override
    void test() {
        env.create()
        def originalValue = KVMGlobalConfig.VM_CPU_HARDWARE_VIRTUALIZATION.value()
        try {
            KVMGlobalConfig.VM_CPU_HARDWARE_VIRTUALIZATION.updateValue(KVMConstant.CPU_HARDWARE_VIRTUALIZATION_DISABLED)
            testWindowsVmWithCpuModeNoneDoesNotSendCpuHardwareVirtualization()

            testWindowsVmUsesGlobalFallbackAndResourceConfigOverrides()
            testNewWindowsVmUsesGlobalEnabledFallback()
            testCreateWindowsVmWithExplicitResourceConfig()
            testJustCreateWindowsVmUsesGlobalOnStart()
            testGuestOsTypeWindowsUsesGlobalFallback()
            testNonWindowsVmIgnoresConfig()
            testNonWindowsVmChangedToWindowsUsesGlobalFallback()
        } finally {
            KVMGlobalConfig.VM_CPU_HARDWARE_VIRTUALIZATION.updateValue(originalValue)
        }
    }

    void testWindowsVmWithCpuModeNoneDoesNotSendCpuHardwareVirtualization() {
        def vm = createVmAndCaptureStartCmd("windows-cpu-mode-none-vm", "windows-image",
                null, false, { KVMAgentCommands.StartVmCmd cmd ->
            assert cmd.nestedVirtualization == KVMConstant.CPU_MODE_NONE
            assert cmd.cpuHardwareVirtualization == null
        })

        assert explicitCpuHardwareVirtualizationValue(vm.uuid) == null

        destroyVmInstance { uuid = vm.uuid }
        expungeVmInstance { uuid = vm.uuid }
    }

    void testWindowsVmUsesGlobalFallbackAndResourceConfigOverrides() {
        def vm = createVmAndCaptureStartCmd("windows-vm", "windows-image", { KVMAgentCommands.StartVmCmd cmd ->
            assert cmd.nestedVirtualization == KVMConstant.CPU_MODE_HOST_MODEL
            assert cmd.cpuHardwareVirtualization == KVMConstant.CPU_HARDWARE_VIRTUALIZATION_DISABLED
        })

        assert explicitCpuHardwareVirtualizationValue(vm.uuid) == null

        KVMGlobalConfig.VM_CPU_HARDWARE_VIRTUALIZATION.updateValue(KVMConstant.CPU_HARDWARE_VIRTUALIZATION_ENABLED)
        assertStartCmdOnReboot(vm.uuid) { KVMAgentCommands.StartVmCmd cmd ->
            assert cmd.cpuHardwareVirtualization == null
        }

        KVMGlobalConfig.VM_CPU_HARDWARE_VIRTUALIZATION.updateValue(KVMConstant.CPU_HARDWARE_VIRTUALIZATION_DISABLED)

        updateResourceConfig {
            category = KVMGlobalConfig.CATEGORY
            name = KVMGlobalConfig.VM_CPU_HARDWARE_VIRTUALIZATION.name
            value = KVMConstant.CPU_HARDWARE_VIRTUALIZATION_ENABLED
            resourceUuid = vm.uuid
        }

        assert explicitCpuHardwareVirtualizationValue(vm.uuid) == KVMConstant.CPU_HARDWARE_VIRTUALIZATION_ENABLED
        assertStartCmdOnReboot(vm.uuid) { KVMAgentCommands.StartVmCmd cmd ->
            assert cmd.cpuHardwareVirtualization == null
        }

        updateResourceConfig {
            category = KVMGlobalConfig.CATEGORY
            name = KVMGlobalConfig.VM_CPU_HARDWARE_VIRTUALIZATION.name
            value = KVMConstant.CPU_HARDWARE_VIRTUALIZATION_DISABLED
            resourceUuid = vm.uuid
        }

        assert explicitCpuHardwareVirtualizationValue(vm.uuid) == KVMConstant.CPU_HARDWARE_VIRTUALIZATION_DISABLED
        assertStartCmdOnReboot(vm.uuid) { KVMAgentCommands.StartVmCmd cmd ->
            assert cmd.cpuHardwareVirtualization == KVMConstant.CPU_HARDWARE_VIRTUALIZATION_DISABLED
        }

        deleteResourceConfig {
            category = KVMGlobalConfig.CATEGORY
            name = KVMGlobalConfig.VM_CPU_HARDWARE_VIRTUALIZATION.name
            resourceUuid = vm.uuid
        }

        assert explicitCpuHardwareVirtualizationValue(vm.uuid) == null
        assertStartCmdOnReboot(vm.uuid) { KVMAgentCommands.StartVmCmd cmd ->
            assert cmd.cpuHardwareVirtualization == KVMConstant.CPU_HARDWARE_VIRTUALIZATION_DISABLED
        }

        destroyVmInstance { uuid = vm.uuid }
        expungeVmInstance { uuid = vm.uuid }
    }

    void testNewWindowsVmUsesGlobalEnabledFallback() {
        KVMGlobalConfig.VM_CPU_HARDWARE_VIRTUALIZATION.updateValue(KVMConstant.CPU_HARDWARE_VIRTUALIZATION_ENABLED)

        def vm = createVmAndCaptureStartCmd("windows-global-enabled-vm", "windows-image", { KVMAgentCommands.StartVmCmd cmd ->
            assert cmd.cpuHardwareVirtualization == null
        })

        assert explicitCpuHardwareVirtualizationValue(vm.uuid) == null

        destroyVmInstance { uuid = vm.uuid }
        expungeVmInstance { uuid = vm.uuid }
        KVMGlobalConfig.VM_CPU_HARDWARE_VIRTUALIZATION.updateValue(KVMConstant.CPU_HARDWARE_VIRTUALIZATION_DISABLED)
    }

    void testCreateWindowsVmWithExplicitResourceConfig() {
        KVMGlobalConfig.VM_CPU_HARDWARE_VIRTUALIZATION.updateValue(KVMConstant.CPU_HARDWARE_VIRTUALIZATION_DISABLED)
        def enabledVm = createVmAndCaptureStartCmd("windows-create-enabled-vm", "windows-image",
                ["resourceConfig::kvm::vm.cpu.hardwareVirtualization::enabled"], { KVMAgentCommands.StartVmCmd cmd ->
            assert cmd.cpuHardwareVirtualization == null
        })
        assert explicitCpuHardwareVirtualizationValue(enabledVm.uuid) == KVMConstant.CPU_HARDWARE_VIRTUALIZATION_ENABLED
        destroyVmInstance { uuid = enabledVm.uuid }
        expungeVmInstance { uuid = enabledVm.uuid }

        KVMGlobalConfig.VM_CPU_HARDWARE_VIRTUALIZATION.updateValue(KVMConstant.CPU_HARDWARE_VIRTUALIZATION_ENABLED)
        def disabledVm = createVmAndCaptureStartCmd("windows-create-disabled-vm", "windows-image",
                ["resourceConfig::kvm::vm.cpu.hardwareVirtualization::disabled"], { KVMAgentCommands.StartVmCmd cmd ->
            assert cmd.cpuHardwareVirtualization == KVMConstant.CPU_HARDWARE_VIRTUALIZATION_DISABLED
        })
        assert explicitCpuHardwareVirtualizationValue(disabledVm.uuid) == KVMConstant.CPU_HARDWARE_VIRTUALIZATION_DISABLED
        destroyVmInstance { uuid = disabledVm.uuid }
        expungeVmInstance { uuid = disabledVm.uuid }

        KVMGlobalConfig.VM_CPU_HARDWARE_VIRTUALIZATION.updateValue(KVMConstant.CPU_HARDWARE_VIRTUALIZATION_DISABLED)
    }

    void testJustCreateWindowsVmUsesGlobalOnStart() {
        KVMGlobalConfig.VM_CPU_HARDWARE_VIRTUALIZATION.updateValue(KVMConstant.CPU_HARDWARE_VIRTUALIZATION_ENABLED)

        def image = env.inventoryByName("windows-image")
        def l3 = env.inventoryByName("l3")
        def instanceOffering = env.inventoryByName("instanceOffering")

        def vm = createVmInstance {
            name = "windows-just-create-vm"
            imageUuid = image.uuid
            l3NetworkUuids = [l3.uuid]
            instanceOfferingUuid = instanceOffering.uuid
            strategy = VmCreationStrategy.JustCreate.toString()
            systemTags = [CPU_MODE_HOST_MODEL_TAG]
        } as VmInstanceInventory

        assert explicitCpuHardwareVirtualizationValue(vm.uuid) == null

        KVMGlobalConfig.VM_CPU_HARDWARE_VIRTUALIZATION.updateValue(KVMConstant.CPU_HARDWARE_VIRTUALIZATION_DISABLED)
        KVMAgentCommands.StartVmCmd startCmd = captureStartCmd {
            startVmInstance {
                uuid = vm.uuid
            }
        }
        assert startCmd.cpuHardwareVirtualization == KVMConstant.CPU_HARDWARE_VIRTUALIZATION_DISABLED

        destroyVmInstance { uuid = vm.uuid }
        expungeVmInstance { uuid = vm.uuid }
        KVMGlobalConfig.VM_CPU_HARDWARE_VIRTUALIZATION.updateValue(KVMConstant.CPU_HARDWARE_VIRTUALIZATION_DISABLED)
    }

    void testGuestOsTypeWindowsUsesGlobalFallback() {
        KVMGlobalConfig.VM_CPU_HARDWARE_VIRTUALIZATION.updateValue(KVMConstant.CPU_HARDWARE_VIRTUALIZATION_DISABLED)
        def vm = createVmAndCaptureStartCmd("windows-guest-os-vm", "windows-guest-os-image", { KVMAgentCommands.StartVmCmd cmd ->
            assert cmd.cpuHardwareVirtualization == KVMConstant.CPU_HARDWARE_VIRTUALIZATION_DISABLED
        })

        assert explicitCpuHardwareVirtualizationValue(vm.uuid) == null

        destroyVmInstance { uuid = vm.uuid }
        expungeVmInstance { uuid = vm.uuid }
    }

    void testNonWindowsVmIgnoresConfig() {
        KVMGlobalConfig.VM_CPU_HARDWARE_VIRTUALIZATION.updateValue(KVMConstant.CPU_HARDWARE_VIRTUALIZATION_DISABLED)
        def vm = createVmAndCaptureStartCmd("linux-vm", "linux-image", { KVMAgentCommands.StartVmCmd cmd ->
            assert cmd.cpuHardwareVirtualization == null
        })

        assert explicitCpuHardwareVirtualizationValue(vm.uuid) == null

        destroyVmInstance { uuid = vm.uuid }
        expungeVmInstance { uuid = vm.uuid }

        def explicitVm = createVmAndCaptureStartCmd("linux-explicit-disabled-vm", "linux-image",
                ["resourceConfig::kvm::vm.cpu.hardwareVirtualization::disabled"], { KVMAgentCommands.StartVmCmd cmd ->
            assert cmd.cpuHardwareVirtualization == null
        })

        assert explicitCpuHardwareVirtualizationValue(explicitVm.uuid) == KVMConstant.CPU_HARDWARE_VIRTUALIZATION_DISABLED

        destroyVmInstance { uuid = explicitVm.uuid }
        expungeVmInstance { uuid = explicitVm.uuid }
    }

    void testNonWindowsVmChangedToWindowsUsesGlobalFallback() {
        KVMGlobalConfig.VM_CPU_HARDWARE_VIRTUALIZATION.updateValue(KVMConstant.CPU_HARDWARE_VIRTUALIZATION_DISABLED)

        def vm = createVmAndCaptureStartCmd("linux-to-windows-vm", "linux-image", { KVMAgentCommands.StartVmCmd cmd ->
            assert cmd.cpuHardwareVirtualization == null
        })

        assert explicitCpuHardwareVirtualizationValue(vm.uuid) == null

        updateVmInstance {
            uuid = vm.uuid
            platform = ImagePlatform.Windows.toString()
            guestOsType = "Windows Server 2025"
        }

        assert explicitCpuHardwareVirtualizationValue(vm.uuid) == null
        assertStartCmdOnReboot(vm.uuid) { KVMAgentCommands.StartVmCmd cmd ->
            assert cmd.cpuHardwareVirtualization == KVMConstant.CPU_HARDWARE_VIRTUALIZATION_DISABLED
        }

        destroyVmInstance { uuid = vm.uuid }
        expungeVmInstance { uuid = vm.uuid }
    }

    private VmInstanceInventory createVmAndCaptureStartCmd(String vmName, String imageName, Closure cmdAssertion) {
        return createVmAndCaptureStartCmd(vmName, imageName, null, true, cmdAssertion)
    }

    private VmInstanceInventory createVmAndCaptureStartCmd(String vmName, String imageName, List<String> systemTags, Closure cmdAssertion) {
        return createVmAndCaptureStartCmd(vmName, imageName, systemTags, true, cmdAssertion)
    }

    private VmInstanceInventory createVmAndCaptureStartCmd(String vmName, String imageName, List<String> systemTags,
                                                          boolean useHostModelCpuMode, Closure cmdAssertion) {
        def image = env.inventoryByName(imageName)
        def l3 = env.inventoryByName("l3")
        def instanceOffering = env.inventoryByName("instanceOffering")

        VmInstanceInventory vm = null
        KVMAgentCommands.StartVmCmd startCmd = captureStartCmd {
            vm = createVmInstance {
                name = vmName
                imageUuid = image.uuid
                l3NetworkUuids = [l3.uuid]
                instanceOfferingUuid = instanceOffering.uuid
                List<String> mergedSystemTags = []
                if (useHostModelCpuMode) {
                    mergedSystemTags.add(CPU_MODE_HOST_MODEL_TAG)
                }
                if (systemTags != null) {
                    mergedSystemTags.addAll(systemTags)
                }
                if (!mergedSystemTags.isEmpty()) {
                    delegate.systemTags = mergedSystemTags
                }
            } as VmInstanceInventory
        }

        cmdAssertion(startCmd)
        return vm
    }

    private void assertStartCmdOnReboot(String vmUuid, Closure cmdAssertion) {
        KVMAgentCommands.StartVmCmd startCmd = captureStartCmd {
            rebootVmInstance {
                uuid = vmUuid
            }
        }
        cmdAssertion(startCmd)
    }

    private KVMAgentCommands.StartVmCmd captureStartCmd(Closure action) {
        KVMAgentCommands.StartVmCmd startCmd = null
        env.afterSimulator(KVMConstant.KVM_START_VM_PATH) { rsp, HttpEntity<String> e ->
            startCmd = JSONObjectUtil.toObject(e.body, KVMAgentCommands.StartVmCmd.class)
            return rsp
        }
        action()
        assert startCmd != null
        return startCmd
    }

    private String explicitCpuHardwareVirtualizationValue(String vmUuid) {
        return Q.New(ResourceConfigVO.class)
                .select(ResourceConfigVO_.value)
                .eq(ResourceConfigVO_.category, KVMGlobalConfig.CATEGORY)
                .eq(ResourceConfigVO_.name, KVMGlobalConfig.VM_CPU_HARDWARE_VIRTUALIZATION.name)
                .eq(ResourceConfigVO_.resourceUuid, vmUuid)
                .findValue()
    }
}
