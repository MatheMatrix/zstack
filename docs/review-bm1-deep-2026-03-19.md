# BM1 角色适配设计深度审阅

**审阅人**: Baremetal V1 领域专家
**审阅文档**: architecture-bm1-adapter-2026-03-18.md v1.0
**审阅方法**: 代码优先 -- 先从 SQL Schema 和模块分析报告重建 BM1 真实架构，再逐章对照设计文档
**审阅结论**: CONDITIONAL_APPROVAL (有 3 个 P0 问题需修正后方可落地)
**日期**: 2026-03-19

---

## 第一部分：用户场景走查

### 场景：管理员注册一台裸金属 Chassis 的完整流程

从代码（SQL schema + ANALYSIS_baremetal_module.md + i18n 引用）还原的真实流程：

```
管理员注册 Chassis 完整流程：

1. 前置条件准备
   ├── 创建 Zone
   ├── 创建 Cluster (hypervisorType=baremetal)
   ├── 部署 PxeServer (APICreateBaremetalPxeServerMsg)
   │   ├── 提供 hostname, sshUsername, sshPassword, sshPort
   │   ├── 提供 dhcpInterface
   │   ├── PxeServerBase 通过 SSH 连接到 PxeServer 主机
   │   ├── 部署 dnsmasq + tftp + HTTP 服务
   │   └── 关联 PxeServer 到 Cluster (APIAttachBaremetalPxeServerToClusterMsg)
   │       └── 创建 BaremetalPxeServerClusterRefVO 记录
   └── 准备 IPMI 网络可达性

2. 注册 Chassis (APICreateBaremetalChassisMsg)
   ├── 验证: ipmiAddress + ipmiPort 唯一 (ukBaremetalChassisVO)
   ├── 验证: clusterUuid 有效且 hypervisorType == baremetal
   ├── 验证: pxeServerUuid 有效且已关联到同一 Cluster
   ├── 持久化 BaremetalChassisVO
   │   ├── uuid, name, description
   │   ├── ipmiAddress, ipmiPort, ipmiUsername, ipmiPassword
   │   ├── zoneUuid, clusterUuid, pxeServerUuid
   │   ├── state = Enabled
   │   └── status = HWInfoUnknown
   ├── 注册到 ResourceVO (resourceType=BaremetalChassisVO)
   └── 返回 BaremetalChassisInventory

3. 硬件发现 (APIInspectBaremetalChassisMsg)
   ├── IPMI Set Boot Device → PXE (ipmitool chassis bootdev pxe)
   ├── IPMI Power Reset (ipmitool power reset)
   ├── status → PxeBooting
   ├── 物理机 PXE 启动 → 从 PxeServer 获取发现镜像
   ├── 发现 Agent 运行 → 采集 CPU/Memory/Disk/NIC 信息
   ├── Agent POST /baremetal/chassis/sendhardwareinfo 回调
   │   ├── 写入 BaremetalHardwareInfoVO (type=basic, content=JSON)
   │   ├── 写入 BaremetalHardwareInfoVO (type=nic, content=JSON)
   │   ├── 写入 BaremetalHardwareInfoVO (type=disk, content=JSON)
   │   └── status → Available
   └── 如果 PXE 启动失败: status → PxeBootFailed

4. 分配 Chassis 给 Instance (APICreateBaremetalInstanceMsg)
   ├── 选择 Available 状态的 Chassis
   ├── Chassis status → Allocated
   ├── 创建 BaremetalInstanceVO
   │   ├── chassisUuid, imageUuid, managementIp
   │   ├── state = Created, status = Unprovisioned
   │   └── pxeServerUuid (从 Chassis 继承)
   └── 创建 BaremetalNicVO (含 PXE NIC 标记)

5. 装机 (启动 Instance → PXE 装机)
   ├── IPMI Set Boot Device → PXE
   ├── IPMI Power On
   ├── Instance status → Provisioning
   ├── kickstart/preseed 自动安装
   ├── 安装完成回调
   │   ├── Instance status → Provisioned
   │   └── Instance state → Running
   └── IPMI Set Boot Device → Disk (下次从硬盘启动)
```

