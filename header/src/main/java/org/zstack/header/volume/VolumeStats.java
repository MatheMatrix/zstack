package org.zstack.header.volume;

import org.zstack.header.storage.primary.StorageResourceStats;

public class VolumeStats extends StorageResourceStats {
    protected String format;
    /**
     * The parent uri of the volume, vendor://pool/path@snapshot or snapshot://uuid
     */
    protected String parentUri;

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

    public void setFormat(String format) {
        this.format = format;
    }

    public String getFormat() {
        return format;
    }

    public void setParentUri(String parentUri) {
        this.parentUri = parentUri;
    }

    public String getParentUri() {
        return parentUri;
    }

    @Deprecated
    public String getRunStatus() {
        return runStatus;
    }

    public void setRunStatus(String runStatus) {
        this.runStatus = runStatus;
    }
}
