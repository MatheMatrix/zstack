# 场景 03：local + 关机 VM + 删除中间节点（快照2，3 个子节点其中 1 个 alive）

> 当前代码逻辑梳理（5.5.6 基线），不含加固设计。
> 与 `02-local-running-delete-mid-with-3-children.md` 对照阅读：树结构相同，唯一差别是 VM 状态从 Running 变为 Stopped。
> 源码：
> - `VolumeSnapshotTreeBase.java` 行 828-919（stepDelete）/ 1097-1290（pull）
> - `VolumeTree.java` 行 364-392（resolveDirection / isOnline）
> - `LocalStorageKvmBackend.java` 行 3845-3865（PullVolumeSnapshotOnPrimaryStorageMsg → OFFLINE_MERGE_PATH）
> - `localstorage.py` 行 834-856（`offline_merge_snapshot`）

> ⚠ **本场景按 `initial.direction ∈ {Auto, Pull}` 的口径推演**（即最后一轮也走离线 pull）。
> 如果 API 入参 `direction=Commit`（或前端不传 → resolveDirection 默认返回 Commit），最后一轮的行为完全不同：会走 `offline_commit_snapshot`（数据 5→2，DB 互换 path，VO_2 直接 DELETE），**请参考实测记录 `05-local-stopped-direction-commit-actual.md`**。
> 决策矩阵见 `04-deleteSingleFlows-online-offline-decision.md` §"与场景 02 / 03 的对应"。
>
> ⚠ **Bug 0 修复后**（参考 `../bugs.md`）：`direction=Auto` 在 Stopped 下不再退化为 Pull —— `shouldUseCommitStrategy` 解耦 vmState 后，Auto + 待删/child 都在 vol 链上时返回 **Commit**，行为等价于场景 05。本场景 03 现在仅适用于 `direction=Pull` 显式入参，且需满足 `shouldCommit=false`（即不在 vol 链上）。

---

## 前提

- 主存储类型：**LocalStorage**
- VM 状态：**Stopped**（合法状态之一，校验在 `deleteSingleFlows()` 行 854-858）
- 待删快照：**快照2**

## 快照树（与场景 02 完全相同）

```
  快照1
   └─ 快照2          ◄── 待删 currentRoot
        ├─ 快照3
        ├─ 快照4
        └─ 快照5 ── vol     ← alive chain
```

## 物理 backing chain

```
1.qcow2 ← 2.qcow2 ← 5.qcow2 ← vol（VM 关机，文件无人持有）
2.qcow2 ← 3.qcow2
2.qcow2 ← 4.qcow2
```

---

## 关键差异：所有轮都走"离线 pull"

`VolumeTree.resolveDirection`（行 364-387）：

```java
boolean online = (vmState == Running || vmState == Paused) && alive(target) && alive(child);
boolean shouldUseCommitStrategy = current && !targetSnapshotIsLatest && online;
```

VM=Stopped → `online=false` → `shouldUseCommitStrategy=false` → Auto / null / Pull 全部解析为 **Pull**；`isOnline`（行 389-392）同样要求 Running/Paused。

后果：哪怕快照5 在 alive chain 上，VM 关机时它也走**离线 pull**，agent 不调 libvirt blockCommit，全部 qemu-img 离线操作。

**离线 pull 的真实控制面 / 数据面**：

| 层 | 实现 |
|---|---|
| Java 控制面 | `pull()` 行 1250-1268 → 构造 `PullVolumeSnapshotOnPrimaryStorageMsg`，参数 `srcSnapshotParentPath`（= 快照1.qcow2）、`srcSnapshot`（= 被删的 currentRoot=快照2）、`dstSnapshot`（= 选中 child） |
| 后端转发 | `LocalStorageKvmBackend.handle(PullVolumeSnapshotOnPrimaryStorageMsg)`（行 3845-3865）→ 构造 `OfflineMergeSnapshotCmd { srcPath = srcSnapshotParentPath, destPath = dst.installPath, fullRebase = (srcPath == null) }` → 走 **`OFFLINE_MERGE_PATH = "/localstorage/snapshot/offlinemerge"`** |
| Agent 数据面 | `offline_merge_snapshot`（`localstorage.py` 行 834-856）：核心一行 `linux.qcow2_rebase(cmd.srcPath, cmd.destPath)`（fullRebase 时改走 `qcow2.create_template` 扁平化） |

**关键澄清**：场景 03 的 pull 走的是 `offline_merge_snapshot`，**不是** `offline_commit_snapshot`（后者由 commit 离线分支 `CommitVolumeSnapshotOnPrimaryStorageMsg` 调用）。`qcow2_rebase(backing=快照1, file=child)` 的语义是把 child 的 backing 从原快照2 改成快照1，并**把"快照2 与 快照1 之间的差量数据"复制进 child 文件**（因为快照1 作为基线只读不可写，只能往 child 写）。

