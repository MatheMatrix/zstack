# ZStack 统一分配引擎与容量管理 — 详细设计文档

**版本**: v1.0
**日期**: 2026-03-18
**作者**: Compute Resource Allocation Architect
**输入**: 架构骨架 v1.0 (第 4 章) + PRD v1.0 (EPIC-4/5, FR-013~021, FR-028~029) + 分配器分析报告

---

## 第 1 章：ServerAllocatorChain 详细设计

### 1.1 与现有 HostAllocatorChain 的对应关系

现有 HostAllocatorChain 的默认策略（`LeastVmPreferredHostAllocatorStrategyFactory`）包含 13 个 Allocator Flow + 3 个 Sort Flow。下表列出每个 Flow 与新 ServerAllocatorChain 的映射关系。

#### 1.1.1 现有 Allocator Flow 清单（执行顺序）

| # | 现有 Flow | 职责 | 新引擎处理方式 |
|---|-----------|------|--------------|
| 1 | `AttachedL2NetworkAllocatorFlow` | 按 L3→L2→Cluster 过滤宿主机 | **不适用** — KVM 专有逻辑，两阶段薄适配模式下由阶段2的现有 HostAllocatorChain 处理 |
| 2 | `DesignatedHostAllocatorFlow` | 按 zone/cluster/host 过滤 | **拆分复用** — ZoneFilterFlow + ClusterFilterFlow 覆盖 zone/cluster 过滤；指定 server 由 ServerAllocatorSpec.serverUuid 直接处理 |
| 3 | `QuotaAllocatorFlow` | 配额检查 | **不适用** — PhysicalServer 层面不做配额检查（配额在 Consumer 层由各角色自行处理） |
| 4 | `BackupStorageSelectPrimaryStorageAllocatorFlow` | 按备份存储选主存储 | **不适用** — 存储分配是 VM 层面概念，PhysicalServer 分配不涉及 |
| 5 | `HostStateAndHypervisorAllocatorFlow` | state=Enabled, status=Connected, hypervisorType 过滤 | **适配** — StateFilterFlow 对齐，过滤 PhysicalServerVO.state=Enabled, status=Connected；hypervisorType 由 RoleTypeFilterFlow 替代 |
| 6 | `ImageBackupStorageAllocatorFlow` | 镜像备份存储过滤 | **不适用** — 镜像是 VM/BM 实例层面概念 |
| 7 | `HostCapacityAllocatorFlow` | CPU/Memory 容量过滤 | **适配** — CapacityFilterFlow，查询 PhysicalServerCapacityVO 替代 HostCapacityVO |
| 8 | `AttachedVolumePrimaryStorageAllocatorFlow` | 已挂载卷的主存储过滤 | **不适用** — 卷挂载是 VM 层面概念 |
| 9 | `HostPrimaryStorageAllocatorFlow` | 宿主机可访问的主存储过滤 | **不适用** — 主存储可达性是 VM 层面概念 |
| 10 | `AvoidHostAllocatorFlow` | 排除指定主机 | **直接复用** — AvoidServerFilterFlow，逻辑完全一致 |
| 11 | `TagAllocatorFlow` | 系统标签过滤 | **不适用** — VM 系统标签过滤，两阶段薄适配模式下由阶段2的现有 HostAllocatorChain 处理 |
| 12 | `ResourceBindingAllocatorFlow` | 资源绑定过滤 | **不适用** — 资源绑定是 VM 层面概念，两阶段薄适配模式下由阶段2处理 |
| 13 | `FilterFlow` | 执行 HostAllocatorFilterExtensionPoint 扩展 | **不适用** — 角色特有扩展过滤由阶段2处理；ServerAllocatorChain 只做通用过滤，不需要 ExtensionFilterFlow |
| 14 | `HostOsVersionAllocatorFlow`（仅 Migrate 策略） | 迁移场景 OS 版本检查 | **不适用** — VM 迁移特有 |
| 15 | `LastHostAllocatorFlow`（仅 Designated/LastHost 策略） | 优先上次运行的宿主机 | **不适用** — VM 重启策略特有 |

#### 1.1.2 现有 Sort Flow 清单

| # | 现有 Sort Flow | 职责 | 新引擎处理方式 |
|---|---------------|------|--------------|
| 1 | `PrimaryStoragePrioritySortFlow` | 主存储优先级排序 | **不适用** — 存储概念 |
| 2 | `SoftAvoidHostSortFlow` | 软排除降优先级 | **适配** — SortFilterFlow 中实现软排除逻辑 |
| 3 | `LeastVmPreferredSortFlow` | 最少 VM 优先 | **不适用** — VM 特有排序策略；ServerAllocatorChain 默认随机排序 |

### 1.2 ServerAllocatorChain Flow 执行顺序

```
ServerAllocatorChain Flow 执行顺序（7 个通用 Flow）：

两阶段薄适配模式下，ServerAllocatorChain 只做通用过滤，不包含任何角色特有 Flow。
已移除：SchedulingModeFilterFlow（非必要）、ExtensionFilterFlow（角色特有逻辑由阶段2处理）、
SortFilterFlow（排序和容量预留由阶段2的 HostSortorChain 处理）。

┌─────────────────────────────────────────────────────────────────────────┐
│ #1  ZoneFilterFlow          [新增]                                      │
│     首个 Flow，从 DB 加载候选 PhysicalServerVO 列表                     │
│     按 spec.zoneUuid 过滤（若为 null 则加载所有 Zone 的服务器）         │
├─────────────────────────────────────────────────────────────────────────┤
│ #2  ClusterFilterFlow       [适配自 DesignatedHostAllocatorFlow]        │
│     按 spec.clusterUuid 过滤                                           │
│     JOIN PhysicalServerRoleVO.clusterUuid                               │
├─────────────────────────────────────────────────────────────────────────┤
│ #3  PoolFilterFlow          [新增]                                      │
│     按 spec.poolUuid 过滤 PhysicalServerVO.poolUuid                    │
├─────────────────────────────────────────────────────────────────────────┤
│ #4  RoleTypeFilterFlow      [新增]                                      │
│     按 spec.requiredRoleType 过滤                                      │
│     JOIN PhysicalServerRoleVO.roleType + roleStatus='Active'            │
├─────────────────────────────────────────────────────────────────────────┤
│ #5  StateFilterFlow         [适配自 HostStateAndHypervisorAllocatorFlow]│
│     过滤 state=Enabled, status=Connected                               │
├─────────────────────────────────────────────────────────────────────────┤
│ #6  AvoidServerFilterFlow   [直接复用 AvoidHostAllocatorFlow 模式]      │
│     排除 spec.avoidServerUuids                                         │
├─────────────────────────────────────────────────────────────────────────┤
│ #7  CapacityFilterFlow      [适配自 HostCapacityAllocatorFlow]          │
│     按 requiredCpu/Memory/Disk 过滤 PhysicalServerCapacityVO            │
│     应用超分比（通过 ServerCapacityOverProvisioningManager）            │
│     应用系统预留（通过 ServerReservedCapacityExtensionPoint）           │
└─────────────────────────────────────────────────────────────────────────┘

阶段1输出：候选 PhysicalServer UUID 列表
  → 通过 PhysicalServerRoleVO 映射回 HostVO UUID 集合
  → 注入 HostAllocatorSpec.candidateHostUuids
  → 阶段2: 现有 HostAllocatorChain 在预筛选集合上正常执行（L2/PS/BS/Tag 等 KVM Flow 全部保留）
```

### 1.3 Flow 的接口定义

所有 Flow 继承 `AbstractServerAllocatorFlow`（见架构骨架 4.3 节）。每个 Flow 实现 `allocate()` 方法，通过 `next(candidates)` 传递候选列表到下一个 Flow，或通过 `fail(reason)` 终止链路。

```java
/**
 * Flow 通用约定：
 * 1. 首个 Flow（ZoneFilterFlow）的 candidates 为 null，需从 DB 加载
 * 2. 后续 Flow 的 candidates 为上一个 Flow 输出的候选列表
 * 3. 任何 Flow 输出空列表即终止链路，返回 NO_AVAILABLE_SERVER 错误
 * 4. Flow 之间通过 spec.extraData 传递中间状态
 */
```

### 1.4 各 Flow 具体过滤逻辑

#### 1.4.1 ZoneFilterFlow（首个 Flow，从 DB 加载）

