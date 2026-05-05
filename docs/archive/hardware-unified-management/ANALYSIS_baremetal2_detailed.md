# Baremetal2 架构师评审: Phase 1 统一硬件管理详细设计

**评审人**: Baremetal2 Elastic Bare Metal Architecture Expert
**评审日期**: 2026-02-27
**评审对象**: `docs/hardware-unified-management/PHASE1_Detailed_Design.md` v1.1
**决定**: **NEEDS MODIFICATION** -- 整体方向认可，但存在若干必须修正的问题

---

## 一、总体评价

Phase 1 设计在 ZStack 现有模式基础上构建了一套独立的 PhysicalServer 抽象层，遵循了
`header/` 层 VO/Inventory/Message 三层模式，且明确声明不修改任何现有模块代码。
这一"增量叠加"策略从风险控制角度是正确的。

但从 baremetal2 模块的实际代码结构和业务逻辑来看，设计存在 **7 个关键问题** 和
**5 个改进建议**，下面逐一展开。

---

## 二、逐项评审

### 2.1 PhysicalServerVO 能否覆盖 baremetal2 的所有关键属性?

**结论: 部分覆盖，存在重要遗漏。**

#### 2.1.1 已覆盖的属性

设计文档中 `PhysicalServerAO` 包含以下字段，与 baremetal2 有良好对应:

| PhysicalServerAO 字段 | baremetal2 对应 | 评价 |
|----------------------|----------------|------|
| `name` | `BareMetal2ChassisAO.name` (第22行) | OK |
| `zoneUuid` | `BareMetal2ChassisAO.zoneUuid` (第28-29行) | OK |
| `oobAddress` / `oobPort` | `BareMetal2IpmiChassisVO.ipmiAddress/ipmiPort` (第20-23行) | OK |
| `oobManagementType` | 可映射 `BareMetal2ChassisAO.type` (第40行，值为 "ipmi") | OK |
| `architecture` | 来自 `BareMetal2ChassisOfferingVO.architecture` (第31行) | OK |
| `state` / `status` | 可映射 `BareMetal2ChassisState` / `BareMetal2ChassisStatus` | 部分 |

**代码证据**:
- `/home/mj/zstack/premium/baremetal2/src/main/java/org/zstack/baremetal2/chassis/BareMetal2ChassisAO.java` 第20-224行
- `/home/mj/zstack/premium/baremetal2/src/main/java/org/zstack/baremetal2/chassis/ipmi/BareMetal2IpmiChassisVO.java` 第18-77行

#### 2.1.2 遗漏的关键属性

**问题 1: 缺少 `provisionType` 字段**

baremetal2 有三种部署模式 (`Remote`, `Local`, `Direct`)，这是 chassis 级别的核心属性，
直接决定了硬件发现、OS部署、存储卷管理的整个流程走向。

```java
// /home/mj/zstack/premium/baremetal2/src/main/java/org/zstack/baremetal2/BareMetal2ProvisionType.java
public enum BareMetal2ProvisionType {
    Remote,   // 远程部署: OS通过网络下发到本地磁盘
    Local,    // 本地部署: OS从本地磁盘启动
    Direct    // 直接部署: 无需PXE，直接管理
}
```

`PhysicalServerAO` 中没有任何字段对应此属性。`provisionType` 影响:
- `BareMetal2ChassisAO.provisionType` (第43-44行) -- chassis 创建时必须指定
- `BareMetal2ChassisOfferingVO.provisionType` (第43-44行) -- offering 匹配时必须一致
- `BareMetal2InstanceAllocateChassisFlow.getCandidateChassisDisk()` (第98行) -- 分配磁盘时基于 provisionType 判断

**建议**: 在 `PhysicalServerAO` 中增加 `provisionMode` 字段(String 类型)，或通过 SystemTag 扩展。

**问题 2: 缺少 `powerStatus` 字段**

baremetal2 有独立的三态电源状态 (`POWER_ON`, `POWER_OFF`, `POWER_UNKNOWN`)，
这与 `PhysicalServerStatus` (Unknown/Connecting/Connected/Disconnected) 是正交的两个维度。

