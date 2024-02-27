package org.zstack.securitymachine;

import org.zstack.header.securitymachine.AddSecurityMachineMessage;

public interface AddWestoneSecurityMachineMessage extends AddSecurityMachineMessage {
	String getPassword();

	int getPort();
}
