package org.zstack.storage.snapshot;

import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.cloudbus.CloudBusCallBack;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.SQL;
import org.zstack.header.core.ReturnValueCompletion;
import org.zstack.header.message.MessageReply;
import org.zstack.header.storage.primary.AskVolumeSnapshotCapabilityMsg;
import org.zstack.header.storage.primary.AskVolumeSnapshotCapabilityReply;
import org.zstack.header.storage.primary.PrimaryStorageConstant;
import org.zstack.header.storage.primary.VolumeSnapshotCapability;
import org.zstack.header.storage.snapshot.VolumeSnapshotStatus;
import org.zstack.header.storage.snapshot.VolumeSnapshotVO;
import org.zstack.header.storage.snapshot.VolumeSnapshotVO_;

import javax.persistence.Tuple;
import javax.persistence.TypedQuery;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

public class VolumeSnapshotSizeSyncHelper {
    private final CloudBus bus;
    private final DatabaseFacade dbf;

    public VolumeSnapshotSizeSyncHelper(CloudBus bus, DatabaseFacade dbf) {
        this.bus = bus;
        this.dbf = dbf;
    }

    public void isSnapshotSizeSyncRequired(String primaryStorageUuid, ReturnValueCompletion<Boolean> completion) {
        AskVolumeSnapshotCapabilityMsg msg = new AskVolumeSnapshotCapabilityMsg();
        msg.setPrimaryStorageUuid(primaryStorageUuid);
        bus.makeTargetServiceIdByResourceUuid(msg, PrimaryStorageConstant.SERVICE_ID, primaryStorageUuid);
        bus.send(msg, new CloudBusCallBack(completion) {
            @Override
            public void run(MessageReply reply) {
                if (!reply.isSuccess()) {
                    completion.fail(reply.getError());
                    return;
                }

                AskVolumeSnapshotCapabilityReply r = reply.castReply();
                VolumeSnapshotCapability cap = r.getCapability();
                completion.success(cap != null && cap.getMode() == VolumeSnapshotCapability.VolumeSnapshotMode.COPY_ON_WRITE);
            }
        });
    }

    public Map<String, String> getSnapshotUuidInstallPaths(String primaryStorageUuid, Collection<String> volumeUuids) {
        if (volumeUuids == null || volumeUuids.isEmpty()) {
            return Collections.emptyMap();
        }

        String sql = "select sp.uuid, sp.primaryStorageInstallPath from VolumeSnapshotVO sp " +
                "where sp.volumeUuid in :volumeUuids " +
                "and sp.primaryStorageUuid = :primaryStorageUuid " +
                "and sp.primaryStorageInstallPath is not null " +
                "and sp.status = :status";
        TypedQuery<Tuple> q = dbf.getEntityManager().createQuery(sql, Tuple.class);
        q.setParameter("volumeUuids", volumeUuids);
        q.setParameter("primaryStorageUuid", primaryStorageUuid);
        q.setParameter("status", VolumeSnapshotStatus.Ready);
        return q.getResultList().stream().collect(Collectors.toMap(
                t -> t.get(0, String.class),
                t -> t.get(1, String.class),
                (v1, v2) -> v1
        ));
    }

    public void updateSnapshotActualSizes(Map<String, Long> snapshotActualSizes) {
        if (snapshotActualSizes == null || snapshotActualSizes.isEmpty()) {
            return;
        }

        snapshotActualSizes.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue() > 0)
                .forEach(e -> SQL.New(VolumeSnapshotVO.class)
                        .eq(VolumeSnapshotVO_.uuid, e.getKey())
                        .set(VolumeSnapshotVO_.size, e.getValue())
                        .update());
    }
}