```java
// /home/mj/zstack/premium/baremetal2/src/main/java/org/zstack/baremetal2/chassis/BareMetal2ChassisPowerStatus.java
public enum BareMetal2ChassisPowerStatus {
    POWER_ON,
    POWER_OFF,
    POWER_UNKNOWN
}
```

KVM 主机不需要独立的 powerStatus，因为其电源状态由 hypervisor 管理。但对于 baremetal，
物理电源状态是一个独立于连接状态的关键维度。一台服务器可以 `status=Connected` 但
`powerStatus=POWER_OFF`（BMC 连通但主机已关机）。

**建议**: 在 `PhysicalServerAO` 中增加 `powerStatus` 字段(String 类型)。

**问题 3: 缺少 `clusterUuid` 字段**

`PhysicalServerAO` 只有 `zoneUuid` 和 `serverPoolUuid`，没有 `clusterUuid`。
但 baremetal2 的 chassis 是直接关联到 cluster 的:

```java
// /home/mj/zstack/premium/baremetal2/src/main/java/org/zstack/baremetal2/chassis/BareMetal2ChassisAO.java 第32-33行
@Column
@ForeignKey(parentEntityClass = ClusterEO.class, onDeleteAction = ForeignKey.ReferenceOption.RESTRICT)
private String clusterUuid;
```

且分配流程强依赖 `clusterUuid` 进行过滤:
```java
// /home/mj/zstack/premium/baremetal2/src/main/java/org/zstack/baremetal2/chassis/BareMetal2ChassisManagerImpl.java 第554-556行
if (CollectionUtils.isNotEmpty(msg.getRequiredClusterUuids())) {
    query = query.in(BareMetal2ChassisVO_.clusterUuid, msg.getRequiredClusterUuids());
}
```

设计文档的意图是 `ServerPool` 替代 cluster 做物理分组，但 baremetal2 的 cluster 不仅是分组，
还关联了 architecture 校验（同一 cluster 必须同架构）和 gateway 的 N:N 映射关系。

**建议**: 保留 `clusterUuid` 在 `PhysicalServerAO` 中，或在 `PhysicalServerRoleVO` 中记录
角色所绑定的 clusterUuid。`ServerPool` 可作为补充分组维度，但不能替代 cluster 语义。

**问题 4: 缺少 `chassisOfferingUuid` 概念**

baremetal2 有一个关键的"自动规格模板"机制 -- `BareMetal2ChassisOfferingVO`。
硬件发现完成后，系统自动创建/匹配一个 offering 记录:

```java
// /home/mj/zstack/premium/baremetal2/src/main/java/org/zstack/baremetal2/chassis/BareMetal2ChassisHardwareInfoSyncer.java 第141-159行
String offeringUuid = chassisOfferingForHardwareInfo(info, chassis);
if (offeringUuid == null) {
    BareMetal2ChassisOfferingVO offer = new BareMetal2ChassisOfferingVO();
    // ... 创建 offering
}
chassis.setChassisOfferingUuid(offeringUuid);
```

用户创建 instance 时可以指定 `chassisOfferingUuid` 来匹配相同规格的任意可用 chassis:

```java
// /home/mj/zstack/premium/baremetal2/src/main/java/org/zstack/baremetal2/instance/APICreateBareMetal2InstanceMsg.java 第59行
@APIParam(required = false, resourceType = BareMetal2ChassisOfferingVO.class, checkAccount = true)
private String chassisOfferingUuid;
```

`ServerHardwareInfoVO` 虽然记录了硬件详情，但缺少这种"规格聚合 + 模板匹配"能力。

**建议**: 在统一模型中保留"硬件规格模板"的概念，可以在 `ServerHardwareInfoVO` 中增加
`specificationHash` 字段，或定义独立的 `ServerSpecificationVO` 来支持模板匹配分配。

---

### 2.2 PhysicalServerRoleVO 的角色绑定机制对 baremetal2 是否合理?

**结论: 基本合理，但存在语义冲突。**

#### 2.2.1 设计合理之处

`PhysicalServerRoleVO` 的核心设计是:
```
PhysicalServerVO (1) ---> (*) PhysicalServerRoleVO ---> roleUuid (指向 HostVO/ChassisVO)
```

