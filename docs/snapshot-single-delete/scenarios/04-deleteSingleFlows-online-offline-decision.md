# 场景 04：`deleteSingleFlows()` 中 online / offline 分支的判定时序

> 当前代码逻辑梳理（5.5.6 基线），不含加固设计。
> 源码：
> - `VolumeSnapshotTreeBase.java` 行 828-1290（`deleteSingleFlows` / `stepDelete` / `commit` / `pull`）
> - `VolumeTree.java` 行 364-392（`resolveDirection` / `isOnline`）

---

## 总览：online / direction 在哪两步被决定

### 极简决策图

```
              ┌────────────────────────┐
              │ deleteSingleFlows()    │
              │  查 vmState (一次)      │
              └───────────┬────────────┘
                          │
                          ▼
              ┌────────────────────────┐
              │ stepDelete() (每轮)     │
              │  children = ?          │
              └───────────┬────────────┘
                          │
        ┌─────────────────┼──────────────────┐
        │                 │                  │
        ▼                 ▼                  ▼
   children=0       children≥2         children=1
        │                 │                  │
        │                 │            ┌─────┴────────┐
        │                 │            │ resolveDir   │
        │                 │            │ +isOnline    │
        │                 │            └─────┬────────┘
        ▼                 ▼                  ▼
  ┌──────────┐      ┌──────────┐       ┌─────────┐
  │ deleteVO │      │  pull    │       │ commit  │ or pull
  │ (终结)   │      │ (强制)   │       │         │
  └──────────┘      └────┬─────┘       └────┬────┘
                         │                  │
                         └────────┬─────────┘
                                  ▼
                         ┌────────────────┐
                         │  online?       │
                         └───┬────────┬───┘
                       true  │        │  false
                             ▼        ▼
                      Hypervisor   PrimaryStorage
                         Msg          Msg
```

### 四象限：(direction × online) → agent 入口（一图速查）

```
                 ┌─────────────────────┬─────────────────────┐
                 │   online = true     │   online = false    │
                 │   (Running/Paused   │   (Stopped/Destroy  │
                 │    + alive chain)   │    或非 alive)       │
   ┌─────────────┼─────────────────────┼─────────────────────┤
   │  Commit     │ libvirt blockCommit │ qemu-img commit     │
   │             │ + pivot (active)    │ child→parent + 子节  │
   │  (默认 / null)│  vm_plugin          │ 点 rebase            │
   │             │  do_block_commit    │ offline_commit_     │
   │             │                     │   snapshot          │
   ├─────────────┼─────────────────────┼─────────────────────┤
   │  Pull       │ block-stream / pull │ qemu-img rebase     │
   │             │ on hypervisor       │ (parent, child)     │
   │  (Auto 在线) │  vm_plugin          │ offline_merge_      │
   │             │  do_pull            │   snapshot          │
   └─────────────┴─────────────────────┴─────────────────────┘
                 ↑                       ↑
                 场景 02 最后一轮         场景 02 轮 1/2
                                         场景 03 全程
```

### "请求 → 多轮 stepDelete → agent 入口"时间线

```
APIDeleteVolumeSnapshotMsg (direction=null/Auto/Pull/Commit)
        │
        ▼
┌──────────────────────────────────────────────────────────────┐
│ deleteSingleFlows()                                          │
│   vmState = query  (一次, 整请求复用)                          │
└──────────────────────────────────────────────────────────────┘
        │
        ▼
┌──────────────────────────────────────────────────────────────┐
│ 轮1  stepDelete  children=[3,4,5]                            │
│   多子节点段 → 强制 pull → child=3 → online?                   │
│       Running+3∈alive → 在线 pull (但本例 3 非 alive → offline)│
└──────────────────────────────────────────────────────────────┘
        │
        ▼
┌──────────────────────────────────────────────────────────────┐
│ 轮2  stepDelete  children=[4,5]                              │
│   多子节点段 → 强制 pull → child=4 → offline                   │
└──────────────────────────────────────────────────────────────┘
        │
        ▼
┌──────────────────────────────────────────────────────────────┐
│ 轮3  stepDelete  children=[5]                                │
│   单子节点段 → resolveDirection → Commit/Pull                  │
│              → isOnline → true/false                         │
│              → commit() or pull() → hypervisor / PS msg      │
└──────────────────────────────────────────────────────────────┘
        │
        ▼
┌──────────────────────────────────────────────────────────────┐
│ 轮4  stepDelete  children=[]                                 │
│   终结 → deleteVolumeSnapshotAndSyncVolumeSize                │
└──────────────────────────────────────────────────────────────┘
```

---

## 详细判定表（保留供查阅）

