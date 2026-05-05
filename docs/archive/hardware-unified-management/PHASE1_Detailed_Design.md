# Phase 1 详细设计：数据模型与 API 定义 (Code-Level)

**版本**: v2.0
**日期**: 2026-02-27
**前置**: 基于 `REVISED_Architecture_Assessment.md` v4.1 + `ARCHITECT_DECISION.md` v2.0 (7 大裁决)
**方法**: 所有代码均严格复制 ZStack 现有模式（引用具体文件路径）
**变更 v1.1**: 移除 EO 层（无软删除需求）；恢复统一分配子系统（ServerCapacityVO + AllocateServerMsg）
**变更 v2.0**: 基于 5 位领域专家评审 + 总架构师裁决，全面修订。详见下方变更清单。

### v2.0 变更清单

| # | 变更 | 影响范围 | 来源 |
|---|------|---------|------|
| 1 | PhysicalServerAO 新增 `powerStatus` 字段 (PhysicalServerPowerStatus 枚举) | AO/VO/Inventory/DDL | 基于总架构师裁决 1.5 (三维状态) |
| 2 | PhysicalServerState 新增 `PreMaintenance` 状态 + `preMaintain` 事件 | State/Event 枚举 | 基于 KVM 专家评审 P0 |
| 3 | OOB 字段全部改为 nullable，oobManagementType 增加 `NONE` | AO/API Msg | 基于 Container 专家评审 P0 |
| 4 | PhysicalServerRoleVO 新增 `clusterUuid`, `sourceUuid`, `roleStatus` | RoleVO/Inventory/DDL | 基于总架构师裁决 1.1 (per-role cluster) |
| 5 | ServerRoleType 移除 `isExclusive()`，改为 `getSchedulingMode()` + SchedulingMode 枚举 | 枚举重构 | 基于总架构师裁决 1.3 (调度模式) |
| 6 | 新增 SchedulingMode 枚举: INTERNAL_SHARED / INTERNAL_EXCLUSIVE / EXTERNAL_READONLY | 新文件 | 基于总架构师裁决 1.3 |
| 7 | 新增 PhysicalServerPowerStatus 枚举: PowerOn / PowerOff / PowerUnknown | 新文件 | 基于总架构师裁决 1.5 |
| 8 | ServerCapacityVO 移除 cpuOverprovisioningRatio / memoryOverprovisioningRatio 字段 | VO/DDL | 基于总架构师裁决 1.6 (独立 Manager) |
| 9 | ServerCapacityVO 移除 getTotalCpu() / getTotalMemory() 计算 getter | VO | 基于总架构师裁决 1.6 |
| 10 | ServerCapacityVO 新增 totalCpu / totalMemory (预计算持久化) | VO/DDL | 基于总架构师裁决 1.6 |
| 11 | ServerCapacityVO 新增 availablePhysicalMemory / cpuNum / cpuSockets / cpuCoreNum | VO/DDL | 基于 KVM 专家 + Allocator 专家评审 |
| 12 | ServerCapacityVO 新增 exclusiveRoleUuid / schedulingMode | VO/DDL | 基于 Allocator 专家建议 |
| 13 | AllocateServerMsg 增加 avoidServerUuids / softAvoidServerUuids / diskSize / architecture / extraData | Msg/Spec | 基于总架构师裁决 1.2 |
| 14 | AllocateServerMsg requiredCpu / requiredMemory 改为 nullable (Long) | Msg | 基于总架构师裁决 1.2 (BM 整机分配) |
| 15 | AllocateServerMsg 移除 clusterUuid，改用 requiredClusterUuids (List) | Msg | 基于总架构师裁决 1.1 |
| 16 | ServerAllocatorSpec 新增 extraData Map | Spec | 基于总架构师裁决 1.2 |
| 17 | ServerHardwareInfoVO 新增 bootMode / discoverySource | VO/DDL | 基于 BM1 专家评审 P1 |
| 18 | 新增 ServerHardwareDetailVO (1:N 硬件详情子表) | 新文件+DDL | 基于总架构师裁决 1.4 |
| 19 | 新增 HardwareDetailType 枚举: BASIC / NIC / DISK / GPU / PCI / MEMORY_DIMM | 新文件 | 基于总架构师裁决 1.4 |
| 20 | 新增 ServerHardwareDetailInventory | 新文件 | 基于总架构师裁决 1.4 |
| 21 | ServerCapacityUpdater 拆分 @Transactional / @DeadlockAutoRestart 为内外两层 | 实现层 | 基于 Allocator 专家评审 P0 (编译错误) |
| 22 | ServerCapacityUpdater 引入 ServerCapacityUpdaterRunnable 回调模式 | 接口设计 | 基于 Allocator 专家建议 |
| 23 | 新增 ServerCapacityOverProvisioningManager 接口 | 新文件 | 基于总架构师裁决 1.6 |
| 24 | 新增 ServerReservedCapacityExtensionPoint 接口 | 新文件 | 基于 Allocator 专家建议 |
| 25 | 新增 ServerAllocatorFilterExtensionPoint 接口 | 新文件 | 基于 Allocator 专家建议 |
| 26 | 新增 ServerAllocatorCompatibilityBridge 接口 | 新文件 | 基于总架构师裁决 1.7 |
| 27 | 新增 RecalculateServerCapacityMsg 内部消息 | 新文件 | 基于 Allocator 专家建议 |
| 28 | 新增 PhysicalServerGlobalConfig (allocator.enabled / cpu.overProvisioning.ratio / memory.overProvisioning.ratio) | 新文件+配置 | 基于 Allocator 专家建议 |
| 29 | PhysicalServerVO 表名明确为 `PhysicalServerVO`，LAZY fetch | VO | 基于 KVM + Allocator 专家评审 |
| 30 | PhysicalServerInventory 新增 powerStatus / hardwareDetails | Inventory | 基于总架构师裁决 1.5 / 1.4 |
| 31 | PhysicalServerRoleInventory 新增 clusterUuid / sourceUuid / roleStatus | Inventory | 基于总架构师裁决 1.1 |
| 32 | APIRegisterPhysicalServerMsg OOB 字段改为 required=false | API Msg | 基于 Container 专家评审 P0 |
| 33 | DB 迁移脚本全面修订 (表名/字段/新表) | DDL | 基于所有裁决 |
| 34 | Container 角色关联改用 NativeHostSyncedExtensionPoint | 关联策略 | 基于 Container 专家评审 P0 |
| 35 | PhysicalServerRoleProvider SPI 新增 getClusterUuid / getSourceUuid / getActualUsage | SPI | 基于总架构师裁决 1.1 |

---

## 0. 文件清单

### header 层新增文件 (~45 个)

```
header/src/main/java/org/zstack/header/server/
├── PhysicalServerAO.java              # MappedSuperclass (参照 HostAO.java) [修订: 新增 powerStatus]
├── PhysicalServerVO.java              # Entity, 无 EO 层 [修订: 表名修正, LAZY fetch]
├── PhysicalServerInventory.java       # API DTO (参照 HostInventory.java) [修订: 新增 powerStatus, hardwareDetails]
├── PhysicalServerState.java           # 管理状态枚举 (参照 HostState.java) [修订: 新增 PreMaintenance]
├── PhysicalServerStatus.java          # 运行状态枚举 (参照 HostStatus.java)
├── PhysicalServerPowerStatus.java     # [新增] 电源状态枚举 (基于裁决 1.5)
├── PhysicalServerStateEvent.java      # 状态转换事件 [修订: 新增 preMaintain, maintain]
├── PhysicalServerStatusEvent.java     # 状态转换事件
├── PhysicalServerConstant.java        # 常量 (参照 HostConstant.java)
├── PhysicalServerGlobalConfig.java    # [新增] 全局配置 (基于裁决 1.6/1.7)
├── ServerPoolVO.java                  # 物理池 (简单 ResourceVO)
├── ServerPoolInventory.java           # 物理池 DTO
├── ServerPoolState.java               # 物理池状态
├── PhysicalServerRoleVO.java          # 角色映射表 [修订: 新增 clusterUuid, sourceUuid, roleStatus]
├── PhysicalServerRoleInventory.java   # 角色映射 DTO [修订: 对应字段]
├── ServerRoleType.java                # 角色类型枚举 [修订: 引入 SchedulingMode]
├── SchedulingMode.java                # [新增] 调度模式枚举 (基于裁决 1.3)
├── ServerHardwareInfoVO.java          # 硬件信息 (1:1) [修订: 新增 bootMode, discoverySource]
├── ServerHardwareInfoInventory.java   # 硬件信息 DTO [修订: 对应字段]
├── ServerHardwareDetailVO.java        # [新增] 1:N 硬件详情子表 (基于裁决 1.4)
├── ServerHardwareDetailInventory.java # [新增] 硬件详情 DTO
├── HardwareDetailType.java            # [新增] 硬件详情类型枚举 (基于裁决 1.4)
│
│── # ====== 统一分配子系统 ======
├── ServerCapacityVO.java              # 唯一容量账本 [修订: 预计算字段, 移除 ratio]
├── ServerCapacityInventory.java       # 容量 DTO [修订: 对应字段]
├── CapacityState.java                 # 容量状态 (Initialized, Normal, Overloaded)
├── AllocateServerMsg.java             # 统一分配请求 [修订: 核心字段 + extraData]
├── AllocateServerReply.java           # 统一分配响应 [修订: 新增 clusterUuid, candidates]
├── ServerAllocatorSpec.java           # 分配规格 [修订: 新增 extraData, flowContext]
├── ServerAllocatorFlow.java           # 分配器责任链接口
├── ServerSortorFlow.java              # 排序器接口
├── ServerCapacityUpdaterRunnable.java # [新增] 容量更新回调接口 (基于裁决 1.6)
├── ServerCapacityOverProvisioningManager.java  # [新增] 超分比管理接口 (基于裁决 1.6)
├── ServerAllocatorFilterExtensionPoint.java    # [新增] 分配过滤扩展点
├── ServerReservedCapacityExtensionPoint.java   # [新增] 预留容量扩展点
├── ServerAllocatorCompatibilityBridge.java     # [新增] 兼容层接口 (基于裁决 1.7)
├── RecalculateServerCapacityMsg.java           # [新增] 重算触发消息
│
│── # ====== API 消息 ======
├── APIRegisterPhysicalServerMsg.java  # 注册物理服务器 [修订: OOB optional]
├── APIRegisterPhysicalServerEvent.java
├── APIQueryPhysicalServerMsg.java     # 查询物理服务器
├── APIQueryPhysicalServerReply.java
├── APIUpdatePhysicalServerMsg.java    # 更新物理服务器
├── APIUpdatePhysicalServerEvent.java
├── APIDeletePhysicalServerMsg.java    # 删除物理服务器
├── APIDeletePhysicalServerEvent.java
├── APICreateServerPoolMsg.java        # 创建物理池
├── APICreateServerPoolEvent.java
├── APIQueryServerPoolMsg.java         # 查询物理池
├── APIQueryServerPoolReply.java
├── APIDeleteServerPoolMsg.java        # 删除物理池
├── APIDeleteServerPoolEvent.java
│
├── PhysicalServerMessage.java         # Message 路由接口
└── PhysicalServerRoleProvider.java    # SPI: 角色数据提供者 [修订: 新增方法]
```

### 配置文件新增/修改 (~3 个)

```
conf/springConfigXml/PhysicalServer.xml   # Spring bean 配置
conf/db/upgrade/V5.5.7__schema.sql        # DB 迁移 (全面修订)
conf/globalConfig/physicalServer.xml       # 全局配置 (超分比, 特性开关)
```

---

## 1. 核心 VO 定义

### 1.1 PhysicalServerAO.java

