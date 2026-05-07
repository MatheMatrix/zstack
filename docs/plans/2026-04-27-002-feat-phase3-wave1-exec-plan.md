---
title: v5.5.18 Phase 3 Wave 1 execution — B-tier 5 unit + test coverage + C/D sync
type: feat
status: active
date: 2026-04-27
parent_plan: docs/plans/2026-04-28-001-fix-phase2-prd-gaps-plan.md
parent_audit: docs/audits/2026-04-27-phase2-prd-audit.md
adr_dependencies: ADR-012 (preGeneratedRoleUuid), ADR-014 (incremental rebuild)
feature_branch_head: 6f2103c5de
---

# v5.5.18 Phase 3 Wave 1 execution — B-tier 5 unit + test coverage + C/D sync

> 本计划是 master fix-plan 的 Wave 1 操作级展开。Master 留作 4-Wave 战略级骨架。
> Wave 1 共 9 unit，本计划只覆盖 **B-tier 5 unit**（U1a / U1b / U4 / U5 / U6）+ 测试编排 + C/D-tier 同步机制。U1c / U2 / U3 因 implementation gap 未消，留 Wave 1 内的二阶段（独立 plan）。

## Overview

把 Phase 2 audit 暴露的 5 个 P0 / P1 gap（path 2 KVM/BM2 接 FlowChain、PhysicalServerCapacityUpdater 缺、Container Pod/Node 字段缺）落到代码 + 集成测试 + 决策清单。U1-lead 已落（commit `6f2103c5de`），3 Flow + Hook SPI 现成可调用；本计划 5 unit 都消费这些 contract。

**为啥分 B-tier / C/D-tier 两轮**：master plan §Open Questions 里 8 个 unit 还有未确认依赖（IPMI executor 名 / Event 形态 / K8s SDK / SPI 是否存在 / VO 表关系等）。B-tier 5 unit 全是 spot-check 后的 path，可直接干；C/D-tier 8 unit 需 producer 先做 5-10min 探索再起手，独立 plan 处理。

## Problem Frame

3 P0 + 2 P1 gap 集中在 path 2 + 容量真表 + Container 数据采集：

- AC-RS-04 — `HostManagerImpl.doAddHost` 不读 `AddKVMHostMsg.serverUuid` 不创建 RoleVO/PS（U1a）
- AC-RS-07 — `BareMetal2ChassisManagerImpl.handle(APIAddBareMetal2ChassisMsg)` 同上（U1b）
- AC-CM-04 — `PhysicalServerCapacityUpdater.recalculate()` 不存在（U4）
- AC-CM-17/18/19 — `KubernetesPodInventory.requestsCpu/Memory` 缺 + `max(Σinit, Σmain) + overhead` 公式缺（U5）
- AC-RS-12 — `KubernetesNodeInventory.systemUUID/machineID/capacity/allocatable` 缺（U6）

**集成测试盲点**（master plan §"Honest gaps" 里识别的）：

- Path 2 rollback 路径（mid-chain failure 时是否清理）没测
- Path 1 attach + path 2 AddHost 同 PS 并发场景没测
- Pod 聚合公式三疑点（multi-container / initContainers / overhead）AC-CM-18 unit 测缺
- Container Node systemUUID → AutoAssociator tier-1 match 链没测

本计划补齐。

## Requirements Trace

- **R1** — 闭合 5 个 in-scope AC（AC-RS-04 / AC-RS-07 / AC-CM-04 / AC-CM-17-19 / AC-RS-12）
- **R2** — Wave 1 collective integration gate：3 Phase 2D case + 4 个新 case 全绿
- **R3** — 每新 / 改 case 提交前过 `reviewing-automation-cases` skill review
- **R4** — 遵守铁律 12（改 header 后 mvn clean install）+ ADR-012 normative ordering（U1a/b 调用 contract 时遵守）
- **R5** — 5 unit 之间无依赖，可并行 ultrawork dispatch（U1-lead 已落是前置）
- **R6** — Producer ≠ Reviewer（CLAUDE.md anti-self-evaluation）
- **R7** — C/D-tier 8 unit 不在本计划 implement，仅在 §C/D Sync Checklist 列待决问题

## Scope Boundaries

**In scope**：

- 5 B-tier impl unit (U1a / U1b / U4 / U5 / U6)
- 4 个新 integration case + 2 个现有 case 扩 assert
- 1 个 unit test (U4 PhysicalServerCapacityUpdaterTest)
- §C/D Sync Checklist（决策待办，无代码）

**Out of scope**：

- U1c (Container processNodeTransactional) — D-tier `@Transactional` proxy 边界 + `afterSyncNodes` 交互未追，留 Wave 1 二阶段
- U2 (Power handler) — C-tier IPMI executor 类名 + 接口未确，留 Wave 1 二阶段
- U3 (APIDiscoverPhysicalServerHardwareMsg dispatch) — C-tier Event 形态未读，留 Wave 1 二阶段
- Wave 2 / Wave 3 / Wave 4 全部 unit
- ADR / PRD 改写

