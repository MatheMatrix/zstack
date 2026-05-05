# Phase 1 详细设计第二轮评审 -- Baremetal2 架构师

**评审人**: Baremetal2 Domain Expert
**评审文档**: PHASE1_Detailed_Design.md v2.0
**参考文档**: ARCHITECT_DECISION.md v2.0, REVIEW_baremetal2.md (第一轮)
**日期**: 2026-02-28
**评审结论**: APPROVED_WITH_NOTES

---

## 第二轮评审结果

本轮评审逐一验证第一轮提出的 4 个 P0 问题、2 个 P1 问题以及其他关注点在 v2.0 中的解决情况。

---

### 已解决的问题

#### [P0-1] PhysicalServerAO 缺少 powerStatus 字段

**原始问题**: `BareMetal2ChassisPowerStatus.java` 定义了 POWER_ON/POWER_OFF/POWER_UNKNOWN 三态电源状态，与连接状态(status)是正交维度。v1.1 仅有 state + status 两维，无法表达"管理口连通但物理机已关机"等场景。

**v2.0 解决方式**: 总架构师裁决 1.5 采纳了三维状态方案。PhysicalServerAO 第 210-212 行新增:

```java
@Column
@Enumerated(EnumType.STRING)
private PhysicalServerPowerStatus powerStatus;  // PowerOn/PowerOff/PowerUnknown
```

新增 `PhysicalServerPowerStatus` 枚举（第 2003 行附近的 SchedulingMode 同区域），包含 `PowerOn`, `PowerOff`, `PowerUnknown` 三个值。PhysicalServerInventory 也同步新增了 `powerStatus` 字段（第 2163 行）。

分配器状态检查逻辑（第 2108-2113 行）明确:
```
ServerStateAllocatorFlow:
  - powerStatus == PowerOn 或 PowerUnknown (排除 PowerOff)
```

**验证**: PowerOn/PowerOff/PowerUnknown 与 BM2 的 POWER_ON/POWER_OFF/POWER_UNKNOWN 完全对应。PowerUnknown 覆盖了"无 OOB 或 OOB 不可达"的场景。分配器正确排除了 PowerOff 状态的服务器。

**结论**: 已验证，完全解决。

---

#### [P0-3] AllocateServerMsg 扩展参数不足

**原始问题**: BM2 的 `AllocateBareMetal2ChassisMsg` 包含 6 个分配参数（requiredClusterUuids, chassisOfferingUuid, requiredChassisDiskUuid, avoidChassisUuids 等），v1.1 的 AllocateServerMsg 无法承载这些参数。同时 requiredCpu/requiredMemory 为必填，不适用于 BM 整机分配。

**v2.0 解决方式**: 总架构师裁决 1.2 采纳"核心字段 + extraData Map"两层设计。AllocateServerMsg（第 1241-1407 行）的变更包括:

1. **requiredCpu / requiredMemory 改为 nullable Long** -- 第 1253-1254 行，BM 整机分配时传 null
2. **新增 avoidServerUuids** -- 第 1261 行，硬排除列表，对应 BM2 的 avoidChassisUuids
3. **新增 softAvoidServerUuids** -- 第 1262 行，软排除（超出 BM2 原始需求，是有益的增强）
4. **新增 requiredDisk** -- 第 1255 行，磁盘需求
5. **新增 architecture** -- 第 1258 行，架构过滤
6. **新增 requiredClusterUuids (List)** -- 第 1245 行，替代 v1.1 的单个 clusterUuid，支持多集群候选
7. **新增 extraData Map** -- 第 1284 行，承载角色特定参数

extraData 的文档注释（第 1272-1283 行）明确列出了 BM2 的使用方式:
```java
* - BM2: "chassisOfferingUuid" -> String
* - BM2: "requiredChassisDiskUuid" -> String
```

**BM2 分配参数覆盖度对照**:

| BM2 原始参数 | v2.0 对应 | 位置 |
|-------------|-----------|------|
| requiredClusterUuids | AllocateServerMsg.requiredClusterUuids | 核心字段 |
| chassisOfferingUuid | extraData.get("chassisOfferingUuid") | 扩展层 |
| requiredChassisDiskUuid | extraData.get("requiredChassisDiskUuid") | 扩展层 |
| avoidChassisUuids | AllocateServerMsg.avoidServerUuids | 核心字段 |
| chassisUuid (绑定指定) | AllocateServerMsg.serverUuid | 核心字段 |
| architecture | AllocateServerMsg.architecture | 核心字段 |