对于 KVM 场景，`roleUuid = hostUuid`，即一台物理服务器可以同时承担 KVM Host 和
Container Host 角色。这个设计是正确的。

#### 2.2.2 baremetal2 的角色绑定问题

**问题 5: baremetal2 的 chassis 不是"角色"，而是物理服务器本身**

在 KVM 场景中，物理服务器 -> Host 是"物理实体承担虚拟化角色"的关系。
但在 baremetal2 中，chassis 本身就代表物理服务器，不存在"角色"的间接层。

更关键的是，baremetal2 的 `BareMetal2ChassisVO` 继承 `ResourceVO`，
不继承 `HostVO`:

```java
// /home/mj/zstack/premium/baremetal2/src/main/java/org/zstack/baremetal2/chassis/BareMetal2ChassisAO.java 第20行
public class BareMetal2ChassisAO extends ResourceVO {
```

而 `BareMetal2InstanceVO` 继承 `VmInstanceVO`:

```java
// /home/mj/zstack/premium/baremetal2/src/main/java/org/zstack/baremetal2/instance/BareMetal2InstanceVO.java 第41行
public class BareMetal2InstanceVO extends VmInstanceVO {
```

这意味着 chassis 的 UUID 在 `ResourceVO` 空间，不在 `HostVO` 空间。
`PhysicalServerRoleVO.roleUuid` 指向的是 `ResourceVO.uuid` 而非 `HostVO.uuid`，
这在类型校验和 `@ForeignKey` 约束上需要特别注意。

**建议**: `PhysicalServerRoleVO` 不应该对 `roleUuid` 设置 FK 约束指向任何特定 VO，
应当保持为无约束的 String 类型（当前设计已经是这样，这一点是正确的）。但需要在
`PhysicalServerRoleProvider` SPI 中明确说明，不同 roleType 的 roleUuid 指向不同的
资源类型。

#### 2.2.3 角色自动关联的匹配方式问题

**问题 6: oobAddress 匹配对 baremetal2 不够准确**

设计文档第 6.1 节规定 BM V2 通过 `oobAddress` 匹配来关联 PhysicalServerVO:

> | BM V2 | 需新增: `BareMetal2ChassisCreateExtensionPoint` | Chassis 创建后 | oobAddress 匹配 |

但 baremetal2 的 IPMI 地址存储在子类 `BareMetal2IpmiChassisVO` 中，不在 `BareMetal2ChassisAO` 基类中。
且 baremetal2 的 `type` 字段是扩展点（可能有 `ipmi` 之外的类型），非 IPMI 类型的 chassis 可能没有 oobAddress。

```java
// /home/mj/zstack/premium/baremetal2/src/main/java/org/zstack/baremetal2/chassis/ipmi/BareMetal2IpmiChassisVO.java 第18行
public class BareMetal2IpmiChassisVO extends BareMetal2ChassisVO {
    private String ipmiAddress;  // IPMI 特有字段，不在基类中
```

**建议**: 对于 baremetal2 的关联匹配，应该:
1. 优先使用 `chassisUuid` 直接匹配（如果在 PhysicalServer 注册时已知）
2. 对于 IPMI 类型，可以通过 `ipmiAddress` 匹配
3. 需要在 `BareMetal2ChassisCreateExtensionPoint` 中提供足够信息，而非仅依赖 oobAddress

---

### 2.3 ServerCapacityVO 的独占模式对 baremetal2 是否合理?

**结论: 独占模式方向正确，但设计有欠缺。**

#### 2.3.1 独占分配的合理性

设计文档中 `ServerRoleType.isExclusive()` 对 BARE_METAL 和 BARE_METAL2 返回 true:

```java
// 设计文档 Section 2.5
public boolean isExclusive() {
    return this == BARE_METAL || this == BARE_METAL2;
}
```

`ServerCapacityUpdater.reserve()` 对独占角色执行整机清零:

```java
// 设计文档 Section 1B.6
if (ServerRoleType.valueOf(roleType).isExclusive()) {
    cap.setAvailableCpu(0);
    cap.setAvailableMemory(0);
    cap.setAvailableDisk(0);
}
```