### Deferred to Separate Tasks

- **Wave 1 二阶段 plan**（U1c / U2 / U3）：等本计划落地 + spot-check 完成后起新 plan
- **C/D sync checklist 落地**：每条问题对应 Wave 2-3 unit 起前 producer 自审

## Context & Research

### Relevant Code and Patterns

- `compute/src/main/java/org/zstack/compute/host/HostManagerImpl.java:375-453` — 现 `doAddHost` 已用 `FlowChain`，U1a 在尾部接 3 Flow
- `premium/baremetal2/src/main/java/org/zstack/baremetal2/BareMetal2ChassisManagerImpl.java` — BM2 add chassis handler，U1b 同模式接入
- `compute/src/main/java/org/zstack/compute/allocator/HostCapacityUpdater.java` — U4 复用其 lock + retry 模板
- `plugin/physicalServer/src/main/java/org/zstack/server/flow/` — U1-lead 已落 3 Flow + DataKey
- `plugin/physicalServer/src/main/java/org/zstack/server/PhysicalServerManagerImpl.java:433-499` — path 1 attach handler，是 ADR-012 ordering 的实现样板
- `header/src/main/java/org/zstack/header/server/PhysicalServerEnqueueDiscoveryHook.java` — U1-lead 已落 SPI，U1a/U1b post-commit 用
- `premium/plugin-premium/container/src/main/java/org/zstack/container/KubernetesNativeProvider.java` — U5/U6 都改这里，K8s API 客户端调用样板
- 测试样板：`test/src/test/groovy/org/zstack/test/integration/kvm/KvmRoleProviderIntegrationCase.groovy` — U1a 的 Path 2 case 仿这个写

### Institutional Learnings

- ADR-012 (preGeneratedRoleUuid ordering) — U1a/U1b caller 必须 pre-generate roleUuid 后填入 `PathTwoFlowDataKey.ROLE_UUID`，否则触发 NB-24 fail-loud
- ADR-014 (incremental rebuild antipattern) — 改 header 后必 `mvn clean install -P premium`
- next-session.md §0 4 个常见坑：SDK regen 顺序 / Groovy GString / Thread.start 黑洞 / dbf 注入

### External References

无 — Wave 1 完全在本仓现有 pattern 内。

## Key Technical Decisions

### Q1：Plan 文件位置 — 新建并列于 master fix-plan
- **Rationale**：master 是战略级（20 unit / 4 wave），本计划是 Wave 1 操作级。新文件保持 master clean，blame 友好
- **File**：`docs/plans/2026-04-27-002-feat-phase3-wave1-exec-plan.md`

### Q2：Test 工具链 — write-groovy-test (写) + reviewing-automation-cases (review)
- **Rationale**：用户指定 `reviewing-automation-cases` 但它是 review skill。拆两步明确分工
- **Workflow per unit**：
  1. 实施 + 写 case（executor / `write-groovy-test`）
  2. case 提交前过 `reviewing-automation-cases` 跑 review pass
  3. 如有 finding → fix → 再过 review
- **Verification gate**：每 unit 含 "reviewing-automation-cases pass" 行

### Q3：U4 cross-Wave test scope — Mock RoleProvider
- **Rationale**：Container 的 `getCapacityConsumption` 现返 0（Wave 2 U8 修），U4 不能等。Mock 让 U4 自洽，Wave 2 联调真实 Provider
- **Implementation**：`PhysicalServerCapacityUpdaterTest` 用 mock `PhysicalServerRoleProvider` Bean 注入，断言 aggregate 公式正确即可。Container 真实 path 的回归在 Wave 2 U8/U9 落地时同步覆盖

### Q4：C/D-tier sync 时机 — plan 末汇总，Wave 2-3 起前自审
- **Rationale**：Wave 1 内仅 U5 有规划期 ambiguity（Pod 聚合触发模型 — see §Open Questions Deferred），U5 本身只加字段不动触发，故不阻塞
- **Mechanism**：本计划 §C/D Sync Checklist 列 8 项 outstanding question，每项含 (question / blocking-wave / pre-flight 探索方法 / decision owner)。Wave 2-3 起 plan 时 producer 先消化此 checklist

### Q5：U1a/U1b 调用 3 Flow 不通过 FlowChainBuilder.newSimpleFlowChain
- **Rationale**：现 `HostManagerImpl.doAddHost:421` 已 build chain，U1a 不重起 chain 而是 `chain.then(autoAssociateFlow).then(createPhysicalServerRoleFlow).then(initPhysicalServerCapacityFlow)` **追加**到现 chain 头部（实际位置：在 add-host extension 之前，因为 Connect 流程会调用 `HostCapacityUpdater.resolveServerUuidOrThrow`，必须 RoleVO 已 persist）
- **Implementation**：3 Flow `@Autowired` 注入 + 在 chain build 后第一个 `chain.then` 之前 prepend
- **Risk**：FlowChainBuilder 是否支持 prepend？需 producer 起手前快速验证；如不支持则在 buildChain 处直接以新顺序 build

