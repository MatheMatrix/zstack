# 场景 05：local + 关机 VM + `direction=Commit` + 删 group 2（**实测**）

> 实测于 5.5.6 基线 ZSV 环境（管理节点 172.26.53.180）。
> 与 `02-...running-...md` / `03-...stopped-...md` 对照阅读：本文件是**实测真值**，前两个是源码推演。
> 实测时间：2026-05-13 16:54:56 ~ 16:54:58（总耗时 ~2s）。

---

## 1. 环境与入参

| 项 | 值 |
|---|---|
| VM uuid | `fa51c9637c024d94a556dd474a5cd74e` |
| VM 状态（操作时） | **Stopped** |
| Host | `69a7844559844d7193c42e78095911e2` |
| 主存储 | LocalStorage `a9222f7b445e4d2ebd1f1f958dec2f7c`（`/vms_ds`） |
| Root volume | `8dea4b2bb57b402e90beb510c8784507` |
| 快照树 | `08ab32b181644617bb4f8cd32804a6dd`（current=1） |
| API | `APIDeleteVolumeSnapshotGroupMsg` |
| API uuid | `e56623d94e294f9bbabd7a1a9eaf31f2` |
| Group uuid | `ee59701943554014a95d2badb0b2b98d`（snap-group "2"） |
| 入参 direction | **`Commit`** |
| 入参 scope | `single` |
| 结果 | `success=true`，操作 1 个 snapshot：`59897f45b2d841e98ec588da025dc841`（即"快照2"） |

## 2. 操作前树结构

### 2.1 快照 VO 表

| 显示名 | snapshot.uuid | parentUuid | distance | latest | installPath 文件名 |
|---|---|---|---|---|---|
| 1 | `aa7290b5…e70c` | NULL | 1 | 0 | `8dea4b2b…4507.qcow2` |
| 2 | `59897f45…c841` | `aa72…e70c` (=1) | 2 | 0 | `aa7290b5…e70c.qcow2` |
| 3 | `92e8b9bc…bc5c` | `59897…c841` (=2) | 3 | 0 | `59897f45…c841.qcow2` |
| 4 | `0baccfe6…d49c` | `59897…c841` (=2) | 3 | 0 | `596c7400…cb54.qcow2` |
| 5 | `be2680f7…5452` | `59897…c841` (=2) | 3 | **1** | `0cabc0f3…cd1a.qcow2` |
| (vol) | volume = `8dea4b2b…4507` | — | — | — | `be2680f7…5452.qcow2` |

> **命名错位提醒**：ZStack 实现"做快照"为"冻结当前 + 新建当前"，所以 snapshot.installPath 的物理**文件名**通常是它**父辈被冻结时的旧文件名**，与该 snapshot 自身的 uuid 不一致。下面用"X.qcow2"代指 VO_X 的物理文件，文件名用括号注明。

### 2.2 物理 backing chain（操作前）

```
imagecache/template/e4e3cca9…e5c.qcow2   (镜像基线，只读)
   ↑
1.qcow2  (文件: 8dea…4507.qcow2)
   ↑
2.qcow2  (文件: aa72…e70c.qcow2)
   ↑                ↑                 ↑
3.qcow2           4.qcow2          5.qcow2
(59897…c841)     (596c…cb54)      (0cab…cd1a)
                                     ↑
                                  vol.qcow2 (be26…5452.qcow2)
```

---

## 3. 实测 Agent HTTP POST 序列（6 次）

抓取自 `management-server.log`（`grep 'api=e56623d94e294f9bbabd7a1a9eaf31f2'`）。