这与 baremetal2 当前的分配逻辑一致 -- baremetal2 的分配就是整机分配，
没有 CPU/内存粒度的部分分配:

```java
// /home/mj/zstack/premium/baremetal2/src/main/java/org/zstack/baremetal2/chassis/BareMetal2ChassisManagerImpl.java 第500-504行
// mark this chassis as allocated as soon as possible
SQL.New(BareMetal2ChassisVO.class)
    .eq(BareMetal2ChassisVO_.uuid, reply.getChassis().getUuid())
    .set(BareMetal2ChassisVO_.status, BareMetal2ChassisStatus.Allocated)
    .update();
```

#### 2.3.2 baremetal2 特有的问题

**问题: baremetal2 不使用 CPU/内存容量做分配决策**

baremetal2 的分配逻辑基于以下维度过滤:
1. `state = Enabled`
2. `status = Available`
3. `chassisOfferingUuid` 匹配（硬件规格模板）
4. `clusterUuid` 约束
5. `avoidChassisUuids` 排除列表

```java
// /home/mj/zstack/premium/baremetal2/src/main/java/org/zstack/baremetal2/chassis/BareMetal2ChassisManagerImpl.java 第538-562行
Q query = Q.New(BareMetal2ChassisVO.class)
    .eq(BareMetal2ChassisVO_.state, BareMetal2ChassisState.Enabled)
    .eq(BareMetal2ChassisVO_.status, BareMetal2ChassisStatus.Available);
if (msg.getChassisOfferingUuid() != null) {
    query = query.eq(BareMetal2ChassisVO_.chassisOfferingUuid, msg.getChassisOfferingUuid());
}
```

它完全没有使用 `requiredCpu` 或 `requiredMemory` 参数。而设计文档的
`AllocateServerMsg` 要求:

```java
// 设计文档 Section 1B.1
private long requiredCpu;
private long requiredMemory;
```

对于独占分配来说，`requiredCpu` 和 `requiredMemory` 是无意义的 -- 无论请求多少，
结果都是整机分配。强制要求这些参数会给 baremetal2 的适配带来不必要的复杂度。

**建议**:
1. 将 `requiredCpu` 和 `requiredMemory` 标记为 `required = false`
2. 增加文档说明: 独占角色的分配忽略 CPU/内存参数，以整机为单位
3. 增加 `chassisOfferingUuid` 或 `hardwareSpecUuid` 参数用于 baremetal 的规格匹配

---

### 2.4 统一分配流程 AllocateServerMsg 能否适配 baremetal2 的分配需求?

**结论: 不能直接适配，需要扩展。**

#### 2.4.1 baremetal2 分配流程的独特性

baremetal2 的实例创建经过以下 FlowChain:

```
BareMetal2InstanceAllocateClusterFlow    -- 分配 cluster
    |
    v
BareMetal2InstanceAllocateChassisFlow    -- 分配 chassis (核心)
    |
    v
BareMetal2InstanceAllocateGatewayFlow    -- 分配 gateway
    |
    v
BareMetal2InstanceAllocateVolumeFlow     -- 分配存储卷
    |
    v
BareMetal2InstanceAllocatePrimaryStorageFlow -- 分配主存储
    |
    v
BareMetal2InstanceCreateProvisionConfigurationsFlow -- 创建部署配置
```

关键点在于: **chassis 分配和 gateway 分配是两个独立步骤**。

```java
// /home/mj/zstack/premium/baremetal2/src/main/java/org/zstack/baremetal2/instance/BareMetal2InstanceAllocateChassisFlow.java 第74-81行
AllocateBareMetal2ChassisMsg msg = new AllocateBareMetal2ChassisMsg();
msg.setRequiredClusterUuids(spec.getRequiredClusterUuids());
msg.setChassisOfferingUuid(spec.getChassisOfferingUuid());
msg.setRequiredChassisUuid(spec.getRequiredChassisUuid());
msg.setAvoidChassisUuids(spec.getAvoidChassisUuids());
msg.setRequiredChassisDiskUuid(spec.getRequiredChassisDiskUuid());
msg.setBareMetal2InstanceUuid(spec.getVmInventory().getUuid());
```

#### 2.4.2 AllocateServerMsg 缺少的参数

