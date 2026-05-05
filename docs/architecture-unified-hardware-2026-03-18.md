# ZStack 统一硬件管理 — 架构设计文档

**版本**: v1.0
**日期**: 2026-03-18
**作者**: Lead Architect
**输入**: PRD v1.0 (33 FRs, 10 NFRs, 7 Epics) + Product Brief v1.0 + 群总架构审查结论

---

## 第 1 章：架构总纲

### 1.1 三层架构

```
┌─────────────────────────────────────────────────────────────────┐
│                    Consumer Layer (消费层)                        │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌───────────────────┐   │
│  │ VM调度器  │ │ BM实例   │ │ BM2实例  │ │ K8s外部调度器     │   │
│  │(现有)     │ │(现有)    │ │(现有)    │ │(现有)             │   │
│  └──────────┘ └──────────┘ └──────────┘ └───────────────────┘   │
├─────────────────────────────────────────────────────────────────┤
│                     Role Layer (角色层)                           │
│  ┌──────────┐ ┌──────────────┐ ┌──────────────┐ ┌────────────┐ │
│  │HostVO    │ │BaremetalV1   │ │BareMetal2    │ │NativeHostVO│ │
│  │KVMHostVO │ │ChassisVO     │ │ChassisVO     │ │(Container) │ │
│  └────┬─────┘ └──────┬───────┘ └──────┬───────┘ └─────┬──────┘ │
│       │               │                │               │        │
│  ┌────┴───────────────┴────────────────┴───────────────┴─────┐  │
│  │              PhysicalServerRoleVO (角色映射)               │  │
│  └────────────────────────────┬───────────────────────────────┘  │
├───────────────────────────────┼──────────────────────────────────┤
│                Physical Layer (物理层)                            │
│  ┌────────────────────────────┴───────────────────────────────┐  │
│  │              PhysicalServerVO (统一物理服务器)              │  │
│  ├────────────────────────────────────────────────────────────┤  │
│  │  PhysicalServerCapacityVO    │ PhysicalServerHardwareInfoVO│  │
│  │  PhysicalServerHardwareDetailVO                            │  │
│  ├────────────────────────────────────────────────────────────┤  │
│  │  SPI: PhysicalServerRoleProvider                           │  │
│  │  SPI: PowerManageable / HardwareDiscoverable               │  │
│  ├────────────────────────────────────────────────────────────┤  │
│  │  ServerAllocatorChain (统一分配引擎)                       │  │
│  │  ServerPoolVO / PhysicalServerProvisionNetworkVO            │  │
│  └────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### 1.2 各层职责边界

| 层 | 职责 | 不做什么 |
|---|------|---------|
| **Physical Layer** | 物理服务器唯一标识、硬件信息、OOB 管理、统一容量账本、分配引擎、SPI 定义 | 不了解具体角色的业务逻辑（VM 调度、BM 装机细节等） |
| **Role Layer** | 现有 HostVO/ChassisVO 等角色实体，通过 PhysicalServerRoleVO 映射到 Physical Layer | 不修改任何现有 VO/API，只在生命周期钩子中单向创建 RoleVO |
| **Consumer Layer** | VM 调度、BM 实例管理、K8s 调度等现有业务逻辑 | 完全不感知 PhysicalServerVO 的存在（通过 CompatibilityBridge 透明代理） |

### 1.3 模块在 ZStack 项目中的位置

**新建独立的 `server/` 顶层模块**，与 `compute/`、`network/`、`storage/` 同级。

理由：
1. PhysicalServer 是独立于 Host 继承链的新概念，放在 `compute/` 会造成概念混淆
2. 分配引擎 (ServerAllocatorChain) 与现有 HostAllocatorChain 平行，需要独立的命名空间
3. 保持 `header/` 中接口定义和 `server/` 中实现的分离模式，符合 ZStack 现有惯例

```
zstack/
├── header/src/main/java/org/zstack/header/
│   └── server/                          # 新增：接口、VO、API 消息、SPI 定义
│       ├── PhysicalServerAO.java
│       ├── PhysicalServerVO.java
│       ├── PhysicalServerRoleVO.java
│       ├── PhysicalServerCapacityVO.java
│       ├── PhysicalServerHardwareInfoVO.java
│       ├── PhysicalServerHardwareDetailVO.java
│       ├── PhysicalServerProvisionNetworkVO.java
│       ├── PhysicalServerProvisionNetworkClusterRefVO.java
│       ├── ServerPoolVO.java
│       ├── PhysicalServerRoleProvider.java      # SPI
│       ├── PowerManageable.java                 # SPI
│       ├── HardwareDiscoverable.java            # SPI
│       ├── ServerAllocatorFilterExtensionPoint.java
│       ├── ServerReservedCapacityExtensionPoint.java
│       ├── AllocateServerMsg.java
│       ├── AllocateServerReply.java
│       ├── APIAddPhysicalServerMsg.java
│       ├── APIDeletePhysicalServerMsg.java
│       ├── APIQueryPhysicalServerMsg.java
│       ├── APIPowerManagePhysicalServerMsg.java
│       ├── APIDiscoverPhysicalServerHardwareMsg.java
│       └── enums/
│           ├── ServerRoleType.java
│           ├── SchedulingMode.java
│           ├── PhysicalServerState.java
│           ├── PhysicalServerStatus.java
│           ├── PhysicalServerPowerStatus.java
│           ├── OobManagementType.java
│           ├── HardwareDetailType.java
│           └── ProvisionNetworkType.java
├── server/                              # 新增：实现模块
│   ├── pom.xml
│   └── src/main/java/org/zstack/server/
│       ├── PhysicalServerManagerImpl.java
│       ├── allocator/
│       │   ├── ServerAllocatorChain.java
│       │   ├── ServerAllocatorChainBuilder.java
│       │   ├── AbstractServerAllocatorFlow.java
│       │   ├── ZoneFilterFlow.java
│       │   ├── ClusterFilterFlow.java
│       │   ├── PoolFilterFlow.java
│       │   ├── RoleTypeFilterFlow.java
│       │   ├── StatusFilterFlow.java
│       │   ├── AvoidServerFilterFlow.java
│       │   └── CapacityFilterFlow.java
│       ├── capacity/
│       │   ├── PhysicalServerCapacityUpdater.java
│       │   └── ServerCapacityOverProvisioningManagerImpl.java
│       └── compatibility/
│           └── ServerAllocatorCompatibilityBridge.java
```

---

## 第 2 章：数据模型（VO 定义）

### 2.1 枚举定义

```java
package org.zstack.header.server.enums;

/**
 * 物理服务器可承载的角色类型。
 * 每新增一种角色，在此枚举添加值并实现 PhysicalServerRoleProvider SPI。
 */
public enum ServerRoleType {
    KVM_HOST,
    BAREMETAL_V1,
    BAREMETAL_V2,
    CONTAINER_HOST
}
```

```java
package org.zstack.header.server.enums;

/**
 * 调度模式，决定分配引擎如何处理此角色的容量。
 *
 * INTERNAL_SHARED   — ZStack 内部分配，支持超分（典型：KVM）
 * INTERNAL_EXCLUSIVE — ZStack 内部分配，整机独占（典型：BM）
 * EXTERNAL_READONLY  — 不通过 ZStack 分配，但容量消耗计入 PhysicalServerCapacityVO.available（典型：K8s）
 */
public enum SchedulingMode {
    INTERNAL_SHARED,
    INTERNAL_EXCLUSIVE,
    EXTERNAL_READONLY
}
```

```java
package org.zstack.header.server.enums;

/**
 * 管理员控制的管理状态。
 * 对齐 HostState，但独立定义以解耦。
 */
public enum PhysicalServerState {
    Enabled,
    Disabled,
    Maintenance
}
```

```java
package org.zstack.header.server.enums;

/**
 * 系统检测的连接状态。
 * 由 OOB 心跳或 agent 探测自动更新。
 */
public enum PhysicalServerStatus {
    Connecting,
    Connected,
    Disconnected
}
```

```java
package org.zstack.header.server.enums;

/**
 * OOB 电源状态，通过 IPMI/Redfish 查询。
 */
public enum PhysicalServerPowerStatus {
    PowerOn,
    PowerOff,
    Unknown
}
```

```java
package org.zstack.header.server.enums;

/**
 * 带外管理协议类型。
 */
public enum OobManagementType {
    IPMI,
    REDFISH
}
```

```java
package org.zstack.header.server.enums;

/**
 * 硬件明细类型。
 */
public enum HardwareDetailType {
    CPU,
    MEMORY,
    DISK,
    NIC,
    GPU
}
```

```java
package org.zstack.header.server.enums;

/**
 * 装机网络类型。
 * STANDALONE_PXE — 独立 PXE 服务器模式（BM1）
 * GATEWAY_PXE    — 网关代理模式（BM2）
 */
public enum ProvisionNetworkType {
    STANDALONE_PXE,
    GATEWAY_PXE
}
```

### 2.2 PhysicalServerAO / PhysicalServerVO

**设计决策**：采用 AO/VO 两层模式（`@MappedSuperclass` → `@Entity`），**不使用 EO 软删除模式**，直接硬删除。所有新建 VO 统一不用 EO，理由：PhysicalServer 删除前已通过角色检查（所有 RoleVO 必须为 Stale），不需要软删除回收站；硬删除简化数据模型，减少 JOIN 开销（无需 `WHERE deleted IS NULL` 过滤）。PhysicalServerAO 继承 ResourceVO 以获得 uuid 管理能力。

```java
package org.zstack.header.server;

import org.zstack.header.server.enums.*;
import org.zstack.header.vo.ForeignKey;
import org.zstack.header.vo.ForeignKey.ReferenceOption;
import org.zstack.header.vo.Index;
import org.zstack.header.vo.ResourceVO;
import org.zstack.header.zone.ZoneVO;
import org.zstack.header.core.encrypt.EncryptColumn;
import org.zstack.core.convert.PasswordConverter;

import javax.persistence.*;
import java.sql.Timestamp;

/**
 * 统一物理服务器抽象实体 (Abstract Object)。
 *
 * 独立于 HostVO 继承链，以 serialNumber 作为硬件唯一主键。
 * OOB 管理凭据存储在此层，所有角色共享。
 * 不使用 EO 软删除模式，直接硬删除。
 */
@MappedSuperclass
public class PhysicalServerAO extends ResourceVO {

    @Column
    @ForeignKey(parentEntityClass = ZoneVO.class, onDeleteAction = ReferenceOption.RESTRICT)
    private String zoneUuid;

    @Column
    @ForeignKey(parentEntityClass = ServerPoolVO.class, onDeleteAction = ReferenceOption.RESTRICT)
    private String poolUuid;

    @Column
    private String name;

    @Column
    private String description;

    @Column
    @Index
    private String managementIp;

    @Column
    private String architecture;

    @Column
    @Index
    private String serialNumber;

    @Column
    private String manufacturer;

    @Column
    private String model;

    @Column
    @Enumerated(EnumType.STRING)
    private PhysicalServerState state;

    @Column
    @Enumerated(EnumType.STRING)
    private PhysicalServerStatus status;

    @Column
    @Enumerated(EnumType.STRING)
    private PhysicalServerPowerStatus powerStatus;

    // ---- OOB 带外管理凭据 ----

    @Column
    @Enumerated(EnumType.STRING)
    private OobManagementType oobManagementType;

    @Column
    private String oobAddress;

    @Column
    private Integer oobPort;

    @Column
    private String oobUsername;

    @EncryptColumn
    @Column
    @Convert(converter = PasswordConverter.class)
    private String oobPassword;

    @Column
    private Timestamp createDate;

    @Column
    private Timestamp lastOpDate;

    @PreUpdate
    private void preUpdate() {
        lastOpDate = null;
    }

    // getter/setter 省略，与字段一一对应
}
```

```java
package org.zstack.header.server;

import org.zstack.header.tag.AutoDeleteTag;
import org.zstack.header.vo.BaseResource;
import org.zstack.header.vo.EntityGraph;
import org.zstack.header.vo.NoView;
import org.zstack.header.zone.ZoneVO;

import javax.persistence.*;

/**
 * 统一物理服务器视图实体。
 *
 * 通过 @OneToOne 关联容量表和硬件信息表（与 HostVO 关联 HostCapacityVO 的模式完全一致）。
 * UNIQUE(zoneUuid, serialNumber) 保证同一 Zone 下序列号唯一。
 * 不使用 EO 软删除模式，直接硬删除。
 */
@Entity
@Table(uniqueConstraints = {
    @UniqueConstraint(name = "uk_zone_serial", columnNames = {"zoneUuid", "serialNumber"})
})
@AutoDeleteTag
@BaseResource
@EntityGraph(
    parents = {
        @EntityGraph.Neighbour(type = ZoneVO.class, myField = "zoneUuid", targetField = "uuid"),
        @EntityGraph.Neighbour(type = ServerPoolVO.class, myField = "poolUuid", targetField = "uuid"),
    }
)
public class PhysicalServerVO extends PhysicalServerAO {

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "uuid")
    @NoView
    private PhysicalServerCapacityVO capacity;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "uuid")
    @NoView
    private PhysicalServerHardwareInfoVO hardwareInfo;

    public PhysicalServerCapacityVO getCapacity() {
        return capacity;
    }

    public void setCapacity(PhysicalServerCapacityVO capacity) {
        this.capacity = capacity;
    }

    public PhysicalServerHardwareInfoVO getHardwareInfo() {
        return hardwareInfo;
    }

    public void setHardwareInfo(PhysicalServerHardwareInfoVO hardwareInfo) {
        this.hardwareInfo = hardwareInfo;
    }
}
```

### 2.3 PhysicalServerRoleVO

**设计决策**：角色映射表，1 台物理服务器 : N 个角色。roleUuid 指向现有 HostVO/ChassisVO 的 UUID（多态引用，不加 FK 约束，因为目标表不同）。UNIQUE(serverUuid, roleType) 保证每台物理服务器同一角色类型只有一条记录。

```java
package org.zstack.header.server;

