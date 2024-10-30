package org.zstack.core.config;

import org.zstack.header.identity.rbac.RBACDescription;
import org.zstack.header.vo.ResourceVO;

public class RBACInfo implements RBACDescription {
    @Override
    public void permissions() {
        permissionBuilder()
                .name("global-config")
                .adminOnlyAPIs("org.zstack.core.config.**")
                .build();
    }

    @Override
    public void contributeToRoles() {
    }

    @Override
    public void roles() {

    }

    @Override
    public void globalReadableResources() {

    }
}
