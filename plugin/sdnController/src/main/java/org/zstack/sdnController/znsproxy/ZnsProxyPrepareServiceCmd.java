package org.zstack.sdnController.znsproxy;

public class ZnsProxyPrepareServiceCmd {
    public static final String COMMAND_PATH = "/zns/notify/prepare-service";

    public String computeManagerUuid;
    public String hostUuid;
    public String managementIp;
    public String sdnControllerUuid;
    public int proxyListenPort;
    public String proxyVersion;
    public String packageName;
    public String packageUrl;
    public String sha256;
}
