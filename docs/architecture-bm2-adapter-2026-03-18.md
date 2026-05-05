# BM2 角色适配详细设计文档

**版本**: v1.0
**日期**: 2026-03-18
**作者**: Baremetal2 Elastic Bare Metal Architecture Expert
**输入**: architecture-unified-hardware-2026-03-18.md (第 3、6、9 章) + PRD FR-025/FR-010~012 + BM2 模块分析

---

## 1. Bm2PhysicalServerRoleProvider 实现设计

### 1.1 类定义

```java
package org.zstack.baremetal2.server;

import org.zstack.header.server.*;
import org.zstack.header.server.enums.*;

/**
 * BM2 角色适配器。
 *
 * 在 BareMetal2Chassis 创建成功且硬件发现完成（status = Available）后，
 * 单向同步创建 PhysicalServerVO + PhysicalServerRoleVO。
 *
 * 不修改任何 BM2 现有代码的行为，只在生命周期钩子中增量创建映射。
 */
@Component
public class Bm2PhysicalServerRoleProvider implements PhysicalServerRoleProvider {
    // ...
}
```

### 1.2 getRoleType()

```java
@Override
public ServerRoleType getRoleType() {
    return ServerRoleType.BAREMETAL_V2;
}
```

常量返回，全局唯一。与 `PhysicalServerRoleVO.roleType` 中存储的枚举值一致。

### 1.3 getSchedulingMode()

```java
@Override
public SchedulingMode getSchedulingMode() {
    return SchedulingMode.INTERNAL_EXCLUSIVE;
}
```

**设计决策**：BM2 不论弹性模式还是绑定模式，对物理服务器的分配粒度始终是**整机独占**。区别仅在于实例停止后是否释放 Chassis，不影响调度模式本身。

弹性 vs 绑定的区别体现在 `PhysicalServerRoleVO.roleStatus` 和容量归还时机上（见第 4 章），不影响 `SchedulingMode` 的返回值。

### 1.4 getCapacityConsumption(serverUuid)

```java
@Override
public CapacityUsage getCapacityConsumption(String serverUuid) {
    CapacityUsage usage = new CapacityUsage();

    // 1. 从 PhysicalServerRoleVO 获取 roleUuid（即 BareMetal2ChassisVO.uuid）
    String chassisUuid = Q.New(PhysicalServerRoleVO.class)
        .select(PhysicalServerRoleVO_.roleUuid)
        .eq(PhysicalServerRoleVO_.serverUuid, serverUuid)
        .eq(PhysicalServerRoleVO_.roleType, ServerRoleType.BAREMETAL_V2)
        .findValue();

    if (chassisUuid == null) {
        return usage; // 无 BM2 角色，无消耗
    }

    // 2. 检查 Chassis 是否已被分配（status = Allocated）
    BareMetal2ChassisStatus status = Q.New(BareMetal2ChassisVO.class)
        .select(BareMetal2ChassisVO_.status)
        .eq(BareMetal2ChassisVO_.uuid, chassisUuid)
        .findValue();

    if (status == BareMetal2ChassisStatus.Allocated) {
        // 独占模式：整机容量全部被消耗
        PhysicalServerCapacityVO cap = dbf.findByUuid(serverUuid,
            PhysicalServerCapacityVO.class);
        if (cap != null) {
            usage.setUsedCpu(cap.getTotalPhysicalCpu());
            usage.setUsedMemory(cap.getTotalPhysicalMemory());
            usage.setUsedDisk(cap.getTotalDisk());
        }
    }
    // status = Available / HardwareInfoUnknown 等：无容量消耗

    return usage;
}
```

**关键逻辑**：

- BM2 不使用 CPU/Memory 粒度的容量扣减，而是以 `BareMetal2ChassisStatus.Allocated` 作为"整机被占用"的唯一判据。
- 当 Chassis 处于 `Available` 状态时（弹性模式下实例停止后释放），容量消耗归零。
- 与 BM2 现有分配逻辑完全一致：BM2 的分配过滤基于 `state=Enabled + status=Available + chassisOfferingUuid 匹配`，不使用 `requiredCpu/requiredMemory`。

### 1.5 onPhysicalServerCreated(serverUuid)

```java
@Override
public void onPhysicalServerCreated(String serverUuid) {
    // BM2 角色的 PhysicalServerVO 由 BM2 的 PostCreate 钩子主动创建，
    // 此回调用于 PhysicalServerVO 被其他角色首先创建的场景。
    //
    // 对于 BM2，如果 PhysicalServerVO 已存在（如同一物理机先注册了 KVM），
    // 则 BM2 Chassis 创建时通过 matchExistingServer() 关联到已有记录，
    // 不会触发此回调。
    //
    // 因此此方法为空实现。BM2 特有的初始化逻辑在 Chassis 创建钩子中处理。
    logger.debug(String.format(
        "onPhysicalServerCreated called for BM2 role, serverUuid=%s, no-op",
        serverUuid));
}
```

### 1.6 onPhysicalServerDeleted(serverUuid)

```java
@Override
public void onPhysicalServerDeleted(String serverUuid) {
    // 1. 查找此物理服务器关联的 BM2 Chassis
    String chassisUuid = Q.New(PhysicalServerRoleVO.class)
        .select(PhysicalServerRoleVO_.roleUuid)
        .eq(PhysicalServerRoleVO_.serverUuid, serverUuid)
        .eq(PhysicalServerRoleVO_.roleType, ServerRoleType.BAREMETAL_V2)
        .findValue();

    if (chassisUuid == null) {
        return;
    }

    // 2. 不删除 BM2 Chassis（PhysicalServer 删除不级联删除角色实体）
    //    只清理 RoleVO 映射记录（由 FK CASCADE 自动处理）
    //    但如果需要通知 BM2 模块做额外清理，可通过扩展点通知
    logger.info(String.format(
        "PhysicalServer[uuid=%s] deleted, BM2 Chassis[uuid=%s] role mapping " +
        "will be cascade deleted via FK", serverUuid, chassisUuid));
}
```

### 1.7 getInventory(roleUuid)