> 参照: `header/src/main/java/org/zstack/header/host/HostAO.java`
> v2.0 变更: 新增 `powerStatus` 字段 (基于总架构师裁决 1.5)；OOB 字段全部 nullable (基于 Container 专家评审)；不增加 clusterUuid (基于总架构师裁决 1.1)

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
    private String serialNumber;  // 全局唯一硬件标识 (来自 SMBIOS)

    @Column
    private String manufacturer;

    @Column
    private String model;

    // ---- 带外管理 (OOB) ----
    // v2.0: 所有 OOB 字段均 nullable，容器节点无 OOB (基于 Container 专家评审 P0)

    @Column
    private String oobManagementType;  // IPMI, REDFISH, NONE (v2.0: 新增 NONE)

    @Column
    @Index
    private String oobAddress;  // v2.0: 增加 @Index (基于 BM1 专家：用于关联匹配)

    @Column
    private Integer oobPort;

    @Column
    private String oobUsername;

    @Column
    @Convert(converter = org.zstack.core.encrypt.PasswordConverter.class)
    private String oobPassword;

    // ---- 三维状态 (基于总架构师裁决 1.5) ----

    @Column
    @Enumerated(EnumType.STRING)
    private PhysicalServerState state;  // 管理状态: Enabled/Disabled/PreMaintenance/Maintenance

    @Column
    @Enumerated(EnumType.STRING)
    private PhysicalServerStatus status;  // 连接状态: Unknown/Connecting/Connected/Disconnected

    @Column
    @Enumerated(EnumType.STRING)
    private PhysicalServerPowerStatus powerStatus;  // v2.0 新增: 电源状态: PowerOn/PowerOff/PowerUnknown

    // ---- 时间戳 ----

    @Column
    private Timestamp createDate;

    @Column
    private Timestamp lastOpDate;

    @PreUpdate
    private void preUpdate() {
        lastOpDate = null;
    }

    // ----- Getters & Setters -----

    public String getZoneUuid() {
        return zoneUuid;
    }

    public void setZoneUuid(String zoneUuid) {
        this.zoneUuid = zoneUuid;
    }

    public String getServerPoolUuid() {
        return serverPoolUuid;
    }

    public void setServerPoolUuid(String serverPoolUuid) {
        this.serverPoolUuid = serverPoolUuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getManagementIp() {
        return managementIp;
    }

    public void setManagementIp(String managementIp) {
        this.managementIp = managementIp;
    }

    public String getArchitecture() {
        return architecture;
    }

    public void setArchitecture(String architecture) {
        this.architecture = architecture;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getOobManagementType() {
        return oobManagementType;
    }

    public void setOobManagementType(String oobManagementType) {
        this.oobManagementType = oobManagementType;
    }

    public String getOobAddress() {
        return oobAddress;
    }

    public void setOobAddress(String oobAddress) {
        this.oobAddress = oobAddress;
    }

    public Integer getOobPort() {
        return oobPort;
    }

    public void setOobPort(Integer oobPort) {
        this.oobPort = oobPort;
    }

    public String getOobUsername() {
        return oobUsername;
    }

    public void setOobUsername(String oobUsername) {
        this.oobUsername = oobUsername;
    }

    public String getOobPassword() {
        return oobPassword;
    }

    public void setOobPassword(String oobPassword) {
        this.oobPassword = oobPassword;
    }

    public PhysicalServerState getState() {
        return state;
    }

    public void setState(PhysicalServerState state) {
        this.state = state;
    }

    public PhysicalServerStatus getStatus() {
        return status;
    }

    public void setStatus(PhysicalServerStatus status) {
        this.status = status;
    }

    public PhysicalServerPowerStatus getPowerStatus() {
        return powerStatus;
    }

    public void setPowerStatus(PhysicalServerPowerStatus powerStatus) {
        this.powerStatus = powerStatus;
    }

    public Timestamp getCreateDate() {
        return createDate;
    }

    public void setCreateDate(Timestamp createDate) {
        this.createDate = createDate;
    }

    public Timestamp getLastOpDate() {
        return lastOpDate;
    }

    public void setLastOpDate(Timestamp lastOpDate) {
        this.lastOpDate = lastOpDate;
    }
}
```

### 1.2 PhysicalServerVO.java

> 无 EO 层，直接 @Entity。物理删除即可（物理服务器退役后直接从库中移除）。
> v2.0 变更: 表名明确为 `PhysicalServerVO` (基于 KVM + Allocator 专家评审)；roles 改为 LAZY fetch；新增 `@OneToMany` 关联 ServerHardwareDetailVO (基于总架构师裁决 1.4)

```java
package org.zstack.header.server;

import org.zstack.header.tag.AutoDeleteTag;
import org.zstack.header.vo.BaseResource;
import org.zstack.header.vo.EntityGraph;
import org.zstack.header.vo.NoView;
import org.zstack.header.zone.ZoneVO;

import javax.persistence.*;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "PhysicalServerVO")  // v2.0: 明确表名，修正 v1.1 的 PhysicalServerAO 错误 (基于 Allocator 专家评审 P0)
@AutoDeleteTag
@BaseResource
@EntityGraph(
    parents = {
        @EntityGraph.Neighbour(type = ZoneVO.class, myField = "zoneUuid", targetField = "uuid"),
        @EntityGraph.Neighbour(type = ServerPoolVO.class, myField = "serverPoolUuid", targetField = "uuid"),
    }
)
public class PhysicalServerVO extends PhysicalServerAO {

    @OneToMany(fetch = FetchType.LAZY)  // v2.0: EAGER -> LAZY (基于 KVM + Allocator 专家评审，避免 N+1)
    @JoinColumn(name = "serverUuid", insertable = false, updatable = false)
    @NoView
    private Set<PhysicalServerRoleVO> roles;

    @OneToMany(fetch = FetchType.LAZY)  // v2.0 新增: 1:N 硬件详情 (基于总架构师裁决 1.4)
    @JoinColumn(name = "serverUuid", insertable = false, updatable = false)
    @NoView
    private List<ServerHardwareDetailVO> hardwareDetails;

    public Set<PhysicalServerRoleVO> getRoles() {
        return roles;
    }

    public void setRoles(Set<PhysicalServerRoleVO> roles) {
        this.roles = roles;
    }

    public List<ServerHardwareDetailVO> getHardwareDetails() {
        return hardwareDetails;
    }

    public void setHardwareDetails(List<ServerHardwareDetailVO> hardwareDetails) {
        this.hardwareDetails = hardwareDetails;
    }
}
```

### 1.3 ServerPoolVO.java

> 简单实体，不需要 AO/EO 层（不支持子类化，暂不需要软删除）
> 参照: `header/src/main/java/org/zstack/header/identity/UserGroupVO.java` 的简单模式
> v2.0: 无变更

```java
package org.zstack.header.server;

import org.zstack.header.vo.BaseResource;
import org.zstack.header.vo.ForeignKey;
import org.zstack.header.vo.ForeignKey.ReferenceOption;
import org.zstack.header.vo.ResourceVO;
import org.zstack.header.zone.ZoneEO;

import javax.persistence.*;
import java.sql.Timestamp;

@Entity
@Table
@BaseResource
public class ServerPoolVO extends ResourceVO {

    @Column
    private String name;

    @Column
    private String description;

    @Column
    @ForeignKey(parentEntityClass = ZoneEO.class, onDeleteAction = ReferenceOption.RESTRICT)
    private String zoneUuid;

