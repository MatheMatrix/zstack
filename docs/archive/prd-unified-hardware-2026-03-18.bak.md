# PRD: ZStack 统一硬件管理

**版本**: v1.0
**日期**: 2026-03-18
**作者**: Product Manager (BMAD Phase 2)
**项目级别**: Level 3 (Complex Integration)
**输入文档**: product-brief-unified-hardware-2026-03-06.md

---

## 1. Executive Summary

ZStack 统一硬件管理项目为 ZStack IaaS 平台引入 `PhysicalServerVO` 统一物理服务器抽象层，将 4 个独立模块（KVM Host、Baremetal V1、Baremetal V2、Container/K8s）的物理服务器管理统一到一个数据模型、分配引擎和操作 API 下。

**交付方式**: 一步到位，不分阶段。最终态设计 + 完整实现，四个角色全部适配。

**架构审查结论**（群总第一性原理审查，2026-03-18）:
- ❌ 双写阶段推翻 — PhysicalServerVO 是派生数据，单向同步即可
- ❌ L2 下放到 ServerPool 推翻 — L2 是调度边界，ServerPool 是运维标签
- ❌ 四层架构 Adapter 层推翻 — 降为三层（Physical → Role → Consumer），SPI 嵌入 Physical 层
- ✅ ServerPool 纳入范围，定位运维标签
- ✅ ProvisionNetwork 提升为统一装机网络，所有角色共用（BM 装机 + 裸机装 KVM ISO）
- ✅ VO 命名统一 PhysicalServer* 前缀
- ✅ serialNumber 作为主匹配标识，managementIp+zoneUuid 降级备选

---

## 2. Business Objectives

1. **统一管理** — 跨 4 模块的统一物理服务器 CRUD、查询、电源管理
2. **降低接入成本** — 新角色通过 PhysicalServerRoleProvider SPI 接入
3. **统一装机** — ProvisionNetwork 覆盖裸金属装机和裸机装 KVM ISO
4. **支撑混合部署** — 数据模型支持一台物理机多角色
5. **100% 向后兼容** — 现有 API 行为不变

### Success Metrics

- QueryPhysicalServerMsg 可查询所有已注册的物理服务器（跨角色）
- AllocateServerMsg 可正确分配四种角色类型
- 特性开关可将现有 AllocateHostMsg 路由到新引擎
- 零回归：现有集成测试 100% 通过

---

## 3. User Personas

| 角色 | 描述 | 核心诉求 |
|------|------|---------|
| 数据中心运维工程师 | 日常管理物理服务器硬件 | 统一视图，一处管理所有物理机 |
| IaaS 平台管理员 | 资源规划和容量管理 | 跨角色容量规划，统一分配调度 |
| ZStack 模块开发者 | 开发新的物理服务器角色 | 标准化 SPI 接入协议 |
| CMDB/资产管理系统 | 通过 API 拉取物理资产数据 | 统一数据源 |

---

## 4. Functional Requirements

### EPIC-1: 物理服务器统一模型（6 FRs）

#### FR-001: PhysicalServerAO/VO/EO 统一实体

**Priority:** Must Have

**Description:**
定义 PhysicalServerAO/VO/EO 统一物理服务器实体，独立于 HostVO 继承链。以 serialNumber 作为硬件唯一主键。核心字段包括 zoneUuid、poolUuid、name、managementIp、architecture、serialNumber、manufacturer、model、state、status，以及 OOB 带外管理凭据。

**Acceptance Criteria:**
- [ ] VO 可正确持久化到数据库
- [ ] UNIQUE(zoneUuid, serialNumber) 约束生效
- [ ] 独立于 HostVO 继承链，无 FK 依赖
- [ ] EO 支持软删除

**Dependencies:** 无

---

#### FR-002: PhysicalServerRoleVO 角色映射

**Priority:** Must Have

**Description:**
角色映射表，支持一台物理服务器关联多种角色（1:N）。每条记录标识 serverUuid、roleType（KVM_HOST/BAREMETAL_V1/BAREMETAL_V2/CONTAINER_HOST）、roleUuid（指向 HostVO/ChassisVO 的 UUID）、schedulingMode、status。

