# 分配引擎深度审阅报告

**版本**: v1.0
**日期**: 2026-03-19
**审阅人**: Compute Resource Allocation Architect
**审阅范围**: `docs/architecture-allocator-2026-03-18.md` + `docs/architecture-unified-hardware-2026-03-18.md` 第 4 章
**对照代码**: `compute/src/main/java/org/zstack/compute/allocator/` 全量源码

---

## 0. 总体评价

设计文档对现有分配器的理解准确度很高，Flow 清单、排序链、容量预留、死锁防护的分析都与源码一致。两阶段薄适配的整体思路合理——阶段1在 PhysicalServer 维度做通用过滤，阶段2复用现有 HostAllocatorChain 做角色特有过滤。

但本报告识别出 **一个架构级阻断风险**（HostCapacityVO → VIEW）和若干需要补充的设计细节。

---

## 1. 阻断风险：HostCapacityVO → VIEW 的可行性

### 1.1 问题本质

设计文档的核心假设是：

> PhysicalServerCapacityVO 是容量的唯一真表（source of truth），HostCapacityVO 降级为 MySQL VIEW。

这个假设在 Hibernate/JPA 层面存在 **三个硬性约束冲突**。

### 1.2 约束冲突 #1：HostCapacityVO 有 `dbf.persist()` 写入

`HostAllocatorManagerImpl.handle(ReportHostCapacityMessage)` 中存在对 HostCapacityVO 的直接 `persist` 和 `update` 操作：

```java
// HostAllocatorManagerImpl.java 第 289~313 行
vo = new HostCapacityVO();
vo.setUuid(msg.getHostUuid());
// ... 设置字段 ...
dbf.persist(vo);   // ← INSERT 到 HostCapacityVO

// 第 335 行
dbf.update(vo);    // ← UPDATE HostCapacityVO
```

**MySQL VIEW 不支持对包含 JOIN 的 VIEW 执行 INSERT/UPDATE**。设计文档中的 VIEW 定义使用了 `JOIN PhysicalServerRoleVO`，这意味着 `dbf.persist(vo)` 和 `dbf.update(vo)` 在运行时会抛出 SQL 异常。

这不只是 `HostCapacityUpdater` 的 59 个调用方的问题——`HostAllocatorManagerImpl` 自身就在做 INSERT。

### 1.3 约束冲突 #2：HostCapacityVO 被 HostVO 通过 @OneToOne EAGER 关联

```java
// HostVO.java 第 26~29 行
@OneToOne(fetch = FetchType.EAGER)
@JoinColumn(name = "uuid")
@NoView
private HostCapacityVO capacity;
```

当 HostCapacityVO 从真表变为 VIEW 后，Hibernate 在加载 HostVO 时会自动 JOIN 这个 VIEW。这带来两个问题：

1. **性能退化**：每次加载 HostVO 都会触发 VIEW 的 JOIN 查询（PhysicalServerCapacityVO JOIN PhysicalServerRoleVO），相比原来的单表 PK 查找，查询成本显著增加。在分配链路中 `DesignatedHostAllocatorFlow` 和 `HostStateAndHypervisorAllocatorFlow` 批量加载 HostVO 时，这个退化会被放大。

2. **Hibernate merge/persist 语义破坏**：`HostCapacityUpdater.merge()` 方法（第 96 行）调用 `dbf.getEntityManager().merge(capacityVO)`，这会尝试对 VIEW 执行 UPDATE。带 JOIN 的 VIEW 不支持 UPDATE。

### 1.4 约束冲突 #3：VIEW 缺少必要列

现有 HostCapacityVO 有 9 个持久化字段：

| 字段 | VIEW 中是否映射 |
|------|----------------|
| `uuid` | 是 (`r.roleUuid`) |
| `totalCpu` | 是 (`c.totalPhysicalCpu`) |
| `availableCpu` | 是 |
| `totalMemory` | 是 (`c.totalPhysicalMemory`) |
| `availableMemory` | 是 |
| `totalPhysicalMemory` | 是 |
| `availablePhysicalMemory` | **否** |
| `cpuNum` | **否** |
| `cpuSockets` | 是 |
| `cpuCoreNum` | 是 (`c.cpuCores`) |

`cpuNum` 和 `availablePhysicalMemory` 在 VIEW 定义中缺失。`cpuNum` 被 `HostCapacityAllocatorFlow.isNoCpu()` 使用，`availablePhysicalMemory` 被 `HostCapacityAllocatorFlow.memoryCheck()` 中的 `HOST_ALLOCATOR_MAX_MEMORY` 逻辑使用，也被 `HostCapacityReserveManagerImpl.filterOutHostsByReservedCapacity()` 引用。

