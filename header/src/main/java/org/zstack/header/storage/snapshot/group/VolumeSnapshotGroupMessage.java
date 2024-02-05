package org.zstack.header.storage.snapshot.group;

import org.zstack.header.storage.snapshot.SnapshotBackendOperation;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by MaJin on 2019/7/9.
 */
public interface VolumeSnapshotGroupMessage {
    String getGroupUuid();

    SnapshotBackendOperation getBackendOperation();

    default List<String> getGroupUuids() {
        return new ArrayList<>();
    }
}
