# Baremetal2 (Elastic Bare Metal) Module Analysis Report

## Document Information
- **Module Path**: `H:\ZStack\zstack\premium\baremetal2\`
- **Analysis Date**: 2025-01-29
- **Author**: Baremetal2 Expert Agent

---

## 1. Module Overview

### 1.1 Module Definition

Baremetal2 is ZStack's second-generation elastic bare metal management module, providing complete lifecycle management for physical servers. Unlike virtualized hosts (KVM), Baremetal2 uses out-of-band management (IPMI/Redfish) for hardware control and PXE-based network boot for OS provisioning.

### 1.2 Core Package Structure

```
premium/baremetal2/src/main/java/org/zstack/baremetal2/
|-- BareMetal2Constant.java              # Module constants
|-- BareMetal2Exception.java             # Custom exceptions
|-- BareMetal2GlobalConfig.java          # Global configuration
|-- BareMetal2ProvisionType.java         # Provision type enum
|-- chassis/                             # Chassis management (physical server)
|   |-- BareMetal2ChassisVO.java         # Core chassis entity
|   |-- BareMetal2ChassisAO.java         # Abstract base class
|   |-- BareMetal2ChassisNicVO.java      # Chassis NIC info
|   |-- BareMetal2ChassisDiskVO.java     # Chassis disk info
|   |-- BareMetal2BondingVO.java         # Network bonding
|   |-- ipmi/                            # IPMI-specific implementation
|       |-- BareMetal2IpmiChassisVO.java # IPMI chassis extension
|-- configuration/                       # Configuration management
|   |-- BareMetal2ChassisOfferingVO.java # Chassis specification templates
|-- gateway/                             # Gateway management (PXE/DHCP server)
|   |-- BareMetal2GatewayVO.java         # Gateway entity
|   |-- BareMetal2GatewayClusterRefVO.java  # N:N cluster relationship
|   |-- BareMetal2GatewayProvisionNicVO.java # Gateway provision NIC
|   |-- allocator/                       # Gateway allocation strategies
|-- instance/                            # Bare metal instance management
|   |-- BareMetal2InstanceVO.java        # Instance entity
|   |-- BareMetal2InstanceProvisionNicVO.java # Instance provision NIC
|-- provisionnetwork/                    # Provision network management
|   |-- BareMetal2ProvisionNetworkVO.java      # Provision network
|   |-- BareMetal2ProvisionNetworkClusterRefVO.java # Cluster relationship
```

### 1.3 Key Characteristics

| Feature | Description |
|---------|-------------|
| **Management Mode** | Out-of-band (IPMI/Redfish) |
| **Provisioning** | PXE-based network boot |
| **Power Control** | Physical power management via BMC |
| **Hardware Discovery** | Automatic via IPMI/inspection |
| **Instance Lifecycle** | Different from VM (physical constraints) |

---

## 2. Core VO Structures

### 2.1 BareMetal2ChassisVO (Physical Chassis)

**Inheritance Hierarchy**:
```
ResourceVO
    |-- BareMetal2ChassisAO (MappedSuperclass)
        |-- BareMetal2ChassisVO
            |-- BareMetal2IpmiChassisVO (IPMI extension)
