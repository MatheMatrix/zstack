# Container/K8s 角色适配器深度审阅报告

**审阅人**: Container/K8s 模块领域专家
**日期**: 2026-03-19
**审阅对象**: `docs/architecture-container-adapter-2026-03-18.md` v1.0
**对照文档**: `docs/architecture-unified-hardware-2026-03-18.md` (骨架 v1.0, 第 3 章 SPI / 第 9.4 章 Container)
**方法论**: 读代码 -> 用户场景推演 -> 耦合诊断 -> 设计文档对照 -> 输出

---

## 0. 总体结论

**有条件通过**。Container 适配器文档是四个适配器中最"干净"的——EXTERNAL_READONLY 模式天然减少了与分配引擎的交互面，降低了耦合风险。但存在 **3 个阻塞项** 和 **5 个改进建议**，需在进入实现前解决阻塞项。

| 级别 | 编号 | 摘要 |
|------|------|------|
| **阻塞** | BLOCK-1 | getCapacityConsumption 的 PodVO 聚合查询写法错误，会返回单行而非 SUM |
| **阻塞** | BLOCK-2 | 过期节点处理中先标 Stale 再删 NativeHost 的顺序导致 HostDeleteExtensionPoint 重复触发 |
| **阻塞** | BLOCK-3 | 混部场景下 PhysicalServerCapacityVO available 字段语义含糊，方案 A 未考虑纯 Container 节点 |
| 改进 | IMP-1 | serialNumber normalize 逻辑应抽到 PhysicalServerManagerImpl，避免各适配器各写一份 |
| 改进 | IMP-2 | syncPhysicalServer 需要处理 K8s Node 的 NotReady/SchedulingDisabled 状态 |
| 改进 | IMP-3 | Pod 容量聚合应使用 SUM 而非逐 Host 遍历，减少 N+1 查询 |
| 改进 | IMP-4 | endpointUuid 语义在 ContainerRoleInventory 中暴露，但骨架的 RoleInventory 基类无此概念 |
| 改进 | IMP-5 | CapacityState 字段使用硬编码字符串 "Ready" 而非枚举，与 INC-4 冲突 |

---

## 1. EXTERNAL_READONLY 模式语义完整性审阅

### 1.1 核心语义是否自洽

设计文档定义 EXTERNAL_READONLY 的哲学为"ZStack 是 K8s 容量数据的镜像，不是管控面"。逐一验证语义闭环：

| 语义断言 | 文档覆盖 | 代码路径覆盖 | 审阅结论 |
|---------|---------|-------------|---------|
| ZStack 不参与 Container 调度 | 3.1 节明确声明 | SchedulingModeFilterFlow 过滤 EXTERNAL_READONLY | 完整 |
| decreaseCapacity/increaseCapacity 是 no-op | 3.2 节代码示例 | PhysicalServerCapacityUpdaterImpl 分支 | 完整 |
| recalculateCapacity 调用 RoleProvider 刷新 | 3.2 节代码示例 | refreshExternalCapacity 委托 getCapacityConsumption | 完整 |
| 超分比固定 1.0 | 1.3 节明确声明 | setCpuRatio 对 CONTAINER_HOST 是 no-op | 完整 |
| PhysicalServerCapacityVO.available 由同步周期驱动 | 3.3 节 buildCapacityFromK8sNode | sync-node + sync-pod 两阶段更新 | **见 BLOCK-3** |

### 1.2 BLOCK-3: 混部场景下 available 字段的二义性

文档第 4.3 节提出"方案 A: PhysicalServerCapacityVO.availableCpu/availableMemory 取 ZStack 可管控角色（KVM）的数据"。但存在以下问题：

**问题 1：纯 Container 节点无 KVM 角色。** 如果一台物理机只有 CONTAINER_HOST 角色（没有 KVM），方案 A 的 available 取谁的值？文档未定义此场景。

**问题 2：sync-node Flow 中 `buildCapacityFromK8sNode` 直接写入 PhysicalServerCapacityVO.availableCpu = allocatable。** 这与方案 A（available 取 KVM 角色）矛盾——Container 同步钩子在写 available，但方案 A 说 available 应该取 KVM 的值。

**问题 3：refreshContainerCapacityAfterPodSync 直接覆盖 capVO.setAvailableCpu。** 如果同一台物理机有 KVM 角色，KVM 也在写 available（通过 HostCapacityUpdater 包装器），两个写入者会互相覆盖。

