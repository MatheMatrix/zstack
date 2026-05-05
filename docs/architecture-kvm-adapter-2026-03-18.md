# KVM 角色适配器详细设计文档

**版本**: v1.0
**日期**: 2026-03-18
**作者**: KVM Host Domain Expert
**输入**: architecture-unified-hardware-2026-03-18.md (第 3 章 SPI、第 9 章 KVM 分工) + PRD FR-023/FR-027/FR-028 + ANALYSIS_kvm_host_module.md

---

## 1. KvmPhysicalServerRoleProvider 实现设计

### 1.1 类定义

```java
package org.zstack.kvm.server;

import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.Q;
import org.zstack.core.db.SQL;
import org.zstack.header.allocator.HostCapacityVO;
import org.zstack.header.allocator.HostCapacityVO_;
import org.zstack.header.host.HostInventory;
import org.zstack.header.host.HostVO;
import org.zstack.header.host.HostVO_;
import org.zstack.header.server.*;
import org.zstack.header.server.enums.SchedulingMode;
import org.zstack.header.server.enums.ServerRoleType;
import org.zstack.kvm.KVMHostInventory;

/**
 * KVM Host 角色适配器。
 *
 * 位置：plugin/kvm/ 模块内，通过 Spring Bean 注册。
 * 不修改任何现有 KVMHost/KVMHostFactory 的方法签名。
 */
@Component
public class KvmPhysicalServerRoleProvider implements PhysicalServerRoleProvider {

    @Autowired
    private DatabaseFacade dbf;

    // ...
}
```

### 1.2 各 SPI 方法实现逻辑

#### 1.2.1 getRoleType()

```java
@Override
public ServerRoleType getRoleType() {
    return ServerRoleType.KVM_HOST;
}
```

**说明**: 常量返回，标识本 Provider 管理 KVM_HOST 类型角色。全局唯一，由 `pluginRgty.getExtensionList()` 发现后按 roleType 索引。

#### 1.2.2 getSchedulingMode()

```java
@Override
public SchedulingMode getSchedulingMode() {
    return SchedulingMode.INTERNAL_SHARED;
}
```

**说明**: KVM Host 支持 VM 共享调度和超分。分配引擎对 INTERNAL_SHARED 模式按需扣减 CPU/Memory，不做整机清零。

#### 1.2.3 getCapacityConsumption(serverUuid)

```java
@Override
public CapacityUsage getCapacityConsumption(String serverUuid) {
    // 1. 通过 PhysicalServerRoleVO 找到 roleUuid（即 HostVO.uuid）
    String hostUuid = Q.New(PhysicalServerRoleVO.class)
        .eq(PhysicalServerRoleVO_.serverUuid, serverUuid)
        .eq(PhysicalServerRoleVO_.roleType, ServerRoleType.KVM_HOST)
        .select(PhysicalServerRoleVO_.roleUuid)
        .findValue();

    if (hostUuid == null) {
        return new CapacityUsage(); // 无角色关联，返回零消耗
    }

    // 2. 直接从 PhysicalServerCapacityVO 读取已用容量
    //    （PhysicalServerCapacityVO 是唯一容量真表，不再从 HostCapacityVO 读取）
    PhysicalServerCapacityVO cap = dbf.findByUuid(serverUuid, PhysicalServerCapacityVO.class);
    if (cap == null) {
        return new CapacityUsage();
    }

    CapacityUsage usage = new CapacityUsage();
    // usedCpu = totalCpu(含超分) - availableCpu
    usage.setUsedCpu(cap.getTotalCpu() - cap.getAvailableCpu());
    // usedMemory = totalMemory(含超分) - availableMemory
    usage.setUsedMemory(cap.getTotalMemory() - cap.getAvailableMemory());
    // KVM 不直接管理磁盘容量（由 PrimaryStorage 管理），usedDisk = 0
    usage.setUsedDisk(0);
    return usage;
}
```

**设计要点**:
- 只读方法，无副作用，仅在容量重计算 (`RecalculatePhysicalServerCapacityMsg`) 时被调用
- 直接从 PhysicalServerCapacityVO 读取（它是容量的 source of truth），不再从 HostCapacityVO 读取
- usedCpu/usedMemory 取的是逻辑值（含超分比）
- KVM 场景下磁盘由 PrimaryStorage 独立管理，不纳入 PhysicalServer 容量账本

#### 1.2.4 onPhysicalServerCreated(serverUuid)

```java
@Override
public void onPhysicalServerCreated(String serverUuid) {
    // KVM 场景下，PhysicalServerVO 由 PostConnect 钩子创建，
    // 此回调在 PhysicalServerVO 被 API 手动创建时触发。
    // KVM Host 的 HostVO 是通过 APIAddKVMHostMsg 独立创建的，
    // 不需要在 PhysicalServer 创建时做额外工作。
    // 预留钩子，未来可用于"先注册物理服务器再部署 KVM"场景。
    logger.debug(String.format("KVM RoleProvider: PhysicalServer[uuid:%s] created, no action needed", serverUuid));
}
```

#### 1.2.5 onPhysicalServerDeleted(serverUuid)

```java
@Override
public void onPhysicalServerDeleted(String serverUuid) {
    // 1. 查找关联的 KVM_HOST 角色记录
    PhysicalServerRoleVO roleVO = Q.New(PhysicalServerRoleVO.class)
        .eq(PhysicalServerRoleVO_.serverUuid, serverUuid)
        .eq(PhysicalServerRoleVO_.roleType, ServerRoleType.KVM_HOST)
        .find();

    if (roleVO == null) {
        return;
    }

    // 2. 标记角色状态为 Stale（不删除 HostVO，因为 PhysicalServer 删除不意味着 KVM Host 删除）
    roleVO.setRoleStatus("Stale");
    dbf.update(roleVO);

    logger.info(String.format("KVM RoleProvider: marked RoleVO[uuid:%s] as Stale for deleted PhysicalServer[uuid:%s]",
        roleVO.getUuid(), serverUuid));
}
```

#### 1.2.6 getInventory(roleUuid)

