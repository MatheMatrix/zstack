# BM2 角色适配深度审阅报告

**审阅人**: Baremetal2 Elastic Bare Metal Domain Expert
**日期**: 2026-03-19
**审阅对象**: `docs/architecture-bm2-adapter-2026-03-18.md` v1.0
**对照源**: DB Schema (V4.0.0 ~ V5.5.6)、SDK Inventory 类、`docs/architecture-unified-hardware-2026-03-18.md`、`docs/architecture-review-final-2026-03-18.md`
**方法**: 代码真相 → 用户场景 → 耦合诊断 → 设计对照

---

## 总体评价

BM2 适配设计文档在"只增不改"原则下完成度较高，是四个适配器中最完整的一个。ProvisionNetwork 字段映射准确，弹性/绑定模式的表达思路清晰，迁移 SQL 具有幂等性保证。但从第一性原理出发，经过逐字段对照 DB Schema 和 SDK Inventory，发现 **5 个必须修正的问题** 和 **4 个改进建议**。

---

## 一、ProvisionNetwork 迁移完整性审查

### 1.1 字段映射逐项验证

对照 `V4.0.0__schema.sql` 中 `BareMetal2ProvisionNetworkVO` 的实际 DDL：

| DB 列名 | 设计文档映射 | 是否正确 |
|---------|------------|---------|
| `uuid` | 新生成 UUID (MD5 确定性) | 正确 |
| `name` | `name` | 正确 |
| `description` | `description` | 正确 |
| `zoneUuid` | `zoneUuid` | 正确 |
| `dhcpInterface` | `dhcpInterface` | 正确 |
| `dhcpRangeStartIp` | `dhcpRangeStartIp` | 正确 |
| `dhcpRangeEndIp` | `dhcpRangeEndIp` | 正确 |
| `dhcpRangeNetmask` | `dhcpRangeNetmask` | 正确 |
| `dhcpRangeGateway` | `dhcpRangeGateway` | 正确 |
| **`dhcpRangeNetworkCidr`** | **未映射** | **遗漏 [P1]** |
| `state` | `state` | 正确 |

**[P1] 遗漏: `dhcpRangeNetworkCidr` 字段未迁移**

DB Schema 明确存在 `dhcpRangeNetworkCidr varchar(64) DEFAULT NULL`（V4.0.0 第 700 行），但设计文档第 3.1 节的映射表和第 3.5 节的迁移 SQL 中均未包含此字段。

统一模型的 `PhysicalServerProvisionNetworkVO`（骨架文档第 2.7 节）也未定义此字段。

**影响**：`dhcpRangeNetworkCidr` 是 CIDR 格式的网段标识（如 `192.168.1.0/24`），与 startIp/endIp/netmask 存在冗余关系，但 BM2 代码中可能用此字段做快速匹配或展示。如果迁移时丢失此字段，通过统一 ProvisionNetwork API 查询时将无法返回 CIDR 信息。

**修复建议**：
- 方案 A（推荐）：在 `PhysicalServerProvisionNetworkVO` 中增加 `dhcpRangeNetworkCidr` 字段，迁移 SQL 同步映射。
- 方案 B：不增加字段，在查询时从 startIp + netmask 计算生成（运行时开销低）。

### 1.2 ClusterRef 迁移正确性

对照 `BareMetal2ProvisionNetworkClusterRefVO` DDL：

| DB 列名 | 设计文档映射 | 是否正确 |
|---------|------------|---------|
| `id` (auto increment) | 新生成 UUID (MD5) | 正确 |
| `clusterUuid` | `clusterUuid` | 正确 |
| `networkUuid` | 转换为统一 UUID | 正确 |
| `lastOpDate` | 未映射（迁移 SQL 中不含） | **遗漏 [P3]** |
| `createDate` | `NOW()` | 可接受 |

**[P3] 低优先级遗漏: `lastOpDate` 未迁移**

