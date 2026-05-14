# 场景 02：local + 在线 VM + 删除中间节点（快照2，3 个子节点其中 1 个 alive）

> 当前代码逻辑梳理（5.5.6 基线），不含加固设计。
> 源码：`storage/src/main/java/org/zstack/storage/snapshot/VolumeSnapshotTreeBase.java`、`VolumeTree.java`、`kvmagent/.../vm_plugin.py`、`kvmagent/.../localstorage_plugin.py`。

---

## 前提

- 主存储类型：**LocalStorage**
- VM 状态：**Running**（active commit 路径）
- 待删快照：**快照2**（中间节点，3 个直接子节点，其中 1 个在 alive chain 上）

## 快照树

```
  快照1
   └─ 快照2          ◄── 待删 currentRoot
        ├─ 快照3
        ├─ 快照4
        └─ 快照5 ── vol     ← alive chain（VM 当前盘）
```

## 物理 backing chain（alive 这条线）

```
1.qcow2  ← 2.qcow2  ← 5.qcow2  ← vol
```

兄弟分支：

```
2.qcow2  ← 3.qcow2
2.qcow2  ← 4.qcow2
```

---

## 总轮次（4 轮 stepDelete）

| 轮 | currentRoot=2 的 children | 选中 | online? | direction | 物理操作 | DB 关键变更 |
|---|---|---|---|---|---|---|
| 1 | [3, 4, 5] | 3 | false | **强制 pull** | `offline_merge_snapshot` → `qcow2_rebase(1.qcow2, 3.qcow2)`（差量进 3） | 3.parentUuid=1, 3.distance-- |
| 2 | [4, 5] | 4 | false | **强制 pull** | `offline_merge_snapshot` → `qcow2_rebase(1.qcow2, 4.qcow2)`（差量进 4） | 4.parentUuid=1, 4.distance-- |
| 3 | [5] | 5 | **true** | resolveDirection → Commit | libvirt blockCommit(top=5, base=2) + pivot | DB 互换 path |
| 4 | [] | — | — | terminal | 删 VO_2 + 物理（5.qcow2 文件已 libvirt 删）| VO_2 删除 |

---

## 轮 1：离线 pull 快照3

代码 `VolumeSnapshotTreeBase.java:912-918`，进入 `children.size() ≥ 2` 分支：

```java
aliveChild = 5             // 唯一 vol 链上（isOnAliveChain 命中；修复后术语）
child = children.get(0) = 3 // 3 != aliveChild → 不替换
online = isOnline(2, 3, Running) = false
pull(3, ..., online=false)
```

**消息**：`PullVolumeSnapshotOnPrimaryStorageMsg`（local 走主存储路径，不经 hypervisor）

**后端 → agent 映射**：`LocalStorageKvmBackend.handle(PullVolumeSnapshotOnPrimaryStorageMsg)` 行 3845-3865 → `OfflineMergeSnapshotCmd{srcPath=1.qcow2, destPath=3.qcow2, fullRebase=false}` → **`OFFLINE_MERGE_PATH = "/localstorage/snapshot/offlinemerge"`**

**agent 物理动作**（`localstorage.py` `offline_merge_snapshot` 行 834-856）：

```
linux.qcow2_rebase(srcPath=1.qcow2, destPath=3.qcow2)
# qemu-img rebase 默认（非 -u）：
#   把 3.qcow2 旧 backing(2.qcow2) 与新 backing(1.qcow2) 之间的差异
#   写入 3.qcow2 数据区，然后改写头部 backing 字段为 1.qcow2
```

**DB 翻转**（`updateDatabaseAfterPull`，详见 `../06-pull-db-rewrite.md`）：

```
VO_3.parentUuid  = 1
VO_3.distance   -= 1
VO_3.installPath 不变
其它 VO 不动
```

VM 状态：完全无感（3 不在 alive chain）。

---

## 轮 2：离线 pull 快照4

与轮 1 完全对称。

**结果**：

```
VO_4.parentUuid = 1
VO_4.distance  -= 1
4.qcow2 物理 backing → 1.qcow2
```

此时 currentRoot=2 在 DB 中的 children 只剩 [5]。

---

## 轮 3：在线 commit 快照5 → 快照2（最复杂的一轮）

