package org.zstack.sdk;

import org.zstack.sdk.VmInstanceTemplateInventory;

public class CreateVmInstanceTemplateResult {
    public VmInstanceTemplateInventory inventory;
    public void setInventory(VmInstanceTemplateInventory inventory) {
        this.inventory = inventory;
    }
    public VmInstanceTemplateInventory getInventory() {
        return this.inventory;
    }

    public java.util.List volumeTemplates;
    public void setVolumeTemplates(java.util.List volumeTemplates) {
        this.volumeTemplates = volumeTemplates;
    }
    public java.util.List getVolumeTemplates() {
        return this.volumeTemplates;
    }

}
