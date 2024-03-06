package org.zstack.sdk;

import org.zstack.sdk.VmInstanceTemplateInventory;

public class CreateVmInstanceTemplateResult {
    public VmInstanceTemplateInventory vmTemplate;
    public VmInstanceTemplateInventory getVmTemplate() {
        return vmTemplate;
    }

    public void setVmTemplate(VmInstanceTemplateInventory vmTemplate) {
        this.vmTemplate = vmTemplate;
    }

    public java.util.List volumeTemplates;
    public void setVolumeTemplates(java.util.List volumeTemplates) {
        this.volumeTemplates = volumeTemplates;
    }
    public java.util.List getVolumeTemplates() {
        return this.volumeTemplates;
    }

}