import org.zstack.header.server.enums.SchedulingMode;
import org.zstack.header.server.enums.ServerRoleType;
import org.zstack.header.vo.ForeignKey;
import org.zstack.header.vo.ForeignKey.ReferenceOption;
import org.zstack.header.vo.Index;
import org.zstack.header.vo.EntityGraph;

import javax.persistence.*;
import java.sql.Timestamp;

/**
 * 物理服务器角色映射表。
 *
 * 每条记录表示一台 PhysicalServer 承载的一种角色。
 * roleUuid 是多态引用，指向 HostVO.uuid / BaremetalChassisVO.uuid /
 * BareMetal2ChassisVO.uuid / NativeHostVO.uuid，不添加 FK 约束
 * （因为目标表各异）。
 *
 * 设计理由：
 * - UNIQUE(serverUuid, roleType) 保证同一物理服务器不会注册两个相同角色
 * - roleUuid 不加 FK 是因为多态引用无法指向单一父表；一致性由 RoleProvider
 *   的 PostDelete 钩子保证
 * - schedulingMode 冗余存储（与 RoleProvider.getSchedulingMode() 一致），
 *   目的是让分配引擎无需回调 SPI 即可过滤
 */
@Entity
@Table(uniqueConstraints = {
    @UniqueConstraint(name = "uk_server_role", columnNames = {"serverUuid", "roleType"})
})
@EntityGraph(
    parents = {
        @EntityGraph.Neighbour(type = PhysicalServerVO.class, myField = "serverUuid", targetField = "uuid")
    }
)
public class PhysicalServerRoleVO {

    @Id
    @Column
    private String uuid;

    @Column
    @ForeignKey(parentEntityClass = PhysicalServerVO.class, onDeleteAction = ReferenceOption.CASCADE)
    @Index
    private String serverUuid;

    @Column
    @Enumerated(EnumType.STRING)
    private ServerRoleType roleType;

    /**
     * 指向角色实体的 UUID（HostVO.uuid / ChassisVO.uuid 等）。
     * 多态引用，不加 FK 约束。
     */
    @Column
    @Index
    private String roleUuid;

    /**
     * 角色实体所在的 clusterUuid。
     * 一台物理机可以在不同角色下属于不同 Cluster。
     */
    @Column
    @Index
    private String clusterUuid;

    @Column
    @Enumerated(EnumType.STRING)
    private SchedulingMode schedulingMode;

    /**
     * 角色状态：Active（角色在线有效）/ Stale（角色已断连或被删除但映射保留）。
     */
    @Column
    private String roleStatus;

    @Column
    private Timestamp createDate;

    @Column
    private Timestamp lastOpDate;

    @PreUpdate
    private void preUpdate() {
        lastOpDate = null;
    }

    // getter/setter 省略
}
```

### 2.4 PhysicalServerCapacityVO（唯一容量账本 / Source of Truth）

**设计决策**：与 PhysicalServerVO 共享 UUID（对齐 HostCapacityVO 的模式），FK CASCADE 到 PhysicalServerVO（不是 HostEO）。增加 cpuOverprovisioningRatio / memoryOverprovisioningRatio 字段，避免每次分配都查 GlobalConfig。不使用 EO 软删除模式，PhysicalServerVO 删除时级联硬删除。

**PhysicalServerCapacityVO 是容量的唯一真相（source of truth）**。HostCapacityVO 降级为 MySQL VIEW，通过 PhysicalServerRoleVO JOIN PhysicalServerCapacityVO 实时投影。所有容量写入（包括现有 HostCapacityUpdater 的 59 个调用方）最终都写入此表。

```java
package org.zstack.header.server;

import org.zstack.header.vo.EntityGraph;
import org.zstack.header.vo.ForeignKey;
import org.zstack.header.vo.ForeignKey.ReferenceOption;
import org.zstack.header.vo.Index;

import javax.persistence.*;

/**
 * 唯一容量账本（source of truth）。与 PhysicalServerVO 共享 UUID（1:1 关系）。
 *
 * 所有容量变更（VM 创建/销毁/迁移、超分比修改、重计算等）
 * 最终都写入此表。HostCapacityVO 是此表的只读 VIEW。
 *
 * 容量计算公式：
 *   getTotalCpu()    = totalPhysicalCpu * cpuOverprovisioningRatio
 *   getTotalMemory() = totalPhysicalMemory * memoryOverprovisioningRatio
 *   availableCpu     = totalCpu - Σ(业务消耗) - Σ(系统预留)
 *   availableMemory  = totalMemory - Σ(业务消耗) - Σ(系统预留)
 */
@Entity
@Table
@EntityGraph(
    parents = {
        @EntityGraph.Neighbour(type = PhysicalServerVO.class, myField = "uuid", targetField = "uuid")
    }
)
public class PhysicalServerCapacityVO {

    @Id
    @Column
    @ForeignKey(parentEntityClass = PhysicalServerVO.class, onDeleteAction = ReferenceOption.CASCADE)
    private String uuid;

    @Column
    @Index
    private long totalPhysicalCpu;

    @Column
    @Index
    private long totalPhysicalMemory;

    /** 物理可用内存（对齐 HostCapacityVO.availablePhysicalMemory） */
    @Column
    private long availablePhysicalMemory;

    /** CPU 线程数（对齐 HostCapacityVO.cpuNum） */
    @Column
    private int cpuNum;

    @Column
    private int cpuSockets;

    @Column
    private int cpuCoreNum;

    @Column
    private double cpuOverprovisioningRatio;

    @Column
    private double memoryOverprovisioningRatio;

    /** totalPhysicalCpu * cpuOverprovisioningRatio 的预计算值 */
    @Column
    @Index
    private long totalCpu;

    /** totalPhysicalMemory * memoryOverprovisioningRatio 的预计算值 */
    @Column
    @Index
    private long totalMemory;

    @Column
    @Index
    private long availableCpu;

    @Column
    @Index
    private long availableMemory;

    @Column
    private long reservedCpu;

    @Column
    private long reservedMemory;

    @Column
    @Index
    private long totalDisk;

    @Column
    @Index
    private long availableDisk;

    /**
     * 容量状态枚举，标识容量数据是否可信及物理机分配状态。
     */
    @Column
    @Enumerated(EnumType.STRING)
    private CapacityState capacityState;

    // CapacityState 枚举定义：
    // public enum CapacityState {
    //     Initialized,    // 硬件发现前，容量数据未知（BM 场景）
    //     Ready,          // 容量数据已就绪，可参与分配
    //     Allocated,      // 已被 INTERNAL_EXCLUSIVE 角色独占
    //     Recalculating,  // 容量重计算进行中
    //     Stale           // 容量数据过期，需刷新
    // }

    // getter/setter 省略

    public long getUsedCpu() {
        return totalCpu - availableCpu;
    }

    public long getUsedMemory() {
        return totalMemory - availableMemory;
    }
}
```

### 2.5 PhysicalServerHardwareInfoVO / PhysicalServerHardwareDetailVO

**设计决策**：HardwareInfoVO 与 PhysicalServerVO 共享 UUID（1:1 汇总表），HardwareDetailVO 是独立主键的多行明细表（1:N）。

```java
package org.zstack.header.server;

import org.zstack.header.vo.EntityGraph;
import org.zstack.header.vo.ForeignKey;
import org.zstack.header.vo.ForeignKey.ReferenceOption;

import javax.persistence.*;
import java.sql.Timestamp;

/**
 * 硬件信息汇总表（1:1 共享 UUID）。
 * 由硬件发现流程填充，可通过 QueryPhysicalServerMsg 查询。
 */
@Entity
@Table
@EntityGraph(
    parents = {
        @EntityGraph.Neighbour(type = PhysicalServerVO.class, myField = "uuid", targetField = "uuid")
    }
)
public class PhysicalServerHardwareInfoVO {

    @Id
    @Column
    @ForeignKey(parentEntityClass = PhysicalServerVO.class, onDeleteAction = ReferenceOption.CASCADE)
    private String uuid;

    @Column
    private String cpuModel;

    @Column
    private int cpuCores;

    @Column
    private int cpuSockets;

    @Column
    private long totalMemory;

    @Column
    private long totalDisk;

    @Column
    private int nicCount;

    @Column
    private int gpuCount;

    @Column
    private String biosVersion;

    @Column
    private String bmcVersion;

    @Column
    private Timestamp lastDiscoveryDate;

    // getter/setter 省略
}
```

```java
package org.zstack.header.server;

import org.zstack.header.server.enums.HardwareDetailType;
import org.zstack.header.vo.EntityGraph;
import org.zstack.header.vo.ForeignKey;
import org.zstack.header.vo.ForeignKey.ReferenceOption;
import org.zstack.header.vo.Index;

import javax.persistence.*;
import java.sql.Timestamp;

/**
 * 硬件明细表（1:N，每台服务器多条记录）。
 * 支持 CPU/MEMORY/DISK/NIC/GPU 等类型，存储 SMART 信息、固件版本等详细数据。
 */
@Entity
@Table
@EntityGraph(
    parents = {
        @EntityGraph.Neighbour(type = PhysicalServerVO.class, myField = "serverUuid", targetField = "uuid")
    }
)
public class PhysicalServerHardwareDetailVO {

    @Id
    @Column
    private String uuid;

    @Column
    @ForeignKey(parentEntityClass = PhysicalServerVO.class, onDeleteAction = ReferenceOption.CASCADE)
    @Index
    private String serverUuid;

    @Column
    @Enumerated(EnumType.STRING)
    @Index
    private HardwareDetailType detailType;

    /** 硬件标识（如 CPU slot 编号、磁盘设备名 /dev/sda） */
    @Column
    private String identifier;

    @Column
    private String vendor;

    @Column
    private String model;

    @Column
    private String serialNumber;

    @Column
    private String firmwareVersion;

    /** 容量（字节）。对 CPU 表示主频（Hz），对 DISK/MEMORY 表示容量 */
    @Column
    private long capacity;

    /** JSON 格式的扩展信息（SMART、温度、功耗等） */
    @Column(length = 4096)
    private String extraInfo;

    @Column
    private Timestamp createDate;

    @Column
    private Timestamp lastOpDate;

    @PreUpdate
    private void preUpdate() {
        lastOpDate = null;
    }

    // getter/setter 省略
}
```

### 2.6 ServerPoolVO

**设计决策**：ServerPool 是运维标签（机房/机架标识），不承载 L2 网络语义。扁平结构（不做层级），通过 name 和 physicalLocation 字段描述物理位置。

```java
package org.zstack.header.server;

import org.zstack.header.server.enums.PhysicalServerState;
import org.zstack.header.tag.AutoDeleteTag;
import org.zstack.header.vo.BaseResource;
import org.zstack.header.vo.EntityGraph;
import org.zstack.header.vo.ForeignKey;
import org.zstack.header.vo.ForeignKey.ReferenceOption;
import org.zstack.header.vo.ResourceVO;
import org.zstack.header.zone.ZoneVO;

import javax.persistence.*;
import java.sql.Timestamp;

/**
 * 物理服务器分组，定位为运维标签（机房/机架标识）。
 * 与 Cluster 的关系：多个 Cluster 可引用同一个 ServerPool（多对一）。
 * 不承载 L2 Network 语义。
 * 不使用 EO 软删除模式，直接硬删除。
 */
@Entity
@Table
@AutoDeleteTag
@BaseResource
@EntityGraph(
    parents = {
        @EntityGraph.Neighbour(type = ZoneVO.class, myField = "zoneUuid", targetField = "uuid")
    }
)
public class ServerPoolVO extends ResourceVO {

    @Column
    @ForeignKey(parentEntityClass = ZoneVO.class, onDeleteAction = ReferenceOption.CASCADE)
    private String zoneUuid;

    @Column
    private String name;

    @Column
    private String description;

    /** 物理位置描述（如"北京亦庄 A3 机房 R12 机柜"） */
    @Column
    private String physicalLocation;

    /** 网络拓扑描述（文本，运维记录用） */
    @Column(length = 2048)
    private String networkTopology;

    @Column
    @Enumerated(EnumType.STRING)
    private PhysicalServerState state;

    @Column
    private Timestamp createDate;

    @Column
    private Timestamp lastOpDate;

    @PreUpdate
    private void preUpdate() {
        lastOpDate = null;
    }

    // getter/setter 省略
}
```

### 2.7 PhysicalServerProvisionNetworkVO + ClusterRefVO

**设计决策**：统一装机网络，复用 BM2 成熟模型。所有角色共用——裸金属装机和裸机装 KVM ISO 都适用。通过 ClusterRefVO 关联表实现与 Cluster 的多对多关系。

```java
package org.zstack.header.server;

