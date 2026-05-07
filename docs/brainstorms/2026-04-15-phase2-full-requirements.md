# Phase 2 Full Scope Requirements: 统一硬件管理容量+分配+兼容+角色补全

> **Date**: 2026-04-15
> **Branch**: `feature/unifi-host-dev`
> **Phase 1 Status**: Tasks 1-11 complete (VO/CRUD/ServerPool/ProvisionNetwork/KvmRoleProvider stub/tests)
> **Decision**: Phase 2 全量 — 容量基础设施 + 分配引擎 + CompatibilityBridge + BM2/Container RoleProvider 补全
> **PRD Sources**: 5 docs at `prd/v5.5.18-unified-hardware/` on GitLab `jiajian.chi/cloud_prd`
> **Verdict**: `docs/design-verdict-2026-04-14.md`

---

## 1. PRD Story 冲突解决

### Conflict 1: HostCapacityVO -> VIEW 迁移风险

**问题**: capacity PRD 声称 6 个写路径，但存在 3 处 read-modify-write 模式会在 VIEW 上失败。

**验证结果**: 6 个显式写路径，全部在 `compute/src/main/java/org/zstack/compute/allocator/`:
- W1: `HostAllocatorManagerImpl:289-313` — `new HostCapacityVO()` + `dbf.persist()`
- W2: `HostAllocatorManagerImpl:287,315-335` — `dbf.findByUuid()` + modify + `dbf.update()`
- W3: `HostCapacityUpdater:75-96` — PESSIMISTIC_WRITE lock + `merge()`
- W4-W6: `HostCpuOverProvisioningManagerImpl:70,75,96` — 3 条 JPQL `update HostCapacityVO`

**额外隐式写路径（review 发现）**:
- W3a: `HostCapacityUpdaterRunnable` 回调 — 所有 `HostCapacityUpdater.run()` 调用方（HostAllocatorManagerImpl:249/:836, HostCapacityReserveManagerImpl:253/:289）通过回调间接写入
- W3b: `ReportHostCapacityExtensionPoint.reportHostCapacity()` — 返回 HostCapacityVO 用于 persist，premium 模块可能有不可见实现

**关键风险**:
1. W1-W3 都是 read-modify-write，VIEW 上 persist/merge/update 全部会失败
2. **HostVO @OneToOne EAGER fetch** — `HostVO.java:26-29` 声明 `@OneToOne(fetch=EAGER) @JoinColumn(name="uuid") private HostCapacityVO capacity`。HostCapacityVO 当前 PK 是 host UUID，但 PhysicalServerCapacityVO PK 是 PhysicalServerVO UUID（不同实体）。VIEW 必须通过 RoleVO JOIN 映射回 host UUID，否则所有 HostVO 加载时 capacity 为 null → 系统级故障
3. **PESSIMISTIC_WRITE on VIEW** — MySQL 不支持 `SELECT FOR UPDATE` on VIEW。Step B 前必须确认所有读取方无 `FOR UPDATE` 用法
4. **FK CASCADE 断裂** — 原 HostCapacityVO FK 到 HostEO 有 ON DELETE CASCADE。RENAME 后此 FK 在 backup 表上，VIEW 无 FK。需 CascadeExtensionPoint 处理 Host 删除时的容量清理

**解决方案**: 分两步迁移
1. **Step A**: 创建 PhysicalServerCapacityVO 真表（新表），W1-W6 + W3a/W3b 全部改为操作新表。`HostCapacityUpdaterRunnable` 接口签名保持不变（内部在 HostCapacityUpdater 中做类型转换）。审计 premium 模块所有 `ReportHostCapacityExtensionPoint` 实现
2. **Step B**: 验证无写入路径遗漏后（grep 全量确认无 PESSIMISTIC_WRITE/FOR UPDATE on HostCapacityVO），执行 VIEW 创建
3. **回滚**: `DROP VIEW HostCapacityVO`，`RENAME TABLE HostCapacityVO_backup TO HostCapacityVO`，然后执行 `RecalculateHostCapacityMsg` 全量重计算

