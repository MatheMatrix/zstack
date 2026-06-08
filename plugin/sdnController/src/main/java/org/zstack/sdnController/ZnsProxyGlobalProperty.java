package org.zstack.sdnController;

import org.zstack.core.GlobalProperty;
import org.zstack.core.GlobalPropertyDefinition;

@GlobalPropertyDefinition
public class ZnsProxyGlobalProperty {
    @GlobalProperty(name = "ZnsProxy.packagePath", defaultValue = "/var/lib/zstack/zns-proxy/package/zns-proxy.bin")
    public static String PACKAGE_PATH;

    @GlobalProperty(name = "ZnsProxy.packageRemotePath", defaultValue = "/var/lib/zstack/zns-proxy/package")
    public static String PACKAGE_REMOTE_PATH;

    @GlobalProperty(name = "ZnsProxy.packageRepositoryPath", defaultValue = "/var/lib/zstack/zns-proxy/package")
    public static String PACKAGE_REPOSITORY_PATH;

    @GlobalProperty(name = "ZnsProxy.proxyPackageName", defaultValue = "zns-proxy.bin")
    public static String PROXY_PACKAGE_NAME;

    @GlobalProperty(name = "ZnsProxy.serviceName", defaultValue = "zstack-zns-proxy")
    public static String SERVICE_NAME;

    @GlobalProperty(name = "ZnsProxy.configPath", defaultValue = "/etc/zstack-zns/zns-proxy.toml")
    public static String CONFIG_PATH;

    @GlobalProperty(name = "ZnsProxy.listenAddress", defaultValue = "0.0.0.0:7890")
    public static String LISTEN_ADDRESS;
}