迁移 SQL 第 496-503 行的 INSERT 语句只有 `(uuid, networkUuid, clusterUuid, createDate)` 四列，缺少 `lastOpDate`。由于统一模型的 `PhysicalServerProvisionNetworkClusterRefVO` 可能没有此字段（骨架文档定义中只有 uuid/networkUuid/clusterUuid/createDate），这属于设计简化而非错误。但如果统一模型的 ClusterRefVO 有 `lastOpDate`，则需要补上。

### 1.3 GatewayProvisionNicVO 处理方案评估

设计文档第 3.2 节决定**不迁移 `BareMetal2GatewayProvisionNicVO`**，理由是 Gateway 属于"装机服务提供方"概念。

**审阅结论：方案正确。**

从 DB Schema 看，`BareMetal2GatewayProvisionNicVO` 的 PK 是 `uuid`，FK 指向 `BareMetal2GatewayVO(uuid)` 且 ON DELETE CASCADE。这意味着 GatewayProvisionNic 的生命周期完全被 Gateway 控制，与 ProvisionNetwork 是松关联（仅通过 `networkUuid` FK）。

统一模型不需要管理 Gateway 基础设施细节，只需知道该 ProvisionNetwork 的类型是 `GATEWAY_PXE`。BM2 内部 Gateway 的 NIC 配置保持原有管理链路。

---

## 二、弹性模式 / 绑定模式表达准确性

### 2.1 两种模式的代码真相

从 SDK 和 DB Schema 验证：

```
CreateBareMetal2InstanceAction:
  chassisUuid        — required=false  → 绑定模式
  chassisOfferingUuid — required=false  → 弹性模式

BareMetal2InstanceVO:
  chassisUuid        — FK → BareMetal2ChassisVO (ON DELETE SET NULL)
  lastChassisUuid    — FK → BareMetal2ChassisVO (ON DELETE SET NULL)
  chassisOfferingUuid — FK → BareMetal2ChassisOfferingVO (ON DELETE SET NULL)
```

弹性模式的标志：`SystemTag.autoReleaseBareMetal2Chassis` 挂在 BareMetal2InstanceVO 上。

**设计文档对此的描述（第 4.1 节）完全正确。**

### 2.2 在统一模型中的表达 — 问题诊断

**[P2] 弹性模式下 `lastChassisUuid` 场景未覆盖**

设计文档第 4.3 节描述弹性模式核心流程时提到"实例再次启动 → 重新分配匹配 Offering 的 Available Chassis（可能是不同的物理机）"。

但未讨论以下场景：

> 弹性实例 I 先绑定 Chassis A（PhysicalServer PA），停机释放后，重新启动绑定到 Chassis B（PhysicalServer PB）。此时 `BareMetal2InstanceVO.lastChassisUuid = A.uuid`，`chassisUuid = B.uuid`。

从统一模型视角，PA 的容量状态应该恢复（Available），PB 的容量状态应该变为已占用（Allocated）。设计文档第 4.2.2 节的容量表描述是正确的，但以下问题未明确：

1. **PhysicalServerRoleVO 不随 Instance 分配变化**（设计文档第 4.3 节第 1 点已说明）— 这是正确的。RoleVO 记录的是 Chassis-PhysicalServer 的长期关系。
2. **但 `getCapacityConsumption()` 依赖 `BareMetal2ChassisVO.status`**（设计文档第 1.4 节）— 这是正确的。Chassis A 的 status 从 Allocated 变回 Available 时，PA 的容量自动恢复。
3. **但容量同步的触发链不完整**：设计文档第 4.2.3 节只描述了 Chassis status 变化时触发 `recalculateCapacity`，未描述是谁调用 `onChassisStatusChanged`。BM2 现有代码中 Chassis status 变化的调用点分散在多个 Flow 中（AllocateChassisFlow、ReleaseChassisFlow 等），需要明确在哪些位置注入钩子。

