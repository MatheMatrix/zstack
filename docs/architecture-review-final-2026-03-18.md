# 统一硬件管理架构 — 最终审查报告

**审查人**: 群总 (qun.li)
**日期**: 2026-03-18
**审查范围**: 6 份架构文档（骨架 + KVM/BM1/BM2/Container 适配 + 分配引擎）
**结论**: **有条件通过，可进入实现阶段**。需先解决 2 个阻塞项。

---

## 1. 一致性检查结果

### 1.1 SPI 接口一致性

逐方法对照骨架（第 3 章）与 4 个适配器文档中的 `PhysicalServerRoleProvider` 实现：

| SPI 方法 | 骨架定义 | KVM | BM1 | BM2 | Container | 一致？ |
|---------|---------|-----|-----|-----|-----------|--------|
| `getRoleType()` | 返回 `ServerRoleType` | KVM_HOST | BAREMETAL_V1 | BAREMETAL_V2 | CONTAINER_HOST | 一致 |
| `getSchedulingMode()` | 返回 `SchedulingMode` | INTERNAL_SHARED | INTERNAL_EXCLUSIVE | INTERNAL_EXCLUSIVE | EXTERNAL_READONLY | 一致 |
| `getCapacityConsumption(serverUuid)` | 返回 `CapacityUsage` | 从 HostCapacityVO 读取 | 从 ChassisStatus 判断 | 从 ChassisStatus 判断 | 从 PodVO 聚合 | 一致 |
| `onPhysicalServerCreated(serverUuid)` | 回调 | no-op | no-op | no-op | no-op | 一致 |
| `onPhysicalServerDeleted(serverUuid)` | 回调 | 标记 Stale | 标记 Stale | 日志 + FK CASCADE | no-op | **见 INC-1** |
| `getInventory(roleUuid)` | 返回 `RoleInventory` 子类 | KvmRoleInventory | Bm1RoleInventory | Bm2RoleInventory | ContainerRoleInventory | 一致 |
| `matchExistingServer(context)` | 返回 `String` serverUuid | 返回 null（用默认） | 自实现 3 级匹配 | 自实现 2 级匹配 | 自实现 2 级匹配 | **见 INC-2** |

**INC-1 [严重程度: 低]** — `onPhysicalServerDeleted` 行为不一致

- KVM 和 BM1 主动将 RoleVO 标记为 Stale。
- BM2 依赖 FK CASCADE 自动删除 RoleVO，不做主动标记。
- Container 是 no-op，也依赖 FK CASCADE。
- **影响**：如果 PhysicalServerVO 删除触发 `onPhysicalServerDeleted` 回调发生在 FK CASCADE 之前，BM2/Container 的回调虽然是空操作但无害。如果发生在 CASCADE 之后，KVM/BM1 的 Stale 标记会找不到记录，也无害（查不到直接 return）。
- **结论**：行为不一致但不会导致错误。建议统一为"先回调再 CASCADE"的顺序，在 `PhysicalServerManagerImpl.deletePhysicalServer()` 中保证调用顺序。不阻塞实现。

**INC-2 [严重程度: 中]** — `matchExistingServer` 返回语义不一致

- KVM 文档说"返回 null 表示使用默认匹配逻辑"（由 `PhysicalServerManagerImpl` 提供）。
- BM1/BM2/Container 各自实现了完整匹配逻辑，不依赖默认逻辑。
- **问题**：骨架文档中 `PhysicalServerManagerImpl` 的默认匹配逻辑未定义。如果 KVM 返回 null，谁来做 serialNumber + managementIp 匹配？
- **修复建议**：在骨架文档第 3 章补充 `PhysicalServerManagerImpl.defaultMatch(RoleMatchContext)` 的定义，明确 KVM 返回 null 时的降级行为。或者让 KVM 也自实现匹配逻辑（参考 Container 的实现，代码量不大）。

### 1.2 VO 字段一致性

| VO | 骨架定义字段数 | 各适配器引用 | 一致？ |
|----|-------------|------------|--------|
| PhysicalServerAO | 16 字段 | 4 个适配器均按骨架定义引用 | 一致 |
| PhysicalServerRoleVO | 9 字段 | 4 个适配器均引用 serverUuid/roleType/roleUuid/clusterUuid/schedulingMode/roleStatus | 一致 |
| PhysicalServerCapacityVO | 17 字段 | KVM 映射 15 字段、BM1/BM2 映射 12 字段、Container 映射 10 字段 | 一致（差异来自角色特性，非矛盾） |
| RoleMatchContext | 骨架定义 4 字段 | BM1 新增 oobAddress 字段 | **见 INC-3** |

