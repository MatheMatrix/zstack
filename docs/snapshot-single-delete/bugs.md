# 单盘快照删除（scope=single） — 当前实现 Bug 清单

> 5.5.6 基线，基于场景 02 / 03 / 04 / 05 的源码梳理 + ZSV 真实环境实测整理。
> 排序：先按"根本性 vs 派生"，再按严重度。
> 加固设计应优先覆盖 🔴 项；🟡 项作为语义修正；🟢 项作为代码质量改进。

## ✅ 修复进度（最新）

| Bug | 状态 | 修复方式 |
|---|---|---|
| Bug 0 | ✅ 已修复 | `VolumeTree.isOnline` 拆为 `isOnAliveChain`（VM 状态无关）+ `isHypervisorOperation`；`stepDelete` 改用 `isOnAliveChain` 选 `aliveChild`，保护对 Running/Stopped 都生效 |
| Bug 1 | ✅ 已修复（顺带） | `resolveDirection` 中 `shouldUseCommitStrategy` 解耦 vmState，Stopped + Auto 现在按结构走 Commit |
| Bug 3 | ✅ 失去影响 | `aliveChild` 显式识别后，`children.get(0)` 顺序不再影响保护 |
| Bug 7 | ✅ 失去影响 | 同上 |
| Bug 5 | 🟢 降级（中→低） | 互换路径变可预测，但仍建议显式记录 "要删的物理路径" |
| Bug 2 | ⚠ 待修复 | `direction=null → Commit` 与"不传 = Auto"惯例不符（1 行可改） |
| Bug 4 | ⚠ 待修复（P0） | 物理推进 + DB 未推进的幽灵态，需 reconciler + 意图日志 |
| Bug 6 | ⚠ 待修复 | 删除期间 VM 状态锁 |
| **Bug 8** | ⚠ 待修复（P0） | API `scope="chain"` 默认与 UI 直觉相反；`auto` 取值文档承诺但未实现；含一段死代码 warn |
| Bug 9 | ⚠ 待修复 | 内部 `DeleteVolumeSnapshotMsg.direction` 无默认 `auto`，cascade 路径退化为 Commit |

---

## Bug 0（根本性 / 🔴 高）：`isOnline` 把"alive chain 归属"与"是否走 hypervisor"耦合在同一布尔值 — ✅ **已修复**

> **修复**：拆 `isOnline` 为 `isOnAliveChain(uuid)` + 静态 `isHypervisorOperation(vmState)`；`stepDelete` 多子节点段改用 `isOnAliveChain` 识别 `aliveChild`，对 Running/Stopped 都生效。原 `isOnline` 签名保留，内部组合两个新方法，行为等价于"既在 alive chain 又走 hypervisor"。`resolveDirection` 中 `shouldUseCommitStrategy` 同步解耦 vmState（顺带修 Bug 1）。

### 现状

```java
// VolumeTree.java 行 389-392
public boolean isOnline(boolean current, target, child, VmInstanceState vmState) {
    return current
        && (vmState == Running || vmState == Paused)   // ← 把 vmState 当作 aliveChain 判定
        && aliveChain.contains(target)
        && aliveChain.contains(child);
}
```

### 问题

"alive chain"的真正含义是 **vol 当前依赖的快照链路**（vol.installPath → parentUuid 反向递归），这条链路在 VM Stopped 时**仍然真实存在**，仅仅是 VM 没在跑而已。重启时 libvirt 会照样按这条链拉起。

当前代码把两个语义合并：
- 通道选择（"用 libvirt 还是 qemu-img"）：**由 vmState 决定**
- 链路归属（"哪个 child 是 vol 所在的那条链，应该最后处理"）：**由 vol.installPath 链决定，与 vmState 无关**

把这两件事压在一个 `isOnline` 返回值里 → Stopped 时 `isOnline` 永远返回 false → `stepDelete` 多子节点段的"避开 alive 子节点"保护**完全失效**。

### 影响范围

1. **直接派生** Bug 3（顺序未定义）：Stopped 时 `onlineChild = null`，换位 if 进不去，`child = children.get(0)` 由底层 collection 顺序决定
2. **放大** Bug 4（幽灵态）的爆炸半径：若 vol 所在链被任意一轮选中，半完成态会直接波及 VM 启动链路
3. 加固设计 reconciler 失去"vol 链是最后被动"这个不变式

### 实测证据

