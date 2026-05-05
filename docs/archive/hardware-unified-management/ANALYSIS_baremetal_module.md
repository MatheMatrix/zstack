         # ZStack Baremetal Module Analysis Report

         ## Document Information
         - **Version**: 1.0
         - **Date**: 2026-01-29
         - **Author**: Baremetal Architecture Expert
         - **Scope**: Traditional Baremetal Module Deep Analysis

         ---

         ## 1. Module Overview

         ### 1.1 Module Location

         - H:\ZStack\zstack\premium\baremetal\
         - H:\ZStack\zstack\header\src\main\java\org\zstack\header\baremetal\

         ### 1.2 Architecture Summary

         The Baremetal module provides complete physical server lifecycle management capabilities, including:
         - **Chassis Management**: Physical server registration, IPMI credential management, hardware discovery
         - **Instance Management**: OS deployment on bare metal servers via PXE boot
         - **PXE Server Management**: Network boot infrastructure for OS provisioning
         - **Network Management**: NIC configuration, bonding, VLAN support

         ### 1.3 Core Components

         | Component | Package | Description |
         |-----------|---------|-------------|
         | BaremetalChassisManagerImpl | org.zstack.baremetal.chassis | Chassis lifecycle management |
         | BaremetalInstanceManagerImpl | org.zstack.baremetal.instance | Instance lifecycle management |
         | BaremetalPxeServerManagerImpl | org.zstack.baremetal.pxeserver | PXE boot server management |

         ### 1.4 Key Design Patterns

         1. **Chassis/Instance Separation**: Unlike KVM where HostVO represents both hardware and hypervisor, Baremetal separates physical hardware (BaremetalChassisVO) from OS instances
         (BaremetalInstanceVO)
         2. **Out-of-Band Management**: IPMI is the primary control plane for power operations
         3. **PXE Infrastructure**: Network boot is essential for provisioning
         4. **Async Hardware Discovery**: Hardware info collected via callback, not synchronous query
         5. **State Machine Pattern**: Well-defined state transitions with event-driven updates

         ---

         ## 2. Core Value Objects (VOs)

         ### 2.1 BaremetalChassisVO

         **File**: premium/baremetal/src/main/java/org/zstack/header/baremetal/chassis/BaremetalChassisVO.java

         Represents a physical server chassis with IPMI management capabilities.

         **Key Fields**:

         | Field | Type | Description |
         |-------|------|-------------|
         | ipmiAddress | String | BMC IP address |
         | ipmiPort | Integer | IPMI port (default: 623) |
         | ipmiUsername | String | BMC username |
         | ipmiPassword | String | BMC password (encrypted) |
         | state | Enum | Administrative state (Enabled/Disabled) |
         | status | Enum | Operational status |
         | hardwareInfos | Set | Discovered hardware components |
         | zoneUuid | String | Parent zone reference |
         | clusterUuid | String | Parent cluster reference |
         | pxeServerUuid | String | Associated PXE server |

         ### 2.2 BaremetalInstanceVO

         **File**: premium/baremetal/src/main/java/org/zstack/header/baremetal/instance/BaremetalInstanceVO.java

         Represents an OS instance deployed on a baremetal chassis.

         **Key Fields**:

         | Field | Type | Description |
         |-------|------|-------------|
         | chassisUuid | String | Associated physical chassis |
         | imageUuid | String | OS image for provisioning |
         | templateUuid | String | Installation template |
         | managementIp | String | Instance management IP |
         | state | Enum | Instance lifecycle state |
         | status | Enum | Provisioning status |
         | bmNics | Set | Network interfaces |
         | platform | String | Linux, Windows, etc. |

         ### 2.3 BaremetalPxeServerVO

         **File**: premium/baremetal/src/main/java/org/zstack/header/baremetal/pxeserver/BaremetalPxeServerVO.java

         Represents a PXE boot server for network-based OS provisioning.

         **Key Fields**:

         | Field | Type | Description |
         |-------|------|-------------|
         | hostname | String | PXE server hostname/IP |
         | sshUsername/Password | String | SSH credentials for management |
         | storagePath | String | Image storage path |
         | dhcpInterface | String | Network interface for DHCP |
         | dhcpRangeBegin/End | String | DHCP IP range |
         | state | Enum | Administrative state |
         | status | Enum | Connection status |

         ### 2.4 BaremetalHardwareInfoVO

         **File**: premium/baremetal/src/main/java/org/zstack/header/baremetal/chassis/BaremetalHardwareInfoVO.java

         Stores discovered hardware information for a chassis.

         **Key Fields**:

         | Field | Type | Description |
         |-------|------|-------------|
         | chassisUuid | String | Parent chassis reference |
         | type | String | CPU, Memory, Storage, NIC, System |
         | content | String | JSON content of hardware details |

         ### 2.5 Supporting VOs Summary

         | VO | Purpose |
         |----|---------|
         | BaremetalNicVO | Network interface on instance |
         | BaremetalPxeServerClusterRefVO | PXE Server to Cluster association |
         | BaremetalBondingVO | NIC bonding configuration |
         | PreconfigurationTemplateVO | OS installation templates |
         | BaremetalImageCacheVO | Cached images on PXE server |

         ---

         ## 3. IPMI/Redfish Management

         ### 3.1 IPMI Credential Storage

         IPMI credentials are stored in BaremetalChassisVO:
         - ipmiAddress: BMC IP address
         - ipmiPort: IPMI port (default: 623)
         - ipmiUsername: BMC username
         - ipmiPassword: BMC password (encrypted via @ENCRYPTParam)

         ### 3.2 IPMI Operations

         | Operation | API Message |
         |-----------|-------------|
         | Power On | APIPowerOnBaremetalChassisMsg |
         | Power Off | APIPowerOffBaremetalChassisMsg |
         | Power Reset | APIPowerResetBaremetalChassisMsg |
         | Get Power Status | APIGetBaremetalChassisPowerStatusMsg |
         | Inspect Hardware | APIInspectBaremetalChassisMsg |

         ### 3.3 Power Status Values

         - SERVER_POWER_ON = "on"
         - SERVER_POWER_OFF = "off"
         - SERVER_POWER_UNKNOWN = "unknown"

         ### 3.4 Hardware Discovery Flow

         1. APIInspectBaremetalChassisMsg
         2. Set chassis status = PxeBooting
         3. Configure boot device = PXE via IPMI
         4. Power reset chassis via IPMI
         5. Chassis boots into discovery environment
         6. Discovery agent collects hardware info
         7. Agent sends data to: /baremetal/chassis/sendhardwareinfo
         8. Create/Update BaremetalHardwareInfoVO records
         9. Set chassis status = Available

         ---

         ## 4. PXE Boot Flow

         ### 4.1 PXE Server Architecture

         Management Node <--> PXE Server (DHCP/TFTP/HTTP) <--> Baremetal Chassis (IPMI/BMC)

         ### 4.2 Instance Provisioning Flow

         1. APICreateBaremetalInstanceMsg
         2. Validate chassis available (status = Available)
         3. Occupy Chassis (set status = Allocated)
         4. Create BaremetalInstanceVO (state = Created)
         5. Create BaremetalNicVO records
         6. Allocate IPs from L3 Networks
         7. Generate preconfiguration (kickstart/preseed)
         8. [If InstantStart] StartBaremetalInstanceMsg
         9. Upload instance configs to PXE server
         10. Set boot device = PXE via IPMI
         11. Power reset chassis via IPMI
         12. Chassis PXE boots, gets IP from dnsmasq
         13. Downloads kernel/initrd via TFTP
         14. NOTIFY_DEPLOY_BEGIN callback (status = Provisioning)
         15. Downloads OS image via HTTP
         16. OS installation proceeds
         17. NOTIFY_DEPLOY_COMPLETE callback (status = Provisioned)
         18. Cleanup PXE configs
         19. Reboot to local disk
         20. NOTIFY_OS_RUNNING callback (state = Running)

         ### 4.3 Deployment Notification Endpoints

         - NOTIFY_DEPLOY_BEGIN = "/baremetal/instance/deploybegin"
         - NOTIFY_DEPLOY_COMPLETE = "/baremetal/instance/deploycomplete"
         - NOTIFY_OS_RUNNING = "/baremetal/instance/osrunning"

         ---

         ## 5. State Machines

         ### 5.1 BaremetalChassisState

         | State | Description |
         |-------|-------------|
         | Enabled | Chassis is operational |
         | Disabled | Chassis is administratively disabled |

         ### 5.2 BaremetalChassisStatus

         | Status | Description |
         |--------|-------------|
         | HWInfoUnknown | Hardware info not yet discovered |
         | PxeBooting | Chassis is PXE booting for discovery |
         | PxeBootFailed | PXE boot for discovery failed |
         | Available | Ready for instance allocation |
         | Allocated | Instance allocated to this chassis |

         ### 5.3 BaremetalInstanceState

         | State | Description |
         |-------|-------------|
         | Created | Instance created but not yet started |
         | Starting | Instance is powering on / provisioning |
         | Running | Instance is running |
         | Stopped | Instance is powered off |
         | Rebooting | Instance is rebooting |
         | Destroyed | Instance is destroyed (soft delete) |
         | UNKNOWN | Power state cannot be determined |
         | Error | Error occurred during lifecycle operation |

         ### 5.4 BaremetalInstanceStatus

         | Status | Description |
         |--------|-------------|
         | Unprovisioned | OS not yet installed |
         | Provisioning | OS installation in progress |
         | Provisioned | OS installation complete |

         ### 5.5 BaremetalPxeServerState

         | State | Description |
         |-------|-------------|
         | Enabled | PXE server is operational |
         | Disabled | PXE server is disabled |
         | Maintenance | PXE server is in maintenance mode |

         ### 5.6 BaremetalPxeServerStatus

         | Status | Description |
         |--------|-------------|
         | Connecting | Connection in progress |
         | Connected | PXE server is connected |
         | Disconnected | PXE server is unreachable |

         ---

         ## 6. API Catalog

         ### 6.1 Chassis APIs

         | API Message | Description |
         |-------------|-------------|
         | APICreateBaremetalChassisMsg | Create a new chassis |
         | APIUpdateBaremetalChassisMsg | Update chassis properties |
         | APIDeleteBaremetalChassisMsg | Delete a chassis |
         | APIQueryBaremetalChassisMsg | Query chassis |
         | APIBatchCreateBaremetalChassisMsg | Batch create chassis from CSV |
         | APICheckBaremetalChassisConfigFileMsg | Validate batch create config file |
         | APIChangeBaremetalChassisStateMsg | Enable/Disable chassis |
         | APIPowerOnBaremetalChassisMsg | Power on chassis via IPMI |
         | APIPowerOffBaremetalChassisMsg | Power off chassis via IPMI |
         | APIPowerResetBaremetalChassisMsg | Power reset chassis via IPMI |
         | APIGetBaremetalChassisPowerStatusMsg | Get power status via IPMI |
         | APIInspectBaremetalChassisMsg | Trigger hardware discovery |
         | APICleanUpBaremetalChassisBondingMsg | Clean up bonding configs |

         ### 6.2 Instance APIs

         | API Message | Description |
         |-------------|-------------|
         | APICreateBaremetalInstanceMsg | Create instance on chassis |
         | APIUpdateBaremetalInstanceMsg | Update instance properties |
         | APIDestroyBaremetalInstanceMsg | Destroy instance (soft delete) |
         | APIExpungeBaremetalInstanceMsg | Permanently delete instance |
         | APIRecoverBaremetalInstanceMsg | Recover destroyed instance |
         | APIQueryBaremetalInstanceMsg | Query instances |
         | APIStartBaremetalInstanceMsg | Start/provision instance |
         | APIStopBaremetalInstanceMsg | Stop instance |
         | APIRebootBaremetalInstanceMsg | Reboot instance |

         ### 6.3 PXE Server APIs

         | API Message | Description |
         |-------------|-------------|
         | APICreateBaremetalPxeServerMsg | Create PXE server |
         | APIUpdateBaremetalPxeServerMsg | Update PXE server |
         | APIDeleteBaremetalPxeServerMsg | Delete PXE server |
         | APIQueryBaremetalPxeServerMsg | Query PXE servers |
         | APIStartBaremetalPxeServerMsg | Start PXE services |
         | APIStopBaremetalPxeServerMsg | Stop PXE services |
         | APIReconnectBaremetalPxeServerMsg | Reconnect to PXE server |
         | APIAttachBaremetalPxeServerToClusterMsg | Attach PXE to cluster |
         | APIDetachBaremetalPxeServerFromClusterMsg | Detach PXE from cluster |

         ### 6.4 Network APIs

         | API Message | Description |
         |-------------|-------------|
         | APICreateBaremetalBondingMsg | Create NIC bonding |
         | APIQueryBaremetalBondingMsg | Query bonding configs |

         ### 6.5 Preconfiguration APIs

         | API Message | Description |
         |-------------|-------------|
         | APIAddPreconfigurationTemplateMsg | Add installation template |
         | APIUpdatePreconfigurationTemplateMsg | Update template |
         | APIDeletePreconfigurationTemplateMsg | Delete template |
         | APIQueryPreconfigurationTemplateMsg | Query templates |
         | APIChangePreconfigurationTemplateStateMsg | Enable/Disable template |

         ---

         ## 7. Mapping to Header v1.2 Unified Interface

         ### 7.1 Entity Mapping

         | Baremetal Entity | Unified Header Entity | Mapping Notes |
         |------------------|----------------------|---------------|
         | BaremetalChassisVO | UnifiedHardwareVO | Physical server abstraction; 1:1 mapping |
         | BaremetalInstanceVO | (Separate concept) | OS instance; NOT mapped to UnifiedHardwareVO |
         | BaremetalChassisVO.ipmi* | HardwareOobInfoVO | Out-of-band management credentials |
         | BaremetalHardwareInfoVO | HardwareCapabilityVO | Discovered hardware specifications |
         | BaremetalNicVO | HardwareNicInfoVO | Network interface data |

         ### 7.2 State Mapping

         | Baremetal State | Unified State |
         |-----------------|---------------|
         | BaremetalChassisState.Enabled | UnifiedHardwareState.ENABLED |
         | BaremetalChassisState.Disabled | UnifiedHardwareState.DISABLED |
         | BaremetalChassisStatus.Available | UnifiedHardwareStatus.AVAILABLE |
         | BaremetalChassisStatus.Allocated | UnifiedHardwareStatus.ALLOCATED |
         | BaremetalChassisStatus.HWInfoUnknown | UnifiedHardwareStatus.DISCOVERING |
         | BaremetalChassisStatus.PxeBooting | UnifiedHardwareStatus.DISCOVERING |
         | BaremetalChassisStatus.PxeBootFailed | UnifiedHardwareStatus.DISCOVERY_FAILED |

         ### 7.3 Capability Mapping

         | Baremetal Capability | Unified Capability |
         |---------------------|-------------------|
         | IPMI Power Control | OOB_POWER_MANAGEMENT |
         | PXE Boot | NETWORK_BOOT |
         | Hardware Discovery | HARDWARE_DISCOVERY |
         | Serial Console (NoVNC) | CONSOLE_ACCESS |
         | NIC Bonding | NIC_BONDING |

         ### 7.4 Key Differences from KVM/Container Hosts

         | Aspect | Baremetal | KVM Host | Container Host |
         |--------|-----------|----------|----------------|
         | Hardware Ownership | Exclusive (1:1) | Shared (many VMs) | Shared (many containers) |
         | Provisioning | PXE boot, full OS install | Agent install | Agent install |
         | Power Control | IPMI out-of-band | N/A | N/A |
         | State Discovery | IPMI queries | Agent heartbeat | Agent heartbeat |
         | Console Access | IPMI SOL, NoVNC | VNC, SSH | SSH |

         ### 7.5 Integration Considerations

         1. **Chassis/Instance Separation**: The unified interface must preserve the distinction between physical hardware (BaremetalChassisVO) and OS instance (BaremetalInstanceVO)

         2. **Out-of-Band Management**: IPMI credentials and operations are critical. The unified interface must support credential storage (encrypted), async IPMI operations with timeout handling, and
         power state caching

         3. **PXE Server Dependency**: Baremetal instances require PXE server for provisioning - model as infrastructure dependency

         4. **Hardware Discovery**: Support pluggable discovery mechanisms for the unique PXE-based discovery flow

         5. **Bidirectional Synchronization**: BaremetalChassisVO to UnifiedHardwareVO (via HardwareReferenceVO) and reverse sync for changes

         ---

         ## 8. Conclusion

         The Baremetal module is a mature, feature-complete implementation for physical server management. Key architectural patterns that MUST be preserved in the unified hardware management interface:

         1. **Chassis/Instance Separation**: Physical hardware lifecycle is separate from OS instance lifecycle
         2. **Out-of-Band Management**: IPMI is the primary control plane for power operations
         3. **PXE Infrastructure**: Network boot is essential for provisioning
         4. **Async Hardware Discovery**: Hardware info collected via callback, not synchronous query
         5. **State Machine Pattern**: Well-defined state transitions with event-driven updates
         6. **Cascade Cleanup**: Proper resource dependency management

         ---

         ## Appendix: File Locations

         | Component | Path |
         |-----------|------|
         | Chassis Manager | premium/baremetal/src/main/java/org/zstack/baremetal/chassis/BaremetalChassisManagerImpl.java |
         | Instance Manager | premium/baremetal/src/main/java/org/zstack/baremetal/instance/BaremetalInstanceManagerImpl.java |
         | PXE Server Manager | premium/baremetal/src/main/java/org/zstack/baremetal/pxeserver/BaremetalPxeServerManagerImpl.java |
         | Chassis VO | premium/baremetal/src/main/java/org/zstack/header/baremetal/chassis/BaremetalChassisVO.java |
         | Instance VO | premium/baremetal/src/main/java/org/zstack/header/baremetal/instance/BaremetalInstanceVO.java |
         | PXE Server VO | premium/baremetal/src/main/java/org/zstack/header/baremetal/pxeserver/BaremetalPxeServerVO.java |
         | Hardware Info VO | premium/baremetal/src/main/java/org/zstack/header/baremetal/chassis/BaremetalHardwareInfoVO.java |