---

## 总轮次（4 轮 stepDelete，与场景 02 同结构但全离线）

| 轮 | currentRoot=2 的 children | 选中 | online? | direction | 物理操作 | DB 关键变更 |
|---|---|---|---|---|---|---|
| 1 | [3, 4, 5] | 3 | false | 强制 pull | `qcow2_rebase(1.qcow2, 3.qcow2)`（差量进 3.qcow2） | 3.parentUuid=1, distance-- |
| 2 | [4, 5] | 4 | false | 强制 pull | `qcow2_rebase(1.qcow2, 4.qcow2)`（差量进 4.qcow2） | 4.parentUuid=1, distance-- |
| 3 | [5] | 5 | **false** | resolveDirection → **Pull**（不再是 Commit） | `qcow2_rebase(1.qcow2, 5.qcow2)`（差量进 5.qcow2） | **5.parentUuid=1, distance--，不互换 path** |
| 4 | [] | — | — | terminal | 删 VO_2 + 物理 2.qcow2 | VO_2 删除 |

**全程数据落地**：每一轮把"快照2 相对于快照1 的增量"**复制进当前选中的 child**（3 / 4 / 5 各拿一份独立副本）。快照1.qcow2 内容**不变**，快照2.qcow2 内容也**不变**，直到轮 4 整文件删除。

---

## 轮 1 / 轮 2：与场景 02 完全相同

`stepDelete` 多子节点分支（行 912-918）不依赖 vmState，只依赖 children.size 与 onlineChild 选择算法。Stopped 时 `isOnline` 全部返回 false，`onlineChild = null`，`child = children.get(0)`，不需要"避开 alive 子节点"的替换。

```java
onlineChild = null            // VM Stopped，没有 alive child
child = children.get(0) = 3
// if 块未触发
online = isOnline(2, 3, Stopped) = false
pull(3, ..., online=false)
```

控制面 → 后端 → agent：

```
PullVolumeSnapshotOnPrimaryStorageMsg{
  srcSnapshotParentPath = "1.qcow2",
  srcSnapshot           = VO_2,
  dstSnapshot           = VO_3
}
  → LocalStorageKvmBackend.handle → OfflineMergeSnapshotCmd{srcPath=1.qcow2, destPath=3.qcow2, fullRebase=false}
  → offline_merge_snapshot:
       linux.qcow2_rebase(srcPath=1.qcow2, destPath=3.qcow2)
       # 物理：3.qcow2 backing 改写为 1.qcow2，差量数据合并入 3.qcow2
DB:  VO_3.parentUuid=1, distance--
```

轮 2 同理对快照4。

---

## 轮 3：离线 pull 快照5（与场景 02 的根本差别）

`children.size() == 1` 分支（行 903-911）：

```java
direction = volumeTree.resolveDirection(2, 5, msg.direction, currentRoot.isLatest, Stopped)
   → online=false → shouldUseCommitStrategy=false → 解析为 Pull
online = isOnline(2, 5, Stopped) = false
pull(5, volumeTree, online=false, comp)   // 离线 pull，不进 commit 分支
```

**关键差异**：场景 02 在轮 3 走在线 commit（libvirt blockCommit + pivot + DB 互换 path）；场景 03 走离线 pull，**不互换 path**，DB 修改路径完全不同。

### 3.1 控制面 flow（`pull()` 行 1097-1290）

```
flow chain:
  1. get-snapshot-backing-chain                获取 srcSnapshotParentPath（= 1.qcow2）
  2. allocate-primary-storage-capacity         预占 size
  3. (条件) get-volume-current-size            仅 dst.uuid == volume.uuid 时；本例 dst=5 ≠ vol → 跳过
  4. pull-volume-snapshot-on-primary-storage   online=false → PullVolumeSnapshotOnPrimaryStorageMsg
                                                online=true 才走 PullVolumeSnapshotOnHypervisorMsg
  5. updateDatabaseAfterPull
```

`PullVolumeSnapshotOnHypervisorMsg` 在本场景**完全不会被构造**，因为 `online=false`。所以 hypervisor 端 vm_plugin 的 do_block_commit 路径在场景 03 整个删除过程中**一次都不调用**。

### 3.2 数据面（`offline_merge_snapshot`）