---

## 第二部分：现有架构诊断 (从代码得出)

### 2.1 BM1 核心数据模型 (从 SQL Schema 还原)

最终态 BaremetalChassisVO (V2.0.0 + V2.1.0 + V2.6.0 + V3.1.1 累积):

| 字段 | 类型 | 来源版本 | 说明 |
|------|------|---------|------|
| uuid | varchar(32) PK | V2.0.0 | |
| name | varchar(255) | V2.1.0 | 新增 |
| description | varchar(2048) | V2.1.0 | 新增 |
| ipmiAddress | varchar(32) | V2.0.0 | 原有 UNIQUE, V2.1.0 改为组合唯一 |
| ipmiPort | int | V2.1.0 新增, V2.6.0 改为 nullable | 与 ipmiAddress 组合唯一 |
| ipmiUsername | varchar(255) | V2.0.0 | |
| ipmiPassword | varchar(255) | V2.0.0 | 加密存储 |
| state | varchar(32) | V2.6.0 | Enabled/Disabled |
| status | varchar(32) | V2.1.0 | HWInfoUnknown/PxeBooting/PxeBootFailed/Available/Allocated |
| zoneUuid | varchar(32) FK→ZoneEO | V2.6.0 | ON DELETE RESTRICT |
| clusterUuid | varchar(32) FK→ClusterEO | V2.6.0 | ON DELETE RESTRICT |
| pxeServerUuid | varchar(32) FK→PxeServerVO | V3.1.1 | ON DELETE SET NULL |

**关键观察**:
1. **ipmiAddress+ipmiPort 是业务唯一键** (V2.1.0 添加 ukBaremetalChassisVO)
2. **clusterUuid 是硬绑定** -- FK ON DELETE RESTRICT, 意味着必须先清理 Chassis 才能删除 Cluster
3. **pxeServerUuid 是软绑定** -- FK ON DELETE SET NULL, PXE Server 删除不影响 Chassis
4. **无 serialNumber 字段** -- 序列号在 BaremetalHardwareInfoVO (type=basic) 的 JSON content 中

### 2.2 BM1 与 BM2 的结构性差异

| 维度 | BM1 (BaremetalChassisVO) | BM2 (BareMetal2ChassisVO) |
|------|------------------------|------------------------|
| IPMI 存储 | 字段直接在 ChassisVO 上 | 独立子表 BareMetal2IpmiChassisVO (共享UUID) |
| 硬件信息 | 1:N BaremetalHardwareInfoVO (JSON) | 独立子表 ChassisNicVO + ChassisDiskVO |
| 实例关系 | ChassisVO ← FK ← InstanceVO | InstanceVO → FK → ChassisVO (含 lastChassisUuid) |
| PXE 网络 | PxeServerVO (独立实体+ClusterRef) | ProvisionNetworkVO (独立+ClusterRef) |
| 集群关系 | 直接 clusterUuid FK | 直接 clusterUuid FK |
| 装机模式 | 独立 PxeServer SSH 管控 dnsmasq | Gateway (KVM Host) 代理 |
| ChassisOffering | 无 | 有 (cpu/memory/arch 模板) |

**核心差异**: BM1 的 IPMI 凭据与 Chassis 紧耦合 (同表), BM2 已经做了分离。这使得 BM1 的 IPMI 映射更直接但也更脆弱 -- 修改 IPMI 地址需要 UPDATE ChassisVO 本体。

### 2.3 PXE Server 的特殊复杂性

BM1 的 PxeServer 是一个**有状态的外部物理服务器**, 不仅是网络配置:

```
BaremetalPxeServerVO 的完整字段 (V2.0.0 + V2.1.0 + V3.1.1):
├── 网络配置: dhcpInterface, dhcpRangeBegin/End/Netmask, dhcpInterfaceAddress
├── SSH 管控: hostname, sshUsername, sshPassword, sshPort
├── 存储管理: storagePath, totalCapacity, availableCapacity
├── 元数据: name, description, state, status, zoneUuid
└── 镜像缓存: BaremetalImageCacheVO (1:N, pxeServerUuid FK)
```

