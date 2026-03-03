> **导航** | [核心设计](../vm-metadata-01-design.md) | [持久化更新机制（新方案）](../vm-metadata-02-dirty-mark.md) | [GC 对比](vm-metadata-new-02h-compare.md)
>
> **技术债务分析**

# VM 元数据 Dirty Mark + Poller 方案：技术债务深度分析

本文对 Dirty Mark + Poller 方案的代码实现进行逐层审计，识别当前存在或潜在的技术债务，并评估风险等级和修复建议。

---

## 评估标准

| 等级 | 含义 |
|------|------|
| **P0 — 正确性** | 可能导致数据不一致或功能错误 |
| **P1 — 设计缺陷** | 架构层面的遗漏，影响功能完整性或可扩展性 |
| **P2 — 运维隐患** | 不影响功能，但增加运维难度或隐含性能隐患 |
| **P3 — 代码卫生** | 代码风格、可维护性问题，无功能影响 |

---

## P0 — 正确性问题

### 0.1 `buildVmInstanceMetadata()` 缺少 `@Transactional(readOnly=true)`

**现状**：`VmInstanceBase.buildVmInstanceMetadata()` 执行 6+ 条 SELECT 查询（VmInstanceVO、VolumeVO×2、VmNicVO、VolumeSnapshotVO、VolumeSnapshotGroupVO、VolumeSnapshotGroupRefVO），每条 SELECT 是独立的自动提交事务。

**风险**：MySQL InnoDB 默认 REPEATABLE READ 隔离级别下，多条 SELECT 在不同事务快照上执行，可能读到不一致的中间状态：

```
T1: 查 VolumeVO → 返回 [vol-A, vol-B]
T2: 另一个 API 删除了 vol-B 的快照
T3: 查 VolumeSnapshotVO WHERE volumeUuid IN (vol-A, vol-B) → 返回 vol-A 的快照（vol-B 的已删除）
```

最终元数据中 vol-B 存在但其快照不存在——数据自相矛盾。

**设计文档要求**：§1.3 明确写道 *"`buildVmInstanceMetadata()` 必须标注 `@Transactional(readOnly=true)`"*。

**修复**：
```java
@Transactional(readOnly = true)
private String buildVmInstanceMetadata(String vmInstanceUuid) { ... }
```
> 注意：`private` 方法上的 Spring `@Transactional` 不生效（需 AOP 代理）。应将此方法提取到单独的 `@Component` Service 中，或改为 `public` 方法并通过自注入调用。这是 ZStack 中常见的事务管理 pattern。

**风险等级**：**P0 — 高**。时间窗口虽小（毫秒级），但在高并发 API 下问题可复现。

---

### 0.2 `claimDirtyRows()` Step2 查询不精确 — 可能重新提交已在处理的行

**现状**：`claimDirtyRows()` 分两步执行：
1. CAS UPDATE 认领一批 unclaimed 行
2. SELECT WHERE `managementNodeUuid = myId` → 查询 "我认领的所有行"

Step2 不仅返回本轮新认领的行，**还返回之前认领但仍在 ChainTask pipeline 中处理的行**。

**影响链**：
```
T0: Poller 认领 vm-A → submitFlushTask → 进入 ChainTask 队列 (running)
T5: Poller 再次运行 → claimDirtyRows():
    Step1: 认领 vm-B (新行)
    Step2: SELECT WHERE myId → 返回 [vm-A, vm-B]
    → submitFlushTask(vm-A) 和 submitFlushTask(vm-B)
    → vm-A 的 per-VM ChainTask 已有 running+pending → exceedMaxPendingCallback
    → releaseClaim(vm-A) → 设置 managementNodeUuid=NULL
```

**问题**：`releaseClaim` 将正在被当前 MN 处理的 vm-A 的认领释放，使其变为 unclaimed。此时另一个 MN 可以认领 vm-A 并启动并发刷写。虽然 per-VM ChainTask 在每个 MN 内部保证串行，但跨 MN 的并发刷写不受 Layer 2 保护。

**实际影响**：低。两个 MN 同时刷写同一 VM 的元数据，最终结果仍是最新全量 payload（幂等写入）。但会浪费资源且可能导致 Agent 端竞争。

