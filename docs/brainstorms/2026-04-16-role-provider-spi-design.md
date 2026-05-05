# RoleProvider SPI + Hardware Discovery 完整设计（v3）

> **Date**: 2026-04-16
> **版本演进**:
> - v1: registerRole 与 addHost 脱节（错误设计）
> - v2: 加 createRoleEntity/deleteRoleEntity 解决 v1 问题
> - v3: 硬件发现上移到 PhysicalServer 层（独立 SPI），checkBeforeDetach → getWorkloadStatus，老 API 加 serverUuid 统一流程

---

## 1. 架构总览

```
┌─────────────────────────────────────────────────────────────┐
│  PhysicalServer 层                                           │
│  ├─ PhysicalServerManagerImpl (编排)                          │
│  ├─ PhysicalServerHardwareService (硬件发现)                  │
│  └─ AutoAssociator (三级降级匹配)                             │
│                                                              │
│  两个独立 SPI:                                                │
│    ① PhysicalServerRoleProvider (角色生命周期+工作负载查询)    │
│    ② HardwareDiscoveryStrategy  (硬件发现策略)                │
└─────────────────────────────────────────────────────────────┘
         ↑                          ↑
         │                          │
    角色模块实现              多方实现（OOB/角色模块/第三方）
    (KVM/BM2/Container)
```

**核心原则**:
1. **硬件属于物理机，不属于角色** — 硬件发现独立成 SPI
2. **统一流程，两种触发** — AddHost/AddChassis 流程加 `serverUuid` 参数，PS-first 和传统路径走同一套代码
3. **工作负载状态能力模型** — `getWorkloadStatus` 覆盖 detach/poweroff/maintenance 等所有破坏性操作的前置检查

---

## 2. 统一流程（解决 v2 的 Path 1/2 分叉）

```
【统一核心流程】:
  输入: AddHostMsg/AddChassisMsg + 可选 serverUuid
  步骤:
    1. if (serverUuid == null):
         serverUuid = AutoAssociator.findOrCreate(managementIp/oobAddress, zoneUuid)
       else:
         validate(serverUuid exists)
    2. 创建角色实体（HostVO/ChassisVO/NativeHostVO）
    3. 连接 / 初始化（KVM SSH、BM2 IPMI 探测）
    4. 创建 PhysicalServerRoleVO(serverUuid, roleUuid, roleType)
    5. 初始化/更新 PhysicalServerCapacityVO
    6. 填充 PhysicalServerVO 缺失字段（serialNumber 等）
    7. 触发异步硬件发现（PhysicalServerHardwareService.discoverHardware(serverUuid)）

【Path 1: PS-first】
  APIAttachPhysicalServerRoleMsg(serverUuid=X, roleType, clusterUuid, roleConfig)
    → RoleProvider.createRoleEntity(ctx with serverUuid=X)
    → 转发到 AddHostMsg/AddChassisMsg（带 serverUuid=X）
    → 走统一流程（步骤 1 因 serverUuid 已知而跳过 AutoAssociator）

【Path 2: 传统】
  APIAddKVMHostMsg(serverUuid=null 或 X)
    → HostManagerImpl
    → 走统一流程（步骤 1 按需触发 AutoAssociator）
```

**需要加 serverUuid 可选字段的老 API**:
- `APIAddKVMHostMsg` / `AddKVMHostMsg`
- `APIAddBareMetal2ChassisMsg` / `AddBareMetal2ChassisMsg`
- Container 在 `syncNodesFromCluster` 中内部调用 `AutoAssociator.findOrCreate`

---

## 3. PhysicalServerRoleProvider SPI