这比 BM2 的 ProvisionNetwork 重得多 -- BM2 的 ProvisionNetwork 只是网络参数, BM1 的 PxeServer 是一个需要 SSH 管理的实体。

---

## 第三部分：设计文档逐章评议

### 3.1 第 1 章: Bm1PhysicalServerRoleProvider -- 评价: 良好, 有 1 个 P1 问题

**优点**:
- getRoleType/getSchedulingMode 定义清晰
- INTERNAL_EXCLUSIVE 模式的容量报告逻辑正确: Allocated → 全量消耗, 其他 → 零消耗
- onPhysicalServerCreated 为 no-op 的决策合理 (数据流方向正确)
- onPhysicalServerDeleted 只标记 Stale 不删 Chassis 的决策正确

**P1: matchExistingServer 缺少 ipmiPort 组合匹配**

设计文档 Q4 回答说"仅用 oobAddress 匹配即可", 理由是 "同一 BMC IP 不同端口指向同一物理机"。但从 SQL schema 看:

```sql
-- V2.1.0
ALTER TABLE BaremetalChassisVO ADD CONSTRAINT ukBaremetalChassisVO
    UNIQUE (ipmiAddress, ipmiPort);
```

BM1 的业务唯一键是 **(ipmiAddress, ipmiPort)**, 不是 ipmiAddress 单字段。虽然 ipmiPort 在实践中几乎总是 623, 但数据库约束表明 BM1 允许同一 IP 不同端口的场景 (例如 BMC 代理/端口转发场景)。设计文档应至少在匹配逻辑中将 oobPort 作为可选的附加过滤条件:

```java
// 如果 oobPort 存在且非默认值, 添加 Port 过滤
if (context.getOobPort() != null && context.getOobPort() != 623) {
    query.eq(PhysicalServerVO_.oobPort, context.getOobPort());
}
```

**P2: getInventory 缺少 managementIp**

Bm1RoleInventory 包含 chassisState/chassisStatus/pxeServerUuid, 但缺少 BM1 实际使用中重要的 managementIp (来自 BaremetalInstanceVO)。虽然设计文档明确说"PhysicalServer 层不感知 Instance", 但从查询使用者的角度看, 缺少这个信息会降低 getInventory 的实用性。建议在 Bm1RoleInventory 中可选地携带 Instance 的 managementIp (如果存在)。

### 3.2 第 2 章: 生命周期同步钩子 -- 评价: 良好, 有 1 个 P0 问题

**优点**:
- ExtensionPoint 方案选型正确, 优于 EventFacade -- BM1 模块本身已大量使用此模式
- 三个扩展点方法 (afterCreate/beforeDelete/afterUpdateIpmi) 覆盖了核心场景
- 同步器放在 server/ 模块而非 premium/baremetal/ 中, 方向正确 (避免反向依赖)

**P0-1: afterCreateBaremetalChassis 的事务边界问题**

设计文档说 "在事务内调用, 实现方可参与同一事务"。但从 BM1 代码引用路径 (BaremetalChassisManagerImpl.createChassis) 看, BM1 的 Chassis 创建是在 **FlowChain** 中执行的, 不是简单的事务方法。FlowChain 的每个 Flow 可能有独立的事务边界。

如果 ExtensionPoint 回调被放在 FlowChain 的最后一个 Flow 之后 (即 FlowChain.done() 回调中), 它**不在任何 Flow 的事务内**, 而是在 FlowChain 完成后的回调中执行。这意味着:
- 如果 PhysicalServerVO 持久化失败, BaremetalChassisVO 已经提交, 无法回滚
- 存在数据不一致窗口: Chassis 存在但 PhysicalServerVO 不存在

**建议**:
1. 明确 ExtensionPoint 的调用位置是在 `dbf.persist(chassisVO)` 之后、同一事务 commit 之前, 还是在 FlowChain.done() 回调中
2. 如果是后者, 需要增加**补偿逻辑**: PhysicalServerManagerImpl 启动时扫描孤儿 Chassis (有 BaremetalChassisVO 但无对应 PhysicalServerRoleVO) 并补建
3. 或者使用 `@Transactional` 注解的同步点而非 FlowChain 回调