场景 05（VM Stopped + Commit）的 children=[3,4,5]，实测 `children.get(0)` 返回 **4**，不是 distance 最小的 3，也不是 vol 所在的 5。本次"5 最后处理"是 collection 顺序的运气，不是代码语义保证。

### 修复方向

```java
// 拆开两个独立判定
public boolean isOnAliveChain(String snapshotUuid) {
    return aliveChain.contains(snapshotUuid);   // 与 vmState 无关
}

public boolean isHypervisorOperation(VmInstanceState vmState) {
    return vmState == Running || vmState == Paused;
}

// stepDelete 改写
SnapshotInventory aliveChild = children.firstMatch(c -> volumeTree.isOnAliveChain(c.getUuid()));
SnapshotInventory child = children.get(0);
if (aliveChild != null && child == aliveChild) {
    child = children.get(1);   // 对 Running / Stopped 都生效
}
boolean online = volumeTree.isOnAliveChain(child) && volumeTree.isHypervisorOperation(vmState);
```

效果：Stopped 时 vol 所在 child（如 5）被识别为 aliveChild → 强制最后处理 → 失败半径只到旁支。

---

## Bug 1（语义错误 / 🟡 中）：`direction=Auto` 在 Stopped 下退化为 Pull — ✅ **已修复（随 Bug 0）**

> **修复**：`resolveDirection` 中 `shouldUseCommitStrategy = current && !targetSnapshotIsLatest && isOnAliveChain(target) && isOnAliveChain(child)`，不再要求 VM Running/Paused。Stopped + Auto + 待删/child 都在 vol 链上 → 返回 Commit，磁盘占用回归单份合并文件。

### 现状

`VolumeTree.resolveDirection`（行 364-387）：

```java
boolean shouldUseCommitStrategy = current && !targetSnapshotIsLatest && online;
if ("Auto".equals(initial)) {
    return shouldUseCommitStrategy ? Commit : Pull;
}
```

VM Stopped → `online=false` → `shouldUseCommitStrategy=false` → Auto 返回 **Pull**。

### 问题

"Auto"的用户预期是"按最优策略走"，但 Stopped 下 Auto = Pull 的代价：

| 路径 | 物理操作 | 磁盘占用 |
|---|---|---|
| Stopped + Commit | `offline_commit_snapshot` 单次 qcow2_commit | 单份合并文件 |
| **Stopped + Auto/Pull** | N 次 `offline_merge_snapshot`（每 child 一次 qcow2_rebase） | **N 份 (target - parent) 差量副本** |

N = currentRoot 的 children 数。N=3 时磁盘占用接近 3 倍。

### 修复方向

`resolveDirection` 里 Auto 在离线场景下也允许返回 Commit。可选规则：
- 简单：`Auto + Stopped + !targetIsLatest` 总是返回 Commit
- 复杂：根据 children 数量 / 差量大小做容量评估

### 影响

不影响正确性，影响**容量预期**。生产环境如果客户期望"删快照能释放空间"，Auto 路径反而把空间放大。

---

## Bug 2（API 语义不一致 / 🟡 中）：`direction=null` 当作 Commit，不是当作 Auto

### 现状

`VolumeTree.resolveDirection` 第一行：

```java
if (initial == null) {
    return VolumeSnapshotDeletionDirection.Commit;
}
```

### 问题

- 大部分 ZStack API "字段不传 = 默认 = Auto" 是惯例
- 这里 "字段不传 = 强制 Commit" —— 行为与 `direction=Auto` 显式传入完全不同（参考 Bug 1）

后果：
- 前端调用方调试时不传 direction，意外触发离线 commit（DB 互换、VO 直接 DELETE）
- 自动化脚本若按 "省略 = 默认" 风格写，行为不可预测

### 修复方向

任一即可：
- API 入口校验 `direction != null`，否则报错
- `resolveDirection` 里 `null` 当 Auto 处理（再结合 Bug 1 修复）

---

## Bug 3（派生 / 🟢 低）：`children.get(0)` 顺序未定义 — ✅ **失去影响（随 Bug 0）**

> **修复后**：不管 collection 返回 [3,4,5] / [4,3,5] / [5,3,4]，`aliveChild=5` 都会被 `isOnAliveChain` 显式识别并放最后处理。顺序假设不再是行为前提。

### 现状

`stepDelete` 多子节点段直接 `children.get(0)`，children 来自 `tree.snapshotLeaf(currentRoot).children` 的 Collection。

### 实测

场景 05 树 [3,4,5] 取出顺序为 [4,3,5]，非按 distance 也非按 createDate。

### 问题

