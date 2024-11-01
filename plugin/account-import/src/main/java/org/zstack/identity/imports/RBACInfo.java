package org.zstack.identity.imports;

import org.zstack.header.identity.rbac.RBACDescription;
import org.zstack.header.rest.SDKPackage;
import org.zstack.identity.imports.api.APIQueryThirdPartyAccountSourceBindingMsg;

@SDKPackage(packageName = "org.zstack.sdk.identity.imports")
public class RBACInfo implements RBACDescription {
    @Override
    public String permissionName() {
        return "account-imports";
    }

    @Override
    public void permissions() {
        permissionBuilder()
                .normalAPIs(APIQueryThirdPartyAccountSourceBindingMsg.class)
                .communityAvailable()
                .zsvProAvailable()
                .build();
    }

    @Override
    public void contributeToRoles() {
        roleContributorBuilder()
                .roleName("identity")
                .actionsInThisPermission()
                .build();
    }
}