### Q6：Pod 聚合的 milliCPU vs core 单位
- **Decision**：`KubernetesPodInventory.requestsCpu` 单位 = milliCPU (long)，与 K8s 原生 Quantity 一致
- **Rationale**：避免 float 精度问题；HostCapacityVO.cpuNum 是 cores (long)，U8 (Wave 2) 做单位换算时 `requestsCpu / 1000` 即可
- **Memory**：`requestsMemory` 单位 = bytes (long)，与 PSC.totalMemory 一致

## Open Questions

### Resolved During Planning

- Plan 命名 / 位置 (Q1)
- Test skill 工作流 (Q2)
- U4 mock RoleProvider 策略 (Q3)
- C/D-tier sync 机制 (Q4)
- U1a/U1b chain 接入方式 (Q5)
- Pod 单位 (Q6)

### Deferred to Implementation

- **U1a/b chain prepend 实际可行性**：起手前 5min 验证 `FlowChainBuilder` API；不支持则改在 build 阶段 inject 新顺序
- **U4 PhysicalServerCapacityVO.totalCpu/totalMemory 数据源**：从 PhysicalServerVO 的硬件字段（如 `cpuNum` / `totalMemory`）取 vs 从 HostCapacityVO 取？本 unit producer 决策时 grep PSV 字段；如缺则 zero 写入（recalculate 在 Wave 4 PERF 时再细化）
- **U5 Pod 聚合的触发模型**：U5 仅加字段，**触发**留 U8（Wave 2）。U8 producer 决策 watch / poll / on-demand
- **U6 systemUUID 是否进 BM2 AutoAssociator 黑名单**：现 `PhysicalServerAutoAssociator.SERIAL_NUMBER_BLACKLIST` 只防 BIOS 默认值；K8s systemUUID 一般可信但 minikube/dev 集群可能有共享值。U1c (Wave 1 二阶段) 起前 producer 验证

## Implementation Units

### Unit 1: U1a — KVM Path 2 接 3 Flow

- [ ] **U1a — `HostManagerImpl.doAddHost` 接 AutoAssociate / CreatePhysicalServerRole / InitPhysicalServerCapacity Flow**

**Goal**：让 KVM 传统 AddHost 路径在 connect flow 之前完成 PS 关联 + RoleVO 持久化 + PSC 初始化（NB-24 ordering），post-commit 触发 hardware discovery。

**Requirements**：R1 (AC-RS-04), R3 (skill review), R4 (mvn clean), R6 (producer != reviewer)

**Dependencies**：U1-lead (已落 `6f2103c5de`)

**Files**:
- Modify: `compute/src/main/java/org/zstack/compute/host/HostManagerImpl.java`
- Test: `test/src/test/groovy/org/zstack/test/integration/kvm/AddKvmHostPath2Case.groovy` (new)
- Test: `test/src/test/groovy/org/zstack/test/integration/kvm/KvmRoleProviderIntegrationCase.groovy` (extend with concurrency assert)

**Approach**:
1. `HostManagerImpl.doAddHost:421` 现已 build `FlowChain`。在 chain 头部（`chain.setName` 之后）prepend 3 Flow（如果 `msg.getServerUuid() != null` OR `MATCH_CONTEXT` 可填充）
2. 在 chain build 前构造 `data` Map，填 `PathTwoFlowDataKey.SERVER_UUID` (msg.serverUuid 或 null) / `MATCH_CONTEXT` (RoleMatchContext from msg) / `CLUSTER_UUID` / `ROLE_UUID` (= hvo.uuid 已 pre-generate per Q5) / `ROLE_TYPE` ("KVM_HOST") / `SCHEDULING_MODE` (INTERNAL_SHARED)
3. chain.done callback 内 `enqueueDiscoveryHook.enqueueDiscovery(serverUuid)` (post-commit best-effort)
4. failure rollback 由 FlowChain 反向跑，3 Flow 各自 rollback 已在 U1-lead 实装

**Patterns to follow**：
- chain.then 模式：`HostManagerImpl.doAddHost:424` `new NoRollbackFlow() { ... }` (现有 flow 写法)
- ADR-012 ordering：path 1 `PhysicalServerManagerImpl.handle(APIAttachPhysicalServerRoleMsg):433-499` (preGenRoleUuid → RoleVO persist → provider.createRoleEntity 顺序)

