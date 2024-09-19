package org.zstack.header.vo;

import org.zstack.header.identity.rbac.RBACDescription;

public class RBACInfo implements RBACDescription {
    @Override
    public String permissionName() {
        return "core-resource";
    }

    @Override
    public void permissions() {
        permissionBuilder()
                .normalAPIs(APIGetResourceNamesMsg.class)
                .communityAvailable()
                .zsvBasicAvailable()
                .zsvProAvailable()
                .build();
    }

    @Override
    public void contributeToRoles() {
        contributeNormalApiToOtherRole();
    }
}
