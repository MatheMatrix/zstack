# Header 层开发计划

**版本**: v2.2
**更新时间**: 2026-01-30
**变更说明**: Server 层统一分配 - 新增 AllocateServerMsg 统一分配入口，ServerCapacityVO 作为唯一容量存储，删除 HostCapacityVO 依赖

## 变更记录

| 版本 | 日期 | 变更内容 |
|------|------|----------|
| v2.2 | 2026-01-30 | **Server 层统一分配**: 新增 AllocateServerMsg/AllocateServerReply 统一分配入口；ServerCapacityVO 作为唯一容量存储（删除 HostCapacityVO）；新增 ServerAllocatorManager/ServerAllocatorChain/ServerSortorChain；新增 ServerCapacityUpdater 容量更新器；设计 HostAllocatorCompatibilityLayer 兼容层；三阶段迁移策略 |
| v2.1 | 2026-01-30 | **容量管理复用**: 根据 compute-resource-allocator agent 分析，删除过度抽象的 ResourceAllocationVO 和 ResourceAllocator；ServerCapacityVO 参照 HostCapacityVO 设计；复用 HostCapacityUpdater 悲观锁模式；通过扩展点集成现有分配流程 |
| v2.0 | 2026-01-30 | **架构重构**: 确立统一架构主导原则，删除 SyncCreatable/GatewayAllocatable/ChassisOfferingManageable/PxeBootable/CapacityManageable 等特化接口，保留 4 个核心能力接口；精简 RoleAdapter 接口 |
| v1.2 | 2026-01-29 | 根据四模块评审反馈修复全部 P1 问题（12项） |
| v1.1 | 2026-01-29 | 新增 GatewayAllocatable/SyncCreatable 接口 |
| v1.0 | 2026-01-29 | 初始版本 |

---

## 1. 核心架构原则（必须遵守）

### 1.1 统一架构主导，而非模块主导

**统一架构 Lead 四个模块，模块必须适配统一架构，不是架构去迁就模块的历史包袱。**

- 模块 agent 的反馈是**思考角度**，不是**设计指令**
- 架构师有权拒绝任何破坏架构一致性的特化请求
- 当模块提出特化需求时，首先考虑是否能通过统一接口 + Adapter 层实现差异

### 1.2 禁止特化接口

| 禁止的设计 | 原因 | 正确做法 |
|-----------|------|---------|
| `ReadOnlyClusterBindable` | 只读绑定是实现细节 | Adapter 层处理，统一使用 ClusterBindable |
| `SyncCreatable` | 同步 vs 主动创建是实现差异 | Adapter 层处理，统一使用 RoleAdapter.createRole() |
| `ChassisOfferingManageable` | Offering 是业务概念 | 不进入统一接口层，Baremetal2 模块内处理 |
| `GatewayAllocatable` | Gateway 是实现细节 | 不进入统一接口层，Baremetal2 模块内处理 |
| `PxeBootable` | PXE 是 Baremetal 实现细节 | 通过 HardwareDiscoverable 统一处理 |
| `CapacityManageable` | 容量管理上移到资源分配层 | 使用统一的 ResourceAllocator |
| 模块特有状态字段 | 状态必须归一化 | 使用统一的 ServerState/ServerStatus |

### 1.3 保留的核心接口

```
RoleAdapter (核心接口)
    ├── getRoleType() / getRoleVoClass()
    ├── createRole() / deleteRole()
    ├── syncFromRole() / syncToRole()
    ├── validateForRole()
    ├── getRoleInventory()
    └── 生命周期钩子

能力接口（4个，不可再增加）
    ├── PowerManageable      - 电源控制
    ├── HardwareDiscoverable - 硬件发现
    ├── AgentDeployable      - Agent 部署
    └── ClusterBindable      - Cluster 绑定
```

### 1.4 Server 层统一容量管理

**ServerCapacityVO 作为唯一容量存储，删除 HostCapacityVO 依赖**。详见第 10 节完整设计。

| 要点 | 说明 |
|------|------|
| 统一入口 | AllocateServerMsg 作为所有分配请求的入口 |
| 单一容量存储 | ServerCapacityVO 取代 HostCapacityVO |
| 悲观锁 | ServerCapacityUpdater 使用 PESSIMISTIC_WRITE |
| 责任链 | ServerAllocatorChain 可扩展 Flow |
| 兼容层 | HostAllocatorCompatibilityLayer 保证老 API 兼容 |

### 1.5 向下兼容

- 保留现有 BaremetalChassisVO、HostVO 等结构
- 现有 API 100% 兼容
- 通过引用表关联新旧结构

---

## 2. 架构层次设计

```
┌─────────────────────────────────────────────────────────────────┐
│                    Unified Hardware Management                   │
├─────────────────────────────────────────────────────────────────┤
│  ┌──────────────────────────────────────────────────────────┐   │
│  │              Physical Server Layer                        │   │
│  │   PhysicalServerVO / ServerCapacityVO / ServerHardwareInfoVO │
│  │   物理资源的唯一真相源                                      │   │
│  └──────────────────────────────────────────────────────────┘   │
│                              │                                   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │              Role Adapter Layer                           │   │
│  │   RoleAdapter + 能力接口 (4个)                            │   │
│  └──────────────────────────────────────────────────────────┘   │
│                              │                                   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │              Role Resource Layer (现有系统)               │   │
│  │   KVMHostVO / NativeHostVO / BaremetalChassisVO          │   │
│  │   通过兼容层与 Server 层交互                               │   │
│  └──────────────────────────────────────────────────────────┘   │
│                              │                                   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │              Consumer Layer (资源消费者)                  │   │
│  │   VmInstanceVO / PodVO / BaremetalInstanceVO             │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

---

## 3. VO 类设计

### 3.1 PhysicalServerAO（抽象基类）

```java
package org.zstack.header.server;

@MappedSuperclass
public class PhysicalServerAO extends ResourceVO {
    @Column
    @ForeignKey(parentEntityClass = ZoneEO.class, onDeleteAction = ReferenceOption.RESTRICT)
    private String zoneUuid;

