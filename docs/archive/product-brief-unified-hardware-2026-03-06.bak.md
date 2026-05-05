# Product Brief: ZStack 统一硬件管理

**版本**: v1.0
**日期**: 2026-03-06
**作者**: Business Analyst (BMAD Phase 1)
**项目级别**: Level 3 (Complex Integration)

---

## 1. Executive Summary

ZStack 统一硬件管理项目旨在为 ZStack IaaS 平台引入 `PhysicalServerVO` 统一物理服务器抽象层，将目前分散在 4 个独立模块（KVM Host、Baremetal V1、Baremetal V2、Container/K8s）中的物理服务器管理能力统一到一个数据模型和分配引擎下。面向数据中心运维团队和平台管理员，解决"同一台物理机在不同模块中各管各的、无法统一视图和调度"的核心痛点。

---

## 2. Problem Statement

### 2.1 问题描述

ZStack 当前管理物理服务器的方式存在严重碎片化：

- **KVM Host** 通过 `HostVO → KVMHostVO` 继承链管理，拥有完整的容量分配和超分比机制
- **Baremetal V1** 通过独立的 `BaremetalChassisVO` 管理，使用状态机做整机独占分配，无容量概念
- **Baremetal V2** 通过独立的 `BareMetal2ChassisAO → BareMetal2ChassisVO` 管理，有弹性/绑定双模式
- **Container** 通过 `NativeHostVO`（继承 HostVO）管理，但容量由 K8s 外部调度，ZStack 不参与分配

**四个模块各自为政**：不同的数据模型、不同的创建/连接流程、不同的分配机制、不同的状态机，甚至不同的 Cluster 语义。

### 2.2 具体问题举例

1. **同一台物理机无法跨角色复用**：一台物理机如果注册为 KVM Host，就无法同时作为 BM2 Chassis 被管理，即使硬件完全允许
2. **无统一硬件视图**：运维人员想查看"机房里所有物理机的状态"，需要分别查 4 个模块的 API，手工汇总
3. **OOB（带外管理）分散**：IPMI 信息在 KVM 侧存 `HostIpmiVO`，在 BM2 侧存 `BareMetal2ChassisAO.ipmiAddress`，重复且不一致
4. **容量分配无法统一调度**：KVM 用 `AllocateHostMsg` + 17 个 Flow，BM 用状态机独占，Container 不参与——无法做跨角色的全局容量规划

### 2.3 为什么是现在

- ZStack 5.5.x 已积累 4 个物理服务器管理模块，碎片化到了必须治理的程度
- 客户需求：大型数据中心客户要求统一资产管理视图和物理服务器生命周期管理
- 技术债务：BM1 和 BM2 共存导致代码维护成本高，统一抽象可为未来合并打基础

### 2.4 不解决的后果

- 新物理服务器角色（如 GPU 集群、智算节点）接入成本线性增长，每次都要从头实现一整套管理逻辑
- 无法支撑"混合部署"场景（同一物理机白天跑 KVM、晚上跑裸金属训练任务）
- 运维效率持续恶化，多模块管理成本随规模线性增长

---

## 3. Target Audience

### 3.1 Primary Users

| 用户角色 | 描述 | 核心诉求 |
|---------|------|---------|
| 数据中心运维工程师 | 日常管理物理服务器硬件，处理上下架、故障、巡检 | 统一视图，一处管理所有物理机 |
| IaaS 平台管理员 | 管理 ZStack 平台配置，负责资源规划和容量管理 | 跨角色容量规划，统一分配调度 |

### 3.2 Secondary Users

| 用户角色 | 描述 | 核心诉求 |
|---------|------|---------|
| ZStack 模块开发者 | 开发新的物理服务器角色模块 | 标准化接入协议，减少重复开发 |
| CMDB/资产管理系统 | 通过 API 拉取物理资产数据 | 统一数据源，一套 API 获取所有物理机信息 |

### 3.3 Top 3 用户需求

1. **统一物理服务器视图**：一个 API 查询所有物理机，无论它承载什么角色
2. **统一分配引擎**：一套分配机制适配共享(KVM)、独占(BM)、外部调度(K8s) 三种模式
3. **标准化接入协议**：新角色模块通过实现 SPI 接口即可接入，无需从头构建管理逻辑

---

## 4. Solution Overview

### 4.1 核心方案

引入独立的 **Physical Server Layer**，不修改任何现有模块代码（Phase 1），通过引用表关联新旧结构：

```
Physical Server Layer (新增)
  PhysicalServerVO          — 物理服务器唯一标识
  PhysicalServerRoleVO      — 角色映射表 (1:N, 指向 HostVO/ChassisVO 等)
  ServerCapacityVO          — 统一容量账本
  ServerHardwareInfoVO      — 硬件信息汇总
  ServerHardwareDetailVO    — 硬件明细 (CPU/MEM/DISK/NIC/GPU)
  AllocateServerMsg         — 统一分配消息
  ServerAllocatorChain      — 统一分配 Flow 链
```

### 4.2 Key Features