Stopped 时 Bug 0 让换位保护失效 → 任意顺序都可能选中 vol 所在 child。

### 与 Bug 0 关系

**Bug 0 是因，Bug 3 是果**。修了 Bug 0（按 alive chain 归属避开 vol 链），children 顺序就不重要了 —— 不管返回 [3,4,5] / [4,3,5] / [5,3,4]，aliveChild=5 都会被识别并放到最后。

如果只想做"小步修复"，可单独排序 children（按 distance 或按"是否在 vol 链上"），但根治还是修 Bug 0。

---

## Bug 4（崩溃半完成态 / 🔴 高）：轮 3 `offline_commit` 物理成功 + DB SQLBatch 失败 → 幽灵态

### 触发

Stopped + Commit + scope=single + 待删节点有子节点（即 commit 路径生效）：
- agent `qcow2_commit(top=5, base=2)` + `qcow2_rebase_no_check(vol)` 完成（物理已合并、vol backing 已切）
- Java 端 `updateDatabaseAfterCommit` 的 SQLBatch 失败（DB 死锁 / 连接断 / JVM crash）

### 物理 vs DB 不一致

```
物理：
  vol.qcow2 头部 backing = 2.qcow2（aa72…e70c）
  2.qcow2 含 5+2 合并数据
  5.qcow2 已被抽空但文件未删（轮 4 还没执行）

DB（仍是互换前状态）：
  VO_2.installPath = 2.qcow2  → 仍存在
  VO_5.installPath = 5.qcow2  → 指向已被抽空的文件
  vol.installPath  = 5.qcow2（DB 字段一直不变）
```

### 后果

1. **VM 启动**：libvirt 读 vol.qcow2 头部找 backing → 找到 2.qcow2 → 能启动 → 但 DB 视图错乱
2. **后续删除请求**：若用户再次发起删 VO_2 / VO_5，stepDelete 会按 DB 推演，与物理状态对不上
3. **reconciler 误判**：看到 VO_5.installPath=5.qcow2 文件被抽空，可能误判为"5 损坏需要修复"，触发重建覆盖已合并数据

### 修复方向

- 物理操作前写"操作意图日志"（CommitVolumeSnapshotIntentVO 或类似），记录 src/dst/topChildren/target DB 状态
- 重启时按日志做幂等推进（物理已成功 → 补 DB；DB 已成功 → 跳过）
- 物理 + DB 的对应关系通过日志显式追踪，不依赖内存 inventory

---

## Bug 5（隐式状态传递 / 🟢 低 — 修 Bug 0 后从 🟡 降级）：轮 4 删除路径依赖未文档化的内存对象状态

### 现状

轮 3 互换后 VO_2 整条 DELETE，但 `stepDelete` 调用栈仍持有 currentRoot 的内存 inventory。轮 4 进入 `deleteVolumeSnapshotAndSyncVolumeSize`，传给 agent 的物理路径来自这个内存 inventory。

实测（场景 05）轮 4 删的是 `0cab…cd1a.qcow2`（原 VO_5 物理文件），不是 `aa72…e70c.qcow2`（原 VO_2 物理文件，已被 VO_5 接管）—— **删对了**。

### 问题

这个"删对了"靠的是某处把内存 inventory 的 installPath 字段在互换时改写为了"被删者旧的 src 文件路径"（5 的旧文件）—— 但这个状态传递**没有显式记录**，全靠 SQLBatch 旁的内存写。

任何重构（比如把互换改成只动 DB 不动内存对象）都可能让轮 4 删错对象：
- **删错为 `aa72…e70c.qcow2`** → 把含合并数据的文件删掉 → vol 启动失败、真实数据丢失

### 修复方向

互换 + 物理删的对应关系显式记录：
```java
SwapResult result = updateDatabaseAfterCommit(src, dst);
// result.physicalFileToDelete = "5.qcow2 的物理路径"
// 显式传给轮 4，不靠内存 inventory
```

---

## Bug 8（API 默认值 / 🔴 高）：`scope = "chain"` 默认值与 UI 直觉相反；`auto` 取值文档承诺但未实现

### 现状

```java
// APIDeleteVolumeSnapshotMsg.java 行 70-71
// APIDeleteVolumeSnapshotGroupMsg.java 行 31-32
@APIParam(required = false, validValues = {"single", "chain", "auto"})
private String scope = "chain";
```

