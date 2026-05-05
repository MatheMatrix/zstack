---
title: v5.5.18 Phase 2 PRD audit — capacity / role-SPI / cleanup
date: 2026-04-27
plan: docs/plans/2026-04-27-001-feat-v5518-phase2-prd-audit-plan.md
feature_branch_head: df04159439
cloud_prd_head: f9928ec75b13efb328b246c9abcfc46d192fde5a
audit_method: 3 parallel subagents (compute-resource-allocator + 2× hardware-unified-arch-lead) → main session merge → reviewer pass deferred
---

# v5.5.18 Phase 2 PRD audit — capacity / role-SPI / cleanup

## Counts

| PRD | ✅ | ⚠️ | ❌ | 🔁 | 🅿 | total |
|---|---|---|---|---|---|---|
| capacity | 9 | 4 | 8 | 5 | 0 | 26 |
| role-SPI | 8 | 7 | 8 | 1 | 0 | 24 |
| cleanup | 12 | 2 | 5 | 0 | 3 | 22 |
| **Total** | **29** | **13** | **21** | **6** | **3** | **72** |

> Status semantics: ✅ 完全实现 / ⚠️ 偏离规约 / ❌ 缺失 / 🔁 已知 deferred (R2 Group C / AC-RS-13-P2) / 🅿 PRD stale per ADR (Q2 决策, ROLLBACK 三条)

## Executive summary

Phase 2 主体落地良好：
- **容量真表 + VIEW 化已完成** — `HostCapacityVO @Immutable` + schema VIEW MERGE + COALESCE 半迁移、W1-W6 全部改写到 `PhysicalServerCapacityVO`、W3 NB-22/24/30 fail-loud + 锁 key 全对
- **SPI v3 五方法签名 + 三家 implements + Attach/Detach API + AutoAssociator 算法 + HardwareDiscoveryScheduler** 都在
- **Schema 迁移整体 OK** — Step 0 Pool init / Step 1+ PS·Role / vcenter option C / BM V1 跳过 / ResourceVO+ARR / admin-only AccountRef + 注解 全过

但有 3 大 P0 blocker、9 大 P1 gap：
- **路径 2 (传统 AddHost/AddChassis)** 没接通 PRD §2.2/§2.3/§2.4 三个 Flow（AutoAssociateFlow / CreatePhysicalServerRoleFlow / InitPhysicalServerCapacityFlow）。AutoAssociator 算法在但**无 production 调用方**。`AddKVMHostMsg.serverUuid` / `AddBareMetal2ChassisMsg.serverUuid` 字段加了但 handler 不读 — carrier-only field。直接影响 AC-RS-04 / AC-RS-07 / AC-RS-10
- **统一电源管理 handler 完全不存在** — `PhysicalServerManagerImpl.handleApiMessage` 没 dispatch `APIPowerOn/Off/ResetPhysicalServerMsg`，serviceConfig 路由到此但落到 `bus.dealWithUnknownMessage` else-branch，runtime `unknownMessage` 错误。AC-CB-14/15/16 全 ❌
- **APIDiscoverPhysicalServerHardwareMsg handler 缺** — service `PhysicalServerHardwareService.discoverHardware` ready but no API entry. AC-CB-18 ❌
- **Container 容量管道完全断** — `getCapacityConsumption()` 返 0、`Cordon service` 不存在、Pod 聚合公式 (`requestsCpu/requestsMemory`) 缺、`KubernetesNodeInventory` 缺 `systemUUID/capacity/allocatable`。AC-CM-04/08/13-19 + AC-RS-10/11/12 全级联 ❌

## Cross-PRD overlap dedup

5 个 cross-PRD overlap 已在 audit 中识别并 dedup（status 在 owning audit 评，另一边 reference）：