**INC-3 [严重程度: 低]** — `RoleMatchContext` 字段扩展

- BM1 适配器文档要求新增 `oobAddress` 字段。骨架文档未包含此字段。
- BM2 适配器文档中，将 `ipmiAddress` 填入 `managementIp` 字段做降级匹配，与 BM1 的 `oobAddress` 字段方案不一致。
- **修复建议**：统一为 BM1 方案（新增 `oobAddress`），BM2 同步修改。向后兼容，不阻塞。

### 1.3 枚举值一致性

| 枚举 | 骨架定义值 | 各适配器使用 | 一致？ |
|------|----------|------------|--------|
| ServerRoleType | KVM_HOST, BAREMETAL_V1, BAREMETAL_V2, CONTAINER_HOST | 完全一致 | 一致 |
| SchedulingMode | INTERNAL_SHARED, INTERNAL_EXCLUSIVE, EXTERNAL_READONLY | 完全一致 | 一致 |
| PhysicalServerState | Enabled, Disabled, Maintenance | KVM/BM1/BM2/Container 只用 Enabled/Disabled | 一致（Maintenance 预留） |
| PhysicalServerStatus | Connecting, Connected, Disconnected | 完全一致 | 一致 |
| PhysicalServerPowerStatus | PowerOn, PowerOff, Unknown | 完全一致 | 一致 |
| ProvisionNetworkType | STANDALONE_PXE, GATEWAY_PXE | BM1 用 STANDALONE_PXE，BM2 用 GATEWAY_PXE | 一致 |
| capacityState（字符串） | 骨架未明确枚举化 | KVM 用 "Ready"；BM1 用 "Initialized"/"Ready"；BM2 用 "Ready"/"Allocated"；Container 用 "Ready" | **见 INC-4** |

**INC-4 [严重程度: 中]** — `capacityState` 未枚举化，各模块使用的值不一致

- BM1 独创 "Initialized" 状态（硬件发现前）。
- BM2 独创 "Allocated" 状态（独占分配后）。
- 骨架文档的 CapacityVO 注释提到 "Initialized / Recalculating / Ready" 三个值，但 "Allocated" 和 "Stale" 未列入。
- **修复建议**：将 capacityState 改为枚举类型 `CapacityState`，统一定义 `Initialized`, `Ready`, `Allocated`, `Recalculating`, `Stale` 五个值。在骨架文档中补充定义。

### 1.4 概念矛盾检查

| 概念 | 文档 A 描述 | 文档 B 描述 | 是否矛盾 |
|------|-----------|-----------|---------|
| Stale 角色何时清理 | KVM 适配器：定时任务清理 | 骨架：未提及清理机制 | **需补充** |
| PhysicalServerVO 删除是否级联 RoleVO | 骨架：FK CASCADE 自动删除 | BM1：onPhysicalServerDeleted 先标 Stale | 不矛盾（先标记再级联） |
| Container 容量取谁的值（混部场景） | Container 适配器：方案 A（取 KVM 角色的 available） | 分配引擎：CapacityFilterFlow 无混部特殊逻辑 | **需补充**（见挑战 C-3） |
| 独占互斥检查时机 | BM1 适配器：afterCreateBaremetalChassis 中检查 | 骨架/分配引擎：未提及互斥检查 | **需补充**（见挑战 C-4） |

---

## 2. 架构挑战

### C-1: 三层不四层 — PhysicalServerVO 独立于 HostVO 继承链

**裁决: 合理**

好处：
- 不改 HostVO/HostAO 的任何字段和注解，零 Hibernate 影响
- 不引入多继承复杂性
- 删除 PhysicalServer* 表即可完整回滚

坏处：
- PhysicalServerVO 和 HostVO 之间无直接 ORM 关联，需通过 RoleVO.roleUuid 间接查询
- 两套容量表（HostCapacityVO + PhysicalServerCapacityVO）需保持同步

**结论**：好处远大于坏处。两套容量表的同步通过"异步事件 + 定时对账"双保险机制解决，数据延迟在秒级，可接受。

### C-2: 单向同步，不做双写

**裁决: 合理**

各适配器文档均遵循"角色实体 → PhysicalServerVO 单向同步"原则：
- KVM：HostCapacityVO → PhysicalServerCapacityVO（异步事件驱动）
- BM1/BM2：ChassisVO 生命周期钩子 → PhysicalServerVO/RoleVO
- Container：K8s Node 同步 → PhysicalServerVO/RoleVO

没有任何文档出现"从 PhysicalServerVO 反写回角色实体"的设计，一致性良好。

### C-3: 混部场景下 PhysicalServerCapacityVO 的 availableCpu/Memory 取值