**验证**: BM2 的全部 6 个分配参数均有对应承载位置。核心层放通用字段，扩展层放角色特有参数，分层清晰。ServerAllocatorFilterExtensionPoint（第 39 行变更清单 #25）允许 BM2 模块注册自定义过滤器来消费 extraData 中的参数。

**结论**: 已验证，完全解决。

---

#### [P0-2] PhysicalServerAO 缺少 clusterUuid 字段

**原始问题**: `BareMetal2ChassisAO.java` 的 clusterUuid 用于分配校验和 architecture 过滤，v1.1 的 PhysicalServerAO 中缺失此字段。

**总架构师裁决**: 拒绝在 PhysicalServerAO 上增加 clusterUuid。理由是 cluster 是角色语义而非物理服务器语义。同一台物理机的 KVM 角色和 BM2 角色可以属于不同 cluster。clusterUuid 放在 PhysicalServerRoleVO 中（per-role cluster）。

**v2.0 解决方式**: PhysicalServerRoleVO（第 537 行）新增:
```java
@Column
private String clusterUuid;  // 角色所属 cluster (per-role cluster)
```

分配时通过 RoleVO JOIN 查询（变更清单 #15: AllocateServerMsg 使用 requiredClusterUuids，ServerClusterAllocatorFlow 通过 PhysicalServerRoleVO JOIN 实现，见第 1535 行注释）。

AllocateServerReply（第 1424 行）也新增了 clusterUuid 字段，从 PhysicalServerRoleVO 获取。

**BM2 影响分析**:

BM2 当前的分配流程: `AllocateBareMetal2ChassisMsg` -> 按 clusterUuid 过滤 BareMetal2ChassisAO。迁移到统一分配后: `AllocateServerMsg(requiredClusterUuids=[...])` -> ServerClusterAllocatorFlow 通过 `JOIN PhysicalServerRoleVO ON roleType=BARE_METAL2 AND clusterUuid IN (...)` 过滤。

这个 JOIN 查询的性能开销是可接受的。PhysicalServerRoleVO 表的 `(serverUuid, roleType)` 唯一约束保证了每个角色类型只有一行记录，不会出现笛卡尔积膨胀。

**我的评估**: 总架构师的理由成立。cluster 确实是角色维度的概念。虽然 BM2 当前一台 chassis 只有一个 cluster，但从统一架构角度看，per-role cluster 的抽象是正确的。分配路径通过 JOIN 查询的额外开销在 BM2 规模（通常数百台 chassis）下完全可以忽略。

**结论**: 接受总架构师裁决。方案合理，BM2 的分配需求可以通过 RoleVO JOIN + requiredClusterUuids 完整满足。

---

#### [P0-4] 角色关联时机不正确

**原始问题**: v1.1 暗示角色关联在 chassis 创建时触发。BM2 的硬件发现需要 PXE 物理重启，发现失败的 chassis 不应消耗 PhysicalServer 资源，因此角色关联应在硬件发现成功后(status=Available)才触发。

**总架构师裁决**: 部分采纳。不强制统一关联时机，而是由各角色的 PhysicalServerRoleProvider 实现自行决定。

**v2.0 解决方式**: 第 3092-3098 行明确:
```
- BM2: 硬件发现成功后关联 (合理，发现失败的 chassis 不应消耗 PhysicalServer 资源)
- BM1: Chassis 创建后就有角色映射需求 (PXE 需要知道 chassis 存在)
- KVM: Host Connected 后关联
- Container: Node 同步入库后关联
```

第 3085 行的角色关联策略表:
```
BM V2 | 需新增: BareMetal2ChassisCreateExtensionPoint 或 EventFacade | 由 BM2 RoleProvider 自行决定
```

PhysicalServerRoleProvider SPI（第 3004 行）提供了 `findRoleAssociation(String roleUuid)` 方法，统一层只提供关联/解关联能力，不规定时机。

**我的评估**: 这个裁决是务实的。四种角色的生命周期差异确实不适合用一个统一规则来约束。BM2 模块在实现 PhysicalServerRoleProvider 时，可以在硬件发现成功回调中调用关联方法，这与我原始诉求的效果完全一致。关键是统一层不强制在创建时关联，给了 BM2 自主决定的空间。