```java
@Override
public RoleInventory getInventory(String roleUuid) {
    HostVO host = dbf.findByUuid(roleUuid, HostVO.class);
    if (host == null) {
        return null;
    }

    // 返回 KVM 特化的 RoleInventory 子类
    KvmRoleInventory inv = new KvmRoleInventory();
    inv.setRoleUuid(roleUuid);
    inv.setRoleType(ServerRoleType.KVM_HOST.toString());
    inv.setClusterUuid(host.getClusterUuid());
    inv.setStatus(host.getStatus().toString());

    // KVM 特有字段
    inv.setManagementIp(host.getManagementIp());
    inv.setHypervisorType(host.getHypervisorType());
    inv.setArchitecture(host.getArchitecture());
    inv.setHostState(host.getState().toString());
    inv.setHostStatus(host.getStatus().toString());

    return inv;
}
```

**KvmRoleInventory 子类**:

```java
package org.zstack.kvm.server;

import org.zstack.header.server.RoleInventory;

/**
 * KVM 角色 Inventory 扩展。
 * 用于单独查询 KVM 角色详情，QueryPhysicalServerMsg 只返回 ref 引用。
 */
public class KvmRoleInventory extends RoleInventory {
    private String managementIp;
    private String hypervisorType;
    private String architecture;
    private String hostState;
    private String hostStatus;

    // getter/setter 省略
}
```

#### 1.2.7 matchExistingServer(context)

```java
@Override
public String matchExistingServer(RoleMatchContext context) {
    // 匹配逻辑委托给统一的匹配算法
    // KVM 不覆盖默认逻辑，直接使用基础匹配策略：
    //   1. serialNumber 精确匹配（zoneUuid + serialNumber）
    //   2. managementIp + zoneUuid 降级匹配
    // 默认实现在 PhysicalServerManagerImpl 中
    return null; // 返回 null 表示使用默认匹配逻辑
}
```

**说明**: KVM 的匹配逻辑不需要特殊处理，使用 `PhysicalServerManagerImpl` 提供的默认匹配算法即可。`matchExistingServer()` 返回 null 时，调用方降级到通用匹配逻辑。

---

## 2. PostConnect 钩子设计

### 2.1 注入位置

在 `HostBase.java` 的连接流程中，`call-post-connect-extensions` Flow 会遍历所有 `PostHostConnectExtensionPoint` 实现并创建 Flow 链。新增的 `KvmPhysicalServerPostConnectExtension` 实现此接口，注入到 PostConnect 链中。

**注入点代码路径** (`compute/src/main/java/org/zstack/compute/host/HostBase.java:1361-1391`):

```
HostBase.connectHost()
  └── Flow: "call-post-connect-extensions"
       └── for (PostHostConnectExtensionPoint p : pluginRgty.getExtensionList(...))
            └── p.createPostHostConnectFlow(inv)  ← 在此注入
```

**关键约束**: PostConnect 流程中的 Flow 如果失败，会导致整个连接链失败（`FlowErrorHandler` 会将 error 传递给上层 trigger）。但 KVM Host 角色注册/关联是"增值"操作，**不应阻塞 KVM Host 的正常连接**。因此 KvmPhysicalServerPostConnectExtension 必须内部捕获所有异常并仅 log warning。

### 2.2 实现类

```java
package org.zstack.kvm.server;

import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.compute.host.PostHostConnectExtensionPoint;
import org.zstack.core.Platform;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.cloudbus.CloudBusCallBack;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.Q;
import org.zstack.core.db.SQL;
import org.zstack.header.core.workflow.Flow;
import org.zstack.header.core.workflow.FlowTrigger;
import org.zstack.header.core.workflow.NoRollbackFlow;
import org.zstack.header.host.HostConstant;
import org.zstack.header.host.HostInventory;
import org.zstack.header.host.HostVO;
import org.zstack.header.message.MessageReply;
import org.zstack.header.server.*;
import org.zstack.header.server.enums.*;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.sql.Timestamp;
import java.util.Map;

/**
 * KVM Host PostConnect 钩子。
 *
 * 在 KVM Host 连接成功后（新增或重连），自动关联到 PhysicalServerVO。
 * 实现 PostHostConnectExtensionPoint，由 HostBase 的连接 FlowChain 调用。
 *
 * 设计原则：
 * 1. 失败不阻塞 KVM Host 正常连接（内部 try-catch）
 * 2. 幂等——重连时不重复创建 PhysicalServerVO
 * 3. 不修改任何现有 KVMHost/KVMHostFactory 代码
 */
public class KvmPhysicalServerPostConnectExtension implements PostHostConnectExtensionPoint {

    private static final CLogger logger = Utils.getLogger(KvmPhysicalServerPostConnectExtension.class);

    @Autowired
    private DatabaseFacade dbf;
    @Autowired
    private CloudBus bus;

    @Override
    public Flow createPostHostConnectFlow(HostInventory host) {
        return new NoRollbackFlow() {
            String __name__ = "kvm-register-physical-server-role";

            @Override
            public void run(FlowTrigger trigger, Map data) {
                try {
                    registerOrUpdatePhysicalServer(host);
                } catch (Exception e) {
                    // 关键：不阻塞 KVM Host 连接
                    logger.warn(String.format(
                        "failed to register/update PhysicalServerRoleVO for KVM host[uuid:%s, ip:%s], " +
                        "this does NOT affect KVM host connectivity: %s",
                        host.getUuid(), host.getManagementIp(), e.getMessage()), e);
                }
                trigger.next();
            }
        };
    }

    // 详细逻辑见 2.3 节
    private void registerOrUpdatePhysicalServer(HostInventory host) { ... }
}
```

### 2.3 角色自动关联逻辑

