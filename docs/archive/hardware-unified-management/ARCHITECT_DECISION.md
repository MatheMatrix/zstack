# 统一硬件管理 Phase 1 -- 总架构师裁决书

**版本**: v2.0
**日期**: 2026-02-27
**作者**: Lead Architect (Unified Hardware Management)
**基于**: PHASE1_Detailed_Design.md v1.1 + 5 位领域专家评审意见
**原则**: 统一架构主导，面向未来扩展性，该重构的就重构

---

## 0. 裁决总览

| # | 问题 | 裁决 | 方案 |
|---|------|------|------|
| 1 | clusterUuid 放在哪里 | **PhysicalServerRoleVO** | per-role cluster，PhysicalServerAO 不放 |
| 2 | AllocateServerMsg 定位 | **核心字段 + extraData Map** | Phase 1 定义接口，Phase 2 实现 |
| 3 | 容器外部调度 | **引入调度模式(SchedulingMode)** | 统一接口包容差异 |
| 4 | ServerHardwareInfoVO 模型 | **1:1 汇总 + 1:N 详情子表** | ServerHardwareDetailVO |
| 5 | 状态机设计 | **三维状态: state + status + powerStatus** | 最小公共状态 + 独立电源维度 |
| 6 | 超分比管理 | **独立 Manager + 预计算持久化** | ServerCapacityOverProvisioningManager |
| 7 | 兼容层时机 | **Phase 1 定义接口 + POC** | Phase 2 完整实现 |

---

## 1. 裁决详情

### 1.1 clusterUuid 放在哪里

**裁决: 方案 B -- clusterUuid 放在 PhysicalServerRoleVO 中 (per-role cluster)**

**理由**:

这是一个关键的架构决策。统一硬件管理的核心理念是：一台物理服务器可以承载多种角色。如果把 clusterUuid 放在 PhysicalServerAO 上，就隐含了"一台物理机只属于一个 cluster"的假设，这直接违背了多角色设计的根基。

具体分析：
- KVM Host 的 cluster 决定了 PrimaryStorage 挂载和 L2Network 可达性
- BM1 的 cluster 决定了 PXE Server 关联和级联删除
- BM2 的 cluster 决定了分配校验和 architecture 过滤
- Container 的 cluster 是 K8s 集群的映射

这四种 cluster 的语义完全不同。一台物理机上如果同时运行 KVM 角色和 BM2 角色（角色切换场景），它们完全可能属于不同 cluster。

**变更**:
- PhysicalServerRoleVO 增加 `clusterUuid` 字段
- PhysicalServerRoleVO 增加 `sourceUuid` 字段（容器专家提出的管理端点关联）
- PhysicalServerAO 不增加 clusterUuid

**对 BM2 专家"PhysicalServerAO 增加 clusterUuid"的回应**: 拒绝。cluster 是角色语义，不是物理服务器语义。BM2 的 cluster 关联通过 PhysicalServerRoleVO.clusterUuid 获取。分配时通过 RoleVO JOIN 查询即可。

**对 KVM 专家的回应**: 采纳其分析。分配时 AllocateServerMsg 通过 requiredClusterUuids 过滤，ServerClusterAllocatorFlow 通过 PhysicalServerRoleVO JOIN 实现。

---

### 1.2 AllocateServerMsg 的定位

**裁决: 方案 B -- 核心字段 + extraData Map，Phase 1 定义接口，Phase 2 实现**

**理由**:

AllocateServerMsg 是统一分配子系统的入口。它必须足够通用以覆盖四种角色，但不应成为 AllocateHostMsg 的简单翻版。统一分配的价值在于把"物理资源过滤"（状态、容量、架构、池、集群）从"虚拟化资源过滤"（L3 网络、主存储、备份存储）中分离出来。

设计分两层：
1. **核心层**: 物理资源维度的通用字段 -- 这是 AllocateServerMsg 的固有职责
2. **扩展上下文层**: `Map<String, Object> extraData` -- 承载角色特定的过滤条件，由兼容层或角色模块注入

这样做的好处是：
- AllocateServerMsg 不会膨胀成 AllocateHostMsg 的超集
- BM2 的 chassisOfferingUuid 通过 extraData 传递，BM1 的 pxeServerUuid 同理
- KVM 的 l3NetworkUuids / requiredPrimaryStorageUuids 通过 extraData 传递
- 新增角色类型时不需要修改 AllocateServerMsg 本身

**对 Allocator 专家的回应**: 完全采纳其"两层设计"建议。AllocateServerMsg 增加 avoidServerUuids、softAvoidServerUuids、diskSize、architecture 为核心字段。l3NetworkUuids 等虚拟化相关字段进 extraData。

**对 Container 专家的回应**: 完全采纳。NATIVE_HOST 不参与主动分配，在 ServerRoleType 上通过 SchedulingMode 声明。

**Phase 1 范围**: 仅定义接口（AllocateServerMsg, ServerAllocatorSpec, ServerAllocatorFlow, ServerSortorFlow），不实现 ServerAllocatorManager。Phase 2 实现完整分配链。

---

### 1.3 容器的外部调度如何处理

**裁决: 方案 B -- 引入调度模式(SchedulingMode)概念**

**理由**:

这是统一架构必须面对的根本性差异。四种角色的调度模式截然不同：
- KVM: ZStack 内部调度，先分配再创建（事前）
- BM1/BM2: ZStack 内部调度，整机独占分配（事前）
- Container: K8s 外部调度，ZStack 事后同步（事后）

统一架构的价值不在于抹平这些差异，而在于用一个统一的模型去描述和包容这些差异。

**设计**:

在 ServerRoleType 上不再用简单的 `isExclusive()` 布尔方法，而是引入 SchedulingMode 枚举：

```java
public enum SchedulingMode {
    /** ZStack 内部调度，按需扣减 CPU/Memory（KVM） */
    INTERNAL_SHARED,
    /** ZStack 内部调度，整机独占分配（BM1/BM2） */
    INTERNAL_EXCLUSIVE,
    /** 外部调度器管理，ZStack 仅做只读同步（Container/K8s） */
    EXTERNAL_READONLY
}
```

影响范围：
- ServerCapacityUpdater: EXTERNAL_READONLY 模式下拒绝 reserve()，仅允许 syncFromExternal()
- AllocateServerMsg: 对 EXTERNAL_READONLY 角色返回 OperationNotSupportedError
- ServerCapacityVO: EXTERNAL_READONLY 模式下 availableCpu/Memory 由同步写入，不由分配扣减

**对 Container 专家的回应**: 完全采纳。这是架构层面的正确抽象。不是把容器排除在分配体系之外，而是在统一体系内用 SchedulingMode 标记其调度特征。

---

### 1.4 ServerHardwareInfoVO 的数据模型

**裁决: 方案 C -- 1:1 汇总表 + 1:N 详情子表**

**理由**:

四种角色对硬件信息的需求粒度差异巨大：
- KVM: 基本只关心 CPU 总数/内存总量/架构，通过 agent 上报
- BM1: 1:N 泛型存储（type=basic/nic/disk/pxeserver，content=JSON），每块网卡都要 MAC 地址和 PXE 标记
- BM2: 详细的 NIC/Disk/PCI/GPU 子资源列表，有独立的 VO（BareMetal2ChassisNicVO 等）
- Container: 通过 K8s API 获取节点资源信息

1:1 结构化表无法满足 BM1/BM2 的详细硬件发现需求。但完全改成 1:N 泛型又会导致 KVM 场景下的简单查询变复杂。

**设计**: 两级结构

1. **ServerHardwareInfoVO (1:1)**: 保持汇总级别的结构化字段，增加 bootMode 和 rawDetail
2. **ServerHardwareDetailVO (1:N)**: 新增详情子表，type + content JSON 模式

