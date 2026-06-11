package org.zstack.sdnController.znsproxy;

import java.util.List;

public class ZnsProxyUnprepareServiceCmd {
    public static final String COMMAND_PATH = "/zns/notify/unprepare-service";

    public String computeManagerUuid;
    public List<String> hostUuids;
}