| # | 时间 | path | 关键参数 | 含义 |
|---|---|---|---|---|
| 1 | 16:54:56.483 | `/localstorage/volume/getbackingchain` | installPath=2.qcow2 | 查"被删者(2)的 backing"→ 得 `srcSnapshotParentPath = 1.qcow2` |
| 2 | 16:54:56.642 | **`/localstorage/snapshot/offlinemerge`** | srcPath=**1.qcow2**<br>destPath=**4.qcow2** (`596c…cb54`) | **轮 1：离线 pull 4 → 1**（`qcow2_rebase(1, 4)`）|
| 3 | 16:54:56.949 | **`/localstorage/snapshot/offlinemerge`** | srcPath=**1.qcow2**<br>destPath=**3.qcow2** (`59897…c841`) | **轮 2：离线 pull 3 → 1**（`qcow2_rebase(1, 3)`）|
| 4 | 16:54:57.236 | **`/localstorage/snapshot/offlinecommit`** | top=**5.qcow2** (`0cab…cd1a`)<br>base=**2.qcow2** (`aa72…e70c`)<br>topChildrenInstallPathInDb=[vol] | **轮 3：离线 commit 5 → 2**（`qcow2_commit(5, 2)` + 给 5 的子节点 rebase 到 2）|
| 5 | 16:54:57.589 | `/localstorage/delete` | path=**5.qcow2** (`0cab…cd1a`) | **轮 4：删除"5 物理文件"**（commit 后被抽空的 top） |
| 6 | 16:54:57.898 | `/localstorage/volume/getsize` | installPath=vol | syncVolumeSize 收尾 |

> 全程**无** `/kvm/vm/*`（即未调 libvirt blockCommit）—— 关机路径不经 hypervisor。

---

## 4. 4 轮 stepDelete 对应

`VolumeSnapshotTreeBase.stepDelete()` 行 875-919 的执行展开：

### 轮 1：children = [3, 4, 5]，多子节点段（强制 pull，忽略 `direction=Commit`）

```
onlineChild = null                    (Stopped → isOnline 全 false)
child       = children.get(0) = 4    ★ 实测选 4，不是 3
online      = false
pull(4, tree, online=false, comp)
   → PullVolumeSnapshotOnPrimaryStorageMsg
   → LocalStorageKvmBackend.handle → OFFLINE_MERGE_PATH
   → OfflineMergeSnapshotCmd{srcPath=1.qcow2, destPath=4.qcow2, fullRebase=false}
   → agent: linux.qcow2_rebase(1.qcow2, 4.qcow2)
            # 4.qcow2 backing: 2.qcow2 → 1.qcow2，(2-1) 差量写入 4.qcow2
DB:  VO_4.parentUuid = 1, distance--
```

⚠️ **修订源码推演**：之前 `02 / 03` 文档假设 `children.get(0) = 3`（按 distance/createDate 升序），实测**选到 4**。说明 `VolumeTree.SnapshotLeaf.getChildren()` 返回顺序**不保证按 distance/createDate**，由底层 collection 实现决定。对最终行为无影响（3、4 均非 alive，谁先谁后等价），但加固设计若依赖"3 一定先于 4"应避免此假设。

### 轮 2：children = [3, 5]，多子节点段

```
child  = children.get(0) = 3
online = false
pull(3, tree, online=false, comp)
   → qcow2_rebase(1.qcow2, 3.qcow2)
DB:  VO_3.parentUuid = 1, distance--
```

### 轮 3：children = [5]，单子节点段（`direction=Commit` 终于生效）

```
direction = resolveDirection(2, 5, "Commit", isLatest=true, Stopped)
          → return fromString("Commit") = Commit
          (initial=Commit 非 Pull、非 null、非 Auto，原样返回；
           shouldUseCommitStrategy=false 仅影响 Pull 是否被拒，不拒 Commit)
online    = isOnline(current=true, 2, 5, Stopped) = false
            (Stopped → 第二个条件失败)
commit(5, tree, online=false, comp)
   → online=false 分支 → CommitVolumeSnapshotOnPrimaryStorageMsg
   → LocalStorageKvmBackend.handle → OFFLINE_COMMIT_PATH
   → OfflineCommitSnapshotCmd{
        top = srcSnapshot(=5).installPath  = 5.qcow2 (0cab…cd1a),
        base= dstSnapshot(=2).installPath  = 2.qcow2 (aa72…e70c),
        topChildrenInstallPathInDb = [vol.installPath = be26…5452.qcow2]
     }
   → agent (offline_commit_snapshot):
       if qcow2_get_backing_file(5.qcow2) != qcow2_get_backing_file(2.qcow2):
            # 5 backing=2, 2 backing=1，两者不同 → 进合并
            linux.qcow2_commit(top=5.qcow2, base=2.qcow2)
            # 把 5 的差量 flush 进 2；2 仍 backing=1
       for child in [vol = be26…5452.qcow2]:
            if qcow2_get_backing_file(vol) != 2.qcow2:
                # vol.backing 当前是 5.qcow2（0cab…cd1a）→ 不等
                linux.qcow2_rebase_no_check(base=2.qcow2, vol)
                # vol.backing: 5.qcow2 → 2.qcow2
```