```java
// 汇总层 -- 所有角色通用的快速查询字段
@Entity
@Table
public class ServerHardwareInfoVO {
    @Id
    @Column
    private String uuid;  // = PhysicalServerVO.uuid

    // CPU 汇总
    private Integer cpuSockets;
    private Integer cpuCoresPerSocket;
    private Integer cpuThreadsPerCore;
    private String cpuModel;

    // 内存汇总
    private Long totalMemoryBytes;
    private Integer memorySlots;

    // 存储汇总
    private Integer diskCount;
    private Long totalDiskBytes;

    // 网络汇总
    private Integer nicCount;

    // 固件信息
    private String biosVersion;
    private String bmcVersion;
    private String bootMode;  // LEGACY, UEFI (BM1 专家提出)

    // 时间戳
    private Timestamp discoveredDate;
    private Timestamp lastUpdatedDate;
}

// 详情层 -- BM1/BM2 的细粒度硬件信息
@Entity
@Table
public class ServerHardwareDetailVO {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column
    private long id;

    @Column
    private String serverUuid;  // FK -> PhysicalServerVO

    @Column
    @Enumerated(EnumType.STRING)
    private HardwareDetailType type;  // NIC, DISK, GPU, PCI, MEMORY_DIMM, BASIC

    @Column
    @Lob
    private String content;  // JSON 格式的详细信息

    @Column
    private Timestamp createDate;

    @Column
    private Timestamp lastOpDate;
}

public enum HardwareDetailType {
    BASIC,       // 基础信息 (序列号、厂商等)
    NIC,         // 网卡详情 (MAC, speed, PXE boot flag)
    DISK,        // 磁盘详情 (size, type, slot)
    GPU,         // GPU 详情 (vendor, model, memory)
    PCI,         // PCI 设备
    MEMORY_DIMM  // 内存条详情 (slot, size, speed)
}
```

**对 BM1 专家的回应**: 采纳。1:N 详情子表与 BaremetalHardwareInfoVO 的 type+content 模式一致，迁移路径清晰。bootMode 加入汇总层。

**对 BM2 专家的回应**: 部分采纳。BM2 的 NIC/Disk/PCI/GPU 独立 VO 保留在角色层，通过 ServerHardwareDetailVO 做汇总级同步。BM2 不需要把所有子资源都同步上来，只同步汇总信息即可。

---

### 1.5 状态机设计

**裁决: 方案 C -- 三维状态: serverState + serverStatus + powerStatus**

**理由**:

现有设计只有 state(管理状态) + status(连接状态) 两个维度。但评审暴露出至少三类正交的状态需求：

1. **管理状态 (state)**: 管理员控制的运行许可 -- Enabled/Disabled/PreMaintenance/Maintenance
2. **连接状态 (status)**: 物理服务器与管理平台的连接 -- Unknown/Connecting/Connected/Disconnected
3. **电源状态 (powerStatus)**: 物理服务器的实际电源状态 -- PowerOn/PowerOff/PowerUnknown

这三个维度是正交的：
- 一台服务器可以是 Enabled(管理允许) + Disconnected(管理口不通) + PowerOn(实际开机)
- 一台服务器可以是 Maintenance(维护中) + Connected(管理口通) + PowerOff(已关机)

将三者混在一起会导致状态爆炸。BM2 专家明确指出 powerStatus 与 status 是正交维度，KVM 专家要求 PreMaintenance，BM1 专家要求 Discovering/DiscoveryFailed。

**设计**:

```java
// 管理状态 -- 管理员意图
public enum PhysicalServerState {
    Enabled,
    Disabled,
    PreMaintenance,  // KVM 专家要求: VM 疏散过渡态
    Maintenance;

    // 状态转换表 (略, 参照 HostState)
}

public enum PhysicalServerStateEvent {
    enable,
    disable,
    preMaintain,  // Enabled/Disabled -> PreMaintenance
    maintain       // PreMaintenance -> Maintenance
}

// 连接状态 -- 管理平台与物理服务器的连接状况
public enum PhysicalServerStatus {
    Unknown,
    Connecting,
    Connected,
    Disconnected;

    // 状态转换表 (略, 与 v1.1 相同)
}

// 电源状态 -- 物理服务器实际电源状态 (通过 OOB 查询)
public enum PhysicalServerPowerStatus {
    PowerOn,
    PowerOff,
    PowerUnknown;  // 无 OOB 或 OOB 不可达时
}
```

**对 BM1 专家"增加 Discovering/DiscoveryFailed"的回应**: 拒绝在 PhysicalServer 层增加。原因：硬件发现是角色层的操作流程，不是物理服务器本身的持久状态。一台物理服务器的 BM1 角色可能在 Discovering，而 KVM 角色同时是 Connected。发现状态应该通过 PhysicalServerRoleVO 的 roleStatus 或角色层自身的 VO 状态来表达。

**PhysicalServerRoleVO 增加 roleStatus**: 采纳 BM1 专家方案 B 的建议。

```java
@Entity
@Table
public class PhysicalServerRoleVO {
    // ... 原有字段 ...

    @Column
    private String roleStatus;  // 角色层自定义状态字符串，PhysicalServer 层不解析

    // 对于 BM1: 可以是 HWInfoUnknown/PxeBooting/Available/Allocated
    // 对于 KVM: 可以是 Connected/Disconnected
    // 对于 Container: 可以是 Ready/NotReady
}
```

这样设计的好处：PhysicalServer 层保持稳定的三维状态，各角色的特有状态不会污染统一层。

---

### 1.6 超分比管理方式

**裁决: 方案 A -- 独立 ServerCapacityOverProvisioningManager + 预计算持久化**

**理由**:

v1.1 设计的 VO getter 实时计算方案有三个致命问题：
1. Hibernate/JPA 的 SQL 查询无法调用 Java getter，导致 `WHERE totalCpu > requiredCpu` 这类查询不可能用 getTotalCpu() 的逻辑值
2. 无法支持 per-server 级别的超分比设置
3. 超分比变更时没有重算机制

现有系统的做法是正确的：
- CPU 超分: 预计算持久化到 totalCpu 字段（totalCpu = physicalCpu * ratio），DB 查询直接可用
- Memory 超分: 运行时动态计算，不修改 VO（因为内存超分比可能频繁变化）

**设计**:

```java
// ServerCapacityVO -- 移除 ratio 字段，totalCpu 直接存预计算值
@Entity
@Table
public class ServerCapacityVO {
    @Id
    @Column
    private String uuid;

    // 物理真实值
    @Column
    private long totalPhysicalCpu;     // 物理 CPU 线程总数
    @Column
    private long totalPhysicalMemory;  // 物理内存 (bytes)

    // CPU: 预计算持久化 (totalCpu = totalPhysicalCpu * cpuRatio)
    @Column
    private long totalCpu;             // 逻辑 CPU (已乘超分比)

    // Memory: 预计算持久化 (totalMemory = totalPhysicalMemory * memRatio)
    @Column
    private long totalMemory;          // 逻辑内存 (已乘超分比, bytes)

    // 可分配量
    @Column
    private long availableCpu;
    @Column
    private long availableMemory;

    // 监控用
    @Column
    private long availablePhysicalMemory;  // KVM 专家要求

    // 细粒度 CPU 信息 (兼容 HostCapacityVO)
    @Column
    private int cpuNum;       // 逻辑 CPU 数
    @Column
    private int cpuSockets;   // 物理插槽数
    @Column
    private int cpuCoreNum;   // 物理核心总数

    // 预留
    @Column
    private long reservedMemory;

    // 磁盘
    @Column
    private long totalDisk;
    @Column
    private long availableDisk;

    // 状态
    @Column
    @Enumerated(EnumType.STRING)
    private CapacityState capacityState;

    // 当前独占角色 (Allocator 专家建议)
    @Column
    private String exclusiveRoleUuid;

    // 调度模式缓存 (避免每次查 RoleType)
    @Column
    @Enumerated(EnumType.STRING)
    private SchedulingMode schedulingMode;
}
```

**ServerCapacityOverProvisioningManager**:

```java
// header 层接口
public interface ServerCapacityOverProvisioningManager {
    /** 获取 CPU 超分比 (先查 per-server，再回退全局) */
    double getCpuRatio(String serverUuid);

    /** 设置 per-server CPU 超分比 */
    void setCpuRatio(String serverUuid, double ratio);

    /** 获取内存超分比 */
    double getMemoryRatio(String serverUuid);

    /** 设置 per-server 内存超分比 */
    void setMemoryRatio(String serverUuid, double ratio);

    /** 重算某台服务器的容量 (ratio 变更后调用) */
    void recalculate(String serverUuid);

    /** 计算超分后的 CPU (供分配器使用) */
    long calculateTotalCpu(long physicalCpu, String serverUuid);

    /** 计算超分后的 Memory (供分配器使用) */
    long calculateTotalMemory(long physicalMemory, String serverUuid);
}
```

**RecalculateServerCapacityMsg**: 新增内部消息，ratio 变更后触发重算。

**对 Allocator 专家的回应**: 完全采纳。这是必须做的重构，VO getter 计算方案在数据库查询场景下完全不可行。

**@Transactional + @DeadlockAutoRestart 修复**: 完全采纳。这是编译级错误，必须立即修正。

---

### 1.7 兼容层时机

**裁决: Phase 1 定义接口 + POC，Phase 2 完整实现**

**理由**:

KVM 专家说得对: 兼容层放到 Phase 3 风险太大。但完全在 Phase 1 实现也不现实（分配链还没有）。折中方案：

- **Phase 1**: 定义兼容层接口 `ServerAllocatorCompatibilityBridge`，包含 AllocateHostMsg <-> AllocateServerMsg 的转换方法签名
- **Phase 1**: 实现最简 POC -- 一个空壳 bridge 能编译通过，跑一个 smoke test 证明转换链路可达
- **Phase 2**: 完整实现分配链和兼容层
- **Phase 3**: 灰度切换

```java
// Phase 1 定义, Phase 2 实现
public interface ServerAllocatorCompatibilityBridge {
    /**
     * 将现有 AllocateHostMsg 转换为 AllocateServerMsg.
     * 虚拟化相关字段 (l3NetworkUuids, primaryStorage) 放入 extraData.
     */
    AllocateServerMsg convertFromHost(AllocateHostMsg hostMsg);

    /**
     * 将 AllocateServerReply 转换回 AllocateHostReply.
     * 从 roleUuid 获取 HostInventory.
     */
    AllocateHostReply convertToHost(AllocateServerReply serverReply);

    /**
     * 是否启用统一分配 (特性开关).
     * false = 走原有 HostAllocatorChain (默认)
     * true  = 走 ServerAllocatorChain
     */
    boolean isEnabled();
}
```

**对 Allocator 专家"特性开关"的回应**: 完全采纳。增加 GlobalConfig `physicalServer.allocator.enabled`，默认 false。

---

## 2. P0 问题清单逐一回应

| # | 问题 | 裁决 | 处理方式 |
|---|------|------|---------|
| 1 | @Transactional + @DeadlockAutoRestart 拆分 | **采纳** | 严格遵循 HostCapacityUpdater 的两层模式: _run() + run() |
| 2 | DDL 表名 PhysicalServerAO -> PhysicalServerVO | **采纳** | @MappedSuperclass 不生成独立表，DDL 表名改为 PhysicalServerVO |
| 3 | PhysicalServerAO 增加 clusterUuid | **拒绝** | cluster 放在 PhysicalServerRoleVO 中 (详见 1.1) |
| 4 | PhysicalServerAO 增加 powerStatus | **采纳** | 增加三维状态中的 powerStatus (详见 1.5) |
| 5 | PhysicalServerState 增加 PreMaintenance | **采纳** | 与 HostState 保持一致 (详见 1.5) |
| 6 | PhysicalServerStatus 增加 Discovering/DiscoveryFailed | **拒绝** | 通过 RoleVO.roleStatus 在角色层解决 (详见 1.5) |
| 7 | AllocateServerMsg 补齐字段 + extraData | **采纳** | 核心字段 + extraData Map 两层设计 (详见 1.2) |
| 8 | Container 角色关联改用 Endpoint 同步扩展点 | **采纳** | 使用 ContainerEndpointSyncExtensionPoint 或新增 NativeHostSyncedExtensionPoint |
| 9 | NATIVE_HOST 标记为外部调度 | **采纳** | SchedulingMode.EXTERNAL_READONLY (详见 1.3) |
| 10 | ServerCapacityVO 补齐 cpuNum/cpuSockets/availablePhysicalMemory | **采纳** | 完整对齐 HostCapacityVO 字段 (详见 1.6) |
| 11 | 超分比改为独立 Manager + 预计算持久化 | **采纳** | ServerCapacityOverProvisioningManager (详见 1.6) |
| 12 | 角色关联时机从创建改为硬件发现后 | **部分采纳** | 角色关联时机由各角色的 RoleProvider 决定，不强制统一 |

### P0-12 详细说明: 角色关联时机

BM2 专家要求"硬件发现成功后才关联角色"。但这不能作为统一规则：
- KVM: Host Connected 后关联（合理）
- BM1: Chassis 创建后就有角色映射需求（PXE 需要知道 chassis 存在）
- BM2: 硬件发现成功后关联（合理，发现失败的 chassis 不应消耗 PhysicalServer 资源）
- Container: Node 同步入库后关联（合理）

**裁决**: 角色关联时机由各角色的 PhysicalServerRoleProvider 实现自行决定。统一层只提供关联/解关联的方法，不规定时机。

---

## 3. 修订后的核心数据模型 (Java 伪代码)

### 3.1 PhysicalServerAO.java (修订)

```java
package org.zstack.header.server;

import org.zstack.header.vo.ForeignKey;
import org.zstack.header.vo.ForeignKey.ReferenceOption;
import org.zstack.header.vo.Index;
import org.zstack.header.vo.ResourceVO;
import org.zstack.header.zone.ZoneEO;

import javax.persistence.*;
import java.sql.Timestamp;

@MappedSuperclass
public class PhysicalServerAO extends ResourceVO {

    @Column
    @ForeignKey(parentEntityClass = ZoneEO.class, onDeleteAction = ReferenceOption.RESTRICT)
    private String zoneUuid;

    @Column
    @ForeignKey(parentEntityClass = ServerPoolVO.class, onDeleteAction = ReferenceOption.SET_NULL)
    private String serverPoolUuid;

    @Column
    private String name;

    @Column
    private String description;

    @Column
    @Index
    private String managementIp;

    @Column
    private String architecture;  // x86_64, aarch64, mips64el, loongarch64

    @Column
    @Index
    private String serialNumber;  // SMBIOS 全局唯一标识

    @Column
    private String manufacturer;

    @Column
    private String model;

    // ---- 带外管理 (OOB) ----
    // 所有 OOB 字段均 nullable，容器节点无 OOB

    @Column
    private String oobManagementType;  // IPMI, REDFISH, NONE (新增 NONE)

    @Column
    @Index
    private String oobAddress;

    @Column
    private Integer oobPort;

    @Column
    private String oobUsername;

    @Column
    @Convert(converter = org.zstack.core.encrypt.PasswordConverter.class)
    private String oobPassword;

    // ---- 三维状态 ----

    @Column
    @Enumerated(EnumType.STRING)
    private PhysicalServerState state;  // 管理状态: Enabled/Disabled/PreMaintenance/Maintenance

    @Column
    @Enumerated(EnumType.STRING)
    private PhysicalServerStatus status;  // 连接状态: Unknown/Connecting/Connected/Disconnected

    @Column
    @Enumerated(EnumType.STRING)
    private PhysicalServerPowerStatus powerStatus;  // 电源状态: PowerOn/PowerOff/PowerUnknown

    // ---- 时间戳 ----

    @Column
    private Timestamp createDate;

    @Column
    private Timestamp lastOpDate;

    @PreUpdate
    private void preUpdate() {
        lastOpDate = null;
    }

    // ----- Getters & Setters (省略) -----
}
```

**v1.1 -> v2.0 变更**:
- 新增 `powerStatus` 字段 (PhysicalServerPowerStatus 枚举)
- `oobManagementType` 增加 `NONE` 选项
- `oobAddress` 增加 @Index（BM1 专家：用于关联匹配）
- PhysicalServerState 增加 `PreMaintenance`
- 不增加 clusterUuid（移至 RoleVO）

### 3.2 PhysicalServerVO.java (修订)

```java
@Entity
@Table(name = "PhysicalServerVO")  // 明确表名，修正 v1.1 的 PhysicalServerAO 错误
@AutoDeleteTag
@BaseResource
@EntityGraph(
    parents = {
        @EntityGraph.Neighbour(type = ZoneVO.class, myField = "zoneUuid", targetField = "uuid"),
        @EntityGraph.Neighbour(type = ServerPoolVO.class, myField = "serverPoolUuid", targetField = "uuid"),
    }
)
public class PhysicalServerVO extends PhysicalServerAO {

    @OneToMany(fetch = FetchType.LAZY)  // v2.0: EAGER -> LAZY (Allocator/KVM 专家)
    @JoinColumn(name = "serverUuid", insertable = false, updatable = false)
    @NoView
    private Set<PhysicalServerRoleVO> roles;

    // hardwareInfo 和 capacity 不做 JPA 关联，改为按需查询
    // (v2.0 变更: 移除 EAGER fetch，避免 N+1 问题)

    // ----- Getters & Setters (省略) -----
}
```

**v1.1 -> v2.0 变更**:
- 表名明确为 `PhysicalServerVO`
- roles 从 EAGER 改为 LAZY
- 移除 hardwareInfo 和 capacity 的 JPA @OneToOne EAGER 关联（改为按需查询）

### 3.3 PhysicalServerRoleVO.java (修订)