```java
/**
 * 角色模块实现此接口接入统一物理服务器管理。
 * 每种角色类型（KVM/BM2/Container）注册一个 Spring Bean。
 * 硬件发现不再归此 SPI，见 HardwareDiscoveryStrategy。
 */
public interface PhysicalServerRoleProvider {

    // ========== 身份声明 ==========

    ServerRoleType getRoleType();
    SchedulingMode getSchedulingMode();

    // ========== 实体生命周期（Path 1: PS 层编排调用） ==========

    /**
     * 创建角色实体。由 APIAttachPhysicalServerRoleMsg handler 调用。
     * 实现者内部转发到老的 Add*Msg API（带 serverUuid），不重复实现创建逻辑。
     *
     * @return 创建的角色实体 UUID（= HostVO.uuid / ChassisVO.uuid / NativeHostVO.uuid）
     */
    String createRoleEntity(CreateRoleEntityContext context);

    /**
     * 删除角色实体。由 APIDetachPhysicalServerRoleMsg handler 调用。
     * 实现者内部转发到老的 Delete*Msg API。
     */
    void deleteRoleEntity(String roleUuid);

    // ========== 工作负载查询 ==========

    /**
     * 查询该角色在指定物理服务器上消耗的资源。
     * 由 RecalculatePhysicalServerCapacityMsg 触发。
     */
    CapacityUsage getCapacityConsumption(String serverUuid, String roleUuid);

    /**
     * 查询角色上的工作负载状态。
     * 统一覆盖 detach/poweroff/maintenance/migration 等破坏性操作的前置检查。
     * 替代 v2 的 checkBeforeDetach（能力模型，不再仅为 detach 设计）。
     */
    RoleWorkloadStatus getWorkloadStatus(String serverUuid, String roleUuid);
}
```

### 3.1 CreateRoleEntityContext

```java
public class CreateRoleEntityContext {
    private String serverUuid;              // PhysicalServerVO UUID（必填）
    private String clusterUuid;             // 角色归属 Cluster
    private String managementIp;            // 从 PhysicalServerVO 读出
    private String zoneUuid;
    private String oobAddress;              // 可空
    private String oobUsername;             // 可空
    private String oobPassword;             // 可空
    private Map<String, String> roleConfig; // 角色特有配置
}
```

### 3.2 RoleWorkloadStatus（v3 新）

```java
public class RoleWorkloadStatus {
    private int activeWorkloadCount;
    private List<WorkloadRef> activeWorkloads;

    // 各破坏性操作的安全性声明（null=允许，非null=拒绝原因）
    private String detachBlockReason;
    private String powerOffBlockReason;
    private String powerResetBlockReason;
    private String maintenanceBlockReason;
    private String migrationBlockReason;
}

public class WorkloadRef {
    private String uuid;
    private String name;
    private String type;   // VM / BareMetalInstance / Pod
    private String state;  // Running / Starting / Migrating
}
```

**调用示例**:
```java
RoleWorkloadStatus status = provider.getWorkloadStatus(serverUuid, roleUuid);

// Detach 场景
if (!force && status.getDetachBlockReason() != null) {
    throw new OperationFailureException(operr("cannot detach: %s", status.getDetachBlockReason()));
}

// Phase 3 PowerOff 场景
if (!force && status.getPowerOffBlockReason() != null) {
    throw new OperationFailureException(operr("cannot power off: %s", status.getPowerOffBlockReason()));
}

// Phase 3 Maintenance 场景
if (status.getMaintenanceBlockReason() != null) {
    throw new OperationFailureException(operr("cannot enter maintenance: %s", status.getMaintenanceBlockReason()));
}
```

新增操作类型只需扩展 `RoleWorkloadStatus` 字段，不改 SPI 签名。

---

## 4. HardwareDiscoveryStrategy SPI（v3 新）

### 4.1 设计原则

**硬件属于物理机本身，不属于任何角色**。不管跑 KVM 还是 K8s，CPU 型号和内存容量都是同一个物理事实。

- PhysicalServer 层拥有 OOB 凭据（`oobAddress`/`oobUsername`/`oobPassword`），可独立发现硬件
- 角色模块的 agent（KVM SSH、K8s API）作为**补充数据源**（细节更丰富），不是唯一数据源
- 多策略按优先级融合，高优先级字段覆盖低优先级

### 4.2 接口定义

```java
/**
 * 硬件发现策略 SPI。任何模块可以注册（不限于角色模块）。
 * PhysicalServerHardwareService 按优先级选择和融合结果。
 */
public interface HardwareDiscoveryStrategy {

    /** 策略名，唯一标识，用于审计和日志 */
    String getName();

    /**
     * 优先级。高优先级的数据字段覆盖低优先级。
     * 建议值:
     *   Redfish       = 100  (最权威，标准化)
     *   IPMI FRU      = 90   (权威，但字段少)
     *   KVM agent     = 50   (通过 SSH 执行 dmidecode/lscpu 等)
     *   K8s nodeInfo  = 30   (最少，只有概要)
     */
    int getPriority();

    /**
     * 此策略是否能处理该服务器。
     */
    boolean canHandle(PhysicalServerVO server);

    /**
     * 发现硬件。返回该策略能获取的字段，其他留 null（不编造）。
     * 不抛异常：失败时返回空/部分结果。Service 层负责融合和审计。
     */
    UnifiedHardwareInfo discover(PhysicalServerVO server);
}
```