```

**Core Fields** (`BareMetal2ChassisAO`):

| Field | Type | Description |
|-------|------|-------------|
| `uuid` | String | Unique identifier (from ResourceVO) |
| `name` | String | Chassis name |
| `description` | String | Description |
| `zoneUuid` | String | Parent zone reference |
| `clusterUuid` | String | Parent cluster reference |
| `chassisOfferingUuid` | String | Associated offering template |
| `type` | String | Chassis type (e.g., "ipmi") |
| `provisionType` | BareMetal2ProvisionType | Remote/Local/Direct |
| `state` | BareMetal2ChassisState | Enabled/Disabled |
| `status` | BareMetal2ChassisStatus | Lifecycle status |
| `powerStatus` | BareMetal2ChassisPowerStatus | Power state |
| `chassisNics` | Set<BareMetal2ChassisNicVO> | Network interfaces |
| `chassisDisks` | Set<BareMetal2ChassisDiskVO> | Storage devices |

**IPMI Extension Fields** (`BareMetal2IpmiChassisVO`):

| Field | Type | Description |
|-------|------|-------------|
| `ipmiAddress` | String | BMC IP address |
| `ipmiPort` | Integer | IPMI port (default 623) |
| `ipmiUsername` | String | IPMI credentials |
| `ipmiPassword` | String | IPMI password (encrypted) |

**Entity Relationships**:
```java
@EntityGraph(
    parents = {
        @EntityGraph.Neighbour(type = ZoneVO.class, myField = "zoneUuid"),
        @EntityGraph.Neighbour(type = ClusterVO.class, myField = "clusterUuid"),
    },
    friends = {
        @EntityGraph.Neighbour(type = BareMetal2ChassisNicVO.class, myField = "uuid"),
        @EntityGraph.Neighbour(type = BareMetal2ChassisDiskVO.class, myField = "uuid"),
        @EntityGraph.Neighbour(type = BareMetal2ChassisOfferingVO.class, myField = "chassisOfferingUuid"),
    }
)
```

### 2.2 BareMetal2GatewayVO (Provisioning Gateway)

**Inheritance Hierarchy**:
```
ResourceVO
    |-- HostAO
        |-- HostVO
            |-- KVMHostVO
                |-- BareMetal2GatewayVO
```

**Key Design Decision**: Gateway extends KVMHostVO because it runs on a KVM host that provides PXE/DHCP/TFTP services.

**Core Fields**:

| Field | Type | Description |
|-------|------|-------------|
| `uuid` | String | Gateway UUID (same as underlying host) |
| `attachedClusterRefs` | Set<BareMetal2GatewayClusterRefVO> | N:N cluster relationships |
| `provisionNic` | BareMetal2GatewayProvisionNicVO | Provision network interface |

**Gateway Provision NIC** (`BareMetal2GatewayProvisionNicVO`):

| Field | Type | Description |
|-------|------|-------------|
| `uuid` | String | Same as gateway UUID |
| `networkUuid` | String | Provision network reference |
| `interfaceName` | String | Network interface name |
| `ip` | String | Gateway IP on provision network |
| `netmask` | String | Network mask |
| `gateway` | String | Default gateway |

### 2.3 BareMetal2ChassisOfferingVO (Specification Template)

**Purpose**: Auto-created template representing hardware specifications for chassis selection.

**Core Fields**:

| Field | Type | Description |
|-------|------|-------------|
| `uuid` | String | Unique identifier |
| `name` | String | Offering name |
| `description` | String | Description |
| `architecture` | String | CPU architecture (x86_64, aarch64) |
| `cpuModelName` | String | CPU model |
| `cpuNum` | Integer | Number of CPUs |
| `memorySize` | Long | Total memory in bytes |
| `provisionType` | BareMetal2ProvisionType | Provisioning type |
| `bootMode` | BareMetal2ChassisBootMode | UEFI/Legacy |
| `state` | BareMetal2ChassisOfferingState | Enabled/Disabled |

**Key Note**: ChassisOffering is auto-created during hardware inspection, similar to PciDeviceSpecVO pattern.

### 2.4 BareMetal2InstanceVO (Deployed Instance)

**Inheritance Hierarchy**:
```
ResourceVO
    |-- VmInstanceAO
        |-- VmInstanceVO
            |-- BareMetal2InstanceVO
```

**Core Fields**:

| Field | Type | Description |
|-------|------|-------------|
| `uuid` | String | Instance UUID |
| `chassisUuid` | String | Currently allocated chassis |
| `lastChassisUuid` | String | Previous chassis (for restart) |
| `gatewayUuid` | String | Currently assigned gateway |
| `lastGatewayUuid` | String | Previous gateway |
| `chassisOfferingUuid` | String | Hardware specification |
| `gatewayAllocatorStrategy` | String | Gateway selection strategy |
| `provisionType` | BareMetal2ProvisionType | Provisioning type |
| `status` | BareMetal2InstanceStatus | Connection status |
| `provisionNics` | Set<BareMetal2InstanceProvisionNicVO> | Provision NICs |

---

## 3. Gateway N:N Allocation Mechanism

### 3.1 Cluster-Gateway Relationship

The `BareMetal2GatewayClusterRefVO` enables **many-to-many** relationship between gateways and clusters:

```
Cluster1 ----+---- Gateway1 (provides PXE/DHCP for Cluster1, Cluster2)
             |