```java
private void registerOrUpdatePhysicalServer(HostInventory host) {
    String hostUuid = host.getUuid();
    String managementIp = host.getManagementIp();
    String zoneUuid = host.getZoneUuid();
    String clusterUuid = host.getClusterUuid();

    // Step 1: 检查是否已有角色映射（重连场景幂等）
    PhysicalServerRoleVO existingRole = Q.New(PhysicalServerRoleVO.class)
        .eq(PhysicalServerRoleVO_.roleType, ServerRoleType.KVM_HOST)
        .eq(PhysicalServerRoleVO_.roleUuid, hostUuid)
        .find();

    if (existingRole != null) {
        // 重连场景：更新角色状态为 Active，同步 clusterUuid
        if (!"Active".equals(existingRole.getRoleStatus())
                || !clusterUuid.equals(existingRole.getClusterUuid())) {
            existingRole.setRoleStatus("Active");
            existingRole.setClusterUuid(clusterUuid);
            dbf.update(existingRole);
        }
        // 同步容量
        syncCapacity(existingRole.getServerUuid(), hostUuid);
        logger.info(String.format(
            "KVM PostConnect: updated existing RoleVO[uuid:%s] for host[uuid:%s]",
            existingRole.getUuid(), hostUuid));
        return;
    }

    // Step 2: 获取 serialNumber
    String serialNumber = obtainSerialNumber(host);

    // Step 3: 匹配已有 PhysicalServerVO
    String serverUuid = matchPhysicalServer(serialNumber, managementIp, zoneUuid);

    // Step 4: 无匹配则新建 PhysicalServerVO
    if (serverUuid == null) {
        serverUuid = createPhysicalServer(host, serialNumber);
    } else {
        // 匹配成功，回填 serialNumber（降级匹配场景）
        backfillSerialNumber(serverUuid, serialNumber);
    }

    // Step 5: 创建 PhysicalServerRoleVO
    createRoleVO(serverUuid, hostUuid, clusterUuid);

    // Step 6: 同步容量到 PhysicalServerCapacityVO
    syncCapacity(serverUuid, hostUuid);
}
```

### 2.4 serialNumber 获取方式

```java
/**
 * 获取物理服务器序列号。
 *
 * 优先级：
 * 1. HostIpmiVO 中存储的 serialNumber（已有 IPMI 信息的 Host）
 * 2. KVM agent 在 /host/fact 或 /host/connect 时已采集的信息
 *    （通过 HostVO 扩展字段或 SystemTag 读取）
 * 3. 后续通过 agent 调用 cat /sys/class/dmi/id/product_serial 获取
 *
 * PostConnect 时 agent 已连通，可直接发 HTTP 请求获取。
 * 但为避免在 PostConnect 流程中增加额外的 agent 调用延迟，
 * 推荐方案：在 KVM agent 的 /host/connect 响应中增加 serialNumber 字段，
 * 由 KVMHost.connectHook() 解析后存入 SystemTag，PostConnect 钩子直接读取。
 */
private String obtainSerialNumber(HostInventory host) {
    // 方案 A：从 HostIpmiVO 读取（如果已有 IPMI 信息）
    HostIpmiVO ipmi = Q.New(HostIpmiVO.class)
        .eq(HostIpmiVO_.uuid, host.getUuid())
        .find();
    if (ipmi != null && isValidSerialNumber(ipmi.getSerialNumber())) {
        return ipmi.getSerialNumber();
    }

    // 方案 B：从 SystemTag 读取（agent connect 时写入）
    String tag = HostSystemTags.SYSTEM_SERIAL_NUMBER.getTokenByResourceUuid(
        host.getUuid(), HostSystemTags.SYSTEM_SERIAL_NUMBER_TOKEN);
    if (isValidSerialNumber(tag)) {
        return tag;
    }

    // 方案 C：PostConnect 阶段发 agent 调用（同步获取）
    // 仅在前两种方式均无数据时触发
    return fetchSerialNumberFromAgent(host);
}

/**
 * 通过 KVM agent 读取 /sys/class/dmi/id/product_serial。
 *
 * 推荐的 agent 端实现：
 *   with open('/sys/class/dmi/id/product_serial') as f:
 *       serial = f.read().strip()
 *
 * 使用现有 collect-kvm-host-facts 采集的 SYSTEM_SERIAL_NUMBER SystemTag。
 * 不新增 agent 端点，复用 KVM Host 连接时已采集的 /sys/class/dmi/id/product_serial 数据。
 */
private String fetchSerialNumberFromAgent(HostInventory host) {
    // 从 HostSystemTags.SYSTEM_SERIAL_NUMBER 读取（KVM connect 时已采集并写入 SystemTag）
    // 返回 serialNumber
    // ...
    return null; // 获取失败时返回 null，降级到 managementIp 匹配
}

private boolean isValidSerialNumber(String sn) {
    if (sn == null || sn.trim().isEmpty()) return false;
    // 过滤已知的无效值
    String normalized = sn.trim().toLowerCase();
    return !normalized.equals("not specified")
        && !normalized.equals("none")
        && !normalized.equals("default string")
        && !normalized.equals("to be filled by o.e.m.")
        && !normalized.equals("0");
}
```

### 2.5 PhysicalServerVO 创建/更新逻辑

