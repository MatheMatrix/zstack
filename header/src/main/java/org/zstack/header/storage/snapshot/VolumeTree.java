package org.zstack.header.storage.snapshot;

import org.zstack.header.vm.VmInstanceState;
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
    private List<VolumeSnapshotInventory> aliveChainSnapshots = new ArrayList<>();

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
        final VmInstanceState vmInstanceState;

        VolumeTree.VolumeSnapshotLeaf targetSnapshotLeaf;
        VolumeTree.VolumeSnapshotLeaf targetParentSnapshotLeaf;
        final List<VolumeTree.VolumeSnapshotLeaf> snapshotChainWhichStatusIsDeletedAndWithSingleChildren = new ArrayList<>();
        final List<VolumeTree.VolumeSnapshotLeaf> snapshotChainWhichStatusIsDeletedAndWithSingleParent = new ArrayList<>();

        VolumeTree.VolumeSnapshotLeaf srcSnapshotLeaf;
        VolumeTree.VolumeSnapshotLeaf dstSnapshotLeaf;
        List<VolumeSnapshotInventory> snapshotsToDelete = new ArrayList<>();
        List<VolumeSnapshotInventory> snapshotChainFromSrcToDst = new ArrayList<>();
        List<String> srcChildrenInstallPath = new ArrayList<>();
        List<VolumeSnapshotVO> allReadySnapshots;
        List<String> aliveChainSnapshotUuids;
        long requiredExtraSize;
        String newLatestSnapshotUuid;
        String newParentUuid;
        boolean deleteDbOnly = false;
        String direction;
        boolean online;
        String errorString;

        VolumeSnapshotDeletionContext(VolumeSnapshotVO targetSnapshot, VmInstanceState vmInstanceState, String direction) {
            this.targetSnapshot = targetSnapshot;
            this.vmInstanceState = vmInstanceState;
            this.direction = direction;
            this.targetSnapshotLeaf = getSnapshotLeaf(targetSnapshot.getUuid());
            this.targetParentSnapshotLeaf = targetSnapshotLeaf.getParent();
            this.aliveChainSnapshotUuids = aliveChainSnapshots.stream()
                    .map(VolumeSnapshotInventory::getUuid).collect(Collectors.toList());
            this.allReadySnapshots = allSnapshots.stream()
                    .filter(vo -> vo.getStatus() == VolumeSnapshotStatus.Ready)
                    .filter(vo -> !Objects.equals(vo.getUuid(), volume.getUuid())).collect(Collectors.toList());

            if (VolumeSnapshotConstant.STORAGE_SNAPSHOT_TYPE.toString().equals(targetSnapshot.getType())) {
                snapshotsToDelete.add(VolumeSnapshotInventory.valueOf(targetSnapshot));
                return;
            }

            if (allReadySnapshots.size() == 1) {
                handleLastReadySnapshot();
                return;
            }

            if (targetParentSnapshotLeaf == null) {
                handleSnapshotWithoutParent();
            } else {
                handleSnapshotWithParent();
            }

            if (deleteDbOnly) {
                return;
            }

            setSnapshotsToDelete();

            if (dstSnapshotLeaf == null) {
                return;
            }

            online = (vmInstanceState == VmInstanceState.Running || vmInstanceState == VmInstanceState.Paused)
                    && aliveChainSnapshotUuids.contains(dstSnapshotLeaf.getUuid());
            setRequiredExtraSize();
        }

        private void handleLastReadySnapshot() {
            VolumeSnapshotVO lastReadySnapshot = allReadySnapshots.get(0);
            if (!lastReadySnapshot.getUuid().equals(targetSnapshot.getUuid())) {
                this.errorString = String.format("the snapshot[uuid:%s] is the only ready snapshot in the snapshot tree[uuid:%s], " +
                                "but the snapshot[uuid:%s] is not the target snapshot[uuid:%s].",
                        lastReadySnapshot.getUuid(), targetSnapshot.getTreeUuid(), targetSnapshot.getUuid(), lastReadySnapshot.getUuid());
                return;
            }

            allSnapshots.forEach(vo -> {
                if (!Objects.equals(vo.getUuid(), volume.getUuid())) {
                    snapshotsToDelete.add(VolumeSnapshotInventory.valueOf(vo));
                }
            });

            if (isCurrent) {
                getTrueDirection(getSnapshotLeaf(volume.getUuid()));

                if (direction.equals(DeleteVolumeSnapshotContent.COMMIT)) {
                    srcSnapshotLeaf = getSnapshotLeaf(volume.getUuid());
                    dstSnapshotLeaf = root;
                } else {
                    dstSnapshotLeaf = getSnapshotLeaf(volume.getUuid());
                }
                requiredExtraSize = snapshotsToDelete.stream().mapToLong(VolumeSnapshotInventory::getSize).sum();
                setSnapshotChainFromSrcToDst();
                this.online = (vmInstanceState == VmInstanceState.Running || vmInstanceState == VmInstanceState.Paused);
                // 当前快照树只有一个节点是Ready状态，当前快照树current为true。使用direction删除当前快照树，需要将当前快照树的根节点的uuid作为新的latest快照uuid。
                logger.debug(String.format("The snapshot tree[uuid:%s] only contains one ready snapshot[uuid:%s], " +
                        "delete the snapshot tree %s.", targetSnapshot.getTreeUuid(), targetSnapshot.getUuid(), this.direction));
                return;
            }
            logger.debug(String.format("the snapshot tree[uuid:%s] only contains one ready snapshot[uuid:%s], " +
                    "delete the snapshot tree directly.", targetSnapshot.getTreeUuid(), targetSnapshot.getUuid()));
        }

        private void handleSnapshotWithoutParent() {
            if (targetSnapshotLeaf.getChildren().isEmpty()) {
                // 当前快照没有父节点和没有子快照，直接删除当前快照。
                logger.debug(String.format("The snapshot[uuid:%s] has no parent and children, delete the snapshot directly.",
                        targetSnapshotLeaf.getUuid()));
                return;
            }

            if (targetSnapshotLeaf.getChildren().size() > 1) {
                // 当前快照子节点数量大于1，将当前快照节点数据库状态标记为Deleted
                logger.debug(String.format("The snapshot[uuid:%s] has more than one children, mark the snapshot as Deleted.",
                        targetSnapshotLeaf.getUuid()));
                deleteDbOnly = true;
                return;
            }

            fillSnapshotChainWhichStatusIsDeletedAndWithSingleChildren(targetSnapshotLeaf.getChildren().get(0));
            VolumeSnapshotLeaf childrenLeaf = getChildrenLeafLeaf(targetSnapshotLeaf.getChildren().get(0));

            if (childrenLeaf == null) {
                // 当前快照节点的子节点是一条单链，每个子节点是状态是Deleted，直接删除当前节点和子节点链。
                logger.debug(String.format("the snapshot[uuid:%s] has a single chain of children, " +
                        "delete the snapshot and children chain.", targetSnapshotLeaf.getUuid()));
                return;
            }

            getTrueDirection(childrenLeaf);

            if (Objects.equals(direction, DeleteVolumeSnapshotContent.PULL)) {
                dstSnapshotLeaf = childrenLeaf;
                return;
            }

            srcSnapshotLeaf = childrenLeaf;
            dstSnapshotLeaf = targetSnapshotLeaf;
            srcChildrenInstallPath = srcSnapshotLeaf.getChildren().stream().map(leaf ->
                    leaf.getInventory().getPrimaryStorageInstallPath()).collect(Collectors.toList());
        }

        private void handleSnapshotWithParent() {
            if (targetSnapshotLeaf.getChildren().size() > 1) {
                // 当前快照子节点数量大于1，将当前快照节点数据库状态标记为Deleted
                logger.debug(String.format("The snapshot[uuid:%s] has more than one children, mark the snapshot as Deleted.",
                        targetSnapshotLeaf.getUuid()));
                deleteDbOnly = true;
                return;
            }

            if (targetSnapshotLeaf.getChildren().isEmpty()) {
                // 当前快照节点的父节点是一条单链，每个子节点是状态是Deleted，直接删除当前节点和子节点链。
                logger.debug(String.format("The snapshot[uuid:%s] has a single chain of children, delete the snapshot and children chain.",
                        targetSnapshotLeaf.getUuid()));
                fillSnapshotChainWhichStatusIsDeletedAndWithSingleParent(targetParentSnapshotLeaf);
                return;
            }

            fillSnapshotChainWhichStatusIsDeletedAndWithSingleChildren(targetSnapshotLeaf.getChildren().get(0));
            VolumeSnapshotLeaf childrenLeaf = getChildrenLeafLeaf(targetSnapshotLeaf.getChildren().get(0));

            fillSnapshotChainWhichStatusIsDeletedAndWithSingleParent(targetParentSnapshotLeaf);
            VolumeSnapshotLeaf lastStatusIsDeletedParentLeaf = getParentLeaf(targetSnapshotLeaf);

            if (childrenLeaf == null) {
                logger.debug(String.format("the snapshot[uuid:%s] has a single chain of children, " +
                        "delete the snapshot and children chain.", targetSnapshotLeaf.getUuid()));
                return;
            }

            getTrueDirection(childrenLeaf);

            if (Objects.equals(direction, DeleteVolumeSnapshotContent.COMMIT)) {
                srcSnapshotLeaf = childrenLeaf;
                dstSnapshotLeaf = lastStatusIsDeletedParentLeaf;
                newParentUuid = dstSnapshotLeaf.getParent() == null ? null : dstSnapshotLeaf.getParent().getUuid();
                srcChildrenInstallPath = srcSnapshotLeaf.getChildren().stream().map(leaf ->
                        leaf.getInventory().getPrimaryStorageInstallPath()).collect(Collectors.toList());
                if (isCurrent && Objects.equals(srcSnapshotLeaf.getUuid(), volume.getUuid()) && dstSnapshotLeaf.getParent() != null) {
                    newLatestSnapshotUuid = dstSnapshotLeaf.getParent().getUuid();
                }
                return;
            }

            srcSnapshotLeaf = lastStatusIsDeletedParentLeaf.getParent();
            dstSnapshotLeaf = childrenLeaf;
            newParentUuid = srcSnapshotLeaf == null ? null : srcSnapshotLeaf.getUuid();
            if (isCurrent && Objects.equals(dstSnapshotLeaf.getUuid(), volume.getUuid()) && srcSnapshotLeaf != null) {
                newLatestSnapshotUuid = srcSnapshotLeaf.getUuid();
            }
        }

        private void fillSnapshotChainWhichStatusIsDeletedAndWithSingleChildren(VolumeTree.VolumeSnapshotLeaf leaf) {
            if ((leaf.getChildren().isEmpty() || leaf.getChildren().size() == 1) &&
                    Objects.equals(leaf.getStatus(), VolumeSnapshotStatus.Deleted.toString())) {
                snapshotChainWhichStatusIsDeletedAndWithSingleChildren.add(leaf);
                if (leaf.getChildren().size() != 1) {
                    return;
                }
                fillSnapshotChainWhichStatusIsDeletedAndWithSingleChildren(leaf.getChildren().get(0));
            }
        }

        private void fillSnapshotChainWhichStatusIsDeletedAndWithSingleParent(VolumeTree.VolumeSnapshotLeaf leaf) {
            if (leaf != null && Objects.equals(leaf.getStatus(), VolumeSnapshotStatus.Deleted.toString()) &&
                    leaf.getChildren().size() == 1) {
                snapshotChainWhichStatusIsDeletedAndWithSingleParent.add(leaf);
                fillSnapshotChainWhichStatusIsDeletedAndWithSingleParent(leaf.getParent());
            }
        }

        private VolumeTree.VolumeSnapshotLeaf getChildrenLeafLeaf(VolumeTree.VolumeSnapshotLeaf leaf) {
            if (snapshotChainWhichStatusIsDeletedAndWithSingleChildren.isEmpty()) {
                return leaf;
            }
            Collections.reverse(snapshotChainWhichStatusIsDeletedAndWithSingleChildren);
            return snapshotChainWhichStatusIsDeletedAndWithSingleChildren.get(0).getChildren().isEmpty() ?
                    null : snapshotChainWhichStatusIsDeletedAndWithSingleChildren.get(0).getChildren().get(0);
        }

        private VolumeTree.VolumeSnapshotLeaf getParentLeaf(VolumeTree.VolumeSnapshotLeaf leaf) {
            if (snapshotChainWhichStatusIsDeletedAndWithSingleParent.isEmpty()) {
                return leaf;
            }
            Collections.reverse(snapshotChainWhichStatusIsDeletedAndWithSingleParent);
            return snapshotChainWhichStatusIsDeletedAndWithSingleParent.get(0);
        }

        private void setSnapshotChainFromSrcToDst() {
            if (Objects.equals(direction, DeleteVolumeSnapshotContent.COMMIT)) {
                for (VolumeSnapshotInventory ancestor : srcSnapshotLeaf.getAncestors()) {
                    if (Objects.equals(ancestor.getUuid(), dstSnapshotLeaf.getUuid())) {
                        snapshotChainFromSrcToDst.add(ancestor);
                        break;
                    }
                    snapshotChainFromSrcToDst.add(ancestor);
                }
            }

            if (srcSnapshotLeaf == null) {
                snapshotChainFromSrcToDst.addAll(dstSnapshotLeaf.getAncestors());
                Collections.reverse(snapshotChainFromSrcToDst);
                return;
            }

            for (VolumeSnapshotInventory ancestor : dstSnapshotLeaf.getAncestors()) {
                if (Objects.equals(ancestor.getUuid(), srcSnapshotLeaf.getUuid())) {
                    snapshotChainFromSrcToDst.add(ancestor);
                    break;
                }
                snapshotChainFromSrcToDst.add(ancestor);
            }
            Collections.reverse(snapshotChainFromSrcToDst);
        }

        private boolean isOnlyCommitAllowed(VolumeTree.VolumeSnapshotLeaf leaf) {
            boolean notVolume = !Objects.equals(leaf.getUuid(), volume.getUuid());
            boolean inAliveChain = aliveChainSnapshotUuids.contains(leaf.getUuid());
            boolean vmRunningOrPaused = (vmInstanceState == VmInstanceState.Running || vmInstanceState == VmInstanceState.Paused);

            return isCurrent && notVolume && inAliveChain && vmRunningOrPaused;
        }

        private void getTrueDirection(VolumeTree.VolumeSnapshotLeaf leaf) {
            if (Objects.equals(direction, DeleteVolumeSnapshotContent.PULL)) {
                if (isOnlyCommitAllowed(leaf)) {
                    errorString = String.format("The snapshot[uuid:%s] will be deleted by block %s, but the direction is %s, " +
                            "change the direction to pull.", targetSnapshotLeaf.getUuid(), direction, DeleteVolumeSnapshotContent.PULL);
                    return;
                }
            }

            if (Objects.equals(direction, DeleteVolumeSnapshotContent.AUTO)) {
                if (isOnlyCommitAllowed(leaf)) {
                    direction = DeleteVolumeSnapshotContent.COMMIT;
                    return;
                }
                direction = DeleteVolumeSnapshotContent.PULL;
            }
            logger.debug(String.format("the snapshot[uuid:%s] will be deleted by block %s.", targetSnapshotLeaf.getUuid(), direction));
        }

        private void setSnapshotsToDelete() {
            snapshotChainWhichStatusIsDeletedAndWithSingleChildren.forEach(leaf -> snapshotsToDelete.add(leaf.getInventory()));
            snapshotsToDelete.add(targetSnapshotLeaf.getInventory());
            Collections.reverse(snapshotChainWhichStatusIsDeletedAndWithSingleParent);
            snapshotChainWhichStatusIsDeletedAndWithSingleParent.forEach(leaf -> snapshotsToDelete.add(leaf.getInventory()));
        }

        private void setRequiredExtraSize() {
            if (Objects.equals(direction, DeleteVolumeSnapshotContent.PULL)) {
                requiredExtraSize = snapshotsToDelete.stream().mapToLong(VolumeSnapshotInventory::getSize).sum();
                return;
            }

            requiredExtraSize = snapshotsToDelete.stream()
                    .filter(vo -> !Objects.equals(vo.getUuid(), dstSnapshotLeaf.getUuid()))
                    .mapToLong(VolumeSnapshotInventory::getSize).sum();
            requiredExtraSize += srcSnapshotLeaf.getInventory().getSize();
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

        public List<VolumeSnapshotInventory> getSnapshotChainFromSrcToDst() {
            return snapshotChainFromSrcToDst;
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

        public String getDirection() {
            return direction;
        }

        public boolean isOnline() {
            return online;
        }

        public String getErrorString() {
            return errorString;
        }

        public VolumeSnapshotLeaf getTreeRootLeaf() {
            return root;
        }
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
            tree.aliveChainSnapshots = leaf != null ? leaf.getAncestors() : new ArrayList<>();
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

    public VolumeSnapshotDeletionContext createDeletionContext(VolumeSnapshotVO targetSnapshot, VmInstanceState vmInstanceState, String direction) {
        return new VolumeSnapshotDeletionContext(targetSnapshot, vmInstanceState, direction);
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
