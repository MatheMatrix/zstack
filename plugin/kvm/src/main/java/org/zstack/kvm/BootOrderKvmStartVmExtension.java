package org.zstack.kvm;

import org.zstack.compute.vm.VmSystemTags;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.vm.VmBootDevice;
import org.zstack.header.vm.VmInstanceSpec;
import org.zstack.header.vm.VmInstanceVO;

import java.util.List;

/**
 * author:kaicai.hu
 * Date:2019/12/25
 */
public class BootOrderKvmStartVmExtension implements KVMStartVmExtensionPoint {

    @Override
    public void startVmOnKvmSuccess(KVMHostInventory host, VmInstanceSpec spec) {
        if (VmSystemTags.BOOT_ORDER_ONCE.hasTag(spec.getVmInventory().getUuid(), VmInstanceVO.class)) {
            VmSystemTags.BOOT_ORDER.delete(spec.getVmInventory().getUuid());
            VmSystemTags.BOOT_ORDER_ONCE.delete(spec.getVmInventory().getUuid());
        }
        if (VmSystemTags.CDROM_BOOT_ONCE.hasTag(spec.getVmInventory().getUuid(), VmInstanceVO.class)) {
            VmSystemTags.BOOT_ORDER.delete(spec.getVmInventory().getUuid());
            VmSystemTags.CDROM_BOOT_ONCE.delete(spec.getVmInventory().getUuid());
        }
    }

    @Override
    public void startVmOnKvmFailed(KVMHostInventory host, VmInstanceSpec spec, ErrorCode err) {

    }

    @Override
    public void beforeStartVmOnKvm(KVMHostInventory host, VmInstanceSpec spec, KVMAgentCommands.StartVmCmd cmd) {
        deleteNoOperationTagIfVmBootedFromCdrom(spec);
    }

    private void deleteNoOperationTagIfVmBootedFromCdrom(VmInstanceSpec spec) {
        if (!VmSystemTags.NO_OPERATING_SYSTEM.hasTag(spec.getVmInventory().getUuid(), VmInstanceVO.class)) {
            return;
        }

        if (spec.getImageSpec().getInventory() != null) {
            VmSystemTags.NO_OPERATING_SYSTEM.delete(spec.getVmInventory().getUuid());
            return;
        }

        final List<String> bootOrders = spec.getBootOrders();
        boolean bootOrderHasCdRom = bootOrders.contains(VmBootDevice.CdRom.toString());
        if (!bootOrderHasCdRom) {
            return;
        }

        final long attachedIsoCount = spec.getCdRomSpecs().stream()
                .filter(VmInstanceSpec.CdRomSpec::isAttachedIso)
                .count();
        if (attachedIsoCount == 0) {
            return;
        }
        VmSystemTags.NO_OPERATING_SYSTEM.delete(spec.getVmInventory().getUuid());
    }
}