**Test scenarios**:
- *Happy*：`AddKVMHostMsg.serverUuid=ps.uuid` → 3 Flow 跑通 → `PhysicalServerRoleVO(serverUuid=ps.uuid, roleType=KVM_HOST, roleUuid=hvo.uuid)` 存在 + `PhysicalServerCapacityVO(uuid=ps.uuid, state=Stale)` 存在 → connect 完成 → `HardwareDiscoveryScheduler` queue 长度 +1
- *Happy*：`AddKVMHostMsg.serverUuid=null` + cluster 绑 ServerPool → `AutoAssociateFlow` create 新 PS → 同上 assert
- *Edge*：cluster 无 ServerPool + `serverUuid=null` → `AutoAssociateFlow.fail("no PhysicalServer matched and no ServerPool is bound")` → AddHost 整体失败 → 无孤儿 HostVO
- *Error*：`MATCH_CONTEXT=null` 且 `SERVER_UUID=null` → `AutoAssociateFlow.fail("AutoAssociateFlow needs pre-supplied serverUuid or RoleMatchContext")`
- *Concurrency*：path 1 attach + path 2 AddHost 对同 PS 同时跑 → `lockPhysicalServerForAttach` 串行化 → 第二个看到第一个的 RoleVO，`CreatePhysicalServerRoleFlow.ROLE_PRE_EXISTED=true` 走 idempotent 分支（assert path 2 不报 `UniqueConstraint(serverUuid, roleType)` 冲突）
- *Integration*：connect flow 内 `HostCapacityUpdater.resolveServerUuidOrThrow(hvo.uuid)` 不抛 NB-24 fail-loud（此前 Phase 2D 修过，path 2 来后回归确认）

**Verification**:
- Build：`./scripts/mvn-safe-install.sh -pl compute,plugin/kvm,plugin/physicalServer,test -am -P premium` SUCCESS
- AC：`grep -nE 'AutoAssociateFlow|CreatePhysicalServerRoleFlow|InitPhysicalServerCapacityFlow' compute/src/main/java/org/zstack/compute/host/HostManagerImpl.java` 命中
- Test：`AddKvmHostPath2Case` happy + 4 个 edge/error/concurrency/integration 全绿
- Test：扩 `KvmRoleProviderIntegrationCase` 加并发场景仍绿
- **Skill review**：`reviewing-automation-cases` skill pass on `AddKvmHostPath2Case.groovy` + 扩展后的 `KvmRoleProviderIntegrationCase.groovy`，无 P1 / P2 finding 未处理

---

### Unit 2: U1b — BM2 Path 2 接 3 Flow

- [ ] **U1b — `BareMetal2ChassisManagerImpl.handle(APIAddBareMetal2ChassisMsg)` 接 3 Flow**

**Goal**：BM2 传统 AddChassis 路径同 U1a 模式接 3 Flow + post-commit hook。

**Requirements**：R1 (AC-RS-07), R3, R4, R6

**Dependencies**：U1-lead

**Files**:
- Modify: `premium/baremetal2/src/main/java/org/zstack/baremetal2/BareMetal2ChassisManagerImpl.java`
- Test: `premium/test-premium/src/test/groovy/org/zstack/test/integration/baremetal2/AddBm2ChassisPath2Case.groovy` (new)
- Test: `premium/test-premium/src/test/groovy/org/zstack/test/integration/baremetal2/Bm2RoleProviderIntegrationCase.groovy` (extend 加 path 1 vs path 2 race assert)

**Approach**：
1. `handle(APIAddBareMetal2ChassisMsg)` 内的 add-chassis FlowChain（如已存在则 prepend；不存在则新建）
2. data 填 `ROLE_TYPE="BAREMETAL2"` / `SCHEDULING_MODE` from `msg.roleConfig.provisionType` (默认 INTERNAL_EXCLUSIVE — 弹性模式留 U11 Wave 3) / `ROLE_UUID = chassis.uuid` (pre-generate)
3. chain.done callback 调 `enqueueDiscoveryHook.enqueueDiscovery(serverUuid)`

**Patterns to follow**：U1a 完全平行；reuse `Bm2RoleProvider` SPI 接口，不动 provider 本身。

**Test scenarios**：与 U1a 平行，roleType / 标识替换为 BM2。

**Verification**：mvn clean -pl premium/baremetal2,plugin/physicalServer,premium/test-premium -am -P premium SUCCESS；新 case 全绿；reviewing-automation-cases pass。

---

### Unit 3: U4 — PhysicalServerCapacityUpdater.recalculate()

- [ ] **U4 — 新建 `PhysicalServerCapacityUpdater`，提供 `recalculate(serverUuid)`**

**Goal**：统一容量真表的 server-level recalculate。读所有 active RoleVO + 调 RoleProvider.getCapacityConsumption 累加 + 减 reserved + safetyBuffer，写回 PhysicalServerCapacityVO。

**Requirements**：R1 (AC-CM-04), R4

**Dependencies**：U1-lead (data PSC 已被 InitPhysicalServerCapacityFlow 创建)；mock 解耦 U8 (Q3)

**Files**:
- Create: `compute/src/main/java/org/zstack/compute/allocator/PhysicalServerCapacityUpdater.java`
- Test: `compute/src/test/java/org/zstack/compute/allocator/PhysicalServerCapacityUpdaterTest.java` (new unit test)