**修复方案 A — 精确查询**：CAS UPDATE 后用 LAST_INSERT_ID 技巧或用 RETURNING 子句（MySQL 8.0+）获取被更新的行。ZStack 用 MySQL 5.7，可改为：
```java
// 先标记本轮认领的行（使用临时标记字段或 retryCount 上的 trick）
// 或用更精确的 SELECT:
return Q.New(VmMetadataDirtyVO.class)
    .eq(VmMetadataDirtyVO_.managementNodeUuid, myId)
    .isNull(VmMetadataDirtyVO_.nextRetryTime)  // 新认领的行 nextRetryTime 为 null
    .list();
```
> 注意：这个 workaround 不完美（首次 markDirty 的行 nextRetryTime 也是 null）。

**修复方案 B — 认领时记录认领批次**：在 CAS UPDATE 中设置一个 `claimBatch` 标记（如当前时间戳），后续 SELECT 按此标记过滤。

**修复方案 C — 不修复，接受现状**：ChainTask 的 deduplication 已兜底。exceedMaxPendingCallback 中的 releaseClaim 改为 **不释放**（因为有可能正在处理）或改为"仅在 managementNodeUuid == myId 时释放"。

**风险等级**：**P0 — 中**。存在跨 MN 并发刷写窗口，但幂等写入保证最终一致。

---

### 0.3 `updateOnFailure` 注解属性未实现

**现状**：`@MetadataImpact` 注解声明了 `boolean updateOnFailure() default false;`，设计上用于标记"API 失败时也需要更新元数据"的场景。

当前 `VmMetadataUpdateInterceptor` 的实现：
```java
if (apiEvent.getError() != null) {
    return;  // 直接跳过，不检查 updateOnFailure
}
```

**影响**：如果某个 API（如部分成功的批量操作）在失败时也会修改 DB 状态，则元数据不会被更新，导致存储上的元数据与 DB 不一致。

**修复**：
```java
MetadataImpact impact = pendingApiImpacts.get(apiEvent.getApiId());
if (apiEvent.getError() != null && !impact.updateOnFailure()) {
    return;
}
```
> 需要在 pendingApis 中额外缓存 annotation 信息。

**风险等级**：**P0 — 中**。取决于是否有 API 实际设置了 `updateOnFailure=true`。当前所有 API 使用默认值 `false`，故暂无实际影响。

---

## P1 — 设计缺陷

### 1.1 `VmUuidResolver` 机制未使用 — 多 VM API 只提取单个 UUID

**现状**：`MetadataImpact` 注解定义了 `resolver()` 属性和多种 `VmUuidResolver` 实现（DefaultVmUuidResolver、VolumeBasedVmUuidResolver、ResourceBasedVmUuidResolver），支持从 API 消息中解析出**多个** vmUuid。

但 `VmMetadataUpdateInterceptor.extractVmInstanceUuid()` 使用反射调用 `getVmInstanceUuid()` / `getResourceUuid()`，只返回**单个** vmUuid。

**影响**：批量 API（如 `APICreateVolumesSnapshotMsg` 涉及多个 Volume、多个 VM）只能提取到一个 vmUuid，其他受影响的 VM 不会被 markDirty。

**修复**：Interceptor 应使用 `VmUuidResolverRegistry` 获取对应的 resolver，调用 `resolve()` 返回 `List<String>`，对每个 vmUuid 调用 `markDirty()`。

**风险等级**：**P1 — 高**。影响功能正确性，但仅限于批量 API 场景。

---

### 1.2 Payload 大小保护未实现

**现状**：设计文档 §10 明确要求：
- \> 8MB → WARN 日志
- \> 30MB → ERROR + 拒绝写入 + 释放认领

当前代码中 `doFlush()` 和 `VmInstanceBase.doHandleUpdateVmInstanceMetadata()` 均未做大小检查。

**影响**：VM 有成千上万快照时，元数据 payload 可能达到数十 MB，导致：
- sblk LV 空间不足（元数据 LV 默认 32MB）
- 网络传输超时
- Agent 内存压力

