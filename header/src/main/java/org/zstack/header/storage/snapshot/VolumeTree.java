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
        final VolumeSnapshotVO snapshotVO;
        final VolumeVO volume;

        VolumeTree.VolumeSnapshotLeaf snapshotLeaf;
        VolumeTree.VolumeSnapshotLeaf parentSnapshotLeaf;
        final List<VolumeTree.VolumeSnapshotLeaf> chainWhichStatusIsDeletedAndWithSingleChildren = new ArrayList<>();
        final List<VolumeTree.VolumeSnapshotLeaf> chainWhichStatusIsDeletedAndWithSingleParent = new ArrayList<>();

        VolumeTree.VolumeSnapshotLeaf srcSnapshotLeaf;
        VolumeTree.VolumeSnapshotLeaf dstSnapshotLeaf;
        final List<VolumeSnapshotInventory> snapshotsToDelete = new ArrayList<>();
        long requiredExtraSize;
        List<String> srcChildrenInstallPath = new ArrayList<>();
        String newLatestSnapshotUuid;
        String newParentUuid;
        boolean deleteDbOnly = false;
        boolean inAliveChain = false;

        VolumeSnapshotDeletionContext(VolumeSnapshotVO snapshotVO, VolumeVO volume) {
            this.snapshotVO = snapshotVO;
            this.volume = volume;
            initialize();
        }

        private void initialize() {
            snapshotLeaf = getSnapshotLeaf(snapshotVO.getUuid());
            parentSnapshotLeaf = snapshotLeaf.getParent();

            if (parentSnapshotLeaf == null) {
                handleSnapshotWithoutParent();
            } else {
                handleSnapshotWithParent();
            }

            if (isCurrent && srcSnapshotLeaf != null && dstSnapshotLeaf != null) {
                List<String> aliveChainUuids = aliveChainSnapshots.stream()
                        .map(VolumeSnapshotInventory::getUuid).collect(Collectors.toList());
                if (aliveChainUuids.contains(srcSnapshotLeaf.getUuid()) && aliveChainUuids.contains(srcSnapshotLeaf.getUuid())) {
                    inAliveChain = true;
                }
                if (Objects.equals(srcSnapshotLeaf.getUuid(), volume.getUuid()) && dstSnapshotLeaf.getParent() != null) {
                    newLatestSnapshotUuid = dstSnapshotLeaf.getParent().getUuid();
                }
            }

            requiredExtraSize = snapshotsToDelete.stream()
                    .filter(snapshot -> !Objects.equals(snapshot.getUuid(), volume.getUuid()))
                    .mapToLong(VolumeSnapshotInventory::getSize).sum();

            if (srcSnapshotLeaf != null) {
                srcChildrenInstallPath = srcSnapshotLeaf.getChildren().stream()
                        .map(leaf -> leaf.getInventory().getPrimaryStorageInstallPath()).collect(Collectors.toList());
            }
            if (dstSnapshotLeaf != null) {
                newParentUuid = dstSnapshotLeaf.getInventory().getParentUuid();
            }
        }

        private void handleSnapshotWithoutParent() {
            if (snapshotLeaf.getChildren().isEmpty()) {
                snapshotsToDelete.add(snapshotLeaf.getInventory());
                return;
            }

            if (snapshotLeaf.getChildren().size() > 1) {
                deleteDbOnly = true;
                return;
            }

            snapshotsToDelete.add(snapshotLeaf.getInventory());
            dstSnapshotLeaf = snapshotLeaf;
            srcSnapshotLeaf = snapshotLeaf.getChildren().get(0);
            fillChainWhichStatusIsDeletedAndWithSingleChildren(srcSnapshotLeaf);
            srcSnapshotLeaf = chainWhichStatusIsDeletedAndWithSingleChildren.isEmpty() ?
                    srcSnapshotLeaf : getSrcSnapshotLeafFromDeletedSingleChildrenChain();
        }

        private void handleSnapshotWithParent() {
            if (snapshotLeaf.getChildren().isEmpty()) {
                if (parentSnapshotLeaf.getChildren().size() == 2 &&
                        Objects.equals(parentSnapshotLeaf.getStatus(), VolumeSnapshotStatus.Deleted.toString())) {
                    snapshotsToDelete.add(snapshotLeaf.getInventory());

                    srcSnapshotLeaf = getSiblingLeaves(snapshotLeaf).get(0);
                    fillChainWhichStatusIsDeletedAndWithSingleChildren(srcSnapshotLeaf);
                    srcSnapshotLeaf = chainWhichStatusIsDeletedAndWithSingleChildren.isEmpty() ? srcSnapshotLeaf :
                            getSrcSnapshotLeafFromDeletedSingleChildrenChain();

                    dstSnapshotLeaf = parentSnapshotLeaf.getParent();
                    fillChainWhichStatusIsDeletedAndWithSingleParent(parentSnapshotLeaf.getParent());
                    dstSnapshotLeaf = chainWhichStatusIsDeletedAndWithSingleParent.isEmpty() ? dstSnapshotLeaf :
                            getDstLeafFromDeletedSingleChildrenChain();
                    return;
                }

                if (parentSnapshotLeaf.getChildren().size() == 1 &&
                        Objects.equals(parentSnapshotLeaf.getStatus(), VolumeSnapshotStatus.Deleted.toString())) {
                    snapshotsToDelete.add(snapshotLeaf.getInventory());
                    fillChainWhichStatusIsDeletedAndWithSingleParent(parentSnapshotLeaf);
                    if (!chainWhichStatusIsDeletedAndWithSingleParent.isEmpty()) {
                        snapshotsToDelete.addAll(chainWhichStatusIsDeletedAndWithSingleParent.stream()
                                .map(VolumeTree.VolumeSnapshotLeaf::getInventory).collect(Collectors.toList()));
                    }
                    return;
                }

                snapshotsToDelete.add(snapshotLeaf.getInventory());
                return;
            }

            // 2 ...
            // 3
            // 4 ...
            if (snapshotLeaf.getChildren().size() == 1) {
                srcSnapshotLeaf = snapshotLeaf.getChildren().get(0);
                fillChainWhichStatusIsDeletedAndWithSingleChildren(srcSnapshotLeaf);
                srcSnapshotLeaf = chainWhichStatusIsDeletedAndWithSingleChildren.isEmpty() ? srcSnapshotLeaf :
                        getSrcSnapshotLeafFromDeletedSingleChildrenChain();
            } else {
                srcSnapshotLeaf = snapshotLeaf;
            }

            if (Objects.equals(parentSnapshotLeaf.getStatus(), VolumeSnapshotStatus.Deleted.toString())
                    && parentSnapshotLeaf.getChildren().size() == 1) {
                dstSnapshotLeaf = parentSnapshotLeaf;
                fillChainWhichStatusIsDeletedAndWithSingleParent(dstSnapshotLeaf);
                dstSnapshotLeaf = chainWhichStatusIsDeletedAndWithSingleParent.isEmpty()
                        ? dstSnapshotLeaf : getDstLeafFromDeletedSingleChildrenChain();
            } else {
                dstSnapshotLeaf = snapshotLeaf;
            }

            if (srcSnapshotLeaf == dstSnapshotLeaf) {
                deleteDbOnly = true;
                srcSnapshotLeaf = null;
                dstSnapshotLeaf = null;
            }
        }

        private VolumeTree.VolumeSnapshotLeaf getSrcSnapshotLeafFromDeletedSingleChildrenChain() {
            Collections.reverse(chainWhichStatusIsDeletedAndWithSingleChildren);
            VolumeTree.VolumeSnapshotLeaf srcSnapshotLeaf = chainWhichStatusIsDeletedAndWithSingleChildren.get(0);
            snapshotsToDelete.addAll(chainWhichStatusIsDeletedAndWithSingleChildren
                    .subList(1, chainWhichStatusIsDeletedAndWithSingleChildren.size())
                    .stream().map(VolumeTree.VolumeSnapshotLeaf::getInventory).collect(Collectors.toList()));
            if (srcSnapshotLeaf.getChildren().size() == 1) {
                srcSnapshotLeaf = srcSnapshotLeaf.getChildren().get(0);
                snapshotsToDelete.add(srcSnapshotLeaf.getInventory());
            }
            return srcSnapshotLeaf;
        }

        private void fillChainWhichStatusIsDeletedAndWithSingleChildren(VolumeTree.VolumeSnapshotLeaf leaf) {
            if (Objects.equals(leaf.getStatus(), VolumeSnapshotStatus.Deleted.toString()) && leaf.getChildren().size() == 1) {
                chainWhichStatusIsDeletedAndWithSingleChildren.add(leaf);
                if (leaf.getChildren().isEmpty()) {
                    return;
                }
                fillChainWhichStatusIsDeletedAndWithSingleChildren(leaf.getChildren().get(0));
            }
        }

        private VolumeTree.VolumeSnapshotLeaf getDstLeafFromDeletedSingleChildrenChain() {
            Collections.reverse(chainWhichStatusIsDeletedAndWithSingleParent);
            VolumeTree.VolumeSnapshotLeaf dstSnapshotLeaf = chainWhichStatusIsDeletedAndWithSingleParent.get(0);
            snapshotsToDelete.addAll(chainWhichStatusIsDeletedAndWithSingleParent
                    .subList(1, chainWhichStatusIsDeletedAndWithSingleParent.size())
                    .stream().map(VolumeTree.VolumeSnapshotLeaf::getInventory).collect(Collectors.toList()));
            if (dstSnapshotLeaf.getParent().getChildren().size() == 1) {
                dstSnapshotLeaf = dstSnapshotLeaf.getChildren().get(0);
                snapshotsToDelete.add(dstSnapshotLeaf.getInventory());
            }
            return dstSnapshotLeaf;
        }

        private void fillChainWhichStatusIsDeletedAndWithSingleParent(VolumeTree.VolumeSnapshotLeaf leaf) {
            if (Objects.equals(leaf.getStatus(), VolumeSnapshotStatus.Deleted.toString()) && leaf.getChildren().size() == 1) {
                chainWhichStatusIsDeletedAndWithSingleParent.add(leaf);
                if (leaf.getParent() == null) {
                    return;
                }
                fillChainWhichStatusIsDeletedAndWithSingleParent(leaf.getParent());
            }
        }

        public VolumeSnapshotLeaf getDstSnapshotLeaf() {
            return dstSnapshotLeaf;
        }

        public VolumeSnapshotLeaf getSrcSnapshotLeaf() {
            return srcSnapshotLeaf;
        }

        public VolumeSnapshotInventory getDstSnapshotInv() {
            return dstSnapshotLeaf == null ? null : dstSnapshotLeaf.getInventory();
        }

        public VolumeSnapshotInventory getSrcSnapshotInv() {
            return srcSnapshotLeaf == null ? null : srcSnapshotLeaf.getInventory();
        }

        public List<VolumeSnapshotInventory> getSnapshotsToDelete() {
            return snapshotsToDelete;
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

        public boolean isInAliveChain() {
            return inAliveChain;
        }

    }

    private VolumeSnapshotLeaf root;
    private boolean isCurrent;
    // the aliveChainSnapshots represents a chain where the volume is located, but only if the VolumeTree contains that volume.
    // If the VolumeTree does not contain the volume, then aliveChainInventory should be empty.
    private List<VolumeSnapshotInventory> aliveChainSnapshots = new ArrayList<>();

    public static VolumeTree fromVOs(List<VolumeSnapshotVO> vos, boolean isCurrent, VolumeVO volumeVO) {
        List<VolumeSnapshotInventory> invs = VolumeSnapshotInventory.valueOf(vos);

        VolumeTree tree = new VolumeTree();
        tree.isCurrent = isCurrent;
        if (tree.isCurrent) {
            VolumeSnapshotInventory latestInv = invs.stream()
                    .filter(VolumeSnapshotInventory::isLatest).collect(Collectors.toList()).get(0);
            VolumeSnapshotVO snapshotVO = new VolumeSnapshotVO();
            snapshotVO.setLatest(false);
            snapshotVO.setName(String.format("volume-%s-%s", volumeVO.getName(), volumeVO.getUuid()));
            snapshotVO.setUuid(volumeVO.getUuid());
            snapshotVO.setParentUuid(latestInv.getUuid());
            snapshotVO.setTreeUuid(latestInv.getTreeUuid());
            snapshotVO.setState(VolumeSnapshotState.Enabled);
            snapshotVO.setStatus(VolumeSnapshotStatus.Ready);
            snapshotVO.setPrimaryStorageInstallPath(volumeVO.getInstallPath());
            snapshotVO.setPrimaryStorageUuid(volumeVO.getPrimaryStorageUuid());
            invs.add(VolumeSnapshotInventory.valueOf(snapshotVO));
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
        return tree;
    }

    public VolumeSnapshotDeletionContext createDeletionContext(VolumeSnapshotVO snapshotVO, VolumeVO volume) {
        return new VolumeSnapshotDeletionContext(snapshotVO, volume);
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

    private List<VolumeSnapshotInventory> getAncestors(String snapshotUuid) {
        VolumeSnapshotLeaf latestLeaf = findSnapshot(new Function<Boolean, VolumeSnapshotInventory>() {
            @Override
            public Boolean call(VolumeSnapshotInventory arg) {
                return arg.getUuid().equals(snapshotUuid);
            }
        });

        List<VolumeSnapshotInventory> ancestors = latestLeaf.getAncestors();
        Collections.reverse(ancestors);
        return ancestors;
    }

    public List<String> getAncestorsInstallPaths(String snapshotUuid) {
        if (getAncestors(snapshotUuid).isEmpty()) {
            return new ArrayList<>();
        }
        return getAncestors(snapshotUuid)
                .stream().map(VolumeSnapshotInventory::getPrimaryStorageInstallPath).collect(Collectors.toList());
    }

    public List<VolumeSnapshotLeaf> getSiblingLeaves(VolumeSnapshotLeaf leaf) {
        if (leaf.getParent() == null || leaf.getParent().getChildren().size() == 1) {
            return new ArrayList<>();
        }

        List<VolumeSnapshotLeaf> siblingLeaves = leaf.getParent().getChildren();
        siblingLeaves.remove(leaf);
        return siblingLeaves;
    }
}