import org.zstack.header.server.enums.PhysicalServerState;
import org.zstack.header.server.enums.ProvisionNetworkType;
import org.zstack.header.tag.AutoDeleteTag;
import org.zstack.header.vo.BaseResource;
import org.zstack.header.vo.EntityGraph;
import org.zstack.header.vo.ForeignKey;
import org.zstack.header.vo.ForeignKey.ReferenceOption;
import org.zstack.header.vo.ResourceVO;
import org.zstack.header.zone.ZoneVO;

import javax.persistence.*;
import java.sql.Timestamp;

/**
 * 统一装机网络。
 *
 * 复用 BM2 BareMetal2ProvisionNetworkVO 成熟模型：
 *   dhcpInterface + DHCP 地址范围 + type (STANDALONE_PXE / GATEWAY_PXE)
 *
 * 所有角色共用：
 *   - BM1 装机：映射自 BaremetalPxeServerVO
 *   - BM2 装机：映射自 BareMetal2ProvisionNetworkVO
 *   - 裸机装 KVM ISO：通过 ProvisionNetwork 进行 PXE/HTTP 引导安装
 * 不使用 EO 软删除模式，直接硬删除。
 */
@Entity
@Table
@AutoDeleteTag
@BaseResource
@EntityGraph(
    parents = {
        @EntityGraph.Neighbour(type = ZoneVO.class, myField = "zoneUuid", targetField = "uuid")
    }
)
public class PhysicalServerProvisionNetworkVO extends ResourceVO {

    @Column
    @ForeignKey(parentEntityClass = ZoneVO.class, onDeleteAction = ReferenceOption.CASCADE)
    private String zoneUuid;

    @Column
    private String name;

    @Column
    private String description;

    @Column
    private String dhcpInterface;

    @Column
    private String dhcpRangeStartIp;

    @Column
    private String dhcpRangeEndIp;

    @Column
    private String dhcpRangeNetmask;

    @Column
    private String dhcpRangeGateway;

    @Column
    @Enumerated(EnumType.STRING)
    private PhysicalServerState state;

    @Column
    @Enumerated(EnumType.STRING)
    private ProvisionNetworkType type;

    @Column
    private Timestamp createDate;

    @Column
    private Timestamp lastOpDate;

    @PreUpdate
    private void preUpdate() {
        lastOpDate = null;
    }

    // getter/setter 省略
}
```

```java
package org.zstack.header.server;

import org.zstack.header.cluster.ClusterVO;
import org.zstack.header.vo.EntityGraph;
import org.zstack.header.vo.ForeignKey;
import org.zstack.header.vo.ForeignKey.ReferenceOption;

import javax.persistence.*;
import java.sql.Timestamp;

/**
 * ProvisionNetwork 与 Cluster 的多对多关联表。
 * Cluster 删除时级联删除关联记录。
 * 不使用 EO 软删除模式，直接硬删除。
 */
@Entity
@Table(uniqueConstraints = {
    @UniqueConstraint(name = "uk_network_cluster",
        columnNames = {"networkUuid", "clusterUuid"})
})
@EntityGraph(
    parents = {
        @EntityGraph.Neighbour(type = PhysicalServerProvisionNetworkVO.class,
            myField = "networkUuid", targetField = "uuid")
    }
)
public class PhysicalServerProvisionNetworkClusterRefVO {

    @Id
    @Column
    private String uuid;

    @Column
    @ForeignKey(parentEntityClass = PhysicalServerProvisionNetworkVO.class,
        onDeleteAction = ReferenceOption.CASCADE)
    private String networkUuid;

    @Column
    @ForeignKey(parentEntityClass = ClusterVO.class,
        onDeleteAction = ReferenceOption.CASCADE)
    private String clusterUuid;

    @Column
    private Timestamp createDate;

    // getter/setter 省略
}
```

---

## 第 3 章：SPI 接口契约

### 3.1 PhysicalServerRoleProvider

**核心 SPI 接口**，各角色模块必须实现此接口并注册为 Spring Bean。分配引擎和管理器通过 `pluginRgty.getExtensionList()` 发现所有实现。

```java
package org.zstack.header.server;

import org.zstack.header.server.enums.SchedulingMode;
import org.zstack.header.server.enums.ServerRoleType;

/**
 * 物理服务器角色接入 SPI。
 *
 * 每种角色（KVM/BM1/BM2/Container）实现此接口，嵌入 Physical Layer。
 * 新角色只需实现此 SPI + 注册 Spring Bean 即可接入统一管理。
 *
 * 语义约束：
 * 1. getRoleType() 和 getSchedulingMode() 必须返回常量，不可动态变化
 * 2. getCapacityConsumption() 必须是无副作用的只读方法
 * 3. onPhysicalServerCreated/Deleted 在事务内调用，实现方可参与事务
 * 4. matchExistingServer() 用于角色自动关联，实现方提供匹配逻辑
 */
public interface PhysicalServerRoleProvider {

    /**
     * 返回此 Provider 管理的角色类型。
     * 每种 ServerRoleType 全局只有一个 Provider。
     */
    ServerRoleType getRoleType();

    /**
     * 返回此角色的调度模式。
     * 分配引擎据此决定容量扣减策略：
     *   INTERNAL_SHARED   — 按需扣减，支持超分
     *   INTERNAL_EXCLUSIVE — 整机清零
     *   EXTERNAL_READONLY  — 不扣减
     */
    SchedulingMode getSchedulingMode();

    /**
     * 查询指定物理服务器上此角色的容量消耗。
     * 容量重计算时调用：Available = Total - Σ(getCapacityConsumption) - Σ(系统预留)。
     *
     * @param serverUuid PhysicalServerVO 的 UUID
     * @return 当前角色在该服务器上的容量消耗
     */
    CapacityUsage getCapacityConsumption(String serverUuid);

    /**
     * 物理服务器创建后的生命周期回调。
     * 实现方可在此创建角色特定的资源（如 HostCapacityVO 初始化等）。
     *
     * @param serverUuid PhysicalServerVO 的 UUID
     */
    void onPhysicalServerCreated(String serverUuid);

    /**
     * 物理服务器删除前的生命周期回调。
     * 实现方清理角色特定的资源。
     *
     * @param serverUuid PhysicalServerVO 的 UUID
     */
    void onPhysicalServerDeleted(String serverUuid);

    /**
     * 查询角色详情（用于单独角色查询场景，QueryPhysicalServerMsg 不调用此方法，只返回 ref 引用）。
     *
     * @param roleUuid PhysicalServerRoleVO.roleUuid
     * @return 角色实体的 Inventory 表示
     */
    RoleInventory getInventory(String roleUuid);

    /**
     * 尝试将新注册的角色实体匹配到已有的 PhysicalServerVO。
     * 匹配规则：serialNumber 优先，managementIp + zoneUuid 降级。
     *
     * @param context 包含 serialNumber、managementIp、zoneUuid 等匹配信息
     * @return 匹配到的 PhysicalServerVO UUID，null 表示未匹配
     */
    String matchExistingServer(RoleMatchContext context);
}
```

辅助数据类：

```java
package org.zstack.header.server;

/**
 * 角色在某台物理服务器上的容量消耗。
 */
public class CapacityUsage {
    private long usedCpu;
    private long usedMemory;
    private long usedDisk;

    // getter/setter 省略
}
```

```java
package org.zstack.header.server;

/**
 * 角色匹配上下文，用于 matchExistingServer()。
 */
public class RoleMatchContext {
    private String serialNumber;
    private String managementIp;
    private String zoneUuid;
    private String clusterUuid;
    private String oobAddress;  // BM1/BM2 的 IPMI/Redfish 地址，用于降级匹配

    // getter/setter 省略
}
```

```java
package org.zstack.header.server;

/**
 * 角色 Inventory 基类。各角色 Provider 返回自己的子类。
 */
public class RoleInventory {
    private String roleUuid;
    private String roleType;
    private String clusterUuid;
    private String status;

    // getter/setter 省略
}
```

### 3.1.1 PhysicalServerManagerImpl 核心方法

以下方法由 `PhysicalServerManagerImpl` 统一实现，各适配器不自行处理互斥和匹配逻辑。

```java
package org.zstack.server;

/**
 * 注册角色到 PhysicalServerVO。
 *
 * 统一入口，所有角色模块在创建角色实体后调用此方法。
 * 内部执行：匹配已有 PhysicalServer → 互斥检查 → 创建 RoleVO。
 */
public class PhysicalServerManagerImpl {

    /**
     * 注册角色。先匹配或创建 PhysicalServerVO，再创建 PhysicalServerRoleVO。
     *
     * @param context 角色匹配上下文（serialNumber、managementIp、zoneUuid、oobAddress）
     * @param provider 角色的 RoleProvider
     * @param roleUuid 角色实体的 UUID（如 HostVO.uuid 或 ChassisVO.uuid）
     * @param clusterUuid 角色所属 Cluster
     * @return PhysicalServerVO 的 UUID
     * @throws OperationFailureException 互斥检查失败时抛出
     */
    public String registerRole(RoleMatchContext context,
                               PhysicalServerRoleProvider provider,
                               String roleUuid,
                               String clusterUuid) {
        // 1. 匹配已有 PhysicalServerVO
        String serverUuid = provider.matchExistingServer(context);
        if (serverUuid == null) {
            serverUuid = defaultMatch(context);
        }
        if (serverUuid == null) {
            serverUuid = createPhysicalServer(context);
        }

        // 2. 互斥检查
        checkSchedulingModeExclusion(serverUuid, provider.getSchedulingMode());

        // 3. 创建 PhysicalServerRoleVO
        createRoleVO(serverUuid, provider, roleUuid, clusterUuid);
        return serverUuid;
    }

    /**
     * 默认匹配逻辑。当 RoleProvider.matchExistingServer() 返回 null 时使用。
     *
     * 优先级：
     * 1. serialNumber + zoneUuid（精确匹配）
     * 2. oobAddress + zoneUuid（BM 场景降级）
     * 3. managementIp + zoneUuid（最终降级）
     */
    private String defaultMatch(RoleMatchContext context) {
        if (context.getSerialNumber() != null) {
            // SELECT uuid FROM PhysicalServerVO
            // WHERE serialNumber = ? AND zoneUuid = ?
            String uuid = findBySerialNumber(context.getSerialNumber(), context.getZoneUuid());
            if (uuid != null) return uuid;
        }

        if (context.getOobAddress() != null) {
            // SELECT uuid FROM PhysicalServerVO
            // WHERE oobAddress = ? AND zoneUuid = ?
            String uuid = findByOobAddress(context.getOobAddress(), context.getZoneUuid());
            if (uuid != null) return uuid;
        }

        if (context.getManagementIp() != null) {
            // SELECT uuid FROM PhysicalServerVO
            // WHERE managementIp = ? AND zoneUuid = ?
            String uuid = findByManagementIp(context.getManagementIp(), context.getZoneUuid());
            if (uuid != null) return uuid;
        }

        return null;
    }

    /**
     * 互斥检查。
     *
     * 规则：
     * - INTERNAL_EXCLUSIVE 不能与 INTERNAL_SHARED 或另一个 INTERNAL_EXCLUSIVE 共存
     * - EXTERNAL_READONLY 可以与任何模式共存
     * - INTERNAL_SHARED 可以与 EXTERNAL_READONLY 共存
     *
     * | 已有角色\新角色        | INTERNAL_SHARED | INTERNAL_EXCLUSIVE | EXTERNAL_READONLY |
     * |----------------------|-----------------|--------------------|--------------------|
     * | INTERNAL_SHARED      | 不允许(同类型)    | 不允许              | 允许               |
     * | INTERNAL_EXCLUSIVE   | 不允许           | 不允许              | 允许               |
     * | EXTERNAL_READONLY    | 允许             | 允许               | 不允许(同类型)       |
     * | 无角色                | 允许             | 允许               | 允许               |
     *
     * 注意：同一 ServerRoleType 的角色不能重复注册（UNIQUE(serverUuid, roleType) 约束保证）。
     */
    private void checkSchedulingModeExclusion(String serverUuid, SchedulingMode newMode) {
        List<PhysicalServerRoleVO> existingRoles = dbf.listByPrimaryKey(
            serverUuid, PhysicalServerRoleVO.class, "serverUuid");

        for (PhysicalServerRoleVO role : existingRoles) {
            if (role.getRoleStatus() != RoleStatus.Active) continue;

            SchedulingMode existingMode = role.getSchedulingMode();

            if (newMode == SchedulingMode.EXTERNAL_READONLY
                || existingMode == SchedulingMode.EXTERNAL_READONLY) {
                continue; // EXTERNAL_READONLY 与任何模式兼容
            }

            // INTERNAL_SHARED 与 INTERNAL_EXCLUSIVE 互斥
            // INTERNAL_EXCLUSIVE 与 INTERNAL_EXCLUSIVE 互斥
            throw new OperationFailureException(operr(
                "cannot register role with scheduling mode [%s] on physical server [%s]:" +
                " conflicts with existing role [%s] (scheduling mode [%s])",
                newMode, serverUuid, role.getRoleType(), existingMode));
        }
    }
}
```

### 3.2 PowerManageable 接口

```java
package org.zstack.header.server;

import org.zstack.header.core.Completion;
import org.zstack.header.core.ReturnValueCompletion;
import org.zstack.header.server.enums.PhysicalServerPowerStatus;

