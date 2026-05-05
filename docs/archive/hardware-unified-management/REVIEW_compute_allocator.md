# Phase 1 详细设计评审意见 -- Compute Resource Allocator Expert

**评审人**: Compute Allocation Domain Expert
**评审文档**: PHASE1_Detailed_Design.md v1.1
**评审结论**: NEEDS_MODIFICATION

---

## 一、总体评价

数据模型层面做了扎实工作，但统一分配子系统（Section 1B）与现有 HostAllocator 体系的对比不够深入，存在关键字段遗漏、架构简化过度、迁移风险低估。

---

## 二、AllocateServerMsg vs AllocateHostMsg

### 缺失关键字段 [必须修复]

| 缺失字段 | 现有位置 | 严重程度 |
|----------|---------|---------|
| `diskSize` | AllocateHostMsg 第13行 | 高 |
| `avoidHostUuids` | 第15行 | 高 |
| `softAvoidHostUuids` | 第16行 | 高 |
| `l3NetworkUuids` | 第17行 | 高 (KVM) |
| `vmInstance` | 第18行 | 高 |
| `image` | 第19行 | 中 |
| `vmOperation` | 第20行 | 中 |
| `requiredPrimaryStorageUuids` | 第27行 | 高 |
| `optionalPrimaryStorageUuids` | 第29行 | 中 |
| `diskOfferings` | 第22行 | 中 |
| `requiredBackupStorageUuid` | 第25行 | 中 |
| `fullAllocate` | 第30行 | 低 |
| `oldMemoryCapacity` | 第31行 | 中 |
| `allocationScene` | 第32行 | 低 |
| `architecture` | 第33行 | 中 |

**建议分两层设计**:
1. **核心层**: 保留精简字段 + 增加 `avoidServerUuids`、`softAvoidServerUuids`、`diskSize`、`architecture`
2. **扩展上下文**: 增加 `Map<Object, Object> extraData`，承载角色特定数据（l3NetworkUuids、vmInstance 等），由兼容层注入

---

## 三、ServerAllocatorFlow 责任链 vs 现有 HostAllocatorFlow

### 现有完整 Flow 清单 (13个)
1. AttachedL2NetworkAllocatorFlow -- L2 网络可达性
2. DesignatedHostAllocatorFlow -- 指定 Zone/Cluster/Host
3. QuotaAllocatorFlow -- 配额检查
4. BackupStorageSelectPrimaryStorageAllocatorFlow -- BS-PS 关联
5. HostStateAndHypervisorAllocatorFlow -- 状态/Hypervisor 过滤
6. ImageBackupStorageAllocatorFlow -- 镜像 BS 关联
7. HostCapacityAllocatorFlow -- CPU/内存容量
8. AttachedVolumePrimaryStorageAllocatorFlow -- 已挂载卷 PS
9. HostPrimaryStorageAllocatorFlow -- Host-PS 关联
10. AvoidHostAllocatorFlow -- 硬排除
11. TagAllocatorFlow -- 标签亲和性
12. ResourceBindingAllocatorFlow -- 资源绑定
13. FilterFlow -- 扩展点过滤器

### 设计只有 5 个 Flow，仅覆盖 ~3 个等价物

**完全缺失**: L2/L3 网络过滤、主存储过滤、回避/软回避、标签过滤、配额检查、扩展点过滤、OS 版本过滤、资源绑定。

**建议**:
1. 增加 `ServerAllocatorFilterExtensionPoint`（对标 HostAllocatorFilterExtensionPoint）
2. 增加 `ServerAllocatorPreStartExtensionPoint`
3. 文档明确列出 Phase 2/3 需实现的 Flow 对照表

---

## 四、ServerCapacityUpdater 悲观锁设计

### 问题 1: @Transactional + @DeadlockAutoRestart 同方法 [编译级错误]

`DbDeadlockAspect.aj` 第19行有 `declare error` 强制禁止两注解在同一方法。**代码将无法编译。**

正确模式（HostCapacityUpdater.java 第100-119行）:
```java
@Transactional       // 内层
private boolean _run(Runnable r) { ... }

@DeadlockAutoRestart // 外层
public boolean run(Runnable r) { return _run(r); }
```

### 问题 2: 缺少回调模式 [强烈建议]
现有 `HostCapacityUpdater` 使用 `HostCapacityUpdaterRunnable` 回调，允许调用方在锁内定制逻辑。设计硬编码了扣减逻辑，不够灵活。

**建议**: 提供 `ServerCapacityUpdaterRunnable` 回调接口。

