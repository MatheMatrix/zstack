# BM1 角色适配详细设计

**版本**: v1.0
**日期**: 2026-03-18
**作者**: Baremetal V1 Architecture Expert
**输入**: architecture-unified-hardware-2026-03-18.md (第 3/6/9 章), PRD FR-024/FR-010~012, ANALYSIS_baremetal_module.md, REVIEW_baremetal_v1.md

---

## 1. Bm1PhysicalServerRoleProvider 实现设计

### 1.1 类定义

```java
package org.zstack.baremetal.chassis;

import org.zstack.header.server.*;
import org.zstack.header.server.enums.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * BM1 角色适配器。
 *
 * 职责：
 * 1. 将 BaremetalChassisVO 生命周期事件同步到 PhysicalServerVO / PhysicalServerRoleVO
 * 2. 报告独占模式下的容量消耗
 * 3. 提供角色匹配逻辑（通过 IPMI 地址 + serialNumber 匹配）
 *
 * 不做的事：
 * - 不修改 BaremetalChassisManagerImpl 的任何方法签名
 * - 不改变 BM1 的 PXE/IPMI 逻辑
 * - 不介入 BaremetalInstanceVO 的生命周期
 */
@Component
public class Bm1PhysicalServerRoleProvider implements PhysicalServerRoleProvider {

    @Autowired
    private DatabaseFacade dbf;
    @Autowired
    private PhysicalServerManager physicalServerManager;

    // ... 下文逐方法说明
}
```

### 1.2 getRoleType()

```java
@Override
public ServerRoleType getRoleType() {
    return ServerRoleType.BAREMETAL_V1;
}
```

返回常量 `BAREMETAL_V1`，全局唯一。分配引擎通过此值路由到 BM1 适配器。

### 1.3 getSchedulingMode()

```java
@Override
public SchedulingMode getSchedulingMode() {
    return SchedulingMode.INTERNAL_EXCLUSIVE;
}
```

BM1 为整机独占模式。分配引擎在 `INTERNAL_EXCLUSIVE` 模式下：
- 分配时将 `PhysicalServerCapacityVO` 的 `availableCpu`/`availableMemory`/`availableDisk` 全部清零
- 释放时恢复为物理容量值

### 1.4 getCapacityConsumption(serverUuid)

```java
/**
 * BM1 独占模式下的容量报告。
 *
 * 逻辑：
 * 1. 通过 serverUuid 查找 PhysicalServerRoleVO (roleType=BAREMETAL_V1)
 * 2. 通过 roleUuid 查找 BaremetalChassisVO
 * 3. 如果 ChassisStatus == Allocated（即有 Instance 占用），返回全部物理容量
 * 4. 如果 ChassisStatus == Available/HWInfoUnknown，返回零消耗
 *
 * 边界情况：
 * - HWInfoUnknown 状态下物理容量尚未发现，CapacityVO 可能全为 0，
 *   此时返回零消耗是正确的（独占分配在 Allocated 状态才计入消耗）
 */
@Override
public CapacityUsage getCapacityConsumption(String serverUuid) {
    CapacityUsage usage = new CapacityUsage();

    PhysicalServerRoleVO roleVO = Q.New(PhysicalServerRoleVO.class)
        .eq(PhysicalServerRoleVO_.serverUuid, serverUuid)
        .eq(PhysicalServerRoleVO_.roleType, ServerRoleType.BAREMETAL_V1)
        .find();

    if (roleVO == null) {
        return usage; // 零消耗
    }

    BaremetalChassisVO chassisVO = dbf.findByUuid(roleVO.getRoleUuid(), BaremetalChassisVO.class);
    if (chassisVO == null || chassisVO.getStatus() != BaremetalChassisStatus.Allocated) {
        return usage; // 未分配，零消耗
    }

    // 独占：返回全部物理容量作为已消耗量
    PhysicalServerCapacityVO capacityVO = dbf.findByUuid(serverUuid, PhysicalServerCapacityVO.class);
    if (capacityVO != null) {
        usage.setUsedCpu(capacityVO.getTotalPhysicalCpu());
        usage.setUsedMemory(capacityVO.getTotalPhysicalMemory());
        usage.setUsedDisk(capacityVO.getTotalDisk());
    }

    return usage;
}
```

### 1.5 onPhysicalServerCreated(serverUuid)

```java
/**
 * PhysicalServer 创建后的回调。
 *
 * BM1 场景下此方法为空操作（no-op），因为 BM1 的数据流是反向的：
 * - 用户先创建 BaremetalChassisVO（通过 APICreateBaremetalChassisMsg）
 * - PostCreate 钩子负责创建 PhysicalServerVO + RoleVO
 * - 不存在先有 PhysicalServerVO 再通知 BM1 创建 Chassis 的场景
 *
 * 未来可用于：通过统一 API 注册物理服务器后，反向创建 BM1 Chassis 的场景。
 */
@Override
public void onPhysicalServerCreated(String serverUuid) {
    // BM1 数据流为 Chassis → PhysicalServer，此回调为空操作
}
```

### 1.6 onPhysicalServerDeleted(serverUuid)

```java
/**
 * PhysicalServer 删除前的回调。
 *
 * 清理动作：
 * 1. 将对应的 PhysicalServerRoleVO (BAREMETAL_V1) 的 roleStatus 置为 Stale
 * 2. 不 cascade 删除 BaremetalChassisVO — Chassis 的删除由 BM1 模块自身管理
 *
 * 设计理由：
 * - PhysicalServerVO 是派生数据，删除它不应影响 BM1 的核心实体
 * - 参考 REVIEW_baremetal_v1.md 第八章建议：PhysicalServerVO 删除语义仅解除 Role 映射
 */
@Override
public void onPhysicalServerDeleted(String serverUuid) {
    UpdateQuery.New(PhysicalServerRoleVO.class)
        .eq(PhysicalServerRoleVO_.serverUuid, serverUuid)
        .eq(PhysicalServerRoleVO_.roleType, ServerRoleType.BAREMETAL_V1)
        .set(PhysicalServerRoleVO_.roleStatus, "Stale")
        .update();
}
```

### 1.7 getInventory(roleUuid)

```java
/**
 * 查询 BM1 角色详情。
 *
 * 将 BaremetalChassisVO 转换为 RoleInventory 子类返回，
 * 用于单独查询角色详情，QueryPhysicalServerMsg 只返回 ref。
 */
@Override
public RoleInventory getInventory(String roleUuid) {
    BaremetalChassisVO chassisVO = dbf.findByUuid(roleUuid, BaremetalChassisVO.class);
    if (chassisVO == null) {
        return null;
    }

    Bm1RoleInventory inv = new Bm1RoleInventory();
    inv.setRoleUuid(roleUuid);
    inv.setRoleType(ServerRoleType.BAREMETAL_V1.toString());
    inv.setClusterUuid(chassisVO.getClusterUuid());
    inv.setStatus(chassisVO.getStatus().toString());
    // BM1 特有字段
    inv.setChassisState(chassisVO.getState().toString());
    inv.setChassisStatus(chassisVO.getStatus().toString());
    inv.setPxeServerUuid(chassisVO.getPxeServerUuid());
    return inv;
}
```