```java
@Entity
@Table(
    name = "PhysicalServerRoleVO",
    uniqueConstraints = {
        @UniqueConstraint(name = "ukServerRole", columnNames = {"serverUuid", "roleType"}),
        @UniqueConstraint(name = "ukRoleUuid", columnNames = {"roleUuid"})
    }
)
public class PhysicalServerRoleVO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column
    private long id;

    @Column
    private String serverUuid;  // FK -> PhysicalServerVO

    @Column
    @Enumerated(EnumType.STRING)
    private ServerRoleType roleType;

    @Column
    private String roleUuid;  // 指向 HostVO.uuid / BaremetalChassisVO.uuid 等

    // ---- v2.0 新增字段 ----

    @Column
    private String clusterUuid;  // 角色所属 cluster (裁决 1.1)

    @Column
    private String sourceUuid;   // 管理来源 (Container: endpointUuid, BM1: pxeServerUuid)

    @Column
    private String roleStatus;   // 角色层自定义状态 (裁决 1.5)

    @Column
    @Enumerated(EnumType.STRING)
    private RoleSyncStatus syncStatus;

    @Column
    private Timestamp createDate;

    @Column
    private Timestamp lastSyncTime;

    public enum RoleSyncStatus {
        InSync,
        OutOfSync
    }

    // ----- Getters & Setters (省略) -----
}
```

**v1.1 -> v2.0 变更**:
- 新增 `clusterUuid` -- 角色级别的集群关联
- 新增 `sourceUuid` -- 管理来源标识
- 新增 `roleStatus` -- 角色自管理的运行状态

### 3.4 ServerRoleType.java (修订)

```java
package org.zstack.header.server;

public enum ServerRoleType {
    KVM_HOST(SchedulingMode.INTERNAL_SHARED),
    BARE_METAL(SchedulingMode.INTERNAL_EXCLUSIVE),
    BARE_METAL2(SchedulingMode.INTERNAL_EXCLUSIVE),
    NATIVE_HOST(SchedulingMode.EXTERNAL_READONLY);

    private final SchedulingMode schedulingMode;

    ServerRoleType(SchedulingMode schedulingMode) {
        this.schedulingMode = schedulingMode;
    }

    public SchedulingMode getSchedulingMode() {
        return schedulingMode;
    }

    public boolean isExclusive() {
        return schedulingMode == SchedulingMode.INTERNAL_EXCLUSIVE;
    }

    public boolean isExternallyScheduled() {
        return schedulingMode == SchedulingMode.EXTERNAL_READONLY;
    }

    public boolean isInternallyScheduled() {
        return schedulingMode != SchedulingMode.EXTERNAL_READONLY;
    }
}
```

### 3.5 SchedulingMode.java (新增)

```java
package org.zstack.header.server;

public enum SchedulingMode {
    /** ZStack 内部调度，按需扣减 CPU/Memory（如 KVM 创建 VM） */
    INTERNAL_SHARED,

    /** ZStack 内部调度，整机独占分配（如 Baremetal 整机装机） */
    INTERNAL_EXCLUSIVE,

    /** 外部调度器管理，ZStack 仅做只读同步（如 K8s 调度 Pod） */
    EXTERNAL_READONLY
}
```

### 3.6 ServerHardwareDetailVO.java (新增)

```java
package org.zstack.header.server;

import javax.persistence.*;
import java.sql.Timestamp;

@Entity
@Table(name = "ServerHardwareDetailVO")
public class ServerHardwareDetailVO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column
    private long id;

    @Column
    private String serverUuid;  // FK -> PhysicalServerVO

    @Column
    @Enumerated(EnumType.STRING)
    private HardwareDetailType type;

    @Column(columnDefinition = "TEXT")
    private String content;  // JSON

    @Column
    private Timestamp createDate;

    @Column
    private Timestamp lastOpDate;

    @PreUpdate
    private void preUpdate() {
        lastOpDate = null;
    }

    // ----- Getters & Setters (省略) -----
}
```

### 3.7 ServerHardwareInfoVO.java (修订)

```java
// 在 v1.1 基础上新增:
@Entity
@Table(name = "ServerHardwareInfoVO")
public class ServerHardwareInfoVO {
    // ... 原有字段不变 ...

    @Column
    private String bootMode;  // LEGACY, UEFI, UNKNOWN (BM1 专家要求)

    // 移除 discoveredDate -> lastUpdatedDate 即可表达
    // 新增汇总来源标记
    @Column
    private String discoverySource;  // OOB, AGENT, SYNC (标记硬件信息来源)
}
```

### 3.8 ServerCapacityVO.java (修订)

```java
@Entity
@Table(name = "ServerCapacityVO")
public class ServerCapacityVO {

    @Id
    @Column
    private String uuid;  // = PhysicalServerVO.uuid

    // ---- 物理真实值 (硬件发现/agent上报) ----
    @Column
    private long totalPhysicalCpu;     // 物理 CPU 线程总数
    @Column
    private long totalPhysicalMemory;  // 物理内存 (bytes)

    // ---- 逻辑值 (预计算持久化: physical * ratio) ----
    @Column
    @Index
    private long totalCpu;             // 逻辑 CPU 总数 (已乘超分比)
    @Column
    @Index
    private long totalMemory;          // 逻辑内存总量 (已乘超分比, bytes)

    // ---- 可分配量 (分配器悲观锁扣减) ----
    @Column
    @Index
    private long availableCpu;
    @Column
    @Index
    private long availableMemory;

    // ---- 监控用 (agent 实时上报) ----
    @Column
    private long availablePhysicalMemory;  // 实际可用物理内存

    // ---- CPU 细粒度 (兼容 HostCapacityVO) ----
    @Column
    private int cpuNum;       // 逻辑 CPU 数 (= totalPhysicalCpu)
    @Column
    private int cpuSockets;   // 物理插槽数
    @Column
    private int cpuCoreNum;   // 物理核心总数

    // ---- 预留 ----
    @Column
    private long reservedMemory;  // 系统预留 (由扩展点动态计算)

    // ---- 磁盘 ----
    @Column
    private long totalDisk;
    @Column
    private long availableDisk;

    // ---- 状态 ----
    @Column
    @Enumerated(EnumType.STRING)
    private CapacityState capacityState;

    // ---- 独占控制 (Allocator 专家建议) ----
    @Column
    private String exclusiveRoleUuid;  // 当前独占角色 UUID, null = 未被独占

    // ---- 调度模式 ----
    @Column
    @Enumerated(EnumType.STRING)
    private SchedulingMode schedulingMode;

    // 不再提供 getTotalCpu() / getTotalMemory() 的计算 getter
    // totalCpu / totalMemory 由 ServerCapacityOverProvisioningManager 预计算写入

    // ----- Getters & Setters (省略) -----
}
```

**v1.1 -> v2.0 变更**:
- 移除 cpuOverprovisioningRatio / memoryOverprovisioningRatio 字段
- 移除 getTotalCpu() / getTotalMemory() 的计算 getter
- 新增 totalCpu / totalMemory 为预计算持久化字段
- 新增 cpuNum / cpuSockets / cpuCoreNum (对齐 HostCapacityVO)
- 新增 availablePhysicalMemory (监控告警用)
- 新增 exclusiveRoleUuid (独占保护)
- 新增 schedulingMode (避免每次都 JOIN RoleVO 查类型)
- 所有可查询字段增加 @Index

---

## 4. 修订后的 AllocateServerMsg 设计

### 4.1 AllocateServerMsg.java (修订)

