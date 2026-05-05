# Container/K8s 角色适配器详细设计

**版本**: v1.0
**日期**: 2026-03-18
**作者**: Container Module Architecture Lead
**输入**: 架构骨架 v1.0 (第 3 章 SPI、第 9.4 章 Container 分工) + PRD FR-026 + Container 模块分析报告

---

## 第 1 章：ContainerRoleProvider 实现设计

### 1.1 类定义

```java
package org.zstack.container;

import org.zstack.header.server.*;
import org.zstack.header.server.enums.SchedulingMode;
import org.zstack.header.server.enums.ServerRoleType;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Container/K8s 角色的 PhysicalServerRoleProvider 实现。
 *
 * 核心特征：
 * 1. SchedulingMode = EXTERNAL_READONLY — ZStack 不参与 K8s 容量分配
 * 2. 容量数据来自 K8s Node Status 的 allocatable/capacity 字段
 * 3. NativeHost 不支持主动创建，所有数据由周期性同步驱动
 * 4. serialNumber 从 K8s Node SystemInfo 的 systemUUID 获取
 */
@Component
public class ContainerPhysicalServerRoleProvider implements PhysicalServerRoleProvider {

    @Autowired
    private DatabaseFacade dbf;

    @Autowired
    private ContainerUtils containerUtils;

    @Override
    public ServerRoleType getRoleType() {
        return ServerRoleType.CONTAINER_HOST;
    }

    @Override
    public SchedulingMode getSchedulingMode() {
        return SchedulingMode.EXTERNAL_READONLY;
    }

    @Override
    public CapacityUsage getCapacityConsumption(String serverUuid) {
        // 见 1.2 节
    }

    @Override
    public void onPhysicalServerCreated(String serverUuid) {
        // 见 1.4 节
    }

    @Override
    public void onPhysicalServerDeleted(String serverUuid) {
        // 见 1.4 节
    }

    @Override
    public RoleInventory getInventory(String roleUuid) {
        // 见 1.5 节
    }

    @Override
    public String matchExistingServer(RoleMatchContext context) {
        // 见 1.6 节
    }
}
```

### 1.2 getCapacityConsumption — K8s 容量读取

**设计决策**：EXTERNAL_READONLY 模式下，`getCapacityConsumption()` 返回 K8s 侧已消耗的资源量。该值参与 `recalculateCapacity` 的税收征收，计入 `PhysicalServerCapacityVO.available` 的扣减，确保混部场景下 available 反映所有角色的真实消耗。

**K8s 容量模型**：

```
K8s Node Status:
  capacity:      — 节点物理总量（OS 可见）
    cpu: "64"
    memory: "131959120Ki"
  allocatable:   — K8s 可调度总量（= capacity - kube-reserved - system-reserved - eviction-threshold）
    cpu: "63500m"
    memory: "128000000Ki"
  allocated:     — 已分配给 Pod 的 request 总和（需从 Pod 汇总计算）
```

**映射到 PhysicalServerCapacityVO**：

| PhysicalServerCapacityVO 字段 | K8s 数据源 | 说明 |
|------------------------------|-----------|------|
| totalPhysicalCpu | Node.status.capacity.cpu | 物理 CPU 总量（转换为 Hz 或核数，与 HostCapacityVO 对齐） |
| totalPhysicalMemory | Node.status.capacity.memory | 物理内存总量（字节） |
| totalCpu | Node.status.allocatable.cpu | K8s 可调度 CPU（= totalPhysicalCpu * 1.0，因为 EXTERNAL_READONLY 不设超分比） |
| totalMemory | Node.status.allocatable.memory | K8s 可调度内存 |
| availableCpu | allocatable.cpu - Σ(pod.requests.cpu) | K8s 剩余可调度 CPU |
| availableMemory | allocatable.memory - Σ(pod.requests.memory) | K8s 剩余可调度内存 |
| cpuOverprovisioningRatio | 1.0（固定） | EXTERNAL_READONLY 不做超分 |
| memoryOverprovisioningRatio | 1.0（固定） | EXTERNAL_READONLY 不做超分 |

**实现逻辑**：

```java
@Override
public CapacityUsage getCapacityConsumption(String serverUuid) {
    // 1. 通过 serverUuid 查到 PhysicalServerRoleVO
    PhysicalServerRoleVO roleVO = Q.New(PhysicalServerRoleVO.class)
        .eq(PhysicalServerRoleVO_.serverUuid, serverUuid)
        .eq(PhysicalServerRoleVO_.roleType, ServerRoleType.CONTAINER_HOST)
        .find();

    if (roleVO == null) {
        return CapacityUsage.ZERO;
    }

    // 2. 通过 roleUuid（= NativeHostVO.uuid）查到该 Host 上所有 Pod 的资源 request 总和
    String hostUuid = roleVO.getRoleUuid();

    // Pod 继承 VmInstanceVO，cpuNum 和 memorySize 存储了 Pod 的 resource request
    Tuple podUsage = Q.New(PodVO.class)
        .eq(PodVO_.hostUuid, hostUuid)
        .select(PodVO_.cpuNum, PodVO_.memorySize)
        .findTuple();

    long usedCpu = podUsage != null ? podUsage.get(0, Long.class) : 0;
    long usedMemory = podUsage != null ? podUsage.get(1, Long.class) : 0;

    CapacityUsage usage = new CapacityUsage();
    usage.setUsedCpu(usedCpu);
    usage.setUsedMemory(usedMemory);
    usage.setUsedDisk(0);  // K8s 磁盘由 CSI 管理，不在 Node 维度统计
    return usage;
}
```

**注意**：实际的 `availableCpu`/`availableMemory` 精确值依赖同步周期。Pod 汇总方式是从本地 PodVO 聚合，而不是每次实时查 K8s API，以减少 API 压力。精度取决于 `SYNC_CONTAINER_RESOURCE_INTERVAL_SECONDS` 的配置值。

### 1.3 为何超分比固定为 1.0

EXTERNAL_READONLY 的语义是"ZStack 不参与容量分配"。K8s 自身有独立的超分机制（Pod resource limits vs requests），ZStack 侧再设超分比没有意义且会导致展示数据与 K8s 实际不一致。因此：

- `cpuOverprovisioningRatio = 1.0`（固定）
- `memoryOverprovisioningRatio = 1.0`（固定）
- `ServerCapacityOverProvisioningManager.setCpuRatio(serverUuid, ratio)` 对 CONTAINER_HOST 角色的 PhysicalServerVO 是 no-op（忽略设置请求，打印 WARN 日志）

### 1.4 onPhysicalServerCreated / Deleted

