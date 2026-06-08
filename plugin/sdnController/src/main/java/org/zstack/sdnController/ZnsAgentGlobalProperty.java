package org.zstack.sdnController;

import org.zstack.core.GlobalProperty;
import org.zstack.core.GlobalPropertyDefinition;

@GlobalPropertyDefinition
public class ZnsAgentGlobalProperty {
    @GlobalProperty(name = "ZnsAgent.packagePath", defaultValue = "/var/lib/zstack/zns-agent/package/zns-agent.bin")
    public static String PACKAGE_PATH;

    @GlobalProperty(name = "ZnsAgent.packageRemotePath", defaultValue = "/var/lib/zstack/zns-agent/package")
    public static String PACKAGE_REMOTE_PATH;

    @GlobalProperty(name = "ZnsAgent.packageRepositoryPath", defaultValue = "/var/lib/zstack/zns-agent/package")
    public static String PACKAGE_REPOSITORY_PATH;

    @GlobalProperty(name = "ZnsAgent.agentPackageName", defaultValue = "zns-agent.bin")
    public static String AGENT_PACKAGE_NAME;

    @GlobalProperty(name = "ZnsAgent.serviceName", defaultValue = "zstack-zns-agent")
    public static String SERVICE_NAME;

    @GlobalProperty(name = "ZnsAgent.configPath", defaultValue = "/etc/zstack-zns/zns-agent.toml")
    public static String CONFIG_PATH;

    @GlobalProperty(name = "ZnsAgent.logPath", defaultValue = "/var/log/zstack/zns-agent/zns-agent.log")
    public static String LOG_PATH;

    @GlobalProperty(name = "ZnsAgent.listenAddress", defaultValue = "0.0.0.0:8090")
    public static String LISTEN_ADDRESS;

    @GlobalProperty(name = "ZnsAgent.reconnectSeconds", defaultValue = "3")
    public static int RECONNECT_SECONDS;
}