辅助数据类：

```java
package org.zstack.baremetal.chassis;

import org.zstack.header.server.RoleInventory;

/**
 * BM1 角色 Inventory 子类，携带 Chassis 特有信息。
 */
public class Bm1RoleInventory extends RoleInventory {
    private String chassisState;
    private String chassisStatus;
    private String pxeServerUuid;

    // getter/setter 省略
}
```

### 1.8 matchExistingServer(context)

```java
/**
 * BM1 角色匹配逻辑。
 *
 * 匹配优先级：
 * 1. serialNumber + zoneUuid 精确匹配
 * 2. oobAddress(ipmiAddress) + zoneUuid 降级匹配
 * 3. managementIp + zoneUuid 降级匹配（BM1 的 managementIp 来自 Instance，
 *    Chassis 创建时可能无 managementIp，因此优先级低于 oobAddress）
 *
 * BM1 特殊考虑：
 * - BM1 Chassis 通过 ipmiAddress + ipmiPort 唯一标识（参考 REVIEW_baremetal_v1.md 第三章）
 * - serialNumber 在 Chassis 创建时可能不可用（HWInfoUnknown 状态），
 *   需依赖 IPMI FRU 发现后回填
 */
@Override
public String matchExistingServer(RoleMatchContext context) {
    // 优先级 1: serialNumber 精确匹配
    if (context.getSerialNumber() != null
            && !context.getSerialNumber().isEmpty()
            && !"Not Specified".equals(context.getSerialNumber())) {

        PhysicalServerVO matched = Q.New(PhysicalServerVO.class)
            .eq(PhysicalServerVO_.serialNumber, context.getSerialNumber())
            .eq(PhysicalServerVO_.zoneUuid, context.getZoneUuid())
            .find();

        if (matched != null) {
            return matched.getUuid();
        }
    }

    // 优先级 2: oobAddress (ipmiAddress) 匹配
    // BM1 Chassis 以 ipmiAddress 为核心标识，映射到 PhysicalServerVO.oobAddress
    if (context.getOobAddress() != null) {
        PhysicalServerVO matched = Q.New(PhysicalServerVO.class)
            .eq(PhysicalServerVO_.oobAddress, context.getOobAddress())
            .eq(PhysicalServerVO_.zoneUuid, context.getZoneUuid())
            .find();

        if (matched != null) {
            return matched.getUuid();
        }
    }

    // 优先级 3: managementIp 降级匹配
    if (context.getManagementIp() != null) {
        PhysicalServerVO matched = Q.New(PhysicalServerVO.class)
            .eq(PhysicalServerVO_.managementIp, context.getManagementIp())
            .eq(PhysicalServerVO_.zoneUuid, context.getZoneUuid())
            .find();

        if (matched != null) {
            return matched.getUuid();
        }
    }

    return null; // 无匹配，需新建 PhysicalServerVO
}
```

**RoleMatchContext 扩展**：需要在 `RoleMatchContext` 中新增 `oobAddress` 字段，用于 BM1 基于 IPMI 地址的匹配。此扩展向后兼容（新增字段，不改已有签名）。

```java
// RoleMatchContext 新增字段（不改已有字段）
public class RoleMatchContext {
    private String serialNumber;
    private String managementIp;
    private String zoneUuid;
    private String clusterUuid;
    private String oobAddress;  // 新增：BM1 用 ipmiAddress 匹配

    // getter/setter 省略
}
```

---

## 2. Chassis 创建/删除同步钩子

### 2.1 注入方式：ExtensionPoint（推荐）

**选型对比**：

| 方式 | 优点 | 缺点 | 结论 |
|------|------|------|------|
| ExtensionPoint | 符合 ZStack 惯例；解耦；BM1 模块不依赖 server 模块 | 需在 header/ 定义新 ExtensionPoint | **推荐** |
| PostCreate 钩子 (BaremetalChassisManagerImpl 内部) | 侵入最小 | 需修改 BaremetalChassisManagerImpl 代码 | 不推荐 |
| EventFacade | REVIEW 建议方案；最低侵入 | 事件顺序不可控；事务边界模糊 | 备选 |

**推荐方案**：在 `header/` 中定义 `BaremetalChassisLifecycleExtensionPoint`，统一层实现此 ExtensionPoint。BM1 的 `BaremetalChassisManagerImpl` 在创建/删除 Chassis 时回调该扩展点——**BM1 模块本身已有大量 ExtensionPoint 回调模式**，新增一个扩展点是最自然的注入方式。

### 2.2 ExtensionPoint 定义

```java
package org.zstack.header.baremetal.chassis;

/**
 * BaremetalChassis 生命周期扩展点。
 *
 * 在 BaremetalChassisManagerImpl 的 createChassis/deleteChassis 流程中回调。
 * 统一层 (server 模块) 实现此扩展点，将 Chassis 事件同步到 PhysicalServerVO。
 */
public interface BaremetalChassisLifecycleExtensionPoint {

    /**
     * Chassis 创建成功后回调。
     * 在事务内调用，实现方可参与同一事务。
     *
     * @param chassisVO 新创建的 BaremetalChassisVO
     */
    void afterCreateBaremetalChassis(BaremetalChassisVO chassisVO);

    /**
     * Chassis 删除前回调。
     * 在事务内调用，实现方可参与同一事务。
     *
     * @param chassisVO 即将删除的 BaremetalChassisVO
     */
    void beforeDeleteBaremetalChassis(BaremetalChassisVO chassisVO);

    /**
     * Chassis IPMI 信息更新后回调。
     * 用于同步 OOB 凭据变更到 PhysicalServerVO。
     *
     * @param chassisVO 更新后的 BaremetalChassisVO
     */
    void afterUpdateBaremetalChassisIpmi(BaremetalChassisVO chassisVO);
}
```

### 2.3 BaremetalChassisManagerImpl 中的注入位置

在 `BaremetalChassisManagerImpl` 的以下方法中添加扩展点回调（不改方法签名，仅在方法体末尾追加回调）：