**修复建议**：补充第 4.2.3 节，列出 BM2 代码中所有 Chassis status 变更的调用点，逐一确认钩子注入位置。建议采用 AOP 或 `BareMetal2ChassisExtensionPoint` 扩展点模式，而非逐点插入代码。

### 2.3 CapacityState 与 Chassis Status 的映射

设计文档中 `PhysicalServerCapacityVO.capacityState` 在 BM2 场景使用 `Ready` 和 `Allocated` 两个值（骨架文档注释、最终审查 INC-4）。

但 BM2 有以下中间状态未映射：

| BareMetal2ChassisStatus | 对应容量语义 | 当前 capacityState 映射 |
|---|---|---|
| `HardwareInfoUnknown` | 不迁移 | 不适用 |
| `IPxeBooting` | 不迁移 | 不适用 |
| `Available` | 容量可用 | `Ready` |
| `Allocated` | 容量已占用 | `Allocated` |
| `IPxeBootFailed` | 不迁移 | 不适用 |

这个映射是合理的。只有 `Available` 和 `Allocated` 两种状态会创建 PhysicalServerVO，所以 capacityState 只需要 `Ready` 和 `Allocated` 两个值。

---

## 三、Chassis 创建/删除同步钩子审查

### 3.1 钩子注入时机 — 正确性确认

设计文档第 2.1 节选择在 Chassis **硬件发现成功后**（status → Available）注入钩子，理由是：
1. 发现前 serialNumber 未知
2. 发现失败的 Chassis 不应纳入统一管理

**审阅结论：时机选择正确。** 从 DB Schema 看，`BareMetal2ChassisVO.chassisOfferingUuid` 是 `DEFAULT NULL`，只有在硬件发现完成后由 `BareMetal2ChassisHardwareInfoSyncer` 填充（V4.3.12 添加 `provisionType` 时同步更新）。在此之前，CPU/内存/磁盘容量信息也不可用。

### 3.2 IPMI 信息映射 — 遗漏发现

**[P4] 遗漏: BareMetal2IpmiChassisVO 的 UNIQUE 约束未在统一模型中体现**

DB Schema（V4.0.0 第 817 行）：
```sql
CONSTRAINT `ukBareMetal2IpmiChassisVO` UNIQUE (`ipmiAddress`, `ipmiPort`)
```

这意味着同一 IPMI 地址 + 端口的组合全局唯一。但统一模型的 `PhysicalServerAO` 只有 `oobAddress` 上的 `@Index`，没有 `UNIQUE(oobAddress, oobPort)` 约束。

**影响**：如果两个不同角色（如 BM2 Chassis 和 BM1 Chassis）的同一 IPMI 地址被分别注册，PhysicalServerVO 层面不会报错，但可能导致两条 PhysicalServerVO 记录指向同一台物理机。

**修复建议**：
- 方案 A：在 `PhysicalServerAO` 上添加 `UNIQUE(zoneUuid, oobAddress, oobPort)` 约束（最严格）。
- 方案 B：依赖 `matchExistingServer()` 的降级匹配（用 oobAddress 匹配到已有 PhysicalServer）防止重复。当前设计已有此匹配逻辑（第 1.8 节第 2 步），但匹配逻辑是 best-effort，不是约束。

建议采用方案 A + B 双重保障。

### 3.3 serialNumber 获取 — 潜在问题

设计文档第 2.3 节描述了 serialNumber 的三个来源，并提供了降级方案（`MD5(zoneUuid + ipmiAddress)`）。

**问题**：降级方案中使用 `MD5(zoneUuid + ipmiAddress)` 生成替代标识。但 `PhysicalServerVO` 上有 `UNIQUE(zoneUuid, serialNumber)` 约束。如果同一台物理机的 IPMI 地址变更（例如 BMC 重置后重新配置 IP），旧的 PhysicalServerVO 记录将因 serialNumber（基于旧 IP 生成）与新 Chassis 的 serialNumber（基于新 IP 生成）不同而无法匹配，导致创建重复的 PhysicalServerVO。