```java
direction = volumeTree.resolveDirection(2, 5, msg.direction, currentRoot.isLatest, Running)
   → Commit  (5 在 alive chain + Running)
online = isOnline(2, 5, Running) = true
commit(5, volumeTree, online=true, comp)
```

### 3.1 控制面 flow（`commit()` 行 921-1094）

```
flow chain:
  1. (条件) SyncVolumeSizeOnPrimaryStorage   仅当 srcSnapshot.uuid == volume.uuid；本例 src=5 ≠ vol → 跳过
  2. AllocatePrimaryStorageSpaceMsg          预占 size
  3. CommitVolumeSnapshotOnHypervisorMsg     online → 走 hypervisor
       ├─ srcSnapshot = 5 inventory
       ├─ dstSnapshot = 2 inventory
       └─ srcChildrenInstallPathInDb = [vol.installPath]   # 5 的子节点是 vol leaf
  4. updateDatabaseAfterCommit               DB 互换（SQLBatch 单事务）
```

### 3.2 数据面（`vm_plugin.py do_block_commit`）

```
top  = src = 5.qcow2（VM 当前活跃盘）
base = dst = 2.qcow2

步骤：
  1. virDomainBlockCommit(disk, base=2.qcow2, top=5.qcow2,
                          flags=VIR_DOMAIN_BLOCK_COMMIT_ACTIVE | SHALLOW)
     → libvirt 把 5 中尚未在 2 的数据 flush 到 2.qcow2
     → 进入 READY 态（active commit 特征）
  2. _wait_for_block_job → READY
  3. virDomainBlockJobAbort(disk, flags=VIR_DOMAIN_BLOCK_JOB_ABORT_PIVOT)
     → VM disk source 从 5.qcow2 → 切到 2.qcow2
  4. for child in srcChildrenInstallPathInDb=[vol.installPath]:
        if qcow2_get_backing_file(child) != base:
            qcow2_rebase_no_check(base, child)
     → 本例 vol 即 5.qcow2 自身，pivot 后 VM 已切到 2.qcow2，通常 noop
```

完成后物理：

```
2.qcow2 内容：原 5 的全部数据已合并进来
5.qcow2 物理文件：libvirt 在 pivot 时删除（VIR_DOMAIN_BLOCK_COMMIT_DELETE）
VM 活跃盘 source：2.qcow2
```

### 3.3 DB 翻转（参考 `../05-commit-db-swap.md` §5.3）

```
src=5, dst=2

互换前：
  VO_5.installPath = 5.qcow2   parentUuid = 2  distance = N
  VO_2.installPath = 2.qcow2   parentUuid = 1  distance = N-1

互换后（**实测修订** —— 见场景 05 §6）：
  VO_2 **整条 DB 记录被删除**（不是"互换后保留至轮 4"）
  VO_5.installPath = 2.qcow2          ← 接管旧 dst 文件（含合并数据）
  VO_5.parentUuid  = 1                ← 跨过 2
  VO_5.distance   -= 1
  VO_5.treeUuid    = 不变（dst=2 不是树根；若 dst 是根则迁到新 tree）

GroupRef 同步：被删者(2) 的 GroupRef 一并删除（VO_2 被 DELETE）

distance 递减：src=5 的所有后代 distance -= 1（本例无更深 snapshot，只有 vol leaf）
```

### 3.4 vol.installPath 的同步

**实测结论**（场景 05 §5.2 / §6）：commit 路径下 `vol.installPath` 字段在 DB 中**不变**。vol 之前挂 5.qcow2（VO_5 旧 installPath），commit + pivot 后物理上 vol 实际挂 2.qcow2（含合并数据的文件），但这个切换通过两个步骤实现：
- **物理层**：libvirt blockCommit pivot 后 vm domain 已经在用 2.qcow2 作为 backing；同步路径里 sibling 的 `qcow2_rebase_no_check(base=2.qcow2, child)` 把 vol 的 backing 链改写到 2.qcow2
- **DB 层**：vol VO 的 installPath 字段保留原值，但 VO_5 的 installPath 字段被改为 2.qcow2（VO_5 接管 dst 物理文件），vol → VO_5 的 backing 关系仍然指向同一物理文件

因此"vol 跟着合并数据走"不是靠 `UPDATE VolumeVO SET installPath=...`，而是靠物理 backing 链 + VO 文件接管的组合。这是 alive 末端 commit 的关键行为（与中间节点 commit 不同：中间节点 commit 没有 vol 需要跟踪）。