```java
private String matchPhysicalServer(String serialNumber, String managementIp, String zoneUuid) {
    // 优先级 1：serialNumber 精确匹配
    if (isValidSerialNumber(serialNumber)) {
        String uuid = Q.New(PhysicalServerVO.class)
            .eq(PhysicalServerVO_.serialNumber, serialNumber)
            .eq(PhysicalServerVO_.zoneUuid, zoneUuid)
            .select(PhysicalServerVO_.uuid)
            .findValue();
        if (uuid != null) {
            return uuid;
        }
    }

    // 优先级 2：managementIp + zoneUuid 降级匹配
    return Q.New(PhysicalServerVO.class)
        .eq(PhysicalServerVO_.managementIp, managementIp)
        .eq(PhysicalServerVO_.zoneUuid, zoneUuid)
        .select(PhysicalServerVO_.uuid)
        .findValue();
}

private String createPhysicalServer(HostInventory host, String serialNumber) {
    PhysicalServerVO server = new PhysicalServerVO();
    server.setUuid(Platform.getUuid());
    server.setZoneUuid(host.getZoneUuid());
    // poolUuid: 从 Cluster → ServerPool 关联表查询，无关联时使用 Zone 默认 Pool
    server.setPoolUuid(resolvePoolUuid(host.getClusterUuid(), host.getZoneUuid()));
    server.setName(host.getName());
    server.setManagementIp(host.getManagementIp());
    server.setArchitecture(host.getArchitecture());
    server.setSerialNumber(serialNumber);
    server.setState(PhysicalServerState.Enabled);
    server.setStatus(PhysicalServerStatus.Connected);
    server.setPowerStatus(PhysicalServerPowerStatus.PowerOn); // agent 在线意味着已开机
    server.setCreateDate(new Timestamp(System.currentTimeMillis()));
    server.setLastOpDate(new Timestamp(System.currentTimeMillis()));

    // 同步 IPMI/OOB 信息（如果 HostIpmiVO 存在）
    syncOobInfo(server, host.getUuid());

    dbf.persist(server);

    // 创建 PhysicalServerCapacityVO（初始化）
    createCapacityVO(server.getUuid(), host.getUuid());

    logger.info(String.format(
        "KVM PostConnect: created PhysicalServerVO[uuid:%s] for host[uuid:%s, ip:%s]",
        server.getUuid(), host.getUuid(), host.getManagementIp()));

    return server.getUuid();
}

private void createRoleVO(String serverUuid, String hostUuid, String clusterUuid) {
    PhysicalServerRoleVO role = new PhysicalServerRoleVO();
    role.setUuid(Platform.getUuid());
    role.setServerUuid(serverUuid);
    role.setRoleType(ServerRoleType.KVM_HOST);
    role.setRoleUuid(hostUuid);
    role.setClusterUuid(clusterUuid);
    role.setSchedulingMode(SchedulingMode.INTERNAL_SHARED);
    role.setRoleStatus("Active");
    role.setCreateDate(new Timestamp(System.currentTimeMillis()));
    role.setLastOpDate(new Timestamp(System.currentTimeMillis()));

    dbf.persist(role);
}

private String resolvePoolUuid(String clusterUuid, String zoneUuid) {
    // 1. 从 ClusterServerPoolRefVO 查找 Cluster 关联的 ServerPool
    String poolUuid = Q.New(ClusterServerPoolRefVO.class)
        .eq(ClusterServerPoolRefVO_.clusterUuid, clusterUuid)
        .select(ClusterServerPoolRefVO_.poolUuid)
        .findValue();

    if (poolUuid != null) {
        return poolUuid;
    }

    // 2. 降级：查找或创建 Zone 默认 Pool
    poolUuid = Q.New(ServerPoolVO.class)
        .eq(ServerPoolVO_.zoneUuid, zoneUuid)
        .eq(ServerPoolVO_.name, "default-pool")
        .select(ServerPoolVO_.uuid)
        .findValue();

    if (poolUuid != null) {
        return poolUuid;
    }

    // 3. 创建默认 Pool
    ServerPoolVO pool = new ServerPoolVO();
    pool.setUuid(Platform.getUuid());
    pool.setZoneUuid(zoneUuid);
    pool.setName("default-pool");
    pool.setDescription("Auto-created default pool for zone");
    pool.setState(PhysicalServerState.Enabled);
    pool.setCreateDate(new Timestamp(System.currentTimeMillis()));
    pool.setLastOpDate(new Timestamp(System.currentTimeMillis()));
    dbf.persist(pool);

    return pool.getUuid();
}
```

### 2.6 失败处理策略

| 失败场景 | 处理方式 | 对 KVM Host 的影响 |
|---------|---------|------------------|
| serialNumber 获取失败 | 降级到 managementIp 匹配 | 无影响，正常连接 |
| PhysicalServerVO 创建失败（如 UNIQUE 冲突） | 捕获异常，log warning，跳过 | 无影响，正常连接 |
| PhysicalServerRoleVO 创建失败 | 捕获异常，log warning，跳过 | 无影响，正常连接 |
| PhysicalServerCapacityVO 同步失败 | 捕获异常，log warning，跳过 | 无影响，正常连接 |
| 数据库不可达 | 上层事务已在进行中，此处不会单独失败 | 由上层连接流程统一处理 |

**核心原则**: `KvmPhysicalServerPostConnectExtension.createPostHostConnectFlow()` 返回的 Flow 内部 **必须 try-catch 所有异常并调用 trigger.next()**，绝不调用 `trigger.fail()`。这保证了统一硬件管理模块的任何故障都不会影响 KVM Host 的正常连接和业务运行。

### 2.7 KVM Host 删除时的钩子

通过实现 `HostDeleteExtensionPoint` 处理 KVM Host 删除场景：

```java
package org.zstack.kvm.server;

import org.zstack.header.host.HostDeleteExtensionPoint;
import org.zstack.header.host.HostException;
import org.zstack.header.host.HostInventory;

/**
 * KVM Host 删除时更新 PhysicalServerRoleVO 状态。
 *
 * 不阻塞删除操作（preDeleteHost 不抛异常）。
 * 在 afterDeleteHost 中将 RoleVO.roleStatus 标记为 Stale。
 */
public class KvmPhysicalServerDeleteExtension implements HostDeleteExtensionPoint {

    @Autowired
    private DatabaseFacade dbf;

    @Override
    public void preDeleteHost(HostInventory inventory) throws HostException {
        // 不阻塞删除
    }

    @Override
    public void beforeDeleteHost(HostInventory inventory) {
        // 无操作
    }

    @Override
    public void afterDeleteHost(HostInventory inventory) {
        try {
            SQL.New(PhysicalServerRoleVO.class)
                .eq(PhysicalServerRoleVO_.roleType, ServerRoleType.KVM_HOST)
                .eq(PhysicalServerRoleVO_.roleUuid, inventory.getUuid())
                .set(PhysicalServerRoleVO_.roleStatus, "Stale")
                .update();

            logger.info(String.format(
                "KVM DeleteHook: marked RoleVO as Stale for deleted host[uuid:%s]",
                inventory.getUuid()));

            // 检查是否为该 PhysicalServer 的最后一个角色
            // 如果是，更新 PhysicalServerVO.status = Disconnected
            checkAndUpdateServerStatus(inventory.getUuid());
        } catch (Exception e) {
            logger.warn(String.format(
                "failed to update RoleVO for deleted host[uuid:%s]: %s",
                inventory.getUuid(), e.getMessage()), e);
        }
    }

    private void checkAndUpdateServerStatus(String hostUuid) {
        PhysicalServerRoleVO role = Q.New(PhysicalServerRoleVO.class)
            .eq(PhysicalServerRoleVO_.roleUuid, hostUuid)
            .eq(PhysicalServerRoleVO_.roleType, ServerRoleType.KVM_HOST)
            .find();

        if (role == null) return;

        long activeRoleCount = Q.New(PhysicalServerRoleVO.class)
            .eq(PhysicalServerRoleVO_.serverUuid, role.getServerUuid())
            .eq(PhysicalServerRoleVO_.roleStatus, "Active")
            .count();

        if (activeRoleCount == 0) {
            SQL.New(PhysicalServerVO.class)
                .eq(PhysicalServerVO_.uuid, role.getServerUuid())
                .set(PhysicalServerVO_.status, PhysicalServerStatus.Disconnected)
                .update();
        }
    }
}
```