```
src=快照2, dst=快照5
OfflineMergeSnapshotCmd{srcPath=1.qcow2, destPath=5.qcow2, fullRebase=false}

if linux.qcow2_get_backing_file(destPath=5.qcow2) == srcPath=1.qcow2:
    return（已经挂在 1.qcow2，幂等 noop）

if not cmd.fullRebase:
    linux.qcow2_rebase(cmd.srcPath=1.qcow2, cmd.destPath=5.qcow2)
    # qemu-img rebase 默认（非 -u）：
    #   把 5.qcow2 旧 backing(2.qcow2) 与新 backing(1.qcow2) 之间的差异
    #   写入 5.qcow2 的数据区，然后改写 5.qcow2 头部 backing 字段为 1.qcow2
else:
    # fullRebase 路径：扁平化（srcPath 为 null 时触发，本例不触发）
    qcow2.create_template(cmd.destPath, tmp) → mv tmp cmd.destPath
```

**与之对比的 `offline_commit_snapshot`（commit 离线分支用）**：

```
top=child, base=parent     # 由 LocalStorageKvmBackend.java:3827-3829 注入
linux.qcow2_commit(top=child, base=parent)             # 把 child flush 进 parent
for c in topChildrenInstallPathInDb:
    linux.qcow2_rebase_no_check(base=parent, c)        # child 的 children 重挂 parent
```

**两者方向相反**：
- `offline_merge_snapshot`（pull 用）：数据从 dropped 节点 **流入 child**（每个 child 独立拷一份），dropped 文件不动
- `offline_commit_snapshot`（commit 用）：数据从 src(child) **流入 dst(被删 currentRoot)**，DB 后续会互换 installPath

场景 03 全程使用前者。

### 3.3 DB 翻转（`updateDatabaseAfterPull`，对照 `../06-pull-db-rewrite.md`）

```
src=2, dst=5

更新前：
  VO_5.installPath = 5.qcow2   parentUuid = 2  distance = N
  VO_2.installPath = 2.qcow2   parentUuid = 1  distance = N-1

更新后：
  VO_5.parentUuid  = 1          ← 跨过 2
  VO_5.distance   -= 1
  VO_5.installPath 不变（仍 5.qcow2，物理上含合并入的 2-vs-1 差量）
  VO_5.size       = newInstallPathSize（agent 返回，因合并入差量略增）
  VO_2 不变（待轮 4 真删）
```

**与场景 02 的对照**：

| 维度 | 场景 02（Running，commit） | 场景 03（Stopped，pull） |
|---|---|---|
| Agent 路径 | `CommitVolumeSnapshotOnHypervisorMsg` → libvirt blockCommit | `PullVolumeSnapshotOnPrimaryStorageMsg` → `offline_merge_snapshot` → `qcow2_rebase` |
| 物理操作位置 | child 数据进入被删者 | 被删者数据复制进 child（每个 child 各一份） |
| dst.installPath | **互换**：VO_2 ↔ VO_5 path 互换 | **不变**：VO_5 path 仍 5.qcow2 |
| vol.installPath | 同步切到 2.qcow2（关键脆弱点） | 不变（仍指 5.qcow2，VM 关机也不影响）|
| treeUuid 迁移 | dst=2 不是根 → 不迁移；若是根则新建 newTree | pull 路径不涉及 treeUuid 迁移 |
| GroupRef installPath | 同步互换 | 不变 |
| libvirt 调用 | blockCommit + pivot + sibling rebase | 完全不调 |
| 被删快照文件何时清 | libvirt pivot 自动删（VIR_DOMAIN_BLOCK_COMMIT_DELETE，文件名是 5.qcow2） | 轮 4 显式删（文件名是 2.qcow2） |

### 3.4 翻转后链状态

```
DB 视角：
  vol.installPath  = 5.qcow2（不变，VM 关机重启时按此 backing chain 启动）
  VO_5.installPath = 5.qcow2  parentUuid = 1  ← 含合并入的 2-vs-1 差量
  VO_2.installPath = 2.qcow2  parentUuid = 1  ← 待删
  VO_3.installPath = 3.qcow2  parentUuid = 1
  VO_4.installPath = 4.qcow2  parentUuid = 1

物理 backing chain：
  vol → 5.qcow2 → 1.qcow2
  3.qcow2 → 1.qcow2
  4.qcow2 → 1.qcow2
  2.qcow2：仍存在但已无人引用（待轮 4 删）
```

---

## 轮 4：删 VO_2 自身

```java
children = []   // VO_5.parentUuid 已跨过 2 指向 1
deleteVolumeSnapshotAndSyncVolumeSize(comp)
```

**消息**：`DeleteVolumeSnapshotOnPrimaryStorageMsg`

**agent 物理动作**：删 VO_2.installPath = **2.qcow2**（场景 02 删的是 5.qcow2，是因为互换后 VO_2 指向 5；本场景未互换，VO_2 仍指 2.qcow2）。

**DB**：VO_2 删除，syncVolumeSize 更新 vol 的 size。

---

## 终态