| Overlap | Owning audit | Reference side | 状态 |
|---|---|---|---|
| W7/W8 Container ReportContainerCapacityMsg / Pod 聚合 | capacity | role-SPI | ❌ (capacity 评) |
| HardwareDiscoveryScheduler 限流队列 | role-SPI | cleanup | ✅ (role-SPI 评) |
| AC-CM-13/14 (Cordon 熔断) | capacity | cleanup PRD §8.1 (M21 dedup) | ❌ (capacity 评) |
| `PhysicalServerHardwareService` 3 private discover | role-SPI (SPI 内部) | cleanup (handler 接线) | ⚠️ + ❌ (各评不同部分) |
| `@Action(adminOnly=true)` 注解扫 + admin accountUuid | cleanup | role-SPI | ✅ (cleanup 评) |

## Critical-gap list — 按 Owner 分组（Phase 3 fix-plan 输入骨架）

### `container-module-architect` — 7 个 P0/P1 (最大块，整条 Container 容量管道断)

| AC | severity | gap | suggested fix-scope |
|---|---|---|---|
| AC-CM-14 | P0 | `ContainerNodeCordonService` 完全不存在；`CoreV1Api.patchNode`/`spec.unschedulable`/cordon/uncordon 0 hits | 新建 `ContainerNodeCordonService` + 整合 `CoreV1Api.patchNode` + label `zstack.io/cordoned-by=capacity` + 重试×3 + 迟滞 buffer/2×buffer + RBAC 自检 |
| AC-CM-13 | P0 | Safety Buffer 动态扣减点缺 | 在 `PhysicalServerCapacityUpdater.recalculate()` 末段按公式扣减（CPU max(4, cpuNum×5%) / Mem max(4G, totalMem×10%)），从 GlobalConfig 读 |
| AC-CM-15 | P1 | `SelfSubjectAccessReview` 自检缺 | `ContainerEndpointBase` 注册路径加 self-check，缺 RBAC → `ContainerManagementEndpointVO.capability=ReadOnly` + WARN log |
| AC-CM-16 | P1 | uncordon 路径不区分 ZStack-cordoned vs 运维 cordon | 仅 uncordon 带 `zstack.io/cordoned-by=capacity` label 的 cordon，依赖 AC-CM-14 |
| AC-CM-17 | P0 | `KubernetesPodInventory.requestsCpu/requestsMemory` 字段缺 + `max(Σinit, Σmain) + overhead` 公式缺 | 在 `getKubernetesPodInventory()` 加新字段独立填充，不动既有 `cpuNum/memorySize`(reads `limits[0]`) |
| AC-CM-18 | P1 | 疑点 1/2/3 unit-test 缺 | `KubernetesNativeProvider.java:189,195-196,200-201` 验证 multi-container/initContainers/overhead 三疑点；fix in 独立 PR |
| AC-CM-19 | P2 | 防 cpuNum/memorySize 语义 regression | 配套 AC-CM-18 的 unit-test 同时断言现 limits 行为不变 |
| AC-CM-04 | P0 | Container `getCapacityConsumption` 返 0（U14-DEFERRED-LOCK） | 接通 Pod aggregation；依赖 AC-CM-17 |
| AC-CM-08 | P0 | EXTERNAL_READONLY 容量未计入 available | 同 AC-CM-04，`PhysicalServerCapacityUpdater.recalculate()` 消费 Container `getCapacityConsumption` |
| AC-RS-10 | P0 | `processNodeTransactional` 完全未实现（5 步原子） | `ContainerEndpointBase.syncNodesFromCluster` 内每 node 走 `@Transactional` 方法：1. AutoAssociator.findOrCreate, 2. persist NativeHostVO, 3. 幂等 upsert PhysicalServerRoleVO, 4. init PhysicalServerCapacityVO |
| AC-RS-11 | P0 | EXTERNAL_READONLY 容量计入 available 不调度 | 同 AC-CM-08 修法 |
| AC-RS-12 | P1 | `KubernetesNodeInventory.systemUUID/machineID/capacity.cpu/capacity.memory/allocatable.cpu/allocatable.memory` 全缺 | 扩展 `NativeProvider.listNodes()` 从 `V1NodeStatus.capacity/allocatable` + `V1NodeSystemInfo.systemUUID/machineID` 提取 |

### `baremetal2-architect` — 3 个 P0 + 2 个 P1