### 4.3 UnifiedHardwareInfo 强类型模型

```java
public class UnifiedHardwareInfo {
    private SystemInfo system;              // 系统级：厂商、型号、序列号、BIOS
    private List<CpuInfo> cpus;             // CPU 明细
    private List<MemoryModule> memory;      // 内存：每条 DIMM
    private List<StorageDevice> storage;    // 存储设备
    private List<NetworkInterface> nics;    // 网卡
    private List<GpuDevice> gpus;           // GPU
    private HealthStatus health;            // 健康状态
    private List<DiscoverySource> sources;  // 审计：哪些策略贡献了数据
    private Map<String, String> extraInfo;  // 逃生舱口，慎用
}

public class SystemInfo {
    private String manufacturer;
    private String model;
    private String serialNumber;
    private String biosVersion;
    private String biosReleaseDate;
    private String chassisType;       // Rack / Tower / Blade
}

public class CpuInfo {
    private String model;             // "Intel Xeon Gold 6248"
    private int sockets;
    private int coresPerSocket;
    private int threadsPerCore;
    private long frequencyMhz;
    private String architecture;      // x86_64 / aarch64
    private List<String> flags;       // vmx, avx, avx512...
}

public class MemoryModule {
    private long sizeBytes;
    private String type;              // DDR4 / DDR5
    private long speedMhz;
    private String manufacturer;
    private String partNumber;
    private String slotName;          // DIMM_A1
}

public class StorageDevice {
    private String model;
    private long sizeBytes;
    private DeviceType type;          // SSD / HDD / NVMe
    private String interfaceType;     // SATA / SAS / PCIe
    private SmartStatus smartStatus;
    private long smartPowerOnHours;
    private String serialNumber;
}

public class NetworkInterface {
    private String name;
    private String macAddress;
    private long speedMbps;
    private String driver;
    private boolean linkUp;
}

public class GpuDevice {
    private String model;
    private long memoryBytes;
    private String driverVersion;
    private String pciAddress;
}

public class HealthStatus {
    private Status overall;           // OK / Warning / Critical
    private List<HealthIssue> issues;
}

public class DiscoverySource {
    private String strategyName;
    private int priority;
    private Timestamp discoveredAt;
    private String status;            // SUCCESS / PARTIAL / FAILED
    private String errorMessage;
}
```

### 4.4 PhysicalServerHardwareService

```java
/**
 * PhysicalServerManagerImpl 提供的硬件发现服务。
 */
public class PhysicalServerHardwareService {

    @Autowired
    private PluginRegistry pluginRegistry;
    @Autowired
    private DatabaseFacade dbf;

    /**
     * 发现并持久化硬件信息。
     * 由 APIDiscoverPhysicalServerHardwareMsg 触发，
     * 或 PhysicalServer 首次连接时自动触发（Should Have）。
     */
    public UnifiedHardwareInfo discoverHardware(String serverUuid) {
        PhysicalServerVO server = dbf.findByUuid(serverUuid, PhysicalServerVO.class);

        // 1. 筛选可用策略，按优先级排序
        List<HardwareDiscoveryStrategy> strategies = pluginRegistry
                .getExtensionList(HardwareDiscoveryStrategy.class).stream()
                .filter(s -> s.canHandle(server))
                .sorted(Comparator.comparingInt(HardwareDiscoveryStrategy::getPriority))
                .collect(Collectors.toList());

        if (strategies.isEmpty()) {
            logger.warn(String.format(
                "no hardware discovery strategy available for PhysicalServer[uuid:%s]", serverUuid));
            return new UnifiedHardwareInfo();  // 空结果
        }

        // 2. 从低优先级到高优先级执行，高优先级覆盖低优先级
        UnifiedHardwareInfo merged = new UnifiedHardwareInfo();
        List<DiscoverySource> sources = new ArrayList<>();
        for (HardwareDiscoveryStrategy s : strategies) {
            DiscoverySource src = new DiscoverySource(s.getName(), s.getPriority(), now());
            try {
                UnifiedHardwareInfo partial = s.discover(server);
                mergeNonNull(merged, partial);
                src.setStatus("SUCCESS");
            } catch (Exception e) {
                src.setStatus("FAILED");
                src.setErrorMessage(e.getMessage());
                logger.warn(String.format("strategy %s failed: %s", s.getName(), e.getMessage()));
            }
            sources.add(src);
        }
        merged.setSources(sources);

        // 3. 持久化到 PhysicalServerHardwareDetailVO
        persistHardwareInfo(serverUuid, merged);

        return merged;
    }

    /** 读取已持久化的硬件信息，不触发发现 */
    public UnifiedHardwareInfo getHardware(String serverUuid) { ... }

    /** 融合：非 null 字段覆盖 */
    private void mergeNonNull(UnifiedHardwareInfo target, UnifiedHardwareInfo source) { ... }
}
```

