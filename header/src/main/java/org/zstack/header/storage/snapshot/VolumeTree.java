package org.zstack.header.storage.snapshot;

import org.zstack.header.volume.VolumeVO;
import org.zstack.utils.DebugUtils;
import org.zstack.utils.function.Function;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * When building the VolumeTree, if the tree is current, then add the volume to the VolumeTree.
 */
public class VolumeTree {
    public static class VolumeSnapshotLeafInventory {
        private VolumeSnapshotInventory inventory;
        private String parentUuid;
        private List<VolumeSnapshotLeafInventory> children = new ArrayList<>();

        public VolumeSnapshotInventory getInventory() {
            return inventory;
        }

        public void setInventory(VolumeSnapshotInventory inventory) {
            this.inventory = inventory;
        }

        public String getParentUuid() {
            return parentUuid;
        }

        public void setParentUuid(String parentUuid) {
            this.parentUuid = parentUuid;
        }

        public List<VolumeSnapshotLeafInventory> getChildren() {
            return children;
        }

        public void setChildren(List<VolumeSnapshotLeafInventory> children) {
            this.children = children;
        }

        public String getStatus() {
            return inventory.getStatus();
        }
    }

    public static class VolumeSnapshotLeaf {
        private VolumeSnapshotInventory inventory;
        private VolumeSnapshotLeaf parent;
        private List<VolumeSnapshotLeaf> children = new ArrayList<>();
        private List<VolumeSnapshotInventory> descendants;
        private List<VolumeSnapshotInventory> ancestors;

        public VolumeSnapshotInventory getInventory() {
            return inventory;
        }

        public void setInventory(VolumeSnapshotInventory inventory) {
            this.inventory = inventory;
        }

        public VolumeSnapshotLeaf getParent() {
            return parent;
        }

        public void setParent(VolumeSnapshotLeaf parent) {
            this.parent = parent;
        }

        public List<VolumeSnapshotLeaf> getChildren() {
            return children;
        }

        public void setChildren(List<VolumeSnapshotLeaf> children) {
            this.children = children;
        }

        public String getUuid() {
            return inventory.getUuid();
        }

        public void setUuid(String uuid) {
            if (inventory == null) {
                inventory = new VolumeSnapshotInventory();
            }
            inventory.setUuid(uuid);
        }

        private static void walkDownAll(VolumeSnapshotLeaf me, Consumer<VolumeSnapshotLeaf> consumer) {
            consumer.accept(me);
            me.children.forEach(c -> walkDownAll(c, consumer));

        }

        public void walkDownAll(Consumer<VolumeSnapshotLeaf> consumer) {
            walkDownAll(this, consumer);
        }

        public VolumeTree toSubTree() {
            VolumeTree tree = new VolumeTree();
            tree.root = this;
            return tree;
        }

        private static VolumeSnapshotLeaf walkUp(VolumeSnapshotLeaf leaf, Function<Boolean, VolumeSnapshotInventory> func) {
            if (func.call(leaf.inventory)) {
                return leaf;
            }

            if (leaf.getParent() == null) {
                return null;
            }

            return walkUp(leaf.getParent(), func);
        }

        public VolumeSnapshotLeaf walkUp(Function<Boolean, VolumeSnapshotInventory> func) {
            if (func.call(inventory)) {
                return this;
            }

            if (getParent() == null) {
                return null;
            }

            return walkUp(getParent(), func);
        }

        public VolumeSnapshotLeaf walkDown(Function<Boolean, VolumeSnapshotInventory> func) {
            return walkDown(this, func);
        }

        private static VolumeSnapshotLeaf walkDown(VolumeSnapshotLeaf leaf, Function<Boolean, VolumeSnapshotInventory> func) {
            if (func.call(leaf.inventory)) {
                return leaf;
            }

            if (leaf.getChildren().isEmpty()) {
                return null;
            }

            for (VolumeSnapshotLeaf l : leaf.getChildren()) {
                VolumeSnapshotLeaf ret = walkDown(l, func);
                if (ret != null) {
                    return ret;
                }
            }

            return null;
        }

        public List<VolumeSnapshotInventory> getDescendants() {
            if (descendants == null) {
                descendants = new ArrayList<VolumeSnapshotInventory>();
                walkDown(new Function<Boolean, VolumeSnapshotInventory>() {
                    @Override
                    public Boolean call(VolumeSnapshotInventory arg) {
                        descendants.add(arg);
                        return false;
                    }
                });
            }

            return descendants;
        }