```java
@Override
public RoleInventory getInventory(String roleUuid) {
    BareMetal2ChassisVO chassis = dbf.findByUuid(roleUuid, BareMetal2ChassisVO.class);
    if (chassis == null) {
        return null;
    }

    Bm2RoleInventory inv = new Bm2RoleInventory();
    inv.setRoleUuid(roleUuid);
    inv.setRoleType(ServerRoleType.BAREMETAL_V2.toString());
    inv.setClusterUuid(chassis.getClusterUuid());
    inv.setStatus(chassis.getStatus().toString());

    // BM2 特有字段
    inv.setChassisType(chassis.getType());
    inv.setProvisionType(chassis.getProvisionType().toString());
    inv.setPowerStatus(chassis.getPowerStatus().toString());
    inv.setChassisOfferingUuid(chassis.getChassisOfferingUuid());

    // 弹性/绑定模式判断
    String instanceUuid = Q.New(BareMetal2InstanceVO.class)
        .select(BareMetal2InstanceVO_.uuid)
        .eq(BareMetal2InstanceVO_.chassisUuid, roleUuid)
        .findValue();
    inv.setBoundInstanceUuid(instanceUuid);

    if (instanceUuid != null) {
        boolean autoRelease = BareMetal2SystemTags.AUTO_RELEASE_BAREMETAL2_CHASSIS
            .hasTag(instanceUuid);
        inv.setElasticMode(autoRelease);
    }

    return inv;
}
```

**Bm2RoleInventory 定义**：

```java
package org.zstack.baremetal2.server;

import org.zstack.header.server.RoleInventory;

/**
 * BM2 角色 Inventory 扩展。
 * 包含弹性/绑定模式状态、Chassis 类型、ProvisionType 等 BM2 特有字段。
 */
public class Bm2RoleInventory extends RoleInventory {
    private String chassisType;        // "ipmi" 等
    private String provisionType;      // Remote / Local / Direct
    private String powerStatus;        // POWER_ON / POWER_OFF / POWER_UNKNOWN
    private String chassisOfferingUuid;
    private String boundInstanceUuid;  // 当前绑定的实例 UUID，null 表示未分配
    private boolean elasticMode;       // true=弹性模式（停机释放），false=绑定模式

    // getter/setter 省略
}
```

### 1.8 matchExistingServer(context)

```java
@Override
public String matchExistingServer(RoleMatchContext context) {
    // BM2 的匹配逻辑：
    // 1. 优先通过 serialNumber 匹配
    if (context.getSerialNumber() != null
            && !context.getSerialNumber().isEmpty()
            && !"Not Specified".equals(context.getSerialNumber())) {
        String uuid = Q.New(PhysicalServerVO.class)
            .select(PhysicalServerVO_.uuid)
            .eq(PhysicalServerVO_.serialNumber, context.getSerialNumber())
            .eq(PhysicalServerVO_.zoneUuid, context.getZoneUuid())
            .findValue();
        if (uuid != null) {
            return uuid;
        }
    }

    // 2. 降级：通过 OOB 地址（IPMI 地址）匹配
    //    BM2 IPMI 地址在 BareMetal2IpmiChassisVO 子类中，
    //    此处用 oobAddress 字段匹配 PhysicalServerVO
    if (context.getOobAddress() != null) {
        String uuid = Q.New(PhysicalServerVO.class)
            .select(PhysicalServerVO_.uuid)
            .eq(PhysicalServerVO_.oobAddress, context.getOobAddress())
            .eq(PhysicalServerVO_.zoneUuid, context.getZoneUuid())
            .findValue();
        if (uuid != null) {
            return uuid;
        }
    }

    // 3. 无匹配
    return null;
}
```

**注意**：BM2 的 `managementIp` 概念与 KVM 不同。BM2 Chassis 没有操作系统级管理 IP（Chassis 继承 ResourceVO 不继承 HostAO），其带外管理地址是 `BareMetal2IpmiChassisVO.ipmiAddress`。因此：
- `PhysicalServerVO.managementIp` 留 NULL（BM2 Chassis 没有 OS 级管理 IP）
- `ipmiAddress` 只映射到 `PhysicalServerVO.oobAddress`
- 在 `RoleMatchContext` 中，BM2 将 `ipmiAddress` 填入 `oobAddress` 字段用于降级匹配（通过 `PhysicalServerVO.oobAddress` 查询）

---

## 2. Chassis 创建/删除同步钩子

### 2.1 BM2 Chassis 创建入口点

BM2 Chassis 创建的核心入口在 `BareMetal2ChassisManagerImpl` 中：

```
APIAddBareMetal2IpmiChassisMsg
    → BareMetal2ChassisFactory.createBareMetal2Chassis()
        → BareMetal2IpmiChassisVO 持久化（status: HardwareInfoUnknown）
        → [可选] 触发 Inspect（PXE 重启物理机进行硬件发现）
        → Chassis status 变为 Available
```

**钩子注入时机**：在 Chassis **硬件发现成功后**（status 从 `HardwareInfoUnknown`/`IPxeBooting` 变为 `Available` 时），而不是 Chassis 创建时。

理由：
1. Chassis 创建时 IPMI 凭据可能不正确，会立即失败
2. 硬件发现需要 PXE 重启物理机（重操作），发现前 serialNumber、CPU/内存等关键信息未知
3. 发现失败（`IPxeBootFailed`、`WrongBootMode`、`WrongArchitecture`）的 Chassis 不应注册到统一管理

**钩子实现伪代码**：