**Approach**：
1. 单方法 `void recalculate(String serverUuid)`，PESSIMISTIC_WRITE lock by serverUuid（NB-30）
2. 查 `PhysicalServerRoleVO WHERE serverUuid=?` → list of (roleUuid, roleType, schedulingMode)
3. 对每条 role，从 SpringBeanRegistry 取对应 `PhysicalServerRoleProvider` (by roleType) → 调 `getCapacityConsumption(serverUuid, roleUuid)` → 累加 cpu/memory
4. `total = ps.cpuNum * cpuOverprovisioningRatio` (read PSC col per Q12 — 但 U12 在 Wave 3) → 本 unit 先用 ps.cpuNum 直接，U12 时改
5. `safetyBuffer = max(4, ps.cpuNum * GlobalConfig.SAFETY_CPU_PCT)` (静态)；U9 (Wave 2) 接动态
6. `available = total - consumed - reserved - buffer`，写 PSC
7. PSC.capacityState = Ready（之前是 Stale）

**Patterns to follow**：
- `compute/src/main/java/org/zstack/compute/allocator/HostCapacityUpdater.java` — lock + retry + write 模板
- ADR-001 / ADR-002 fail-loud + UUID 语义

**Test scenarios** (unit test, mock providers)：
- *Happy KVM single role*：1 mock provider 返 CapacityUsage(cpu=8, mem=16G) → PSC.availableCpu = total - 8 - reserved - buffer
- *Happy mixed*：2 mock provider (KVM 4 + Container 2) → 累加 6
- *Edge no role*：active role 列空 → consumed=0
- *Edge PS not exists*：throw OperationFailureException("PhysicalServer[uuid:%s] not found")
- *Concurrent*：mock 2 thread 同时 recalculate → PESSIMISTIC_WRITE 串行；最终值不重复扣
- *Error provider throws*：1 provider throws RuntimeException → 整体抛 OperationFailureException 不写 PSC（fail-loud per NB-24 风格）

**Verification**：
- Build：mvn clean install -pl compute -am -P premium SUCCESS
- AC：`grep -nE 'class PhysicalServerCapacityUpdater' compute/src/main/java/org/zstack/compute/allocator/PhysicalServerCapacityUpdater.java`
- Test：`PhysicalServerCapacityUpdaterTest` 6 个 scenario 绿
- **Note**：本 unit 无 .groovy integration case（unit test 充分）。Container 真实 path 回归在 Wave 2 U8 落地时联调

---

### Unit 4: U5 — KubernetesPodInventory.requestsCpu/Memory + 聚合公式

- [ ] **U5 — Pod 聚合三疑点（multi-container / initContainers / overhead）字段实装**

**Goal**：在 KubernetesPodInventory 加 requestsCpu/Memory 字段，KubernetesNativeProvider 填充时按 PRD §2.10 公式 `max(Σinit, Σmain) + overhead` 计算。不动现有 `cpuNum`/`memorySize`（reads `limits[0]`，AC-CM-19 防 regression）。

**Requirements**：R1 (AC-CM-17/18/19), R3, R4

**Dependencies**：无（独立字段）

**Files**:
- Modify: `premium/plugin-premium/container/src/main/java/org/zstack/container/KubernetesPodInventory.java` (加 2 字段 + getter/setter)
- Modify: `premium/plugin-premium/container/src/main/java/org/zstack/container/KubernetesNativeProvider.java` (填充逻辑 line 189/195-196/200-201 三疑点)
- Test: `premium/plugin-premium/container/src/test/java/org/zstack/container/KubernetesNativeProviderTest.java` (extend or new unit test)
- Test: `premium/test-premium/src/test/groovy/org/zstack/test/integration/container/PodAggregationCase.groovy` (new integration case)

**Approach**：
1. 加字段：`long requestsCpu` (milliCPU per Q6) / `long requestsMemory` (bytes)
2. `KubernetesNativeProvider.getKubernetesPodInventory()` 实装：
   - parse `pod.spec.initContainers[i].resources.requests.cpu` (Quantity → milliCPU long), sum → `sumInit`
   - parse `pod.spec.containers[i].resources.requests.cpu`, sum → `sumMain`
   - `requestsCpu = max(sumInit, sumMain) + (pod.spec.overhead?.cpu or 0)`
   - 同公式 memory（bytes）
3. 现有 `cpuNum`/`memorySize` 字段填充逻辑**完全不动**（CLAUDE.md 禁止无意义改动）

**Patterns to follow**：
- K8s Quantity parse：现 `KubernetesNativeProvider.java:189` 已 parse `limits` Quantity；U5 复用同套解析逻辑
- 不动黑盒 K8s API client