**影响**：低频场景，但会导致同一台物理机在统一视图中出现两条记录。

**修复建议**：降级 serialNumber 生成策略应优先使用物理地址（如 BMC MAC 地址）而非 IP 地址。如果 BMC MAC 不可用，在 `matchExistingServer()` 中增加"旧 serialNumber + 新 oobAddress"的交叉匹配逻辑。

---

## 四、迁移 SQL 正确性审查

### 4.1 PhysicalServerEO 迁移 SQL (第 5.2.1 节)

逐列对照：

| 迁移 SQL 列 | 数据来源 | 正确性 |
|---|---|---|
| `uuid` | `MD5('bm2-chassis-' + c.uuid)` | 正确，确定性 |
| `zoneUuid` | `c.zoneUuid` | 正确 |
| `poolUuid` | `NULL` | 正确（注释说明需后续分配） |
| `name` | `c.name` | 正确 |
| `managementIp` | `ipmi.ipmiAddress` | **语义需确认 [P5]** |
| `serialNumber` | `NULL` | 需从硬件发现数据提取 |
| `architecture` | `o.architecture` | 正确 |
| `manufacturer` | `NULL` | 需从硬件发现数据提取 |
| `model` | `NULL` | 需从硬件发现数据提取 |
| `state` | `CASE c.state ...` | 正确 |
| `status` | `CASE c.status ...` | 正确 |
| `powerStatus` | `CASE c.powerStatus ...` | 正确 |
| `oobManagementType` | `'IPMI'` | 正确（但仅覆盖 IPMI 类型） |
| `oobAddress` | `ipmi.ipmiAddress` | 正确 |
| `oobPort` | `ipmi.ipmiPort` | 正确 |
| `oobUsername` | `ipmi.ipmiUsername` | 正确 |
| `oobPassword` | `ipmi.ipmiPassword` | **加密兼容性需确认 [I1]** |

**[P5] managementIp 设置为 ipmiAddress 的语义问题**

`PhysicalServerAO.managementIp` 在 KVM 场景是 Host 的管理 IP（SSH 地址），在 BM2 场景被设置为 IPMI 地址。这两个概念不同：
- KVM 的 managementIp 是操作系统层的 IP，可以 SSH 连接
- BM2 的 IPMI 地址是 BMC 板的 IP，只能发送 IPMI 命令

将 IPMI 地址填入 `managementIp` 会导致统一查询时产生歧义：用户看到的 `managementIp` 可能是操作系统 IP（KVM）也可能是 BMC IP（BM2），语义不一致。

**修复建议**：BM2 的 `managementIp` 应设为 NULL 或设为 BM2 Instance 运行时获取的操作系统 IP（如果有的话）。IPMI 地址已通过 `oobAddress` 字段表达，不应重复填入 `managementIp`。

**[I1] 改进建议: oobPassword 加密兼容性**

`BareMetal2IpmiChassisVO.ipmiPassword` 在 BM2 中使用 `@EncryptColumn` 注解加密存储。迁移 SQL 直接 `SELECT ipmiPassword` 后 INSERT 到 `PhysicalServerEO.oobPassword`。如果两边使用相同的加密方案（`PasswordConverter`），则迁移无问题。如果不同（BM2 用 AES-128 而统一模型用 AES-256），需要在迁移脚本中先解密再加密。

**建议**：在迁移文档中明确声明两边的加密方案相同，或增加解密-加密步骤。

### 4.2 LEFT JOIN BareMetal2IpmiChassisVO 的覆盖度

迁移 SQL 使用 `LEFT JOIN BareMetal2IpmiChassisVO ipmi ON c.uuid = ipmi.uuid`。

对于非 IPMI 类型的 Chassis（当前仅有 IPMI 类型，但未来可扩展），`ipmi.*` 字段将全部为 NULL。此时：
- `managementIp = NULL` — 可接受
- `oobAddress = NULL` — 可接受（第 1.8 节 Q5 已说明）
- `oobManagementType = 'IPMI'` — **错误**，非 IPMI 类型不应标记为 IPMI