| baremetal2 需要的参数 | AllocateServerMsg 是否支持 | 说明 |
|---------------------|--------------------------|------|
| `requiredClusterUuids` (List) | 仅有单个 `clusterUuid` | baremetal2 支持多 cluster 候选 |
| `chassisOfferingUuid` | 无 | 规格模板匹配是核心分配逻辑 |
| `requiredChassisUuid` | 有 `serverUuid` | 可映射 |
| `requiredChassisDiskUuid` | 无 | 磁盘级别的定向分配 |
| `avoidChassisUuids` | 无 | 排除列表 |
| `bareMetal2InstanceUuid` | 无 | 关联到请求方实例 |
| `dryRun` | 有 | OK |

**建议**:
1. 将 `clusterUuid` 改为 `clusterUuids` (List 类型) 支持多候选
2. 增加 `avoidServerUuids` 排除列表
3. 增加 `hardwareSpecUuid` 用于规格模板匹配
4. 增加 `requiredDiskUuid` 用于磁盘级定向分配
5. 增加 `consumerUuid` 用于关联请求方实例
6. 或者，在 `AllocateServerMsg` 中增加一个 `Map<String, String> extraProperties`
   作为扩展属性容器，避免统一接口膨胀

---

### 2.5 硬件发现流程 (Discovery-First) 与 baremetal2 现有流程的对比

**结论: 理念对齐，但执行顺序有差异。**

#### 2.5.1 设计文档的 Discovery-First 流程

```
注册 PhysicalServer (oobAddress) -> 自动硬件发现 -> 填充 ServerHardwareInfoVO
    -> 角色关联 -> 就绪
```

#### 2.5.2 baremetal2 的实际流程

```
添加 Chassis (IPMI 凭据 + clusterUuid) -> Chassis 创建 (状态: HardwareInfoUnknown)
    -> 触发 Inspect (PXE reboot + iPXE 上报)
    -> 接收硬件信息 -> 更新 NIC/Disk/PCI
    -> 自动创建/匹配 ChassisOffering
    -> 状态变为 Available
```

**关键差异**:

1. **发现触发方式不同**:
   - 设计文档暗示注册后自动发现
   - baremetal2 需要显式触发 `APIInspectBareMetal2ChassisMsg`，因为硬件发现需要 PXE 重启物理机

```java
// /home/mj/zstack/premium/baremetal2/src/main/java/org/zstack/baremetal2/chassis/BareMetal2ChassisManagerImpl.java 第422-441行
if (msg.getReboot()) {
    InspectBareMetal2ChassisMsg imsg = new InspectBareMetal2ChassisMsg();
    // ... 触发硬件发现
} else if (BareMetal2Utils.isNonReboot(chassis.getUuid(), chassis.getProvisionType().toString())) {
    createBareMetal2InstanceNonReboot(chassis, msg, completion);
} else {
    completion.success(inventory);  // 不自动发现，等待手动 inspect
}
```

2. **发现内容颗粒度不同**:
   - 设计文档的 `ServerHardwareInfoVO` 是汇总信息 (cpuSockets, totalMemoryBytes 等)
   - baremetal2 发现的是详细的子资源列表:
     - `BareMetal2ChassisNicVO` -- 每张网卡的 MAC、速度、是否为部署网卡
     - `BareMetal2ChassisDiskVO` -- 每块磁盘的大小、类型、WWN
     - `BareMetal2ChassisPciDeviceVO` -- 每个 PCI 设备的详细信息
     - `BareMetal2ChassisGpuDeviceVO` -- 每个 GPU 的序列号、显存、功耗

```java
// /home/mj/zstack/premium/baremetal2/src/main/java/org/zstack/baremetal2/chassis/BareMetal2ChassisHardwareInfoSyncer.java 第521-567行
static class BareMetal2ChassisHardwareInfo {
    String architecture;
    String cpuModelName;
    Integer cpuNum;
    Long memorySize;
    String bootMode;
    List<NicInfo> nics;    // 详细 NIC 列表
    List<DiskInfo> disks;  // 详细磁盘列表
    List<PciInfo> pciDevices;  // 详细 PCI 设备列表 (含 GPU)
}
```

