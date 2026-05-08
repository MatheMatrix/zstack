# Plan: Cordon Production Trigger — close the 0-caller gap (2026-05-09)

## Context

**Why now**: 2026-05-09 真机 172.26.201.160 收尾验证 PSC writer collapse 时
发现 `ContainerNodeCordonService.cordon()` / `evaluate()` / 反向 mirror
`isUnschedulable(V1Node)` 三个入口在 production 全部 **0 caller**。
`ContainerCordonReservedCapacityExtension` 链条接对了但 `cordonedHostUuids`
registry 在生产侧永远空，AC-CM-13「cordoned 节点 free → reserved」**生产路径
死分支**——IT case `ContainerCordonReservedCase` 用反射写 set 才绕过去。

PSC writer collapse plan 把 R3 标 ✅ 偏乐观，已回退 ⚠️。本 plan 把这块补齐。

**Target outcome**:
- ZStack 容量驱动的自动 cordon/uncordon 链路打通（`evaluate()` 接进 sync 末尾）
- K8s 侧 operator 手动 cordon 的状态被 ZStack 反向 mirror 进 registry，让
  `ContainerCordonReservedCapacityExtension` 看得见
- IT case 不再用反射写 `cordonedHostUuids`，全部走 production API

**Branch**: `feature/unifi-host-dev` · 当前 HEAD `178818906b` (zstack) /
`2e558edb9a` (premium)
**PRD pin**: cloud_prd `f9928ec`
**Closes**: STATUS.md §4.1 R3 ⚠️ → ✅；§4.3 NB-5 ⚠️ 部分；AC-CM-13 production
trigger gap

---

## Problem statement (来自 0509 真机 + grep 全仓)

| # | 入口 | 实装 | production caller | 后果 |
|---|---|---|---|---|
| P1 | `cordonService.cordon(hostUuid)` | ✅ PATCH K8s + 写 registry | **0** | ZStack 永远不会主动 cordon |
| P2 | `cordonService.evaluate(host, free*, buffer*)` | ✅ hysteresis 判定 | **0** | 容量到阈值时无人触发 cordon |
| P3 | `isUnschedulable(V1Node)` 反向 mirror | ✅ static helper | **0** | operator 手动 cordon 后 ZStack 看不见 |
| P4 | `KubernetesNodeInventory` DTO | 字段不全 | — | listNodes adapter 把 V1Node 收敛成 inventory 时丢了 `spec.unschedulable` |

**证据**：
- `grep "cordonService\.cordon\|\.evaluate(\|isUnschedulable("` 排除 test：0 命中
- `cordonedHostUuids` field private，外部只能反射写（IT 在用，production 无路径）
- `KubernetesNodeInventory.java` 顶层字段（line 9-180）没 `unschedulable` 字段

---

## Design — 三个入口分别接产线

### 设计原则

1. **Observe vs Act 分离**：K8s 反向 mirror 只写 in-memory registry，不 PATCH
   K8s（避免跟 K8s 真实状态打架）。`cordon()` / `uncordon()` 保持 ZStack
   主动行为（PATCH + 写 label + 写 registry）。
2. **PSC 锁外调 K8s**：`evaluate() → cordon() → patchNodeWithRetry` 是阻塞
   K8s IO，**绝不能在 `recalculate()` 的 PESSIMISTIC_WRITE 锁内调**。锁释放
   后再调，逻辑放 `ContainerEndpointBase.success()` 里 `recalculate` 之后。
3. **Container 路径专属**：`evaluate()` 调用只挂在 Container sync 路径，不
   走通用 post-recalculate hook（`evaluate` 的 `cordon` 内部 `findByUuid(NativeHostVO)`，
   KVM hostUuid 进去会 NPE）。
4. **Eventual consistency 接受**：reservation 反映 cordon 状态滞后 1 sync 周期
   （这一轮先 recalculate→evaluate→cordon→registry 写入；下轮 recalculate 才把
   reservation 算进 PSC.available\*），跟 plan 0508 已经接受的 Pod 聚合滞后一致。

### Layer 0 — DTO 扩展

`KubernetesNodeInventory` 加 `Boolean unschedulable` 字段。listNodes adapter
（`ContainerEndpoint.listNodes(...)` 实现侧，找到 V1Node→Inventory 的转换处）
读 `spec.getUnschedulable()` 注入。Inventory 现在已含 capacityCpu / capacityMemory，
加这一个 nullable Boolean 字段不破坏 backward compat（serialization tolerant）。

### Layer 1 — K8s 反向 mirror（observe-only）

