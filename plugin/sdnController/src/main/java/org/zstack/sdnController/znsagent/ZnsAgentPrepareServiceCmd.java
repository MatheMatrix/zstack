package org.zstack.sdnController.znsagent;

import java.util.List;

public class ZnsAgentPrepareServiceCmd {
    public static final String COMMAND_PATH = "/zns/notify/prepare-service";

    public String computerManagerUuid;
    public List<String> hostUuids;
    public List<String> controllerAddresses;
    public List<String> controllerIps;
    public String agentVersion;
    public String packageName;
    public String packageUrl;
    public String sha256;
}
