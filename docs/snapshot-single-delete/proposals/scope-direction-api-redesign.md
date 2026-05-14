# 快照删除 API 参数（`scope` / `direction`）重构提案

> 范围：`APIDeleteVolumeSnapshotMsg`、`APIDeleteVolumeSnapshotGroupMsg` 及其内部派生 msg
> 关联 Bug：bugs.md 中 **Bug 2 / Bug 8 / Bug 9**
> 基线：5.5.6
> 状态：提案（未实施）

---

## 1. 背景

历史上"删除快照"的语义只有一种 —— **删除待删节点 + 所有子孙节点**（子树雪崩删）。后来引入"单点删除"（只删该节点本身，子孙 merge 到 parent），通过 `scope` 入参区分两种行为：

```java
// APIDeleteVolumeSnapshotMsg.java / APIDeleteVolumeSnapshotGroupMsg.java
@APIParam(required = false, validValues = {"single", "chain", "auto"})
private String scope = "chain";

@APIParam(required = false, validValues = {"pull", "commit", "auto"})
private String direction = "auto";
```

```java
// VolumeSnapshotTreeBase.handleDeletionMsg 行 473-490
if (Objects.equals(msg.getScope(), DeleteVolumeSnapshotScope.Chain.toString())) {
    if (msg.getScope() == null) {
        logger.warn("snapshot deletion scope is null, default to Chain scope");
    }
    ...
    deleteChainFlows();
} else {
    deleteSingleFlows();
}
```

---

## 2. 当前设计的问题

### 2.1 `scope` 相关

| # | 问题 | 影响 |
|---|---|---|
| S1 | `validValues` 列了 `"auto"`，但代码用 `Objects.equals(scope, "Chain")` 字符串硬比，`auto` 实际等价于 `single` | 文档承诺 ≠ 实现；调用方误判 |
| S2 | `if (msg.getScope() == null) logger.warn(...)` 是死代码 —— 上一行 `Objects.equals(null, "Chain")` 已 false，永远进不来 | warn 永远打不出，作者意图（"null → Chain 兜底"）未生效 |
| S3 | "凡是非 Chain 字符串都默默走 single" —— 拼错 / 大小写漂移 / 老 `auto` 全部静默走 single | 异常值无法被发现，潜在数据破坏 |
| S4 | `chain` 命名容易被误读为"alive chain"或"整棵 tree" | 文档与实现差异，新人误读 |
| S5 | Group API 默认 `chain` 风险高一个量级（多盘 × 子树） | 一次 API 调用可能删几十个 snapshot |

### 2.2 `direction` 相关

| # | 问题 | 影响 |
|---|---|---|
| D1 | API 层默认 `"auto"`，但内部 `DeleteVolumeSnapshotMsg.direction` / `VolumeSnapshotDeletionMsg.direction` 没有默认值（null） | cascade、group split、定时清理路径若不显式 set 即传 null |
| D2 | `VolumeTree.resolveDirection(null) → Commit`，与"不传 = Auto"惯例相反 | 同棵树两条入口（API vs cascade）行为分叉；Stopped 下意外走 offline_commit |

### 2.3 语义对清

为避免"chain"再被歧义解读，先固化术语：

| 术语 | 定义 |
|---|---|
| **chain（本提案中）** | 以待删节点为根的子树（`currentLeaf.getDescendants()`），含所有子孙、旁支、分叉。**不是** alive chain，**不是**整棵 tree。 |
| **single** | 仅待删节点本身；子孙保留并 merge 到 parent。 |
| **alive chain**（不在本提案 scope 中） | vol 当前依赖的快照链路（vol.installPath → parentUuid 反向递归）。仅出现在 `VolumeTree.aliveChain` 内部判定，与 API `scope` 无关。 |

---

## 3. 设计目标