```java
@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class ZoneFilterFlow extends AbstractServerAllocatorFlow {
    @Autowired
    private DatabaseFacade dbf;

    @Override
    public void allocate() {
        // 首个 Flow：从 DB 加载候选列表
        SimpleQuery<PhysicalServerVO> query = dbf.createQuery(PhysicalServerVO.class);
        if (spec.getZoneUuid() != null) {
            query.add(PhysicalServerVO_.zoneUuid, Op.EQ, spec.getZoneUuid());
        }
        if (spec.getServerUuid() != null) {
            // 指定分配，直接定位
            query.add(PhysicalServerVO_.uuid, Op.EQ, spec.getServerUuid());
        }

        List<PhysicalServerVO> ret = query.list();
        if (ret.isEmpty()) {
            fail("no physical server found" +
                (spec.getZoneUuid() != null ? " in zone[" + spec.getZoneUuid() + "]" : "") +
                (spec.getServerUuid() != null ? " with uuid[" + spec.getServerUuid() + "]" : ""));
        } else {
            next(ret);
        }
    }
}
```

#### 1.4.2 ClusterFilterFlow

```java
/**
 * 按 clusterUuid 过滤。
 *
 * 逻辑：JOIN PhysicalServerRoleVO，筛选 roleVO.clusterUuid = spec.clusterUuid。
 * 一台 PhysicalServer 可通过不同角色属于不同 Cluster，
 * 因此需要确保 requiredRoleType 对应的角色在目标 Cluster 中。
 *
 * 如果 spec.clusterUuid 为 null，则跳过此 Flow。
 */
@Override
public void allocate() {
    throwExceptionIfIAmTheFirstFlow();

    if (spec.getClusterUuid() == null) {
        skip();
        return;
    }

    String sql = "select ps from PhysicalServerVO ps, PhysicalServerRoleVO role" +
        " where ps.uuid = role.serverUuid" +
        " and role.clusterUuid = :clusterUuid" +
        " and ps.uuid in (:candidateUuids)";

    // 若同时指定了 roleType，在此一并过滤
    if (spec.getRequiredRoleType() != null) {
        sql += " and role.roleType = :roleType";
    }

    TypedQuery<PhysicalServerVO> q = dbf.getEntityManager().createQuery(sql, PhysicalServerVO.class);
    q.setParameter("clusterUuid", spec.getClusterUuid());
    q.setParameter("candidateUuids", getServerUuidsFromCandidates());
    if (spec.getRequiredRoleType() != null) {
        q.setParameter("roleType", spec.getRequiredRoleType());
    }

    List<PhysicalServerVO> ret = q.getResultList();
    if (ret.isEmpty()) {
        fail("no physical server found in cluster[" + spec.getClusterUuid() + "]");
    } else {
        next(ret);
    }
}
```

#### 1.4.3 PoolFilterFlow（新增）

```java
/**
 * 按 poolUuid 过滤。
 *
 * 逻辑：直接过滤 PhysicalServerVO.poolUuid。
 * 如果 spec.poolUuid 为 null，则跳过此 Flow。
 */
@Override
public void allocate() {
    throwExceptionIfIAmTheFirstFlow();

    if (spec.getPoolUuid() == null) {
        skip();
        return;
    }

    List<PhysicalServerVO> ret = candidates.stream()
        .filter(ps -> spec.getPoolUuid().equals(ps.getPoolUuid()))
        .collect(Collectors.toList());

    if (ret.isEmpty()) {
        fail("no physical server found in pool[" + spec.getPoolUuid() + "]");
    } else {
        next(ret);
    }
}
```

#### 1.4.4 RoleTypeFilterFlow（新增）

```java
/**
 * 按 requiredRoleType 过滤。
 *
 * 逻辑：JOIN PhysicalServerRoleVO，筛选：
 *   - roleType = spec.requiredRoleType
 *   - roleStatus = 'Active'
 *
 * 如果 spec.requiredRoleType 为 null，则跳过此 Flow。
 *
 * 注意：如果 ClusterFilterFlow 已经做了 JOIN + roleType 过滤，
 * 此 Flow 会自动跳过（通过 spec.extraData 标记避免重复 JOIN）。
 */
@Override
public void allocate() {
    throwExceptionIfIAmTheFirstFlow();

    if (spec.getRequiredRoleType() == null) {
        skip();
        return;
    }

    // 如果 ClusterFilterFlow 已经做了 roleType 过滤，跳过
    if (Boolean.TRUE.equals(spec.getExtraData().get("roleTypeFiltered"))) {
        skip();
        return;
    }

    String sql = "select distinct ps from PhysicalServerVO ps, PhysicalServerRoleVO role" +
        " where ps.uuid = role.serverUuid" +
        " and role.roleType = :roleType" +
        " and role.roleStatus = 'Active'" +
        " and ps.uuid in (:candidateUuids)";

    TypedQuery<PhysicalServerVO> q = dbf.getEntityManager().createQuery(sql, PhysicalServerVO.class);
    q.setParameter("roleType", spec.getRequiredRoleType());
    q.setParameter("candidateUuids", getServerUuidsFromCandidates());

    List<PhysicalServerVO> ret = q.getResultList();
    if (ret.isEmpty()) {
        fail("no physical server with active role[" + spec.getRequiredRoleType() + "] found");
    } else {
        next(ret);
    }
}
```

#### 1.4.5 StateFilterFlow

```java
/**
 * 过滤 state 和 status。
 * 对齐 HostStateAndHypervisorAllocatorFlow，但不过滤 hypervisorType。
 */
@Override
public void allocate() {
    throwExceptionIfIAmTheFirstFlow();

    List<PhysicalServerVO> ret = candidates.stream()
        .filter(ps -> PhysicalServerState.Enabled == ps.getState()
                   && PhysicalServerStatus.Connected == ps.getStatus())
        .collect(Collectors.toList());

    if (ret.isEmpty()) {
        fail("no Enabled+Connected physical server found in " + candidates.size() + " candidates");
    } else {
        next(ret);
    }
}
```

#### 1.4.6 SchedulingModeFilterFlow

```java
// SchedulingModeFilterFlow 已移除（非必要，RoleTypeFilterFlow 已覆盖角色类型过滤）
```

#### 1.4.7 AvoidServerFilterFlow

```java
/**
 * 排除 avoidServerUuids。
 * 完全对齐 AvoidHostAllocatorFlow 逻辑。
 */
@Override
public void allocate() {
    throwExceptionIfIAmTheFirstFlow();

    if (spec.getAvoidServerUuids() == null || spec.getAvoidServerUuids().isEmpty()) {
        skip();
        return;
    }

    List<PhysicalServerVO> ret = candidates.stream()
        .filter(ps -> !spec.getAvoidServerUuids().contains(ps.getUuid()))
        .collect(Collectors.toList());

    if (ret.isEmpty()) {
        fail("after ruling out avoided servers" + spec.getAvoidServerUuids() +
             ", no server left in candidates");
    } else {
        next(ret);
    }
}
```

#### 1.4.8 CapacityFilterFlow（原 1.4.9）

> **说明**：原设计中的 ExtensionFilterFlow 已移除。两阶段薄适配模式下，ServerAllocatorChain
> 不包含任何角色特有扩展过滤。KVM 的 L2/Tag/ResourceBinding 过滤由阶段2的现有
> HostAllocatorChain 执行，无需在阶段1中通过桥接扩展点处理。