**Acceptance Criteria:**
- [ ] UNIQUE(serverUuid, roleType) 约束生效
- [ ] roleUuid 可正确指向 HostVO、BaremetalChassisVO、BareMetal2ChassisVO、NativeHostVO
- [ ] 角色状态（Active/Stale）可查询

**Dependencies:** FR-001

---

#### FR-003: PhysicalServerHardwareInfoVO 硬件汇总

**Priority:** Must Have

**Description:**
物理服务器硬件信息汇总表，与 PhysicalServerVO 共享 UUID。包含 cpuModel、cpuCores、cpuSockets、totalMemory、totalDisk、nicCount、gpuCount 等。

**Acceptance Criteria:**
- [ ] 通过 QueryPhysicalServerMsg 可查询硬件汇总
- [ ] 硬件发现可更新此表

**Dependencies:** FR-001

---

#### FR-004: PhysicalServerHardwareDetailVO 硬件明细

**Priority:** Should Have

**Description:**
单项硬件详情表（CPU/MEM/DISK/NIC/GPU），支持 S.M.A.R.T 信息、固件版本等详细数据。每台物理服务器对应多条明细记录。

**Acceptance Criteria:**
- [ ] 支持按 detailType（CPU/DISK/NIC/GPU）查询
- [ ] 硬件发现可批量写入明细

**Dependencies:** FR-001, FR-003

---

#### FR-005: OOB 带外管理凭据统一存储

**Priority:** Must Have

**Description:**
在 PhysicalServerAO 中统一存储 OOB 管理信息：oobManagementType（IPMI/REDFISH）、oobAddress、oobPort、oobUsername、oobPassword（@Password 加密）。

**Acceptance Criteria:**
- [ ] 密码字段加密存储
- [ ] 支持 IPMI 和 Redfish 两种类型
- [ ] 创建 PhysicalServer 时 OOB 信息为可选（Container 场景无 OOB）

**Dependencies:** FR-001

---

#### FR-006: PhysicalServer 状态机

**Priority:** Must Have

**Description:**
统一状态机定义。State: Enabled/Disabled/Maintenance（管理员控制）。Status: Connecting/Connected/Disconnected（系统检测）。PhysicalServerPowerStatus: PowerOn/PowerOff/Unknown（OOB 电源状态）。

**Acceptance Criteria:**
- [ ] State 转换需管理员 API 调用
- [ ] Status 由 OOB 心跳和 agent 探测自动更新
- [ ] PowerStatus 由 IPMI/Redfish 查询获取
- [ ] Maintenance 状态下不参与分配

**Dependencies:** FR-001

---

### EPIC-2: ServerPool 物理分组（3 FRs）

#### FR-007: ServerPoolVO CRUD

**Priority:** Must Have

**Description:**
物理分组管理，定位为运维标签（机房/机架标识）。核心字段：uuid、name、zoneUuid、physicalLocation（物理位置描述）、networkTopology（网络拓扑描述，文本）、state（Enabled/Disabled）。

**Acceptance Criteria:**
- [ ] 标准 ResourceVO，支持 CRUD API
- [ ] 归属 Zone，Zone 删除级联
- [ ] 不承载 L2 Network 语义

**Dependencies:** 无

---

#### FR-008: Cluster:ServerPool 多对一关联

**Priority:** Must Have

**Description:**
多个 Cluster 可以引用同一个 ServerPool。在 ClusterVO 上增加可选的 poolUuid FK，或通过 ClusterServerPoolRefVO 关联表实现。

**Acceptance Criteria:**
- [ ] 多个 Cluster 可关联同一 ServerPool
- [ ] Cluster 未关联 ServerPool 时行为不变（向后兼容）
- [ ] 查询 ServerPool 时可列出关联的所有 Cluster

**Dependencies:** FR-007

---

#### FR-009: PhysicalServer 归属 ServerPool

**Priority:** Must Have

**Description:**
PhysicalServerVO.poolUuid FK 到 ServerPoolVO，创建时必填。