3. **发现后的副作用不同**:
   - baremetal2 发现后会自动创建 `BareMetal2ChassisOfferingVO` 作为规格模板
   - baremetal2 发现后会校验 cluster 的 architecture 是否匹配
   - baremetal2 发现后会校验 bootMode 是否支持

**建议**:
1. `ServerHardwareInfoVO` 应作为"摘要"存在，不替代各子系统的详细硬件信息
2. 硬件发现应定义为异步操作，支持手动触发和自动触发两种模式
3. 增加扩展点 `PhysicalServerHardwareDiscoveryExtensionPoint`，让各角色模块自行实现发现逻辑

---

### 2.6 baremetal2 特有的属性或逻辑在新设计中被忽略

#### 2.6.1 Gateway (部署网关) 概念完全缺失

baremetal2 最独特的架构元素是 **Gateway** -- 一个运行在 KVM Host 上的 PXE/DHCP/TFTP
服务，负责为 baremetal instance 提供网络引导和 OS 部署。

```java
// /home/mj/zstack/premium/baremetal2/src/main/java/org/zstack/baremetal2/gateway/BareMetal2GatewayVO.java 第20行
public class BareMetal2GatewayVO extends KVMHostVO {
```

Gateway 有自己的:
- N:N cluster 关联 (`BareMetal2GatewayClusterRefVO`)
- 独立的分配策略体系 (`DefaultGatewayAllocatorStrategy`, `LeastBmPreferredGatewayAllocatorStrategy`)
- 部署网卡 (`BareMetal2GatewayProvisionNicVO`)
- 独立的 provision network 概念

统一分配流程只考虑了"服务器分配"，没有考虑"部署基础设施分配"。

**建议**: 在 Phase 1 设计文档中至少承认 Gateway 的存在，并声明其不在 Phase 1 范围内。
在后续 Phase 中考虑是否需要将 Gateway 抽象为"Provisioning Service"统一管理。

#### 2.6.2 Bonding (网卡绑定) 概念缺失

baremetal2 支持物理网卡绑定:

```java
// /home/mj/zstack/premium/baremetal2/src/main/java/org/zstack/baremetal2/chassis/BareMetal2BondingVO.java 第20行
public class BareMetal2BondingVO extends ResourceVO implements ToInventory, OwnedByAccount {
    private String chassisUuid;
    private String name;
    private String slaves;   // 绑定的网卡列表
    private String opts;     // bonding 选项
    private Integer mode;    // bonding 模式 (0-6)
```

Bonding 影响 chassis 分配逻辑 -- 如果 instance 已有 bonding 关联，必须分配到对应的 chassis:

```java
// /home/mj/zstack/premium/baremetal2/src/main/java/org/zstack/baremetal2/instance/BareMetal2InstanceAllocateChassisFlow.java 第49-56行
String bondingUuid = Q.New(BareMetal2BondingNicRefVO.class)
    .select(BareMetal2BondingNicRefVO_.bondingUuid)
    .eq(BareMetal2BondingNicRefVO_.instanceUuid, spec.getVmInventory().getUuid())
    .limit(1).findValue();
if (bondingUuid != null) {
    dstChassis = Q.New(BareMetal2BondingVO.class)
        .select(BareMetal2BondingVO_.chassisUuid)
        .eq(BareMetal2BondingVO_.uuid, bondingUuid).findValue();
}
```

这种"网络拓扑约束分配"在设计文档中完全没有体现。

#### 2.6.3 弹性分配 vs 绑定分配模式缺失

baremetal2 支持两种分配模式，由创建实例时的参数决定:

```java
// /home/mj/zstack/premium/baremetal2/src/main/java/org/zstack/baremetal2/instance/APICreateBareMetal2InstanceMsg.java 第54-60行
// if chassisUuid is set, the instance will be bind to it
// when the instance stopped, its chassis will be hold
@APIParam(required = false, resourceType = BareMetal2ChassisVO.class)
private String chassisUuid;

// if chassisOfferingUuid is set, the instance will not bind to any chassis
// when the instance stopped, its chassis will be released
@APIParam(required = false, resourceType = BareMetal2ChassisOfferingVO.class)
private String chassisOfferingUuid;
```

