# Unified Hardware Management - Header Plan (v3.2)

**版本**: v3.2 (架构全貌版)
**更新时间**: 2026-02-02
**变更说明**: 完整恢复架构层次图，整合物理/逻辑解耦、统一分配、深度监控及所有代码定义。

---

## 1. 核心架构原则

### 1.1 统一架构主导 (Unified-Led)
统一架构 Lead 四个模块（KVM, Baremetal V1/V2, Container），模块必须适配统一架构。

### 1.2 物理与逻辑完全解耦 (Physical-Logical Decoupling)
- **ServerPool (物理池)**: 承载位置、机架、L2 连通性边界等物理属性。它是 PhysicalServer 的唯一容器。
- **Cluster (逻辑集群)**: 纯粹的调度策略边界。通过 **Role** 引用物理资源，不直接拥有物理机。

### 1.3 硬件主权上移 (Hardware Elevation)
所有硬件原始指标（磁盘 S.M.A.R.T、固件版本等）由 `PhysicalServer` 层统一采集（带外 + zwatch-agent）并下沉给各业务角色。

### 1.4 禁止特化接口 (No Specialization)
严禁设计针对特定角色的特化接口。所有差异通过 `RoleAdapter` 内部逻辑处理。

### 1.5 Git Blame 保护 (History Preservation)
**策略**: Wrap, don't delete. 保留 compute 模块核心文件，通过包装器（Wrapper）确保历史积淀 100% 可追溯。

---

## 2. 架构层次设计

```
┌─────────────────────────────────────────────────────────────────┐
│                    Unified Hardware Management                   │
├─────────────────────────────────────────────────────────────────┤
│  ┌──────────────────────────────────────────────────────────┐   │
│  │            Physical Infrastructure Layer (物理层)         │   │
│  │   ServerPoolVO (物理边界) / PhysicalServerVO (唯一真相)    │   │
│  │   ServerCapacityVO (统一账本) / ServerHardwareInfoVO       │   │
│  │   管理物理位置、二层网络接入、机架位置等物理属性           │   │
│  └──────────────────────────────────────────────────────────┘   │
│                              │                                   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │             Logical Orchestration Layer (逻辑层)          │   │
│  │   Cluster (逻辑集群) / RoleAdapter / 统一能力接口          │   │
│  │   通过 Role 引用物理资源，定义软件调度策略                 │   │
│  └──────────────────────────────────────────────────────────┘   │
│                              │                                   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │              Role Resource Layer (现有系统)               │   │
│  │   KVMHostVO / NativeHostVO / BaremetalChassisVO          │   │
│  │   通过兼容层与 Server 层交互，保留业务逻辑与 Blame         │   │
│  └──────────────────────────────────────────────────────────┘   │
│                              │                                   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │              Consumer Layer (资源消费者)                  │   │
│  │   VmInstanceVO / PodVO / BaremetalInstanceVO             │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

---

## 3. 领域模型设计 (VOs)

### 3.1 PhysicalServerVO & AO
```java
@MappedSuperclass
public class PhysicalServerAO extends ResourceVO {
    @Column @ForeignKey(parentEntityClass = ZoneEO.class)
    private String zoneUuid;
    @Column @ForeignKey(parentEntityClass = ServerPoolVO.class)
    private String poolUuid;
    @Column @Index
    private String name;
    @Column
    private String managementIp;
    @Column
    private String architecture; // x86_64, aarch64
    @Column @Index
    private String serialNumber; // 硬件主键
    @Column
    private String manufacturer;
    @Column
    private String model;
    @Column @Enumerated(EnumType.STRING)
    private ServerState state;   // Enabled, Disabled, Maintenance
    @Column @Enumerated(EnumType.STRING)
    private ServerStatus status; // Connecting, Connected, Disconnected

    // 带外管理凭据
    @Column @Enumerated(EnumType.STRING)
    private OobManagementType oobManagementType;
    @Column private String oobAddress;
    @Column private Integer oobPort;
    @Column private String oobUsername;
    @Column @Password private String oobPassword;
}
```

### 3.2 ServerCapacityVO (唯一容量账本)
```java
@Entity @Table(name = "ServerCapacityVO")
public class ServerCapacityVO {
    @Id private String uuid; // 与 PhysicalServerVO 共享

    @Column private long totalPhysicalCpu;
    @Column private long totalPhysicalMemory;
    @Column private double cpuOverprovisioningRatio = 10.0;
    @Column private double memoryOverprovisioningRatio = 1.0;

    @Column private long availableCpu;
    @Column private long availableMemory;
    @Column private long reservedMemory; // 系统预留 (OS, Ceph Agents)

    @Column private long totalDisk;
    @Column private long availableDisk;

    @Column @Enumerated(EnumType.STRING)
    private CapacityState capacityState;