```java
/**
 * 按 CPU/Memory/Disk 容量过滤。
 * 对齐 HostCapacityAllocatorFlow，但查询 PhysicalServerCapacityVO。
 *
 * 超分比已预计算到 PhysicalServerCapacityVO.totalCpu/totalMemory，
 * 因此过滤时直接比较 availableCpu/availableMemory。
 *
 * 系统预留通过 ServerReservedCapacityExtensionPoint 获取后扣减。
 */
@Override
public void allocate() {
    throwExceptionIfIAmTheFirstFlow();

    List<PhysicalServerVO> ret;

    if (spec.getSchedulingMode() == SchedulingMode.INTERNAL_EXCLUSIVE) {
        // ========== 独占模式：不检查 CPU/Memory 容量，只检查是否已被占用 ==========
        ret = candidates.stream()
            .filter(ps -> {
                PhysicalServerCapacityVO cap = ps.getCapacity();
                // 无容量记录或状态为 Initialized（硬件未发现）均允许独占分配
                if (cap == null) return true;
                // 已被独占（Allocated）的不可再分配
                return cap.getCapacityState() != CapacityState.Allocated;
            })
            .collect(Collectors.toList());

        if (ret.isEmpty()) {
            fail("no available physical server for exclusive allocation" +
                 " (all candidates already allocated)");
        } else {
            next(ret);
        }
    } else {
        // ========== 共享模式：正常 CPU/Memory/Disk 容量检查 ==========
        long requiredCpu = spec.getRequiredCpu();
        long requiredMemory = spec.getRequiredMemory();
        long requiredDisk = spec.getRequiredDisk();

        // 获取系统预留容量
        Map<String, ReservedServerCapacity> reservedMap = getReservedCapacities(
            getServerUuidsFromCandidates());

        ret = candidates.stream()
            .filter(ps -> {
                PhysicalServerCapacityVO cap = ps.getCapacity();
                if (cap == null) return false;

                ReservedServerCapacity reserved = reservedMap.getOrDefault(
                    ps.getUuid(), ReservedServerCapacity.ZERO);

                boolean cpuOk = requiredCpu == 0 ||
                    (cap.getAvailableCpu() - reserved.getReservedCpu()) >= requiredCpu;
                boolean memOk = requiredMemory == 0 ||
                    (cap.getAvailableMemory() - reserved.getReservedMemory()) >= requiredMemory;
                boolean diskOk = requiredDisk == 0 ||
                    (cap.getAvailableDisk() - reserved.getReservedDisk()) >= requiredDisk;

                return cpuOk && memOk && diskOk;
            })
            .collect(Collectors.toList());

        if (ret.isEmpty()) {
            fail("no physical server with available capacity" +
                 " cpu[" + requiredCpu + "], memory[" + requiredMemory + " bytes]" +
                 ", disk[" + requiredDisk + " bytes] found");
        } else {
            next(ret);
        }
    }
}

private Map<String, ReservedServerCapacity> getReservedCapacities(List<String> serverUuids) {
    Map<String, ReservedServerCapacity> result = new HashMap<>();
    for (ServerReservedCapacityExtensionPoint ext :
            pluginRgty.getExtensionList(ServerReservedCapacityExtensionPoint.class)) {
        for (String uuid : serverUuids) {
            ReservedServerCapacity rc = ext.getReservedCapacity(uuid);
            result.merge(uuid, rc, ReservedServerCapacity::add);
        }
    }
    return result;
}
```

> **说明**：原设计中的 SortFilterFlow 已移除。两阶段薄适配模式下，排序和容量预留
> 由阶段2的 HostSortorChain + reserveCapacity 处理。阶段1的 ServerAllocatorChain
> 输出的是未排序的候选 PhysicalServer 列表，映射回 HostVO UUID 集合后注入
> HostAllocatorSpec.candidateHostUuids，由现有 HostAllocatorChain 继续处理。

### 1.5 Flow 依赖关系

```
ZoneFilterFlow
  ↓ (提供初始候选列表)
ClusterFilterFlow ← 依赖 PhysicalServerRoleVO.clusterUuid
  ↓
PoolFilterFlow ← 依赖 PhysicalServerVO.poolUuid
  ↓
RoleTypeFilterFlow ← 依赖 PhysicalServerRoleVO.roleType
  ↓
StateFilterFlow ← 依赖 PhysicalServerVO.state/status
  ↓
AvoidServerFilterFlow ← 无外部依赖
  ↓
CapacityFilterFlow ← 依赖 PhysicalServerCapacityVO + ServerReservedCapacityExtensionPoint
  ↓
(阶段1输出：候选 PhysicalServer 列表)
  ↓ 映射回 HostVO UUID 集合
  ↓ 注入 HostAllocatorSpec.candidateHostUuids
(阶段2：现有 HostAllocatorChain 正常执行)
```

**关键约束**：
- ClusterFilterFlow 和 RoleTypeFilterFlow 都需要 JOIN `PhysicalServerRoleVO`，当两者条件同时存在时，ClusterFilterFlow 会合并 roleType 条件一并查询，并设置 `spec.extraData.put("roleTypeFiltered", true)` 避免 RoleTypeFilterFlow 重复 JOIN。
- CapacityFilterFlow 必须在 StateFilterFlow 之后，确保只对可用服务器检查容量。
- CapacityFilterFlow 是阶段1的最后一个 Flow。排序和容量预留由阶段2的 HostSortorChain 处理，不在阶段1中执行。

---

## 第 2 章：PhysicalServerCapacityUpdater 详细设计

**核心定位**：PhysicalServerCapacityUpdater 是容量写入的唯一入口。PhysicalServerCapacityVO 是容量的唯一真表（source of truth），HostCapacityVO 降级为 MySQL VIEW。现有 HostCapacityUpdater 是本组件的包装器（不是反过来），59 个调用方零改动，内部通过 PhysicalServerRoleVO 查找 serverUuid 后委托本组件执行写入。

### 2.1 类结构

```java
package org.zstack.server.capacity;

/**
 * 物理服务器容量更新器 — 容量写入的唯一入口。
 *
 * PhysicalServerCapacityVO 是容量的 source of truth。
 * HostCapacityUpdater 是本组件的包装器，保留原有 API 不变，
 * 内部通过 RoleVO 查找 serverUuid 后委托本组件。
 *
 * 实现模式对齐 HostCapacityUpdater：
 * - @Transactional 在私有方法 _run() 上
 * - @DeadlockAutoRestart 在公开方法 run() 上
 * - PESSIMISTIC_WRITE 锁保证行级别并发安全
 *
 * 增加的能力：
 * - 按 SchedulingMode 分支处理容量扣减
 * - recalculateCapacity() 实现税收模式全量重计算
 *   （直接操作 PhysicalServerCapacityVO，无需同步到 HostCapacityVO）
 */
@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class PhysicalServerCapacityUpdaterImpl implements PhysicalServerCapacityUpdater {

    @Autowired
    private DatabaseFacade dbf;
    @Autowired
    private PluginRegistry pluginRgty;

    private String serverUuid;
    private PhysicalServerCapacityVO capacityVO;
    private PhysicalServerCapacityVO originalCopy;
}
```

### 2.2 悲观锁获取策略

```java
/**
 * 锁获取策略：
 *
 * 1. 锁粒度：PhysicalServerCapacityVO 行级锁（与原 HostCapacityUpdater 一致）
 * 2. 单表锁：只锁 PhysicalServerCapacityVO（HostCapacityVO 已降级为 VIEW，无需加锁）
 * 3. 锁超时：依赖 MySQL innodb_lock_wait_timeout（默认 50s），
 *    @DeadlockAutoRestart 自动重试
 *
 * 死锁防护：
 * - 只有一张容量真表需要加锁，不存在双表锁顺序问题
 * - @DeadlockAutoRestart 捕获 DeadlockLoserDataAccessException 自动重试
 */
private boolean lockCapacity() {
    capacityVO = dbf.getEntityManager().find(
        PhysicalServerCapacityVO.class,
        serverUuid,
        LockModeType.PESSIMISTIC_WRITE
    );

    if (capacityVO != null) {
        originalCopy = copyCapacity(capacityVO);
    }

    return capacityVO != null;
}
```

### 2.3 INTERNAL_SHARED 模式的扣减逻辑

```java
/**
 * INTERNAL_SHARED 模式（典型：KVM）：
 *
 * 扣减公式：
 *   availableCpu -= requiredCpu
 *   availableMemory -= requiredMemory (已经过超分比计算)
 *   availableDisk -= requiredDisk
 *
 * 与 HostCapacityReserveManagerImpl.reserveCapacityWithChecking() 对齐：
 * 1. 先扣减 available
 * 2. 检查扣减后是否低于系统预留
 * 3. 低于预留则抛 UnableToReserveServerCapacityException
 */
@Override
public void decreaseCapacity(String serverUuid, long requiredCpu,
                              long requiredMemory, long requiredDisk) {
    this.serverUuid = serverUuid;

    // 查询该 server 上 active role 的 schedulingMode
    SchedulingMode mode = getActiveSchedulingMode(serverUuid);

    run(cap -> {
        if (mode == SchedulingMode.INTERNAL_EXCLUSIVE) {
            // INTERNAL_EXCLUSIVE: 清零
            cap.setAvailableCpu(0);
            cap.setAvailableMemory(0);
            cap.setAvailableDisk(0);
        } else {
            // INTERNAL_SHARED: 按需扣减
            long availCpu = cap.getAvailableCpu() - requiredCpu;
            long availMemory = cap.getAvailableMemory() - requiredMemory;
            long availDisk = cap.getAvailableDisk() - requiredDisk;

            // 下溢保护
            if (requiredCpu != 0 && availCpu < 0) {
                throw new UnableToReserveServerCapacityException(
                    "no enough CPU on server[" + serverUuid + "]");
            }
            if (requiredMemory != 0 && availMemory < 0) {
                throw new UnableToReserveServerCapacityException(
                    "no enough memory on server[" + serverUuid + "]");
            }

            cap.setAvailableCpu(availCpu);
            cap.setAvailableMemory(availMemory);
            cap.setAvailableDisk(Math.max(availDisk, 0));
        }
        return cap;
    });
}
```