```
createChassis() 方法：
  ├── ... 现有创建逻辑（不变）...
  ├── dbf.persist(chassisVO)
  ├── ... 现有 ExtensionPoint 回调 ...
  └── 新增: pluginRgty.getExtensionList(BaremetalChassisLifecycleExtensionPoint.class)
            .forEach(ext -> ext.afterCreateBaremetalChassis(chassisVO));

deleteChassis() 方法：
  ├── 新增: pluginRgty.getExtensionList(BaremetalChassisLifecycleExtensionPoint.class)
  │         .forEach(ext -> ext.beforeDeleteBaremetalChassis(chassisVO));
  ├── ... 现有删除逻辑（不变）...
  └── dbf.remove(chassisVO)

updateChassis() 方法（IPMI 字段变更时）：
  ├── ... 现有更新逻辑（不变）...
  ├── dbf.update(chassisVO)
  └── 新增（仅 IPMI 字段变更时）:
      pluginRgty.getExtensionList(BaremetalChassisLifecycleExtensionPoint.class)
            .forEach(ext -> ext.afterUpdateBaremetalChassisIpmi(chassisVO));
```

### 2.4 统一层实现：Bm1ChassisLifecycleSynchronizer

```java
package org.zstack.server.roleprovider;

/**
 * BM1 Chassis 生命周期到 PhysicalServerVO 的同步器。
 *
 * 实现 BaremetalChassisLifecycleExtensionPoint，
 * 在 Chassis 创建/删除/IPMI 更新时同步到统一层。
 *
 * 放置位置：server/ 模块（不在 premium/baremetal/ 中，
 * 避免 premium 代码反向依赖 server 模块）。
 */
@Component
public class Bm1ChassisLifecycleSynchronizer
        implements BaremetalChassisLifecycleExtensionPoint {

    @Autowired
    private DatabaseFacade dbf;
    @Autowired
    private Bm1PhysicalServerRoleProvider bm1Provider;

    @Override
    public void afterCreateBaremetalChassis(BaremetalChassisVO chassisVO) {
        // 1. 构造匹配上下文
        RoleMatchContext ctx = new RoleMatchContext();
        ctx.setZoneUuid(chassisVO.getZoneUuid());
        ctx.setOobAddress(chassisVO.getIpmiAddress());
        // serialNumber 在创建时可能不可用（HWInfoUnknown），设为 null
        ctx.setSerialNumber(null);
        ctx.setClusterUuid(chassisVO.getClusterUuid());

        // 2. 匹配或新建 PhysicalServerVO
        String serverUuid = bm1Provider.matchExistingServer(ctx);

        if (serverUuid == null) {
            // 新建 PhysicalServerVO
            PhysicalServerVO serverVO = new PhysicalServerVO();
            serverVO.setUuid(Platform.getUuid());
            serverVO.setZoneUuid(chassisVO.getZoneUuid());
            serverVO.setName(chassisVO.getName());
            serverVO.setState(PhysicalServerState.Enabled);
            serverVO.setStatus(PhysicalServerStatus.Connecting);
            // OOB 同步
            syncIpmiToOob(serverVO, chassisVO);
            dbf.persist(serverVO);
            serverUuid = serverVO.getUuid();

            // 初始化 CapacityVO（全零，等待硬件发现后填充）
            PhysicalServerCapacityVO capVO = new PhysicalServerCapacityVO();
            capVO.setUuid(serverUuid);
            capVO.setCapacityState(CapacityState.Initialized);
            dbf.persist(capVO);
        } else {
            // 已有 PhysicalServerVO，同步 OOB 信息
            PhysicalServerVO serverVO = dbf.findByUuid(serverUuid, PhysicalServerVO.class);
            syncIpmiToOob(serverVO, chassisVO);
            dbf.update(serverVO);
        }

        // 3. 创建 PhysicalServerRoleVO
        PhysicalServerRoleVO roleVO = new PhysicalServerRoleVO();
        roleVO.setUuid(Platform.getUuid());
        roleVO.setServerUuid(serverUuid);
        roleVO.setRoleType(ServerRoleType.BAREMETAL_V1);
        roleVO.setRoleUuid(chassisVO.getUuid());
        roleVO.setClusterUuid(chassisVO.getClusterUuid());
        roleVO.setSchedulingMode(SchedulingMode.INTERNAL_EXCLUSIVE);
        roleVO.setRoleStatus("Active");
        dbf.persist(roleVO);
    }

    @Override
    public void beforeDeleteBaremetalChassis(BaremetalChassisVO chassisVO) {
        // 1. 更新 RoleVO 状态为 Stale
        UpdateQuery.New(PhysicalServerRoleVO.class)
            .eq(PhysicalServerRoleVO_.roleUuid, chassisVO.getUuid())
            .eq(PhysicalServerRoleVO_.roleType, ServerRoleType.BAREMETAL_V1)
            .set(PhysicalServerRoleVO_.roleStatus, "Stale")
            .update();

        // 2. 检查是否为最后一个角色，如是则更新 PhysicalServerVO 状态
        String serverUuid = Q.New(PhysicalServerRoleVO.class)
            .eq(PhysicalServerRoleVO_.roleUuid, chassisVO.getUuid())
            .select(PhysicalServerRoleVO_.serverUuid)
            .findValue();

        if (serverUuid != null) {
            long activeRoles = Q.New(PhysicalServerRoleVO.class)
                .eq(PhysicalServerRoleVO_.serverUuid, serverUuid)
                .eq(PhysicalServerRoleVO_.roleStatus, "Active")
                .notEq(PhysicalServerRoleVO_.roleUuid, chassisVO.getUuid())
                .count();

            if (activeRoles == 0) {
                UpdateQuery.New(PhysicalServerVO.class)
                    .eq(PhysicalServerVO_.uuid, serverUuid)
                    .set(PhysicalServerVO_.status, PhysicalServerStatus.Disconnected)
                    .update();
            }
        }
    }

    @Override
    public void afterUpdateBaremetalChassisIpmi(BaremetalChassisVO chassisVO) {
        // 查找对应的 PhysicalServerVO 并同步 OOB 字段
        String serverUuid = Q.New(PhysicalServerRoleVO.class)
            .eq(PhysicalServerRoleVO_.roleUuid, chassisVO.getUuid())
            .eq(PhysicalServerRoleVO_.roleType, ServerRoleType.BAREMETAL_V1)
            .select(PhysicalServerRoleVO_.serverUuid)
            .findValue();

        if (serverUuid != null) {
            PhysicalServerVO serverVO = dbf.findByUuid(serverUuid, PhysicalServerVO.class);
            syncIpmiToOob(serverVO, chassisVO);
            dbf.update(serverVO);
        }
    }

    /**
     * 将 IPMI 凭据同步到 PhysicalServerVO 的 OOB 字段。
     */
    private void syncIpmiToOob(PhysicalServerVO serverVO, BaremetalChassisVO chassisVO) {
        serverVO.setOobManagementType(OobManagementType.IPMI);
        serverVO.setOobAddress(chassisVO.getIpmiAddress());
        serverVO.setOobPort(chassisVO.getIpmiPort());
        serverVO.setOobUsername(chassisVO.getIpmiUsername());
        serverVO.setOobPassword(chassisVO.getIpmiPassword());
    }
}
```

### 2.5 IPMI 信息到 PhysicalServerVO OOB 字段的映射

