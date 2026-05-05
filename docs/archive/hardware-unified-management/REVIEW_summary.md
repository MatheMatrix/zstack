# 统一硬件管理 Phase 1 架构评审汇总

**评审时间**: 2026-02-27
**评审对象**: PHASE1_Detailed_Design.md v1.1
**参与专家**: KVM Host / Baremetal V1 / Baremetal V2 / Container / Compute Allocator

---

## 总体结论：全部 5 个专家均判定 NEEDS MODIFICATION

| 专家 | 结论 | 阻塞问题 | 重要问题 |
|------|------|---------|---------|
| KVM Host | NEEDS_REVISION | 3 | 5 |
| Baremetal V1 | NEEDS_MODIFICATION | 2 | 5 |
| Baremetal2 | NEEDS_MODIFICATION | 4 | - |
| Container | 需修改 | 3 | 2 |
| Compute Allocator | 必须修复 | 2 | 4 |

---

## 跨专家共识问题

### 共识 1: 缺少 clusterUuid（4/5 专家指出）
- **KVM**: Cluster 决定 PrimaryStorage 挂载、L2Network 可达、迁移域
- **BM V1**: BaremetalChassisVO 直接持有 clusterUuid，级联删除依赖
- **BM2**: BareMetal2ChassisAO 直接关联 cluster，分配校验依赖
- **Container**: NativeHostVO 通过 HostVO 继承持有 clusterUuid

### 共识 2: AllocateServerMsg 字段严重不足（3/5 专家指出）
- 现有 AllocateHostMsg 有 22+ 字段，设计只保留 9 个
- 缺失: avoidHostUuids, l3NetworkUuids, vmInstance, image, requiredPrimaryStorageUuids 等
- BM2 额外需要: chassisOfferingUuid, requiredChassisDiskUuid

### 共识 3: ServerCapacityVO 模型过度简化（2/5 专家指出）
- 缺少 cpuNum, cpuSockets, cpuCoreNum, availablePhysicalMemory
- 超分比应用独立 Manager 而非 VO getter
- 缺少 ratio 变更后重算触发机制

### 共识 4: 状态机不完整（3/5 专家指出）
- KVM: 缺 PreMaintenance 过渡态
- BM V1: 缺 Discovering/DiscoveryFailed
- BM2: 缺 powerStatus 独立维度

---

## 各专家独特发现

### KVM 专家
- OOB 信息与 HostIpmiVO 冗余
- libvirt/qemu 版本等 KVM 元数据边界未明确
- EAGER fetch 可能导致 N+1 查询（Allocator 也指出）
- DDL 表名 PhysicalServerAO 应为 PhysicalServerVO（Allocator 也指出）

### Baremetal V1 专家
- 缺少 pxeServerUuid 关联
- ServerHardwareInfoVO (1:1 结构化) 无法表达 BM 的 1:N 泛型存储
- 缺少 Boot Mode (LEGACY/UEFI)
- 缺少 OobManagementStrategy SPI
- Chassis-Instance 二层架构、Bonding、PreconfigurationTemplate 未覆盖

### Baremetal2 专家
- 缺少 provisionType (Remote/Local/Direct)
- 缺少 chassisOfferingUuid (弹性分配核心)
- Gateway 概念完全忽略
- 弹性 vs 绑定双模式语义差异
- 角色关联应在硬件发现成功后

### Container 专家
- NativeFactory.createHost() 抛 UnsupportedOperationException -- HostAfterConnectedExtensionPoint 不适用
- 容器模块完全没有容量管理，K8s 自主调度
- Pod CPU/Memory 可能为零
- NativeClusterVO、GPU/PCI 设备、Pod 生命周期特殊性未覆盖

### Compute Allocator 专家
- @Transactional + @DeadlockAutoRestart 同方法 → 编译错误
- 现有 13 个 AllocatorFlow，设计仅覆盖 ~3 个
- 缺少 6 种排序策略迁移规划
- 缺少 ServerAllocatorFilterExtensionPoint
- HostCapacityUpdater 使用 Runnable 回调模式，设计硬编码扣减
- 需要特性开关 + 容量对账定时任务

---

## P0 问题完整清单

| # | 问题 | 指出专家 |
|---|------|---------|
| 1 | @Transactional + @DeadlockAutoRestart 拆分（编译错误） | Allocator |
| 2 | DDL 表名 PhysicalServerAO → PhysicalServerVO | KVM, Allocator |
| 3 | PhysicalServerAO 增加 clusterUuid 或等价关联 | KVM, BM1, BM2, Container |
| 4 | PhysicalServerAO 增加 powerStatus 独立维度 | BM2 |
| 5 | PhysicalServerState 增加 PreMaintenance | KVM |
| 6 | PhysicalServerStatus 增加 Discovering/DiscoveryFailed 或 roleStatus | BM1 |
| 7 | AllocateServerMsg 补齐关键字段 + extraData Map | KVM, BM2, Allocator |
| 8 | Container 角色关联改用 Endpoint 同步扩展点 | Container |
| 9 | NATIVE_HOST 标记为外部调度，拒绝主动分配 | Container |
| 10 | ServerCapacityVO 补齐 cpuNum/cpuSockets/availablePhysicalMemory | KVM, Allocator |
| 11 | 超分比改为独立 Manager + 预计算持久化 | KVM, Allocator |
| 12 | 角色关联时机：硬件发现成功后而非创建时 | BM2 |

---

## 设计优点（全部专家认可）

1. PhysicalServerAO/VO 继承结构严格遵循 ZStack 惯例
2. PhysicalServerRoleVO 映射设计方向正确
3. SPI 接口 PhysicalServerRoleProvider 扩展模式正确
4. 不修改任何现有代码的增量策略
5. ServerPoolVO 作为物理分组补充 Cluster
6. 独占/共享双模式分配方向正确
7. Spring XML 注册、状态机、Inventory 等模式正确复制

---

## 待总架构师裁决的问题

1. clusterUuid 放在 PhysicalServerAO 还是 PhysicalServerRoleVO？
2. 统一分配子系统（AllocateServerMsg）是否保留在 Phase 1，还是推迟？
3. 容器的外部调度模式如何与统一分配共存？
4. ServerHardwareInfoVO 保持 1:1 结构化还是改为 1:N 泛型？
5. 状态机统一 vs 各角色 roleStatus 独立？
6. 超分比管理：VO 内部 getter vs 独立 Manager？
7. 兼容层是否需要在 Phase 1 做 POC？