    @Column
    @Enumerated(EnumType.STRING)
    private ServerPoolState state;

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

### 1.4 PhysicalServerRoleVO.java

> 角色映射表，非 ResourceVO (纯引用关系)
> 唯一约束: (serverUuid, roleType) 和 (roleUuid)
> v2.0 变更: 新增 `clusterUuid` (基于总架构师裁决 1.1)，`sourceUuid` (基于 Container 专家评审)，`roleStatus` (基于总架构师裁决 1.5)

```java
package org.zstack.header.server;

import org.zstack.header.vo.ForeignKey;
import org.zstack.header.vo.ForeignKey.ReferenceOption;

import javax.persistence.*;
import java.sql.Timestamp;

@Entity
@Table(
    name = "PhysicalServerRoleVO",
    uniqueConstraints = {
        @UniqueConstraint(name = "ukPhysicalServerRoleVOServerRole", columnNames = {"serverUuid", "roleType"}),
        @UniqueConstraint(name = "ukPhysicalServerRoleVORoleUuid", columnNames = {"roleUuid"})
    }
)
public class PhysicalServerRoleVO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column
    private long id;

    @Column
    @ForeignKey(parentEntityClass = PhysicalServerVO.class, onDeleteAction = ReferenceOption.CASCADE)
    private String serverUuid;

    @Column
    @Enumerated(EnumType.STRING)
    private ServerRoleType roleType;

    @Column
    private String roleUuid;  // 指向 HostVO.uuid / BaremetalChassisVO.uuid 等

    // ---- v2.0 新增字段 (基于总架构师裁决 1.1) ----

    @Column
    private String clusterUuid;  // 角色所属 cluster (per-role cluster, 不在 PhysicalServerAO 上)

    @Column
    private String sourceUuid;   // 管理来源 (Container: endpointUuid, BM1: pxeServerUuid)

    @Column
    private String roleStatus;   // 角色层自定义状态字符串，PhysicalServer 层不解析
                                 // BM1: HWInfoUnknown/PxeBooting/Available/Allocated
                                 // KVM: Connected/Disconnected
                                 // Container: Ready/NotReady

    // ---- 原有字段 ----

    @Column
    @Enumerated(EnumType.STRING)
    private RoleSyncStatus syncStatus;

    @Column
    private Timestamp createDate;

    @Column
    private Timestamp lastSyncTime;

    // ----- 内部枚举 -----

    public enum RoleSyncStatus {
        InSync,
        OutOfSync
    }

    // ----- Getters & Setters -----

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getServerUuid() {
        return serverUuid;
    }

    public void setServerUuid(String serverUuid) {
        this.serverUuid = serverUuid;
    }

    public ServerRoleType getRoleType() {
        return roleType;
    }

    public void setRoleType(ServerRoleType roleType) {
        this.roleType = roleType;
    }

    public String getRoleUuid() {
        return roleUuid;
    }

    public void setRoleUuid(String roleUuid) {
        this.roleUuid = roleUuid;
    }

    public String getClusterUuid() {
        return clusterUuid;
    }

    public void setClusterUuid(String clusterUuid) {
        this.clusterUuid = clusterUuid;
    }

    public String getSourceUuid() {
        return sourceUuid;
    }

    public void setSourceUuid(String sourceUuid) {
        this.sourceUuid = sourceUuid;
    }

    public String getRoleStatus() {
        return roleStatus;
    }

    public void setRoleStatus(String roleStatus) {
        this.roleStatus = roleStatus;
    }

    public RoleSyncStatus getSyncStatus() {
        return syncStatus;
    }

    public void setSyncStatus(RoleSyncStatus syncStatus) {
        this.syncStatus = syncStatus;
    }

    public Timestamp getCreateDate() {
        return createDate;
    }

    public void setCreateDate(Timestamp createDate) {
        this.createDate = createDate;
    }

    public Timestamp getLastSyncTime() {
        return lastSyncTime;
    }

    public void setLastSyncTime(Timestamp lastSyncTime) {
        this.lastSyncTime = lastSyncTime;
    }
}
```

### 1.5 ServerHardwareInfoVO.java

> 硬件发现信息，1:1 关联 PhysicalServerVO
> 通过 OOB (IPMI/Redfish) 或 Agent 采集
> v2.0 变更: 新增 `bootMode` (基于 BM1 专家评审)，新增 `discoverySource` (基于总架构师裁决 1.4)

```java
package org.zstack.header.server;

import javax.persistence.*;
import java.sql.Timestamp;

@Entity
@Table(name = "ServerHardwareInfoVO")
public class ServerHardwareInfoVO {

    @Id
    @Column
    private String uuid;  // 与 PhysicalServerVO.uuid 共享

    @Column
    private Integer cpuSockets;

    @Column
    private Integer cpuCoresPerSocket;

    @Column
    private Integer cpuThreadsPerCore;

    @Column
    private String cpuModel;

    @Column
    private Long totalMemoryBytes;

    @Column
    private Integer memorySlots;

    @Column
    private Integer diskCount;

    @Column
    private Long totalDiskBytes;

    @Column
    private Integer nicCount;

    @Column
    private String biosVersion;

    @Column
    private String bmcVersion;

    @Column
    private String bootMode;  // v2.0 新增: LEGACY, UEFI, UNKNOWN (基于 BM1 专家评审)

    @Column
    private String discoverySource;  // v2.0 新增: OOB, AGENT, SYNC (标记硬件信息来源, 基于总架构师裁决 1.4)

    @Column
    private Timestamp discoveredDate;

    @Column
    private Timestamp lastUpdatedDate;

    // ----- Getters & Setters -----

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public Integer getCpuSockets() {
        return cpuSockets;
    }

    public void setCpuSockets(Integer cpuSockets) {
        this.cpuSockets = cpuSockets;
    }

    public Integer getCpuCoresPerSocket() {
        return cpuCoresPerSocket;
    }

    public void setCpuCoresPerSocket(Integer cpuCoresPerSocket) {
        this.cpuCoresPerSocket = cpuCoresPerSocket;
    }

    public Integer getCpuThreadsPerCore() {
        return cpuThreadsPerCore;
    }

    public void setCpuThreadsPerCore(Integer cpuThreadsPerCore) {
        this.cpuThreadsPerCore = cpuThreadsPerCore;
    }

    public String getCpuModel() {
        return cpuModel;
    }

    public void setCpuModel(String cpuModel) {
        this.cpuModel = cpuModel;
    }

    public Long getTotalMemoryBytes() {
        return totalMemoryBytes;
    }

    public void setTotalMemoryBytes(Long totalMemoryBytes) {
        this.totalMemoryBytes = totalMemoryBytes;
    }

    public Integer getMemorySlots() {
        return memorySlots;
    }

    public void setMemorySlots(Integer memorySlots) {
        this.memorySlots = memorySlots;
    }

    public Integer getDiskCount() {
        return diskCount;
    }

    public void setDiskCount(Integer diskCount) {
        this.diskCount = diskCount;
    }

    public Long getTotalDiskBytes() {
        return totalDiskBytes;
    }

    public void setTotalDiskBytes(Long totalDiskBytes) {
        this.totalDiskBytes = totalDiskBytes;
    }

    public Integer getNicCount() {
        return nicCount;
    }

    public void setNicCount(Integer nicCount) {
        this.nicCount = nicCount;
    }

    public String getBiosVersion() {
        return biosVersion;
    }

    public void setBiosVersion(String biosVersion) {
        this.biosVersion = biosVersion;
    }

    public String getBmcVersion() {
        return bmcVersion;
    }

    public void setBmcVersion(String bmcVersion) {
        this.bmcVersion = bmcVersion;
    }

    public String getBootMode() {
        return bootMode;
    }

    public void setBootMode(String bootMode) {
        this.bootMode = bootMode;
    }

    public String getDiscoverySource() {
        return discoverySource;
    }

    public void setDiscoverySource(String discoverySource) {
        this.discoverySource = discoverySource;
    }

    public Timestamp getDiscoveredDate() {
        return discoveredDate;
    }

    public void setDiscoveredDate(Timestamp discoveredDate) {
        this.discoveredDate = discoveredDate;
    }

    public Timestamp getLastUpdatedDate() {
        return lastUpdatedDate;
    }

    public void setLastUpdatedDate(Timestamp lastUpdatedDate) {
        this.lastUpdatedDate = lastUpdatedDate;
    }
}
```

### 1.6 ServerHardwareDetailVO.java (v2.0 新增)

> 1:N 硬件详情子表。BM1/BM2 的细粒度硬件信息存储。
> 基于总架构师裁决 1.4: "1:1 汇总表 + 1:N 详情子表" 的两级结构设计。
> 与 BaremetalHardwareInfoVO 的 type+content 模式一致，迁移路径清晰。

```java
package org.zstack.header.server;

import org.zstack.header.vo.ForeignKey;
import org.zstack.header.vo.ForeignKey.ReferenceOption;

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
    @ForeignKey(parentEntityClass = PhysicalServerVO.class, onDeleteAction = ReferenceOption.CASCADE)
    private String serverUuid;

    @Column
    @Enumerated(EnumType.STRING)
    private HardwareDetailType type;  // NIC, DISK, GPU, PCI, MEMORY_DIMM, BASIC

    @Column(columnDefinition = "TEXT")
    private String content;  // JSON 格式的详细信息

    @Column
    private Timestamp createDate;

    @Column
    private Timestamp lastOpDate;

    @PreUpdate
    private void preUpdate() {
        lastOpDate = null;
    }

    // ----- Getters & Setters -----

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getServerUuid() {
        return serverUuid;
    }

    public void setServerUuid(String serverUuid) {
        this.serverUuid = serverUuid;
    }

    public HardwareDetailType getType() {
        return type;
    }

    public void setType(HardwareDetailType type) {
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Timestamp getCreateDate() {
        return createDate;
    }

    public void setCreateDate(Timestamp createDate) {
        this.createDate = createDate;
    }

    public Timestamp getLastOpDate() {
        return lastOpDate;
    }

    public void setLastOpDate(Timestamp lastOpDate) {
        this.lastOpDate = lastOpDate;
    }
}
```

### 1.7 ServerCapacityVO.java

> **核心设计**: 统一容量账本。所有角色的资源分配都通过此表扣减。
> 参照: `header/src/main/java/org/zstack/header/host/HostCapacityVO.java` 的 1:1 模式
> 关键差异: 支持独占角色 (Baremetal) 和共享角色 (KVM) 的双模式分配
> v2.0 变更 (基于总架构师裁决 1.6):
>   - 移除 cpuOverprovisioningRatio / memoryOverprovisioningRatio 字段
>   - 移除 getTotalCpu() / getTotalMemory() 的计算 getter
>   - 新增 totalCpu / totalMemory 为预计算持久化字段 (由 ServerCapacityOverProvisioningManager 写入)
>   - 新增 availablePhysicalMemory / cpuNum / cpuSockets / cpuCoreNum (对齐 HostCapacityVO)
>   - 新增 exclusiveRoleUuid / schedulingMode

```java
package org.zstack.header.server;

import org.zstack.header.vo.Index;

import javax.persistence.*;

@Entity
@Table(name = "ServerCapacityVO")
public class ServerCapacityVO {

    @Id
    @Column
    private String uuid;  // 与 PhysicalServerVO.uuid 共享

    // ---- 物理真实值 (由硬件发现/agent上报填充) ----

    @Column
    private long totalPhysicalCpu;     // 物理 CPU 线程总数

    @Column
    private long totalPhysicalMemory;  // 物理内存总量 (bytes)

    // ---- 逻辑值 (预计算持久化: physical * ratio) ----
    // v2.0: 由 ServerCapacityOverProvisioningManager 预计算写入
    // 不再提供 getTotalCpu()/getTotalMemory() 的计算 getter (基于总架构师裁决 1.6)

    @Column
    @Index
    private long totalCpu;             // 逻辑 CPU 总数 (已乘超分比)

    @Column
    @Index
    private long totalMemory;          // 逻辑内存总量 (已乘超分比, bytes)

    // ---- 可分配量 (分配器悲观锁扣减/释放) ----

    @Column
    @Index
    private long availableCpu;

    @Column
    @Index
    private long availableMemory;

    // ---- 监控用 (agent 实时上报) ----

    @Column
    private long availablePhysicalMemory;  // v2.0 新增: 实际可用物理内存 (基于 KVM 专家评审)

    // ---- CPU 细粒度信息 (兼容 HostCapacityVO) ----

    @Column
    private int cpuNum;       // v2.0 新增: 逻辑 CPU 数 (= totalPhysicalCpu)

    @Column
    private int cpuSockets;   // v2.0 新增: 物理插槽数

    @Column
    private int cpuCoreNum;   // v2.0 新增: 物理核心总数

    // ---- 预留 ----

    @Column
    private long reservedMemory;  // 系统预留 (由 ServerReservedCapacityExtensionPoint 动态计算)

    // ---- 磁盘 ----

    @Column
    private long totalDisk;

    @Column
    private long availableDisk;

    // ---- 状态 ----

    @Column
    @Enumerated(EnumType.STRING)
    private CapacityState capacityState;  // Initialized, Normal, Overloaded

    // ---- 独占控制 (基于 Allocator 专家建议) ----

    @Column
    private String exclusiveRoleUuid;  // v2.0 新增: 当前独占角色 UUID, null = 未被独占

    // ---- 调度模式缓存 ----

    @Column
    @Enumerated(EnumType.STRING)
    private SchedulingMode schedulingMode;  // v2.0 新增: 缓存避免每次 JOIN RoleVO

    // ----- Getters & Setters -----

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public long getTotalPhysicalCpu() {
        return totalPhysicalCpu;
    }

    public void setTotalPhysicalCpu(long totalPhysicalCpu) {
        this.totalPhysicalCpu = totalPhysicalCpu;
    }

    public long getTotalPhysicalMemory() {
        return totalPhysicalMemory;
    }

    public void setTotalPhysicalMemory(long totalPhysicalMemory) {
        this.totalPhysicalMemory = totalPhysicalMemory;
    }

    public long getTotalCpu() {
        return totalCpu;
    }

    public void setTotalCpu(long totalCpu) {
        this.totalCpu = totalCpu;
    }

    public long getTotalMemory() {
        return totalMemory;
    }

    public void setTotalMemory(long totalMemory) {
        this.totalMemory = totalMemory;
    }

    public long getAvailableCpu() {
        return availableCpu;
    }

    public void setAvailableCpu(long availableCpu) {
        this.availableCpu = availableCpu;
    }

    public long getAvailableMemory() {
        return availableMemory;
    }

    public void setAvailableMemory(long availableMemory) {
        this.availableMemory = availableMemory;
    }

    public long getAvailablePhysicalMemory() {
        return availablePhysicalMemory;
    }

    public void setAvailablePhysicalMemory(long availablePhysicalMemory) {
        this.availablePhysicalMemory = availablePhysicalMemory;
    }

    public int getCpuNum() {
        return cpuNum;
    }

    public void setCpuNum(int cpuNum) {
        this.cpuNum = cpuNum;
    }

    public int getCpuSockets() {
        return cpuSockets;
    }

    public void setCpuSockets(int cpuSockets) {
        this.cpuSockets = cpuSockets;
    }

    public int getCpuCoreNum() {
        return cpuCoreNum;
    }

    public void setCpuCoreNum(int cpuCoreNum) {
        this.cpuCoreNum = cpuCoreNum;
    }

    public long getReservedMemory() {
        return reservedMemory;
    }

    public void setReservedMemory(long reservedMemory) {
        this.reservedMemory = reservedMemory;
    }

    public long getTotalDisk() {
        return totalDisk;
    }

    public void setTotalDisk(long totalDisk) {
        this.totalDisk = totalDisk;
    }

    public long getAvailableDisk() {
        return availableDisk;
    }

    public void setAvailableDisk(long availableDisk) {
        this.availableDisk = availableDisk;
    }

    public CapacityState getCapacityState() {
        return capacityState;
    }

    public void setCapacityState(CapacityState capacityState) {
        this.capacityState = capacityState;
    }

    public String getExclusiveRoleUuid() {
        return exclusiveRoleUuid;
    }

    public void setExclusiveRoleUuid(String exclusiveRoleUuid) {
        this.exclusiveRoleUuid = exclusiveRoleUuid;
    }

    public SchedulingMode getSchedulingMode() {
        return schedulingMode;
    }

    public void setSchedulingMode(SchedulingMode schedulingMode) {
        this.schedulingMode = schedulingMode;
    }
}
```

### 1.8 CapacityState.java

> v2.0: 无变更

```java
package org.zstack.header.server;

public enum CapacityState {
    Initialized,  // 刚创建，尚未填充物理值
    Normal,       // 正常可用
    Overloaded    // 超载 (availableCpu < 0 或 availableMemory < 0, 或独占分配后)
}
```

---

## 1B. 统一分配子系统 (Server Allocation)

> **核心思想**: 资源分配逻辑从角色层(HostAllocator)下沉到 Server 层。
> 所有消费者（VM, Pod, BM Instance）统一请求 `AllocateServerMsg`，
> 由 `ServerAllocatorChain` 过滤 -> `ServerSortorChain` 排序 -> `ServerCapacityUpdater` 悲观锁扣减。
> 参照: `header/src/main/java/org/zstack/header/allocator/` 下的 HostAllocator 体系
> v2.0 变更: AllocateServerMsg 采用核心字段 + extraData 两层设计 (基于总架构师裁决 1.2)

### 1B.1 AllocateServerMsg.java

> 参照: `header/src/main/java/org/zstack/header/allocator/AllocateHostMsg.java`
> v2.0 变更 (基于总架构师裁决 1.2):
>   - 移除 clusterUuid，改用 requiredClusterUuids (List) (基于总架构师裁决 1.1)
>   - requiredCpu/requiredMemory 改为 nullable Long (BM 整机分配时可为 null)
>   - 新增 avoidServerUuids / softAvoidServerUuids / requiredDisk / architecture
>   - 新增 extraData Map 承载角色特定过滤条件
>   - 新增 listAll 控制字段

```java
package org.zstack.header.server;

import org.zstack.header.message.NeedReplyMessage;
import java.util.*;

/**
 * 统一物理服务器分配请求。
 *
 * 设计原则 (v2.0, 基于总架构师裁决 1.2):
 * - 核心层: 物理资源维度的通用字段 (所有角色共用)
 * - 扩展层: extraData Map 承载角色特定数据
 *
 * 使用场景:
 * - KVM: 创建 VM 时分配一台物理 Host
 * - BM1/BM2: 创建 BaremetalInstance 时独占一台物理机
 * - Container: 不使用此 Msg (EXTERNAL_READONLY 模式, 基于总架构师裁决 1.3)
 */
public class AllocateServerMsg extends NeedReplyMessage {

    // ---- 作用域过滤 ----
    private String zoneUuid;
    private List<String> requiredClusterUuids;  // v2.0: 支持多 cluster 候选 (替代 v1.1 的单个 clusterUuid, 基于总架构师裁决 1.1)
    private String serverPoolUuid;

    // ---- 角色过滤 ----
    private String requiredRoleType;     // KVM_HOST / BARE_METAL / BARE_METAL2
    private String serverUuid;           // 指定具体 Server (迁移/绑定场景)

    // ---- 容量需求 ----
    private Long requiredCpu;            // v2.0: Long (nullable), BM 整机分配时为 null (required = false)
    private Long requiredMemory;         // v2.0: Long (nullable), required = false
    private Long requiredDisk;           // v2.0 新增: 磁盘需求 (基于总架构师裁决 1.2)

    // ---- 架构过滤 ----
    private String architecture;         // v2.0 新增: x86_64, aarch64 等 (基于总架构师裁决 1.2)

    // ---- 回避/亲和 ----
    private List<String> avoidServerUuids;      // v2.0 新增: 硬排除 (基于 Allocator 专家评审)
    private List<String> softAvoidServerUuids;   // v2.0 新增: 软排除 (基于 Allocator 专家评审)

    // ---- 策略 ----
    private String allocatorStrategy;    // LEAST_USED / RANDOM / DESIGNATED / ...

    // ---- 控制 ----
    private boolean dryRun;              // true = 只检查不扣减
    private boolean listAll;             // v2.0 新增: true = 返回所有候选 (用于 UI 展示)

    // ---- 扩展上下文 (角色特定, 基于总架构师裁决 1.2) ----
    /**
     * 角色模块通过此 Map 传递特有的过滤条件。
     * 例如:
     * - KVM: "l3NetworkUuids" -> List<String>
     * - KVM: "requiredPrimaryStorageUuids" -> Set<String>
     * - KVM: "vmInstance" -> VmInstanceInventory
     * - BM2: "chassisOfferingUuid" -> String
     * - BM2: "requiredChassisDiskUuid" -> String
     * - BM1: "pxeServerUuid" -> String
     *
     * ServerAllocatorFilterExtensionPoint 的实现方可以读取这些数据。
     */
    private Map<String, Object> extraData;

    // ----- Getters & Setters -----

    public String getZoneUuid() {
        return zoneUuid;
    }

    public void setZoneUuid(String zoneUuid) {
        this.zoneUuid = zoneUuid;
    }

    public List<String> getRequiredClusterUuids() {
        return requiredClusterUuids;
    }

    public void setRequiredClusterUuids(List<String> requiredClusterUuids) {
        this.requiredClusterUuids = requiredClusterUuids;
    }

    public String getServerPoolUuid() {
        return serverPoolUuid;
    }

    public void setServerPoolUuid(String serverPoolUuid) {
        this.serverPoolUuid = serverPoolUuid;
    }

    public String getRequiredRoleType() {
        return requiredRoleType;
    }

    public void setRequiredRoleType(String requiredRoleType) {
        this.requiredRoleType = requiredRoleType;
    }

    public String getServerUuid() {
        return serverUuid;
    }

    public void setServerUuid(String serverUuid) {
        this.serverUuid = serverUuid;
    }

    public Long getRequiredCpu() {
        return requiredCpu;
    }

    public void setRequiredCpu(Long requiredCpu) {
        this.requiredCpu = requiredCpu;
    }

    public Long getRequiredMemory() {
        return requiredMemory;
    }

    public void setRequiredMemory(Long requiredMemory) {
        this.requiredMemory = requiredMemory;
    }

    public Long getRequiredDisk() {
        return requiredDisk;
    }

    public void setRequiredDisk(Long requiredDisk) {
        this.requiredDisk = requiredDisk;
    }

    public String getArchitecture() {
        return architecture;
    }

    public void setArchitecture(String architecture) {
        this.architecture = architecture;
    }

    public List<String> getAvoidServerUuids() {
        return avoidServerUuids;
    }

    public void setAvoidServerUuids(List<String> avoidServerUuids) {
        this.avoidServerUuids = avoidServerUuids;
    }

    public List<String> getSoftAvoidServerUuids() {
        return softAvoidServerUuids;
    }

    public void setSoftAvoidServerUuids(List<String> softAvoidServerUuids) {
        this.softAvoidServerUuids = softAvoidServerUuids;
    }

    public String getAllocatorStrategy() {
        return allocatorStrategy;
    }

    public void setAllocatorStrategy(String allocatorStrategy) {
        this.allocatorStrategy = allocatorStrategy;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public boolean isListAll() {
        return listAll;
    }

    public void setListAll(boolean listAll) {
        this.listAll = listAll;
    }

    public Map<String, Object> getExtraData() {
        return extraData;
    }

    public void setExtraData(Map<String, Object> extraData) {
        this.extraData = extraData;
    }
}
```

### 1B.2 AllocateServerReply.java

> v2.0 变更: 新增 clusterUuid (从 RoleVO 获取)；新增 candidates 列表 (支持 listAll 模式)

```java
package org.zstack.header.server;

import org.zstack.header.message.MessageReply;
import java.util.List;

public class AllocateServerReply extends MessageReply {
    private String serverUuid;
    private String roleUuid;       // = hostUuid for KVM, = chassisUuid for BM
    private String roleType;
    private String clusterUuid;    // v2.0 新增: 从 PhysicalServerRoleVO 获取 (基于总架构师裁决 1.1)

    // dryRun=false 且 listAll=false 时: 单个结果 (serverUuid/roleUuid 有值)
    // listAll=true 时: 所有候选 (candidates 有值)
    private List<PhysicalServerInventory> candidates;  // v2.0 新增: 支持返回候选列表

    // ----- Getters & Setters -----

    public String getServerUuid() {
        return serverUuid;
    }

    public void setServerUuid(String serverUuid) {
        this.serverUuid = serverUuid;
    }

    public String getRoleUuid() {
        return roleUuid;
    }

    public void setRoleUuid(String roleUuid) {
        this.roleUuid = roleUuid;
    }

    public String getRoleType() {
        return roleType;
    }

    public void setRoleType(String roleType) {
        this.roleType = roleType;
    }

    public String getClusterUuid() {
        return clusterUuid;
    }

    public void setClusterUuid(String clusterUuid) {
        this.clusterUuid = clusterUuid;
    }

    public List<PhysicalServerInventory> getCandidates() {
        return candidates;
    }

    public void setCandidates(List<PhysicalServerInventory> candidates) {
        this.candidates = candidates;
    }
}
```

### 1B.3 ServerAllocatorSpec.java

> 分配器内部传递对象，包含过滤/排序所需的全部上下文
> v2.0 变更: 增加 extraData Map (基于总架构师裁决 1.2)；增加 flowContext 供 Flow 间共享数据

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
    private List<String> requiredClusterUuids;  // v2.0: List (替代 v1.1 的单个 clusterUuid)
    private String serverPoolUuid;
    private String requiredRoleType;
    private String serverUuid;
    private Long requiredCpu;       // v2.0: nullable Long
    private Long requiredMemory;    // v2.0: nullable Long
    private Long requiredDisk;      // v2.0 新增
    private String architecture;    // v2.0 新增
    private List<String> avoidServerUuids;      // v2.0 新增
    private List<String> softAvoidServerUuids;  // v2.0 新增
    private String allocatorStrategy;
    private boolean dryRun;
    private boolean listAll;        // v2.0 新增
    private Map<String, Object> extraData;  // v2.0 新增 (基于总架构师裁决 1.2)