- **PhysicalServerVO 统一实体**：独立于 HostVO 继承链，通过 PhysicalServerRoleVO 引用关联
- **SchedulingMode 三模式调度**：INTERNAL_SHARED (KVM) / INTERNAL_EXCLUSIVE (BM) / EXTERNAL_READONLY (Container)
- **per-role cluster**：clusterUuid 放在 RoleVO 上，一台物理机可同时属于不同角色的不同 Cluster
- **核心字段 + extraData 分配消息**：通用参数 + 角色特有参数分层，通过 ExtensionPoint 扩展
- **超分比独立 Manager + 预计算持久化**：对齐现有 HostCpuOverProvisioningManager 模式
- **PhysicalServerRoleProvider SPI**：标准化角色接入协议

### 4.3 Value Proposition

用一个统一的模型**描述和包容差异**，而不是抹平差异。现有 4 个模块 100% 向后兼容，新模块通过 SPI 标准化接入。

---

## 5. Business Objectives

### 5.1 业务目标

- **统一管理**：提供跨 4 个模块的统一物理服务器 CRUD API 和查询能力
- **降低接入成本**：新角色模块接入从"实现一整套管理逻辑"简化为"实现 PhysicalServerRoleProvider SPI"
- **支撑混合部署**：为未来"同一物理机角色切换"打下数据模型基础
- **100% 向后兼容**：现有 API（AddKVMHostMsg、AllocateHostMsg 等）行为完全不变

### 5.2 Success Metrics

- Phase 1 完成后，可通过 `QueryPhysicalServerMsg` 查询所有已注册的物理服务器（跨角色）
- Phase 2 完成后，`AllocateServerMsg` 可正确分配 KVM/BM1/BM2 三种角色（Container 排除）
- Phase 3 完成后，通过特性开关可将现有 `AllocateHostMsg` 路由到新引擎
- 零回归：现有集成测试 100% 通过，无行为变更

### 5.3 Business Value

- 减少重复代码：4 个模块的 OOB 管理、硬件发现、容量管理逻辑可逐步收敛到统一层
- 提升运维效率：统一视图减少"分模块查询、手工汇总"的运维开销
- 加速新功能开发：GPU 集群、智算节点等新角色可快速接入

---

## 6. Scope

### 6.1 In Scope（本项目范围）

**一步到位交付（不分阶段，最终态设计 + 完整实现）**

数据模型（名称暂定，可调整，统一 PhysicalServer* 前缀）：
- PhysicalServerAO/VO/EO 统一物理服务器实体（独立于 HostVO 继承链）
- PhysicalServerRoleVO 角色映射表（1:N，指向 HostVO/ChassisVO 等）
- PhysicalServerCapacityVO 统一容量账本
- PhysicalServerHardwareInfoVO / PhysicalServerHardwareDetailVO 硬件信息
- ServerPoolVO 物理分组（Cluster:ServerPool = 多对一，定位为运维标签，不承载 L2 网络语义）
- PhysicalServerProvisionNetworkVO 统一装机网络（复用 BM2 成熟模型，所有角色共用——裸金属装机和裸机装 KVM ISO 都适用）

分配引擎：
- AllocateServerMsg / ServerAllocatorSpec / AllocateServerReply 消息定义
- ServerAllocatorChain Flow 链
- SchedulingMode 三模式调度（INTERNAL_SHARED / INTERNAL_EXCLUSIVE / EXTERNAL_READONLY）
- PhysicalServerCapacityUpdater 悲观锁扣减
- ServerCapacityOverProvisioningManager 超分比管理
- ServerAllocatorFilterExtensionPoint / ServerReservedCapacityExtensionPoint 扩展点

角色 SPI + 四角色适配：
- PhysicalServerRoleProvider SPI 定义
- KVM Host RoleProvider 实现
- Baremetal V1 RoleProvider 实现
- Baremetal V2 RoleProvider 实现
- Container/K8s RoleProvider 实现
- 角色自动关联机制（serialNumber 优先匹配，managementIp + zoneUuid 降级）

兼容层：
- ServerAllocatorCompatibilityBridge（AllocateHostMsg → AllocateServerMsg 薄代理，保留原始消息引用）
- 特性开关灰度切换
- 存量数据一次性 SQL 迁移脚本

统一 API：
- QueryPhysicalServerMsg 跨角色统一查询
- 统一电源管理 API（PowerManageable 接口）
- 统一硬件发现 API（HardwareDiscoverable 接口）
- DDL schema + GlobalConfig

### 6.2 Out of Scope

- 不替代或废弃 HostVO / BaremetalChassisVO / NativeHostVO 等现有实体（Wrap, don't delete）
- 不改变现有 API 行为（AddKVMHostMsg、APICreateBaremetalChassisMsg 等）
- 不做 UI 层变更
- 不包含跨角色实时切换能力（仅打下数据模型基础）
- L2 Network 挂载不从 Cluster 下放（L2 是调度边界，ServerPool 是运维标签，职责不同）
- 不做双写/数据同步阶段（PhysicalServerVO 是派生数据，单向同步即可）

### 6.3 Future Considerations