**修复建议**：

方案 A 在混部场景下不可行。应改为以下策略：
- **纯 Container 节点**：available = K8s allocatable - Pod requests（即当前 sync-pod 写入的值）。
- **混部节点**：available 由 KVM 侧管控（因为 KVM 是 INTERNAL_SHARED，分配引擎依赖此值）。Container 侧的容量数据**不写入 PhysicalServerCapacityVO.available**，仅通过 `getCapacityConsumption()` 返回值在 QueryPhysicalServer 的角色维度展示。
- 判断依据：`PhysicalServerRoleVO` 表中是否存在同一 serverUuid 的 INTERNAL_SHARED/INTERNAL_EXCLUSIVE 角色。存在则 Container 同步跳过 available 写入；不存在则 Container 写入。

### 1.3 EXTERNAL_READONLY 与 CompatibilityBridge 的交互

文档 5.3 节简述"无需额外处理，过滤在 Flow 链中自然发生"。验证路径：

```
AllocateHostMsg → CompatibilityBridge.shouldIntercept()
  → 阶段1: ServerAllocatorChain
    → SchedulingModeFilterFlow: 过滤 EXTERNAL_READONLY
  → 阶段2: HostAllocatorChain (仅 KVM 候选)
```

**结论正确**。EXTERNAL_READONLY 的 PhysicalServerVO 在阶段1即被过滤，不会进入阶段2。Container NativeHostVO 也不会被 HostAllocatorChain 选中（因为 DummyNativeHost 不参与 Host 分配流程）。两层过滤保证了安全。

但需要注意一个边界情况：如果 CompatibilityBridge **未启用**（阶段1全部灰度期），现有 HostAllocatorChain 是否会选中 NativeHostVO？答案是不会——现有 HostAllocatorChain 通过 `hypervisorType` 过滤，NativeHostVO 的 hypervisorType = "Native" 不匹配 KVM 场景。但这是隐式依赖，建议在文档中明确说明。

---

## 2. NativeHostVO 继承 HostVO 的意外触发风险

### 2.1 继承链分析

```
ResourceVO
  └── HostAO (@MappedSuperclass)
        └── HostVO (@Entity)
              ├── KVMHostVO (@Entity, 继承)
              └── NativeHostVO (@Entity, 继承) ← Container
```

NativeHostVO 继承 HostVO 意味着：
1. 所有 `HostVO.class` 的 JPA 查询（`Q.New(HostVO.class)`）会包含 NativeHostVO 记录
2. HostVO 上的 `@OneToOne HostCapacityVO capacity` 关联会尝试 JOIN HostCapacityVO
3. 所有 `HostMessage` 类型的消息可以路由到 NativeHost

### 2.2 HostCapacityVO JOIN 风险

HostVO 定义：
```java
@OneToOne(fetch = FetchType.EAGER)
@JoinColumn(name = "uuid")
private HostCapacityVO capacity;
```

当查询 `SELECT * FROM HostVO WHERE uuid = <nativeHostUuid>` 时，Hibernate 会 EAGER JOIN HostCapacityVO。但 NativeHostVO 没有对应的 HostCapacityVO 记录（Container 不通过 HostCapacityVO 管理容量）。

**风险评估**：`@OneToOne` + `FetchType.EAGER` 在目标记录不存在时，Hibernate 会设置 `capacity = null`。这不会抛异常。但当 HostCapacityVO 降级为 VIEW（骨架文档 4.6 节）后，VIEW 的 JOIN 条件是 `r.roleType = 'KVM_HOST'`。NativeHostVO 没有 KVM_HOST 角色，VIEW 查询返回空——仍然是 null，行为一致。

**结论**：无风险。文档 5.4 节的分析正确。

### 2.3 HostMessage 路由风险

文档 5.1 节分析了 ChangeHostState、ReconnectHost、PingHost、DeleteHost、MaintainHost 五种操作。但遗漏了以下操作：