新方法 `ContainerNodeCordonService.mirrorFromK8s(String hostUuid, boolean k8sUnschedulable)`：

```java
// 实装语义（伪码）：
public void mirrorFromK8s(String hostUuid, boolean k8sUnschedulable) {
    if (hostUuid == null) return;
    if (k8sUnschedulable) {
        cordonedHostUuids.add(hostUuid);  // operator-cordoned，记录但不 PATCH
    } else {
        // 仅当不是 ZStack-cordon（无 zstack.io/cordoned-by=capacity label）时移除。
        // ZStack 自己 cordon 的不让 K8s 状态翻覆——`evaluate()` 才有权 uncordon。
        // 由于 registry 没记 cordon-by-source，简化：只要 K8s 说 schedulable 就 remove。
        // 后续 evaluate() 还会 re-cordon，自洽。
        cordonedHostUuids.remove(hostUuid);
    }
}
```

调用点：`ContainerEndpointBase.processNodeTransactional(cluster, node)` 在
`toNativeHostVO(node)` persist 完之后（NativeHostVO.uuid 已确定），把
`KubernetesNodeInventory.unschedulable` 调 `mirrorFromK8s(host.uuid, ...)`.

### Layer 2 — 容量 hysteresis trigger（active）

在 `ContainerEndpointBase.success()` callback `psCapacityUpdater.recalculate(serverUuid)`
**之后**（line 707 紧接其后），追加：

```java
// PSC 锁已随 recalculate @Transactional 提交释放。re-query 拿 fresh available*
// (recalculate 已写回 DB)；buffer 用同一份 GlobalConfig 计算逻辑。
PhysicalServerCapacityVO fresh = dbf.findByUuid(serverUuid, PhysicalServerCapacityVO.class);
if (fresh != null && fresh.getTotalCpu() > 0) {
    long cpuBuf = PhysicalServerCapacityBuffers.calcCpuBuffer(fresh.getTotalCpu());
    long memBuf = PhysicalServerCapacityBuffers.calcMemBuffer(fresh.getTotalMemory());
    try {
        cordonService.evaluate(h.getUuid(),
                fresh.getAvailableCpu(), fresh.getAvailableMemory(),
                cpuBuf, memBuf);
    } catch (Throwable t) {
        logger.warn("[ContainerEndpointBase] cordon evaluate failed for host[{}]: {}",
                h.getUuid(), t.getMessage(), t);
        // fail-soft，不阻断 sync
    }
}
```

`evaluate()` 内部判定：
- `freeCpu < cpuBuf` OR `freeMem < memBuf` → 调 `cordon()` → PATCH K8s + add registry
- `freeCpu > 2 × cpuBuf` AND `freeMem > 2 × memBuf` → 调 `uncordon()` → PATCH K8s
  (only if zstack label) + remove registry
- 之间 → no-op（hysteresis）

### Buffer 计算抽出

在 `compute/src/main/java/org/zstack/compute/allocator/` 新建工具类
`PhysicalServerCapacityBuffers.java`（package-visible static helpers）：

```java
public final class PhysicalServerCapacityBuffers {
    static final long CPU_BUFFER_FLOOR = 4L;       // cores
    static final long MEMORY_BUFFER_FLOOR = 4L * 1024L * 1024L * 1024L;  // 4 GiB

    public static long calcCpuBuffer(long totalCpu) {
        int pct = HostAllocatorGlobalConfig.PHYSICAL_SERVER_CPU_SAFETY_BUFFER_PERCENT
                .value(Integer.class);
        return Math.max(CPU_BUFFER_FLOOR, totalCpu * pct / 100);
    }

    public static long calcMemBuffer(long totalMemory) {
        int pct = HostAllocatorGlobalConfig.PHYSICAL_SERVER_MEMORY_SAFETY_BUFFER_PERCENT
                .value(Integer.class);
        return Math.max(MEMORY_BUFFER_FLOOR, totalMemory * pct / 100);
    }

    private PhysicalServerCapacityBuffers() {}
}
```

`PhysicalServerCapacityUpdater._recalculate(...)` 现有 lines 217-220 内联计算
**重构成调这两个 helper**（保持行为不变，只是去重；本 plan 范围内做不留 TODO）。

---

## Implementation units

### U-α — `KubernetesNodeInventory` 加 `unschedulable` 字段

**File**:
- `premium/plugin-premium/container/src/main/java/org/zstack/container/inventory/KubernetesNodeInventory.java`（DTO）
- listNodes adapter 把 V1Node 转 Inventory 的实装文件（grep `new KubernetesNodeInventory()` 定位）

