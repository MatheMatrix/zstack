# Phase 1 详细设计第二轮评审 -- Compute Resource Allocator Expert

**评审人**: Compute Allocation Domain Expert
**评审文档**: PHASE1_Detailed_Design.md v2.0
**参照文档**: ARCHITECT_DECISION.md v2.0, REVIEW_compute_allocator.md (第一轮)
**评审结论**: APPROVED

---

## 第二轮评审结果

本轮评审逐一验证第一轮提出的 2 个必须修复 + 4 个强烈建议/建议项是否已正确解决。

---

### 已解决的问题

#### [必须修复 1] @Transactional + @DeadlockAutoRestart 拆分两层

**原始问题**: v1.1 设计中 `ServerCapacityUpdater` 将 `@Transactional` 和 `@DeadlockAutoRestart` 放在同一方法上，会触发 `DbDeadlockAspect.aj` 第19行的 `declare error`，导致编译失败。

**验证结果**: v2.0 第 1569-1679 行 `ServerCapacityUpdater` 严格遵循 `HostCapacityUpdater.java` 的两层模式:
- 内层 `_run(ServerCapacityUpdaterRunnable)` 标注 `@Transactional` (第1657行)
- 外层 `run(ServerCapacityUpdaterRunnable)` 标注 `@DeadlockAutoRestart` (第1675行)
- 两注解分别在不同方法上，与 `HostCapacityUpdater.java:100-119` 完全一致

同时引入了 `ServerCapacityUpdaterRunnable` 回调接口 (第 2770-2778 行)，替代硬编码扣减，允许调用方在悲观锁内定制逻辑。第 1686-1723 行展示了三种调度模式 (INTERNAL_SHARED / INTERNAL_EXCLUSIVE / EXTERNAL_READONLY) 下的典型使用方式，覆盖了共享扣减、独占清零、外部同步三种场景。

--> **已验证通过**

---

#### [必须修复 2] DDL 表名 PhysicalServerAO -> PhysicalServerVO

**原始问题**: v1.1 的 DDL 使用 `PhysicalServerAO` 作为表名，但 `@MappedSuperclass` 不生成独立表，实际表应以 `@Entity` 所在的 `PhysicalServerVO` 命名。

**验证结果**: v2.0 多处修正:
- `PhysicalServerVO.java` (第402行): `@Table(name = "PhysicalServerVO")` 明确表名，并标注注释 "修正 v1.1 的 PhysicalServerAO 错误"
- DDL (第3260-3261行): 注释明确 "表名改为 PhysicalServerVO (不是 PhysicalServerAO)"
- DDL (第3265行): `CREATE TABLE ... PhysicalServerVO`
- `PhysicalServerRoleVO` FK (第3315-3316行): `REFERENCES PhysicalServerVO(uuid)`
- `ServerHardwareInfoVO` FK (第3341-3342行): `REFERENCES PhysicalServerVO(uuid)`
- `ServerHardwareDetailVO` FK (第3359-3360行): `REFERENCES PhysicalServerVO(uuid)`
- `ServerCapacityVO` FK (第3394-3395行): `REFERENCES PhysicalServerVO(uuid)`

所有外键引用均指向 `PhysicalServerVO`，一致性完整。

--> **已验证通过**

---

#### [强烈建议 1] AllocateServerMsg 增加字段 + extraData

**原始问题**: v1.1 的 `AllocateServerMsg` 缺少 `diskSize`、`avoidHostUuids`、`softAvoidHostUuids`、`l3NetworkUuids`、`vmInstance`、`image`、`architecture` 等关键字段。建议分核心层 + 扩展上下文两层设计。

**验证结果**: v2.0 的 `AllocateServerMsg` (第1241-1407行) 完全采纳两层设计:

核心层新增字段:
- `avoidServerUuids` (List, 第1261行) -- 硬排除
- `softAvoidServerUuids` (List, 第1262行) -- 软排除
- `requiredDisk` (Long, 第1255行) -- 磁盘需求 (原建议的 diskSize)
- `architecture` (String, 第1258行) -- 架构过滤
- `requiredClusterUuids` (List, 第1245行) -- 支持多 cluster 候选 (优于原 clusterUuid)
- `requiredCpu` / `requiredMemory` 改为 nullable Long (第1253-1254行) -- 支持 BM 整机分配
- `listAll` (boolean, 第1269行) -- 返回所有候选