```java
@Override
public void onPhysicalServerCreated(String serverUuid) {
    // Container 场景下此回调为空操作。
    // 原因：PhysicalServerVO 的创建是由 Container 同步钩子主动发起的
    //（syncNodesFromCluster → matchExistingServer → 创建 PhysicalServerVO），
    // 不需要在 PhysicalServerManager 创建 PhysicalServerVO 之后反过来通知 Container 模块。
    //
    // 与 KVM 不同——KVM 是先有 HostVO（AddKVMHost API 创建），后有 PhysicalServerVO（PostConnect 同步）；
    // Container 是先有 NativeHostVO（K8s 同步创建），后有 PhysicalServerVO（同步钩子创建）。
    // 两者方向一致，不需要反向通知。
    logger.debug(String.format("onPhysicalServerCreated: no-op for CONTAINER_HOST, serverUuid=%s", serverUuid));
}

@Override
public void onPhysicalServerDeleted(String serverUuid) {
    // Container 场景下此回调为空操作。
    // 原因：PhysicalServerVO 的删除不应影响 NativeHostVO（NativeHost 生命周期由 K8s 管理）。
    // 如果管理员通过 APIDeletePhysicalServerMsg 删除了 PhysicalServerVO，
    // 下次同步周期会重新创建它。
    //
    // RoleVO 通过 FK CASCADE 自动删除，无需额外清理。
    logger.debug(String.format("onPhysicalServerDeleted: no-op for CONTAINER_HOST, serverUuid=%s", serverUuid));
}
```

### 1.5 getInventory — Container 特有字段

```java
@Override
public RoleInventory getInventory(String roleUuid) {
    NativeHostVO nativeHost = dbf.findByUuid(roleUuid, NativeHostVO.class);
    if (nativeHost == null) {
        return null;
    }

    ContainerRoleInventory inv = new ContainerRoleInventory();
    inv.setRoleUuid(roleUuid);
    inv.setRoleType(ServerRoleType.CONTAINER_HOST.toString());
    inv.setClusterUuid(nativeHost.getClusterUuid());
    inv.setStatus(nativeHost.getStatus().toString());

    // Container 特有字段
    inv.setEndpointUuid(nativeHost.getEndpointUuid());
    inv.setK8sNodeName(nativeHost.getName());
    inv.setK8sNodeUid(nativeHost.getUuid());  // NativeHostVO.uuid = K8s Node UID（去连字符）
    inv.setManagementIp(nativeHost.getManagementIp());

    return inv;
}
```

**ContainerRoleInventory 定义**：

```java
package org.zstack.container;

import org.zstack.header.server.RoleInventory;

/**
 * Container 角色 Inventory，扩展基类以包含 Container 特有字段。
 */
public class ContainerRoleInventory extends RoleInventory {
    private String endpointUuid;
    private String k8sNodeName;
    private String k8sNodeUid;
    private String managementIp;

    // getter/setter 省略
}
```

### 1.6 matchExistingServer — 角色自动关联

```java
@Override
public String matchExistingServer(RoleMatchContext context) {
    // 匹配优先级：
    // 1. serialNumber 精确匹配（serialNumber = K8s Node SystemInfo.systemUUID）
    // 2. managementIp + zoneUuid 降级匹配

    String serialNumber = context.getSerialNumber();
    String zoneUuid = context.getZoneUuid();
    String managementIp = context.getManagementIp();

    // Step 1: serialNumber 匹配
    if (serialNumber != null && !serialNumber.isEmpty()
            && !"Not Specified".equals(serialNumber)
            && !"None".equals(serialNumber)) {
        String serverUuid = Q.New(PhysicalServerVO.class)
            .eq(PhysicalServerVO_.serialNumber, serialNumber)
            .eq(PhysicalServerVO_.zoneUuid, zoneUuid)
            .select(PhysicalServerVO_.uuid)
            .findValue();
        if (serverUuid != null) {
            return serverUuid;
        }
    }

    // Step 2: managementIp + zoneUuid 降级匹配
    if (managementIp != null && !managementIp.isEmpty()) {
        String serverUuid = Q.New(PhysicalServerVO.class)
            .eq(PhysicalServerVO_.managementIp, managementIp)
            .eq(PhysicalServerVO_.zoneUuid, zoneUuid)
            .select(PhysicalServerVO_.uuid)
            .findValue();
        if (serverUuid != null) {
            return serverUuid;
        }
    }

    // Step 3: 无匹配
    return null;
}
```

---

## 第 2 章：NativeHost 连接同步钩子

### 2.1 核心设计

Container 模块的 NativeHost **没有传统的 Connect/Reconnect 流程**。NativeHostVO 的创建、更新、删除完全由 `ContainerEndpointBase.syncNodesFromCluster()` 的周期性同步驱动。因此，PhysicalServerVO 的同步钩子需要嵌入到同步流程中，而不是传统的 `HostAfterConnectedExtensionPoint`。

**钩子注入点**：在 `syncNodesFromCluster()` 方法中，每次成功创建或更新 NativeHostVO 之后，触发 PhysicalServerVO 的创建/关联。

### 2.2 同步入口点

```
ContainerEndpointBase.doSyncContainerManagementEndpoint()
  └── Flow 2: sync-node
        └── syncNodesFromCluster(NativeClusterVO cluster, ...)
              └── 遍历 K8s nodes:
                    ├── containerUtils.toNativeHostVO(node)   // 转换
                    ├── dbf.persist(host)  或 dbf.update(host) // 持久化
                    └── ★ syncPhysicalServer(host, node)       // 新增：同步 PhysicalServerVO
```

### 2.3 syncPhysicalServer 方法设计

```java
/**
 * 在 syncNodesFromCluster 中，每处理一个 NativeHostVO 之后调用此方法，
 * 创建或关联对应的 PhysicalServerVO + PhysicalServerRoleVO。
 *
 * @param host 已持久化的 NativeHostVO
 * @param nodeInventory K8s Node Inventory（含 systemInfo、capacity 等原始数据）
 */
private void syncPhysicalServer(NativeHostVO host, KubernetesNodeInventory nodeInventory) {
    // 1. 提取 serialNumber
    String serialNumber = extractSerialNumber(nodeInventory);

    // 2. 构建匹配上下文
    RoleMatchContext context = new RoleMatchContext();
    context.setSerialNumber(serialNumber);
    context.setManagementIp(host.getManagementIp());
    context.setZoneUuid(host.getZoneUuid());
    context.setClusterUuid(host.getClusterUuid());

    // 3. 调用 PhysicalServerManager 注册/关联
    //    PhysicalServerManager 内部调用各 RoleProvider.matchExistingServer()，
    //    匹配成功则关联已有 PhysicalServerVO，匹配失败则新建。
    PhysicalServerManager serverManager = ...; // Spring 注入
    serverManager.registerRole(
        ServerRoleType.CONTAINER_HOST,
        host.getUuid(),          // roleUuid = NativeHostVO.uuid
        host.getClusterUuid(),
        context,
        buildCapacityFromK8sNode(nodeInventory)
    );
}
```

### 2.4 serialNumber 获取方式

**数据来源**：Kubernetes Node 的 `status.nodeInfo` 包含以下系统信息：