| 遗漏的操作 | 是否影响 PhysicalServerVO | 风险等级 |
|-----------|--------------------------|---------|
| `RecalculateHostCapacityMsg` | NativeHost 无 HostCapacityVO，recalculate 空操作 | 无 |
| `CheckHostCapacityMsg` | 同上 | 无 |
| `APIUpdateHostMsg` | 修改 name/description/managementIp → 不影响 PhysicalServerVO（两者独立） | 无 |
| `APIGetHostTaskMsg` | 只读查询 | 无 |
| `APIGetHostWebSshUrlMsg` | DummyNativeHost 不支持 → 返回错误 | 无 |
| `APIPowerOnHostMsg` / `APIPowerResetHostMsg` / `APIShutdownHostMsg` | 电源管理消息发到 NativeHost → DummyNativeHost 处理 | **需确认** |

**电源管理消息**：`APIPowerOnHostMsg` 等是发往 HostVO 的 API 消息。如果用户对 NativeHostVO 调用 PowerOn，DummyNativeHost 如何处理？文档未提及。

**建议**：在 `ContainerPhysicalServerRoleProvider` 或 DummyNativeHost 中对电源管理消息返回 `SysErrors.OPERATION_NOT_SUPPORTED`。文档应显式说明此处理。

### 2.4 PostConnect 钩子确认

文档第 2.1 节和 OQ-3 正确指出 NativeHost 没有传统的 Connect/Reconnect 生命周期。代码验证：

- `NativeHostInventory` 仅多一个 `endpointUuid` 字段
- `HypervisorFactory.createHost()` 对 NativeHost 的实现（NativeFactory）抛 `UnsupportedOperationException`
- `HypervisorFactory.getHost()` 返回 `DummyNativeHost`，对所有消息直接回复空

因此 `HostAfterConnectedExtensionPoint.afterHostConnected()` **永远不会被调用**——因为 DummyNativeHost 不执行 Connect Flow，自然不会触发 PostConnect 回调。

**结论**：文档的设计决策（直接在 syncNodesFromCluster 中注入钩子）是正确且唯一可行的方案。

---

## 3. K8s 容量同步入口点审阅

### 3.1 同步链路完整性

文档 2.2 节定义的同步入口点：

```
ContainerEndpointBase.doSyncContainerManagementEndpoint()
  └── Flow 2: sync-node
        └── syncNodesFromCluster()
              └── syncPhysicalServer()
```

**问题**：Container 模块实际代码在 premium/ 中（本仓库不包含）。从 SDK 的 NativeHostInventory 可以确认 NativeHostVO 仅扩展 `endpointUuid` 字段。但无法从本仓库代码验证 `syncNodesFromCluster` 的实际实现。

**基于 SDK 和设计文档的推断**：
- `KubernetesNodeInventory` 当前没有 `systemUUID`/`machineID` 字段 → 需要新增
- `syncNodesFromCluster` 当前没有 PhysicalServer 同步逻辑 → 需要新增
- 这些修改都在 premium 模块内，不影响开源代码

### 3.2 BLOCK-1: getCapacityConsumption 的查询错误

文档 1.2 节代码：

```java
Tuple podUsage = Q.New(PodVO.class)
    .eq(PodVO_.hostUuid, hostUuid)
    .select(PodVO_.cpuNum, PodVO_.memorySize)
    .findTuple();
```

**错误**：`findTuple()` 返回**单条记录**的 Tuple，不是聚合结果。如果该 Host 上有 10 个 Pod，此查询只返回第一个 Pod 的 cpuNum/memorySize，不是 SUM。

**正确写法**应使用 SQL 聚合：

```java
Tuple podUsage = SQL.New(
    "select coalesce(sum(p.cpuNum), 0), coalesce(sum(p.memorySize), 0) " +
    "from PodVO p where p.hostUuid = :hostUuid", Tuple.class)
    .param("hostUuid", hostUuid)
    .find();
```

注意：文档 3.3 节的 `refreshContainerCapacityAfterPodSync` 中使用了正确的 `SQL.New` + `sum()` 写法，说明作者知道正确做法，但 1.2 节遗漏了。两处逻辑本质上做的是同一件事（聚合 Pod 资源），应该抽成共享方法。

### 3.3 BLOCK-2: 过期节点处理的双重触发

文档 2.8 节定义了过期节点处理逻辑：

```
Step 1: 查找过期的 NativeHostVO UUIDs
Step 2: 遍历 staleHostUuids → 标记 PhysicalServerRoleVO.roleStatus = Stale
Step 3: deleteClusterResourcesByUuids(null, staleHostUuids, null)  // 删除 NativeHost
```

同时文档 5.1 节定义了 `HostDeleteExtensionPoint.afterDeleteHost()` 钩子：