物理结束态：
- `2.qcow2` (aa72…) 内含原 2 + 5 的合并数据，backing 仍是 1.qcow2
- `5.qcow2` (0cab…) 已被抽空（数据已合并入 2），但**文件还在**
- `vol.qcow2` (be26…) backing 改写为 `2.qcow2`

### 轮 3 DB 互换（SQLBatch 单事务，与场景 02 同结构）

```
src=5 (be26…5452), dst=2 (59897…c841)

互换前：
  VO_5.installPath = 0cab…cd1a.qcow2   parentUuid = 2  distance = 3
  VO_2.installPath = aa72…e70c.qcow2   parentUuid = 1  distance = 2
  vol.installPath  = be26…5452.qcow2

互换后：
  VO_2 整条 DB 记录删除（commit 路径"dst 即被删者"，DB 不再保留旧 path）
  VO_5.installPath = aa72…e70c.qcow2   parentUuid = 1  distance = 2  ← 接管 2 的物理文件
  vol.installPath  = be26…5452.qcow2 (不变，但物理 backing 已切到 aa72…e70c)
```

⚠️ **与之前推演的差异**：源码注释推断"VO_2.installPath 互换为 5 的旧文件名"，实测**直接删 VO_2**（连同 Group "2"），VO_2 没有保留任何 path 记录。互换发生在 VO_5 这一侧（VO_5 接管原 2 的文件），同时 VO_2 整条删除。

### 轮 4：children=[]，物理清扫

```
children = []   // VO_5.parentUuid 已跨过 2 指向 1
deleteVolumeSnapshotAndSyncVolumeSize(comp)
   → DeleteVolumeSnapshotOnPrimaryStorageMsg → /localstorage/delete
     path = 0cab…cd1a.qcow2   ★ 删的是 5 的原物理文件（已被抽空）
   → SyncVolumeSize → /localstorage/volume/getsize
     vol.actualSize 更新
```

---

## 5. 操作后实测状态

### 5.1 快照 VO 表（实测）

| name | uuid | parentUuid | distance | latest | installPath 文件名 |
|---|---|---|---|---|---|
| 1 | aa72…e70c | NULL | 1 | 0 | `8dea…4507.qcow2`（不变）|
| 3 | 92e8…bc5c | **aa72…e70c (=1)** | **2** ↓ | 0 | `59897…c841.qcow2`（不变）|
| 4 | 0bac…d49c | **aa72…e70c (=1)** | **2** ↓ | 0 | `596c…cb54.qcow2`（不变）|
| **5** | be26…5452 | **aa72…e70c (=1)** | **2** ↓ | **1** | **`aa72…e70c.qcow2`** ⬅ **变了** |

VO_2 消失。VolumeSnapshotGroupVO "2" 同步消失。

### 5.2 vol.installPath（实测）

```
vol.installPath = /vms_ds/.../snapshots/be2680f7…5452.qcow2
```

**未变**（仍是 vol 自己的 uuid 文件）。物理 backing 由原 `0cab…cd1a` 切到 `aa72…e70c`。

### 5.3 物理 backing chain（实测 `qemu-img info`）