**裁决: 需修改**

Container 适配器推荐方案 A（取 INTERNAL_SHARED 角色的 available 值），但分配引擎文档的 `recalculateCapacity()` 实现中只是简单累加所有非 EXTERNAL_READONLY 角色的 `getCapacityConsumption()`，没有"取哪个角色的 available"的逻辑。

**问题**：如果一台物理机同时有 KVM（INTERNAL_SHARED）和 BM1（INTERNAL_EXCLUSIVE）两个角色（虽然互斥检查应阻止这种情况），`recalculateCapacity()` 会累加两者的消耗。这在正常情况下不会发生（互斥检查保证），但如果互斥检查有 bug，容量数据会错误。

**修复建议**：
1. 在 `recalculateCapacity()` 中增加断言：一台物理服务器最多只有一个 INTERNAL_* 角色处于 Active 状态（EXTERNAL_READONLY 除外）
2. 明确文档说明混部只有 INTERNAL_SHARED + EXTERNAL_READONLY 的组合，INTERNAL_EXCLUSIVE 不参与混部

### C-4: 独占角色互斥检查的位置

**裁决: 需补充**

BM1 适配器在 `afterCreateBaremetalChassis()` 中实现了互斥检查，但这个检查是分散的、各适配器自行实现的。如果新增第五种角色，可能遗忘互斥检查。

**修复建议**：将互斥检查收归到 `PhysicalServerManagerImpl.registerRole()` 方法中统一实现，规则为：
- INTERNAL_EXCLUSIVE 不能与 INTERNAL_SHARED 或另一个 INTERNAL_EXCLUSIVE 共存
- EXTERNAL_READONLY 可以与任何模式共存
- 各适配器不再自行做互斥检查

### C-5: CompatibilityBridge 的 Shadow Mode

**裁决: 合理**

KVM 适配器第 4.3 节推荐 Shadow Mode（灰度期间新旧引擎同时执行、对比结果、以旧引擎为准）。分配引擎文档第 4 章的特性开关设计支持此模式。

但分配引擎文档中没有 Shadow Mode 的具体实现代码。需在实现阶段补充。

### C-6: CapacityFilterFlow 对 INTERNAL_EXCLUSIVE 的特殊处理

**裁决: 合理**

BM1 适配器第 4.1 节提出：INTERNAL_EXCLUSIVE 模式下跳过 CPU/Memory/Disk 容量检查，仅检查 capacityState != "Allocated"。分配引擎的 CapacityFilterFlow 代码中没有体现这个逻辑。

**修复建议**：在分配引擎文档的 CapacityFilterFlow（第 1.4.9 节）中补充 INTERNAL_EXCLUSIVE 分支：

```java
if (spec.getSchedulingMode() == SchedulingMode.INTERNAL_EXCLUSIVE) {
    // 独占模式：不检查 CPU/Memory 容量，只检查是否已被分配
    ret = candidates.stream()
        .filter(ps -> {
            PhysicalServerCapacityVO cap = ps.getCapacity();
            return cap == null || !"Allocated".equals(cap.getCapacityState());
        })
        .collect(Collectors.toList());
} else {
    // 共享模式：正常容量检查
    // ... 现有逻辑
}
```

### C-7: ServerPool 不下放 L2 语义

**裁决: 合理**

骨架文档 ServerPoolVO 定义清晰：运维标签，不承载 L2 网络。各适配器文档无任何地方将 L2 网络与 ServerPool 关联。

### C-8: ProvisionNetwork 所有角色共用

**裁决: 合理**

BM1 适配器将 BaremetalPxeServerVO 同步到 PhysicalServerProvisionNetworkVO（type=STANDALONE_PXE），BM2 将 BareMetal2ProvisionNetworkVO 同步到统一模型（type=GATEWAY_PXE）。Container 不涉及装机网络。统一查询 API 可看到所有角色的装机网络。

---

## 3. 边界条件分析

### 3.1 场景矩阵