**Acceptance Criteria:**
- [ ] 创建 PhysicalServer 时 poolUuid 必填
- [ ] ServerPool 删除前必须先移除或迁移所有 PhysicalServer
- [ ] 支持修改 PhysicalServer 的 poolUuid（调整分组）

**Dependencies:** FR-001, FR-007

---

### EPIC-3: 统一装机网络（3 FRs）

#### FR-010: PhysicalServerProvisionNetworkVO

**Priority:** Must Have

**Description:**
统一装机网络资源，复用 BM2 成熟模型。核心字段：uuid、name、zoneUuid、dhcpInterface、dhcpRangeStartIp/EndIp/Netmask/Gateway、state、type（STANDALONE_PXE / GATEWAY_PXE）。

**Acceptance Criteria:**
- [ ] 支持两种装机模式
- [ ] DHCP 地址范围可配置
- [ ] BM1 BaremetalPxeServerVO 和 BM2 BareMetal2ProvisionNetworkVO 可映射到此统一抽象

**Dependencies:** 无

---

#### FR-011: ProvisionNetwork 通过 ClusterRef 关联

**Priority:** Must Have

**Description:**
PhysicalServerProvisionNetworkClusterRefVO 关联表，networkUuid + clusterUuid。Cluster 删除时级联删除关联。

**Acceptance Criteria:**
- [ ] 一个 ProvisionNetwork 可关联多个 Cluster
- [ ] 一个 Cluster 可关联多个 ProvisionNetwork
- [ ] 级联删除行为正确

**Dependencies:** FR-010

---

#### FR-012: 装机网络适用于所有角色

**Priority:** Must Have

**Description:**
ProvisionNetwork 不限定裸金属专用。裸机装 KVM ISO（将未管理的物理服务器安装 hypervisor OS）同样使用此网络。

**Acceptance Criteria:**
- [ ] KVM 角色的 RoleProvider 可使用 ProvisionNetwork 进行 OS 安装
- [ ] BM1/BM2 的装机流程可迁移到统一 ProvisionNetwork
- [ ] API 不限定角色类型

**Dependencies:** FR-010, FR-022

---

### EPIC-4: 统一容量管理（5 FRs）

#### FR-013: PhysicalServerCapacityVO 统一容量账本

**Priority:** Must Have

**Description:**
与 PhysicalServerVO 共享 UUID。字段：totalPhysicalCpu、totalPhysicalMemory、cpuOverprovisioningRatio、memoryOverprovisioningRatio、availableCpu、availableMemory、reservedMemory、totalDisk、availableDisk、capacityState。

**Acceptance Criteria:**
- [ ] getTotalCpu() = totalPhysicalCpu × cpuOverprovisioningRatio
- [ ] getTotalMemory() = totalPhysicalMemory × memoryOverprovisioningRatio
- [ ] FK CASCADE 到 PhysicalServerVO

**Dependencies:** FR-001

---

#### FR-014: SchedulingMode 三模式调度

**Priority:** Must Have

**Description:**
枚举定义三种调度模式：
- INTERNAL_SHARED：ZStack 内部分配，支持超分（KVM）
- INTERNAL_EXCLUSIVE：ZStack 内部分配，独占（BM）
- EXTERNAL_READONLY：外部调度，ZStack 只读（Container/K8s）

**Acceptance Criteria:**
- [ ] INTERNAL_EXCLUSIVE 分配时清零所有可用量
- [ ] EXTERNAL_READONLY 不参与 ZStack 容量扣减
- [ ] SchedulingMode 在 PhysicalServerRoleVO 上标记

**Dependencies:** FR-002

---

#### FR-015: PhysicalServerCapacityUpdater 悲观锁扣减

**Priority:** Must Have

**Description:**
容量更新器，使用 PESSIMISTIC_WRITE 锁 + @DeadlockAutoRestart。根据 roleType 判断独占/共享分配逻辑。

**Acceptance Criteria:**
- [ ] 并发扣减不产生超卖
- [ ] 死锁自动重试
- [ ] @Transactional 和 @DeadlockAutoRestart 不在同一方法上

