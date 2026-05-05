# Phase 1 详细设计第二轮评审 -- KVM Host Expert

**评审人**: KVM Host Domain Expert
**评审文档**: PHASE1_Detailed_Design.md v2.0
**参照**: REVIEW_kvm_host_expert.md (第一轮评审), ARCHITECT_DECISION.md v2.0 (总架构师裁决)
**评审结论**: **APPROVED** (附 2 个低级别建议)

---

## 第二轮评审结果

### 已解决的问题

#### [问题一] (P0) 缺少 clusterUuid 关联

- **原始问题**: PhysicalServerAO 缺少 clusterUuid，无法表达 Host 与 PrimaryStorage/L2Network 的挂载关系和迁移域。
- **解决方式**: 总架构师裁决 1.1 采纳了我的建议 -- clusterUuid 放在 PhysicalServerRoleVO 上 (per-role cluster)，PhysicalServerAO 不放。v2.0 设计中 PhysicalServerRoleVO 已新增 `clusterUuid` 字段（见设计文档 1.4 节第 537 行），AllocateServerMsg 将 `clusterUuid` 改为 `requiredClusterUuids` (List) 支持多 cluster 候选（见 1B.1 节第 1245 行），AllocateServerReply 新增 `clusterUuid` 从 RoleVO 回传（第 1424 行），ServerClusterAllocatorFlow 通过 RoleVO JOIN 实现过滤（第 1535 行）。
- **验证结论**: 完全解决。设计正确地将 cluster 语义下沉到角色层，避免了"一台物理机只属于一个 cluster"的错误假设，同时保证了 KVM 场景下通过 RoleVO JOIN 即可获取 cluster 关联。

-> 已验证通过

#### [问题三] (P0) 容量模型严重缺失 -- cpuNum/cpuSockets/cpuCoreNum/availablePhysicalMemory

- **原始问题**: ServerCapacityVO 只有 `totalPhysicalCpu` 一个 CPU 字段，丢失了 HostCapacityVO 中的 `cpuNum`(逻辑CPU)、`cpuSockets`(插槽)、`cpuCoreNum`(核心) 三个字段；缺少 `availablePhysicalMemory` 监控字段；超分比使用静态 double 字段无法表达两套独立超分管理器。
- **解决方式**: v2.0 的 ServerCapacityVO（设计文档 1.7 节）已完整补齐：
  - `cpuNum` (int) -- 逻辑 CPU 数，第 1011 行
  - `cpuSockets` (int) -- 物理插槽数，第 1014 行
  - `cpuCoreNum` (int) -- 物理核心总数，第 1017 行
  - `availablePhysicalMemory` (long) -- 实际可用物理内存，第 1006 行
  - 移除了 `cpuOverprovisioningRatio`/`memoryOverprovisioningRatio` 静态字段（变更清单 #8）
  - `totalCpu`/`totalMemory` 改为预计算持久化字段，由 `ServerCapacityOverProvisioningManager` 写入（变更清单 #10）
  - 新增独立的 `ServerCapacityOverProvisioningManager` 接口（4B.2 节），支持 per-server 级别的 getCpuRatio/getMemoryRatio/setCpuRatio/setMemoryRatio/recalculate

  我逐一与现有 HostCapacityVO（`/home/mj/zstack/header/src/main/java/org/zstack/header/allocator/HostCapacityVO.java`）对比：

  | HostCapacityVO 字段 | ServerCapacityVO 对应 | 状态 |
  |---------------------|----------------------|------|
  | totalMemory | totalMemory (预计算) | OK |
  | totalCpu | totalCpu (预计算) | OK |
  | cpuNum | cpuNum | OK |
  | cpuSockets | cpuSockets | OK |
  | cpuCoreNum | cpuCoreNum | OK |
  | availableMemory | availableMemory | OK |
  | availableCpu | availableCpu | OK |
  | totalPhysicalMemory | totalPhysicalMemory | OK |
  | availablePhysicalMemory | availablePhysicalMemory | OK |

- **验证结论**: 完全解决。ServerCapacityVO 字段已完整对齐 HostCapacityVO，且超分比管理方案更优（独立 Manager + 预计算持久化，避免了 VO getter 在 JPA 查询中不可用的致命问题）。

-> 已验证通过

#### [问题五] (P0) AllocateServerMsg 分配上下文严重不足

