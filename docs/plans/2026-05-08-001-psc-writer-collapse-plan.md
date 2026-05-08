# Plan: PSC Writer Collapse — Two-Layer Capacity Model (2026-05-08)

> **Triggered by**: 0507 bin 真机回归（172.26.201.160）发现 `PhysicalServerCapacityUpdater.recalculate` 0 调用点 + KVM PSC 裸写无锁 + Container PSC 停在 InitFlow defaults。
> **Branch**: `feature/unifi-host-dev` · parent `35433f9cbd` · premium `150c6eec88`
> **PRD pin**: cloud_prd `f9928ec`
> **Closes**: STATUS.md §4.2 U10 / §5 line 190 / NB-7 文档自相矛盾收口；AC-CM-13 当前死分支兑现

---

## 1. Problem statement (来自 0507 真机实测，证据归档于本 session)

| # | 问题 | 证据 |
|---|---|---|
| P1 | `PhysicalServerCapacityUpdater.recalculate(serverUuid)` 在生产代码 0 调用点 | grep 全仓非测试代码：0 处真实调用 / 0 处 @Autowired 注入 / 4 处 javadoc 引用 |
| P2 | KVM PSC `totalCpu/totalMemory` 写入路径裸写无锁 | `HostAllocatorManagerImpl.handle(ReportHostCapacityMessage)` line 275-313 用 `dbf.persist/update`，无 PESSIMISTIC_WRITE，无 @Transactional → 破 NB-30 单锁键不变量 |
| P3 | Container PSC 写过一次就停留默认 0 | `PhysicalServerPathTwoOrchestrator.runStandalone → InitPhysicalServerCapacityFlow` 创 row（defaults），之后 `syncNodesFromCluster` per-node 0 处调 recalculate / 0 处更新 PSC |
| P4 | AC-CM-13「cordoned 节点 free → reserved」生产路径死分支 | `ContainerCordonReservedCapacityExtension:87-92` 走 HCV null skip — HCV VIEW 硬编码 `r.roleType='KVM_HOST'`，Container Host HCV 永远 null |
| P5 | STATUS.md §4.2 U10 vs §5 line 190 自相矛盾，NB-7 line 141 stale | "Pod 聚合返 0" vs "Pod 聚合 ✅ DONE" 共存；processNodeTransactional 实际在 line 745 |

---

## 2. Design — Two-Layer Capacity Model

```
┌────────────────────────────────────────────────────────────────┐
│ Layer 1  物理量 — 各模块自己 sync 入口写                         │
│                                                                │
│ KVM:       ReportHostCapacityMessage.handle                    │
│            → 写 PSC.totalCpu / totalMemory / cpuSockets /      │
│              cpuCoreNum / cpuNum                               │
│            → 末尾 recalculate(serverUuid)                      │
│                                                                │
│ Container: ContainerEndpointBase.syncNodesFromCluster          │
│            per-NativeHost success callback                     │
│            → 写 PSC.totalCpu / totalMemory                     │
│              （from NativeHostVO.capacityCpu/Memory）          │
│            → 末尾 recalculate(serverUuid)                      │
│                                                                │
│ BM2:       v1.1+ 走同模式（本 plan 不动）                       │
└────────────────────────────────────────────────────────────────┘
                            ↓
┌────────────────────────────────────────────────────────────────┐
│ Layer 2  虚拟量 — 唯一入口                                       │
│                                                                │
│ PhysicalServerCapacityUpdater.recalculate(serverUuid)          │
│   - 拿 PESSIMISTIC_WRITE PSC 锁 (serverUuid)                   │
│   - 跑所有 RoleProvider.getCapacityConsumption                 │
│   - 跑所有 ServerReservedCapacityExtensionPoint                │
│   - available = total - consumed - buffer - reserved          │
│                                                                │
│ Trigger 源：                                                    │
│   - 物理量 sync（Layer 1 末尾）                                  │
│   - VM lifecycle (KVM)                                          │
│   - GlobalConfig 改 ratio / reserved                           │
│   - Cordon flip / Pod 增删（自动随 sync 触发）                   │
└────────────────────────────────────────────────────────────────┘
```

**不动的边界**:
- `HostCapacityVO` VIEW 定义保留（KVM 专属语义；用户决策）
- `PhysicalServerHardwareService.discoverHardware` 保留（继续只写 HardwareInfoVO 元数据，不掺和 PSC.total）
- `HostCapacityUpdater` POJO 路径保留（VM allocator flow `HostCapacityAllocatorFlow` 还在用，本 plan 标 `@Deprecated` + Javadoc 指向 recalculate；下个 phase 全砍）

---

## 3. Implementation units

### U-A — KVM `ReportHostCapacityMessage` handler 收敛