| BaremetalChassisVO 字段 | PhysicalServerAO 字段 | 说明 |
|------------------------|----------------------|------|
| ipmiAddress | oobAddress | BMC IP 地址 |
| ipmiPort | oobPort | IPMI 端口（默认 623） |
| ipmiUsername | oobUsername | BMC 用户名 |
| ipmiPassword | oobPassword | BMC 密码（两端均 @EncryptColumn 加密） |
| (固定 IPMI) | oobManagementType | 固定为 `OobManagementType.IPMI` |

密码同步注意事项：
- BaremetalChassisVO 使用 `@ENCRYPTParam` 加密存储 ipmiPassword
- PhysicalServerAO 使用 `@EncryptColumn` + `PasswordConverter` 加密存储 oobPassword
- 同步时需解密后重新加密，或直接传递明文由目标端的 JPA Converter 自动加密
- 推荐后者：`chassisVO.getIpmiPassword()` 返回解密后的明文（Hibernate 自动解密），`serverVO.setOobPassword(plaintext)` 由 `PasswordConverter` 自动加密持久化

### 2.6 serialNumber 获取方式

BM1 Chassis 创建时 serialNumber 通常不可用（状态为 `HWInfoUnknown`）。获取时机和路径：

```
serialNumber 获取流程：

1. Chassis 创建时（APICreateBaremetalChassisMsg）：
   ├── serialNumber = null（未知）
   └── PhysicalServerVO.serialNumber = null

2. 硬件发现时（APIInspectBaremetalChassisMsg）：
   ├── IPMI Set Boot Device → PXE
   ├── Power Reset
   ├── 发现环境 PXE 启动
   ├── 发现 Agent 采集硬件信息
   ├── POST /baremetal/chassis/sendhardwareinfo 回调
   │   ├── 解析 BaremetalHardwareInfoVO (type="basic")
   │   │   └── JSON content 中包含 "productSerial" 字段
   │   └── 也可从 IPMI FRU data 获取：
   │       ipmitool -H <bmc_ip> -U <user> -P <pass> fru print
   │       → "Product Serial" 字段
   └── 回填 PhysicalServerVO.serialNumber

3. 回填时机（新增逻辑）：
   在 BaremetalChassisManagerImpl.handleSendHardwareInfo() 末尾
   追加回填逻辑：
   ├── 从 BaremetalHardwareInfoVO (type="basic") 解析 productSerial
   ├── 更新 PhysicalServerVO.serialNumber
   └── 如果 serialNumber 已存在且不同，记录 WARN 日志
```

**IPMI FRU 命令示例**：
```bash
ipmitool -H 10.0.1.100 -U admin -P password fru print 0
# 输出中 "Product Serial" 字段即为 serialNumber
# 例：Product Serial  : ABCD1234567890
```

**备选获取路径**（Agent 内）：
```bash
cat /sys/class/dmi/id/product_serial
# 或
dmidecode -s system-serial-number
```

---

## 3. PXE 装机网络迁移

### 3.1 BaremetalPxeServerVO → PhysicalServerProvisionNetworkVO 字段映射

| BaremetalPxeServerVO 字段 | PhysicalServerProvisionNetworkVO 字段 | 映射说明 |
|--------------------------|--------------------------------------|---------|
| uuid | (新生成 UUID) | 不复用 PxeServer UUID，通过 SystemTag 记录映射关系 |
| (通过 ClusterRef → Zone 推导) | zoneUuid | BM1 PxeServer 无直接 zoneUuid，需从关联 Cluster 推导 |
| hostname | (无直接对应) | PxeServer 的 hostname/IP 是 SSH 管理地址，非 DHCP 概念 |
| dhcpInterface | dhcpInterface | 直接映射，DHCP 服务监听的网卡名 |
| (从 DHCP 配置解析) | dhcpRangeStartIp | 需从 PxeServer 上的 dnsmasq.conf 解析 `dhcp-range` |
| (从 DHCP 配置解析) | dhcpRangeEndIp | 同上 |
| (从 DHCP 配置解析) | dhcpRangeNetmask | 同上 |
| (从 DHCP 配置解析) | dhcpRangeGateway | 同上 |
| (固定 STANDALONE_PXE) | type | BM1 使用独立 PXE 服务器模式 |
| state | state | Enabled/Disabled 直接映射 |
| (无直接对应) | name | 默认取 `"BM1-PXE-" + hostname` |

**关键差异**：BM1 的 `BaremetalPxeServerVO` 不直接存储 DHCP 范围——DHCP 配置由 PxeServer 上的 dnsmasq 服务管理。迁移时需要：
1. 通过 SSH 连接到 PxeServer 读取 dnsmasq 配置，或
2. 从 `BaremetalPxeServerVO` 关联的 Chassis 分配记录推算 DHCP 范围，或
3. 要求管理员在迁移时手动补充 DHCP 范围

**推荐方案**：方案 3（管理员手动补充），因为自动推导不可靠且 dnsmasq 配置格式可能变化。迁移脚本生成待补充记录，管理员通过 API 补全。

### 3.2 BaremetalPxeServerClusterRefVO → PhysicalServerProvisionNetworkClusterRefVO 映射

| BaremetalPxeServerClusterRefVO 字段 | PhysicalServerProvisionNetworkClusterRefVO 字段 | 映射说明 |
|-------------------------------------|------------------------------------------------|---------|
| uuid | uuid | 新生成 |
| pxeServerUuid | networkUuid | 映射到对应的 ProvisionNetworkVO UUID |
| clusterUuid | clusterUuid | 直接映射 |

迁移 SQL 示意：

```sql
-- 为每个 BM1 PxeServer 创建 ProvisionNetwork 记录
INSERT IGNORE INTO PhysicalServerProvisionNetworkVO
  (uuid, zoneUuid, name, dhcpInterface, type, state, createDate, lastOpDate)
SELECT
    MD5(CONCAT('bm1-pxe-', pxe.uuid)),   -- 确定性 UUID
    c.zoneUuid,                            -- 从关联 Cluster 推导
    CONCAT('BM1-PXE-', pxe.hostname),
    pxe.dhcpInterface,
    'STANDALONE_PXE',
    CASE pxe.state WHEN 'Enabled' THEN 'Enabled' ELSE 'Disabled' END,
    pxe.createDate,
    NOW()
FROM BaremetalPxeServerVO pxe
JOIN BaremetalPxeServerClusterRefVO ref ON pxe.uuid = ref.pxeServerUuid
JOIN ClusterVO c ON ref.clusterUuid = c.uuid
GROUP BY pxe.uuid;  -- 一个 PxeServer 可能关联多个 Cluster，只取一次

-- 映射 ClusterRef
INSERT IGNORE INTO PhysicalServerProvisionNetworkClusterRefVO
  (uuid, networkUuid, clusterUuid, createDate)
SELECT
    MD5(CONCAT('bm1-pxe-ref-', ref.uuid)),
    MD5(CONCAT('bm1-pxe-', ref.pxeServerUuid)),
    ref.clusterUuid,
    NOW()
FROM BaremetalPxeServerClusterRefVO ref;

-- 记录映射关系的 SystemTag
INSERT IGNORE INTO SystemTagVO (uuid, resourceUuid, resourceType, tag, createDate, lastOpDate)
SELECT
    MD5(CONCAT('bm1-pxe-tag-', pxe.uuid)),
    MD5(CONCAT('bm1-pxe-', pxe.uuid)),
    'PhysicalServerProvisionNetworkVO',
    CONCAT('originBm1PxeServerUuid::', pxe.uuid),
    NOW(),
    NOW()
FROM BaremetalPxeServerVO pxe;
```

