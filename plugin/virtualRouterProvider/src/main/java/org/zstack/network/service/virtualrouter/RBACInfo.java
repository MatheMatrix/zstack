package org.zstack.network.service.virtualrouter;

import org.zstack.header.identity.rbac.RBACDescription;

public class RBACInfo implements RBACDescription {
    @Override
    public String permissionName() {
        return "vrouter";
    }

    @Override
    public void permissions() {
        permissionBuilder()
                .communityAvailable()
                .zsvBasicAvailable()
                .zsvProAvailable()
                .build();
    }

    @Override
    public void roles() {
        roleBuilder()
                .uuid("74a27f7f461e4601877c2728c52ec9e5")
                .permissionBaseOnThis()
                .permissionsByName("vip")
                .build();
    }
}
