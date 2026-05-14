# 04 — scope 分支与 stepDelete 递归

## 4.1 scope 分支点

**文件**：`VolumeSnapshotTreeBase.java:473`

```java
if (Objects.equals(msg.getScope(), DeleteVolumeSnapshotScope.Chain.toString())) {
    deleteChainFlows();      // 旧行为：删当前 + 所有后代
} else {
    deleteSingleFlows();     // single/auto：只删当前节点
}
```

注意：`scope=auto` 也走 `deleteSingleFlows()` 分支；只有显式 `chain` 走级联删除。

## 4.2 stepDelete 完整代码

**文件**：`VolumeSnapshotTreeBase.java:875-918`

```java
private void stepDelete(Completion completion) {
    // 1) 从 DB 拉取整棵树最新状态
    List<VolumeSnapshotVO> vos = Q.New(VolumeSnapshotVO.class)
            .eq(VolumeSnapshotVO_.treeUuid, currentRoot.getTreeUuid()).list();
    boolean current = Q.New(VolumeSnapshotTreeVO.class)
            .eq(VolumeSnapshotTreeVO_.uuid, currentRoot.getTreeUuid())
            .select(VolumeSnapshotTreeVO_.current).findValue();

    // 2) 重建内存树
    VolumeTree volumeTree = VolumeTree.fromVOs(vos, current, VolumeInventory.valueOf(volume));
    List<VolumeSnapshotLeaf> children =
            volumeTree.getSnapshotLeaf(currentRoot.getUuid()).getChildren();

    // 3) 终止条件：无子节点
    if (children.isEmpty()) {
        deleteVolumeSnapshotAndSyncVolumeSize(completion);
        return;
    }

    // 4) 递归 completion
    Completion comp = new Completion(completion) {
        @Override public void success() { stepDelete(completion); }
        @Override public void fail(ErrorCode e) { completion.fail(e); }
    };

    // 5) 找 online 子节点（vm running/paused 且在 aliveChain）
    VolumeSnapshotLeaf onlineChild = children.stream()
            .filter(c -> volumeTree.isOnline(current, currentRoot.getUuid(), c.getUuid(), vmState))
            .findFirst().orElse(null);

    VolumeSnapshotLeaf child = children.get(0);

    if (children.size() == 1) {
        DeleteVolumeSnapshotDirection direction = volumeTree.resolveDirection(
                currentRoot.getUuid(), child.getUuid(),
                msg.getDirection(), currentRoot.isLatest(), vmState);
        boolean online = volumeTree.isOnline(current, currentRoot.getUuid(), child.getUuid(), vmState);
        if (direction == Commit) commit(child, volumeTree, online, comp);
        else                     pull(child, volumeTree, online, comp);
    } else {
        // 多子节点（分叉链）
        if (onlineChild != null && child.getUuid().equals(onlineChild.getUuid())) {
            child = children.get(1);   // 优先处理非 online 子节点
        }
        boolean online = volumeTree.isOnline(current, currentRoot.getUuid(), child.getUuid(), vmState);
        pull(child, volumeTree, online, comp);   // 多子节点统一 pull
    }
}
```

## 4.3 递归特性

| 维度 | 说明 |
|---|---|
| 终止条件 | `children.isEmpty()` |
| 每次递归 | 处理一个子节点；commit/pull 后子节点数 -1 |
| 最坏深度 | 子节点总数（**不是链深度**） |
| 多子节点策略 | 强制 pull；优先非 online 子节点 |
| 失败处理 | `comp.fail()` 直接上抛，**已完成的中间步骤不回滚**，依赖存储幂等 |

## 4.4 多子节点优先非 online 原因

online 子节点的 backing file 正在被 qemu 持有写 I/O，修改它有风险；
先处理非 online 子节点，把它们逐个 pull 掉；最后 online 子节点剩一个，落入"单子节点"分支正常处理。

## 4.5 特殊短路

`VolumeSnapshotTreeBase.java:836`：
```java
if (VolumeSnapshotConstant.STORAGE_SNAPSHOT_TYPE.toString().equals(currentRoot.getType())
        || Objects.equals(currentRoot.getVolumeType(), VolumeType.Memory.toString())) {
    deleteVolumeSnapshotAndSyncVolumeSize(completion);
    return;
}
```

CDP / 存储快照 / 内存快照绕过 commit/pull，直接调用存储删除。

## 4.6 VmState 限制

`:854` 仅允许 `Running / Paused / Destroyed / Stopped / Destroying`，其它（如 Migrating / Unknown）直接失败。