---

## 3. 容量映射

### 3.1 HostCapacityVO 与 PhysicalServerCapacityVO 字段映射

| HostCapacityVO 字段 | PhysicalServerCapacityVO 字段 | 映射说明 |
|-------------------|---------------------------|---------|
| `totalCpu` | `totalCpu` | 含超分比的逻辑 CPU 总量。同步时直接复制 |
| `availableCpu` | `availableCpu` | 逻辑可用 CPU。同步时直接复制 |
| `cpuNum` | `cpuNum` | CPU 线程数（列名一致，VIEW 直接透传） |
| `cpuSockets` | `cpuSockets` | CPU 插槽数 |
| `cpuCoreNum` | `cpuCoreNum` | CPU 核心数（列名一致，VIEW 直接透传） |
| `totalMemory` | `totalMemory` | 含超分比的逻辑内存总量 |
| `availableMemory` | `availableMemory` | 逻辑可用内存 |
| `totalPhysicalMemory` | `totalPhysicalMemory` | 物理内存总量 |
| `availablePhysicalMemory` | `availablePhysicalMemory` | 物理可用内存（列名一致，VIEW 直接透传） |
| — | `totalPhysicalCpu` | 物理 CPU 总量。从 `totalCpu / cpuOverprovisioningRatio` 反算 |
| — | `cpuOverprovisioningRatio` | 从 HostCpuOverProvisioningManager 读取 |
| — | `memoryOverprovisioningRatio` | 从 HostCapacityOverProvisioningManager 读取 |
| — | `totalDisk` / `availableDisk` | KVM 不管理磁盘容量，设为 0 |
| — | `reservedCpu` / `reservedMemory` | 从 HostCapacityReserveManager 读取 |
| — | `capacityState` | 同步完成后设为 "Ready" |

### 3.2 容量同步实现

```java
private void syncCapacity(String serverUuid, String hostUuid) {
    HostCapacityVO hostCap = dbf.findByUuid(hostUuid, HostCapacityVO.class);
    if (hostCap == null) {
        return;
    }

    // 获取超分比
    double cpuRatio = hostCpuOverProvisioningManager.getRatio(hostUuid);
    double memRatio = hostCapacityOverProvisioningManager.getRatio(hostUuid);

    PhysicalServerCapacityVO serverCap = dbf.findByUuid(serverUuid, PhysicalServerCapacityVO.class);
    boolean isNew = (serverCap == null);
    if (isNew) {
        serverCap = new PhysicalServerCapacityVO();
        serverCap.setUuid(serverUuid);
    }

    // 物理值
    long physicalCpu = (cpuRatio > 0) ? Math.round(hostCap.getTotalCpu() / cpuRatio) : hostCap.getTotalCpu();
    serverCap.setTotalPhysicalCpu(physicalCpu);
    serverCap.setTotalPhysicalMemory(hostCap.getTotalPhysicalMemory());
    serverCap.setCpuSockets(hostCap.getCpuSockets());
    serverCap.setCpuCoreNum(hostCap.getCpuCoreNum());
    serverCap.setCpuNum(hostCap.getCpuNum());

    // 超分比
    serverCap.setCpuOverprovisioningRatio(cpuRatio);
    serverCap.setMemoryOverprovisioningRatio(memRatio);

    // 逻辑值（含超分）
    serverCap.setTotalCpu(hostCap.getTotalCpu());
    serverCap.setTotalMemory(hostCap.getTotalMemory());
    serverCap.setAvailableCpu(hostCap.getAvailableCpu());
    serverCap.setAvailableMemory(hostCap.getAvailableMemory());
    serverCap.setAvailablePhysicalMemory(hostCap.getAvailablePhysicalMemory());

    // KVM 不管理磁盘
    serverCap.setTotalDisk(0);
    serverCap.setAvailableDisk(0);

    // 预留值（后续通过 ServerReservedCapacityExtensionPoint 汇总）
    serverCap.setReservedCpu(0);
    serverCap.setReservedMemory(0);

    serverCap.setCapacityState(CapacityState.Ready);

    if (isNew) {
        dbf.persist(serverCap);
    } else {
        dbf.update(serverCap);
    }
}
```

### 3.3 超分比同步

KVM Host 现有的超分比管理通过两个独立的 Manager 实现：
- `HostCpuOverProvisioningManager`: CPU 超分比（per-host + 全局）
- `HostCapacityOverProvisioningManager`: Memory 超分比（per-host + 全局）

**同步策略**:

1. **PostConnect 时**：读取当前 Host 的超分比，写入 `PhysicalServerCapacityVO.cpuOverprovisioningRatio` / `memoryOverprovisioningRatio`
2. **超分比变更时**：通过 GlobalConfig 变更监听器触发所有关联 PhysicalServer 的容量重计算。不需要新增监听器——利用现有的 `RecalculateHostCapacityMsg` 触发后，在回调中同步到 PhysicalServerCapacityVO

### 3.4 容量变化时的更新路径（6 个写入路径改造方案）

**设计决策**：PhysicalServerCapacityVO 是容量的唯一真表（source of truth），HostCapacityVO 降级为 MySQL VIEW。6 个写入点全部改为直接写 PhysicalServerCapacityVO（不通过 VIEW 写），47 个读取方零改动（通过 VIEW 透明读取）。

