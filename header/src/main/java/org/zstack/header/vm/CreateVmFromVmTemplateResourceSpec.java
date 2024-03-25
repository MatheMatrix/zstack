package org.zstack.header.vm;

import org.zstack.header.identity.SessionInventory;
import org.zstack.header.volume.VolumeType;
import org.zstack.utils.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

public class CreateVmFromVmTemplateResourceSpec {
    private NewVmInstanceMessage2 apiMsg;
    private List<String> names;
    private SessionInventory session;
    private List<String> sshKeyPairUuids;

    private VmTemplateVO vmTemplateVO;
    private String VmInstanceTemplateUuid;
    private VmInstanceInventory vmInstanceInventory;
    private List<APICreateVmInstanceMsg.DiskAO> allDiskAOs;
    private List<APICreateVmInstanceMsg.DiskAO> templateVolumeDiskAOs;
    private List<String> templateVolumeUuids;

    public NewVmInstanceMessage2 getApiMsg() {
        return apiMsg;
    }

    public void setApiMsg(NewVmInstanceMessage2 apiMsg) {
        this.apiMsg = apiMsg;
    }

    public List<String> getNames() {
        return names;
    }

    public void setNames(List<String> names) {
        this.names = names;
    }

    public SessionInventory getSession() {
        return session;
    }

    public void setSession(SessionInventory session) {
        this.session = session;
    }

    public List<String> getSshKeyPairUuids() {
        return sshKeyPairUuids;
    }

    public void setSshKeyPairUuids(List<String> sshKeyPairUuids) {
        this.sshKeyPairUuids = sshKeyPairUuids;
    }

    public VmTemplateVO getVmTemplateVO() {
        return vmTemplateVO;
    }

    public void setVmTemplateVO(VmTemplateVO vmTemplateVO) {
        this.vmTemplateVO = vmTemplateVO;
    }

    public String getVmInstanceTemplateUuid() {
        return VmInstanceTemplateUuid;
    }

    public void setVmInstanceTemplateUuid(String vmInstanceTemplateUuid) {
        VmInstanceTemplateUuid = vmInstanceTemplateUuid;
    }

    public VmInstanceInventory getVmInstanceInventory() {
        return vmInstanceInventory;
    }

    public void setVmInstanceInventory(VmInstanceInventory vmInstanceInventory) {
        this.vmInstanceInventory = vmInstanceInventory;
    }

    public List<APICreateVmInstanceMsg.DiskAO> getAllDiskAOs() {
        return allDiskAOs;
    }

    public void setAllDiskAOs(List<APICreateVmInstanceMsg.DiskAO> allDiskAOs) {
        this.allDiskAOs = allDiskAOs;
    }

    public List<APICreateVmInstanceMsg.DiskAO> getTemplateVolumeDiskAOs() {
        return templateVolumeDiskAOs;
    }

    public void setTemplateVolumeDiskAOs(List<APICreateVmInstanceMsg.DiskAO> templateVolumeDiskAOs) {
        this.templateVolumeDiskAOs = templateVolumeDiskAOs;
    }

    public List<String> getTemplateVolumeUuids() {
        return templateVolumeUuids;
    }

    public void setTemplateVolumeUuids(List<String> templateVolumeUuids) {
        this.templateVolumeUuids = templateVolumeUuids;
    }

    public static CreateVmFromVmTemplateResourceSpec buildSpecFromApi(APICreateVmInstanceFromVmInstanceTemplateMsg msg) {
        CreateVmFromVmTemplateResourceSpec spec = new CreateVmFromVmTemplateResourceSpec();
        spec.setApiMsg(msg);
        spec.setNames(msg.getNames());
        spec.setSession(msg.getSession());
        spec.setVmTemplateVO(msg.getVmTemplateVO());
        spec.setVmInstanceInventory(msg.getVmInstanceInventory());
        spec.setVmInstanceTemplateUuid(msg.getVmInstanceTemplateUuid());
        spec.setAllDiskAOs(buildDiskAOs(spec, msg));
        spec.setTemplateVolumeUuids(spec.getTemplateVolumeDiskAOs().stream().map(APICreateVmInstanceMsg.DiskAO::getSourceUuid).collect(Collectors.toList()));
        spec.setSshKeyPairUuids(msg.getSshKeyPairUuids());
        return spec;
    }

    private static List<APICreateVmInstanceMsg.DiskAO> buildDiskAOs(CreateVmFromVmTemplateResourceSpec spec, APICreateVmInstanceFromVmInstanceTemplateMsg msg) {
        List<APICreateVmInstanceMsg.DiskAO> diskAOs = CollectionUtils.isEmpty(msg.getDiskAOs()) ? new ArrayList<>() : msg.getDiskAOs();
        VmInstanceInventory vmInv = msg.getVmInstanceInventory();

        List<APICreateVmInstanceMsg.DiskAO> allDiskAOs = new ArrayList<>();
        List<APICreateVmInstanceMsg.DiskAO> newDiskAOs = new ArrayList<>();
        Map<String, APICreateVmInstanceMsg.DiskAO> templateVolumeDiskAOMap = new HashMap<>();
        List<APICreateVmInstanceMsg.DiskAO> templateVolumeDiskAOs = new ArrayList<>();

        diskAOs.forEach(diskAO -> {
            if (Objects.equals(diskAO.getSourceType(), VolumeType.Template.toString())) {
                templateVolumeDiskAOMap.put(diskAO.getSourceUuid(), diskAO);
            } else {
                newDiskAOs.add(diskAO);
            }
        });

        vmInv.getAllTemplateDiskVolumes().stream().sorted(Comparator.comparingLong(it -> it.getLastAttachDate().getTime())).forEach(volume -> {
            APICreateVmInstanceMsg.DiskAO diskAO;
            if (templateVolumeDiskAOMap.containsKey(volume.getUuid())) {
                diskAO = templateVolumeDiskAOMap.get(volume.getUuid());
                diskAO.setName(diskAO.getName());
                diskAO.setResize(diskAO.getResize());
                diskAO.setSourceUuid(volume.getUuid());
                diskAO.setPrimaryStorageUuid(diskAO.getPrimaryStorageUuid() != null ? diskAO.getPrimaryStorageUuid() : volume.getPrimaryStorageUuid());
                diskAO.setSystemTags(diskAO.getSystemTags());
            } else {
                diskAO = new APICreateVmInstanceMsg.DiskAO();
                diskAO.setSourceUuid(volume.getUuid());
                diskAO.setSourceType(VolumeType.Template.toString());
                diskAO.setPrimaryStorageUuid(volume.getPrimaryStorageUuid());
                diskAO.setSystemTags(diskAO.getSystemTags());
            }
            if (diskAO.isBoot()) {
                diskAO.setPlatform(diskAO.getPlatform() != null ? diskAO.getPlatform() : vmInv.getPlatform());
                diskAO.setGuestOsType(diskAO.getGuestOsType() != null ? diskAO.getGuestOsType() : vmInv.getGuestOsType());
                diskAO.setArchitecture(diskAO.getArchitecture() != null ? diskAO.getArchitecture() : vmInv.getArchitecture());
            }
            templateVolumeDiskAOs.add(diskAO);
        });

        spec.setTemplateVolumeDiskAOs(templateVolumeDiskAOs);
        allDiskAOs.addAll(templateVolumeDiskAOs);
        allDiskAOs.addAll(newDiskAOs);
        return allDiskAOs;
    }
}
