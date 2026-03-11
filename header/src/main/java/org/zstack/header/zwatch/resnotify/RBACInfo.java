package org.zstack.header.zwatch.resnotify;

import org.zstack.header.identity.rbac.RBACDescription;

public class RBACInfo implements RBACDescription {
    @Override
    public void permissions() {
        permissionBuilder()
                .normalAPIs(
                        APISubscribeResNotifyMsg.class,
                        APIDeleteResNotifySubscriptionMsg.class,
                        APIUpdateResNotifySubscriptionMsg.class,
                        APIQueryResNotifySubscriptionMsg.class
                )
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