**File**: `compute/src/main/java/org/zstack/compute/allocator/HostAllocatorManagerImpl.java`
**Lines**: 275-313
**Owner agent**: kvm-host-expert (review by compute-resource-allocator)

**改动**:
1. 把整个 handler 包进 `@Transactional` 事务
2. 用 `em.find(PhysicalServerCapacityVO.class, serverUuid, LockModeType.PESSIMISTIC_WRITE)` 拿锁（serverUuid 由 `resolveServerUuidOrThrow(hostUuid)` 来 — line 285 已有）
3. **保留**: `setTotalCpu / setTotalMemory / setCpuSockets / setCpuCoreNum / setCpuNum / setTotalPhysicalMemory / setAvailablePhysicalMemory` 等物理量字段
4. **删除**: 任何 `setAvailableCpu / setAvailableMemory` 直写
5. `dbf.update(psc)` 后调 `psCapacityUpdater.recalculate(serverUuid)`（同事务内 re-entry safe，因为同 lock target）

**注入**: `@Autowired PhysicalServerCapacityUpdater psCapacityUpdater`，按 CLAUDE.md §15 用 lazy getter pattern 还是直接 @Autowired？—— 这里 `HostAllocatorManagerImpl` 不是 `@Configurable preConstruction=true`，普通 Spring bean，直接 @Autowired 即可。

**验证锚点**: 真机 172.26.201.160 KVM host `65a7d893e46d447f89ce8a1a49b58ed6` 周期上报后，`PhysicalServerCapacityVO[serverUuid=d066db930a0041138640fcae28c1514d]` 的 `availableCpu` 值由 recalculate 算出（不再由 handler 直写）。

### U-B — KVM `RecalculateHostCapacityMsg` handler 替换

**File**: `compute/src/main/java/org/zstack/compute/allocator/HostAllocatorManagerImpl.java`
**Lines**: 155-269
**Owner agent**: compute-resource-allocator (review by kvm-host-expert)

**改动**:
1. 把整段 `HostCapacityUpdater(s.hostUuid).run(...)` 替换为：
   ```
   String serverUuid = resolveServerUuidOrThrow(msg.getHostUuid());
   psCapacityUpdater.recalculate(serverUuid);
   bus.reply(msg, new RecalculateHostCapacityMsgReply());
   ```
2. `HostCapacityUpdater` 类**不删**，标 `@Deprecated` + Javadoc 注明「仅 `HostCapacityAllocatorFlow` / `ReturnHostCapacityMsg` 等 VM allocator 增量写路径使用，新调用点请用 `PhysicalServerCapacityUpdater.recalculate`」
3. 上游所有发 `RecalculateHostCapacityMsg` 的 trigger（HostBase connect flow tail / HostManagerImpl ratio change / HostCpuOverProvisioningManagerImpl / KvmHostReserveExtension）**保持不变** —— 只是 handler 内部走新路径，对调用方透明

**约束**: msg 接口保持 backward compatible（不改 msg 字段、不改 reply 字段、不改 service id）。

### U-C — Container `syncNodesFromCluster` 写物理量 + recalculate

**File**: `premium/plugin-premium/container/src/main/java/org/zstack/container/endpoint/ContainerEndpointBase.java`
**Lines**: ~659-717（`runStandalone` success callback 内 / per-NativeHost fan-out tail）
**Owner agent**: container-module-architect

**改动**: 在 `runStandalone(nativeHost, ...)` 的 success callback 里（确保 `CreatePhysicalServerRoleFlow` 已经 insert RoleVO 之后）追加：

```java
String serverUuid = Q.New(PhysicalServerRoleVO.class)
        .eq(PhysicalServerRoleVO_.roleUuid, h.getUuid())
        .eq(PhysicalServerRoleVO_.roleType, ServerRoleType.CONTAINER_HOST.toString())
        .select(PhysicalServerRoleVO_.serverUuid)
        .findValue();
if (serverUuid != null) {
    // Layer 1: 物理量
    new SQLBatch() {
        @Override
        protected void scripts() {
            PhysicalServerCapacityVO psc = q(PhysicalServerCapacityVO.class)
                    .eq(PhysicalServerCapacityVO_.uuid, serverUuid)
                    .lock(LockModeType.PESSIMISTIC_WRITE).find();
            if (psc != null) {
                NativeHostVO nh = dbf.findByUuid(h.getUuid(), NativeHostVO.class);
                if (nh != null && nh.getCapacityCpu() != null) {
                    psc.setTotalCpu(nh.getCapacityCpu() / 1000); // mcore → cores
                }
                if (nh != null && nh.getCapacityMemory() != null) {
                    psc.setTotalMemory(nh.getCapacityMemory());
                }
                merge(psc);
            }
        }
    }.execute();
    // Layer 2: 虚拟量
    psCapacityUpdater.recalculate(serverUuid);
}
```