```
写入路径                                  改造方案
─────────                                ──────────
1. PostConnect (KVMHost.connectHook)  →  初始化 PhysicalServerCapacityVO（见 2.3 节 Step 6）
                                         直接写 PhysicalServerCapacityVO，不通过 VIEW

2. VM 创建 (ReserveHostCapacityMsg)   →  HostCapacityUpdater（包装器，59 个调用方零改动）
                                         → 内部通过 RoleVO 查找 serverUuid
                                         → 委托 PhysicalServerCapacityUpdater.decreaseCapacity()

3. VM 销毁 (ReturnHostCapacityMsg)    →  HostCapacityUpdater（包装器）
                                         → 委托 PhysicalServerCapacityUpdater.increaseCapacity()

4. VM 迁移                           →  HostCapacityUpdater.decrease(src) + increase(dst)
                                         → 各自委托 PhysicalServerCapacityUpdater

5. RecalculateHostCapacityMsg         →  触发 PhysicalServerCapacityUpdater.recalculateCapacity()
                                         → 直接操作 PhysicalServerCapacityVO

6. 超分比修改                         →  HostCpuOverProvisioningManagerImpl 裸 JPQL 删除！
   (HostCpuOverProvisioningManagerImpl)    旧代码中 3 处裸 JPQL UPDATE 全部删掉：
                                           - 第 70 行: update HostCapacityVO ... cpuNum * :ratio
                                           - 第 75 行: update HostCapacityVO ... not in (:uuids)
                                           - 第 96 行: update HostCapacityVO ... uuid = :huuid
                                         替代方案：触发 RecalculatePhysicalServerCapacityMsg
                                           → 批量/单台重计算，不直接裸写 SQL
```

**47 个读取方零改动**：所有通过 `Q.New(HostCapacityVO.class)` 或 JPQL `SELECT` 读取容量的代码，通过 HostCapacityVO VIEW 透明读取 PhysicalServerCapacityVO 的数据，无需任何改动。

**为什么不需要 CanonicalEvent listener 和定时对账**：
- HostCapacityUpdater 包装器直接写入 PhysicalServerCapacityVO，数据天然一致
- HostCapacityVO 是 VIEW（`SELECT ... FROM PhysicalServerCapacityVO JOIN PhysicalServerRoleVO`），查询时实时投影，无延迟
- 消除了旧设计中 5 分钟数据延迟窗口和事件丢失风险

---

## 4. CompatibilityBridge 细节（KVM 视角）— 两阶段薄适配

### 4.1 两阶段薄适配概述

CompatibilityBridge 采用两阶段薄适配模式，ServerAllocatorChain（阶段1）只做通用过滤，KVM 特有的所有 Flow 在阶段2由现有 HostAllocatorChain 正常执行，无需改动。

```
AllocateHostMsg
  → CompatibilityBridge 拦截（在 HostAllocatorManagerImpl.doHandleAllocateHost() 中）
  → 阶段1: ServerAllocatorChain（7 个通用 Flow：Zone/Cluster/Pool/RoleType/State/Avoid/Capacity）
    → 输出候选 PhysicalServer UUID 列表
  → 映射: PhysicalServerVO UUID → PhysicalServerRoleVO → HostVO UUID 集合
  → 注入: HostAllocatorSpec.candidateHostUuids = HostVO UUID 集合
  → 阶段2: 现有 HostAllocatorChain 在预筛选集合上正常执行
    → 所有 KVM Flow（L2/PS/BS/Tag/ResourceBinding 等）在小候选集上跑
    → HostSortorChain + reserveCapacity（锁机制不变）
```

**关键设计点**：
1. **不需要 originalMessage 透传** — ServerAllocatorChain 不读 AllocateHostMsg 的任何 KVM 特有字段（l3NetworkUuids、requiredPrimaryStorageUuids 等）
2. **不需要 ExtensionFilterFlow 桥接** — 没有 KvmL2NetworkServerFilter 等桥接扩展点
3. **现有 KVM Flow 全部保留** — 阶段2中 HostAllocatorChain 的 16 个 Flow 全部不改动
4. **候选集预缩小** — 阶段1输出的候选集通常从数百台缩小到几十台，阶段2更高效

### 4.2 KVM 特有 Flow 在阶段2的执行

以下 KVM 特有 Flow 在阶段2的现有 HostAllocatorChain 中正常执行，**无需任何改动**：

| # | KVM Flow | 阶段2行为 |
|---|----------|----------|
| 1 | `AttachedL2NetworkAllocatorFlow` | 在预筛选的小候选集上执行 L3→L2→Cluster 过滤 |
| 2 | `AttachedPrimaryStorageAllocatorFlow` | 在预筛选集上检查主存储挂载 |
| 3 | `AttachedVolumePrimaryStorageAllocatorFlow` | 在预筛选集上检查卷的主存储 |
| 4 | `HostPrimaryStorageAllocatorFlow` | 在预筛选集上检查主存储可达性 |
| 5 | `BackupStorageSelectPrimaryStorageAllocatorFlow` | 在预筛选集上按备份存储选主存储 |
| 6 | `ImageBackupStorageAllocatorFlow` | 在预筛选集上检查镜像仓库 |
| 7 | `TagAllocatorFlow` | 在预筛选集上检查系统标签 |
| 8 | `ResourceBindingAllocatorFlow` | 在预筛选集上检查资源绑定 |
| 9 | `QuotaAllocatorFlow` | 在预筛选集上检查配额 |
| 10 | `HostOsVersionAllocatorFlow` | 在预筛选集上检查 OS 版本（迁移场景） |
| 11 | `LastHostAllocatorFlow` | 在预筛选集上优先上次运行的 Host |
| 12 | `FilterFlow` | 在预筛选集上执行扩展点过滤 |

**核心优势**：不在 ServerAllocatorChain 中重新实现 16 个 Flow，完全消除了"厚适配"方案的风险。现有 HostAllocatorChain 的 Flow 代码零改动，只是输入的候选集被阶段1提前缩小了。

### 4.3 注入机制：candidateHostUuids

```java
// HostAllocatorSpec 新增字段：
private List<String> candidateHostUuids;  // 阶段1预筛选的候选 HostVO UUID 集合

// DesignatedHostAllocatorFlow 中唯一的变更点：
// 在加载初始候选 HostVO 列表后，与 candidateHostUuids 取交集
if (spec.getCandidateHostUuids() != null && !spec.getCandidateHostUuids().isEmpty()) {
    candidates = candidates.stream()
        .filter(h -> spec.getCandidateHostUuids().contains(h.getUuid()))
        .collect(Collectors.toList());
}
```