    @Column
    @ForeignKey(parentEntityClass = ServerPoolVO.class, onDeleteAction = ReferenceOption.SET_NULL)
    private String poolUuid;

    @Column
    @Index
    private String name;

    @Column
    private String description;

    @Column
    @Index
    private String managementIp;

    @Column
    private String architecture;

    @Column
    @Enumerated(EnumType.STRING)
    private ServerState state;

    @Column
    @Enumerated(EnumType.STRING)
    private ServerStatus status;

    @Column
    @Enumerated(EnumType.STRING)
    private OobManagementType oobManagementType;

    @Column
    private String oobAddress;

    @Column
    private Integer oobPort;

    @Column
    private String oobUsername;

    @Column
    private String oobPassword;

    @Column
    private String powerState;

    @Column
    @Index
    private String serialNumber;

    @Column
    private String manufacturer;

    @Column
    private String model;

    @Column
    private Timestamp createDate;

    @Column
    private Timestamp lastOpDate;
}
```

### 3.2 PhysicalServerVO

```java
package org.zstack.header.server;

@Entity
@Table(name = "PhysicalServerVO")
@AutoDeleteTag
@BaseResource
public class PhysicalServerVO extends PhysicalServerAO {
    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "uuid")
    @NoView
    private ServerCapacityVO capacity;

    @OneToMany(fetch = FetchType.EAGER)
    @JoinColumn(name = "serverUuid", insertable = false, updatable = false)
    @NoView
    private Set<PhysicalServerRoleVO> roles = new HashSet<>();
}
```

### 3.3 ServerPoolVO

```java
package org.zstack.header.server;

@Entity
@Table(name = "ServerPoolVO")
public class ServerPoolVO extends ResourceVO {
    @Column
    @ForeignKey(parentEntityClass = ZoneEO.class, onDeleteAction = RESTRICT)
    private String zoneUuid;

    @Column
    private String name;

    @Column
    private String description;

    @Column
    @Enumerated(EnumType.STRING)
    private ServerPoolState state;

    @Column
    private String physicalLocation; // 物理位置（如：机房 A-01 机架）

    @Column
    private String networkTopology;   // 物理网络拓扑标识（关联二层网络连通性）

    @Column
    private Timestamp createDate;

    @Column
    private Timestamp lastOpDate;
}
```

### 3.4 PhysicalServerRoleVO

```java
package org.zstack.header.server;

@Entity
@Table(name = "PhysicalServerRoleVO")
public class PhysicalServerRoleVO {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column
    @ForeignKey(parentEntityClass = PhysicalServerVO.class, onDeleteAction = CASCADE)
    private String serverUuid;

    @Column
    private String roleType;  // 使用 String，通过 ServerRoleTypes 注册

    @Column
    private String roleUuid;

    @Column
    @Enumerated(EnumType.STRING)
    private SyncStatus syncStatus;

    @Column
    private Timestamp lastSyncTime;

    @Column
    private Timestamp createDate;

    @Column
    private Timestamp lastOpDate;
}
```

### 3.5 ServerCapacityVO

```java
package org.zstack.header.allocator;

@Entity
@Table(name = "ServerCapacityVO")
public class ServerCapacityVO {
    @Id
    private String uuid;  // 与 PhysicalServerVO 共享

    @Column
    private long totalCpu;

    @Column
    private long availableCpu;

    @Column
    private int cpuNum;

    @Column
    private int cpuSockets;

    @Column
    private int cpuCoreNum;

    @Column
    private long totalMemory;

    @Column
    private long availableMemory;

    @Column
    private long totalPhysicalMemory;

    @Column
    private long availablePhysicalMemory;

    @Column
    private long totalDisk;

    @Column
    private long availableDisk;

    @Column
    @Enumerated(EnumType.STRING)
    private CapacityState capacityState;
}
```

---

## 4. 枚举类设计

### 4.1 ServerState

```java
public enum ServerState {
    Enabled,
    Disabled,
    PreMaintenance,
    Maintenance
}
```

### 4.2 ServerStatus

```java
public enum ServerStatus {
    Connecting,
    Connected,
    Disconnected
}
```

### 4.3 ServerRoleTypes（注册机制）

```java
package org.zstack.header.server;

public final class ServerRoleTypes {
    private static final Map<String, ServerRoleType> REGISTRY = new ConcurrentHashMap<>();

    public static final ServerRoleType BARE_METAL = register(
        "BARE_METAL", "BaremetalChassis",
        "org.zstack.header.baremetal.chassis.BaremetalChassisVO"
    );

    public static final ServerRoleType BARE_METAL_V2 = register(
        "BARE_METAL_V2", "BaremetalV2Chassis",
        "org.zstack.baremetal2.chassis.BaremetalChassisVO"
    );

    public static final ServerRoleType KVM_HOST = register(
        "KVM_HOST", "KVMHost",
        "org.zstack.kvm.KVMHostVO"
    );

    public static final ServerRoleType NATIVE_HOST = register(
        "NATIVE_HOST", "NativeHost",
        "org.zstack.header.container.NativeHostVO"
    );

    public static ServerRoleType register(String type, String displayName, String voClassName) {
        // 注册逻辑
    }

    public static ServerRoleType valueOf(String type) {
        // 查找逻辑
    }
}
```

### 4.4 OobManagementType

```java
public enum OobManagementType {
    IPMI("IPMI", 623),
    Redfish("Redfish", 443),
    iLO("HP iLO", 443),
    iDRAC("Dell iDRAC", 443),
    IMM("Lenovo IMM", 443)
}
```

### 4.5 SyncStatus

```java
public enum SyncStatus {
    InSync,
    ServerNewer,
    RoleNewer,
    Syncing,
    SyncFailed
}
```

---

## 5. RoleAdapter 接口设计（精简版）

### 5.1 核心接口 RoleAdapter

```java
package org.zstack.header.server;

public interface RoleAdapter {

    ServerRoleType getRoleType();

    Class<?> getRoleVoClass();

    void syncFromRole(String roleUuid, String serverUuid, Completion completion);