**单位口径**: `NativeHostVO.capacityCpu` 是 milliCPU（K8s 标准），PSC.totalCpu 是 vCPU 整数核（KVM 路径口径）—— floor div 1000，跟 `ContainerNodeInfoDiscoveryAdapter:populateCarrier` 已有规则一致（"200m" 或 "0.5" 向下圆到 0）。

**注入**: `@Autowired PhysicalServerCapacityUpdater psCapacityUpdater`。

**验证锚点**: 真机 172.26.201.160 7 个 NativeHost 对应 7 个 PSC row，sync 后 `totalCpu` 变成 8/120/16/192/8/192/8（cores），`availableCpu` 由 recalculate 减去 `getCapacityConsumption` Pod 聚合得出。

### U-D — `ContainerCordonReservedCapacityExtension` HCV fallback

**File**: `premium/plugin-premium/container/src/main/java/org/zstack/container/server/ContainerCordonReservedCapacityExtension.java`
**Lines**: 87-92
**Owner agent**: container-module-architect

**改动**: 把 `dbf.findByUuid(hostUuid, HostCapacityVO.class)` null skip 分支改成：

```java
// HCV VIEW 滤 KVM_HOST → Container Host HCV 永远 null。回落 PSC 真表（同 lock target，
// recalculate 已持有 PESSIMISTIC_WRITE on serverUuid，extension 在锁内被调，re-entry safe）。
String serverUuid = Q.New(PhysicalServerRoleVO.class)
        .eq(PhysicalServerRoleVO_.roleUuid, hostUuid)
        .eq(PhysicalServerRoleVO_.roleType, ServerRoleType.CONTAINER_HOST.toString())
        .select(PhysicalServerRoleVO_.serverUuid)
        .findValue();
if (serverUuid == null) {
    return null; // 没 RoleVO，PS 还没建出来或正在 cascade-delete
}
PhysicalServerCapacityVO psc = dbf.findByUuid(serverUuid, PhysicalServerCapacityVO.class);
if (psc == null || psc.getTotalCpu() == 0) {
    logger.debug("[ContainerCordonReservedCapacityExtension] PSC[serverUuid={}] missing or " +
            "totalCpu=0 (Layer 1 not synced yet); skipping reservation.", serverUuid);
    return null;
}

ReservedHostCapacity rc = new ReservedHostCapacity();
rc.setReservedCpuCapacity(Math.max(0L, psc.getTotalCpu() - usedCpu));
rc.setReservedMemoryCapacity(Math.max(0L, psc.getTotalMemory() - usedMem));
return rc;
```

**配套**: 删除文件顶部 `import org.zstack.header.allocator.HostCapacityVO` 不再用。

### U-E — IT cases (4 个，按 12a 红线)

**Owner agent**: 各 case 派对应模块 expert

| Case | File | 走的 production API | 断言 |
|---|---|---|---|
| `KvmReportHostCapacityRecalcCase` | `compute/src/test/groovy/.../KvmReportHostCapacityRecalcCase.groovy` | KVM addHost + KVM agent 上报 ReportHostCapacityMessage | PSC.totalCpu/totalMemory 写入 + PSC.availableCpu = total - consumed - buffer（不是 0、不是 total） |
| `ContainerSyncRecalcCase` | `premium/test-premium/.../container/ContainerSyncRecalcCase.groovy` | addContainerManagementEndpoint + syncContainerManagementEndpoint + K8sApiMocks 1 cluster 1 node + 2 Running pod | PSC.totalCpu = NativeHost.capacityCpu/1000；PSC.availableCpu = total - Σpod.cpuNum - buffer |
| `ContainerCordonReservedCase` | `premium/test-premium/.../container/ContainerCordonReservedCase.groovy` | sync → cordon node → 再次 sync | cordon 后 PSC.availableCpu == 0（reserved = total - used 全占满）。覆盖 AC-CM-13 当前死分支 |
| `KvmContainerCoexistRecalcCase` | `premium/test-premium/.../server/KvmContainerCoexistRecalcCase.groovy` | 同 PS 挂 KVM_HOST + CONTAINER_HOST 两 role（非真实拓扑，但 SchedulingMode 允许） | recalculate 聚合两边 used；PSC.availableCpu 正确扣减两 role 之和 |

**12a 红线**: case body 禁 `dbf.persist*` / `SQL.New("insert")`；只能调 production API + 等同步 / cluster polling。fixture helper（如 `K8sApiMocks.mockSingleZakuCluster`）允许 bootstrap K8s mock 状态。

---

## 4. 风险 + 兼容性