**对现有代码的影响**：
- HostAllocatorSpec 新增一个 `candidateHostUuids` 字段（向后兼容，默认 null）
- DesignatedHostAllocatorFlow 增加一个 if 分支（candidateHostUuids 为 null 时行为完全不变）
- 其他 12 个 KVM Flow 零改动

---

## 5. 兼容性风险分析

### 5.1 HostVO 引用影响评估

通过代码扫描，当前仓库中有 **275 个 Java 文件**引用了 `HostVO`（包括 HostVO 本身、HostVO_、HostVO.class 等）。

**按模块分布**：

| 模块 | 引用文件数（估算） | 风险等级 | 说明 |
|------|---------------|---------|------|
| `compute/allocator/` | ~20 | **高** | 分配器 Flow 链直接操作 HostVO 列表 |
| `compute/host/` | ~15 | **高** | Host 生命周期管理，状态机转换 |
| `plugin/kvm/` | ~30 | **中** | KVM 插件内部使用，PostConnect 钩子不改这些代码 |
| `header/host/` | ~20 | **低** | 接口定义和 VO 定义，不修改 |
| `header/allocator/` | ~10 | **中** | 分配器接口，CompatibilityBridge 拦截点 |
| `plugin/其他*` | ~50 | **低** | 网络/存储/安全等插件引用 HostVO，不受影响 |
| `premium/` | ~80 | **低** | 企业版插件，不修改 |
| 其他 | ~50 | **低** | VM 管理、镜像、控制台等 |

### 5.2 最容易被破坏的现有行为

按风险从高到低排序：

1. **AllocateHostMsg 分配路径** (风险: 高)
   - 影响范围：VM 创建、启动、迁移的核心路径
   - 风险点：阶段1的通用过滤输出的候选集如果过大或过小，会影响阶段2的效率或正确性；PhysicalServer→HostVO UUID 映射如果遗漏会丢失候选
   - 防护措施：特性开关默认关闭；阶段1输出的候选集与旧路径对比验证；candidateHostUuids 为空时降级到旧路径

2. **HostCapacityUpdater 包装器路径** (风险: 中)
   - 影响范围：VM 创建/销毁的容量扣减/归还
   - 风险点：HostCapacityUpdater 包装器需要通过 RoleVO 查找 serverUuid，增加一次 DB 查询
   - 防护措施：只锁 PhysicalServerCapacityVO 一张表（HostCapacityVO 已是 VIEW），消除了双表锁顺序导致死锁的风险；RoleVO 查询可缓存

3. **Host PostConnect FlowChain** (风险: 中)
   - 影响范围：Host 添加和重连
   - 风险点：新增 Flow 如果执行时间过长或抛出未捕获异常，会阻塞连接
   - 防护措施：内部 try-catch + trigger.next()；serialNumber 获取设超时

4. **Host Delete Cascade** (风险: 中)
   - 影响范围：Host 删除操作
   - 风险点：RoleVO 更新失败可能导致 PhysicalServer 视图中显示已删除 Host 的过期角色
   - 防护措施：afterDeleteHost 中捕获异常；定时任务清理 Stale 角色

5. **HostVO Hibernate 加载** (风险: 低)
   - 影响范围：所有 HostVO 查询
   - 风险点：如果在 HostVO 上增加到 PhysicalServerRoleVO 的关联（@OneToMany），Hibernate EAGER 加载会影响性能
   - 防护措施：**不在 HostVO 上加任何新注解**。PhysicalServerRoleVO 到 HostVO 的关联是逻辑引用（roleUuid 无 FK），不在 Hibernate 层体现

### 5.3 具体防护措施

| 防护措施 | 实现方式 | 覆盖风险 |
|---------|---------|---------|
| **特性开关** | GlobalConfig `server.compatibility.bridge.enabled`，默认 false | CompatibilityBridge 不启用则完全走旧路径 |
| **角色类型开关** | GlobalConfig `server.compatibility.bridge.enabledRoleTypes`，默认空 | 可按 KVM/BM 逐步启用 |
| **阶段1候选集对比验证** | 灰度期间对比阶段1输出的候选集是否为旧路径结果的超集；candidateHostUuids 为空时降级到旧路径 | 验证两阶段薄适配不遗漏候选 |
| **PostConnect 异常隔离** | try-catch + trigger.next() | PostConnect 钩子失败不影响 Host 连接 |
| **Delete 异常隔离** | afterDeleteHost 中 try-catch | 删除钩子失败不影响 Host 删除 |
| **单表锁** | 只锁 PhysicalServerCapacityVO（HostCapacityVO 已是 VIEW，无需加锁） | 消除双表死锁风险 |
| **不修改 HostVO** | 不加 @OneToMany、不加新字段、不改注解 | 零 Hibernate 影响 |
| **不修改 KVMHost.java** | 通过 ExtensionPoint 注入，不改方法签名 | 零代码侵入 |
| **无需对账** | HostCapacityVO 是 VIEW，数据天然一致，无需定时对账 | 消除 5 分钟延迟窗口 |
| **SQL 回滚** | 可安全删除 PhysicalServer* 表恢复原状 | NFR-008 可回滚保障 |

---

## 6. Open Questions 回答

### Q1: KVM agent 是否在所有硬件平台都能稳定读取 `/sys/class/dmi/id/product_serial`？虚拟化嵌套场景下 serialNumber 是否可靠？

**回答**:

不完全可靠。已知的不可靠场景：

| 场景 | `/sys/class/dmi/id/product_serial` 行为 | 应对策略 |
|------|---------------------------------------|---------|
| 标准 x86 服务器（Dell/HP/Lenovo） | 返回真实序列号 | 正常使用 |
| 白牌/DIY 服务器 | 可能返回 "Not Specified" 或 "To Be Filled By O.E.M." | `isValidSerialNumber()` 过滤无效值，降级到 managementIp 匹配 |
| 虚拟化嵌套（nested KVM） | 返回虚拟机 UUID（libvirt 生成）或宿主机透传值 | 视为有效 serialNumber，但不具备跨宿主机唯一性。嵌套场景通常在开发/测试环境，可接受 |
| ARM 服务器 (aarch64) | 部分固件不支持 DMI，文件可能不存在 | 捕获 FileNotFoundException，降级到 managementIp 匹配 |
| 容器内运行 agent | 依赖宿主机的 DMI 信息，需要挂载 /sys | KVM agent 不在容器内运行，此场景不适用 |