```java
/**
 * 在 BareMetal2ChassisManagerImpl 中注入。
 * 监听 Chassis status 变化事件，当 status 变为 Available 时触发同步。
 */
public void afterChassisStatusChanged(String chassisUuid,
        BareMetal2ChassisStatus oldStatus, BareMetal2ChassisStatus newStatus) {

    if (newStatus != BareMetal2ChassisStatus.Available) {
        return;
    }

    BareMetal2ChassisVO chassis = dbf.findByUuid(chassisUuid, BareMetal2ChassisVO.class);

    // 构建匹配上下文
    RoleMatchContext ctx = new RoleMatchContext();
    ctx.setZoneUuid(chassis.getZoneUuid());
    ctx.setClusterUuid(chassis.getClusterUuid());

    // 获取 serialNumber（从 ChassisOffering 或硬件发现结果）
    String serialNumber = getSerialNumberFromInspection(chassisUuid);
    ctx.setSerialNumber(serialNumber);

    // 获取 IPMI 地址（仅 IPMI 类型）
    if ("ipmi".equals(chassis.getType())) {
        BareMetal2IpmiChassisVO ipmiChassis = dbf.findByUuid(chassisUuid,
            BareMetal2IpmiChassisVO.class);
        ctx.setOobAddress(ipmiChassis.getIpmiAddress());
    }

    // 调用 PhysicalServerManager 注册
    physicalServerManager.registerRole(
        ctx,
        ServerRoleType.BAREMETAL_V2,
        chassisUuid,
        chassis.getClusterUuid(),
        SchedulingMode.INTERNAL_EXCLUSIVE
    );
}
```

### 2.2 IPMI/Redfish 信息映射

| BareMetal2IpmiChassisVO 字段 | PhysicalServerAO 字段 | 说明 |
|-----|-----|------|
| `ipmiAddress` | `oobAddress` | BMC IP 地址 |
| `ipmiPort` | `oobPort` | IPMI 端口（默认 623） |
| `ipmiUsername` | `oobUsername` | BMC 用户名 |
| `ipmiPassword` | `oobPassword` | BMC 密码（@EncryptColumn 加密） |
| `type = "ipmi"` | `oobManagementType = IPMI` | 管理协议类型 |

**注意**：BM2 的 IPMI 字段存储在子类 `BareMetal2IpmiChassisVO` 中，不在基类 `BareMetal2ChassisAO` 中。非 IPMI 类型的 Chassis（如未来的 Redfish 类型）不存储这些字段。同步时需判断 `chassis.getType()`。

### 2.3 serialNumber 获取

BM2 的 serialNumber 通过硬件发现流程获取，入口在 `BareMetal2ChassisHardwareInfoSyncer`：

```
PXE boot → iPXE → Inspection Agent
    → 收集 CPU/Memory/Disk/NIC/PCI 信息
    → 回调 BareMetal2ChassisHardwareInfoSyncer.syncHardwareInfo()
        → 创建 BareMetal2ChassisNicVO、BareMetal2ChassisDiskVO
        → 创建/匹配 BareMetal2ChassisOfferingVO
        → chassis.status = Available
```

serialNumber 来源：
1. **IPMI FRU**：通过 `ipmitool fru` 命令获取产品序列号
2. **Agent 读取**：inspection agent 读取 `/sys/class/dmi/id/product_serial`
3. **硬件发现结果**：存储在 `BareMetal2ChassisHardwareInfo.serialNumber`（如有此字段）

如果硬件发现未返回有效 serialNumber（空串或 "Not Specified"），则在 PhysicalServerVO 创建时使用 `MD5(zoneUuid + ipmiAddress)` 生成确定性 UUID 作为替代标识。

### 2.4 Chassis 删除同步钩子

```java
/**
 * 在 BareMetal2ChassisManagerImpl.deleteChassis() 中注入。
 * Chassis 删除前更新 PhysicalServerRoleVO。
 */
public void beforeChassisDeleted(String chassisUuid) {
    // 1. 查找关联的 PhysicalServerRoleVO
    PhysicalServerRoleVO roleVO = Q.New(PhysicalServerRoleVO.class)
        .eq(PhysicalServerRoleVO_.roleUuid, chassisUuid)
        .eq(PhysicalServerRoleVO_.roleType, ServerRoleType.BAREMETAL_V2)
        .find();

    if (roleVO == null) {
        return;
    }

    // 2. 更新 roleStatus 为 Stale
    roleVO.setRoleStatus("Stale");
    dbf.update(roleVO);

    // 3. 检查是否是最后一个角色
    long activeRoleCount = Q.New(PhysicalServerRoleVO.class)
        .eq(PhysicalServerRoleVO_.serverUuid, roleVO.getServerUuid())
        .notEq(PhysicalServerRoleVO_.roleStatus, "Stale")
        .count();

    if (activeRoleCount == 0) {
        // 所有角色已下线，更新 PhysicalServer 状态
        SQL.New(PhysicalServerVO.class)
            .eq(PhysicalServerVO_.uuid, roleVO.getServerUuid())
            .set(PhysicalServerVO_.status, PhysicalServerStatus.Disconnected)
            .update();
    }
}
```

---

## 3. ProvisionNetwork 迁移（BM2 是主参考模型）

### 3.1 BareMetal2ProvisionNetworkVO → PhysicalServerProvisionNetworkVO 字段映射

BM2 的 ProvisionNetwork 是统一模型的原生参考实现。字段映射精确对应，几乎无信息损失。

| BareMetal2ProvisionNetworkVO 字段 | PhysicalServerProvisionNetworkVO 字段 | 映射说明 |
|------|------|------|
| `uuid` | 新生成 UUID | 通过 SystemTag 记录 `originBm2ProvisionNetworkUuid::{原始uuid}` |
| `name` | `name` | 直接映射 |
| `description` | `description` | 直接映射 |
| `zoneUuid` | `zoneUuid` | 直接映射 |
| `dhcpInterface` | `dhcpInterface` | 直接映射，BM2 原生字段 |
| `dhcpRangeStartIp` | `dhcpRangeStartIp` | 直接映射 |
| `dhcpRangeEndIp` | `dhcpRangeEndIp` | 直接映射 |
| `dhcpRangeNetmask` | `dhcpRangeNetmask` | 直接映射 |
| `dhcpRangeGateway` | `dhcpRangeGateway` | 直接映射 |
| `dhcpRangeNetworkCidr` | 不加入 PhysicalServerProvisionNetworkVO | 冗余字段，可从 startIp + netmask 计算得出。BM2 兼容层的 Inventory 返回时自行计算填充（`NetworkUtils.getCidrFromIpMask(startIp, netmask)`） |
| `state` | `state` | Enabled/Disabled → PhysicalServerState.Enabled/Disabled |
| (隐含 Gateway 模式) | `type` | → `ProvisionNetworkType.GATEWAY_PXE`（见 3.3 节） |

