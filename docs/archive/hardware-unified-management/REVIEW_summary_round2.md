# 统一硬件管理 Phase 1 v2.0 第二轮评审汇总

**评审时间**: 2026-02-28
**评审对象**: PHASE1_Detailed_Design.md v2.0
**参考文档**: ARCHITECT_DECISION.md v2.0 (7 大裁决)
**参与专家**: KVM Host / Baremetal V1 / Baremetal V2 / Container / Compute Allocator

---

## 总体结论：全部 5 个专家均通过评审

| 专家 | 第一轮结论 | 第二轮结论 | P0 解决率 | P1 解决率 | 新发现问题 |
|------|-----------|-----------|----------|----------|-----------|
| KVM Host | NEEDS_REVISION (3P0+5中) | **APPROVED** | 4/4 (100%) | 10/10 (100%) | 2 NOTE (低) |
| Baremetal V1 | NEEDS_MODIFICATION (2P0+5P1) | **APPROVED** | 2/2 (100%) | 4/5 (80%) | 2 P2 (不阻塞) |
| Baremetal V2 | NEEDS_MODIFICATION (4P0) | **APPROVED_WITH_NOTES** | 4/4 (100%) | 2/2 (100%) | 3 NOTE (信息性) |
| Container | 需修改 (3P0+2P1) | **APPROVED_WITH_NOTES** | 3/3 (100%) | 2/2 (100%) | 3 NOTE (低) |
| Compute Allocator | 必须修复 (2必须+4建议) | **APPROVED** | 2/2 (100%) | 8/8 (100%) | 3 观察点 (低) |

**P0 问题解决率: 15/15 (100%)**
**P1 问题解决率: 26/27 (96%)**

---

## 第一轮 P0 问题解决验证

| # | P0 问题 | 指出专家 | v2.0 解决方式 | 验证状态 |
|---|---------|---------|--------------|---------|
| 1 | @Transactional + @DeadlockAutoRestart 编译错误 | Allocator | 拆分为内层 `_run()` + 外层 `run()` 两层方法 | 已验证通过 |
| 2 | DDL 表名 PhysicalServerAO → PhysicalServerVO | KVM, Allocator | 所有 DDL + FK 引用统一修正 | 已验证通过 |
| 3 | 缺少 clusterUuid | KVM, BM1, BM2, Container | PhysicalServerRoleVO.clusterUuid (per-role) | 已验证通过 |
| 4 | 缺少 powerStatus | BM2 | PhysicalServerPowerStatus 三维状态 | 已验证通过 |
| 5 | 缺少 PreMaintenance 过渡态 | KVM | PhysicalServerState 新增 PreMaintenance | 已验证通过 |
| 6 | 缺少 Discovering/DiscoveryFailed | BM1 | PhysicalServerRoleVO.roleStatus 自定义字符串 | 已验证通过 |
| 7 | AllocateServerMsg 字段严重不足 | KVM, BM2, Allocator | 核心字段 + extraData Map 两层设计 | 已验证通过 |
| 8 | Container 角色关联扩展点错误 | Container | 改用 NativeHostSyncedExtensionPoint | 已验证通过 |
| 9 | NATIVE_HOST 不应参与主动分配 | Container | SchedulingMode.EXTERNAL_READONLY | 已验证通过 |
| 10 | ServerCapacityVO 缺少 cpuNum 等字段 | KVM, Allocator | 完整对齐 HostCapacityVO 所有字段 | 已验证通过 |
| 11 | 超分比 VO getter 不兼容 DB 查询 | KVM, Allocator | 独立 Manager + 预计算持久化 | 已验证通过 |
| 12 | 角色关联时机不正确 | BM2 | 由各 RoleProvider 自行决定 | 已验证通过 |

---

## 总架构师 7 大裁决采纳情况

| 裁决 | 内容 | 专家验证 |
|------|------|---------|
| 1.1 | clusterUuid 放 PhysicalServerRoleVO (per-role) | KVM/BM2/Container 均认可 |
| 1.2 | AllocateServerMsg = 核心字段 + extraData Map | KVM/BM2/Allocator 均认可 |
| 1.3 | SchedulingMode 三枚举 (SHARED/EXCLUSIVE/READONLY) | Container/BM2/Allocator 均认可 |
| 1.4 | 1:1 汇总 + 1:N 明细双层硬件信息 | BM1/BM2 均认可 |
| 1.5 | 三维状态 (state + status + powerStatus) | KVM/BM2 均认可 |
| 1.6 | 独立 ServerCapacityOverProvisioningManager | KVM/Allocator 均认可 |
| 1.7 | Phase 1 接口 + POC，Phase 2 完整兼容层 | KVM/Allocator 均认可 |

---

## 第二轮新发现的 NOTE/观察点 (均不阻塞 Phase 1)

### KVM Expert (2)
1. APIUpdatePhysicalServerMsg.state 的 validValues 缺少 PreMaintenance (建议事件驱动)
2. PhysicalServerVO 中 hardwareDetails 的 @OneToMany 关联描述存在矛盾 (建议统一为按需查询)

### Baremetal V1 Expert (2)
1. OobManagementStrategy SPI 接口未在 v2.0 中定义 (Phase 2 跟进)
2. PreconfigurationTemplate 在统一层的关联方式未明确 (Phase 2 跟进)

### Baremetal V2 Expert (3)
1. ServerHardwareDetailVO 与 BM2 独立硬件 VO 的同步边界需明确 (Phase 2)
2. INTERNAL_EXCLUSIVE 模式下 ServerCapacityVO 清零行为需规范 (Phase 2)
3. BM2 角色关联触发扩展点命名建议改为 DiscoveryComplete (文档级)

### Container Expert (3)
1. NativeHostSyncedExtensionPoint 接口签名未给出 (Phase 2 前补充)
2. PowerUnknown 默认值已被 schedulingMode 条件正确保护 (信息性)
3. EXTERNAL_READONLY 容量对账应依赖外部同步而非内部重算 (Phase 2)

### Compute Allocator Expert (3)
1. ServerCapacityUpdater 包路径需在 Phase 2 确认 (实现细节)
2. ServerAllocatorFilterExtensionPoint 建议作为独立 Flow 嵌入链中 (实现建议)
3. ServerCapacityVO.schedulingMode 角色变化时的缓存一致性 (Phase 2)

---

## 设计亮点（专家共识）

1. **SchedulingMode 枚举** -- Container 专家评价"优于原始建议的布尔方法，是更优的架构抽象"
2. **per-role cluster** -- 4 位专家均认可，完美匹配"一台物理机多角色、各角色属不同 cluster"的现实
3. **extraData Map 两层设计** -- Allocator 专家评价"不膨胀成 AllocateHostMsg 的超集"
4. **超分比独立 Manager + 预计算持久化** -- KVM 专家评价"比现有 HostCapacityVO 方案更规范"
5. **sourceUuid 通用化** -- Container 专家评价"不仅解决容器的 endpointUuid，还覆盖 BM1 的 pxeServerUuid"
6. **ServerCapacityUpdaterRunnable 回调模式** -- Allocator 专家验证"完全对齐 HostCapacityUpdater 模式"
7. **Phase 1 不修改任何现有代码** -- 所有专家一致认可增量策略的安全性

---

## 结论

**PHASE1_Detailed_Design.md v2.0 通过全部 5 位领域专家的第二轮评审，可以作为 Phase 1 实施的权威设计文档。**

Phase 2 实施前需关注 13 个 NOTE/观察点（均不阻塞 Phase 1），建议在 Phase 2 设计文档中逐一回应。