- 物理服务器生命周期管理（上架/下架/退役）
- 跨角色动态切换（同一物理机从 KVM 切到 BM）
- 统一固件管理（BIOS/BMC 升级）
- 物理服务器拓扑发现（机架/机柜/PDU 关联）

---

## 7. Stakeholders

- **IaaS 平台架构师** — Influence: High。定义统一抽象的技术方向，决定分层设计
- **KVM 模块 Owner** — Influence: High。最复杂的角色适配，容量管理核心利益方
- **Baremetal 模块 Owner** — Influence: High。BM1/BM2 双模块共存，统一管理的直接受益方
- **Container 模块 Owner** — Influence: Medium。外部调度模式需要特殊适配
- **QA 团队** — Influence: Medium。零回归验证，兼容性测试
- **产品经理** — Influence: Medium。客户需求排序和优先级确认

---

## 8. Constraints and Assumptions

### 8.1 Constraints

- **Java 8**：项目基于 Java 8，不可升级
- **Hibernate 5.3**：ORM 框架版本锁定，设计需符合 JPA 2.1 规范
- **Spring 5.2**：依赖注入和事务管理框架版本锁定
- **向后兼容**：现有 API、DB schema、前端行为 100% 不变
- **渐进式交付**：必须分 Phase 交付，每个 Phase 独立可测试
- **DbDeadlockAspect.aj**：@Transactional 和 @DeadlockAutoRestart 不能在同一方法上（编译时强制检查）

### 8.2 Assumptions

- 现有 4 个模块的 VO/API/状态机在项目周期内不会发生破坏性变更
- BM1 和 BM2 短期内会持续共存，不会在本项目期间合并
- Container 模块的 K8s 调度模式（外部调度）不会变为 ZStack 内部调度
- 物理服务器的 managementIp + zoneUuid 可作为跨模块匹配的唯一标识
- 现有集成测试覆盖率足够验证向后兼容性

---

## 9. Success Criteria

- Phase 1 设计文档通过全部 5 位领域专家评审（**已完成** — v2.0 全票通过）
- Phase 1 代码实现通过编译，DDL 可正确执行，不影响现有表
- Phase 2 实现后 `AllocateServerMsg` 可正确分配 KVM/BM1/BM2 三种角色
- Phase 2 实现后 4 个角色的 RoleProvider 可正确自动关联
- Phase 3 灰度切换后 `AllocateHostMsg` 路由到新引擎，现有测试 100% 通过
- 全程零回归：任何 Phase 交付后现有功能不受影响

---

## 10. Timeline

| 里程碑 | 内容 | 交付物 |
|--------|------|--------|
| Phase 1 | 数据模型 + 接口定义 + DDL | VO/Msg/SPI Java 接口 + DDL schema |
| Phase 2 | 完整实现 + 角色适配 | 4 个 RoleProvider + 分配器 + 容量管理 |
| Phase 3 | 兼容层 + 灰度切换 | CompatibilityBridge + 特性开关 + 对比验证 |

---

## 11. Risks

- **Risk:** 兼容层（Phase 3）复杂度超预期，AllocateHostMsg 的 22+ 字段映射困难
  - **Likelihood:** Medium
  - **Mitigation:** Phase 1 已定义 CompatibilityBridge 接口 + POC，提前暴露问题

- **Risk:** 角色自动关联误匹配（managementIp 在不同 Zone 重复）
  - **Likelihood:** Low
  - **Mitigation:** 匹配条件已加 zoneUuid 联合约束（v2.0 KVM 专家建议）

- **Risk:** ServerCapacityVO 与 HostCapacityVO 数据不一致（双写期间）
  - **Likelihood:** Medium
  - **Mitigation:** 容量对账定时任务（RecalculateServerCapacityMsg）+ 特性开关控制切换时机

- **Risk:** Container 模块的 NativeHostSyncedExtensionPoint 需要修改现有容器代码
  - **Likelihood:** High
  - **Mitigation:** 延迟到 Phase 2 实现，Phase 1 仅定义接口；可选方案 B 使用 EventFacade 事件驱动

- **Risk:** 性能影响——PhysicalServerRoleVO JOIN 查询增加分配链路延迟
  - **Likelihood:** Low
  - **Mitigation:** RoleVO 表数据量小（等于物理机数量级），加索引后 JOIN 开销可忽略

---

## 12. Existing Artifacts

本项目已有以下成果，均通过评审：

| 文档 | 版本 | 状态 |
|------|------|------|
| REVISED_Architecture_Assessment.md | v4.1 | 已完成 |
| ARCHITECT_DECISION.md | v2.0 | 已完成（7 大裁决） |
| PHASE1_Detailed_Design.md | v2.0 | 已完成（5 专家全票通过） |
| REVIEW_summary_round2.md | v1.0 | 已完成（P0 100% 解决） |

---

## Validation Checklist

- [x] Executive summary 清晰简洁
- [x] Problem statement 有具体示例
- [x] Target audience 明确定义
- [x] Solution 解决了所述问题
- [x] Business objectives 可衡量
- [x] Scope 明确（in/out 显式声明）
- [x] Stakeholders 已识别
- [x] Success criteria 可衡量
- [x] Risks 已识别并有缓解策略
