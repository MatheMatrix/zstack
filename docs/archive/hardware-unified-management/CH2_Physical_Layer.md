# 第二章：数据模型设计 (Data Model Design)

## 2.1 核心 VO 设计

### 2.1.1 PhysicalServerVO (物理服务器)
物理实体的唯一抽象，不包含任何业务属性。

```java
@Entity
@Table(name = "PhysicalServerVO")
@AutoDeleteTag
@BaseResource
public class PhysicalServerVO extends PhysicalServerAO {
    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "uuid")
    @NoView
    private ServerCapacityVO capacity;  // 1:1 关联容量

    @OneToMany(fetch = FetchType.EAGER)
    @JoinColumn(name = "serverUuid", insertable = false, updatable = false)
    @NoView
    private Set<PhysicalServerRoleVO> roles = new HashSet<>(); // 1:N 关联角色
}
```

### 2.1.2 ServerCapacityVO (统一容量)
**关键变更**：这是系统中唯一的容量账本。

```java
@Entity
@Table(name = "ServerCapacityVO")
public class ServerCapacityVO {
    @Id
    private String uuid; // 与 PhysicalServerVO 共享 UUID

    // 物理规格
    @Column private long totalPhysicalCpu;
    @Column private long totalPhysicalMemory;

    // 逻辑规格 (含超分)
    @Column private long totalCpu;
    @Column private long totalMemory;

    // 剩余可用
    @Column private long availableCpu;
    @Column private long availableMemory;

    // 系统预留 (Tax)
    @Column private long reservedMemory;
}
```

### 2.1.3 ServerPoolVO (物理池)
定义物理边界（如机房、机架）和网络拓扑边界。

```java
@Entity
@Table(name = "ServerPoolVO")
public class ServerPoolVO extends ResourceVO {
    @Column
    private String physicalLocation; // 物理位置标识
    @Column
    private String networkTopology;  // L2 网络拓扑标识
    @Column
    @Enumerated(EnumType.STRING)
    private ServerPoolState state;
}
```

---

## 2.2 数据库迁移脚本 (Schema)

```sql
-- 1. ServerPoolVO
CREATE TABLE IF NOT EXISTS `ServerPoolVO` (
    `uuid` VARCHAR(32) NOT NULL PRIMARY KEY,
    `zoneUuid` VARCHAR(32) NOT NULL,
    `name` VARCHAR(255) NOT NULL,
    `description` VARCHAR(2048) DEFAULT NULL,
    `state` VARCHAR(32) NOT NULL DEFAULT 'Enabled',
    `physicalLocation` VARCHAR(255) DEFAULT NULL,
    `networkTopology` VARCHAR(255) DEFAULT NULL,
    `createDate` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `lastOpDate` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT `fkServerPoolVOZoneEO` FOREIGN KEY (`zoneUuid`) REFERENCES `ZoneEO` (`uuid`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. PhysicalServerVO
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

-- 3. ServerCapacityVO (取代 HostCapacityVO)
CREATE TABLE IF NOT EXISTS `ServerCapacityVO` (
    `uuid` VARCHAR(32) NOT NULL PRIMARY KEY,
    `totalCpu` BIGINT NOT NULL DEFAULT 0,
    `availableCpu` BIGINT NOT NULL DEFAULT 0,
    `totalMemory` BIGINT NOT NULL DEFAULT 0,
    `availableMemory` BIGINT NOT NULL DEFAULT 0,
    `totalPhysicalMemory` BIGINT NOT NULL DEFAULT 0,
    `availablePhysicalMemory` BIGINT NOT NULL DEFAULT 0,
    `totalDisk` BIGINT NOT NULL DEFAULT 0,
    `availableDisk` BIGINT NOT NULL DEFAULT 0,
    `capacityState` VARCHAR(32) NOT NULL DEFAULT 'Normal',
    `cpuOverprovisioningRatio` DOUBLE NOT NULL DEFAULT 10.0,
    `memoryOverprovisioningRatio` DOUBLE NOT NULL DEFAULT 1.0,
    `reservedMemory` BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT `fkServerCapacityVOPhysicalServerVO` FOREIGN KEY (`uuid`)
        REFERENCES `PhysicalServerVO` (`uuid`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. PhysicalServerRoleVO (多角色映射)
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
```