**P1: 互斥检查时序问题**

设计文档第 Q3 的互斥检查代码在 `afterCreateBaremetalChassis` 中执行。但如果检查失败 (已有 INTERNAL_SHARED 角色), 代码抛出 ApiMessageInterceptionException -- 此时 Chassis **已经创建完成**。应该在 `beforeCreateBaremetalChassis` 中做互斥检查, 或者在 BaremetalChassisApiInterceptor 中提前检查。

### 3.3 第 3 章: PXE 装机网络迁移 -- 评价: 有重大遗漏, P0 问题

**P0-2: BaremetalPxeServerVO 与 ProvisionNetworkVO 是本质不同的实体**

设计文档将 BM1 PxeServerVO 映射到 PhysicalServerProvisionNetworkVO, 但这两者的抽象层级完全不同:

| 维度 | BaremetalPxeServerVO | PhysicalServerProvisionNetworkVO |
|------|---------------------|-------------------------------|
| 本质 | **有状态的物理/逻辑服务器** (可 SSH 管理) | **网络参数配置** |
| SSH 管控 | hostname + sshUsername + sshPassword + sshPort | 无 |
| 镜像存储 | storagePath + capacity 管理 + ImageCacheVO | 无 |
| DHCP 管理 | 通过 SSH 操控 dnsmasq 进程 | 声明式 DHCP 参数 |
| Agent 部署 | PxeServerBase 通过 SSH 部署 tftp/HTTP Agent | 无 Agent |

**映射 PxeServer → ProvisionNetwork 丢失了 PxeServer 的实体管理能力** (SSH 连接、Agent 部署、镜像缓存、容量管理)。这不是简单的字段映射问题, 而是抽象层级错配。

**建议方案**:

方案 A (推荐): **不映射, 通过引用关联**
- PhysicalServerProvisionNetworkVO 增加 `originEntityType` + `originEntityUuid` 字段
- BM1 PxeServer 注册一条 ProvisionNetworkVO 记录, 只填充网络参数部分 (dhcpInterface 等)
- SSH/Agent/ImageCache 管理继续由 BM1 PxeServerVO 独立承担
- 统一层查询 ProvisionNetwork 时能看到 BM1 的 PXE 网络, 但不需要也无法管理 PxeServer

方案 B: **Phase 1 不映射 PXE**
- BM1 的 PXE 管理完全在 BM1 层独立运行
- 统一层通过 Bm1RoleInventory.pxeServerUuid 暴露关联
- 后续版本再考虑 PXE 抽象统一

方案 A 的优势是统一层能展示"某物理服务器通过哪个装机网络部署", 但不承担 PxeServer 管理职责。

**P1: DHCP 范围留空的实际影响**

设计文档 Q1 的结论是 DHCP 范围迁移时留空, 手动补充。但从 V3.1.1 schema 看, BM1 PxeServerVO 已有 dhcpRangeBegin/End/Netmask 字段 (V2.0.0 就有):

```sql
-- V2.0.0 原始定义
CREATE TABLE BaremetalPxeServerVO (
  dhcpRangeBegin varchar(32) DEFAULT NULL,
  dhcpRangeEnd varchar(32) DEFAULT NULL,
  dhcpRangeNetmask varchar(32) DEFAULT NULL,
  ...
```

设计文档认为 "BM1 PxeServerVO 不直接存储 DHCP 范围", 这与 V2.0.0 schema 矛盾。实际上 BM1 PxeServerVO **确实存储了 DHCP 范围** (dhcpRangeBegin/End/Netmask), 只是这些字段在某些版本中可能被废弃或不再由前端设置。需要进一步确认实际使用情况:

1. 如果这些字段在生产环境中确实有值 → 可以直接映射, 不需要手动补充
2. 如果这些字段已被废弃 (V3.1.1 的外部 PxeServer 模式) → 设计文档的结论成立

### 3.4 第 4 章: 独占分配适配 -- 评价: 良好