```java
package org.zstack.header.server;

import org.zstack.header.message.NeedReplyMessage;
import java.util.*;

/**
 * 统一物理服务器分配请求。
 *
 * 设计原则 (v2.0):
 * - 核心层: 物理资源维度的通用字段 (所有角色共用)
 * - 扩展层: extraData Map 承载角色特定数据
 *
 * 使用场景:
 * - KVM: 创建 VM 时分配一台物理 Host
 * - BM1/BM2: 创建 BaremetalInstance 时独占一台物理机
 * - Container: 不使用此 Msg (EXTERNAL_READONLY 模式)
 */
public class AllocateServerMsg extends NeedReplyMessage {

    // ---- 作用域过滤 ----
    private String zoneUuid;
    private List<String> requiredClusterUuids;  // v2.0: 支持多 cluster 候选
    private String serverPoolUuid;

    // ---- 角色过滤 ----
    private String requiredRoleType;     // KVM_HOST / BARE_METAL / BARE_METAL2
    private String serverUuid;           // 指定具体 Server (迁移/绑定场景)

    // ---- 容量需求 ----
    private Long requiredCpu;            // v2.0: Long (nullable), BM 整机分配时为 null
    private Long requiredMemory;         // v2.0: Long (nullable)
    private Long requiredDisk;           // v2.0: 新增

    // ---- 架构过滤 ----
    private String architecture;         // v2.0: 新增 (x86_64, aarch64)

    // ---- 回避/亲和 ----
    private List<String> avoidServerUuids;      // v2.0: 新增 (硬排除)
    private List<String> softAvoidServerUuids;   // v2.0: 新增 (软排除)

    // ---- 策略 ----
    private String allocatorStrategy;    // LEAST_USED / RANDOM / DESIGNATED / ...

    // ---- 控制 ----
    private boolean dryRun;              // true = 只检查不扣减
    private boolean listAll;             // v2.0: true = 返回所有候选 (用于 UI 展示)

    // ---- 扩展上下文 (角色特定) ----
    /**
     * 角色模块通过此 Map 传递特有的过滤条件。
     * 例如:
     * - KVM: "l3NetworkUuids" -> List<String>
     * - KVM: "requiredPrimaryStorageUuids" -> Set<String>
     * - KVM: "vmInstance" -> VmInstanceInventory
     * - BM2: "chassisOfferingUuid" -> String
     * - BM2: "requiredChassisDiskUuid" -> String
     *
     * ServerAllocatorFilterExtensionPoint 的实现方可以读取这些数据。
     */
    private Map<String, Object> extraData;

    // ----- Getters & Setters (省略) -----
}
```

### 4.2 ServerAllocatorSpec.java (修订)

```java
package org.zstack.header.server;

import java.util.*;

/**
 * 分配器内部上下文。由 AllocateServerMsg 转换而来，
 * 在 AllocatorFlow 链中逐步丰富。
 */
public class ServerAllocatorSpec {

    // ---- 从 AllocateServerMsg 复制 ----
    private String zoneUuid;
    private List<String> requiredClusterUuids;
    private String serverPoolUuid;
    private String requiredRoleType;
    private String serverUuid;
    private Long requiredCpu;
    private Long requiredMemory;
    private Long requiredDisk;
    private String architecture;
    private List<String> avoidServerUuids;
    private List<String> softAvoidServerUuids;
    private String allocatorStrategy;
    private boolean dryRun;
    private boolean listAll;
    private Map<String, Object> extraData;

    // ---- 中间结果 ----
    private List<PhysicalServerInventory> candidates;  // 由各 Flow 逐步过滤

    // ---- 运行时上下文 (Flow 间共享) ----
    private Map<String, Object> flowContext = new HashMap<>();

    // ----- Getters & Setters (省略) -----
}
```

### 4.3 ServerAllocatorFilterExtensionPoint.java (新增)

```java
package org.zstack.header.server;

import java.util.List;

/**
 * 分配器过滤扩展点。
 * 角色模块可以注册此扩展点，注入角色特有的过滤逻辑。
 *
 * 例如: KVM 模块注册一个扩展，从 extraData 中读取 l3NetworkUuids，
 * 过滤掉不满足网络可达性的服务器。
 */
public interface ServerAllocatorFilterExtensionPoint {
    /**
     * 在标准 Flow 链执行完毕后，对候选列表做额外过滤。
     *
     * @param spec       分配规格 (含 extraData)
     * @param candidates 当前候选列表
     * @return 过滤后的候选列表 (不可返回 null)
     */
    List<PhysicalServerInventory> filterCandidates(
        ServerAllocatorSpec spec,
        List<PhysicalServerInventory> candidates
    );

    /** 此扩展只对哪种角色类型生效，null = 对所有类型生效 */
    ServerRoleType getApplicableRoleType();
}
```

### 4.4 ServerReservedCapacityExtensionPoint.java (新增)

```java
package org.zstack.header.server;

/**
 * 预留容量扩展点。
 * 各模块可以声明在某台物理服务器上需要预留的内存量。
 * 例如: Ceph Agent 预留 512MB, ZStack Agent 预留 256MB.
 *
 * 参照: HostReservedCapacityExtensionPoint
 */
public interface ServerReservedCapacityExtensionPoint {
    /**
     * 计算需要在该服务器上预留的内存量 (bytes)
     */
    long getReservedMemory(String serverUuid);
}
```

### 4.5 AllocateServerReply.java (修订)

```java
package org.zstack.header.server;

import org.zstack.header.message.MessageReply;
import java.util.List;

public class AllocateServerReply extends MessageReply {
    private String serverUuid;
    private String roleUuid;
    private String roleType;
    private String clusterUuid;  // v2.0: 新增 (从 RoleVO 获取)

    // dryRun=false 且 listAll=false 时: 单个结果
    // listAll=true 时: 所有候选
    private List<PhysicalServerInventory> candidates;  // v2.0: 支持返回候选列表

    // ----- Getters & Setters (省略) -----
}
```

---

## 5. 修订后的状态机设计

### 5.1 PhysicalServerState.java (修订)

```java
package org.zstack.header.server;

import org.zstack.header.exception.CloudRuntimeException;
import java.util.HashMap;
import java.util.Map;

public enum PhysicalServerState {
    Enabled,
    Disabled,
    PreMaintenance,  // v2.0 新增
    Maintenance;

    static {
        Enabled.transactions(
            new Transaction(PhysicalServerStateEvent.enable, Enabled),
            new Transaction(PhysicalServerStateEvent.disable, Disabled),
            new Transaction(PhysicalServerStateEvent.preMaintain, PreMaintenance)
        );
        Disabled.transactions(
            new Transaction(PhysicalServerStateEvent.disable, Disabled),
            new Transaction(PhysicalServerStateEvent.enable, Enabled),
            new Transaction(PhysicalServerStateEvent.preMaintain, PreMaintenance)
        );
        PreMaintenance.transactions(
            new Transaction(PhysicalServerStateEvent.enable, Enabled),
            new Transaction(PhysicalServerStateEvent.disable, Disabled),
            new Transaction(PhysicalServerStateEvent.maintain, Maintenance),
            new Transaction(PhysicalServerStateEvent.preMaintain, PreMaintenance)
        );
        Maintenance.transactions(
            new Transaction(PhysicalServerStateEvent.enable, Enabled),
            new Transaction(PhysicalServerStateEvent.disable, Disabled)
        );
    }

    // ... Transaction / nextState 实现 (与 HostState 完全对齐) ...
}
```

### 5.2 PhysicalServerStateEvent.java (修订)

```java
public enum PhysicalServerStateEvent {
    enable,
    disable,
    preMaintain,  // v2.0 新增
    maintain       // v2.0 新增 (PreMaintenance -> Maintenance)
}
```

### 5.3 PhysicalServerPowerStatus.java (新增)

```java
package org.zstack.header.server;

/**
 * 物理服务器电源状态。通过 OOB (IPMI/Redfish) 查询获取。
 * 与 status(连接状态) 正交。
 */
public enum PhysicalServerPowerStatus {
    /** 物理机已上电 */
    PowerOn,
    /** 物理机已断电 */
    PowerOff,
    /** 无法获取电源状态 (无 OOB 或 OOB 不可达) */
    PowerUnknown
}
```

### 5.4 状态维度对照表

| 维度 | 枚举 | 含义 | 谁控制 | 值 |
|------|------|------|--------|-----|
| state | PhysicalServerState | 管理员意图 | 用户 API | Enabled, Disabled, PreMaintenance, Maintenance |
| status | PhysicalServerStatus | 管理面连接 | 系统自动 | Unknown, Connecting, Connected, Disconnected |
| powerStatus | PhysicalServerPowerStatus | 物理电源 | OOB 查询 | PowerOn, PowerOff, PowerUnknown |
| roleStatus | PhysicalServerRoleVO.roleStatus | 角色运行 | 角色模块 | 自定义字符串 |

### 5.5 分配器对状态的使用

```
ServerStateAllocatorFlow:
  - state == Enabled (排除 Disabled/PreMaintenance/Maintenance)
  - status == Connected (排除 Unknown/Connecting/Disconnected)
  - powerStatus == PowerOn 或 PowerUnknown (排除 PowerOff)
  - schedulingMode != EXTERNAL_READONLY (排除容器)
```

---

## 6. 修订后的 ServerCapacityUpdater 设计

### 6.1 ServerCapacityUpdaterRunnable.java (新增)

```java
package org.zstack.header.server;

/**
 * 回调接口，允许调用方在悲观锁内定制容量更新逻辑。
 * 参照: HostCapacityUpdaterRunnable
 */
public interface ServerCapacityUpdaterRunnable {
    /**
     * 在悲观锁保护下调用。
     *
     * @param cap 当前容量 VO (已加 PESSIMISTIC_WRITE 锁)
     * @return 修改后的 VO (非 null 则 merge)，null 表示放弃本次更新
     */
    ServerCapacityVO call(ServerCapacityVO cap);
}
```