### 2.4 INTERNAL_EXCLUSIVE 模式的清零逻辑

```java
/**
 * INTERNAL_EXCLUSIVE 模式（典型：BM）：
 *
 * 分配时整机独占 — 所有可用量清零。
 * 释放时从 RoleProvider.getCapacityConsumption() 重计算。
 *
 * 语义：
 * - 分配：availableCpu = 0, availableMemory = 0, availableDisk = 0
 * - 释放：触发 recalculateCapacity()（因为可能有其他 INTERNAL_SHARED 角色）
 */
```

### 2.5 EXTERNAL_READONLY 模式的容量处理

```java
/**
 * EXTERNAL_READONLY 模式（典型：K8s/Container）：
 *
 * ZStack 不通过自身分配引擎分配 Container 工作负载，但容量消耗计入 available。
 *
 * decreaseCapacity()：Container sync 时正常扣减，通过 PhysicalServerCapacityUpdater 执行
 * increaseCapacity()：Container Pod 删除时正常归还
 * recalculateCapacity()：所有角色（包括 EXTERNAL_READONLY）的 getCapacityConsumption() 都参与征税
 *
 * 语义：available = 总容量 - 所有角色消耗（包括 Container） - 系统预留，不能超配
 */
```

### 2.6 @DeadlockAutoRestart 的使用方式

```java
/**
 * @DeadlockAutoRestart 与 @Transactional 的分离：
 *
 * ZStack 框架约束：DbDeadlockAspect.aj 在编译时检查，
 * 同一方法不能同时标注 @Transactional 和 @DeadlockAutoRestart。
 *
 * 解决方案（对齐 HostCapacityUpdater 模式）：
 * - 公开方法 run() 标注 @DeadlockAutoRestart
 * - 私有方法 _run() 标注 @Transactional
 * - run() 调用 _run()，AspectJ 织入后：
 *   DeadlockAutoRestart 包裹 run() → run() 调用 _run() → @Transactional 包裹 _run()
 *
 * 实际代码结构：
 */

@Transactional
private boolean _run(PhysicalServerCapacityUpdaterRunnable runnable) {
    if (!lockCapacity()) {
        logDeletedServer();
        return false;
    }

    PhysicalServerCapacityVO cap = runnable.call(capacityVO);
    if (cap != null) {
        capacityVO = cap;
        dbf.getEntityManager().merge(capacityVO);
        logCapacityChange();
        return true;
    }
    return false;
}

@DeadlockAutoRestart
public boolean run(PhysicalServerCapacityUpdaterRunnable runnable) {
    return _run(runnable);
}
```

### 2.7 归还容量

```java
/**
 * 归还容量（VM 停止、BM 实例删除等场景）。
 *
 * INTERNAL_SHARED：直接增加 available（上限不超过 total）
 * INTERNAL_EXCLUSIVE：触发全量重计算（因为其他角色可能也在使用）
 * EXTERNAL_READONLY：正常归还（Container Pod 删除时）
 */
@Override
public void increaseCapacity(String serverUuid,
                              long releasedCpu, long releasedMemory, long releasedDisk) {
    this.serverUuid = serverUuid;

    SchedulingMode mode = getActiveSchedulingMode(serverUuid);

    if (mode == SchedulingMode.INTERNAL_EXCLUSIVE) {
        // 独占模式释放时做全量重计算
        recalculateCapacity(serverUuid);
        return;
    }

    // INTERNAL_SHARED: 增加 available
    run(cap -> {
        long availCpu = Math.min(cap.getAvailableCpu() + releasedCpu, cap.getTotalCpu());
        long availMemory = Math.min(cap.getAvailableMemory() + releasedMemory, cap.getTotalMemory());
        long availDisk = Math.min(cap.getAvailableDisk() + releasedDisk, cap.getTotalDisk());

        cap.setAvailableCpu(availCpu);
        cap.setAvailableMemory(availMemory);
        cap.setAvailableDisk(availDisk);
        return cap;
    });
}
```

---

## 第 3 章：ServerCapacityOverProvisioningManager 详细设计

### 3.1 对齐 HostCpuOverProvisioningManager 的实现模式

```java
package org.zstack.server.capacity;

/**
 * 超分比管理器实现。
 *
 * 对齐现有实现模式：
 * - HostCpuOverProvisioningManagerImpl：CPU 超分比，全局 Integer + per-host ConcurrentHashMap
 * - HostCapacityOverProvisioningManagerImpl：Memory 超分比，全局 Double + per-host ConcurrentHashMap
 *
 * 统一管理器合并 CPU 和 Memory 超分比到一个类中：
 * - CPU 超分比：int 类型（典型值 1~20，表示倍数）
 * - Memory 超分比：double 类型（典型值 1.0~1.5，表示倍数）
 */
public class ServerCapacityOverProvisioningManagerImpl
        implements ServerCapacityOverProvisioningManager {

    // ---- CPU 超分比 ----
    private double cpuGlobalRatio;
    private ConcurrentHashMap<String, Double> serverCpuRatio = new ConcurrentHashMap<>();

    // ---- Memory 超分比 ----
    private double memoryGlobalRatio = 1.0;
    private ConcurrentHashMap<String, Double> serverMemoryRatio = new ConcurrentHashMap<>();

    @Autowired
    private DatabaseFacade dbf;
    @Autowired
    private CloudBus bus;
    @Autowired
    private ResourceConfigFacade rcf;
}
```

### 3.2 全局默认值 + per-server 覆盖机制

```java
/**
 * 优先级链（与 HostCpuOverProvisioningManager.getRatio() 完全一致）：
 *
 * 1. per-server 覆盖值（ConcurrentHashMap 缓存 + ResourceConfig 持久化）
 * 2. GlobalConfig 全局默认值
 *
 * GlobalConfig 定义（在 ServerAllocatorGlobalConfig 中）：
 *   server.cpu.overProvisioning.ratio  默认值 10  (int)
 *   server.memory.overProvisioning.ratio  默认值 1.0 (double)
 *
 * per-server 覆盖通过 ResourceConfig 实现（对齐 HostGlobalConfig.HOST_CPU_OVER_PROVISIONING_RATIO
 * 的 ResourceConfig 模式），存储在 ResourceConfigVO 中。
 */

@Override
public double getCpuRatio(String serverUuid) {
    Double r = serverCpuRatio.get(serverUuid);
    return r != null ? r :
        rcf.getResourceConfigValue(
            ServerAllocatorGlobalConfig.CPU_OVER_PROVISIONING_RATIO,
            serverUuid, Double.class);
}

@Override
public double getMemoryRatio(String serverUuid) {
    Double r = serverMemoryRatio.get(serverUuid);
    return r != null ? r :
        rcf.getResourceConfigValue(
            ServerAllocatorGlobalConfig.MEMORY_OVER_PROVISIONING_RATIO,
            serverUuid, Double.class);
}

@Override
public long calculateCpuByRatio(String serverUuid, long physicalCpu) {
    double ratio = getCpuRatio(serverUuid);
    return Math.round(physicalCpu * ratio);
}

@Override
public long calculateMemoryByRatio(String serverUuid, long physicalMemory) {
    double ratio = getMemoryRatio(serverUuid);
    return Math.round(physicalMemory * ratio);
}
```

### 3.3 超分比变化触发容量重计算的事件链

**设计决策**：超分比变化时**统一通过 RecalculatePhysicalServerCapacityMsg 触发重计算**，不直接裸写 SQL 更新容量值。这与 HostCpuOverProvisioningManagerImpl 的改造一致——现有 3 处裸 JPQL UPDATE（`update HostCapacityVO cap set cap.totalCpu = ...`）全部删除，改为触发重计算消息。