扩展上下文层:
- `extraData` (Map<String, Object>, 第1284行) -- 明确注释了 KVM 的 l3NetworkUuids/requiredPrimaryStorageUuids/vmInstance、BM2 的 chassisOfferingUuid、BM1 的 pxeServerUuid 等角色特定数据的用法

总架构师裁决 1.2 明确说明: "AllocateServerMsg 不会膨胀成 AllocateHostMsg 的超集"，虚拟化相关字段通过 extraData 传递，新增角色类型时不需要修改 AllocateServerMsg 本身。这正是第一轮评审建议的方向。

--> **已验证通过**

---

#### [强烈建议 2] ServerAllocatorSpec 增加 extraData

**原始问题**: v1.1 的 `ServerAllocatorSpec` 缺少 `extraData` Map，无法在 Flow 链中传递角色特定上下文。

**验证结果**: v2.0 的 `ServerAllocatorSpec` (第1488-1514行) 包含:
- `extraData` (Map<String, Object>, 第1505行) -- 从 AllocateServerMsg 复制
- `flowContext` (Map<String, Object>, 第1511行) -- Flow 间共享运行时数据

两个 Map 的职责明确分离: extraData 承载来自请求方的角色特定输入，flowContext 承载 Flow 链内部的中间计算结果。这比第一轮建议的单个 extraData 更加清晰。

--> **已验证通过**

---

#### [强烈建议 3] 超分比改为预计算 + 独立 Manager

**原始问题**: v1.1 在 ServerCapacityVO 上存储 ratio 字段并使用 getter 实时计算，与 DB 查询不兼容，也无法支持 per-server ratio 和 ratio 变更重算。

**验证结果**: v2.0 完全采纳，多处协同修正:

1. **ServerCapacityVO** (第958-1186行):
   - 移除 `cpuOverprovisioningRatio` / `memoryOverprovisioningRatio` 字段 (变更清单 #8)
   - 移除 `getTotalCpu()` / `getTotalMemory()` 计算 getter (变更清单 #9)
   - `totalCpu` / `totalMemory` 改为预计算持久化字段 (第986-991行)，注释明确 "由 ServerCapacityOverProvisioningManager 预计算写入"
   - 新增 `availablePhysicalMemory` / `cpuNum` / `cpuSockets` / `cpuCoreNum` (第1006-1017行)

2. **ServerCapacityOverProvisioningManager** 接口 (第2802-2823行):
   - `getCpuRatio(serverUuid)` / `getMemoryRatio(serverUuid)` -- 先查 per-server 再回退全局
   - `setCpuRatio` / `setMemoryRatio` -- per-server 设置
   - `recalculate(serverUuid)` -- ratio 变更后重算
   - `calculateTotalCpu` / `calculateTotalMemory` -- 供分配器使用

3. **RecalculateServerCapacityMsg** (第2939-2951行): 内部消息，ratio 变更或对账时触发

4. **PhysicalServerGlobalConfig** (第2971-2983行):
   - `cpu.overProvisioning.ratio` 默认 10 (与现有 Host 一致)
   - `memory.overProvisioning.ratio` 默认 1 (与现有 Host 一致)

5. **DDL** (第3371-3396行): ServerCapacityVO 表无 ratio 列，`totalCpu`/`totalMemory` 均有索引 (idxServerCapacityVOTotalCpu/idxServerCapacityVOTotalMem)，支持 `WHERE totalCpu >= requiredCpu` 的 DB 查询

这套设计完整对齐了现有的 `HostCpuOverProvisioningManagerImpl` + `HostCapacityOverProvisioningManagerImpl` 双 Manager 模式，且统一为单一接口，更加简洁。

--> **已验证通过**

---

#### [建议 1] ServerAllocatorFilterExtensionPoint

**原始问题**: v1.1 的 Flow 链只有 5 个，缺少 L2/L3 网络过滤、主存储过滤、标签过滤等扩展能力。建议增加 `ServerAllocatorFilterExtensionPoint`。

**验证结果**: v2.0 第 2843-2858 行定义了 `ServerAllocatorFilterExtensionPoint`:
- `filterCandidates(spec, candidates)` -- 标准 Flow 链执行完毕后的额外过滤
- `getApplicableRoleType()` -- 可限定只对特定角色类型生效，null 表示对所有类型生效
- 注释明确了使用场景: "KVM 模块注册一个扩展，从 extraData 中读取 l3NetworkUuids，过滤掉不满足网络可达性的服务器"

同时 Flow 链扩展到 7 个 (第1531-1538行): ServerStateAllocatorFlow, ServerCapacityAllocatorFlow, ServerRoleAllocatorFlow, ServerClusterAllocatorFlow, ServerPoolAllocatorFlow, ServerArchitectureAllocatorFlow, ServerAvoidAllocatorFlow。比 v1.1 的 5 个增加了 Cluster 过滤和 Avoid 过滤。

--> **已验证通过**

---

#### [建议 2] ServerReservedCapacityExtensionPoint

**原始问题**: v1.1 的 `reservedMemory` 是静态字段，缺少动态计算的扩展点机制。建议增加 `ServerReservedCapacityExtensionPoint`。

**验证结果**: v2.0 第 2877-2883 行定义了 `ServerReservedCapacityExtensionPoint`:
- `getReservedMemory(serverUuid)` -- 各模块声明预留内存量
- 参照 `HostReservedCapacityExtensionPoint`
- `ServerCapacityVO.reservedMemory` 注释已更新为 "由 ServerReservedCapacityExtensionPoint 动态计算" (第1022行)

--> **已验证通过**

---

#### [建议 3] EAGER -> LAZY fetch

**原始问题**: v1.1 的 `PhysicalServerVO` 对 roles 使用 EAGER fetch，批量查询时有 N+1 性能问题。

**验证结果**: v2.0 的 `PhysicalServerVO` (第413行):
```java
@OneToMany(fetch = FetchType.LAZY)  // v2.0: EAGER -> LAZY
```
roles 和 hardwareDetails 两个关联均使用 LAZY fetch (第413行、第418行)。

--> **已验证通过**

---

#### [建议 4] 角色互斥声明

**原始问题**: v1.1 的 `ServerRoleType` 只有简单的 `isExclusive()` 布尔方法，无法表达独占/共享/外部调度的三种模式差异，也无法处理角色互斥并发问题。

**验证结果**: v2.0 引入了 `SchedulingMode` 枚举 (第2003-2012行):
- `INTERNAL_SHARED` -- KVM，按需扣减
- `INTERNAL_EXCLUSIVE` -- BM1/BM2，整机独占
- `EXTERNAL_READONLY` -- Container/K8s，只读同步

`ServerRoleType` (第2048-2078行) 每个角色类型关联一个 SchedulingMode，并保留便捷方法 `isExclusive()` / `isExternallyScheduled()` / `isInternallyScheduled()`。

`ServerCapacityVO` 增加了 `exclusiveRoleUuid` (第1041行) 和 `schedulingMode` (第1047行) 字段。独占分配时的典型使用 (第1701-1711行) 展示了:
```java
cap.setExclusiveRoleUuid(roleUuid);
cap.setCapacityState(CapacityState.Overloaded);
```

这比第一轮建议的简单互斥声明更加完整: SchedulingMode 从模型层面区分了三种调度范式，exclusiveRoleUuid 在运行时防止并发独占，CapacityState.Overloaded 标记使分配器在过滤阶段就能快速拦截。

--> **已验证通过**

---

#### [建议 5] 特性开关 + 容量对账

**原始问题**: 兼容层复杂度高，建议增加特性开关和容量对账定时任务。

**验证结果**:

1. **特性开关**: `PhysicalServerGlobalConfig.ALLOCATOR_ENABLED` (第2976行)，默认 false。GlobalConfig XML (第3192-3198行) 定义了 `physicalServer.allocator.enabled`，类型 Boolean。`ServerAllocatorCompatibilityBridge.isEnabled()` (第2917-2921行) 返回此开关值。

2. **兼容层接口**: `ServerAllocatorCompatibilityBridge` (第2903-2922行) 定义了 `convertFromHost` / `convertToHost` / `isEnabled` 三个方法，Phase 1 定义 + POC，Phase 2 完整实现。

3. **容量对账**: GlobalConfig XML (第3219-3225行) 定义了 `physicalServer.capacity.reconciliation.interval`，默认 3600 秒。`RecalculateServerCapacityMsg` (第2939-2951行) 提供了触发机制。`PhysicalServerRoleProvider.getActualUsage()` (第3046行) 提供了各角色实际使用量的查询 SPI。

4. **灰度策略**: Phase 9 实施步骤 (第3486行) 明确了灰度切换路径: 先 BM2 -> BM1 -> KVM。

--> **已验证通过**

---

### 未解决或部分解决的问题

无。第一轮评审提出的所有问题均已在 v2.0 中得到完整解决。

---

### 新发现的问题

经过对 v2.0 全文的仔细审阅，发现以下值得注意但不阻塞批准的观察点:

#### [观察 1] ServerCapacityUpdater 包路径

v2.0 第1579行 `ServerCapacityUpdater` 的 package 声明为 `org.zstack.server.allocator`，但文件清单 (第52-115行) 将分配子系统接口放在 `header/src/main/java/org/zstack/header/server/` 下。实现类 (`ServerCapacityUpdater`) 应放在 `compute/` 或独立的 `server/` 模块的 `src/main/java/` 下，而非 `header/` 下。当前设计仅定义了接口层 (Phase 1)，实际实现在 Phase 2，此处仅作为设计参考代码展示，包路径在实现阶段确认即可。

**影响**: 无。Phase 2 实现时确定即可。

#### [观察 2] ServerAllocatorFilterExtensionPoint 的调用时机

当前接口定义为 "标准 Flow 链执行完毕后" 调用。但现有 `HostAllocatorFilterExtensionPoint` 的实际调用位置是在 FilterFlow (第13个 Flow) 中作为 Flow 链的一环。建议 Phase 2 实现时，将 `ServerAllocatorFilterExtensionPoint` 的调用作为一个独立的 Flow (如 `ServerExtensionFilterAllocatorFlow`) 嵌入 Flow 链，而非在链外调用，以保持与现有模式一致。

**影响**: 低。接口签名无需变更，仅影响实现层的调用位置。

#### [观察 3] ServerCapacityVO.schedulingMode 缓存一致性

`schedulingMode` 作为缓存字段存在于 `ServerCapacityVO` (第1047行)，其值来源于 `ServerRoleType.getSchedulingMode()`。当物理服务器的角色发生变化时 (如从 KVM_HOST 切换到 BARE_METAL)，需要同步更新此缓存。建议 Phase 2 实现 `PhysicalServerRoleAssociator` 时，在角色关联/解关联操作中增加对 `ServerCapacityVO.schedulingMode` 的更新逻辑。特别是多角色共存场景 (如 KVM + BM2 角色切换)，需要定义 schedulingMode 的优先级规则 (INTERNAL_EXCLUSIVE > INTERNAL_SHARED > EXTERNAL_READONLY)。

**影响**: 低。Phase 2 实现时需关注，不影响 Phase 1 接口定义。

---

### 最终结论: APPROVED

v2.0 设计全面、系统性地解决了第一轮评审提出的全部 10 个问题。具体而言:

- **2 个必须修复** (编译错误 + DDL 表名): 均已正确修复，代码严格对齐现有模式
- **3 个强烈建议** (AllocateServerMsg 字段 + ServerAllocatorSpec extraData + 超分比架构): 均已完整采纳，总架构师裁决给出了清晰的设计方向
- **5 个建议** (扩展点 + LAZY fetch + 角色互斥 + 特性开关): 均已实现，且部分建议的实现质量超出预期 (如 SchedulingMode 三枚举优于简单互斥声明)

变更清单 (35项) 完整、可追溯，每项变更均标注了来源 (哪位专家/哪条裁决)。验证清单 (第 8.1/8.2/8.3 节) 覆盖了模式合规、兼容性和新文件三个维度。设计可作为 Phase 1 实施的权威依据。

上述 3 个新观察点均为 Phase 2 实现阶段的注意事项，不影响 Phase 1 的接口定义和数据模型设计，因此不阻塞批准。