### 3.3 兼容策略

**核心原则**：BM1 代码继续使用 `BaremetalPxeServerVO`，统一层做数据同步。

```
数据流向（单向同步）：

BaremetalPxeServerVO ──(同步)──→ PhysicalServerProvisionNetworkVO
     ↑                                    ↑
     │                                    │
  BM1 代码读写                     统一层只读查询
  （不变）                         （跨角色查询）

运行时行为：
1. BM1 创建/删除/更新 PxeServer → 触发 BaremetalPxeServerLifecycleExtensionPoint
2. 同步器将变更同步到 PhysicalServerProvisionNetworkVO
3. 统一层的 QueryPhysicalServerProvisionNetworkMsg 可查到 BM1 的 PXE 网络
4. BM1 的 PXE 装机流程完全不变，继续操作 BaremetalPxeServerVO
```

**PxeServer 生命周期扩展点**（新增，定义在 header/ 中）：

```java
package org.zstack.header.baremetal.pxeserver;

public interface BaremetalPxeServerLifecycleExtensionPoint {
    void afterCreateBaremetalPxeServer(BaremetalPxeServerVO pxeVO);
    void beforeDeleteBaremetalPxeServer(BaremetalPxeServerVO pxeVO);
    void afterAttachBaremetalPxeServerToCluster(String pxeServerUuid, String clusterUuid);
    void afterDetachBaremetalPxeServerFromCluster(String pxeServerUuid, String clusterUuid);
}
```

---

## 4. 独占分配适配

### 4.1 INTERNAL_EXCLUSIVE 模式下 ServerCapacityUpdater 的行为

```java
/**
 * PhysicalServerCapacityUpdaterImpl 中的独占分配逻辑。
 *
 * INTERNAL_EXCLUSIVE 分支：
 */
public void decreaseCapacity(String serverUuid,
        long requiredCpu, long requiredMemory, long requiredDisk) {

    PhysicalServerRoleVO roleVO = findActiveRole(serverUuid);
    SchedulingMode mode = roleVO.getSchedulingMode();

    if (mode == SchedulingMode.INTERNAL_EXCLUSIVE) {
        // 独占模式：清零所有可用量（忽略 requiredCpu/Memory/Disk 参数）
        PhysicalServerCapacityVO cap = lockAndGet(serverUuid);

        // 边界：Initialized 状态下容量全为 0，直接标记为已分配
        cap.setAvailableCpu(0);
        cap.setAvailableMemory(0);
        cap.setAvailableDisk(0);
        cap.setCapacityState(CapacityState.Allocated);

        dbf.update(cap);

    } else if (mode == SchedulingMode.INTERNAL_SHARED) {
        // 共享模式：按需扣减（KVM 路径）
        // ...
    }
    // EXTERNAL_READONLY: 不扣减，跳过
}

public void increaseCapacity(String serverUuid,
        long releasedCpu, long releasedMemory, long releasedDisk) {

    PhysicalServerRoleVO roleVO = findActiveRole(serverUuid);
    SchedulingMode mode = roleVO.getSchedulingMode();

    if (mode == SchedulingMode.INTERNAL_EXCLUSIVE) {
        // 独占模式：恢复全部可用量
        PhysicalServerCapacityVO cap = lockAndGet(serverUuid);

        cap.setAvailableCpu(cap.getTotalCpu());
        cap.setAvailableMemory(cap.getTotalMemory());
        cap.setAvailableDisk(cap.getTotalDisk());
        cap.setCapacityState(CapacityState.Ready);

        dbf.update(cap);
    }
}
```

**Initialized 状态特殊处理**（回应 REVIEW_baremetal_v1.md 第四章建议）：

```
独占角色在 CapacityState == Initialized 时的分配行为：

问题：Chassis 创建后处于 HWInfoUnknown，物理容量全为 0，独占清零无意义。
     但 BM1 仍需要「分配」这台 Chassis 给 Instance。

解决方案：
  CapacityFilterFlow 中对 INTERNAL_EXCLUSIVE 模式的特殊处理：
  - 如果 schedulingMode == INTERNAL_EXCLUSIVE：
    - 跳过 CPU/Memory/Disk 容量检查
    - 仅检查 capacityState != "Allocated"（即未被其他独占角色占用）
  - 这样 HWInfoUnknown（capacityState=Initialized）的 Chassis 也可被分配
```

### 4.2 BM1 状态机与 PhysicalServer 状态的映射

BM1 有两层独立的状态机（Chassis 和 Instance），与 PhysicalServer 的状态机是独立的。不做合并，只做映射同步。

#### Chassis 状态 → PhysicalServer 状态映射

| BaremetalChassisState | PhysicalServerState | 同步时机 |
|----------------------|--------------------|---------|
| Enabled | Enabled | Chassis State 变更时同步 |
| Disabled | Disabled | Chassis State 变更时同步 |

| BaremetalChassisStatus | PhysicalServerStatus | 同步时机 |
|-----------------------|---------------------|---------|
| HWInfoUnknown | Connecting | Chassis 创建时 |
| PxeBooting | Connecting | 硬件发现开始时 |
| PxeBootFailed | Disconnected | 发现失败时 |
| Available | Connected | 发现完成或 Instance 释放后 |
| Allocated | Connected | Instance 分配后（仍然在线） |

**设计说明**：
- PhysicalServerStatus 只表达「连接状态」（Connecting/Connected/Disconnected），不表达业务状态
- BM1 Chassis 的业务状态（Available/Allocated）通过 `PhysicalServerRoleVO.roleStatus` + `PhysicalServerCapacityVO.capacityState` 间接表达
- 这遵循了 REVIEW_baremetal_v1.md 第六章的方案 B 建议：在 RoleVO 中自管理运行状态

#### Instance 状态（不映射）

BaremetalInstanceVO 的状态机（Created → Starting → Running → Stopped → Destroyed）完全在 BM1 消费层管理，不映射到 PhysicalServer 层。PhysicalServer 层不感知 Instance 的存在。

#### 状态同步实现