**需要补充的一点**: v2.0 的第 3085 行写的触发时机描述为"由 BM2 RoleProvider 自行决定"，但触发扩展点写的是 `BareMetal2ChassisCreateExtensionPoint`，名称暗示"创建时"。建议将此扩展点更名为 `BareMetal2ChassisReadyExtensionPoint` 或更通用的名称，避免误导实现者在 chassis 创建时就触发关联。这是一个文档级建议，不阻塞设计通过。

**结论**: 接受总架构师裁决。BM2 的硬件发现后关联需求可以在 RoleProvider 实现中自行保证。

---

### 部分解决的问题

#### [P1-1] 缺少 provisionType / chassisOfferingUuid

**原始问题**: BM2 的 `BareMetal2ProvisionType`（Remote/Local/Direct 三种模式）决定整个部署流程走向；`chassisOfferingUuid` 是弹性分配的核心。

**v2.0 处理方式**:
- `chassisOfferingUuid`: 通过 `AllocateServerMsg.extraData` 承载（第 1278 行明确文档化），由 BM2 的 ServerAllocatorFilterExtensionPoint 实现消费。
- `provisionType`: 未在统一层体现。

**我的评估**: chassisOfferingUuid 通过 extraData 承载是合理的，因为它是 BM2 特有的规格筛选概念，不属于物理服务器的通用属性。provisionType 同理，它是 BM2 角色层的部署策略，不应上浮到统一层。这两个字段在 BM2 兼容层实现时，由 BM2 模块自行注入 extraData 即可。

**结论**: 通过 extraData 机制间接解决。provisionType 保留在 BM2 角色层是正确的设计决策。

---

#### [P1-2/其他] Gateway、Bonding、弹性/绑定双模式

**原始问题**: BM2 有三个被完全忽略的特有概念:
1. Gateway (BareMetal2GatewayVO): PXE/DHCP/TFTP 服务载体
2. Bonding (BareMetal2BondingVO): 影响分配约束
3. 弹性 vs 绑定双模式: 通过指定 chassisUuid(绑定) 或 chassisOfferingUuid(弹性) 决定停机时是否释放

**v2.0 处理方式**:
- **Gateway**: 未在统一层体现。这是正确的 -- Gateway 是 BM2 的部署基础设施概念，等价于 KVM 的 host agent，不需要上浮到物理服务器抽象层。PhysicalServerRoleVO.sourceUuid（第 540 行）可以存储 Gateway 关联信息（虽然文档注释写的是 Container 的 endpointUuid 和 BM1 的 pxeServerUuid）。
- **Bonding**: 未在统一层体现。Bonding 是 NIC 配置层面的概念，应通过 ServerHardwareDetailVO（type=NIC）的 content JSON 中携带 bonding 信息，或在 BM2 角色层保留独立 VO。
- **弹性/绑定双模式**: 通过 AllocateServerMsg.serverUuid（第 1250 行，"指定具体 Server，迁移/绑定场景"）+ extraData 中的 chassisOfferingUuid 组合表达。serverUuid 非空 = 绑定模式；serverUuid 为空 + chassisOfferingUuid 非空 = 弹性模式。

**我的评估**: 这些概念确实属于 BM2 角色层的实现细节，不应该污染统一的物理服务器抽象。v2.0 通过 extraData + ServerHardwareDetailVO + PhysicalServerRoleVO.sourceUuid 提供了足够的扩展点，BM2 模块在 Phase 2 实现兼容层时可以利用这些机制。

**残留风险**: PhysicalServerRoleVO.sourceUuid 的注释中没有提及 BM2 的 Gateway 关联。建议在 Phase 2 设计文档中明确 BM2 RoleProvider 的 sourceUuid 语义（如果 BM2 需要使用此字段）。

**结论**: 设计方向正确，BM2 特有概念保留在角色层。统一层提供了足够的扩展机制。

---

### 新发现的问题

#### [NOTE-1] ServerHardwareDetailVO 与 BM2 独立硬件 VO 的同步边界

v2.0 在裁决 1.4 中提到: "BM2 的 NIC/Disk/PCI/GPU 独立 VO 保留在角色层，通过 ServerHardwareDetailVO 做汇总级同步。BM2 不需要把所有子资源都同步上来，只同步汇总信息即可。"

这个设计方向正确，但需要在 Phase 2 中明确:
1. 同步的触发时机 -- 建议在硬件发现完成后由 BM2 RoleProvider 主动推送
2. 同步的粒度 -- ServerHardwareInfoVO 的汇总字段（cpuSockets, totalMemoryBytes 等）必须与 BM2 硬件发现结果一致
3. 同步的方向 -- 应为单向: BM2 角色层 -> 统一层，统一层不应反向修改 BM2 的硬件 VO