### 6.2 ServerCapacityUpdater.java (修订)

```java
package org.zstack.server.allocator;

import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.transaction.annotation.Transactional;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.DeadlockAutoRestart;
import org.zstack.header.server.ServerCapacityVO;
import org.zstack.header.server.ServerCapacityUpdaterRunnable;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import javax.persistence.LockModeType;

/**
 * 悲观锁容量更新器。严格遵循 HostCapacityUpdater 的两层模式。
 *
 * 关键修正 (v2.0):
 * - @Transactional 和 @DeadlockAutoRestart 分别在不同方法上
 * - 使用回调模式 (ServerCapacityUpdaterRunnable) 而非硬编码扣减
 */
@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class ServerCapacityUpdater {
    private static final CLogger logger = Utils.getLogger(ServerCapacityUpdater.class);

    @Autowired
    private DatabaseFacade dbf;

    private String serverUuid;
    private ServerCapacityVO capacityVO;
    private ServerCapacityVO originalCopy;

    public ServerCapacityUpdater(String serverUuid) {
        this.serverUuid = serverUuid;
    }

    private boolean lockCapacity() {
        capacityVO = dbf.getEntityManager().find(
            ServerCapacityVO.class, serverUuid, LockModeType.PESSIMISTIC_WRITE
        );

        if (capacityVO != null) {
            originalCopy = new ServerCapacityVO();
            originalCopy.setTotalCpu(capacityVO.getTotalCpu());
            originalCopy.setAvailableCpu(capacityVO.getAvailableCpu());
            originalCopy.setTotalMemory(capacityVO.getTotalMemory());
            originalCopy.setAvailableMemory(capacityVO.getAvailableMemory());
            originalCopy.setTotalPhysicalMemory(capacityVO.getTotalPhysicalMemory());
            originalCopy.setAvailablePhysicalMemory(capacityVO.getAvailablePhysicalMemory());
        }

        return capacityVO != null;
    }

    private void logDeletedServer() {
        logger.warn(String.format(
            "[Server Capacity] unable to update capacity for server[uuid:%s]. " +
            "It may have been deleted.", serverUuid));
    }

    private void logCapacityChange() {
        if (logger.isTraceEnabled()) {
            logger.trace(String.format(
                "[Server Capacity] capacity changed for server[uuid:%s]:\n" +
                "total cpu: %s -> %s\n" +
                "available cpu: %s -> %s\n" +
                "total memory: %s -> %s\n" +
                "available memory: %s -> %s",
                serverUuid,
                originalCopy.getTotalCpu(), capacityVO.getTotalCpu(),
                originalCopy.getAvailableCpu(), capacityVO.getAvailableCpu(),
                originalCopy.getTotalMemory(), capacityVO.getTotalMemory(),
                originalCopy.getAvailableMemory(), capacityVO.getAvailableMemory()));
        }
    }

    // 内层: @Transactional (事务管理)
    @Transactional
    private boolean _run(ServerCapacityUpdaterRunnable runnable) {
        if (!lockCapacity()) {
            logDeletedServer();
            return false;
        }

        ServerCapacityVO result = runnable.call(capacityVO);
        if (result != null) {
            capacityVO = result;
            dbf.getEntityManager().merge(capacityVO);
            logCapacityChange();
            return true;
        }
        return false;
    }

    // 外层: @DeadlockAutoRestart (死锁重试)
    @DeadlockAutoRestart
    public boolean run(ServerCapacityUpdaterRunnable runnable) {
        return _run(runnable);
    }
}
```

### 6.3 典型使用方式

```java
// 共享角色扣减 (KVM 创建 VM)
new ServerCapacityUpdater(serverUuid).run(cap -> {
    if (cap.getAvailableCpu() < requiredCpu) {
        throw new UnableToReserveCapacityException(operr("Not enough CPU"));
    }
    if (cap.getAvailableMemory() < requiredMemory) {
        throw new UnableToReserveCapacityException(operr("Not enough memory"));
    }
    cap.setAvailableCpu(cap.getAvailableCpu() - requiredCpu);
    cap.setAvailableMemory(cap.getAvailableMemory() - requiredMemory);
    return cap;
});

// 独占角色分配 (BM 整机分配)
new ServerCapacityUpdater(serverUuid).run(cap -> {
    if (cap.getExclusiveRoleUuid() != null) {
        throw new UnableToReserveCapacityException(operr("Server already exclusively allocated"));
    }
    cap.setExclusiveRoleUuid(roleUuid);
    cap.setAvailableCpu(0);
    cap.setAvailableMemory(0);
    cap.setAvailableDisk(0);
    cap.setCapacityState(CapacityState.Overloaded);
    return cap;
});

// 外部调度同步 (Container 事后同步)
new ServerCapacityUpdater(serverUuid).run(cap -> {
    if (cap.getSchedulingMode() != SchedulingMode.EXTERNAL_READONLY) {
        return null;  // 拒绝非外部调度模式的同步写入
    }
    cap.setAvailableCpu(syncedAvailableCpu);
    cap.setAvailableMemory(syncedAvailableMemory);
    cap.setAvailablePhysicalMemory(syncedAvailablePhysMem);
    return cap;
});
```

---

## 7. 修订后的 API 消息

### 7.1 APIRegisterPhysicalServerMsg.java (修订)

```java
@TagResourceType(PhysicalServerVO.class)
@Action(category = PhysicalServerConstant.ACTION_CATEGORY)
@RestRequest(
    path = "/physical-servers",
    method = HttpMethod.POST,
    responseClass = APIRegisterPhysicalServerEvent.class,
    parameterName = "params"
)
public class APIRegisterPhysicalServerMsg extends APICreateMessage {

    @APIParam(resourceType = ZoneVO.class)
    private String zoneUuid;

    @APIParam(maxLength = 255)
    private String name;

    @APIParam(required = false, maxLength = 2048)
    private String description;

    // v2.0: OOB 字段全部 required = false (Container 专家要求)
    @APIParam(required = false, maxLength = 255)
    private String oobAddress;

    @APIParam(required = false)
    private Integer oobPort;

    @APIParam(required = false, maxLength = 255)
    private String oobUsername;

    @APIParam(required = false, maxLength = 255)
    private String oobPassword;

    @APIParam(required = false, validValues = {"IPMI", "REDFISH", "NONE"})
    private String oobManagementType;  // v2.0: 增加 NONE

    @APIParam(required = false, resourceType = ServerPoolVO.class)
    private String serverPoolUuid;

    @APIParam(required = false, maxLength = 255)
    private String managementIp;

    @APIParam(required = false, maxLength = 255)
    private String serialNumber;  // v2.0: 允许注册时提供

    @APIParam(required = false, validValues = {"x86_64", "aarch64", "mips64el", "loongarch64"})
    private String architecture;  // v2.0: 新增

    // ----- Getters & Setters (省略) -----
}
```

**v1.1 -> v2.0 变更**:
- OOB 字段全部改为 `required = false`
- oobManagementType 增加 `NONE`
- 新增 serialNumber 和 architecture 参数

---

## 8. 修订后的 PhysicalServerRoleProvider SPI

```java
package org.zstack.header.server;

/**
 * 角色数据提供者 SPI (v2.0)。
 * 各角色模块实现此接口，注册到 PluginRegistry。
 */
public interface PhysicalServerRoleProvider {

    /** 角色类型标识 */
    ServerRoleType getRoleType();

    /**
     * 通过角色资源 UUID 查找物理服务器关联。
     * 不存在返回 null。
     */
    PhysicalServerRoleVO findRoleAssociation(String roleUuid);

    /**
     * 获取角色的连接状态。
     * @return 状态字符串 (如 "Connected", "Available", "Ready")
     */
    String getRoleStatus(String roleUuid);

    /**
     * 获取角色的容量使用摘要 (只读视图)。
     * @return null 表示该角色不管理容量
     */
    ServerCapacitySummary getCapacitySummary(String roleUuid);

    /**
     * v2.0 新增: 获取角色的 cluster UUID。
     * 分配时通过此方法获取角色的集群归属。
     */
    String getClusterUuid(String roleUuid);

    /**
     * v2.0 新增: 获取角色的管理来源 UUID。
     * Container: endpointUuid
     * BM1: pxeServerUuid
     * 其他: null
     */
    String getSourceUuid(String roleUuid);

    /**
     * v2.0 新增: 获取角色资源的实际使用量。
     * 用于容量对账 (RecalculateServerCapacityMsg)。
     */
    ServerCapacityUsage getActualUsage(String roleUuid);

    /** 容量摘要 (只读视图) */
    class ServerCapacitySummary {
        public long totalCpu;
        public long usedCpu;
        public long totalMemoryBytes;
        public long usedMemoryBytes;
        public long totalDiskBytes;
        public long usedDiskBytes;
    }

    /** 实际使用量 (用于对账) */
    class ServerCapacityUsage {
        public long usedCpu;
        public long usedMemoryBytes;
    }
}
```