| AC | severity | gap | suggested fix-scope |
|---|---|---|---|
| AC-CB-14 | P0 | `APIPowerOnPhysicalServerMsg` handler 不存在 | `PhysicalServerManagerImpl.handleApiMessage` 加 dispatch + `HostIpmiPowerExecutor`-style 调用；NB-10 PS Manager 不引入 KVM 类型 |
| AC-CB-15 | P0 | 电源操作不更新 `PhysicalServerVO.powerStatus` | 依赖 AC-CB-14 |
| AC-CB-16 | P0 | 无 OOB 凭据时 operr fallback 不存在 | `if ps.oobAddress == null → operr("for KVM hosts use APIPowerResetHostMsg")` (NB-10) |
| AC-RS-07 | P0 | `BareMetal2ChassisManagerImpl.handle(APIAddBareMetal2ChassisMsg)` 不读 `msg.serverUuid` 不创建 RoleVO/PS | 老 Add 流程尾追 3 Flow（同 KVM 路径 2 修法） |
| AC-RS-08 | P1 | `Bm2RoleProvider.createRoleEntity` 硬编码 `provisionType=Remote` 不映射弹性/绑定双模式 | `roleConfig.get("provisionType")` 透传，缺 default Remote |
| AC-CM-07 | P1 | `CapacityUsage.exclusive=true` 没 consumer 在 PSC zero `availableCpu/availableMemory` | `PhysicalServerCapacityUpdater.recalculate()` 内 BM2 分支 zero |

### `hardware-unified-arch-lead` — 5 个 P0/P1（FlowChain 编排根因 + handler 接线）

| AC | severity | gap | suggested fix-scope |
|---|---|---|---|
| AC-RS-04+07+10 共同根因 | P0 | PRD §2.2/§2.3/§2.4 的 6 个 Flow 全部缺失（grep `class.*Flow` 0 hits） | 设计 + 实现 `AutoAssociateFlow / CreatePhysicalServerRoleFlow / InitPhysicalServerCapacityFlow` 三 Flow + post-commit `enqueueDiscovery` hook（KVM 与 BM2 共用）；Container 用 `processNodeTransactional` 走单 @Transactional（PRD §2.4 NB-7） |
| AC-CB-18 | P0 | `APIDiscoverPhysicalServerHardwareMsg` handler 缺 (service ready) | `PhysicalServerManagerImpl.handleApiMessage` 加 `instanceof APIDiscoverPhysicalServerHardwareMsg` 分支 → `hardwareService.discoverHardware(msg.getUuid())` + Event reply |
| Manager dispatcher | P1 | EXTERNAL_READONLY 不提前拒绝 → Container `createRoleEntity` 抛错栈给用户 | `if (provider.getSchedulingMode() == EXTERNAL_READONLY) return operr(...)` 在 attach handler 提前 |
| AC-RS-20 一致性 | P1 | Attach handler 缺 post-commit `enqueueDiscovery` hook | 同 FlowChain 的 post-commit hook（与 §2.5b NB-4 一致） |
| §2.5b 3 private discover | P2 | `PhysicalServerHardwareService` 3 个 discover + `persistHardwareInfo` 仍 stub (U15 deferred) | 实装 `ipmiFruDiscover/kvmAgentDiscover/k8sNodeInfoDiscover`；建 `PhysicalServerHardwareInfoVO` 真表（schema 已声明的 detail 表外加汇总表） |
| AC-CB-09 | P1 | 确定性 UUID 算法漂移 (`MD5(src.uuid+'-ps')` vs PRD `MD5(mgmtIp+zoneUuid)`) | 二选一对齐 — 改 schema 或改 PRD/ADR-011 |
| AC-CB-08 | P1 | `serialNumber` 三个 INSERT 全 NULL | BM2 块 `LEFT JOIN BareMetal2HardwareInfoVO` 提取；KVM/Native 留 NULL 由 discover-time 回填 |
| AC-CB-M18 / NB25 logging | P2 | 迁移日志 "BM V1 chassis count: N, skipped" / "vcenter ESXi hosts migrated: N rows" 缺 | 加 `SELECT … INTO @cnt; INSERT INTO logging table` 或 Java-side post-migrate hook |
| AC-CB-Step0a/0b | P3 | Pool name drift (`bm2-pool-<uuid8>` vs `bm2-<name>-pool`; `default-pool` vs `default-shared-pool`) | 二选一对齐（cosmetic） |