```
imagecache/template/e4e3cca9…e5c.qcow2
   ↑
8dea…4507.qcow2                            [= VO_1 物理文件，未变]
   ↑               ↑                ↑
3.qcow2           4.qcow2         aa72…e70c.qcow2   [= 新 VO_5 物理文件，原 2.qcow2，含 5+2 合并]
(59897…c841)     (596c…cb54)        ↑
                                  be26…5452.qcow2   [vol，未变]
```

### 5.4 物理 ls（`/vms_ds/rootVolumes/.../snapshots/`）

| 文件名 | size | 角色 |
|---|---|---|
| `8dea…4507.qcow2` | 18 MiB | VO_1（基础） |
| `aa72…e70c.qcow2` | 6 MiB | **新 VO_5（含合并），原 VO_2 文件被接管** |
| `59897…c841.qcow2` | 6 MiB | VO_3 |
| `596c…cb54.qcow2` | 6 MiB | VO_4 |
| `be26…5452.qcow2` | 18 MiB | vol（当前可写层） |
| ~~`0cabc0f3…cd1a.qcow2`~~ | (已删) | 原 VO_5 物理文件，被轮 4 清除 |
| `92e8…bc5c.qcow2` | 18 MiB | （操作前的 vol 文件？需另查，不影响本场景）|

---

## 6. 与源码推演（场景 03 - Commit 分支）的差异点回顾

| 检查点 | 源码推演 | 实测 | 一致 |
|---|---|---|---|
| 多子节点段强制 pull（忽略 direction） | ✓ | ✓ POST `offlinemerge` 而非 `offlinecommit` | ✅ |
| 多子节点段 `child = children.get(0)` 是 distance/createDate 最小者 | 推测"3" | **实测"4"** | ⚠ 顺序假设错 |
| 单子节点段 `direction=Commit` 显式传入 → resolveDirection 原样返回 Commit | ✓ | ✓ | ✅ |
| `online = false`（Stopped）→ 走 `CommitVolumeSnapshotOnPrimaryStorageMsg` | ✓ | ✓ POST 落到 `/localstorage/snapshot/offlinecommit` | ✅ |
| top=child(5), base=被删者(2), topChildren=[vol] | ✓ | ✓ 完全吻合请求 body | ✅ |
| DB 互换 installPath（VO_5 接管 2 的物理文件） | ✓ | ✓ VO_5.installPath = `aa72…e70c.qcow2` | ✅ |
| VO_2 处理方式 | 推测"互换 path 后保留至轮 4" | **实测直接删除（无保留态）** | ⚠ 互换是单边的 |
| vol.installPath 同步 | 推测"切到 2.qcow2 文件名" | **实测不变**（仍 `be26…5452.qcow2`）；切换发生在物理 backing 层 `qcow2_rebase_no_check` | ⚠ DB 层 vol.installPath 是稳定的，"vol 跟随物理文件名"靠 backing 链而非 installPath 字段 |
| 轮 4 物理删 = 旧 5 物理文件（0cab…cd1a） | ✓ | ✓ | ✅ |

### 关键修订（已影响场景 02 / 03 文档）

1. **`children.get(0)` 顺序不保证按 distance**：场景 02 / 03 文档中"轮 1 删 3、轮 2 删 4"应改为"具体顺序由底层 collection 决定，3 和 4 中任一先后均合法"
2. **VO_2（dst 被删者）在 DB 中是"删除"而非"互换占位保留"**：场景 02 中关于"VO_2.installPath 互换为 5.qcow2 待轮 4 删"的描述需修正——`updateDatabaseAfterCommit` 直接将 VO_2 DELETE，VO_5 接收新 installPath；轮 4 删的是"VO_5 原文件"而非"VO_2 占位"
3. **`vol.installPath` 不参与互换**：commit 路径下 vol.installPath 字段稳定不变；vol 跟随到合并后文件，是通过**物理 backing 链改写**（`qcow2_rebase_no_check`）+ **VO_5 接管旧 dst 文件**的组合，DB 中 vol VO 的 installPath 字段不动