如果 Hibernate 加载 HostCapacityVO 时发现 VIEW 返回的列与 `@Column` 注解不匹配（列不存在），会在 `SessionFactory` 初始化阶段抛出 `SchemaValidationException`（如果 `hibernate.hbm2ddl.auto=validate`），或在运行时抛出 `SQLSyntaxErrorException`。

### 1.5 结论

**HostCapacityVO → VIEW 在当前代码基础上不可行**。核心原因是：

1. 存在 `dbf.persist(new HostCapacityVO())` 和 `dbf.update(vo)` 的 INSERT/UPDATE 操作
2. `HostCapacityUpdater.merge()` 对 VIEW 执行 UPDATE 会失败
3. VIEW 定义缺少 `cpuNum` 和 `availablePhysicalMemory` 列
4. HostVO 的 `@OneToOne EAGER` 关联会导致性能退化

### 1.6 备选方案

#### 方案 A：保留 HostCapacityVO 为真表 + 包装器双写（推荐）

保留 HostCapacityVO 为真表不变。HostCapacityUpdater 改为包装器模式，内部先写入 PhysicalServerCapacityVO（新增真表），再同步写入 HostCapacityVO（保留原表）。两表写入在同一个 `@Transactional` 中，保证事务一致性。

```
写入路径：
  HostCapacityUpdater.run(runnable)
    → PhysicalServerCapacityUpdater.run()     // 写入 PhysicalServerCapacityVO（悲观锁）
    → 同事务内同步更新 HostCapacityVO          // 保留原表数据一致

读取路径：
  HostVO.getCapacity()                        // 不变，仍然读 HostCapacityVO 真表
  ServerAllocatorChain                        // 读 PhysicalServerCapacityVO
```

**优点**：
- 59 个调用方零改动
- `HostAllocatorManagerImpl.handle(ReportHostCapacityMessage)` 的 persist/update 逻辑不受影响
- HostVO 的 @OneToOne EAGER 性能不退化
- 不需要修改 Hibernate 映射

**缺点**：
- 存在双写，需要保证一致性（但在同一事务内，比异步对账可靠得多）
- `HostAllocatorManagerImpl.handle(ReportHostCapacityMessage)` 中的 `dbf.persist(vo)` 需要适配为同时创建 PhysicalServerCapacityVO（这是唯一一个需要改动的 INSERT 入口）

**一致性保证**：
- 正常路径：同一事务内双写，强一致
- 异常路径：新增定时对账任务（每 5 分钟比较两表差异），作为兜底机制
- 新 Host 首次上线：`ReportHostCapacityMessage` 处理中同时创建两张表的记录

#### 方案 B：HostCapacityVO 改为 Hibernate @Subselect（只读映射）

使用 Hibernate 的 `@Subselect` 注解将 HostCapacityVO 映射为只读实体，底层仍然是 SQL 子查询，但不需要在 MySQL 层创建 VIEW。

```java
@Entity
@Subselect("SELECT r.roleUuid AS uuid, c.totalPhysicalCpu AS totalCpu, ... " +
           "FROM PhysicalServerCapacityVO c " +
           "JOIN PhysicalServerRoleVO r ON r.serverUuid = c.uuid " +
           "WHERE r.roleType = 'KVM_HOST'")
@Immutable
public class HostCapacityVO { ... }
```

**问题**：
- `@Subselect` 实体是 `@Immutable` 的，不能执行 persist/update/merge
- 需要改造所有 HostCapacityVO 的写入路径（`HostCapacityUpdater.merge()`、`HostAllocatorManagerImpl.dbf.persist(vo)` 等）
- 改动面大，与"59 个调用方零改动"的目标矛盾

#### 方案 C：保留 HostCapacityVO 真表 + 异步事件驱动同步

与方案 A 类似，但同步方式改为异步事件驱动（CanonicalEvent），解耦写入路径。

**问题**：
- 引入延迟窗口（事件处理有延迟）
- 分配器在延迟窗口内可能读到过时数据
- 复杂度高于方案 A

**推荐方案 A**——保留 HostCapacityVO 为真表 + 同事务双写。这是对现有代码改动最小、风险最低的方案。

---

## 2. 两阶段薄适配：candidateHostUuids 注入机制

### 2.1 现状分析

设计文档提出通过 `HostAllocatorSpec.candidateHostUuids` 字段将阶段1的输出注入阶段2。但实际代码中 `HostAllocatorSpec` **不存在** `candidateHostUuids` 字段，需要新增。

### 2.2 注入点选择

