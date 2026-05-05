# 数据结构精简裁决记录 (2026-04-14)

## 三方审查机制
- **保留派**: 为每个字段辩护
- **删除派**: 挑战每个字段
- **Tech Lead (Opus)**: 独立裁决

## 已执行的删除（三方一致）

| 项目 | 理由 |
|---|---|
| PhysicalServerAO: cpuModel/cpuSockets/cpuCores | 无 API 写入，硬件发现写 DetailVO |
| ServerRoleType: BAREMETAL_V1 | 无实现，YAGNI |
| APIAttachPhysicalServerRoleMsg: roleConfig | 零消费者 |
| CapacityUsage: usedDisk | 磁盘由存储子系统管理 |

## 裁决保留（Tech Lead 最终决定）

### PhysicalServerAO

| 字段 | 删除派意见 | 保留理由 |
|---|---|---|
| manufacturer | 归 DetailVO | PRD 明确 + CreateMsg 有字段 + handler 有写入 |
| model | 归 DetailVO | 同上 |
| powerStatus | 零读写 | Phase 5 电源管理核心，删了再加成本高 |
| oobPort | 用默认值 | 非标端口场景真实存在 |
| architecture | 从硬件发现推导 | CreateMsg 有 validValues + handler 写入 |

### ServerPoolVO

| 字段 | 删除派意见 | 保留理由 |
|---|---|---|
| physicalLocation | 用 description/systemTag | PRD 定义 + handler 实际读写 |
| networkTopology | 同上 | PRD 定义 + handler 实际读写 |

### PhysicalServerRoleVO

| 字段 | 删除派意见 | 保留理由 |
|---|---|---|
| schedulingMode | 从 Provider 实时获取 | 需要历史快照，Provider 升级可能改默认值 |

### PhysicalServerHardwareDetailVO

| 字段 | 删除派意见 | 保留理由 |
|---|---|---|
| specification | 与 itemModel 重叠 | 型号和规格语义不同（如 "DDR4" vs "32GB 3200MHz"） |
| firmwareVersion | 放 extraInfo | 固件版本是安全合规审计必需字段 |
| healthStatus | 实时数据不应存静态表 | 硬件发现时刻的健康快照有审计价值 |

### CapacityUsage

| 字段 | 删除派意见 | 保留理由 |
|---|---|---|
| usedCpu/usedMemory | 三个实现都返回空 | Phase 2 马上用，提前定义接口是 SPI 设计惯例 |

### 枚举

| 值 | 删除派意见 | 保留理由 |
|---|---|---|
| SchedulingMode.EXTERNAL_READONLY | 语义模糊 | Container 只读是三层架构核心设计 |
| ProvisionNetworkType.GATEWAY_PXE | 无行为区分代码 | PRD 明确两种装机类型 |
| PhysicalServerPowerStatus (整个) | 零读写 | Phase 5 电源管理基础枚举 |

### API

| 项 | 删除派意见 | 保留理由 |
|---|---|---|
| ScanMsg: concurrency/timeoutPerHost | 应为 GlobalConfig | 大规模扫描时用户需要可调 |
| ProvisionNetworkVO: dhcpRangeGateway | PXE 不需要网关 | GATEWAY_PXE 模式跨子网需要网关 |
| RefVO: lastOpDate | 无更新操作 | ZStack 框架惯例，所有表都有 |

### 空壳 API（10 个文件）

| 文件 | 保留理由 |
|---|---|
| APIPowerOn/Off/ResetPhysicalServerMsg + Event (6) | Phase 5 实现，SDK 定义先行是 TDD 模式 |
| APIDiscoverPhysicalServerHardwareMsg + Event (2) | Phase 5 Should Have |
| APIScanPhysicalServersMsg + Event (2) | Phase 5 实现 |

> **注意**: 这些空壳 API 已在 serviceConfig 中注册路由但 ManagerImpl 中无 handler。
> 调用会收到 unknownMessage 错误。实现 Phase 5 时需添加 handler 或从 serviceConfig 移除路由。
