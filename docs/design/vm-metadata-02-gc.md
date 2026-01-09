> **导航** | [核心设计](vm-metadata-01-design.md) | **GC 与消息流** | [注册与运维](vm-metadata-03-registration.md) | [sblk 二进制协议](vm-metadata-04-sblk.md) | [API 设计](vm-metadata-05-api.md)

# 虚拟机元数据设计文档 —— Part 2: GC 与消息流

| 属性 | 值 |
|------|-----|
| 文档版本 | 2.0 |
| 最后更新 | 2026-03-02 |
| 状态 | 设计中 |

**修订记录**

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| 1.0 | 2026-02 | 初始版本，方案 B / 方案 C 分离描述 |
| 2.0 | 2026-03-02 | 重构：整合双 MN 方案、重试上限改为 GlobalConfig（默认 5）、按 PRD 格式重排 |

---

## 目录

1. [概述](#1-概述)
2. [消息调用链与路由](#2-消息调用链与路由)
3. [并发控制](#3-并发控制)
4. [GC 策略](#4-gc-策略)
5. [双 MN 高可用](#5-双-mn-高可用)
6. [管理平面恢复策略](#6-管理平面恢复策略)
7. [升级后全量刷新](#7-升级后全量刷新)
8. [Payload 大小保护](#8-payload-大小保护)
9. [开发约束清单](#9-开发约束清单)
10. [附录](#附录)

---

## 1. 概述

虚拟机元数据持久化到主存储后，任何影响元数据的 API 操作成功后，都需要将最新元数据同步更新到主存储。由于主存储写入可能失败（存储不可用、agent 故障等），引入 GC（GarbageCollector）机制进行可靠重试。

**核心不变量**

- GC handler 始终从 DB 查询 VM 的完整当前状态来构建 payload，不使用触发 API 时的增量数据。
- 任何一个 GC 执行后，元数据都反映数据库的最新完整状态。
- `buildVmInstanceMetadata()` 必须标注 `@Transactional(readOnly = true)`，MySQL InnoDB REPEATABLE READ 事务内所有查询使用同一快照，保证单次构建的读一致性。`readOnly = true` 不启动写事务，开销极小。

**最终一致性模型**

`buildVmInstanceMetadata()` 读 DB 到 `pwrite` 完成之间存在毫秒级窗口，期间其他 API 可能修改了 DB（如删除快照）。此时写入的元数据可能包含已过期信息。这不是问题——修改 DB 的 API 成功后会提交新 GC，新 GC 从 DB 全量读取时已反映最新状态，覆盖写入自然修正。即使新 GC 被 `exceedMaxPendingCallback` 丢弃，pending GC 的执行时间必然晚于被丢弃 GC 对应的 API commit 时间，因此 pending GC 读到的一定是最新状态。对注册场景，Part 3 §3.4 的 installPath 存在性检查提供额外兜底。

---

## 2. 消息调用链与路由

### 2.1 完整调用链

```
API (e.g. StartVmInstanceMsg) 成功
  ↓
VmMetadataUpdateInterceptor.beforePublishEvent()
  ↓
submitUpdateVmInstanceMetadataGC(vmUuid)
  ↓ SubmitTimeBasedGarbageCollectorMsg → hash 环路由到 owner MN
  ↓ （reply 失败 → 本地 submit 回退，见 §5.7.1）
  ↓
GarbageCollectorManagerImpl.handle(SubmitTimeBasedGarbageCollectorMsg)
  → gc.submit() → saveToDb() + setupTimer()
  ↓ timer 到期
  ↓
UpdateVmInstanceMetadataGC.triggerNow()
  ├─ 非 owner → delegateToOwner()（执行层归集，见 §5.7.2）
  └─ 是 owner ↓
  ↓ 外层 ChainTask "update-vm-metadata-global" (syncLevel=N)
  ↓ 内层 ChainTask "update-vm-{vmUuid}-metadata" (maxPending=1)
  ↓
bus.send(UpdateVmInstanceMetadataMsg) → makeLocalServiceId（本地处理）
  ↓
VmInstanceBase.handle(UpdateVmInstanceMetadataMsg)
  ↓ buildVmInstanceMetadata(vmUuid) — 从 DB 全量读取（核心不变量）
  ↓
bus.send(UpdateVmInstanceMetadataOnPrimaryStorageMsg) → makeLocalServiceId
  ↓
NFS/LocalStorage.handle()
  ↓ Layer 3 ChainTask "update-metadata-on-ps-{psUuid}"
  ↓ 查询 hostUuid → 转发 UpdateVmInstanceMetadataOnHypervisorMsg
  ↓ makeTargetServiceIdByResourceUuid(hostUuid)  ← 保留 hash 环路由
SharedBlockPrimaryStorage.handle()
  ↓ 选取可访问该 VG 的任意 Connected Host
  ↓ 转发 UpdateVmInstanceMetadataOnHypervisorMsg
  ↓ makeTargetServiceIdByResourceUuid(hostUuid)  ← 保留 hash 环路由
  ↓
HostBase.handle() → HTTP call to KVM agent
  ↓
成功 → bus.reply → GC.success() → Done
失败 → bus.reply → GC.onUpdateFail() → 指数退避 → setupTimer()
```

> **SubmitGCMsg 不携带 opType**：OP type 由 Host Agent 端写入时动态决定（对比新旧 payload 的存储拓扑差异），管理层面无需指定。详见 [Part 4 §5.2](vm-metadata-04-sblk.md#52-完整流程)。

> **消息超时设置**：`UpdateVmInstanceMetadataMsg` 和 `UpdateVmInstanceMetadataOnHypervisorMsg` 均设置 `msg.setTimeout(TimeUnit.MINUTES.toMillis(2))`（2 分钟），防止大 payload（10MB+）的 O_DIRECT 写入 + 可能的 lvextend 操作超出默认消息超时。

### 2.2 消息路由策略

| 消息 | 路由方式 | 原因 |
|------|----------|------|
| `SubmitTimeBasedGarbageCollectorMsg` | `makeTargetServiceIdByResourceUuid(vmUuid)` | hash 环归集，GC 提交到 owner MN |
| `UpdateVmInstanceMetadataMsg` | **`makeLocalServiceId`** | GC 已在 owner MN 上，本地处理即可 |
| `UpdateVmInstanceMetadataOnPrimaryStorageMsg` | **`makeLocalServiceId`** | PS handler 无本地状态依赖，避免跨 MN 开销 |
| `UpdateVmInstanceMetadataOnHypervisorMsg` | `makeTargetServiceIdByResourceUuid(hostUuid)` | 需路由到 host-owner MN 发 HTTP 请求 |

前两跳消息从"可能跨 MN"变为"确定性本地"，减少最多 2 次跨 MN 消息传递。第三跳保留 hash 环路由到正确的 host-owner MN。

---

## 3. 并发控制

### 3.1 四层串行化保证

```
Layer 0 — Owner 归集（执行层归集）
    triggerNow() 检查 isManagedByUs，非 owner 通过 delegate 转移到 owner MN
    ⇒ 同一 VM 的 GC 执行归集到一个 MN

Layer 1 — GC 框架
    同一 GC 实例的 runTrigger() 通过 lockJob CAS 防止并发
    ⇒ 单个 GC 不会被并行触发

Layer 2 — ChainTask 队列 "update-vm-{vmUuid}-metadata"
    syncLevel=1, maxPendingTasks=1
    ⇒ 同一 VM 最多 1 个正在执行 + 1 个排队
    ⇒ 超出时 exceedMaxPendingCallback() 直接标记 GC Done
      （队列中/执行中的 GC 将查询 DB 获取包含本次变更的最新状态）

Layer 3 — 主存储级队列 "update-metadata-on-ps-{psUuid}"
    syncLevel = vm.metadata.ps.maxConcurrent (GlobalConfig, 默认 5)
    ⇒ 同一 MN 上，同一存储最多 N 个并发写入
    ⇒ 双 MN 环境下实际全局并发 = 2 × syncLevel
```

**Layer 3 实现位置**：各主存储 `handle(UpdateVmInstanceMetadataOnPrimaryStorageMsg)` 内部用 `thdf.chainSubmit()` 包装，关键覆写：

- `getSyncSignature()` → `"update-metadata-on-ps-" + self.getUuid()`
- `getSyncLevel()` → 读取 `VmGlobalConfig.VM_METADATA_PS_MAX_CONCURRENT`
- `run()` → 调用实际写入逻辑后 `chain.next()`

### 3.2 全局并发限流

同一时刻每个 MN 最多 N 个 VM 的元数据更新在执行（默认 10），通过嵌套 ChainTask 实现：

```
嵌套 ChainTask 结构（位于 UpdateVmInstanceMetadataGC.triggerNow() 内）：
  外层: syncSignature = "update-vm-metadata-global"
        syncLevel = vm.metadata.global.maxConcurrent (默认 10)
    内层: syncSignature = "update-vm-{vmUuid}-metadata"
          syncLevel = 1, maxPendingTasks = 1, deduplicateString = syncSignature
```

- 外层控制全局并发数，每个 MN 最多 N 个 VM 同时更新
- 内层保证 per-VM 串行 + 去重
- 两层都是 JVM 本地 ChainTask，无跨 MN 开销

> **per-MN 语义**：外层 `syncLevel` 是 JVM 本地限制。双 MN 环境下实际全局并发最大为 `2 × syncLevel`。§5 的哈希环归集已将同一 VM 的 GC 集中到一个 MN，因此同一 VM 不会在两个 MN 上同时执行。全局并发 2N 对存储层压力可控（Layer 3 per-PS 限流进一步约束）。

**外层全局队列与 Layer 3 的交互**：外层全局队列 `syncLevel=10` 限制单个 MN 上同时最多 10 个 VM 的元数据更新在执行。这 10 个并发任务分布在不同主存储上时，Layer 3 per-PS 队列 `syncLevel=5` 进一步约束同一存储的并发数。例如：单 MN 管理 3 个 PS，全局并发 10，每个 PS 最多 5 并发，则实际分配取决于各 PS 上 VM 的 GC 提交顺序。外层全局队列控制的是"总水位"，Layer 3 控制的是"每个 PS 的分水位"，二者嵌套生效，不存在互相绕过的路径。

---

## 4. GC 策略

### 4.1 提交策略：submit 而非 deduplicateSubmit

**问题**

`deduplicateSubmit()` 内部调用 `existedAndNotCompleted()` 检查 DB 中是否存在同 NAME 且未完成的 GC。但 GC 从 timer 触发到执行完毕期间 status 始终为 `Idle`，新请求会被误判为"已有 GC 在处理"：

```
T0: API-1 成功 → DB metadata=v1 → GC-1 创建 (status=Idle)
T1: GC-1 timer 到期 → triggerNow() → 读 DB → 得到 v1 → 开始写 PS...
    此时 GC-1 status 仍为 Idle（只在 success/fail/cancel 时才变）
T2: API-2 成功 → DB metadata=v2 → deduplicateSubmit:
    existedAndNotCompleted() → 发现 GC-1 (Idle ≠ Done) → 跳过！
T3: GC-1 写入 v1 → success() → Done
    ⇒ PS 上是 v1，DB 上是 v2 → 元数据丢失！
```

**设计**

每次都 `submit()` 创建新 GC，通过 ChainTask `maxPendingTasks=1` 控制同一 VM 的执行扩散：

- 队列中最多 1 running + 1 pending
- pending GC 执行时从 DB 全量读取最新数据，覆盖写入
- 多余 GC 在 `exceedMaxPendingCallback` 中标记 `success()`（Done），表示"职责已被接管"

**triggerNow 执行逻辑**：

1. **前置检查**（按顺序短路返回）：
   - VM 不存在 → `completion.cancel()`
   - 非 owner → `delegateToOwner(completion)`（见 §5.7.2）
   - `retryCount >= VM_METADATA_GC_MAX_RETRY` → 发布 `MetadataStaleEvent` + `completion.success()`

2. **嵌套 ChainTask 提交**：
   - **外层**（全局限流）：`syncSignature = "update-vm-metadata-global"`，`syncLevel` 读取 `VM_METADATA_GLOBAL_MAX_CONCURRENT`
   - **内层**（per-VM 串行）：`syncSignature = "update-vm-{vmUuid}-metadata"`，`maxPendingTasks = 1`，`deduplicateString = syncSignature`
   - **exceedMaxPendingCallback**：直接 `completion.success()` + `outerChain.next()`（职责已被接管）

3. **内层 run()**：发送 `UpdateVmInstanceMetadataMsg`（`makeLocalServiceId`，`setTimeout(2min)`），成功则 `retryCount = 0` + `updateContext()` + `completion.success()`，失败则 `onUpdateFail()`。最后依次 `innerChain.next()` → `outerChain.next()`

### 4.2 Handler 端不创建新 GC

**问题**

若 `VmInstanceBase.handle(UpdateVmInstanceMetadataMsg)` 在写主存储失败时创建新 GC：

```java
if (!r.isSuccess()) {
    submitUpdateVmInstanceMetadataGC();  // ← 创建新 GC → 滚雪球
}
```

GC-1 失败 → 创建 GC-2 → GC-1 的 `fail()` 又触发 `setupTimer` → 两个 GC 并行重试 → 指数膨胀。

**设计**

Handler 失败时直接 `bus.reply(msg, errorReply)`，由 GC 端 `onUpdateFail()` 统一走指数退避重试。

### 4.3 GC NAME 统一格式

**问题**

不同代码路径使用不同格式的 NAME 导致去重失效：

```java
gc.NAME = String.format("gc-update-vm-%s-metadata", vmUuid);      // ❌ 格式 A
return String.format("update-vm-%s-metadata-gc", vmInstanceUuid);  // ✓ 格式 B
```

**设计**

所有调用方统一使用 `UpdateVmInstanceMetadataGC.getGCName(vmUuid)` 设置 NAME：

```java
public static String getGCName(String vmInstanceUuid) {
    return String.format("update-vm-%s-metadata-gc", vmInstanceUuid);
}
```

### 4.4 非 API 内部操作的元数据触发

`@MetadataImpact` 注解仅标注在 `APIMessage` 子类上，通过 `VmMetadataUpdateInterceptor` 自动触发 GC。以下不经过 API 拦截器的内部操作也会修改 VM 关联资源，需要手动触发 GC：

| 内部操作 | 影响 | 触发位置 |
|----------|------|----------|
| 级联删除 Volume/Snapshot | 存储拓扑变更 | `MetadataCascadeExtension.asyncCascade()` |
| HA 重启 VM | hostUuid/state 变化 | HA handler 完成回调 |
| 定时快照清理 | 快照删除 | Cleanup handler 完成回调 |
| 内部卷迁移（非 API 触发） | installPath 变更 | 迁移 handler 完成回调 |

**MetadataCascadeExtension** 监听 Volume 级联删除的 `DELETION_CLEANUP_CODE`，从 `VolumeDeletionStruct` 提取受影响的 `vmInstanceUuid`，调用 `interceptor.submitUpdateVmInstanceMetadataGC(uuid)` 触发 GC。

**两道防线**：

1. **开发规范**：修改 VM 存储拓扑字段的内部消息处理器，成功后必须调用 `submitUpdateVmInstanceMetadataGC()`
2. **健康巡检兜底**：周期巡检全量比对 DB vs 存储元数据，发现不一致则触发 full-refresh（见 §6）

> 对注册场景，即使元数据暂时落后于 DB（内部操作修改了 DB 但 GC 尚未完成），Part 3 §3.4 的 installPath 存在性检查提供额外兜底——引用不存在的 LV 会被拒绝注册。

### 4.5 指数退避与重试上限

每次更新失败后，`onUpdateFail()` 递增 `retryCount` 并以指数退避延迟重试。最大重试次数由 GlobalConfig `vm.metadata.gc.maxRetry` 控制（默认 5）：

| 尝试次数 | retryCount 变化 | 下次退避延迟 | 累计耗时 |
|----------|-----------------|-------------|----------|
| 1 | 0 → 1 | 20s | ~30s |
| 2 | 1 → 2 | 40s | ~70s |
| 3 | 2 → 3 | 80s | ~150s |
| 4 | 3 → 4 | 160s | ~310s |
| 5 | 4 → 5 | 320s | ~630s |
| — | ≥ maxRetry | **放弃** | — |

> **延迟公式**：`BASE_DELAY_SECONDS × 2^min(retryCount, MAX_EXPONENT)`  
> 其中 `BASE_DELAY_SECONDS = 10`，`MAX_EXPONENT = 10`（防止左移溢出）。  
> 默认 5 次重试，总耗时约 10 分钟后放弃。

**关键设计要点**

- `retryCount` 标记 `@GC`，序列化到 `GarbageCollectorVO.context` JSON
- 每次失败通过 `updateContext()` 持久化 `retryCount` + `NEXT_TIME`
- MN 重启后孤儿加载通过 `loadFromVO()` 恢复，不会重置为 0
- 超过最大重试次数后标记 Done，发布 `MetadataStaleEvent`

**onUpdateFail 逻辑**：`retryCount++` → 计算 `NEXT_TIME = BASE_DELAY_SECONDS × 2^min(retryCount, MAX_EXPONENT)` → WARN 日志 → `updateContext()`（持久化到 GC context JSON）→ `completion.fail(err)`（父类 fail → Idle + setupTimer）。

### 4.6 MetadataStaleEvent 恢复流程

GC 放弃后发布的 `MetadataStaleEvent`（path = `/vm/metadata/stale`）由 `MetadataHealthCheckJob` 监听并处理：

**处理链路**

1. 将 vmUuid 加入"优先刷新队列"（内存队列 + 持久化到 ResourceConfig）
2. 立即提交延迟 5 分钟的定时任务，到期后提交新 GC（retryCount=0 的全新尝试），不等待下次 24 小时健康巡检
3. 若仍失败 → retryCount 递增 → 达到上限后再次放弃 → 再次 MetadataStaleEvent

**cycle 限制**防止无限循环：

引入 `vm.metadata.maxStaleRecoveryCycles` GlobalConfig（默认 3），ResourceConfig 记录 cycle 计数。

| cycle | 行为 |
|-------|------|
| 1 | 第一次放弃后巡检重试（全部退避次数） |
| 2 | 第二次放弃后巡检重试（全部退避次数） |
| 3 | 标记为 `permanently stale`，仅审计日志，不再自动重试 |

**permanently stale 标记的消除**：

- 该 VM 的 `@MetadataImpact` API 成功后触发的正常 GC 执行成功 → 自动删除 stale ResourceConfig
- 管理员手动调用 `APIUpdateVmMetadataMsg` → 重置 stale 标记 + 触发 GC
- stale 标记不影响 API 触发的正常 GC，仅影响巡检自动补救流程

### 4.7 孤儿 GC 加载保护

#### 4.7.1 并发加载防护

`loadOrphanJobs()` 非 synchronized，周期扫描和 nodeLeft 触发可能并发加载同一 GC。`loadFromVO()` 中使用条件更新（乐观锁）：

```java
int updated = SQL.New(GarbageCollectorVO.class)
    .eq(GarbageCollectorVO_.uuid, vo.getUuid())
    .isNull(GarbageCollectorVO_.managementNodeUuid)  // 关键条件
    .set(GarbageCollectorVO_.managementNodeUuid, Platform.getManagementServerId())
    .set(GarbageCollectorVO_.status, GCStatus.Idle)
    .update();

if (updated == 0) return false;  // 已被其他线程/MN 认领
```

#### 4.7.2 状态过滤优化

MN 宕机后 FK `ON DELETE SET NULL` 对所有 GC 行生效，包括 Done 状态。在框架层 `loadOrphanJobs()` 查询中增加状态过滤，避免对已完成 GC 的无效处理：

```java
// 框架级正确性修复：Done 的 GC 不应被重新加载。
List<GarbageCollectorVO> orphans = Q.New(GarbageCollectorVO.class)
    .isNull(GarbageCollectorVO_.managementNodeUuid)
    .notEq(GarbageCollectorVO_.status, GCStatus.Done)
    .list();
```

> 此为框架层正确性修复，对所有 GC 类型都有益。CAS 乐观锁仍保留作为并发安全网，但查询过滤避免了对大量 Done 行的无效处理。

### 4.8 GC 记录清理

每次 API 成功都 `submit()` 创建新 GC 行，需控制 Done 状态记录累积：

| 机制 | 说明 |
|------|------|
| `exceedMaxPendingCallback` | 多余 GC 立即标记 Done，不会实际执行 |
| `GCGlobalConfig.CLEANUP_INTERVAL` | 默认每 24h 清理 Done 状态的 GC 记录，通过 GlobalConfig 可配置 |
| 极端场景 | 100 API/VM → 100 行 GC，其中 98 行立即 Done，24h 内被清理 |
| 状态过滤 | §4.7 的状态过滤避免 Done 行被无效加载 |

**推荐索引**：

```sql
CREATE INDEX idx_gc_orphan ON GarbageCollectorVO (managementNodeUuid, status);
CREATE INDEX idx_gc_cleanup ON GarbageCollectorVO (status, lastOpDate);
```

---

## 5. 双 MN 高可用

### 5.1 问题背景：MN 宕机接管与恢复

双 MN 部署中，GC 任务可能因 MN 宕机而失去执行者。以下时序展示了从宕机到恢复的完整流程：

```
T0  MN-A 运行 GC-X for vmA
    DB: {uuid:GC-X, managementNodeUuid:MN-A, status:Idle}

T1  MN-A 进程死亡

T2  MN-B 心跳检测 → 删除 ManagementNodeVO(MN-A)
    FK ON DELETE SET NULL → GC-X.managementNodeUuid = NULL

T3  MN-B 孤儿扫描 (每60s) → loadFromVO(GC-X):
    乐观锁: UPDATE SET managementNodeUuid=MN-B WHERE managementNodeUuid IS NULL
    updated=1 → 认领成功 → setupTimer()

T4  MN-A 恢复加入 hash 环
    MN-A 孤儿扫描 → GC-X.managementNodeUuid=MN-B (非NULL) → 跳过

T5  GC-X 在 MN-B triggerNow()
    → isManagedByUs=true 或 delegateToOwner → 最终执行 ✓
```

**接管延迟**：最长 = 心跳超时判定(~30s) + 孤儿扫描间隔(60s) ≈ 90s

**优化**：`GarbageCollectorManagerImpl` 实现 `ManagementNodeChangeListener`，在 `nodeLeft()` 时立即触发 `loadOrphanJobs()`，将延迟降到 ~31s。

**DB 约束**：

```sql
ALTER TABLE GarbageCollectorVO
  ADD CONSTRAINT fkGarbageCollectorVOManagementNodeVO
  FOREIGN KEY (managementNodeUuid) REFERENCES ManagementNodeVO (uuid)
  ON DELETE SET NULL;
```

### 5.2 哈希环机制

双 MN 使用 `ConsistentHash`（500 虚拟节点）做资源路由。`ResourceDestinationMakerImpl` 维护 JVM 本地哈希环：

- `nodeJoin(inv)` / `nodeLeft(inv)` → 增删虚拟节点
- `makeDestination(resourceUuid)` → 一致性哈希查找 owner MN
- `CloudBusImpl3.makeTargetServiceIdByResourceUuid()` 内部调用 `destMaker.makeDestination()` 做路由

### 5.3 节点生命周期事件传播

MN 拓扑变更通过以下链路传播：

1. MN-B 启动完成 → `evtf.fire(NODE_LIFECYCLE_PATH)` → 本地 localSend + `bus.publish()` HTTP POST 到其他 MN
2. MN-A 接收 HTTP POST → `EventFacadeImpl.handleEvent()` → 匹配 glob → **`@AsyncThread` 异步执行** → `destinationMaker.nodeJoin(inv)`

**关键点**：`CallbackWrapper.call()` 标注 `@AsyncThread`，从收到 HTTP 消息到实际更新 JVM 哈希环，存在线程调度延迟。

### 5.4 哈希环不一致窗口分析

#### 5.4.1 MN 宕机（窗口 ~30 秒，高严重性）

心跳参数：`heartbeatInterval = 5s`，`MAX_HEARTBEAT_FAILURE = 5`，检测超时 ≈ `(5+1) × 5 = 30s`。

```
T0          T~30s
MN-B 宕机   MN-A 确认死亡 → nodeLeft() → 哈希环 {A}
│←──── 不一致窗口：~30 秒 ────►│
MN-A 哈希环仍为 {A, B}，发往 MN-B 的消息将丢失
```

**后果（无防护时）**：`SubmitGCMsg` 发往已死的 MN-B → 消息丢失 → GC 未持久化 → 元数据更新**彻底丢失**。

#### 5.4.2 MN 加入（窗口毫秒级，低严重性）

**初始状态**：MN-A 独自运行，哈希环 `{A}`

```
MN-B 启动流程:
  Step 5: I-join → MN-B 本地哈希环加载所有节点 → MN-B 哈希环 = {A, B}
  Step 8: say-I-join → 通知 MN-A
          → MN-A @AsyncThread 延迟    ← MN-A 哈希环尚未更新
          → nodeJoin(B)              ← MN-A 哈希环 = {A, B}
```

**后果（无防护时）**：毫秒级窗口内两个 MN 哈希环不一致，同一 VM 的 GC 可能落在不同 MN 上。

#### 5.4.3 对比

| | MN 宕机 | MN 加入 |
|---|---|---|
| **窗口长度** | ~30 秒 | 毫秒级 |
| **后果** | GC 消息丢失 | 同一 VM 被两个 MN 同时更新 |
| **严重程度** | **高** | 低 |
| **防护措施** | 提交层路由回退（§5.7.1） | 执行层归集（§5.7.2） |

### 5.5 时序验证

#### 5.5.1 正常态

```
MN-A: API 成功 → send(SubmitGCMsg) → MN-B → gc.submit() → GC 在 MN-B
MN-B: API 成功 → send(SubmitGCMsg) → MN-B → gc.submit() → GC 在 MN-B
→ 全在 MN-B ✓
```

#### 5.5.2 MN 宕机（~30 秒窗口）

```
T0:   MN-B 宕机
T5:   MN-A API 成功 → send(SubmitGCMsg → MN-B) → reply 超时
      → 回退: gc.submit() 本地 → GC 在 MN-A（DB 已持久化）
T15:  GC triggerNow() → hash 环仍 {A,B} → 非 owner → delegate → 超时
      → onUpdateFail() → retryCount=1, 退避 20s
T35:  GC triggerNow() → hash 环已变 {A} → isManagedByUs=true
      → ChainTask → UpdateVmInstanceMetadataMsg → 成功 ✓
```

#### 5.5.3 MN 加入（毫秒级窗口）

```
T0:    MN-B iJoin，MN-A hash 环尚未更新
T0:    MN-A GC → hash {A} → A → 本地；MN-B GC → hash {A,B} → B → 本地
T0+ms: MN-A 收到 NodeJoin → hash 环变 {A, B}
T10:   GC on MN-A → hash={A,B} → 非 owner → delegate → MN-B → Done ✓
T10:   GC on MN-B → owner → 执行 → 成功 ✓
→ 自动归集到 MN-B ✓
```

### 5.6 极端情况分析

#### 5.6.1 delegate 消耗 retryCount

`delegateToOwner()` 失败时调用 `onUpdateFail()`，递增 `retryCount`：

```
T10: triggerNow() → delegate MN-B → 超时 → retryCount=1
T30: triggerNow() → delegate MN-B → 超时 → retryCount=2
T70: triggerNow() → hash 环已变 {A} → isManagedByUs=true → 正常执行 ✓
```

**结论：不是问题。** 心跳检测约 30 秒，指数退避 10s→20s→40s，2~3 次重试后 hash 环已修正。消耗 1~2 次 retryCount，剩余次数足够用于实际更新失败。

#### 5.6.2 delegate 成功后 owner 立即宕机

```
T10:  GC-1 on MN-A → delegate → MN-B gc.submit() → GC-1 Done
      GC-3 在 MN-B（DB 已持久化, managementNodeUuid=MN-B）
T11:  MN-B 宕机
T~40: MN-A 检测到 MN-B 死亡 → 删除 ManagementNodeVO(B)
      → FK ON DELETE SET NULL → GC-3.managementNodeUuid=NULL
T~40+: MN-A loadOrphanJobs() → 发现 GC-3 → loadFromVO → 认领
T~50:  GC-3 triggerNow() on MN-A → isManagedByUs=true → 执行 ✓
```

**结论：不是问题。** 已持久化的 GC 通过 FK 级联 SET NULL + 孤儿扫描被 MN-A 接管。

#### 5.6.3 delegation 循环（hash 环反复变化）

hash 环反复变化时 GC 在 MN-A 和 MN-B 之间反复 delegate：

```
T0:  hash=B → GC-1 on MN-A delegate → GC-3 on MN-B, GC-1 Done
T10: hash=A → GC-3 on MN-B delegate → GC-4 on MN-A, GC-3 Done
T20: hash=B → GC-4 on MN-A delegate → GC-5 on MN-B, GC-4 Done
```

- 每次 delegation 创建新 GC（**继承当前 retryCount**），旧 GC 标记 Done
- 任意时刻只有 **1 个活跃 GC**
- MN 拓扑变更由心跳检测自动触发（宕机 → 心跳超时 ~30s → nodeLeft；恢复 → nodeJoin），不会在短时间内反复发生

**结论：理论存在，实际不可能。** MN 拓扑变更由心跳机制自动检测，一次宕机判定 + 恢复加入的最短周期远超 10 秒，不可能形成快速循环。retryCount 在 delegation 间传递，确保 PS 持续不可用时最终会触发放弃。

#### 5.6.4 本地 MN 在 send 与 callback 之间崩溃

`Interceptor.submitUpdateVmInstanceMetadataGC()` 中 `bus.send()` 发出后、callback 执行前 MN 崩溃：

- **远程 MN 收到并 submit 成功** → GC 在远程 MN 上，不丢 ✓
- **远程 MN 也不可达** → 消息丢失，GC 未创建 ✗

**结论：唯一真正的丢失窗口。** 与改动前行为一致（原方案也有同样窗口），窗口极短（毫秒级），无法避免。

#### 5.6.5 大量 GC 同时 delegate

短时间内 100 个 API 触发 100 个 GC，部分在非 owner MN 上 delegate：

```
MN-A 上 50 个 GC → delegate 到 MN-B
MN-B 上累计 100 个 GC → triggerNow()
→ ChainTask maxPendingTasks=1 → 最多 1 running + 1 pending
→ 98 个 GC 立即标记 Done（exceedMaxPendingCallback）
```

**结论：不是问题。** ChainTask 天然限流。delegate 只增加 GC 行数，不增加执行并发。

#### 5.6.6 makeDestination 返回自己

`isManagedByUs()` 和 `makeDestination()` 两次调用之间 hash 环变化，delegate 目标变为自己：

```
triggerNow():
  → isManagedByUs("vm-123") = false
  → delegateToOwner()
  → owner = makeDestination("vm-123") = 自己  ← hash 环已变
  → send SubmitGCMsg to 自己 → gc.submit() → GC-3 在本地
  → GC-1 Done
  → GC-3 triggerNow() → isManagedByUs=true → 执行 ✓
```

**结论：不是问题。** 多了一轮间接，但功能正确。

#### 5.6.7 汇总

| # | 极端情况 | 是否丢 GC | 归集效果 | 需要额外处理 |
|---|----------|-----------|----------|--------------|
| 1 | delegate 消耗 retryCount | 否 | ✓ | 否 |
| 2 | delegate 后 owner 宕机 | 否（FK SET NULL + 孤儿扫描） | ✓ | 否 |
| 3 | delegation 循环 | 否 | ✓ | 否（实际不会发生） |
| 4 | send-callback 间崩溃 | 极小概率丢 | — | 否（与改动前一致） |
| 5 | 大量 GC 同时 delegate | 否 | ✓ | 否（ChainTask 限流） |
| 6 | delegate 目标是自己 | 否 | ✓ | 否（多一轮间接但正确） |

### 5.7 解决方案：提交层路由 + 执行层归集

综合 §5.4 的两类问题，采用**双层防护**策略：

| 层级 | 名称 | 解决的问题 | 入口 |
|------|------|-----------|------|
| 提交层 | 远程提交 + reply 回退 | MN 宕机时 GC 消息丢失（§5.4.1） | `VmMetadataUpdateInterceptor.submitUpdateVmInstanceMetadataGC()` |
| 执行层 | triggerNow 路由归集 | MN 加入时 GC 分散（§5.4.2） | `UpdateVmInstanceMetadataGC.triggerNow()` |

两层协同工作，覆盖所有 MN 拓扑变更场景。

#### 5.7.1 提交层路由（远程提交 + reply 回退）

**逻辑**：构造 GC 对象 + `SubmitTimeBasedGarbageCollectorMsg`，通过 `makeTargetServiceIdByResourceUuid(vmUuid)` 路由到 owner MN。关键回退：

```java
bus.send(gcmsg, new CloudBusCallBack(null) {
    @Override
    public void run(MessageReply reply) {
        if (!reply.isSuccess()) {
            gc.submit(getInitialGcDelaySec(), TimeUnit.SECONDS); // 本地兜底
        }
    }
});
```

正常态 GC 路由到 owner MN 归集；owner 不可达时 reply 失败 → 回退本地持久化，GC 不丢失。

#### 5.7.2 执行层归集（triggerNow 路由委托）

```java
@Override
protected void triggerNow(GCCompletion completion) {
    if (!destMaker.isManagedByUs(vmInstanceUuid)) {
        delegateToOwner(completion);  // 非 owner → 委托给 owner
        return;
    }
    // owner → 正常 ChainTask 执行...
}
```

`delegateToOwner` 发送 `SubmitTimeBasedGarbageCollectorMsg` 到 owner MN（**携带当前 retryCount**），成功则本地 GC 标记 Done（职责已转移），失败则指数退避重试。下次 `triggerNow()` 时 hash 环可能已修正，当前 MN 可能成为 owner 直接执行。新 GC 继承 retryCount，确保 delegation 不会重置重试计数器。

#### 5.7.3 SubmitTimeBasedGarbageCollectorMsg 协议

`SubmitTimeBasedGarbageCollectorMsg extends NeedReplyMessage`，携带 `gcContext`（GC 对象 JSON）、`gcClassName`、`gcInterval`、`unit` 四个字段。

处理流程：`GarbageCollectorManagerImpl.handle()` → 反序列化 GC 对象 → `gc.submit(gcInterval, unit)` 本地持久化 → reply 成功。

> **`@GC` 字段跨版本兼容约束**：所有 `@GC` 标注字段的默认值必须为 JVM 类型默认值（int→0, String→null, boolean→false），或显式初始化为安全值。升级前持久化的 GC context JSON 若缺少新增字段，反序列化后得到 JVM 默认值。新增字段的业务逻辑必须容忍默认值（如 `retryCount=0` 是安全的，代表"未重试"）。若无法满足此约束，需在 `loadFromVO()` 中增加迁移逻辑。

---

## 6. 管理平面恢复策略

以下是 sblk 协议层和 GC 层向上委托的管理层面统一恢复实现：

| 触发源 | 检测方式 | 管理平面行为 | 实现类 |
|--------|---------|-------------|--------|
| GC 退避上限后放弃 | `MetadataGC.triggerNow()` | 审计日志 + `MetadataStaleEvent` | `UpdateVmInstanceMetadataGC` |
| read 返回 NEED_REPAIR | 巡检 / 读取时 | 发 `RepairMetadataMsg` 到 Host（512B Header 写） | `MetadataHealthCheckJob` |
| read 返回 CORRUPTED | 巡检 / 读取时 | 触发 `SubmitGCMsg` full-refresh（全量重写） | `MetadataHealthCheckJob` |
| read 返回 STORAGE_CHANGE_INCOMPLETE | 巡检 / 读取时 | 触发 `SubmitGCMsg` full-refresh（op=2） | `MetadataHealthCheckJob` |
| VG 空间不足 | Agent 返回错误码 | 告警 + GC 退避 + 巡检重试 | `AlertManager` |
| 注册崩溃残留 | MN 启动/定时扫描 | Saga 回滚（5 条件判断） | `RegistrationCleanupJob` |
| 存储迁移元数据创建失败 | 迁移流程 post-hook | 告警 + GC 自愈（下次写入时创建） | `VmStorageMigrateFlow` |
| VM 销毁元数据残留 | 销毁流程 post-hook + 巡检 | 孤儿 LV 检测 + 运维清理 | `MetadataHealthCheckJob` |

**定期健康巡检**（`MetadataHealthCheckJob implements PeriodicTask`）：

1. 按 PS 分组查询所有启用元数据的 VM
2. 对每台 Host 发 `BatchCheckMetadataStatusMsg`（批量读 Header），每批最多 `healthCheck.batchSize`（默认 50）个 LV，每个 LV 的 `sblk_read_header` 启动独立 daemon 线程 + `join(timeout=5s)` 防超时，HTTP 层面总超时 `healthCheck.httpTimeoutSec`（默认 30s）
3. 汇总结果：`NEED_REPAIR` → `RepairMetadataMsg`（512B Header 写）；`CORRUPTED / STORAGE_CHANGE_INCOMPLETE` → `SubmitGCMsg`（full-refresh）；`TIMEOUT` → 记录日志下次重检
4. 处理 `MetadataStaleEvent` 优先队列（见 §4.6）

**VM 销毁时的元数据清理**：

在 `DestroyVmInstanceFlow` 链中增加一个 `NoRollbackFlow` step：查找根卷所在 PS → 调用 `metadataStorageHandler.deleteMetadata()` → **best-effort**，失败仅 WARN 日志，不阻塞 VM 销毁（`trigger.next()`）。

**孤儿检测**：健康巡检中匹配 `_vmmeta` LV 但无对应 `VmInstanceVO` → 标记孤儿 → 审计日志。

---

## 7. 升级后全量刷新

滚动升级期间，新版本 MN 可能新增 `@MetadataImpact` 字段或修改 DTO 结构，导致旧版本 MN 写入的元数据缺少新字段。需要在所有 MN 升级到同一版本后触发一次全量刷新。

### 7.1 触发条件

在 `managementNodeReady()` 回调中执行：

1. 查询所有在线 `ManagementNodeVO`，收集 version 集合
2. 若存在多个不同版本（滚动升级中）→ 跳过
3. 版本唯一且与 `lastRefreshVersion`（GlobalConfig 持久化）不同 → 提交延迟 10 分钟的定时任务
4. 10 分钟后再次检查所有 MN 版本是否一致 → 一致则执行 `submitFullRefreshLongJob(currentVersion)`，不一致则跳过

> **延迟 10 分钟的原因**：滚动升级期间，`managementNodeReady()` 在每个 MN 启动时都会触发。第一个 MN 升级完成时可能短暂出现"版本唯一"假象（旧 MN 尚未恢复上线）。延迟 10 分钟给足够时间让所有 MN 完成升级并加入集群，避免对仅部分升级完成的环境触发全量刷新。

### 7.2 全量刷新 LongJob

`MetadataFullRefreshLongJob extends LongJob`：遍历所有启用元数据的 VM UUID，逐个调用 `interceptor.submitUpdateVmInstanceMetadataGC(vmUuid)` 提交 GC（retryCount=0）。每提交一个即更新 `job.setJobResult("Progress: n/total")`。通过 ChainTask 自动限流，不会造成 PS 压力。

### 7.3 schemaVersion 兼容

存储上的元数据 schemaVersion 可能落后于当前管理平面版本（如升级后全量刷新尚未完成）：

| 场景 | 行为 |
|------|------|
| `schemaVersion` == 当前版本 | 正常 |
| `schemaVersion` != 当前版本 | 标记 `NEED_FULL_REFRESH`，触发 GC 用当前版本重写 |
| 写入时 | 始终使用当前版本 schema |

> 注册场景的版本兼容规则详见 Part 1 §6.3：默认拒绝不匹配版本，`force=true` 允许同 MAJOR 跨 MINOR 注册。

---

## 8. Payload 大小保护

`buildVmInstanceMetadata()` 生成的 JSON payload 理论上可能随 VM 资源增长（大量快照、大量磁盘）而膨胀。sblk 协议层的 LV 默认大小有上限（详见 Part 4），需要在管理层面增加保护。

**实现**：在 `VmInstanceBase.handle(UpdateVmInstanceMetadataMsg)` 中，对 `buildVmInstanceMetadata()` 返回的 `byte[]` 做大小检查：

| 阈值 | 行为 | 说明 |
|------|------|------|
| > 8MB | WARN 日志 | 早期预警，提示运维关注 |
| > 30MB | ERROR + 拒绝写入 | 保护 sblk LV 空间 |

> 正常 VM 的 metadata payload 通常在 10KB~500KB 范围内。超过 8MB 几乎一定表示异常（如快照未清理导致数千条记录）。

---

## 9. 开发约束清单

| # | 约束 | 原因 | 检查方式 |
|---|------|------|----------|
| 1 | 新增 `@GC` 字段必须有 JVM 类型默认安全值 | 跨版本反序列化兼容（§5.7.3） | Code Review |
| 2 | 修改 VM 存储拓扑的内部消息 handler 必须调用 `submitUpdateVmInstanceMetadataGC()` | 非 API 操作不经过拦截器（§4.4） | Code Review + 巡检兜底 |
| 3 | `buildVmInstanceMetadata()` 必须标注 `@Transactional(readOnly=true)` | 读一致性保证（§1） | 单元测试 |
| 4 | GC NAME 必须通过 `UpdateVmInstanceMetadataGC.getGCName(vmUuid)` 生成 | 格式统一（§4.3） | Code Review |
| 5 | Handler 端写入失败时**不得**创建新 GC，必须 reply error 由 GC 端重试 | 防止 GC 滚雪球（§4.2） | Code Review |

---

## 附录

### A. GlobalConfig 配置项汇总

| 配置项 | 类型 | 默认值 | 说明 | 章节 |
|--------|------|--------|------|------|
| `vm.metadata.enabled` | Boolean | false | 元数据功能总开关 | §1 |
| `vm.metadata.gc.maxRetry` | Integer | 5 | GC 最大重试次数，超过后放弃并发布 MetadataStaleEvent | §4.5 |
| `vm.metadata.gc.initialDelaySec` | Long | 10 | GC 首次触发延迟（秒） | §5.7.1 |
| `vm.metadata.ps.maxConcurrent` | Integer | 5 | 同一 MN 同一主存储最大并发元数据写入数 | §3.1 |
| `vm.metadata.global.maxConcurrent` | Integer | 10 | 同一 MN 最大并发 VM 元数据更新数 | §3.2 |
| `vm.metadata.maxStaleRecoveryCycles` | Integer | 3 | 巡检自动补救最大 cycle 数 | §4.6 |
| `vm.metadata.healthCheck.intervalHours` | Integer | 24 | 健康巡检间隔（小时） | §6 |
| `vm.metadata.healthCheck.batchSize` | Integer | 50 | 每批巡检 LV 数量 | §6 |
| `vm.metadata.healthCheck.httpTimeoutSec` | Integer | 30 | 巡检 HTTP 请求超时（秒） | §6 |

### B. ThreadFacade 扩展预留

> 原设计的 `hasPendingTask()` 接口已被 `ChainTask.exceedMaxPendingCallback()` 机制完全取代。当前 `ThreadFacade` 无需新增方法。

如后续有其他需要查询 ChainTask 队列状态的场景，可在 `ThreadFacade` 接口中扩展 `getPendingTaskCount(String syncSignature)` 方法。