并通过 SystemTag 控制停机时是否释放 chassis:

```java
// /home/mj/zstack/premium/baremetal2/src/main/java/org/zstack/baremetal2/BareMetal2SystemTags.java 第22行
public static SystemTag AUTO_RELEASE_BAREMETAL2_CHASSIS =
    new SystemTag("autoReleaseBareMetal2Chassis", BareMetal2InstanceVO.class);
```

这种"弹性 vs 绑定"的双模式在统一分配中需要被考虑。

#### 2.6.4 BareMetal2InstanceVO 继承 VmInstanceVO

baremetal2 的 instance 继承了 `VmInstanceVO`:

```java
// /home/mj/zstack/premium/baremetal2/src/main/java/org/zstack/baremetal2/instance/BareMetal2InstanceVO.java 第41行
public class BareMetal2InstanceVO extends VmInstanceVO {
```

这意味着 baremetal2 instance 复用了 VM 的整套生命周期（创建/启动/停止/销毁），
通过 `setHostUuid(gatewayUuid)` 来桥接 Gateway 与 VM 框架:

```java
// /home/mj/zstack/premium/baremetal2/src/main/java/org/zstack/baremetal2/instance/BareMetal2InstanceVO.java 第147行
public void setGatewayUuid(String gatewayUuid) {
    setHostUuid(gatewayUuid);  // 利用 VM 框架的 hostUuid 字段
    this.gatewayUuid = gatewayUuid;
}
```

统一设计需要理解: baremetal2 的 instance 已经在 VmInstanceVO 体系内，
而 PhysicalServerVO 是对 chassis 的抽象，两者的关系链是:

```
PhysicalServerVO -> (PhysicalServerRoleVO) -> BareMetal2ChassisVO -> (被分配给) -> BareMetal2InstanceVO
```

不是:

```
PhysicalServerVO -> (直接关联) -> BareMetal2InstanceVO
```

---

### 2.7 迁移路径对现有 baremetal2 业务的风险评估

#### 2.7.1 低风险项

| 方面 | 风险 | 原因 |
|------|------|------|
| 现有 API 兼容性 | 低 | 设计明确不修改任何现有代码 |
| 数据模型独立性 | 低 | PhysicalServerVO 是全新表 |
| 编译兼容性 | 低 | 新增代码在独立包 `org.zstack.header.server` |
| 运行时隔离 | 低 | PhysicalServer 是独立 Service |

#### 2.7.2 中风险项

| 方面 | 风险 | 原因 |
|------|------|------|
| 数据同步一致性 | 中 | PhysicalServerRoleVO 与 BareMetal2ChassisVO 的双写一致性 |
| 状态机映射 | 中 | baremetal2 有 6 种 ChassisStatus + 3 种 PowerStatus，映射到 4 种 PhysicalServerStatus 有信息损失 |
| 分配路径冲突 | 中 | AllocateServerMsg 和 AllocateBareMetal2ChassisMsg 并行存在，需要避免双重分配 |
| 查询性能 | 中 | PhysicalServer 查询需要 JOIN 多张表，对大规模部署可能有性能影响 |

#### 2.7.3 高风险项

**风险 1: 角色关联时机不确定**

设计文档通过 `BareMetal2ChassisCreateExtensionPoint` 在 chassis 创建后自动关联。
但 chassis 创建时可能:
- 尚未完成硬件发现（status = HardwareInfoUnknown）
- IPMI 凭据可能不正确
- Chassis 可能在创建后被立即删除

如果关联了一个随后被删除的 chassis，`PhysicalServerRoleVO` 中会残留脏数据。

**建议**: 关联应在硬件发现成功后（status 变为 Available 时）触发，而非创建时。

**风险 2: 独占分配的容量清零与 baremetal2 的弹性释放语义冲突**

设计文档的 `ServerCapacityUpdater.reserve()` 对独占角色执行清零。
但 baremetal2 的弹性模式下，instance 停止后 chassis 会被释放，容量应该恢复。

当前 baremetal2 通过 `status = Available/Allocated` 来控制分配，不使用容量扣减。
如果 Phase 3 要将 baremetal2 的分配切换到 `ServerCapacityUpdater`，
需要确保弹性释放时能正确恢复容量。

