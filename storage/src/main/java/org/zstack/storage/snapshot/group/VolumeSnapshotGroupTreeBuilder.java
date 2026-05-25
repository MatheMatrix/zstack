package org.zstack.storage.snapshot.group;

import org.zstack.core.db.Q;
import org.zstack.header.query.QueryCondition;
import org.zstack.header.query.QueryOp;
import org.zstack.header.storage.snapshot.APIQueryVolumeSnapshotTreeMsg;
import org.zstack.header.storage.snapshot.VolumeSnapshotInventory;
import org.zstack.header.storage.snapshot.VolumeSnapshotVO;
import org.zstack.header.storage.snapshot.VolumeSnapshotVO_;
import org.zstack.header.storage.snapshot.group.VolumeSnapshotGroupRefVO;
import org.zstack.header.storage.snapshot.group.VolumeSnapshotGroupRefVO_;
import org.zstack.header.storage.snapshot.group.VolumeSnapshotGroupTreeInventory;
import org.zstack.header.storage.snapshot.group.VolumeSnapshotGroupTreeRefInventory;
import org.zstack.header.storage.snapshot.group.VolumeSnapshotGroupVO;
import org.zstack.header.storage.snapshot.group.VolumeSnapshotGroupVO_;
import org.zstack.header.volume.VolumeType;
import org.zstack.header.volume.VolumeVO;
import org.zstack.header.volume.VolumeVO_;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class VolumeSnapshotGroupTreeBuilder {
    private static final CLogger logger = Utils.getLogger(VolumeSnapshotGroupTreeBuilder.class);

    public List<VolumeSnapshotGroupTreeInventory> buildForVm(String vmInstanceUuid) {
        List<VolumeSnapshotGroupVO> groupVOs = Q.New(VolumeSnapshotGroupVO.class)
                .eq(VolumeSnapshotGroupVO_.vmInstanceUuid, vmInstanceUuid)
                .list();
        if (groupVOs.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> groupUuids = groupVOs.stream()
                .map(VolumeSnapshotGroupVO::getUuid)
                .collect(Collectors.toList());

        List<VolumeSnapshotGroupRefVO> refs = Q.New(VolumeSnapshotGroupRefVO.class)
                .in(VolumeSnapshotGroupRefVO_.volumeSnapshotGroupUuid, groupUuids)
                .list();

        Map<String, VolumeSnapshotVO> snapVOs = loadLiveSnapshotVOs(refs);
        Map<String, String> parentMap = buildParentMap(snapVOs);
        Map<String, String> snapToGroup = buildSnapToGroupMap(refs);

        Map<String, List<VolumeSnapshotGroupRefVO>> refsByGroup = refs.stream()
                .collect(Collectors.groupingBy(VolumeSnapshotGroupRefVO::getVolumeSnapshotGroupUuid));

        Map<String, VolumeSnapshotGroupTreeInventory> groupNodeMap =
                buildGroupNodes(groupVOs, refsByGroup, snapVOs);

        linkParents(groupNodeMap, refsByGroup, parentMap, snapToGroup);

        return assembleForest(groupNodeMap);
    }

    public String extractVolumeUuidCondition(APIQueryVolumeSnapshotTreeMsg msg) {
        List<QueryCondition> conditions = msg.getConditions();
        if (conditions == null) {
            return null;
        }
        String found = null;
        for (QueryCondition c : conditions) {
            if (!"volumeUuid".equals(c.getName())) {
                continue;
            }
            if (!QueryOp.EQ.toString().equals(c.getOp())) {
                continue;
            }
            if (found != null && !found.equals(c.getValue())) {
                logger.warn(String.format("multiple conflicting volumeUuid eq conditions in APIQueryVolumeSnapshotTreeMsg: %s vs %s; taking the first",
                        found, c.getValue()));
                return found;
            }
            found = c.getValue();
        }
        return found;
    }

    public String findRootVmUuid(String volumeUuid) {
        return Q.New(VolumeVO.class)
                .select(VolumeVO_.vmInstanceUuid)
                .eq(VolumeVO_.uuid, volumeUuid)
                .eq(VolumeVO_.type, VolumeType.Root)
                .findValue();
    }

    private Map<String, VolumeSnapshotVO> loadLiveSnapshotVOs(List<VolumeSnapshotGroupRefVO> refs) {
        List<String> liveSnapUuids = refs.stream()
                .filter(r -> !r.isSnapshotDeleted())
                .map(VolumeSnapshotGroupRefVO::getVolumeSnapshotUuid)
                .collect(Collectors.toList());
        if (liveSnapUuids.isEmpty()) {
            return Collections.emptyMap();
        }
        List<VolumeSnapshotVO> svos = Q.New(VolumeSnapshotVO.class)
                .in(VolumeSnapshotVO_.uuid, liveSnapUuids)
                .list();
        return svos.stream().collect(Collectors.toMap(VolumeSnapshotVO::getUuid, v -> v));
    }

    private Map<String, String> buildParentMap(Map<String, VolumeSnapshotVO> snapVOs) {
        Map<String, String> m = new HashMap<>();
        for (VolumeSnapshotVO v : snapVOs.values()) {
            m.put(v.getUuid(), v.getParentUuid());
        }
        return m;
    }

    private Map<String, String> buildSnapToGroupMap(List<VolumeSnapshotGroupRefVO> refs) {
        Map<String, String> m = new HashMap<>();
        for (VolumeSnapshotGroupRefVO r : refs) {
            m.put(r.getVolumeSnapshotUuid(), r.getVolumeSnapshotGroupUuid());
        }
        return m;
    }

    private Map<String, VolumeSnapshotGroupTreeInventory> buildGroupNodes(
            List<VolumeSnapshotGroupVO> groupVOs,
            Map<String, List<VolumeSnapshotGroupRefVO>> refsByGroup,
            Map<String, VolumeSnapshotVO> snapVOs) {
        Map<String, VolumeSnapshotGroupTreeInventory> groupNodeMap = new HashMap<>();
        for (VolumeSnapshotGroupVO g : groupVOs) {
            VolumeSnapshotGroupTreeInventory node = new VolumeSnapshotGroupTreeInventory();
            node.setUuid(g.getUuid());
            node.setName(g.getName());
            node.setDescription(g.getDescription());
            node.setVmInstanceUuid(g.getVmInstanceUuid());
            node.setCreateDate(g.getCreateDate());
            node.setLastOpDate(g.getLastOpDate());

            List<VolumeSnapshotGroupRefVO> groupRefs = refsByGroup.getOrDefault(g.getUuid(), Collections.emptyList());
            long deletedCount = groupRefs.stream().filter(VolumeSnapshotGroupRefVO::isSnapshotDeleted).count();
            int total = groupRefs.size();
            node.setIncomplete(deletedCount > 0 && deletedCount < total);

            node.setRefs(buildRefInventories(groupRefs, snapVOs));
            groupNodeMap.put(g.getUuid(), node);
        }
        return groupNodeMap;
    }

    private List<VolumeSnapshotGroupTreeRefInventory> buildRefInventories(
            List<VolumeSnapshotGroupRefVO> groupRefs,
            Map<String, VolumeSnapshotVO> snapVOs) {
        List<VolumeSnapshotGroupTreeRefInventory> refInvs = new ArrayList<>();
        for (VolumeSnapshotGroupRefVO r : groupRefs) {
            VolumeSnapshotGroupTreeRefInventory refInv = new VolumeSnapshotGroupTreeRefInventory();
            refInv.setVolumeUuid(r.getVolumeUuid());
            refInv.setVolumeName(r.getVolumeName());
            refInv.setVolumeType(r.getVolumeType());
            refInv.setVolumeSnapshotUuid(r.getVolumeSnapshotUuid());
            refInv.setSnapshotDeleted(r.isSnapshotDeleted());
            if (r.isSnapshotDeleted()) {
                refInv.setSnapshot(null);
            } else {
                VolumeSnapshotVO svo = snapVOs.get(r.getVolumeSnapshotUuid());
                refInv.setSnapshot(svo == null ? null : VolumeSnapshotInventory.valueOf(svo));
            }
            refInvs.add(refInv);
        }
        return refInvs;
    }

    private void linkParents(Map<String, VolumeSnapshotGroupTreeInventory> groupNodeMap,
                             Map<String, List<VolumeSnapshotGroupRefVO>> refsByGroup,
                             Map<String, String> parentMap,
                             Map<String, String> snapToGroup) {
        for (VolumeSnapshotGroupTreeInventory node : groupNodeMap.values()) {
            String parentGroupUuid = resolveParentGroupUuid(node.getUuid(),
                    refsByGroup.getOrDefault(node.getUuid(), Collections.emptyList()),
                    parentMap, snapToGroup);
            if (parentGroupUuid != null && groupNodeMap.containsKey(parentGroupUuid)) {
                node.setParentGroupUuid(parentGroupUuid);
            }
        }
    }

    private String resolveParentGroupUuid(String selfGroupUuid,
                                          List<VolumeSnapshotGroupRefVO> selfRefs,
                                          Map<String, String> parentMap,
                                          Map<String, String> snapToGroup) {
        Map<String, Integer> votes = new HashMap<>();
        for (VolumeSnapshotGroupRefVO r : selfRefs) {
            if (r.isSnapshotDeleted()) {
                continue;
            }
            String cur = parentMap.get(r.getVolumeSnapshotUuid());
            Set<String> visited = new HashSet<>();
            visited.add(r.getVolumeSnapshotUuid());
            while (cur != null && !visited.contains(cur)) {
                visited.add(cur);
                String g = snapToGroup.get(cur);
                if (g != null && !g.equals(selfGroupUuid)) {
                    votes.merge(g, 1, Integer::sum);
                    break;
                }
                cur = parentMap.get(cur);
            }
        }
        if (votes.isEmpty()) {
            return null;
        }
        String winner = null;
        int top = -1;
        boolean tie = false;
        for (Map.Entry<String, Integer> e : votes.entrySet()) {
            if (e.getValue() > top) {
                winner = e.getKey();
                top = e.getValue();
                tie = false;
            } else if (e.getValue() == top) {
                tie = true;
            }
        }
        if (tie) {
            logger.warn(String.format("group[uuid:%s] has tied parentGroup votes: %s; picking %s",
                    selfGroupUuid, votes, winner));
        }
        return winner;
    }

    private List<VolumeSnapshotGroupTreeInventory> assembleForest(Map<String, VolumeSnapshotGroupTreeInventory> groupNodeMap) {
        List<VolumeSnapshotGroupTreeInventory> forest = new ArrayList<>();
        for (VolumeSnapshotGroupTreeInventory node : groupNodeMap.values()) {
            if (node.getParentGroupUuid() == null) {
                forest.add(node);
            } else {
                groupNodeMap.get(node.getParentGroupUuid()).getChildren().add(node);
            }
        }

        Comparator<VolumeSnapshotGroupTreeInventory> byCreateDateAsc =
                Comparator.comparing(VolumeSnapshotGroupTreeInventory::getCreateDate,
                        Comparator.nullsFirst(Comparator.naturalOrder()));
        forest.sort(byCreateDateAsc);
        for (VolumeSnapshotGroupTreeInventory node : groupNodeMap.values()) {
            node.getChildren().sort(byCreateDateAsc);
        }

        markCurrent(groupNodeMap);
        return forest;
    }

    private void markCurrent(Map<String, VolumeSnapshotGroupTreeInventory> groupNodeMap) {
        VolumeSnapshotGroupTreeInventory newest = null;
        for (VolumeSnapshotGroupTreeInventory node : groupNodeMap.values()) {
            if (newest == null) {
                newest = node;
                continue;
            }
            if (node.getCreateDate() != null && (newest.getCreateDate() == null
                    || node.getCreateDate().after(newest.getCreateDate()))) {
                newest = node;
            }
        }
        if (newest != null) {
            newest.setCurrent(true);
        }
    }
}