**Owner**: container-module-architect

**改动**:
1. 加 nullable `Boolean unschedulable` 字段 + getter/setter
2. adapter 转换处：`inv.setUnschedulable(node.getSpec() != null ? node.getSpec().getUnschedulable() : null);`
3. `null` 视同 false（K8s 默认 schedulable）

**Backward compat**: 加新字段不破坏现有 JSON serialization（缺字段 = null）

### U-β — `ContainerNodeCordonService.mirrorFromK8s()` 新方法

**File**: `premium/plugin-premium/container/src/main/java/org/zstack/container/ContainerNodeCordonService.java` + `ContainerNodeCordonServiceImpl.java`

**Owner**: container-module-architect

**改动**:
1. 接口加 `void mirrorFromK8s(String hostUuid, boolean k8sUnschedulable);`
2. impl 按上方设计实装；javadoc 显式说明「observe-only，不 PATCH K8s，不查
   `readOnlyEndpoints` RBAC（registry 维护是 in-memory，不需 K8s 权限）」
3. 跟现有 `cordon()` / `uncordon()` 区分：那两个写 label 并 PATCH K8s，本方法只写 registry

### U-γ — `ContainerEndpointBase.processNodeTransactional` 接 mirror

**File**: `premium/plugin-premium/container/src/main/java/org/zstack/container/ContainerEndpointBase.java`
**Lines**: ~794-840（processNodeTransactional 内）

**Owner**: container-module-architect

**改动**: 在 NativeHostVO persist 后 + transaction commit 前追加：

```java
Boolean k8sUns = node.getUnschedulable();
cordonService.mirrorFromK8s(host.getUuid(), Boolean.TRUE.equals(k8sUns));
```

**注意**: K8s default unschedulable=null/false → mirrorFromK8s 调 `cordonedHostUuids.remove`，
对从未 cordon 过的 host 是 no-op。每次 sync 都跑 = registry 跟 K8s 状态对齐。

### U-δ — Buffer 抽到 helper + `PhysicalServerCapacityUpdater` 重构

**Files**:
- 新建 `compute/src/main/java/org/zstack/compute/allocator/PhysicalServerCapacityBuffers.java`
- 改 `PhysicalServerCapacityUpdater.java` lines 217-220 调 helper

**Owner**: compute-resource-allocator

**改动**: 抽 + 替换。行为零变化，纯 dedup。

### U-ε — `ContainerEndpointBase.success()` 接 `evaluate()`

**File**: `premium/plugin-premium/container/src/main/java/org/zstack/container/ContainerEndpointBase.java`
**Lines**: 紧接 line 707 `getPsCapacityUpdater().recalculate(serverUuid);` 之后

**Owner**: container-module-architect (review by compute-resource-allocator)

**改动**: 按上方 Layer 2 伪码插入。复用 U-δ 的 `PhysicalServerCapacityBuffers`。
fail-soft 包 try-catch。

**约束**:
- 必须在 recalculate 之后（拿到 fresh available\*）
- 必须在 PSC 事务 commit 之后（recalculate `@Transactional` 已提交）
- 调 evaluate 时 PSC 锁已释放，`cordon()` 内部走 K8s PATCH 不会卡 PSC 锁

### U-ζ — IT cases (3 个，按 12a 红线 + 不再用反射)

**Owner**: container-module-architect

| Case | File | 走的 production API | 断言 |
|---|---|---|---|
| `ContainerK8sCordonMirrorCase` | `premium/test-premium/.../container/ContainerK8sCordonMirrorCase.groovy` | addEndpoint + syncEndpoint with K8sApiMocks 1 cluster 1 node `spec.unschedulable=true` | `cordonedHostUuids.contains(hostUuid)` ✅；K8s PATCH 调用计数 = 0（K8sApiMocks counter 验证）；下轮 sync `unschedulable=false` 后 registry 移除 |
| `ContainerCapacityCordonEvaluateCase` | 同目录 | sync 后 free<buffer（mock 节点 capacity 8c + 6 个 1c Pod = free 2c < buffer 4c） | sync 后 `cordonedHostUuids.contains(hostUuid)` ✅；`patchNode` 被调 1 次 with `spec.unschedulable=true` + zstack label；再下一轮 sync (free 仍 < 2×buffer) → 不 uncordon (hysteresis) |
| `ContainerCapacityHysteresisUncordonCase` | 同目录 | 第一轮 sync 触发 cordon；第二轮 sync 把 Pod load 降到 free > 2×buffer | sync 第二轮后 `cordonedHostUuids` 不含 hostUuid；patchNode 调 2 次（cordon + uncordon），第二次 body 含 `unschedulable=false` |