/**
 * 电源管理 SPI。
 *
 * 默认实现使用 PhysicalServerAO 中的 OOB 凭据执行 IPMI/Redfish 操作。
 * 角色 RoleProvider 可额外实现此接口覆盖默认行为（如 BM2 有自己的电源管理网关）。
 *
 * 语义约束：
 * 1. 所有操作异步执行（通过 Completion 回调）
 * 2. 操作成功后实现方必须更新 PhysicalServerAO.powerStatus
 * 3. 无 OOB 凭据时应立即 fail，不做重试
 */
public interface PowerManageable {

    void powerOn(String serverUuid, Completion completion);

    void powerOff(String serverUuid, Completion completion);

    void powerReset(String serverUuid, Completion completion);

    void getPowerStatus(String serverUuid,
                        ReturnValueCompletion<PhysicalServerPowerStatus> completion);
}
```

### 3.3 HardwareDiscoverable 接口

```java
package org.zstack.header.server;

import org.zstack.header.core.Completion;

/**
 * 硬件发现 SPI。
 *
 * 支持两种采集路径：
 * 1. OOB 路径：通过 IPMI FRU 命令获取硬件信息（无需 OS 运行）
 * 2. Agent 路径：通过 agent 读取 /sys/class/dmi/、lspci 等（需 OS 运行）
 *
 * 采集结果写入 PhysicalServerHardwareInfoVO + PhysicalServerHardwareDetailVO。
 *
 * 语义约束：
 * 1. 采集是幂等操作，重复执行覆盖已有数据
 * 2. 异步执行，采集完成后通过 Completion 回调
 */
public interface HardwareDiscoverable {

    /**
     * 触发硬件信息采集。
     *
     * @param serverUuid PhysicalServerVO 的 UUID
     * @param completion 完成回调
     */
    void discoverHardware(String serverUuid, Completion completion);
}
```

### 3.4 扩展点

```java
package org.zstack.header.server;

import java.util.List;

/**
 * 分配器过滤扩展点。
 *
 * 对齐 HostAllocatorFilterExtensionPoint 模式。
 * 第三方模块实现此接口可在 ServerAllocatorChain 中注入自定义过滤逻辑。
 */
public interface ServerAllocatorFilterExtensionPoint {

    /**
     * 过滤候选物理服务器列表。
     *
     * @param candidates 当前候选列表
     * @param spec 分配规格
     * @return 过滤后的候选列表（子集）
     */
    List<PhysicalServerVO> filterServerCandidates(
        List<PhysicalServerVO> candidates, ServerAllocatorSpec spec);

    /**
     * 过滤失败时的错误描述。
     */
    String filterErrorReason();
}
```

```java
package org.zstack.header.server;

/**
 * 系统预留容量扩展点。
 *
 * 各模块声明在物理服务器上的系统级资源预留（OS 开销、Ceph Agent、监控 Agent 等）。
 * 容量重计算时汇总所有扩展点的预留量。
 *
 * 对齐 HostCapacityReserveManager 的设计模式，但以税收模型简化：
 *   AvailableCpu = TotalCpu - Σ(业务税) - Σ(系统税)
 *   系统税 = Σ(getAllReservedCapacity)
 */
public interface ServerReservedCapacityExtensionPoint {

    /**
     * 返回此扩展在指定物理服务器上的系统预留容量。
     *
     * @param serverUuid PhysicalServerVO 的 UUID
     * @return 预留容量
     */
    ReservedServerCapacity getReservedCapacity(String serverUuid);
}
```

```java
package org.zstack.header.server;

/**
 * 系统预留容量。
 */
public class ReservedServerCapacity {
    private long reservedCpu;
    private long reservedMemory;
    private long reservedDisk;

    // getter/setter 省略
}
```

---

## 第 4 章：统一分配引擎接口

### 4.1 AllocateServerMsg / AllocateServerReply

```java
package org.zstack.header.server;

import org.zstack.header.message.NeedReplyMessage;
import org.zstack.header.server.enums.SchedulingMode;
import org.zstack.header.server.enums.ServerRoleType;

import java.util.List;

/**
 * 统一分配消息。
 *
 * 两阶段薄适配模式：ServerAllocatorChain 只做通用过滤（Zone/Cluster/Pool/Status/Capacity），
 * 不读取任何角色特有字段。KVM 特有过滤（L2/PS/BS 等）由现有 HostAllocatorChain 在阶段2处理。
 *
 * 设计理由：
 * - ServerAllocatorChain 不需要访问 AllocateHostMsg 的角色特有字段
 * - 不需要 ExtensionFilterFlow 桥接扩展点
 * - 现有 HostAllocatorChain 的 KVM Flow 全部保留，无需在新链中重写
 */
public class AllocateServerMsg extends NeedReplyMessage {

    /** 需要分配的角色类型 */
    private ServerRoleType requiredRoleType;

    /** 需求 CPU（Hz 或 核数，与 HostAllocatorSpec 对齐） */
    private long requiredCpu;

    /** 需求内存（字节） */
    private long requiredMemory;

    /** 需求磁盘（字节） */
    private long requiredDisk;

    /** 限定 Zone */
    private String zoneUuid;

    /** 限定 Cluster */
    private String clusterUuid;

    /** 指定分配到特定物理服务器 */
    private String serverUuid;

    /** 限定 ServerPool */
    private String poolUuid;

    /** 限定调度模式 */
    private SchedulingMode schedulingMode;

    /** 排除列表 */
    private List<String> avoidServerUuids;

    /** 软排除列表 */
    private List<String> softAvoidServerUuids;

    /** 是否 DryRun（仅返回候选列表，不实际分配） */
    private boolean dryRun;

    // getter/setter 省略
}
```

```java
package org.zstack.header.server;

import org.zstack.header.message.MessageReply;

/**
 * 统一分配响应。
 */
public class AllocateServerReply extends MessageReply {

    /** 分配到的物理服务器 Inventory */
    private PhysicalServerInventory server;

    /** 分配到的角色 UUID（RoleVO.roleUuid） */
    private String roleUuid;

    // getter/setter 省略
}
```

### 4.2 ServerAllocatorSpec

```java
package org.zstack.header.server;

import org.zstack.header.message.NeedReplyMessage;
import org.zstack.header.server.enums.SchedulingMode;
import org.zstack.header.server.enums.ServerRoleType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 分配规格，对齐 HostAllocatorSpec 模式。
 * 从 AllocateServerMsg 构建，在 Flow 链中传递。
 */
public class ServerAllocatorSpec {

    private ServerRoleType requiredRoleType;
    private long requiredCpu;
    private long requiredMemory;
    private long requiredDisk;
    private String zoneUuid;
    private String clusterUuid;
    private String serverUuid;
    private String poolUuid;
    private SchedulingMode schedulingMode;
    private List<String> avoidServerUuids;
    private List<String> softAvoidServerUuids;
    private boolean dryRun;

    /** 扩展数据，供 Flow 之间传递中间状态 */
    private Map<Object, Object> extraData = new HashMap<>();

    /**
     * 从 AllocateServerMsg 构建 Spec。
     */
    public static ServerAllocatorSpec fromAllocateServerMsg(AllocateServerMsg msg) {
        ServerAllocatorSpec spec = new ServerAllocatorSpec();
        spec.setRequiredRoleType(msg.getRequiredRoleType());
        spec.setRequiredCpu(msg.getRequiredCpu());
        spec.setRequiredMemory(msg.getRequiredMemory());
        spec.setRequiredDisk(msg.getRequiredDisk());
        spec.setZoneUuid(msg.getZoneUuid());
        spec.setClusterUuid(msg.getClusterUuid());
        spec.setServerUuid(msg.getServerUuid());
        spec.setPoolUuid(msg.getPoolUuid());
        spec.setSchedulingMode(msg.getSchedulingMode());
        spec.setAvoidServerUuids(msg.getAvoidServerUuids());
        spec.setSoftAvoidServerUuids(msg.getSoftAvoidServerUuids());
        spec.setDryRun(msg.isDryRun());
        return spec;
    }

    // getter/setter 省略
}
```

### 4.3 ServerAllocatorChain 设计

**设计决策**：完全对齐 `HostAllocatorChain` 的 Flow 链模式，每个 Flow 继承 `AbstractServerAllocatorFlow`，按顺序执行过滤/排序。

```
ServerAllocatorChain Flow 执行顺序（7 个通用 Flow）：

两阶段薄适配模式下，ServerAllocatorChain 只做通用过滤，不包含任何角色特有 Flow。
KVM 特有的 L2/PS/BS/Tag/ResourceBinding 过滤由阶段2的 HostAllocatorChain 处理。

1. ZoneFilterFlow         — 按 zoneUuid 过滤（首个 Flow，从 DB 加载候选列表）
2. ClusterFilterFlow      — 按 clusterUuid 过滤
3. PoolFilterFlow         — 按 poolUuid 过滤
4. RoleTypeFilterFlow     — 按 requiredRoleType 过滤（JOIN PhysicalServerRoleVO）
5. StateFilterFlow        — 过滤 state=Enabled, status=Connected
6. AvoidServerFilterFlow  — 排除 avoidServerUuids
7. CapacityFilterFlow     — 按 requiredCpu/requiredMemory/requiredDisk 过滤
```

```java
package org.zstack.header.server;

import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.errorcode.OperationFailureException;

import java.util.List;

/**
 * 服务器分配 Flow 抽象基类。
 * 对齐 AbstractHostAllocatorFlow 模式。
 */
public abstract class AbstractServerAllocatorFlow {

    protected List<PhysicalServerVO> candidates;
    protected ServerAllocatorSpec spec;
    private ServerAllocatorTrigger trigger;

    public abstract void allocate();

    public void setCandidates(List<PhysicalServerVO> candidates) {
        this.candidates = candidates;
    }

    public void setSpec(ServerAllocatorSpec spec) {
        this.spec = spec;
    }

    public void setTrigger(ServerAllocatorTrigger trigger) {
        this.trigger = trigger;
    }

    protected void next(List<PhysicalServerVO> candidates) {
        trigger.next(candidates);
    }

    protected void skip() {
        trigger.skip();
    }

    protected void fail(String reason) {
        ErrorCode errorCode = new ErrorCode();
        errorCode.setCode(ServerAllocatorError.NO_AVAILABLE_SERVER.toString());
        errorCode.setDetails(reason);
        throw new OperationFailureException(errorCode);
    }

    protected boolean amITheFirstFlow() {
        return candidates == null;
    }

    protected List<String> getServerUuidsFromCandidates() {
        // 提取 UUID 列表，供子类使用
        return null; // 实际实现
    }
}
```

### 4.4 PhysicalServerCapacityUpdater 接口（容量写入唯一入口）

```java
package org.zstack.header.server;

/**
 * 容量更新器接口 — 容量写入的唯一入口。
 *
 * PhysicalServerCapacityVO 是容量的 source of truth。
 * 所有容量变更（包括现有 HostCapacityUpdater 的 59 个调用方）
 * 最终都通过此接口写入 PhysicalServerCapacityVO。
 *
 * HostCapacityUpdater 是本接口的包装器（见 4.6 节），
 * 保留原有 public 方法签名不变，内部转写 PhysicalServerCapacityVO。
 *
 * 使用 PESSIMISTIC_WRITE 锁保证并发安全。
 * 实现类上标注 @DeadlockAutoRestart（不与 @Transactional 同方法）。
 *
 * 容量扣减逻辑按 SchedulingMode 分支：
 * - INTERNAL_SHARED：requiredCpu/Memory 扣减
 * - INTERNAL_EXCLUSIVE：清零所有可用量
 * - EXTERNAL_READONLY：不扣减（跳过）
 */
public interface PhysicalServerCapacityUpdater {

    /**
     * 扣减容量（分配时调用）。
     *
     * @param serverUuid PhysicalServerVO UUID
     * @param requiredCpu 需求 CPU
     * @param requiredMemory 需求内存
     * @param requiredDisk 需求磁盘
     */
    void decreaseCapacity(String serverUuid,
                          long requiredCpu, long requiredMemory, long requiredDisk);

    /**
     * 归还容量（释放时调用）。
     */
    void increaseCapacity(String serverUuid,
                          long releasedCpu, long releasedMemory, long releasedDisk);

    /**
     * 全量重计算容量（税收模式）。
     * 直接操作 PhysicalServerCapacityVO，无需同步到 HostCapacityVO
     * （HostCapacityVO 是 VIEW，自动反映最新数据）。
     *
     * Available = Total - Σ(RoleProvider.getCapacityConsumption) - Σ(ReservedCapacity)
     *
     * @param serverUuid PhysicalServerVO UUID
     */
    void recalculateCapacity(String serverUuid);
}
```

### 4.5 ServerCapacityOverProvisioningManager 接口

```java
package org.zstack.header.server;

/**
 * 超分比管理器。
 * 对齐 HostCapacityOverProvisioningManager 模式，但同时管理 CPU 和 Memory 的超分比。
 *
 * 全局默认值通过 GlobalConfig 配置，per-server 覆盖通过 SystemTag 实现。
 */
public interface ServerCapacityOverProvisioningManager {

    // ---- CPU 超分比 ----

    void setCpuGlobalRatio(double ratio);

    double getCpuGlobalRatio();

    void setCpuRatio(String serverUuid, double ratio);

    void deleteCpuRatio(String serverUuid);

    double getCpuRatio(String serverUuid);

    long calculateCpuByRatio(String serverUuid, long physicalCpu);

    // ---- Memory 超分比 ----

    void setMemoryGlobalRatio(double ratio);

    double getMemoryGlobalRatio();

