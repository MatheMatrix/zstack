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
        final List<VolumeTree.VolumeSnapshotLeaf> deletedSingleChildrenChain = new ArrayList<>();
        final List<VolumeTree.VolumeSnapshotLeaf> deletedSingleParentChain = new ArrayList<>();

        VolumeSnapshotInventory srcSnapshotInv;
        VolumeSnapshotInventory dstSnapshotInv;
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

            List<String> aliveChainUuids = aliveChainSnapshots.stream()
                    .map(VolumeSnapshotInventory::getUuid).collect(Collectors.toList());
            if (isCurrent && srcSnapshotInv != null && dstSnapshotInv != null &&
                    aliveChainUuids.contains(srcSnapshotInv.getUuid()) && aliveChainUuids.contains(dstSnapshotInv.getUuid())) {
                inAliveChain = true;
            }
        }

        private void handleSnapshotWithoutParent() {
            // 3
            if (snapshotLeaf.getChildren().isEmpty()) {
                snapshotsToDelete.add(snapshotLeaf.getInventory());
            }

            //   3
            // 4...n
            if (snapshotLeaf.getChildren().size() > 1) {
                deleteDbOnly = true;
            }

            //  3
            //  4
            // ...
            snapshotsToDelete.add(snapshotLeaf.getInventory());
            dstSnapshotInv = snapshotLeaf.getInventory();
            VolumeTree.VolumeSnapshotLeaf srcSnapshotLeaf = buildSrcLeafAndSnapshotsToDeletedFromChildren(snapshotLeaf.getChildren().get(0));
            srcSnapshotInv = srcSnapshotLeaf.getInventory();
            requiredExtraSize = snapshotsToDeleteSize();
            srcChildrenInstallPath = srcSnapshotLeaf.getChildren().stream()
                    .map(leaf -> leaf.getInventory().getPrimaryStorageInstallPath()).collect(Collectors.toList());
            if (isCurrent && hasLatestSnapshot()) {
                newLatestSnapshotUuid = Objects.equals(srcSnapshotInv.getUuid(), volume.getUuid()) ? null : srcSnapshotInv.getUuid();
            }
        }

        private void handleSnapshotWithParent() {
            //  0
            // ...
            //  2
            //  3
            if (snapshotLeaf.getChildren().isEmpty()) {
                //...
                //(2)
                // 3 4
                //    ...
                if (parentSnapshotLeaf.getChildren().size() == 2 && Objects.equals(parentSnapshotLeaf.getStatus(), VolumeSnapshotStatus.Deleted.toString())) {
                    snapshotsToDelete.add(snapshotLeaf.getInventory());
                    snapshotsToDelete.add(parentSnapshotLeaf.getInventory());
                    VolumeTree.VolumeSnapshotLeaf srcSnapshotLeaf = buildSrcLeafAndSnapshotsToDeletedFromChildren(getSiblingLeaves(snapshotLeaf).get(0));
                    VolumeTree.VolumeSnapshotLeaf dstSnapshotLeaf = buildDstLeafAndSnapshotsToDeletedFromParent(parentSnapshotLeaf.getParent());
                    srcSnapshotInv = srcSnapshotLeaf.getInventory();
                    dstSnapshotInv = dstSnapshotLeaf.getInventory();
                    requiredExtraSize = snapshotsToDeleteSize();
                    srcChildrenInstallPath = srcSnapshotLeaf.getChildren().stream()
                            .map(leaf -> leaf.getInventory().getPrimaryStorageInstallPath()).collect(Collectors.toList());
                    newParentUuid = dstSnapshotInv.getParentUuid();
                    return;
                }

                //0
                //...
                //(2)
                // 3
                snapshotsToDelete.add(snapshotLeaf.getInventory());
                findDeletedSingleParentChain(parentSnapshotLeaf);
                if (!deletedSingleParentChain.isEmpty()) {
                    snapshotsToDelete.addAll(deletedSingleParentChain.stream().map(VolumeTree.VolumeSnapshotLeaf::getInventory).collect(Collectors.toList()));
                    if (hasLatestSnapshot()) {
                        Collections.reverse(deletedSingleParentChain);
                        newLatestSnapshotUuid = deletedSingleParentChain.get(0).getParent().getUuid();
                    }
                }
                return;
            }

            //  0
            // ...
            // (2)
            //  3
            //  4
            // ...
            if (snapshotLeaf.getChildren().size() == 1) {
                snapshotsToDelete.add(snapshotLeaf.getInventory());
                VolumeTree.VolumeSnapshotLeaf srcSnapshotLeaf = buildSrcLeafAndSnapshotsToDeletedFromChildren(snapshotLeaf.getChildren().get(0));
                VolumeTree.VolumeSnapshotLeaf dstSnapshotLeaf = buildDstLeafAndSnapshotsToDeletedFromParent(parentSnapshotLeaf);

                srcSnapshotInv = srcSnapshotLeaf.getInventory();
                dstSnapshotInv = dstSnapshotLeaf.getInventory();
                requiredExtraSize = snapshotsToDeleteSize();
                srcChildrenInstallPath = srcSnapshotLeaf.getChildren().stream()
                        .map(leaf -> leaf.getInventory().getPrimaryStorageInstallPath()).collect(Collectors.toList());
                newParentUuid = dstSnapshotInv.getParentUuid();
                if (hasLatestSnapshot()) {
                    newLatestSnapshotUuid = Objects.equals(srcSnapshotInv.getUuid(), volume.getUuid()) ?
                            dstSnapshotInv.getParentUuid() : srcSnapshotLeaf.getUuid();
                }
                return;
            }

            //  0
            // ...连续的Deleted且只有一个child的节点
            // (2)
            //  3
            // 4 5
            if (parentSnapshotLeaf.getChildren().size() == 1 &&
                    Objects.equals(parentSnapshotLeaf.getStatus(), VolumeSnapshotStatus.Deleted.toString())) {
                VolumeTree.VolumeSnapshotLeaf srcSnapshotLeaf = snapshotLeaf;
                VolumeTree.VolumeSnapshotLeaf dstSnapshotLeaf = buildDstLeafAndSnapshotsToDeletedFromParent(parentSnapshotLeaf);
                srcSnapshotInv = srcSnapshotLeaf.getInventory();
                dstSnapshotInv = dstSnapshotLeaf.getInventory();
                requiredExtraSize = snapshotsToDeleteSize();
                srcChildrenInstallPath = srcSnapshotLeaf.getChildren().stream()
                        .map(leaf -> leaf.getInventory().getPrimaryStorageInstallPath()).collect(Collectors.toList());
                newParentUuid = dstSnapshotInv.getParentUuid();
                if (hasLatestSnapshot()) {
                    newLatestSnapshotUuid = srcSnapshotLeaf.getUuid();
                }
                return;
            }
            deleteDbOnly = true;
        }

        private VolumeTree.VolumeSnapshotLeaf buildSrcLeafAndSnapshotsToDeletedFromChildren(VolumeTree.VolumeSnapshotLeaf leaf) {
            findDeletedSingleChildChain(leaf);
            if (deletedSingleChildrenChain.isEmpty()) {
                return leaf;
            }

            Collections.reverse(deletedSingleChildrenChain);
            VolumeTree.VolumeSnapshotLeaf srcLeaf = deletedSingleChildrenChain.get(0);
            snapshotsToDelete.addAll(deletedSingleChildrenChain.subList(1, deletedSingleChildrenChain.size())
                    .stream().map(VolumeTree.VolumeSnapshotLeaf::getInventory).collect(Collectors.toList()));
            if (srcLeaf.getChildren().size() == 1) {
                srcLeaf = srcLeaf.getChildren().get(0);
                snapshotsToDelete.add(deletedSingleChildrenChain.get(0).getInventory());
            }

            return srcLeaf;
        }

        private VolumeTree.VolumeSnapshotLeaf buildDstLeafAndSnapshotsToDeletedFromParent(VolumeTree.VolumeSnapshotLeaf leaf) {
            findDeletedSingleParentChain(leaf);
            if (!deletedSingleParentChain.isEmpty()) {
                return leaf;
            }

            VolumeTree.VolumeSnapshotLeaf dstSnapshotLeaf;
            Collections.reverse(deletedSingleParentChain);
            dstSnapshotLeaf = deletedSingleParentChain.get(0);
            snapshotsToDelete.addAll(deletedSingleParentChain
                    .stream().map(VolumeTree.VolumeSnapshotLeaf::getInventory).collect(Collectors.toList()));
            return dstSnapshotLeaf;
        }

        private void findDeletedSingleChildChain(VolumeTree.VolumeSnapshotLeaf leaf) {
            if (Objects.equals(leaf.getStatus(), VolumeSnapshotStatus.Deleted.toString()) && leaf.getChildren().size() == 1) {
                deletedSingleChildrenChain.add(leaf);
                if (leaf.getChildren().isEmpty()) {
                    return;
                }
                findDeletedSingleChildChain(leaf.getChildren().get(0));
            }
        }

        private void findDeletedSingleParentChain(VolumeTree.VolumeSnapshotLeaf leaf) {
            if (Objects.equals(leaf.getStatus(), VolumeSnapshotStatus.Deleted.toString()) && leaf.getChildren().size() == 1) {
                deletedSingleParentChain.add(leaf);
                if (leaf.getParent() == null) {
                    return;
                }
                findDeletedSingleParentChain(leaf.getParent());
            }
        }

        private long snapshotsToDeleteSize() {
            return snapshotsToDelete.stream().filter(snapshot -> !Objects.equals(snapshot.getUuid(), volume.getUuid()))
                    .mapToLong(VolumeSnapshotInventory::getSize).sum();
        }

        private boolean hasLatestSnapshot() {
            return snapshotsToDelete.stream().anyMatch(VolumeSnapshotInventory::isLatest);
        }

        public VolumeSnapshotInventory getSrcSnapshotInv() {
            return srcSnapshotInv;
        }

        public VolumeSnapshotInventory getDstSnapshotInv() {
            return dstSnapshotInv;
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

    public static VolumeTree fromVOs(List<VolumeSnapshotVO> vos, boolean current, VolumeVO volumeVO) {
        List<VolumeSnapshotInventory> invs = VolumeSnapshotInventory.valueOf(vos);

        VolumeTree tree = new VolumeTree();
        if (current) {
            VolumeSnapshotInventory latestInv = invs.stream().filter(VolumeSnapshotInventory::isLatest).collect(Collectors.toList()).get(0);
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

        tree.isCurrent = current;
        if (tree.getSnapshotLeaf(volumeVO.getUuid()) != null) {
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