> 这三条修订需要回填到 `02-...running-...md` 和 `03-...stopped-...md`，作为后续修订项记入索引。

---

## 7. 关键脆弱点（基于实测路径）

| 阶段 | 失败 | 后果 |
|---|---|---|
| 轮 1/2 `offlinemerge` | `qcow2_rebase` 失败 / DB 翻转失败 | 某 child 物理 backing 已切但 DB parentUuid 未翻；或反之 |
| 轮 3 `offlinecommit` 第一步 `qcow2_commit(5,2)` 失败 | 2.qcow2 未含合并数据，但代码已发出请求 | DB 未翻转，幂等可重试 |
| 轮 3 `offlinecommit` 中途崩溃（`qcow2_commit` 成功 + `qcow2_rebase_no_check(vol)` 失败） | 2.qcow2 已含合并，vol.backing 仍指 5.qcow2 | DB 未翻转 → 二次删除请求可触发 reconciler 修复 |
| 轮 3 SQLBatch 失败 | 物理已合并 + vol.backing 已切，DB 仍记 vol→VO_5(0cab…) | **VO_2 仍在 DB，VO_5.installPath 仍是 0cab…，但 0cab… 物理文件已被抽空** —— 数据可见性破坏，需 reconciler 介入 |
| 轮 4 `delete` 失败 | 0cab…cd1a 文件残留 | 孤儿文件，无人引用，GC 清扫即可 |

**Stopped + Commit 路径最严重故障 = 轮 3 物理操作成功 + DB SQLBatch 失败**：物理上 vol 已挂 2.qcow2，但 DB 仍记 vol 挂 5.qcow2（=0cab…cd1a），重启会按 DB 拉起，导致 backing chain 指向**已被抽空但未删除**的 0cab…cd1a 文件，看不到任何已写入 2.qcow2 的数据。

加固设计的 reconciler I3b/I4 必须覆盖此场景。

---

## 8. 一图总结（实测时序）

```
16:54:56.076  APIDeleteVolumeSnapshotGroupMsg 进入
              direction=Commit, scope=single, groupUuid=ee59…2b98d
   │
16:54:56.483  POST /getbackingchain (查 2.qcow2 的 backing → 1.qcow2)
   │
16:54:56.642  [轮 1] POST /offlinemerge(srcPath=1, destPath=4)
              agent: qcow2_rebase(1.qcow2, 4.qcow2)
              DB: VO_4.parentUuid=1, distance--
   │
16:54:56.949  [轮 2] POST /offlinemerge(srcPath=1, destPath=3)
              agent: qcow2_rebase(1.qcow2, 3.qcow2)
              DB: VO_3.parentUuid=1, distance--
   │
16:54:57.236  [轮 3] POST /offlinecommit(top=5, base=2, topChildren=[vol])
              agent: qcow2_commit(5→2) + qcow2_rebase_no_check(2, vol)
              [DB SQLBatch] DELETE VO_2; VO_5.installPath=aa72…(原2文件),
                              VO_5.parentUuid=1, distance--
   │
16:54:57.589  [轮 4] POST /delete(path=0cab…=旧5物理文件)
   │
16:54:57.898  POST /getsize (vol) → SyncVolumeSize
   │
16:54:58.009  APIDeleteVolumeSnapshotGroupEvent success
              results: [{snapshotUuid=59897f45…c841, success=true}]
              总耗时 ≈ 1.93s
```

---

## 9. 与场景 02 / 03 / 04 的引用更新建议

- `02-...running-...md` 终态表"VO_2.installPath 互换为 5"应修正为"**VO_2 被直接删除**"
- `03-...stopped-...md` 顶部"Stopped + initial=Auto/Pull"小节保留；"Stopped + initial=Commit"分支应**全部引向本文件**而非自行推演
- `04-deleteSingleFlows-online-offline-decision.md` 末尾"场景 02/03 对应"表添加一行 "场景 05 = Stopped + Commit 实测，最后一轮走 offline commit + DB 互换 + 删 child 旧文件"
- `00-index.md` 添加场景 05 条目