    void setMemoryRatio(String serverUuid, double ratio);

    void deleteMemoryRatio(String serverUuid);

    double getMemoryRatio(String serverUuid);

    long calculateMemoryByRatio(String serverUuid, long physicalMemory);
}
```

### 4.6 HostCapacityUpdater 包装器设计

**设计决策**：HostCapacityVO 从真表降级为 MySQL VIEW 后，现有 HostCapacityUpdater 的 59 个调用方不需要任何改动。HostCapacityUpdater 的 public 方法签名全部保留，内部实现改为通过 PhysicalServerRoleVO 查找 serverUuid，委托 PhysicalServerCapacityUpdater 写入 PhysicalServerCapacityVO。由于 HostCapacityVO 是 VIEW，写入 PhysicalServerCapacityVO 后查询 HostCapacityVO 即可看到最新数据，无需任何同步机制。

**HostCapacityVO VIEW 定义**：

```sql
CREATE VIEW HostCapacityVO AS
SELECT r.roleUuid AS uuid,
       c.totalCpu, c.availableCpu,
       c.totalMemory, c.availableMemory,
       c.totalPhysicalMemory, c.availablePhysicalMemory,
       c.cpuNum, c.cpuSockets, c.cpuCoreNum
FROM PhysicalServerCapacityVO c
JOIN PhysicalServerRoleVO r ON r.serverUuid = c.uuid
WHERE r.roleType = 'KVM_HOST';
```

**VIEW 列映射说明**：
- `uuid` = `r.roleUuid`（即 HostVO.uuid），保证现有 47 个读取方通过 hostUuid 查询时透明命中
- `totalCpu` / `availableCpu` / `totalMemory` / `availableMemory` — 含超分比的逻辑值，直接透传
- `totalPhysicalMemory` / `availablePhysicalMemory` — 物理内存值，直接透传
- `cpuNum` / `cpuSockets` / `cpuCoreNum` — CPU 拓扑信息，直接透传
- PhysicalServerCapacityVO 的列名与 HostCapacityVO 原表列名保持一致，VIEW 不做列名转换

**包装器实现**：

```java
/**
 * 原 HostCapacityUpdater 的包装器。
 * API 不变（59 个调用方零改动），内部转写 PhysicalServerCapacityVO。
 *
 * 设计要点：
 * 1. 所有 public 方法签名与原 HostCapacityUpdater 完全一致
 * 2. 通过 hostUuid（即 roleUuid）查找 PhysicalServerRoleVO 获取 serverUuid
 * 3. 委托 PhysicalServerCapacityUpdater 执行实际写入
 * 4. HostCapacityVO 是 VIEW，写入后自动可见，无需双写
 *
 * 6 个写入路径的改造：
 *   (1) KVMHost.connectHook() → HostCapacityUpdater.run() → 内部转写 PhysicalServerCapacityVO
 *   (2) VM 创建 → ReserveHostCapacityMsg → HostCapacityUpdater → 内部转写
 *   (3) VM 销毁 → ReturnHostCapacityMsg → HostCapacityUpdater → 内部转写
 *   (4) VM 迁移 → HostCapacityUpdater.decrease(src) + increase(dst) → 内部转写
 *   (5) RecalculateHostCapacityMsg → HostCapacityUpdater → 委托 recalculateCapacity()
 *   (6) HostCpuOverProvisioningManagerImpl 超分比变更 → 不再裸写 JPQL（见下方说明）
 */
public class HostCapacityUpdater {
    // 原有 public 方法签名全部保留

    @DeadlockAutoRestart
    public void run() {
        // 1. 通过 hostUuid 查 PhysicalServerRoleVO 获取 serverUuid
        String serverUuid = findServerUuidByRoleUuid(hostUuid);
        // 2. 委托 PhysicalServerCapacityUpdater
        physicalServerCapacityUpdater.update(serverUuid, ...);
    }

    private String findServerUuidByRoleUuid(String hostUuid) {
        return Q.New(PhysicalServerRoleVO.class)
            .eq(PhysicalServerRoleVO_.roleType, ServerRoleType.KVM_HOST)
            .eq(PhysicalServerRoleVO_.roleUuid, hostUuid)
            .select(PhysicalServerRoleVO_.serverUuid)
            .findValue();
    }
}
```

**HostCpuOverProvisioningManagerImpl 裸 JPQL 删除**：

现有 `HostCpuOverProvisioningManagerImpl` 中有 3 处裸 JPQL UPDATE 直接写 HostCapacityVO：

```java
// 第 70 行：全局超分比变化时批量更新
update HostCapacityVO cap set cap.totalCpu = cap.cpuNum * :ratio

// 第 75 行：全局超分比变化时更新非自定义超分比的 Host
update HostCapacityVO cap set cap.totalCpu = ... where cap.uuid not in (:uuids)

// 第 96 行：per-host 超分比变化时更新单台
update HostCapacityVO cap set cap.totalCpu = ... where cap.uuid = :huuid
```

**改造方案**：这 3 处裸 JPQL 全部删掉，因为：
1. HostCapacityVO 已是 VIEW，不能直接 UPDATE
2. 超分比变化应通过统一的容量重计算路径处理

替代方案：超分比变化时，触发 `RecalculatePhysicalServerCapacityMsg` 批量重计算：
- 全局超分比变化 → 发送 `RecalculatePhysicalServerCapacityMsg`（per zone）→ 遍历所有 PhysicalServer 重计算
- per-host 超分比变化 → 发送 `RecalculatePhysicalServerCapacityMsg`（单台 serverUuid）→ 重计算单台

这与 `ServerCapacityOverProvisioningManagerImpl` 的事件链（见分配引擎文档 3.3 节）统一，不直接裸写 SQL。

**为什么不需要容量同步机制**：旧设计中 HostCapacityVO 是真表，PhysicalServerCapacityVO 是派生数据，需要异步事件 + 定时对账来保持一致。新设计中 PhysicalServerCapacityVO 是唯一真表，HostCapacityVO 是 VIEW，数据天然一致，彻底消除了同步延迟和不一致的风险。

### 4.7 CompatibilityBridge 接口（两阶段薄适配）

**设计决策**：两阶段薄适配模式。不再使用 originalMessage 透传，不在 ServerAllocatorChain 中处理任何 KVM 特有逻辑。

**两阶段流程**：

```
AllocateHostMsg
  → CompatibilityBridge 拦截
  → 阶段1: ServerAllocatorChain（只做 Zone/Cluster/Pool/Status/Capacity 通用过滤）
  → 输出候选 PhysicalServer 列表 → 映射回 HostVO UUID 集合
  → 注入 HostAllocatorSpec.candidateHostUuids
  → 阶段2: 现有 HostAllocatorChain 正常执行（在预筛选的小集合上跑 L2/PS/BS 等 KVM Flow）
  → HostSortorChain + reserveCapacity（锁机制不变）
```

**关键设计点**：
1. 不需要 originalMessage 透传 — ServerAllocatorChain 不读 KVM 特有字段
2. 不需要 ExtensionFilterFlow — KVM 特有过滤由现有 HostAllocatorChain 处理
3. 现有 HostAllocatorChain 只跑一遍，候选集被阶段1提前缩小
4. AllocateServerMsg 已简化 — 去掉 originalMessage 字段
5. 注入方式：在 HostAllocatorSpec 中新增 candidateHostUuids 字段，DesignatedHostAllocatorFlow 识别此字段做预过滤

```java
package org.zstack.header.server;

import org.zstack.header.allocator.AllocateHostMsg;
import org.zstack.header.allocator.AllocateHostReply;
import org.zstack.header.core.ReturnValueCompletion;
import org.zstack.header.host.HostInventory;

import java.util.List;

/**
 * 兼容层两阶段薄适配。
 *
 * 核心设计：
 * 1. 阶段1：ServerAllocatorChain 只做通用过滤（Zone/Cluster/Pool/Status/Capacity）
 * 2. 阶段1输出 PhysicalServer 列表，映射回 HostVO UUID 集合
 * 3. 注入 HostAllocatorSpec.candidateHostUuids，缩小阶段2的候选集
 * 4. 阶段2：现有 HostAllocatorChain 在预筛选集合上正常执行所有 KVM Flow
 * 5. 特性开关控制是否启用阶段1，开关关闭时 100% 走旧路径
 *
 * 转换流程：
 *   AllocateHostMsg
 *     → CompatibilityBridge.allocate()
 *       → 阶段1: AllocateServerMsg（不含 originalMessage）
 *         → ServerAllocatorChain（7 个通用 Flow）
 *           → 候选 PhysicalServer 列表
 *             → 映射回 HostVO UUID 集合（通过 PhysicalServerRoleVO）
 *               → 注入 HostAllocatorSpec.candidateHostUuids
 *       → 阶段2: 现有 HostAllocatorChain（全部 KVM Flow 正常执行）
 *         → HostSortorChain + reserveCapacity
 *           → AllocateHostReply
 */
public interface ServerAllocatorCompatibilityBridge {

    /**
     * 判断是否应启用兼容层（检查特性开关 + 角色类型）。
     */
    boolean shouldIntercept(AllocateHostMsg msg);

    /**
     * 执行两阶段兼容分配。
     *
     * @param msg 原始 AllocateHostMsg
     * @param completion 回调，成功返回 HostInventory 列表（与旧引擎返回格式一致）
     */
    void allocate(AllocateHostMsg msg,
                  ReturnValueCompletion<List<HostInventory>> completion);
}
```

**TODO：KVM 分配路径的两阶段整合方案待定**

当前 KVM 通过 HostCapacityVO VIEW 走现有 HostAllocatorChain 已能工作（VIEW 保证容量数据一致性），
但最终目标是所有角色的分配都经过 ServerAllocatorChain，确保扩展性。

待定事项：
1. **执行模式**：先阶段1再阶段2（串行），还是阶段1和阶段2融合执行（减少重复 Flow），需实现阶段验证性能后决定
2. **重复 Flow 问题**：串行两阶段时，Zone/Cluster/Status/Capacity 在两条链中各执行一次，有冗余。可能的优化方向：
   - 阶段1产出的候选集直接作为阶段2的初始 candidates（跳过阶段2中等价的 Flow）
   - 或将阶段1和阶段2的 Flow 合并为一条混合链
3. **接口预留**：CompatibilityBridge 接口保留，ServerAllocatorChain 的 Flow 链设计不假设 KVM 不经过。`candidateHostUuids` 注入机制是通用的，未来可用于任何需要预过滤的场景。

此 TODO 在实现阶段根据性能测试结果决定最终方案。

---

## 第 4.5 章：KVM + Container 混部容量管理

### 4.5.1 问题定义

一台物理机同时承载 KVM（ZStack 调度，INTERNAL_SHARED）和 Container（K8s 调度），两个调度器彼此不可见。需要解决：
- 如何防止容量超卖
- 如何计算混部下的真实可用容量
- 超分比在混部场景的语义

### 4.5.2 核心模型：互为系统预留

PhysicalServer 层是全知者，KVM 和 Container 互为"系统预留"：

```
PhysicalServer 层（唯一真相）:
  totalPhysicalCpu = 64
  kvmPhysicalUsed = 40
  containerPhysicalUsed = 20（Σ pod.requests.cpu）
  safetyBuffer = max(4, totalPhysical × 5%)
  realAvailable = 64 - 40 - 20 - 4 = 0

KVM Role 视角:
  availablePhysical = totalPhysical - containerReserved - safetyBuffer
  availableLogical = availablePhysical × overProvisioningRatio - kvmLogicalUsed
  → Container 消耗对 KVM 表现为"系统预留"，不可见

Container Role 视角:
  通过 Node Taint 控制（v1.0）
  通过 Device Plugin + Webhook 精确控制（v1.1+）
  → KVM 消耗对 Container 表现为不可调度