```java
@Override
public void afterDeleteHost(HostInventory host) {
    // 标记 RoleVO 为 Stale
    PhysicalServerRoleVO roleVO = Q.New(...)
        .eq(PhysicalServerRoleVO_.roleUuid, host.getUuid())
        .find();
    if (roleVO != null) {
        roleVO.setRoleStatus("Stale");
        dbf.update(roleVO);
    }
}
```

**问题**：Step 2 已经标记了 Stale，然后 Step 3 删除 NativeHost 触发 `afterDeleteHost()`，再次标记 Stale。这是**双重触发**：
1. 虽然第二次标记是幂等的（重复 set "Stale" 不会出错），但产生了不必要的数据库写入。
2. 更严重的是：**如果 `deleteClusterResourcesByUuids` 内部不走 HostVO 的标准删除流程（不触发 `HostDeleteExtensionPoint`），则 `afterDeleteHost` 永远不会被调用**，此时 Step 2 是唯一的 Stale 标记路径——但这需要确认 premium 代码的实际行为。

**修复建议**：
- 选项 A（推荐）：移除 2.8 节的手动 Stale 标记（Step 2），完全依赖 `HostDeleteExtensionPoint.afterDeleteHost()` 钩子。前提是确认 `deleteClusterResourcesByUuids` 走 HostVO 删除路径并触发 ExtensionPoint。
- 选项 B：保留 2.8 节的手动标记，移除 `HostDeleteExtensionPoint` 实现中的重复逻辑（在 afterDeleteHost 中只处理 API 删除 Endpoint 的级联场景）。
- 无论选哪个方案，都应在文档中明确区分两种删除路径（同步清理 vs API 级联删除），并标注每条路径的 Stale 标记责任方。

### 3.4 IMP-3: Pod 容量聚合的 N+1 问题

文档 3.3 节的 `refreshContainerCapacityAfterPodSync` 遍历所有 Host，对每个 Host 执行一次 SQL 聚合查询：

```java
for (NativeHostInventory host : hosts) {
    // 查 RoleVO → 查 CapacityVO → SQL sum PodVO → 更新 CapacityVO
}
```

如果一个 K8s 集群有 100 个 Node，这将产生 400+ 次数据库查询（每个 Node 4 次：查 RoleVO、查 CapacityVO、聚合 Pod、更新 CapacityVO）。

**建议**：改为批量操作：
1. 一次查询获取所有 Host 的 RoleVO 映射：`Map<hostUuid, serverUuid>`
2. 一次 GROUP BY 聚合所有 Host 的 Pod 资源：`SELECT hostUuid, sum(cpuNum), sum(memorySize) FROM PodVO GROUP BY hostUuid`
3. 批量更新 PhysicalServerCapacityVO

---

## 4. serialNumber 匹配逻辑审阅

### 4.1 systemUUID 作为 serialNumber 的可靠性

文档选择 K8s Node `status.nodeInfo.systemUUID` 作为 serialNumber，这是正确的首选。但需要补充以下边界情况：

| 场景 | systemUUID 可靠性 | 影响 |
|------|------------------|------|
| 物理服务器（标准服务器厂商） | 高，来自 SMBIOS | 与 KVM 的 product_serial 同源，可匹配 |
| 虚拟机上的 K8s Node（嵌套虚拟化） | 低，VM 的 systemUUID 由 hypervisor 生成 | 每次重建 VM 可能变化；与宿主机的 serial 无关 |
| ARM 服务器 | 中，部分 ARM SoC 不提供 SMBIOS | machineID 降级有效 |
| 容器内 K8s Node（KinD/k3d） | 无意义，容器没有独立 SMBIOS | 开发测试场景，生产不会出现 |

**建议**：文档 2.4 节应补充"虚拟化嵌套场景（K8s 部署在 VM 中）不保证 systemUUID 与宿主机 serialNumber 匹配"的警告。这意味着在 VM 上跑 K8s 的场景下，混部匹配只能依赖 managementIp 或管理员手动关联。

### 4.2 IMP-1: normalizeSerialNumber 应抽到公共层

文档 2.4 节定义了 `normalizeSerialNumber()`（去连字符、转大写）。KVM 适配器文档也有类似的 normalize 逻辑。BM1/BM2 通过 IPMI 获取的 serial 也需要 normalize。