---

## 9. 修订后的角色关联策略

### 9.1 KVM Host

| 项目 | 设计 |
|------|------|
| 监听扩展点 | `HostAfterConnectedExtensionPoint` |
| 匹配方式 | `managementIp` + `zoneUuid` (KVM 专家建议加 zone 联合条件) |
| 关联时机 | Host 首次 Connected |
| clusterUuid | 从 HostVO.clusterUuid 同步 |

### 9.2 Container (NativeHost)

| 项目 | 设计 |
|------|------|
| 监听扩展点 | 新增 `NativeHostSyncedExtensionPoint` (方案 B) |
| 匹配方式 | `managementIp` (K8s InternalIP) + `zoneUuid` |
| 关联时机 | Endpoint 同步完成后 |
| sourceUuid | ContainerManagementEndpointVO.uuid |
| clusterUuid | NativeClusterVO.uuid |

**注意**: 不使用 `HostAfterConnectedExtensionPoint`，因为 Container 专家明确指出 NativeFactory.createHost() 抛 UnsupportedOperationException，且整个 container 模块不触发该扩展点。

### 9.3 Baremetal V1

| 项目 | 设计 |
|------|------|
| 监听扩展点 | 新增 `BaremetalChassisCreateExtensionPoint` 或使用 EventFacade |
| 匹配方式 | `oobAddress` + `oobPort` (BM1 专家要求组合匹配) |
| 关联时机 | Chassis 创建后 |
| sourceUuid | pxeServerUuid |
| clusterUuid | BaremetalChassisVO.clusterUuid |

### 9.4 Baremetal V2

| 项目 | 设计 |
|------|------|
| 监听扩展点 | 新增 `BareMetal2ChassisCreateExtensionPoint` 或使用 EventFacade |
| 匹配方式 | `oobAddress` + `oobPort` |
| 关联时机 | 由 BM2 RoleProvider 自行决定 (可选: 创建时或发现后) |
| clusterUuid | BareMetal2ChassisAO.clusterUuid |

---

## 10. 新增/修改的文件清单

### 10.1 Phase 1 新增文件 (~45 个)

```
header/src/main/java/org/zstack/header/server/
├── PhysicalServerAO.java                      # [修订] 新增 powerStatus, NONE
├── PhysicalServerVO.java                      # [修订] 表名修正, LAZY fetch
├── PhysicalServerInventory.java               # [修订] 新增 powerStatus 字段
├── PhysicalServerState.java                   # [修订] 新增 PreMaintenance
├── PhysicalServerStatus.java                  # [保持]
├── PhysicalServerPowerStatus.java             # [新增] 电源状态枚举
├── PhysicalServerStateEvent.java              # [修订] 新增 preMaintain, maintain
├── PhysicalServerStatusEvent.java             # [保持]
├── PhysicalServerConstant.java                # [保持]
├── PhysicalServerMessage.java                 # [保持]
│
├── ServerPoolVO.java                          # [保持]
├── ServerPoolInventory.java                   # [保持]
├── ServerPoolState.java                       # [保持]
│
├── PhysicalServerRoleVO.java                  # [修订] 新增 clusterUuid, sourceUuid, roleStatus
├── PhysicalServerRoleInventory.java           # [修订] 对应字段
├── ServerRoleType.java                        # [修订] 引入 SchedulingMode
├── SchedulingMode.java                        # [新增] 调度模式枚举
│
├── ServerHardwareInfoVO.java                  # [修订] 新增 bootMode, discoverySource
├── ServerHardwareInfoInventory.java           # [修订] 对应字段
├── ServerHardwareDetailVO.java                # [新增] 1:N 硬件详情子表
├── ServerHardwareDetailInventory.java         # [新增] 详情 DTO
├── HardwareDetailType.java                    # [新增] 详情类型枚举
│
├── ServerCapacityVO.java                      # [修订] 预计算字段, 移除 ratio getter
├── ServerCapacityInventory.java               # [修订] 对应字段
├── CapacityState.java                         # [保持]
│
├── AllocateServerMsg.java                     # [修订] 核心字段 + extraData
├── AllocateServerReply.java                   # [修订] 新增 clusterUuid, candidates
├── ServerAllocatorSpec.java                   # [修订] 对应字段 + flowContext
├── ServerAllocatorFlow.java                   # [保持]
├── ServerSortorFlow.java                      # [保持]
├── ServerCapacityUpdaterRunnable.java         # [新增] 回调接口
├── ServerAllocatorFilterExtensionPoint.java   # [新增] 分配过滤扩展点
├── ServerReservedCapacityExtensionPoint.java  # [新增] 预留容量扩展点
├── ServerAllocatorCompatibilityBridge.java    # [新增] 兼容层接口
├── ServerCapacityOverProvisioningManager.java # [新增] 超分比管理接口
├── RecalculateServerCapacityMsg.java          # [新增] 重算触发消息
│
├── PhysicalServerRoleProvider.java            # [修订] 新增 getClusterUuid 等方法
│
├── APIRegisterPhysicalServerMsg.java          # [修订] OOB optional, 新增字段
├── APIRegisterPhysicalServerEvent.java        # [保持]
├── APIQueryPhysicalServerMsg.java             # [保持]
├── APIQueryPhysicalServerReply.java           # [保持]
├── APIUpdatePhysicalServerMsg.java            # [修订] 新增 powerStatus 操作
├── APIUpdatePhysicalServerEvent.java          # [保持]
├── APIDeletePhysicalServerMsg.java            # [保持]
├── APIDeletePhysicalServerEvent.java          # [保持]
├── APICreateServerPoolMsg.java                # [保持]
├── APICreateServerPoolEvent.java              # [保持]
├── APIQueryServerPoolMsg.java                 # [保持]
├── APIQueryServerPoolReply.java               # [保持]
├── APIDeleteServerPoolMsg.java                # [保持]
└── APIDeleteServerPoolEvent.java              # [保持]
```

### 10.2 配置文件

```
conf/springConfigXml/PhysicalServer.xml        # Spring bean 配置
conf/db/upgrade/V5.5.7__schema.sql             # DB 迁移脚本 (修订)
conf/globalConfig/physicalServer.xml            # 全局配置 (超分比, 特性开关)
```

### 10.3 DDL 变更要点