```java
// VolumeSnapshotTreeBase.java 行 473-490
if (Objects.equals(msg.getScope(), DeleteVolumeSnapshotScope.Chain.toString())) {
    if (msg.getScope() == null) {                      // ← 死代码：上一行已 false
        logger.warn("snapshot deletion scope is null, default to Chain scope");
    }
    ...
    deleteChainFlows();                                 // 删 currentLeaf 及其所有 descendants
} else {
    deleteSingleFlows();                                // 仅删该节点 + merge
}
```

### 问题

1. **默认 `chain` 与"删快照"UI 直觉不符**：用户在快照管理页面点"删除"，预期是 single（"只删这一个，别动旁支/后代"）。默认 chain 会**雪崩删整棵子树**，CLI/SDK 用户漏传 scope 即触发，恢复成本极高。
2. **快照组（Group）默认 `chain` 风险更大**：一个 group 含多盘，每盘按 chain 默认 → 单次 API 调用可能删几十个 snapshot。
3. **`auto` 是死字符串**：`Objects.equals(scope, "Chain")` 是硬比较，传 `"auto"` 实际进 else 分支等价于 `single`。文档（validValues）承诺 auto 智能判断，实现完全没有。
4. **死代码 warn**：`if (msg.getScope() == null) logger.warn(...)` 永远进不来 —— 第一行 `Objects.equals(null, "Chain")` 已返回 false。表明原作者意图"null → Chain"但被 API 层默认值掩盖。

### 修复方向

- **改默认为 `single`**：单盘 API 默认 single（与 UI 直觉一致）；Group 的默认建议同步改 single 或前端强制确认弹窗
- **实现 `auto` 分支**：如"无 children → single；有 children 且全是叶子 → single；否则按用户场景"；或直接从 validValues 移除 `auto`
- **删除死代码 warn**：替换为真正的 null 防御 `if (scope == null) scope = Single;`
- **统一 enum 比较**：用 `DeleteVolumeSnapshotScope.valueOf(scope) == Chain` 而不是字符串硬比，避免大小写 / 拼写漂移

### 影响

- 误删风险：CLI / 自动化脚本漏传 scope → 整棵子树消失
- 文档与实现脱节：开放给用户的 `auto` 取值名义存在、行为不存在
- 加固设计若依赖 scope 语义（如 reconciler 区分单点/链）会被字符串硬比的实现绊倒

---

## Bug 9（API → 内部 msg 默认值脱钩 / 🟡 中）：内部 `DeleteVolumeSnapshotMsg.direction` 没默认 `auto`，cascade 路径退化为 Commit

### 现状

```java
// APIDeleteVolumeSnapshotMsg.java
private String direction = "auto";          // ✅ API 层有默认

// DeleteVolumeSnapshotMsg.java
private String direction;                    // ❌ 内部 msg 无默认
private String scope;                        // ❌ 同上

// VolumeSnapshotDeletionMsg.java
private String direction;                    // ❌ 同上
private String scope;
```

### 问题

任何**非 API 入口**的调用路径（cascade 删 volume 时联动删 snapshot、snapshot group 内部 split 派发到单盘 msg、定时清理任务等），如果不显式 `setDirection("auto")`，直接传 null 进 `VolumeTree.resolveDirection`，会落到 Bug 2 路径 → 强制 Commit。

后果：
- 用户从 UI 操作 = `direction=auto` 路径
- 系统级联（删 vm/volume 联动）= `direction=null → Commit` 路径
- **同样的快照树，两条入口行为完全不同**，对账 / 复现困难

### 修复方向

- 内部 msg 字段也给 `= "auto"` 默认（一行）
- 或在 `VolumeSnapshotTreeBase.handleDeletionMsg` 入口统一兜底：`if (direction == null) direction = "auto";`
- 与 Bug 2 一并修复（"resolveDirection 中 null 当 Auto"）即可顺带解决，但更稳妥是 msg 层和处理层双兜底

### 与 Bug 2 关系

Bug 2 是"resolveDirection 把 null 当 Commit"；Bug 9 是"为什么内部 msg 会把 null 传进来"。修 Bug 2 解决症状，修 Bug 9 解决源头。两条都修最稳。

---

## Bug 6（顶替原 Risk 6 / 🟡 中）：删除过程中 vmState 无锁，可能与 VM 启动竞争

### 触发

`deleteSingleFlows` 行 852-859 一次性查 vmState，整个递归 stepDelete 复用该值。期间若 VM 被并发启动（API / 调度器 / autoStart）：
- agent 正在做 `qcow2_commit` / `qcow2_rebase`
- libvirt 同时尝试启动 VM，qemu 探测 backing 链

