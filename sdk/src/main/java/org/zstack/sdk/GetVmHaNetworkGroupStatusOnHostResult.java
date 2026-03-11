package org.zstack.sdk;



public class GetVmHaNetworkGroupStatusOnHostResult {
    public java.lang.String hostStatus;
    public void setHostStatus(java.lang.String hostStatus) {
        this.hostStatus = hostStatus;
    }
    public java.lang.String getHostStatus() {
        return this.hostStatus;
    }

    public boolean enableHa;
    public void setEnableHa(boolean enableHa) {
        this.enableHa = enableHa;
    }
    public boolean getEnableHa() {
        return this.enableHa;
    }

    public boolean monitorEnabled;
    public void setMonitorEnabled(boolean monitorEnabled) {
        this.monitorEnabled = monitorEnabled;
    }
    public boolean getMonitorEnabled() {
        return this.monitorEnabled;
    }

    public java.util.List networkGroupDetails;
    public void setNetworkGroupDetails(java.util.List networkGroupDetails) {
        this.networkGroupDetails = networkGroupDetails;
    }
    public java.util.List getNetworkGroupDetails() {
        return this.networkGroupDetails;
    }

}