在 `Bm1ChassisLifecycleSynchronizer` 中增加状态变更同步方法：

```java
/**
 * Chassis 状态变更时同步到 PhysicalServerVO。
 *
 * 注入点：BaremetalChassisManagerImpl 中已有的
 * ChangeBaremetalChassisStateExtensionPoint 或 Chassis 状态变更逻辑末尾。
 */
public void syncChassisStatusToPhysicalServer(
        BaremetalChassisVO chassisVO, BaremetalChassisStatus newStatus) {

    String serverUuid = Q.New(PhysicalServerRoleVO.class)
        .eq(PhysicalServerRoleVO_.roleUuid, chassisVO.getUuid())
        .select(PhysicalServerRoleVO_.serverUuid)
        .findValue();

    if (serverUuid == null) return;

    PhysicalServerStatus psStatus;
    switch (newStatus) {
        case HWInfoUnknown:
        case PxeBooting:
            psStatus = PhysicalServerStatus.Connecting;
            break;
        case PxeBootFailed:
            psStatus = PhysicalServerStatus.Disconnected;
            break;
        case Available:
        case Allocated:
            psStatus = PhysicalServerStatus.Connected;
            break;
        default:
            psStatus = PhysicalServerStatus.Disconnected;
    }

    UpdateQuery.New(PhysicalServerVO.class)
        .eq(PhysicalServerVO_.uuid, serverUuid)
        .set(PhysicalServerVO_.status, psStatus)
        .update();
}
```

---

## 5. 兼容性风险和迁移

### 5.1 BM1 现有 API 和行为不变的保证

| 保证项 | 实现方式 | 验证方法 |
|--------|---------|---------|
| APICreateBaremetalChassisMsg 行为不变 | 仅在方法体末尾追加 ExtensionPoint 回调 | 现有 BM1 集成测试全量通过 |
| APIDeleteBaremetalChassisMsg 行为不变 | 仅在方法体前追加 ExtensionPoint 回调 | 同上 |
| APIInspectBaremetalChassisMsg 行为不变 | 在 sendHardwareInfo 末尾追加 serialNumber 回填 | 同上 |
| IPMI 电源操作 API 不变 | 不改 IPMI 操作代码 | 同上 |
| PXE 装机流程不变 | PxeServer 代码不改，同步层独立运行 | 同上 |
| BaremetalChassisVO Schema 不变 | 不改 VO 定义 | 编译验证 |
| BaremetalPxeServerVO Schema 不变 | 不改 VO 定义 | 编译验证 |
| BaremetalInstanceVO 生命周期不变 | 统一层不感知 Instance | 同上 |

**不改的文件清单**（git blame 保护）：
- `BaremetalChassisVO.java` — 不改
- `BaremetalChassisAO.java` — 不改
- `BaremetalInstanceVO.java` — 不改
- `BaremetalPxeServerVO.java` — 不改
- `BaremetalPxeServerClusterRefVO.java` — 不改
- `BaremetalHardwareInfoVO.java` — 不改

**会改的文件**（最小侵入）：
- `BaremetalChassisManagerImpl.java` — 仅追加 3 处 ExtensionPoint 回调（createChassis 末尾、deleteChassis 前、updateChassis 中 IPMI 字段变更时）
- `BaremetalPxeServerManagerImpl.java` — 仅追加 4 处 ExtensionPoint 回调

### 5.2 存量 BaremetalChassisVO → PhysicalServerVO 迁移脚本

```sql
-- ============================================================
-- BM1 存量 Chassis 迁移到 PhysicalServerVO
-- 幂等：INSERT IGNORE + 确定性 UUID (MD5)
-- ============================================================

-- 1. 为每个 Zone 创建默认 ServerPool（如果不存在）
INSERT IGNORE INTO ServerPoolVO
  (uuid, zoneUuid, name, description, state, createDate, lastOpDate)
SELECT
    MD5(CONCAT('default-pool-', z.uuid)),
    z.uuid,
    'default-pool',
    'Auto-created default pool for migration',
    'Enabled',
    NOW(),
    NOW()
FROM ZoneVO z;

-- 2. 为每个 BM1 Chassis 创建 PhysicalServerVO
INSERT IGNORE INTO PhysicalServerVO
  (uuid, zoneUuid, poolUuid, name, managementIp, serialNumber,
   state, status, oobManagementType, oobAddress, oobPort,
   oobUsername, oobPassword, createDate, lastOpDate)
SELECT
    MD5(CONCAT('bm1-chassis-', chassis.uuid)),
    chassis.zoneUuid,
    MD5(CONCAT('default-pool-', chassis.zoneUuid)),
    COALESCE(chassis.name, CONCAT('BM1-', chassis.ipmiAddress)),
    NULL,   -- BM1 Chassis 无 managementIp（Instance 才有）
    NULL,   -- serialNumber 需硬件发现后回填
    CASE chassis.state
        WHEN 'Enabled' THEN 'Enabled'
        ELSE 'Disabled'
    END,
    CASE chassis.status
        WHEN 'Available' THEN 'Connected'
        WHEN 'Allocated' THEN 'Connected'
        WHEN 'HWInfoUnknown' THEN 'Connecting'
        WHEN 'PxeBooting' THEN 'Connecting'
        WHEN 'PxeBootFailed' THEN 'Disconnected'
        ELSE 'Disconnected'
    END,
    'IPMI',
    chassis.ipmiAddress,
    chassis.ipmiPort,
    chassis.ipmiUsername,
    chassis.ipmiPassword,
    chassis.createDate,
    NOW()
FROM BaremetalChassisVO chassis;

-- 3. 创建 PhysicalServerRoleVO
INSERT IGNORE INTO PhysicalServerRoleVO
  (uuid, serverUuid, roleType, roleUuid, clusterUuid,
   schedulingMode, roleStatus, createDate, lastOpDate)
SELECT
    MD5(CONCAT('bm1-role-', chassis.uuid)),
    MD5(CONCAT('bm1-chassis-', chassis.uuid)),
    'BAREMETAL_V1',
    chassis.uuid,
    chassis.clusterUuid,
    'INTERNAL_EXCLUSIVE',
    'Active',
    NOW(),
    NOW()
FROM BaremetalChassisVO chassis;

-- 4. 创建 PhysicalServerCapacityVO（从硬件发现数据填充）
INSERT IGNORE INTO PhysicalServerCapacityVO
  (uuid, totalPhysicalCpu, totalPhysicalMemory, totalDisk,
   cpuOverprovisioningRatio, memoryOverprovisioningRatio,
   totalCpu, totalMemory, availableCpu, availableMemory,
   availableDisk, capacityState)
SELECT
    MD5(CONCAT('bm1-chassis-', chassis.uuid)),
    COALESCE(hw_cpu.cpuCores, 0),
    COALESCE(hw_mem.totalMemory, 0),
    COALESCE(hw_disk.totalDisk, 0),
    1.0,    -- BM1 不超分
    1.0,    -- BM1 不超分
    COALESCE(hw_cpu.cpuCores, 0),
    COALESCE(hw_mem.totalMemory, 0),
    -- 如果 Chassis 已 Allocated，可用量为 0；否则为全量
    CASE chassis.status
        WHEN 'Allocated' THEN 0
        ELSE COALESCE(hw_cpu.cpuCores, 0)
    END,
    CASE chassis.status
        WHEN 'Allocated' THEN 0
        ELSE COALESCE(hw_mem.totalMemory, 0)
    END,
    CASE chassis.status
        WHEN 'Allocated' THEN 0
        ELSE COALESCE(hw_disk.totalDisk, 0)
    END,
    CASE
        WHEN hw_cpu.cpuCores IS NOT NULL THEN 'Ready'
        ELSE 'Initialized'
    END
FROM BaremetalChassisVO chassis
LEFT JOIN (
    SELECT chassisUuid,
           JSON_EXTRACT(content, '$.cpuCores') AS cpuCores
    FROM BaremetalHardwareInfoVO WHERE type = 'basic'
) hw_cpu ON chassis.uuid = hw_cpu.chassisUuid
LEFT JOIN (
    SELECT chassisUuid,
           JSON_EXTRACT(content, '$.totalMemory') AS totalMemory
    FROM BaremetalHardwareInfoVO WHERE type = 'basic'
) hw_mem ON chassis.uuid = hw_mem.chassisUuid
LEFT JOIN (
    SELECT chassisUuid,
           JSON_EXTRACT(content, '$.totalDisk') AS totalDisk
    FROM BaremetalHardwareInfoVO WHERE type = 'disk'
) hw_disk ON chassis.uuid = hw_disk.chassisUuid;

-- 5. 记录映射 SystemTag（用于追溯）
INSERT IGNORE INTO SystemTagVO
  (uuid, resourceUuid, resourceType, tag, createDate, lastOpDate)
SELECT
    MD5(CONCAT('bm1-tag-', chassis.uuid)),
    MD5(CONCAT('bm1-chassis-', chassis.uuid)),
    'PhysicalServerVO',
    CONCAT('originBm1ChassisUuid::', chassis.uuid),
    NOW(),
    NOW()
FROM BaremetalChassisVO chassis;
```

