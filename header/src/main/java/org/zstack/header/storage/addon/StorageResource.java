package org.zstack.header.storage.addon;

public abstract class StorageResource {
    protected String installPath;
    protected Long size;
    protected Long actualSize;
    protected String parentUri;

    public String getInstallPath() {
        return installPath;
    }

    public void setInstallPath(String installPath) {
        this.installPath = installPath;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public Long getResourceActualSize() {
        return actualSize;
    }

    public void setActualSize(Long actualSize) {
        this.actualSize = actualSize;
    }

    public void setActualSize(long actualSize) {
        this.actualSize = actualSize;
    }

    public String getParentUri() {
        return parentUri;
    }

    public void setParentUri(String parentUri) {
        this.parentUri = parentUri;
    }
}