**VIEW DDL 设计**（解决 HostVO UUID 映射 + cpuSockets/cpuCoreNum 列问题）:
```sql
-- PhysicalServerCapacityVO 列包含: uuid, totalPhysicalCpu, totalPhysicalMemory,
-- cpuSockets, cpuCoreNum, availableCpu, availableMemory, availablePhysicalMemory,
-- cpuOverprovisioningRatio, memoryOverprovisioningRatio, reservedMemory,
-- totalDisk, availableDisk, capacityState
-- 注: cpuSockets/cpuCoreNum 从 HostCapacityVO 继承，供 License 查询使用

RENAME TABLE HostCapacityVO TO HostCapacityVO_backup;

CREATE VIEW HostCapacityVO AS
  SELECT r.roleUuid AS uuid,
         c.totalPhysicalCpu * c.cpuOverprovisioningRatio AS totalCpu,
         c.totalPhysicalCpu AS cpuNum,
         c.cpuSockets,
         c.cpuCoreNum,
         c.totalPhysicalMemory * c.memoryOverprovisioningRatio AS totalMemory,
         c.availableCpu,
         c.availableMemory,
         c.totalPhysicalMemory,
         c.availablePhysicalMemory
  FROM PhysicalServerCapacityVO c
  JOIN PhysicalServerRoleVO r ON r.serverUuid = c.uuid
  WHERE r.roleType = 'KVM_HOST'
    AND r.roleUuid IN (SELECT uuid FROM HostEO WHERE deleted IS NULL);
```

**AC-V2-CAP-01**: Step A 完成后，全量编译通过，所有写路径（含隐式）全部操作 PhysicalServerCapacityVO
**AC-V2-CAP-02**: Step B 完成后，HostVO 加载时 `host.getCapacity()` 不为 null，LicenseManagerImpl 的 cpuSockets 查询返回正确值
**AC-V2-CAP-03**: `zstack/test` 和 `premium/test-premium` 现有测试 zero regression。Step B 前全量 grep 确认无 `PESSIMISTIC_WRITE`/`FOR UPDATE` on HostCapacityVO

### Conflict 2: 容量 PRD 与 Phase 1 实现的 Gap

**问题**: Phase 1 无 PhysicalServerCapacityVO，KvmRoleProvider.getCapacityConsumption() 返回空。

**解决方案**: Phase 2 按以下顺序实现
1. 创建 PhysicalServerCapacityVO 表 + DDL
2. 实现 PhysicalServerCapacityUpdater（包装 HostCapacityUpdater 模式）
3. 补全 KvmRoleProvider.getCapacityConsumption() — 从 PhysicalServerCapacityVO 读取
4. 补全 KvmRoleProvider.checkBeforeDetach() — 检查运行中 VM

**AC-V2-CAP-04**: KvmRoleProvider.getCapacityConsumption() 返回值与 HostCapacityVO 数据一致
**AC-V2-CAP-05**: PhysicalServerCapacityUpdater 使用 PESSIMISTIC_WRITE + @DeadlockAutoRestart

### Conflict 3: BM2/Container 前置依赖

**问题**: BM2 无 chassis 生命周期扩展点，Container afterSyncNodes 从未被调用。

**验证结果**:
- BM2: `BareMetal2ChassisManagerImpl` 只有 `ClusterChangeStateExtensionPoint`，无 chassis add/delete 钩子
- Container: `ContainerEndpointSyncExtensionPoint.afterSyncNodes()` 在接口定义（line 12）但 `ContainerEndpointBase` 从未调用

**解决方案**:
1. **BM2**: 新增 `BareMetal2ChassisLifecycleExtensionPoint`（接口定义在 `header/`，调用点在 `premium/baremetal2/`）
   - `afterChassisAdded(BareMetal2ChassisInventory)` — 在 `addBareMetal2Chassis` 完成后调用
   - `beforeChassisDeleted(BareMetal2ChassisInventory)` — 在 `BareMetal2ChassisDeletionMsg` handler 中调用
   - Bm2RoleProvider 实现此扩展点
2. **Container**: 修复 `ContainerEndpointBase.syncNodes()` 方法，在节点同步完成后调用 `afterSyncNodes()`
   - ContainerRoleProvider 实现 `ContainerEndpointSyncExtensionPoint`