### 3.5 互换后链状态

```
DB 视角：
  vol.installPath  = 5.qcow2（**不变**，但物理 backing 已切到 2.qcow2）
  VO_5.installPath = 2.qcow2   parentUuid = 1   ← 接管原 dst 文件
  VO_2 已删除
  VO_3.installPath = 3.qcow2   parentUuid = 1
  VO_4.installPath = 4.qcow2   parentUuid = 1

物理 backing chain：
  vol → 2.qcow2（含合并数据）→ 1.qcow2
  3.qcow2 → 1.qcow2
  4.qcow2 → 1.qcow2
  5.qcow2：libvirt 已删
```

---

## 轮 4：物理清扫 5.qcow2

> ⚠ **实测修订**：VO_2 在轮 3 的 SQLBatch 中已被直接删除（不是"互换 path 保留至轮 4"）。轮 4 的 `children=[]` 是因为 VO_5.parentUuid 已跨过 2 指向 1，VO_2 在树中已不可见。

```java
children = []
deleteVolumeSnapshotAndSyncVolumeSize(comp)
```

**消息**：`DeleteVolumeSnapshotOnPrimaryStorageMsg`

**agent 物理动作**（实测）：删 **5.qcow2 物理文件**（即原 VO_5 的旧 installPath；libvirt 在 pivot 时已逻辑解除引用，此处 agent 真正删盘）。注意：传给 agent 的 path 来自 stepDelete 调用栈记住的 currentRoot 物理路径，而非已删除的 VO_2 VO。

**DB**：syncVolumeSize 更新 vol 的 size。VO_2 已在轮 3 删除，本轮 DB 无 VO 删除。

---

## 终态

```
快照树（DB）：
  快照1
   ├─ 快照3              installPath=3.qcow2  backing=1.qcow2
   ├─ 快照4              installPath=4.qcow2  backing=1.qcow2
   └─ 快照5 ── vol      VO_5.installPath=2.qcow2（接管旧 dst 文件）
                         vol.installPath=5.qcow2（DB 字段不变，但物理 backing 已切到 2.qcow2）

VO_2 已删除（轮 3 SQLBatch 中 DELETE）

物理：
  1.qcow2 ← 2.qcow2 ← vol（VM 活跃，物理上 vol.backing = 2.qcow2，2.qcow2 含原 5+2 合并数据）
  1.qcow2 ← 3.qcow2
  1.qcow2 ← 4.qcow2
  5.qcow2 文件已删（轮 4）
```

> "VO_5 接管 2.qcow2 / VO_2 直接删 / vol.installPath 不变"这三条对应实测验证记录详见场景 05 §5、§6。

---

## 全程关键脆弱点（仅梳理，不含加固）

| 轮 | 失败类型 | 当前后果 |
|---|---|---|
| 1 / 2 | `qcow2_rebase` 失败（agent crash 或 IO 错） | 3 / 4 backing 可能部分改写但未完成；DB 翻转尚未发生，幂等可重试 |
| 1 / 2 | `qcow2_rebase` 成功 + DB 翻转 SQL 失败 | 物理 child.backing=1，DB child.parentUuid=2 → 不一致 |
| 3 | blockCommit 卡住 / pivot 前 agent 死 | VM 可能仍指 5.qcow2，DB 未翻转 |
| 3 | blockCommit 成功但 reply 丢失 / SQLBatch 失败 | 物理已切到 2.qcow2，DB 仍旧态（VO_2 未删 / VO_5.installPath 仍 5.qcow2），重启会按 DB 读 5.qcow2 而 libvirt 已删它 |
| 3 | DB 翻转成功，但 vol 物理 backing 改写失败 | vol.qcow2 头部 backing 仍指 5.qcow2（已删）→ VM 重启失败 |
| 4 | 删 5.qcow2 失败 | 孤儿文件残留 |

---

## 与其它场景对照

| 场景 | 轮数 | 核心特征 |
|---|---|---|
| `01-multi-children-stepDelete.md` | 4 | 通用多子节点骨架；以 X→A→{B,C,D} 抽象演示 |
| **本场景 02**（local + Running + 删快照2） | 4 | 落到具体存储 + 在线 + alive 子节点是 vol 直接父；最后一轮在线 commit + vol.installPath 同步是关键差异 |