```

PhysicalServer 层通过 Msg/SDK 协调双方：
- KVM 消耗变化 → PhysicalServerCapacityUpdater 更新 → 检查是否需要打/移除 Taint
- Container Pod 变化 → sync 更新 → PhysicalServerCapacityVO.available 自动扣减

### 4.5.3 Safety Buffer

```
cpuSafetyBuffer = max(4 cores, totalPhysicalCpu × 5%)
memorySafetyBuffer = max(4 GB, totalPhysicalMemory × 10%)
```

- 内存 buffer 更保守（OOM kill 后果比 CPU 降速严重）
- GlobalConfig 配置，管理员可调
- DaemonSet 暗消耗纳入 buffer

### 4.5.4 超分比语义

超分比应用于 KVM 可用的物理份额（扣除 Container 消耗和 safety buffer 后）：

```
physicalAvailableForKVM = totalPhysicalCpu - containerPhysicalUsed - safetyBuffer
kvmAvailableLogical = physicalAvailableForKVM × cpuOverProvisioningRatio - kvmLogicalUsed
```

### 4.5.5 内存语义

Container 按 Pod **request**（不是 limit）扣除物理容量：

```
containerPhysicalMemoryUsed = Σ(pod.spec.containers[].resources.requests.memory)
```

理由：与 K8s 调度语义一致，request 是"保证量"。

### 4.5.6 K8s 侧防超卖（分期）

| 阶段 | 方案 | 实现 | 精度 |
|------|------|------|------|
| v1.0 | Node Taint 熔断 | kubeconfig → patchNode API | Node 级开关 |
| v1.1 | Device Plugin | DaemonSet + Extended Resource | Node 级精确 |
| v1.1 | Admission Webhook | Webhook Service | Pod 级精确 |

v1.0 Taint 逻辑（在 ContainerRoleProvider 或 PhysicalServerManagerImpl 中实现）：

```java
public void checkAndUpdateNodeTaint(String serverUuid) {
    PhysicalServerCapacityVO cap = dbf.findByUuid(serverUuid, PhysicalServerCapacityVO.class);
    long physicalAvailable = cap.getTotalPhysicalCpu()
        - getKvmPhysicalUsed(serverUuid)
        - getContainerPhysicalUsed(serverUuid);

    String nodeName = getK8sNodeName(serverUuid);
    if (nodeName == null) return; // 非混部节点

    if (physicalAvailable < cpuSafetyBuffer) {
        k8sClient.addTaint(nodeName, "zstack.io/capacity-exhausted", "NoSchedule");
    } else {
        k8sClient.removeTaint(nodeName, "zstack.io/capacity-exhausted");
    }
}
```

框架预留：v1.1 加 Device Plugin 和 Webhook 不改框架，只扩展 ContainerRoleProvider。

### 4.5.7 验收标准

- AC-1: kvmPhysicalUsed + containerPhysicalUsed + safetyBuffer ≤ totalPhysical
- AC-2: PhysicalServer 层与两侧偏差在 1 个 sync 周期（10s）内收敛
- AC-3: kvmAvailableLogical = (totalPhysical - containerReserved - safetyBuffer) × ratio - kvmUsed
- AC-4: physicalAvailable < safetyBuffer 时，ZStack 拒绝新 VM，K8s Node 被打 Taint

### 4.5.8 ADR（Architecture Decision Record）

- **Decision**: 互为系统预留 + Node Taint 熔断
- **Drivers**: 防超卖硬约束、单一数据源、调度器主权不侵犯
- **Alternatives**: 静态分区（利用率低）、Capacity Broker（复杂度高）、Zaku 项目配额（精度不够，会误限制非混部 Node）
- **Why chosen**: 最小实现成本 + 分期可扩展 + 基于实际环境验证（172.30.8.31/32 昇腾混部）
- **Consequences**: v1.0 粒度为 Node 级开关，sync 窗口内有有界不一致
- **Follow-ups**: v1.1 Device Plugin + Admission Webhook

### 4.5.9 已知限制

- sync 延迟窗口（~10s），safety buffer 兜底
- NUMA topology 不处理（v2.0）
- 项目配额不适用于部分混部的多节点集群

---

## 第 5 章：ServerPool 设计

### 5.1 ServerPoolVO 完整定义

见第 2.6 节。

### 5.2 Cluster:ServerPool 多对一关联设计

**设计决策**：使用关联表 `ClusterServerPoolRefVO`，而不是在 ClusterVO 上加 FK。

理由：
1. **不改 ClusterVO**（Wrap, don't delete 原则，保护 git blame）
2. 多个 Cluster 可引用同一 ServerPool（多对一），关联表可自然表达
3. 关联可选——未关联 ServerPool 的 Cluster 行为完全不变（向后兼容）
4. 未来如果需要多对多（一个 Cluster 对应多个 Pool），关联表无需改 schema

```java
package org.zstack.header.server;

import org.zstack.header.cluster.ClusterVO;
import org.zstack.header.vo.ForeignKey;
import org.zstack.header.vo.ForeignKey.ReferenceOption;

import javax.persistence.*;
import java.sql.Timestamp;

/**
 * Cluster 与 ServerPool 的关联表。
 *
 * 多个 Cluster 可引用同一个 ServerPool（多对一）。
 * UNIQUE(clusterUuid) 保证每个 Cluster 只关联一个 ServerPool。
 * 不使用 EO 软删除模式，直接硬删除。
 */
@Entity
@Table(uniqueConstraints = {
    @UniqueConstraint(name = "uk_cluster", columnNames = {"clusterUuid"})
})
public class ClusterServerPoolRefVO {

    @Id
    @Column
    private String uuid;

    @Column
    @ForeignKey(parentEntityClass = ClusterVO.class, onDeleteAction = ReferenceOption.CASCADE)
    private String clusterUuid;

    @Column
    @ForeignKey(parentEntityClass = ServerPoolVO.class, onDeleteAction = ReferenceOption.CASCADE)
    private String poolUuid;

    @Column
    private Timestamp createDate;

    // getter/setter 省略
}
```

### 5.3 PhysicalServer.poolUuid 关联

PhysicalServerAO 中的 `poolUuid` FK 直接指向 ServerPoolVO（见 2.2 节），创建时必填。ServerPool 删除前必须先移除或迁移所有 PhysicalServer（`onDeleteAction = RESTRICT`）。

---

## 第 6 章：统一装机网络

### 6.1 PhysicalServerProvisionNetworkVO 完整定义

见第 2.7 节。

### 6.2 与 BM1/BM2 的映射关系

| 统一字段 | BM1 BaremetalPxeServerVO | BM2 BareMetal2ProvisionNetworkVO |
|---------|------------------------|-------------------------------|
| uuid | 新生成 | 新生成 |
| zoneUuid | 从 Cluster → Zone 推导 | 直接字段 |
| dhcpInterface | pxeNicMac (对应网卡名) | dhcpInterface |
| dhcpRangeStartIp | 从 DHCP 配置推导 | dhcpRangeStartIp |
| dhcpRangeEndIp | 从 DHCP 配置推导 | dhcpRangeEndIp |
| dhcpRangeNetmask | 从 DHCP 配置推导 | dhcpRangeNetmask |
| dhcpRangeGateway | 从 DHCP 配置推导 | dhcpRangeGateway |
| type | STANDALONE_PXE | GATEWAY_PXE |

**迁移策略**：存量数据迁移脚本为每个 BM1 PxeServer 和 BM2 ProvisionNetwork 生成对应的 PhysicalServerProvisionNetworkVO 记录，保留原始 UUID 的映射关系（通过 SystemTag 记录 `originBm1PxeServerUuid::xxx` 或 `originBm2ProvisionNetworkUuid::xxx`）。

### 6.3 裸机装 KVM ISO 场景

工作流程：
1. 管理员创建 ProvisionNetwork（type = STANDALONE_PXE 或 GATEWAY_PXE）并关联到目标 Cluster
2. 管理员通过 APIAddPhysicalServerMsg 注册新物理服务器（提供 OOB 凭据）
3. KVM RoleProvider 调用 ProvisionNetwork 的 PXE/HTTP 引导能力，安装 KVM hypervisor ISO
4. 安装完成后 KVM agent 启动，走正常 KVM Host Connect 流程
5. PostConnect 钩子创建 PhysicalServerRoleVO（roleType = KVM_HOST）

ProvisionNetwork 的 API 不限定角色类型，任何角色均可调用装机流程。

---

## 第 7 章：统一 API

### 7.1 APIAddPhysicalServerMsg

```java
package org.zstack.header.server;

import org.zstack.header.identity.Action;
import org.zstack.header.message.APICreateMessage;
import org.zstack.header.message.APIParam;
import org.zstack.header.rest.RestRequest;
import org.zstack.header.server.enums.OobManagementType;

import org.springframework.http.HttpMethod;

/**
 * 添加物理服务器到统一管理。
 *
 * 关键字段：
 * - zoneUuid / poolUuid：归属
 * - serialNumber：硬件唯一标识（可选，连接后自动发现）
 * - managementIp：管理网 IP
 * - OOB 字段：可选（Container 场景无 OOB）
 */
@Action(category = PhysicalServerConstant.ACTION_CATEGORY)
@RestRequest(
    path = "/physical-servers",
    method = HttpMethod.POST,
    responseClass = APIAddPhysicalServerEvent.class
)
public class APIAddPhysicalServerMsg extends APICreateMessage {

    @APIParam(resourceType = ZoneVO.class)
    private String zoneUuid;

    @APIParam(resourceType = ServerPoolVO.class)
    private String poolUuid;

    @APIParam(maxLength = 255)
    private String name;

    @APIParam(required = false, maxLength = 2048)
    private String description;

    @APIParam(maxLength = 255)
    private String managementIp;

    @APIParam(required = false, maxLength = 255)
    private String serialNumber;

    @APIParam(required = false, maxLength = 255)
    private String architecture;

    @APIParam(required = false, maxLength = 255)
    private String manufacturer;

    @APIParam(required = false, maxLength = 255)
    private String model;

    // ---- OOB（可选） ----
    @APIParam(required = false)
    private OobManagementType oobManagementType;

    @APIParam(required = false, maxLength = 255)
    private String oobAddress;

    @APIParam(required = false)
    private Integer oobPort;

    @APIParam(required = false, maxLength = 255)
    private String oobUsername;

    @APIParam(required = false, maxLength = 255)
    private String oobPassword;

    // getter/setter 省略
}
```

### 7.2 APIDeletePhysicalServerMsg

```java
package org.zstack.header.server;

import org.zstack.header.identity.Action;
import org.zstack.header.message.APIDeleteMessage;
import org.zstack.header.message.APIParam;
import org.zstack.header.rest.RestRequest;

import org.springframework.http.HttpMethod;

/**
 * 删除物理服务器。
 * 删除前检查：所有角色 RoleVO 的 roleStatus 必须为 Stale 或不存在。
 * 如果仍有 Active 角色，需先删除角色再删物理服务器。
 */
@Action(category = PhysicalServerConstant.ACTION_CATEGORY)
@RestRequest(
    path = "/physical-servers/{uuid}",
    method = HttpMethod.DELETE,
    responseClass = APIDeletePhysicalServerEvent.class
)
public class APIDeletePhysicalServerMsg extends APIDeleteMessage {

    @APIParam(resourceType = PhysicalServerVO.class)
    private String uuid;

    // getter/setter 省略
}
```

### 7.3 APIQueryPhysicalServerMsg

```java
package org.zstack.header.server;

import org.zstack.header.identity.Action;
import org.zstack.header.query.APIQueryMessage;
import org.zstack.header.query.AutoQuery;
import org.zstack.header.rest.RestRequest;

import org.springframework.http.HttpMethod;

import java.util.List;

import static java.util.Arrays.asList;

/**
 * 统一查询物理服务器。
 * 支持标准 ZStack Query 语法，可按 poolUuid、zoneUuid、roleType、state、status 过滤。
 * 返回 PhysicalServerInventory（含角色列表、硬件汇总、容量信息）。
 */
@Action(category = PhysicalServerConstant.ACTION_CATEGORY, names = {"read"})
@AutoQuery(replyClass = APIQueryPhysicalServerReply.class,
           inventoryClass = PhysicalServerInventory.class)
@RestRequest(
    path = "/physical-servers",
    optionalPaths = {"/physical-servers/{uuid}"},
    responseClass = APIQueryPhysicalServerReply.class,
    method = HttpMethod.GET
)
public class APIQueryPhysicalServerMsg extends APIQueryMessage {

    public static List<String> __example__() {
        return asList("zoneUuid=" + uuid());
    }
}
```

#### PhysicalServerInventory 返回结构

```java
/**
 * 物理服务器 Inventory。QueryPhysicalServerMsg 的返回结构。
 *
 * roles 只展示引用（roleType + roleUuid + clusterUuid + status），
 * 不展开角色详情。通过 roleUuid 可跳转查询对应的 HostInventory/ChassisInventory。
 *
 * capacity 在物理机级别，available 已扣除所有角色消耗（包括 EXTERNAL_READONLY）。
 */
public class PhysicalServerInventory {
    private String uuid;
    private String name;
    private String zoneUuid;
    private String poolUuid;
    private String managementIp;
    private String serialNumber;
    private String architecture;
    private String manufacturer;
    private String model;
    private String state;       // Enabled / Disabled / Maintenance
    private String status;      // Connecting / Connected / Disconnected
    private String powerStatus; // PowerOn / PowerOff / Unknown

    // OOB
    private String oobManagementType;
    private String oobAddress;

    // 硬件汇总（1:1，可能为 null）
    private PhysicalServerHardwareInfoInventory hardwareInfo;

    // 容量（物理机级别，唯一数据源）
    private PhysicalServerCapacityInventory capacity;

    // 角色引用列表（1:N，只展示 ref）
    private List<PhysicalServerRoleRefInventory> roles;

    private Timestamp createDate;
    private Timestamp lastOpDate;
}

/**
 * 角色引用，只展示关联信息，不展开角色详情。
 */
public class PhysicalServerRoleRefInventory {
    private String roleType;        // KVM_HOST / BAREMETAL_V2 / CONTAINER_HOST
    private String roleUuid;        // 指向 HostVO.uuid / ChassisVO.uuid 等
    private String schedulingMode;  // INTERNAL_SHARED / INTERNAL_EXCLUSIVE / EXTERNAL_READONLY
    private String roleStatus;      // Active / Stale
    private String clusterUuid;
}
```

查询支持嵌套过滤：`conditions=roles.roleType=KVM_HOST`

### 7.4 APIPowerManagePhysicalServerMsg

```java
package org.zstack.header.server;

import org.zstack.header.identity.Action;
import org.zstack.header.message.APIMessage;
import org.zstack.header.message.APIParam;
import org.zstack.header.rest.RestRequest;

import org.springframework.http.HttpMethod;

/**
 * 统一电源管理 API。
 * 操作类型：PowerOn / PowerOff / PowerReset / GetPowerStatus。
 */