`DesignatedHostAllocatorFlow` 是 HostAllocatorChain 中首个有能力从 DB 加载候选列表的 Flow。其 `allocate()` 方法在 `amITheFirstFlow()` 为 true 时执行 DB 查询，否则在内存中过滤已有的 candidates。

注入 `candidateHostUuids` 的最自然位置是：当 `DesignatedHostAllocatorFlow` 是首个 Flow 且 `spec.candidateHostUuids` 非空时，将 DB 查询的 WHERE 条件中追加 `h.uuid IN (:candidateHostUuids)`。

```java
// DesignatedHostAllocatorFlow.allocate() 第 30~55 行中的 SQL 拼接
if (spec.getCandidateHostUuids() != null && !spec.getCandidateHostUuids().isEmpty()) {
    sql.append(String.format("h.uuid in ('%s') and ",
        String.join("','", spec.getCandidateHostUuids())));
}
```

### 2.3 风险点

1. **SQL 注入**：`DesignatedHostAllocatorFlow` 使用字符串拼接构造 SQL（不用参数绑定），candidateHostUuids 如果包含非法字符会有 SQL 注入风险。建议改为 `TypedQuery.setParameter()` 参数绑定。

2. **候选集过大**：如果阶段1输出大量 serverUuid（例如 1000+），`IN` 子句性能会下降。建议设定上限，超过阈值时跳过预过滤，让阶段2自行全量执行。

3. **HostStateAndHypervisorAllocatorFlow 也能做首个 Flow**：在某些策略工厂配置下，`HostStateAndHypervisorAllocatorFlow` 可能是首个 Flow（而非 `DesignatedHostAllocatorFlow`）。此时 candidateHostUuids 的注入也需要在 `HostStateAndHypervisorAllocatorFlow` 的 `allocate(String hypervisorType)` 方法中处理。

### 2.4 建议

**不要在每个 Flow 中分别处理 candidateHostUuids**。改为在 `HostAllocatorChain.start()` 中统一注入：

```java
// HostAllocatorChain.start() 中
if (allocationSpec.getCandidateHostUuids() != null) {
    // 将 candidateHostUuids 设置到 spec.extraData 中
    // 首个 Flow 的 DB 查询会自动识别
}
```

或者更优雅的方案：在 `HostAllocatorChainBuilder.build()` 中根据 candidateHostUuids 是否存在，在 Flow 链头部插入一个 `PreFilterByServerAllocatorFlow`，该 Flow 从 DB 加载指定 UUID 的 HostVO 列表。这样不修改任何现有 Flow。

---

## 3. 并发安全分析

### 3.1 ChainTask 并发控制

`HostAllocatorManagerImpl.handle(AllocateHostMsg)` 使用 `ChainTask` 控制并发。当 `HOST_ALLOCATOR_ALLOW_CONCURRENT = true` 时，`getSyncLevel()` 返回 `HOST_ALLOCATOR_CONCURRENT_LEVEL`（默认值通常为 10），允许并发分配。

这对两阶段薄适配有影响：如果 CompatibilityBridge 在 ChainTask 之前拦截了消息（设计文档 4.4 节），那么 ServerAllocatorChain 的并发控制需要由新引擎自行实现，不能依赖现有 ChainTask。

### 3.2 PhysicalServerCapacityVO 的悲观锁

设计文档中 `PhysicalServerCapacityUpdater.lockCapacity()` 使用 `PESSIMISTIC_WRITE`，与 `HostCapacityUpdater` 一致。如果采用方案 A（双写），需要确保锁顺序：

```
正确的锁顺序（方案 A）：
  1. 先锁 PhysicalServerCapacityVO（PESSIMISTIC_WRITE）
  2. 再更新 HostCapacityVO（在同一事务内，无需额外加锁）
```

如果 HostCapacityUpdater 的旧路径（未经包装器）和新路径（经包装器）并发执行，可能出现数据不一致。建议在过渡期间，通过特性开关确保同一时刻只有一条路径在写入。

### 3.3 @DeadlockAutoRestart 的工作机制

代码验证：

- `DbDeadlockAspect.aj` 的 `declare error` 编译期检查确保 `@Transactional` 和 `@DeadlockAutoRestart` 不会出现在同一方法上。
- AspectJ `around advice` 在 `@DeadlockAutoRestart` 方法上包裹一个带 `@Transactional` 的匿名 Callable，实现事务边界与重试分离。
- 重试逻辑只针对 `SQLTransactionRollbackException` + `Deadlock` 关键字，其他异常（包括 `Lock wait timeout`）直接抛出。
- 重试间隔为随机 400~600ms，最大重试次数由 `DatabaseGlobalProperty.retryTimes` 控制。