1. **保留默认 `chain`** —— 与老 API 行为兼容，避免 5.x.x 升级断老脚本 / cascade 路径
2. **删除 `auto` 死值** —— 清理 validValues 中无实现的取值
3. **enum 显式校验** —— 非法字符串抛 argerr，不再"任意非 Chain 都按 single"
4. **修复死代码 warn** —— 真正生效的 null 兜底分支
5. **内部 msg 默认值与 API 对齐** —— cascade 路径与 API 路径行为一致
6. **API 描述明确雪崩删语义** —— 让用户一眼看清"chain = 子树删"

---

## 4. 详细方案

### 4.1 入参定义改写

```java
// APIDeleteVolumeSnapshotMsg.java
@APIParam(required = false, validValues = {"single", "chain"},
          description = "chain (默认) = 删除该节点及其所有子孙节点（子树删）；" +
                        "single = 仅删除该节点本身，子孙节点 merge 到 parent")
private String scope = "chain";

@APIParam(required = false, validValues = {"pull", "commit", "auto"},
          description = "auto (默认) = 按 VM 状态与链路结构自适应选择 commit 或 pull")
private String direction = "auto";
```

`APIDeleteVolumeSnapshotGroupMsg.java` 同步改写（参数定义相同）。

变化点：
- `scope` validValues 移除 `"auto"`
- `description` 写清 chain 是雪崩删
- `direction` validValues 不变（`auto` 是真实实现，与 scope 的死值不同）

### 4.2 后端处理改写

```java
// VolumeSnapshotTreeBase.handleDeletionMsg 行 473 附近
DeleteVolumeSnapshotScope parsedScope;
if (msg.getScope() == null) {
    parsedScope = DeleteVolumeSnapshotScope.Chain;
    logger.warn(String.format(
        "snapshot[uuid=%s] deletion scope is null, default to Chain (subtree delete)",
        msg.getSnapshotUuid()));
} else {
    try {
        parsedScope = DeleteVolumeSnapshotScope.valueOf(StringUtils.capitalize(msg.getScope()));
    } catch (IllegalArgumentException e) {
        throw new OperationFailureException(argerr(
            "invalid scope[%s], expect one of: single, chain", msg.getScope()));
    }
}

if (parsedScope == DeleteVolumeSnapshotScope.Chain) {
    long size = 0;
    for (VolumeSnapshotInventory inv : currentLeaf.getDescendants()) {
        if (inv.isLatest()) ancestorOfLatest = true;
        size += inv.getSize();
    }
    requiredSize = Math.min(size, volume.getSize());
    deleteChainFlows();
} else {
    deleteSingleFlows();
}
```

修复点：
- **S1 / S3**：enum 校验，非法字符串（含老 `auto`、拼错、大小写漂移）被 argerr 拦截
- **S2**：死代码 warn 挪到真正的 null 兜底分支
- **隐式分支风险**：`else` 不再"凡是非 chain 都按 single"，仅 `Single` enum 值进 single 路径（这里 enum 二选一，等价于显式 switch；如未来加第三种值需改 switch）

### 4.3 内部 msg 默认值同步

```java
// header/.../DeleteVolumeSnapshotMsg.java
private String direction = "auto";                                  // 修 D1
private String scope     = DeleteVolumeSnapshotScope.Chain.toString(); // 与 API 默认对齐

// header/.../VolumeSnapshotDeletionMsg.java
private String direction = "auto";
private String scope     = DeleteVolumeSnapshotScope.Chain.toString();

// header/.../group/DeleteVolumeSnapshotGroupInnerMsg.java
private String direction = "auto";
private String scope     = DeleteVolumeSnapshotScope.Chain.toString();
```

效果：cascade、group split、定时清理任何路径若不显式 set，行为与 API 默认一致（chain + auto），不再退化为 Commit（Bug 9 闭环）。

### 4.4 `direction=null` 兜底（修 Bug 2）

`VolumeTree.resolveDirection` 第一行：

