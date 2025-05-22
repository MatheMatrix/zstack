package org.zstack.kvm;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.db.Q;
import org.zstack.header.vm.ArchiveVmBundle;
import org.zstack.header.vm.VmInstanceInventory;
import org.zstack.header.vm.VmNicInventory;
import org.zstack.header.vm.devices.*;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.vm.VmInstanceSpec;
import org.zstack.utils.gson.JSONObjectUtil;

public class VirtualPciDeviceKvmExtensionPoint implements KVMStartVmExtensionPoint, KVMSyncVmDeviceInfoExtensionPoint {
    @Autowired
    private VmInstanceDeviceManager vidManager;

    @Override
    public void beforeStartVmOnKvm(KVMHostInventory host, VmInstanceSpec spec, KVMAgentCommands.StartVmCmd cmd) {
        if (cmd.getRootVolume() != null) {
            setDeviceAddress(cmd.getRootVolume(), cmd);
        }

        if (cmd.getDataVolumes() != null) {
            cmd.getDataVolumes().forEach(to -> setDeviceAddress(to, cmd));
        }

        if (cmd.getNics() != null) {
            cmd.getNics().forEach(to -> setDeviceAddress(to, cmd));
        }

        if (cmd.getCdRoms() != null) {
            cmd.getCdRoms().forEach(to -> setDeviceAddress(to, cmd));
        }

        cmd.setVmXml(getVmXml(spec.getVmInventory().getUuid()));
    }

    private void setDeviceAddress(BaseVirtualDeviceTO to, KVMAgentCommands.StartVmCmd cmd) {
        to.setDeviceAddress(vidManager.getVmDeviceAddress(to.getResourceUuid(), cmd.getVmInstanceUuid()));
    }

    private String getVmXml(String vmUuid) {
        VmInstanceDeviceAddressVO vo = Q.New(VmInstanceDeviceAddressVO.class)
                .eq(VmInstanceDeviceAddressVO_.vmInstanceUuid, vmUuid)
                .eq(VmInstanceDeviceAddressVO_.resourceUuid, vmUuid)
                .find();
        if (vo == null || vo.getMetadata() == null) {
            return null;
        }

        ArchiveVmBundle archiveVmBundle = JSONObjectUtil.toObject(vo.getMetadata(), ArchiveVmBundle.class);
        if (archiveVmBundle == null || archiveVmBundle.getXml() == null) {
            return null;
        }
        return archiveVmBundle.getXml();
    }

    @Override
    public void afterReceiveVmDeviceInfoResponse(VmInstanceInventory vm, KVMAgentCommands.VmDevicesInfoResponse rsp, VmInstanceSpec spec) {
        if (rsp.getVirtualDeviceInfoList() == null) {
            return;
        }

        String vmUuid = spec != null ? spec.getVmInventory().getUuid() : vm.getUuid();
        // only update pci address, metadata is not mandatory in normal usage
        // check its usage when create snapshot or backup
        rsp.getVirtualDeviceInfoList().forEach(info -> {
            if (info.isValid()) {
                vidManager.createOrUpdateVmDeviceAddress(info, vmUuid);
            }
        });

        vidManager.recordOrUpdateVmXml(rsp.getVmXml(), vm.getUuid());

        if (rsp.getNicInfos() == null) {
            return;
        }

        rsp.getNicInfos().forEach(info -> {
            VmNicInventory nic = (spec != null ? spec.getDestNics() : vm.getVmNics())
                    .stream()
                    .filter(vmNicInventory -> vmNicInventory.getMac().equals(info.getMacAddress()))
                    .findFirst()
                    .orElse(null);
            if (nic == null) {
                return;
            }

            vidManager.createOrUpdateVmDeviceAddress(new VirtualDeviceInfo(nic.getUuid(), info.getDeviceAddress()), vmUuid);
        });

        if (!StringUtils.isEmpty(rsp.getMemBalloonInfo().getDeviceAddress().toString())) {
            vidManager.createOrUpdateVmDeviceAddress(new VirtualDeviceInfo(vidManager.MEM_BALLOON_UUID,
                    DeviceAddress.fromString(rsp.getMemBalloonInfo().getDeviceAddress().toString())), vmUuid);
        }
    }

    @Override
    public void startVmOnKvmSuccess(KVMHostInventory host, VmInstanceSpec spec) {

    }

    @Override
    public void startVmOnKvmFailed(KVMHostInventory host, VmInstanceSpec spec, ErrorCode err) {

    }
}