        public List<VolumeSnapshotInventory> getAncestors() {
            if (ancestors == null) {
                ancestors = new ArrayList<VolumeSnapshotInventory>();
                walkUp(new Function<Boolean, VolumeSnapshotInventory>() {
                    @Override
                    public Boolean call(VolumeSnapshotInventory arg) {
                        ancestors.add(arg);
                        return false;
                    }
                });

                Collections.reverse(ancestors);
            }

            return ancestors;
        }

        public VolumeSnapshotLeafInventory toLeafInventory(Set<String> filterUuids) {
            return doToLeafInventory(filterUuids);
        }

        public VolumeSnapshotLeafInventory toLeafInventory() {
            return doToLeafInventory(null);
        }

        private VolumeSnapshotLeafInventory doToLeafInventory(Set<String> filterUuids) {
            VolumeSnapshotLeafInventory leafInventory = new VolumeSnapshotLeafInventory();
            leafInventory.setInventory(getInventory(filterUuids));
            if (parent != null) {
                leafInventory.setParentUuid(parent.getUuid());
            }

            for (VolumeSnapshotLeaf leaf : children) {
                leafInventory.getChildren().add(leaf.doToLeafInventory(filterUuids));
            }

            return leafInventory;
        }

        private VolumeSnapshotInventory getInventory(Set<String> filterUuids) {
            if (filterUuids == null || filterUuids.contains(inventory.getUuid())) {
                return inventory;
            } else {
                VolumeSnapshotInventory inv = new VolumeSnapshotInventory();
                inv.setUuid(inventory.getUuid());
                return inv;
            }
        }

        public List<String> getChildrenVolumeSnapshotInventoryUuid() {
            return children.stream().map(it -> it.getInventory().getUuid()).collect(Collectors.toList());
        }

        public String getStatus() {
            return inventory.getStatus();
        }
    }

    public class VolumeSnapshotDeletionContext {
        final VolumeSnapshotVO targetSnapshot;
        final VolumeVO volume;
        List<VolumeSnapshotVO> allSnapshotsVOs;
        List<VolumeSnapshotInventory> aliveChainSnapshots;

        VolumeTree.VolumeSnapshotLeaf targetSnapshotLeaf;
        VolumeTree.VolumeSnapshotLeaf parentSnapshotLeaf;
        final List<VolumeTree.VolumeSnapshotLeaf> snapshotChainWhichStatusIsDeletedAndWithSingleChildren = new ArrayList<>();
        final List<VolumeTree.VolumeSnapshotLeaf> snapshotChainWhichStatusIsDeletedAndWithSingleParent = new ArrayList<>();

        VolumeTree.VolumeSnapshotLeaf srcSnapshotLeaf;
        VolumeTree.VolumeSnapshotLeaf dstSnapshotLeaf;
        final Map<String, VolumeSnapshotInventory> snapshotsToDeleteByUuid = new HashMap<>();
        long requiredExtraSize;
        List<String> srcChildrenInstallPath = new ArrayList<>();
        String newLatestSnapshotUuid;
        String newParentUuid;
        boolean deleteDbOnly = false;

        VolumeSnapshotDeletionContext(VolumeSnapshotVO targetSnapshot, VolumeVO volume, List<VolumeSnapshotVO> allSnapshotsVOs,
                                      List<VolumeSnapshotInventory> aliveChainSnapshots) {
            this.targetSnapshot = targetSnapshot;
            this.volume = volume;
            this.allSnapshotsVOs = allSnapshotsVOs;
            this.aliveChainSnapshots = aliveChainSnapshots;
            initialize();
        }

        private void initialize() {
            if (VolumeSnapshotConstant.STORAGE_SNAPSHOT_TYPE.toString().equals(targetSnapshot.getType())) {
                snapshotsToDeleteByUuid.put(targetSnapshot.getUuid(), VolumeSnapshotInventory.valueOf(targetSnapshot));
                return;
            }

            targetSnapshotLeaf = getSnapshotLeaf(targetSnapshot.getUuid());
            parentSnapshotLeaf = targetSnapshotLeaf.getParent();

            if (isLastReadySnapshot()) {
                handleLastReadySnapshot();
            } else if (parentSnapshotLeaf == null) {
                handleSnapshotWithoutParent();
            } else {
                handleSnapshotWithParent();
            }

            setNewLatestSnapshotUuid();
            setRequiredExtraSize();
            setSnapshotsToDeleteByUuid();
            setSrcChildrenInstallPath();
            setNewParentUuid();
        }