Cluster2 ----+---- Gateway2 (provides PXE/DHCP for Cluster2, Cluster3)
             |
Cluster3 ----+
```

**Schema Definition**:
```java
@Entity
@Table
public class BareMetal2GatewayClusterRefVO {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column
    @ForeignKey(parentEntityClass = ClusterEO.class, onDeleteAction = CASCADE)
    private String clusterUuid;

    @Column
    @ForeignKey(parentEntityClass = BareMetal2GatewayVO.class, onDeleteAction = CASCADE)
    private String gatewayUuid;
}
```

### 3.2 Gateway Allocator Strategies

Located in `gateway/allocator/`, three strategies are available:

| Strategy | Description |
|----------|-------------|
| `DefaultGatewayAllocatorStrategy` | Random selection from available gateways |
| `LastGatewayPreferredAllocatorStrategy` | Prefer the last used gateway for instance restart |
| `LeastBmPreferredGatewayAllocatorStrategy` | Select gateway with fewest attached instances |

**Allocator Flow**:
```
AllocateBareMetal2GatewayMsg
    |
    v
BareMetal2GatewayAllocatorStrategyFactory.getStrategy()
    |
    v
AbstractGatewayAllocatorStrategy.allocate()
    |-- BareMetal2InstanceQuotaAllocatorFlow
    |-- BareMetal2GatewayMainAllocatorFlow
    |-- *SortFlow (based on strategy)
    |
    v
AllocateBareMetal2GatewayReply
```

### 3.3 Allocation Message Structure

```java
public class AllocateBareMetal2GatewayMsg extends NeedReplyMessage {
    private boolean dryRun;
    private String requiredZoneUuid;
    private List<String> requiredClusterUuids;
    private String requiredGatewayUuid;
    private List<String> avoidGatewayUuids;
    private String bareMetal2InstanceUuid;
    private String allocatorStrategy;
}
```

---

## 4. Ternary State Model

### 4.1 State (Administrative State)

```java
public enum BareMetal2ChassisState {
    Enabled,   // Chassis can be allocated
    Disabled;  // Chassis cannot be allocated

    // State transitions
    Enabled -> Disabled (event: disable)
    Disabled -> Enabled (event: enable)
}
```

### 4.2 Status (Lifecycle Status)

```java
public enum BareMetal2ChassisStatus {
    HardwareInfoUnknown,  // Hardware not inspected
    IPxeBooting,          // PXE boot in progress
    IPxeBootFailed,       // PXE boot failed
    WrongBootMode,        // Boot mode mismatch (UEFI vs Legacy)
    WrongArchitecture,    // Architecture mismatch
    Available,            // Ready for allocation
    Allocated,            // Assigned to an instance
}
```

### 4.3 PowerStatus (Physical Power State)

```java
public enum BareMetal2ChassisPowerStatus {
    POWER_ON,       // Chassis is powered on
    POWER_OFF,      // Chassis is powered off
    POWER_UNKNOWN   // Cannot determine power state
}
```

### 4.4 Instance Status (Connection Status)

```java
public enum BareMetal2InstanceStatus {
    Connecting,      // Establishing connection to instance
    Connected,       // Instance is connected and healthy
    Disconnected,    // Lost connection to instance
    Converting,      // Being converted to other type
    Converted,       // Conversion complete
    ConvertFailed;   // Conversion failed
}
```

### 4.5 State Diagram

```
                     +----------------+
                     | HardwareInfo   |
                     | Unknown        |
                     +-------+--------+
                             |
                    [Inspect Chassis]
                             |
                             v
     +-------------+   +----------------+   +-------------+
     | WrongBoot   |<--| IPxeBooting    |-->| WrongArch   |
     | Mode        |   +-------+--------+   |             |
     +-------------+           |            +-------------+
                               |
                    [Inspection Success]
                               |
                               v
                     +----------------+
                     | Available      |<--------+
                     +-------+--------+         |
                             |                  |
                    [Allocate Chassis]   [Release Chassis]
                             |                  |
                             v                  |
                     +----------------+---------+
                     | Allocated      |
                     +----------------+