**Dependencies:** FR-013, FR-014

---

#### FR-016: ServerCapacityOverProvisioningManager

**Priority:** Must Have

**Description:**
超分比管理器，CPU 和 Memory 独立比率。对齐现有 HostCpuOverProvisioningManager 模式。支持全局默认值 + per-server 覆盖。

**Acceptance Criteria:**
- [ ] 全局 GlobalConfig 可配置默认超分比
- [ ] 支持 per-server 覆盖（SystemTag 或独立表）
- [ ] 修改超分比触发容量重计算

**Dependencies:** FR-013

---

#### FR-017: 容量重计算

**Priority:** Must Have

**Description:**
RecalculatePhysicalServerCapacityMsg，采用税收模式：Available = Total - Σ(业务税) - Σ(系统税)。业务税通过 RoleProvider.getCapacityConsumption() 征收，系统税通过 ServerReservedCapacityExtensionPoint 征收。

**Acceptance Criteria:**
- [ ] 可手动触发重计算（API 或内部消息）
- [ ] 重计算后容量数据准确
- [ ] 支持全量重计算和单台重计算

**Dependencies:** FR-013, FR-022

---

### EPIC-5: 统一分配引擎（4 FRs）

#### FR-018: AllocateServerMsg 统一分配消息

**Priority:** Must Have

**Description:**
统一分配消息，核心字段：requiredRoleType、requiredCpu、requiredMemory、clusterUuid、zoneUuid、serverUuid（指定分配）、poolUuid（池内分配）、schedulingMode。采用薄代理模式：通过 originalMessage 字段保留原始消息引用，角色特有参数从 originalMessage 中获取，不做 Map<String,String> 序列化。

**Acceptance Criteria:**
- [ ] 可正确分配四种角色类型
- [ ] originalMessage 引用在 Flow 链中可访问
- [ ] 分配失败返回明确错误码

**Dependencies:** FR-013, FR-014

---

#### FR-019: ServerAllocatorChain Flow 链

**Priority:** Must Have

**Description:**
可扩展的 Flow 责任链，每个 Flow 负责一个过滤/筛选逻辑。基础 Flow 包括：ZoneFilter、ClusterFilter、PoolFilter、RoleTypeFilter、StatusFilter、CapacityFilter、SortFilter。

**Acceptance Criteria:**
- [ ] Flow 可通过 Spring 注入扩展
- [ ] 每个 Flow 独立可测试
- [ ] Flow 执行顺序可配置

**Dependencies:** FR-018

---

#### FR-020: ServerAllocatorFilterExtensionPoint

**Priority:** Must Have

**Description:**
扩展点，允许第三方模块在分配链中注入自定义过滤逻辑。

**Acceptance Criteria:**
- [ ] 扩展点可被外部模块实现
- [ ] 过滤逻辑在 Flow 链中正确执行
- [ ] 扩展点有清晰的 Javadoc 文档

**Dependencies:** FR-019

---

#### FR-021: ServerReservedCapacityExtensionPoint

**Priority:** Should Have

**Description:**
系统预留容量扩展点，各模块可声明在物理服务器上的系统级资源预留（OS 开销、Ceph Agent、监控 Agent 等）。

**Acceptance Criteria:**
- [ ] 容量重计算时汇总所有扩展点的预留量
- [ ] 预留量在分配时被正确扣除

**Dependencies:** FR-017, FR-019

---

### EPIC-6: 角色 SPI + 四角色适配（6 FRs）

#### FR-022: PhysicalServerRoleProvider SPI

**Priority:** Must Have

**Description:**
标准化角色接入协议。核心方法：
- getRoleType(): ServerRoleType
- getSchedulingMode(): SchedulingMode
- getCapacityConsumption(serverUuid): CapacityUsage
- onPhysicalServerCreated/Deleted(serverUuid): 生命周期回调
- getInventory(roleUuid): RoleInventory（查询角色详情）