**修复建议**：将 `oobManagementType` 从硬编码 `'IPMI'` 改为 `CASE WHEN ipmi.uuid IS NOT NULL THEN 'IPMI' ELSE NULL END`。

### 4.3 ProvisionNetwork 迁移 SQL 遗漏

如第 1.1 节所述，迁移 SQL 的 SELECT 列表缺少 `dhcpRangeNetworkCidr`。

### 4.4 PhysicalServerCapacityVO 迁移的磁盘容量问题

迁移 SQL 第 734 行：`0, 0, -- totalDisk/availableDisk: 需从磁盘数据汇总`。

BM2 的磁盘信息存储在 `BareMetal2ChassisDiskVO` 中（`diskSize bigint unsigned`，V4.0.0 第 803 行），每个 Chassis 可能有多块磁盘。迁移时 `totalDisk` 应为 `SUM(diskSize)`。

**修复建议**：迁移 SQL 中用子查询汇总磁盘容量：

```sql
(SELECT COALESCE(SUM(d.diskSize), 0)
 FROM BareMetal2ChassisDiskVO d
 WHERE d.chassisUuid = c.uuid) AS totalDisk
```

同时 `availableDisk` 应与 `availableCpu`/`availableMemory` 的逻辑对齐（Available 时 = totalDisk，Allocated 时 = 0）。

---

## 五、硬件发现信息完整性

### 5.1 BM2 硬件信息 VO 全景

从 DB Schema 梳理 BM2 的完整硬件信息链：

```
BareMetal2ChassisVO
  ├── BareMetal2ChassisNicVO         (V4.0.0, V4.4.24 + nicName, V5.5.6 + isPrimaryProvisionNic)
  ├── BareMetal2ChassisDiskVO        (V4.0.0, V4.3.12 + wwn)
  ├── BareMetal2ChassisPciDeviceVO   (V5.1.8)
  │   └── BareMetal2ChassisGpuDeviceVO (V5.1.8, V5.2.0 memory/power 改 bigint)
  └── BareMetal2ChassisOfferingVO    (V4.0.0, V4.3.12 + provisionType)
```

### 5.2 与 PhysicalServerHardwareDetailVO 的映射

设计文档第 5.2.1 节的 `PhysicalServerHardwareInfoVO` 迁移 SQL 使用 `BareMetal2ChassisOfferingVO` 获取 CPU/内存汇总信息，用子查询获取 NIC 数量。但以下信息未迁移：

| BM2 硬件数据 | PhysicalServerHardwareDetailVO 映射 | 状态 |
|---|---|---|
| `BareMetal2ChassisNicVO` (mac, speed, nicName, isPrimaryProvisionNic) | `HardwareDetailType.NIC` | **未设计** |
| `BareMetal2ChassisDiskVO` (type, diskSize, wwn) | `HardwareDetailType.DISK` | **未设计** |
| `BareMetal2ChassisPciDeviceVO` (type, vendorId, deviceId, ...) | `HardwareDetailType.GPU` 或新类型 | **未设计** |
| `BareMetal2ChassisGpuDeviceVO` (serialNumber, memory, power) | `HardwareDetailType.GPU` | **未设计** |

**[I2] 改进建议: 补充 HardwareDetailVO 的迁移设计**

当前设计只迁移了 `PhysicalServerHardwareInfoVO`（汇总表），未设计 `PhysicalServerHardwareDetailVO`（明细表）的迁移。BM2 的 NIC、Disk、PCI、GPU 明细数据非常丰富，是统一硬件管理的重要信息源。

建议在 Phase 2 中补充以下迁移：
- `BareMetal2ChassisNicVO` → `PhysicalServerHardwareDetailVO (detailType=NIC)`
- `BareMetal2ChassisDiskVO` → `PhysicalServerHardwareDetailVO (detailType=DISK)`
- `BareMetal2ChassisPciDeviceVO + GpuDeviceVO` → `PhysicalServerHardwareDetailVO (detailType=GPU)`