**Test scenarios** (unit test)：
- *Happy multi-main*：Pod containers=[200m, 300m] → sumMain=500m → requestsCpu=500
- *Happy with init*：Pod init=[800m], main=[200m] → max(800,200)=800 → requestsCpu=800
- *Happy with overhead*：Pod max=500m + overhead=100m → requestsCpu=600
- *Edge no requests*：Pod containers 无 requests 字段 → requestsCpu=0
- *Edge nil overhead*：pod.spec.overhead = null → requestsCpu=max(...) 不加 overhead
- *Edge memory unit*：Pod requests memory="2Gi" → requestsMemory = 2 * 1024^3 = 2147483648 bytes
- *Regression AC-CM-19*：同一 Pod 跑老 path → cpuNum 仍读 limits[0] 不变；memorySize 同

**Test scenarios** (integration `PodAggregationCase.groovy`)：
- create pod with overhead → query KubernetesPodInventory → 断言 requestsCpu/Memory 与公式一致
- regression：现 `cpuNum`/`memorySize` 仍是 limits-based

**Verification**：
- Build：mvn clean install -pl premium/plugin-premium/container,premium/test-premium -am -P premium SUCCESS
- AC：`grep -nE 'requestsCpu|requestsMemory' premium/plugin-premium/container/src/main/java/org/zstack/container/KubernetesPodInventory.java` 命中
- Test：unit test 7 scenario 全绿；integration `PodAggregationCase` 全绿
- **Skill review**：`reviewing-automation-cases` pass on `PodAggregationCase.groovy`

---

### Unit 5: U6 — KubernetesNodeInventory 字段扩展

- [ ] **U6 — Node systemUUID/machineID/capacity/allocatable 6 字段**

**Goal**：从 K8s Node 抽 systemUUID/machineID/capacity.cpu/capacity.memory/allocatable.cpu/allocatable.memory 6 字段，让 AutoAssociator tier-1 (serialNumber match) 有数据可用。

**Requirements**：R1 (AC-RS-12), R3, R4

**Dependencies**：无

**Files**:
- Modify: `premium/plugin-premium/container/src/main/java/org/zstack/container/KubernetesNodeInventory.java` (加 6 字段 + getter/setter)
- Modify: `premium/plugin-premium/container/src/main/java/org/zstack/container/KubernetesNativeProvider.java` (`listNodes()` 抽字段)
- Test: extend `premium/test-premium/src/test/groovy/org/zstack/test/integration/container/ContainerRoleProviderIntegrationCase.groovy` (assert 新字段非空)

**Approach**：
1. 加字段：`String systemUUID` / `String machineID` / `long capacityCpu` / `long capacityMemory` / `long allocatableCpu` / `long allocatableMemory`
2. `NativeProvider.listNodes()` 内：
   - `node.status.nodeInfo.systemUUID` → systemUUID
   - `node.status.nodeInfo.machineID` → machineID
   - `node.status.capacity["cpu"]` (Quantity → milliCPU long) → capacityCpu
   - `node.status.capacity["memory"]` (Quantity → bytes long) → capacityMemory
   - `allocatable.cpu/memory` 同
3. K8s Quantity 解析复用 U5 同套

**Patterns to follow**：
- 现 `listNodes()` 返回 `KubernetesNodeInventory` 模板已存
- nil-safe：缺 systemInfo 不 NPE，字段=null/0

**Test scenarios** (integration extend)：
- *Happy*：mock K8s node with full systemInfo → 6 字段全非空，单位正确
- *Edge missing systemUUID*：mock K8s node `systemInfo.systemUUID=null` → inventory.systemUUID=null，无 NPE
- *Edge missing capacity*：mock node `status.capacity=null` → capacityCpu=0
- *Integration*：U1c (将来 Wave 1 二阶段) `processNodeTransactional` 可用 systemUUID 走 AutoAssociator tier-1 — 本 unit 不实施 U1c，但断言字段被填充以支撑

**Verification**：
- Build：mvn clean install -pl premium/plugin-premium/container -am -P premium SUCCESS
- AC：`grep -nE 'systemUUID|machineID|capacityCpu|allocatableCpu' premium/plugin-premium/container/src/main/java/org/zstack/container/KubernetesNodeInventory.java`
- Test：扩展后的 ContainerRoleProviderIntegrationCase 仍绿，加 3 个新 assert
- **Skill review**：`reviewing-automation-cases` pass on extended case

---

## C/D Sync Checklist (Wave 2-3 起前 producer 自审)

8 个 C/D-tier unit 起 plan 前必须先消化以下问题。每条 owner = 对应 master plan U-unit producer。

### U1c (Container processNodeTransactional, D-tier)
- *Q*：`@Transactional` 在 `ContainerEndpointBase` 现有方法上是否已 Spring proxy 化？需 verify private/final/self-call 边界
- *Q*：现 `afterSyncNodes` hook 跟 `processNodeTransactional` 5-step 哪些重叠？是否要重构 hook 链
- *Pre-flight*：grep `@Transactional` in `premium/plugin-premium/container/`，读 `ContainerEndpointBase.syncNodesFromCluster`