```json
{
  "status": {
    "nodeInfo": {
      "machineID": "a1b2c3d4e5f6...",
      "systemUUID": "421E4C56-3F2B-1A9D-8C7E-001A2B3C4D5E",
      "bootID": "...",
      "kernelVersion": "5.15.0",
      "osImage": "Ubuntu 22.04",
      "architecture": "amd64"
    }
  }
}
```

**选择 systemUUID 作为 serialNumber**：

| 候选字段 | 优点 | 缺点 | 结论 |
|---------|------|------|------|
| systemUUID | 来自 SMBIOS/DMI，与裸机 product_serial 同源 | 虚拟化嵌套场景可能不可靠 | **首选** |
| machineID | 稳定，由 OS 生成 | 重装 OS 后变化；与其他角色（KVM）的 serialNumber 不是同一数据源 | 降级备选 |
| bootID | 每次重启变化 | 不适合做唯一标识 | 排除 |

**提取逻辑**：

```java
private String extractSerialNumber(KubernetesNodeInventory nodeInventory) {
    // 优先使用 systemUUID（与 KVM 的 /sys/class/dmi/id/product_serial 同源）
    String systemUUID = nodeInventory.getSystemUUID();
    if (isValidSerialNumber(systemUUID)) {
        return normalizeSerialNumber(systemUUID);
    }

    // 降级使用 machineID
    String machineID = nodeInventory.getMachineID();
    if (isValidSerialNumber(machineID)) {
        return machineID;
    }

    // 兜底：返回 null，由 matchExistingServer 走 managementIp 降级匹配
    return null;
}

/**
 * 检查 serialNumber 是否有效。
 * 排除已知无效值：null、空串、全零、"Not Specified"、"None" 等。
 */
private boolean isValidSerialNumber(String sn) {
    if (sn == null || sn.trim().isEmpty()) return false;
    String trimmed = sn.trim();
    if ("Not Specified".equalsIgnoreCase(trimmed)) return false;
    if ("None".equalsIgnoreCase(trimmed)) return false;
    if (trimmed.matches("^0+$")) return false;
    if (trimmed.matches("^0+(-0+)*$")) return false;  // 全零 UUID: 00000000-0000-...
    return true;
}

/**
 * 规范化 serialNumber：去掉连字符，统一大写。
 * 目的是与 KVM 侧 product_serial 的格式对齐（KVM agent 读到的可能带连字符也可能不带）。
 */
private String normalizeSerialNumber(String sn) {
    return sn.replace("-", "").toUpperCase().trim();
}
```

**修改 KubernetesNodeInventory 的要求**：

当前 `KubernetesNodeInventory` 可能不包含 `systemUUID` 和 `machineID` 字段。需要在 `KubernetesNativeProvider.listNodes()` 中从 `V1Node.status.nodeInfo` 提取这两个字段，添加到 `KubernetesNodeInventory` 中。

```java
// 在 KubernetesNativeProvider 中，构建 KubernetesNodeInventory 时补充：
if (node.getStatus() != null && node.getStatus().getNodeInfo() != null) {
    V1NodeSystemInfo nodeInfo = node.getStatus().getNodeInfo();
    inventory.setSystemUUID(nodeInfo.getSystemUUID());
    inventory.setMachineID(nodeInfo.getMachineID());
    inventory.setArchitecture(nodeInfo.getArchitecture());
    inventory.setOsImage(nodeInfo.getOsImage());
    inventory.setKernelVersion(nodeInfo.getKernelVersion());
}
```

### 2.5 PhysicalServerVO 创建/更新逻辑

当 `matchExistingServer()` 返回 null（无匹配）时，`PhysicalServerManager.registerRole()` 内部新建 PhysicalServerVO。此时需要填充的字段：

| PhysicalServerVO 字段 | 数据来源 | 说明 |
|-----------------------|---------|------|
| uuid | 新生成（Platform.getUuid()） | PhysicalServer 独立 UUID，不复用 NativeHostVO.uuid |
| zoneUuid | NativeHostVO.zoneUuid | 继承 Zone 归属 |
| poolUuid | 默认 Pool 或配置指定 | 见 2.6 节 |
| name | NativeHostVO.name（= K8s Node name） | 物理机名称 |
| managementIp | NativeHostVO.managementIp（= K8s Node InternalIP） | 管理 IP |
| serialNumber | extractSerialNumber(nodeInventory) | systemUUID 或 machineID |
| architecture | nodeInfo.architecture（"amd64" → "x86_64"） | 架构类型映射 |
| manufacturer | 不可获取 | K8s Node 不暴露硬件厂商，留空 |
| model | 不可获取 | K8s Node 不暴露机型，留空 |
| state | Enabled | 初始启用 |
| status | 从 K8s Node Ready 条件映射 | Ready=True → Connected; 否则 → Disconnected |
| powerStatus | Unknown | Container 场景无 OOB，电源状态未知 |
| oobManagementType | null | Container 场景无 OOB |
| oobAddress/Port/Username/Password | null | Container 场景无 OOB |

当 `matchExistingServer()` 返回已有 serverUuid 时（已被其他角色如 KVM 注册过），执行**增量更新**：

- 如果 PhysicalServerVO.serialNumber 为空且本次获取到了有效值，回填之
- 更新 PhysicalServerVO.status 为最新的 K8s Node 状态
- 不覆盖 name、manufacturer、model 等（可能 KVM 侧填写更准确）

### 2.6 poolUuid 的确定策略

Container 场景下 PhysicalServerVO 的 poolUuid 确定逻辑：

1. **系统配置项**：`ContainerGlobalConfig.DEFAULT_SERVER_POOL_UUID` — 管理员可配置容器节点默认归属的 ServerPool
2. **Zone 默认 Pool**：如果未配置，使用 Zone 的默认 Pool（`default-pool-{zoneUuid}`，由迁移脚本创建）
3. **关联已有**：如果通过 serialNumber 匹配到已有 PhysicalServerVO，沿用其 poolUuid，不覆盖

### 2.7 同步时序图

```
周期性定时器 / APISyncContainerManagementEndpointMsg
    │
    ▼
ContainerEndpointBase.doSyncContainerManagementEndpoint()
    │
    ├── Flow 1: sync-cluster
    │     └── provider.listClusters() → saveAsNativeClusters()
    │
    ├── Flow 2: sync-node  ★ 钩子注入点
    │     └── syncNodesFromCluster(cluster):
    │           for each K8s Node:
    │             │
    │             ├── containerUtils.toNativeHostVO(node) → NativeHostVO
    │             │
    │             ├── dbf.persist(host) / dbf.update(host)
    │             │
    │             └── ★ syncPhysicalServer(host, node):
    │                   │
    │                   ├── extractSerialNumber(node)
    │                   │     └── systemUUID → normalizeSerialNumber()
    │                   │
    │                   ├── RoleMatchContext(serialNumber, managementIp, zoneUuid)
    │                   │
    │                   └── PhysicalServerManager.registerRole():
    │                         │
    │                         ├── matchExistingServer(context)
    │                         │     ├── 匹配成功 → 关联已有 PhysicalServerVO
    │                         │     └── 匹配失败 → 新建 PhysicalServerVO
    │                         │
    │                         ├── 创建/更新 PhysicalServerRoleVO
    │                         │     (serverUuid, CONTAINER_HOST, roleUuid=NativeHostVO.uuid)
    │                         │
    │                         └── 创建/更新 PhysicalServerCapacityVO
    │                               (totalPhysicalCpu, totalPhysicalMemory, ...)
    │
    ├── Flow 3: sync-pod
    │     └── doSyncPodsFromNodes()
    │           └── ★ 同步完成后触发容量刷新（见第 3 章）
    │
    └── ... 后续 Flow 不变
```