### 3.2 BareMetal2GatewayProvisionNicVO 的处理方案

`BareMetal2GatewayProvisionNicVO` 记录 Gateway 在 ProvisionNetwork 上的网卡信息：

| 字段 | 说明 |
|------|------|
| `uuid` | 与 GatewayVO 共享 UUID |
| `networkUuid` | 指向 BareMetal2ProvisionNetworkVO |
| `interfaceName` | 网卡接口名（如 `eth1`） |
| `ip` | Gateway 在装机网络上的 IP |
| `netmask` | 网段掩码 |
| `gateway` | 默认网关 |

**处理方案**：

`BareMetal2GatewayProvisionNicVO` 是 Gateway 基础设施的配置，属于"装机服务提供方"的概念，不属于"装机网络"本身。在统一模型中：

1. **不迁移 GatewayProvisionNicVO 到统一模型**。Gateway 是 BM2 特有的部署基础设施概念，统一 ProvisionNetwork 不关心由谁提供 PXE/DHCP 服务。
2. **Gateway 保持 BM2 内部管理**。`BareMetal2GatewayVO`、`BareMetal2GatewayClusterRefVO`、`BareMetal2GatewayProvisionNicVO` 继续由 BM2 模块独立管理。
3. **统一 ProvisionNetwork 通过 `type = GATEWAY_PXE` 标识**，表明此网络的 PXE 服务由 Gateway 提供（而非独立 PXE 服务器）。

**关联关系**：
```
PhysicalServerProvisionNetworkVO (type=GATEWAY_PXE)
    ↕ (通过 SystemTag 或 networkUuid 映射)
BareMetal2ProvisionNetworkVO
    ↕ (通过 BareMetal2GatewayProvisionNicVO.networkUuid)
BareMetal2GatewayVO (继承 KVMHostVO)
    ↕ (通过 BareMetal2GatewayClusterRefVO)
ClusterVO
```

### 3.3 STANDALONE_PXE vs GATEWAY_PXE 映射

| ProvisionNetworkType | 对应的现有模型 | PXE 服务提供方 | 适用角色 |
|------|------|------|------|
| `STANDALONE_PXE` | BM1 `BaremetalPxeServerVO` | 独立 PXE 服务器 | BM1 装机、裸机装 KVM ISO |
| `GATEWAY_PXE` | BM2 `BareMetal2ProvisionNetworkVO` + `BareMetal2GatewayVO` | BM2 Gateway（运行在 KVM Host 上） | BM2 装机 |

**BM2 的所有 ProvisionNetwork 统一映射为 `GATEWAY_PXE` 类型**，因为 BM2 的装机流程始终依赖 Gateway 提供 PXE/DHCP/TFTP 服务。

### 3.4 ProvisionNetworkClusterRef 映射

| BareMetal2ProvisionNetworkClusterRefVO 字段 | PhysicalServerProvisionNetworkClusterRefVO 字段 |
|------|------|
| `id` (auto increment) | `uuid` (新生成) |
| `networkUuid` → BM2 ProvisionNetwork | `networkUuid` → 统一 ProvisionNetwork |
| `clusterUuid` | `clusterUuid` |

BM2 的 ClusterRef 使用自增 `id` 主键，统一模型使用 UUID 主键。迁移时为每条 Ref 记录生成新 UUID。

### 3.5 迁移 SQL 示例

```sql
-- BM2 ProvisionNetwork → 统一 ProvisionNetwork
INSERT IGNORE INTO PhysicalServerProvisionNetworkVO
    (uuid, zoneUuid, name, description, dhcpInterface,
     dhcpRangeStartIp, dhcpRangeEndIp, dhcpRangeNetmask, dhcpRangeGateway,
     state, type, createDate, lastOpDate)
SELECT
    MD5(CONCAT('bm2-pn-', uuid)),   -- 确定性 UUID
    zoneUuid,
    name,
    description,
    dhcpInterface,
    dhcpRangeStartIp,
    dhcpRangeEndIp,
    dhcpRangeNetmask,
    dhcpRangeGateway,
    state,
    'GATEWAY_PXE',
    createDate,
    NOW()
FROM BareMetal2ProvisionNetworkVO;

-- 记录原始 UUID 映射（通过 SystemTag）
INSERT IGNORE INTO SystemTagVO (uuid, resourceUuid, resourceType, tag, type, createDate, lastOpDate)
SELECT
    MD5(CONCAT('bm2-pn-tag-', uuid)),
    MD5(CONCAT('bm2-pn-', uuid)),
    'PhysicalServerProvisionNetworkVO',
    CONCAT('originBm2ProvisionNetworkUuid::', uuid),
    'System',
    NOW(), NOW()
FROM BareMetal2ProvisionNetworkVO;

-- BM2 ClusterRef → 统一 ClusterRef
INSERT IGNORE INTO PhysicalServerProvisionNetworkClusterRefVO
    (uuid, networkUuid, clusterUuid, createDate)
SELECT
    MD5(CONCAT('bm2-pn-ref-', id)),
    MD5(CONCAT('bm2-pn-', networkUuid)),
    clusterUuid,
    NOW()
FROM BareMetal2ProvisionNetworkClusterRefVO;
```

---

## 4. 弹性模式 / 绑定模式适配

### 4.1 两种模式的定义

BM2 支持两种 Chassis 分配模式，由创建实例时的参数决定：

| 模式 | 创建参数 | 停机行为 | 实现机制 |
|------|---------|---------|---------|
| **绑定模式** | `chassisUuid` 指定 | 停机后 Chassis 保持 `Allocated`，不释放 | 实例与 Chassis 1:1 绑定 |
| **弹性模式** | `chassisOfferingUuid` 指定 | 停机后 Chassis 释放为 `Available` | SystemTag `autoReleaseBareMetal2Chassis` |