| 风险 | 缓解 |
|---|---|
| KVM agent 周期上报频率较高（每 30 min/host，加上 connect flow），recalculate 全量聚合可能慢 | 单 host recalculate 走 PSC PESSIMISTIC_WRITE 锁同 row，并发不冲突；fleet sweep 见 R5 节，本 plan 范围内不动 |
| `HostCapacityUpdater` POJO 路径暂留，跟新 recalculate 写同一行（PSC）可能撞锁 | recalculate 单 lock key serverUuid + HostCapacityUpdater 老路径也是 serverUuid（line 154）→ 同 lock 串行，安全。POJO 只 writeback 3 字段，跟 recalculate 写 available\* 字段重叠但**最后一个 commit 赢** —— 在 VM lifecycle 高频 + KVM 周期上报低频的现实里收敛是可接受的（下个 phase 砍 POJO） |
| `availablePhysicalMemory` 字段 ReportHostCapacityMessage handler 还在写 | 这字段在 `_recalculate` 里没动（不是 reserved/consumed 派生），handler 继续写 OK。只 `availableCpu/availableMemory` 由 recalculate 接管 |
| Backward compat | API msg / API reply / service id / VO schema 全部不变；handler 内部实装变化对外透明 |
| Schema / Flyway | 不动 |

---

## 5. 收口对账

### STATUS.md 同步更新
- §4.1 R3 ⚠️ → ✅（AC-CM-13 reserved 路径打通）
- §4.1 R4 ⚠️ → ✅（path-2 + container 容量管道补全；KVM-cross-role consumption 兑现）
- §4.2 U10 「createRoleEntity stub + getCapacityConsumption 返 0」改为「createRoleEntity 显式抛 EXTERNAL_READONLY；getCapacityConsumption ✅ 真值；recalculate trigger ✅ wired」
- §4.3 NB-7 ❌ → ✅（processNodeTransactional 描述修正 + recalculate hook 落地）
- §5 line 190 描述跟 §4.2 U10 同步收敛
- §5 ✅ 区新增「PSC writer collapse — Layer 1 (KVM/Container sync) + Layer 2 (recalculate sole writer)」一行

### ADR 引用
- ADR-001/002 POJO 例外保留（HostCapacityUpdater 暂留语义不变）
- ADR-012 ordering normative 兑现（recalculate ordering 走 single lock key）
- NB-22 in-method POJO 例外（HostCapacityUpdater 仍是路径 1）
- NB-24 fail-loud（recalculate 内部已实装，本 plan 不动）
- NB-30 单锁键（U-A 修复 KVM 当前破洞）

### Phase 关联
本 plan 是 Phase 3 fix-plan 的一刀，但范围窄、独立可 ship。**不依赖 Phase 3 master plan 起草完成**（master plan 仍待写）。可单独 commit + push + MR。

---

## 6. Execution

### Phase 0 — Worktree 设置
```bash
cd /home/mj/zstack-workspace
mkdir -p worktrees
git -C zstack-unifi-host worktree add worktrees/psc-writer-collapse feature/unifi-host-dev
git -C zstack-unifi-host/worktrees/psc-writer-collapse/premium worktree add ../../worktrees/psc-writer-collapse-premium feature/unifi-host-dev
# .m2 隔离按 CLAUDE.md §13
```

### Phase 1 — 并行落码 (U-A+U-B 跟 U-C+U-D 独立)
- **Lane KVM** (kvm-host-expert + compute-resource-allocator): U-A → U-B → mvn-safe-install -pl compute,plugin/kvm
- **Lane Container** (container-module-architect): U-C → U-D → mvn-safe-install -pl premium

### Phase 2 — IT cases (U-E)
4 case 各派对应 expert 写。可在 Phase 1 完成后并行起步。

### Phase 3 — 真机回归
172.26.201.160 endpoint `ef554bb8255d4ce0b891a1367841b88b`。验证：
- 7 NativeHost PSC.totalCpu/totalMemory 反映 K8s 真值
- KVM host PSC.availableCpu 由 recalculate 算出（不再裸写）
- cordon 一个 K8s 节点 → PSC.availableCpu drop to 0

### Phase 4 — Commit 收敛
- 单 commit per U-unit（5 commit）`zcommit <type> <scope> "" "desc" "body"`
- 一律 `git push origin feature/unifi-host-dev`
- 不开 MR，等 master plan / Phase 3 整体 ship 时统一 PR

---

## 7. Out of scope

- BM2 path（v1.1+）
- HCV VIEW 改 multi-roleType（用户决策保留 KVM 专属语义）
- HostCapacityUpdater POJO 路径全砍（下 phase）
- KVM agent 周期 timer 配置改动（用户决策物理量心跳保留）
- ServerAllocatorChain R2 Group C
- Hardware service 写 PSC.total（保留分层：HardwareInfoVO 跟 PSC.total 解耦，元数据归 hardware service，容量归 sync 入口）

---

**Plan owner**: jin.ma · **Drafted**: 2026-05-08 · **Last touch**: 2026-05-08
