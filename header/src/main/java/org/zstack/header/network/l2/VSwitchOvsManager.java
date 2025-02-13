package org.zstack.header.network.l2;

import org.zstack.header.host.HypervisorType;

public interface VSwitchOvsManager {
    public OvsVSwitchBackend getOvsVSwitchBackend(HypervisorType type);
}