### 4.5 内置策略

#### 4.5.1 RedfishDiscoveryStrategy（优先级 100）

```java
// 位置: plugin/physicalServer
public class RedfishDiscoveryStrategy implements HardwareDiscoveryStrategy {
    public String getName() { return "redfish"; }
    public int getPriority() { return 100; }

    public boolean canHandle(PhysicalServerVO server) {
        return server.getOobAddress() != null
            && OobManagementType.REDFISH == server.getOobManagementType();
    }

    public UnifiedHardwareInfo discover(PhysicalServerVO server) {
        RedfishClient client = new RedfishClient(
            server.getOobAddress(), server.getOobPort(),
            server.getOobUsername(), server.getOobPassword());
        // GET /redfish/v1/Systems/1 → 完整硬件清单
        // GET /redfish/v1/Chassis/1/Thermal, Power → 健康状态
        // Redfish 是标准化协议，填充 UnifiedHardwareInfo 绝大部分字段
        return buildFromRedfish(client.getSystem(), client.getChassis());
    }
}
```

#### 4.5.2 IpmiFruDiscoveryStrategy（优先级 90）

```java
// 位置: plugin/physicalServer
public class IpmiFruDiscoveryStrategy implements HardwareDiscoveryStrategy {
    public String getName() { return "ipmi-fru"; }
    public int getPriority() { return 90; }

    public boolean canHandle(PhysicalServerVO server) {
        return server.getOobAddress() != null
            && OobManagementType.IPMI == server.getOobManagementType();
    }

    public UnifiedHardwareInfo discover(PhysicalServerVO server) {
        // ipmitool -I lanplus -H {oobAddr} -U {user} -P {pass} fru print → SystemInfo
        // ipmitool sdr         → HealthStatus
        // IPMI FRU 字段有限：只能填 system.manufacturer/model/serialNumber/biosVersion
        UnifiedHardwareInfo info = new UnifiedHardwareInfo();
        info.setSystem(readIpmiFru(server));
        info.setHealth(readIpmiSdr(server));
        return info;
    }
}
```

#### 4.5.3 KvmAgentHardwareStrategy（优先级 50）

```java
// 位置: plugin/kvm（角色模块贡献，但不属于 RoleProvider SPI）
public class KvmAgentHardwareStrategy implements HardwareDiscoveryStrategy {
    public String getName() { return "kvm-agent-dmidecode"; }
    public int getPriority() { return 50; }

    public boolean canHandle(PhysicalServerVO server) {
        // 仅当该服务器有 KVM 角色时可用
        return Q.New(PhysicalServerRoleVO.class)
            .eq(PhysicalServerRoleVO_.serverUuid, server.getUuid())
            .eq(PhysicalServerRoleVO_.roleType, ServerRoleType.KVM_HOST.toString())
            .eq(PhysicalServerRoleVO_.roleStatus, PhysicalServerRoleStatus.Active)
            .isExists();
    }

    public UnifiedHardwareInfo discover(PhysicalServerVO server) {
        // 通过 RoleVO 反查 hostUuid
        String hostUuid = Q.New(PhysicalServerRoleVO.class)
            .eq(PhysicalServerRoleVO_.serverUuid, server.getUuid())
            .eq(PhysicalServerRoleVO_.roleType, ServerRoleType.KVM_HOST.toString())
            .select(PhysicalServerRoleVO_.roleUuid).findValue();

        // 通过 KVM agent 执行 dmidecode/lscpu/lsmem/lsblk
        // 优势: 能拿到 OOB 拿不到的细节——CPU flags、DIMM 明细、磁盘 SMART
        GetHardwareInfoCmd cmd = new GetHardwareInfoCmd();
        GetHardwareInfoResponse resp = kvmAgent.call(hostUuid, cmd);
        return buildFromKvmAgent(resp);
    }
}
```

