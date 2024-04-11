package org.zstack.header.vm;

public class VmInstanceTemplateInventory {
    private String uuid;
    private String vmInstanceUuid;
    private String type;

    public static VmInstanceTemplateInventory valueOf(TemplateVmInstanceVO vo) {
        VmInstanceTemplateInventory inv = new VmInstanceTemplateInventory();
        inv.setUuid(vo.getUuid());
        inv.setVmInstanceUuid(vo.getVmInstanceUuid());
        return inv;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getVmInstanceUuid() {
        return vmInstanceUuid;
    }

    public void setVmInstanceUuid(String vmInstanceUuid) {
        this.vmInstanceUuid = vmInstanceUuid;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