    // ---- 中间结果: 候选服务器列表 (由各 Flow 逐步过滤) ----
    private List<PhysicalServerInventory> candidates;

    // ---- 运行时上下文 (Flow 间共享) ----
    private Map<String, Object> flowContext = new HashMap<>();

    // ----- Getters & Setters (省略) -----
}
```

### 1B.4 ServerAllocatorFlow.java

> 分配器责任链接口。每个 Flow 负责一种过滤维度。
> 参照: `header/src/main/java/org/zstack/header/allocator/HostAllocatorFilterExtensionPoint.java`
> v2.0: 无变更

```java
package org.zstack.header.server;

import org.zstack.header.core.ReturnValueCompletion;
import java.util.List;

/**
 * 分配器责任链中的一个过滤节点。
 * 实现类:
 *   1. ServerStateAllocatorFlow:    过滤 state=Disabled/PreMaintenance/Maintenance 或 status=Disconnected 或 powerStatus=PowerOff
 *   2. ServerCapacityAllocatorFlow: 检查 availableCpu/availableMemory 是否充足
 *   3. ServerRoleAllocatorFlow:     检查服务器是否具备请求的 roleType
 *   4. ServerClusterAllocatorFlow:  如指定 requiredClusterUuids，检查角色是否绑定该集群 (通过 RoleVO JOIN)
 *   5. ServerPoolAllocatorFlow:     如指定 serverPoolUuid，检查是否在该池内
 *   6. ServerArchitectureAllocatorFlow: 如指定 architecture，过滤不匹配的服务器
 *   7. ServerAvoidAllocatorFlow:    排除 avoidServerUuids 中的服务器
 */
public interface ServerAllocatorFlow {
    void allocate(ServerAllocatorSpec spec,
                  List<PhysicalServerInventory> candidates,
                  ReturnValueCompletion<List<PhysicalServerInventory>> completion);
}
```

### 1B.5 ServerSortorFlow.java

> 排序器接口。对过滤后的候选列表排序。
> v2.0: 无变更

```java
package org.zstack.header.server;

import java.util.List;

/**
 * 排序器接口。
 * 实现类:
 *   1. LeastUsedServerSortor: CPU/内存剩余最多优先
 *   2. RandomServerSortor:    随机排序
 */
public interface ServerSortorFlow {
    List<PhysicalServerInventory> sort(ServerAllocatorSpec spec,
                                       List<PhysicalServerInventory> candidates);
}
```

### 1B.6 ServerCapacityUpdater 设计 (Phase 2 完整实现)

> 悲观锁扣减/释放。核心逻辑：独占角色清零，共享角色扣减。
> 参照: `compute/src/main/java/org/zstack/compute/allocator/HostCapacityUpdater.java`
> v2.0 关键修正 (基于 Allocator 专家评审 P0):
>   - @Transactional 和 @DeadlockAutoRestart 分别在不同方法上 (修复编译错误)
>   - 使用回调模式 (ServerCapacityUpdaterRunnable) 而非硬编码扣减
>   - 对 EXTERNAL_READONLY 模式拒绝 reserve()，仅允许 syncFromExternal()

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
 * 关键修正 (v2.0, 基于 Allocator 专家评审 P0):
 * - @Transactional 和 @DeadlockAutoRestart 分别在不同方法上 (内外两层)
 * - 使用回调模式 (ServerCapacityUpdaterRunnable) 而非硬编码扣减逻辑
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

### 1B.7 ServerCapacityUpdater 典型使用方式 (v2.0)

> 基于总架构师裁决 1.3 和 1.6，展示三种调度模式下的容量更新方式

```java
// 共享角色扣减 (KVM 创建 VM, SchedulingMode.INTERNAL_SHARED)
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

// 独占角色分配 (BM 整机分配, SchedulingMode.INTERNAL_EXCLUSIVE)
new ServerCapacityUpdater(serverUuid).run(cap -> {
    if (cap.getExclusiveRoleUuid() != null) {
        throw new UnableToReserveCapacityException(operr("Server already exclusively allocated"));
    }
    cap.setExclusiveRoleUuid(roleUuid);
    cap.setAvailableCpu(0);
    cap.setAvailableMemory(0);
    cap.setAvailableDisk(0);
    cap.setCapacityState(CapacityState.Overloaded);  // 基于 Allocator 专家建议
    return cap;
});

// 外部调度同步 (Container 事后同步, SchedulingMode.EXTERNAL_READONLY)
// 注意: EXTERNAL_READONLY 模式下拒绝 reserve()，仅允许 syncFromExternal() (基于总架构师裁决 1.3)
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

### 1B.8 兼容层设计 (HostAllocatorCompatibilityLayer)

> Phase 1 定义接口 + POC 桩，Phase 2 完整实现 (基于总架构师裁决 1.7)

```
现有流程:                       新流程 (Phase 3 切换后):
VmAllocateHostFlow              VmAllocateHostFlow
  -> AllocateHostMsg               -> AllocateHostMsg
  -> HostAllocatorChain              -> HostAllocatorCompatibilityLayer
  -> HostCapacityUpdater               -> AllocateServerMsg
  -> HostInventory                      -> ServerAllocatorChain
                                       -> ServerCapacityUpdater
                                       -> 反向映射回 HostInventory
```

### 1B.9 ServerCapacityInventory.java

> v2.0 变更: 移除 cpuOverprovisioningRatio / memoryOverprovisioningRatio；新增预计算字段和细粒度 CPU 信息

```java
package org.zstack.header.server;

import java.io.Serializable;

public class ServerCapacityInventory implements Serializable {
    private String uuid;
    private long totalPhysicalCpu;
    private long totalPhysicalMemory;
    private long totalCpu;           // v2.0: 预计算值 (替代 v1.1 的 getter 计算)
    private long totalMemory;        // v2.0: 预计算值
    private long availableCpu;
    private long availableMemory;
    private long availablePhysicalMemory;  // v2.0 新增
    private int cpuNum;              // v2.0 新增
    private int cpuSockets;          // v2.0 新增
    private int cpuCoreNum;          // v2.0 新增
    private long reservedMemory;
    private long totalDisk;
    private long availableDisk;
    private String capacityState;
    private String exclusiveRoleUuid;   // v2.0 新增
    private String schedulingMode;      // v2.0 新增

    public static ServerCapacityInventory valueOf(ServerCapacityVO vo) {
        ServerCapacityInventory inv = new ServerCapacityInventory();
        inv.uuid = vo.getUuid();
        inv.totalPhysicalCpu = vo.getTotalPhysicalCpu();
        inv.totalPhysicalMemory = vo.getTotalPhysicalMemory();
        inv.totalCpu = vo.getTotalCpu();
        inv.totalMemory = vo.getTotalMemory();
        inv.availableCpu = vo.getAvailableCpu();
        inv.availableMemory = vo.getAvailableMemory();
        inv.availablePhysicalMemory = vo.getAvailablePhysicalMemory();
        inv.cpuNum = vo.getCpuNum();
        inv.cpuSockets = vo.getCpuSockets();
        inv.cpuCoreNum = vo.getCpuCoreNum();
        inv.reservedMemory = vo.getReservedMemory();
        inv.totalDisk = vo.getTotalDisk();
        inv.availableDisk = vo.getAvailableDisk();
        inv.capacityState = vo.getCapacityState() != null ? vo.getCapacityState().toString() : null;
        inv.exclusiveRoleUuid = vo.getExclusiveRoleUuid();
        inv.schedulingMode = vo.getSchedulingMode() != null ? vo.getSchedulingMode().toString() : null;
        return inv;
    }

    // ----- Getters & Setters (省略) -----
}
```

---

## 2. 状态机定义

### 2.1 PhysicalServerState.java

> 参照: `header/src/main/java/org/zstack/header/host/HostState.java`
> v2.0 变更: 新增 `PreMaintenance` 状态 (基于 KVM 专家评审 P0: VM 疏散过渡态)

```java
package org.zstack.header.server;

import org.zstack.header.exception.CloudRuntimeException;
import java.util.HashMap;
import java.util.Map;

public enum PhysicalServerState {
    Enabled,
    Disabled,
    PreMaintenance,  // v2.0 新增: VM 疏散过渡态 (基于 KVM 专家评审 P0)
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

    private static class Transaction {
        PhysicalServerStateEvent event;
        PhysicalServerState nextState;
        Transaction(PhysicalServerStateEvent event, PhysicalServerState nextState) {
            this.event = event;
            this.nextState = nextState;
        }
    }

    private Map<PhysicalServerStateEvent, Transaction> transactionMap = new HashMap<>();

    private void transactions(Transaction... transactions) {
        for (Transaction t : transactions) {
            transactionMap.put(t.event, t);
        }
    }

    public PhysicalServerState nextState(PhysicalServerStateEvent event) {
        Transaction t = transactionMap.get(event);
        if (t == null) {
            throw new CloudRuntimeException(String.format(
                "cannot find next state for current state[%s] on event[%s]", this, event));
        }
        return t.nextState;
    }
}
```

### 2.2 PhysicalServerStateEvent.java

> v2.0 变更: 新增 `preMaintain` 和 `maintain` 事件 (基于 KVM 专家评审 P0)

```java
package org.zstack.header.server;

public enum PhysicalServerStateEvent {
    enable,
    disable,
    preMaintain,  // v2.0 新增: Enabled/Disabled -> PreMaintenance
    maintain       // v2.0 新增: PreMaintenance -> Maintenance
}
```

### 2.3 PhysicalServerStatus.java

> 参照: `header/src/main/java/org/zstack/header/host/HostStatus.java`
> v2.0: 无变更

```java
package org.zstack.header.server;

import org.zstack.header.exception.CloudRuntimeException;
import java.util.HashMap;
import java.util.Map;

public enum PhysicalServerStatus {
    Unknown,
    Connecting,
    Connected,
    Disconnected;

    static {
        Unknown.transactions(
            new Transaction(PhysicalServerStatusEvent.connecting, Connecting),
            new Transaction(PhysicalServerStatusEvent.disconnected, Disconnected)
        );
        Connecting.transactions(
            new Transaction(PhysicalServerStatusEvent.connected, Connected),
            new Transaction(PhysicalServerStatusEvent.disconnected, Disconnected),
            new Transaction(PhysicalServerStatusEvent.connecting, Connecting)
        );
        Connected.transactions(
            new Transaction(PhysicalServerStatusEvent.disconnected, Disconnected),
            new Transaction(PhysicalServerStatusEvent.connecting, Connecting)
        );
        Disconnected.transactions(
            new Transaction(PhysicalServerStatusEvent.connecting, Connecting),
            new Transaction(PhysicalServerStatusEvent.connected, Connected),
            new Transaction(PhysicalServerStatusEvent.disconnected, Disconnected)
        );
    }

    private static class Transaction {
        PhysicalServerStatusEvent event;
        PhysicalServerStatus nextStatus;
        Transaction(PhysicalServerStatusEvent event, PhysicalServerStatus nextStatus) {
            this.event = event;
            this.nextStatus = nextStatus;
        }
    }

    private Map<PhysicalServerStatusEvent, Transaction> transactionMap = new HashMap<>();

    private void transactions(Transaction... transactions) {
        for (Transaction t : transactions) {
            transactionMap.put(t.event, t);
        }
    }

    public PhysicalServerStatus nextStatus(PhysicalServerStatusEvent event) {
        Transaction t = transactionMap.get(event);
        if (t == null) {
            throw new CloudRuntimeException(String.format(
                "cannot find next status for current status[%s] on event[%s]", this, event));
        }
        return t.nextStatus;
    }
}
```