    void syncToRole(String serverUuid, String roleUuid, Completion completion);

    void createRole(PhysicalServerInventory server, RoleCreationContext context,
                    ReturnValueCompletion<String> completion);

    void deleteRole(String roleUuid, NoErrorCompletion completion);

    RoleValidationResult validateForRole(PhysicalServerInventory server, RoleCreationContext context);

    Object getRoleInventory(String roleUuid);

    /**
     * 获取角色当前消耗的资源容量
     * 用于 Recalculate 流程：统计 VM、Pod 等占用的真实资源
     */
    ServerCapacityInfo getCapacityConsumption(String roleUuid);

    // 生命周期钩子
    default void onRoleActivated(String roleUuid, String serverUuid) {}

    default void onRoleDeactivated(String roleUuid, String serverUuid) {}

    default void onRoleStateChanged(String roleUuid, String serverUuid,
                                    String oldState, String newState) {}
}
```

### 5.2 PowerManageable

```java
public interface PowerManageable {
    void powerOn(String roleUuid, Completion completion);
    void powerOff(String roleUuid, Completion completion);
    void powerReset(String roleUuid, Completion completion);
    void getPowerStatus(String roleUuid, ReturnValueCompletion<String> completion);
}
```

### 5.3 HardwareDiscoverable

```java
public interface HardwareDiscoverable {
    void triggerDiscovery(String roleUuid, Completion completion);
    void handleHardwareInfoCallback(String identifier, ServerHardwareInfo info, Completion completion);
    void checkOobConnection(String oobAddress, int oobPort, String username, String password,
                            ReturnValueCompletion<ConnectionCheckResult> completion);
}
```

### 5.4 AgentDeployable

```java
public interface AgentDeployable {
    void deployAgent(String roleUuid, Completion completion);
    void reconnectAgent(String roleUuid, Completion completion);
    void checkAgentStatus(String roleUuid, ReturnValueCompletion<Boolean> completion);
}
```

### 5.5 ClusterBindable

```java
public interface ClusterBindable {
    RoleValidationResult validateClusterCompatibility(String serverUuid, String clusterUuid);
    void bindToCluster(String roleUuid, String clusterUuid, Completion completion);
    void unbindFromCluster(String roleUuid, Completion completion);
}
```

### 5.6 各角色适配器实现

| 适配器 | 核心接口 | 能力接口 |
|--------|---------|---------|
| BaremetalRoleAdapter | RoleAdapter | PowerManageable, HardwareDiscoverable |
| Baremetal2RoleAdapter | RoleAdapter | PowerManageable, HardwareDiscoverable |
| KvmHostRoleAdapter | RoleAdapter | AgentDeployable, ClusterBindable |
| ContainerHostRoleAdapter | RoleAdapter | ClusterBindable |

### 5.7 实现差异在 Adapter 层处理

```java
// Container 的绑定不支持手动操作
class ContainerHostRoleAdapter implements RoleAdapter, ClusterBindable {
    @Override
    public void bindToCluster(String roleUuid, String clusterUuid, Completion completion) {
        throw new OperationFailureException(
            operr("Container host cluster binding is determined by K8s")
        );
    }

    @Override
    public void createRole(...) {
        throw new OperationFailureException(
            operr("NativeHost can only be created through K8s sync")
        );
    }
}
```

---

## 6. Pod 创建流程

### 6.1 K8s 同步发现（主流程）

```
K8s Cluster                         ZStack Container Module
    │                                       │
    │  kubectl apply -f pod.yaml            │
    │  ──────────────────────►              │
    │                                       │
    │  Pod scheduled to Node                │
    │       │                               │
    │       │    SyncContainerEndpointMsg   │
    │       │  ◄────────────────────────────┤
    │       │                               │
    │       ▼                               │
    │  Return Pod list ────────────────────►│
    │                                       │
    │                               1. 查找/创建 PhysicalServer
    │                               2. 创建 PodVO
    │                               3. 更新 HostCapacityVO (复用现有机制)
```

### 6.2 ZStack 主动创建

```
User                    ZStack API                  K8s API
  │                         │                          │
  │  APICreatePodMsg ──────►│                          │
  │                         │                          │
  │                 1. 选择 PhysicalServer (by capacity)
  │                 2. 预分配容量 (复用 HostCapacityUpdater)
  │                         │                          │
  │                         │  Create Pod ────────────►│
  │                         │◄───────── Pod Created ───│
  │                         │                          │
  │                 3. 创建 PodVO，确认分配              │
  │◄── APICreatePodReply ───│                          │
```

### 6.3 Pod 与 PhysicalServer 的关系

```
PhysicalServerVO
    │
    ├── PhysicalServerRoleVO (roleType=NATIVE_HOST)
    │       └── roleUuid ──► NativeHostVO
    │                            └── PodVO.hostUuid
    │
    └── ServerCapacityVO (容量通过 HostCapacityUpdater 模式更新)
            ├── totalCpu / availableCpu
            └── totalMemory / availableMemory
