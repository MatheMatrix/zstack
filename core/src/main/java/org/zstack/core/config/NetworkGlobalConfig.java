package org.zstack.core.config;

/**
 * Global configuration entries for network-related settings.
 */
@GlobalConfigDefinition
public class NetworkGlobalConfig {
    public static final String CATEGORY = "network";

    @GlobalConfigValidation(validValues = {"true", "false"})
    @GlobalConfigDef(
            type = Boolean.class,
            defaultValue = "false",
            description = "When true, the management node prefers IPv6 addresses on dual-stack hosts. " +
                    "Has no effect on IPv4-only or IPv6-only hosts."
    )
    public static GlobalConfig PREFER_IPV6 = new GlobalConfig(CATEGORY, "management.server.prefer.ipv6");
}
