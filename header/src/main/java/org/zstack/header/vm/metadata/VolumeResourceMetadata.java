package org.zstack.header.vm.metadata;

import java.util.List;

public class VolumeResourceMetadata extends ResourceMetadata {
    private List<String> snapshotReferences;
    private List<String> snapshotReferenceTrees;

    public List<String> getSnapshotReferences() {
        return snapshotReferences;
    }

    public void setSnapshotReferences(List<String> snapshotReferences) {
        this.snapshotReferences = snapshotReferences;
    }

    public List<String> getSnapshotReferenceTrees() {
        return snapshotReferenceTrees;
    }

    public void setSnapshotReferenceTrees(List<String> snapshotReferenceTrees) {
        this.snapshotReferenceTrees = snapshotReferenceTrees;
    }
}
