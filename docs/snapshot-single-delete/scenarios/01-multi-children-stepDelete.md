# 15. 多子节点 stepDelete 处理逻辑（现状梳理）

> 本文档属于"当前实现梳理"，与 `04-scope-and-stepDelete.md` 互补：04 讲 scope/递归框架，本文聚焦 **currentRoot 有多个直接子节点时** 的具体决策与执行顺序。
> 源码：`storage/src/main/java/org/zstack/storage/snapshot/VolumeSnapshotTreeBase.java` `deleteSingleFlows()` / `stepDelete()`，行号 828–919（5.5.6 基线）。

---

## 15.1 调用入口

```
deleteSingleFlows()  (行 828)
   └─ flow "delete-single-volume-snapshot"
        ├─ 类型分流：StorageSnapshot / Memory → 直接 deleteVolumeSnapshotAndSyncVolumeSize
        ├─ vmState 校验（Running / Paused / Destroyed / Stopped / Destroying）
        └─ stepDelete()       ◄── 递归核心
```

`currentRoot` = 待删快照（不是它的兄弟）。后续讨论的 children 都是 **currentRoot 的直接子节点**。

---

## 15.2 stepDelete 单轮决策表（行 875–919）

每轮重读 DB 重建 `VolumeTree`，处理一个子节点后递归再调一次 stepDelete。

```
1. vos = Q(VolumeSnapshotVO).eq(treeUuid).list()
2. volumeTree = VolumeTree.fromVOs(vos, current, volumeInv)
3. children = volumeTree.getSnapshotLeaf(currentRoot.uuid).getChildren()

┌──────────────────────────┬────────────────────────────────────────────────────┐
│ children.size()           │ 行为                                                │
├──────────────────────────┼────────────────────────────────────────────────────┤
│ 0                         │ deleteVolumeSnapshotAndSyncVolumeSize（终态，删自身）  │
│ 1                         │ resolveDirection → commit 或 pull                  │
│ ≥ 2 (多子节点)              │ 选一个非 alive chain 上的 child → 离线 pull           │
└──────────────────────────┴────────────────────────────────────────────────────┘
```

### 多子节点选择算法（行 912–918）

```java
onlineChild = children.stream()
    .filter(c -> volumeTree.isOnline(current, currentRoot.uuid, c.uuid, vmState))
    .findFirst().orElse(null);

child = children.get(0);
if (onlineChild != null && Objects.equals(child.uuid, onlineChild.uuid)) {
    child = children.get(1);   // 避开 alive 子节点，挑下一个
}
boolean online = volumeTree.isOnline(current, currentRoot.uuid, child.uuid, vmState);
pull(child, volumeTree, online, comp);
```

要点：
- **永远先离线 pull 非 alive 的子节点**：alive chain 上的子节点最后一轮才处理（届时 children.size() 已收敛到 1）
- **只挑 children.get(0) 或 children.get(1)**：每轮处理一个，下一轮再选
- direction 强制为 pull：多子节点路径不调 resolveDirection，直接 `pull(...)`

---

## 15.3 pull 对一个子节点的物理 + DB 影响

设 currentRoot=X，要 pull 的子节点=Y：

| 层 | 变化 |
|---|---|
| 物理 qcow2 | `qcow2_commit(X → Y)`：X 的差量被合进 Y；Y 的 backing 从 X 翻到 X.parent |
| DB（updateDatabaseAfterPull）| Y.parentUuid = X.parentUuid；Y.distance--；其它 X 的子节点不动 |

效果：Y 不再依赖 X，从 currentRoot 的 children 列表中"脱离"；下一轮 stepDelete 重读 DB 时 Y 已不在 children 里。

---

## 15.4 完整执行轨迹示例

快照树：

```
  X (待删, currentRoot)
  └─ A
       ├─ B
       ├─ C
       └─ D ── vol   ← alive chain 末端
```

待删的是 **A**（currentRoot=A，children=[B, C, D]）。

| 轮 | children 重读 | onlineChild | 选中 child | 决策 | 行为 |
|---|---|---|---|---|---|
| 1 | [B, C, D] | D | B（首个非 alive）| 离线 pull | qcow2_commit(A→B), B.parentUuid=X |
| 2 | [C, D] | D | C | 离线 pull | qcow2_commit(A→C), C.parentUuid=X |
| 3 | [D] (size=1) | D | D | resolveDirection → Commit (latest+online) | 在线 blockCommit(A→D) + pivot |
| 4 | [] | — | — | terminal | deleteVolumeSnapshotAndSyncVolumeSize(A) |

最终：
- 物理：A 的 qcow2 文件被删；B/C 的 backing 直接指 X；D 通过 in-place commit 把 A 的数据吃掉（D.installPath 不变，但内容含 A）
- DB：A 的 VO 删除；B/C/D 的 parentUuid 全部跳过 A 直接指向 X

---

## 15.5 关键不变量与代码对应

| 不变量 | 代码位置 | 作用 |
|---|---|---|
| 每轮重读 DB | 行 876 `Q.New(VolumeSnapshotVO).eq(treeUuid).list()` | 上一轮 DB 翻转后下一轮决策基于最新状态，避免基于陈旧子节点列表做错决定 |
| 多子节点先 pull 非 alive | 行 913–915 `if (child == onlineChild) child = children.get(1)` | 保证 alive chain 上的活跃文件不被离线操作打断 |
| alive 子节点最后处理 | 多轮 pull 后 children.size() 收敛到 1，进入 commit 分支 | 在线 commit 走 libvirt blockCommit，与 alive VM 协同 |
| 同步递归（comp.success → stepDelete）| 行 891–895 | 全程在 chainSubmit 锁内，无并发；reconciler 可同步介入每轮 |
| 终态收敛 | children.isEmpty() → deleteVolumeSnapshotAndSyncVolumeSize | 数据已全部搬走，自身物理 + DB 真删 |

---

## 15.6 资料 children 顺序的依赖

代码使用 `children.get(0)` / `children.get(1)`，依赖 `VolumeTree.fromVOs` 返回 children 的顺序。该顺序由 DB 查询顺序决定（无显式 ORDER BY），实践上稳定但不应依赖语义意义。`onlineChild` 通过 `isOnline` 判定，与 children 顺序无关——这保证了"避开 alive"逻辑不会因 DB 顺序波动而失效。

---

## 15.7 与 commit 单子节点路径的差别

| 场景 | direction | 物理操作 | DB 翻转 |
|---|---|---|---|
| 单子节点 + commit | child(src) → currentRoot(dst) | qcow2_commit(src→dst) + 兄弟 rebase 到 dst | dst 移入 src 位置（详见 05-commit-db-swap）|
| 单子节点 + pull | currentRoot(src) → child(dst) | qcow2_commit(src→dst) | dst.parentUuid = src.parentUuid（详见 06-pull-db-rewrite）|
| 多子节点 | 强制 pull | 选一个非 alive child 做 pull | 该 child.parentUuid = currentRoot.parentUuid，其余 children 不动 |

多子节点本质上是**对 N 个 child 顺序应用 pull**，把多分叉树逐步收敛为单分支，最后回归到"单子节点 commit"路径完成 alive 合并。