### 2.8 过期节点处理

在 `syncNodesFromCluster()` 中，K8s 上已删除但 ZStack 仍存在的 NativeHost 会被删除（`deleteClusterResourcesByUuids`）。同步钩子需要在此处理过期的 PhysicalServerRoleVO：

```java
// 现有代码：删除过期 NativeHost
List<String> staleHostUuids = Q.New(NativeHostVO.class)
    .eq(NativeHostVO_.endpointUuid, self.getUuid())
    .notIn(NativeHostVO_.uuid, currentHostUuids)
    .select(NativeHostVO_.uuid)
    .listValues();

// ★ 新增：标记对应的 PhysicalServerRoleVO 为 Stale
for (String staleHostUuid : staleHostUuids) {
    PhysicalServerRoleVO roleVO = Q.New(PhysicalServerRoleVO.class)
        .eq(PhysicalServerRoleVO_.roleUuid, staleHostUuid)
        .eq(PhysicalServerRoleVO_.roleType, ServerRoleType.CONTAINER_HOST)
        .find();
    if (roleVO != null) {
        roleVO.setRoleStatus("Stale");
        dbf.update(roleVO);

        // 如果该 PhysicalServer 没有其他 Active 角色，更新状态为 Disconnected
        long activeRoleCount = Q.New(PhysicalServerRoleVO.class)
            .eq(PhysicalServerRoleVO_.serverUuid, roleVO.getServerUuid())
            .eq(PhysicalServerRoleVO_.roleStatus, "Active")
            .count();
        if (activeRoleCount == 0) {
            PhysicalServerVO serverVO = dbf.findByUuid(roleVO.getServerUuid(), PhysicalServerVO.class);
            if (serverVO != null) {
                serverVO.setStatus(PhysicalServerStatus.Disconnected);
                dbf.update(serverVO);
            }
        }
    }
}

// 现有代码：删除过期 NativeHost
deleteClusterResourcesByUuids(null, staleHostUuids, null);
```

---

## 第 3 章：EXTERNAL_READONLY 模式详细设计

### 3.1 核心原则

EXTERNAL_READONLY 的设计哲学：**ZStack 不通过自身分配引擎分配 Container 工作负载，但容量消耗计入 PhysicalServerCapacityVO.available**。

- ZStack 不参与 Container 工作负载的调度决策
- Container 角色的容量消耗通过 PhysicalServerCapacityUpdater.decreaseCapacity() 正常扣减
- ZStack 定期同步 K8s 报告的容量数据，并通过 recalculateCapacity 统一征税
- available = 总容量 - 所有角色消耗（包括 Container），不能超配

### 3.2 PhysicalServerCapacityUpdater 对 EXTERNAL_READONLY 的处理

EXTERNAL_READONLY 不再是 no-op，而是正常参与容量扣减和征税。Container sync 时通过 `PhysicalServerCapacityUpdater.decreaseCapacity()` 扣减容量，`recalculateCapacity` 的税收模式不跳过 EXTERNAL_READONLY，所有角色的 `getCapacityConsumption()` 都参与计算。

```java
// PhysicalServerCapacityUpdaterImpl.java

@Override
public void decreaseCapacity(String serverUuid,
                              long requiredCpu, long requiredMemory, long requiredDisk) {
    // EXTERNAL_READONLY 正常扣减（Container sync 时调用）
    // 所有 SchedulingMode 统一走扣减逻辑
    SchedulingMode mode = getSchedulingMode(serverUuid);
    // ...正常扣减 ...
}

@Override
public void increaseCapacity(String serverUuid,
                              long releasedCpu, long releasedMemory, long releasedDisk) {
    // EXTERNAL_READONLY 正常归还（Container Pod 被删除时调用）
    SchedulingMode mode = getSchedulingMode(serverUuid);
    // ...正常归还 ...
}

@Override
public void recalculateCapacity(String serverUuid) {
    // 所有角色（包括 EXTERNAL_READONLY）都参与税收征收
    // Container 的 getCapacityConsumption() 返回 K8s 侧已消耗的资源量
    // available = total - Σ(所有角色消耗) - Σ(系统预留)
    // ...税收模式重计算 ...
}
```

### 3.3 K8s 容量变化时的更新机制

**触发时机**：容量数据在每次同步周期中更新，具体在两个点：

1. **sync-node Flow**：同步 K8s Node 时，更新 `totalPhysicalCpu` / `totalPhysicalMemory`（Node capacity/allocatable）
2. **sync-pod Flow**：同步 Pod 时，重新聚合 Pod resource requests，更新 `availableCpu` / `availableMemory`

**更新逻辑（嵌入 syncPhysicalServer 方法）**：

```java
private PhysicalServerCapacityVO buildCapacityFromK8sNode(KubernetesNodeInventory node) {
    PhysicalServerCapacityVO cap = new PhysicalServerCapacityVO();

    // 从 K8s Node capacity 获取物理总量
    cap.setTotalPhysicalCpu(parseK8sCpuToHz(node.getCapacityCpu()));
    cap.setTotalPhysicalMemory(parseK8sMemoryToBytes(node.getCapacityMemory()));

    // EXTERNAL_READONLY 不做超分
    cap.setCpuOverprovisioningRatio(1.0);
    cap.setMemoryOverprovisioningRatio(1.0);

    // totalCpu/Memory = allocatable（K8s 可调度总量）
    cap.setTotalCpu(parseK8sCpuToHz(node.getAllocatableCpu()));
    cap.setTotalMemory(parseK8sMemoryToBytes(node.getAllocatableMemory()));

    // available 先设为 allocatable，Pod 同步后再更新
    cap.setAvailableCpu(cap.getTotalCpu());
    cap.setAvailableMemory(cap.getTotalMemory());

    cap.setReservedCpu(0);
    cap.setReservedMemory(0);
    cap.setCapacityState(CapacityState.Ready);

    return cap;
}
```

**Pod 同步完成后的容量刷新**（嵌入 sync-pod Flow 之后）：