### 2.4 PhysicalServerStatusEvent.java

> v2.0: 无变更

```java
package org.zstack.header.server;

public enum PhysicalServerStatusEvent {
    connecting,
    connected,
    disconnected
}
```

### 2.5 PhysicalServerPowerStatus.java (v2.0 新增)

> 物理服务器电源状态枚举。基于总架构师裁决 1.5: 三维状态中的独立电源维度。
> 与 status(连接状态) 正交。通过 OOB (IPMI/Redfish) 查询获取。

```java
package org.zstack.header.server;

/**
 * 物理服务器电源状态。通过 OOB (IPMI/Redfish) 查询获取。
 * 与 status(连接状态) 正交 (基于总架构师裁决 1.5)。
 *
 * 设计理由:
 * - 一台服务器可以是 Enabled(管理允许) + Disconnected(管理口不通) + PowerOn(实际开机)
 * - 一台服务器可以是 Maintenance(维护中) + Connected(管理口通) + PowerOff(已关机)
 * - BM2 专家明确指出 powerStatus 与 status 是正交维度
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

### 2.6 SchedulingMode.java (v2.0 新增)

> 调度模式枚举。基于总架构师裁决 1.3: 用统一模型包容四种角色的调度差异。

```java
package org.zstack.header.server;

/**
 * 物理服务器的调度模式。
 * 统一架构的价值不在于抹平差异，而在于用一个统一的模型去描述和包容差异。
 * (基于总架构师裁决 1.3)
 */
public enum SchedulingMode {
    /** ZStack 内部调度，按需扣减 CPU/Memory（如 KVM 创建 VM） */
    INTERNAL_SHARED,

    /** ZStack 内部调度，整机独占分配（如 Baremetal 整机装机） */
    INTERNAL_EXCLUSIVE,

    /** 外部调度器管理，ZStack 仅做只读同步（如 K8s 调度 Pod） */
    EXTERNAL_READONLY
}
```

### 2.7 HardwareDetailType.java (v2.0 新增)

> 硬件详情类型枚举。基于总架构师裁决 1.4: 1:N 详情子表的分类。

```java
package org.zstack.header.server;

/**
 * ServerHardwareDetailVO 的详情类型。
 * (基于总架构师裁决 1.4)
 */
public enum HardwareDetailType {
    BASIC,       // 基础信息 (序列号、厂商等)
    NIC,         // 网卡详情 (MAC, speed, PXE boot flag)
    DISK,        // 磁盘详情 (size, type, slot)
    GPU,         // GPU 详情 (vendor, model, memory)
    PCI,         // PCI 设备
    MEMORY_DIMM  // 内存条详情 (slot, size, speed)
}
```

### 2.8 ServerRoleType.java

> v2.0 变更 (基于总架构师裁决 1.3): 移除 `isExclusive()` 方法，改为 `getSchedulingMode()` 返回 SchedulingMode 枚举。
> 保留便捷方法 isExclusive() / isExternallyScheduled() / isInternallyScheduled() 作为 SchedulingMode 的快捷查询。

```java
package org.zstack.header.server;

/**
 * 角色类型枚举。
 * v2.0: 每个角色类型关联一个 SchedulingMode (基于总架构师裁决 1.3)。
 */
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

    /** 便捷方法: 是否独占分配 */
    public boolean isExclusive() {
        return schedulingMode == SchedulingMode.INTERNAL_EXCLUSIVE;
    }

    /** 便捷方法: 是否外部调度 */
    public boolean isExternallyScheduled() {
        return schedulingMode == SchedulingMode.EXTERNAL_READONLY;
    }

    /** 便捷方法: 是否内部调度 */
    public boolean isInternallyScheduled() {
        return schedulingMode != SchedulingMode.EXTERNAL_READONLY;
    }
}
```

### 2.9 ServerPoolState.java

> v2.0: 无变更

```java
package org.zstack.header.server;

public enum ServerPoolState {
    Enabled,
    Disabled
}
```

### 2.10 状态维度对照表 (v2.0 新增)

> 基于总架构师裁决 1.5: 三维状态 + 角色层 roleStatus

| 维度 | 枚举 | 含义 | 谁控制 | 值 |
|------|------|------|--------|-----|
| state | PhysicalServerState | 管理员意图 | 用户 API | Enabled, Disabled, PreMaintenance, Maintenance |
| status | PhysicalServerStatus | 管理面连接 | 系统自动 | Unknown, Connecting, Connected, Disconnected |
| powerStatus | PhysicalServerPowerStatus | 物理电源 | OOB 查询 | PowerOn, PowerOff, PowerUnknown |
| roleStatus | PhysicalServerRoleVO.roleStatus | 角色运行 | 角色模块 | 自定义字符串 |

### 2.11 分配器对状态的使用 (v2.0)

```
ServerStateAllocatorFlow:
  - state == Enabled (排除 Disabled/PreMaintenance/Maintenance)
  - status == Connected (排除 Unknown/Connecting/Disconnected)
  - powerStatus == PowerOn 或 PowerUnknown (排除 PowerOff)
  - schedulingMode != EXTERNAL_READONLY (排除容器)
```

---

## 3. Inventory 定义

### 3.1 PhysicalServerInventory.java

> 参照: `header/src/main/java/org/zstack/header/host/HostInventory.java`
> v2.0 变更: 新增 `powerStatus` 字段 (基于总架构师裁决 1.5)；新增 `hardwareDetails` 列表 (基于总架构师裁决 1.4)

```java
package org.zstack.header.server;

import org.zstack.header.search.Inventory;
import org.zstack.header.configuration.PythonClassInventory;
import org.zstack.header.query.ExpandedQueries;
import org.zstack.header.query.ExpandedQuery;
import org.zstack.header.zone.ZoneInventory;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Inventory(mappingVOClass = PhysicalServerVO.class)
@PythonClassInventory
@ExpandedQueries({
    @ExpandedQuery(expandedField = "zone", inventoryClass = ZoneInventory.class,
        foreignKey = "zoneUuid", expandedInventoryKey = "uuid"),
    @ExpandedQuery(expandedField = "serverPool", inventoryClass = ServerPoolInventory.class,
        foreignKey = "serverPoolUuid", expandedInventoryKey = "uuid"),
})
public class PhysicalServerInventory implements Serializable {
    private String uuid;
    private String zoneUuid;
    private String serverPoolUuid;
    private String name;
    private String description;
    private String managementIp;
    private String architecture;
    private String serialNumber;
    private String manufacturer;
    private String model;
    private String oobManagementType;
    private String oobAddress;
    private Integer oobPort;
    private String state;
    private String status;
    private String powerStatus;  // v2.0 新增 (基于总架构师裁决 1.5)
    private Timestamp createDate;
    private Timestamp lastOpDate;

    // 聚合字段
    private ServerHardwareInfoInventory hardwareInfo;              // 来自 ServerHardwareInfoVO
    private ServerCapacityInventory capacity;                      // 来自 ServerCapacityVO
    private List<PhysicalServerRoleInventory> roles;               // 来自 PhysicalServerRoleVO
    private List<ServerHardwareDetailInventory> hardwareDetails;   // v2.0 新增: 来自 ServerHardwareDetailVO (基于总架构师裁决 1.4)

    public PhysicalServerInventory() {
    }

    protected PhysicalServerInventory(PhysicalServerVO vo) {
        this.uuid = vo.getUuid();
        this.zoneUuid = vo.getZoneUuid();
        this.serverPoolUuid = vo.getServerPoolUuid();
        this.name = vo.getName();
        this.description = vo.getDescription();
        this.managementIp = vo.getManagementIp();
        this.architecture = vo.getArchitecture();
        this.serialNumber = vo.getSerialNumber();
        this.manufacturer = vo.getManufacturer();
        this.model = vo.getModel();
        this.oobManagementType = vo.getOobManagementType();
        this.oobAddress = vo.getOobAddress();
        this.oobPort = vo.getOobPort();
        this.state = vo.getState() != null ? vo.getState().toString() : null;
        this.status = vo.getStatus() != null ? vo.getStatus().toString() : null;
        this.powerStatus = vo.getPowerStatus() != null ? vo.getPowerStatus().toString() : null;
        this.createDate = vo.getCreateDate();
        this.lastOpDate = vo.getLastOpDate();

        if (vo.getRoles() != null) {
            this.roles = PhysicalServerRoleInventory.valueOf(vo.getRoles());
        }

        if (vo.getHardwareDetails() != null) {
            this.hardwareDetails = ServerHardwareDetailInventory.valueOf(vo.getHardwareDetails());
        }
    }

    public static PhysicalServerInventory valueOf(PhysicalServerVO vo) {
        return new PhysicalServerInventory(vo);
    }

    public static List<PhysicalServerInventory> valueOf(Collection<PhysicalServerVO> vos) {
        List<PhysicalServerInventory> invs = new ArrayList<>(vos.size());
        for (PhysicalServerVO vo : vos) {
            invs.add(PhysicalServerInventory.valueOf(vo));
        }
        return invs;
    }

    // ----- Getters & Setters (省略) -----
}
```

### 3.2 ServerPoolInventory.java

> v2.0: 无变更

```java
package org.zstack.header.server;

import org.zstack.header.search.Inventory;
import org.zstack.header.configuration.PythonClassInventory;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Inventory(mappingVOClass = ServerPoolVO.class)
@PythonClassInventory
public class ServerPoolInventory implements Serializable {
    private String uuid;
    private String name;
    private String description;
    private String zoneUuid;
    private String state;
    private Timestamp createDate;
    private Timestamp lastOpDate;

    public ServerPoolInventory() {
    }

    protected ServerPoolInventory(ServerPoolVO vo) {
        this.uuid = vo.getUuid();
        this.name = vo.getName();
        this.description = vo.getDescription();
        this.zoneUuid = vo.getZoneUuid();
        this.state = vo.getState() != null ? vo.getState().toString() : null;
        this.createDate = vo.getCreateDate();
        this.lastOpDate = vo.getLastOpDate();
    }

    public static ServerPoolInventory valueOf(ServerPoolVO vo) {
        return new ServerPoolInventory(vo);
    }

    public static List<ServerPoolInventory> valueOf(Collection<ServerPoolVO> vos) {
        List<ServerPoolInventory> invs = new ArrayList<>(vos.size());
        for (ServerPoolVO vo : vos) {
            invs.add(ServerPoolInventory.valueOf(vo));
        }
        return invs;
    }

    // ----- Getters & Setters (省略) -----
}
```

### 3.3 PhysicalServerRoleInventory.java

> v2.0 变更: 新增 `clusterUuid`, `sourceUuid`, `roleStatus` 字段 (基于总架构师裁决 1.1/1.5)

```java
package org.zstack.header.server;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class PhysicalServerRoleInventory implements Serializable {
    private long id;
    private String serverUuid;
    private String roleType;
    private String roleUuid;
    private String clusterUuid;   // v2.0 新增 (基于总架构师裁决 1.1)
    private String sourceUuid;    // v2.0 新增 (基于 Container 专家评审)
    private String roleStatus;    // v2.0 新增 (基于总架构师裁决 1.5)
    private String syncStatus;
    private Timestamp createDate;
    private Timestamp lastSyncTime;

    public PhysicalServerRoleInventory() {
    }

    public static PhysicalServerRoleInventory valueOf(PhysicalServerRoleVO vo) {
        PhysicalServerRoleInventory inv = new PhysicalServerRoleInventory();
        inv.id = vo.getId();
        inv.serverUuid = vo.getServerUuid();
        inv.roleType = vo.getRoleType() != null ? vo.getRoleType().toString() : null;
        inv.roleUuid = vo.getRoleUuid();
        inv.clusterUuid = vo.getClusterUuid();
        inv.sourceUuid = vo.getSourceUuid();
        inv.roleStatus = vo.getRoleStatus();
        inv.syncStatus = vo.getSyncStatus() != null ? vo.getSyncStatus().toString() : null;
        inv.createDate = vo.getCreateDate();
        inv.lastSyncTime = vo.getLastSyncTime();
        return inv;
    }

    public static List<PhysicalServerRoleInventory> valueOf(Collection<PhysicalServerRoleVO> vos) {
        List<PhysicalServerRoleInventory> invs = new ArrayList<>(vos.size());
        for (PhysicalServerRoleVO vo : vos) {
            invs.add(PhysicalServerRoleInventory.valueOf(vo));
        }
        return invs;
    }

    // ----- Getters & Setters (省略) -----
}
```

### 3.4 ServerHardwareInfoInventory.java

> v2.0 变更: 新增 `bootMode`, `discoverySource` 字段 (基于总架构师裁决 1.4)

```java
package org.zstack.header.server;

import java.io.Serializable;
import java.sql.Timestamp;

public class ServerHardwareInfoInventory implements Serializable {
    private String uuid;
    private Integer cpuSockets;
    private Integer cpuCoresPerSocket;
    private Integer cpuThreadsPerCore;
    private String cpuModel;
    private Long totalMemoryBytes;
    private Integer memorySlots;
    private Integer diskCount;
    private Long totalDiskBytes;
    private Integer nicCount;
    private String biosVersion;
    private String bmcVersion;
    private String bootMode;          // v2.0 新增 (基于 BM1 专家评审)
    private String discoverySource;   // v2.0 新增 (基于总架构师裁决 1.4)
    private Timestamp discoveredDate;
    private Timestamp lastUpdatedDate;