| 场景 | 触发条件 | 预期行为 | 文档覆盖 | 风险 |
|------|---------|---------|---------|------|
| **升级：老版本 → 新版本** | 执行迁移 SQL | BM1/BM2/KVM 存量数据生成 PhysicalServerVO + RoleVO + CapacityVO | BM1 第 5.2 节、BM2 第 5.2 节 | 低 — 幂等 INSERT IGNORE + 确定性 UUID |
| **升级后 serialNumber 为空** | 老 KVM Host 无 IPMI、未采集 SN | PhysicalServerVO.serialNumber = null，下次 PostConnect 回填 | KVM 第 2.4 节 | 低 — managementIp 降级匹配兜底 |
| **升级后 DHCP 范围缺失（BM1）** | BM1 PxeServer 不存储 DHCP 范围 | ProvisionNetworkVO 的 DHCP 字段留空，管理员手动补充 | BM1 第 3.1 节 | 低 — 不影响 BM1 功能 |
| **回滚：新版本 → 老版本** | 删除 PhysicalServer* 表 | BM1/BM2/KVM/Container 所有现有功能不受影响 | BM2 第 5.3 节 | 低 — 无 FK 从旧表指向新表 |
| **并发：多管理节点同时 PostConnect 同一 KVM Host** | HA 场景 | matchPhysicalServer 可能并发创建两条 PhysicalServerVO | KVM 第 2.5 节 | **中** — UNIQUE(zoneUuid, serialNumber) 约束防重，第二次 INSERT 抛异常被 try-catch 捕获，降级到已有记录 |
| **并发：VM 创建 + 容量重计算** | 正常业务 | HostCapacityUpdater 和 PhysicalServerCapacityUpdater 在独立事务中 | KVM 第 3.4 节 | 低 — 不嵌套锁 |
| **并发：BM1 Chassis 创建 + KVM PostConnect（同一物理机）** | 管理员先注册 Chassis 再加 KVM Host | 两者通过 serialNumber/oobAddress 匹配到同一 PhysicalServerVO | BM1 第 1.8 节 | **中** — 需互斥检查阻止 INTERNAL_EXCLUSIVE + INTERNAL_SHARED 共存 |
| **混部：KVM + Container 容量** | 同一物理机两个角色 | PhysicalServerCapacityVO.available 取 KVM 角色值，Container 容量在 Inventory 角色展开中单独展示 | Container 第 4.3 节 | 低 — 但需 recalculateCapacity 实现与方案 A 对齐 |
| **故障：PhysicalServerRoleVO 创建失败** | DB 异常、UNIQUE 冲突 | KVM：try-catch + trigger.next()，不影响 Host 连接 | KVM 第 2.6 节 | 低 — 明确的异常隔离策略 |
| **故障：PhysicalServerRoleVO 创建失败** | BM1 Chassis 创建 | ExtensionPoint 回调在事务内，失败回滚整个 Chassis 创建 | BM1 第 2.1 节 | **中** — 与 KVM "不阻塞"策略不一致 |
| **故障：Container 同步中断** | K8s API 不可达 | PhysicalServerVO 不更新，保持上次同步状态 | Container 第 2.8 节 | 低 — 下次同步自动恢复 |
| **故障：迁移 SQL 部分执行** | DB 连接中断 | INSERT IGNORE 幂等，重新执行即可 | BM1/BM2 迁移脚本 | 低 |

### 3.2 BM1 钩子事务策略不一致（重要发现）

**INC-5 [严重程度: 高]** — BM1 和 KVM 的故障隔离策略矛盾

- KVM 适配器：PostConnect 钩子内部 try-catch 所有异常 + trigger.next()，PhysicalServer 注册失败不影响 KVM Host 连接。**设计正确**。
- BM1 适配器：`afterCreateBaremetalChassis()` 在事务内调用，失败会回滚整个 Chassis 创建。**这意味着 PhysicalServer 层的 bug 会导致 BM1 Chassis 创建失败**。

这违背了"统一硬件管理模块的任何故障都不应影响现有功能"的核心原则。

**修复建议**：BM1 的 `afterCreateBaremetalChassis()` 实现必须内部 try-catch 所有异常，不向上传播。如果在事务内无法 catch（因为事务 rollback-only），则改为在 Chassis 创建事务提交后异步触发 PhysicalServer 同步（通过 `@Async` 或 `EventFacade`）。

---

## 4. TOP 5 风险

| 排名 | 风险 | 严重程度 | 可能性 | 影响 | 缓解措施 |
|------|------|---------|--------|------|---------|
| **1** | BM1 钩子事务回滚导致 Chassis 创建失败（INC-5） | 高 | 中 | PhysicalServer 层 bug 破坏 BM1 核心功能 | **阻塞项**：BM1 同步改为事务外异步执行 |
| **2** | `recalculateCapacity()` 缺少 INTERNAL_EXCLUSIVE 特殊处理和互斥断言（C-3/C-4/C-6） | 中 | 中 | 独占角色容量计算错误；互斥检查遗漏导致数据不一致 | **阻塞项**：补充分配引擎文档中 INTERNAL_EXCLUSIVE 分支和互斥检查统一实现 |
| **3** | capacityState 未枚举化（INC-4） | 中 | 高 | 不同适配器使用不同字符串值，后续维护困难 | 实现前统一为枚举类型 |
| **4** | KVM matchExistingServer 返回 null 的默认逻辑未定义（INC-2） | 中 | 中 | KVM Host PostConnect 时无法正确匹配已有 PhysicalServerVO | 骨架文档补充 defaultMatch 定义 |
| **5** | CompatibilityBridge Shadow Mode 缺少具体实现设计 | 低 | 低 | 灰度切换新引擎时无法对比验证 | 实现阶段补充，不阻塞架构设计 |