```
快照树：
  快照1
   ├─ 快照3              installPath=3.qcow2  backing=1.qcow2  含 (2-1) 差量
   ├─ 快照4              installPath=4.qcow2  backing=1.qcow2  含 (2-1) 差量
   └─ 快照5 ── vol      installPath=5.qcow2  backing=1.qcow2  含 (2-1) 差量

物理：
  1.qcow2 ← 5.qcow2 ← vol
  1.qcow2 ← 3.qcow2
  1.qcow2 ← 4.qcow2
  2.qcow2 已删
```

**注意"差量被复制 3 份"**：场景 03 由于走 pull，被删快照(2)与父(1)之间的差量数据会被分别复制到 3、4、5 三个文件中，磁盘占用相比场景 02 偏高（场景 02 只有一份合并文件）。这是 commit-vs-pull 的固有差异，与是否在线无关。

与场景 02 终态对比：

| 维度 | 场景 02 终态 | 场景 03 终态 |
|---|---|---|
| 含合并数据的物理文件 | 单个 2.qcow2（VO_5 占用，含 5+2 全合并） | 3.qcow2 / 4.qcow2 / 5.qcow2 各含一份 (2-1) 差量 |
| vol.installPath 指向 | 2.qcow2 | 5.qcow2 |
| 删除掉的物理文件 | 5.qcow2（libvirt 在 pivot 时删）+ 2.qcow2 实际名（互换后归 VO_2，轮 4 走 delete）| 2.qcow2 |
| 总磁盘占用 | 较低（差量只一份） | 较高（差量 N 份，N=child 数） |

**功能等价**：vol 拉起的 backing chain 长度都是 2 层（`vol → child → 1.qcow2`），用户视角"快照2 已删，3/4/5 仍在"完全一致。

---

## 全程关键脆弱点（仅梳理，不含加固）

| 轮 | 失败类型 | 当前后果 |
|---|---|---|
| 1 / 2 | qcow2_rebase 失败（agent crash 或 IO 错） | 该 child 的 backing 可能已部分改写但数据未完成；DB 翻转尚未发生 → 物理仍指 2 / DB 仍指 2，幂等可重试 |
| 1 / 2 | qcow2_rebase 成功 + DB 翻转 SQL 失败 | 物理 child.backing=1，DB child.parentUuid=2 → 不一致 |
| 3 | 同上（对快照5） | 同上 |
| 3 | DB 翻转 SQL 失败 | 物理 5.qcow2 已挂 1，DB 仍记 parentUuid=2 |
| 4 | 删 2.qcow2 失败 | 孤儿文件残留 |

注意：场景 03 没有 active commit pivot 的状态机问题，也没有 vol.installPath 必须同步切的脆弱点；最大风险只剩"qcow2_rebase 与 DB 翻转两步非原子"。

---

## 与场景 02 的核心结论

1. **agent 入口完全不同**：Stopped → `OFFLINE_MERGE_PATH` (`offline_merge_snapshot` → `qcow2_rebase`)；Running → `CommitVolumeSnapshotOnHypervisorMsg` (libvirt blockCommit) 或 `OFFLINE_COMMIT_PATH` (`offline_commit_snapshot` → `qcow2_commit`)
2. **物理数据落地的文件不同**：场景 02 落到 dst（被删者的 path，单一文件）；场景 03 落到每个 child（多份副本）
3. **DB 是否互换 path 不同**：commit 互换、pull 不互换；这直接决定加固设计 reconciler I4（installPath 不一致）的检测要在两条路径上分别考虑
4. **vol.installPath 同步要求不同**：场景 02 必须切（脆弱点），场景 03 不动（天然安全）
5. **失败模式不同**：场景 03 没有 active commit pivot 状态机问题，但 "qcow2_rebase + DB 翻转" 仍是两步非原子操作；且失败会以"3/4/5 中某些已 rebase、某些未 rebase"的部分推进态出现

---

## 附：用户直觉表述与代码事实的对应

用户口头描述："把快照2 的内容合并到快照1，删除快照2，快照 3/4/5 重新指定父节点为 1"。

按代码事实拆解：

| 用户语 | 代码事实 |
|---|---|
| "3/4/5 重新指定父节点为 1" | ✅ `qcow2_rebase(1.qcow2, child.qcow2)` 改写 child 头部 backing 字段；DB `VO_child.parentUuid=1` |
| "把快照2 的内容合并到快照1" | ⚠ 严格意义上 1.qcow2 不被写（只读基线）。等效效果：(快照2 - 快照1) 的差量数据被分别**复制进每个 child**，使每个 child 在新的 1.qcow2 backing 下行为等价于原先在 2.qcow2 backing 下 |
| "删除快照2" | ✅ 轮 4 真正物理删 2.qcow2 |