**AC-V2-ROLE-01**: BM2 chassis 创建后自动创建 PhysicalServerVO + RoleVO
**AC-V2-ROLE-02**: BM2 chassis 删除后 RoleVO.status 变为 Stale
**AC-V2-ROLE-03**: Container NativeHost 同步后自动创建 PhysicalServerVO + RoleVO
**AC-V2-ROLE-04**: afterSyncNodes() 在 syncNodes() 完成后被调用（Bug 修复验证）

### Conflict 4: ~~CompatibilityBridge~~ → VIEW 即兼容层

**原始问题**: compat PRD 要求 Bridge 拦截 AllocateHostMsg → 转换为 AllocateServerMsg → 结果回注。

**设计讨论结论（2026-04-16）**: **不需要 Bridge**。

**核心洞察**: HostCapacityVO VIEW 迁移本身就是兼容层：
- VmAllocateHostFlow → AllocateHostMsg → HostAllocatorChain → 读 HostCapacityVO（VIEW）
- VIEW 底层查 PhysicalServerCapacityVO（真表，已包含所有角色消耗）
- VM 层的 13+ 个 AllocateHostMsg 发送方**零改动**，自动获得统一容量数据

**层次定位**:
- `AllocateHostMsg`: VM 层使用，语义是"给我一个 Host 跑 VM"，永远由 HostAllocatorChain 处理
- `AllocateServerMsg`: PhysicalServer 层使用，语义是"分配一台物理服务器"，由 ServerAllocatorChain 处理
- 两者是**不同层次的独立消息**，不存在转换关系

**混部并发安全**:
- KVM 分配: PESSIMISTIC_WRITE 锁 PhysicalServerCapacityVO → 扣减 → 解锁
- Container 消耗: afterSyncNodes → RecalculateCapacity → PESSIMISTIC_WRITE 锁同一行 → 重计算 → 解锁
- 两者锁同一行，串行执行，不会数据损坏
- K8s async sync 的脏读窗口由 Safety Buffer 覆盖（CPU max(4c, 5%), Mem max(4G, 10%)）

**FR-028/029 处置**: 从 Phase 2 scope 移除。如果未来需要"PhysicalServer 层先于 Host 层过滤"的场景，可通过 BeforeSendMessageInterceptor 改 AllocateHostMsg 路由实现，但 Phase 2 不需要。

### Conflict 5: 空壳 API 处理

**问题**: 10 个空壳 API 在 serviceConfig 注册了路由但无 handler，调用会 unknownMessage。

**解决方案**: Phase 2 **保留 serviceConfig 路由，添加 "not implemented" handler**。
- 在 PhysicalServerManagerImpl 中为 PowerOn/Off/Reset、Discover、Scan 添加 handler，返回 `operr("API not implemented in current version, planned for Phase 3")`
- 保留: API 消息类、Event 类、serviceConfig 路由（SDK 客户端可正常调用，收到明确错误而非 unknownMessage）

**AC-V2-API-01**: 调用 PowerOn/Off/Reset/Discover/Scan 返回 operr 友好错误码
**AC-V2-API-02**: SDK 中这些 Action 类正常工作，调用不会 crash（收到 typed error response）

### Conflict 6: 存量数据迁移时机

**问题**: 迁移脚本需要 PhysicalServerCapacityVO 存在。

**解决方案**: 迁移脚本分两部分
1. **DDL 迁移**（V5.5.18__schema.sql 追加）: 先建 PhysicalServerCapacityVO 表，再做 HostCapacityVO -> VIEW 改造
2. **数据迁移**（V5.5.18.1__data_migration.sql）: 为存量 Host/Chassis/NativeHost 生成 PhysicalServerVO + RoleVO + CapacityVO 记录
3. 数据迁移脚本幂等（INSERT ... ON DUPLICATE KEY UPDATE）

顺序：DDL → 写路径改造 → VIEW 创建 → 数据迁移

**AC-V2-MIG-01**: 迁移脚本幂等，重复执行不产生重复数据
**AC-V2-MIG-02**: 迁移后 QueryPhysicalServerMsg 可查到所有存量物理机
**AC-V2-MIG-03**: 迁移后 HostCapacityVO VIEW 数据与迁移前一致

### Conflict 7: AC 编号冲突

**问题**: role SPI PRD 的 AC-RS-13~16（§2.6 自动关联）与 AC-RS-14~17（§2.7 Attach/Detach）重叠。

