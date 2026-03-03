> **导航** | [核心设计](../vm-metadata-01-design.md) | [持久化更新机制（新方案）](../vm-metadata-02-dirty-mark.md) | [注册与运维](../vm-metadata-03-registration.md) | [sblk 二进制协议](../vm-metadata-04-sblk.md) | [API 设计](../vm-metadata-05-api.md)
>
> **新方案子文档** | [概述](../vm-metadata-02-dirty-mark.md) | **对比与约束**

# Dirty Mark + Poller vs GC 方案对比

本文从数据模型、标脏入口、消息链、并发控制、双 MN HA 五个维度，逐项对比新旧两个方案的差异。

| 属性 | 值 |
|------|-----|
| 文档版本 | 1.0 |
| 最后更新 | 2026-03-03 |

---

## 1. 数据模型对比：VmMetadataDirtyVO vs GarbageCollectorVO

| | GarbageCollectorVO | VmMetadataDirtyVO |
|---|---|---|
| 主键 | uuid（自动生成） | vmInstanceUuid（业务键） |
| 一个 VM 的行数 | 0~N（每次 submit 一行） | 0~1（主键去重） |
| context | JSON blob（@GC 字段序列化） | 无需（全量从 DB 构建） |
| status | Idle / Processing / Done | 无需（行存在=脏，删除=完成） |
| runnerClass | GC 子类全限定名 | 无需（固定逻辑） |
| 清理需求 | 24h 定期清理 Done 记录 | 无需（成功即删除） |

---

## 2. 标脏入口对比：markDirty vs GC submit

| | GC submit() | markDirty() |
|---|---|---|
| DB 操作 | INSERT 新行 | INSERT or UPDATE lastOpDate |
| 100 次 API | 100 行 GC | 1 行 Dirty |
| 去重 | 无法 deduplicateSubmit | 主键天然去重 |
| 跨 MN 路由 | 需 SubmitGCMsg 路由到 owner | 无需路由，本地 INSERT |
| 功能开关检查 | Interceptor 层检查 | markDirty 内统一检查 |
| 触发延迟 | GC timer 首次 10s | 立即唤醒（triggerFlushForVm）+ Poller 安全网 |

---

## 3. 消息调用链对比

| | GC 方案 | 新方案 |
|---|---|---|
| 跳数 | 5 跳（Interceptor → SubmitGCMsg → GC.triggerNow → UpdateMsg → OnPSMsg → OnHypervisorMsg） | 4 跳（markDirty → Poller → UpdateMsg → OnPSMsg → OnHypervisorMsg） |
| 跨 MN 消息 | SubmitGCMsg 需 hash 环路由 | **无**（markDirty 本地 INSERT，Poller 本地执行） |
| 触发延迟 | GC timer（首次 10s） | 立即唤醒（markDirty → triggerFlush）|

**去掉了 SubmitGCMsg 这一跳**——这是双 MN 复杂度的根源。不再需要路由、不再需要 reply 回退、不再需要 delegation。

---

## 4. 并发控制对比

| | GC 方案（四层） | 新方案（三层） |
|---|---|---|
| Layer 0 | Owner 归集（hash 环 isManagedByUs + delegation） | **移除**（DB CAS 认领天然解决） |
| Layer 1 | GC lockJob CAS (AtomicBoolean) | DB CAS 认领 |
| Layer 2 | per-VM ChainTask | per-VM ChainTask（不变） |
| Layer 3 | per-PS ChainTask | per-PS ChainTask（不变） |

---

## 5. 双 MN HA：GC 方案六种极端情况的逐一对照

对应 GC 方案 §5.6 分析的六种极端情况：

| # | GC 方案的极端情况 | 新方案情况 |
|---|---|---|
| 1 | delegation 消耗 retryCount | **不存在**。无 delegation，retryCount 仅在实际刷写失败时递增 |
| 2 | delegate 后 owner 宕机 | **不存在**。无 delegation。MN 宕机 → FK SET_NULL → 另一 MN 认领 |
| 3 | delegation 循环（hash 环反复变化） | **不存在**。无 delegation，无 hash 环依赖 |
| 4 | send-callback 间 MN 崩溃 | **改善**。markDirty 是本地 INSERT（已持久化），即使 Poller 在 send 后崩溃，FK SET_NULL 释放后另一 MN 接管。唯一丢失窗口缩小到"INSERT 执行到 MySQL commit 之间"（微秒级） |
| 5 | 大量 GC 同时 delegate | **不存在**。100 个 API → 1 行 dirty。Poller 认领 1 行 → ChainTask 执行 1 次。无 delegate 风暴 |
| 6 | makeDestination 返回自己 | **不存在**。无 hash 环依赖 |

**结论**：GC 方案需要分析的 6 种极端情况，新方案中 **5 种压根不存在，1 种得到改善**。

---

## 6. 完整维度对比

| 维度 | GC 方案 | Dirty Mark 方案 |
|------|---------|-----------------|
| **DB 模型** | 一个任务一行 GC 记录 | 一个 VM 最多一行 dirty 标记 |
| **100 次 API 的 DB 行数** | 100 行（98 行立即 Done） | 1 行 |
| **去重能力** | deduplicateSubmit 不可用 | 主键天然去重 |
| **定时清理** | 需要 cleanUpCompletedJobs（24h） | 不需要（成功即删除） |
| **跨 MN 消息** | SubmitGCMsg hash 环路由 | 无（全部本地操作） |
| **双 MN 协调** | hash 环路由 + delegation + reply 回退 | DB CAS 认领（零协调） |
| **极端情况** | 6 种需分析 | 5 种不存在 + 1 种改善 |
| **MN 宕机接管延迟** | ~90s（或 nodeLeft 优化到 ~31s） | ~30s（nodeLeft 立即触发） |
| **MN 宕机处理代码** | 需 loadOrphanJobs + loadFromVO + setupTimer | nodeLeft → claimAndFlush()（一行） |
| **框架修改** | 需修改 GC 框架（loadOrphanJobs 状态过滤、索引） | 不需要修改任何框架 |
| **GC 行清理** | 24h 定期清理 Done 记录 | 不需要 |
| **并发控制层数** | 4 层 | 3 层 |
| **触发延迟** | GC timer 首次 10s | 立即唤醒 + Poller 安全网 |
| **retryCount 准确性** | delegation 失败也消耗 | 仅实际刷写失败消耗 |
| **代码复杂度** | 高（GC 子类 + Interceptor 路由 + delegation） | 低（1 VO + 1 PeriodicTask + markDirty） |
| **新增类** | UpdateVmInstanceMetadataGC + 修改 GC 框架 | VmMetadataDirtyVO + MetadataDirtyPoller |
| **触发统一性** | API 触发 vs 巡检触发逻辑不同 | 统一 markDirty() |
| **升级全量刷新** | LongJob + 逐个 submitGC | 批量 markDirty，Poller 自动处理 |