---

## 5. 之前审查决策的遵守情况

| 决策 | 6 份文档遵守情况 | 判定 |
|------|----------------|------|
| 三层不四层 | 骨架第 1 章明确三层架构，各适配器均按三层实现 | 遵守 |
| 不做双写，单向同步 | 全部 4 个适配器均为角色实体 → PhysicalServerVO 单向同步 | 遵守 |
| L2 不下放 ServerPool | ServerPoolVO 无 L2 相关字段，分配引擎 PoolFilterFlow 仅做 UUID 匹配 | 遵守 |
| ProvisionNetwork 所有角色共用 | BM1 → STANDALONE_PXE，BM2 → GATEWAY_PXE，统一 ProvisionNetworkVO | 遵守 |
| VO 命名 PhysicalServer* 前缀 | PhysicalServerVO/PhysicalServerRoleVO/PhysicalServerCapacityVO 等 | 遵守 |
| serialNumber 主匹配 | 全部 4 个适配器均以 serialNumber 为第一优先匹配键 | 遵守 |
| CompatibilityBridge 薄代理 | 分配引擎第 4 章：只提取 6 个通用字段 + originalMessage 透传 | 遵守 |
| 一步到位 | 未出现"分期实现"的妥协设计 | 遵守 |
| Wrap, don't delete | 不改任何现有 VO/API，通过 ExtensionPoint 注入 | 遵守 |

---

## 6. 总体评价

### 6.1 做得好的地方

1. **异常隔离策略（KVM）**：KVM 适配器的 PostConnect 钩子 try-catch + trigger.next() 设计是教科书级的"增值功能不阻塞核心路径"模式。其他适配器应统一采用此策略。

2. **容量同步的双保险**：异步事件驱动 + 定时对账。即使事件丢失，5 分钟内自动修正。对于"派生数据"的定位是正确的。

3. **迁移脚本的幂等设计**：INSERT IGNORE + MD5 确定性 UUID，可安全重复执行。回滚仅需删表。

4. **分配引擎的 10 个 Flow 设计**：比现有 16 个 Flow 精简，去掉了 VM 层面的存储/镜像/配额 Flow，只保留物理服务器维度的通用过滤。ExtensionFilterFlow 通过 originalMessage 桥接 KVM 专有逻辑，避免了在统一引擎中硬编码角色特定逻辑。

5. **锁隔离**：PhysicalServerCapacityVO 的同步在独立事务中执行，不嵌套 HostCapacityUpdater 事务。@DeadlockAutoRestart + @Transactional 分离模式对齐现有代码。

### 6.2 阻塞项（必须在实现前解决）

**阻塞项 1**：BM1 `afterCreateBaremetalChassis()` 的事务策略必须改为异步执行或内部 try-catch，不能让 PhysicalServer 层故障回滚 BM1 Chassis 创建事务。BM2 的状态变化钩子同理审查。

**阻塞项 2**：分配引擎文档需补充：
- CapacityFilterFlow 中 INTERNAL_EXCLUSIVE 的特殊处理分支
- `PhysicalServerManagerImpl.registerRole()` 中的互斥检查统一实现
- capacityState 枚举化定义

### 6.3 非阻塞改进项（实现过程中完善即可）

1. 骨架文档补充 `PhysicalServerManagerImpl.defaultMatch()` 定义
2. RoleMatchContext 统一增加 oobAddress 字段
3. Stale 角色的定时清理机制（骨架文档补充）
4. Shadow Mode 具体实现（分配引擎文档补充）
5. BM1 DHCP 范围补充的管理员 API 设计

### 6.4 最终结论

> **架构设计整体扎实，核心决策（三层架构、单向同步、SPI 驱动、CompatibilityBridge 薄代理）正确且一致。解决上述 2 个阻塞项后，可以进入实现阶段。**
>
> 实现优先级建议：KVM 适配器先行（最成熟、风险最低），验证 PostConnect 钩子和容量同步机制后，再并行推进 BM1/BM2/Container 适配器。分配引擎在 KVM CompatibilityBridge Shadow Mode 验证通过后再切换生产路径。