#### 4.5.4 K8sNodeInfoStrategy（优先级 30）

```java
// 位置: premium/container
public class K8sNodeInfoStrategy implements HardwareDiscoveryStrategy {
    public String getName() { return "k8s-nodeinfo"; }
    public int getPriority() { return 30; }

    public boolean canHandle(PhysicalServerVO server) {
        return hasContainerRole(server);
    }

    public UnifiedHardwareInfo discover(PhysicalServerVO server) {
        // 从 K8s node API 获取 nodeInfo:
        //   architecture, osImage, kernelVersion, containerRuntimeVersion, systemUUID
        // 聚合后的 capacity:
        //   cpu, memory, ephemeral-storage（无 DIMM / 磁盘明细）
        // 只填充 system 和概要的 cpus/memory（单条聚合记录）
    }
}
```

---

## 5. KVM 无 OOB 场景兼容性分析

**场景**: 存量 KVM host 未配置 IPMI/Redfish，`PhysicalServerVO.oobAddress = null`。

### 5.1 角色注册

**Path 2（传统 AddHost + serverUuid=null）**:
```
APIAddKVMHostMsg(serverUuid=null, managementIp=X, ...)
  → HostManagerImpl 走统一流程
  → 步骤 1: serverUuid=null → AutoAssociator.findOrCreate()
      Tier 1 serialNumber: null（AddHost 时还拿不到）
      Tier 2 oobAddress:   null（未配置）
      Tier 3 managementIp: 命中已有 PhysicalServerVO → 关联
                           不命中 → 新建 PhysicalServerVO(oobAddress=null)
  → 步骤 2-7 正常
✅ 正常工作，无 OOB 不影响注册
```

### 5.2 容量管理

```
PhysicalServerCapacityVO 写入路径（KVM agent 上报）:
  ReportHostCapacityMessage → HostAllocatorManagerImpl → PhysicalServerCapacityVO
  ✅ 完全不依赖 OOB
  
KvmRoleProvider.getCapacityConsumption():
  SELECT sum(cpuNum), sum(memorySize) FROM VmInstanceVO WHERE hostUuid=X
  ✅ 纯 DB 查询，不依赖 OOB
```

### 5.3 工作负载查询

```
KvmRoleProvider.getWorkloadStatus():
  SELECT ... FROM VmInstanceVO WHERE hostUuid=X AND state IN (Running, ...)
  ✅ 纯 DB 查询，不依赖 OOB
```

### 5.4 硬件发现

**关键场景**——无 OOB 如何发现硬件？

```
PhysicalServerHardwareService.discoverHardware(serverUuid):
  遍历 HardwareDiscoveryStrategy:
    RedfishDiscoveryStrategy.canHandle: false（oobAddress=null）
    IpmiFruDiscoveryStrategy.canHandle: false（oobAddress=null）
    KvmAgentHardwareStrategy.canHandle: true（有 KVM 角色）✅
    K8sNodeInfoStrategy.canHandle: false

  → 仅 KvmAgentHardwareStrategy 执行
  → 通过 SSH + dmidecode 拿到硬件信息（比 OOB 还详细）
  ✅ 正常工作，数据来源是 KVM agent
```

**数据完整性对比**:

| 字段 | Redfish | IPMI FRU | KVM agent (dmidecode) |
|------|---------|----------|----------------------|
| manufacturer | ✅ | ✅ | ✅ |
| model | ✅ | ✅ | ✅ |
| serialNumber | ✅ | ✅ | ✅ |
| biosVersion | ✅ | ⚠️ 部分 | ✅ |
| CPU 型号/核数 | ✅ | ❌ | ✅ |
| CPU flags | ✅ | ❌ | ✅ |
| DIMM 明细 | ✅ | ❌ | ✅ |
| 磁盘 SMART | ✅ | ❌ | ✅（需安装 smartmontools） |
| 网卡速率 | ✅ | ❌ | ✅ |
| 健康状态 | ✅ | ✅ | ⚠️ 部分（通过 IPMI 或软件） |

**结论**: 无 OOB 的 KVM host，KvmAgentHardwareStrategy 能提供**比 IPMI FRU 更完整**的硬件信息（仅健康状态弱于 OOB）。完全兼容。

### 5.5 电源管理（Phase 3）