        private void setNewLatestSnapshotUuid() {
            if (!isCurrent || srcSnapshotLeaf == null || dstSnapshotLeaf == null) {
                return;
            }
            if (Objects.equals(srcSnapshotLeaf.getUuid(), volume.getUuid()) && dstSnapshotLeaf.getParent() != null) {
                newLatestSnapshotUuid = dstSnapshotLeaf.getParent().getUuid();
            }
        }

        private void setRequiredExtraSize() {
            requiredExtraSize = snapshotsToDeleteByUuid.values().stream().mapToLong(VolumeSnapshotInventory::getSize).sum();
        }

        private void setSnapshotsToDeleteByUuid() {
            snapshotsToDeleteByUuid.put(targetSnapshotLeaf.getInventory().getUuid(), targetSnapshotLeaf.getInventory());
            snapshotChainWhichStatusIsDeletedAndWithSingleParent.forEach(leaf ->
                    snapshotsToDeleteByUuid.put(leaf.getInventory().getUuid(), leaf.getInventory()));
            snapshotChainWhichStatusIsDeletedAndWithSingleChildren.forEach(leaf ->
                    snapshotsToDeleteByUuid.put(leaf.getInventory().getUuid(), leaf.getInventory()));
        }

        private void setSrcChildrenInstallPath() {
            if (srcSnapshotLeaf != null) {
                srcChildrenInstallPath = srcSnapshotLeaf.getChildren().stream()
                        .map(leaf -> leaf.getInventory().getPrimaryStorageInstallPath()).collect(Collectors.toList());
            }
        }

        private void setNewParentUuid() {
            if (dstSnapshotLeaf != null) {
                newParentUuid = dstSnapshotLeaf.getInventory().getParentUuid();
            }
        }

        private boolean isLastReadySnapshot() {
            List<VolumeSnapshotVO> snapshotVOs = allSnapshotsVOs.stream().filter(snapshotVO ->
                    Objects.equals(snapshotVO.getStatus(), VolumeSnapshotStatus.Ready)).collect(Collectors.toList());

            if (isCurrent) {
                snapshotVOs = snapshotVOs.stream().filter(snapshotVO ->
                        !snapshotVO.getUuid().equals(volume.getUuid())).collect(Collectors.toList());
            }

            return snapshotVOs.size() == 1 && snapshotVOs.get(0).getUuid().equals(targetSnapshot.getUuid());
        }

        private void handleLastReadySnapshot() {
            allSnapshotsVOs.forEach(vo -> snapshotsToDeleteByUuid.put(vo.getUuid(), VolumeSnapshotInventory.valueOf(vo)));
            if (isCurrent) {
                srcSnapshotLeaf = getSnapshotLeaf(volume.getUuid());
                dstSnapshotLeaf = getSnapshotLeaf(aliveChainSnapshots.get(0).getUuid());
            }
        }

        private void handleSnapshotWithoutParent() {
            if (targetSnapshotLeaf.getChildren().isEmpty()) {
                return;
            }

            if (targetSnapshotLeaf.getChildren().size() > 1) {
                deleteDbOnly = true;
                return;
            }

            srcSnapshotLeaf = targetSnapshotLeaf.getChildren().get(0);
            fillSnapshotChainWhichStatusIsDeletedAndWithSingleChildren(srcSnapshotLeaf);
            srcSnapshotLeaf = getSrcSnapshotLeaf();
            dstSnapshotLeaf = srcSnapshotLeaf == null ? null : targetSnapshotLeaf;
        }

        private void handleSnapshotWithParent() {
            if (targetSnapshotLeaf.getChildren().size() > 1) {
                deleteDbOnly = true;
                return;
            }

            if (targetSnapshotLeaf.getChildren().isEmpty()) {
                fillSnapshotChainWhichStatusIsDeletedAndWithSingleParent(parentSnapshotLeaf);
                return;
            }

            srcSnapshotLeaf = targetSnapshotLeaf.getChildren().get(0);
            fillSnapshotChainWhichStatusIsDeletedAndWithSingleChildren(srcSnapshotLeaf);
            srcSnapshotLeaf = getSrcSnapshotLeaf();

            dstSnapshotLeaf = targetSnapshotLeaf;
            fillSnapshotChainWhichStatusIsDeletedAndWithSingleParent(parentSnapshotLeaf);
            dstSnapshotLeaf = getDstSnapshotLeaf();
            dstSnapshotLeaf = srcSnapshotLeaf != null ? dstSnapshotLeaf : null;
        }