```java
// 在 doSyncPodsFromNodes 完成后，刷新各 NativeHost 对应的 PhysicalServerCapacityVO
private void refreshContainerCapacityAfterPodSync(List<NativeHostInventory> hosts) {
    for (NativeHostInventory host : hosts) {
        PhysicalServerRoleVO roleVO = Q.New(PhysicalServerRoleVO.class)
            .eq(PhysicalServerRoleVO_.roleUuid, host.getUuid())
            .eq(PhysicalServerRoleVO_.roleType, ServerRoleType.CONTAINER_HOST)
            .find();

        if (roleVO == null) continue;

        PhysicalServerCapacityVO capVO = dbf.findByUuid(
            roleVO.getServerUuid(), PhysicalServerCapacityVO.class);
        if (capVO == null) continue;

        // 聚合该 Host 上所有 Pod 的 CPU/Memory request
        Long totalPodCpu = SQL.New(
            "select coalesce(sum(p.cpuNum), 0) from PodVO p where p.hostUuid = :hostUuid",
            Long.class)
            .param("hostUuid", host.getUuid())
            .find();
        Long totalPodMemory = SQL.New(
            "select coalesce(sum(p.memorySize), 0) from PodVO p where p.hostUuid = :hostUuid",
            Long.class)
            .param("hostUuid", host.getUuid())
            .find();

        capVO.setAvailableCpu(capVO.getTotalCpu() - totalPodCpu);
        capVO.setAvailableMemory(capVO.getTotalMemory() - totalPodMemory);
        dbf.update(capVO);
    }
}
```

### 3.4 分配引擎中的 EXTERNAL_READONLY 过滤

`ServerAllocatorChain` 的 `SchedulingModeFilter` Flow 中，当 `AllocateServerMsg.schedulingMode != EXTERNAL_READONLY` 时（即正常分配请求），会过滤掉所有 EXTERNAL_READONLY 的 PhysicalServerVO。

反之，如果有人显式请求 `EXTERNAL_READONLY` 模式的分配（语义上不应发生），`CapacityFilterFlow` 会跳过容量检查（因为 ZStack 不管控 K8s 容量）。

```java
// SchedulingModeFilterFlow.java
@Override
public void allocate() {
    if (spec.getSchedulingMode() != null) {
        // 显式指定了 schedulingMode，按指定值过滤
        candidates = candidates.stream()
            .filter(s -> hasRoleWithSchedulingMode(s.getUuid(), spec.getSchedulingMode()))
            .collect(Collectors.toList());
    } else {
        // 默认排除 EXTERNAL_READONLY（ZStack 内部分配不应分到 K8s 管控的机器上）
        candidates = candidates.stream()
            .filter(s -> !hasOnlyExternalReadonlyRoles(s.getUuid()))
            .collect(Collectors.toList());
    }

    if (candidates.isEmpty()) {
        fail("no server matches the scheduling mode requirement");
    }
    next(candidates);
}
```

---

## 第 4 章：KVM + Container 混部场景

### 4.1 场景描述

同一台物理机同时运行：
- KVM hypervisor（管理虚拟机）
- K8s kubelet（作为 K8s 集群的 Worker 节点）

此时在 ZStack 中，这台物理机同时拥有：
- 一个 KVMHostVO（hypervisorType = KVM）
- 一个 NativeHostVO（hypervisorType = Native）

### 4.2 两个 RoleVO 如何共存

```
PhysicalServerVO (uuid = ps-001)
  ├── PhysicalServerRoleVO (roleType = KVM_HOST)
  │     ├── roleUuid = kvm-host-uuid-001
  │     ├── clusterUuid = kvm-cluster-001
  │     └── schedulingMode = INTERNAL_SHARED
  │
  └── PhysicalServerRoleVO (roleType = CONTAINER_HOST)
        ├── roleUuid = native-host-uuid-001
        ├── clusterUuid = native-cluster-001
        └── schedulingMode = EXTERNAL_READONLY
```

**约束保证**：`UNIQUE(serverUuid, roleType)` 确保同一物理服务器同一角色类型只有一条记录。KVM_HOST 和 CONTAINER_HOST 是不同的 roleType，可以共存。

**不同 Cluster 归属**：注意两个角色的 `clusterUuid` 不同——KVM Host 属于 KVM Cluster，NativeHost 属于 Native Cluster。这是正常的，因为 PhysicalServerRoleVO.clusterUuid 记录的是角色实体在各自体系中的 Cluster 归属。

### 4.3 混部容量管理（最终方案）

**设计决策：互为系统预留 + Node Taint 熔断。**

PhysicalServer 层是全知者，KVM 和 Container 互为"系统预留"。PhysicalServerCapacityVO 记录全局唯一真相，两个调度器各自只看到自己被分配的份额。

#### 核心容量模型

```
PhysicalServer 层（唯一真相）:
  totalPhysicalCpu = 64
  kvmPhysicalUsed = 40
  containerPhysicalUsed = 20（Σ pod.requests.cpu）
  safetyBuffer = max(4, totalPhysical × 5%)
  realAvailable = 64 - 40 - 20 - 4 = 0

KVM Role 视角:
  availablePhysical = totalPhysical - containerReserved - safetyBuffer
  availableLogical = availablePhysical × overProvisioningRatio - kvmLogicalUsed
  → Container 消耗对 KVM 表现为"系统预留"，不可见

Container Role 视角:
  通过 Node Taint 控制（v1.0）
  通过 Device Plugin + Webhook 精确控制（v1.1+）
  → KVM 消耗对 Container 表现为不可调度
```

#### Safety Buffer

```
cpuSafetyBuffer = max(4 cores, totalPhysicalCpu × 5%)
memorySafetyBuffer = max(4 GB, totalPhysicalMemory × 10%)
```

- 内存 buffer 更保守（OOM kill 后果比 CPU 降速严重）
- GlobalConfig 配置，管理员可调
- DaemonSet 暗消耗纳入 buffer

#### 超分比语义

超分比应用于 KVM 可用的物理份额（扣除 Container 消耗和 safety buffer 后）：

```
physicalAvailableForKVM = totalPhysicalCpu - containerPhysicalUsed - safetyBuffer
kvmAvailableLogical = physicalAvailableForKVM × cpuOverProvisioningRatio - kvmLogicalUsed
```

#### 内存语义

Container 按 Pod **request**（不是 limit）扣除物理容量：

```
containerPhysicalMemoryUsed = Σ(pod.spec.containers[].resources.requests.memory)
```

理由：与 K8s 调度语义一致，request 是"保证量"。

#### K8s 侧防超卖（分期）

| 阶段 | 方案 | 实现 | 精度 |
|------|------|------|------|
| v1.0 | Node Taint 熔断 | kubeconfig → patchNode API | Node 级开关 |
| v1.1 | Device Plugin | DaemonSet + Extended Resource | Node 级精确 |
| v1.1 | Admission Webhook | Webhook Service | Pod 级精确 |

