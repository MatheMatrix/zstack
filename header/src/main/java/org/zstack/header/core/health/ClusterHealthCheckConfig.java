package org.zstack.header.core.health;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClusterHealthCheckConfig {

    private static final Map<String, ClusterHealthCheckCallback> callbacks = new ConcurrentHashMap<>();

    /**
    * Register callback for ClusterHealthCheckCallback.
    *
    * @param clusterType clusterType
    * @param callback callback
    */
    public static void registerHealthCheckCallback(String clusterType, ClusterHealthCheckCallback callback) {
        callbacks.put(clusterType, callback);
    }

    /**
     * Get ClusterHealthCheckCallback
     *
     * @param clusterType cluster type eg: zaku
     * @return return null if not registered
     */
    public static ClusterHealthCheckCallback getHealthCheckCallback(String clusterType) {
        return callbacks.get(clusterType);
    }


    /**
     * Check cluster through ClusterHealthCheckCallback
     *
     * @param clusterType clusterType
     * @param clusterId clusterId
     * @return return true if unhealthy
     */
    public static boolean isClusterUnhealthy(String clusterType, String clusterId) {
        ClusterHealthCheckCallback callback = callbacks.get(clusterType);
        if (callback == null) {
            return false;
        }
        return callback.isClusterUnhealthy(clusterId);
    }
}