设计文档中 PhysicalServerCapacityUpdater 的 `run()` / `_run()` 分离模式完全对齐 HostCapacityUpdater，这是正确的。

---

## 4. Flow 设计验证

### 4.1 Flow 清单对照

设计文档列出的 7 个 Flow（ZoneFilterFlow → ClusterFilterFlow → PoolFilterFlow → RoleTypeFilterFlow → StateFilterFlow → AvoidServerFilterFlow → CapacityFilterFlow）的职责划分合理，与现有 13 个 Flow 的映射关系准确。

**发现一处不一致**：设计文档 1.2 节说 ServerAllocatorChain "已移除 SortFilterFlow（排序和容量预留由阶段2的 HostSortorChain 处理）"，但 1.4.10 节又给出了 SortFilterFlow 的完整实现代码。如果阶段1确实不做排序和容量预留（交给阶段2），那么 1.4.10 节是冗余的，应该删除。

### 4.2 CapacityFilterFlow 的独占模式

设计中 `CapacityFilterFlow` 区分 `INTERNAL_EXCLUSIVE`（只检查是否已被占用）和 `INTERNAL_SHARED`（检查 CPU/Memory/Disk 容量）。这个分支设计合理。

但需要注意：在两阶段薄适配路径中，CapacityFilterFlow（阶段1）和 HostCapacityAllocatorFlow（阶段2）会对容量做 **两次检查**。阶段1检查 PhysicalServerCapacityVO，阶段2检查 HostCapacityVO。如果采用方案 A（双写保持一致），两次检查的结果应一致，但会有冗余开销。

建议：CompatibilityBridge 路径下，如果阶段1已经做了容量过滤，在阶段2中跳过 HostCapacityAllocatorFlow。可以通过 `spec.extraData.put("capacityAlreadyFiltered", true)` 标记。

### 4.3 策略工厂选择逻辑

`doHandleAllocateHost()` 第 438~453 行展示了策略工厂选择流程：先遍历 `HostAllocatorStrategyExtensionPoint` 扩展点，扩展点可返回自定义策略名；若无扩展点命中，使用消息自带的 `allocatorStrategy`。

CompatibilityBridge 在消息拦截层面介入（在 `handle(AllocateHostMsg)` 中，ChainTask 提交之前），不影响策略工厂选择逻辑。这个设计是安全的。

---

## 5. 容量重计算（税收模式）验证

### 5.1 recalculateCapacity 逻辑

税收模式的计算公式 `Available = Total - Sigma(业务税) - Sigma(系统税)` 是合理的。遍历所有 `PhysicalServerRoleProvider` 征收业务税的方式干净清晰。

### 5.2 EXTERNAL_READONLY 的 no-op 处理

设计文档正确地将 EXTERNAL_READONLY 角色排除在业务税征收之外。但需要明确：EXTERNAL_READONLY 角色的容量数据由谁更新？设计文档提到"外部调度器通过心跳上报"，但没有给出具体的上报入口和写入路径。建议补充 EXTERNAL_READONLY 的容量上报 API 设计。

### 5.3 多角色并行征税的语义

设计文档 5.2 节提到"如果一台服务器有多个角色，业务税取各角色消耗的并集（非叠加）"，但 5.3 节的实现代码是 `totalUsedCpu += usage.getUsedCpu()`，这是**叠加**而非并集。

当一台物理服务器同时承担 KVM + Container 角色时：
- KVM RoleProvider 返回所有运行中 VM 的 CPU 消耗
- Container RoleProvider（EXTERNAL_READONLY）被跳过

如果一台物理服务器同时有 KVM + BM_V1 角色（理论上可能），两者的 CPU 消耗会被叠加。这在现实中应该不会发生（一台机器不会同时做 KVM 宿主机和裸金属），但建议在 `recalculateCapacity` 中增加防御性校验。

---

## 6. CompatibilityBridge 风险

### 6.1 双重容量预留

设计文档 7.3 节第 5 点已识别了这个风险——CompatibilityBridge 路径可能触发 ServerAllocatorChain 和 HostSortorChain 的双重容量预留。建议的解决方案是 `AllocateHostMsg.setFullAllocate(false)` 跳过 HostSortorChain 的预留。

但注意：`AllocateHostMsg` 目前没有 `fullAllocate` 字段。由于设计文档 1.2 节已经将 SortFilterFlow 从阶段1移除（排序和预留由阶段2处理），正确的做法应该是反过来——阶段1不做预留，只做过滤；容量预留完全由阶段2的 HostSortorChain 执行。这样就不存在双重预留的问题。

### 6.2 inferRoleType 的推导准确性