这不阻塞 Phase 1，但需要在 Phase 2 的 BM2 兼容层设计中明确定义。

**严重性**: 信息性，Phase 2 跟进。

#### [NOTE-2] BM2 角色的 SchedulingMode.INTERNAL_EXCLUSIVE 与容量清零

ServerRoleType.BARE_METAL2 的 SchedulingMode 为 INTERNAL_EXCLUSIVE（第 2051 行），这意味着 ServerCapacityVO 在分配时会将 availableCpu/availableMemory 清零（独占语义）。

BM2 当前的分配逻辑完全不使用 CPU/内存容量参数，而是基于 chassisOfferingUuid 和 status 过滤。统一分配后，ServerCapacityVO 的容量账本对 BM2 来说更多是"记录"性质而非"决策"性质。

需要确认: INTERNAL_EXCLUSIVE 模式下的 ServerCapacityUpdater，在 BM2 分配成功后，是否应该将 availableCpu/availableMemory 设为 0（表示已独占），还是保持不变（因为 BM2 不关心这些字段）?

我的建议是: 应该清零。即使 BM2 的分配决策不依赖容量，清零可以防止其他角色（如 KVM）尝试在已被 BM2 独占的物理机上分配资源。ServerCapacityVO.exclusiveRoleUuid（第 369 行）字段也应在 BM2 分配时写入，作为独占锁。

**严重性**: 信息性，Phase 2 实现时需要明确行为规范。

#### [NOTE-3] BM2 角色关联触发扩展点命名

第 3085 行提到 BM2 的触发扩展点为 `BareMetal2ChassisCreateExtensionPoint`，但同时说明"由 BM2 RoleProvider 自行决定"关联时机。扩展点名称中的 "Create" 容易误导为在 chassis 创建时触发。

**建议**: Phase 2 实现时，BM2 应使用 `BareMetal2ChassisDiscoveryCompleteExtensionPoint` 或 EventFacade 的 canonicalEvent 来触发角色关联，而非在 ChassisCreate 时触发。这与总架构师裁决"BM2: 硬件发现成功后关联"一致。

**严重性**: 文档级建议，不阻塞 Phase 1。

---

### 评审总结对照表

| # | 原始问题 | 严重性 | v2.0 状态 | 验证结论 |
|---|---------|--------|----------|---------|
| P0-1 | 缺少 powerStatus | P0 | PhysicalServerPowerStatus 三维状态 | 已验证，完全解决 |
| P0-2 | 缺少 clusterUuid | P0 | PhysicalServerRoleVO.clusterUuid (per-role) | 已验证，接受裁决方案 |
| P0-3 | AllocateServerMsg 参数不足 | P0 | 核心字段 + extraData Map 两层设计 | 已验证，完全解决 |
| P0-4 | 角色关联时机 | P0 | 由各 RoleProvider 自行决定 | 已验证，接受裁决方案 |
| P1-1 | provisionType / chassisOfferingUuid | P1 | extraData 承载 chassisOfferingUuid；provisionType 保留角色层 | 间接解决，方向正确 |
| P1-2 | Gateway / Bonding / 双模式 | P1 | 保留角色层，统一层提供扩展机制 | 设计合理，Phase 2 跟进 |

---

### 最终结论: APPROVED_WITH_NOTES

v2.0 已经解决了第一轮提出的全部 4 个 P0 问题。总架构师对 clusterUuid 和角色关联时机的裁决方案，虽然与我原始提议的具体方案不同，但从统一架构的全局视角看是更合理的设计选择。BM2 的核心需求（电源状态、集群过滤、分配参数扩展、关联时机自主权）均已在 v2.0 中得到满足。

**Phase 2 跟进事项**:
1. BM2 兼容层实现中，明确 ServerHardwareDetailVO 与 BM2 硬件 VO 的同步边界和触发时机
2. INTERNAL_EXCLUSIVE 模式下 ServerCapacityVO 的容量清零与 exclusiveRoleUuid 写入行为规范
3. BM2 角色关联应使用硬件发现完成事件而非 chassis 创建事件触发
4. PhysicalServerRoleVO.sourceUuid 在 BM2 场景下的语义定义（是否存储 gatewayUuid）

以上跟进事项均不阻塞 Phase 1 接口定义的合并。
