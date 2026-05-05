# Unified Hardware Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Introduce PhysicalServerVO as a unified abstraction layer for 4 hardware role types (KVM Host, BM1, BM2, Container/K8s), with unified capacity management, allocation engine, provision network, and backward-compatible migration.

**Architecture:** Three-layer model — Physical Layer (PhysicalServerVO) → Role Layer (HostVO/ChassisVO via SPI) → Consumer Layer. PhysicalServerCapacityVO is the single source of truth; HostCapacityVO becomes a read-only MySQL VIEW. Two-phase thin adapter (CompatibilityBridge) bridges legacy AllocateHostMsg to new ServerAllocatorChain.

**Tech Stack:** Java 8, Hibernate 5.3.26, Spring 5.2.25, Maven, Flyway, ZStack plugin architecture

---

## Dependency Graph

```
Phase 1: Physical Server Model (PRD-1)     Phase 2: Provision Network (PRD-4)
         │                                           │
         ▼                                           │
Phase 3: Role SPI & Adapters (PRD-2) ◄───────────────┘
         │
         ▼
Phase 4: Capacity Management (PRD-3)
         │
         ▼
Phase 5: Compat Bridge & Migration (PRD-5)
```

Phase 1 and Phase 2 are independent and can be developed in parallel.

## Module Layout

```
header/src/main/java/org/zstack/header/server/
├── PhysicalServerAO.java              # MappedSuperclass
├── PhysicalServerVO.java              # Entity
├── PhysicalServerVO_.java             # Metamodel (auto)
├── PhysicalServerInventory.java       # Inventory
├── PhysicalServerState.java           # Enum: Enabled/Disabled/Maintenance
├── PhysicalServerStatus.java          # Enum: Connecting/Connected/Disconnected
├── PhysicalServerPowerStatus.java     # Enum: PowerOn/PowerOff/Unknown
├── PhysicalServerConstant.java        # Constants
├── PhysicalServerRoleVO.java          # Role mapping
├── PhysicalServerRoleVO_.java
├── PhysicalServerRoleInventory.java
├── ServerRoleType.java                # Enum: KVM_HOST/BAREMETAL_V1/BAREMETAL_V2/CONTAINER_HOST
├── SchedulingMode.java                # Enum: INTERNAL_SHARED/INTERNAL_EXCLUSIVE/EXTERNAL_READONLY
├── PhysicalServerHardwareInfoVO.java  # Hardware summary
├── PhysicalServerHardwareDetailVO.java # Hardware detail
├── HardwareDetailType.java            # Enum: CPU/MEMORY/DISK/NIC/GPU
├── ServerPoolVO.java                  # Server pool
├── ServerPoolVO_.java
├── ServerPoolInventory.java
├── ServerPoolState.java               # Enum: Enabled/Disabled
├── ClusterServerPoolRefVO.java        # Cluster:Pool many-to-one ref
├── PhysicalServerCapacityVO.java      # Capacity (真表)
├── PhysicalServerCapacityVO_.java
├── PhysicalServerRoleProvider.java    # SPI interface
├── RoleMatchContext.java              # SPI context for auto-association
├── CapacityUsage.java                 # SPI return type
├── PowerManageable.java               # Power management interface
├── HardwareDiscoverable.java          # Hardware discovery interface
├── PhysicalServerProvisionNetworkVO.java
├── PhysicalServerProvisionNetworkInventory.java
├── PhysicalServerProvisionNetworkClusterRefVO.java
├── ProvisionNetworkType.java          # Enum: STANDALONE_PXE/GATEWAY_PXE
│
│   # API Messages
├── APICreatePhysicalServerMsg.java
├── APICreatePhysicalServerEvent.java
├── APIDeletePhysicalServerMsg.java
├── APIDeletePhysicalServerEvent.java
├── APIUpdatePhysicalServerMsg.java
├── APIUpdatePhysicalServerEvent.java
├── APIQueryPhysicalServerMsg.java
├── APIQueryPhysicalServerReply.java
├── APIChangePhysicalServerStateMsg.java
├── APIChangePhysicalServerStateEvent.java
├── APIScanPhysicalServersMsg.java     # LongJob: IPMI scan
├── APIScanPhysicalServersEvent.java
├── APIAttachPhysicalServerRoleMsg.java  # FR-035
├── APIAttachPhysicalServerRoleEvent.java
├── APIDetachPhysicalServerRoleMsg.java  # FR-036
├── APIDetachPhysicalServerRoleEvent.java
├── APIPowerOnPhysicalServerMsg.java
├── APIPowerOffPhysicalServerMsg.java
├── APIPowerResetPhysicalServerMsg.java
├── APIDiscoverPhysicalServerHardwareMsg.java
│
│   # ServerPool API Messages
├── APICreateServerPoolMsg.java
├── APICreateServerPoolEvent.java
├── APIDeleteServerPoolMsg.java
├── APIDeleteServerPoolEvent.java
├── APIUpdateServerPoolMsg.java
├── APIUpdateServerPoolEvent.java
├── APIQueryServerPoolMsg.java
├── APIQueryServerPoolReply.java
├── APIAttachServerPoolToClusterMsg.java
├── APIDetachServerPoolFromClusterMsg.java
│
│   # ProvisionNetwork API Messages
├── APICreateProvisionNetworkMsg.java
├── APICreateProvisionNetworkEvent.java
├── APIDeleteProvisionNetworkMsg.java
├── APIDeleteProvisionNetworkEvent.java
├── APIQueryProvisionNetworkMsg.java
├── APIQueryProvisionNetworkReply.java
├── APIAttachProvisionNetworkToClusterMsg.java
├── APIDetachProvisionNetworkFromClusterMsg.java
│
│   # Allocator Messages
├── AllocateServerMsg.java
├── AllocateServerReply.java
├── AllocateServerSpec.java
│
│   # Extension Points
├── ServerAllocatorFilterExtensionPoint.java
└── ServerReservedCapacityExtensionPoint.java

plugin/physicalServer/
├── pom.xml
└── src/main/java/org/zstack/server/
    ├── PhysicalServerManagerImpl.java      # Main service
    ├── PhysicalServerFactory.java
    ├── PhysicalServerApiInterceptor.java   # API validation
    ├── PhysicalServerCascadeExtension.java # Cascade cleanup
    ├── PhysicalServerTracker.java          # OOB heartbeat
    ├── ServerPoolManagerImpl.java
    ├── ServerPoolCascadeExtension.java
    ├── PhysicalServerCapacityUpdater.java  # Capacity write entry
    ├── OverProvisioningManagerImpl.java    # Overprovision ratio
    ├── ServerAllocatorChainImpl.java       # Allocator chain
    ├── flow/                              # Allocator flows
    │   ├── ZoneFilterFlow.java
    │   ├── ClusterFilterFlow.java
    │   ├── PoolFilterFlow.java
    │   ├── RoleTypeFilterFlow.java
    │   ├── StatusFilterFlow.java
    │   ├── CapacityFilterFlow.java
    │   └── SortFilterFlow.java
    ├── CompatibilityBridge.java           # Legacy bridge
    ├── ScanPhysicalServersLongJob.java    # IPMI scan LongJob
    ├── ProvisionNetworkManagerImpl.java
    └── ProvisionNetworkCascadeExtension.java

plugin/physicalServer/src/main/resources/
└── META-INF/spring/physicalServer.xml     # Spring config

# KVM Adapter (modify existing)
plugin/kvm/src/main/java/org/zstack/kvm/
└── KvmPhysicalServerRoleProvider.java     # NEW: KVM role adapter

# Container Adapter (modify existing, if container module in open-source)
# BM2 Adapter (in premium, separate plan)
```

## Reference Files (read before implementing)

| File | Purpose |
|------|---------|
| `header/.../host/HostAO.java` | VO pattern reference (MappedSuperclass + Entity) |
| `header/.../host/HostVO.java` | Entity inheritance pattern |
| `header/.../allocator/HostCapacityVO.java` | Current capacity VO (will become VIEW) |
| `header/.../allocator/AllocateHostMsg.java` | Legacy allocate message (Bridge source) |
| `header/.../allocator/AllocateHostSpec.java` | Allocator spec (add candidateHostUuids) |
| `plugin/kvm/KVMHostFactory.java` | Plugin factory pattern reference |
| `header/.../host/HostInventory.java` | Inventory pattern reference |
| `core/.../cascade/CascadeExtensionPoint.java` | Cascade framework reference |

---

## Phase 1: Physical Server Model (PRD-1)

> **Priority:** P0 Must Have | **Dependencies:** None | **Estimated tasks:** 18

### Task 1.1: Maven Module Setup

**Files:**
- Create: `plugin/physicalServer/pom.xml`
- Modify: `plugin/pom.xml` (add module)