```java
// APICreateBareMetal2InstanceMsg.java
// 绑定模式：直接指定 chassisUuid
@APIParam(required = false, resourceType = BareMetal2ChassisVO.class)
private String chassisUuid;

// 弹性模式：指定 chassisOfferingUuid，系统自动匹配可用 Chassis
@APIParam(required = false, resourceType = BareMetal2ChassisOfferingVO.class)
private String chassisOfferingUuid;
```

### 4.2 在统一模型中的表达

弹性/绑定模式在统一模型中不需要新增 VO 字段，而是通过以下机制组合表达：

#### 4.2.1 PhysicalServerRoleVO 层面

| 字段 | 绑定模式值 | 弹性模式（已分配）值 | 弹性模式（空闲）值 |
|------|----------|---------------------|------------------|
| `roleType` | BAREMETAL_V2 | BAREMETAL_V2 | BAREMETAL_V2 |
| `roleStatus` | Active | Active | Active |
| `schedulingMode` | INTERNAL_EXCLUSIVE | INTERNAL_EXCLUSIVE | INTERNAL_EXCLUSIVE |

两种模式下 `PhysicalServerRoleVO` 的记录完全相同。区别仅在 BM2 内部的 `BareMetal2ChassisVO.status` 字段。

#### 4.2.2 PhysicalServerCapacityVO 层面

| 场景 | availableCpu | availableMemory | availableDisk |
|------|-------------|-----------------|---------------|
| Chassis 未分配（Available） | = totalCpu | = totalMemory | = totalDisk |
| Chassis 已分配（Allocated，绑定或弹性） | 0 | 0 | 0 |
| 弹性模式，实例停止后 Chassis 释放 | = totalCpu | = totalMemory | = totalDisk |
| 绑定模式，实例停止后 Chassis 不释放 | 0 | 0 | 0 |

**关键**：弹性模式下实例停止后，BM2 将 Chassis status 改回 `Available`。此时 `getCapacityConsumption()` 返回全零，容量重计算后可用量恢复为总量。

#### 4.2.3 容量同步时机

BM2 的 Chassis 状态变化（Allocated ↔ Available）是容量变化的触发点。在以下事件发生时，需触发 `PhysicalServerCapacityUpdater.recalculateCapacity()`:

1. **Chassis 分配**（`status: Available → Allocated`）：清零可用量
2. **Chassis 释放**（`status: Allocated → Available`，仅弹性模式）：恢复可用量
3. **绑定模式实例停止**：Chassis 保持 Allocated，无容量变化

```java
/**
 * 在 BM2 Chassis 状态变化时触发容量同步。
 * 监听 BareMetal2ChassisStatus 变化。
 */
public void onChassisStatusChanged(String chassisUuid,
        BareMetal2ChassisStatus oldStatus, BareMetal2ChassisStatus newStatus) {

    // 仅关注 Available ↔ Allocated 的变化
    if ((oldStatus == BareMetal2ChassisStatus.Available
            && newStatus == BareMetal2ChassisStatus.Allocated)
        || (oldStatus == BareMetal2ChassisStatus.Allocated
            && newStatus == BareMetal2ChassisStatus.Available)) {

        String serverUuid = Q.New(PhysicalServerRoleVO.class)
            .select(PhysicalServerRoleVO_.serverUuid)
            .eq(PhysicalServerRoleVO_.roleUuid, chassisUuid)
            .eq(PhysicalServerRoleVO_.roleType, ServerRoleType.BAREMETAL_V2)
            .findValue();

        if (serverUuid != null) {
            capacityUpdater.recalculateCapacity(serverUuid);
        }
    }
}
```

### 4.3 弹性模式下 Chassis 自由绑定/解绑 Instance

弹性模式的核心流程：

```
实例创建（chassisOfferingUuid）
    → 分配匹配 Offering 的 Available Chassis → Chassis.status = Allocated
    → 分配 Gateway → PXE 部署 OS → 实例 Running
    → 实例停止
    → SystemTag autoReleaseBareMetal2Chassis 存在
    → Chassis.status = Available, Chassis 与 Instance 解绑
    → 实例再次启动
    → 重新分配匹配 Offering 的 Available Chassis（可能是不同的物理机）
```

在统一模型中，这个过程通过以下映射体现：

1. **实例启动时**：PhysicalServerRoleVO 不变（RoleVO 记录的是 Chassis-PhysicalServer 的长期关系，不随 Instance 分配而变化）
2. **PhysicalServerCapacityVO** 的可用量在 Chassis status 变化时更新
3. **PhysicalServerRoleVO.roleStatus** 始终为 `Active`（只要 Chassis 存在且可用）

### 4.4 绑定模式下的独占分配

```
实例创建（chassisUuid 直接指定）
    → Chassis.status = Allocated → 不释放
    → 实例生命周期结束才可能释放
```

PhysicalServerCapacityVO 在 Chassis 被指定分配后永久清零，直到 Instance 被删除或手动释放。

---

## 5. 兼容性风险和迁移

### 5.1 BM2 现有 API 不变的保证

**铁律：不修改任何 BM2 现有代码的行为**。

以下 API 的入参、出参、错误码、行为完全不变：

| API 类别 | API 列表 | 保证 |
|---------|---------|------|
| Chassis 管理 | `APIAddBareMetal2IpmiChassisMsg`、`APIDeleteBareMetal2ChassisMsg`、`APIUpdateBareMetal2ChassisMsg`、`APIChangeBareMetal2ChassisStateMsg`、`APIQueryBareMetal2ChassisMsg`、`APIInspectBareMetal2ChassisMsg` | 入参/出参不变 |
| 电源管理 | `APIPowerOnBareMetal2ChassisMsg`、`APIPowerOffBareMetal2ChassisMsg`、`APIPowerResetBareMetal2ChassisMsg`、`APIGetBareMetal2ChassisPowerStatusMsg` | 行为不变 |
| Gateway 管理 | `APIAddBareMetal2GatewayMsg`、`APIDeleteBareMetal2GatewayMsg`、`APIAttachBareMetal2GatewayToClusterMsg` 等 | 完全不涉及 |
| 实例管理 | `APICreateBareMetal2InstanceMsg`、`APIStartBareMetal2InstanceMsg` 等 | 弹性/绑定行为不变 |
| ProvisionNetwork | `APICreateBareMetal2ProvisionNetworkMsg`、`APIAttachBareMetal2ProvisionNetworkToClusterMsg` 等 | 行为不变 |
| ChassisOffering | `APIQueryBareMetal2ChassisOfferingMsg` 等 | 行为不变 |