**修复**：在 `VmInstanceBase.doHandleUpdateVmInstanceMetadata()` 中，`buildVmInstanceMetadata()` 之后添加大小检查。

**风险等级**：**P1 — 中**。正常使用不会触发，但缺乏保护机制。

---

### 1.3 `storageStructureChange` OR 升级的粘性问题

**现状**：`markDirty()` 的 INSERT ON DUPLICATE KEY UPDATE 对 `storageStructureChange` 使用 `OR` 升级：
```sql
storageStructureChange = storageStructureChange OR VALUES(storageStructureChange)
```

一旦标记为 STORAGE（true），在整个 dirty 行生命周期内不会降级为 CONFIG（false），**即使 STORAGE 变更已被成功刷写**。

**场景**：
```
T0: STORAGE API → markDirty(vm, true) → storageStructureChange=true
T1: 认领, snapshotVersion=1, flush with OP type 2 → 成功
T2: CONFIG API → markDirty(vm, false) → storageStructureChange = true OR false = true
T3: version mismatch → release → 重新认领
T4: flush with OP type 2 — 但此时只有 CONFIG 变更！
```

T4 使用了不必要的 OP type 2，Agent 做了多余的存储拓扑处理。

**影响**：性能浪费，不影响正确性。OP type 2 是 OP type 1 的超集，多做不少做。

**修复**：在 `onFlushSuccess` 的 version mismatch 分支中，额外重置 `storageStructureChange=false`（已成功刷写的 STORAGE 变更不需要在下轮重复处理）。但这引入了新的竞态——重置和 markDirty 的 OR 更新可能冲突。需要用 CAS 方式处理。

**风险等级**：**P1 — 低**。仅性能影响，正确性不受影响。

---

## P2 — 运维隐患

### 2.1 无监控指标

**现状**：VmMetadataDirtyMarker 没有暴露任何运维指标：
- dirty 行总数、认领中行数
- 刷写成功/失败次数、延迟
- 退避中行数、达最大重试次数的事件计数
- Poller 执行耗时

**影响**：生产环境中难以判断元数据更新是否健康运行，排查问题依赖日志。

**修复**：引入 ZStack 内部的 Metrics 机制（如果有），或至少提供一个查询 API 返回当前 dirty table 统计。

---

### 2.2 Poller 在功能关闭时仍空转

**现状**：`vm.metadata.enabled=false` 时，`markDirty()` 会提前返回，不会产生 dirty 行。但 Poller 仍然每 5 秒执行 `claimAndFlush()` → 执行 SELECT → 返回 0 行。

**影响**：微小的 DB 开销（一次空 SELECT < 1ms），但在大量 MN 集群中可能有细微影响。

**修复**：在 `claimAndFlush()` 入口检查功能开关，或在 `managementNodeReady()` 中根据开关决定是否启动 Poller，并监听开关变更。

---

### 2.3 `nodeLeft` 在回调线程上执行 DB 操作

**现状**：`nodeLeft()` 直接调用 `claimAndFlush()`，其中包含 CAS UPDATE + SELECT + 多次 ChainTask 提交。

**影响**：如果 `nodeLeft` 是在管理节点心跳检测线程上被调用的，DB 操作可能阻塞心跳处理。

**修复**：
```java
@Override
public void nodeLeft(ManagementNodeInventory inv) {
    thdf.submitTask(() -> claimAndFlush());
}
```

---

### 2.4 指数退避常量硬编码

**现状**：`BASE_DELAY_SECONDS=10` 和 `MAX_EXPONENT=10` 为编译时常量，不可通过 GlobalConfig 动态调整。

**影响**：如果退避策略不适合特定部署环境（如 PS 恢复缓慢需要更长退避），需要重新编译部署。

**修复**：将退避参数作为 GlobalConfig 暴露，或至少提供系统属性覆盖。

---

## P3 — 代码卫生

### 3.1 `VmMetadataUpdateInterceptor.submitMarkDirty()` 访问限定符

**现状**：`submitMarkDirty()` 从 `void` (package-private, 供 MetadataCascadeExtension 等调用) 改为 `private`。但 `MetadataCascadeExtension` 现在直接注入 `VmMetadataDirtyMarker` 调用 `markDirty()`，不再需要通过 Interceptor 中转。

