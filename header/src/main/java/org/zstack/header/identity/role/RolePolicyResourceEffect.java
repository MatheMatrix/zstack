package org.zstack.header.identity.role;

public enum RolePolicyResourceEffect {
    Single,

    /**
     * allow in a range (clusterUuid, zoneUuid)
     */
    Range,
}