**解决方案**: Phase 2 使用独立的 AC 编号体系 `AC-V2-{domain}-{seq}`，不再引用原始 PRD 编号。原始 PRD AC 作为参考但不作为实现依据。

---

## 2. Phase 2 Scope 与依赖图

### 2.1 实现分组（按依赖顺序）

```
Group A: 容量基础设施（无外部依赖）
├── A1: PhysicalServerCapacityVO DDL + Entity
├── A2: PhysicalServerCapacityUpdater（PESSIMISTIC_WRITE + @DeadlockAutoRestart）
├── A3: 超分比管理器（GlobalConfig + per-server override）
└── A4: 容量重计算 RecalculatePhysicalServerCapacityMsg

Group B: 写路径改造（依赖 A1-A2）
├── B1: HostAllocatorManagerImpl 3 处写路径 → PhysicalServerCapacityVO
├── B2: HostCpuOverProvisioningManagerImpl 3 条 JPQL → PhysicalServerCapacityVO
├── B3: HostCapacityUpdater 改为 PhysicalServerCapacityUpdater 包装器
└── B4: HostCapacityVO RENAME + CREATE VIEW

Group C: 分配引擎（依赖 A1）
├── C1: AllocateServerMsg / AllocateServerReply 消息定义
├── C2: ServerAllocatorChain Flow 链
│     — ZoneFilter → ClusterFilter → PoolFilter → RoleTypeFilter → StatusFilter → CapacityFilter → SortFilter
│     — 参考 HostAllocatorChain 模式但独立实现，面向 PhysicalServer 而非 Host
├── C3: ServerAllocatorFilterExtensionPoint（允许第三方注入自定义过滤）
├── C4: ServerReservedCapacityExtensionPoint（各模块声明系统级资源预留）
└── C5: AllocateServerMsg 的 CloudBus 消息路由 + PhysicalServerManagerImpl handler
    设计理由: 独立于 HostAllocatorChain 的分配引擎是统一硬件管理的核心基础设施。
    HostAllocatorChain 面向 HostVO（KVM 专用），无法原生支持 BM2/Container 的调度语义
    （INTERNAL_EXCLUSIVE 独占、EXTERNAL_READONLY 只读）。Phase 2 建立完整框架，
    Phase 3+ 可逐步将 BM2/Container 分配直接走 ServerAllocatorChain 而非桥接回旧链。

~~Group D: 兼容桥~~ — **已移除（2026-04-16 设计讨论决定）**

> **移除理由**: HostCapacityVO VIEW 本身就是"桥"。HostAllocatorChain 读 HostCapacityVO（VIEW），
> 底层查的是 PhysicalServerCapacityVO（真表，已包含所有角色消耗）。VM 层的 AllocateHostMsg
> 调用方无需知道 PhysicalServer 层的存在。AllocateServerMsg 定位为 PhysicalServer 层自用
>（AttachRole 编排、BM2 独占分配等），不会被 VmAllocateHostFlow 调用。
>
> **混部并发安全**: KVM 和 Container 对 PhysicalServerCapacityVO 的写入通过 PESSIMISTIC_WRITE
> 锁同一行，串行执行。K8s 异步 sync 的脏读窗口由 Safety Buffer 覆盖。
>
> **原 Group D 相关 FR**: FR-028（Bridge）推到 Phase 3 重新评估是否需要，FR-029（特性开关）随之取消。

Group E: BM2/Container 角色补全（可并行于 C/D）
├── E1: BareMetal2ChassisLifecycleExtensionPoint 定义 + 调用
├── E2: Bm2RoleProvider 实现扩展点
├── E3: ContainerEndpointBase.afterSyncNodes() Bug 修复
├── E4: ContainerRoleProvider 实现 afterSyncNodes
└── E5: KvmRoleProvider.getCapacityConsumption() 补全

Group F: 数据迁移 + 清理（依赖 A-E 全部）
├── F1: V5.5.18.1__data_migration.sql 幂等脚本（PhysicalServer + Role + Capacity）
├── F2: ResourceVO + AccountResourceRefVO 同步注册
├── F3: BM2 ProvisionNetwork → PhysicalServerProvisionNetworkVO 数据迁移 + 可更新 VIEW
│     — UUID 保持不变，直接迁移
│     — INSERT INTO PhysicalServerProvisionNetworkVO SELECT uuid, ..., 'GATEWAY_PXE' FROM BareMetal2ProvisionNetworkVO
│     — RENAME TABLE BareMetal2ProvisionNetworkVO TO BareMetal2ProvisionNetworkVO_backup
│     — CREATE VIEW BareMetal2ProvisionNetworkVO AS SELECT uuid, name, description, zoneUuid,
│       dhcpInterface, dhcpRangeStartIp, dhcpRangeEndIp, dhcpRangeNetmask, dhcpRangeGateway,
│       state, createDate, lastOpDate FROM PhysicalServerProvisionNetworkVO
│       WHERE type = 'GATEWAY_PXE' WITH CHECK OPTION
│     — PhysicalServerProvisionNetworkVO.type 列设 DEFAULT 'GATEWAY_PXE'
│     — VIEW 为可更新单表映射：BM2 读/写全部穿透 VIEW，零代码改动
│     — dhcpRangeNetworkCidr 无业务代码读取，VIEW 不返回此列（仅 VO getter 定义，无消费者）
│     — 装机流程按 ProvisionNetworkType 做 provider 化：
│       GATEWAY_PXE → BM2GatewayPxeProvisionProvider（封装现有 PrepareProvisionNetworkInGatewayMsg 流程）
│       STANDALONE_PXE → 预留，未来 BM1/KVM 装机接入
│       新增 type → 实现 ProvisionProvider SPI 即可扩展
│
│     ★ Cluster → ServerPool 关联重构（2026-04-16 设计）:
│     — 废弃 PhysicalServerProvisionNetworkClusterRefVO（Phase 1 产物，语义错误）
│     — 废弃 APIAttachProvisionNetworkToClusterMsg / APIDetachProvisionNetworkFromClusterMsg
│     — 新增 PhysicalServerProvisionNetworkPoolRefVO（真关联表）
│     — 新增 APIAttachProvisionNetworkToPoolMsg / APIDetachProvisionNetworkFromPoolMsg
│     — PhysicalServerProvisionNetworkInventory: attachedClusterUuids → attachedPoolUuids
│     — BM2 兼容层:
│       - BareMetal2ProvisionNetworkClusterRefVO → VIEW over PoolRef + ClusterVO JOIN (BM2 代码零改动)
│       - BM2 ClusterRef 历史数据迁移到 PoolRef（通过 Cluster.serverPoolUuid 映射）
│       - APIAttachBareMetal2ProvisionNetworkToClusterMsg 保留，handler 内部转 PoolRef
│     — Phase 1 集成测试 15 处 attachProvisionNetworkToCluster → attachProvisionNetworkToPool
│     — SDK 中 4 个 Cluster Action（未 release）直接删除
├── F4: 空壳 API "not implemented" handler（PowerOn/Off/Reset/Discover/Scan 返回 operr）
├── F5: PhysicalServerProvisionNetworkVO 去掉 OwnedByAccount 接口
└── F6: 全量回归测试
```