**Acceptance Criteria:**
- [ ] 接口方法语义明确，有完整 Javadoc
- [ ] 四个角色模块均可实现此 SPI
- [ ] 新角色模块通过实现此 SPI 即可接入

**Dependencies:** 无

---

#### FR-023: KVM Host RoleProvider

**Priority:** Must Have

**Description:**
KVM 角色适配器。在 KVM Host PostConnect 时创建 PhysicalServerRoleVO，SchedulingMode = INTERNAL_SHARED。getCapacityConsumption 从 HostCapacityVO 读取已用容量。

**Acceptance Criteria:**
- [ ] 新建 KVM Host 自动创建 PhysicalServerVO + RoleVO
- [ ] 删除 KVM Host 自动更新 RoleVO 状态
- [ ] 容量查询数据与 HostCapacityVO 一致

**Dependencies:** FR-001, FR-002, FR-022

---

#### FR-024: Baremetal V1 RoleProvider

**Priority:** Must Have

**Description:**
BM1 角色适配器。BaremetalChassis 创建时同步创建 PhysicalServerVO + RoleVO，SchedulingMode = INTERNAL_EXCLUSIVE。IPMI 信息从 ChassisVO 同步到 PhysicalServerVO OOB 字段。

**Acceptance Criteria:**
- [ ] Chassis 创建/删除自动同步
- [ ] IPMI 信息正确同步
- [ ] 独占分配时清零可用容量

**Dependencies:** FR-001, FR-002, FR-022

---

#### FR-025: Baremetal V2 RoleProvider

**Priority:** Must Have

**Description:**
BM2 角色适配器。BareMetal2Chassis 创建时同步创建 PhysicalServerVO + RoleVO。支持弹性/绑定双模式，SchedulingMode = INTERNAL_EXCLUSIVE。

**Acceptance Criteria:**
- [ ] BM2 Chassis 创建/删除自动同步
- [ ] 弹性模式和绑定模式正确映射
- [ ] ProvisionNetwork 可迁移到统一抽象

**Dependencies:** FR-001, FR-002, FR-022

---

#### FR-026: Container/K8s RoleProvider

**Priority:** Must Have

**Description:**
Container 角色适配器。NativeHost 连接时同步创建 PhysicalServerVO + RoleVO，SchedulingMode = EXTERNAL_READONLY。容量由 K8s 报告，ZStack 不扣减。

**Acceptance Criteria:**
- [ ] NativeHost 连接/断开自动同步
- [ ] EXTERNAL_READONLY 模式下不参与 ZStack 容量分配
- [ ] K8s 报告的容量可同步到 PhysicalServerCapacityVO（只读展示）

**Dependencies:** FR-001, FR-002, FR-022

---

#### FR-027: 角色自动关联

**Priority:** Must Have

**Description:**
物理服务器注册新角色时，自动匹配已有 PhysicalServerVO。主匹配条件：serialNumber（硬件序列号）。降级匹配：managementIp + zoneUuid。匹配成功则关联到已有 PhysicalServerVO，否则新建。

**Acceptance Criteria:**
- [ ] 同一台物理机注册为 KVM 和 Container 时自动关联到同一个 PhysicalServerVO
- [ ] serialNumber 匹配优先于 managementIp 匹配
- [ ] 降级匹配时 zoneUuid 必须一致
- [ ] 匹配失败创建新 PhysicalServerVO

**Dependencies:** FR-001, FR-002, FR-022

---

### EPIC-7: 兼容层 + 统一 API（6 FRs）

#### FR-028: CompatibilityBridge 薄代理

**Priority:** Must Have

**Description:**
拦截 AllocateHostMsg，转换为 AllocateServerMsg（保留原始消息引用），调用 ServerAllocatorChain，将结果反向映射回 HostInventory。不做 22+ 字段的完整映射。

**Acceptance Criteria:**
- [ ] AllocateHostMsg 透传到新引擎后行为不变
- [ ] 原始消息中的 l3NetworkUuids、primaryStorageUuids 等字段在 Flow 链中可访问
- [ ] 分配结果正确映射回 HostInventory

**Dependencies:** FR-018, FR-019

---

