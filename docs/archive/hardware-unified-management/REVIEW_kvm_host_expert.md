# Phase 1 详细设计评审意见 -- KVM Host Expert

**评审人**: KVM Host Domain Expert
**评审文档**: PHASE1_Detailed_Design.md v1.1
**评审结论**: NEEDS_REVISION

---

## 一、整体评价

设计文档展现了良好的架构意识，遵循了 ZStack 现有的 VO/Inventory/API Message 三层模式。但从 KVM Host 子系统的角度来看，设计在以下关键维度存在不足：

1. 容量模型过度简化，丢失了 KVM 实际使用的多层超分机制
2. 分配流程设计忽略了 KVM 分配链中大量的存储/网络/安全约束
3. 角色绑定机制对 KVM 场景过于笨重
4. 缺少对 KVM 特有元数据（libvirt/qemu版本、CPU模型、NUMA拓扑）的考虑
5. 状态机缺少 PreMaintenance 过渡态

---

## 二、PhysicalServerVO 设计评审

### 2.1 优点
- PhysicalServerAO 作为 `@MappedSuperclass` 继承 `ResourceVO`，与 `HostAO` 模式一致
- 不使用 EO 层（物理删除）的决策合理
- `serialNumber` 和 `manufacturer`/`model` 的引入是对 HostAO 的有益补充

### 2.2 问题一：缺少 clusterUuid 关联 [高]
`HostAO` 在第25-26行明确定义了 `clusterUuid`，直接决定了 Host 与 PrimaryStorage 的挂载关系、L2Network 的网络可达性、Host 之间的迁移域。

**建议**: `PhysicalServerAO` 不应引入 `clusterUuid`（一台物理服务器可能同时承担多角色属于不同 cluster），但 `AllocateServerMsg` 和 `ServerAllocatorSpec` 必须支持通过 `PhysicalServerRoleVO.roleUuid` 关联到对应的 `HostVO.clusterUuid`。

### 2.3 问题二：OOB 信息与现有 HostIpmiVO 的冗余 [中]
现有 `HostIpmiVO` 管理 IPMI 信息，`PhysicalServerAO` 又定义了 `oobAddress` 等字段，同一组 IPMI 凭证将存在于两处。

**建议**: 明确 OOB 信息的唯一数据源，`PhysicalServerAO.oobAddress` 应作为主数据源。

---

## 三、ServerCapacityVO 与现有 HostCapacityVO 对比

### 3.1 问题三：容量模型严重缺失 [高]
`HostCapacityVO` 包含 `cpuNum`(逻辑CPU)、`cpuSockets`(插槽)、`cpuCoreNum`(核心) 三个字段。`ServerCapacityVO` 只有 `totalPhysicalCpu`。

更严重的是，ZStack 现有 CPU 超分使用 `HostCpuOverProvisioningManager`（per-host 级别），内存超分使用 `HostCapacityOverProvisioningManager`。**`ServerCapacityVO` 只有一个静态 double 字段，无法表达两套独立超分管理器。**

### 3.2 问题四：缺少 availablePhysicalMemory [中]
`HostCapacityVO` 有 `availablePhysicalMemory` 用于监控告警，`ServerCapacityVO` 缺少此字段。

**建议**: 增加 `availablePhysicalMemory`、`cpuNum`、`cpuSockets`、`cpuCoreNum` 字段。超分比机制需要与现有 Manager 接口对接。

---

## 四、AllocateServerMsg 分配流程对比

### 4.1 问题五：分配上下文严重不足 [高]
对比 `AllocateHostMsg` 的 22+ 字段，`AllocateServerMsg` 只保留了 9 个。缺失关键字段：

| 字段 | 影响 |
|------|------|
| `avoidHostUuids` / `softAvoidHostUuids` | 反亲和规则 |
| `l3NetworkUuids` | 网络可达性过滤 |
| `vmInstance` / `image` / `vmOperation` | VM 上下文信息 |
| `requiredPrimaryStorageUuids` | 存储亲和 |
| `diskOfferings` | 磁盘规格标签 |
| `architecture` | CPU 架构过滤 |

### 4.2 问题六：ServerAllocatorFlow 责任链过于简单 [高]
设计只有 5 个 Flow，但现有 HostAllocator 有 13 个 Flow，包括 L3 网络、主存储、备份存储、标签亲和、扩展点过滤器等。

**建议**: 增加 `ServerAllocatorFilterExtensionPoint` 接口，允许角色模块注入自定义过滤逻辑。或将统一分配推迟到 Phase 3。

---

## 五、PhysicalServerRoleVO 评审

### 5.1 问题七：唯一约束可能过严 [中]
嵌套虚拟化场景下同一服务器可能有多个 KVM 角色。

### 5.2 问题八：managementIp 匹配需加 zoneUuid [中]
多个 Zone 下的私有网络可能有相同 managementIp。

---

## 六、兼容层设计

### 问题九：兼容层放在 Phase 3 风险太大 [高]
Phase 1 应包含兼容层的接口定义和至少一个冒烟测试。

---

## 七、KVM 特有逻辑缺失

### 问题十：libvirt/qemu 版本信息 [中]
存储在 SystemTag 中，迁移场景强制检查版本一致性。**建议不在 PhysicalServer 层存储，保持在 HostVO 的 SystemTag 中。**

### 问题十一：NUMA 拓扑信息 [低]
`HostNumaNodeVO` 存储 NUMA 节点拓扑，可接受不在 PhysicalServer 层存储。

### 问题十二：SSH 凭证 [低]
KVMHostVO 的 SSH 凭证是角色特有的，合理不在 PhysicalServer 层。

---

## 八、状态机设计

### 问题十三：缺少 PreMaintenance [中]
现有 `HostState` 有 PreMaintenance 过渡态，用于 VM 迁移/停止疏散。

---

## 九、DB 迁移脚本

### 问题十四：DDL 表名 `PhysicalServerAO` 应为 `PhysicalServerVO` [低]
@MappedSuperclass 不映射独立表。

---

## 十、总结

### 必须修改（阻塞性）
1. ServerCapacityVO 补齐 cpuNum/cpuSockets/cpuCoreNum/availablePhysicalMemory
2. AllocateServerMsg 补齐存储/网络/反亲和约束字段，或推迟统一分配子系统
3. PhysicalServerState 增加 PreMaintenance

### 建议修改
4. managementIp 匹配增加 zoneUuid 联合条件
5. 明确 OOB 信息与 HostIpmiVO 数据源策略
6. DDL 表名修正
7. Phase 1 增加兼容层接口定义和冒烟测试
8. 明确 PhysicalServer vs HostVO/KVMHostVO 的信息边界