**v1.0 Taint 管理代码示例**（在 ContainerRoleProvider 或 PhysicalServerManagerImpl 中实现）：

```java
public void checkAndUpdateNodeTaint(String serverUuid) {
    PhysicalServerCapacityVO cap = dbf.findByUuid(serverUuid, PhysicalServerCapacityVO.class);
    long physicalAvailable = cap.getTotalPhysicalCpu()
        - getKvmPhysicalUsed(serverUuid)
        - getContainerPhysicalUsed(serverUuid);

    String nodeName = getK8sNodeName(serverUuid);
    if (nodeName == null) return; // 非混部节点

    if (physicalAvailable < cpuSafetyBuffer) {
        k8sClient.addTaint(nodeName, "zstack.io/capacity-exhausted", "NoSchedule");
    } else {
        k8sClient.removeTaint(nodeName, "zstack.io/capacity-exhausted");
    }
}
```

**Zaku 对接说明**：Taint 管理通过 kubeconfig 直连 K8s API（patchNode），不走 Zaku 平台 API。理由：Taint 是 Node 级操作，Zaku 平台 API 面向应用编排层，不暴露 Node 级原语。kubeconfig 在 ContainerManagementEndpointVO 中已有存储。

#### 协调机制

PhysicalServer 层通过 Msg/SDK 协调双方：
- KVM 消耗变化 → PhysicalServerCapacityUpdater 更新 → 检查是否需要打/移除 Taint
- Container Pod 变化 → sync 更新 → PhysicalServerCapacityVO.available 自动扣减

#### 验收标准

- AC-1: kvmPhysicalUsed + containerPhysicalUsed + safetyBuffer ≤ totalPhysical
- AC-2: PhysicalServer 层与两侧偏差在 1 个 sync 周期（10s）内收敛
- AC-3: kvmAvailableLogical = (totalPhysical - containerReserved - safetyBuffer) × ratio - kvmUsed
- AC-4: physicalAvailable < safetyBuffer 时，ZStack 拒绝新 VM，K8s Node 被打 Taint

#### ADR（Architecture Decision Record）

- **Decision**: 互为系统预留 + Node Taint 熔断
- **Drivers**: 防超卖硬约束、单一数据源、调度器主权不侵犯
- **Alternatives**: 静态分区（利用率低）、Capacity Broker（复杂度高）、Zaku 项目配额（精度不够，会误限制非混部 Node）
- **Why chosen**: 最小实现成本 + 分期可扩展 + 基于实际环境验证（172.30.8.31/32 昇腾混部）
- **Consequences**: v1.0 粒度为 Node 级开关，sync 窗口内有有界不一致
- **Follow-ups**: v1.1 Device Plugin + Admission Webhook

#### 已知限制

- sync 延迟窗口（~10s），safety buffer 兜底
- NUMA topology 不处理（v2.0）
- 项目配额不适用于部分混部的多节点集群

**混部容量展示结构**（QueryPhysicalServerMsg 返回）：

```json
{
  "uuid": "ps-001",
  "name": "混部物理机",
  "serialNumber": "421E4C56...",
  "capacity": {
    "totalPhysicalCpu": 64000000000,
    "totalPhysicalMemory": 137438953472,
    "availableCpu": 32000000000,
    "availableMemory": 68719476736
  },
  "roles": [
    {
      "roleType": "KVM_HOST",
      "roleUuid": "kvm-host-uuid-001",
      "schedulingMode": "INTERNAL_SHARED",
      "roleStatus": "Active",
      "capacityConsumption": {
        "usedCpu": 32000000000,
        "usedMemory": 68719476736
      }
    },
    {
      "roleType": "CONTAINER_HOST",
      "roleUuid": "native-host-uuid-001",
      "schedulingMode": "EXTERNAL_READONLY",
      "roleStatus": "Active",
      "capacityConsumption": {
        "usedCpu": 48000000000,
        "usedMemory": 103079215104
      }
    }
  ]
}
```

### 4.4 serialNumber / managementIp 匹配保证两个角色关联到同一个 PhysicalServerVO

**关联流程**：

假设先注册 KVM Host，后添加 Container Endpoint 并同步 NativeHost：

```
Step 1: AddKVMHost → KVM PostConnect
  → KVM agent 读取 /sys/class/dmi/id/product_serial → serialNumber = "421E4C563F2B..."
  → matchExistingServer() → 无匹配 → 新建 PhysicalServerVO(uuid=ps-001, serialNumber="421E4C563F2B...")
  → 新建 PhysicalServerRoleVO(serverUuid=ps-001, roleType=KVM_HOST, roleUuid=kvm-host-001)

Step 2: SyncContainerManagementEndpoint → syncNodesFromCluster
  → K8s Node systemUUID = "421E4C56-3F2B-1A9D-8C7E-001A2B3C4D5E"
  → normalizeSerialNumber() → "421E4C563F2B1A9D8C7E001A2B3C4D5E"
  → matchExistingServer(serialNumber="421E4C563F2B...") → 匹配到 ps-001
  → 新建 PhysicalServerRoleVO(serverUuid=ps-001, roleType=CONTAINER_HOST, roleUuid=native-host-001)
```

**关键保证**：
1. serialNumber 的 `normalizeSerialNumber()` 统一去除连字符、转大写，确保 KVM agent 的 `product_serial` 和 K8s 的 `systemUUID` 能匹配
2. 如果 serialNumber 不可用（虚拟化嵌套），降级到 managementIp + zoneUuid 匹配
3. managementIp 可能不同（KVM 用管理网 IP，K8s 用 InternalIP），此时需要确保两者的管理网 IP 相同，或者 serialNumber 有效

**managementIp 不同的情况**：

在多网卡场景下，KVM Host 的 managementIp（SSH 管理地址）和 K8s Node 的 InternalIP 可能不同。此时 managementIp 降级匹配会失败。解决方案：

- **依赖 serialNumber 匹配**：这是首选方案，物理机的 SMBIOS systemUUID 在所有网络视角下一致
- **管理员手动关联**：提供 API 允许管理员手动指定 PhysicalServerRoleVO 的 serverUuid

### 4.5 混部场景下的状态同步

两个角色独立更新 PhysicalServerRoleVO.roleStatus：
- KVM 角色：通过 HostTracker Ping 心跳更新（HostVO.status → roleStatus 映射）
- Container 角色：通过周期性同步更新（K8s Node Ready 条件 → roleStatus 映射）

PhysicalServerVO.status 取两个角色状态的"乐观值"：只要有一个角色是 Active/Connected，PhysicalServerVO.status = Connected。

```java
// PhysicalServerManager 中的状态聚合逻辑
private PhysicalServerStatus aggregateStatus(String serverUuid) {
    List<String> roleStatuses = Q.New(PhysicalServerRoleVO.class)
        .eq(PhysicalServerRoleVO_.serverUuid, serverUuid)
        .select(PhysicalServerRoleVO_.roleStatus)
        .listValues();

    // 只要有一个 Active 角色，PhysicalServer 就是 Connected
    if (roleStatuses.stream().anyMatch("Active"::equals)) {
        return PhysicalServerStatus.Connected;
    }
    return PhysicalServerStatus.Disconnected;
}
```