整个删除请求只关心**两个布尔 / 枚举判定**：

| 判定 | 取值 | 决定时机 | 决定位置 | 决定输入 |
|---|---|---|---|---|
| `vmState` | Running / Paused / Stopped / Destroyed / Destroying | `deleteSingleFlows()` flow 开头 | 行 852-859 | `VmInstanceVO.state`（如果 volume 没挂 vm，`vmState=null`） |
| `direction` | Commit / Pull / Auto / null | `stepDelete()` 仅在 **children.size()==1** 时计算 | 行 904-905 | `msg.getDirection()`（API 入参）+ `currentRoot.isLatest()` + `vmState` |
| `online` | true / false | `stepDelete()` 每一轮选完 child 后立即算 | 行 906 / 行 916 | `tree.current` + `vmState` + `target/child ∈ aliveChain` |

`commit()` / `pull()` 内部再用一次 `online`（参数透传）决定走 hypervisor 消息还是 primary storage 消息。

---

## 第一步：`vmState` 校验（行 852-859）

```java
if (volume.getVmInstanceUuid() != null) {
    vmState = Q.New(VmInstanceVO.class)...select(state).findValue();
    if (vmState != Running && vmState != Paused
            && vmState != Destroyed && vmState != Stopped && vmState != Destroying) {
        trigger.fail("vm is not Running/Paused/Destroyed/Stopped/Destroying");
        return;
    }
}
```

要点：
- volume 未挂 VM → `vmState = null`，后续所有 `online` 计算返回 false（Pull 全走离线）
- 合法的 vmState：5 种，其中 **Running / Paused 才有可能 online**；Stopped / Destroying / Destroyed 一定 offline
- `vmState` 仅查一次，整个删除请求过程中**复用同一快照值**（不在 stepDelete 每轮重查）

---

## 第二步（每轮）：`stepDelete()` 选 child 并判定 online / direction

行 875-919 的伪流程：

```
stepDelete():
    children = tree.getSnapshotLeaf(currentRoot.uuid).getChildren()

    if children.isEmpty():
        deleteVolumeSnapshotAndSyncVolumeSize()      # 终结分支，无 online/direction 判定
        return

    onlineChild = children.firstMatch(c -> isOnline(currentRoot, c, vmState))   # ⚠ Bug 0 已修复：改为 isOnAliveChain(c)，命名也改为 aliveChild

    if children.size() == 1:
        child = children.get(0)
        direction = tree.resolveDirection(currentRoot, child, msg.direction, currentRoot.isLatest, vmState)
        online = tree.isOnline(current, currentRoot, child, vmState)
        if direction == Commit:
            commit(child, tree, online, comp)
        else:
            pull(child, tree, online, comp)
    else:
        # 多子节点：避开 alive child（让它最后一轮单独跑 commit）
        if onlineChild != null && children.get(0) == onlineChild:
            child = children.get(1)
        else:
            child = children.get(0)
        online = tree.isOnline(current, currentRoot, child, vmState)
        pull(child, tree, online, comp)              # 多子节点段恒走 pull，不判定 direction
```

### 2.1 `direction` 判定的"作用域"

`direction` **只在 children.size()==1 时计算并使用**。多子节点段恒走 pull（行 917 `pull(...)`，不调 `resolveDirection`）。也就是说：

- 多子节点段：`msg.getDirection()` 即使是 Commit，也**被忽略**，强制 pull
- 多子节点段最终把所有非 alive 子节点都推下去后，剩 1 个子节点（通常是 alive child）→ 才进入"判 direction"分支

### 2.2 `resolveDirection`（`VolumeTree.java` 行 364-387）

```java
boolean online = (vmState == Running || Paused)
              && aliveChain.contains(target) && aliveChain.contains(child);
boolean shouldUseCommitStrategy = current && !targetSnapshotIsLatest && online;

if (initialDirection == "Pull" && shouldUseCommitStrategy)
    throw "the snapshot will be deleted by block 'commit', but the direction is 'pull'";

if (initialDirection == null)        return Commit;          // 默认 Commit
if (initialDirection == "Auto")      return shouldUseCommitStrategy ? Commit : Pull;
return DeleteVolumeSnapshotDirection.fromString(initialDirection);   // 显式 Commit / Pull
```

输入到决策的真值表（current 树 + child=alive child 的常见情形）：

| `vmState` | `targetIsLatest` | online | shouldCommit | initial=Auto | initial=null | initial=Pull | initial=Commit |
|---|---|---|---|---|---|---|---|
| Running | false | true | **true** | **Commit** | Commit | ❌ throw | Commit |
| Running | true | true | false | Pull | Commit | Pull | Commit |
| Stopped | * | false | false | Pull | Commit | Pull | Commit |
| Paused | false | true | true | Commit | Commit | ❌ throw | Commit |