**优点**:
- INTERNAL_EXCLUSIVE 的 decreaseCapacity/increaseCapacity 逻辑简洁正确
- Initialized 状态特殊处理的分析到位: 跳过容量检查, 仅检查 capacityState != Allocated
- 状态映射表合理, 不试图合并 BM1 状态机

**P2: 状态同步方向不可逆的隐患**

设计文档的状态同步是单向的 (Chassis → PhysicalServer), 但考虑这个场景:

1. 管理员通过统一层 API 将 PhysicalServer state 改为 Disabled
2. BM1 不感知这个变更, BaremetalChassisVO.state 仍为 Enabled
3. 下次 Chassis 状态变更同步时, PhysicalServer 状态被覆盖回 Enabled

这在当前"Phase 1 PhysicalServerVO 是只读投影"的定位下不是问题 (统一层 API 不应修改 PhysicalServerVO.state)。但需要明确文档化: **统一层 API 对 BM1 关联的 PhysicalServerVO 是只读的, state/status 变更必须通过 BM1 原生 API**。

### 3.5 第 5 章: 兼容性和迁移 -- 评价: 良好, 有 1 个 P0 问题

**P0-3: 迁移脚本使用 MD5(CONCAT()) 生成 UUID 违反 ZStack 约定**

设计文档的迁移 SQL 使用 `MD5(CONCAT('bm1-chassis-', chassis.uuid))` 生成确定性 UUID。这有两个问题:

1. **ZStack 的 UUID 是 Platform.getUuid() 生成的 32 位随机 UUID (无连字符)**, 但 MySQL MD5() 返回 32 位十六进制字符串 -- 格式上兼容, 但 MD5 的碰撞空间远小于随机 UUID
2. **更重要的问题**: ZStack 的 ResourceVO 注册需要 `resourceType` 和 `concreteResourceType` 字段, 迁移脚本缺少 ResourceVO INSERT 语句。没有 ResourceVO 记录的实体在 ZStack 权限系统、标签系统、查询系统中都无法工作

**建议**:
- 迁移脚本需要增加 ResourceVO INSERT
- 使用 `REPLACE(UUID(), '-', '')` 而非 MD5 生成 UUID (牺牲幂等性换安全性), 或通过幂等键 (originBm1ChassisUuid SystemTag) 保障幂等
- 另一种方案: 使用 Java 迁移 (Flyway Java migration) 而非纯 SQL, 利用 Platform.getUuid()

**P1: 迁移脚本的 JSON_EXTRACT 兼容性**

BaremetalHardwareInfoVO.content 是自由格式的 JSON 字符串, 不同版本的发现 Agent 可能输出不同的 key。迁移脚本假设 `$.cpuCores`, `$.totalMemory`, `$.totalDisk` 存在, 但实际 key 名可能不同 (例如 `cpuNum` vs `cpuCores`, `memorySize` vs `totalMemory`)。需要确认实际的 JSON 格式。

---

## 第四部分：设计文档遗漏

### 遗漏 1: BaremetalHardwareInfoVO 的 1:N → 1:1 转换精度损失 [P1]

BM1 的硬件信息是 1:N 结构 (每种 type 一条记录, content 为 JSON), 包含丰富的细节:
- type=nic 的 content 包含每块 NIC 的 MAC、speed、PXE 标记
- type=disk 的 content 包含每块磁盘的型号、容量、接口类型

设计文档的 syncHardwareInfoToPhysicalServer 将这些汇总为 PhysicalServerHardwareInfoVO 的标量字段 (nicCount, totalDisk), 丢失了明细。虽然主架构文档有 PhysicalServerHardwareDetailVO (1:N 明细表), 但 BM1 adapter 设计未提及如何将 BaremetalHardwareInfoVO 映射到 DetailVO。

### 遗漏 2: PreconfigurationTemplate 的处理 [P2]

V3.4.0 为 BaremetalInstanceVO 增加了 templateUuid (FK → PreconfigurationTemplateVO), 这是 kickstart/preseed 模板的引用。设计文档未涉及此关系。虽然 Template 属于 Instance (消费层) 不属于 Chassis (角色层), 但需要明确文档化: PreconfigurationTemplate 不在统一层管辖范围。