---

## 第 5 章：兼容性风险分析

### 5.1 NativeHostVO 继承 HostVO 引发的操作传播风险

**风险描述**：NativeHostVO 继承 HostVO，HostVO 体系有大量操作（ChangeHostState、ReconnectHost、MigrateVm 等）。这些操作可能意外影响 Container 角色对应的 PhysicalServerVO。

**分析**：

| HostVO 操作 | 是否影响 PhysicalServerVO | 原因 |
|-------------|--------------------------|------|
| ChangeHostState(Enabled/Disabled) | **否** | PhysicalServerVO.state 独立管理，不与 HostVO.state 同步 |
| ReconnectHost | **否** | DummyNativeHost 直接返回成功，不触发真实连接。同步钩子由周期性同步驱动 |
| PingHost | **否** | DummyNativeHost 直接返回 PingHostReply，不触发任何 PhysicalServer 更新 |
| DeleteHost | **需要处理** | NativeHost 删除时需要同步更新 PhysicalServerRoleVO.roleStatus = Stale |
| MaintainHost | **否** | DummyNativeHost 不处理维护模式消息 |

**DeleteHost 的处理**：

NativeHost 的删除有两种路径：
1. **K8s 同步删除**（常规路径）：syncNodesFromCluster 发现过期节点 → deleteClusterResourcesByUuids → 见 2.8 节的 Stale 处理
2. **API 删除 Endpoint**（级联路径）：DeleteContainerManagementEndpoint → CASCADE 删除所有 NativeHost → 需要通过 `HostDeleteExtensionPoint` 钩子更新 RoleVO

```java
// 实现 HostDeleteExtensionPoint 以捕获 NativeHost 删除事件
@Component
public class ContainerPhysicalServerRoleProvider
    implements PhysicalServerRoleProvider, HostDeleteExtensionPoint {

    @Override
    public void preDeleteHost(HostInventory host) {
        // 不阻止删除
    }

    @Override
    public void afterDeleteHost(HostInventory host) {
        if (!ContainerConstant.NATIVE_HYPERVISOR_TYPE.equals(host.getHypervisorType())) {
            return;
        }

        // 标记 RoleVO 为 Stale
        PhysicalServerRoleVO roleVO = Q.New(PhysicalServerRoleVO.class)
            .eq(PhysicalServerRoleVO_.roleUuid, host.getUuid())
            .eq(PhysicalServerRoleVO_.roleType, ServerRoleType.CONTAINER_HOST)
            .find();

        if (roleVO != null) {
            roleVO.setRoleStatus("Stale");
            dbf.update(roleVO);
        }
    }
}
```

### 5.2 Container 模块的 ExtensionPoint 是否足够支持钩子注入

**现有可用 ExtensionPoint**：

| ExtensionPoint | 是否适用 | 说明 |
|----------------|---------|------|
| `HostAfterConnectedExtensionPoint` | **不适用** | NativeHost 没有传统的 Connect 流程（DummyNativeHost.handleMessage 直接回复） |
| `HostConnectionReestablishExtensionPoint` | **不适用** | 同上 |
| `HostDeleteExtensionPoint` | **适用** | NativeHost 删除时可收到通知（见 5.1） |
| 无同步完成 ExtensionPoint | **需要新增或直接修改** | 同步流程没有公开的扩展点 |

**结论**：Container 模块缺少同步完成的 ExtensionPoint。推荐的方案是**直接在 ContainerEndpointBase.syncNodesFromCluster() 中添加 PhysicalServer 同步调用**，而不是通过 ExtensionPoint 间接注入。

**理由**：
1. 同步钩子需要访问 `KubernetesNodeInventory`（含 systemUUID、capacity 等），这些数据在 ExtensionPoint 回调中不可用
2. Container 模块是 premium 内部模块，可以直接修改
3. 新增 ExtensionPoint 的成本高于直接修改，且使用方只有 PhysicalServer 适配

**修改范围**：

| 文件 | 修改内容 |
|------|---------|
| `ContainerEndpointBase.java` | 在 `syncNodesFromCluster()` 中添加 `syncPhysicalServer()` 调用 |
| `ContainerEndpointBase.java` | 在 sync-pod Flow 完成后添加 `refreshContainerCapacityAfterPodSync()` |
| `ContainerEndpointBase.java` | 在过期节点删除前添加 RoleVO Stale 标记 |
| `KubernetesNodeInventory.java` | 新增 `systemUUID`、`machineID`、`capacityCpu`、`capacityMemory`、`allocatableCpu`、`allocatableMemory` 字段 |
| `KubernetesNativeProvider.java` | 在 `listNodes()` 中填充新增字段 |
| `ContainerPhysicalServerRoleProvider.java` | 新增类，实现 `PhysicalServerRoleProvider` + `HostDeleteExtensionPoint` |
| `ContainerRoleInventory.java` | 新增类 |

### 5.3 CompatibilityBridge 与 EXTERNAL_READONLY 的交互

当 `CompatibilityBridge.shouldIntercept()` 启用后，现有的 `AllocateHostMsg` 会被转换为 `AllocateServerMsg`。EXTERNAL_READONLY 的 PhysicalServerVO 在 `SchedulingModeFilterFlow` 中被过滤掉，不会被分配。这确保了 Container 节点不会被误分配为 KVM Host 或 BM 实例。

**无需额外处理**：CompatibilityBridge 不需要特殊感知 Container 角色，因为过滤在 Flow 链中自然发生。

### 5.4 NativeHostVO 作为 HostVO 子类触发的 HostCapacityVO 相关操作

**风险**：某些全局逻辑（如 `RecalculateHostCapacityMsg`、`HostCapacityStruct` 初始化）可能扫描所有 HostVO 子类，包括 NativeHostVO，尝试操作 HostCapacityVO。

**现状**：NativeHostVO 没有对应的 HostCapacityVO 记录（Container 不通过 HostCapacityVO 管理容量）。因此上述操作对 NativeHost 是空操作（查不到 HostCapacityVO 记录）。

**结论**：不存在兼容性风险。PhysicalServerCapacityVO 是独立于 HostCapacityVO 的新表，两套容量体系互不干扰。

---

## 第 6 章：Open Questions 回答

### OQ-1: KVM + Container 混部场景下，managementIp 是否相同？

**回答**：不一定相同。

- KVM Host 的 managementIp 是 SSH 管理地址，由 `AddKVMHostMsg` 时管理员指定
- K8s Node 的 InternalIP 由 kubelet 自动检测或 `--node-ip` 参数指定

在多网卡物理机上，两者可能使用不同的网卡。

