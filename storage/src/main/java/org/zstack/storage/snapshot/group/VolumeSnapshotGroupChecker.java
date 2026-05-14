package org.zstack.storage.snapshot.group;

import org.zstack.core.db.Q;
import org.zstack.header.storage.snapshot.group.VolumeSnapshotGroupAvailability;
import org.zstack.header.storage.snapshot.group.VolumeSnapshotGroupRefVO;
import org.zstack.header.storage.snapshot.group.VolumeSnapshotGroupRefVO_;
import org.zstack.header.storage.snapshot.group.VolumeSnapshotGroupVO;
import org.zstack.header.storage.snapshot.group.VolumeSnapshotGroupVO_;
import org.zstack.header.vo.ResourceVO;
import org.zstack.header.volume.Volume;
import org.zstack.header.volume.VolumeType;
import org.zstack.header.volume.VolumeVO;
import org.zstack.header.volume.VolumeVO_;

import javax.persistence.Tuple;
import java.util.*;
import java.util.stream.Collectors;

import static org.zstack.core.Platform.i18n;

/**
 * Created by MaJin on 2019/7/12.
 */
public class VolumeSnapshotGroupChecker {
    public static boolean isAvailable(String uuid) {
        return getAvailability(uuid).isAvailable();
    }

    /**
     * Find all incomplete snapshot groups on a VM.
     * An incomplete group is one where part of its refs have snapshotDeleted=true
     * but at least one ref is still alive (snapshotDeleted=false).
     * Such groups represent a "debt" that pollutes subsequent group/VM operations.
     *
     * @param vmInstanceUuid    the VM to inspect
     * @param excludeGroupUuid  group uuid to exclude from the result (e.g. when the caller is
     *                          itself trying to delete that group, do not flag it as a blocker);
     *                          pass null to include all groups
     * @return list of incomplete group uuids (excluding excludeGroupUuid); empty if none
     */
    public static List<String> findIncompleteGroupsOnVm(String vmInstanceUuid, String excludeGroupUuid) {
        if (vmInstanceUuid == null) {
            return Collections.emptyList();
        }

        List<String> groupUuids = Q.New(VolumeSnapshotGroupVO.class)
                .select(VolumeSnapshotGroupVO_.uuid)
                .eq(VolumeSnapshotGroupVO_.vmInstanceUuid, vmInstanceUuid)
                .listValues();

        List<String> incomplete = new ArrayList<>();
        for (Object o : groupUuids) {
            String guuid = o.toString();
            if (guuid.equals(excludeGroupUuid)) {
                continue;
            }
            long deletedRefs = Q.New(VolumeSnapshotGroupRefVO.class)
                    .eq(VolumeSnapshotGroupRefVO_.volumeSnapshotGroupUuid, guuid)
                    .eq(VolumeSnapshotGroupRefVO_.snapshotDeleted, true).count();
            if (deletedRefs == 0) {
                continue;
            }
            long totalRefs = Q.New(VolumeSnapshotGroupRefVO.class)
                    .eq(VolumeSnapshotGroupRefVO_.volumeSnapshotGroupUuid, guuid).count();
            if (deletedRefs < totalRefs) {
                incomplete.add(guuid);
            }
        }
        return incomplete;
    }

    public static List<VolumeSnapshotGroupAvailability> getAvailability(List<String> uuids) {
        List<VolumeSnapshotGroupAvailability> results = new ArrayList<>();
        List<VolumeSnapshotGroupVO> groups = Q.New(VolumeSnapshotGroupVO.class)
                .in(VolumeSnapshotGroupVO_.uuid, uuids)
                .list();

        Map<String, String> groupVmRef = groups.stream().collect(Collectors.toMap(ResourceVO::getUuid, VolumeSnapshotGroupVO::getVmInstanceUuid));
        Map<String, Map<String, String>> vmAttachedVols = Q.New(VolumeVO.class)
                .in(VolumeVO_.vmInstanceUuid, groupVmRef.values().stream().distinct().collect(Collectors.toList()))
                .select(VolumeVO_.vmInstanceUuid, VolumeVO_.uuid, VolumeVO_.name).listTuple().stream()
                .collect(Collectors.groupingBy(t -> t.get(0, String.class),
                        Collectors.toMap(it -> ((Tuple) it).get(1, String.class), it -> ((Tuple) it).get(2, String.class))));

        Map<String, Map<String, String>> vmAttachedVolsWithoutMemoryVolume = Q.New(VolumeVO.class)
                .notEq(VolumeVO_.type, VolumeType.Memory)
                .in(VolumeVO_.vmInstanceUuid, groupVmRef.values().stream().distinct().collect(Collectors.toList()))
                .select(VolumeVO_.vmInstanceUuid, VolumeVO_.uuid, VolumeVO_.name).listTuple().stream()
                .collect(Collectors.groupingBy(t -> t.get(0, String.class),
                        Collectors.toMap(it -> ((Tuple) it).get(1, String.class), it -> ((Tuple) it).get(2, String.class))));

        for (VolumeSnapshotGroupVO group : groups) {
            if (!hasMemorySnapshot(group)) {
                results.add(getAvailability(group, vmAttachedVolsWithoutMemoryVolume.get(group.getVmInstanceUuid())));
                continue;
            }

            results.add(getAvailability(group, vmAttachedVols.get(group.getVmInstanceUuid())));
        }

        return results;
    }