**实现方式**：统一管理模块以"只增不改"的方式接入，通过生命周期钩子单向创建 PhysicalServerVO/RoleVO，不拦截、不代理、不修改 BM2 的任何消息处理流程。

### 5.2 存量 BareMetal2ChassisVO → PhysicalServerVO 迁移

#### 5.2.1 迁移脚本

```sql
-- 为所有 BM2 Chassis 创建 PhysicalServerVO
-- 仅迁移 status 为 Available 或 Allocated 的 Chassis（已通过硬件发现）
INSERT IGNORE INTO PhysicalServerVO
    (uuid, zoneUuid, poolUuid, name, managementIp,
     serialNumber, architecture, manufacturer, model,
     state, status, powerStatus,
     oobManagementType, oobAddress, oobPort, oobUsername, oobPassword,
     createDate, lastOpDate)
SELECT
    MD5(CONCAT('bm2-chassis-', c.uuid)),
    c.zoneUuid,
    NULL,                                    -- poolUuid: 需后续分配或创建默认 Pool
    c.name,
    NULL,                                    -- managementIp: BM2 Chassis 没有 OS 级管理 IP，留 NULL
    NULL,                                    -- serialNumber: 需从硬件发现数据提取
    o.architecture,                          -- 从 ChassisOffering 获取
    NULL,                                    -- manufacturer: 需从硬件发现数据提取
    NULL,                                    -- model: 需从硬件发现数据提取
    CASE c.state
        WHEN 'Enabled' THEN 'Enabled'
        WHEN 'Disabled' THEN 'Disabled'
    END,
    CASE c.status
        WHEN 'Available' THEN 'Connected'
        WHEN 'Allocated' THEN 'Connected'
        ELSE 'Disconnected'
    END,
    CASE c.powerStatus
        WHEN 'POWER_ON' THEN 'PowerOn'
        WHEN 'POWER_OFF' THEN 'PowerOff'
        ELSE 'Unknown'
    END,
    'IPMI',
    ipmi.ipmiAddress,
    ipmi.ipmiPort,
    ipmi.ipmiUsername,
    ipmi.ipmiPassword,
    c.createDate,
    NOW()
FROM BareMetal2ChassisVO c
LEFT JOIN BareMetal2IpmiChassisVO ipmi ON c.uuid = ipmi.uuid
LEFT JOIN BareMetal2ChassisOfferingVO o ON c.chassisOfferingUuid = o.uuid
WHERE c.status IN ('Available', 'Allocated');

-- 创建 PhysicalServerRoleVO
INSERT IGNORE INTO PhysicalServerRoleVO
    (uuid, serverUuid, roleType, roleUuid, clusterUuid,
     schedulingMode, roleStatus, createDate, lastOpDate)
SELECT
    MD5(CONCAT('bm2-role-', c.uuid)),
    MD5(CONCAT('bm2-chassis-', c.uuid)),
    'BAREMETAL_V2',
    c.uuid,
    c.clusterUuid,
    'INTERNAL_EXCLUSIVE',
    'Active',
    NOW(), NOW()
FROM BareMetal2ChassisVO c
WHERE c.status IN ('Available', 'Allocated');

-- 创建 PhysicalServerCapacityVO
INSERT IGNORE INTO PhysicalServerCapacityVO
    (uuid, totalPhysicalCpu, totalPhysicalMemory,
     cpuSockets, cpuCores,
     cpuOverprovisioningRatio, memoryOverprovisioningRatio,
     totalCpu, totalMemory, availableCpu, availableMemory,
     reservedCpu, reservedMemory, totalDisk, availableDisk,
     capacityState)
SELECT
    MD5(CONCAT('bm2-chassis-', c.uuid)),
    COALESCE(o.cpuNum, 0),
    COALESCE(o.memorySize, 0),
    0,                                         -- cpuSockets: 需从硬件发现数据提取
    COALESCE(o.cpuNum, 0),
    1.0,                                       -- BM2 不超分
    1.0,                                       -- BM2 不超分
    COALESCE(o.cpuNum, 0),
    COALESCE(o.memorySize, 0),
    CASE c.status
        WHEN 'Available' THEN COALESCE(o.cpuNum, 0)
        WHEN 'Allocated' THEN 0
    END,
    CASE c.status
        WHEN 'Available' THEN COALESCE(o.memorySize, 0)
        WHEN 'Allocated' THEN 0
    END,
    0, 0,
    0, 0,                                      -- totalDisk/availableDisk: 需从磁盘数据汇总
    'Ready'
FROM BareMetal2ChassisVO c
LEFT JOIN BareMetal2ChassisOfferingVO o ON c.chassisOfferingUuid = o.uuid
WHERE c.status IN ('Available', 'Allocated');

-- 创建 PhysicalServerHardwareInfoVO
INSERT IGNORE INTO PhysicalServerHardwareInfoVO
    (uuid, cpuModel, cpuCores, cpuSockets, totalMemory, totalDisk,
     nicCount, gpuCount, lastDiscoveryDate)
SELECT
    MD5(CONCAT('bm2-chassis-', c.uuid)),
    o.cpuModelName,
    COALESCE(o.cpuNum, 0),
    0,
    COALESCE(o.memorySize, 0),
    0,
    (SELECT COUNT(*) FROM BareMetal2ChassisNicVO n WHERE n.chassisUuid = c.uuid),
    0,
    NOW()
FROM BareMetal2ChassisVO c
LEFT JOIN BareMetal2ChassisOfferingVO o ON c.chassisOfferingUuid = o.uuid
WHERE c.status IN ('Available', 'Allocated');
```

#### 5.2.2 状态映射表

| BareMetal2ChassisState | PhysicalServerState | 说明 |
|---|---|---|
| `Enabled` | `Enabled` | 直接映射 |
| `Disabled` | `Disabled` | 直接映射 |