**K8sApiMocks 扩展**:
- 新 helper `mockK8sNodesWithCapacityAndUnschedulable(env, hostUuids, ips, cpu, mem, unschedulableFlags)`
  把 `spec.unschedulable` 注入 V1Node。
- 拦截 `CoreV1Api.patchNode` 调用计数 + 捕获 body（用现有 PowerMockito 框架，参考
  `ContainerNodeCordonServiceCase.groovy:144-188` 的 seam 模式 — 但这次走真 sync API，
  不是 `buildImplWithSeams` 反射构造）。

**12a 红线**: case body 0 `dbf.persist*` / 0 `getDeclaredField("cordonedHostUuids")`。
fixture 在 K8sApiMocks 里准备 V1Node 状态，case 只调 production API。

---

## 风险 + 兼容性

| 风险 | 缓解 |
|---|---|
| evaluate→cordon→K8s PATCH 阻塞 sync 流程 | recalculate 锁已释放；patchNodeWithRetry 内部有 retry+timeout（已实装）；fail-soft 包 try-catch 不阻 sync |
| 双源争抢：operator cordon 跟 ZStack 容量 cordon 冲突 | mirrorFromK8s 只写 registry 不 PATCH；evaluate→cordon 写 label `zstack.io/cordoned-by=capacity`，uncordon 仅当 label 在才 PATCH（已实装 line 47-50）；operator 手动 cordon 不带 label，ZStack 永不主动 uncordon 它 |
| Reservation 反映 cordon 状态滞后 1 sync 周期 | 接受。下轮 recalculate 把 reservation 算进；UX 上 7 个 NativeHost 同步周期 ≤ 60s（cluster polling），可接受 |
| `KubernetesNodeInventory` schema 改动影响序列化 | 新字段 nullable Boolean，旧 JSON 缺字段 = null 兼容；ZStack 内部 DTO 不进 DB schema |
| Buffer helper 抽出后 `_recalculate` 行为漂移 | 行为零变化（floor 常量 + GlobalConfig key + math 三者搬过来）；U-ζ IT case `ContainerCapacityCordonEvaluateCase` 间接覆盖（free<buf 才触发 cordon，buf 算错就 hysteresis 偏） |
| Schema / Flyway | 不动 |
| Backward compat | API msg / API reply / service id / VO schema 全部不变；只 DTO 加 nullable 字段 + cordonService 接口加新方法 |

---

## 收口对账

### STATUS.md 同步更新（合并 commit 时一并改）
- §4.1 R3 ⚠️ → ✅（AC-CM-13 production trigger 闭环）
- §4.3 NB-5 ⚠️（Container Cordon 熔断）描述补全 — Pod 聚合 + cordon trigger 都通
- §5 ✅ 区新增「Cordon production trigger — `mirrorFromK8s` (K8s→registry observe)
  + `evaluate()` (capacity→K8s active hysteresis)」
- §5 PSC writer collapse 条目 cordon 注脚改写为「production trigger 已补，IT 3/3 + 真机回归 ✅」

### ADR 引用
- ADR-012 ordering normative — recalculate ordering 已遵守，本 plan 不动
- NB-5 capacity §2.9-§2.10 — Cordon 熔断 + Pod 聚合，本 plan 兑现 trigger 部分
- NB-30 单锁键 — recalculate 锁释放后才调 evaluate，不破坏

### Phase 关联
本 plan 是 PSC writer collapse plan 的 follow-up。范围窄、独立可 ship。

---

## Execution

### Phase 0 — Worktree（CLAUDE.md §13）
直接在 `feature/unifi-host-dev` 主 worktree 干 — 改动小、commit 数少 (~6)，
不需要独立 worktree。`./scripts/mvn-safe-install.sh -pl <mod> -am` 走默认。

### Phase 1 — 落码（顺序）
1. **U-δ** Buffer helper（compute；纯重构，先 land 让 U-ε 能复用）
2. **U-α** Inventory DTO 扩字段（不依赖任何 unit）
3. **U-β** mirrorFromK8s 新方法（依赖 U-α 不严格但 logic 上配对）
4. **U-γ** processNodeTransactional 接 mirror（依赖 U-α + U-β）
5. **U-ε** success() 接 evaluate（依赖 U-δ）

### Phase 2 — IT cases (U-ζ)
3 case 各写各跑 `./scripts/run-case.sh <CaseName>`。可并行起步（依赖 Phase 1
全部完成）。