**建议**：将 `normalizeSerialNumber()` 和 `isValidSerialNumber()` 抽到 `PhysicalServerManagerImpl` 或 `RoleMatchContext` 的静态方法中，确保四个适配器使用完全一致的规范化逻辑。否则一个适配器 normalize 了、另一个没有，会导致同一台物理机的 serialNumber 格式不一致而匹配失败。

---

## 5. K8s Node 状态映射审阅

### 5.1 IMP-2: Node 状态的精细处理

文档 2.5 节定义了 PhysicalServerVO.status 映射：`Ready=True → Connected; 否则 → Disconnected`。但 K8s Node 有更丰富的状态：

| K8s Node Condition | 当前映射 | 建议映射 | 原因 |
|-------------------|---------|---------|------|
| Ready=True | Connected | Connected | 正确 |
| Ready=False | Disconnected | Disconnected | 正确 |
| Ready=Unknown | Disconnected | Disconnected | 正确（心跳丢失） |
| Unschedulable=True (cordoned) | 未处理 | **应映射到 PhysicalServerState.Disabled** | Node 被 cordon 意味着管理员不希望调度新 Pod，语义等同于 Disabled |
| MemoryPressure/DiskPressure | 未处理 | 可设 capacityState = Stale | 触发容量刷新 |

**建议**：在 `syncPhysicalServer` 中增加对 `node.spec.unschedulable` 的检查。如果 K8s Node 被 cordon（`unschedulable=true`），将对应 PhysicalServerRoleVO.roleStatus 设为 "Disabled" 或新增 "Cordoned" 状态。否则管理员在 K8s 侧 cordon 了节点，ZStack 侧仍显示为 Active，造成信息不一致。

---

## 6. 与骨架文档的一致性对照

### 6.1 SPI 方法逐项对照

| SPI 方法 | 骨架 3.1 定义 | Container 适配器实现 | 一致？ |
|---------|-------------|-------------------|--------|
| getRoleType() | 返回 ServerRoleType 常量 | CONTAINER_HOST | 一致 |
| getSchedulingMode() | 返回 SchedulingMode 常量 | EXTERNAL_READONLY | 一致 |
| getCapacityConsumption(serverUuid) | 无副作用只读方法 | 从 PodVO 聚合 | **BLOCK-1: 查询错误** |
| onPhysicalServerCreated(serverUuid) | 在事务内调用 | no-op（日志） | 一致 |
| onPhysicalServerDeleted(serverUuid) | 在事务内调用 | no-op（日志） | 一致（但与 KVM/BM1 的 Stale 标记行为不一致，见 INC-1） |
| getInventory(roleUuid) | 返回 RoleInventory 子类 | ContainerRoleInventory | 一致 |
| matchExistingServer(context) | 返回 serverUuid 或 null | 自实现 2 级匹配 | 一致 |

### 6.2 骨架 9.4 节 Open Questions 回答质量

| OQ | 骨架提问 | 适配器回答 | 评价 |
|----|---------|-----------|------|
| OQ-1 | managementIp 是否相同？ | 不一定，依赖 serialNumber 或手动关联 | 充分 |
| OQ-2 | Allocatable vs Capacity？ | 两者都用，用途不同 | 充分，映射表清晰 |
| OQ-3 | PostConnect 钩子是否存在？ | 不存在，直接在 syncNodesFromCluster 注入 | 充分，方案可行 |

骨架 9.4 节遗漏的问题（适配器应主动回答但未覆盖）：
- **OQ-未提**: Container Endpoint 的认证方式（kubeConfig）变更时，PhysicalServerVO 如何处理？目前设计中 PhysicalServerVO 不持有 kubeConfig，但 NativeCluster 的 kubeConfig 过期会导致同步失败，PhysicalServerRoleVO 应如何反映？

### 6.3 IMP-5: capacityState 硬编码字符串

文档 3.3 节 `cap.setCapacityState("Ready")` 使用字符串常量。骨架文档 `PhysicalServerCapacityVO` 的注释列出了 `CapacityState` 枚举（Initialized, Ready, Allocated, Recalculating, Stale），但最终审查报告 INC-4 指出此枚举尚未正式定义。

**建议**：等骨架修复 INC-4（将 capacityState 改为枚举）后，Container 适配器同步改为枚举引用。当前文档中标注"待骨架修复后同步"即可。

