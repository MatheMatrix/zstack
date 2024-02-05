package org.zstack.header.storage.snapshot;

import org.zstack.header.vm.VmInstanceState;
import org.zstack.header.volume.VolumeInventory;
import org.zstack.utils.DebugUtils;
import org.zstack.utils.Utils;
import org.zstack.utils.function.Function;
import org.zstack.utils.logging.CLogger;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * When building the VolumeTree, if the tree is current, then add the volume to the VolumeTree.
 */
public class VolumeTree {
    private static final CLogger logger = Utils.getLogger(VolumeTree.class);

    private VolumeSnapshotLeaf root;
    VolumeInventory volume;
    List<VolumeSnapshotVO> allSnapshots = new ArrayList<>();
    private boolean current;

    public boolean isCurrent() {
        return current;
    }

    // the aliveChainSnapshots represents a chain where the volume is located, but only if the VolumeTree contains that volume.
    // If the VolumeTree does not contain the volume, then aliveChainInventory should be empty.
    private List<VolumeSnapshotInventory> aliveChain = new ArrayList<>();

    private static class VolumeSnapshotLeafInventory {
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

    public static VolumeTree fromVOs(List<VolumeSnapshotVO> vos, boolean isCurrent, VolumeInventory volumeInv) {
        List<VolumeSnapshotVO> noParentVos = vos.stream().filter(it -> it.getParentUuid() == null).collect(Collectors.toList());
        if (noParentVos.size() != 1) {
            throw new IllegalArgumentException(String.format("There are %d root snapshots on tree[uuid:%s]",
                    noParentVos.size(), vos.get(0).getTreeUuid()));
        }

        List<VolumeSnapshotInventory> invs = VolumeSnapshotInventory.valueOf(vos);

        VolumeTree tree = new VolumeTree();
        tree.current = isCurrent;
        tree.volume = volumeInv;
        if (tree.current) {
            VolumeSnapshotVO snapshotVO = new VolumeSnapshotVO();
            snapshotVO.setLatest(false);
            snapshotVO.setName(String.format("volume-%s-%s", volumeInv.getName(), volumeInv.getUuid()));
            snapshotVO.setUuid(volumeInv.getUuid());
            VolumeSnapshotInventory latestInv = findLatestVolumeSnapshotInventory(invs);
            snapshotVO.setParentUuid(latestInv.getUuid());
            snapshotVO.setTreeUuid(latestInv.getTreeUuid());
            snapshotVO.setState(VolumeSnapshotState.Enabled);
            snapshotVO.setStatus(VolumeSnapshotStatus.Ready);
            snapshotVO.setPrimaryStorageInstallPath(volumeInv.getInstallPath());
            snapshotVO.setPrimaryStorageUuid(volumeInv.getPrimaryStorageUuid());
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

        if (tree.current) {
            VolumeSnapshotLeaf leaf = tree.getSnapshotLeaf(volumeInv.getUuid());
            tree.aliveChain = leaf != null ? leaf.getAncestors() : new ArrayList<>();
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

    public List<String> getAliveChainSnapshotUuids() {
        return aliveChain.stream().map(VolumeSnapshotInventory::getUuid).collect(Collectors.toList());
    }

    private List<VolumeSnapshotInventory> getSnapshotAllDescendants(String snapshotUuid) {
        VolumeSnapshotLeaf leaf = getSnapshotLeaf(snapshotUuid);
        if (leaf == null) {
            return new ArrayList<>();
        }

        List<VolumeSnapshotLeaf> descendants = new ArrayList<>();
        Queue<VolumeSnapshotLeaf> queue = new LinkedList<>(leaf.children);
        while (!queue.isEmpty()) {
            VolumeSnapshotLeaf current = queue.poll();
            descendants.add(current);
            queue.addAll(current.children);
        }
        return descendants.stream().map(VolumeSnapshotLeaf::getInventory).collect(Collectors.toList());
    }

    public List<String> getSnapshotAllDescendantsUuid(String snapshotUuid) {
        return getSnapshotAllDescendants(snapshotUuid).stream().map(VolumeSnapshotInventory::getUuid).collect(Collectors.toList());
    }

    public String resolveDirection(String snapshotUuid, String initialDirection, boolean isLatest, VmInstanceState vmState) {
        boolean online = (vmState == VmInstanceState.Running || vmState == VmInstanceState.Paused)
                && getAliveChainSnapshotUuids().contains(snapshotUuid);

        boolean shouldUseCommitStrategy = current && !isLatest && online;

        if (Objects.equals(initialDirection, DeleteVolumeSnapshotDirection.Pull.toString())) {
            if (shouldUseCommitStrategy) {
                throw new IllegalArgumentException("the snapshot will be deleted by block 'commit', but the direction is 'pull', " +
                        "change the direction to 'commit' or 'auto'.");
            }
        }

        if (Objects.equals(initialDirection, DeleteVolumeSnapshotDirection.Auto.toString())) {
            return shouldUseCommitStrategy ?
                    DeleteVolumeSnapshotDirection.Commit.toString() :
                    DeleteVolumeSnapshotDirection.Pull.toString();
        }
        return initialDirection;
    }
}
