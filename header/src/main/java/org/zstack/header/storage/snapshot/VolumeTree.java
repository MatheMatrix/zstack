package org.zstack.header.storage.snapshot;

import org.zstack.header.storage.primary.CommitVolumeSnapshotOnPrimaryStorageMsg;
import org.zstack.header.storage.primary.PullVolumeSnapshotOnPrimaryStorageMsg;
import org.zstack.header.vm.VmInstanceState;
import org.zstack.header.volume.VolumeInventory;
import org.zstack.header.volume.VolumeVO;
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
    VolumeVO volume;
    List<VolumeSnapshotVO> allSnapshots = new ArrayList<>();
    private boolean isCurrent;
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

    public class VolumeSnapshotDeletionContext {
        VolumeSnapshotInventory targetSnapshot;
        VolumeSnapshotLeaf targetSnapshotLeaf;
        VmInstanceState vmState;
        boolean online;
        String errorString;
        List<String> aliveChainSnapshotUuids;
        CommitVolumeSnapshotOnPrimaryStorageMsg commitMsg;
        List<PullVolumeSnapshotOnPrimaryStorageMsg> pullMsgs = new ArrayList<>();

        VolumeSnapshotDeletionContext(VolumeSnapshotInventory targetSnapshot, VmInstanceState vmState, String direction) {
            initializeFields(targetSnapshot, vmState);

            if (shouldSkipProcessing()) {
                return;
            }

            processChildrenByDirection(direction);
        }

        private boolean shouldSkipProcessing() {
            return VolumeSnapshotConstant.STORAGE_SNAPSHOT_TYPE.toString().equals(targetSnapshot.getType())
                    || targetSnapshotLeaf == null
                    || targetSnapshotLeaf.getChildren().isEmpty();
        }

        private void initializeFields(VolumeSnapshotInventory targetSnapshot, VmInstanceState vmState) {
            this.targetSnapshot = targetSnapshot;
            this.targetSnapshotLeaf = getSnapshotLeaf(targetSnapshot.getUuid());
            this.aliveChainSnapshotUuids = aliveChain.stream().map(VolumeSnapshotInventory::getUuid).collect(Collectors.toList());
            this.vmState = vmState;
            this.online = determineOnlineStatus(vmState);
        }

        private boolean determineOnlineStatus(VmInstanceState vmState) {
            return (vmState == VmInstanceState.Running || vmState == VmInstanceState.Paused)
                    && aliveChainSnapshotUuids.contains(targetSnapshot.getUuid());
        }

        private void processChildrenByDirection(String direction) {
            direction = resolveDirection(vmState, direction);
            if (errorString != null) {
                return;
            }

            if (Objects.equals(direction, DeleteVolumeSnapshotDirection.Pull.toString())) {
                handlePullDirection(targetSnapshotLeaf.getChildren());
                return;
            }
            handleCommitDirection(targetSnapshotLeaf.getChildren());
        }

        private void handlePullDirection(List<VolumeSnapshotLeaf> children) {
            children.forEach(leaf -> pullMsgs.add(createPullMessage(leaf)));
        }

        private void handleCommitDirection(List<VolumeSnapshotLeaf> children) {
            children.forEach(leaf -> {
                if (!aliveChainSnapshotUuids.contains(leaf.getUuid())) {
                    pullMsgs.add(createPullMessage(leaf));
                    return;
                }

                commitMsg = new CommitVolumeSnapshotOnPrimaryStorageMsg();
                commitMsg.setVolume(VolumeInventory.valueOf(volume));
                commitMsg.setSrcSnapshot(leaf.getInventory());
                commitMsg.setDstSnapshot(targetSnapshot);
                commitMsg.setOnline(online);
                if (leaf.getChildren().size() > 1) {
                    commitMsg.setSrcChildrenInstallPathInDb(leaf.getChildren()
                            .stream().map(child -> child.getInventory().getPrimaryStorageInstallPath())
                            .collect(Collectors.toList()));
                }
            });
        }

        private PullVolumeSnapshotOnPrimaryStorageMsg createPullMessage(VolumeSnapshotLeaf child) {
            PullVolumeSnapshotOnPrimaryStorageMsg pullMsg = new PullVolumeSnapshotOnPrimaryStorageMsg();
            pullMsg.setVolume(VolumeInventory.valueOf(volume));
            pullMsg.setSrcSnapshot(targetSnapshot);
            pullMsg.setDstSnapshot(child.getInventory());
            pullMsg.setOnline(online && aliveChainSnapshotUuids.contains(child.getUuid()));
            return pullMsg;
        }

        private boolean shouldUseCommitStrategy(String targetSnapshotUuid, VmInstanceState vmInstanceState) {
            boolean notVolume = !Objects.equals(targetSnapshotUuid, volume.getUuid());
            boolean inAliveChain = aliveChainSnapshotUuids.contains(targetSnapshotUuid);
            boolean vmRunningOrPaused = (vmInstanceState == VmInstanceState.Running || vmInstanceState == VmInstanceState.Paused);

            return isCurrent && notVolume && inAliveChain && vmRunningOrPaused;
        }

        private String resolveDirection(VmInstanceState vmState, String initialDirection) {
            if (Objects.equals(initialDirection, DeleteVolumeSnapshotDirection.Pull.toString())) {
                if (shouldUseCommitStrategy(targetSnapshot.getUuid(), vmState)) {
                    errorString = String.format("the snapshot[uuid:%s] will be deleted by block %s, but the direction is %s, " +
                            "change the direction to pull.", targetSnapshot.getUuid(), initialDirection, DeleteVolumeSnapshotDirection.Pull);
                    return initialDirection;
                }
            }

            if (Objects.equals(initialDirection, DeleteVolumeSnapshotScope.Auto.toString())) {
                return shouldUseCommitStrategy(targetSnapshot.getUuid(), vmState) ?
                        DeleteVolumeSnapshotDirection.Commit.toString() :
                        DeleteVolumeSnapshotDirection.Pull.toString();
            }
            return initialDirection;
        }

        public String getErrorString() {
            return errorString;
        }

        public VolumeSnapshotInventory getTargetSnapshot() {
            return targetSnapshot;
        }

        public CommitVolumeSnapshotOnPrimaryStorageMsg getCommitMsg() {
            return commitMsg;
        }

        public List<PullVolumeSnapshotOnPrimaryStorageMsg> getPullMsgs() {
            return pullMsgs;
        }
    }

    public VolumeSnapshotDeletionContext createDeletionContext(VolumeSnapshotInventory targetSnapshot, VmInstanceState vmState, String direction) {
        return new VolumeSnapshotDeletionContext(targetSnapshot, vmState, direction);
    }

    public static VolumeTree fromVOs(List<VolumeSnapshotVO> vos, boolean isCurrent, VolumeVO volumeVO) {
        List<VolumeSnapshotVO> noParentVos = vos.stream().filter(it -> it.getParentUuid() == null).collect(Collectors.toList());
        if (noParentVos.size() != 1) {
            throw new IllegalArgumentException(String.format("There are %d root snapshots on tree[uuid:%s]",
                    noParentVos.size(), vos.get(0).getTreeUuid()));
        }

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
            VolumeSnapshotLeaf leaf = tree.getSnapshotLeaf(volumeVO.getUuid());
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
}