        private void fillSnapshotChainWhichStatusIsDeletedAndWithSingleChildren(VolumeTree.VolumeSnapshotLeaf leaf) {
            if (leaf.getChildren().size() == 1 && Objects.equals(leaf.getStatus(), VolumeSnapshotStatus.Deleted.toString())) {
                snapshotChainWhichStatusIsDeletedAndWithSingleChildren.add(leaf);
                if (leaf.getChildren().size() != 1) {
                    return;
                }
                fillSnapshotChainWhichStatusIsDeletedAndWithSingleChildren(leaf.getChildren().get(0));
            }
        }

        private void fillSnapshotChainWhichStatusIsDeletedAndWithSingleParent(VolumeTree.VolumeSnapshotLeaf leaf) {
            if (leaf != null && Objects.equals(leaf.getStatus(), VolumeSnapshotStatus.Deleted.toString()) && leaf.getChildren().size() == 1) {
                snapshotChainWhichStatusIsDeletedAndWithSingleParent.add(leaf);
                if (leaf.getParent() == null) {
                    return;
                }
                fillSnapshotChainWhichStatusIsDeletedAndWithSingleParent(leaf.getParent());
            }
        }

        private VolumeTree.VolumeSnapshotLeaf getSrcSnapshotLeaf() {
            if (snapshotChainWhichStatusIsDeletedAndWithSingleChildren.isEmpty()) {
                return srcSnapshotLeaf;
            }
            Collections.reverse(snapshotChainWhichStatusIsDeletedAndWithSingleChildren);
            return snapshotChainWhichStatusIsDeletedAndWithSingleChildren.get(0).getChildren().isEmpty() ?
                    null : snapshotChainWhichStatusIsDeletedAndWithSingleChildren.get(0).getChildren().get(0);
        }

        private VolumeTree.VolumeSnapshotLeaf getDstSnapshotLeaf() {
            if (snapshotChainWhichStatusIsDeletedAndWithSingleParent.isEmpty()) {
                return dstSnapshotLeaf;
            }
            Collections.reverse(snapshotChainWhichStatusIsDeletedAndWithSingleParent);
            return snapshotChainWhichStatusIsDeletedAndWithSingleParent.get(0);
        }

        public VolumeSnapshotInventory getDstSnapshotInv() {
            return dstSnapshotLeaf == null ? null : dstSnapshotLeaf.getInventory();
        }

        public VolumeSnapshotInventory getSrcSnapshotInv() {
            return srcSnapshotLeaf == null ? null : srcSnapshotLeaf.getInventory();
        }

        public Map<String, VolumeSnapshotInventory> getSnapshotsToDeleteByUuid() {
            return snapshotsToDeleteByUuid;
        }

        public long getRequiredExtraSize() {
            return requiredExtraSize;
        }

        public List<String> getSrcChildrenInstallPath() {
            return srcChildrenInstallPath;
        }

        public String getNewLatestSnapshotUuid() {
            return newLatestSnapshotUuid;
        }

        public String getNewParentUuid() {
            return newParentUuid;
        }

        public boolean isDeleteDbOnly() {
            return deleteDbOnly;
        }
    }

    private VolumeSnapshotLeaf root;
    VolumeVO volume;
    List<VolumeSnapshotVO> allSnapshots = new ArrayList<>();
    private boolean isCurrent;
    // the aliveChainSnapshots represents a chain where the volume is located, but only if the VolumeTree contains that volume.
    // If the VolumeTree does not contain the volume, then aliveChainInventory should be empty.
    private List<VolumeSnapshotInventory> aliveChainSnapshots = new ArrayList<>();