- [ ] **Step 1:** Create `plugin/physicalServer/pom.xml` with dependencies on `header`, `utils`, `core`

```xml
<project>
    <parent>
        <groupId>org.zstack</groupId>
        <artifactId>plugin</artifactId>
        <version>5.5.0</version>
        <relativePath>../pom.xml</relativePath>
    </parent>
    <artifactId>physicalServer</artifactId>
    <packaging>jar</packaging>
    <dependencies>
        <dependency>
            <groupId>org.zstack</groupId>
            <artifactId>header</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>org.zstack</groupId>
            <artifactId>core</artifactId>
            <version>${project.version}</version>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2:** Add `<module>physicalServer</module>` to `plugin/pom.xml`
- [ ] **Step 3:** Create directory structure: `plugin/physicalServer/src/main/java/org/zstack/server/` and `src/main/resources/META-INF/spring/`
- [ ] **Step 4:** Create Spring config `plugin/physicalServer/src/main/resources/META-INF/spring/physicalServer.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:context="http://www.springframework.org/schema/context"
       xsi:schemaLocation="http://www.springframework.org/schema/beans
       http://www.springframework.org/schema/beans/spring-beans.xsd
       http://www.springframework.org/schema/context
       http://www.springframework.org/schema/context/spring-context.xsd">
    <context:component-scan base-package="org.zstack.server"/>
</beans>
```

- [ ] **Step 5:** Verify build: `cd plugin/physicalServer && mvn compile`

- [ ] **Step 6:** Commit

```bash
git add plugin/physicalServer/ plugin/pom.xml
git commit -m "feat(server): add physicalServer plugin module skeleton"
```

### Task 1.2: Enums & Constants

**Files:**
- Create: `header/src/main/java/org/zstack/header/server/PhysicalServerState.java`
- Create: `header/src/main/java/org/zstack/header/server/PhysicalServerStatus.java`
- Create: `header/src/main/java/org/zstack/header/server/PhysicalServerPowerStatus.java`
- Create: `header/src/main/java/org/zstack/header/server/ServerRoleType.java`
- Create: `header/src/main/java/org/zstack/header/server/SchedulingMode.java`
- Create: `header/src/main/java/org/zstack/header/server/HardwareDetailType.java`
- Create: `header/src/main/java/org/zstack/header/server/ServerPoolState.java`
- Create: `header/src/main/java/org/zstack/header/server/ProvisionNetworkType.java`
- Create: `header/src/main/java/org/zstack/header/server/PhysicalServerConstant.java`

- [ ] **Step 1:** Create all enum classes

```java
// PhysicalServerState.java
package org.zstack.header.server;
public enum PhysicalServerState {
    Enabled, Disabled, Maintenance
}

// PhysicalServerStatus.java
public enum PhysicalServerStatus {
    Connecting, Connected, Disconnected
}

// PhysicalServerPowerStatus.java
public enum PhysicalServerPowerStatus {
    PowerOn, PowerOff, Unknown
}

// ServerRoleType.java
public enum ServerRoleType {
    KVM_HOST, BAREMETAL_V1, BAREMETAL_V2, CONTAINER_HOST
}

// SchedulingMode.java
public enum SchedulingMode {
    INTERNAL_SHARED, INTERNAL_EXCLUSIVE, EXTERNAL_READONLY
}

// HardwareDetailType.java
public enum HardwareDetailType {
    CPU, MEMORY, DISK, NIC, GPU
}

// ServerPoolState.java
public enum ServerPoolState {
    Enabled, Disabled
}

// ProvisionNetworkType.java
public enum ProvisionNetworkType {
    STANDALONE_PXE, GATEWAY_PXE
}
```

- [ ] **Step 2:** Create PhysicalServerConstant

```java
package org.zstack.header.server;
public interface PhysicalServerConstant {
    String SERVICE_ID = "physicalServer";
    String ACTION_CATEGORY = "physicalServer";
    String SERVER_POOL_ACTION_CATEGORY = "serverPool";
}
```

- [ ] **Step 3:** Verify compile: `mvn compile -pl header`
- [ ] **Step 4:** Commit

### Task 1.3: ServerPoolVO & Inventory

**Files:**
- Create: `header/.../server/ServerPoolVO.java`
- Create: `header/.../server/ServerPoolInventory.java`
- Create: `header/.../server/ClusterServerPoolRefVO.java`

- [ ] **Step 1:** Create ServerPoolVO extending ResourceVO

```java
package org.zstack.header.server;

@Entity
@Table(name = "ServerPoolVO")
@BaseResource
public class ServerPoolVO extends ResourceVO {
    @Column
    @ForeignKey(parentEntityClass = ZoneEO.class, onDeleteAction = ReferenceOption.RESTRICT)
    private String zoneUuid;

    @Column
    private String name;

    @Column
    private String physicalLocation;

    @Column
    private String networkTopology;

    @Column
    @Enumerated(EnumType.STRING)
    private ServerPoolState state;

    // getters/setters
}
```

- [ ] **Step 2:** Create ServerPoolInventory (follow HostInventory pattern with `@Inventory` annotation)
- [ ] **Step 3:** Create ClusterServerPoolRefVO

```java
@Entity
@Table(name = "ClusterServerPoolRefVO")
public class ClusterServerPoolRefVO {
    @Id
    @Column
    private long id;

    @Column
    @ForeignKey(parentEntityClass = ClusterEO.class, onDeleteAction = ReferenceOption.CASCADE)
    private String clusterUuid;  // UNIQUE constraint

    @Column
    @ForeignKey(parentEntityClass = ServerPoolVO.class, onDeleteAction = ReferenceOption.CASCADE)
    private String poolUuid;
}
```

- [ ] **Step 4:** Verify compile
- [ ] **Step 5:** Commit

### Task 1.4: PhysicalServerAO/VO & Inventory

**Files:**
- Create: `header/.../server/PhysicalServerAO.java`
- Create: `header/.../server/PhysicalServerVO.java`
- Create: `header/.../server/PhysicalServerInventory.java`

- [ ] **Step 1:** Create PhysicalServerAO (MappedSuperclass)

```java
package org.zstack.header.server;

@MappedSuperclass
public class PhysicalServerAO extends ResourceVO {
    @Column
    @ForeignKey(parentEntityClass = ZoneEO.class, onDeleteAction = ReferenceOption.RESTRICT)
    private String zoneUuid;

    @Column
    @ForeignKey(parentEntityClass = ServerPoolVO.class, onDeleteAction = ReferenceOption.RESTRICT)
    private String poolUuid;

    @Column
    private String name;

    @Column
    private String managementIp;

    @Column
    private String architecture;  // x86_64 / aarch64

    @Column
    private String serialNumber;  // nullable, UNIQUE(zoneUuid, serialNumber) at app layer

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

    // OOB fields
    @Column
    private String oobManagementType;  // IPMI / REDFISH

    @Column
    private String oobAddress;

    @Column
    private Integer oobPort;

    @Column
    private String oobUsername;

    @Column
    @Convert(converter = PasswordConverter.class)
    private String oobPassword;

    // getters/setters
}
```

- [ ] **Step 2:** Create PhysicalServerVO (Entity, no EO)

```java
@Entity
@Table(name = "PhysicalServerVO",
    uniqueConstraints = @UniqueConstraint(columnNames = {"zoneUuid", "serialNumber"}))
@BaseResource
public class PhysicalServerVO extends PhysicalServerAO {
    @OneToMany(fetch = FetchType.EAGER)
    @JoinColumn(name = "serverUuid", insertable = false, updatable = false)
    @Fetch(FetchMode.SUBSELECT)
    private Set<PhysicalServerRoleVO> roles;

    // getters/setters
}
```

- [ ] **Step 3:** Create PhysicalServerInventory with `@Inventory` annotation, include role list expansion
- [ ] **Step 4:** Verify compile
- [ ] **Step 5:** Commit

### Task 1.5: PhysicalServerRoleVO

**Files:**
- Create: `header/.../server/PhysicalServerRoleVO.java`
- Create: `header/.../server/PhysicalServerRoleInventory.java`

- [ ] **Step 1:** Create PhysicalServerRoleVO

```java
@Entity
@Table(name = "PhysicalServerRoleVO",
    uniqueConstraints = @UniqueConstraint(columnNames = {"serverUuid", "roleType"}))
public class PhysicalServerRoleVO extends ResourceVO {
    @Column
    private String serverUuid;  // no DB FK, app-layer ref to PhysicalServerVO

    @Column
    @Enumerated(EnumType.STRING)
    private ServerRoleType roleType;

    @Column
    private String roleUuid;  // polymorphic ref, no DB FK

    @Column
    @Enumerated(EnumType.STRING)
    private SchedulingMode schedulingMode;