#### FR-029: 特性开关灰度切换

**Priority:** Must Have

**Description:**
GlobalConfig 控制 CompatibilityBridge 是否启用，支持按角色类型逐步启用（先 BM → 再 KVM）。开关关闭时走原有分配链路。

**Acceptance Criteria:**
- [ ] 开关可运行时修改，无需重启
- [ ] 按角色类型独立控制
- [ ] 开关关闭时 100% 走旧路径

**Dependencies:** FR-028

---

#### FR-030: 存量数据迁移脚本

**Priority:** Must Have

**Description:**
一次性 SQL 脚本，为所有存量 HostVO/BaremetalChassisVO/BareMetal2ChassisVO/NativeHostVO 生成对应的 PhysicalServerVO + PhysicalServerRoleVO。

**Acceptance Criteria:**
- [ ] 脚本幂等，重复执行不产生重复数据
- [ ] serialNumber 尽可能从现有数据提取
- [ ] 无 serialNumber 时使用 managementIp + zoneUuid 生成确定性 UUID
- [ ] 迁移后 QueryPhysicalServerMsg 可查到所有存量物理机

**Dependencies:** FR-001, FR-002

---

#### FR-031: QueryPhysicalServerMsg 统一查询

**Priority:** Must Have

**Description:**
跨角色统一查询 API，支持标准 ZStack Query 语法。可按 poolUuid、zoneUuid、clusterUuid、roleType、state、status 过滤。返回 PhysicalServerInventory（含角色列表、硬件汇总、容量信息）。

**Acceptance Criteria:**
- [ ] 查询结果包含所有角色类型的物理服务器
- [ ] 支持分页、排序、条件过滤
- [ ] 性能：1000 台物理机查询 < 500ms

**Dependencies:** FR-001, FR-002, FR-003

---

#### FR-032: 统一电源管理 API

**Priority:** Must Have

**Description:**
PowerManageable 接口，提供 powerOn/powerOff/powerReset/powerStatus 操作。通过 OOB 凭据（IPMI/Redfish）执行。各角色 RoleProvider 可覆盖默认实现。

**Acceptance Criteria:**
- [ ] 支持 IPMI 和 Redfish 两种协议
- [ ] 电源操作结果更新 PhysicalServerPowerStatus
- [ ] 无 OOB 凭据时返回明确错误

**Dependencies:** FR-005, FR-022

---

#### FR-033: 统一硬件发现 API

**Priority:** Should Have

**Description:**
HardwareDiscoverable 接口，触发物理服务器硬件信息采集。通过 OOB（IPMI FRU）或 agent（读取 /sys/class/dmi/）获取硬件数据，写入 HardwareInfoVO/HardwareDetailVO。

**Acceptance Criteria:**
- [ ] 支持 OOB 和 agent 两种采集方式
- [ ] 采集结果写入 FR-003/FR-004 定义的表
- [ ] 采集可手动触发或在 PhysicalServer 首次连接时自动触发

**Dependencies:** FR-003, FR-004, FR-005

---

## 5. Non-Functional Requirements

### NFR-001: API 向后兼容

**Priority:** Must Have

**Description:**
现有 API（AddKVMHostMsg、AllocateHostMsg、APICreateBaremetalChassisMsg 等）行为 100% 不变。

**Acceptance Criteria:**
- [ ] 所有现有 API 的入参、出参、错误码不变
- [ ] 现有 UI 操作无需修改

**Rationale:** 用户和前端依赖现有 API 契约

---

### NFR-002: 零回归

**Priority:** Must Have

**Description:**
现有集成测试 100% 通过，不引入任何行为变更。

**Acceptance Criteria:**
- [ ] zstack/test 和 premium/test-premium 全量测试通过
- [ ] 无新增 WARN/ERROR 日志

**Rationale:** 向后兼容的底线保障

---

### NFR-003: JOIN 查询性能

**Priority:** Must Have

**Description:**
PhysicalServerRoleVO JOIN 查询不增加分配链路延迟。