```

---

## 5. Deployment/Allocation/Usage Workflows

### 5.1 Deployment Workflow (Chassis Registration)

```
Step 1: Add Chassis
    APIAddBareMetal2IpmiChassisMsg
        |-- ipmiAddress, ipmiPort, ipmiUsername, ipmiPassword
        |-- clusterUuid, name, description
        |
        v
    BareMetal2ChassisFactory.createBareMetal2Chassis()
        |
        v
    BareMetal2IpmiChassisVO created (status: HardwareInfoUnknown)

Step 2: Hardware Inspection
    APIInspectBareMetal2ChassisMsg
        |
        v
    PXE boot -> iPXE -> Inspection Agent
        |
        v
    Collect: CPU, Memory, Disk, NIC information
        |
        v
    BareMetal2ChassisNicVO, BareMetal2ChassisDiskVO created
    BareMetal2ChassisOfferingVO auto-created
    Chassis status -> Available
```

### 5.2 Allocation Workflow (Chassis Selection)

```
Step 1: Create Instance Request
    APICreateBareMetal2InstanceMsg
        |-- chassisOfferingUuid OR chassisUuid
        |-- imageUuid, clusterUuid
        |-- gatewayAllocatorStrategy
        |
        v

Step 2: Allocate Chassis
    AllocateBareMetal2ChassisMsg
        |-- requiredZoneUuid, requiredClusterUuids
        |-- chassisOfferingUuid (match hardware specs)
        |-- avoidChassisUuids
        |
        v
    Filter by:
        1. Zone/Cluster constraints
        2. ChassisOffering match
        3. State = Enabled
        4. Status = Available
        5. Avoid list exclusion
        |
        v
    Chassis selected, status -> Allocated

Step 3: Allocate Gateway
    AllocateBareMetal2GatewayMsg
        |-- requiredClusterUuids
        |-- allocatorStrategy
        |
        v
    Gateway selected based on strategy
```

### 5.3 Usage Workflow (Instance Lifecycle)

```
Step 1: Instance Provisioning
    Start Instance
        |
        v
    Power on chassis (IPMI)
        |
        v
    PXE boot from Gateway
        |
        v
    Download and install OS image
        |
        v
    Boot into installed OS
        |
        v
    Agent connects back to ZStack
        |
        v
    Instance status -> Connected

Step 2: Power Management
    APIPowerOnBareMetal2ChassisMsg  -> IPMI power on
    APIPowerOffBareMetal2ChassisMsg -> IPMI power off
    APIPowerResetBareMetal2ChassisMsg -> IPMI power cycle

Step 3: Instance Stop/Delete
    Stop Instance
        |
        v
    [If chassisUuid specified] -> Chassis remains Allocated (bound)
    [If chassisOfferingUuid]   -> Chassis released to Available (elastic)