**解决方案**：
1. 依赖 serialNumber（systemUUID）匹配，这是跨角色唯一可靠的物理标识
2. 如果 serialNumber 不可用（虚拟化嵌套、白牌服务器），回退到 managementIp 匹配
3. 提供管理员手动关联 API（`APIAttachPhysicalServerRoleMsg`），作为最终兜底

### OQ-2: K8s 容量应取 Allocatable 还是 Capacity？

**回答**：两者都需要，但用途不同。

| K8s 字段 | 映射到 | 用途 |
|---------|-------|------|
| capacity | totalPhysicalCpu / totalPhysicalMemory | 物理总量展示，与 KVM 侧的物理容量数据对齐 |
| allocatable | totalCpu / totalMemory | K8s 可调度总量（用于计算 available） |

计算公式：
```
availableCpu = allocatable.cpu - Σ(pod.requests.cpu)
availableMemory = allocatable.memory - Σ(pod.requests.memory)
```

**不使用 capacity 作为可调度总量**的原因：capacity 包含了 K8s 自身的系统预留（kube-reserved + system-reserved + eviction-threshold），这些不应计入"可用"量。

### OQ-3: NativeHostVO 的 PostConnect 钩子是否存在且可扩展？

**回答**：不存在传统的 PostConnect 钩子。

NativeHostVO 虽然继承 HostVO，但 `NativeFactory.getHost()` 返回 `DummyNativeHost`，该实现：
- 对 `PingHostMsg` 直接返回成功
- 对其他消息返回空回复
- 不触发 `HostAfterConnectedExtensionPoint`

NativeHost 的创建/更新完全由 `ContainerEndpointBase.syncNodesFromCluster()` 驱动，没有 Connect/Reconnect 生命周期。

**解决方案**：直接在 `syncNodesFromCluster()` 方法中注入 PhysicalServer 同步调用（见第 2 章）。不依赖 ExtensionPoint。

### OQ-4: Container 场景下 PhysicalServerVO 的 OOB 字段如何处理？

**回答**：Container 场景下 OOB 相关字段（oobManagementType、oobAddress、oobPort、oobUsername、oobPassword）全部留空（null）。

- Container 节点的电源管理不通过 IPMI/Redfish，而是由 K8s 集群管理员在裸金属层面处理
- `APIPowerManagePhysicalServerMsg` 发送到无 OOB 凭据的 PhysicalServer 时，返回明确错误（如 `SysErrors.OPERATION_NOT_SUPPORTED`）

如果混部场景下同一台物理机的 KVM 角色带有 OOB 凭据，PhysicalServerVO 的 OOB 字段由 KVM RoleProvider 在 PostConnect 钩子中填充。Container 角色不修改这些字段。

### OQ-5: Container 场景下 PhysicalServerVO 的 architecture 字段如何填充？

**回答**：从 K8s Node SystemInfo 中获取。

K8s Node 的 `status.nodeInfo.architecture` 字段（如 "amd64"、"arm64"）需要映射到 ZStack 的架构标识：

| K8s architecture | ZStack architecture |
|-----------------|---------------------|
| amd64 | x86_64 |
| arm64 | aarch64 |
| 其他 | 原样存储 |

### OQ-6: Endpoint 删除时的级联处理

**回答**：删除 `ContainerManagementEndpointVO` 时，CASCADE 自动删除所有 NativeClusterVO 和 NativeHostVO。需要确保：

1. NativeHostVO 删除前，通过 `HostDeleteExtensionPoint` 将对应的 PhysicalServerRoleVO 标记为 Stale
2. 不自动删除 PhysicalServerVO（可能有其他角色仍在使用）
3. 如果 PhysicalServerVO 没有任何 Active 角色，管理员可手动删除或保留

---

## 附录 A：需要修改的文件清单

### 新增文件

| 文件路径 | 说明 |
|---------|------|
| `premium/plugin-premium/container/src/main/java/org/zstack/container/ContainerPhysicalServerRoleProvider.java` | PhysicalServerRoleProvider + HostDeleteExtensionPoint 实现 |
| `premium/plugin-premium/container/src/main/java/org/zstack/container/ContainerRoleInventory.java` | Container 角色 Inventory |

### 修改文件

| 文件路径 | 修改内容 |
|---------|---------|
| `container/src/.../ContainerEndpointBase.java` | syncNodesFromCluster() 中添加 syncPhysicalServer() 调用；过期节点处理中添加 RoleVO Stale 标记；sync-pod 完成后添加容量刷新 |
| `container/src/.../KubernetesNodeInventory.java` | 新增 systemUUID、machineID、capacityCpu、capacityMemory、allocatableCpu、allocatableMemory 字段 |
| `container/src/.../KubernetesNativeProvider.java` | listNodes() 中从 V1Node.status.nodeInfo 和 V1Node.status.capacity/allocatable 填充新增字段 |

### 不修改的文件

| 文件路径 | 原因 |
|---------|------|
| NativeHostVO.java | 约束：不改 NativeHostVO/HostVO |
| HostVO.java | 约束：不改 NativeHostVO/HostVO |
| NativeFactory.java | createHost() 仍然抛出 UnsupportedOperationException，不变 |
| DummyNativeHost.java | PingHostMsg 处理逻辑不变 |
| ContainerManagerImpl.java | start() 和周期性同步调度逻辑不变 |

---

## 附录 B：关键设计决策汇总

| # | 决策 | 理由 |
|---|------|------|
| CD-1 | syncPhysicalServer 直接嵌入 syncNodesFromCluster，不用 ExtensionPoint | 需要 KubernetesNodeInventory 的 systemUUID/capacity 数据，ExtensionPoint 回调中不可用 |
| CD-2 | serialNumber 取 K8s Node systemUUID，normalizeSerialNumber 去连字符转大写 | 与 KVM agent 的 product_serial 同源，normalizeSerialNumber 消除格式差异 |
| CD-3 | PhysicalServerCapacityVO 的 available 取 ZStack 可管控角色的值 | EXTERNAL_READONLY 的容量不参与分配引擎，放 available 字段会误导 |
| CD-4 | EXTERNAL_READONLY 下 decreaseCapacity/increaseCapacity 正常扣减/归还 | 容量消耗计入 available，确保混部场景不超配 |
| CD-5 | onPhysicalServerCreated/Deleted 为空操作 | Container 侧是同步方向的发起方，不需要反向通知 |
| CD-6 | 混部场景下两个角色容量各自独立，不做分割 | 两者容量来源不同（KVM agent vs K8s API），ZStack 不同时调度两者 |
| CD-7 | machineID 作为 serialNumber 的降级备选 | systemUUID 在虚拟化嵌套场景可能不可靠，machineID 更稳定（但重装 OS 后变化） |
| CD-8 | 容量刷新在 sync-node 和 sync-pod 两个 Flow 中分别处理 | sync-node 更新物理总量和 allocatable，sync-pod 更新已分配量 |
