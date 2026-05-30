package org.zstack.kvm;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.db.Q;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.vm.ArchiveVmBundle;
import org.zstack.header.vm.VmInstanceInventory;
import org.zstack.header.vm.VmInstanceSpec;
import org.zstack.header.vm.VmNicInventory;
import org.zstack.header.vm.devices.*;
import org.zstack.utils.Utils;
import org.zstack.utils.gson.JSONObjectUtil;
import org.zstack.utils.logging.CLogger;

import java.util.HashSet;
import java.util.Set;

public class VirtualPciDeviceKvmExtensionPoint implements KVMStartVmExtensionPoint, KVMSyncVmDeviceInfoExtensionPoint {
    private static final CLogger logger = Utils.getLogger(VirtualPciDeviceKvmExtensionPoint.class);

    @Autowired
    private VmInstanceResourceMetadataManager vidManager;

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
        VmInstanceResourceMetadataVO vo = Q.New(VmInstanceResourceMetadataVO.class)
                .eq(VmInstanceResourceMetadataVO_.vmInstanceUuid, vmUuid)
                .eq(VmInstanceResourceMetadataVO_.resourceUuid, vmUuid)
                .find();
        if (vo == null) {
            logger.debug(String.format("VmInstanceResourceMetadataVO is not found for vm[%s]", vmUuid));
            return null;
        }

        if (vo.getMetadata() == null) {
            logger.debug(String.format("metadata is not found for vm[%s] VmInstanceResourceMetadataVO", vmUuid));
            return null;
        }

        ArchiveVmBundle archiveVmBundle;
        try {
            archiveVmBundle = JSONObjectUtil.toObject(vo.getMetadata(), ArchiveVmBundle.class);
            if (archiveVmBundle.getXml() == null) {
                logger.debug(String.format("vm xml is not found for in metadata[%s]", vo.getMetadata()));
                return null;
            }
        } catch (Exception e) {
            logger.warn(String.format("failed to deserialize vm[%s] metadata", vmUuid), e);
            return null;
        }
        return archiveVmBundle.getXml();
    }

    @Override
    public void afterReceiveVmDeviceInfoResponse(VmInstanceInventory vm, KVMAgentCommands.VmDevicesInfoResponse rsp, VmInstanceSpec spec) {
        String vmUuid = spec != null ? spec.getVmInventory().getUuid() : vm.getUuid();

        Set<String> surviving = new HashSet<>();
        // Tracks whether the agent reply actually contains valid device info.
        // Only flipped to true when we observe at least one valid virtual
        // device / matched nic / balloon address from the response itself.
        // This is the ONLY signal used to decide whether prune may run;
        // resourceUuids added as defensive fallbacks (e.g. nic uuids copied
        // from spec when nicInfos is null) must NOT influence this flag.
        boolean hasAuthoritativeDeviceInfo = false;

        vidManager.saveVmXmlMetadata(rsp.getVmXml(), vmUuid);
        // the vmXml row uses vmUuid as resourceUuid; keep it
        surviving.add(vmUuid);

        // only update pci address, metadata is not mandatory in normal usage
        // check its usage when create snapshot or backup
        if (rsp.getVirtualDeviceInfoList() != null) {
            for (VirtualDeviceInfo info : rsp.getVirtualDeviceInfoList()) {
                if (info.isValid()) {
                    vidManager.createOrUpdateVmResourceMetadata(info, vmUuid);
                    surviving.add(info.getResourceUuid());
                    hasAuthoritativeDeviceInfo = true;
                }
            }
        }

        if (rsp.getNicInfos() != null) {
            for (KVMAgentCommands.VmNicInfo info : rsp.getNicInfos()) {
                VmNicInventory nic = (spec != null ? spec.getDestNics() : vm.getVmNics())
                        .stream()
                        .filter(vmNicInventory -> vmNicInventory.getMac().equals(info.getMacAddress()))
                        .findFirst()
                        .orElse(null);
                if (nic == null) {
                    continue;
                }

                vidManager.createOrUpdateVmResourceMetadata(new VirtualDeviceInfo(nic.getUuid(), info.getDeviceAddress()), vmUuid);
                surviving.add(nic.getUuid());
                hasAuthoritativeDeviceInfo = true;
            }
        } else {
            // nicInfos is null => partial agent response; we do NOT have an
            // authoritative nic snapshot. Preserve every known nic uuid of
            // this VM so a prune step (triggered by other authoritative fields)
            // cannot drop their address rows as collateral damage.
            (spec != null ? spec.getDestNics() : vm.getVmNics())
                    .forEach(n -> surviving.add(n.getUuid()));
        }

        if (rsp.getMemBalloonInfo() != null
                && rsp.getMemBalloonInfo().getDeviceAddress() != null
                && !StringUtils.isEmpty(rsp.getMemBalloonInfo().getDeviceAddress().toString())) {
            vidManager.createOrUpdateVmResourceMetadata(new VirtualDeviceInfo(vidManager.MEM_BALLOON_UUID,
                    DeviceAddress.fromString(rsp.getMemBalloonInfo().getDeviceAddress().toString())), vmUuid);
            surviving.add(vidManager.MEM_BALLOON_UUID);
            hasAuthoritativeDeviceInfo = true;
        }

        // Differential prune of stale address-only rows (metadataClass IS NULL)
        // whose resourceUuid is no longer present in the libvirt domain, so DB
        // state stays consistent after devices (e.g. cdrom) are detached while
        // the VM is stopped. Rows owned by other extension points (non-null
        // metadataClass) are untouched.
        //
        // Guard: only prune when the response actually delivered authoritative
        // device info. An empty / all-invalid reply gives no ground truth; we
        // must not treat absence-of-data as proof-of-deletion.
        if (!hasAuthoritativeDeviceInfo) {
            logger.debug(String.format(
                    "skip pruneStaleDeviceMetadata for vm[%s]: empty device snapshot",
                    vmUuid));
            return;
        }
        try {
            int pruned = vidManager.pruneStaleDeviceMetadata(vmUuid, surviving);
            if (pruned > 0) {
                logger.debug(String.format(
                        "pruned %d stale VmInstanceResourceMetadataVO row(s) for vm[%s]",
                        pruned, vmUuid));
            }
        } catch (Exception e) {
            logger.warn(String.format("failed to prune stale device metadata for vm[%s]", vmUuid), e);
        }
    }

    @Override
    public void startVmOnKvmSuccess(KVMHostInventory host, VmInstanceSpec spec) {

    }

    @Override
    public void startVmOnKvmFailed(KVMHostInventory host, VmInstanceSpec spec, ErrorCode err) {

    }
}
