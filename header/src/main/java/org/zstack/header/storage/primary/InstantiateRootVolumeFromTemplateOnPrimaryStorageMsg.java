package org.zstack.header.storage.primary;

import org.zstack.header.vm.VmInstanceSpec.ImageSpec;

public class InstantiateRootVolumeFromTemplateOnPrimaryStorageMsg extends InstantiateVolumeOnPrimaryStorageMsg implements PrimaryStorageMessage {
    private ImageSpec templateSpec;
    private boolean allowEmpty;

    public ImageSpec getTemplateSpec() {
        return templateSpec;
    }

    public void setTemplateSpec(ImageSpec templateSpec) {
        this.templateSpec = templateSpec;
    }

    public boolean isAllowEmpty() {
        return allowEmpty;
    }

    public void setAllowEmpty(boolean allowEmpty) {
        this.allowEmpty = allowEmpty;
    }
}
