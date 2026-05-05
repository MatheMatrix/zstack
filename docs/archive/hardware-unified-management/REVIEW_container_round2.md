# Phase 1 详细设计评审意见 -- Container 模块架构师 (第二轮)

**评审人**: Container Module Expert
**评审文档**: PHASE1_Detailed_Design.md v2.0
**对比基线**: REVIEW_container.md (第一轮: 3 P0 + 2 P1)
**参考**: ARCHITECT_DECISION.md v2.0 (7 大裁决)
**评审结论**: APPROVED_WITH_NOTES

---

## 第二轮评审结果

### 已解决的问题

#### [P0-1] 角色关联扩展点错误（HostAfterConnectedExtensionPoint 不适用容器）

**原始问题**: NativeFactory.createHost() 直接抛 UnsupportedOperationException，整个 container 模块不触发 HostAfterConnectedExtensionPoint。v1.1 设计中依赖该扩展点进行容器角色关联是不可行的。

**v2.0 解决方式**:
- 变更清单 #34 明确: "Container 角色关联改用 NativeHostSyncedExtensionPoint"
- 第 6.1 节角色自动关联策略表: Container 行改为 `NativeHostSyncedExtensionPoint (新增, 方案 B)`，触发时机为 "Endpoint 同步完成后"
- 第 6.2 节 KVM 关联实现中 `mapHypervisorToRoleType()` 方法明确注释: "NativeHost 不在此处理 (使用独立扩展点)"
- 总架构师裁决 P0-8 明确采纳: "使用 ContainerEndpointSyncExtensionPoint 或新增 NativeHostSyncedExtensionPoint"

**代码交叉验证**: 确认 `NativeHostSyncedExtensionPoint` 在现有代码库中尚不存在（这是预期的，因为它是 v2.0 新增的扩展点接口，将在实现阶段创建）。实际容器节点同步路径 `ContainerEndpointBase.syncNodes() -> syncNodesFromCluster()` 是正确的触发入口点。

**验证结论**: 已解决。方案 B (新增 NativeHostSyncedExtensionPoint) 是正确的选择，它允许在 `syncNodesFromCluster()` 完成后触发角色关联，完全符合容器模块的实际创建路径。

---

#### [P0-2] ServerCapacityVO 扣减模式不适用 K8s 自主调度

**原始问题**: 容器模块完全没有容量扣减代码，K8s 自主调度 Pod，ZStack 只做事后同步。共享扣减模式对 Container 角色不适用。

**v2.0 解决方式**:
- 总架构师裁决 1.3 引入 `SchedulingMode` 枚举:
  - `INTERNAL_SHARED` (KVM)
  - `INTERNAL_EXCLUSIVE` (BM1/BM2)
  - `EXTERNAL_READONLY` (Container/K8s)
- `ServerRoleType.NATIVE_HOST` 绑定 `SchedulingMode.EXTERNAL_READONLY`
- 设计文档第 1B.6 节 ServerCapacityUpdater 明确:
  - EXTERNAL_READONLY 模式下拒绝 reserve()
  - 仅允许 syncFromExternal()
- 第 1B.7 节提供了三种调度模式的典型使用方式代码示例，包括 EXTERNAL_READONLY 的只读同步示例
- ServerCapacityVO 新增 `schedulingMode` 字段作为缓存，避免每次 JOIN RoleVO 查询
- 第 2.11 节分配器对状态的使用中明确: `schedulingMode != EXTERNAL_READONLY (排除容器)`

**验证结论**: 已解决。SchedulingMode 枚举是优于我原始建议 (`isExternallyScheduled()` 布尔方法) 的设计，它用一个统一的模型包容了四种角色的调度差异，架构上更加内聚。ServerCapacityUpdater 的三种使用示例代码清晰展示了 EXTERNAL_READONLY 的行为约束。

---

#### [P0-3] AllocateServerMsg 对 NATIVE_HOST 无意义

**原始问题**: ZStack 不创建 Pod，Pod 调度权在 K8s Scheduler，AllocateServerMsg 对 Container 角色无实际意义。

**v2.0 解决方式**:
- AllocateServerMsg Javadoc 明确标注: "Container: 不使用此 Msg (EXTERNAL_READONLY 模式)"
- 总架构师裁决 1.2 对 Container 专家的回应: "完全采纳。NATIVE_HOST 不参与主动分配，在 ServerRoleType 上通过 SchedulingMode 声明"
- 总架构师裁决 1.3: AllocateServerMsg 对 EXTERNAL_READONLY 角色返回 `OperationNotSupportedError`
- 第 2.11 节 ServerStateAllocatorFlow 明确将 `schedulingMode != EXTERNAL_READONLY` 作为过滤条件
- ServerRoleType 提供便捷方法 `isExternallyScheduled()` 和 `isInternallyScheduled()`

**验证结论**: 已解决。通过 SchedulingMode 机制在分配链的入口处（ServerStateAllocatorFlow）就排除了 EXTERNAL_READONLY 角色，不会出现误路由问题。如果通过兼容层试图对 NATIVE_HOST 发起分配，也会在 Flow 链中被正确拒绝。