### `kvm-host-expert` — 1 个 P0 + 2 个 P1

| AC | severity | gap | suggested fix-scope |
|---|---|---|---|
| AC-RS-04 | P0 | `HostManagerImpl.doAddHost` 不读 `AddKVMHostMsg.serverUuid` 不创建 RoleVO/PS | 同 hardware-unified-arch-lead 的 FlowChain 3 Flow（KVM 模块侧接入点） |
| AC-RS-05 | P1 | `DeleteHostMsg` 不 cascade SQL 删 `PhysicalServerRoleVO`（孤儿 RoleVO） | `HostManagerImpl.handle(DeleteHostMsg)` 内同事务 SQL 级联删 RoleVO（与 PRD §2.2 注销规约一致）|
| AC-RS-14 | P1 | `SYSTEM_SERIAL_NUMBER` SystemTag 没注入 RoleMatchContext | 在 KVM PostHostConnect 钩子构建 `RoleMatchContext` 时 `setSerialNumber(HostSystemTags.SYSTEM_SERIAL_NUMBER.getTokenByResourceUuid(...))` |

### `compute-resource-allocator` — 1 个 P0 + 2 个 P1

| AC | severity | gap | suggested fix-scope |
|---|---|---|---|
| AC-CM-04 | P0 | `PhysicalServerCapacityUpdater.recalculate()` 不存在 | 新建：`available = total - Σ kvmConsumption - Σ containerConsumption - systemReserved - safetyBuffer`，PESSIMISTIC_WRITE 锁 PSC by serverUuid，调 RoleProvider.getCapacityConsumption + ServerReservedCapacityExtensionPoint |
| AC-CM-11 | P1 | `HostCpuOverProvisioningManagerImpl.getRatio` 未读 `PhysicalServerCapacityVO.cpuOverprovisioningRatio` 列 | 修 read path: per-server override 优先 PSC 列，fallback ResourceConfig |
| AC-CM-PERF-01 | P1 | `EXPLAIN SELECT ... FROM HostCapacityVO WHERE uuid=?` 证据 + 1000-host 性能数字 | Phase 3 性能验证（标设计层 ✅，PR 时附 EXPLAIN 输出） |

## Deferred items confirmed (不进 fix list)

- 🔁 `AC-AL-01..05`: ServerAllocator R2 Group C → v5.5.18.x（plan §Scope Boundaries）
- 🔁 `AC-RS-13-P2`: 跨角色 serialNumber 归一化 → v1.1+（PRD §8.5）
- 🅿 `AC-CB-ROLLBACK-01..03`: PRD stale per ADR-007（Q2 user 决策，PRD 应改写删 backup 期待）

## Phase 3 fix-plan U-unit 候选骨架

按 Owner 分组建议直接映射 fix-plan U-unit（每 critical-gap 1 unit，总 ~22 unit）。Phase 3 fix-plan 应优先：

**Wave 1 — P0 unblock (并行)**:
- U1 → `kvm-host-expert` + `baremetal2-architect` + `container-module-architect` + `hardware-unified-arch-lead` 协调：FlowChain 3 Flow 实装 (AC-RS-04/07/10)
- U2 → `baremetal2-architect`：3 个 power handler + IPMI executor (AC-CB-14/15/16)
- U3 → `hardware-unified-arch-lead`：APIDiscoverPhysicalServerHardwareMsg handler dispatch (AC-CB-18)
- U4 → `compute-resource-allocator`：PhysicalServerCapacityUpdater.recalculate() (AC-CM-04)
- U5 → `container-module-architect`：KubernetesPodInventory.requestsCpu/Memory + 聚合公式 (AC-CM-17/18/19)
- U6 → `container-module-architect`：KubernetesNodeInventory 字段扩展 (AC-RS-12)