```java
// 修改前
if (initial == null) {
    return VolumeSnapshotDeletionDirection.Commit;
}

// 修改后
if (initial == null) {
    initial = VolumeSnapshotDeletionDirection.Auto.toString();   // 与 API 默认一致
}
```

修了内部 msg 默认值后这条仍是双保险：万一某条 cascade 路径用旧 builder 不带默认值构造 msg，仍能在 resolveDirection 入口兜住。

---

## 5. Group API 单独评估

`APIDeleteVolumeSnapshotGroupMsg` 的 scope 透传给每盘 single msg：

```java
// VolumeSnapshotGroupBase.handle(APIDeleteVolumeSnapshotGroupMsg)
imsg.setScope(msg.getScope());          // 行 192 / 227
imsg.setDirection(msg.getDirection());
```

Group + chain 默认风险：**多盘 × 子树**，单次 API 可删几十个 snapshot，回滚成本极高。

| 选项 | 描述 | 推荐度 | 兼容性 |
|---|---|---|---|
| A. Group 保留默认 `chain` | 与单盘一致 + 与老脚本兼容；UI/文档单独警示风险 | ⭐⭐⭐ | ✅ 完全兼容 |
| B. Group 默认改 `single` | 与单盘默认拉开，强调"按盘点删" | ⭐⭐⭐ | ⚠ 老脚本行为变化 |
| C. Group `required = true` | 强制用户显式选择 | ⭐⭐⭐⭐ 最安全 | ⚠ 老脚本断 |

**推荐 A**（最小改动）：UI 层 + 文档显著警示，老脚本不动。如果业务上确认"快照组 = 一致性快照集，几乎无人对它做子树删"，再走 C 在下个大版本下线默认值。

---

## 6. 兼容矩阵

| 调用方传参 | 旧行为 | 新行为 | 兼容 |
|---|---|---|---|
| 不传 `scope` | chain | chain（默认） | ✅ 等价 |
| `scope=chain` | chain | chain | ✅ 等价 |
| `scope=Chain` | chain（大写恰好等于 enum.toString） | chain（normalize） | ✅ 等价 |
| `scope=CHAIN` | 走 single（字符串非精确 "Chain"） | chain（normalize） | ⚠ 行为变化但更合理 |
| `scope=single` | single | single | ✅ 等价 |
| `scope=auto` | 走 single（字符串非 "Chain"） | argerr 拒绝 | ⚠ **break** |
| `scope=garbage` | 走 single | argerr 拒绝 | ⚠ **break** |
| 不传 `direction` | auto（API 层默认） | auto | ✅ 等价 |
| 内部 msg 不 set `direction` | null → resolveDirection 返回 Commit | auto → 按 vmState/链路 | ⚠ **行为变化**（更合理） |
| 内部 msg 不 set `scope` | null → 走 single 分支（"非 Chain"） | chain（默认） → 走 chain 分支 | ⚠ **行为变化**（与 API 默认对齐） |

### break 项处理

1. **`scope=auto` break**：当前实际行为是"伪装成智能、其实落 single"，调用方若依赖此行为本身就是 bug 用法。可选过渡：保留 `auto` 在 validValues 一个版本，内部 alias 到 chain（或 single，按实际调用方调研结果决定），warn `"scope=auto is deprecated"`。
2. **`scope=garbage` break**：原本静默 single，新行为 argerr。这是**好的 break** —— 之前的隐藏 bug 暴露出来。
3. **内部 msg 默认行为变化**：cascade / group split 路径如果原本依赖 "null=Commit / null=single" 隐含语义，会变。需要全量审查内部 msg 的所有调用点：

```bash
# 搜索 cascade 路径
rg "new DeleteVolumeSnapshotMsg\(\)" -l
rg "new VolumeSnapshotDeletionMsg\(\)" -l
```