- **原始问题**: 对比 AllocateHostMsg 的 22+ 字段，AllocateServerMsg 只保留了 9 个。缺失 avoidHostUuids/softAvoidHostUuids（反亲和）、l3NetworkUuids（网络可达性）、architecture（CPU 架构过滤）等关键字段。
- **解决方式**: 总架构师裁决 1.2 采用了"核心字段 + extraData Map"两层设计。v2.0 的 AllocateServerMsg（1B.1 节）已补齐：
  - `avoidServerUuids` (List) -- 硬排除，第 1261 行
  - `softAvoidServerUuids` (List) -- 软排除，第 1262 行
  - `architecture` (String) -- CPU 架构过滤，第 1258 行
  - `requiredDisk` (Long) -- 磁盘需求，第 1255 行
  - `extraData` (Map<String, Object>) -- 承载角色特定过滤条件，第 1284 行
  - `listAll` (boolean) -- 返回全部候选，第 1269 行
  - `requiredCpu`/`requiredMemory` 改为 nullable Long（BM 整机分配时为 null），第 1253-1254 行

  对于 KVM 的 l3NetworkUuids、requiredPrimaryStorageUuids、vmInstance 等虚拟化相关字段，通过 `extraData` Map 传递，由 `ServerAllocatorFilterExtensionPoint` 的 KVM 实现方读取。这个设计是正确的 -- 将"物理资源过滤"（状态、容量、架构、池、集群）从"虚拟化资源过滤"（L3 网络、主存储）中分离出来，符合统一硬件管理的分层理念。

  新增的 `ServerAllocatorFilterExtensionPoint` 接口（4B.3 节）允许 KVM 模块注入自定义过滤逻辑，解决了我第一轮评审中问题六（ServerAllocatorFlow 责任链过于简单）的顾虑。

- **验证结论**: 完全解决。两层设计既保证了 AllocateServerMsg 不膨胀为 AllocateHostMsg 的超集，又通过 extraData + FilterExtensionPoint 保留了 KVM 场景所需的全部过滤维度。

-> 已验证通过

#### [问题十三] (P0) 缺少 PreMaintenance 过渡态

- **原始问题**: 现有 HostState 有 PreMaintenance 过渡态，用于 VM 迁移/停止疏散。PhysicalServerState 缺少此状态。
- **解决方式**: v2.0 已在 PhysicalServerState 中新增 `PreMaintenance` 状态（2.1 节第 1814 行），并新增 `preMaintain` 和 `maintain` 两个事件（2.2 节第 1878-1879 行）。状态转换表与 HostState 保持一致：
  - Enabled/Disabled -> preMaintain -> PreMaintenance
  - PreMaintenance -> maintain -> Maintenance
  - PreMaintenance -> enable -> Enabled
  - PreMaintenance -> disable -> Disabled
  - PreMaintenance -> preMaintain -> PreMaintenance (幂等)
  - Maintenance -> enable -> Enabled
  - Maintenance -> disable -> Disabled

  分配器 ServerStateAllocatorFlow 已明确排除 PreMaintenance 状态（2.11 节第 2109 行）。

- **验证结论**: 完全解决。状态机设计与现有 HostState（`/home/mj/zstack/header/src/main/java/org/zstack/header/host/HostState.java`）完全对齐，转换逻辑正确。

-> 已验证通过

#### [问题二] (中) OOB 信息与现有 HostIpmiVO 的冗余

- **原始问题**: PhysicalServerAO 的 OOB 字段与 HostIpmiVO 存在数据冗余。
- **解决方式**: 总架构师裁决未直接回应此问题，但 v2.0 设计通过以下方式间接缓解：
  - OOB 字段全部改为 nullable（变更清单 #3），容器节点无 OOB
  - oobManagementType 增加 `NONE` 选项
  - PhysicalServerAO.oobAddress 作为主数据源，HostIpmiVO 在兼容层（Phase 3）中通过双向同步保持一致
- **验证结论**: 可接受。Phase 1 阶段两者独立存在不会导致问题，Phase 3 兼容层会统一数据源。

-> 已验证通过

#### [问题四] (中) 缺少 availablePhysicalMemory

- **原始问题**: HostCapacityVO 有 `availablePhysicalMemory` 用于监控告警，ServerCapacityVO 缺少此字段。
- **解决方式**: 已在 ServerCapacityVO 中补齐（第 1006 行），DDL 中也已包含（第 3379 行）。
- **验证结论**: 完全解决。