```

---

## 7. 数据库迁移脚本

### V5.5.6__schema.sql

```sql
-- ServerPoolVO
CREATE TABLE IF NOT EXISTS `ServerPoolVO` (
    `uuid` VARCHAR(32) NOT NULL PRIMARY KEY,
    `zoneUuid` VARCHAR(32) NOT NULL,
    `name` VARCHAR(255) NOT NULL,
    `description` VARCHAR(2048) DEFAULT NULL,
    `state` VARCHAR(32) NOT NULL DEFAULT 'Enabled',
    `createDate` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `lastOpDate` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT `fkServerPoolVOZoneEO` FOREIGN KEY (`zoneUuid`)
        REFERENCES `ZoneEO` (`uuid`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- PhysicalServerVO
CREATE TABLE IF NOT EXISTS `PhysicalServerVO` (
    `uuid` VARCHAR(32) NOT NULL PRIMARY KEY,
    `zoneUuid` VARCHAR(32) NOT NULL,
    `poolUuid` VARCHAR(32) DEFAULT NULL,
    `name` VARCHAR(255) NOT NULL,
    `description` VARCHAR(2048) DEFAULT NULL,
    `managementIp` VARCHAR(255) DEFAULT NULL,
    `architecture` VARCHAR(64) DEFAULT NULL,
    `state` VARCHAR(32) NOT NULL DEFAULT 'Enabled',
    `status` VARCHAR(32) NOT NULL DEFAULT 'Connecting',
    `oobManagementType` VARCHAR(32) DEFAULT NULL,
    `oobAddress` VARCHAR(255) DEFAULT NULL,
    `oobPort` INT DEFAULT NULL,
    `oobUsername` VARCHAR(255) DEFAULT NULL,
    `oobPassword` VARCHAR(255) DEFAULT NULL,
    `powerState` VARCHAR(32) DEFAULT NULL,
    `serialNumber` VARCHAR(255) DEFAULT NULL,
    `manufacturer` VARCHAR(255) DEFAULT NULL,
    `model` VARCHAR(255) DEFAULT NULL,
    `createDate` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `lastOpDate` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idxPhysicalServerVOZoneUuid` (`zoneUuid`),
    INDEX `idxPhysicalServerVOPoolUuid` (`poolUuid`),
    INDEX `idxPhysicalServerVOManagementIp` (`managementIp`),
    INDEX `idxPhysicalServerVOSerialNumber` (`serialNumber`),
    CONSTRAINT `fkPhysicalServerVOZoneEO` FOREIGN KEY (`zoneUuid`)
        REFERENCES `ZoneEO` (`uuid`) ON DELETE RESTRICT,
    CONSTRAINT `fkPhysicalServerVOServerPoolVO` FOREIGN KEY (`poolUuid`)
        REFERENCES `ServerPoolVO` (`uuid`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- PhysicalServerRoleVO
CREATE TABLE IF NOT EXISTS `PhysicalServerRoleVO` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `serverUuid` VARCHAR(32) NOT NULL,
    `roleType` VARCHAR(32) NOT NULL,
    `roleUuid` VARCHAR(32) NOT NULL,
    `syncStatus` VARCHAR(32) NOT NULL DEFAULT 'InSync',
    `lastSyncTime` TIMESTAMP NULL DEFAULT NULL,
    `createDate` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `lastOpDate` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `ukServerRole` (`serverUuid`, `roleType`),
    UNIQUE KEY `ukRoleUuid` (`roleUuid`),
    INDEX `idxPhysicalServerRoleVOServerUuid` (`serverUuid`),
    CONSTRAINT `fkPhysicalServerRoleVOPhysicalServerVO` FOREIGN KEY (`serverUuid`)
        REFERENCES `PhysicalServerVO` (`uuid`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ServerCapacityVO
CREATE TABLE IF NOT EXISTS `ServerCapacityVO` (
    `uuid` VARCHAR(32) NOT NULL PRIMARY KEY,
    `totalCpu` BIGINT NOT NULL DEFAULT 0,
    `availableCpu` BIGINT NOT NULL DEFAULT 0,
    `cpuNum` INT NOT NULL DEFAULT 0,
    `cpuSockets` INT NOT NULL DEFAULT 0,
    `cpuCoreNum` INT NOT NULL DEFAULT 0,
    `totalMemory` BIGINT NOT NULL DEFAULT 0,
    `availableMemory` BIGINT NOT NULL DEFAULT 0,
    `capacityState` VARCHAR(32) NOT NULL DEFAULT 'Normal',
    CONSTRAINT `fkServerCapacityVOPhysicalServerVO` FOREIGN KEY (`uuid`)
        REFERENCES `PhysicalServerVO` (`uuid`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ServerHardwareInfoVO
CREATE TABLE IF NOT EXISTS `ServerHardwareInfoVO` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `serverUuid` VARCHAR(32) NOT NULL,
    `infoType` VARCHAR(64) NOT NULL,
    `infoKey` VARCHAR(255) NOT NULL,
    `infoValue` TEXT DEFAULT NULL,
    `createDate` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `lastOpDate` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idxServerHardwareInfoVOServerUuid` (`serverUuid`),
    CONSTRAINT `fkServerHardwareInfoVOPhysicalServerVO` FOREIGN KEY (`serverUuid`)
        REFERENCES `PhysicalServerVO` (`uuid`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ServerPoolL2RefVO
CREATE TABLE IF NOT EXISTS `ServerPoolL2RefVO` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `poolUuid` VARCHAR(32) NOT NULL,
    `l2NetworkUuid` VARCHAR(32) NOT NULL,
    `createDate` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `lastOpDate` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `ukServerPoolL2Ref` (`poolUuid`, `l2NetworkUuid`),
    CONSTRAINT `fkServerPoolL2RefVOServerPoolVO` FOREIGN KEY (`poolUuid`)
        REFERENCES `ServerPoolVO` (`uuid`) ON DELETE CASCADE,
    CONSTRAINT `fkServerPoolL2RefVOL2NetworkEO` FOREIGN KEY (`l2NetworkUuid`)
        REFERENCES `L2NetworkEO` (`uuid`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## 8. 文件清单

| 文件路径 | 类型 | 说明 |
|---------|------|------|
| `header/.../server/PhysicalServerAO.java` | VO | 抽象基类 |
| `header/.../server/PhysicalServerVO.java` | VO | 物理服务器 |
| `header/.../server/ServerPoolVO.java` | VO | 服务器池 |
| `header/.../server/PhysicalServerRoleVO.java` | VO | 角色映射 |
| `header/.../allocator/ServerCapacityVO.java` | VO | 容量管理（参照 HostCapacityVO 设计） |
| `header/.../server/ServerState.java` | 枚举 | 服务器状态 |
| `header/.../server/ServerStatus.java` | 枚举 | 连接状态 |
| `header/.../server/ServerRoleTypes.java` | 类 | 角色类型注册 |
| `header/.../server/RoleAdapter.java` | 接口 | 核心适配器 |
| `header/.../server/PowerManageable.java` | 接口 | 电源管理 |
| `header/.../server/HardwareDiscoverable.java` | 接口 | 硬件发现 |
| `header/.../server/AgentDeployable.java` | 接口 | Agent 部署 |
| `header/.../server/ClusterBindable.java` | 接口 | Cluster 绑定 |
| `conf/db/upgrade/V5.5.6__schema.sql` | SQL | 数据库迁移 |

**总计**: ~25 个文件（较 v2.0 精简，删除了过度抽象的 ResourceAllocationVO 和 ResourceAllocator）

---

## 10. Server 层统一分配（长期方案）

### 10.1 核心变更

| 项目 | 旧架构 | 新架构 |
|-----|--------|--------|
| 容量存储 | HostCapacityVO (每个 Host 一个) | ServerCapacityVO (每个 PhysicalServer 一个) |
| 分配入口 | AllocateHostMsg → HostAllocatorChain | AllocateServerMsg → ServerAllocatorChain |
| 分配结果 | 返回 hostUuid | 返回 serverUuid + roleUuid |
| 消费者关联 | VmInstanceVO.hostUuid → HostVO | VmInstanceVO.hostUuid → HostVO (兼容) + 内部通过 RoleVO 关联 Server |

### 10.2 统一分配流程

```
┌─────────────────────────────────────────────────────────────────────┐
│                         新分配流程                                   │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│   VM/Pod/BM 创建                                                     │
│        │                                                             │
│        ▼                                                             │
│   AllocateServerMsg                                                  │
│   ┌─────────────────────────────────────────────────────────────┐   │
│   │  requiredCpu, requiredMemory, requiredRoleType              │   │
│   │  (roleType: KVM_HOST / NATIVE_HOST / BARE_METAL)            │   │
│   └─────────────────────────────────────────────────────────────┘   │
│        │                                                             │
│        ▼                                                             │
│   ┌─────────────────────────────────────────────────────────────┐   │
│   │              ServerAllocatorManager (新)                     │   │
│   │                                                              │   │
│   │  1. ServerAllocatorChain 过滤候选 PhysicalServer             │   │
│   │     ├── ServerStateAllocatorFlow (state=Enabled)            │   │
│   │     ├── ServerCapacityAllocatorFlow (容量检查)               │   │
│   │     ├── ServerRoleAllocatorFlow (有对应角色)                 │   │
│   │     ├── ServerClusterAllocatorFlow (角色绑定的 Cluster)      │   │
│   │     └── ... 扩展 Flow                                        │   │
│   │                                                              │   │
│   │  2. ServerSortorChain 排序 + 预留容量                         │   │
│   │     ├── LeastUsedServerSortorFlow                           │   │
│   │     └── ServerCapacityReserveFlow (悲观锁更新)               │   │
│   │                                                              │   │
│   └─────────────────────────────────────────────────────────────┘   │
│        │                                                             │
│        ▼                                                             │
│   AllocateServerReply                                                │
│   ┌─────────────────────────────────────────────────────────────┐   │
│   │  serverUuid: "ps-001"                                        │   │
│   │  roleUuid: "host-001" (KVMHostVO.uuid)                       │   │
│   │  roleType: KVM_HOST                                          │   │
│   └─────────────────────────────────────────────────────────────┘   │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### 10.3 消息定义

```java
// ========== AllocateServerMsg ==========

@APIMessage
public class AllocateServerMsg extends NeedReplyMessage {
    @APIParam
    private String zoneUuid;

    @APIParam(required = false)
    private String clusterUuid;

    @APIParam(required = false)
    private String serverPoolUuid;

    @APIParam
    private String requiredRoleType;  // KVM_HOST / NATIVE_HOST / BARE_METAL

    @APIParam
    private long requiredCpu;

    @APIParam
    private long requiredMemory;

    @APIParam(required = false)
    private String allocatorStrategy;  // LEAST_USED / RANDOM / ...

    // 向下兼容：指定具体 Server
    @APIParam(required = false)
    private String serverUuid;
}

// ========== AllocateServerReply ==========

public class AllocateServerReply extends MessageReply {
    private String serverUuid;
    private String roleUuid;      // = hostUuid for VM, = chassisUuid for BM
    private String roleType;
    private ServerCapacityInventory capacity;
}

// ========== ServerAllocatorSpec ==========

public class ServerAllocatorSpec {
    private String zoneUuid;
    private String clusterUuid;
    private String serverPoolUuid;
    private String requiredRoleType;
    private long requiredCpu;
    private long requiredMemory;
    private String allocatorStrategy;
    private String serverUuid;  // 指定分配

    // 排除列表
    private List<String> avoidServerUuids;
}
```

### 10.4 分配器接口

```java
// ========== 分配策略接口 ==========

public interface ServerAllocatorStrategy {
    void allocate(ServerAllocatorSpec spec,
                  ReturnValueCompletion<List<PhysicalServerInventory>> completion);

    void dryRun(ServerAllocatorSpec spec,
                ReturnValueCompletion<List<PhysicalServerInventory>> completion);
}

// ========== 分配 Flow 接口 ==========

public interface ServerAllocatorFlow {
    void allocate(ServerAllocatorSpec spec,
                  List<PhysicalServerInventory> candidates,
                  ReturnValueCompletion<List<PhysicalServerInventory>> completion);

    void rollback(ServerAllocatorSpec spec,
                  FlowRollback trigger,
                  Map<String, Object> data);
}

// ========== 排序 Flow 接口 ==========

public interface ServerSortorFlow {
    void sort(ServerAllocatorSpec spec,
              List<PhysicalServerInventory> candidates,
              ReturnValueCompletion<List<PhysicalServerInventory>> completion);
}
```

### 10.5 ServerCapacityUpdater

```java
package org.zstack.server.allocator;

public class ServerCapacityUpdater {
    private String serverUuid;

    public ServerCapacityUpdater(String serverUuid) {
        this.serverUuid = serverUuid;
    }

    @Transactional
    @DeadlockAutoRestart
    public boolean reserve(long cpu, long memory) {
        ServerCapacityVO cap = dbf.getEntityManager().find(
            ServerCapacityVO.class,
            serverUuid,
            LockModeType.PESSIMISTIC_WRITE  // 悲观锁
        );

        if (cap == null) {
            logger.warn("ServerCapacityVO not found for server: " + serverUuid);
            return false;
        }

        if (cap.getAvailableCpu() < cpu) {
            throw new UnableToReserveCapacityException(
                operr("Not enough CPU capacity. Required: %d, Available: %d",
                      cpu, cap.getAvailableCpu())
            );
        }

        if (cap.getAvailableMemory() < memory) {
            throw new UnableToReserveCapacityException(
                operr("Not enough memory capacity. Required: %d, Available: %d",
                      memory, cap.getAvailableMemory())
            );
        }

        cap.setAvailableCpu(cap.getAvailableCpu() - cpu);
        cap.setAvailableMemory(cap.getAvailableMemory() - memory);
        dbf.getEntityManager().merge(cap);

        logger.debug(String.format(
            "Reserved capacity on server[uuid:%s]: cpu=%d, memory=%d",
            serverUuid, cpu, memory
        ));

        return true;
    }

    @Transactional
    @DeadlockAutoRestart
    public void release(long cpu, long memory) {
        ServerCapacityVO cap = dbf.getEntityManager().find(
            ServerCapacityVO.class,
            serverUuid,
            LockModeType.PESSIMISTIC_WRITE
        );

        if (cap == null) {
            logger.warn("ServerCapacityVO not found for server: " + serverUuid);
            return;
        }

        long newAvailCpu = Math.min(cap.getAvailableCpu() + cpu, cap.getTotalCpu());
        long newAvailMem = Math.min(cap.getAvailableMemory() + memory, cap.getTotalMemory());

        cap.setAvailableCpu(newAvailCpu);
        cap.setAvailableMemory(newAvailMem);
        dbf.getEntityManager().merge(cap);

        logger.debug(String.format(
            "Released capacity on server[uuid:%s]: cpu=%d, memory=%d",
            serverUuid, cpu, memory
        ));
    }
}
```

### 10.6 分配 Flow 实现

```java
// ========== ServerStateAllocatorFlow ==========

@Component
public class ServerStateAllocatorFlow implements ServerAllocatorFlow {
    @Override
    public void allocate(ServerAllocatorSpec spec,
                         List<PhysicalServerInventory> candidates,
                         ReturnValueCompletion<List<PhysicalServerInventory>> completion) {
        List<PhysicalServerInventory> result = candidates.stream()
            .filter(s -> ServerState.Enabled.toString().equals(s.getState()))
            .filter(s -> ServerStatus.Connected.toString().equals(s.getStatus()))
            .collect(Collectors.toList());

        if (result.isEmpty()) {
            completion.fail(operr("No server in Enabled/Connected state"));
            return;
        }

        completion.success(result);
    }
}

// ========== ServerCapacityAllocatorFlow ==========

@Component
public class ServerCapacityAllocatorFlow implements ServerAllocatorFlow {
    @Autowired
    private DatabaseFacade dbf;

    @Override
    public void allocate(ServerAllocatorSpec spec,
                         List<PhysicalServerInventory> candidates,
                         ReturnValueCompletion<List<PhysicalServerInventory>> completion) {
        long requiredCpu = spec.getRequiredCpu();
        long requiredMemory = spec.getRequiredMemory();

        List<PhysicalServerInventory> result = new ArrayList<>();

        for (PhysicalServerInventory server : candidates) {
            ServerCapacityVO cap = dbf.findByUuid(server.getUuid(), ServerCapacityVO.class);
            if (cap == null) continue;

            if (cap.getAvailableCpu() >= requiredCpu &&
                cap.getAvailableMemory() >= requiredMemory) {
                result.add(server);
            }
        }

        if (result.isEmpty()) {
            completion.fail(operr(
                "No server has enough capacity. Required: cpu=%d, memory=%d",
                requiredCpu, requiredMemory
            ));
            return;
        }

        completion.success(result);
    }
}

// ========== ServerRoleAllocatorFlow ==========

@Component
public class ServerRoleAllocatorFlow implements ServerAllocatorFlow {
    @Override
    public void allocate(ServerAllocatorSpec spec,
                         List<PhysicalServerInventory> candidates,
                         ReturnValueCompletion<List<PhysicalServerInventory>> completion) {
        String requiredRoleType = spec.getRequiredRoleType();

        List<PhysicalServerInventory> result = candidates.stream()
            .filter(s -> s.getRoles().stream()
                .anyMatch(r -> r.getRoleType().equals(requiredRoleType)))
            .collect(Collectors.toList());

        if (result.isEmpty()) {
            completion.fail(operr("No server has role type: %s", requiredRoleType));
            return;
        }

        completion.success(result);
    }
}

// ========== ServerClusterAllocatorFlow ==========

@Component
public class ServerClusterAllocatorFlow implements ServerAllocatorFlow {
    @Override
    public void allocate(ServerAllocatorSpec spec,
                         List<PhysicalServerInventory> candidates,
                         ReturnValueCompletion<List<PhysicalServerInventory>> completion) {
        String clusterUuid = spec.getClusterUuid();
        if (clusterUuid == null) {
            // 不限制 cluster
            completion.success(candidates);
            return;
        }

        String roleType = spec.getRequiredRoleType();

        List<PhysicalServerInventory> result = candidates.stream()
            .filter(s -> {
                // 检查角色绑定的 cluster
                return s.getRoles().stream()
                    .filter(r -> r.getRoleType().equals(roleType))
                    .anyMatch(r -> isRoleBoundToCluster(r.getRoleUuid(), clusterUuid));
            })
            .collect(Collectors.toList());

        if (result.isEmpty()) {
            completion.fail(operr(
                "No server with role[%s] bound to cluster[%s]",
                roleType, clusterUuid
            ));
            return;
        }

        completion.success(result);
    }

    private boolean isRoleBoundToCluster(String roleUuid, String clusterUuid) {
        // 查询 HostVO.clusterUuid 或其他角色的 cluster 绑定
        // ...
    }
}
```

### 10.7 兼容层设计

```java
// ========== HostAllocatorCompatibilityLayer ==========

@Component
public class HostAllocatorCompatibilityLayer implements HostAllocatorStrategy {
    @Autowired
    private ServerAllocatorManager serverAllocatorManager;
    @Autowired
    private DatabaseFacade dbf;

    @Override
    public void allocate(HostAllocatorSpec spec,
                         ReturnValueCompletion<List<HostInventory>> completion) {
        // 1. 转换为 AllocateServerMsg
        AllocateServerMsg msg = new AllocateServerMsg();
        msg.setRequiredRoleType(ServerRoleTypes.KVM_HOST.toString());
        msg.setRequiredCpu(spec.getCpuCapacity());
        msg.setRequiredMemory(spec.getMemoryCapacity());
        msg.setClusterUuid(spec.getClusterUuid());
        msg.setZoneUuid(spec.getZoneUuid());

        if (spec.getHostUuid() != null) {
            // 指定 host，需要找到对应的 server
            String serverUuid = findServerUuidByRoleUuid(spec.getHostUuid());
            msg.setServerUuid(serverUuid);
        }

        // 2. 调用 Server 层分配
        serverAllocatorManager.allocate(msg, new ReturnValueCompletion<AllocateServerReply>(completion) {
            @Override
            public void success(AllocateServerReply reply) {
                // 3. 转换结果：roleUuid 就是 hostUuid
                HostVO host = dbf.findByUuid(reply.getRoleUuid(), HostVO.class);
                if (host == null) {
                    completion.fail(operr("Host not found: %s", reply.getRoleUuid()));
                    return;
                }
                completion.success(Collections.singletonList(HostInventory.valueOf(host)));
            }

            @Override
            public void fail(ErrorCode errorCode) {
                completion.fail(errorCode);
            }
        });
    }

    private String findServerUuidByRoleUuid(String roleUuid) {
        String sql = "select r.serverUuid from PhysicalServerRoleVO r where r.roleUuid = :roleUuid";
        TypedQuery<String> q = dbf.getEntityManager().createQuery(sql, String.class);
        q.setParameter("roleUuid", roleUuid);
        List<String> result = q.getResultList();
        return result.isEmpty() ? null : result.get(0);
    }
}

// ========== ServerReservedCapacityExtensionPoint ==========

public interface ServerReservedCapacityExtensionPoint {
    /**
     * 获取系统预留容量（如 OS 预留、Ceph Agent 预留等）
     */
    ServerCapacityInfo getReservedCapacity(String serverUuid);
}

// ========== HostCapacity API 兼容 ==========

// QueryHostCapacity 返回虚拟的 HostCapacityInventory
// 实际数据来自 ServerCapacityVO，按角色拆分展示

@Component
public class HostCapacityQueryExtension implements QueryExtensionPoint<HostCapacityInventory> {
    // 兼容查询，从 ServerCapacityVO 构造 HostCapacityInventory
}
```

### 10.8 超分策略处理

```java
// ServerCapacityVO 增强

@Entity
@Table(name = "ServerCapacityVO")
public class ServerCapacityVO {
    @Id
    private String uuid;

    // 物理容量（真实硬件）
    @Column
    private long totalPhysicalCpu;

    @Column
    private long totalPhysicalMemory;

    // 超分比（可配置）
    @Column
    private double cpuOverprovisioningRatio = 10.0;   // 默认 10 倍

    @Column
    private double memoryOverprovisioningRatio = 1.0; // 默认不超分

    // 计算后的可分配容量
    public long getTotalCpu() {
        return (long)(totalPhysicalCpu * cpuOverprovisioningRatio);
    }

    public long getTotalMemory() {
        return (long)(totalPhysicalMemory * memoryOverprovisioningRatio);
    }

    // 剩余容量
    @Column
    private long availableCpu;

    @Column
    private long availableMemory;

    @Column
    private long reservedMemory;  // 已预留的系统内存（如 OS、Ceph 等）

    // 详细字段
    @Column
    private int cpuNum;

    @Column
    private int cpuSockets;

    @Column
    private int cpuCoreNum;
}
```

### 10.9 迁移策略（三阶段）

#### Phase 1: 双写期（向下兼容）

```
目标：新老系统并行运行

1. 新建 ServerCapacityVO，与 HostCapacityVO 双写
2. 新分配可选择走 ServerAllocatorManager
3. 老 API (AllocateHostMsg) 通过兼容层转发
4. 所有现有功能不受影响
```

#### Phase 2: 迁移期（数据迁移）

```
目标：迁移存量数据

1. 迁移脚本：HostCapacityVO → ServerCapacityVO
   - 为每个 HostVO 创建对应的 PhysicalServerVO
   - 复制容量数据
   - 建立 PhysicalServerRoleVO 映射

2. 切换默认分配入口
   - AllocateHostMsg 默认走 HostAllocatorCompatibilityLayer
   - 内部转发到 ServerAllocatorManager
```

#### Phase 3: 清理期（删除老逻辑）

```
目标：移除冗余代码

1. 删除 HostCapacityVO（不再使用）
2. 删除老分配逻辑（HostAllocatorChain 等）
3. 兼容层降级为只读查询（QueryHostCapacity 等）
4. AllocateHostMsg 完全由兼容层处理
```

### 10.10 新增文件清单

| 文件路径 | 类型 | 说明 |
|---------|------|------|
| `header/.../server/AllocateServerMsg.java` | 消息 | 统一分配请求 |
| `header/.../server/AllocateServerReply.java` | 消息 | 分配结果 |
| `header/.../server/ServerAllocatorSpec.java` | 类 | 分配规格 |
| `header/.../server/ServerAllocatorStrategy.java` | 接口 | 分配策略 |
| `header/.../server/ServerAllocatorFlow.java` | 接口 | 分配 Flow |
| `header/.../server/ServerSortorFlow.java` | 接口 | 排序 Flow |
| `server/.../allocator/ServerAllocatorManager.java` | 服务 | 分配管理器 |
| `server/.../allocator/ServerAllocatorChain.java` | 类 | 分配链 |
| `server/.../allocator/ServerSortorChain.java` | 类 | 排序链 |
| `server/.../allocator/ServerCapacityUpdater.java` | 类 | 容量更新器 |
| `server/.../allocator/flow/ServerStateAllocatorFlow.java` | Flow | 状态过滤 |
| `server/.../allocator/flow/ServerCapacityAllocatorFlow.java` | Flow | 容量过滤 |
| `server/.../allocator/flow/ServerRoleAllocatorFlow.java` | Flow | 角色过滤 |
| `server/.../allocator/flow/ServerClusterAllocatorFlow.java` | Flow | Cluster 过滤 |
| `server/.../allocator/flow/LeastUsedServerSortorFlow.java` | Flow | 负载排序 |
| `server/.../allocator/flow/ServerCapacityReserveFlow.java` | Flow | 容量预留 |
| `server/.../compatibility/HostAllocatorCompatibilityLayer.java` | 兼容 | Host 分配兼容 |
| `server/.../compatibility/HostCapacityQueryExtension.java` | 兼容 | 容量查询兼容 |

---

## 11. 更新后的实施检查清单

- [ ] 所有 VO 类继承自正确的基类
- [ ] RoleAdapter 仅有 4 个能力接口扩展
- [ ] 无特化接口（无 SyncCreatable、GatewayAllocatable 等）
- [ ] ServerCapacityVO 作为唯一容量存储
- [ ] ServerCapacityUpdater 使用悲观锁模式
- [ ] AllocateServerMsg 作为统一分配入口
- [ ] HostAllocatorCompatibilityLayer 保证老 API 兼容
- [ ] 各模块 Adapter 实现差异在类内部处理
- [ ] 迁移策略分三阶段执行

---

## 12. 资源上线流程（Discovery-First）

统一硬件管理采用“先发现，后绑定”的流程，确保 PhysicalServerVO 是硬件能力的唯一真相源。

### 12.1 流程图

```
User                API / ServerManager            Hardware / RoleAdapter
  │                         │                               │
  │ APIAddPhysicalServerMsg │                               │
  ├────────────────────────►│                               │
  │ (OOB IP, Credentials)   │ 1. checkOobConnection()       │
  │                         ├──────────────────────────────►│
  │                         │ 2. triggerDiscovery()         │
  │                         ├──────────────────────────────►│
  │                         │◄──────────────────────────────┤
  │                         │ 3. Create PhysicalServerVO    │
  │                         │    (SN, CPU, Mem, Model...)   │
  │◄────────────────────────┤                               │
  │                         │                               │
  │ APIBindPhysicalServerRoleMsg                            │
  ├────────────────────────►│                               │
  │ (RoleType, Config)      │ 4. validateForRole()          │
  │                         ├──────────────────────────────►│
  │                         │ 5. createRole()               │
  │                         ├──────────────────────────────►│
  │                         │    (Create KVMHostVO etc.)    │
  │                         │ 6. create PhysicalServerRoleVO│
  │◄────────────────────────┤                               │
```

### 12.2 关键消息设计

```java
// APIAddPhysicalServerMsg: 仅通过 OOB 添加硬件资源
@APIMessage
public class APIAddPhysicalServerMsg extends APICreateMessage {
    @APIParam
    private String zoneUuid;
    @APIParam(required = false)
    private String poolUuid;
    @APIParam
    private String oobAddress;
    @APIParam
    private String oobUsername;
    @APIParam
    private String oobPassword;
    @APIParam
    private OobManagementType oobManagementType;
}

// APIBindPhysicalServerRoleMsg: 为现有硬件绑定业务角色
@APIMessage
public class APIBindPhysicalServerRoleMsg extends APIMessage {
    @APIParam(resourceType = PhysicalServerVO.class)
    private String serverUuid;
    @APIParam
    private String roleType; // KVM_HOST, BARE_METAL, NATIVE_HOST
    @APIParam(required = false)
    private Map<String, String> roleConfig; // 角色特有配置，如 KVM 的 managementIp
}
```

---

## 13. 监控架构与状态机

采用“双轨制”监控，兼顾硬件健康（OOB）与业务连通性（IB）。

### 13.1 双轨监控模型

| 维度 | 监控轨道 | 负责对象 | 核心指标 | 同步机制 |
| :--- | :--- | :--- | :--- | :--- |
| **带外 (OOB)** | Resource Health Track | BMC / IPMI / Redfish | 电源状态、风扇、温度、物理链路 | `HardwareDiscoverable` 定时轮询 |
| **带内 (IB)** | Role Function Track | Agent / OS Heartbeat | 角色服务状态 (Host Status) | 通过 RoleAdapter 生命周期钩子反向同步 |

### 13.2 状态机定义 (PhysicalServerStatus)

`PhysicalServerVO.status` 反映硬件的综合可用性：

- **Connecting**: 正在进行 OOB 发现或角色 Agent 部署。
- **Connected**: OOB 连通且**所有**已绑定角色的 IB 心跳正常。
- **Disconnected**: OOB 不连通，或**任一**关键角色（如 KVM Host）心跳丢失。
- **Maintenance**: 人工标记维护，此时不触发自动重连逻辑，也不作为分配候选。

### 13.3 状态同步逻辑

```java
public class ServerStatusManager {
    // 处理带外监控回调
    public void onOobStatusChanged(String serverUuid, String oobStatus) {
        // 如果 OOB 断开，PhysicalServer 设为 Disconnected
        // 并通知所有关联角色进入 Unknown/Disconnected 状态
    }

    // 处理带内角色心跳回调 (通过 ExtensionPoint)
    public void onRoleStatusChanged(String roleUuid, String oldStatus, String newStatus) {
        // 通过 PhysicalServerRoleVO 找到 serverUuid
        // 综合评估是否需要更新 PhysicalServerVO.status
    }
}
```

---

**计划版本**: v2.3
**更新时间**: 2026-02-02
**变更说明**: 新增资源上线流程（Section 12）与双轨监控架构（Section 13）