---

## 六、Bonding 约束的分配引擎接入审查

### 6.1 设计方案评估

设计文档 Q4（第 6 章）提出通过 `ServerAllocatorFilterExtensionPoint` 注入 `Bm2BondingConstraintFilter`。

从 DB Schema 看 Bonding 的关系链：

```
BareMetal2BondingVO (uuid, name, slaves, opts, chassisUuid, mode)
  ↓
BareMetal2BondingNicRefVO (nicUuid → VmNicVO, instanceUuid → BM2InstanceVO,
                           bondingUuid → BondingVO, provisionNicUuid → ProvisionNicVO)
```

Bonding 约束的核心逻辑：如果 Instance 已有 Bonding 配置，分配时必须选择同一 Chassis（因为 Bonding 的物理网卡是 Chassis 级别的资源）。

**审阅结论：方案合理但实现细节需补充。**

问题：`Bm2BondingConstraintFilter.filterServerCandidates()` 接收的是 `List<PhysicalServerVO>`，但 Bonding 约束需要从 Instance UUID 反查 Bonding → Chassis → PhysicalServer。过滤器需要通过 `spec.getOriginalMessage()` 获取 `instanceUuid`，但 `AllocateServerMsg` 中没有 `originalMessage` 字段 — 只有 `ServerAllocatorSpec.extraData` 可以承载此信息。

**修复建议**：在 `AllocateServerMsg` 或 `ServerAllocatorSpec` 中增加 `originalMessage` 字段（NeedReplyMessage 类型），让 BM2 的分配请求可以透传 `AllocateBareMetal2ChassisMsg`。或者使用 `extraData` Map 传递 `instanceUuid`。

### 6.2 BondingNicRefVO 的 provisionNicUuid 关联

V4.7.13 新增了 `BareMetal2BondingNicRefVO.provisionNicUuid` FK 到 `BareMetal2InstanceProvisionNicVO`。这表示 Bonding 不仅关联业务网卡（VmNicVO），还关联装机网卡（ProvisionNicVO）。

**影响**：统一分配引擎只关注 Chassis 级别的约束，ProvisionNic 级别的 Bonding 约束仍在 BM2 内部处理，不需要暴露到统一模型。当前设计在这一点上是正确的。

---

## 七、电源管理委托设计审查

### 7.1 通过 API 消息委托 vs 直接调用

设计文档 Q2（第 6 章）提出 BM2 的 `PowerManageable` 实现通过发送 `APIPowerOnBareMetal2ChassisMsg` 委托给 BM2 现有流程。

**问题**：使用 `APIPowerOnBareMetal2ChassisMsg`（API 消息）意味着需要经过 API 鉴权、ApiMessageInterceptor 校验等完整 API 调用链路。统一电源管理 API 已经完成了鉴权，再走一次 BM2 API 鉴权是重复且可能冲突的（权限模型不同）。

**修复建议**：使用内部消息（`PowerOnBareMetal2ChassisMsg`）而非 API 消息（`APIPowerOnBareMetal2ChassisMsg`）。BM2 内部必然有对应的内部消息处理器。这样避免重复鉴权，也减少 API 调用开销。

### 7.2 电源状态同步

`PowerManageable.getPowerStatus()` 的 BM2 实现需要调用 BM2 Gateway Agent 查询 IPMI 电源状态。查询结果需要同时更新：
1. `BareMetal2ChassisVO.powerStatus` — BM2 内部状态
2. `PhysicalServerAO.powerStatus` — 统一模型状态

设计文档未讨论两者的一致性保证。

**修复建议**：在 `getPowerStatus()` 的回调中同时更新两个 VO 的 powerStatus。或者，统一模型的 powerStatus 不独立存储，而是通过 `getInventory()` 实时查询 BM2 的 powerStatus（但这会增加查询延迟）。

