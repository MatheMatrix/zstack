package org.zstack.kvm;

import org.zstack.core.config.GlobalConfigUpdateMsg;
import org.zstack.header.host.HostMessage;

public class GlobalConfigUpdateOnKvmHostMsg extends GlobalConfigUpdateMsg implements HostMessage {
    private String agentPath;
    private String hostUuid;
    @Override
    public String getHostUuid() {
        return hostUuid;
    }

    public String getAgentPath() {
        return agentPath;
    }

    public void setAgentPath(String agentPath) {
        this.agentPath = agentPath;
    }

    public void setHostUuid(String hostUuid) {
        this.hostUuid = hostUuid;
    }
}