**影响**：无。重构已完成，旧的 package-private 契约不再需要。

---

### 3.2 设计文档中的遗留 GC 注释

**现状**：部分代码注释仍引用 GC 概念：
- `VmMetadataCanonicalEvents.java` 可能存在过时的 GC 相关事件定义
- 部分 Javadoc 中可能残留 "GC" 字样

**影响**：无功能影响，但降低代码可读性。

---

### 3.3 `MetadataImpact.java` 中内部类的编译依赖

**现状**：`MetadataImpact` 是一个注解类，但其内部定义了多个 `VmUuidResolver` 实现类（`VolumeBasedVmUuidResolver`、`ResourceBasedVmUuidResolver`），这些类引用了 `SQL`、`VolumeVO`、`VolumeMessage` 等外部依赖。将 "实现类" 放在 "注解类" 内部是不寻常的做法。

**影响**：注解类的编译依赖过重，且内部类的 `@Autowired` 字段注入不会自动生效（内部类不是 Spring Bean）。

**修复**：将 Resolver 实现类移出注解类，单独作为 Spring Component 注册。

---

## 总结矩阵

| # | 问题 | 等级 | 状态 | 修复说明 |
|---|------|------|------|----------|
| 0.1 | buildVmInstanceMetadata 缺 @Transactional | P0 | ✅ 已修复 | 提取至 `VmMetadataBuilder` Spring Component，标注 `@Transactional(readOnly=true)` |
| 0.2 | claimDirtyRows Step2 查询不精确 | P0 | ✅ 已修复 | `exceedMaxPendingCallback()` 不再调用 `releaseClaim()`，避免释放正在处理的行 |
| 0.3 | updateOnFailure 未实现 | P0 | ✅ 已修复 | `MetadataImpactInfo` 存储 `updateOnFailure` 标志，`beforePublishEvent` 据此决定是否跳过 |
| 1.1 | VmUuidResolver 未使用 | P1 | ✅ 已修复 | 删除 compute `MetadataImpact`（含破损内部类），创建 4 个 `VmUuidFromApiResolver` 实现并注册为 Spring Bean，Interceptor 使用 Resolver 链解析 `List<String>` vmUuids |
| 1.2 | Payload 大小保护未实现 | P1 | ✅ 已修复 | `VmInstanceBase.doHandleUpdateVmInstanceMetadata()` 中 build 后检查 payload 大小，>8MB WARN / >30MB 拒绝 |
| 1.3 | storageStructureChange OR 粘性 | P1 | ✅ 已修复 | `onFlushSuccess` version mismatch 分支重置 `storageStructureChange=false` |
| 2.1 | 无监控指标 | P2 | ⏸ 延后 | 计划下一迭代引入 Metrics |
| 2.2 | Poller 功能关闭时空转 | P2 | ✅ 已修复 | `claimAndFlush()` 入口检查 `VmGlobalConfig.VM_METADATA` 开关 |
| 2.3 | nodeLeft 在回调线程执行 DB | P2 | ✅ 已修复 | `nodeLeft()` 通过 `thdf.submit(Task)` 异步执行 `claimAndFlush()` |
| 2.4 | 退避常量硬编码 | P2 | ⏸ 延后 | 视需求通过 GlobalConfig 暴露 |
| 3.1 | submitMarkDirty 访问限定符 | P3 | ✅ 已修复 | 重构后为 package-private，仅供同包使用 |
| 3.2 | 遗留 GC 注释 | P3 | ✅ 已修复 | 代码文件中已无遗留 GC 注释 |
| 3.3 | MetadataImpact 内部类设计 | P3 | ✅ 已修复 | 删除 compute `MetadataImpact.java`/`MetadataImpactLevel.java`，Resolver 作为独立 Spring Bean |

> **本轮修复 11/13 项**。剩余 2 项（2.1 监控指标、2.4 退避常量）为低优先级，计划后续迭代处理。
>
> **附加修复**：`VmInstanceMetadataDTO.ResourceMetadata` 的 `systemTags`/`resourceConfigs` 字段类型从 `List<String>` 修正为 `String`（Base64 编码字符串）。