> ⚠ **Bug 0 修复后**（参考 `../bugs.md`）：`shouldUseCommitStrategy` 已解耦 vmState。新规则只看 "target/child 是否都在 aliveChain"。修复后 `Stopped + target/child∈aliveChain` 行：`shouldCommit=true`、`Auto → Commit`、`Pull → ❌ throw`。Stopped + Auto + 待删/child 都在 vol 链上 → 走 offline commit（与场景 05 路径一致），不再写出 N 份差量。

注意三个反直觉点：
1. `initial=null` 总是返回 Commit（不看 online） —— Commit 路径在离线下会落到 `CommitVolumeSnapshotOnPrimaryStorageMsg → offline_commit_snapshot`
2. `initial=Pull` 在 shouldCommit 时直接 throw —— API 拒绝
3. `initial=Auto` 才会真正按 online 切换；这是 `APIDeleteVolumeSnapshotMsg` 默认值（前端通常不显式指定 → 走 Auto）

### 2.3 `isOnline`（`VolumeTree.java` 行 389-392）

```java
return treeIsCurrent
    && (vmState == Running || Paused)
    && aliveChain.contains(target) && aliveChain.contains(child);
```

四个条件全 true 才返回 true：
- `treeIsCurrent`：该 snapshot 树当前挂在 volume 上（VolumeSnapshotTreeVO.current=true）
- `vmState ∈ {Running, Paused}`
- `target`（被删者）在 aliveChain 上
- `child`（被选中合并方）在 aliveChain 上

**关键观察**：`shouldUseCommitStrategy` 的 online 子句**与 `isOnline` 对 `target/child` 的判定本质相同**（除 `treeIsCurrent` 外）。所以 `direction == Commit` 几乎一定意味着 `online == true`（仅"非 current 树"是反例 —— 但非 current 树通常也不在 aliveChain）。

---

## 第三步：`commit()` / `pull()` 用 `online` 选 hypervisor 还是 primary storage 消息

### 3.1 `commit()` 行 1006-1080

```java
if (online) {
    String hostUuid = ...VmInstanceVO.hostUuid;
    CommitVolumeSnapshotOnHypervisorMsg cmsg = new CommitVolumeSnapshotOnHypervisorMsg();
    ...
    bus.send(cmsg);    // → KVMHost → vm_plugin do_block_commit (libvirt blockCommit + pivot)
} else {
    CommitVolumeSnapshotOnPrimaryStorageMsg cmsg = new CommitVolumeSnapshotOnPrimaryStorageMsg();
    ...
    bus.send(cmsg);    // → LocalStorageKvmBackend.handle → OFFLINE_COMMIT_PATH → offline_commit_snapshot
}
```

### 3.2 `pull()` 行 1227-1268

```java
if (online) {
    PullVolumeSnapshotOnHypervisorMsg pmsg = new PullVolumeSnapshotOnHypervisorMsg();
    ...
    bus.send(pmsg);    // → KVMHost → vm_plugin do_block_stream / do_block_commit (取决于 hypervisor 实现)
} else {
    PullVolumeSnapshotOnPrimaryStorageMsg pmsg = new PullVolumeSnapshotOnPrimaryStorageMsg();
    ...
    bus.send(pmsg);    // → LocalStorageKvmBackend.handle → OFFLINE_MERGE_PATH → offline_merge_snapshot
}
```

### 3.3 (direction × online) 四象限到 agent 入口

| direction | online | Java 消息 | Agent 入口 | 物理操作 |
|---|---|---|---|---|
| Commit | true | `CommitVolumeSnapshotOnHypervisorMsg` | KVM `vm_plugin` `do_block_commit` | libvirt blockCommit (active) + pivot |
| Commit | false | `CommitVolumeSnapshotOnPrimaryStorageMsg` | local `offline_commit_snapshot` | `qcow2_commit(child→parent)` + 给 child 的 children 重 rebase 到 parent |
| Pull | true | `PullVolumeSnapshotOnHypervisorMsg` | KVM `vm_plugin`（pull-on-hypervisor 路径，存储具体逻辑因 backend 而异） | online block-stream / commit 子型 |
| Pull | false | `PullVolumeSnapshotOnPrimaryStorageMsg` | local `offline_merge_snapshot` | `qcow2_rebase(parent, child)`（差量进 child） |

注意第 2 行（Commit + offline）几乎只在 `initial=null`（前端不传 direction）+ Stopped 下被走到。多子节点段被强制 pull 不会落到这里。

---

