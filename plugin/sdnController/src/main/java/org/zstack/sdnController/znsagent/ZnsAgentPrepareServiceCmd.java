package org.zstack.sdnController.znsagent;

import java.util.List;

public class ZnsAgentPrepareServiceCmd {
    public static final String COMMAND_PATH = "/zns/notify/prepare-service";

    public String computerManagerUuid;
    public List<String> hostUuids;
    public List<String> controllerIps;
}
