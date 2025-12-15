package org.zstack.sdk.zmigrate.api;



public class GetZMigrateInfosResult {
    public java.lang.String zMigrateVmInstanceStatus;
    public void setZMigrateVmInstanceStatus(java.lang.String zMigrateVmInstanceStatus) {
        this.zMigrateVmInstanceStatus = zMigrateVmInstanceStatus;
    }
    public java.lang.String getZMigrateVmInstanceStatus() {
        return this.zMigrateVmInstanceStatus;
    }

    public java.lang.String version;
    public void setVersion(java.lang.String version) {
        this.version = version;
    }
    public java.lang.String getVersion() {
        return this.version;
    }

    public long platforms;
    public void setPlatforms(long platforms) {
        this.platforms = platforms;
    }
    public long getPlatforms() {
        return this.platforms;
    }

    public long gateways;
    public void setGateways(long gateways) {
        this.gateways = gateways;
    }
    public long getGateways() {
        return this.gateways;
    }

    public long migrateJobs;
    public void setMigrateJobs(long migrateJobs) {
        this.migrateJobs = migrateJobs;
    }
    public long getMigrateJobs() {
        return this.migrateJobs;
    }

    public long zMigrateStartTime;
    public void setZMigrateStartTime(long zMigrateStartTime) {
        this.zMigrateStartTime = zMigrateStartTime;
    }
    public long getZMigrateStartTime() {
        return this.zMigrateStartTime;
    }

}