### 2.2 并行度与关键路径

```
时间线（无 Bridge，B4 是最后一步高风险 DDL）:
──────────────────────────────────────────────
  A1-A4 (容量基础)     │  E1-E5 (角色补全)
       │               │       │
  B1-B3 (写路径改造)   │       │
  C1-C5 (分配引擎)     │       │
       │               ├───────┘
  B4 (VIEW 创建)       │
       │               │
  F1-F4 (迁移+清理) ───┘
──────────────────────────────────────────────
```

- Group E（BM2/Container 角色补全）与 A-C 并行开发（不同模块，不同文件）
- B4（VIEW 创建）是最后一步高风险 DDL，所有写路径改造 + 分配引擎验证通过后再执行
- **无 Group D**: VIEW 本身就是兼容层，HostAllocatorChain 通过 VIEW 自动读取 PhysicalServerCapacityVO 的统一容量

### 2.3 涉及的 FR 清单

| FR | PRD | 描述 | Group |
|----|-----|------|-------|
| FR-013 | capacity | PhysicalServerCapacityVO 统一容量账本 | A |
| FR-014 | capacity | SchedulingMode 三模式调度 | A |
| FR-015 | capacity | PhysicalServerCapacityUpdater 悲观锁 | A |
| FR-016 | capacity | 超分比管理器 | A |
| FR-017 | capacity | 容量重计算 | A |
| FR-018 | capacity | AllocateServerMsg 统一分配消息 | C |
| FR-019 | capacity | ServerAllocatorChain Flow 链（7 Flows） | C |
| FR-020 | capacity | ServerAllocatorFilterExtensionPoint 分配过滤扩展点 | C |
| FR-021 | capacity | ServerReservedCapacityExtensionPoint 系统预留容量扩展点 | C |
| FR-022 | role SPI | PhysicalServerRoleProvider SPI（已 Phase 1 定义，补全实现） | E |
| FR-023 | role SPI | KVM RoleProvider 补全 | E |
| FR-025 | role SPI | BM2 RoleProvider | E |
| FR-026 | role SPI | Container RoleProvider | E |
| FR-027 | role SPI | 角色自动关联（Phase 1 已部分实现） | E |
| FR-028 | compat | ~~CompatibilityBridge~~ 已移除 — VIEW 即兼容层 | — |
| FR-029 | compat | ~~特性开关~~ 随 FR-028 取消 | — |
| FR-030 | compat | 存量数据迁移 | F |