`CompatibilityBridge.shouldIntercept()` 需要从 `AllocateHostMsg` 推导 `ServerRoleType`。设计文档中使用 `inferRoleType(msg)`，但没有给出详细实现。推导依据应该是 `msg.getHypervisorType()` → KVM_HOST，但 hypervisorType 可能为 null（在某些间接分配路径中）。需要有明确的降级策略：hypervisorType 为 null 时不拦截，走旧路径。

---

## 7. 总结：关键发现清单

| # | 严重等级 | 发现 | 建议 |
|---|---------|------|------|
| 1 | **阻断** | HostCapacityVO → VIEW 不可行：存在 `dbf.persist()` INSERT、`merge()` UPDATE、VIEW 缺列、EAGER JOIN 性能退化 | 采用方案 A：保留真表 + 同事务双写 |
| 2 | **高** | VIEW 定义缺少 `cpuNum` 和 `availablePhysicalMemory` 两个字段 | 若仍坚持 VIEW 方案，必须补全列映射；推荐直接采用方案 A 规避 |
| 3 | **高** | `candidateHostUuids` 字段在 `HostAllocatorSpec` 中不存在，需要新增 | 新增字段 + 在 Flow 链头部插入 `PreFilterByServerAllocatorFlow`，不修改现有 Flow |
| 4 | **中** | 设计文档 1.2 节与 1.4.10 节关于 SortFilterFlow 的描述矛盾 | 如果阶段1不做排序和预留，删除 1.4.10 节 |
| 5 | **中** | CompatibilityBridge 路径双重容量预留风险 | 阶段1只做过滤，不做预留；预留完全由阶段2执行 |
| 6 | **中** | 两阶段路径下 CapacityFilterFlow 和 HostCapacityAllocatorFlow 双重容量检查 | CompatibilityBridge 路径通过 extraData 标记跳过阶段2的容量检查 |
| 7 | **低** | recalculateCapacity 的业务税是叠加而非并集，与文档描述不一致 | 修正文档描述为"叠加"，或修改代码实现为并集 |
| 8 | **低** | EXTERNAL_READONLY 容量上报入口未设计 | 补充心跳上报 API 的详细设计 |
| 9 | **低** | `DesignatedHostAllocatorFlow` 使用字符串拼接 SQL，candidateHostUuids 注入有 SQL 注入风险 | 改为参数绑定 |

---

## 8. 方案 A 实施要点

如果采用推荐的方案 A（保留 HostCapacityVO 真表 + 同事务双写），需要关注的关键实施点：

### 8.1 HostCapacityUpdater 包装器改造

```
改造前（现有）：
  HostCapacityUpdater._run()
    → lockCapacity()          // PESSIMISTIC_WRITE on HostCapacityVO
    → runnable.call(cap)      // 用户逻辑修改 cap
    → merge(cap)              // UPDATE HostCapacityVO

改造后（方案 A）：
  HostCapacityUpdater._run()
    → lookupServerUuid()      // 通过 RoleVO 查找 serverUuid（可缓存）
    → lockServerCapacity()    // PESSIMISTIC_WRITE on PhysicalServerCapacityVO
    → runnable.call(cap)      // 用户逻辑修改 cap（此处 cap 可以是适配器对象）
    → mergeServerCapacity()   // UPDATE PhysicalServerCapacityVO
    → syncToHostCapacity()    // UPDATE HostCapacityVO（同事务，无需额外加锁）
```

### 8.2 ReportHostCapacityMessage 处理改造

`HostAllocatorManagerImpl.handle(ReportHostCapacityMessage)` 中的 `dbf.persist(vo)` 需要同时创建 PhysicalServerCapacityVO 记录。这是唯一的 HostCapacityVO INSERT 入口，改造点明确。

### 8.3 锁顺序约定

```
全局锁顺序（防止死锁）：
  PhysicalServerCapacityVO 行锁 → HostCapacityVO 更新（同事务，无需锁）

永远不要反过来先锁 HostCapacityVO 再操作 PhysicalServerCapacityVO。
```

### 8.4 过渡期双路径防护

特性开关关闭时，HostCapacityUpdater 走旧路径（直接操作 HostCapacityVO）。特性开关打开后，走包装器路径（先写 PhysicalServerCapacityVO 再同步 HostCapacityVO）。两条路径不能并发，否则 HostCapacityVO 的数据会不一致。

建议：特性开关切换为一次性操作（不可回退），或者在包装器路径中对 HostCapacityVO 也加悲观锁（以 PhysicalServerCapacityVO → HostCapacityVO 的固定顺序），保证两条路径的数据最终一致。