    public long getTotalCpu() { return (long)(totalPhysicalCpu * cpuOverprovisioningRatio); }
    public long getTotalMemory() { return (long)(totalPhysicalMemory * memoryOverprovisioningRatio); }
}
```

---

## 4. 精细化资源上线流 (Lifecycle)

统一采用 **“Discovery-First”** 流程：

1.  **录入 (Add)**: 调用 `APIAddPhysicalServerMsg` (仅需 OOB 地址/凭据)。
2.  **发现 (Probe)**: 调用 `HardwareDiscoverable.triggerDiscovery()` 获取 SN 和规格，创建 `PhysicalServerVO`。
3.  **探测 (Detect)**: 一旦探测到 OS 运行环境，进入带内管理阶段。
4.  **监控就绪 (zwatch)**: **物理服务器层自动部署 zwatch-agent**。
5.  **深层硬件沉淀 (Elevation)**: zwatch-agent 将详尽指标（磁盘健康、网卡固件等）推送到 `PhysicalServer` 层。
6.  **角色绑定 (Bind)**: 硬件信息完整后，才允许绑定逻辑角色（KVM_HOST 等）。

---

## 5. 统一监控架构 (ServerMonitorService)

### 5.1 双轨监控模型
- **带外 (OOB)**: 由 `OobMonitor` 负责，监控电源、风扇、温感、PSU 健康。
- **带内 (IB)**: 由 **zwatch-agent** 负责，监控磁盘损坏、网卡丢包、CPU 降频。**监控主权收归物理层。**

### 5.2 状态判定逻辑
- **Connected**: OOB 连通 且 zwatch 心跳正常。
- **Disconnected**: OOB 失联（硬件关机）或 zwatch 异常（OS 崩溃）。

---

## 6. 统一分配逻辑 (Allocation)

### 6.1 ServerCapacityUpdater (悲观锁扣减)
```java
public class ServerCapacityUpdater {
    @Transactional @DeadlockAutoRestart
    public boolean reserve(long cpu, long memory, String roleType) {
        ServerCapacityVO cap = dbf.getEntityManager().find(
            ServerCapacityVO.class, serverUuid, LockModeType.PESSIMISTIC_WRITE
        );

        // 逻辑下沉：独占角色判定 (BARE_METAL)
        if (ServerRoleTypes.isExclusiveRole(roleType)) {
            cap.setAvailableCpu(0);
            cap.setAvailableMemory(0);
            cap.setAvailableDisk(0);
        } else {
            // 共享分配逻辑 (KVM_HOST)
            cap.setAvailableCpu(cap.getAvailableCpu() - cpu);
            cap.setAvailableMemory(cap.getAvailableMemory() - memory);
        }
        dbf.getEntityManager().merge(cap);
        return true;
    }
}
```

---

## 7. 容量重计算与自愈 (Recalculate)

采用 **“税收模式 (Tax Collector)”**:
1.  **征收业务税**: 调用 `RoleAdapter.getCapacityConsumption()`。
2.  **征收系统税**: 调用 `ServerReservedCapacityExtensionPoint` (统计 OS/Ceph Agent 预留)。
3.  **最终核销**: `Available = Total - ∑(业务税) - ∑(系统税)`。

---

## 8. 数据库迁移脚本 (V5.5.6__schema.sql)

```sql
CREATE TABLE IF NOT EXISTS `ServerPoolVO` (
    `uuid` VARCHAR(32) NOT NULL PRIMARY KEY,
    `physicalLocation` VARCHAR(255) DEFAULT NULL,
    `networkTopology` VARCHAR(255) DEFAULT NULL,
    `state` VARCHAR(32) NOT NULL DEFAULT 'Enabled'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `ServerCapacityVO` (
    `uuid` VARCHAR(32) NOT NULL PRIMARY KEY,
    `availableCpu` BIGINT NOT NULL DEFAULT 0,
    `availableMemory` BIGINT NOT NULL DEFAULT 0,
    `reservedMemory` BIGINT NOT NULL DEFAULT 0,
    `availableDisk` BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT `fkServerCapacityVOPhysicalServerVO` FOREIGN KEY (`uuid`)
        REFERENCES `PhysicalServerVO` (`uuid`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## 9. 迁移路线图 (Phase 1-3)

1.  **Phase 1 (Shadow)**: 引入 `server` 模块，定义基础 VO 和上线流。
2.  **Phase 2 (Migration)**: 实现 Host -> PhysicalServer 数据映射，启动双写验证。
3.  **Phase 3 (Wrap)**: **核心收割**。将 `AllocateHostMsg` 路由至新引擎，重构 `HostCapacityUpdater` 为包装器，保留 Blame。

---
**批准状态**: 已合并 KVM, Baremetal, Container 及分配器专家所有意见，架构图已恢复。