### U2 (Power handler 3 个, C-tier)
- *Q*：BM2 模块的 IPMI executor 真实类名？(`HostIpmiPowerExecutor` / `IpmiPowerExecutor` / Redfish 等)
- *Pre-flight*：`grep -rnE 'class.*Power.*Executor|class.*Ipmi' premium/baremetal2/src/main/java/`
- *Q*：`PhysicalServerVO.powerStatus` enum 值是否覆盖 PoweringOn / PoweringOff 中间态

### U3 (APIDiscoverPhysicalServerHardwareMsg dispatch, C-tier)
- *Q*：`APIDiscoverPhysicalServerHardwareEvent` 的 inventory 字段是 `UnifiedHardwareInfo` 还是 `PhysicalServerHardwareDetailInventory`？
- *Pre-flight*：read `header/.../server/APIDiscoverPhysicalServerHardwareEvent.java`

### U7 (ContainerNodeCordonService, C-tier)
- *Q*：shipped K8s Java SDK 版本支持 `SelfSubjectAccessReview` 吗？依赖 `io.kubernetes:client-java` 版本
- *Pre-flight*：`grep client-java premium/plugin-premium/container/pom.xml`，对照 K8s SDK changelog

### U8 (getCapacityConsumption Pod aggregation, C-tier)
- *Q*：Pod 聚合触发模型 — watch / poll / on-demand？U5 加了字段后由谁触发跨 Pod 累加
- *Pre-flight*：read PRD `capacity/feat-unified_capacity_management_prd.md` §2.9-§2.10

### U9 (Safety Buffer 动态扣减, C-tier)
- *Q*：`ServerReservedCapacityExtensionPoint` 已存在还是新建？
- *Pre-flight*：`grep -rn 'ServerReservedCapacityExtensionPoint' compute/ header/ plugin/ premium/`

### U11 (BM2 ProvisionType 弹性 + INTERNAL_EXCLUSIVE consumer, C-tier)
- *Q*：`CapacityUsage.exclusive` 字段从哪来？BM2 RoleVO state 还是 BareMetal2InstanceVO？
- *Pre-flight*：read `Bm2RoleProvider.getCapacityConsumption` + `BareMetal2InstanceVO`

### U12 (超分比 read path 绑 PSC 列, C-tier)
- *Q*：`HostCpuOverProvisioningManagerImpl.getRatio()` 现实现长啥样？per-host override 路径已存在还是新增
- *Pre-flight*：read `compute/src/main/java/org/zstack/compute/allocator/HostCpuOverProvisioningManagerImpl.java`

### U16 (3 private discover + new VO, C-tier)
- *Q*：建议新建的 `PhysicalServerHardwareInfoVO` vs 现存 `PhysicalServerHardwareDetailVO` 关系？是否冗余？可能需 ADR-015 决策
- *Pre-flight*：read `header/.../server/PhysicalServerHardwareDetailVO.java`，对照 PRD §2.5b 期待

## System-Wide Impact

- **Interaction graph**：U1a/U1b 在 chain 头部加 3 Flow，影响所有走 `HostManagerImpl.doAddHost` / `BareMetal2ChassisManagerImpl.handle(Add*)` 的入口（包括非 KVM/BM2 的将来 hypervisor）— 但 `if msg.getServerUuid() != null OR matchContext available` 守卫保证不影响现有路径
- **Error propagation**：3 Flow 任一失败 → FlowChain 反向 rollback → 现有 HostVO/ChassisVO 创建路径未启动 → 无孤儿
- **State lifecycle risks**：`AutoAssociateFlow` 创建的 PhysicalServerVO 在 chain 失败后**不**回滚（NoRollbackFlow per design — PS 是查找/复用对象，留下不破一致性）。`PhysicalServerCascadeExtension` 现有 cascade 仍能在 PS 显式 delete 时清 RoleVO + PSC
- **API surface parity**：`AddKVMHostMsg.serverUuid` / `APIAddBareMetal2ChassisMsg.serverUuid` 字段从 carrier-only 转为 functional — SDK / apihelper 不需 regen（field 已加），但 PR 描述需注 behavior change
- **Integration coverage**：path 1 attach + path 2 add 并发场景由 `lockPhysicalServerForAttach` 串行化，但 path 2 path 自身的并发（两个 AddKVMHostMsg 同 PS）未在本 plan 测；留 Wave 1 二阶段评估
- **Unchanged invariants**：
  - 现有 `APIAttachPhysicalServerRoleMsg` 路径 (path 1) 不动
  - `HostCapacityUpdater` (W3 现 Phase 2 实现) 不动 — U4 新建 `PhysicalServerCapacityUpdater` 是新方法不替换
  - 三 Phase 2D integration case (KVM / BM2 / Container) 必须仍绿 — Wave 1 collective gate
  - `KubernetesPodInventory.cpuNum/memorySize` (limits-based) 不动 — AC-CM-19 防 regression
  - `AddKVMHostMsg`/`APIAddBareMetal2ChassisMsg` 字段 schema 不变（仅 handler 行为变）