## 第四步：判定时序时间线（一次 stepDelete 调用）

```
[控制面入口]
    deleteSingleFlows() flow start
       │
       ├─ Storage / Memory 类型短路 → deleteVolumeSnapshotAndSyncVolumeSize → end
       │
       ├─ vmState = query VmInstanceVO.state              # 仅一次
       │     不在 5 种合法状态 → fail
       │
       └─ stepDelete()                                    # 递归入口
              │
              ├─ children = tree.snapshotLeaf(currentRoot).children
              ├─ if empty → deleteVolumeSnapshotAndSyncVolumeSize → comp.success → 收敛
              │
              ├─ onlineChild = children.firstMatch(isOnline)    # 选 alive child
              │
              ├─ if size == 1:
              │     direction = resolveDirection(target, child, msg.dir, isLatest, vmState)  # ★direction 判定★
              │     online    = isOnline(current, target, child, vmState)                    # ★online 判定★
              │     if Commit → commit(child, tree, online, comp)
              │              └─ commit 内: if online → CommitOnHypervisor; else → CommitOnPS
              │     else     → pull(child, tree, online, comp)
              │              └─ pull 内:   if online → PullOnHypervisor;   else → PullOnPS
              │
              └─ if size >= 2:
                    if onlineChild != null && children.get(0) == onlineChild:
                        child = children.get(1)    # 避开 alive，让它最后做
                    online = isOnline(current, target, child, vmState)        # ★online 判定★（无 direction 判定）
                    pull(child, tree, online, comp)
                              └─ pull 内:   if online → PullOnHypervisor; else → PullOnPS

[每轮 child 处理完成后]
    comp.success() → stepDelete(comp)   # 重新拉一轮 children，递归直至 empty
```

每轮 stepDelete 至多产生一条 commit 或 pull 消息；vmState 在整个递归中复用，online 每轮单独算（树结构在变，但 vmState 不变 → online 实际由 "child 是否仍在 aliveChain" 决定）。

---

## 关键结论速查

1. **online / offline 不是请求级开关，是"每轮 × 该轮选中 child"级开关**
2. **direction 仅在最后一轮（children.size==1）才参与决策**；多子节点段恒走 pull
3. **vmState ∈ {Stopped, Destroyed, Destroying} → 整个请求所有轮全 offline**（无论 child 是否在 aliveChain）
4. **vmState ∈ {Running, Paused} 但被删快照不在 aliveChain → 仍 offline**（典型如：删的是分叉的 sibling 而非主链）
5. **`initial=null`（前端不传） → direction 一定 Commit**：在 Stopped 时会把 commit 路径打到 `offline_commit_snapshot`；前端如果想 Auto 行为必须显式传 `direction=Auto`
6. **`initial=Pull` 但 shouldCommit → API 直接 throw**：这是个白名单校验，避免在线 alive chain 被强制走 pull 导致 VM 被踢出

---

## 与场景 02 / 03 / 05 的对应

| 场景 | vmState | initial.direction | 多子节点段（轮 1-2） | 最后一轮（children.size=1） | 类型 |
|---|---|---|---|---|---|
| 02 (Running, 删快照2) | Running | Commit / Auto | online=false → 离线 pull (`offlinemerge`) | direction=Commit + online=true → **在线** commit (libvirt blockCommit + pivot) | 源码推演 |
| 03 (Stopped, 删快照2) | Stopped | Auto / Pull | online=false → 离线 pull (`offlinemerge`) | direction=Pull + online=false → 离线 pull (`offlinemerge`，差量进 5.qcow2，DB 不互换) | 源码推演 |
| **05** (Stopped, 删快照2) | Stopped | **Commit** | online=false → 离线 pull (`offlinemerge`) | direction=Commit + online=false → **离线 commit (`offlinecommit`，数据 5→2，DB 互换 + VO_2 DELETE)** | **实测** |

注：场景 03 的"最后一轮"实际行为取决于 API 入参的 `direction`：
- `direction=null`（无入参）→ resolveDirection 返回 Commit → **同场景 05 路径**
- `direction=Auto` → 因 `online=false` 返回 Pull → `PullVolumeSnapshotOnPrimaryStorageMsg` → `offline_merge_snapshot`（数据 1→5 差量，DB 不互换）
- `direction=Pull` → 不 throw（因 `shouldCommit=false`）→ 同 Auto
- `direction=Commit` → 落到场景 05 实测路径

**`03-...stopped-...md` 按 `initial=Auto/Pull` 口径写**；**`05-...actual.md` 按 `initial=Commit` 实测**。两者覆盖 Stopped 路径的两种 direction 分支。加固设计的"入参矩阵"必须分别覆盖。