### 遗漏 3: BaremetalNicVO 的 PXE 标记 [P1]

BaremetalNicVO 有 `pxe` 标记字段, 表示哪块 NIC 是 PXE 启动网卡。这与 BM2 的 ChassisNicVO.isProvisionNic 语义对应。设计文档未说明如何将这个信息同步到统一层 (PhysicalServerHardwareDetailVO 的 NIC 明细中应包含 provisionNic 标记)。

### 遗漏 4: BaremetalConsoleProxyVO 的处理 [P2]

V2.1.0 添加了 BaremetalConsoleProxyVO (chassisUuid FK), 用于 IPMI SOL (Serial Over LAN) 控制台。设计文档未提及如何在统一层展示/管理 BM1 的远程控制台。建议在 PhysicalServerVO 的 SPI 中增加 ConsoleAccessible 接口, 让 BM1 适配器提供 SOL 控制台 URL。

### 遗漏 5: Chassis 状态变更的扩展点缺失 [P1]

设计文档定义了 BaremetalChassisLifecycleExtensionPoint (create/delete/ipmiUpdate), 但 **缺少状态变更扩展点**。4.2 节的 syncChassisStatusToPhysicalServer 方法没有对应的 ExtensionPoint 定义, 也没有说明在 BaremetalChassisManagerImpl 的哪个位置注入调用。

BM1 的状态变更路径包括:
- HWInfoUnknown → PxeBooting (InspectChassis 开始)
- PxeBooting → Available (发现成功)
- PxeBooting → PxeBootFailed (发现失败)
- Available → Allocated (Instance 分配)
- Allocated → Available (Instance 释放/销毁)

需要在 BaremetalChassisLifecycleExtensionPoint 中增加:
```java
void afterBaremetalChassisStatusChanged(BaremetalChassisVO chassisVO,
    BaremetalChassisStatus oldStatus, BaremetalChassisStatus newStatus);
```

或复用 BM1 已有的 ChangeBaremetalChassisStatusExtensionPoint (如果存在)。

### 遗漏 6: 密码加密方式差异的详细处理 [P2]

设计文档 2.5 节提到两种加密方式:
- BaremetalChassisVO 使用 `@ENCRYPTParam` (ZStack 自定义注解)
- PhysicalServerAO 使用 `@EncryptColumn` + `PasswordConverter` (JPA Converter)

推荐"明文传递由目标端自动加密", 但未说明 `chassisVO.getIpmiPassword()` 通过 Hibernate lazy decryption 返回的到底是明文还是密文。如果 BM1 的加密机制是在 API 层而非 JPA 层 (`@ENCRYPTParam` 是 API 参数校验注解, 不一定有 JPA 自动解密), 那么 DB 中存的可能是密文, getter 返回的也是密文。需要确认 BM1 实际的密码读取路径。

---

## 第五部分：改进建议汇总

### P0 (阻塞落地)

| # | 问题 | 建议 |
|---|------|------|
| P0-1 | ExtensionPoint 在 FlowChain 中的事务边界不清 | 明确调用位置; 增加启动时孤儿补偿扫描; 或在 FlowChain 的事务性 Flow 内调用而非 done() 回调 |
| P0-2 | PxeServer→ProvisionNetwork 抽象层级错配 | 采用引用关联方案 (方案 A) 或 Phase 1 不映射 PXE (方案 B) |
| P0-3 | 迁移脚本缺少 ResourceVO 注册; MD5 UUID 风险 | 增加 ResourceVO INSERT; 改用 Java migration 或 REPLACE(UUID()) |

### P1 (需修正)

| # | 问题 | 建议 |
|---|------|------|
| P1-1 | matchExistingServer 缺少 oobPort 辅助匹配 | 增加可选 oobPort 过滤 |
| P1-2 | 互斥检查在 afterCreate 中执行时机过晚 | 移到 beforeCreate 或 ApiInterceptor |
| P1-3 | PxeServerVO 的 dhcpRange 字段实际可能有值 | 确认生产数据; 有值则直接映射 |
| P1-4 | BaremetalHardwareInfoVO 到 HardwareDetailVO 的映射未涉及 | 补充 DetailVO 同步逻辑 |
| P1-5 | Chassis 状态变更扩展点缺失 | 增加 afterStatusChanged 方法 |
| P1-6 | BaremetalNicVO 的 PXE 标记未同步到统一层 NIC 明细 | 在 syncHardwareInfo 中处理 |

