# 03 — direction 决策（resolveDirection）

## 3.1 核心代码

**文件**：`storage/src/main/java/org/zstack/storage/snapshot/VolumeTree.java:364`

```java
public DeleteVolumeSnapshotDirection resolveDirection(
        String targetSnapshotUuid,        // 待删节点（dst, 老节点）
        String childSnapshotUuid,         // 待删节点的子节点（src, 新节点）
        String initialDirection,          // 用户传入的 direction
        boolean targetSnapshotIsLatest,   // 待删节点是否 latest
        VmInstanceState vmState) {

    boolean online =
        (vmState == VmInstanceState.Running || vmState == VmInstanceState.Paused)
        && getAliveChainSnapshotUuids().contains(targetSnapshotUuid)
        && getAliveChainSnapshotUuids().contains(childSnapshotUuid);

    boolean shouldUseCommitStrategy = current && !targetSnapshotIsLatest && online;

    if (Objects.equals(initialDirection, DeleteVolumeSnapshotDirection.Pull.toString())
            && shouldUseCommitStrategy) {
        throw new IllegalArgumentException(
            "the snapshot will be deleted by block 'commit', but the direction is 'pull', " +
            "change the direction to 'commit' or 'auto'.");
    }

    if (initialDirection == null) return DeleteVolumeSnapshotDirection.Commit;

    if (Objects.equals(initialDirection, DeleteVolumeSnapshotDirection.Auto.toString())) {
        return shouldUseCommitStrategy
                ? DeleteVolumeSnapshotDirection.Commit
                : DeleteVolumeSnapshotDirection.Pull;
    }

    return DeleteVolumeSnapshotDirection.fromString(initialDirection);
}
```

## 3.2 决策表

| current | targetIsLatest | online | initialDirection | 结果 |
|---|---|---|---|---|
| 任意 | 任意 | 任意 | `null` | **Commit**（兜底） |
| true | false | true | `pull` | **抛 IllegalArgumentException** |
| true | false | true | `auto` | **Commit** |
| 其它组合 | — | — | `auto` | **Pull** |
| 任意 | 任意 | 任意 | `commit` | **Commit** |
| 任意 | 任意 | 任意 | `pull`（合法） | **Pull** |

## 3.3 关键字段含义

| 字段 | 含义 |
|---|---|
| `current` (`VolumeTree.current`，第38行) | 来自 `VolumeSnapshotTreeVO.current`，true 表示快照链尾连着活跃 volume |
| `targetSnapshotIsLatest` | 来自 `VolumeSnapshotVO.latest = 1`，调用方传 `currentRoot.isLatest()` |
| `aliveChain` | volume 沿 backing chain 上溯到根的所有节点，代表"qemu 当前持有的文件链" |

## 3.4 调用方

`VolumeSnapshotTreeBase.java:904`：
```java
DeleteVolumeSnapshotDirection direction = volumeTree.resolveDirection(
    currentRoot.getUuid(),       // 待删节点
    child.getUuid(),             // 子节点
    msg.getDirection(),          // 用户传入
    currentRoot.isLatest(),      // 来自 DB
    vmState);
```

## 3.5 `VolumeTree.fromVOs()` 构建过程

`VolumeTree.java:260-327`：

1. 校验：至多一个根（`parentUuid == null`）、至多一个 latest
2. 若 `current && 有 latest`，把 **volume 自身作为虚拟叶节点** 挂到 latest 之后（uuid = volume uuid）
3. HashMap 还原 parent/children
4. 从 volume 虚拟节点向上收集 `aliveChain`

```java
// 步骤 3：构建树
Map<String, VolumeSnapshotLeaf> map = new HashMap<>();
for (VolumeSnapshotInventory inv : invs) {
    VolumeSnapshotLeaf leaf = map.computeIfAbsent(inv.getUuid(), k -> new VolumeSnapshotLeaf());
    leaf.inventory = inv;
    if (inv.getParentUuid() != null) {
        VolumeSnapshotLeaf parent = map.computeIfAbsent(inv.getParentUuid(), k -> new VolumeSnapshotLeaf());
        parent.children.add(leaf);
        leaf.parent = parent;
    } else {
        tree.root = leaf;
    }
}

// 步骤 4：计算 aliveChain
if (tree.current) {
    VolumeSnapshotLeaf leaf = tree.getSnapshotLeaf(volumeInv.getUuid());
    tree.aliveChain = leaf != null ? leaf.getAncestors() : new ArrayList<>();
}
```
