# Phase 1 详细设计评审意见 -- Baremetal2 架构师

**评审人**: Baremetal2 Domain Expert
**评审文档**: PHASE1_Detailed_Design.md v1.1
**评审结论**: NEEDS_MODIFICATION (4 P0)

---

## 一、PhysicalServerVO 属性覆盖度 -- 4 项重要遗漏

| 遗漏属性 | 严重性 | 证据 |
|---------|--------|------|
| `powerStatus` (三态电源状态) | P0 | `BareMetal2ChassisPowerStatus.java` -- POWER_ON/POWER_OFF/POWER_UNKNOWN 与连接状态是正交维度 |
| `clusterUuid` (集群关联) | P0 | `BareMetal2ChassisAO.java` 第32-33行 -- 分配和 architecture 校验都依赖此字段 |
| `provisionType` (部署模式) | P1 | `BareMetal2ProvisionType.java` -- Remote/Local/Direct 三种模式决定整个部署流程走向 |
| `chassisOfferingUuid` (规格模板) | P1 | `BareMetal2ChassisHardwareInfoSyncer.java` 第141-159行 -- 硬件发现后自动创建规格模板，是弹性分配核心 |

---

## 二、PhysicalServerRoleVO 角色绑定

### 基本合理，但有语义陷阱
baremetal2 的 `BareMetal2ChassisVO` 继承 `ResourceVO` 而非 `HostVO`，chassis 不是"角色"而是物理服务器本身。

`PhysicalServerRoleVO.roleUuid` 不应设置指向特定 VO 的 FK 约束（当前设计已正确处理）。但角色关联仅依赖 oobAddress 对非 IPMI 类型 chassis 不适用。

---

## 三、ServerCapacityVO 独占模式

### 方向正确，但细节需调整
独占分配清零方向与 baremetal2 整机分配语义一致。但 baremetal2 的分配完全不使用 CPU/内存参数，而是基于 `chassisOfferingUuid`、`clusterUuid`、`status=Available` 过滤。

**建议**: `AllocateServerMsg` 中 `requiredCpu`/`requiredMemory` 应标记为 optional。

---

## 四、AllocateServerMsg 适配性 -- 不能直接适配

baremetal2 的 `AllocateBareMetal2ChassisMsg` 包含 6 个分配参数，其中缺失：
- `requiredClusterUuids` (List) -- 多集群候选
- `chassisOfferingUuid` -- 规格筛选
- `requiredChassisDiskUuid` -- 磁盘约束
- `avoidChassisUuids` -- 回避列表

**建议**: AllocateServerMsg 增加 `avoidServerUuids`、`hardwareSpecUuid`、`consumerUuid`；将 `requiredCpu/Memory` 改为 optional。

---

## 五、硬件发现流程差异

baremetal2 硬件发现需要 PXE 物理重启机器，发现内容包括详细的 NIC/Disk/PCI/GPU 子资源列表（第521-567行的 BareMetal2ChassisHardwareInfo），远超 `ServerHardwareInfoVO` 的汇总级信息。

---

## 六、被完全忽略的 baremetal2 特有概念

1. **Gateway** (部署网关): `BareMetal2GatewayVO` 继承 KVMHostVO，是 PXE/DHCP/TFTP 服务载体，有独立 N:N cluster 关联和分配策略
2. **Bonding** (网卡绑定): `BareMetal2BondingVO` 影响 chassis 分配约束
3. **弹性 vs 绑定双模式**: 通过指定 `chassisUuid`(绑定) 或 `chassisOfferingUuid`(弹性) 决定停机时是否释放 chassis
4. **BareMetal2InstanceVO 继承 VmInstanceVO**: instance 在 VM 框架内运行，通过 `setHostUuid(gatewayUuid)` 桥接

---

## 七、迁移风险

### 高风险
- 角色关联时机：应在硬件发现成功后（status=Available）而非 chassis 创建时触发
- 独占容量清零与弹性释放的语义冲突

### 中风险
- 双写一致性、状态映射信息损失、分配路径并行冲突

### 低风险
- API 兼容性、数据模型独立性、编译兼容性

---

## 八、P0 必须修改项

1. PhysicalServerAO 增加 `powerStatus` 字段
2. PhysicalServerAO 增加 `clusterUuid` 字段
3. AllocateServerMsg 扩展参数 + requiredCpu/Memory 改为 optional
4. 角色关联时机从 chassis 创建时改为硬件发现成功后