```

---

## 6. API Message Catalog

### 6.1 Chassis Management APIs

| API Message | Type | Description |
|-------------|------|-------------|
| `APIAddBareMetal2ChassisMsg` | Create | Add single chassis |
| `APIAddBareMetal2IpmiChassisMsg` | Create | Add IPMI chassis with credentials |
| `APIBatchAddBareMetal2ChassisMsg` | Create | Batch add chassis from file |
| `APIBatchAddBareMetal2IpmiChassisMsg` | Create | Batch add IPMI chassis |
| `APIDeleteBareMetal2ChassisMsg` | Delete | Remove chassis |
| `APIUpdateBareMetal2ChassisMsg` | Update | Modify chassis properties |
| `APIUpdateBareMetal2IpmiChassisMsg` | Update | Modify IPMI credentials |
| `APIChangeBareMetal2ChassisStateMsg` | Update | Enable/Disable chassis |
| `APIQueryBareMetal2ChassisMsg` | Query | Query chassis |
| `APIInspectBareMetal2ChassisMsg` | Action | Trigger hardware inspection |
| `APIGetBareMetal2ChassisPowerStatusMsg` | Query | Get power status |
| `APIPowerOnBareMetal2ChassisMsg` | Action | Power on chassis |
| `APIPowerOffBareMetal2ChassisMsg` | Action | Power off chassis |
| `APIPowerResetBareMetal2ChassisMsg` | Action | Reset chassis power |
| `APIGetBareMetal2SupportedBootModeMsg` | Query | Get supported boot modes |
| `APICheckBareMetal2ChassisConfigFileMsg` | Query | Validate config file |
| `APICreateBareMetal2ChassisHardwareInfoMsg` | Create | Manually create hardware info |

### 6.2 Gateway Management APIs

| API Message | Type | Description |
|-------------|------|-------------|
| `APIAddBareMetal2GatewayMsg` | Create | Add gateway |
| `APIDeleteBareMetal2GatewayMsg` | Delete | Remove gateway |
| `APIUpdateBareMetal2GatewayMsg` | Update | Modify gateway |
| `APIChangeBareMetal2GatewayStateMsg` | Update | Enable/Disable gateway |
| `APIQueryBareMetal2GatewayMsg` | Query | Query gateways |
| `APIReconnectBareMetal2GatewayMsg` | Action | Reconnect gateway |
| `APIAttachBareMetal2GatewayToClusterMsg` | Attach | Attach to cluster |
| `APIDetachBareMetal2GatewayFromClusterMsg` | Detach | Detach from cluster |
| `APIChangeBareMetal2GatewayClusterMsg` | Update | Change cluster association |
| `APIGetBareMetal2GatewayAllocatorStrategiesMsg` | Query | List allocation strategies |

### 6.3 Instance Management APIs

| API Message | Type | Description |
|-------------|------|-------------|
| `APICreateBareMetal2InstanceMsg` | Create | Create instance |
| `APIQueryBareMetal2InstanceMsg` | Query | Query instances |
| `APIUpdateBareMetal2InstanceMsg` | Update | Update instance |
| `APIStartBareMetal2InstanceMsg` | Action | Start instance |
| `APIReconnectBareMetal2InstanceMsg` | Action | Reconnect instance |
| `APIChangeBareMetal2InstancePasswordMsg` | Action | Change password |
| `APIAttachProvisionNicToBondingMsg` | Attach | Attach NIC to bonding |
| `APIDetachProvisionNicFromBondingMsg` | Detach | Detach NIC from bonding |

### 6.4 Provision Network APIs

| API Message | Type | Description |
|-------------|------|-------------|
| `APICreateBareMetal2ProvisionNetworkMsg` | Create | Create provision network |
| `APIDeleteBareMetal2ProvisionNetworkMsg` | Delete | Delete provision network |
| `APIUpdateBareMetal2ProvisionNetworkMsg` | Update | Update provision network |
| `APIChangeBareMetal2ProvisionNetworkStateMsg` | Update | Change state |
| `APIQueryBareMetal2ProvisionNetworkMsg` | Query | Query provision networks |
| `APIAttachBareMetal2ProvisionNetworkToClusterMsg` | Attach | Attach to cluster |
| `APIDetachBareMetal2ProvisionNetworkFromClusterMsg` | Detach | Detach from cluster |
| `APIGetBareMetal2ProvisionNetworkIpAddressCapacityMsg` | Query | Get IP capacity |

### 6.5 Configuration APIs

| API Message | Type | Description |
|-------------|------|-------------|
| `APIQueryBareMetal2ChassisOfferingMsg` | Query | Query offerings |
| `APIUpdateBareMetal2ChassisOfferingMsg` | Update | Update offering |
| `APIChangeBareMetal2ChassisOfferingStateMsg` | Update | Enable/Disable offering |

### 6.6 Bonding APIs

| API Message | Type | Description |
|-------------|------|-------------|
| `APICreateBareMetal2BondingMsg` | Create | Create NIC bonding |
| `APIQueryBareMetal2BondingMsg` | Query | Query bondings |
| `APIQueryBareMetal2BondingNicRefMsg` | Query | Query bonding NIC refs |
| `APICleanUpBareMetal2BondingMsg` | Delete | Clean up bondings |

---

## 7. Header Plan v1.2 Mapping Analysis

### 7.1 Unified Hardware Management Interface Requirements

Based on the Header Plan v1.2 for unified hardware management, the following mapping analysis is provided:

#### 7.1.1 HardwareTypeDiscriminator Mapping

| Unified Interface | Baremetal2 Implementation |
|-------------------|---------------------------|
| `getHardwareType()` | Returns `HardwareType.BAREMETAL` |
| `getManagementMode()` | Returns `ManagementMode.OUT_OF_BAND` |
| `getResourceUuid()` | `BareMetal2ChassisVO.uuid` |

#### 7.1.2 PowerManagementInterface Mapping

| Unified Method | Baremetal2 Implementation |
|----------------|---------------------------|
| `powerOn()` | `APIPowerOnBareMetal2ChassisMsg` -> IPMI power on |
| `powerOff()` | `APIPowerOffBareMetal2ChassisMsg` -> IPMI power off |
| `powerReset()` | `APIPowerResetBareMetal2ChassisMsg` -> IPMI reset |
| `getPowerStatus()` | `APIGetBareMetal2ChassisPowerStatusMsg` |

#### 7.1.3 HardwareDiscoveryInterface Mapping

| Unified Method | Baremetal2 Implementation |
|----------------|---------------------------|
| `discoverHardware()` | `APIInspectBareMetal2ChassisMsg` |
| `getCpuInfo()` | From `BareMetal2ChassisOfferingVO` |
| `getMemoryInfo()` | From `BareMetal2ChassisOfferingVO` |
| `getDiskInfo()` | From `BareMetal2ChassisDiskVO` collection |
| `getNicInfo()` | From `BareMetal2ChassisNicVO` collection |

#### 7.1.4 ProvisioningInterface Mapping

| Unified Method | Baremetal2 Implementation |
|----------------|---------------------------|
| `provision()` | PXE-based deployment via Gateway |
| `getProvisioningType()` | `BareMetal2ProvisionType` (Remote/Local/Direct) |
| `getProvisioningStatus()` | `BareMetal2ChassisStatus` |

#### 7.1.5 MonitoringInterface Mapping

| Unified Method | Baremetal2 Implementation |
|----------------|---------------------------|
| `getHealthStatus()` | Via IPMI sensor data |
| `getConnectionStatus()` | `BareMetal2InstanceStatus` |

### 7.2 Unified Hardware VO Reference Mapping

**Proposed Reference Structure**:
```
UnifiedHardwareVO (new)
    |
    +-- HardwareReferenceVO
            |-- unifiedHardwareUuid
            |-- resourceUuid (BareMetal2ChassisVO.uuid)
            |-- resourceType = "BareMetal2ChassisVO"