---

## 三、与 Baremetal V1 的对比分析

V1 的 `BaremetalChassisVO` 与 V2 的关键区别值得在统一设计中注意:

| 方面 | V1 (BaremetalChassisVO) | V2 (BareMetal2ChassisVO) |
|------|------------------------|-------------------------|
| IPMI 字段位置 | 直接在 VO 中 | 在子类 BareMetal2IpmiChassisVO 中 |
| PXE 依赖 | BaremetalPxeServerVO | BareMetal2GatewayVO (继承 KVMHostVO) |
| 硬件信息 | BaremetalHardwareInfoVO (Set) | ChassisNic/Disk/PCI/GPU 独立 VO |
| 弹性分配 | 不支持 | 支持 (offering 模式) |
| ProvisionType | 无 | Remote/Local/Direct |
| 继承链 | ResourceVO (直接) | ResourceVO -> ChassisAO -> ChassisVO -> IpmiChassisVO |

**代码证据**:
- V1: `/home/mj/zstack/premium/baremetal/src/main/java/org/zstack/header/baremetal/chassis/BaremetalChassisVO.java` -- IPMI 字段在第57-66行直接定义
- V2: `/home/mj/zstack/premium/baremetal2/src/main/java/org/zstack/baremetal2/chassis/ipmi/BareMetal2IpmiChassisVO.java` -- IPMI 字段在子类第19-29行

统一设计需要同时适配 V1 和 V2，而不能假设两者结构一致。

---

## 四、改进建议汇总

### 必须修改 (Blocking)

| # | 问题 | 建议 | 优先级 |
|---|------|------|--------|
| 1 | PhysicalServerAO 缺少 `powerStatus` | 增加 `powerStatus` 字段 (String) | P0 |
| 2 | PhysicalServerAO 缺少 `clusterUuid` | 增加 `clusterUuid` 字段或在 RoleVO 中记录 | P0 |
| 3 | AllocateServerMsg 缺少 baremetal2 必要参数 | 增加 `avoidServerUuids`, `hardwareSpecUuid`, `consumerUuid`；将 `requiredCpu/Memory` 改为 optional | P0 |
| 4 | 角色关联时机错误 | 改为硬件发现成功后触发，而非 chassis 创建时 | P1 |

### 建议修改 (Non-blocking)

| # | 问题 | 建议 | 优先级 |
|---|------|------|--------|
| 5 | 缺少 `provisionType` 概念 | 增加字段或通过 SystemTag 扩展 | P1 |
| 6 | ServerHardwareInfoVO 粒度不足 | 声明为摘要信息，不替代子系统详细硬件数据 | P2 |
| 7 | 缺少 Gateway 概念的说明 | 在文档中声明 Gateway 不在 Phase 1 范围 | P2 |
| 8 | 缺少弹性/绑定双模式的考虑 | 在 AllocateServerMsg 中增加 `exclusive` 标志 | P2 |
| 9 | 状态映射信息损失 | 定义 `PhysicalServerStatus` 与各子系统状态的精确映射表 | P2 |

---

## 五、审批意见

**决定: NEEDS MODIFICATION**

Phase 1 设计的整体架构方向正确 -- 独立新增的 PhysicalServer 抽象层不侵入现有代码，
这是降低风险的正确策略。但上述 4 个 P0 问题必须在进入实现之前解决。

特别强调:
1. **不要低估 baremetal2 的复杂性** -- baremetal2 不仅仅是"没有 hypervisor 的 Host"，
   它有自己完整的 chassis/gateway/instance/bonding/offering 生态
2. **分配流程不能简单统一** -- baremetal2 的分配维度 (offering 匹配、磁盘定向、bonding 约束、
   弹性释放) 远超 KVM 的 CPU/内存容量分配
3. **硬件发现是异步重操作** -- baremetal2 的硬件发现需要物理重启机器，不能假设为轻量级操作

在 P0 问题修复后，本评审方批准进入 Phase 1 实现。

---

*本文档由 Baremetal2 Elastic Bare Metal Architecture Expert 编写*
*所有代码引用基于 `/home/mj/zstack/premium/baremetal2/` 目录的实际源代码*