```
APIPowerOnPhysicalServerMsg → PhysicalServerManagerImpl
  检查 oobAddress:
    有 OOB → 通过 IPMI/Redfish 执行 chassis power on
    无 OOB → 返回 operr("no OOB credentials configured, power management unavailable")
```
Phase 1 PRD 已定义此行为（"无 OOB 凭据时返回明确错误"），Phase 3 实现时按此即可。

### 5.6 升级路径

```
老环境 KVM host 升级后:
  1. 存量 HostVO 通过数据迁移脚本自动关联 PhysicalServerVO（按 managementIp + zoneUuid）
  2. PhysicalServerVO.oobAddress = null（未配置）
  3. KvmAgentHardwareStrategy 自动工作（首次连接时触发硬件发现）
  4. 运维可后续通过 APIUpdatePhysicalServerMsg 补配 OOB，然后手动触发重发现
  5. 补配后 RedfishDiscoveryStrategy/IpmiFruDiscoveryStrategy 生效，补充健康状态字段

向后兼容: ✅
```

### 5.7 代码验证结果（2026-04-16）

通过实际代码检查确认 v3 设计完全兼容 KVM 无 OOB 场景，并发现若干利好：

**1. KVM agent 已有完整硬件发现机制（不是新建，是复用）**

`plugin/kvm/src/main/java/org/zstack/kvm/KVMHost.java:6148-6169` 的 `saveGeneralHostHardwareFacts()` 方法已经在采集：

```java
recordHardwareChangesAndCreateTag(HostSystemTags.SYSTEM_SERIAL_NUMBER, ...);
recordHardwareChangesAndCreateTag(HostSystemTags.SYSTEM_MANUFACTURER, ...);
recordHardwareChangesAndCreateTag(HostSystemTags.SYSTEM_PRODUCT_NAME, ...);
recordHardwareChangesAndCreateTag(HostSystemTags.SYSTEM_UUID, ...);
recordHardwareChangesAndCreateTag(HostSystemTags.HOST_CPU_MODEL_NAME, ...);
recordHardwareChangesAndCreateTag(HostSystemTags.CPU_GHZ, ...);
recordHardwareChangesAndCreateTag(HostSystemTags.CPU_PROCESSOR_NUM, ...);
recordHardwareChangesAndCreateTag(HostSystemTags.CPU_CACHE, ...);
recordHardwareChangesAndCreateTag(HostSystemTags.MEMORY_SLOTS_MAXIMUM, ...);
createTagWithoutNonValue(HostSystemTags.BIOS_VENDOR, ...);
createTagWithoutNonValue(HostSystemTags.BIOS_VERSION, ...);
createTagWithoutNonValue(HostSystemTags.BIOS_RELEASE_DATE, ...);
createTagWithoutNonValue(HostSystemTags.BMC_VERSION, ...);
createTagWithoutNonValue(HostSystemTags.POWER_SUPPLY_MODEL_NAME, ...);
createTagWithoutNonValue(HostSystemTags.POWER_SUPPLY_MANUFACTURER, ...);
createTagWithoutNonValue(HostSystemTags.IPMI_ADDRESS, ...);  // ← 意外利好
```

**→ KvmAgentHardwareStrategy 实现只需**: 复用已有的 `GetVirtualizerInfoCmd`，将 `HostFactResponse` 字段映射到 `UnifiedHardwareInfo`。不需要新增 agent 命令。

**2. AutoAssociator Tier 1 在 KVM 无 OOB 场景依然生效**

`HostSystemTags.SYSTEM_SERIAL_NUMBER` 由 KVM agent 通过 dmidecode 采集，**不依赖 OOB**。
→ KVM host 即使无 OOB 也能走 Tier 1 serialNumber 匹配，**不必降级到 Tier 3 managementIp**。

调用方在 PostHostConnectExtensionPoint 钩子中构建 RoleMatchContext 时，serialNumber 从 SystemTag 读取：
```java
String sn = HostSystemTags.SYSTEM_SERIAL_NUMBER.getTokenByResourceUuid(
    hostUuid, HostSystemTags.SYSTEM_SERIAL_NUMBER_TOKEN);
ctx.setSerialNumber(sn);  // 可能非空，即使无 OOB
```

**3. 意外发现：KVM agent 已采集 IPMI_ADDRESS**

