package org.zstack.header.tpm;

import org.zstack.header.identity.rbac.RBACDescription;
import org.zstack.header.tpm.api.APIAddTpmMsg;
import org.zstack.header.tpm.api.APIGetTpmCapabilityMsg;
import org.zstack.header.tpm.api.APIQueryTpmMsg;
import org.zstack.header.tpm.api.APIRemoveTpmMsg;
import org.zstack.header.tpm.api.APIUpdateTpmMsg;

public class RBACInfo implements RBACDescription {
    @Override
    public void permissions() {
        permissionBuilder()
                .adminOnlyAPIs(
                        APIAddTpmMsg.class,
                        APIGetTpmCapabilityMsg.class,
                        APIQueryTpmMsg.class,
                        APIRemoveTpmMsg.class,
                        APIUpdateTpmMsg.class
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
