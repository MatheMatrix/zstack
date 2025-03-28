package org.zstack.compute.vm;

import org.zstack.core.db.Q;
import org.zstack.header.storage.snapshot.*;
import org.zstack.header.vm.VmInstanceInventory;
import org.zstack.header.volume.VolumeInventory;

import java.util.*;
import java.util.stream.Collectors;

public class VmVolumeSnapshotChainUtil {
    static public Map<String, List<String>> getVmVolumesAliveSnapshotChain(VmInstanceInventory vmInstanceInventory) {
        Map<String, String> volumesInstallPathByUuid = vmInstanceInventory.getAllDiskVolumes().stream().collect(Collectors.toMap(VolumeInventory::getUuid, VolumeInventory::getInstallPath));

        List<String> currentTreeUuids = Q.New(VolumeSnapshotTreeVO.class)
                .eq(VolumeSnapshotTreeVO_.current, true)
                .in(VolumeSnapshotVO_.volumeUuid, volumesInstallPathByUuid.keySet())
                .select(VolumeSnapshotTreeVO_.uuid).listValues();

        if (currentTreeUuids.isEmpty()) {
            return Collections.emptyMap();
        }

        List<VolumeSnapshotVO> aliveChainVolumeSnapshots = Q.New(VolumeSnapshotVO.class)
                .in(VolumeSnapshotVO_.volumeUuid, volumesInstallPathByUuid.keySet())
                .in(VolumeSnapshotVO_.treeUuid, currentTreeUuids)
                .list();

        Map<String, List<VolumeSnapshotVO>> aliveChainVolumeSnapshotsByVolumeUuid = new HashMap<>();
        volumesInstallPathByUuid.keySet().forEach(volumeUuid -> aliveChainVolumeSnapshots.forEach(volumeSnapshotVO -> {
            if (Objects.equals(volumeSnapshotVO.getVolumeUuid(), volumeUuid)) {
                if (!aliveChainVolumeSnapshotsByVolumeUuid.containsKey(volumeUuid)) {
                    aliveChainVolumeSnapshotsByVolumeUuid.put(volumeUuid, new ArrayList<>());
                }
                aliveChainVolumeSnapshotsByVolumeUuid.get(volumeUuid).add(volumeSnapshotVO);
            }
        }));

        Map<String, List<String>> volumesSnapshotChain = new HashMap<>();
        aliveChainVolumeSnapshotsByVolumeUuid.forEach((volumeUuid, vos) -> {
            String latestSnapshotUuid = vos.stream().filter(VolumeSnapshotAO::isLatest).map(VolumeSnapshotVO::getUuid).findFirst().orElse(null);
            List<String> aliveChainInstallPath = new ArrayList<>();
            aliveChainInstallPath.add(volumesInstallPathByUuid.get(volumeUuid));
            aliveChainInstallPath.addAll(VolumeSnapshotTree.fromVOs(vos).getAliveChainInstallPath(latestSnapshotUuid));
            volumesSnapshotChain.put(volumeUuid, aliveChainInstallPath);
        });
        return volumesSnapshotChain;
    }
}