### 问题 3: 缺少 reservedMemory 扩展点
现有系统通过 `HostReservedCapacityExtensionPoint` 动态计算预留内存。设计的 `reservedMemory` 是静态字段。

**建议**: 增加 `ServerReservedCapacityExtensionPoint` 接口。

---

## 五、超分比逻辑

### 现有三层架构
1. CPU 超分: `HostCpuOverProvisioningManagerImpl` -- 全局 + per-host ratio，预计算持久化到 totalCpu
2. Memory 超分: `HostCapacityOverProvisioningManagerImpl` -- 运行时动态计算，不修改 VO
3. Ratio 变更: 触发 `RecalculateHostCapacityMsg` 重算

### 设计方案: VO getter 实时计算

| 维度 | 现有 | 设计 | 风险 |
|------|------|------|------|
| CPU ratio | 预计算持久化 | getter 实时计算 | 中: 与 DB 查询不兼容 |
| Memory ratio | 运行时动态(per-host) | VO 静态字段 | 高: 无法支持 per-server |
| Ratio 变更 | RecalculateHostCapacityMsg | 无机制 | 高 |

**建议**:
1. CPU: 预计算持久化到 totalCpu 字段（与 DB 查询兼容）
2. Memory: 不存储在 VO，通过独立 Manager 运行时计算
3. 增加 `RecalculateServerCapacityMsg`

---

## 六、Tax Collector 容量重计算

现有 `RecalculateHostCapacityMsg` 处理（第154-274行）:
1. 查询所有运行中 VM 的使用量（按 hostUuid group by）
2. 查询系统组件内存（SysComponentMemUsageExtensionPoint）
3. 悲观锁更新 availableMemory = totalMemory - usedMemory

**建议**: 通过 `PhysicalServerRoleProvider` SPI 向各角色查询实际使用量，增加 `getActualUsage()` 方法。

---

## 七、独占/共享双模式

### 问题 1: 独占清零后缺少状态标记
清零后应设置 `cap.setCapacityState(CapacityState.Overloaded)`。

### 问题 2: 独占释放的 reservedMemory 处理
`getTotalMemory()` 是 getter（physicalMem * ratio），独占场景的 ratio 应为 1.0。

### 问题 3: 缺少并发保护
两个独占请求并发时，应在 ServerCapacityAllocatorFlow 过滤阶段拦截。

**建议**: 增加 `currentExclusiveRoleUuid` 字段记录当前独占角色，`ServerRoleType` 增加角色互斥声明。

---

## 八、兼容层评价

### 问题 1: 字段映射复杂性被低估
l3NetworkUuids、requiredPrimaryStorageUuids、image 等在 Server 层无对应字段。兼容层需要**两阶段分配**: 先 Server 层物理过滤，再 Host 层虚拟化过滤。

### 问题 2: 排序策略缺失
现有 6 种策略（Default, LeastVmPreferred, StoppedVmAware, Designated, LastHostPreferred, MigrateVm），设计只有 2 个 Sortor。

### 问题 3: 容量预留一致性
扣减在 ServerCapacityVO 还是 HostCapacityVO？需要双写 + 对账。

**建议**:
1. 增加特性开关 `physicalServer.allocator.enabled`
2. 增加对账定时任务
3. 用事件驱动替代双写

---

## 九、其他设计问题

### EAGER fetch 的 N+1 查询 [建议]
PhysicalServerVO 的 hardwareInfo/capacity 用 EAGER fetch，批量查询时有性能问题。

**建议**: 改为 LAZY 或不在 VO 上做 JPA 关联。

### ServerCapacityVO 缺少 cpuNum/cpuSockets/cpuCoreNum [建议]
兼容层需要将 `totalPhysicalCpu` 映射回 `HostCapacityVO.cpuNum`。

### DDL 表名 PhysicalServerAO -> PhysicalServerVO [必须修复]
@MappedSuperclass 不生成独立表。

---

## 十、优先改进清单

1. **[必须]** @Transactional + @DeadlockAutoRestart 拆分两层
2. **[必须]** DDL 表名修正
3. **[强烈建议]** AllocateServerMsg 增加字段 + extraData Map
4. **[强烈建议]** ServerAllocatorSpec 增加 extraData
5. **[强烈建议]** 超分比改为预计算 + 独立 Manager
6. **[建议]** 增加 ServerAllocatorFilterExtensionPoint
7. **[建议]** 增加 ServerReservedCapacityExtensionPoint
8. **[建议]** EAGER -> LAZY fetch
9. **[建议]** ServerRoleType 增加角色互斥声明
10. **[建议]** 特性开关 + 容量对账机制