    public static ServerHardwareInfoInventory valueOf(ServerHardwareInfoVO vo) {
        ServerHardwareInfoInventory inv = new ServerHardwareInfoInventory();
        inv.uuid = vo.getUuid();
        inv.cpuSockets = vo.getCpuSockets();
        inv.cpuCoresPerSocket = vo.getCpuCoresPerSocket();
        inv.cpuThreadsPerCore = vo.getCpuThreadsPerCore();
        inv.cpuModel = vo.getCpuModel();
        inv.totalMemoryBytes = vo.getTotalMemoryBytes();
        inv.memorySlots = vo.getMemorySlots();
        inv.diskCount = vo.getDiskCount();
        inv.totalDiskBytes = vo.getTotalDiskBytes();
        inv.nicCount = vo.getNicCount();
        inv.biosVersion = vo.getBiosVersion();
        inv.bmcVersion = vo.getBmcVersion();
        inv.bootMode = vo.getBootMode();
        inv.discoverySource = vo.getDiscoverySource();
        inv.discoveredDate = vo.getDiscoveredDate();
        inv.lastUpdatedDate = vo.getLastUpdatedDate();
        return inv;
    }

    // ----- Getters & Setters (省略) -----
}
```

### 3.5 ServerHardwareDetailInventory.java (v2.0 新增)

> 基于总架构师裁决 1.4: 1:N 硬件详情子表的 DTO

```java
package org.zstack.header.server;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ServerHardwareDetailInventory implements Serializable {
    private long id;
    private String serverUuid;
    private String type;
    private String content;
    private Timestamp createDate;
    private Timestamp lastOpDate;

    public ServerHardwareDetailInventory() {
    }

    public static ServerHardwareDetailInventory valueOf(ServerHardwareDetailVO vo) {
        ServerHardwareDetailInventory inv = new ServerHardwareDetailInventory();
        inv.id = vo.getId();
        inv.serverUuid = vo.getServerUuid();
        inv.type = vo.getType() != null ? vo.getType().toString() : null;
        inv.content = vo.getContent();
        inv.createDate = vo.getCreateDate();
        inv.lastOpDate = vo.getLastOpDate();
        return inv;
    }

    public static List<ServerHardwareDetailInventory> valueOf(Collection<ServerHardwareDetailVO> vos) {
        List<ServerHardwareDetailInventory> invs = new ArrayList<>(vos.size());
        for (ServerHardwareDetailVO vo : vos) {
            invs.add(ServerHardwareDetailInventory.valueOf(vo));
        }
        return invs;
    }

    // ----- Getters & Setters (省略) -----
}
```

---

## 4. API 消息定义

### 4.1 PhysicalServerConstant.java

> 参照: `header/src/main/java/org/zstack/header/host/HostConstant.java`
> v2.0: 无变更

```java
package org.zstack.header.server;

public interface PhysicalServerConstant {
    String SERVICE_ID = "physicalServer";
    String ACTION_CATEGORY = "physicalServer";
}
```

### 4.2 PhysicalServerMessage.java

> 参照: `header/src/main/java/org/zstack/header/host/HostMessage.java`
> v2.0: 无变更

```java
package org.zstack.header.server;

public interface PhysicalServerMessage {
    String getServerUuid();
}
```

### 4.3 APIRegisterPhysicalServerMsg.java

> 参照: `header/src/main/java/org/zstack/header/configuration/APICreateDiskOfferingMsg.java`
> v2.0 变更 (基于 Container 专家评审 P0):
>   - OOB 字段全部改为 `required = false`
>   - oobManagementType 增加 `NONE` 选项
>   - 新增 serialNumber 和 architecture 参数

```java
package org.zstack.header.server;

import org.zstack.header.identity.Action;
import org.zstack.header.message.APICreateMessage;
import org.zstack.header.message.APIParam;
import org.zstack.header.rest.RestRequest;
import org.zstack.header.tag.TagResourceType;
import org.zstack.header.zone.ZoneVO;

@TagResourceType(PhysicalServerVO.class)
@Action(category = PhysicalServerConstant.ACTION_CATEGORY)
@RestRequest(
    path = "/physical-servers",
    method = org.zstack.header.rest.RestRequest.HttpMethod.POST,
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

    // v2.0: OOB 字段全部 required = false (基于 Container 专家评审 P0: 容器节点无 OOB)
    @APIParam(required = false, maxLength = 255)
    private String oobAddress;

    @APIParam(required = false)
    private Integer oobPort;

    @APIParam(required = false, maxLength = 255)
    private String oobUsername;

    @APIParam(required = false, maxLength = 255)
    private String oobPassword;

    @APIParam(required = false, validValues = {"IPMI", "REDFISH", "NONE"})  // v2.0: 增加 NONE
    private String oobManagementType;

    @APIParam(required = false, resourceType = ServerPoolVO.class)
    private String serverPoolUuid;

    @APIParam(required = false, maxLength = 255)
    private String managementIp;

    @APIParam(required = false, maxLength = 255)
    private String serialNumber;  // v2.0 新增: 允许注册时提供

    @APIParam(required = false, validValues = {"x86_64", "aarch64", "mips64el", "loongarch64"})
    private String architecture;  // v2.0 新增

    public static APIRegisterPhysicalServerMsg __example__() {
        APIRegisterPhysicalServerMsg msg = new APIRegisterPhysicalServerMsg();
        msg.setName("server-01");
        msg.setZoneUuid(uuid());
        msg.setOobAddress("192.168.1.100");
        msg.setOobPort(623);
        msg.setOobUsername("admin");
        msg.setOobPassword("password");
        msg.setOobManagementType("IPMI");
        return msg;
    }

    // ----- Getters & Setters (省略) -----
}
```

### 4.4 APIRegisterPhysicalServerEvent.java

> v2.0: 无变更

```java
package org.zstack.header.server;

import org.zstack.header.message.APIEvent;
import org.zstack.header.rest.RestResponse;

@RestResponse(allTo = "inventory")
public class APIRegisterPhysicalServerEvent extends APIEvent {
    private PhysicalServerInventory inventory;

    public APIRegisterPhysicalServerEvent() {
        super(null);
    }

    public APIRegisterPhysicalServerEvent(String apiId) {
        super(apiId);
    }

    public PhysicalServerInventory getInventory() {
        return inventory;
    }

    public void setInventory(PhysicalServerInventory inventory) {
        this.inventory = inventory;
    }

    public static APIRegisterPhysicalServerEvent __example__() {
        APIRegisterPhysicalServerEvent event = new APIRegisterPhysicalServerEvent();
        // ... populate example
        return event;
    }
}
```

### 4.5 APIQueryPhysicalServerMsg.java

> 参照: `header/src/main/java/org/zstack/header/host/APIQueryHostMsg.java`
> v2.0: 无变更

```java
package org.zstack.header.server;

import org.zstack.header.identity.Action;
import org.zstack.header.query.APIQueryMessage;
import org.zstack.header.query.AutoQuery;
import org.zstack.header.rest.RestRequest;

import static org.zstack.utils.CollectionDSL.list;
import java.util.List;

@Action(category = PhysicalServerConstant.ACTION_CATEGORY, names = {"read"})
@AutoQuery(replyClass = APIQueryPhysicalServerReply.class, inventoryClass = PhysicalServerInventory.class)
@RestRequest(
    path = "/physical-servers",
    optionalPaths = {"/physical-servers/{uuid}"},
    responseClass = APIQueryPhysicalServerReply.class,
    method = org.zstack.header.rest.RestRequest.HttpMethod.GET
)
public class APIQueryPhysicalServerMsg extends APIQueryMessage {
    public static List<String> __example__() {
        return list("uuid=" + uuid());
    }
}
```

### 4.6 APIQueryPhysicalServerReply.java

> v2.0: 无变更

```java
package org.zstack.header.server;

import org.zstack.header.query.APIQueryReply;
import org.zstack.header.rest.RestResponse;

import java.util.List;

@RestResponse(allTo = "inventories")
public class APIQueryPhysicalServerReply extends APIQueryReply {
    private List<PhysicalServerInventory> inventories;

    public List<PhysicalServerInventory> getInventories() {
        return inventories;
    }

    public void setInventories(List<PhysicalServerInventory> inventories) {
        this.inventories = inventories;
    }
}
```

### 4.7 APIUpdatePhysicalServerMsg.java

> v2.0: 无变更

```java
package org.zstack.header.server;

import org.zstack.header.identity.Action;
import org.zstack.header.message.APIMessage;
import org.zstack.header.message.APIParam;
import org.zstack.header.rest.RestRequest;

@Action(category = PhysicalServerConstant.ACTION_CATEGORY)
@RestRequest(
    path = "/physical-servers/{uuid}/actions",
    method = org.zstack.header.rest.RestRequest.HttpMethod.PUT,
    responseClass = APIUpdatePhysicalServerEvent.class,
    isAction = true
)
public class APIUpdatePhysicalServerMsg extends APIMessage implements PhysicalServerMessage {

    @APIParam(resourceType = PhysicalServerVO.class)
    private String uuid;

    @APIParam(maxLength = 255, required = false)
    private String name;

    @APIParam(maxLength = 2048, required = false)
    private String description;

    @APIParam(required = false, resourceType = ServerPoolVO.class)
    private String serverPoolUuid;

    @APIParam(required = false, validValues = {"Enabled", "Disabled", "Maintenance"})
    private String state;

    @Override
    public String getServerUuid() {
        return uuid;
    }

    // ----- Getters & Setters (省略) -----
}
```

### 4.8 APIDeletePhysicalServerMsg.java

> v2.0: 无变更

```java
package org.zstack.header.server;

import org.zstack.header.identity.Action;
import org.zstack.header.message.APIDeleteMessage;
import org.zstack.header.message.APIParam;
import org.zstack.header.rest.RestRequest;

@Action(category = PhysicalServerConstant.ACTION_CATEGORY)
@RestRequest(
    path = "/physical-servers/{uuid}",
    method = org.zstack.header.rest.RestRequest.HttpMethod.DELETE,
    responseClass = APIDeletePhysicalServerEvent.class
)
public class APIDeletePhysicalServerMsg extends APIDeleteMessage implements PhysicalServerMessage {

    @APIParam
    private String uuid;

    @Override
    public String getServerUuid() {
        return uuid;
    }

    public static APIDeletePhysicalServerMsg __example__() {
        APIDeletePhysicalServerMsg msg = new APIDeletePhysicalServerMsg();
        msg.setUuid(uuid());
        return msg;
    }

    // ----- Getters & Setters (省略) -----
}
```

### 4.9 ServerPool API 消息 (精简)

> v2.0: 无变更

```java
// APICreateServerPoolMsg.java
@TagResourceType(ServerPoolVO.class)
@Action(category = PhysicalServerConstant.ACTION_CATEGORY)
@RestRequest(path = "/server-pools", method = HttpMethod.POST,
    responseClass = APICreateServerPoolEvent.class, parameterName = "params")
public class APICreateServerPoolMsg extends APICreateMessage {
    @APIParam(maxLength = 255) private String name;
    @APIParam(required = false, maxLength = 2048) private String description;
    @APIParam(resourceType = ZoneVO.class) private String zoneUuid;
}

// APIQueryServerPoolMsg.java
@AutoQuery(replyClass = APIQueryServerPoolReply.class, inventoryClass = ServerPoolInventory.class)
@RestRequest(path = "/server-pools", optionalPaths = {"/server-pools/{uuid}"},
    responseClass = APIQueryServerPoolReply.class, method = HttpMethod.GET)
public class APIQueryServerPoolMsg extends APIQueryMessage {}

// APIDeleteServerPoolMsg.java
@RestRequest(path = "/server-pools/{uuid}", method = HttpMethod.DELETE,
    responseClass = APIDeleteServerPoolEvent.class)
public class APIDeleteServerPoolMsg extends APIDeleteMessage {
    @APIParam private String uuid;
}
```

---

## 4B. 新增接口与消息 (v2.0)

### 4B.1 ServerCapacityUpdaterRunnable.java (v2.0 新增)

> 回调接口，允许调用方在悲观锁内定制容量更新逻辑。
> 参照: `compute/src/main/java/org/zstack/compute/allocator/HostCapacityUpdater.java` 的回调模式
> 基于 Allocator 专家评审建议

```java
package org.zstack.header.server;

/**
 * 回调接口，允许调用方在悲观锁内定制容量更新逻辑。
 * 参照: HostCapacityUpdaterRunnable
 * (基于 Allocator 专家建议)
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

### 4B.2 ServerCapacityOverProvisioningManager.java (v2.0 新增)

> 超分比管理接口。基于总架构师裁决 1.6: 独立 Manager + 预计算持久化。
> Phase 1 定义接口，Phase 2 实现。

```java
package org.zstack.header.server;

/**
 * 超分比管理接口。
 * (基于总架构师裁决 1.6: 独立 Manager + 预计算持久化)
 *
 * 设计理由:
 * - v1.1 的 VO getter 实时计算方案有三个致命问题:
 *   1. Hibernate/JPA 的 SQL 查询无法调用 Java getter
 *   2. 无法支持 per-server 级别的超分比设置
 *   3. 超分比变更时没有重算机制
 * - 现有系统做法: CPU 超分预计算持久化到 totalCpu 字段，DB 查询直接可用
 *
 * Phase 1 定义接口, Phase 2 实现 ServerCapacityOverProvisioningManagerImpl
 */
public interface ServerCapacityOverProvisioningManager {
    /** 获取 CPU 超分比 (先查 per-server，再回退全局) */
    double getCpuRatio(String serverUuid);

    /** 设置 per-server CPU 超分比 */
    void setCpuRatio(String serverUuid, double ratio);