**Acceptance Criteria:**
- [ ] 分配链路新增延迟 < 5ms（PhysicalServerRoleVO 数据量 = 物理机数量级）
- [ ] 关键查询字段有索引

**Rationale:** 分配是热路径，不能引入性能回退

---

### NFR-004: 死锁安全

**Priority:** Must Have

**Description:**
PhysicalServerCapacityUpdater 悲观锁不引入新的死锁热点。

**Acceptance Criteria:**
- [ ] 锁获取顺序与现有 HostCapacityUpdater 一致
- [ ] @DeadlockAutoRestart 正确配置
- [ ] 压力测试下无死锁

**Rationale:** 容量扣减是并发热点

---

### NFR-005: Git Blame 保护

**Priority:** Must Have

**Description:**
Wrap, don't delete。不修改现有 VO 文件名、变量名、方法签名。

**Acceptance Criteria:**
- [ ] 现有文件的 git blame 不被本次改动污染
- [ ] 不重命名任何现有类/方法/变量

**Rationale:** 历史追溯是大型项目的生命线

---

### NFR-006: SPI 可扩展性

**Priority:** Must Have

**Description:**
新角色模块通过实现 PhysicalServerRoleProvider SPI 即可接入，无需修改核心代码。

**Acceptance Criteria:**
- [ ] SPI 接口稳定，有完整 Javadoc
- [ ] 新增角色只需实现 SPI + 注册 Spring Bean
- [ ] 不需要修改 server 模块核心代码

**Rationale:** 降低新角色接入成本，支持 GPU 集群等未来角色

---

### NFR-007: 迁移幂等

**Priority:** Must Have

**Description:**
存量数据迁移脚本幂等，可重复执行不产生重复数据。

**Acceptance Criteria:**
- [ ] INSERT ... ON DUPLICATE KEY 或等效机制
- [ ] 迁移前后数据一致性可验证

**Rationale:** 生产环境迁移必须安全可重试

---

### NFR-008: 可回滚

**Priority:** Must Have

**Description:**
统一管理模块可完整卸载（删 schema + 删 module），不影响现有系统运行。

**Acceptance Criteria:**
- [ ] 删除 PhysicalServer* 表后，现有 HostVO/ChassisVO 系统正常运行
- [ ] 特性开关关闭后，AllocateHostMsg 完全走旧路径

**Rationale:** 上线安全网

---

### NFR-009: 技术栈约束

**Priority:** Must Have

**Description:**
Java 8 + Hibernate 5.3.26 + Spring 5.2.25，不可升级。

**Acceptance Criteria:**
- [ ] 代码不使用 Java 9+ 特性
- [ ] JPA 注解符合 JPA 2.1 规范

**Rationale:** 项目基线约束

---

### NFR-010: 事务约束

**Priority:** Must Have

**Description:**
@Transactional 和 @DeadlockAutoRestart 不能在同一方法上（DbDeadlockAspect.aj 编译时强制检查）。

**Acceptance Criteria:**
- [ ] AspectJ 编译通过
- [ ] 事务方法和死锁重试方法分离

**Rationale:** ZStack 框架硬约束

---

## 6. Epics & Traceability

| Epic ID | Epic Name | FRs | 优先级 | Story 估算 |
|---------|-----------|-----|--------|-----------|
| EPIC-1 | 物理服务器统一模型 | FR-001~006 | Must | 6-8 stories |
| EPIC-2 | ServerPool 物理分组 | FR-007~009 | Must | 3-4 stories |
| EPIC-3 | 统一装机网络 | FR-010~012 | Must | 4-5 stories |
| EPIC-4 | 统一容量管理 | FR-013~017 | Must | 5-7 stories |
| EPIC-5 | 统一分配引擎 | FR-018~021 | Must | 5-6 stories |
| EPIC-6 | 角色 SPI + 四角色适配 | FR-022~027 | Must | 8-10 stories |
| EPIC-7 | 兼容层 + 统一 API | FR-028~033 | Must | 7-9 stories |

**总计**: 7 Epics, 33 FRs (29 Must / 4 Should), 10 NFRs (10 Must), 预估 38-49 stories

---