凡是不显式 setDirection / setScope 的，确认是否需要保留旧的隐含语义；如需要，应在该路径显式 setDirection("commit") / setScope("single") 而不是依赖默认。

---

## 7. 改动清单

| # | 文件 | 改动 |
|---|---|---|
| 1 | `header/src/main/java/org/zstack/header/storage/snapshot/APIDeleteVolumeSnapshotMsg.java` | `scope` validValues 移除 `auto`；description 写清子树语义 |
| 2 | `header/src/main/java/org/zstack/header/storage/snapshot/group/APIDeleteVolumeSnapshotGroupMsg.java` | 同 1 |
| 3 | `header/src/main/java/org/zstack/header/storage/snapshot/DeleteVolumeSnapshotMsg.java` | `direction = "auto"`、`scope = "Chain"` 默认 |
| 4 | `header/src/main/java/org/zstack/header/storage/snapshot/VolumeSnapshotDeletionMsg.java` | 同 3 |
| 5 | `header/src/main/java/org/zstack/header/storage/snapshot/group/DeleteVolumeSnapshotGroupInnerMsg.java` | 同 3 |
| 6 | `storage/src/main/java/org/zstack/storage/snapshot/VolumeSnapshotTreeBase.java` 行 473 | enum normalize + 死代码 warn 修复 + argerr |
| 7 | `storage/src/main/java/org/zstack/storage/snapshot/VolumeTree.java` `resolveDirection` | `null → Auto` 兜底 |
| 8 | API 文档 / changelog | 兼容矩阵公告；UI 建议默认选 single；Group 警示 |
| 9 | 调用点审查 | `rg "new DeleteVolumeSnapshotMsg"` 验证内部 msg 默认变化的影响 |

---

## 8. 测试要点

| 测试场景 | 预期 |
|---|---|
| API 不传 scope → 走 chain（兼容老行为） | ✅ |
| API 传 `scope=chain` 删多分支节点 | 子树全删 |
| API 传 `scope=single` 删多分支节点 | 仅该节点删，子孙 merge |
| API 传 `scope=auto` | argerr，不再静默走 single |
| API 传 `scope=GARBAGE` | argerr |
| API 不传 direction → resolveDirection 走 Auto 分支 | ✅ |
| Cascade 路径（删 vm/volume 联动）→ 内部 msg 走 chain + auto | 与 API 一致 |
| Group API 不传 scope → 多盘均走 chain | 兼容老行为 |
| 老脚本传 `scope=Chain`（首字母大写）| 走 chain（兼容） |
| `scope=CHAIN`（全大写）| 走 chain（normalize 后） |

---

## 9. 与 bugs.md 的对应

本提案落地后，bugs.md 中的修复进度更新：

| Bug | 当前状态 | 提案落地后 |
|---|---|---|
| Bug 2 (`direction=null → Commit`) | ⚠ 待修复 | ✅ resolveDirection 兜底为 Auto |
| Bug 8 (`scope` validValues / 死代码 / 默认风险) | ⚠ 待修复（P0） | ✅ enum normalize + argerr + 死代码修复 |
| Bug 9 (内部 msg `direction` 无默认) | ⚠ 待修复 | ✅ 三个内部 msg 同步默认 `auto` + `chain` |

---

## 10. 风险与决策点

| 决策点 | 选项 | 备注 |
|---|---|---|
| `scope=auto` 是否保留过渡期 | 直接 break / 一版本 deprecated | 取决于调用方调研：有无脚本真传 auto |
| Group 默认是否改 `single` | A 保持兼容 / B 改 single / C required | 推荐 A，激进可走 C |
| 内部 msg 默认值变化是否需要 cascade 路径全审 | 是 | 必须全量 grep `new DeleteVolumeSnapshotMsg()` 确认无依赖 null 行为 |
| `chain` 是否重命名 `subtree` | 保留 / 重命名 + alias | 已决定保留：term 历史包袱 + 重命名收益小 |