```sql
-- 表名修正: PhysicalServerVO (不是 PhysicalServerAO)
CREATE TABLE IF NOT EXISTS `PhysicalServerVO` (
    -- ... 原有字段 ...
    `powerStatus` varchar(32) NOT NULL DEFAULT 'PowerUnknown',  -- v2.0 新增
    -- state 默认值不变，增加 PreMaintenance 为合法值
);

-- PhysicalServerRoleVO 新增字段
ALTER TABLE `PhysicalServerRoleVO`
    ADD COLUMN `clusterUuid` varchar(32) DEFAULT NULL,
    ADD COLUMN `sourceUuid` varchar(32) DEFAULT NULL,
    ADD COLUMN `roleStatus` varchar(64) DEFAULT NULL;

-- ServerHardwareInfoVO 新增字段
ALTER TABLE `ServerHardwareInfoVO`
    ADD COLUMN `bootMode` varchar(32) DEFAULT NULL,
    ADD COLUMN `discoverySource` varchar(32) DEFAULT NULL;

-- ServerCapacityVO 重设计
CREATE TABLE IF NOT EXISTS `ServerCapacityVO` (
    `uuid` varchar(32) NOT NULL,
    `totalPhysicalCpu` bigint NOT NULL DEFAULT 0,
    `totalPhysicalMemory` bigint NOT NULL DEFAULT 0,
    `totalCpu` bigint NOT NULL DEFAULT 0,          -- 预计算
    `totalMemory` bigint NOT NULL DEFAULT 0,        -- 预计算
    `availableCpu` bigint NOT NULL DEFAULT 0,
    `availableMemory` bigint NOT NULL DEFAULT 0,
    `availablePhysicalMemory` bigint NOT NULL DEFAULT 0,
    `cpuNum` int NOT NULL DEFAULT 0,
    `cpuSockets` int NOT NULL DEFAULT 0,
    `cpuCoreNum` int NOT NULL DEFAULT 0,
    `reservedMemory` bigint NOT NULL DEFAULT 0,
    `totalDisk` bigint NOT NULL DEFAULT 0,
    `availableDisk` bigint NOT NULL DEFAULT 0,
    `capacityState` varchar(32) NOT NULL DEFAULT 'Initialized',
    `exclusiveRoleUuid` varchar(32) DEFAULT NULL,
    `schedulingMode` varchar(32) DEFAULT NULL,
    PRIMARY KEY (`uuid`),
    KEY `idxServerCapacityVOTotalCpu` (`totalCpu`),
    KEY `idxServerCapacityVOAvailCpu` (`availableCpu`),
    KEY `idxServerCapacityVOTotalMem` (`totalMemory`),
    KEY `idxServerCapacityVOAvailMem` (`availableMemory`),
    CONSTRAINT `fkServerCapacityVOPhysicalServerVO` FOREIGN KEY (`uuid`)
        REFERENCES `PhysicalServerVO` (`uuid`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- 新增硬件详情子表
CREATE TABLE IF NOT EXISTS `ServerHardwareDetailVO` (
    `id` bigint unsigned NOT NULL AUTO_INCREMENT,
    `serverUuid` varchar(32) NOT NULL,
    `type` varchar(32) NOT NULL,
    `content` TEXT,
    `createDate` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00',
    `lastOpDate` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00' ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idxServerHardwareDetailVOServerUuid` (`serverUuid`),
    KEY `idxServerHardwareDetailVOType` (`type`),
    CONSTRAINT `fkServerHardwareDetailVOPhysicalServerVO` FOREIGN KEY (`serverUuid`)
        REFERENCES `PhysicalServerVO` (`uuid`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
```

---

## 11. GlobalConfig 定义

```xml
<!-- conf/globalConfig/physicalServer.xml -->
<globalConfig>
    <!-- 统一分配器特性开关 -->
    <config>
        <category>physicalServer</category>
        <name>allocator.enabled</name>
        <description>Enable unified physical server allocator</description>
        <defaultValue>false</defaultValue>
        <type>java.lang.Boolean</type>
    </config>

    <!-- 默认 CPU 超分比 -->
    <config>
        <category>physicalServer</category>
        <name>cpu.overProvisioning.ratio</name>
        <description>Default CPU over-provisioning ratio</description>
        <defaultValue>10</defaultValue>
        <type>java.lang.Double</type>
    </config>

    <!-- 默认内存超分比 -->
    <config>
        <category>physicalServer</category>
        <name>memory.overProvisioning.ratio</name>
        <description>Default memory over-provisioning ratio</description>
        <defaultValue>1</defaultValue>
        <type>java.lang.Double</type>
    </config>

    <!-- 容量对账定时任务间隔 (秒) -->
    <config>
        <category>physicalServer</category>
        <name>capacity.reconciliation.interval</name>
        <description>Interval in seconds for capacity reconciliation</description>
        <defaultValue>3600</defaultValue>
        <type>java.lang.Long</type>
    </config>
</globalConfig>
```

---

## 12. 关键设计变更总结

### 12.1 相对于 v1.1 的变更清单

| # | 变更 | 影响范围 | 来源 |
|---|------|---------|------|
| 1 | PhysicalServerAO 新增 powerStatus | AO/VO/Inventory/DDL | BM2 P0 |
| 2 | PhysicalServerState 新增 PreMaintenance | State/Event 枚举 | KVM P0 |
| 3 | OOB 字段全部 nullable，新增 NONE 类型 | AO/API Msg | Container P0 |
| 4 | PhysicalServerRoleVO 新增 clusterUuid/sourceUuid/roleStatus | RoleVO/Inventory/DDL | 4/5 专家共识 |
| 5 | ServerRoleType 引入 SchedulingMode | 枚举重构 | Container P0 |
| 6 | ServerCapacityVO 移除 ratio getter，改为预计算字段 | VO/DDL/Updater | Allocator P0 |
| 7 | ServerCapacityVO 补齐 cpuNum/cpuSockets/cpuCoreNum/availPhysMem | VO/DDL | KVM + Allocator |
| 8 | ServerCapacityVO 新增 exclusiveRoleUuid/schedulingMode | VO/DDL | Allocator 建议 |
| 9 | ServerCapacityUpdater 拆分 @Transactional/@DeadlockAutoRestart | 实现层 | Allocator P0(编译错误) |
| 10 | ServerCapacityUpdater 改用 Runnable 回调模式 | 接口设计 | Allocator 建议 |
| 11 | AllocateServerMsg 增加 avoidServerUuids/architecture/extraData | Msg/Spec | 3/5 专家共识 |
| 12 | 新增 ServerHardwareDetailVO (1:N 详情子表) | 新文件+DDL | BM1 P1 |
| 13 | ServerHardwareInfoVO 新增 bootMode/discoverySource | VO/DDL | BM1 P1 |
| 14 | 新增 ServerAllocatorFilterExtensionPoint | 新接口 | Allocator 建议 |
| 15 | 新增 ServerReservedCapacityExtensionPoint | 新接口 | Allocator 建议 |
| 16 | 新增 ServerCapacityOverProvisioningManager | 新接口 | Allocator P0 |
| 17 | 新增 ServerAllocatorCompatibilityBridge | 新接口(Phase 1 定义) | KVM P0 |
| 18 | 新增 RecalculateServerCapacityMsg | 新消息 | Allocator 建议 |
| 19 | PhysicalServerVO 移除 EAGER fetch | VO 变更 | KVM + Allocator |
| 20 | DDL 表名 PhysicalServerAO -> PhysicalServerVO | DDL 修正 | KVM + Allocator |
| 21 | Container 角色关联改用新扩展点 | 关联策略 | Container P0 |
| 22 | GlobalConfig + 特性开关 | 配置文件 | Allocator 建议 |

### 12.2 架构原则执行情况

| 原则 | 执行 |
|------|------|
| cluster 是角色语义不是服务器语义 | PhysicalServerRoleVO 持有 clusterUuid，不在 AO 层 |
| 调度差异通过统一模型包容 | SchedulingMode 枚举统一描述四种调度行为 |
| 硬件信息分层存储 | 1:1 汇总 + 1:N 详情，快速查询和深度查询分离 |
| 状态正交分离 | state/status/powerStatus 三维 + roleStatus 角色层 |
| 超分比与 DB 查询兼容 | 预计算持久化，独立 Manager 管理 |
| 不膨胀核心接口 | AllocateServerMsg 核心字段 + extraData 扩展 |
| 兼容层提前验证 | Phase 1 定义接口 + POC |

### 12.3 被明确拒绝的请求

| 请求 | 来源 | 拒绝理由 |
|------|------|---------|
| PhysicalServerAO 增加 clusterUuid | BM2 | 违反多角色架构，cluster 是角色语义 |
| PhysicalServerStatus 增加 Discovering/DiscoveryFailed | BM1 | 发现是角色流程，不是服务器持久状态 |
| AllocateServerMsg 搬入所有 AllocateHostMsg 字段 | - | 使用 extraData 避免接口膨胀 |
| Gateway 概念纳入统一层 | BM2 | Gateway 是 BM2 实现细节，不进统一接口 |
| chassisOfferingUuid 纳入统一层 | BM2 | Offering 是业务概念，通过 extraData 传递 |
| provisionType 纳入 PhysicalServerAO | BM2 | 部署模式是角色实现细节 |

---

## 13. Phase 分工修订

### Phase 1 (Header 定义 + 编译验证)
- 所有 VO、枚举、Inventory、API 消息的 Java 代码
- 所有 SPI 接口定义
- DDL 迁移脚本
- Spring XML 配置
- GlobalConfig 配置
- 编译验证: `mvn clean compile -pl header`
- 兼容层接口定义 + POC 桩

### Phase 2 (实现)
- PhysicalServerManagerImpl (Service 实现)
- ServerAllocatorManagerImpl (分配链实现)
- ServerCapacityOverProvisioningManagerImpl
- ServerCapacityUpdater (完整实现)
- 角色关联钩子 (4 种角色各自的 RoleProvider)
- 兼容层完整实现
- 容量对账定时任务

### Phase 3 (灰度切换)
- 特性开关 physicalServer.allocator.enabled = true
- AllocateHostMsg -> AllocateServerMsg 的透明切换
- 数据迁移工具 (现有 Host -> PhysicalServer 初始化)
- 灰度策略: 先 BM2 (最独立) -> BM1 -> KVM (最复杂)

---

**裁决状态**: 终稿。此文档为 Phase 1 实施的权威设计依据。