### 6.4 IMP-4: ContainerRoleInventory 的 endpointUuid

`ContainerRoleInventory` 新增了 `endpointUuid` 字段，这是 Container 特有的概念（K8s Endpoint 管理入口的 UUID）。骨架的 `RoleInventory` 基类只有 roleUuid、roleType、clusterUuid、status 四个字段。

**endpointUuid 的语义**：NativeHostVO.endpointUuid 指向 ContainerManagementEndpointVO.uuid，表示该 NativeHost 是通过哪个 K8s 管理端点同步过来的。这在 Container 域内部有意义（定位同步来源），但在统一硬件管理层面，这个字段不应该出现在 PhysicalServerVO 的角色展示中。

**建议**：endpointUuid 保留在 ContainerRoleInventory 中供 Container 内部使用，但 QueryPhysicalServerMsg 的角色展开中是否要暴露此字段需要产品侧确认。如果暴露，应在骨架文档的 RoleInventory 注释中说明"各角色的子类可包含角色特有字段"。

---

## 7. 混部场景 (KVM + Container) 详细推演

### 7.1 正向流程推演：先 KVM 后 Container

```
T1: 管理员通过 APIAddKVMHostMsg 添加 KVM Host
    → KVMHostFactory.createHost() → KVMHostVO(uuid=H1, managementIp=10.0.0.1)
    → PostConnect 成功
    → KvmRoleProvider.matchExistingServer() → null
    → PhysicalServerManager.defaultMatch(serialNumber="ABC123") → null
    → 新建 PhysicalServerVO(uuid=PS1, serialNumber="ABC123", managementIp=10.0.0.1)
    → 新建 PhysicalServerRoleVO(serverUuid=PS1, roleType=KVM_HOST, roleUuid=H1)
    → 写入 PhysicalServerCapacityVO(uuid=PS1, ...)

T2: 管理员添加 K8s Endpoint，同步发现 Node（同一台物理机）
    → syncNodesFromCluster() → NativeHostVO(uuid=NH1, managementIp=10.0.0.1)
    → syncPhysicalServer():
      → extractSerialNumber(node) → systemUUID="ABC123" (normalize 后与 KVM 一致)
      → ContainerRoleProvider.matchExistingServer(serialNumber="ABC123") → PS1 ← 匹配成功
      → checkSchedulingModeExclusion(PS1, EXTERNAL_READONLY)
        → 已有 KVM_HOST(INTERNAL_SHARED) + 新 EXTERNAL_READONLY → 允许
      → 新建 PhysicalServerRoleVO(serverUuid=PS1, roleType=CONTAINER_HOST, roleUuid=NH1)
    → 容量更新：
      → BLOCK-3 问题：Container sync 尝试写 PhysicalServerCapacityVO.available
        → 应该跳过（KVM 在管控 available）
```

**结论**：流程整体可行，但需要解决 BLOCK-3 的 available 写入冲突。

### 7.2 反向流程推演：先 Container 后 KVM

```
T1: K8s Endpoint 同步发现 Node
    → NativeHostVO(uuid=NH1) → PhysicalServerVO(uuid=PS1) → RoleVO(CONTAINER_HOST)
    → PhysicalServerCapacityVO.available 写入 K8s 数据

T2: 管理员添加 KVM Host（同一台物理机）
    → KVMHostVO(uuid=H1) → PostConnect
    → matchExistingServer(serialNumber="ABC123") → 匹配到 PS1
    → checkSchedulingModeExclusion(PS1, INTERNAL_SHARED)
      → 已有 CONTAINER_HOST(EXTERNAL_READONLY) + 新 INTERNAL_SHARED → 允许
    → 新建 RoleVO(KVM_HOST)
    → HostCapacityUpdater 包装器写 PhysicalServerCapacityVO
      → 覆盖了 Container 之前写的 available 值 → 正确（KVM 接管容量管控）
```

**结论**：反向流程也可行。KVM 的 PostConnect 会正确覆盖 available 值。但这依赖 KVM PostConnect 一定在 Container sync 之后执行——如果两者并发（极端情况），需要通过 `PESSIMISTIC_WRITE` 锁保证。骨架文档已有此设计。

### 7.3 边界场景：KVM Host 先删除