-> 已验证通过

#### [问题六] (高) ServerAllocatorFlow 责任链过于简单

- **原始问题**: 设计只有 5 个 Flow，但现有 HostAllocator 有 13 个 Flow。
- **解决方式**: v2.0 将 Flow 扩展为 7 个（1B.4 节第 1531-1538 行）：ServerStateAllocatorFlow、ServerCapacityAllocatorFlow、ServerRoleAllocatorFlow、ServerClusterAllocatorFlow、ServerPoolAllocatorFlow、ServerArchitectureAllocatorFlow、ServerAvoidAllocatorFlow。更重要的是，新增了 `ServerAllocatorFilterExtensionPoint` 接口（4B.3 节），允许角色模块注入自定义过滤逻辑，通过 extraData 读取角色特定的过滤条件。
- **验证结论**: 完全解决。7 个内置 Flow + 可扩展的 FilterExtensionPoint 提供了足够的过滤维度。

-> 已验证通过

#### [问题七] (中) 唯一约束 (serverUuid, roleType) 可能过严

- **原始问题**: 嵌套虚拟化场景下同一服务器可能有多个 KVM 角色。
- **解决方式**: 总架构师裁决维持了此约束（设计文档第 512 行）。从实际场景考量，ZStack 当前不支持同一物理机上的多个同类型角色实例。嵌套虚拟化是极端边界场景，当前阶段不需要为其放宽约束。
- **验证结论**: 可接受。如果未来需要支持，可以通过 DB migration 解除此约束。

-> 已验证通过

#### [问题八] (中) managementIp 匹配需加 zoneUuid

- **原始问题**: 多个 Zone 下的私有网络可能有相同 managementIp。
- **解决方式**: v2.0 的 KVM 关联实现（6.2 节第 3113 行）已使用 `findByManagementIpAndZone(host.getManagementIp(), host.getZoneUuid())`，注释中明确标注了"v2.0: 加 zone 联合条件, 基于 KVM 专家建议"。
- **验证结论**: 完全解决。

-> 已验证通过

#### [问题九] (高) 兼容层放在 Phase 3 风险太大

- **原始问题**: Phase 1 应包含兼容层的接口定义和至少一个冒烟测试。
- **解决方式**: 总架构师裁决 1.7 采纳了我的建议，调整为 "Phase 1 定义接口 + POC，Phase 2 完整实现"。v2.0 已新增 `ServerAllocatorCompatibilityBridge` 接口（4B.5 节），包含 `convertFromHost`/`convertToHost`/`isEnabled` 三个方法签名，以及 `PhysicalServerGlobalConfig.ALLOCATOR_ENABLED` 特性开关（默认 false）。
- **验证结论**: 完全解决。Phase 1 定义接口 + 空壳 POC 的策略合理，降低了兼容层延迟到 Phase 3 才暴露问题的风险。

-> 已验证通过

#### [问题十] (中) libvirt/qemu 版本信息

- **原始问题**: KVM 特有的 libvirt/qemu 版本信息存储在 SystemTag 中。
- **解决方式**: v2.0 设计正确地保持这些信息在 HostVO 的 SystemTag 中，不在 PhysicalServer 层存储。这与我第一轮评审的建议一致。
- **验证结论**: 不需要变更，保持原状即可。

-> 已验证通过

#### [问题十一] (低) NUMA 拓扑信息

- **原始问题**: HostNumaNodeVO 存储 NUMA 节点拓扑。
- **解决方式**: 保持在 Host 角色层，不在 PhysicalServer 层存储。
- **验证结论**: 正确。NUMA 是 KVM 调度层面的细节，不属于统一硬件管理的范畴。

-> 已验证通过

#### [问题十二] (低) SSH 凭证

- **原始问题**: KVMHostVO 的 SSH 凭证是角色特有的。
- **解决方式**: 保持在 KVMHostVO 中，不在 PhysicalServer 层存储。
- **验证结论**: 正确。SSH 凭证是 KVM agent 通信的角色特有数据。

-> 已验证通过

#### [问题十四] (低) DDL 表名 PhysicalServerAO 应为 PhysicalServerVO