| BareMetal2ChassisStatus | PhysicalServerStatus | 说明 |
|---|---|---|
| `HardwareInfoUnknown` | 不迁移 | 未通过硬件发现，不纳入统一管理 |
| `IPxeBooting` | 不迁移 | 发现中，不纳入统一管理 |
| `IPxeBootFailed` | 不迁移 | 发现失败 |
| `WrongBootMode` | 不迁移 | 配置错误 |
| `WrongArchitecture` | 不迁移 | 配置错误 |
| `Available` | `Connected` | Chassis 就绪 |
| `Allocated` | `Connected` | Chassis 已被分配（仍连通） |

| BareMetal2ChassisPowerStatus | PhysicalServerPowerStatus | 说明 |
|---|---|---|
| `POWER_ON` | `PowerOn` | 直接映射 |
| `POWER_OFF` | `PowerOff` | 直接映射 |
| `POWER_UNKNOWN` | `Unknown` | 直接映射 |

#### 5.2.3 幂等保证

- 使用 `MD5(CONCAT('bm2-chassis-', chassisUuid))` 生成确定性 UUID，重复执行不产生重复数据
- `INSERT IGNORE` + UNIQUE 约束兜底
- 迁移后验证：`COUNT(PhysicalServerRoleVO WHERE roleType='BAREMETAL_V2') == COUNT(BareMetal2ChassisVO WHERE status IN ('Available','Allocated'))`

### 5.3 迁移回滚

统一管理模块可完整卸载：

1. 删除 `PhysicalServerProvisionNetworkClusterRefVO`、`PhysicalServerProvisionNetworkVO`
2. 删除 `PhysicalServerRoleVO`
3. 删除 `PhysicalServerHardwareDetailVO`、`PhysicalServerHardwareInfoVO`
4. 删除 `PhysicalServerCapacityVO`
5. 删除 `PhysicalServerVO`
6. 删除 `ClusterServerPoolRefVO`、`ServerPoolVO`
7. 删除相关 SystemTag

卸载后 BM2 的所有现有功能不受影响（所有 BM2 VO 和 API 完全独立于 PhysicalServer 体系）。

---

## 6. Open Questions 回答

### Q1: BM2 弹性模式下 Chassis 切换 Cluster 时，PhysicalServerRoleVO.clusterUuid 如何同步？

**回答**：BM2 当前不支持 Chassis 跨 Cluster 迁移。Chassis 的 `clusterUuid` 在创建时指定，之后不变。弹性模式下的"自由绑定/解绑"是在同一 Cluster 内的 Instance ↔ Chassis 关系变化，不涉及 Cluster 切换。

如果未来 BM2 支持 Chassis 的 Cluster 变更（如 `APIUpdateBareMetal2ChassisMsg` 支持修改 `clusterUuid`），则需要在该 API 处理完成后，同步更新 `PhysicalServerRoleVO.clusterUuid`。可通过 `BareMetal2ChassisUpdateExtensionPoint`（如有）或消息拦截器实现。

### Q2: BM2 的电源管理网关（通过 BM2 agent 代理 IPMI）是否需要覆盖 PowerManageable 默认实现？

**回答**：**需要覆盖**。BM2 的电源管理流程不是直接从 ZStack 管理节点发送 IPMI 命令，而是通过 BM2 Gateway Agent 代理执行。流程如下：

```
统一电源管理 API → Bm2PowerManageable.powerOn(serverUuid)
    → 查找 PhysicalServerRoleVO → 获取 chassisUuid
    → 发送 PowerOnBareMetal2ChassisMsg → BM2 Chassis Manager
    → BM2 Gateway Agent → IPMI power on
```

`Bm2PhysicalServerRoleProvider` 应同时实现 `PowerManageable` 接口：

```java
@Component
public class Bm2PhysicalServerRoleProvider
        implements PhysicalServerRoleProvider, PowerManageable {

    @Override
    public void powerOn(String serverUuid, Completion completion) {
        String chassisUuid = getRoleUuidByServerUuid(serverUuid);
        if (chassisUuid == null) {
            completion.fail(operr("No BM2 chassis found for server[uuid=%s]", serverUuid));
            return;
        }

        APIPowerOnBareMetal2ChassisMsg msg = new APIPowerOnBareMetal2ChassisMsg();
        msg.setChassisUuid(chassisUuid);
        bus.send(msg, new CloudBusCallBack(completion) {
            @Override
            public void run(MessageReply reply) {
                if (reply.isSuccess()) {
                    completion.success();
                } else {
                    completion.fail(reply.getError());
                }
            }
        });
    }

    // powerOff、powerReset、getPowerStatus 类似委托给 BM2 现有 API
}
```

### Q3: BM2 的 BareMetal2ChassisOfferingVO（硬件规格模板）在统一模型中如何处理？

**回答**：`BareMetal2ChassisOfferingVO` 保持 BM2 内部管理，不迁移到统一模型。

理由：
1. ChassisOffering 是 BM2 弹性分配的核心机制（按规格匹配而非指定 Chassis），与统一分配引擎的 CPU/Memory 容量分配理念不同
2. ChassisOffering 由硬件发现自动创建，有 BM2 特有字段（`provisionType`、`bootMode`）
3. 统一模型的 `PhysicalServerHardwareInfoVO` 已覆盖硬件汇总信息，但不替代 Offering 的"模板匹配"功能

BM2 的 chassisOffering 过滤在 BM2 自身的分配链中处理（阶段2），ServerAllocatorChain（阶段1）不涉及角色特有参数。

### Q4: BM2 的 Bonding（网卡绑定）约束在统一分配中如何体现？

**回答**：Bonding 约束保持在 BM2 内部处理。

BM2 的分配流程中，如果 Instance 已关联 Bonding，会强制分配到特定 Chassis（`BareMetal2InstanceAllocateChassisFlow` 第 49-56 行）。这个逻辑通过 `ServerAllocatorFilterExtensionPoint` 接入统一分配引擎：