```
T3: 管理员删除 KVM Host
    → HostDeleteExtensionPoint → KvmRoleProvider.afterDeleteHost()
      → RoleVO(KVM_HOST).roleStatus = Stale
    → PhysicalServerVO 仍存在（有 CONTAINER_HOST 角色）
    → PhysicalServerCapacityVO.available 谁来管？
      → 下次 Container sync 周期，refreshContainerCapacityAfterPodSync 写入 K8s 数据
      → 正确恢复到纯 Container 节点的 available 语义
```

**结论**：需要在 Container sync 中检查"是否存在其他 INTERNAL 角色"的逻辑。KVM 角色被标记为 Stale 后，Container sync 应重新接管 available 写入。

---

## 8. 修改影响范围评估

### 8.1 文件清单对照

文档附录 A 列出了需要修改的文件。对照评估：

| 文件 | 修改类型 | 风险等级 | 备注 |
|------|---------|---------|------|
| ContainerPhysicalServerRoleProvider.java | 新增 | 低 | 独立新类 |
| ContainerRoleInventory.java | 新增 | 低 | 独立新类 |
| ContainerEndpointBase.java | 修改 | **中** | 三处注入点（sync-node、sync-pod、过期清理） |
| KubernetesNodeInventory.java | 修改 | **中** | 新增 6 个字段，所有序列化/反序列化代码需兼容 |
| KubernetesNativeProvider.java | 修改 | 低 | 在 listNodes() 中补充字段提取 |

**遗漏的修改**：
1. `ContainerGlobalConfig.java` — 新增 `DEFAULT_SERVER_POOL_UUID` 配置项（文档 2.6 节提到但未列入附录 A）
2. `spring/container.xml` 或 `@Component` — 确保 ContainerPhysicalServerRoleProvider 被 Spring 扫描
3. 数据库迁移脚本 — KubernetesNodeInventory 新增字段如果涉及持久化表需要 Flyway migration

### 8.2 对现有 Container 功能的回归风险

| 现有功能 | 是否受影响 | 原因 |
|---------|-----------|------|
| 创建/删除 Endpoint | 否 | 不修改 Endpoint 相关代码 |
| 同步 K8s Cluster/Node/Pod | **低风险** | syncNodesFromCluster 中新增调用，但在已有 persist/update 之后追加，不影响现有逻辑 |
| Pod 调度（K8s 侧） | 否 | ZStack 不参与 |
| NativeHost 查询 | 否 | NativeHostVO 不修改 |
| Container 监控 | 否 | Prometheus 集成不受影响 |

---

## 9. 总结与行动项

### 阻塞项（必须在实现前解决）

| 编号 | 问题 | 建议修复 |
|------|------|---------|
| BLOCK-1 | 1.2 节 getCapacityConsumption 的 PodVO 查询使用 findTuple() 而非 SUM 聚合 | 改用 SQL.New + sum()，与 3.3 节保持一致；抽取共享方法 |
| BLOCK-2 | 2.8 节手动 Stale 标记与 5.1 节 HostDeleteExtensionPoint 双重触发 | 明确两条删除路径的责任方，消除重复（推荐选项 B） |
| BLOCK-3 | 混部场景 PhysicalServerCapacityVO.available 写入冲突 | 增加"是否存在 INTERNAL 角色"的检查，有则 Container 跳过 available 写入 |

### 改进建议

| 编号 | 建议 | 优先级 |
|------|------|-------|
| IMP-1 | normalizeSerialNumber / isValidSerialNumber 抽到公共层 | 高 |
| IMP-2 | 处理 K8s Node Unschedulable/MemoryPressure 状态 | 中 |
| IMP-3 | refreshContainerCapacityAfterPodSync 改为批量操作 | 中 |
| IMP-4 | 确认 ContainerRoleInventory.endpointUuid 是否需要在 QueryPhysicalServer 中暴露 | 低 |
| IMP-5 | capacityState 待骨架 INC-4 修复后同步改为枚举 | 低 |

### 文档补充建议

1. 补充"纯 Container 节点 vs 混部节点"的 available 字段写入策略决策表
2. 补充电源管理消息（APIPowerOnHostMsg 等）发往 NativeHost 的处理说明
3. 补充虚拟化嵌套场景下 systemUUID 不可靠的警告
4. 补充 CompatibilityBridge 未启用时 NativeHostVO 不会被 HostAllocatorChain 选中的安全保证说明
5. 附录 A 补充遗漏的修改文件（ContainerGlobalConfig、Spring 配置、Flyway migration）
