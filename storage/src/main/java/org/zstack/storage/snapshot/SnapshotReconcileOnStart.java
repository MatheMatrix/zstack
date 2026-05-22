package org.zstack.storage.snapshot;

import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Configurable;
import org.zstack.core.db.Q;
import org.zstack.header.managementnode.ManagementNodeReadyExtensionPoint;
import org.zstack.header.storage.snapshot.VolumeSnapshotStatus;
import org.zstack.header.storage.snapshot.VolumeSnapshotVO;
import org.zstack.header.storage.snapshot.VolumeSnapshotVO_;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.List;

/**
 * ZSV-10538: passive reconcile for snapshots stuck in Deleting status.
 *
 * When MN crashes mid-deletion, the VolumeSnapshotVO row stays in Deleting (this is the
 * persistent latch designed in docs/snapshot-single-delete/18-idempotency-design-status-deleting.md).
 * On the next MN startup we scan for such rows and emit a WARN log per stuck snapshot so
 * operators can decide whether to resume by re-issuing DeleteVolumeSnapshot{,Group}.
 *
 * NOTE: we deliberately do NOT auto-retry. SBLK host failover / lock conflicts make
 * unattended retry risky; the safer choice is to let the operator re-issue the same API
 * which routes through the idempotent resume path in VolumeSnapshotTreeBase.
 */
@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class SnapshotReconcileOnStart implements ManagementNodeReadyExtensionPoint {
    private static final CLogger logger = Utils.getLogger(SnapshotReconcileOnStart.class);

    @Override
    public void managementNodeReady() {
        List<VolumeSnapshotVO> stuck = Q.New(VolumeSnapshotVO.class)
                .eq(VolumeSnapshotVO_.status, VolumeSnapshotStatus.Deleting)
                .list();
        if (stuck.isEmpty()) {
            return;
        }
        logger.warn(String.format(
                "[ZSV-10538] Found %d snapshot(s) in Deleting status at MN startup. " +
                        "These were left mid-deletion by a previous MN session. No automatic action taken. " +
                        "Re-issue APIDeleteVolumeSnapshot{,Group} to resume; the API is idempotent.",
                stuck.size()));
        for (VolumeSnapshotVO v : stuck) {
            logger.warn(String.format(
                    "  - snapshot[uuid:%s, name:%s, volumeUuid:%s, treeUuid:%s, deletingSince:%s, installPath:%s]",
                    v.getUuid(), v.getName(), v.getVolumeUuid(), v.getTreeUuid(),
                    v.getDeletingSince(), v.getPrimaryStorageInstallPath()));
        }
    }
}