    public static VolumeTree fromVOs(List<VolumeSnapshotVO> vos, boolean isCurrent, VolumeVO volumeVO) {
        List<VolumeSnapshotInventory> invs = VolumeSnapshotInventory.valueOf(vos);

        VolumeTree tree = new VolumeTree();
        tree.isCurrent = isCurrent;
        if (tree.isCurrent) {
            VolumeSnapshotVO snapshotVO = new VolumeSnapshotVO();
            snapshotVO.setLatest(false);
            snapshotVO.setName(String.format("volume-%s-%s", volumeVO.getName(), volumeVO.getUuid()));
            snapshotVO.setUuid(volumeVO.getUuid());
            VolumeSnapshotInventory latestInv = findLatestVolumeSnapshotInventory(invs);
            snapshotVO.setParentUuid(latestInv.getUuid());
            snapshotVO.setTreeUuid(latestInv.getTreeUuid());
            snapshotVO.setState(VolumeSnapshotState.Enabled);
            snapshotVO.setStatus(VolumeSnapshotStatus.Ready);
            snapshotVO.setPrimaryStorageInstallPath(volumeVO.getInstallPath());
            snapshotVO.setPrimaryStorageUuid(volumeVO.getPrimaryStorageUuid());
            invs.add(VolumeSnapshotInventory.valueOf(snapshotVO));
            tree.volume = volumeVO;
        }

        Map<String, VolumeSnapshotLeaf> map = new HashMap<>();
        for (VolumeSnapshotInventory inv : invs) {
            VolumeSnapshotLeaf leaf = map.get(inv.getUuid());
            if (leaf == null) {
                leaf = new VolumeSnapshotLeaf();
                leaf.inventory = inv;
                map.put(inv.getUuid(), leaf);
            } else {
                leaf.inventory = inv;
            }

            if (inv.getParentUuid() != null) {
                VolumeSnapshotLeaf parent = map.get(inv.getParentUuid());
                if (parent == null) {
                    parent = new VolumeSnapshotLeaf();
                    parent.setUuid(inv.getParentUuid());
                    map.put(parent.getUuid(), parent);
                }

                parent.children.add(leaf);
                leaf.parent = parent;
            } else {
                tree.root = leaf;
            }
        }

        if (tree.isCurrent) {
            tree.aliveChainSnapshots = tree.getSnapshotLeaf(volumeVO.getUuid()).getAncestors();
        }
        DebugUtils.Assert(tree.root != null, "why tree root is null???");
        tree.allSnapshots = vos;
        return tree;
    }

    private static VolumeSnapshotInventory findLatestVolumeSnapshotInventory(List<VolumeSnapshotInventory> invs) {
        List<VolumeSnapshotInventory> inventories = invs.stream()
                .filter(VolumeSnapshotInventory::isLatest).collect(Collectors.toList());
        if (inventories.size() != 1) {
            throw new IllegalArgumentException(String.format("There are %d latest snapshots on tree[uuid:%s]",
                    inventories.size(), invs.get(0).getTreeUuid()));
        }
        return inventories.get(0);
    }

    public VolumeSnapshotDeletionContext createDeletionContext(VolumeSnapshotVO snapshotVO) {
        return new VolumeSnapshotDeletionContext(snapshotVO, volume, allSnapshots, aliveChainSnapshots);
    }

    public List<String> getAliveChainInstallPaths() {
        return aliveChainSnapshots.stream()
                .map(VolumeSnapshotInventory::getPrimaryStorageInstallPath).collect(Collectors.toList());
    }

    private VolumeSnapshotLeaf findSnapshot(final List<VolumeSnapshotLeaf> leafs, final Function<Boolean, VolumeSnapshotInventory> func) {
        for (VolumeSnapshotLeaf leaf : leafs) {
            VolumeSnapshotLeaf ret = findSnapshot(leaf.children, func);
            if (ret != null) {
                return ret;
            }

            if (func.call(leaf.getInventory())) {
                return leaf;
            }
        }

        return null;
    }

    private VolumeSnapshotLeaf findSnapshot(Function<Boolean, VolumeSnapshotInventory> func) {
        if (func.call(root.getInventory())) {
            return root;
        }
        return findSnapshot(root.children, func);
    }

    public VolumeSnapshotLeaf getSnapshotLeaf(String snapshotUuid) {
        return findSnapshot(new Function<Boolean, VolumeSnapshotInventory>() {
            @Override
            public Boolean call(VolumeSnapshotInventory arg) {
                return arg.getUuid().equals(snapshotUuid);
            }
        });
    }
}