@Action(category = PhysicalServerConstant.ACTION_CATEGORY)
@RestRequest(
    path = "/physical-servers/{uuid}/actions",
    method = HttpMethod.PUT,
    responseClass = APIPowerManagePhysicalServerEvent.class,
    isAction = true
)
public class APIPowerManagePhysicalServerMsg extends APIMessage {

    @APIParam(resourceType = PhysicalServerVO.class)
    private String uuid;

    @APIParam(validValues = {"PowerOn", "PowerOff", "PowerReset", "GetPowerStatus"})
    private String action;

    /** true 时跳过前置检查（运行中 VM/Instance/Pod 检查），直接执行电源指令 */
    private boolean force;

    // getter/setter 省略
}
```

#### 处理流程

1. 查 PhysicalServerVO 的 OOB 凭据（oobAddress/oobType），无凭据则报错
2. 前置检查（PowerOff/PowerReset 时）：遍历所有 Active 角色的 RoleProvider，调用 `prePhysicalServerPowerOff(serverUuid)` 回调
   - KVM RoleProvider：检查有无运行中 VM，有则拒绝
   - BM2 RoleProvider：检查有无运行中 Instance，有则拒绝
   - Container RoleProvider：检查有无运行中 Pod，有则拒绝
   - `force=true` 时跳过前置检查
3. 通过 IPMI/Redfish 发送电源指令（统一层直接执行，不委托角色模块）
4. 更新 PhysicalServerVO.powerStatus

#### PhysicalServerRoleProvider SPI 新增方法

```java
/**
 * 电源关闭前置检查。返回 null 表示允许，返回 ErrorCode 表示拒绝。
 * PowerOff / PowerReset 时由统一层遍历所有 Active 角色的 Provider 依次调用。
 * force=true 时统一层跳过此调用。
 */
default ErrorCode prePhysicalServerPowerOff(String serverUuid) { return null; }
```

现有 12 个电源 API（3 模块 × 4 操作）保持不变，统一 API 是新增入口。

### 7.5 APIDiscoverPhysicalServerHardwareMsg

```java
package org.zstack.header.server;

import org.zstack.header.identity.Action;
import org.zstack.header.message.APIMessage;
import org.zstack.header.message.APIParam;
import org.zstack.header.rest.RestRequest;

import org.springframework.http.HttpMethod;

/**
 * 触发硬件信息采集。
 * 采集结果写入 PhysicalServerHardwareInfoVO + PhysicalServerHardwareDetailVO。
 */
@Action(category = PhysicalServerConstant.ACTION_CATEGORY)
@RestRequest(
    path = "/physical-servers/{uuid}/actions",
    method = HttpMethod.PUT,
    responseClass = APIDiscoverPhysicalServerHardwareEvent.class,
    isAction = true
)
public class APIDiscoverPhysicalServerHardwareMsg extends APIMessage {

    @APIParam(resourceType = PhysicalServerVO.class)
    private String uuid;

    // getter/setter 省略
}
```

#### 触发时机

- 手动 API 调用
- PhysicalServer 首次 OOB 连通时自动触发

#### 执行逻辑

1. **OOB 通道**：通过 IPMI FRU 采集 serialNumber、CPU、内存等（如果有 OOB 凭据）
2. **RoleProvider 通道**：遍历所有 Active 角色，调用 `HardwareDiscoverable.discover(serverUuid)`
   - 统一层是发起方，RoleProvider 是执行方
   - 每个角色主动执行一次完整的硬件发现：
     - KVM：调 agent `/collect-host-facts`
     - BM2：调 InspectChassis
     - Container：调 K8s Node Info
   - 不依赖各角色的 ping/connect 定时采集
   - 返回标准化的硬件信息（统一层定义的字段）
3. **合并**：OOB 数据优先，RoleProvider 补充缺失字段
4. **写入** PhysicalServerHardwareInfoVO / PhysicalServerHardwareDetailVO

#### HardwareDiscoverable 接口更新

原接口 `discoverHardware(String serverUuid, Completion completion)` 升级为带返回值的变体：

```java
/**
 * 主动触发硬件发现。统一层调用，RoleProvider 通过各自通道采集。
 * 返回标准化硬件信息，统一层合并后写入 HardwareInfoVO。
 *
 * @param serverUuid PhysicalServerVO 的 UUID
 * @param completion 完成回调，携带采集结果
 */
void discover(String serverUuid, ReturnValueCompletion<HardwareDiscoveryResult> completion);
```

`HardwareDiscoveryResult` 字段与 PhysicalServerHardwareInfoVO 对齐，统一层按 OOB 优先策略合并各 Provider 结果后写入 VO。

---

## 第 8 章：存量数据迁移

### 8.1 一次性 SQL 迁移脚本思路

```sql
-- 迁移脚本核心思路（幂等：INSERT IGNORE / ON DUPLICATE KEY）

-- 1. 为所有 KVM Host 创建 PhysicalServerVO（直接写入 PhysicalServerVO，不使用 EO 模式）
INSERT IGNORE INTO PhysicalServerVO (uuid, zoneUuid, poolUuid, name, managementIp,
    serialNumber, state, status, createDate, lastOpDate)
SELECT
    UUID(),                  -- 新 UUID（使用确定性生成：MD5(zoneUuid + managementIp)）
    h.zoneUuid,
    NULL,                    -- poolUuid 后续手动分配或创建默认 Pool
    h.name,
    h.managementIp,
    ipmi.serialNumber,       -- 从 HostIpmiVO 或 agent 数据获取
    h.state,
    h.status,
    h.createDate,
    NOW()
FROM HostVO h
LEFT JOIN HostIpmiVO ipmi ON h.uuid = ipmi.uuid
WHERE h.hypervisorType = 'KVM';

-- 2. 创建对应的 PhysicalServerRoleVO
INSERT IGNORE INTO PhysicalServerRoleVO (uuid, serverUuid, roleType, roleUuid,
    clusterUuid, schedulingMode, roleStatus, createDate, lastOpDate)
SELECT
    UUID(),
    ps.uuid,
    'KVM_HOST',
    h.uuid,
    h.clusterUuid,
    'INTERNAL_SHARED',
    'Active',
    NOW(),
    NOW()
FROM HostVO h
JOIN PhysicalServerVO ps ON ps.managementIp = h.managementIp
    AND ps.zoneUuid = h.zoneUuid
WHERE h.hypervisorType = 'KVM';

-- 3~4: 类似脚本为 BM1 Chassis、BM2 Chassis、NativeHost 生成对应记录
-- （BM1: INTERNAL_EXCLUSIVE, BM2: INTERNAL_EXCLUSIVE, Container: EXTERNAL_READONLY）
-- 5: 创建 PhysicalServerCapacityVO（从 HostCapacityVO 复制）
-- 6: 创建默认 ServerPool（每个 Zone 一个 "default-pool"）

-- ★ 6.5: 为每个新建资源注册 ResourceVO + AccountResourceRefVO
-- PhysicalServerVO 继承 ResourceVO，Hibernate persist 时会自动写入 ResourceVO。
-- 但迁移 SQL 直接 INSERT INTO PhysicalServerVO，需要手动补充 ResourceVO 和 AccountResourceRefVO 注册。
-- 同理 ServerPoolVO、PhysicalServerProvisionNetworkVO 等新资源也需要注册。

-- 6.5a: 为 PhysicalServerVO 注册 ResourceVO
INSERT IGNORE INTO ResourceVO (uuid, resourceType, resourceName, concreteResourceType)
SELECT uuid, 'PhysicalServerVO', name, 'org.zstack.header.server.PhysicalServerVO'
FROM PhysicalServerVO;

-- 6.5b: 为 PhysicalServerVO 注册 AccountResourceRefVO（关联到 admin 账户）
INSERT IGNORE INTO AccountResourceRefVO
    (uuid, accountUuid, ownerAccountUuid, resourceUuid, resourceType, concreteResourceType,
     permission, isShared, createDate, lastOpDate)
SELECT
    MD5(CONCAT('ps-acct-ref-', uuid)),
    '36c27e8ff05c4780bf6d2fa65700f22e',   -- admin 账户 UUID
    '36c27e8ff05c4780bf6d2fa65700f22e',
    uuid,
    'PhysicalServerVO',
    'org.zstack.header.server.PhysicalServerVO',
    2,       -- AccountConstant.RESOURCE_PERMISSION_WRITE
    0,       -- not shared
    NOW(), NOW()
FROM PhysicalServerVO;

-- 6.5c: 为 ServerPoolVO 注册 ResourceVO + AccountResourceRefVO
INSERT IGNORE INTO ResourceVO (uuid, resourceType, resourceName, concreteResourceType)
SELECT uuid, 'ServerPoolVO', name, 'org.zstack.header.server.ServerPoolVO'
FROM ServerPoolVO;

INSERT IGNORE INTO AccountResourceRefVO
    (uuid, accountUuid, ownerAccountUuid, resourceUuid, resourceType, concreteResourceType,
     permission, isShared, createDate, lastOpDate)
SELECT
    MD5(CONCAT('pool-acct-ref-', uuid)),
    '36c27e8ff05c4780bf6d2fa65700f22e',
    '36c27e8ff05c4780bf6d2fa65700f22e',
    uuid,
    'ServerPoolVO',
    'org.zstack.header.server.ServerPoolVO',
    2, 0, NOW(), NOW()
FROM ServerPoolVO;

-- 6.5d: 为 PhysicalServerProvisionNetworkVO 注册 ResourceVO + AccountResourceRefVO
INSERT IGNORE INTO ResourceVO (uuid, resourceType, resourceName, concreteResourceType)
SELECT uuid, 'PhysicalServerProvisionNetworkVO', name,
    'org.zstack.header.server.PhysicalServerProvisionNetworkVO'
FROM PhysicalServerProvisionNetworkVO;

INSERT IGNORE INTO AccountResourceRefVO
    (uuid, accountUuid, ownerAccountUuid, resourceUuid, resourceType, concreteResourceType,
     permission, isShared, createDate, lastOpDate)
SELECT
    MD5(CONCAT('pn-acct-ref-', uuid)),
    '36c27e8ff05c4780bf6d2fa65700f22e',
    '36c27e8ff05c4780bf6d2fa65700f22e',
    uuid,
    'PhysicalServerProvisionNetworkVO',
    'org.zstack.header.server.PhysicalServerProvisionNetworkVO',
    2, 0, NOW(), NOW()
FROM PhysicalServerProvisionNetworkVO;

-- 7: HostCapacityVO 从真表迁移为 VIEW（三步操作，顺序不可变）
--    Step 7a: 数据已在 Step 5 填充到 PhysicalServerCapacityVO，验证数据一致性
SELECT COUNT(*) FROM HostCapacityVO h
JOIN PhysicalServerRoleVO r ON r.roleUuid = h.uuid AND r.roleType = 'KVM_HOST'
JOIN PhysicalServerCapacityVO c ON c.uuid = r.serverUuid
WHERE h.totalCpu != c.totalPhysicalCpu OR h.availableCpu != c.availableCpu
   OR h.totalMemory != c.totalPhysicalMemory OR h.availableMemory != c.availableMemory;
-- 上述查询必须返回 0，否则中止迁移

--    Step 7b: DROP 原表（数据已安全存入 PhysicalServerCapacityVO）
DROP TABLE IF EXISTS HostCapacityVO;

--    Step 7c: 创建同名 VIEW，现有 47 个读取 HostCapacityVO 的代码零改动
CREATE VIEW HostCapacityVO AS
SELECT r.roleUuid AS uuid,
       c.totalCpu, c.availableCpu,
       c.totalMemory, c.availableMemory,
       c.totalPhysicalMemory, c.availablePhysicalMemory,
       c.cpuNum, c.cpuSockets, c.cpuCoreNum
FROM PhysicalServerCapacityVO c
JOIN PhysicalServerRoleVO r ON r.serverUuid = c.uuid
WHERE r.roleType = 'KVM_HOST';
```

**幂等保证**：
- 使用确定性 UUID 生成（`MD5(zoneUuid + managementIp)` 或 `MD5(zoneUuid + serialNumber)`），重复执行不会产生重复数据
- `INSERT IGNORE` + UNIQUE 约束兜底
- 迁移前后通过 COUNT 对比验证数据一致性
- Step 7 的 HostCapacityVO VIEW 迁移：先验证 PhysicalServerCapacityVO 数据完整性，再 DROP TABLE，最后 CREATE VIEW。回滚时可 DROP VIEW + 从备份恢复原表

### 8.2 各角色模块 PostConnect/PostCreate 钩子设计

```
角色生命周期钩子（单向创建，不做双写）：

KVM Host:
  ├── PostConnect 钩子：
  │   1. 读取 serialNumber（agent 调用 /sys/class/dmi/id/product_serial）
  │   2. 调用 matchExistingServer(serialNumber, managementIp, zoneUuid)
  │   3. 匹配成功 → 关联到已有 PhysicalServerVO，创建/更新 RoleVO
  │   4. 匹配失败 → 新建 PhysicalServerVO + RoleVO
  │   5. 初始化 PhysicalServerCapacityVO（HostCapacityUpdater 包装器后续直接写入此表）
  └── PreDestroy 钩子：
      1. 更新 RoleVO.roleStatus = Stale
      2. 如果是最后一个角色，更新 PhysicalServerVO.status = Disconnected