**迁移验证**：
```sql
-- 验证：BM1 Chassis 数量 == 对应 PhysicalServerVO 数量
SELECT
    (SELECT COUNT(*) FROM BaremetalChassisVO) AS chassis_count,
    (SELECT COUNT(*) FROM PhysicalServerRoleVO WHERE roleType = 'BAREMETAL_V1') AS role_count;
-- 期望：chassis_count == role_count
```

---

## 6. Open Questions 回答

### Q1: BM1 BaremetalPxeServerVO 的 DHCP 配置是否完全可以从现有数据推导出 dhcpRangeStartIp/EndIp？

**回答**：不能。

BM1 的 `BaremetalPxeServerVO` 不直接存储 DHCP IP 范围。DHCP 配置由 PxeServer 上运行的 dnsmasq 进程管理，配置文件位于 PxeServer 本地（通常为 `/etc/dnsmasq.d/` 或类似路径）。`BaremetalPxeServerVO` 只存储 PxeServer 的 SSH 凭据和 `dhcpInterface`。

**解决方案**：
1. **迁移时**：迁移脚本将 `dhcpRangeStartIp`/`dhcpRangeEndIp`/`dhcpRangeNetmask`/`dhcpRangeGateway` 留空，标记 `type = STANDALONE_PXE`
2. **运行时补充**：提供管理员 API 手动补全 DHCP 范围，或在 PxeServer 下次 Reconnect 时通过 SSH 读取 dnsmasq 配置自动回填
3. **兼容无影响**：BM1 装机流程继续使用 `BaremetalPxeServerVO`，DHCP 范围缺失不影响 BM1 功能。仅统一层的查询展示可能缺少此信息

### Q2: BM1 的 IPMI 凭据与 PhysicalServerVO 的 OOB 字段是否完全对应？有无 BM1 特有的 IPMI 参数？

**回答**：核心凭据完全对应，有 2 个 BM1 特有参数需额外处理。

**完全对应的字段**（见 2.5 节映射表）：
- ipmiAddress → oobAddress
- ipmiPort → oobPort
- ipmiUsername → oobUsername
- ipmiPassword → oobPassword

**BM1 特有的 IPMI 参数**（不映射到 PhysicalServerAO，保留在 BM1 层）：
1. **Boot Mode (Legacy/EFI)**：通过 SystemTag `baremetalChassisBootMode::Legacy` 或 `baremetalChassisBootMode::EFI` 标记。这是 IPMI `Set Boot Device` 命令的参数，不属于 OOB 凭据范畴。建议保留在 BM1 的 SystemTag 中，不映射到 PhysicalServerAO。如果未来统一层需要此信息，可在 `PhysicalServerHardwareInfoVO` 中增加 `bootMode` 字段（参考 REVIEW_baremetal_v1.md 建议）。
2. **IPMI Cipher Suite**：部分服务器需要指定 IPMI cipher suite（如 `-C 3`），当前通过 GlobalConfig 或 SystemTag 配置。保留在 BM1 层，不映射。

### Q3: 独占角色互斥如何保障？

**回答**：通过业务逻辑层检查，而非数据库约束。

`UNIQUE(serverUuid, roleType)` 约束只防止同一角色类型重复注册，不防止独占角色与共享角色共存（如同一台机器注册为 BM1 + KVM）。

**保障措施**：
1. 在 `afterCreateBaremetalChassis()` 中检查：如果 `PhysicalServerVO` 已有 `INTERNAL_SHARED` 类型的 Active Role（如 KVM），则抛出错误拒绝创建 BM1 角色
2. 反之亦然：KVM PostConnect 中如果发现已有 `INTERNAL_EXCLUSIVE` 角色，也拒绝
3. 例外：`EXTERNAL_READONLY` (Container) 可以与任何模式共存

```java
// 注意：此互斥检查已统一到 PhysicalServerManagerImpl.registerRole()，BM1 适配器不自行实现
// PhysicalServerManagerImpl.registerRole() 会检查独占/共享角色互斥
```

### Q4: 角色匹配应使用 (oobAddress, oobPort) 组合还是仅 oobAddress？

**回答**：使用 `oobAddress` 单字段匹配即可，辅以 `zoneUuid` 限定。

理由：
- 同一个 BMC IP 地址在同一个 Zone 内不会出现在两台不同的物理机上
- `ipmiPort` 几乎总是 623（IPMI 标准端口），不是区分标识
- 即使有非标准端口场景，同一 BMC IP 的不同端口也指向同一台物理机
- `zoneUuid` 约束已经提供了足够的隔离（不同 Zone 可能有 IP 重叠）

