package org.zstack.header.core.health;

/**
 * Cluster health checker.
 */
public interface ClusterHealthCheckCallback {

    /*
     * check cluster status
     *
     * @param clusterId cluster id
     * @return return true if unhealthy
     */
    boolean isClusterUnhealthy(String clusterId);
}