package org.zstack.securitymachine;

import org.zstack.securitymachine.api.APICreateWestoneSecretResourcePoolMsg;
import org.zstack.securitymachine.api.APICreateZhongfuSecretResourcePoolMsg;
import org.zstack.securitymachine.api.APIWestoneTestMsg;
import org.zstack.header.identity.rbac.RBACDescription;

public class RBACInfo implements RBACDescription {
	@Override
	public void permissions() {
		permissionBuilder().name("securityMachineThirdParty").adminOnlyAPIs(
				APIWestoneTestMsg.class,
				APICreateZhongfuSecretResourcePoolMsg.class,
				APICreateWestoneSecretResourcePoolMsg.class).build();
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