---

## 八、与最终审查报告 (architecture-review-final) 的交叉验证

### 8.1 INC-3 (RoleMatchContext oobAddress 字段) 的影响

最终审查报告 INC-3 指出 BM2 将 `ipmiAddress` 填入 `managementIp` 做降级匹配，与 BM1 用 `oobAddress` 字段不一致。

BM2 适配文档第 1.8 节的实现是用 `context.getManagementIp()` 查 `PhysicalServerVO.oobAddress`，这在语义上是错误的（managementIp 匹配应该查 managementIp 字段，不是 oobAddress 字段）。

**修复建议**：采纳 INC-3 的建议，在 `RoleMatchContext` 中新增 `oobAddress` 字段，BM2 填入 `ipmiAddress` 到 `context.oobAddress`（而非 `context.managementIp`），匹配时查 `PhysicalServerVO.oobAddress`。

### 8.2 INC-4 (capacityState 枚举化) 的影响

BM2 适配文档使用了 `capacityState = "Allocated"` 状态值（未明确出现在代码中，但在 `getCapacityConsumption` 的逻辑中隐含）。最终审查要求枚举化。

BM2 适配文档需要明确：在 Chassis status = Allocated 时，`PhysicalServerCapacityVO.capacityState` 设置为 `CapacityState.Allocated`。在 Chassis status = Available 时，设置为 `CapacityState.Ready`。

---

## 九、问题汇总与优先级

### 必须修正 (Blocker / P1-P2)

| 编号 | 问题 | 章节 | 优先级 |
|------|------|------|--------|
| P1 | ProvisionNetwork 迁移遗漏 `dhcpRangeNetworkCidr` 字段 | 1.1 | P1 |
| P2 | 弹性模式 Chassis status 变更钩子注入点未列举 | 2.2 | P2 |
| P4 | OOB 地址唯一性约束缺失 | 3.2 | P2 |
| P5 | managementIp 被错误设置为 IPMI 地址 | 4.1 | P1 |
| -- | 电源管理应使用内部消息而非 API 消息 | 7.1 | P2 |

### 应当修正 (Important / P3)

| 编号 | 问题 | 章节 | 优先级 |
|------|------|------|--------|
| P3 | ClusterRef 迁移缺少 lastOpDate | 1.2 | P3 |
| -- | 迁移 SQL oobManagementType 硬编码为 'IPMI' | 4.2 | P3 |
| -- | 迁移 SQL totalDisk 为 0，应从 ChassisDiskVO 汇总 | 4.4 | P3 |
| -- | RoleMatchContext 字段不一致（managementIp vs oobAddress） | 8.1 | P3 |

### 改进建议 (Nice to have / P4)

| 编号 | 建议 | 章节 |
|------|------|------|
| I1 | oobPassword 加密兼容性确认 | 4.1 |
| I2 | 补充 HardwareDetailVO 明细迁移（NIC/Disk/PCI/GPU） | 5.2 |
| -- | serialNumber 降级策略改用物理地址 | 3.3 |
| -- | 电源状态双 VO 一致性保证 | 7.2 |

---

## 十、结论

BM2 适配设计文档的核心架构决策（SchedulingMode=INTERNAL_EXCLUSIVE、钩子注入时机选在硬件发现后、ProvisionNetwork 以 BM2 为主参考模型、弹性/绑定通过 ChassisStatus 驱动容量变化）均正确。

但在工程细节层面存在 5 个 P1/P2 问题需要修正后方可进入实现阶段。其中最关键的两个是：
1. **P5 (managementIp 语义污染)** — 会导致统一查询时 managementIp 字段对不同角色含义不同，用户困惑。
2. **P1 (dhcpRangeNetworkCidr 遗漏)** — ProvisionNetwork 作为 BM2 的主参考模型，字段遗漏会破坏"BM2 无信息损失"的设计承诺。

修正上述问题后，BM2 适配设计可进入实现阶段。