    /** 获取内存超分比 (先查 per-server，再回退全局) */
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

### 4B.3 ServerAllocatorFilterExtensionPoint.java (v2.0 新增)

> 分配器过滤扩展点。基于 Allocator 专家建议。

```java
package org.zstack.header.server;

import java.util.List;

/**
 * 分配器过滤扩展点。
 * 角色模块可以注册此扩展点，注入角色特有的过滤逻辑。
 * (基于 Allocator 专家建议)
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

### 4B.4 ServerReservedCapacityExtensionPoint.java (v2.0 新增)

> 预留容量扩展点。基于 Allocator 专家建议。
> 参照: `header/src/main/java/org/zstack/header/allocator/HostReservedCapacityExtensionPoint.java`

```java
package org.zstack.header.server;

/**
 * 预留容量扩展点。
 * 各模块可以声明在某台物理服务器上需要预留的内存量。
 * 例如: Ceph Agent 预留 512MB, ZStack Agent 预留 256MB.
 * (基于 Allocator 专家建议)
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

### 4B.5 ServerAllocatorCompatibilityBridge.java (v2.0 新增)

> 兼容层接口。基于总架构师裁决 1.7: Phase 1 定义接口 + POC，Phase 2 完整实现。

```java
package org.zstack.header.server;

import org.zstack.header.allocator.AllocateHostMsg;
import org.zstack.header.allocator.AllocateHostReply;

/**
 * 兼容层接口: AllocateHostMsg <-> AllocateServerMsg 的双向转换。
 * (基于总架构师裁决 1.7: Phase 1 定义接口, Phase 2 实现)
 *
 * Phase 1: 定义此接口 + 空壳 POC (编译通过 + smoke test)
 * Phase 2: 完整实现分配链和兼容层
 * Phase 3: 灰度切换 (特性开关 physicalServer.allocator.enabled)
 */
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

### 4B.6 RecalculateServerCapacityMsg.java (v2.0 新增)

> 内部消息: 超分比变更后触发容量重算。基于 Allocator 专家建议。

```java
package org.zstack.header.server;

import org.zstack.header.message.NeedReplyMessage;

/**
 * 内部消息: 触发某台服务器的容量重算。
 * 当超分比变更或需要对账时发送。
 * (基于 Allocator 专家建议)
 */
public class RecalculateServerCapacityMsg extends NeedReplyMessage implements PhysicalServerMessage {

    private String serverUuid;

    @Override
    public String getServerUuid() {
        return serverUuid;
    }

    public void setServerUuid(String serverUuid) {
        this.serverUuid = serverUuid;
    }
}
```

### 4B.7 PhysicalServerGlobalConfig.java (v2.0 新增)

> 全局配置定义。基于 Allocator 专家建议。
> 参照: `header/src/main/java/org/zstack/header/host/HostGlobalConfig.java`

```java
package org.zstack.header.server;

import org.zstack.core.config.GlobalConfig;
import org.zstack.core.config.GlobalConfigDefinition;
import org.zstack.core.config.GlobalConfigValidation;

/**
 * PhysicalServer 全局配置。
 * (基于 Allocator 专家建议 + 总架构师裁决 1.7)
 */
@GlobalConfigDefinition
public class PhysicalServerGlobalConfig {

    public static final String CATEGORY = "physicalServer";

    @GlobalConfigValidation
    public static GlobalConfig ALLOCATOR_ENABLED = new GlobalConfig(CATEGORY, "allocator.enabled");

    @GlobalConfigValidation(numberGreaterThan = 0)
    public static GlobalConfig CPU_OVER_PROVISIONING_RATIO = new GlobalConfig(CATEGORY, "cpu.overProvisioning.ratio");

    @GlobalConfigValidation(numberGreaterThan = 0)
    public static GlobalConfig MEMORY_OVER_PROVISIONING_RATIO = new GlobalConfig(CATEGORY, "memory.overProvisioning.ratio");
}
```

---

## 5. SPI: PhysicalServerRoleProvider

> 这是各模块需要实现的接口，用于角色关联和数据聚合
> v2.0 变更: 新增 getClusterUuid / getSourceUuid / getActualUsage 方法 (基于总架构师裁决 1.1)

```java
package org.zstack.header.server;

/**
 * 角色数据提供者 SPI (v2.0)。
 * 各角色模块 (KVM, Baremetal, Container) 实现此接口，
 * 注册到 PluginRegistry，由 PhysicalServerManager 聚合调用。
 *
 * 注意: 这不是操作接口。不涉及创建/删除/连接等业务操作。
 * 业务操作仍由各模块自行处理。
 */
public interface PhysicalServerRoleProvider {

    /** 角色类型标识 */
    ServerRoleType getRoleType();

    /**
     * 通过角色资源 UUID 反向查询物理服务器关联。
     * 如果已存在关联返回现有记录；如果不存在返回 null。
     */
    PhysicalServerRoleVO findRoleAssociation(String roleUuid);

    /**
     * 获取角色状态信息 (用于聚合到 PhysicalServer 视图)。
     * @return 状态字符串，如 "Connected", "Disconnected" 等
     */
    String getRoleStatus(String roleUuid);

    /**
     * 获取角色的容量使用摘要 (只读，用于统一视图展示)。
     * @return null 表示该角色不支持容量概念 (如 Baremetal)
     */
    ServerCapacitySummary getCapacitySummary(String roleUuid);

    /**
     * v2.0 新增: 获取角色的 cluster UUID。
     * 分配时通过此方法获取角色的集群归属。
     * (基于总架构师裁决 1.1: per-role cluster)
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

    /**
     * 容量摘要数据结构 (只读视图)
     */
    class ServerCapacitySummary {
        public long totalCpu;
        public long usedCpu;
        public long totalMemoryBytes;
        public long usedMemoryBytes;
        public long totalDiskBytes;
        public long usedDiskBytes;
    }

    /**
     * v2.0 新增: 实际使用量 (用于对账)
     */
    class ServerCapacityUsage {
        public long usedCpu;
        public long usedMemoryBytes;
    }
}
```

---

## 6. 扩展点集成设计

### 6.1 角色自动关联策略

> 不修改任何现有模块代码。利用现有扩展点，在 **PhysicalServer 模块** 中实现监听。
> v2.0 变更 (基于 Container 专家评审 P0): Container 角色关联改用 NativeHostSyncedExtensionPoint，
> 不使用 HostAfterConnectedExtensionPoint。各角色关联时机由 RoleProvider 自行决定 (基于总架构师裁决)。

| 角色 | 监听扩展点 | 触发时机 | 匹配方式 | clusterUuid 来源 | sourceUuid 来源 |
|------|-----------|---------|---------|----------------|----------------|
| KVM Host | `HostAfterConnectedExtensionPoint` | Host 首次 Connected | managementIp + zoneUuid | HostVO.clusterUuid | null |
| Container | `NativeHostSyncedExtensionPoint` (新增, 方案 B) | Endpoint 同步完成后 | managementIp + zoneUuid | NativeClusterVO.uuid | ContainerManagementEndpointVO.uuid |
| BM V1 | 需新增: `BaremetalChassisCreateExtensionPoint` 或 EventFacade | Chassis 创建后 | oobAddress + oobPort | BaremetalChassisVO.clusterUuid | pxeServerUuid |
| BM V2 | 需新增: `BareMetal2ChassisCreateExtensionPoint` 或 EventFacade | 由 BM2 RoleProvider 自行决定 | oobAddress + oobPort | BareMetal2ChassisAO.clusterUuid | null |

**Container 关联设计说明** (基于 Container 专家评审 P0):
- **不使用** `HostAfterConnectedExtensionPoint`
- Container 专家明确指出 NativeFactory.createHost() 抛 UnsupportedOperationException，整个 container 模块不触发 HostAfterConnectedExtensionPoint
- 应使用 `ContainerEndpointSyncExtensionPoint` 或新增 `NativeHostSyncedExtensionPoint`

**角色关联时机说明** (基于总架构师裁决):
- 角色关联时机由各角色的 PhysicalServerRoleProvider 实现自行决定
- 统一层只提供关联/解关联的方法，不规定时机
- BM2: 硬件发现成功后关联 (合理，发现失败的 chassis 不应消耗 PhysicalServer 资源)
- BM1: Chassis 创建后就有角色映射需求 (PXE 需要知道 chassis 存在)
- KVM: Host Connected 后关联
- Container: Node 同步入库后关联

### 6.2 KVM 关联实现 (在 PhysicalServer 模块)

```java
// PhysicalServerRoleAssociator.java (在 server 模块实现)
@Component
public class PhysicalServerRoleAssociator implements HostAfterConnectedExtensionPoint {

    @Autowired
    private DatabaseFacade dbf;

    @Override
    public void afterHostConnected(HostInventory host) {
        // 1. 通过 managementIp + zoneUuid 查找 PhysicalServerVO (v2.0: 加 zone 联合条件, 基于 KVM 专家建议)
        PhysicalServerVO server = findByManagementIpAndZone(host.getManagementIp(), host.getZoneUuid());
        if (server == null) {
            return;  // 该 Host 没有对应的物理服务器注册，跳过
        }

        // 2. 确定角色类型
        ServerRoleType roleType = mapHypervisorToRoleType(host.getHypervisorType());
        if (roleType == null) {
            return;
        }

        // 3. 检查是否已关联
        boolean exists = Q.New(PhysicalServerRoleVO.class)
            .eq(PhysicalServerRoleVO_.serverUuid, server.getUuid())
            .eq(PhysicalServerRoleVO_.roleType, roleType)
            .isExists();
        if (exists) {
            return;
        }

        // 4. 创建关联 (v2.0: 设置 clusterUuid 和 roleStatus)
        PhysicalServerRoleVO role = new PhysicalServerRoleVO();
        role.setServerUuid(server.getUuid());
        role.setRoleType(roleType);
        role.setRoleUuid(host.getUuid());
        role.setClusterUuid(host.getClusterUuid());  // v2.0 新增 (基于总架构师裁决 1.1)
        role.setRoleStatus("Connected");              // v2.0 新增 (基于总架构师裁决 1.5)
        role.setSyncStatus(PhysicalServerRoleVO.RoleSyncStatus.InSync);
        role.setCreateDate(new Timestamp(System.currentTimeMillis()));
        role.setLastSyncTime(new Timestamp(System.currentTimeMillis()));
        dbf.persist(role);
    }

    private ServerRoleType mapHypervisorToRoleType(String hypervisorType) {
        if ("KVM".equals(hypervisorType)) return ServerRoleType.KVM_HOST;
        // v2.0: NativeHost 不在此处理 (使用独立扩展点, 基于 Container 专家评审)
        return null;
    }
}
```

### 6.3 Spring XML 注册

```xml
<!-- conf/springConfigXml/PhysicalServer.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:zstack="http://zstack.org/schema/zstack"
       xsi:schemaLocation="..."
       default-init-method="init"
       default-destroy-method="destroy">

    <bean id="PhysicalServerManager"
          class="org.zstack.server.PhysicalServerManagerImpl">
        <zstack:plugin>
            <zstack:extension interface="org.zstack.header.Component" />
            <zstack:extension interface="org.zstack.header.Service" />
        </zstack:plugin>
    </bean>

    <bean id="PhysicalServerRoleAssociator"
          class="org.zstack.server.PhysicalServerRoleAssociator">
        <zstack:plugin>
            <zstack:extension interface="org.zstack.header.host.HostAfterConnectedExtensionPoint" />
        </zstack:plugin>
    </bean>

</beans>
```

### 6.4 GlobalConfig XML

> v2.0 新增 (基于 Allocator 专家建议)

```xml
<!-- conf/globalConfig/physicalServer.xml -->
<globalConfig>
    <!-- 统一分配器特性开关 (基于总架构师裁决 1.7) -->
    <config>
        <category>physicalServer</category>
        <name>allocator.enabled</name>
        <description>Enable unified physical server allocator</description>
        <defaultValue>false</defaultValue>
        <type>java.lang.Boolean</type>
    </config>

    <!-- 默认 CPU 超分比 (基于总架构师裁决 1.6) -->
    <config>
        <category>physicalServer</category>
        <name>cpu.overProvisioning.ratio</name>
        <description>Default CPU over-provisioning ratio</description>
        <defaultValue>10</defaultValue>
        <type>java.lang.Double</type>
    </config>

    <!-- 默认内存超分比 (基于总架构师裁决 1.6) -->
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

## 7. 数据库迁移脚本

> v2.0 全面修订: 表名修正, 新增字段, 新增表, 移除旧字段

```sql
-- V5.5.7__schema.sql (或匹配当前版本号)
-- v2.0 全面修订 (基于总架构师裁决)

-- ============================================================
-- ServerPoolVO: 物理服务器池 (可选物理分组)
-- ============================================================
CREATE TABLE IF NOT EXISTS `zstack`.`ServerPoolVO` (
    `uuid` varchar(32) NOT NULL,
    `name` varchar(255) NOT NULL,
    `description` varchar(2048) DEFAULT NULL,
    `zoneUuid` varchar(32) NOT NULL,
    `state` varchar(32) NOT NULL DEFAULT 'Enabled',
    `createDate` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00',
    `lastOpDate` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00' ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`uuid`),
    CONSTRAINT `fkServerPoolVOZoneEO` FOREIGN KEY (`zoneUuid`)
        REFERENCES `ZoneEO` (`uuid`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- 插入 ResourceVO 基表
-- (ZStack 的 ResourceVO 使用 JOINED 继承，需要对应行)

-- ============================================================
-- PhysicalServerVO: 物理服务器唯一标识
-- v2.0: 表名改为 PhysicalServerVO (不是 PhysicalServerAO)
--        @MappedSuperclass 不生成独立表 (基于 Allocator 专家评审 P0)
-- v2.0: 新增 powerStatus 列 (基于总架构师裁决 1.5)
-- v2.0: oobAddress 增加索引 (基于 BM1 专家评审)
-- ============================================================
CREATE TABLE IF NOT EXISTS `zstack`.`PhysicalServerVO` (
    `uuid` varchar(32) NOT NULL,
    `zoneUuid` varchar(32) NOT NULL,
    `serverPoolUuid` varchar(32) DEFAULT NULL,
    `name` varchar(255) NOT NULL,
    `description` varchar(2048) DEFAULT NULL,
    `managementIp` varchar(255) DEFAULT NULL,
    `architecture` varchar(32) DEFAULT NULL,
    `serialNumber` varchar(255) DEFAULT NULL,
    `manufacturer` varchar(255) DEFAULT NULL,
    `model` varchar(255) DEFAULT NULL,
    `oobManagementType` varchar(32) DEFAULT NULL,
    `oobAddress` varchar(255) DEFAULT NULL,
    `oobPort` int(10) unsigned DEFAULT NULL,
    `oobUsername` varchar(255) DEFAULT NULL,
    `oobPassword` varchar(255) DEFAULT NULL,
    `state` varchar(32) NOT NULL DEFAULT 'Enabled',
    `status` varchar(32) NOT NULL DEFAULT 'Unknown',
    `powerStatus` varchar(32) NOT NULL DEFAULT 'PowerUnknown',
    `createDate` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00',
    `lastOpDate` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00' ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`uuid`),
    KEY `idxPhysicalServerVOManagementIp` (`managementIp`),
    KEY `idxPhysicalServerVOSerialNumber` (`serialNumber`),
    KEY `idxPhysicalServerVOOobAddress` (`oobAddress`),
    CONSTRAINT `fkPhysicalServerVOZoneEO` FOREIGN KEY (`zoneUuid`)
        REFERENCES `ZoneEO` (`uuid`) ON DELETE RESTRICT,
    CONSTRAINT `fkPhysicalServerVOServerPoolVO` FOREIGN KEY (`serverPoolUuid`)
        REFERENCES `ServerPoolVO` (`uuid`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- ============================================================
-- PhysicalServerRoleVO: 角色映射引用表
-- v2.0: 新增 clusterUuid, sourceUuid, roleStatus 列 (基于总架构师裁决 1.1/1.5)
-- v2.0: FK 引用 PhysicalServerVO (不是 PhysicalServerAO)
-- ============================================================
CREATE TABLE IF NOT EXISTS `zstack`.`PhysicalServerRoleVO` (
    `id` bigint unsigned NOT NULL AUTO_INCREMENT,
    `serverUuid` varchar(32) NOT NULL,
    `roleType` varchar(32) NOT NULL,
    `roleUuid` varchar(32) NOT NULL,
    `clusterUuid` varchar(32) DEFAULT NULL,
    `sourceUuid` varchar(32) DEFAULT NULL,
    `roleStatus` varchar(64) DEFAULT NULL,
    `syncStatus` varchar(32) NOT NULL DEFAULT 'InSync',
    `createDate` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00',
    `lastSyncTime` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00',
    PRIMARY KEY (`id`),
    UNIQUE KEY `ukPhysicalServerRoleVOServerRole` (`serverUuid`, `roleType`),
    UNIQUE KEY `ukPhysicalServerRoleVORoleUuid` (`roleUuid`),
    CONSTRAINT `fkPhysicalServerRoleVOPhysicalServerVO` FOREIGN KEY (`serverUuid`)
        REFERENCES `PhysicalServerVO` (`uuid`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- ============================================================
-- ServerHardwareInfoVO: 硬件发现信息 (1:1)
-- v2.0: 新增 bootMode, discoverySource 列 (基于总架构师裁决 1.4)
-- ============================================================
CREATE TABLE IF NOT EXISTS `zstack`.`ServerHardwareInfoVO` (
    `uuid` varchar(32) NOT NULL,
    `cpuSockets` int(10) unsigned DEFAULT NULL,
    `cpuCoresPerSocket` int(10) unsigned DEFAULT NULL,
    `cpuThreadsPerCore` int(10) unsigned DEFAULT NULL,
    `cpuModel` varchar(255) DEFAULT NULL,
    `totalMemoryBytes` bigint unsigned DEFAULT NULL,
    `memorySlots` int(10) unsigned DEFAULT NULL,
    `diskCount` int(10) unsigned DEFAULT NULL,
    `totalDiskBytes` bigint unsigned DEFAULT NULL,
    `nicCount` int(10) unsigned DEFAULT NULL,
    `biosVersion` varchar(255) DEFAULT NULL,
    `bmcVersion` varchar(255) DEFAULT NULL,
    `bootMode` varchar(32) DEFAULT NULL,
    `discoverySource` varchar(32) DEFAULT NULL,
    `discoveredDate` timestamp NULL DEFAULT NULL,
    `lastUpdatedDate` timestamp NULL DEFAULT NULL,
    PRIMARY KEY (`uuid`),
    CONSTRAINT `fkServerHardwareInfoVOPhysicalServerVO` FOREIGN KEY (`uuid`)
        REFERENCES `PhysicalServerVO` (`uuid`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- ============================================================
-- ServerHardwareDetailVO: 1:N 硬件详情子表 (v2.0 新增)
-- 基于总架构师裁决 1.4: 两级硬件信息结构
-- ============================================================
CREATE TABLE IF NOT EXISTS `zstack`.`ServerHardwareDetailVO` (
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

-- ============================================================
-- ServerCapacityVO: 统一容量账本 (1:1 PhysicalServerVO)
-- v2.0 全面重设计 (基于总架构师裁决 1.6):
--   移除: cpuOverprovisioningRatio, memoryOverprovisioningRatio
--   新增: totalCpu, totalMemory (预计算持久化)
--   新增: availablePhysicalMemory, cpuNum, cpuSockets, cpuCoreNum
--   新增: exclusiveRoleUuid, schedulingMode
-- ============================================================
CREATE TABLE IF NOT EXISTS `zstack`.`ServerCapacityVO` (
    `uuid` varchar(32) NOT NULL,
    `totalPhysicalCpu` bigint NOT NULL DEFAULT 0,
    `totalPhysicalMemory` bigint NOT NULL DEFAULT 0,
    `totalCpu` bigint NOT NULL DEFAULT 0,
    `totalMemory` bigint NOT NULL DEFAULT 0,
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
```

---

## 8. 验证清单

### 8.1 模式合规验证

| 检查项 | 参照文件 | 状态 |
|--------|---------|------|
| AO 使用 @MappedSuperclass | `HostAO.java` | OK |
| VO 使用 @Entity @Table(name=...) @BaseResource (无 EO) | `UserGroupVO.java` 简单模式 | OK |
| VO 表名明确为 PhysicalServerVO (不是 AO) | 基于 Allocator 专家评审 P0 | OK (v2.0 修正) |
| FK 使用 @ForeignKey 注解 (非 JPA @FK) | `HostAO.java:zoneUuid` | OK |
| Inventory 有 valueOf(VO) 和 valueOf(Collection) | `HostInventory.java` | OK |
| Query 消息使用 @AutoQuery | `APIQueryHostMsg.java` | OK |
| Create 消息继承 APICreateMessage | `APICreateDiskOfferingMsg.java` | OK |
| Delete 消息继承 APIDeleteMessage | `APIDeleteHostMsg.java` | OK |
| 状态枚举有 nextState(event) | `HostState.java` | OK |
| PreMaintenance 状态及 preMaintain/maintain 事件 | 基于 KVM 专家评审 P0 | OK (v2.0 新增) |
| PhysicalServerPowerStatus 三维状态 | 基于总架构师裁决 1.5 | OK (v2.0 新增) |
| @PreUpdate 清空 lastOpDate | `HostAO.java` | OK |
| Spring XML 使用 zstack:extension | `Kvm.xml` | OK |
| DB 迁移使用 Flyway V* 命名 | `V5.5.6__schema.sql` | OK |
| ServerCapacityVO 悲观锁扣减 (两层模式) | `HostCapacityUpdater.java` | OK (v2.0 修正) |
| @Transactional / @DeadlockAutoRestart 分离 | 基于 Allocator 专家评审 P0 | OK (v2.0 修正) |
| ServerCapacityVO 无 ratio 字段/无计算 getter | 基于总架构师裁决 1.6 | OK (v2.0 修正) |
| totalCpu/totalMemory 预计算持久化 | 基于总架构师裁决 1.6 | OK (v2.0 新增) |
| AllocateServerMsg 核心字段 + extraData Map | 基于总架构师裁决 1.2 | OK (v2.0 修正) |
| requiredClusterUuids 为 List (非单个) | 基于总架构师裁决 1.1 | OK (v2.0 修正) |
| SchedulingMode 枚举 (替代 isExclusive()) | 基于总架构师裁决 1.3 | OK (v2.0 新增) |
| ServerHardwareDetailVO (1:N 详情子表) | 基于总架构师裁决 1.4 | OK (v2.0 新增) |
| PhysicalServerRoleVO 含 clusterUuid/sourceUuid/roleStatus | 基于总架构师裁决 1.1/1.5 | OK (v2.0 新增) |
| ServerCapacityOverProvisioningManager 接口 | 基于总架构师裁决 1.6 | OK (v2.0 新增) |
| ServerAllocatorCompatibilityBridge 接口 | 基于总架构师裁决 1.7 | OK (v2.0 新增) |
| ServerAllocatorFilterExtensionPoint 接口 | 基于 Allocator 专家建议 | OK (v2.0 新增) |
| ServerReservedCapacityExtensionPoint 接口 | 基于 Allocator 专家建议 | OK (v2.0 新增) |
| RecalculateServerCapacityMsg 内部消息 | 基于 Allocator 专家建议 | OK (v2.0 新增) |
| PhysicalServerGlobalConfig 全局配置 | 基于 Allocator 专家建议 | OK (v2.0 新增) |
| APIRegisterPhysicalServerMsg OOB 字段 required=false | 基于 Container 专家评审 P0 | OK (v2.0 修正) |
| Container 角色关联使用 NativeHostSyncedExtensionPoint | 基于 Container 专家评审 P0 | OK (v2.0 修正) |
| AllocatorFlow 责任链模式 | `HostAllocatorFilterExtensionPoint.java` | OK |

### 8.2 兼容性验证

| 检查项 | 期望结果 |
|--------|---------|
| 现有 HostVO 继承链是否修改 | 否，PhysicalServerVO 独立 |
| 现有 HostCapacityVO 是否修改 | 否，Phase 3 通过兼容层桥接 |
| 现有 API (AddKVMHostMsg 等) 是否修改 | 否，100% 兼容 |
| 现有 Cluster 体系是否修改 | 否，ServerPool 并存 |
| 现有 AllocateHostMsg 是否修改 | 否，Phase 3 用 CompatibilityBridge 拦截 |
| Baremetal V1/V2 模块是否修改 | Phase 1 不修改，仅定义接口 |
| Container 模块是否修改 | Phase 1 不修改，仅定义扩展点接口 |

### 8.3 新文件检查 (v2.0 新增)

| 新文件 | 类型 | 来源 |
|--------|------|------|
| SchedulingMode.java | 枚举 | 总架构师裁决 1.3 |
| PhysicalServerPowerStatus.java | 枚举 | 总架构师裁决 1.5 |
| HardwareDetailType.java | 枚举 | 总架构师裁决 1.4 |
| ServerHardwareDetailVO.java | Entity | 总架构师裁决 1.4 |
| ServerHardwareDetailInventory.java | DTO | 总架构师裁决 1.4 |
| ServerCapacityOverProvisioningManager.java | 接口 | 总架构师裁决 1.6 |
| ServerCapacityUpdaterRunnable.java | 接口 | Allocator 专家建议 |
| ServerAllocatorFilterExtensionPoint.java | 扩展点 | Allocator 专家建议 |
| ServerReservedCapacityExtensionPoint.java | 扩展点 | Allocator 专家建议 |
| ServerAllocatorCompatibilityBridge.java | 接口 | 总架构师裁决 1.7 |
| RecalculateServerCapacityMsg.java | 消息 | Allocator 专家建议 |
| PhysicalServerGlobalConfig.java | 配置 | Allocator 专家建议 |

---

## 9. Phase 1 实施步骤

1. 在 `header/` 下创建 `org.zstack.header.server` 包
2. 按顺序创建枚举: CapacityState, ServerPoolState, PhysicalServerState, PhysicalServerStateEvent, PhysicalServerStatus, PhysicalServerStatusEvent, PhysicalServerPowerStatus (v2.0), SchedulingMode (v2.0), HardwareDetailType (v2.0), ServerRoleType
3. 创建 VO: ServerPoolVO -> PhysicalServerAO -> PhysicalServerVO -> PhysicalServerRoleVO -> ServerHardwareInfoVO -> ServerHardwareDetailVO (v2.0) -> ServerCapacityVO
4. 创建 Inventory: ServerPoolInventory, PhysicalServerInventory, PhysicalServerRoleInventory, ServerHardwareInfoInventory, ServerHardwareDetailInventory (v2.0), ServerCapacityInventory
5. 创建分配子系统: AllocateServerMsg, AllocateServerReply, ServerAllocatorSpec, ServerAllocatorFlow, ServerSortorFlow
6. 创建 v2.0 新增接口: ServerCapacityUpdaterRunnable, ServerCapacityOverProvisioningManager, ServerAllocatorFilterExtensionPoint, ServerReservedCapacityExtensionPoint, ServerAllocatorCompatibilityBridge, RecalculateServerCapacityMsg
7. 创建 API 消息: APIRegisterPhysicalServerMsg/Event, APIQueryPhysicalServerMsg/Reply, APIUpdatePhysicalServerMsg/Event, APIDeletePhysicalServerMsg/Event, ServerPool API 消息
8. 创建 SPI: PhysicalServerRoleProvider, PhysicalServerMessage, PhysicalServerConstant, PhysicalServerGlobalConfig (v2.0)
9. 添加 DB 迁移脚本到 `conf/db/upgrade/V5.5.7__schema.sql`
10. 添加 Spring XML 到 `conf/springConfigXml/PhysicalServer.xml`
11. 添加 GlobalConfig 到 `conf/globalConfig/physicalServer.xml`
12. 编译验证: `mvn clean compile -pl header`
13. Phase 2: 实现 PhysicalServerManagerImpl + ServerAllocatorManagerImpl + ServerCapacityOverProvisioningManagerImpl + ServerCapacityUpdater + 角色关联钩子 + 兼容层完整实现 + 容量对账定时任务
14. Phase 3: 灰度切换 -- 特性开关 physicalServer.allocator.enabled = true, AllocateHostMsg -> AllocateServerMsg 的透明切换, 数据迁移工具, 灰度策略 (先 BM2 -> BM1 -> KVM)

---

**批准状态**: v2.0 终稿。此设计文档基于 5 位领域专家评审 + 总架构师 7 大裁决，为 Phase 1 实施的权威设计依据。