### P2 (建议改进)

| # | 问题 | 建议 |
|---|------|------|
| P2-1 | PhysicalServer 状态只读约束未文档化 | 明确文档化统一层对 BM1 PhysicalServerVO 是只读的 |
| P2-2 | PreconfigurationTemplate 处理未明确 | 文档化: Template 属消费层, 不映射 |
| P2-3 | ConsoleProxy 未涉及 | 后续版本考虑 ConsoleAccessible SPI |
| P2-4 | 密码加密路径需确认 | 确认 getter 是否自动解密 |
| P2-5 | JSON 字段 key 名需确认 | 与发现 Agent 开发者确认实际 JSON schema |
| P2-6 | getInventory 缺少 managementIp | 可选携带 Instance managementIp |

---

## 第六部分：设计亮点

尽管有上述问题, 本设计文档在以下方面做得优秀:

1. **最小侵入原则**: 约 33 行新增代码分布在 3 个现有文件中, 不改任何已有方法签名。这是对 premium 代码库正确的尊重。

2. **PhysicalServerVO 是派生数据**: 删除 PhysicalServerVO 不影响 BaremetalChassisVO, 反之亦然。这避免了级联删除的灾难性场景。

3. **Instance 层不映射**: 明确将 BaremetalInstanceVO 排除在统一层之外, 符合"PhysicalServer 只管物理"的原则。

4. **独占容量模型正确**: INTERNAL_EXCLUSIVE 的清零/恢复逻辑符合 BM1 整机独占的语义。

5. **serialNumber 延迟回填**: 正确识别了 BM1 创建时无 serialNumber 的特殊性, 并设计了合理的回填路径。

6. **决策记录完整**: 附录 C 的 8 条设计决策都有明确理由, 便于后续回溯。

---

## 附录: 从代码还原的 BM1 完整实体关系图

```
BaremetalPxeServerVO (PXE 服务器)
├── 1:N BaremetalPxeServerClusterRefVO (PXE-Cluster 关联)
├── 1:N BaremetalImageCacheVO (镜像缓存)
└── 1:N BaremetalChassisVO.pxeServerUuid (Chassis 归属)

BaremetalChassisVO (物理机箱)
├── FK → ZoneEO (zoneUuid, RESTRICT)
├── FK → ClusterEO (clusterUuid, RESTRICT)
├── FK → BaremetalPxeServerVO (pxeServerUuid, SET NULL)
├── 1:N BaremetalHardwareInfoVO (硬件发现信息, chassisUuid FK, RESTRICT)
├── 1:N BaremetalConsoleProxyVO (控制台代理, chassisUuid FK, RESTRICT)
├── 1:N BaremetalHostBondingVO (网卡绑定, 已在 V2.6.0 DROP)
├── 1:1 BaremetalHostCfgVO (装机配置, 已在 V2.6.0 DROP)
│   └── 1:N BaremetalHostNicCfgVO (NIC 配置, 已在 V2.6.0 DROP)
└── 1:N BaremetalInstanceVO (实例)

BaremetalInstanceVO (裸金属实例)
├── FK → BaremetalChassisVO (chassisUuid, RESTRICT)
├── FK → ZoneEO (zoneUuid, SET NULL)
├── FK → ClusterEO (clusterUuid, SET NULL)
├── FK → ImageEO (imageUuid, SET NULL)
├── FK → BaremetalPxeServerVO (pxeServerUuid, SET NULL)
├── FK → PreconfigurationTemplateVO (templateUuid, SET NULL, V3.4.0 新增)
├── 1:N BaremetalNicVO (网络接口)
│   ├── FK → L3NetworkEO (SET NULL)
│   └── FK → UsedIpVO (SET NULL)
└── 1:N CustomPreconfigurationVO (自定义装机配置, V3.4.0 新增)
```
