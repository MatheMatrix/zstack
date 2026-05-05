# Phase 1 详细设计评审意见 -- Baremetal V1 架构师

**评审人**: Baremetal V1 Domain Expert
**评审文档**: PHASE1_Detailed_Design.md v1.1
**评审结论**: NEEDS_MODIFICATION

---

## 一、PhysicalServerVO 属性覆盖度

### 已覆盖属性（优点）
- name, description, zoneUuid 一致
- oobAddress/oobPort/oobUsername/oobPassword 合理泛化自 ipmiAddress 等

### 缺失 1: clusterUuid [P0]
`BaremetalChassisVO` 第49行明确定义 `clusterUuid`，关联 PXE Server、级联删除、Instance 继承 Cluster/Zone 都依赖此字段。ServerPool 不等价于 Cluster。

**建议**: PhysicalServerAO 增加 clusterUuid 或在 PhysicalServerRoleVO 中增加 clusterUuid 关联。

### 缺失 2: pxeServerUuid [P1]
`BaremetalChassisVO` 第53行的 PXE Server 外键关系是硬件发现的关键路径。PXE Server UUID 是 Chassis 身份验证的一部分。

**建议**: 在 PhysicalServerRoleVO 或 Baremetal 扩展表中维持 PXE Server 关联。

---

## 二、OOB 管理抽象

### 优点
将 IPMI 特有字段泛化为 oobManagementType 是合理的抽象。

### 问题 1: 缺少 Boot Mode [P1]
通过 SystemTag 标记的 Legacy/EFI Boot 模式是物理服务器固有属性，应在 ServerHardwareInfoVO 中显式建模（`bootMode` 字段）。

### 问题 2: OOB 操作接口未定义 [P1]
现有 `BaremetalUtils.java` 实现了 powerOn/powerOff/powerReset/setBootDev/status 等 IPMI 操作。Phase 1 应定义 `OobManagementStrategy` SPI 接口。

---

## 三、PhysicalServerRoleVO 角色绑定

### 问题 1: 独占角色互斥缺少强制保障 [P2]
唯一约束 `(serverUuid, roleType)` 不防止独占角色和共享角色共存。

**建议**: 增加业务逻辑层互斥检查。

### 问题 2: 角色匹配应使用 (oobAddress, oobPort) 组合 [P1]
现有 Chassis 通过 `ipmiAddress + ipmiPort` 唯一标识，仅用 `oobAddress` 匹配不够。

---

## 四、ServerCapacityVO 独占模式

### 基本合理，但有边界问题
Chassis 创建后可能长期处于 `HWInfoUnknown`（未完成硬件发现），容量全为0，独占清零无意义。

**建议**: `ServerCapacityUpdater.reserve()` 增加对 `CapacityState.Initialized` 的特殊处理。独占角色在 Initialized 状态下应直接允许分配。

---

## 五、ServerHardwareInfoVO vs BaremetalHardwareInfoVO

### 架构差异严重 [P1]
| 特性 | BaremetalHardwareInfoVO | ServerHardwareInfoVO |
|------|----------------------|---------------------|
| 关系 | 1:N (一台 Chassis 多条记录) | 1:1 |
| 数据格式 | `type` + `content` (JSON) | 结构化字段 |
| 类型区分 | basic, nic, disk, pxeserver | 无类型区分 |

`ServerHardwareInfoVO` 的 `nicCount` 无法表达每块网卡的 MAC 地址、PXE 启动标记等详情。

**建议**: 增加 `rawHardwareDetail` 字段（TEXT），或保留 1:N 详情子表。

---

## 六、硬件发现流程对比

### 状态机不匹配 Baremetal 发现流程 [P0]

| BaremetalChassisStatus | PhysicalServerStatus 对应 |
|----------------------|-------------------------|
| HWInfoUnknown | Unknown (部分覆盖) |
| PxeBooting | **无对应** |
| PxeBootFailed | **语义不匹配** |
| Available | **语义不匹配** |
| Allocated | **需要额外标记** |

**建议方案 A**: 增加 Discovering/DiscoveryFailed 状态
**建议方案 B（推荐）**: 在 PhysicalServerRoleVO 中增加 `roleStatus` 字段，让各角色自管理运行状态

---

## 七、Baremetal 特有遗漏

1. **BaremetalInstanceVO**: Chassis-Instance 二层架构未覆盖，应保持为角色层独立实体
2. **Bonding 配置**: BaremetalBondingVO 存储 NIC Bonding，完全未覆盖
3. **PreconfigurationTemplate**: kickstart/preseed 模板管理未覆盖
4. **License 检查**: Baremetal 专属的 addon license 体系需要集成

---

## 八、迁移风险

### 高风险
- 级联删除一致性：删除 PhysicalServerVO 不应 cascade 到 BaremetalChassisVO
- 双写数据一致性：IPMI 地址等冗余字段的同步

### 建议
- PhysicalServerVO 删除语义：仅解除 Role 映射
- 使用 EventFacade 替代新增 ExtensionPoint，减少对 premium 代码侵入
- Phase 1 阶段 PhysicalServerVO 是 BaremetalChassisVO 的只读投影
