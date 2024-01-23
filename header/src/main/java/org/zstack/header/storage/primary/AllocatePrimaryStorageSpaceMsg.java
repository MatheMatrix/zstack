package org.zstack.header.storage.primary;

public class AllocatePrimaryStorageSpaceMsg extends AllocatePrimaryStorageMsg {
    private boolean force;
    private String requiredInstallUri;
    private boolean allocatedInstallUrlDryRun;

    public boolean isForce() {
        return force;
    }

    public void setForce(boolean force) {
        this.force = force;
    }

    public String getRequiredInstallUri() {
        return requiredInstallUri;
    }

    public void setRequiredInstallUri(String requiredInstallUrl) {
        this.requiredInstallUri = requiredInstallUrl;
    }

    public boolean isAllocatedInstallUrlDryRun() {
        return allocatedInstallUrlDryRun;
    }

    public void setAllocatedInstallUrlDryRun(boolean allocatedInstallUrlDryRun) {
        this.allocatedInstallUrlDryRun = allocatedInstallUrlDryRun;
    }
}
