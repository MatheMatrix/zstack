package org.zstack.network.securitygroup;

public interface SecurityGroupConstant {
    public static String SERVICE_ID = "securityGroup";

    public static String ACTION_CATEGORY = "securityGroup";

    public static String SECURITY_GROUP_PROVIDER_TYPE = "SecurityGroup";
    public static String SECURITY_GROUP_NETWORK_SERVICE_TYPE = "SecurityGroup";
    public static String WORLD_OPEN_CIDR = "0.0.0.0/0";
    public static String WORLD_OPEN_CIDR_IPV6 = "::/0";
    int ONE_API_RULES_MAX_NUM = 1000;
}