- **原始问题**: @MappedSuperclass 不映射独立表，DDL 表名应为 PhysicalServerVO。
- **解决方式**: v2.0 DDL 中表名已修正为 `PhysicalServerVO`（第 3265 行），`@Table(name = "PhysicalServerVO")` 注解也已明确（第 402 行）。
- **验证结论**: 完全解决。

-> 已验证通过

---

### 未解决或部分解决的问题

无。所有第一轮提出的 14 个问题均已解决或确认可接受。

---

### 新发现的问题

#### [新问题一] (低) APIUpdatePhysicalServerMsg.state 的 validValues 缺少 PreMaintenance

设计文档第 2672 行：

```java
@APIParam(required = false, validValues = {"Enabled", "Disabled", "Maintenance"})
private String state;
```

`validValues` 中缺少 `"PreMaintenance"`。虽然在 HostState 的实际流程中 PreMaintenance 是通过 `APIChangeHostStateMsg` 的 `stateEvent` 触发而非直接设置 state，但如果 APIUpdatePhysicalServerMsg 的 state 字段用于直接设置目标状态（而非事件驱动），那么需要明确设计意图：

- **方案 A**: 如果 state 字段表示目标状态，应增加 `"PreMaintenance"` 到 validValues
- **方案 B**: 如果 PreMaintenance 只能通过事件驱动进入（更符合 HostState 的设计），则应新增 `APIChangePhysicalServerStateMsg`，通过 stateEvent 字段（enable/disable/preMaintain/maintain）控制状态转换，而非在 Update API 中直接设置 state

**建议**: 采用方案 B，与现有 `APIChangeHostStateMsg` 保持一致。PreMaintenance 是一个需要触发 VM 疏散流程的过渡态，不应通过简单的 Update API 直接设置。但这不阻塞 Phase 1 的接口定义工作，可以在实现阶段完善。

#### [新问题二] (低) PhysicalServerVO 中 hardwareDetails 的 @OneToMany 关联与 LAZY fetch 策略不一致

设计文档在两处对 PhysicalServerVO 的关联给出了不同描述：

- 第 386-421 行（1.2 节）：PhysicalServerVO 包含 `@OneToMany(fetch = FetchType.LAZY)` 的 `hardwareDetails` 字段
- 第 593-616 行（3.2 节修订版）：PhysicalServerVO 移除了 hardwareDetails 关联，注释说"hardwareInfo 和 capacity 不做 JPA 关联，改为按需查询"

两处描述存在矛盾。从 KVM 场景的性能考量来看，按需查询（不做 JPA 关联）是更优方案 -- KVM Host 连接和分配场景完全不需要加载 hardwareDetails。

**建议**: 统一为 3.2 节的方案，移除 PhysicalServerVO 上的 hardwareDetails 关联，改为按需查询。如果 API 查询需要返回 hardwareDetails，在 Inventory 构建阶段通过单独的 DAO 查询填充。

---

### 总结评价

v2.0 设计文档基于总架构师的 7 大裁决进行了全面修订，质量显著提升：

1. **容量模型完备性**: ServerCapacityVO 已与 HostCapacityVO 完全对齐，超分比管理方案（独立 Manager + 预计算持久化）比现有 HostCapacityVO 的方案更加规范。

2. **分配上下文充分性**: AllocateServerMsg 的"核心字段 + extraData"两层设计是架构层面的正确抽象。ServerAllocatorFilterExtensionPoint 保证了 KVM 的虚拟化过滤需求不会丢失。

3. **状态机完整性**: 三维状态（state + status + powerStatus）+ roleStatus 的四层设计精确覆盖了 KVM Host 的所有状态场景。PreMaintenance 的加入保证了 VM 疏散工作流的正确性。

4. **兼容性保证**: Phase 1 不修改任何现有模块代码，通过 CompatibilityBridge 接口 + 特性开关实现渐进式迁移。这对于 KVM 子系统的稳定性至关重要。

5. **向后兼容**: HostVO/KVMHostVO/HostCapacityVO 继承链完全不受影响，现有 API（AddKVMHostMsg、AllocateHostMsg 等）100% 兼容。

从 KVM Host 子系统的角度看，v2.0 设计已无阻塞性问题。两个新发现的低级别问题不影响 Phase 1 接口定义工作的推进。

### 最终结论: APPROVED

Phase 1 详细设计 v2.0 通过 KVM Host Expert 的第二轮评审。建议在实现阶段注意上述两个低级别问题的处理。
