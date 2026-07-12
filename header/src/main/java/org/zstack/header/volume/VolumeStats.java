package org.zstack.header.volume;

import org.zstack.header.storage.addon.StorageResource;

public class VolumeStats extends StorageResource {
    protected String format;

    // TODO(shenjin): remove it
    @Deprecated
    protected String runStatus;

    public VolumeStats(String installPath, Long actualSize) {
        this.installPath = installPath;
        this.actualSize = actualSize;
    }


    public VolumeStats(String installPath, Long actualSize, Long size) {
        this.installPath = installPath;
        this.actualSize = actualSize;
        this.size = size;
    }

    public VolumeStats() {
    }

    public Long getActualSize() {
        return actualSize;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getFormat() {
        return format;
    }

    @Deprecated
    public String getRunStatus() {
        return runStatus;
    }

    public void setRunStatus(String runStatus) {
        this.runStatus = runStatus;
    }
}