理由：
1. HostCapacityVO 已是 VIEW，不能直接 UPDATE
2. 容量写入应通过 PhysicalServerCapacityUpdater 统一入口，不应绕过
3. 重计算路径自带悲观锁和 @DeadlockAutoRestart，并发安全

```
全局超分比变化事件链：
┌──────────────────────────────────────────────────────────────────┐
│ 1. 管理员修改 GlobalConfig server.cpu.overProvisioning.ratio    │
│    → GlobalConfigUpdateExtensionPoint.updateGlobalConfig()       │
├──────────────────────────────────────────────────────────────────┤
│ 2. ServerCapacityOverProvisioningManagerImpl.setCpuGlobalRatio() │
│    → 更新内存缓存 cpuGlobalRatio                                 │
│    → 不直接裸写 SQL 更新 totalCpu（已删除裸 JPQL）               │
│    → 触发全量重计算                                              │
├──────────────────────────────────────────────────────────────────┤
│ 3. 发送 RecalculatePhysicalServerCapacityMsg (per zone)          │
│    → PhysicalServerManagerImpl.handle()                          │
│    → 遍历每台服务器执行 recalculateCapacity()                   │
│    → recalculateCapacity() 内部读取最新超分比，                  │
│      重算 totalCpu = totalPhysicalCpu * ratio，                  │
│      然后重算 availableCpu/availableMemory                       │
└──────────────────────────────────────────────────────────────────┘

Per-server 超分比变化事件链：
┌──────────────────────────────────────────────────────────────────┐
│ 1. 管理员设置 per-server ResourceConfig                          │
│    → ServerCapacityOverProvisioningManagerImpl.setCpuRatio()      │
├──────────────────────────────────────────────────────────────────┤
│ 2. 更新 ConcurrentHashMap 缓存                                  │
│    → 不直接裸写 SQL 更新单台 totalCpu（已删除裸 JPQL）           │
│    → 发送 RecalculatePhysicalServerCapacityMsg (单台 serverUuid) │
│    → recalculateCapacity() 使用新超分比重算                      │
└──────────────────────────────────────────────────────────────────┘

HostCpuOverProvisioningManagerImpl 改造（兼容层）：
┌──────────────────────────────────────────────────────────────────┐
│ 现有 3 处裸 JPQL UPDATE 全部删除：                                │
│   - 第 70 行: update HostCapacityVO cap                          │
│              set cap.totalCpu = cap.cpuNum * :ratio              │
│   - 第 75 行: update HostCapacityVO cap                          │
│              set cap.totalCpu = ... where cap.uuid not in (:uuids)│
│   - 第 96 行: update HostCapacityVO cap                          │
│              set cap.totalCpu = ... where cap.uuid = :huuid      │
│                                                                  │
│ 替代方案：超分比变化时发送                                        │
│   RecalculatePhysicalServerCapacityMsg                           │
│   → 全局变化: per zone 批量重计算                                 │
│   → per-host 变化: 通过 RoleVO 查找 serverUuid → 单台重计算      │
└──────────────────────────────────────────────────────────────────┘
```

---

## 第 4 章：CompatibilityBridge 详细设计（两阶段薄适配）

### 4.1 两阶段流程概述

```
AllocateHostMsg
  → CompatibilityBridge 拦截（在 HostAllocatorManagerImpl.doHandleAllocateHost() 中）
  → 阶段1: 构造 AllocateServerMsg（只提取通用字段，不含 originalMessage）
    → ServerAllocatorChain（7 个通用 Flow：Zone/Cluster/Pool/RoleType/State/Avoid/Capacity）
    → 输出候选 PhysicalServer UUID 列表
  → 映射逻辑: PhysicalServerVO UUID → PhysicalServerRoleVO → HostVO UUID 集合
  → 注入: 设置 HostAllocatorSpec.candidateHostUuids = 上述 UUID 集合
  → 阶段2: 现有 HostAllocatorChain 正常执行
    → 所有 KVM Flow（L2/PS/BS/Tag/ResourceBinding 等）在预筛选的小集合上跑
    → HostSortorChain + reserveCapacity（锁机制不变）
  → AllocateHostReply（与旧引擎返回格式完全一致）
```

**关键设计点**：
1. **不需要 originalMessage 透传** — ServerAllocatorChain 只做通用过滤，不读 KVM 特有字段
2. **不需要 ExtensionFilterFlow** — 没有桥接扩展点（KvmL2NetworkServerFilter 等），L2/Tag/ResourceBinding 过滤由阶段2的现有 Flow 处理
3. **现有 HostAllocatorChain 只跑一遍** — 候选集被阶段1提前缩小，阶段2在小集合上执行
4. **AllocateServerMsg 简化** — 去掉 originalMessage 字段，只包含通用分配参数
5. **注入方式** — 在 HostAllocatorSpec 中新增 `candidateHostUuids` 字段，`DesignatedHostAllocatorFlow` 识别此字段做预过滤

### 4.2 拦截点与两阶段实现

```java
package org.zstack.server.compatibility;

/**
 * 兼容层两阶段薄适配实现。
 *
 * 核心思路：
 * - 阶段1：ServerAllocatorChain 只做通用过滤（不读 KVM 特有字段）
 * - 阶段1输出映射回 HostVO UUID 集合，注入 HostAllocatorSpec.candidateHostUuids
 * - 阶段2：现有 HostAllocatorChain 在预筛选集合上正常执行所有 KVM Flow
 */
public class ServerAllocatorCompatibilityBridgeImpl
        implements ServerAllocatorCompatibilityBridge {

    @Autowired
    private DatabaseFacade dbf;
    @Autowired
    private CloudBus bus;

    @Override
    public boolean shouldIntercept(AllocateHostMsg msg) {
        // 1. 检查全局特性开关
        if (!ServerAllocatorGlobalConfig.ENABLE_UNIFIED_ALLOCATOR.value(Boolean.class)) {
            return false;
        }

        // 2. 按角色类型检查是否启用
        ServerRoleType roleType = inferRoleType(msg);
        if (roleType == null) {
            return false;
        }

        String enabledRoles = ServerAllocatorGlobalConfig.ENABLED_ROLE_TYPES.value(String.class);
        if ("ALL".equals(enabledRoles)) {
            return true;
        }

        return Arrays.asList(enabledRoles.split(","))
            .contains(roleType.toString());
    }

    @Override
    public void allocate(AllocateHostMsg msg,
                          ReturnValueCompletion<List<HostInventory>> completion) {

        // ==== 阶段1: ServerAllocatorChain 通用过滤 ====

        AllocateServerMsg serverMsg = new AllocateServerMsg();

        // 提取通用字段（不设置 originalMessage）
        ServerRoleType roleType = inferRoleType(msg);
        serverMsg.setRequiredRoleType(roleType);
        serverMsg.setRequiredCpu(msg.getCpuCapacity());
        serverMsg.setRequiredMemory(msg.getMemoryCapacity());
        serverMsg.setRequiredDisk(msg.getDiskSize());
        serverMsg.setDryRun(true);  // 阶段1始终 DryRun，不做容量预留

        // 从 DesignatedAllocateHostMsg 提取 zone/cluster
        if (msg instanceof DesignatedAllocateHostMsg) {
            DesignatedAllocateHostMsg dmsg = (DesignatedAllocateHostMsg) msg;
            serverMsg.setZoneUuid(dmsg.getZoneUuid());
            serverMsg.setClusterUuid(
                dmsg.getClusterUuids() != null && !dmsg.getClusterUuids().isEmpty()
                    ? dmsg.getClusterUuids().get(0) : null);
            if (dmsg.getHostUuid() != null) {
                String serverUuid = findServerUuidByRoleUuid(dmsg.getHostUuid());
                serverMsg.setServerUuid(serverUuid);
            }
        }

        // avoidHostUuids -> avoidServerUuids 转换
        if (msg.getAvoidHostUuids() != null && !msg.getAvoidHostUuids().isEmpty()) {
            List<String> serverUuids = findServerUuidsByRoleUuids(msg.getAvoidHostUuids());
            serverMsg.setAvoidServerUuids(serverUuids);
        }

        // 发送 AllocateServerMsg（阶段1）
        bus.makeLocalServiceId(serverMsg, PhysicalServerConstant.SERVICE_ID);
        bus.send(serverMsg, new CloudBusCallBack(completion) {
            @Override
            public void run(MessageReply reply) {
                if (!reply.isSuccess()) {
                    completion.fail(reply.getError());
                    return;
                }

                // ==== 映射逻辑: PhysicalServer → HostVO UUID ====
                AllocateServerReply serverReply = reply.castReply();
                List<PhysicalServerInventory> candidates = serverReply.getCandidates();
                List<String> serverUuids = candidates.stream()
                    .map(PhysicalServerInventory::getUuid)
                    .collect(Collectors.toList());

                // 通过 PhysicalServerRoleVO 映射回 HostVO UUID 集合
                List<String> candidateHostUuids = Q.New(PhysicalServerRoleVO.class)
                    .select(PhysicalServerRoleVO_.roleUuid)
                    .in(PhysicalServerRoleVO_.serverUuid, serverUuids)
                    .eq(PhysicalServerRoleVO_.roleType, roleType)
                    .eq(PhysicalServerRoleVO_.roleStatus, "Active")
                    .listValues();

                if (candidateHostUuids.isEmpty()) {
                    completion.fail(operr("no eligible host found after" +
                        " ServerAllocatorChain pre-filtering"));
                    return;
                }

                // ==== 阶段2: 现有 HostAllocatorChain 正常执行 ====
                // 注入 candidateHostUuids，在预筛选集合上跑所有 KVM Flow
                injectCandidateHostUuidsAndAllocate(msg, candidateHostUuids, completion);
            }
        });
    }

    /**
     * 将阶段1输出的 candidateHostUuids 注入到 HostAllocatorSpec，
     * 然后调用现有 HostAllocatorChain 执行阶段2。
     *
     * 注入方式：在 HostAllocatorSpec 中新增 candidateHostUuids 字段，
     * DesignatedHostAllocatorFlow 识别此字段后，只加载这些 Host 作为初始候选集。
     */
    private void injectCandidateHostUuidsAndAllocate(
            AllocateHostMsg msg,
            List<String> candidateHostUuids,
            ReturnValueCompletion<List<HostInventory>> completion) {
        // 在 msg 中设置预筛选的候选集（通过新增的 candidateHostUuids 字段）
        msg.setCandidateHostUuids(candidateHostUuids);
        // 调用现有 HostAllocatorChain（doHandleAllocateHost）
        // DesignatedHostAllocatorFlow 会识别 candidateHostUuids 做预过滤
        doHandleAllocateHost(msg, completion);
    }
}
```