    @Column
    private String roleStatus;  // Active / Stale

    // getters/setters
}
```

- [ ] **Step 2:** Create PhysicalServerRoleInventory
- [ ] **Step 3:** Verify compile
- [ ] **Step 4:** Commit

### Task 1.6: HardwareInfoVO & HardwareDetailVO

**Files:**
- Create: `header/.../server/PhysicalServerHardwareInfoVO.java`
- Create: `header/.../server/PhysicalServerHardwareDetailVO.java`

- [ ] **Step 1:** Create HardwareInfoVO (shares UUID with PhysicalServerVO)

```java
@Entity
@Table(name = "PhysicalServerHardwareInfoVO")
public class PhysicalServerHardwareInfoVO {
    @Id
    @Column
    @ForeignKey(parentEntityClass = PhysicalServerVO.class, onDeleteAction = ReferenceOption.CASCADE)
    private String uuid;

    @Column
    private String cpuModel;
    @Column
    private Integer cpuCores;
    @Column
    private Integer cpuSockets;
    @Column
    private Long totalMemory;
    @Column
    private Long totalDisk;
    @Column
    private Integer nicCount;
    @Column
    private Integer gpuCount;
}
```

- [ ] **Step 2:** Create HardwareDetailVO (1:N per server)

```java
@Entity
@Table(name = "PhysicalServerHardwareDetailVO")
public class PhysicalServerHardwareDetailVO {
    @Id
    @Column
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    @ForeignKey(parentEntityClass = PhysicalServerVO.class, onDeleteAction = ReferenceOption.CASCADE)
    private String serverUuid;

    @Column
    @Enumerated(EnumType.STRING)
    private HardwareDetailType type;

    @Column
    private String itemModel;
    @Column
    private String specification;
    @Column
    private String firmwareVersion;
    @Column
    private String healthStatus;
    @Column
    private String extraInfo;  // JSON, e.g. S.M.A.R.T for disks
}
```

- [ ] **Step 3:** Verify compile
- [ ] **Step 4:** Commit

### Task 1.7: ServerPool API Messages

**Files:**
- Create: `header/.../server/APICreateServerPoolMsg.java` + Event
- Create: `header/.../server/APIDeleteServerPoolMsg.java` + Event
- Create: `header/.../server/APIUpdateServerPoolMsg.java` + Event
- Create: `header/.../server/APIQueryServerPoolMsg.java` + Reply
- Create: `header/.../server/APIAttachServerPoolToClusterMsg.java` + Event
- Create: `header/.../server/APIDetachServerPoolFromClusterMsg.java` + Event

- [ ] **Step 1:** Create CRUD API messages following ZStack pattern (`@APIParam`, `@Action`, `@RestRequest`)

```java
@Action(category = PhysicalServerConstant.SERVER_POOL_ACTION_CATEGORY)
@RestRequest(method = HttpMethod.POST, path = "/server-pools",
    responseClass = APICreateServerPoolEvent.class)
public class APICreateServerPoolMsg extends APICreateMessage {
    @APIParam(maxLength = 255)
    private String name;

    @APIParam(resourceType = ZoneVO.class)
    private String zoneUuid;

    @APIParam(required = false, maxLength = 2048)
    private String physicalLocation;

    @APIParam(required = false, maxLength = 2048)
    private String networkTopology;
}
```

- [ ] **Step 2:** Create remaining ServerPool API messages (Delete, Update, Query, Attach/Detach Cluster)
- [ ] **Step 3:** Verify compile
- [ ] **Step 4:** Commit

### Task 1.8: PhysicalServer API Messages

**Files:**
- Create: `header/.../server/APICreatePhysicalServerMsg.java` + Event
- Create: `header/.../server/APIDeletePhysicalServerMsg.java` + Event
- Create: `header/.../server/APIUpdatePhysicalServerMsg.java` + Event
- Create: `header/.../server/APIQueryPhysicalServerMsg.java` + Reply
- Create: `header/.../server/APIChangePhysicalServerStateMsg.java` + Event
- Create: `header/.../server/APIScanPhysicalServersMsg.java` + Event (LongJob)

- [ ] **Step 1:** Create CRUD API messages

```java
@Action(category = PhysicalServerConstant.ACTION_CATEGORY)
@RestRequest(method = HttpMethod.POST, path = "/physical-servers",
    responseClass = APICreatePhysicalServerEvent.class)
public class APICreatePhysicalServerMsg extends APICreateMessage {
    @APIParam(maxLength = 255)
    private String name;

    @APIParam(resourceType = ZoneVO.class)
    private String zoneUuid;

    @APIParam(resourceType = ServerPoolVO.class)
    private String poolUuid;

    @APIParam(maxLength = 255)
    private String managementIp;

    @APIParam(required = false, validValues = {"x86_64", "aarch64"})
    private String architecture;

    @APIParam(required = false, maxLength = 255)
    private String serialNumber;

    // OOB fields (all optional)
    @APIParam(required = false)
    private String oobManagementType;
    @APIParam(required = false)
    private String oobAddress;
    @APIParam(required = false)
    private Integer oobPort;
    @APIParam(required = false)
    private String oobUsername;
    @APIParam(required = false, password = true)
    private String oobPassword;
}
```

- [ ] **Step 2:** Create APIScanPhysicalServersMsg (LongJob)

```java
@LongJobFor(APIScanPhysicalServersMsg.class)
public class APIScanPhysicalServersMsg extends APIMessage implements LongJobMessage {
    @APIParam(resourceType = ZoneVO.class)
    private String zoneUuid;

    @APIParam(resourceType = ServerPoolVO.class)
    private String poolUuid;

    @APIParam
    private String ipRange;  // "192.168.1.100-192.168.1.200" or CIDR

    @APIParam(required = false, numberRange = {1, 65535})
    private int oobPort = 623;

    @APIParam
    private List<OobCredential> credentials;

    @APIParam(required = false, numberRange = {1, 100})
    private int concurrency = 20;