    public static VolumeSnapshotGroupAvailability getAvailability(String uuid) {
        VolumeSnapshotGroupVO group = Q.New(VolumeSnapshotGroupVO.class).eq(VolumeSnapshotGroupVO_.uuid, uuid).find();
        return getAvailability(group);
    }

    private static boolean hasMemorySnapshot(VolumeSnapshotGroupVO group) {
        return group.getVolumeSnapshotRefs()
                .stream()
                .anyMatch(ref -> VolumeType.Memory.toString().equals(ref.getVolumeType()));
    }

    public static VolumeSnapshotGroupAvailability getAvailability(VolumeSnapshotGroupVO group) {
        List<Tuple> attachedVolUuids;
        if (hasMemorySnapshot(group)) {
            attachedVolUuids = Q.New(VolumeVO.class).select(VolumeVO_.uuid, VolumeVO_.name)
                    .eq(VolumeVO_.vmInstanceUuid, group.getVmInstanceUuid())
                    .listTuple();
        } else {
            attachedVolUuids = Q.New(VolumeVO.class).select(VolumeVO_.uuid, VolumeVO_.name)
                    .eq(VolumeVO_.vmInstanceUuid, group.getVmInstanceUuid()).notEq(VolumeVO_.type, VolumeType.Memory)
                    .listTuple();
        }
        return getAvailability(group, attachedVolUuids.stream().collect(
                Collectors.toMap(it -> it.get(0, String.class), it -> it.get(1, String.class))));
    }

    private static VolumeSnapshotGroupAvailability getAvailability(VolumeSnapshotGroupVO group, Map<String, String> attachedVolUuidName) {
        List<String> reason = new ArrayList<>();
        List<String> deletedSnapshotInfos = new ArrayList<>();
        List<String> detachedVolInfos = new ArrayList<>();

        Set<String> attachedUuids = new HashSet<>(attachedVolUuidName.keySet());
        Map<String, String> newAttachedVol = new HashMap<>(attachedVolUuidName);
        for (VolumeSnapshotGroupRefVO ref : group.getVolumeSnapshotRefs()) {
            if (ref.isSnapshotDeleted()) {
                deletedSnapshotInfos.add(String.format("[uuid:%s, name:%s]", ref.getVolumeSnapshotUuid(), ref.getVolumeSnapshotName()));
            } else if (!attachedUuids.contains(ref.getVolumeUuid())) {
                detachedVolInfos.add(String.format("[uuid:%s, name:%s]", ref.getVolumeUuid(), ref.getVolumeName()));
            }

            newAttachedVol.remove(ref.getVolumeUuid());
        }

        if (!deletedSnapshotInfos.isEmpty()) {
            reason.add(i18n("snapshot(s) %s in the group has been deleted, can only revert one by one.", String.join(", ", deletedSnapshotInfos)));
        }

        if (!detachedVolInfos.isEmpty()) {
            reason.add(i18n("volume(s) %s is no longer attached, can only revert one by one. " +
                    "If you need to group revert, please re-attach it.", String.join(", ", detachedVolInfos)));
        }

        if (!newAttachedVol.isEmpty()) {
            String volInfos = String.join(", ", newAttachedVol.entrySet().stream().map(e ->
                    String.format("[uuid:%s, name:%s]", e.getKey(), e.getValue()))
                    .collect(Collectors.toList()));
            reason.add(i18n("new volume(s) %s attached after snapshot point, can only revert one by one. " +
                    "If you need to group revert, please detach it.", volInfos));
        }
        return new VolumeSnapshotGroupAvailability(group.getUuid(), String.join("\n", reason));
    }
}