### 2.4 Phase 2 Out of Scope（推 Phase 3）

| FR | 描述 | 原因 |
|----|------|------|
| FR-024 | BM1 RoleProvider | 已移除 — BAREMETAL_V1 枚举已在裁决中删除，不再计划实现 |
| FR-031 | QueryPhysicalServerMsg 增强（已 Phase 1 基础实现） | Phase 1 已可用 |
| FR-032 | 统一电源管理 handler | 空壳 API 先移除路由 |
| FR-033 | 统一硬件发现 handler | 空壳 API 先移除路由 |
| FR-034 | APIScanPhysicalServersMsg handler | 空壳 API 先移除路由 |
| FR-035/036 | AttachRole/DetachRole 编排（已 Phase 1 基础实现） | Phase 1 已可用 |
| 混部容量 | KVM+Container 互为系统预留 + Node Taint | 依赖 K8s REST client 基础设施 |
| UI | 所有 PRD 的 §3 UI 需求 | 后端先行 |

---

## 3. Phase 2 验收标准完整列表

### 3.1 容量基础设施 (Group A)

- **AC-V2-CAP-01**: PhysicalServerCapacityVO 与 PhysicalServerVO 共享 UUID，FK CASCADE
- **AC-V2-CAP-02**: 6 个写路径全部操作 PhysicalServerCapacityVO 真表
- **AC-V2-CAP-03**: HostCapacityVO 为只读 VIEW，47 个读取方零改动
- **AC-V2-CAP-04**: KvmRoleProvider.getCapacityConsumption() 返回值与实际容量一致
- **AC-V2-CAP-05**: PhysicalServerCapacityUpdater 使用 PESSIMISTIC_WRITE + @DeadlockAutoRestart
- **AC-V2-CAP-06**: @Transactional 和 @DeadlockAutoRestart 不在同一方法上
- **AC-V2-CAP-07**: 超分比 GlobalConfig 可配置，per-server 可覆盖
- **AC-V2-CAP-08**: 修改超分比触发容量重计算
- **AC-V2-CAP-09**: INTERNAL_SHARED 模式: available = totalPhysical * ratio - allRoleConsumption，不超配
- **AC-V2-CAP-10**: INTERNAL_EXCLUSIVE 模式: 分配时 available 直接设为 0（公式不适用），分配器从候选集排除 available=0 的服务器
- **AC-V2-CAP-11**: EXTERNAL_READONLY 容量消耗计入 available 但不参与分配引擎调度
- **AC-V2-CAP-12**: Step B 回滚验证：HostCapacityVO_backup 表在 Step B 完成后存在，DROP VIEW + RENAME 可完成回滚且无数据丢失

### 3.2 分配引擎 (Group C)