    @APIParam(required = false, numberRange = {1, 30})
    private int timeoutPerHost = 3;
}
```

- [ ] **Step 3:** Create remaining API messages (Delete, Update, Query, ChangeState)
- [ ] **Step 4:** Verify compile
- [ ] **Step 5:** Commit

### Task 1.9: Flyway DB Migration Script

**Files:**
- Create: `plugin/physicalServer/src/main/resources/db/migration/V1.0__PhysicalServerSchema.sql`

- [ ] **Step 1:** Write schema creation SQL

```sql
CREATE TABLE IF NOT EXISTS `ServerPoolVO` (
    `uuid` VARCHAR(32) NOT NULL,
    `name` VARCHAR(255) NOT NULL,
    `zoneUuid` VARCHAR(32) NOT NULL,
    `physicalLocation` VARCHAR(2048) DEFAULT NULL,
    `networkTopology` VARCHAR(2048) DEFAULT NULL,
    `state` VARCHAR(32) NOT NULL DEFAULT 'Enabled',
    `lastOpDate` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `createDate` TIMESTAMP NOT NULL DEFAULT '0000-00-00 00:00:00',
    PRIMARY KEY (`uuid`),
    CONSTRAINT `fkServerPoolVOZoneEO` FOREIGN KEY (`zoneUuid`) REFERENCES `ZoneEO` (`uuid`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `PhysicalServerVO` (
    `uuid` VARCHAR(32) NOT NULL,
    `name` VARCHAR(255) NOT NULL,
    `zoneUuid` VARCHAR(32) NOT NULL,
    `poolUuid` VARCHAR(32) NOT NULL,
    `managementIp` VARCHAR(255) DEFAULT NULL,
    `architecture` VARCHAR(32) DEFAULT NULL,
    `serialNumber` VARCHAR(255) DEFAULT NULL,
    `manufacturer` VARCHAR(255) DEFAULT NULL,
    `model` VARCHAR(255) DEFAULT NULL,
    `state` VARCHAR(32) NOT NULL DEFAULT 'Enabled',
    `status` VARCHAR(32) NOT NULL DEFAULT 'Connecting',
    `powerStatus` VARCHAR(32) NOT NULL DEFAULT 'Unknown',
    `oobManagementType` VARCHAR(32) DEFAULT NULL,
    `oobAddress` VARCHAR(255) DEFAULT NULL,
    `oobPort` INT DEFAULT NULL,
    `oobUsername` VARCHAR(255) DEFAULT NULL,
    `oobPassword` VARCHAR(255) DEFAULT NULL,
    `lastOpDate` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `createDate` TIMESTAMP NOT NULL DEFAULT '0000-00-00 00:00:00',
    PRIMARY KEY (`uuid`),
    UNIQUE KEY `uk_zone_serial` (`zoneUuid`, `serialNumber`),
    CONSTRAINT `fkPhysicalServerVOZoneEO` FOREIGN KEY (`zoneUuid`) REFERENCES `ZoneEO` (`uuid`) ON DELETE RESTRICT,
    CONSTRAINT `fkPhysicalServerVOServerPoolVO` FOREIGN KEY (`poolUuid`) REFERENCES `ServerPoolVO` (`uuid`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `ClusterServerPoolRefVO` (
    `id` BIGINT AUTO_INCREMENT,
    `clusterUuid` VARCHAR(32) NOT NULL,
    `poolUuid` VARCHAR(32) NOT NULL,
    `lastOpDate` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `createDate` TIMESTAMP NOT NULL DEFAULT '0000-00-00 00:00:00',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cluster` (`clusterUuid`),
    CONSTRAINT `fkClusterServerPoolRefVOClusterEO` FOREIGN KEY (`clusterUuid`) REFERENCES `ClusterEO` (`uuid`) ON DELETE CASCADE,
    CONSTRAINT `fkClusterServerPoolRefVOServerPoolVO` FOREIGN KEY (`poolUuid`) REFERENCES `ServerPoolVO` (`uuid`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `PhysicalServerRoleVO` (
    `uuid` VARCHAR(32) NOT NULL,
    `serverUuid` VARCHAR(32) NOT NULL,
    `roleType` VARCHAR(32) NOT NULL,
    `roleUuid` VARCHAR(32) DEFAULT NULL,
    `schedulingMode` VARCHAR(32) NOT NULL,
    `roleStatus` VARCHAR(32) NOT NULL DEFAULT 'Active',
    `lastOpDate` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `createDate` TIMESTAMP NOT NULL DEFAULT '0000-00-00 00:00:00',
    PRIMARY KEY (`uuid`),
    UNIQUE KEY `uk_server_role` (`serverUuid`, `roleType`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `PhysicalServerHardwareInfoVO` (
    `uuid` VARCHAR(32) NOT NULL,
    `cpuModel` VARCHAR(255) DEFAULT NULL,
    `cpuCores` INT DEFAULT NULL,
    `cpuSockets` INT DEFAULT NULL,
    `totalMemory` BIGINT DEFAULT NULL,
    `totalDisk` BIGINT DEFAULT NULL,
    `nicCount` INT DEFAULT NULL,
    `gpuCount` INT DEFAULT NULL,
    `lastOpDate` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `createDate` TIMESTAMP NOT NULL DEFAULT '0000-00-00 00:00:00',
    PRIMARY KEY (`uuid`),
    CONSTRAINT `fkHardwareInfoVOPhysicalServerVO` FOREIGN KEY (`uuid`) REFERENCES `PhysicalServerVO` (`uuid`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `PhysicalServerHardwareDetailVO` (
    `id` BIGINT AUTO_INCREMENT,
    `serverUuid` VARCHAR(32) NOT NULL,
    `type` VARCHAR(32) NOT NULL,
    `itemModel` VARCHAR(255) DEFAULT NULL,
    `specification` VARCHAR(1024) DEFAULT NULL,
    `firmwareVersion` VARCHAR(255) DEFAULT NULL,
    `healthStatus` VARCHAR(255) DEFAULT NULL,
    `extraInfo` TEXT DEFAULT NULL,
    `lastOpDate` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `createDate` TIMESTAMP NOT NULL DEFAULT '0000-00-00 00:00:00',
    PRIMARY KEY (`id`),
    CONSTRAINT `fkHardwareDetailVOPhysicalServerVO` FOREIGN KEY (`serverUuid`) REFERENCES `PhysicalServerVO` (`uuid`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
```

- [ ] **Step 2:** Verify SQL syntax
- [ ] **Step 3:** Commit

### Task 1.10: ServerPoolManagerImpl (CRUD)

**Files:**
- Create: `plugin/physicalServer/src/main/java/org/zstack/server/ServerPoolManagerImpl.java`
- Create: `plugin/physicalServer/src/main/java/org/zstack/server/ServerPoolCascadeExtension.java`

- [ ] **Step 1:** Implement ServerPoolManagerImpl handling Create/Delete/Update/Query/AttachCluster/DetachCluster
- [ ] **Step 2:** Implement ServerPoolCascadeExtension (Zone deletion cascade)
- [ ] **Step 3:** Verify compile
- [ ] **Step 4:** Commit

### Task 1.11: PhysicalServerManagerImpl (CRUD)

**Files:**
- Create: `plugin/physicalServer/src/main/java/org/zstack/server/PhysicalServerManagerImpl.java`
- Create: `plugin/physicalServer/src/main/java/org/zstack/server/PhysicalServerApiInterceptor.java`

- [ ] **Step 1:** Implement PhysicalServerManagerImpl as AbstractService

```java
@Component
public class PhysicalServerManagerImpl extends AbstractService
    implements PhysicalServerManager, ApiMessageInterceptor {

    @Override
    public void handleMessage(Message msg) {
        if (msg instanceof APICreatePhysicalServerMsg) {
            handle((APICreatePhysicalServerMsg) msg);
        } else if (msg instanceof APIDeletePhysicalServerMsg) {
            handle((APIDeletePhysicalServerMsg) msg);
        }
        // ... other handlers
    }
}
```

- [ ] **Step 2:** Implement API interceptor for validation (serialNumber uniqueness check at app layer, poolUuid validity, IP format)
- [ ] **Step 3:** Verify compile
- [ ] **Step 4:** Commit

### Task 1.12: ScanPhysicalServersLongJob

**Files:**
- Create: `plugin/physicalServer/src/main/java/org/zstack/server/ScanPhysicalServersLongJob.java`

- [ ] **Step 1:** Implement LongJob for IPMI scanning

```java
@LongJobFor(APIScanPhysicalServersMsg.class)
@Component
public class ScanPhysicalServersLongJob implements LongJob {
    @Override
    public void start(LongJobVO job, ReturnValueCompletion<LongJobInventory> completion) {
        // 1. Parse IP range
        // 2. Parallel ipmitool mc info probe (concurrency-limited)
        // 3. For reachable IPs, try credentials
        // 4. On auth success, read FRU for serialNumber/manufacturer/model
        // 5. Deduplicate by serialNumber + zoneUuid
        // 6. Create PhysicalServerVO for new discoveries
        // 7. Report results
    }
}
```

- [ ] **Step 2:** Implement IP range parser (supports "start-end" and CIDR notation)
- [ ] **Step 3:** Add 1024 IP limit validation
- [ ] **Step 4:** Verify compile
- [ ] **Step 5:** Commit

### Task 1.13: Integration Test — ServerPool CRUD

**Files:**
- Create: `test/src/test/java/org/zstack/test/server/TestServerPoolCrud.java`

- [ ] **Step 1:** Write integration test

```java
public class TestServerPoolCrud extends SubCase {
    @Override
    public void test() {
        // Create ServerPool
        ServerPoolInventory pool = createServerPool("pool-1", zone.getUuid());
        assert pool != null;

        // Query
        List<ServerPoolInventory> pools = queryServerPool(
            new QueryCondition("zoneUuid", QueryOp.EQ, zone.getUuid()));
        assert pools.size() == 1;

        // Attach to Cluster
        attachServerPoolToCluster(pool.getUuid(), cluster.getUuid());

        // Delete (should cascade ClusterRef)
        deleteServerPool(pool.getUuid());
    }
}
```

- [ ] **Step 2:** Run test: `mvn test -Dtest=TestServerPoolCrud -pl test`
- [ ] **Step 3:** Fix any failures
- [ ] **Step 4:** Commit

### Task 1.14: Integration Test — PhysicalServer CRUD

**Files:**
- Create: `test/src/test/java/org/zstack/test/server/TestPhysicalServerCrud.java`

- [ ] **Step 1:** Write integration test covering Create/Query/Update/Delete/ChangeState
- [ ] **Step 2:** Test serialNumber uniqueness constraint (app-layer)
- [ ] **Step 3:** Test OOB password encryption
- [ ] **Step 4:** Run test and fix failures
- [ ] **Step 5:** Commit

---

## Phase 2: Provision Network (PRD-4)

> **Priority:** P0 Must Have | **Dependencies:** None (independent) | **Estimated tasks:** 6

### Task 2.1: ProvisionNetworkVO & API Messages

**Files:**
- Create: `header/.../server/PhysicalServerProvisionNetworkVO.java`
- Create: `header/.../server/PhysicalServerProvisionNetworkInventory.java`
- Create: `header/.../server/PhysicalServerProvisionNetworkClusterRefVO.java`
- Create: API messages (Create/Delete/Query/AttachCluster/DetachCluster)

- [ ] **Step 1:** Create ProvisionNetworkVO with type field (STANDALONE_PXE/GATEWAY_PXE)
- [ ] **Step 2:** Create ClusterRefVO
- [ ] **Step 3:** Create API messages (no role-type restriction on API)
- [ ] **Step 4:** Verify compile
- [ ] **Step 5:** Commit

### Task 2.2: DB Migration for ProvisionNetwork

**Files:**
- Add to: `plugin/physicalServer/src/main/resources/db/migration/V1.0__PhysicalServerSchema.sql`

- [ ] **Step 1:** Add ProvisionNetwork tables to migration script

```sql
CREATE TABLE IF NOT EXISTS `PhysicalServerProvisionNetworkVO` (
    `uuid` VARCHAR(32) NOT NULL,
    `name` VARCHAR(255) NOT NULL,
    `description` VARCHAR(2048) DEFAULT NULL,
    `zoneUuid` VARCHAR(32) NOT NULL,
    `type` VARCHAR(32) NOT NULL DEFAULT 'GATEWAY_PXE',
    `dhcpInterface` VARCHAR(255) DEFAULT NULL,
    `dhcpRangeStartIp` VARCHAR(64) DEFAULT NULL,
    `dhcpRangeEndIp` VARCHAR(64) DEFAULT NULL,
    `dhcpRangeNetmask` VARCHAR(64) DEFAULT NULL,
    `dhcpRangeGateway` VARCHAR(64) DEFAULT NULL,
    `state` VARCHAR(32) NOT NULL DEFAULT 'Enabled',
    `lastOpDate` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `createDate` TIMESTAMP NOT NULL DEFAULT '0000-00-00 00:00:00',
    PRIMARY KEY (`uuid`),
    CONSTRAINT `fkProvisionNetworkVOZoneEO` FOREIGN KEY (`zoneUuid`) REFERENCES `ZoneEO` (`uuid`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `PhysicalServerProvisionNetworkClusterRefVO` (
    `id` BIGINT AUTO_INCREMENT,
    `networkUuid` VARCHAR(32) NOT NULL,
    `clusterUuid` VARCHAR(32) NOT NULL,
    `lastOpDate` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `createDate` TIMESTAMP NOT NULL DEFAULT '0000-00-00 00:00:00',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_net_cluster` (`networkUuid`, `clusterUuid`),
    CONSTRAINT `fkPNClusterRefVOProvisionNetworkVO` FOREIGN KEY (`networkUuid`) REFERENCES `PhysicalServerProvisionNetworkVO` (`uuid`) ON DELETE CASCADE,
    CONSTRAINT `fkPNClusterRefVOClusterEO` FOREIGN KEY (`clusterUuid`) REFERENCES `ClusterEO` (`uuid`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
```

- [ ] **Step 2:** Commit

### Task 2.3: ProvisionNetworkManagerImpl

**Files:**
- Create: `plugin/physicalServer/src/main/java/org/zstack/server/ProvisionNetworkManagerImpl.java`
- Create: `plugin/physicalServer/src/main/java/org/zstack/server/ProvisionNetworkCascadeExtension.java`

- [ ] **Step 1:** Implement CRUD handler (Create/Delete/Query/AttachCluster/DetachCluster)
- [ ] **Step 2:** Implement CascadeExtension for Zone deletion
- [ ] **Step 3:** Verify compile
- [ ] **Step 4:** Commit

### Task 2.4: Integration Test — ProvisionNetwork

- [ ] **Step 1:** Write test covering CRUD + ClusterRef + type filtering
- [ ] **Step 2:** Run and fix
- [ ] **Step 3:** Commit

---

## Phase 3: Role SPI & Adapters (PRD-2)

> **Priority:** P0 Must Have | **Dependencies:** Phase 1 | **Estimated tasks:** 10

### Task 3.1: PhysicalServerRoleProvider SPI

**Files:**
- Create: `header/.../server/PhysicalServerRoleProvider.java`
- Create: `header/.../server/RoleMatchContext.java`
- Create: `header/.../server/CapacityUsage.java`
- Create: `header/.../server/PowerManageable.java`
- Create: `header/.../server/HardwareDiscoverable.java`

- [ ] **Step 1:** Define SPI interface with Javadoc

```java
package org.zstack.header.server;

/**
 * SPI for role modules to integrate with unified hardware management.
 * Implement this interface and register as Spring Bean to add a new role type.
 */
public interface PhysicalServerRoleProvider {
    ServerRoleType getRoleType();
    SchedulingMode getSchedulingMode();
    CapacityUsage getCapacityConsumption(String serverUuid);
    void onPhysicalServerCreated(String serverUuid);
    void onPhysicalServerDeleted(String serverUuid);
    RoleInventory getInventory(String roleUuid);
}
```

- [ ] **Step 2:** Create RoleMatchContext (serialNumber, oobAddress, managementIp, zoneUuid for auto-association)
- [ ] **Step 3:** Create CapacityUsage (usedCpu, usedMemory)
- [ ] **Step 4:** Create PowerManageable and HardwareDiscoverable interfaces
- [ ] **Step 5:** Verify compile
- [ ] **Step 6:** Commit

### Task 3.2: AttachRole / DetachRole API Messages (FR-035, FR-036)

**Files:**
- Create: `header/.../server/APIAttachPhysicalServerRoleMsg.java` + Event
- Create: `header/.../server/APIDetachPhysicalServerRoleMsg.java` + Event

- [ ] **Step 1:** Create APIAttachPhysicalServerRoleMsg

```java
@Action(category = PhysicalServerConstant.ACTION_CATEGORY)
public class APIAttachPhysicalServerRoleMsg extends APIMessage {
    @APIParam(resourceType = PhysicalServerVO.class)
    private String serverUuid;

    @APIParam(validValues = {"KVM_HOST", "BAREMETAL_V2", "CONTAINER_HOST"})
    private String roleType;

    @APIParam(resourceType = ClusterVO.class)
    private String clusterUuid;

    @APIParam(required = false)
    private Map<String, String> roleConfig;
}
```

- [ ] **Step 2:** Create APIDetachPhysicalServerRoleMsg (with force flag)
- [ ] **Step 3:** Verify compile
- [ ] **Step 4:** Commit

### Task 3.3: Role Registration & Mutual Exclusion Logic

**Files:**
- Add to: `plugin/physicalServer/.../PhysicalServerManagerImpl.java`

- [ ] **Step 1:** Implement registerRole() with mutual exclusion check

```java
/**
 * Register a role to a physical server.
 * Mutual exclusion rules:
 * - INTERNAL_EXCLUSIVE cannot coexist with INTERNAL_SHARED
 * - EXTERNAL_READONLY compatible with anything
 * - Same roleType cannot register twice (UNIQUE constraint)
 */
public void registerRole(String serverUuid, ServerRoleType roleType,
                         String roleUuid, SchedulingMode mode) {
    // 1. Check existing roles
    List<PhysicalServerRoleVO> existing = Q.New(PhysicalServerRoleVO.class)
        .eq(PhysicalServerRoleVO_.serverUuid, serverUuid).list();

    // 2. Mutual exclusion check
    if (mode == SchedulingMode.INTERNAL_EXCLUSIVE) {
        boolean hasShared = existing.stream()
            .anyMatch(r -> r.getSchedulingMode() == SchedulingMode.INTERNAL_SHARED);
        if (hasShared) throw new ApiMessageInterceptionException(...);
    }
    if (mode == SchedulingMode.INTERNAL_SHARED) {
        boolean hasExclusive = existing.stream()
            .anyMatch(r -> r.getSchedulingMode() == SchedulingMode.INTERNAL_EXCLUSIVE);
        if (hasExclusive) throw new ApiMessageInterceptionException(...);
    }

    // 3. Create RoleVO
    PhysicalServerRoleVO role = new PhysicalServerRoleVO();
    role.setUuid(Platform.getUuid());
    role.setServerUuid(serverUuid);
    role.setRoleType(roleType);
    role.setRoleUuid(roleUuid);
    role.setSchedulingMode(mode);
    role.setRoleStatus("Active");
    dbf.persist(role);
}
```

- [ ] **Step 2:** Implement AttachRole handler (orchestration: mutex check → delegate RoleProvider.createRole → registerRole)
- [ ] **Step 3:** Implement DetachRole handler (load check → delegate role module delete → update RoleVO)
- [ ] **Step 4:** Verify compile
- [ ] **Step 5:** Commit

### Task 3.4: Three-Level Auto-Association Logic

**Files:**
- Add to: `plugin/physicalServer/.../PhysicalServerManagerImpl.java`

- [ ] **Step 1:** Implement findOrCreatePhysicalServer(RoleMatchContext)

```java
/**
 * Three-level degradation matching:
 * 1. serialNumber + zoneUuid (preferred)
 * 2. oobAddress + zoneUuid (degraded)
 * 3. managementIp + zoneUuid (final fallback)
 *
 * Invalid serialNumbers ("Not Specified", "To Be Filled") are filtered.
 */
public PhysicalServerVO findOrCreatePhysicalServer(RoleMatchContext ctx) {
    // Level 1: serialNumber
    if (isValidSerialNumber(ctx.getSerialNumber())) {
        PhysicalServerVO found = Q.New(PhysicalServerVO.class)
            .eq(PhysicalServerVO_.zoneUuid, ctx.getZoneUuid())
            .eq(PhysicalServerVO_.serialNumber, ctx.getSerialNumber())
            .find();
        if (found != null) return found;
    }

    // Level 2: oobAddress
    if (ctx.getOobAddress() != null) {
        PhysicalServerVO found = Q.New(PhysicalServerVO.class)
            .eq(PhysicalServerVO_.zoneUuid, ctx.getZoneUuid())
            .eq(PhysicalServerVO_.oobAddress, ctx.getOobAddress())
            .find();
        if (found != null) return found;
    }

    // Level 3: managementIp
    if (ctx.getManagementIp() != null) {
        PhysicalServerVO found = Q.New(PhysicalServerVO.class)
            .eq(PhysicalServerVO_.zoneUuid, ctx.getZoneUuid())
            .eq(PhysicalServerVO_.managementIp, ctx.getManagementIp())
            .find();
        if (found != null) return found;
    }

    // No match: create new
    return createPhysicalServer(ctx);
}
```

- [ ] **Step 2:** Add invalid serialNumber filter set
- [ ] **Step 3:** Verify compile
- [ ] **Step 4:** Commit

### Task 3.5: KVM RoleProvider

**Files:**
- Create: `plugin/kvm/src/main/java/org/zstack/kvm/KvmPhysicalServerRoleProvider.java`

> **Note:** `PostHostConnectExtensionPoint` is in `org.zstack.compute.host` (compute module), not in `header`. Verify `plugin/kvm/pom.xml` already has `compute` as a dependency (it should — KVM plugin already uses compute classes).

- [ ] **Step 1:** Implement KvmPhysicalServerRoleProvider

```java
@Component
public class KvmPhysicalServerRoleProvider
    implements PhysicalServerRoleProvider, PostHostConnectExtensionPoint,
               HostDeleteExtensionPoint {

    @Override
    public ServerRoleType getRoleType() {
        return ServerRoleType.KVM_HOST;
    }

    @Override
    public SchedulingMode getSchedulingMode() {
        return SchedulingMode.INTERNAL_SHARED;
    }

    @Override
    public void afterHostConnect(HostInventory host) {
        // Build RoleMatchContext from host (use SYSTEM_SERIAL_NUMBER SystemTag)
        // Call findOrCreatePhysicalServer + registerRole
    }

    @Override
    public void preDeleteHost(HostInventory host) {
        // Update RoleVO status to Stale
    }
}
```

- [ ] **Step 2:** Add dependency on physicalServer module in `plugin/kvm/pom.xml`
- [ ] **Step 3:** Verify compile
- [ ] **Step 4:** Commit

### Task 3.6: Integration Test — Role Registration & Mutual Exclusion

- [ ] **Step 1:** Test: register KVM (INTERNAL_SHARED) → register Container (EXTERNAL_READONLY) → success
- [ ] **Step 2:** Test: register BM2 (INTERNAL_EXCLUSIVE) → register KVM (INTERNAL_SHARED) → fail (mutual exclusion)
- [ ] **Step 3:** Test: register KVM → register KVM → fail (same type)
- [ ] **Step 4:** Test: three-level auto-association (serialNumber match, IP fallback, new creation)
- [ ] **Step 5:** Run and fix
- [ ] **Step 6:** Commit

---

## Phase 4: Capacity Management (PRD-3)

> **Priority:** P0 Must Have | **Dependencies:** Phase 1, Phase 3 | **Estimated tasks:** 12

### Task 4.1: PhysicalServerCapacityVO

**Files:**
- Create: `header/.../server/PhysicalServerCapacityVO.java`

- [ ] **Step 1:** Create CapacityVO

```java
@Entity
@Table(name = "PhysicalServerCapacityVO")
public class PhysicalServerCapacityVO {
    @Id
    @Column
    @ForeignKey(parentEntityClass = PhysicalServerVO.class, onDeleteAction = ReferenceOption.CASCADE)
    private String uuid;

    @Column
    private long totalPhysicalCpu;
    @Column
    private long totalPhysicalMemory;
    @Column
    private int cpuSockets;
    @Column
    private int cpuCoreNum;
    @Column
    private long availablePhysicalMemory;
    @Column
    private double cpuOverprovisioningRatio;
    @Column
    private double memoryOverprovisioningRatio;
    @Column
    private long availableCpu;
    @Column
    private long availableMemory;
    @Column
    private long reservedMemory;
    @Column
    private long totalDisk;
    @Column
    private long availableDisk;
    @Column
    private String capacityState;  // Initialized/Ready/Allocated/Recalculating/Stale

    // Derived getters
    public long getTotalCpu() {
        return (long)(totalPhysicalCpu * cpuOverprovisioningRatio);
    }
    public long getTotalMemory() {
        return (long)(totalPhysicalMemory * memoryOverprovisioningRatio);
    }
}
```

- [ ] **Step 2:** Verify compile
- [ ] **Step 3:** Commit

### Task 4.2: DB Migration — HostCapacityVO → VIEW

**Files:**
- Create: `plugin/physicalServer/src/main/resources/db/migration/V1.1__CapacityMigration.sql`

- [ ] **Step 1:** Write migration SQL

```sql
-- Step 1: Drop FK
ALTER TABLE `HostCapacityVO` DROP FOREIGN KEY `fkHostCapacityVOHostEO`;

-- Step 2: Rename table
ALTER TABLE `HostCapacityVO` RENAME TO `PhysicalServerCapacityVO`;

-- Step 3: Keep existing column names unchanged. PhysicalServerCapacityVO.java uses
-- @Column(name="totalCpu") to map field totalPhysicalCpu to existing column.
-- No RENAME COLUMN needed — preserves backward compatibility.

-- Step 3b: Add new columns
ALTER TABLE `PhysicalServerCapacityVO`
    ADD COLUMN `cpuOverprovisioningRatio` DOUBLE NOT NULL DEFAULT 1.0,
    ADD COLUMN `memoryOverprovisioningRatio` DOUBLE NOT NULL DEFAULT 1.0,
    ADD COLUMN `reservedMemory` BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN `totalDisk` BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN `availableDisk` BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN `capacityState` VARCHAR(32) NOT NULL DEFAULT 'Ready';

-- Step 4: Add FK to PhysicalServerVO (deferred until migration script creates PS records)
-- ALTER TABLE `PhysicalServerCapacityVO` ADD CONSTRAINT ...

-- Step 5: Create VIEW
CREATE OR REPLACE VIEW `HostCapacityVO` AS
    SELECT
        psc.uuid,
        CAST(psc.totalPhysicalCpu * psc.cpuOverprovisioningRatio AS SIGNED) AS totalCpu,
        psc.totalPhysicalCpu AS cpuNum,
        psc.cpuSockets,
        psc.cpuCoreNum,
        CAST(psc.totalPhysicalMemory * psc.memoryOverprovisioningRatio AS SIGNED) AS totalMemory,
        psc.availableCpu,
        psc.availableMemory,
        psc.totalPhysicalMemory,
        psc.availablePhysicalMemory
    FROM PhysicalServerCapacityVO psc
    WHERE psc.uuid IN (SELECT uuid FROM HostEO WHERE deleted IS NULL);
```

- [ ] **Step 2:** Verify VIEW is read-only (INSERT/UPDATE should fail)
- [ ] **Step 3:** Commit

### Task 4.2.1: HostCapacityVO Entity Survival & Null-Safety Audit

> **Critical:** `HostCapacityVO.java` MUST remain as a Hibernate `@Entity` class — only the underlying DB object changes from TABLE to VIEW. The `.java` file is NOT deleted or renamed.

**Files:**
- Verify: `header/src/main/java/org/zstack/header/allocator/HostCapacityVO.java` — keep unchanged
- Audit: All callers of `hostVO.getCapacity()` for null-safety

- [ ] **Step 1:** Confirm `HostCapacityVO.java` `@Entity` annotation, `@EntityGraph`, and `@OneToOne EAGER` join in `HostVO.java` all remain untouched
- [ ] **Step 2:** The VIEW includes `WHERE uuid IN (SELECT uuid FROM HostEO WHERE deleted IS NULL)`. For PhysicalServers that are NOT KVM hosts (BM2-only, Container-only), there will be NO row in the VIEW. Verify this is acceptable (only KVM hosts have HostVO records, so the VIEW covers all existing HostCapacityVO consumers)
- [ ] **Step 3:** Audit all code paths calling `hostVO.getCapacity()` — grep for `.getCapacity()` in compute/ and plugin/ modules. Add null checks where missing (though for KVM hosts this should always return non-null via the VIEW)
- [ ] **Step 4:** Verify `@EntityGraph` on `HostCapacityVO` referencing `HostVO.class` still works with VIEW-backed entity (MySQL allows `@Entity` mapped to VIEW for reads)
- [ ] **Step 5:** Commit any null-safety fixes

### Task 4.3: Modify 6 Write Paths

**Files:**
- Modify: `compute/src/main/java/org/zstack/compute/allocator/HostAllocatorManagerImpl.java` (W1, W2)
- Modify: `compute/src/main/java/org/zstack/compute/allocator/HostCapacityUpdater.java` (W3)
- Modify: `compute/src/main/java/org/zstack/compute/allocator/HostCpuOverProvisioningManagerImpl.java` (W4-W6)

> **Note:** All 3 files are in `compute/` module, not `header/` or `plugin/`. Additionally, `HostAllocatorManagerImpl` has ~8 references to `HostCapacityVO` — W1-W2 are writes, the remaining ~6 are JPQL reads (e.g. `from HostCapacityVO hc` at lines ~672/692/712) which work transparently via the VIEW.

- [ ] **Step 1 (W1-W2):** In `HostAllocatorManagerImpl.handle(ReportHostCapacityMessage)` (`compute/src/main/java/org/zstack/compute/allocator/HostAllocatorManagerImpl.java`), change `new HostCapacityVO()` + `dbf.persist()` to `new PhysicalServerCapacityVO()` + `dbf.persist()`
- [ ] **Step 2 (W3):** In `HostCapacityUpdater._run()` (`compute/.../HostCapacityUpdater.java`), change `dbf.getEntityManager().merge()` to find/merge PhysicalServerCapacityVO internally while keeping HostCapacityUpdaterRunnable interface unchanged
- [ ] **Step 3 (W4-W6):** In `HostCpuOverProvisioningManagerImpl` (`compute/.../HostCpuOverProvisioningManagerImpl.java`), change 3 JPQL queries from `update HostCapacityVO` to `update PhysicalServerCapacityVO`
- [ ] **Step 4:** Verify the ~6 JPQL READ queries (`from HostCapacityVO hc` at lines ~672/692/712 in HostAllocatorManagerImpl) work correctly against the VIEW — they should be transparent
- [ ] **Step 5:** Verify compile — ensure 47 read paths still compile (they read via VIEW)
- [ ] **Step 6:** Commit

### Task 4.4: PhysicalServerCapacityUpdater

**Files:**
- Create: `plugin/physicalServer/.../PhysicalServerCapacityUpdater.java`

- [ ] **Step 1:** Implement with PESSIMISTIC_WRITE lock + @DeadlockAutoRestart

```java
@Component
public class PhysicalServerCapacityUpdater {
    @DeadlockAutoRestart
    public void updateCapacity(String serverUuid, PhysicalServerCapacityUpdaterRunnable runnable) {
        PhysicalServerCapacityVO vo = dbf.getEntityManager()
            .find(PhysicalServerCapacityVO.class, serverUuid, LockModeType.PESSIMISTIC_WRITE);
        if (vo == null) return;
        PhysicalServerCapacityVO updated = runnable.call(vo);
        if (updated != null) {
            dbf.getEntityManager().merge(updated);
        }
    }
}
```

- [ ] **Step 2:** Ensure @Transactional and @DeadlockAutoRestart NOT on same method
- [ ] **Step 3:** Verify compile
- [ ] **Step 4:** Commit

### Task 4.5: OverProvisioning Manager

**Files:**
- Create: `plugin/physicalServer/.../OverProvisioningManagerImpl.java`

- [ ] **Step 1:** Implement GlobalConfig defaults + per-server SystemTag override
- [ ] **Step 2:** Implement recalculation trigger on ratio change
- [ ] **Step 3:** Commit

### Task 4.6: AllocateServerMsg & ServerAllocatorChain

**Files:**
- Create: `header/.../server/AllocateServerMsg.java`
- Create: `header/.../server/AllocateServerReply.java`
- Create: `header/.../server/AllocateServerSpec.java`
- Create: `plugin/physicalServer/.../ServerAllocatorChainImpl.java`
- Create: `plugin/physicalServer/.../flow/*.java` (7 flows)

- [ ] **Step 1:** Create AllocateServerMsg with fields: requiredRoleType, requiredCpu, requiredMemory, clusterUuid, zoneUuid, serverUuid, poolUuid, schedulingMode
- [ ] **Step 2:** Create AllocateServerSpec
- [ ] **Step 3:** Implement ServerAllocatorChainImpl with Spring-injected Flow list

```java
@Component
public class ServerAllocatorChainImpl {
    @Autowired
    private List<AbstractServerAllocatorFlow> flows;

    public void allocate(AllocateServerSpec spec, ReturnValueCompletion<PhysicalServerInventory> completion) {
        FlowChain chain = FlowChainBuilder.newSimpleFlowChain();
        chain.setName("server-allocator-chain");
        chain.setData(new HashMap<>());
        chain.getData().put("spec", spec);
        chain.getData().put("candidates", /* initial full list */);

        for (AbstractServerAllocatorFlow flow : flows) {
            chain.then(flow);
        }

        chain.done(new FlowDoneHandler(completion) { ... });
        chain.error(new FlowErrorHandler(completion) { ... });
        chain.start();
    }
}
```

- [ ] **Step 4:** Implement 7 flows: ZoneFilter, ClusterFilter, PoolFilter, RoleTypeFilter, StatusFilter, CapacityFilter, SortFilter
- [ ] **Step 5:** Verify compile
- [ ] **Step 6:** Commit

### Task 4.7: Extension Points

**Files:**
- Create: `header/.../server/ServerAllocatorFilterExtensionPoint.java`
- Create: `header/.../server/ServerReservedCapacityExtensionPoint.java`

- [ ] **Step 1:** Define extension point interfaces
- [ ] **Step 2:** Wire into allocator chain and capacity recalculation
- [ ] **Step 3:** Commit

### Task 4.8: Mixed Deployment — Safety Buffer & Mutual Reservation

**Files:**
- Add to: `plugin/physicalServer/.../PhysicalServerCapacityUpdater.java`

- [ ] **Step 1:** Implement safety buffer calculation: CPU max(4, total×5%), Memory max(4GB, total×10%)
- [ ] **Step 2:** Implement mutual system reservation: KVM available = totalPhysical - containerReserved - safetyBuffer
- [ ] **Step 3:** Add GlobalConfig for safety buffer ratios
- [ ] **Step 4:** Commit

### Task 4.9: Integration Test — Capacity

- [ ] **Step 1:** Test: HostCapacityVO VIEW returns same data as PhysicalServerCapacityVO
- [ ] **Step 2:** Test: capacity update via PhysicalServerCapacityUpdater → read via HostCapacityVO VIEW
- [ ] **Step 3:** Test: overprovision ratio change triggers recalculation
- [ ] **Step 4:** Test: concurrent capacity deduction (no oversell)
- [ ] **Step 5:** Test: mixed deployment safety buffer
- [ ] **Step 6:** Run and fix
- [ ] **Step 7:** Commit

### Task 4.10: Integration Test — Allocator

- [ ] **Step 1:** Test: allocate KVM server via ServerAllocatorChain
- [ ] **Step 2:** Test: allocate with zone/cluster/pool filters
- [ ] **Step 3:** Test: capacity filter rejects when insufficient
- [ ] **Step 4:** Run and fix
- [ ] **Step 5:** Commit

---

## Phase 5: Compat Bridge & Migration (PRD-5)

> **Priority:** P0 Must Have | **Dependencies:** Phase 3, Phase 4 | **Estimated tasks:** 10

### Task 5.1: Add candidateHostUuids to HostAllocatorSpec

**Files:**
- Modify: `header/.../allocator/AllocateHostSpec.java`

- [ ] **Step 1:** Add field (default null, no behavior change when null)

```java
// In AllocateHostSpec.java — add field
private List<String> candidateHostUuids;  // null = no filter (backward compatible)
// getter/setter
```

- [ ] **Step 2:** Verify compile — all existing callers unaffected (null default)
- [ ] **Step 3:** Commit

### Task 5.2: CandidateFilterFlow in HostAllocatorChain

**Files:**
- Create: `header/.../allocator/CandidateHostUuidsFilterFlow.java`
- Modify: Host allocator chain configuration to insert this flow at head

- [ ] **Step 1:** Implement flow: if candidateHostUuids is not null, filter candidates to this whitelist

```java
public class CandidateHostUuidsFilterFlow extends AbstractHostAllocatorFlow {
    @Override
    public void allocate(AllocateHostSpec spec, List<HostVO> candidates) {
        if (spec.getCandidateHostUuids() == null) {
            next(candidates);
            return;
        }
        Set<String> whitelist = new HashSet<>(spec.getCandidateHostUuids());
        List<HostVO> filtered = candidates.stream()
            .filter(h -> whitelist.contains(h.getUuid()))
            .collect(Collectors.toList());
        next(filtered);
    }
}
```

- [ ] **Step 2:** Register flow at head of HostAllocatorChain
- [ ] **Step 3:** Verify compile
- [ ] **Step 4:** Commit

### Task 5.3: CompatibilityBridge

**Files:**
- Create: `plugin/physicalServer/.../CompatibilityBridge.java`

> **Note:** `BeforeAllocateHostExtensionPoint` does NOT exist in the codebase. Use `HostAllocatorPreStartExtensionPoint` (in `compute/src/main/java/org/zstack/compute/allocator/`) which has method `beforeHostAllocatorStart(HostAllocatorSpec spec, List<AbstractHostAllocatorFlow> flows)`. The bridge injects a custom flow at the head of the flow list to filter by candidateHostUuids.

- [ ] **Step 1:** Implement two-phase thin adapter using existing extension point

```java
@Component
public class CompatibilityBridge implements HostAllocatorPreStartExtensionPoint {
    @Autowired
    private ServerAllocatorChainImpl serverAllocatorChain;

    /**
     * Phase 1: Convert AllocateHostMsg → AllocateServerSpec, run ServerAllocatorChain
     * Phase 2: Inject candidateHostUuids into HostAllocatorSpec via a head-of-chain flow
     *
     * Uses HostAllocatorPreStartExtensionPoint to inject filtering before
     * the standard HostAllocatorChain runs.
     */
    @Override
    public void beforeHostAllocatorStart(AllocateHostSpec spec,
            List<AbstractHostAllocatorFlow> flows) {
        if (!isEnabled()) {
            return;
        }

        AllocateServerSpec serverSpec = convertToServerSpec(spec);
        // Run ServerAllocatorChain synchronously to get candidates
        List<String> candidateServerUuids = serverAllocatorChain.allocateSync(serverSpec);
        // Map PhysicalServer UUIDs → Host UUIDs via RoleVO
        List<String> hostUuids = mapToHostUuids(candidateServerUuids, ServerRoleType.KVM_HOST);
        spec.setCandidateHostUuids(hostUuids);
        // CandidateHostUuidsFilterFlow (Task 5.2) will pick this up at head of chain
    }
}
```

- [ ] **Step 2:** Implement GlobalConfig toggle (`unifiedHardwareManagement.enabled`, default true)
- [ ] **Step 3:** Verify compile
- [ ] **Step 4:** Commit

### Task 5.4: Data Migration Script (FR-030)

**Files:**
- Create: `plugin/physicalServer/src/main/resources/db/migration/V1.2__DataMigration.sql`

- [ ] **Step 1:** Write idempotent migration SQL

```sql
-- Migrate KVM Hosts
INSERT INTO PhysicalServerVO (uuid, name, zoneUuid, poolUuid, managementIp, state, status)
    SELECT h.uuid, h.name, c.zoneUuid, /* default pool uuid */, h.managementIp,
           h.state, h.status
    FROM HostEO h
    JOIN ClusterEO c ON h.clusterUuid = c.uuid
    WHERE h.deleted IS NULL AND h.hypervisorType = 'KVM'
    ON DUPLICATE KEY UPDATE lastOpDate = CURRENT_TIMESTAMP;

INSERT INTO ResourceVO (uuid, resourceName, resourceType, concreteResourceType)
    SELECT uuid, name, 'PhysicalServerVO', 'org.zstack.header.server.PhysicalServerVO'
    FROM PhysicalServerVO
    ON DUPLICATE KEY UPDATE resourceName = VALUES(resourceName);

INSERT INTO AccountResourceRefVO (accountUuid, ownerAccountUuid, resourceUuid, resourceType,
    concreteResourceType, permission, isShared, lastOpDate, createDate)
    SELECT '36c27e8ff05c4780bf6d2fa65700f22e', '36c27e8ff05c4780bf6d2fa65700f22e',
           uuid, 'PhysicalServerVO', 'org.zstack.header.server.PhysicalServerVO',
           2, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    FROM PhysicalServerVO
    ON DUPLICATE KEY UPDATE lastOpDate = CURRENT_TIMESTAMP;

-- Create RoleVO for KVM
INSERT INTO PhysicalServerRoleVO (uuid, serverUuid, roleType, roleUuid, schedulingMode, roleStatus)
    SELECT REPLACE(UUID(), '-', ''), ps.uuid, 'KVM_HOST', h.uuid, 'INTERNAL_SHARED', 'Active'
    FROM PhysicalServerVO ps
    JOIN HostEO h ON ps.uuid = h.uuid
    WHERE h.hypervisorType = 'KVM'
    ON DUPLICATE KEY UPDATE lastOpDate = CURRENT_TIMESTAMP;

-- Similar blocks for BM2 and Container (using their respective tables)
```

- [ ] **Step 2:** Create default ServerPool per Zone for legacy entries
- [ ] **Step 3:** Verify idempotency (run twice, no duplicate data)
- [ ] **Step 4:** Commit

### Task 5.5: Unified Query (FR-031)

**Files:**
- Already created in Task 1.8: APIQueryPhysicalServerMsg

- [ ] **Step 1:** Implement query handler with standard ZStack Query API
- [ ] **Step 2:** Support filters: poolUuid, zoneUuid, clusterUuid (via RoleVO JOIN), roleType, state, status
- [ ] **Step 3:** Return PhysicalServerInventory with expanded roles list
- [ ] **Step 4:** Commit

### Task 5.6: Unified Power Management (FR-032)

**Files:**
- Create: `header/.../server/APIPowerOnPhysicalServerMsg.java` + Event
- Create: `header/.../server/APIPowerOffPhysicalServerMsg.java` + Event
- Create: `header/.../server/APIPowerResetPhysicalServerMsg.java` + Event

- [ ] **Step 1:** Create API messages
- [ ] **Step 2:** Implement handler: read OOB credentials → execute ipmitool / Redfish API → update PowerStatus
- [ ] **Step 3:** Return error if no OOB credentials
- [ ] **Step 4:** Commit

### Task 5.7: Unified Hardware Discovery (FR-033, Should Have)

**Files:**
- Create: `header/.../server/APIDiscoverPhysicalServerHardwareMsg.java` + Event

- [ ] **Step 1:** Create API message
- [ ] **Step 2:** Implement handler: dispatch to each RoleProvider's discovery, plus OOB FRU read
- [ ] **Step 3:** Write results to HardwareInfoVO / HardwareDetailVO
- [ ] **Step 4:** Commit

### Task 5.8: Integration Test — CompatibilityBridge

- [ ] **Step 1:** Test: AllocateHostMsg with bridge enabled → same result as legacy path
- [ ] **Step 2:** Test: Bridge disabled (GlobalConfig=false) → 100% legacy path
- [ ] **Step 3:** Test: Bridge filters correctly via ServerAllocatorChain, then HostAllocatorChain refines
- [ ] **Step 4:** Run and fix
- [ ] **Step 5:** Commit

### Task 5.9: Integration Test — Data Migration

- [ ] **Step 1:** Test: run migration → QueryPhysicalServerMsg returns all legacy hosts
- [ ] **Step 2:** Test: run migration twice → no duplicate records
- [ ] **Step 3:** Test: ResourceVO and AccountResourceRefVO records exist
- [ ] **Step 4:** Run and fix
- [ ] **Step 5:** Commit

### Task 5.10: Full Regression Test

- [ ] **Step 1:** Run `zstack/test` full test suite — zero new failures
- [ ] **Step 2:** Run `premium/test-premium` full test suite — zero new failures
- [ ] **Step 3:** Verify no new WARN/ERROR logs
- [ ] **Step 4:** Final commit

---

## Checklist Before Completion

- [ ] All Phase 1-5 tasks completed
- [ ] All VOs have corresponding Flyway migration scripts
- [ ] HostCapacityVO VIEW works — 47 read paths unchanged
- [ ] 6 write paths modified to write PhysicalServerCapacityVO
- [ ] CompatibilityBridge two-phase thin adapter works
- [ ] Data migration is idempotent
- [ ] Mutual exclusion (EXCLUSIVE vs SHARED) enforced
- [ ] Three-level auto-association (serialNumber → oobAddress → managementIp) works
- [ ] All integration tests pass
- [ ] Full regression (zstack/test + premium/test-premium) passes
- [ ] No existing file renames or variable renames (git blame protection)