### Phase 3 — 真机回归
172.26.201.160 endpoint `ef554bb8255d4ce0b891a1367841b88b`：
- 验证当前 7 NativeHost 任一个 `kubectl cordon <node>`（需 K8s 管理面，本 plan
  范围内由人协助；agent 不依赖此步）→ 等下轮 sync → registry mirror 成功 →
  PSC.availableCpu drop to 0
- 不易在生产环境 stress free<buffer（需要起 N 个 Pod 占满），这步留 IT 覆盖。

### Phase 4 — Commit 收敛
- 单 commit per U-unit (5 commit + IT 1-3 commit) `zcommit <type> <scope> "" "desc" "body"`
- 完成后 push origin feature/unifi-host-dev
- 不开 MR，等 master plan 整体 ship

---

## Verification

### Code 层
- `grep -rn "cordonService\.cordon\|\.evaluate(\|mirrorFromK8s(" --exclude-dir=test --exclude="*Case.groovy"` ≥ 3 命中（U-γ 1 + U-ε 1，cordon/uncordon 间接通过 evaluate 调）
- `grep -rn "isUnschedulable(" /home/mj/zstack-workspace/zstack-unifi-host/premium` 至少 1 production caller（adapter 转换处）

### IT 层
- `./scripts/run-case.sh ContainerK8sCordonMirrorCase` exit 0
- `./scripts/run-case.sh ContainerCapacityCordonEvaluateCase` exit 0
- `./scripts/run-case.sh ContainerCapacityHysteresisUncordonCase` exit 0
- 既有 `ContainerCordonReservedCase` / `ContainerNodeCordonServiceCase` / `ContainerSyncRecalcCase` 仍 PASS（regression）

### 真机 (best-effort)
- 用户在 K8s 侧 `kubectl cordon <node>` → 等 60s（cluster polling 周期）→
  201.160 mysql `SELECT availableCpu FROM PhysicalServerCapacityVO WHERE uuid=...` = 0
- 用户 `kubectl uncordon <node>` → 等下轮 sync → availableCpu 恢复到非 0

---

## Out of scope

- BM2 path（v1.1+）
- KVM 路径加 cordon evaluate（KVM 不是容器节点，concept 不适用）
- `HostCapacityUpdater` POJO 路径全砍（PSC writer collapse plan §7 已 OOS）
- Cordon source 标记（区分 ZStack-by-capacity vs operator）—— 现状 label
  `zstack.io/cordoned-by=capacity` 已能区分，再加 in-memory `cordonedSource`
  字段属于 over-engineer，留 v1.1+
- Cordon evaluate 频率限流（hysteresis 已经天然限流；高频 patch K8s 风险用
  patchNodeWithRetry 内部 backoff 兜住）
- Schema 改动 — 0

---

## Critical files

**改动**:
- `compute/src/main/java/org/zstack/compute/allocator/PhysicalServerCapacityBuffers.java` (新建)
- `compute/src/main/java/org/zstack/compute/allocator/PhysicalServerCapacityUpdater.java` (重构 buffer calc)
- `premium/plugin-premium/container/src/main/java/org/zstack/container/inventory/KubernetesNodeInventory.java` (加字段)
- `premium/plugin-premium/container/src/main/java/org/zstack/container/ContainerNodeCordonService.java` (接口)
- `premium/plugin-premium/container/src/main/java/org/zstack/container/ContainerNodeCordonServiceImpl.java` (impl)
- `premium/plugin-premium/container/src/main/java/org/zstack/container/ContainerEndpointBase.java` (2 处：processNodeTransactional + success)
- listNodes adapter (V1Node → KubernetesNodeInventory 转换处，TBD via grep `new KubernetesNodeInventory()`)

**新建 IT**:
- `premium/test-premium/src/test/groovy/org/zstack/test/integration/container/ContainerK8sCordonMirrorCase.groovy`
- `premium/test-premium/src/test/groovy/org/zstack/test/integration/container/ContainerCapacityCordonEvaluateCase.groovy`
- `premium/test-premium/src/test/groovy/org/zstack/test/integration/container/ContainerCapacityHysteresisUncordonCase.groovy`

**扩展 helper**:
- `premium/test-premium/src/test/groovy/org/zstack/test/integration/container/K8sApiMocks.groovy` (新 mock helper + patchNode 拦截 counter)

---

**Plan owner**: jin.ma · **Drafted**: 2026-05-09 · **Last touch**: 2026-05-09