**建议**: 在 `obtainSerialNumber()` 中实现多级降级策略（IPMI FRU → DMI → managementIp），并在 `isValidSerialNumber()` 中维护已知无效值的黑名单。serialNumber 为空或无效时不阻塞流程，降级匹配即可。

### Q2: KVM Host 的 HostCapacityVO 更新是否需要同步触发 PhysicalServerCapacityVO 更新？还是异步定时对账？

**回答**:

**已解决（架构变更）**：此问题在新设计中不再存在。

PhysicalServerCapacityVO 是容量的唯一真表（source of truth），HostCapacityVO 降级为 MySQL VIEW。HostCapacityUpdater 改为包装器，内部直接写入 PhysicalServerCapacityVO，因此：

1. **不需要同步触发**：没有两张表需要同步，只有一张真表
2. **不需要异步事件驱动**：不存在"派生数据"的概念
3. **不需要定时对账**：数据天然一致，HostCapacityVO VIEW 实时投影 PhysicalServerCapacityVO
4. **不存在死锁风险**：只锁 PhysicalServerCapacityVO 一张表，不存在锁顺序问题

旧设计的复杂度（CanonicalEvent listener + 5 分钟定时对账 + 数据延迟窗口）被彻底消除

---

## 附录 A：新增文件清单

```
plugin/kvm/src/main/java/org/zstack/kvm/server/
├── KvmPhysicalServerRoleProvider.java          # SPI 实现
├── KvmPhysicalServerPostConnectExtension.java  # PostConnect 钩子
├── KvmPhysicalServerDeleteExtension.java       # Delete 钩子
├── KvmRoleInventory.java                       # KVM 角色 Inventory
└── KvmPhysicalServerConstants.java             # 常量定义
```

所有新文件放在 `plugin/kvm/` 模块内，通过 Spring Bean 注册，不修改任何现有文件。

## 附录 B：Spring Bean 注册

```xml
<!-- plugin/kvm/src/main/resources/spring/kvm-server.xml -->
<bean id="kvmPhysicalServerRoleProvider"
      class="org.zstack.kvm.server.KvmPhysicalServerRoleProvider" />

<bean id="kvmPhysicalServerPostConnectExtension"
      class="org.zstack.kvm.server.KvmPhysicalServerPostConnectExtension" />

<bean id="kvmPhysicalServerDeleteExtension"
      class="org.zstack.kvm.server.KvmPhysicalServerDeleteExtension" />
```

## 附录 C：数据流总览

```
┌─────────────┐     PostConnect      ┌──────────────────────────────┐
│ KVMHost     │ ──────────────────→  │ KvmPhysicalServerPostConnect │
│ connectHook │                      │ Extension                    │
└─────────────┘                      └──────────┬───────────────────┘
                                                 │
                                     ┌───────────▼─────────────┐
                                     │  obtainSerialNumber()    │
                                     │  (IPMI/SystemTag/Agent)  │
                                     └───────────┬─────────────┘
                                                 │
                                     ┌───────────▼─────────────┐
                                     │  matchPhysicalServer()   │
                                     │  (serialNumber优先,      │
                                     │   managementIp+zone降级) │
                                     └───────────┬─────────────┘
                                                 │
                                    匹配成功?     │
                                  ┌─── Yes ──────┤
                                  │              No
                                  │               │
                          ┌───────▼──────┐  ┌─────▼──────────────┐
                          │ 关联已有      │  │ 创建新              │
                          │ PhysicalServer│  │ PhysicalServerVO    │
                          │ VO           │  │ + CapacityVO        │
                          └───────┬──────┘  └─────┬──────────────┘
                                  │               │
                                  └───────┬───────┘
                                          │
                                ┌─────────▼──────────┐
                                │ 创建/更新            │
                                │ PhysicalServerRoleVO │
                                │ (KVM_HOST, Active)   │
                                └─────────┬──────────┘
                                          │
                                ┌─────────▼──────────┐
                                │ initCapacity()      │
                                │ 初始化              │
                                │ PhysicalServer      │
                                │ CapacityVO          │
                                │ (唯一容量真表)      │
                                └────────────────────┘

┌─────────────┐     afterDeleteHost   ┌──────────────────────────────┐
│ HostBase    │ ──────────────────→  │ KvmPhysicalServerDelete      │
│ deleteHost  │                      │ Extension                    │
└─────────────┘                      └──────────┬───────────────────┘
                                                 │
                                     ┌───────────▼─────────────┐
                                     │ RoleVO.status = Stale    │
                                     │ 检查是否最后一个角色      │
                                     │ → 更新 ServerVO.status   │
                                     └─────────────────────────┘
```

## 附录 D：约束检查清单

| 约束 | 本设计是否遵守 | 说明 |
|------|-------------|------|
| 不改 KVMHost.java 方法签名 | 是 | 通过 PostHostConnectExtensionPoint 注入 |
| 不改 KVMHostFactory.java 方法签名 | 是 | 不修改工厂类 |
| 不改 HostVO/HostAO 字段 | 是 | 不在 HostVO 上加任何注解或字段 |
| 新增代码通过 ExtensionPoint 注入 | 是 | PostHostConnectExtensionPoint + HostDeleteExtensionPoint |
| 中文描述 + 英文代码 | 是 | 文档中文，代码/注释英文 |
| Java 8 兼容 | 是 | 不使用 Java 9+ 特性 |
| @Transactional 和 @DeadlockAutoRestart 不在同一方法 | 是 | HostCapacityUpdater 包装器委托 PhysicalServerCapacityUpdater，各自独立事务 |
| PostConnect 失败不影响 Host 连接 | 是 | try-catch + trigger.next() |
| 无容量同步机制 | 是 | PhysicalServerCapacityVO 是唯一真表，HostCapacityVO 是 VIEW，数据天然一致 |
