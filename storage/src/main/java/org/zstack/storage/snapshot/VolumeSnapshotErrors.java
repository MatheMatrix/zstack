package org.zstack.storage.snapshot;

/**
 */
public enum VolumeSnapshotErrors {
    NOT_IN_CORRECT_STATE(1000),
    FULL_SNAPSHOT_ERROR(1001),
    BATCH_DELETE_ERROR(1002),
    /* ZSV-10538: snapshot is currently being deleted by an in-flight task */
    DELETING_IN_PROGRESS(1101),
    /* ZSV-10538: previous deletion did not finish; resume by retrying the same API */
    DELETING_RETRY_HINT(1102),
    /* ZSV-10538: operation rejected because snapshot is in Deleting status */
    OPERATION_REJECTED_DURING_DELETING(1103);

    private String code;

    VolumeSnapshotErrors(int id) {
        code = String.format("VOLUME_SNAPSHOT.%s", id);
    }

    @Override
    public String toString() {
        return code;
    }
}