- **AC-V2-ALLOC-01**: AllocateServerMsg 按 requiredRoleType 分配正确角色类型（KVM_HOST / BAREMETAL_V2 / CONTAINER_HOST）
- **AC-V2-ALLOC-02**: ServerAllocatorChain 7 个 Flow 各自独立可测试（每个 Flow 有独立的单元测试）
- **AC-V2-ALLOC-03**: Flow 执行顺序可通过 Spring XML 配置（参考 HostAllocatorChain 的 FlowBuilder 模式）
- **AC-V2-ALLOC-04**: ServerAllocatorFilterExtensionPoint 可被外部模块实现并注入自定义过滤逻辑
- **AC-V2-ALLOC-05**: ServerReservedCapacityExtensionPoint 可被 OS/Agent/Monitor 预留模块实现
- **AC-V2-ALLOC-06**: 分配失败返回明确错误码（包含候选集为空的原因及每个 Flow 的过滤日志）
- **AC-V2-ALLOC-07**: INTERNAL_SHARED 模式支持超分比分配，INTERNAL_EXCLUSIVE 模式独占分配，EXTERNAL_READONLY 不通过此引擎调度

### 3.3 混部并发安全 (Cross-cutting)

> VIEW 即兼容层：HostAllocatorChain 通过 HostCapacityVO（VIEW）自动读取 PhysicalServerCapacityVO
> 的统一容量数据（已包含所有角色消耗），无需 CompatibilityBridge。

- **AC-V2-MIX-01**: KVM 分配读 HostCapacityVO（VIEW）时，available 已扣除 Container 消耗
- **AC-V2-MIX-02**: KVM 和 Container 对 PhysicalServerCapacityVO 的写入通过 PESSIMISTIC_WRITE 锁同一行，不产生数据损坏
- **AC-V2-MIX-03**: Safety Buffer 可通过 GlobalConfig 配置（CPU: max(4 cores, total*5%), Memory: max(4GB, total*10%)）
- **AC-V2-MIX-04**: physicalAvailable < safetyBuffer 时 KVM 分配拒绝新 VM
- **AC-V2-MIX-05**: Container sync 频率可通过 GlobalConfig 配置（控制脏读窗口大小）

### 3.4 角色补全 (Group E)

- **AC-V2-ROLE-01**: BM2 chassis 创建后自动创建 PhysicalServerVO + RoleVO（通过新扩展点）
- **AC-V2-ROLE-02**: BM2 chassis 删除后 RoleVO.status = Stale
- **AC-V2-ROLE-03**: Container NativeHost 同步后自动创建 PhysicalServerVO + RoleVO
- **AC-V2-ROLE-04**: afterSyncNodes() 在 syncNodes() 完成后被调用
- **AC-V2-ROLE-05**: KvmRoleProvider.getCapacityConsumption() 从 PhysicalServerCapacityVO 查询
- **AC-V2-ROLE-06**: KvmRoleProvider.checkBeforeDetach() 检查运行中 VM 并拒绝（非 force）
- **AC-V2-ROLE-07**: BM2 匹配策略：ipmiAddress + zoneUuid（Phase 1 降级匹配）
- **AC-V2-ROLE-08**: Container 匹配策略：managementIp + zoneUuid（Phase 1 降级匹配）
- **AC-V2-ROLE-09**: EXCLUSIVE(BM2) 与 SHARED(KVM) 互斥检查生效

**2026-05-01 integration checkpoint**: RoleProvider integration acceptance coverage is 20/20 AC GREEN (>=95%). Scope is functional AC/IT coverage, not JaCoCo line coverage. Verified with worktree-local Maven repo:
`-Dmaven.repo.local=/home/mj/zstack-workspace/zstack-unifi-host/.m2/repository`.
Covered case set: `KvmRoleProviderIntegrationCase` (5/5), `Bm2RoleProviderIntegrationCase` (8/8), `ContainerRoleProviderIntegrationCase` + `ProcessNodeTransactionalCase` (7/7). Additional unit coverage: `Bm2RoleProviderTest`, `Bm2PhysicalServerRoleCascadeExtensionTest`, `ContainerRoleProviderTest`, `ContainerNodeInfoDiscoveryAdapterTest`.

### 3.5 数据迁移 (Group F)

