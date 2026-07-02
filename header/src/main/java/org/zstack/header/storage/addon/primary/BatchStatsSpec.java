package org.zstack.header.storage.addon.primary;

import java.util.Collection;
import java.util.Collections;

public class BatchStatsSpec {
    private Collection<String> installPaths = Collections.emptyList();
    private Collection<String> snapshotInstallPaths = Collections.emptyList();

    public Collection<String> getInstallPaths() {
        return installPaths;
    }

    public void setInstallPaths(Collection<String> installPaths) {
        this.installPaths = installPaths == null ? Collections.emptyList() : installPaths;
    }

    public Collection<String> getSnapshotInstallPaths() {
        return snapshotInstallPaths;
    }

    public void setSnapshotInstallPaths(Collection<String> snapshotInstallPaths) {
        this.snapshotInstallPaths = snapshotInstallPaths == null ? Collections.emptyList() : snapshotInstallPaths;
    }

    public boolean isWithSnapshot() {
        return !snapshotInstallPaths.isEmpty();
    }
}