```

### 7.3 Key Integration Points

| Integration Point | Baremetal2 Requirement |
|-------------------|------------------------|
| **Cluster Association** | Chassis uses `clusterUuid` directly, not through host |
| **Zone Association** | Chassis has direct `zoneUuid` reference |
| **Gateway Dependency** | Instance requires Gateway for provisioning |
| **Elastic vs Bound** | Chassis can be elastic (via Offering) or bound (direct UUID) |
| **Power State Sync** | Must sync power status with unified model |

### 7.4 Recommendations for Unified Interface Design

1. **Preserve Existing APIs**: All existing Baremetal2 APIs must continue to work
2. **Adapter Pattern**: Create `BareMetal2UnifiedHardwareAdapter` implementing unified interfaces
3. **Bidirectional Sync**: Sync power status, connection status between models
4. **Gateway Abstraction**: Consider abstracting Gateway as a "Provisioning Service" in unified model
5. **State Mapping**: Map ternary state model to unified state machine

---

## 8. Summary

### 8.1 Module Strengths

1. **Complete Lifecycle Management**: From chassis registration to instance deployment
2. **Flexible Allocation**: Elastic (offering-based) or bound (direct chassis) modes
3. **N:N Gateway Architecture**: Scalable PXE infrastructure
4. **Rich API Surface**: Comprehensive management capabilities
5. **State Machine**: Well-defined ternary state model

### 8.2 Unique Characteristics for Unified Management

1. **Out-of-band Management**: Unlike KVM hosts, uses IPMI/Redfish
2. **PXE Provisioning**: Network-based OS deployment
3. **Physical Power Control**: Real hardware power management
4. **Hardware Inspection**: Active discovery vs passive registration
5. **Gateway Dependency**: Requires external service for provisioning

### 8.3 Integration Complexity

| Aspect | Complexity | Notes |
|--------|------------|-------|
| State Mapping | Medium | Ternary model to unified model |
| Power Management | Low | Direct interface mapping |
| Hardware Discovery | Medium | Different discovery mechanism |
| Provisioning | High | PXE unique to baremetal |
| Lifecycle | Medium | Different from VM lifecycle |

---

*Document generated for ZStack Unified Hardware Management Project*