### 4.3 HostAllocatorSpec.candidateHostUuids 注入机制

```java
/**
 * 在 HostAllocatorSpec 中新增 candidateHostUuids 字段。
 *
 * DesignatedHostAllocatorFlow 识别此字段后，只加载这些 Host 作为初始候选集。
 * 不改变任何现有 Flow 的逻辑，只在首个 Flow 加载候选列表时缩小范围。
 */

// HostAllocatorSpec 新增字段（header/allocator/）：
private List<String> candidateHostUuids;  // 阶段1预筛选的候选 HostVO UUID 集合

// DesignatedHostAllocatorFlow 中的变更（仅一处）：
@Override
public void allocate() {
    // ... 原有加载逻辑 ...

    // 新增：如果 candidateHostUuids 不为空，与现有 zone/cluster 过滤结果取交集
    if (spec.getCandidateHostUuids() != null && !spec.getCandidateHostUuids().isEmpty()) {
        candidates = candidates.stream()
            .filter(h -> spec.getCandidateHostUuids().contains(h.getUuid()))
            .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            fail("after intersecting with ServerAllocatorChain pre-filtered candidates," +
                 " no host left");
            return;
        }
    }

    next(candidates);
}
```

### 4.4 特性开关实现

```java
/**
 * 特性开关通过两个 GlobalConfig 控制：
 *
 * 1. server.allocator.enabled (Boolean, 默认 false)
 *    - 全局总开关
 *    - 关闭时 100% 走旧路径（HostAllocatorChain），不执行阶段1
 *
 * 2. server.allocator.enabledRoleTypes (String, 默认 "")
 *    - 启用的角色类型列表，逗号分隔
 *    - 特殊值 "ALL" 表示所有角色都走两阶段路径
 *    - 灰度策略：先 "BAREMETAL_V1,BAREMETAL_V2" → 再 "KVM_HOST" → 最后 "ALL"
 *
 * 运行时修改：
 *   GlobalConfig 支持运行时修改无需重启。
 *   修改后下一次 AllocateHostMsg 立即生效。
 *
 * 在 HostAllocatorManagerImpl.doHandleAllocateHost() 中的拦截点：
 */
// HostAllocatorManagerImpl.doHandleAllocateHost() 中：
private void doHandleAllocateHost(AllocateHostMsg msg) {
    // 检查兼容层是否应该拦截
    if (compatibilityBridge.shouldIntercept(msg)) {
        // 两阶段薄适配：阶段1通用过滤 → 映射 → 阶段2现有 Chain
        compatibilityBridge.allocate(msg, new ReturnValueCompletion<List<HostInventory>>(null) {
            @Override
            public void success(List<HostInventory> returnValue) {
                AllocateHostReply reply = new AllocateHostReply();
                reply.setHost(returnValue.get(0));
                bus.reply(msg, reply);
            }
            @Override
            public void fail(ErrorCode errorCode) {
                AllocateHostReply reply = new AllocateHostReply();
                reply.setError(errorCode);
                bus.reply(msg, reply);
            }
        });
        return;
    }

    // 旧路径（开关关闭时）
    doAllocateHostLegacy(msg);
}
```

---

## 第 5 章：容量重计算（税收模式）

### 5.1 RecalculatePhysicalServerCapacityMsg 处理逻辑

```java
/**
 * 容量重计算消息处理。
 *
 * 支持三种粒度：
 * - 单台服务器重计算：msg.serverUuid != null
 * - 某个 Zone 下全量重计算：msg.zoneUuid != null
 * - 某个 Pool 下全量重计算：msg.poolUuid != null
 *
 * 处理逻辑：
 */
private void handle(RecalculatePhysicalServerCapacityMsg msg) {
    List<String> serverUuids = new ArrayList<>();

    if (msg.getServerUuid() != null) {
        serverUuids.add(msg.getServerUuid());
    } else if (msg.getPoolUuid() != null) {
        serverUuids.addAll(
            Q.New(PhysicalServerVO.class)
             .select(PhysicalServerVO_.uuid)
             .eq(PhysicalServerVO_.poolUuid, msg.getPoolUuid())
             .listValues());
    } else if (msg.getZoneUuid() != null) {
        serverUuids.addAll(
            Q.New(PhysicalServerVO.class)
             .select(PhysicalServerVO_.uuid)
             .eq(PhysicalServerVO_.zoneUuid, msg.getZoneUuid())
             .listValues());
    }

    for (String serverUuid : serverUuids) {
        capacityUpdater.recalculateCapacity(serverUuid);
    }
}
```

### 5.2 税收模式计算公式

```
Available = Total - Σ(业务税) - Σ(系统税)

其中：
  Total = PhysicalCapacity × OverprovisioningRatio（已预计算到 CapacityVO.totalCpu/totalMemory）

  业务税 = Σ(RoleProvider[i].getCapacityConsumption(serverUuid))
    - KVM: 所有运行中 VM 的 cpuNum + memorySize 之和
    - BM_V1: 如果有 BM 实例，则整机消耗
    - BM_V2: 如果有 BM 实例，则整机消耗
    - Container: K8s 报告的已用容量（EXTERNAL_READONLY 模式同样参与征税）

  系统税 = Σ(ServerReservedCapacityExtensionPoint[j].getReservedCapacity(serverUuid))
    - OS 开销（内核预留内存）
    - Ceph Agent 内存占用
    - 监控 Agent 内存占用
    - 等等
```

### 5.3 recalculateCapacity 实现

