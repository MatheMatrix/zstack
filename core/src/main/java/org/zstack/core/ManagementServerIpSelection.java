package org.zstack.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ManagementServerIpSelection {
    public enum FailureReason {
        NO_SAME_FAMILY_MGT,
        ROUTE_LOOKUP_FAILED,
        ROUTE_SOURCE_NOT_MANAGEMENT
    }

    private final String selectedIp;
    private final String remoteIp;
    private final int remoteFamily;
    private final List<String> managementServerIps;
    private final String routeSourceIp;
    private final String routeError;
    private final FailureReason failureReason;

    private ManagementServerIpSelection(String selectedIp, String remoteIp, int remoteFamily,
                                        List<String> managementServerIps, String routeSourceIp,
                                        String routeError, FailureReason failureReason) {
        this.selectedIp = selectedIp;
        this.remoteIp = remoteIp;
        this.remoteFamily = remoteFamily;
        this.managementServerIps = Collections.unmodifiableList(new ArrayList<>(managementServerIps));
        this.routeSourceIp = routeSourceIp;
        this.routeError = routeError;
        this.failureReason = failureReason;
    }

    public static ManagementServerIpSelection success(String selectedIp, String remoteIp, int remoteFamily,
                                                       List<String> managementServerIps, String routeSourceIp) {
        return new ManagementServerIpSelection(selectedIp, remoteIp, remoteFamily,
                managementServerIps, routeSourceIp, null, null);
    }

    public static ManagementServerIpSelection failure(String remoteIp, int remoteFamily,
                                                       List<String> managementServerIps, String routeSourceIp,
                                                       String routeError, FailureReason failureReason) {
        return new ManagementServerIpSelection(null, remoteIp, remoteFamily,
                managementServerIps, routeSourceIp, routeError, failureReason);
    }

    public boolean isSuccess() {
        return failureReason == null;
    }

    public String getSelectedIp() {
        return selectedIp;
    }

    public String getRemoteIp() {
        return remoteIp;
    }

    public int getRemoteFamily() {
        return remoteFamily;
    }

    public List<String> getManagementServerIps() {
        return managementServerIps;
    }

    public String getRouteSourceIp() {
        return routeSourceIp;
    }

    public String getRouteError() {
        return routeError;
    }

    public FailureReason getFailureReason() {
        return failureReason;
    }
}