- **AC-V2-MIG-01**: 迁移脚本幂等（INSERT ... ON DUPLICATE KEY UPDATE）
- **AC-V2-MIG-02**: 迁移同步注册 ResourceVO + AccountResourceRefVO
- **AC-V2-MIG-03**: 迁移后 QueryPhysicalServerMsg 查到所有存量物理机
- **AC-V2-MIG-04**: 迁移后 HostCapacityVO VIEW 数据与迁移前一致
- **AC-V2-MIG-05**: BM2 ProvisionNetwork 数据迁移后 UUID 不变，BM2 的 Query/Create/Update/Delete 全部穿透可更新 VIEW，零代码改动
- **AC-V2-MIG-06**: BareMetal2ProvisionNetworkVO VIEW 为可更新单表映射（WITH CHECK OPTION），INSERT/UPDATE/DELETE 穿透到 PhysicalServerProvisionNetworkVO
- **AC-V2-MIG-07**: PhysicalServerProvisionNetworkVO 不实现 OwnedByAccount 接口
- **AC-V2-MIG-08**: ProvisionNetwork 关联从 Cluster 改为 ServerPool — 废弃 PhysicalServerProvisionNetworkClusterRefVO，新增 PhysicalServerProvisionNetworkPoolRefVO 为唯一真关联
- **AC-V2-MIG-09**: BareMetal2ProvisionNetworkClusterRefVO 迁移为 VIEW（over PoolRef JOIN ClusterVO），BM2 24 处引用零改动
- **AC-V2-MIG-10**: APIAttachBareMetal2ProvisionNetworkToClusterMsg handler 内部转写到 PoolRef（通过 ClusterAO.serverPoolUuid 映射），Cluster 无 ServerPool 关联时返回明确错误
- **AC-V2-MIG-11**: BM2 ClusterRef 历史数据迁移脚本跳过 Cluster.serverPoolUuid 为空的记录，迁移报告列出未迁移记录

### 3.6 零回归 (Cross-cutting)

- **AC-V2-REG-01**: `mvn test` 在 compute/ 模块全部通过
- **AC-V2-REG-02**: 现有 KVM Host CRUD 操作无回归
- **AC-V2-REG-03**: 空壳 API 调用返回 operr 友好错误（not implemented），不 crash
- **AC-V2-REG-04**: HostVO 通过 `dbf.findByUuid` 加载时 `getCapacity()` 不为 null（VIEW 后验证）

---

## 4. 技术风险与缓解

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|----------|
| HostVO @OneToOne EAGER 在 VIEW 上 JOIN 失败 | 中 | **极高** | VIEW DDL 通过 RoleVO JOIN 映射 host UUID；Step B 前全量集成测试验证 HostVO 加载 |
| HostCapacityVO VIEW 有遗漏的写入/锁路径 | 中 | 高 | Step A 审计含隐式路径（HostCapacityUpdaterRunnable + ReportHostCapacityExtensionPoint）；Step B 前 grep PESSIMISTIC_WRITE；backup 表回滚 + RecalculateHostCapacityMsg |
| Premium 模块有不可见的 ReportHostCapacityExtensionPoint 实现 | 中 | 高 | Step A 在 premium 模块全量 grep；HostCapacityUpdaterRunnable 签名保持不变做内部转换 |
| FK CASCADE 断裂：Host 删除不清理 PhysicalServerCapacityVO | 中 | 中 | CascadeExtensionPoint 或 RoleProvider.onPhysicalServerRoleDetaching() 处理清理 |
| BM2 ExtensionPoint 影响现有 BM2 流程 | 低 | 高 | 扩展点调用在核心流程完成后（post-hook），不阻塞 |
| Container afterSyncNodes 修复引入副作用 | 低 | 中 | 修复点精确：仅在 syncNodes() 末尾添加一行调用 |
| 存量数据迁移丢失 serialNumber | 高 | 低 | 使用 managementIp 确定性 UUID 兜底 |

---

## 5. 关键设计约束（从裁决文档继承）

1. **保留字段**: powerStatus、oobPort、architecture、physicalLocation、networkTopology、schedulingMode、specification、firmwareVersion、healthStatus、usedCpu/usedMemory（见 verdict）
2. **已删除字段**: cpuModel/cpuSockets/cpuCores（从 PhysicalServerAO 删除，硬件发现写 DetailVO）、BAREMETAL_V1 枚举值、roleConfig、usedDisk
3. **不修改现有文件**: 遵守 NFR-005 Git Blame 保护，所有变更通过新文件或最小侵入式修改
4. **Java 8 约束**: 无 lambda 类型推断、无 var 关键字、无 java.time