## Risks & Dependencies

| Risk | Severity | Mitigation |
|---|---|---|
| Q5 chain prepend API 不存在 | P0 | U1a producer 起手前 5min 验证 `FlowChainBuilder` API；如不支持改 chain 重 build with new order |
| FlowChain 在 connect flow 失败时不回滚 3 Flow | P0 | U1-lead 已设计 rollback；U1a/U1b 测试用例显式覆盖 connect-flow-fail 场景 |
| `KubernetesPodInventory` 加字段触发 K8s API client serialization 变化 | P1 | 字段 default=0 + nullable getter；现有反序列化路径 backward-compat |
| `PhysicalServerCapacityUpdater` 跟现 `HostCapacityUpdater` 命名混淆 / 未来误调 | P1 | Javadoc 注明：HostCapacityUpdater = path 1 / W1-W6 兼容路径，PSC.recalculate = path 2/3 + Wave 2 收敛 |
| Wave 1 collective gate 三 case 任一失败 | P0 | 5 unit producer 各自跑相关 case 后才 push；主 session 在 Wave 1 切换时跑全部三 case 收尾 |
| skill review 阻塞 commit (review 慢) | P2 | review 平均 ~3min；如 P1/P2 finding 多则 fix 后再过；P3 finding 在 PR 描述 acknowledge 不阻 |
| BM2 add chassis flow 比 KVM 复杂（IPMI 验证 / 硬件发现）→ U1b 改动面比 U1a 大 | P1 | U1b producer 起前先读 BareMetal2ChassisManagerImpl，如已用 FlowChain 直接接入；如未 chain 则同步评估是否要先 chainify |
| U5/U6 K8s Quantity 单位换算错（milliCPU vs core, Mi vs MB） | P1 | unit test 显式验单位边界；review skill 重点检查 |

## Documentation / Operational Notes

- **本 plan 提交时同步更新**：
  - master plan `2026-04-28-001-fix-phase2-prd-gaps-plan.md` §Implementation Units 加 cross-ref note "Wave 1 详 detail in [2026-04-27-002 exec plan]"（modify-only，不改单元结构）
  - `docs/STATUS.md` §4 + §Phase 3 待创建 → done with link（次本 plan 落地后）
  - `docs/brainstorms/next-session.md` Wave 1 进度同步（每 unit 完成后追 H3 子节）
- **commit / push 策略**：每 unit 1 commit + push（zcommit per CLAUDE.md 铁律 4-7）；主 session 在 5 unit 全绿 + 3 phase 2D case 仍绿后再 sync `next-session.md`
- **铁律 12 触发**：U1a / U1b 改 compute + premium，且 U1-lead 改了 header — 每 unit verification 强制 `mvn-safe-install.sh` (clean install)
- **PR 描述要求**：每 unit 在 PR / commit body 注明 (1) AC 编号 (2) 关联 NB / ADR (3) skill review 结果摘要 (4) test case file path

## Sources & References

- **Master fix-plan**：[docs/plans/2026-04-28-001-fix-phase2-prd-gaps-plan.md](2026-04-28-001-fix-phase2-prd-gaps-plan.md)
- **Audit**：[docs/audits/2026-04-27-phase2-prd-audit.md](../audits/2026-04-27-phase2-prd-audit.md)
- **STATUS**：[docs/STATUS.md](../STATUS.md)
- **U1-lead commit**：`6f2103c5de` (落 6 文件 / 374 行)
- **Plan commit**：`f095a4fae9` (master 起草)
- **PRD sources** (cloud_prd `f9928ec`):
  - `prd/v5.5.18-unified-hardware/capacity/feat-unified_capacity_management_prd.md` §2.10 Pod 聚合公式
  - `prd/v5.5.18-unified-hardware/server/feat-role_spi_adapter_prd.md` §2.4 path 2 / §2.5b hardware service
- **ADRs**: [ADR-001](../decisions/ADR-001-hostcapacity-updater-static-resolve.md), [ADR-002](../decisions/ADR-002-hostcapacity-updater-uuid-semantics.md), [ADR-007](../decisions/ADR-007-no-backup-tables.md), [ADR-011](../decisions/ADR-011-md5-salt-uuid-derivation.md), [ADR-012](../decisions/ADR-012-roleprovider-pre-generated-role-uuid.md), [ADR-014](../decisions/ADR-014-incremental-rebuild-antipattern.md)
- **Runbooks**: `docs/runbooks/v5518-sql-ddl-pitfalls.md` / `docs/runbooks/testing-envs.md`
- **CLAUDE.md routing**: 项目根 §Agent Routing 节
- **Skills used**:
  - `write-groovy-test` (per Q2 author phase)
  - `reviewing-automation-cases` (per Q2 review phase)
  - `mvn-safe-install.sh` (per ADR-014 / 铁律 12)