```java
/**
 * 全量重计算单台服务器的容量。
 *
 * 直接操作 PhysicalServerCapacityVO（唯一容量真表），无需同步到 HostCapacityVO
 * （HostCapacityVO 是 VIEW，自动反映最新数据）。
 *
 * 执行步骤：
 * 1. 获取 PhysicalServerCapacityVO 悲观锁
 * 2. 征收业务税：遍历所有 RoleProvider
 * 3. 征收系统税：遍历所有 ServerReservedCapacityExtensionPoint
 * 4. 计算 Available = Total - 业务税 - 系统税
 * 5. 更新 PhysicalServerCapacityVO
 *
 * 边界条件：
 * - Available 不允许为负数（下限钳制到 0）
 * - EXTERNAL_READONLY 角色同样参与业务税征收（容量消耗计入 available）
 * - 如果一台服务器有多个角色，业务税取各角色消耗的并集（非叠加）
 *   例：KVM + Container 混部，KVM 消耗独立计算（共享模式），
 *        Container 消耗同样计入（available = total - KVM消耗 - Container消耗 - 系统预留）
 */
@Override
public void recalculateCapacity(String serverUuid) {
    this.serverUuid = serverUuid;

    run(cap -> {
        // ---- 1. 征收业务税 ----
        long totalUsedCpu = 0;
        long totalUsedMemory = 0;
        long totalUsedDisk = 0;

        for (PhysicalServerRoleProvider provider :
                pluginRgty.getExtensionList(PhysicalServerRoleProvider.class)) {

            // 所有角色（包括 EXTERNAL_READONLY）都参与业务税征收
            CapacityUsage usage = provider.getCapacityConsumption(serverUuid);
            if (usage != null) {
                totalUsedCpu += usage.getUsedCpu();
                totalUsedMemory += usage.getUsedMemory();
                totalUsedDisk += usage.getUsedDisk();
            }
        }

        // ---- 2. 征收系统税 ----
        long totalReservedCpu = 0;
        long totalReservedMemory = 0;
        long totalReservedDisk = 0;

        for (ServerReservedCapacityExtensionPoint ext :
                pluginRgty.getExtensionList(ServerReservedCapacityExtensionPoint.class)) {
            ReservedServerCapacity reserved = ext.getReservedCapacity(serverUuid);
            if (reserved != null) {
                totalReservedCpu += reserved.getReservedCpu();
                totalReservedMemory += reserved.getReservedMemory();
                totalReservedDisk += reserved.getReservedDisk();
            }
        }

        // ---- 3. 计算 Available ----
        long availCpu = cap.getTotalCpu() - totalUsedCpu - totalReservedCpu;
        long availMemory = cap.getTotalMemory() - totalUsedMemory - totalReservedMemory;
        long availDisk = cap.getTotalDisk() - totalUsedDisk - totalReservedDisk;

        // 下限钳制到 0
        cap.setAvailableCpu(Math.max(availCpu, 0));
        cap.setAvailableMemory(Math.max(availMemory, 0));
        cap.setAvailableDisk(Math.max(availDisk, 0));

        // 记录预留值
        cap.setReservedCpu(totalReservedCpu);
        cap.setReservedMemory(totalReservedMemory);

        // 标记容量状态为 Ready
        cap.setCapacityState(CapacityState.Ready);

        return cap;
    });
}
```

---

## 第 6 章：现有分配器 Flow 复用分析

### 6.1 完整 Flow 清单与复用判定

| # | 现有 Flow 类名 | 复用判定 | 说明 |
|---|---------------|---------|------|
| 1 | `HostStateAndHypervisorAllocatorFlow` | **需要适配** | state/status 过滤对齐到 StateFilterFlow；hypervisorType 概念替换为 roleType |
| 2 | `DesignatedHostAllocatorFlow` | **需要适配** | zone/cluster 过滤拆分到 ZoneFilterFlow + ClusterFilterFlow；host 指定替换为 server 指定 |
| 3 | `HostCapacityAllocatorFlow` | **需要适配** | 查询目标从 HostCapacityVO 改为 PhysicalServerCapacityVO；超分比逻辑复用 |
| 4 | `AttachedL2NetworkAllocatorFlow` | **不适用（阶段2处理）** | L2 网络挂载是 KVM VM 专有概念；两阶段薄适配模式下由阶段2的现有 HostAllocatorChain 处理 |
| 5 | `AttachedPrimaryStorageAllocatorFlow` | **不适用** | 主存储挂载是 VM 层面概念，PhysicalServer 不涉及 |
| 6 | `AttachedVolumePrimaryStorageAllocatorFlow` | **不适用** | 卷挂载是 VM 层面概念 |
| 7 | `HostPrimaryStorageAllocatorFlow` | **不适用** | 主存储可达性是 VM 层面概念 |
| 8 | `BackupStorageSelectPrimaryStorageAllocatorFlow` | **不适用** | 备份存储是 VM/镜像层面概念 |
| 9 | `ImageBackupStorageAllocatorFlow` | **不适用** | 镜像是 VM 层面概念 |
| 10 | `AvoidHostAllocatorFlow` | **可直接复用** | 逻辑完全通用：排除指定 UUID 列表。适配为 AvoidServerFilterFlow |
| 11 | `TagAllocatorFlow` | **不适用（阶段2处理）** | VM 系统标签过滤；两阶段薄适配模式下由阶段2的现有 HostAllocatorChain 处理 |
| 12 | `ResourceBindingAllocatorFlow` | **不适用（阶段2处理）** | 资源绑定是 VM 层面概念；两阶段薄适配模式下由阶段2处理 |
| 13 | `FilterFlow` | **需要适配** | 执行扩展点的模式完全复用，扩展点接口从 HostAllocatorFilterExtensionPoint 改为 ServerAllocatorFilterExtensionPoint |
| 14 | `QuotaAllocatorFlow` | **不适用** | 配额在 Consumer 层处理 |
| 15 | `HostOsVersionAllocatorFlow` | **不适用** | VM 迁移场景特有 |
| 16 | `LastHostAllocatorFlow` | **不适用** | VM 重启策略特有 |

**Sort Flow 复用判定：**

| # | 现有 Sort Flow | 复用判定 | 说明 |
|---|--------------|---------|------|
| 1 | `PrimaryStoragePrioritySortFlow` | **不适用** | 主存储优先级排序是 VM 概念 |
| 2 | `SoftAvoidHostSortFlow` | **可直接复用** | 逻辑通用：降低指定 UUID 的优先级。集成到 SortFilterFlow |
| 3 | `LeastVmPreferredSortFlow` | **不适用** | VM 数量是 KVM 特有指标 |
| 4 | `StoppedVmAwareLeastVmPreferredSortFlow` | **不适用** | VM 特有 |
| 5 | `LastHostPreferredSortFlow` | **不适用** | VM 重启特有 |
| 6 | `RandomSortFlow` | **可直接复用** | 随机排序通用。集成到 SortFilterFlow 默认行为 |

### 6.2 适配方案

**HostStateAndHypervisorAllocatorFlow -> StateFilterFlow**：
- 保留 state=Enabled, status=Connected 过滤逻辑
- 删除 hypervisorType 过滤（由 RoleTypeFilterFlow 替代）
- 首个 Flow 从 DB 加载的职责由 ZoneFilterFlow 承担

**DesignatedHostAllocatorFlow -> ZoneFilterFlow + ClusterFilterFlow**：
- zone 过滤提升到 ZoneFilterFlow（首个 Flow，负责 DB 加载）
- cluster 过滤独立为 ClusterFilterFlow，改查 PhysicalServerRoleVO.clusterUuid
- host 指定转换为 server 指定，在 ZoneFilterFlow 中通过 spec.serverUuid 处理

**HostCapacityAllocatorFlow -> CapacityFilterFlow**：
- 查询目标从 HostCapacityVO 改为 PhysicalServerCapacityVO
- 超分比已预计算到 CapacityVO.totalCpu/totalMemory，过滤逻辑简化
- 系统预留从 HostReservedCapacityExtensionPoint 改为 ServerReservedCapacityExtensionPoint

**FilterFlow / ExtensionFilterFlow**：
- 已移除。两阶段薄适配模式下，ServerAllocatorChain 不包含角色特有扩展过滤
- KVM 的 L2/Tag/ResourceBinding 等过滤由阶段2的现有 HostAllocatorChain 执行
- 不再需要桥接扩展点（KvmL2NetworkServerFilter 等）

---

## 第 7 章：性能分析

### 7.1 PhysicalServerRoleVO JOIN 的查询计划