```java
/**
 * BM2 Bonding 约束过滤器。
 * 通过 ServerAllocatorFilterExtensionPoint 注入到统一分配链。
 */
public class Bm2BondingConstraintFilter implements ServerAllocatorFilterExtensionPoint {

    @Override
    public List<PhysicalServerVO> filterServerCandidates(
            List<PhysicalServerVO> candidates, ServerAllocatorSpec spec) {

        if (spec.getRequiredRoleType() != ServerRoleType.BAREMETAL_V2) {
            return candidates;  // 非 BM2 分配，跳过
        }

        NeedReplyMessage originalMsg = spec.getOriginalMessage();
        if (!(originalMsg instanceof AllocateBareMetal2ChassisMsg)) {
            return candidates;
        }

        String instanceUuid = ((AllocateBareMetal2ChassisMsg) originalMsg)
            .getBareMetal2InstanceUuid();
        // ... Bonding 约束逻辑（与现有代码一致）
        return filteredCandidates;
    }
}
```

### Q5: 非 IPMI 类型的 BM2 Chassis 如何处理 OOB 信息？

**回答**：BM2 的 Chassis `type` 字段是扩展点，当前实现仅有 `"ipmi"` 类型。IPMI 字段存储在子类 `BareMetal2IpmiChassisVO` 中。

对于非 IPMI 类型的 Chassis（如未来的 Redfish 类型）：
1. `PhysicalServerAO.oobManagementType` 设置为对应类型（`REDFISH`）
2. OOB 地址/端口/凭据从对应子类 VO 获取
3. 如果无 OOB 信息（如 `Direct` provision type 的 Chassis 可能无 BMC），则 OOB 字段留空，`PhysicalServerAO.oobManagementType = null`

### Q6: BM2 的 ProvisionType（Remote/Local/Direct）在统一模型中需要记录吗？

**回答**：不在 `PhysicalServerAO` 中记录。`provisionType` 是 BM2 业务层的概念（影响装机流程：是通过网络下发 OS 还是从本地磁盘启动），不属于物理服务器层面的属性。

该信息通过以下方式可访问：
1. `Bm2RoleInventory.provisionType` — 通过 `getInventory()` 查询
2. `BareMetal2ChassisVO.provisionType` — 通过原始 BM2 API 查询
3. 统一查询 API 返回的 `PhysicalServerInventory.roles[]` 中包含 `Bm2RoleInventory`

---

## 附录 A：BM2 适配涉及的代码变更清单

| 变更位置 | 变更类型 | 说明 |
|---------|---------|------|
| `premium/baremetal2/server/Bm2PhysicalServerRoleProvider.java` | **新增** | SPI 实现类 |
| `premium/baremetal2/server/Bm2RoleInventory.java` | **新增** | BM2 角色 Inventory |
| `premium/baremetal2/server/Bm2BondingConstraintFilter.java` | **新增** | Bonding 约束过滤扩展 |
| `premium/baremetal2/chassis/BareMetal2ChassisManagerImpl.java` | **增量** | 添加 status 变化钩子调用（不改现有逻辑） |
| `conf/db/migration/V*.sql` | **新增** | BM2 存量数据迁移脚本 |

**不修改的文件**（保护 git blame）：
- `BareMetal2ChassisAO.java` / `BareMetal2ChassisVO.java` / `BareMetal2IpmiChassisVO.java`
- `BareMetal2InstanceVO.java`
- `BareMetal2ProvisionNetworkVO.java` / `BareMetal2ProvisionNetworkClusterRefVO.java`
- `BareMetal2GatewayVO.java` / `BareMetal2GatewayProvisionNicVO.java`
- `BareMetal2ChassisOfferingVO.java`
- 所有 BM2 API Message 类
- 所有 BM2 分配流程 Flow 类

## 附录 B：BM2 VO 关系图与统一模型映射

```
BM2 现有模型                               统一模型
============                               ========

BareMetal2IpmiChassisVO                     PhysicalServerVO
  ├── ipmiAddress ──────────────────────→     ├── oobAddress
  ├── ipmiPort ─────────────────────────→     ├── oobPort
  ├── ipmiUsername ─────────────────────→     ├── oobUsername
  └── ipmiPassword ─────────────────────→     └── oobPassword
         ↑ extends
BareMetal2ChassisVO                              ↑ 1:1 映射
  ├── uuid ──────────────────────────→   PhysicalServerRoleVO.roleUuid
  ├── zoneUuid ────────────────────→     PhysicalServerVO.zoneUuid
  ├── clusterUuid ─────────────────→     PhysicalServerRoleVO.clusterUuid
  ├── state ───────────────────────→     PhysicalServerVO.state
  ├── status ──────────────────────→     (getCapacityConsumption 判据)
  ├── powerStatus ─────────────────→     PhysicalServerVO.powerStatus
  ├── provisionType ───────────────→     Bm2RoleInventory.provisionType
  └── chassisOfferingUuid ─────────→     Bm2RoleInventory.chassisOfferingUuid

BareMetal2ProvisionNetworkVO                PhysicalServerProvisionNetworkVO
  ├── dhcpInterface ────────────────→     ├── dhcpInterface
  ├── dhcpRangeStartIp ────────────→     ├── dhcpRangeStartIp
  ├── dhcpRangeEndIp ──────────────→     ├── dhcpRangeEndIp
  ├── dhcpRangeNetmask ────────────→     ├── dhcpRangeNetmask
  ├── dhcpRangeGateway ────────────→     ├── dhcpRangeGateway
  └── state ───────────────────────→     └── state

BareMetal2ProvisionNetworkClusterRefVO      PhysicalServerProvisionNetworkClusterRefVO
  ├── networkUuid ─────────────────→       ├── networkUuid (统一 UUID)
  └── clusterUuid ─────────────────→       └── clusterUuid

BareMetal2GatewayVO                         (保留 BM2 内部管理，不迁移)
BareMetal2GatewayProvisionNicVO             (保留 BM2 内部管理，不迁移)
BareMetal2ChassisOfferingVO                 (保留 BM2 内部管理，不迁移)
BareMetal2BondingVO                         (保留 BM2 内部管理，不迁移)
BareMetal2InstanceVO                        (保留 BM2 内部管理，消费层不变)
```