---

#### [P1-1] 缺少 endpointUuid 映射

**原始问题**: NativeHostVO 的核心特有字段 `endpointUuid` 通过 @ForeignKey 指向 ContainerManagementEndpointVO。v1.1 的 PhysicalServerRoleVO 无法表达"容器角色来自哪个管理端点"。

**v2.0 解决方式**:
- PhysicalServerRoleVO 新增 `sourceUuid` 字段（变更清单 #4）
- 注释明确: "管理来源 (Container: endpointUuid, BM1: pxeServerUuid)"
- 第 6.1 节角色关联策略表: Container 行的 sourceUuid 来源为 `ContainerManagementEndpointVO.uuid`
- PhysicalServerRoleProvider SPI 新增 `getSourceUuid(String roleUuid)` 方法
- PhysicalServerRoleInventory 新增 sourceUuid 字段，API 层可见

**代码交叉验证**: 确认现有 `NativeHostVO.endpointUuid` 字段（位于 `premium/plugin-premium/container/src/main/java/org/zstack/container/entity/NativeHostVO.java` 第 23 行）可通过 Container 模块实现的 PhysicalServerRoleProvider.getSourceUuid() 返回，并持久化到 PhysicalServerRoleVO.sourceUuid。

**验证结论**: 已解决。sourceUuid 的设计是通用的，不仅容器能用（endpointUuid），BM1 也能用（pxeServerUuid），体现了统一抽象的思想。

---

#### [P1-2] 缺少 clusterUuid

**原始问题**: NativeHostVO 继承 HostVO 持有 clusterUuid，ServerPool 是扁平物理分组，无法表达 K8s 的 Cluster -> Node 层级语义。

**v2.0 解决方式**:
- 总架构师裁决 1.1 明确: "clusterUuid 放在 PhysicalServerRoleVO 中 (per-role cluster)"
- PhysicalServerRoleVO 新增 `clusterUuid` 字段（变更清单 #4）
- PhysicalServerAO 不增加 clusterUuid（正确的决策，因为 cluster 是角色语义）
- 第 6.1 节: Container 行的 clusterUuid 来源为 `NativeClusterVO.uuid`
- AllocateServerMsg 将 v1.1 的单个 clusterUuid 改为 `requiredClusterUuids` (List)，通过 PhysicalServerRoleVO JOIN 查询
- PhysicalServerRoleProvider SPI 新增 `getClusterUuid(String roleUuid)` 方法

**验证结论**: 已解决。per-role cluster 的设计完美匹配了容器场景：一台物理机可以同时作为 KVM Host（属于 KVM Cluster A）和 K8s Node（属于 K8s Cluster B），两者的 cluster 语义完全不同，放在 RoleVO 上是架构正确的选择。

---

### 其他问题验证

#### [P2] OOB 字段 required=false

**v2.0 解决方式**:
- PhysicalServerAO 中所有 OOB 字段均 nullable（第 181 行注释明确）
- oobManagementType 增加 `NONE` 选项（DDL 中无 NOT NULL 约束）
- APIRegisterPhysicalServerMsg 中所有 OOB 字段改为 `required = false`（变更清单 #32）
- oobManagementType validValues 增加 "NONE"

**验证结论**: 已解决。容器节点注册时可以完全不提供 OOB 信息。

#### [原评审第六节] 容器特有遗漏

| 遗漏项 | v2.0 处理 | 状态 |
|--------|----------|------|
| NativeClusterVO 层级 | 通过 PhysicalServerRoleVO.clusterUuid 指向 NativeClusterVO.uuid | 已解决（引用层面） |
| ContainerManagementEndpointVO | 通过 PhysicalServerRoleVO.sourceUuid 保存 endpointUuid | 已解决（引用层面） |
| GPU/PCI 设备管理 | ServerHardwareDetailVO 新增 GPU/PCI 类型（HardwareDetailType 枚举含 GPU、PCI） | 已解决（数据模型层面） |
| Pod 生命周期特殊性 | EXTERNAL_READONLY 模式 + roleStatus 自定义字符串 (Ready/NotReady) | 部分解决（见下方备注） |

---

### 未解决或部分解决的问题

无 P0/P1 级未解决问题。

---

### 新发现的问题

#### [NOTE-1] NativeHostSyncedExtensionPoint 的接口定义未给出

**严重程度**: 低（P2，不阻塞 Phase 1 接口定义阶段）

**描述**: v2.0 设计在第 6.1 节和变更清单 #34 中明确 Container 角色关联使用 NativeHostSyncedExtensionPoint（方案 B），但文档中未给出该扩展点的接口定义（方法签名、参数、返回值）。作为对比，KVM 的关联实现（第 6.2 节 PhysicalServerRoleAssociator）给出了完整代码。

**建议**: Phase 2 实现前需要明确 NativeHostSyncedExtensionPoint 的接口签名。建议形如:

```java
public interface NativeHostSyncedExtensionPoint {
    /**
     * 当容器端点同步完成后（syncNodesFromCluster 完成），逐个通知新发现/更新的节点。
     * @param host 同步后的 NativeHostVO
     * @param cluster 所属 NativeClusterVO
     * @param endpoint 管理端点 ContainerManagementEndpointVO
     */
    void afterNativeHostSynced(HostInventory host, String clusterUuid, String endpointUuid);
}
```

该接口应在 `ContainerEndpointBase.syncNodesFromCluster()` 中的节点 persist/update 后触发调用。这需要对 container 模块做少量修改（增加扩展点触发代码），应纳入 Phase 2 实施计划。

#### [NOTE-2] PhysicalServerPowerStatus 对容器节点的默认值

**严重程度**: 低（P2）

**描述**: DDL 中 `powerStatus` 默认值为 `PowerUnknown`，这对于容器节点是合理的（容器节点通常无 OOB，无法查询电源状态）。但 ServerStateAllocatorFlow 在第 2.11 节中指出 `powerStatus == PowerOn 或 PowerUnknown` 时才可分配。虽然 EXTERNAL_READONLY 角色已被 schedulingMode 条件排除，但如果未来有其他外部调度角色类型需要参与分配且无 OOB，需确保 PowerUnknown 不会成为阻塞条件。

**建议**: 当前设计已正确处理（EXTERNAL_READONLY 在 schedulingMode 条件就被排除了，PowerUnknown 条件不会被 evaluate），无需修改。仅作为实现时的注意事项记录。

#### [NOTE-3] 容量对账场景下 EXTERNAL_READONLY 模式的 syncFromExternal 触发机制

**严重程度**: 低（P2）

**描述**: 第 1B.7 节给出了 EXTERNAL_READONLY 的容量同步示例代码，但未明确谁负责触发这个同步操作。对于容器场景，容量数据应由 ContainerEndpointBase 的定时同步任务或 NativeHostSyncedExtensionPoint 的回调来触发。PhysicalServerGlobalConfig 中定义了 `capacity.reconciliation.interval`（默认 3600 秒），但对于 EXTERNAL_READONLY 模式，容量对账应依赖外部调度器的实际数据而非内部重算。

**建议**: Phase 2 实现 RecalculateServerCapacityMsg 处理逻辑时，对 EXTERNAL_READONLY 模式应跳过内部重算，改为触发一次外部同步（通过 PhysicalServerRoleProvider.getActualUsage() 获取 K8s 报告的实际用量）。当前 SPI 中 `getActualUsage(String roleUuid)` 的设计已经为此提供了接口支撑。

---

### 第一轮与第二轮问题解决对照表

| # | 级别 | 问题 | v1.1 状态 | v2.0 状态 | 解决方式 |
|---|------|------|----------|----------|---------|
| 1 | P0 | 角色关联扩展点错误 | 未解决 | 已解决 | 改用 NativeHostSyncedExtensionPoint |
| 2 | P0 | ServerCapacityVO 扣减不适用 K8s | 未解决 | 已解决 | SchedulingMode.EXTERNAL_READONLY |
| 3 | P0 | AllocateServerMsg 对 NATIVE_HOST 无意义 | 未解决 | 已解决 | SchedulingMode 排除 + OperationNotSupportedError |
| 4 | P1 | 缺少 endpointUuid 映射 | 未解决 | 已解决 | PhysicalServerRoleVO.sourceUuid |
| 5 | P1 | 缺少 clusterUuid | 未解决 | 已解决 | PhysicalServerRoleVO.clusterUuid (per-role) |
| 6 | P2 | OOB 字段 required=false | 未解决 | 已解决 | APIParam required=false + NONE 选项 |

---

### 最终结论: APPROVED_WITH_NOTES

**v2.0 设计从容器模块的视角完全通过评审。** 所有 3 个 P0 问题和 2 个 P1 问题均已正确解决。总架构师裁决采纳了本模块提出的全部核心建议，特别是:

1. **SchedulingMode 枚举的引入** -- 这是比我原始建议（布尔方法 isExternallyScheduled）更优的架构抽象，用一个统一的类型系统描述了四种角色截然不同的调度模式。
2. **per-role cluster 的设计** -- 将 clusterUuid 放在 PhysicalServerRoleVO 而非 PhysicalServerAO 上，完美匹配了容器场景中"一台物理机可承载多种角色、各角色属于不同 cluster"的现实。
3. **sourceUuid 的通用化** -- 不仅解决了容器的 endpointUuid 映射问题，还覆盖了 BM1 的 pxeServerUuid，体现了良好的统一抽象。

**3 个 NOTE 级备注** 均为 Phase 2 实现阶段的注意事项，不阻塞 Phase 1 的接口定义工作:
- NOTE-1: NativeHostSyncedExtensionPoint 需在 Phase 2 前定义接口签名
- NOTE-2: PowerUnknown 默认值在当前设计中已被正确处理
- NOTE-3: EXTERNAL_READONLY 的容量对账应依赖外部同步而非内部重算

**签署**: Container Module Architecture Lead
**日期**: 2026-02-28
