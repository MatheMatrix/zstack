# Baremetal2 模块分析报告

## 1. 与 Baremetal v1 的核心差异

| 维度 | Baremetal v1 | Baremetal2 v2 |
|------|--------------|---------------|
| 继承结构 | 平坦单表 | 三层继承 (AO → VO → IpmiVO) |
| 硬件信息 | 单个 HardwareInfoVO (JSON) | 4个独立 VO (Disk/NIC/PCI/GPU) |
| IPMI 存储 | 在 ChassisVO 主表 | 在 IpmiChassisVO 子表 |
| 部署基础设施 | PxeServerVO (独立服务) | GatewayVO (KVM Host 复用) |
| 规格管理 | 无 | ChassisOfferingVO (自动创建) |

## 2. VO 继承体系

### 2.1 继承关系

```
ResourceVO (基类)
  └─ BareMetal2ChassisAO (抽象映射超类 @MappedSuperclass)
      └─ BareMetal2ChassisVO (通用实体)
          └─ BareMetal2IpmiChassisVO (IPMI 特化，@PrimaryKeyJoinColumn)
```

### 2.2 BareMetal2ChassisAO 核心字段

| 字段名 | 类型 | 说明 |
|--------|------|------|
| name | String | 名称 |
| zoneUuid | String | 所属 Zone |
| clusterUuid | String | 所属 Cluster |
| chassisOfferingUuid | String | 关联规格 |
| provisionType | BareMetal2ProvisionType | PXE/STATIC_IP |
| state | BareMetal2ChassisState | Enabled/Disabled |
| status | BareMetal2ChassisStatus | Creating/Ready/Provisioning |
| powerStatus | BareMetal2ChassisPowerStatus | On/Off/Unknown |

### 2.3 BareMetal2IpmiChassisVO 特有字段

| 字段名 | 类型 | 说明 |
|--------|------|------|
| ipmiAddress | String | IPMI 地址 |
| ipmiPort | Integer | IPMI 端口 |
| ipmiUsername | String | IPMI 用户名 |
| ipmiPassword | String | IPMI 密码 |

## 3. 细粒度硬件 VO

### 3.1 BareMetal2ChassisDiskVO

| 字段名 | 类型 | 说明 |
|--------|------|------|
| chassisUuid | String | 所属 Chassis（CASCADE） |
| diskSize | Long | 容量（字节） |
| wwn | String | World Wide Name |
| type | String | HDD/SSD/NVME |

### 3.2 BareMetal2ChassisNicVO

| 字段名 | 类型 | 说明 |
|--------|------|------|
| chassisUuid | String | 所属 Chassis（CASCADE） |
| mac | String | MAC 地址 |
| speed | String | 速率 |
| nicName | String | 网卡名称 |
| isProvisionNic | Boolean | 是否部署网卡 |
| isPrimaryProvisionNic | Boolean | 是否主部署网卡（PXE） |

### 3.3 BareMetal2ChassisPciDeviceVO

| 字段名 | 类型 | 说明 |
|--------|------|------|
| chassisUuid | String | 所属 Chassis（CASCADE） |
| type | String | GPU/NIC/Storage |
| pciDeviceAddress | String | PCI 总线地址 |
| vendorId | String | 厂商 ID |
| deviceId | String | 设备 ID |
| iommuGroup | String | IOMMU 组 |

### 3.4 BareMetal2ChassisGpuDeviceVO（继承 PciDeviceVO）

| 字段名 | 类型 | 说明 |
|--------|------|------|
| serialNumber | String | GPU 序列号 |
| memory | Long | 显存大小 |
| power | Long | 功耗 |
| isDriverLoaded | boolean | 驱动加载状态 |

## 4. Gateway 模式

### 4.1 BareMetal2GatewayVO

```java
@PrimaryKeyJoinColumn(name = "uuid", referencedColumnName = "uuid")
public class BareMetal2GatewayVO extends KVMHostVO {
    Set<BareMetal2GatewayClusterRefVO> attachedClusterRefs;
    BareMetal2GatewayProvisionNicVO provisionNic;
}
```

**核心设计**：
- Gateway 本质是一台 KVM Host
- 一个 Gateway 可服务多个 Baremetal Cluster
- 通过 ProvisionNicVO 指定部署网卡

### 4.2 与 v1 PxeServer 的区别

| 维度 | v1 PxeServer | v2 Gateway |
|------|--------------|-----------|
| 基础设施 | 独立的 PXE 服务器 | KVM Host 复用 |
| 服务范围 | DHCP Range 关联 | GatewayClusterRefVO 关联 |
| 资源利用 | 专用服务器 | 与虚拟化共享 |

## 5. ChassisOffering（规格化）

### 5.1 BareMetal2ChassisOfferingVO

| 字段名 | 类型 | 说明 |
|--------|------|------|
| name | String | 规格名称 |
| architecture | String | x86_64/aarch64 |
| cpuModelName | String | CPU 型号 |
| cpuNum | Integer | CPU 核心数 |
| memorySize | Long | 内存大小 |
| provisionType | BareMetal2ProvisionType | PXE/STATIC_IP |
| bootMode | BareMetal2ChassisBootMode | UEFI/LEGACY |

**特点**：硬件发现时自动生成规格

## 6. 对 RoleAdapter 接口的建议

### 6.1 必须支持继承体系

```java
// 获取 Chassis 类型（IPMI/Redfish/SSH）
String getChassisType(String chassisUuid);

// 加载特定类型的 Chassis VO
BareMetal2ChassisVO loadChassisVO(String chassisUuid);

// 同步 IPMI 特有字段
void syncIpmiCredentials(String chassisUuid, String ipmiAddress,
    Integer ipmiPort, String ipmiUsername, String ipmiPassword);
```

### 6.2 细粒度硬件同步

```java
// 同步所有硬件组件
void syncAllHardwareComponents(String chassisUuid, String unifiedUuid);

// 磁盘同步
void syncDisksToUnified(String chassisUuid, String unifiedHardwareUuid);

// 网卡同步（包括 isProvisionNic）
void syncNicsToUnified(String chassisUuid, String unifiedHardwareUuid);

// PCI/GPU 设备同步
void syncPciDevicesToUnified(String chassisUuid, String unifiedHardwareUuid);
```

### 6.3 Gateway 关联处理

```java
// 获取关联的 Gateway（通过 Cluster → GatewayRef 间接关联）
String getAssociatedGatewayUuid(String chassisUuid);
```

### 6.4 状态映射

v2 使用三元状态，需要映射到 UnifiedHardwareVO：

```java
UnifiedHardwareState mapState(
    BareMetal2ChassisState state,
    BareMetal2ChassisStatus status,
    BareMetal2ChassisPowerStatus powerStatus
);
```

## 7. 关键技术挑战

1. **继承体系的 ORM 映射**：JPA JOINED 策略需要处理多态
2. **细粒度硬件的版本冲突**：需要乐观锁或时间戳
3. **Gateway 的双重身份**：既是 KVM Host，又是 Baremetal 基础设施