```sql
-- ClusterFilterFlow + RoleTypeFilterFlow 合并查询
SELECT DISTINCT ps.*
FROM PhysicalServerVO ps
INNER JOIN PhysicalServerRoleVO role ON ps.uuid = role.serverUuid
WHERE role.clusterUuid = ?
  AND role.roleType = ?
  AND role.roleStatus = 'Active'
  AND ps.uuid IN (?)

-- 索引策略：
-- PhysicalServerRoleVO 上已有：
--   INDEX(serverUuid)     — @Index 注解
--   INDEX(roleUuid)       — @Index 注解
--   INDEX(clusterUuid)    — @Index 注解
--   UNIQUE(serverUuid, roleType) — 联合唯一约束
--
-- 查询计划分析（EXPLAIN 预期）：
-- 1. 使用 uk_server_role(serverUuid, roleType) 联合索引
--    或 idx_clusterUuid 索引，取决于 where 条件选择性
-- 2. PhysicalServerRoleVO 数据量 = 物理服务器数量 × 平均角色数（≈1.1~1.5）
--    典型环境：1000 台物理机 → ~1200 行 RoleVO → 全表在缓冲池中
-- 3. JOIN 结果集 << RoleVO 总行数（cluster 过滤后通常只剩几十台）
```

### 7.2 分配链路新增延迟估算

```
现有 HostAllocatorChain 链路延迟（13 Flow，典型值）：
  ≈ 5~15ms（取决于候选宿主机数量和存储/L2 网络复杂度）

新增 ServerAllocatorChain 链路延迟（阶段1，7 个通用 Flow）：
  ┌───────────────────────────────────┬──────────┬──────────────────────────┐
  │ Flow                              │ 估算延迟 │ 说明                     │
  ├───────────────────────────────────┼──────────┼──────────────────────────┤
  │ ZoneFilterFlow                    │ 1~2ms    │ 按 zoneUuid 查 DB        │
  │ ClusterFilterFlow + RoleTypeFilter│ 1~2ms    │ JOIN PhysicalServerRoleVO│
  │ PoolFilterFlow                    │ <0.5ms   │ 内存过滤                 │
  │ StateFilterFlow                   │ <0.5ms   │ 内存过滤                 │
  │ AvoidServerFilterFlow             │ <0.5ms   │ 内存过滤                 │
  │ CapacityFilterFlow                │ 1~2ms    │ 内存过滤+预留查询        │
  ├───────────────────────────────────┼──────────┼──────────────────────────┤
  │ **阶段1总计**                     │ **3~6ms**│                          │
  └───────────────────────────────────┴──────────┴──────────────────────────┘

两阶段薄适配额外开销：
  - AllocateHostMsg → AllocateServerMsg 转换：<0.5ms（内存操作）
  - PhysicalServer → HostVO UUID 映射：<1ms（PhysicalServerRoleVO 批量查询）
  - 注入 candidateHostUuids：<0.1ms（内存操作）
  - 总阶段1额外开销：≈1.5ms

阶段2（现有 HostAllocatorChain）：
  - 在预筛选的小候选集上执行，候选集通常从数百台缩小到几十台
  - 各 KVM Flow（L2/PS/BS 等）的 DB 查询因候选集更小而更快
  - 估算延迟：3~10ms（因候选集缩小而低于原始的 5~15ms）

结论：
  - 两阶段总延迟：阶段1(3~6ms) + 映射(1.5ms) + 阶段2(3~10ms) ≈ 7.5~17.5ms
  - 对比旧路径(5~15ms)，新增 ≈2.5ms，满足 NFR-003（分配链路新增延迟 < 5ms）
  - 优势：阶段1缩小候选集后，阶段2的 KVM Flow 查询更高效
```

### 7.3 热路径优化建议

1. **PhysicalServerRoleVO 查询合并**：ClusterFilterFlow 和 RoleTypeFilterFlow 的 JOIN 查询合并为一次 SQL（通过 spec.extraData 标记），减少一次 DB 往返。

2. **PhysicalServerCapacityVO Eager Fetch**：PhysicalServerVO 通过 `@OneToOne(fetch = FetchType.EAGER)` 关联 CapacityVO，首次查询即加载容量数据，CapacityFilterFlow 无需额外查询。

3. **批量 ServerReservedCapacityExtensionPoint**：对 CapacityFilterFlow 中的系统预留查询做批量化，避免 per-server 逐个调用扩展点。将接口从 `getReservedCapacity(String serverUuid)` 扩展为 `getReservedCapacities(List<String> serverUuids)` 批量方法（保留单个方法作为默认实现）。

4. **PhysicalServer→HostVO 映射批量化**：阶段1输出到阶段2的映射通过 `IN` 查询一次完成（`SELECT roleUuid FROM PhysicalServerRoleVO WHERE serverUuid IN (...)`），避免逐个查询。

5. **阶段1 DryRun 模式**：阶段1的 ServerAllocatorChain 始终以 DryRun 模式执行，不做容量预留（容量预留由阶段2的 HostSortorChain 统一处理），避免双重容量预留问题。

---

## 附录 A：关键接口签名汇总

```java
// PhysicalServerCapacityUpdater — 容量写入的唯一入口
// HostCapacityUpdater 是它的包装器（59 个调用方零改动）
public interface PhysicalServerCapacityUpdater {
    void decreaseCapacity(String serverUuid, long requiredCpu, long requiredMemory, long requiredDisk);
    void increaseCapacity(String serverUuid, long releasedCpu, long releasedMemory, long releasedDisk);
    void recalculateCapacity(String serverUuid);  // 直接操作 PhysicalServerCapacityVO，无需同步
}

// ServerCapacityOverProvisioningManager
public interface ServerCapacityOverProvisioningManager {
    void setCpuGlobalRatio(double ratio);
    double getCpuGlobalRatio();
    void setCpuRatio(String serverUuid, double ratio);
    void deleteCpuRatio(String serverUuid);
    double getCpuRatio(String serverUuid);
    long calculateCpuByRatio(String serverUuid, long physicalCpu);
    void setMemoryGlobalRatio(double ratio);
    double getMemoryGlobalRatio();
    void setMemoryRatio(String serverUuid, double ratio);
    void deleteMemoryRatio(String serverUuid);
    double getMemoryRatio(String serverUuid);
    long calculateMemoryByRatio(String serverUuid, long physicalMemory);
}

// ServerAllocatorCompatibilityBridge（两阶段薄适配）
public interface ServerAllocatorCompatibilityBridge {
    boolean shouldIntercept(AllocateHostMsg msg);
    void allocate(AllocateHostMsg msg, ReturnValueCompletion<List<HostInventory>> completion);
    // 阶段1: ServerAllocatorChain 通用过滤 → 映射回 HostVO UUID → 注入 candidateHostUuids
    // 阶段2: 现有 HostAllocatorChain 在预筛选集合上执行所有 KVM Flow
}

// ServerReservedCapacityExtensionPoint
public interface ServerReservedCapacityExtensionPoint {
    ReservedServerCapacity getReservedCapacity(String serverUuid);
}

// PhysicalServerRoleProvider (第 3 章 SPI)
public interface PhysicalServerRoleProvider {
    ServerRoleType getRoleType();
    SchedulingMode getSchedulingMode();
    CapacityUsage getCapacityConsumption(String serverUuid);
    void onPhysicalServerCreated(String serverUuid);
    void onPhysicalServerDeleted(String serverUuid);
    RoleInventory getInventory(String roleUuid);
    String matchExistingServer(RoleMatchContext context);
}
```

## 附录 B：GlobalConfig 定义

```java
package org.zstack.server.allocator;

public class ServerAllocatorGlobalConfig {

    @GlobalConfigDef(
        category = "physicalServer",
        name = "allocator.enabled",
        defaultValue = "false",
        description = "是否启用统一分配引擎两阶段薄适配（CompatibilityBridge 总开关）"
    )
    public static GlobalConfig ENABLE_UNIFIED_ALLOCATOR;

    @GlobalConfigDef(
        category = "physicalServer",
        name = "allocator.enabledRoleTypes",
        defaultValue = "",
        description = "启用的角色类型列表（逗号分隔，ALL 表示全部）"
    )
    public static GlobalConfig ENABLED_ROLE_TYPES;

    @GlobalConfigDef(
        category = "physicalServer",
        name = "cpu.overProvisioning.ratio",
        defaultValue = "10",
        description = "CPU 超分比全局默认值"
    )
    @ResourceConfigDef
    public static GlobalConfig CPU_OVER_PROVISIONING_RATIO;

    @GlobalConfigDef(
        category = "physicalServer",
        name = "memory.overProvisioning.ratio",
        defaultValue = "1",
        description = "Memory 超分比全局默认值"
    )
    @ResourceConfigDef
    public static GlobalConfig MEMORY_OVER_PROVISIONING_RATIO;
}
```