BM1 Chassis:
  ├── PostCreate 钩子：
  │   1. serialNumber 从 IPMI FRU 获取
  │   2. matchExistingServer → 关联或新建
  │   3. 同步 IPMI 信息到 PhysicalServerVO OOB 字段
  └── PreDestroy 钩子：同上

BM2 Chassis:
  ├── PostCreate 钩子：
  │   1. serialNumber 从 BM2 agent 获取
  │   2. matchExistingServer → 关联或新建
  │   3. 同步 OOB 信息
  └── PreDestroy 钩子：同上

Container (NativeHost):
  ├── PostConnect 钩子：
  │   1. serialNumber 从 agent 获取
  │   2. matchExistingServer → 关联或新建
  │   3. 容量标记为 EXTERNAL_READONLY
  └── PreDisconnect 钩子：
      1. 更新 RoleVO.roleStatus = Stale
```

### 8.3 角色自动关联匹配逻辑

```
匹配算法（优先级从高到低）：

1. serialNumber 精确匹配：
   SELECT uuid FROM PhysicalServerVO
   WHERE serialNumber = :serialNumber AND zoneUuid = :zoneUuid
   → 匹配成功：直接关联

2. managementIp + zoneUuid 降级匹配：
   SELECT uuid FROM PhysicalServerVO
   WHERE managementIp = :managementIp AND zoneUuid = :zoneUuid
   → 匹配成功：关联，同时回填 serialNumber

3. 无匹配：
   → 新建 PhysicalServerVO

注意事项：
- 降级匹配时 zoneUuid 必须一致（防止跨 Zone 误匹配）
- 匹配成功时需检查 UNIQUE(serverUuid, roleType) 约束，防止重复注册
- serialNumber 为空串或 "Not Specified" 时视为无效，跳过 step 1
```

---

## 第 9 章：模块专家分工说明

### 9.1 KVM 模块专家

**需要实现的 SPI 方法**：
- `KvmPhysicalServerRoleProvider implements PhysicalServerRoleProvider`
  - `getRoleType()` → `ServerRoleType.KVM_HOST`
  - `getSchedulingMode()` → `SchedulingMode.INTERNAL_SHARED`
  - `getCapacityConsumption(serverUuid)` → 从 PhysicalServerCapacityVO 读取已用 CPU/Memory（HostCapacityVO 已降级为 VIEW）
  - `matchExistingServer(context)` → serialNumber 优先，managementIp + zoneUuid 降级

**需要添加的钩子**：
- `KVMHostFactory` 或 `KVMHostConnectTask` 中，在 PostConnect 成功后调用 PhysicalServerManager 注册/关联
- `KVMHost.handleApiMessage()` 中 Delete 路径，PreDestroy 更新 RoleVO

**兼容性风险**：
- KVM 有 662 文件引用 Cluster 体系，HostAllocatorChain 有 17+ Flow——本次不改任何现有代码，只在 PostConnect 增量创建 PhysicalServerRoleVO
- CompatibilityBridge 两阶段薄适配中，阶段1只做通用过滤，KVM 特有的 13 个 Flow 在阶段2由现有 HostAllocatorChain 执行，无需重写；需测试阶段1输出的候选集映射回 HostVO UUID 的正确性

**Open Questions**：
1. KVM agent 是否在所有硬件平台都能稳定读取 `/sys/class/dmi/id/product_serial`？虚拟化嵌套场景下 serialNumber 是否可靠？
2. ~~KVM Host 的 HostCapacityVO 更新（connect/reconnect/VM 操作）是否需要同步触发 PhysicalServerCapacityVO 更新？还是异步定时对账？~~ **已解决**：PhysicalServerCapacityVO 是唯一真表，HostCapacityUpdater 包装器直接写入 PhysicalServerCapacityVO，HostCapacityVO 降级为 VIEW，无需任何同步机制。

### 9.2 BM1 模块专家（延后实现 — Could）

> **BM2 优先策略**：BM1 RoleProvider 延后实现，不在当前迭代交付。SPI 接口设计已为 BM1 保留完整兼容性：
> - `ServerRoleType` 枚举仍包含 `BAREMETAL_V1`
> - SPI 接口不含 BM2 特有假设（`getSchedulingMode()`、`getCapacityConsumption()` 语义通用）
> - `ProvisionNetworkVO` 保留 `STANDALONE_PXE` 类型（对应 BM1 BaremetalPxeServerVO）
> - `RoleMatchContext` 包含 `oobAddress` 字段（BM1 IPMI 降级匹配）
> - `CapacityState` 包含 `Initialized` 状态（BM1 硬件发现前容量未知场景）
>
> BM1 模块专家无需等待，可在 BM2 实现稳定后，通过实现同一套 SPI 无修改接入。

**需要实现的 SPI 方法**：
- `Bm1PhysicalServerRoleProvider implements PhysicalServerRoleProvider`
  - `getRoleType()` → `ServerRoleType.BAREMETAL_V1`
  - `getSchedulingMode()` → `SchedulingMode.INTERNAL_EXCLUSIVE`
  - `getCapacityConsumption(serverUuid)` → BM1 独占模式，分配后返回全部物理容量
  - `matchExistingServer(context)` → IPMI serialNumber / managementIp

**需要添加的钩子**：
- `BaremetalChassisManagerImpl` 中 createChassis 成功后注册 PhysicalServerVO
- deleteChassis 路径更新 RoleVO

**兼容性风险**：
- BM1 的 BaremetalPxeServerVO 到 PhysicalServerProvisionNetworkVO 的映射可能丢失部分 DHCP 配置细节
- BM1 的状态机（PowerOn → Allocated → Running）与 PhysicalServer 的状态机是独立的，需确保不冲突

**Open Questions**：
1. BM1 BaremetalPxeServerVO 的 DHCP 配置是否完全可以从现有数据推导出 dhcpRangeStartIp/EndIp？还是需要管理员手动补充？
2. BM1 的 IPMI 凭据与 PhysicalServerVO 的 OOB 字段是否完全对应？有无 BM1 特有的 IPMI 参数？

### 9.3 BM2 模块专家

**需要实现的 SPI 方法**：
- `Bm2PhysicalServerRoleProvider implements PhysicalServerRoleProvider`
  - `getRoleType()` → `ServerRoleType.BAREMETAL_V2`
  - `getSchedulingMode()` → `SchedulingMode.INTERNAL_EXCLUSIVE`
  - `getCapacityConsumption(serverUuid)` → 独占模式
  - `matchExistingServer(context)` → serialNumber / managementIp

**需要添加的钩子**：
- `BareMetal2ChassisManager` 中 createChassis 后注册
- deleteChassis 更新 RoleVO
- BM2 弹性/绑定模式需在 RoleVO 中正确映射

**兼容性风险**：
- BM2 ProvisionNetwork 是最接近统一模型的，迁移风险最低
- BM2 弹性模式的 Chassis 可能在不同 Cluster 间漂移，需确保 RoleVO.clusterUuid 正确更新

**Open Questions**：
1. BM2 弹性模式下 Chassis 切换 Cluster 时，PhysicalServerRoleVO.clusterUuid 如何同步？是在 BM2 的 Cluster 切换钩子中更新？
2. BM2 的电源管理网关（通过 BM2 agent 代理 IPMI）是否需要覆盖 PowerManageable 默认实现？

### 9.4 Container 模块专家

**需要实现的 SPI 方法**：
- `ContainerPhysicalServerRoleProvider implements PhysicalServerRoleProvider`
  - `getRoleType()` → `ServerRoleType.CONTAINER_HOST`
  - `getSchedulingMode()` → `SchedulingMode.EXTERNAL_READONLY`
  - `getCapacityConsumption(serverUuid)` → K8s 报告的容量（只读展示）
  - `matchExistingServer(context)` → serialNumber / managementIp

**需要添加的钩子**：
- NativeHost Connect 成功后注册 PhysicalServerVO
- NativeHost Disconnect/Delete 更新 RoleVO

**兼容性风险**：
- NativeHostVO 继承 HostVO，走 HostAllocatorChain。但 Container 不参与 ZStack 容量分配，需确保 CompatibilityBridge 阶段1正确识别 EXTERNAL_READONLY 并跳过
- Container 模块可能在 plugin/ 或 premium/ 中，需确认模块位置和依赖链

**Open Questions**：
1. KVM + Container 混部场景下，同一物理机的 managementIp 是否相同？如果不同（多网卡），如何匹配？
2. K8s 报告的节点容量（Allocatable vs Capacity）应取哪个值写入 PhysicalServerCapacityVO？
3. Container 场景下 NativeHostVO 已有自己的 Connect/Reconnect 逻辑，PostConnect 钩子是否存在且可扩展？

### 9.5 分配引擎专家

**需要实现的核心组件**：
- `ServerAllocatorChain` + `ServerAllocatorChainBuilder`（对齐 HostAllocatorChain 模式）
- 10 个 Filter Flow（见 4.3 节）
- `PhysicalServerCapacityUpdaterImpl`（悲观锁 + @DeadlockAutoRestart）
- `ServerCapacityOverProvisioningManagerImpl`
- `ServerAllocatorCompatibilityBridgeImpl`

**需要关注的兼容性风险**：
- CompatibilityBridge 两阶段薄适配中，阶段1只做通用过滤不涉及 KVM 特有字段，风险大幅降低；关键风险点在于 PhysicalServer→HostVO UUID 映射的正确性
- 阶段2复用现有 HostAllocatorChain，KVM Flow 全部保留——需验证 candidateHostUuids 注入后 DesignatedHostAllocatorFlow 的预过滤行为正确
- 锁获取顺序必须与现有 HostCapacityUpdater 一致，避免引入新死锁

**Open Questions**：
1. ServerAllocatorChain 的 Flow 类名列表是硬编码还是通过 Spring XML/Java Config 配置？（现有 HostAllocatorChain 通过 Spring XML 配置 flowClassNames）
2. CompatibilityBridge 的 shouldIntercept() 判断逻辑：是根据 GlobalConfig per-hypervisorType 开关，还是根据目标 Cluster 的 hypervisorType？
3. 阶段1候选集对比验证：灰度期间是否对比阶段1输出的候选集与旧路径结果，确保阶段1输出是旧路径结果的超集？
4. PhysicalServerCapacityUpdater 的锁范围：是锁单行（WHERE uuid = ?）还是锁表？应与 HostCapacityUpdater 保持一致（单行悲观锁）

---

## 附录 A：VO 关系图

```
ZoneVO
  ├── ServerPoolVO (zoneUuid FK)
  │     ├── ClusterServerPoolRefVO (poolUuid FK)
  │     │     └── ClusterVO (clusterUuid FK)
  │     └── PhysicalServerVO (poolUuid FK)
  │           ├── PhysicalServerCapacityVO (uuid 共享，1:1)
  │           ├── PhysicalServerHardwareInfoVO (uuid 共享，1:1)
  │           ├── PhysicalServerHardwareDetailVO (serverUuid FK，1:N)
  │           └── PhysicalServerRoleVO (serverUuid FK，1:N)
  │                 ├── roleUuid → HostVO.uuid (KVM_HOST)
  │                 ├── roleUuid → BaremetalChassisVO.uuid (BAREMETAL_V1)
  │                 ├── roleUuid → BareMetal2ChassisVO.uuid (BAREMETAL_V2)
  │                 └── roleUuid → NativeHostVO.uuid (CONTAINER_HOST)
  └── PhysicalServerProvisionNetworkVO (zoneUuid FK)
        └── PhysicalServerProvisionNetworkClusterRefVO (networkUuid FK)
              └── ClusterVO (clusterUuid FK)
```

## 附录 B：关键设计决策汇总

| # | 决策 | 理由 |
|---|------|------|
| D1 | PhysicalServerVO 独立于 HostVO 继承链 | 避免污染现有继承体系；PhysicalServer 是物理概念，Host 是逻辑概念 |
| D2 | AO/VO 两层模式（不用 EO） | 新表不需要软删除回收站，硬删除简化数据模型；删除前已有角色检查保护，FK CASCADE 保证级联清理 |
| D3 | RoleVO.roleUuid 不加 FK 约束 | 多态引用（指向 4 种不同表），FK 约束无法表达；一致性由 RoleProvider 钩子保证 |
| D4 | CapacityVO 共享 UUID（1:1） | 对齐 HostCapacityVO 模式，简化 JOIN |
| D5 | Cluster:ServerPool 用关联表而非改 ClusterVO | Wrap, don't delete 原则；不污染 ClusterVO 的 git blame |
| D6 | CompatibilityBridge 采用两阶段薄适配而非 originalMessage 透传 | 阶段1只做通用过滤不读 KVM 特有字段，阶段2复用现有 HostAllocatorChain，避免在新链中重写 16 个 Flow 的高风险 |
| D7 | ServerAllocatorChain 独立于 HostAllocatorChain | 职责分离；新引擎操作 PhysicalServerVO，旧引擎操作 HostVO |
| D8 | SchedulingMode 冗余在 RoleVO 上 | 分配引擎热路径优化，无需每次回调 SPI |
| D9 | 超分比同时管理 CPU 和 Memory | 现有 HostCapacityOverProvisioningManager 只管 Memory，新引擎补齐 CPU |
| D10 | 新建 server/ 顶层模块 | 独立命名空间，避免与 compute/ 混淆 |
| D11 | PhysicalServerCapacityVO 是唯一真表，HostCapacityVO 降级为 VIEW | 消除双表同步的复杂度和延迟风险；HostCapacityUpdater 包装器保证 59 个调用方零改动 |