后果难以预测：qemu-img 与 qemu 进程对同一文件加锁冲突、或 qemu 读到半完成的 backing 头部。

### 修复方向

- 删除操作期间在 VM 上加状态锁（如 `LockVmInstanceMsg`）
- 或每轮重新校验 vmState，发现变动即终止

---

## Bug 7（次要）：`children` 排序行为依赖底层实现 — ✅ **失去影响（随 Bug 0）**

修复后测试不再受 collection 实现顺序影响，因为 `aliveChild` 选择是基于内容（uuid 是否在 aliveChain 中）而非位置。但**为了测试稳定性**，仍建议未来给 children 加确定排序。

---

## 严重度汇总表

| # | Bug | 严重度 | 类型 | 根因 / 派生 | 修复状态 |
|---|---|---|---|---|---|
| **Bug 0** | `isOnline` 耦合 vmState 与 aliveChain | 🔴 高 | 设计层 | 根因 | ✅ 已修复 |
| Bug 1 | Auto 在 Stopped 退化为 Pull，磁盘放大 N 倍 | 🟡 中 | 语义错误 | 独立 | ✅ 随 Bug 0 修复 |
| Bug 2 | direction=null 当作 Commit 而非 Auto | 🟡 中 | API 语义不一致 | 独立 | ⚠ 待修复 |
| Bug 3 | children.get(0) 顺序未定义 | 🟢 低 | 实现细节 | 派生自 Bug 0 | ✅ 失去影响 |
| **Bug 4** | offline commit 物理成功 + SQLBatch 失败 → 幽灵态 | 🔴 高 | 崩溃原子性 | 独立 | ⚠ 待修复（P0） |
| Bug 5 | 轮 4 删除路径靠内存 inventory 传递 | 🟢 低（修 Bug 0 后降级） | 代码质量 | 重构风险 | ⚠ 待修复（P1） |
| Bug 6 | vmState 无锁，删除与 VM 启动可竞争 | 🟡 中 | 并发 | 独立 | ⚠ 待修复 |
| Bug 7 | children 顺序依赖底层 collection 实现 | 🟢 低 | 测试稳定性 | 派生自 Bug 0 | ✅ 失去影响 |
| **Bug 8** | API `scope="chain"` 默认值 + `auto` 取值未实现 + 死代码 warn | 🔴 高 | API 契约 | 独立 | ⚠ 待修复（P0） |
| Bug 9 | 内部 `DeleteVolumeSnapshotMsg.direction` 无默认 `auto` | 🟡 中 | 入口一致性 | 与 Bug 2 同源 | ⚠ 待修复 |

---

## 加固设计优先级建议（剩余项）

| 优先级 | 任务 | 覆盖 Bug |
|---|---|---|
| ~~P0~~ | ~~拆 `isOnline` 为 `isOnAliveChain` + `isHypervisorOperation`~~ | ~~Bug 0、1、3、7（降级 5）~~ ✅ 已完成 |
| **P0** | reconciler 检测"物理推进 + DB 未推进"幽灵态 + 操作意图日志 | Bug 4 |
| **P0** | API `scope` 默认改 `single`、实现 `auto` 分支或下线 `auto` validValue、删死代码 warn | Bug 8 |
| P1 | `direction=null` 当 Auto + 内部 msg 默认值同步为 `auto` | Bug 2、Bug 9 |
| P1 | 互换 + 物理删的对应关系显式化 | Bug 5 |
| P2 | 删除操作期间 VM 状态锁 | Bug 6 |

---

## Bug → 场景对应

| Bug | 在哪些场景文档可见 |
|---|---|
| Bug 0 | 03（口径说明）、04（决策矩阵）、05（实测顺序异常） |
| Bug 1 | 03（Auto/Pull 路径写出磁盘 N 份差量）、04（决策矩阵） |
| Bug 2 | 04（"initial=null → Commit"决策表） |
| Bug 3 | 05 §6（实测 children.get(0)=4 非 3） |
| Bug 4 | 05 §7（脆弱点表，Stopped + Commit 最严重故障） |
| Bug 5 | 05 §4 轮 4 / §6 与推演的差异 |
| Bug 6 | 04（vmState 一次性读取） |
| Bug 7 | 05 §6（顺序差异） |
| Bug 8 | 04（scope 决策入口；当前文档未覆盖 chain 路径，建议补一段） |
| Bug 9 | 04（direction 入口路径，cascade / group split 未列出） |