**Wave 2 — P0 cordon stack (depends U4+U5)**:
- U7 → `container-module-architect`：`ContainerNodeCordonService` + RBAC self-check (AC-CM-14/15/16)
- U8 → `container-module-architect`：`processNodeTransactional` (AC-RS-10)
- U9 → `container-module-architect`：getCapacityConsumption 接通 Pod aggregation (AC-CM-08, AC-RS-11)

**Wave 3 — P1 一致性**:
- U10 → `kvm-host-expert`：DeleteHostMsg cascade RoleVO + SYSTEM_SERIAL_NUMBER 注入 RoleMatchContext (AC-RS-05/14)
- U11 → `baremetal2-architect`：BM2 ProvisionType 弹性/绑定映射 + INTERNAL_EXCLUSIVE consumer (AC-RS-08, AC-CM-07)
- U12 → `compute-resource-allocator`：超分比 read path 绑 PSC 列 (AC-CM-11)
- U13 → `hardware-unified-arch-lead`：post-commit enqueueDiscovery hook + EXTERNAL_READONLY dispatcher 提前拒绝
- U14 → `hardware-unified-arch-lead`：UUID 算法 / pool naming / serialNumber 提取对齐 (AC-CB-09/Step0a/Step0b/08)
- U15 → `hardware-unified-arch-lead`：迁移日志 (AC-CB-M18/NB25)
- U16 → `hardware-unified-arch-lead`：3 private discover 实装 (§2.5b)

**Wave 4 — Phase 3 性能 + PRD 维护**:
- U17 → `compute-resource-allocator`：AC-CM-PERF-01 EXPLAIN 验证（集成测试）
- U18 → upstream cloud_prd 维护者：PRD 改写删 ROLLBACK-01..03 + 选定 UUID 算法 + 选定 pool naming（不在本仓 scope）

## Audit metadata

- **Trigger commit**: `df04159439` (feature/unifi-host-dev)
- **PRD source pin**: cloud_prd `f9928ec75b13efb328b246c9abcfc46d192fde5a` (NB-1..34 final consolidation)
- **本 plan**: [docs/plans/2026-04-27-001-feat-v5518-phase2-prd-audit-plan.md](../plans/2026-04-27-001-feat-v5518-phase2-prd-audit-plan.md)
- **Sub-reports source** (in tool responses, not files): 3 audit subagents
  - Capacity → `compute-resource-allocator` (~125K token, 37 tool uses)
  - Role-SPI → `hardware-unified-arch-lead` (~146K token, 54 tool uses)
  - Cleanup → `hardware-unified-arch-lead` (~116K token, 27 tool uses)
- **Read-only sweep**: `git status --porcelain -- '*.java' '*.sql' '*.xml' '*.groovy'` 三 audit 后均空，主 session merge 后亦空
- **Q3=B reviewer pass**: deferred — 3 audit subagent 报告 file:line 全有，silent-agreement 风险已抑制；user 时间约束下 Phase 3 fix-plan 实装时再做 spot-verify
- **Total ACs counted**: 72（plan 假设 ~63，超出 +14% — 因 audit 期间额外打开 §2.1 M8 BlockReason 表 + §2.5b NB-4/19 + Step0a/0b/AdminUUID/NB15 等 sub-AC，仍在 reasonable 范围内）

## Audit method notes

3 个 audit 在 main session 一次 fan-out 并行 dispatch（per plan §High-Level Technical Design）。3 chunk 在 tool response 中返回 markdown table；本 index 在 main session 直接合并 + dedup 5 个 cross-PRD overlap，无独立 lead 收尾。

跨 audit 的同 file 多处引用（典型：`KubernetesNativeProvider.java` 在 capacity 与 role-SPI 各自独立 grep）结论一致 — 该文件相关 AC 一致打 ❌（Pod 字段缺 / Node 字段缺）。无矛盾 status，不需 disputed re-grep。

`ADR-013 cross-check` (cleanup audit 显式跑): `BareMetal2ProvisionNetworkClusterRefVO_backup` 在 schema 里**确认不存在**（schema 头注 L18-19 显式声明 "No `*_backup` tables are retained"）。ADR-013 已完整落地，确认 ROLLBACK-01..03 标 🅿 是 PRD 应改而非代码应修。
