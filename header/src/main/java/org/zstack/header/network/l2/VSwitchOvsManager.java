package org.zstack.header.network.l2;

import org.zstack.header.host.HypervisorType;

public interface VSwitchOvsManager {
    public VSwitchOvsHypervisorFactory getHypervisorfactory(HypervisorType type);
}