`HostSystemTags.IPMI_ADDRESS` 由 KVM agent 自动探测（应是执行 `ipmitool lan print` 或读 `/sys/class/dmi/` 等）。
→ **Phase 3 机会**: 对于无 OOB 配置的 KVM host，可以用此 SystemTag 自动补配 `PhysicalServerVO.oobAddress`，无需运维手动配置。不需进入 Phase 2 scope，但设计上留出这条路径。

**4. AutoAssociator.java:64-74 的 Tier 3 managementIp + zoneUuid 匹配**

作为最终兜底，即使 serialNumber 和 oobAddress 都为空，也能按 managementIp 匹配已有 PhysicalServerVO，保证存量升级能自动关联。

**结论**: KVM 无 OOB 场景下：
- 硬件发现 = 复用现有 agent 机制，数据完整度高于 IPMI FRU
- 角色注册 = AutoAssociator 三级降级全部覆盖
- 容量管理 = 纯 DB 查询，本来就不依赖 OOB
- 电源管理（Phase 3）= 明确返回"未配置 OOB"错误（PRD 已定义此行为）

向后兼容性与平滑升级路径均已验证 ✅

---

## 6. 最终 SPI 签名

```diff
 public interface PhysicalServerRoleProvider {
     ServerRoleType getRoleType();
     SchedulingMode getSchedulingMode();
-    CapacityUsage getCapacityConsumption(String serverUuid);
-    void onPhysicalServerCreated(String serverUuid, String roleUuid);
-    void onPhysicalServerRoleDetaching(String serverUuid, String roleUuid);
-    String checkBeforeDetach(String serverUuid, String roleUuid);
+    String createRoleEntity(CreateRoleEntityContext context);
+    void deleteRoleEntity(String roleUuid);
+    CapacityUsage getCapacityConsumption(String serverUuid, String roleUuid);
+    RoleWorkloadStatus getWorkloadStatus(String serverUuid, String roleUuid);
 }

+ // v3 新增独立 SPI
+ public interface HardwareDiscoveryStrategy {
+     String getName();
+     int getPriority();
+     boolean canHandle(PhysicalServerVO server);
+     UnifiedHardwareInfo discover(PhysicalServerVO server);
+ }
```

**移除**:
- `onPhysicalServerCreated` / `onPhysicalServerRoleDetaching`: 方向错误
- `checkBeforeDetach`: 粒度太窄，替换为 `getWorkloadStatus`
- `collectHardwareInfo`: 硬件归 PS 层，移到独立 SPI

**新增**:
- `createRoleEntity` / `deleteRoleEntity`: 角色实体生命周期
- `getWorkloadStatus`: 能力模型，覆盖所有破坏性操作前置检查
- `HardwareDiscoveryStrategy`: 独立的硬件发现 SPI（多策略融合）

---

## 7. Phase 2 Scope 调整

| 项 | 原位置 | v3 位置 | 变化 |
|----|--------|---------|------|
| FR-033 统一硬件发现 | Phase 3 out-of-scope | **Phase 2 in-scope** | 上移：有了清晰设计，可与 Group A 并行实现 |
| `collectHardwareInfo` RoleProvider 方法 | Group E | 删除 | 替换为 HardwareDiscoveryStrategy |
| `checkBeforeDetach` | Group E | 替换为 `getWorkloadStatus` | SPI 升级 |
| AddHostMsg / AddChassisMsg 加 serverUuid | 隐含 | Group B 显式任务 | 老 API 扩展 |
| `HardwareDiscoveryStrategy` + 4 个内置策略 | — | **新增 Group H** | 4 个策略分别在 plugin/physicalServer、plugin/kvm、premium/container |

### Group H: 硬件发现（新增）

```
Group H: 硬件发现（依赖 A1，可与 C/D/E 并行）
├── H1: HardwareDiscoveryStrategy SPI + UnifiedHardwareInfo 强类型模型
├── H2: PhysicalServerHardwareService（编排 + 持久化）
├── H3: RedfishDiscoveryStrategy（plugin/physicalServer）
├── H4: IpmiFruDiscoveryStrategy（plugin/physicalServer）
├── H5: KvmAgentHardwareStrategy（plugin/kvm，包含 GetHardwareInfoCmd agent 命令）
├── H6: K8sNodeInfoStrategy（premium/container）
├── H7: APIDiscoverPhysicalServerHardwareMsg handler（不再是空壳 API）
└── H8: PhysicalServer 首次连接时自动触发硬件发现（Should Have）
```