### Q5: PhysicalServerVO 删除是否会 cascade 删除 BaremetalChassisVO？

**回答**：不会。设计上已排除此风险。

`PhysicalServerRoleVO.roleUuid` 是**多态引用，不加 FK 约束**（架构文档第 2.3 节 D3 决策）。因此：
- 删除 `PhysicalServerVO` 会 CASCADE 删除 `PhysicalServerRoleVO`（通过 `serverUuid` FK）
- 但 `PhysicalServerRoleVO` 的 `roleUuid` 没有 FK 指向 `BaremetalChassisVO`
- 所以 `BaremetalChassisVO` 不受影响

反向：删除 `BaremetalChassisVO` 也不会删除 `PhysicalServerVO`——同步器的 `beforeDeleteBaremetalChassis()` 只将 RoleVO 标记为 Stale，不删除 PhysicalServerVO。

### Q6: 硬件发现数据如何同步到 PhysicalServerHardwareInfoVO？

**回答**：在硬件发现回调中增量同步。

BM1 的硬件发现通过 `POST /baremetal/chassis/sendhardwareinfo` 回调写入 `BaremetalHardwareInfoVO`（1:N，type + JSON content 格式）。同步到 `PhysicalServerHardwareInfoVO`（1:1 汇总格式）的逻辑：

```java
// 在 BaremetalChassisManagerImpl.handleSendHardwareInfo() 末尾追加
private void syncHardwareInfoToPhysicalServer(
        String chassisUuid, List<BaremetalHardwareInfoVO> hwInfos) {

    String serverUuid = Q.New(PhysicalServerRoleVO.class)
        .eq(PhysicalServerRoleVO_.roleUuid, chassisUuid)
        .select(PhysicalServerRoleVO_.serverUuid)
        .findValue();
    if (serverUuid == null) return;

    PhysicalServerHardwareInfoVO infoVO = dbf.findByUuid(
        serverUuid, PhysicalServerHardwareInfoVO.class);
    boolean isNew = (infoVO == null);
    if (isNew) {
        infoVO = new PhysicalServerHardwareInfoVO();
        infoVO.setUuid(serverUuid);
    }

    for (BaremetalHardwareInfoVO hw : hwInfos) {
        JSONObject content = JSON.parseObject(hw.getContent());
        switch (hw.getType()) {
            case "basic":
                infoVO.setCpuModel(content.getString("cpuModel"));
                infoVO.setCpuCores(content.getIntValue("cpuCores"));
                infoVO.setCpuSockets(content.getIntValue("cpuSockets"));
                infoVO.setTotalMemory(content.getLongValue("totalMemory"));
                // 回填 serialNumber 到 PhysicalServerVO
                String serial = content.getString("productSerial");
                if (serial != null && !serial.isEmpty()
                        && !"Not Specified".equals(serial)) {
                    UpdateQuery.New(PhysicalServerVO.class)
                        .eq(PhysicalServerVO_.uuid, serverUuid)
                        .set(PhysicalServerVO_.serialNumber, serial)
                        .update();
                }
                break;
            case "nic":
                infoVO.setNicCount(content.getIntValue("count"));
                break;
            case "disk":
                infoVO.setTotalDisk(content.getLongValue("totalDisk"));
                break;
            // GPU 等类型类似处理
        }
    }

    infoVO.setLastDiscoveryDate(new Timestamp(System.currentTimeMillis()));

    if (isNew) {
        dbf.persist(infoVO);
    } else {
        dbf.update(infoVO);
    }

    // 同步容量到 CapacityVO
    syncCapacityFromHardwareInfo(serverUuid, infoVO);
}
```

---

## 附录 A：新增文件清单

| 文件路径 | 用途 |
|---------|------|
| `header/.../baremetal/chassis/BaremetalChassisLifecycleExtensionPoint.java` | Chassis 生命周期扩展点定义 |
| `header/.../baremetal/pxeserver/BaremetalPxeServerLifecycleExtensionPoint.java` | PxeServer 生命周期扩展点定义 |
| `server/.../roleprovider/Bm1PhysicalServerRoleProvider.java` | BM1 RoleProvider SPI 实现 |
| `server/.../roleprovider/Bm1ChassisLifecycleSynchronizer.java` | Chassis → PhysicalServerVO 同步器 |
| `server/.../roleprovider/Bm1PxeServerLifecycleSynchronizer.java` | PxeServer → ProvisionNetworkVO 同步器 |
| `server/.../roleprovider/Bm1RoleInventory.java` | BM1 角色 Inventory 子类 |
| `conf/db/migration/V_bm1_migrate_physical_server.sql` | 存量数据迁移脚本 |

## 附录 B：修改文件清单（最小侵入）

| 文件路径 | 修改内容 | 影响行数 |
|---------|---------|---------|
| `BaremetalChassisManagerImpl.java` | createChassis 末尾追加 ExtensionPoint 回调 | +3 行 |
| `BaremetalChassisManagerImpl.java` | deleteChassis 前追加 ExtensionPoint 回调 | +3 行 |
| `BaremetalChassisManagerImpl.java` | updateChassis IPMI 变更时追加回调 | +5 行 |
| `BaremetalChassisManagerImpl.java` | handleSendHardwareInfo 末尾追加 serialNumber 回填 | +5 行 |
| `BaremetalPxeServerManagerImpl.java` | create/delete/attach/detach 追加回调 | +12 行 |
| `RoleMatchContext.java` | 新增 oobAddress 字段 | +5 行 |

**总侵入量**：约 33 行新增代码分布在 3 个现有文件中，不改任何已有方法签名。

## 附录 C：关键设计决策汇总

| # | 决策 | 理由 |
|---|------|------|
| B1 | ExtensionPoint 注入而非 EventFacade | 事务一致性可控；符合 BM1 模块已有的扩展模式 |
| B2 | PhysicalServerVO 删除不 cascade 到 ChassisVO | PhysicalServerVO 是派生数据，不反向控制主实体 |
| B3 | serialNumber 延迟回填 | Chassis 创建时 serialNumber 不可用，需硬件发现后回填 |
| B4 | oobAddress 优先于 managementIp 匹配 | BM1 Chassis 以 IPMI 地址为核心标识，managementIp 属于 Instance |
| B5 | DHCP 范围迁移时留空，手动补充 | BM1 PxeServer 不直接存储 DHCP 范围，自动推导不可靠 |
| B6 | CapacityFilterFlow 跳过 Initialized 状态的容量检查 | 独占角色在硬件发现前仍需可分配 |
| B7 | Boot Mode / Cipher Suite 保留在 BM1 层 | 非 OOB 凭据，是 IPMI 操作参数，不属于统一层关注范畴 |
| B8 | BM1 Instance 状态机不映射到 PhysicalServer | Instance 是消费层概念，统一层只管物理服务器 |