## 7. NFR Traceability

| NFR ID | NFR Name | 关联 Epic | 验证方式 |
|--------|----------|----------|---------|
| NFR-001 | API 向后兼容 | EPIC-7 | 现有 API 测试 |
| NFR-002 | 零回归 | ALL | 全量集成测试 |
| NFR-003 | JOIN 性能 | EPIC-5, EPIC-7 | 分配链路 benchmark |
| NFR-004 | 死锁安全 | EPIC-4 | 并发压力测试 |
| NFR-005 | Blame 保护 | ALL | git blame 抽查 |
| NFR-006 | SPI 可扩展 | EPIC-6 | 新角色接入 POC |
| NFR-007 | 迁移幂等 | EPIC-7 | 重复执行验证 |
| NFR-008 | 可回滚 | ALL | 卸载验证 |
| NFR-009 | 技术栈 | ALL | 编译验证 |
| NFR-010 | 事务约束 | EPIC-4 | AspectJ 编译 |

---

## 8. Dependencies

### Internal Dependencies
- HostVO / HostCapacityVO (compute module)
- BaremetalChassisVO / BaremetalPxeServerVO (premium/baremetal)
- BareMetal2ChassisVO / BareMetal2ProvisionNetworkVO (premium/baremetal2)
- NativeHostVO (premium/container 或 plugin/container)
- L2NetworkClusterRefVO (不修改，仅读取)
- ClusterVO (增加可选 poolUuid)

### External Dependencies
- 无外部系统依赖

---

## 9. Assumptions

1. 现有 4 个模块的 VO/API/状态机在项目周期内不发生破坏性变更
2. BM1 和 BM2 短期内持续共存
3. Container 模块的 K8s 调度模式不变为 ZStack 内部调度
4. serialNumber 可通过 IPMI FRU 或 agent 读取 /sys/class/dmi/id/product_serial 获取
5. 现有集成测试覆盖率足够验证向后兼容性

---

## 10. Out of Scope

- 不替代或废弃 HostVO / BaremetalChassisVO / NativeHostVO 等现有实体
- 不改变现有 API 行为
- 不做 UI 层变更
- L2 Network 挂载不从 Cluster 下放（L2 是调度边界，ServerPool 是运维标签）
- 不做双写/数据同步阶段
- 不包含跨角色实时切换能力
- 不动 HostCapacityUpdater 本身（只做消息拦截转发）

---

## 11. Open Questions

1. serialNumber 在虚拟化环境和白牌服务器中的可靠性需进一步验证
2. KVM + Container 混部场景下，角色自动关联的 managementIp 可能不同（多网卡），需确认匹配策略
3. BM1/BM2 的 ProvisionNetwork 迁移到统一抽象的具体字段映射需详细设计
4. ServerPool 是否需要层级（机房 → 机柜 → 机位），还是扁平结构

---

## 12. Risks

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| CompatibilityBridge 复杂度超预期 | Medium | High | 薄代理模式，保留原始消息引用 |
| 角色自动关联误匹配 | Low | Medium | serialNumber + zoneUuid 联合约束 |
| KVM RoleProvider 涉及 662 文件引用的 Cluster 体系 | Medium | High | 只在 PostConnect 钩子增量创建，不改现有 Cluster 逻辑 |
| 统一分配引擎与现有 17+ Flow 行为不一致 | Medium | High | 特性开关灰度 + 双引擎对比验证 |
| 一步到位范围大，交付周期长 | Medium | Medium | 按 Epic 逐个推进，EPIC-1/2 优先 |

---

## Validation Checklist

- [x] 所有 Must-Have FRs 有明确的验收标准
- [x] NFRs 可量化验证
- [x] Epics 逻辑分组合理，FRs 全覆盖
- [x] 优先级区分清晰（29 Must / 4 Should）
- [x] 需求追溯到业务目标
- [x] Out of Scope 显式声明
- [x] 风险已识别并有缓解策略
- [x] 群总架构审查结论已纳入

---

## Next Step

→ `/bmad:architecture` — 基于此 PRD 进行系统架构设计
