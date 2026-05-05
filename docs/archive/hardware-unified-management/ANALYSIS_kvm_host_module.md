# KVM Host Module Analysis Report

> Generated: 2026-01-29
> Author: KVM Host Domain Expert Agent
> Scope: plugin/kvm/, compute/, header/src/main/java/org/zstack/header/host/

---

## 1. Module Overview

### 1.1 Architecture Position

KVM Host 模块是 ZStack 云平台的核心计算资源管理组件，负责 KVM 虚拟化主机的完整生命周期管理。该模块采用插件化架构，通过继承 `HostVO` 基类实现 KVM 特定功能扩展。

**模块分布**:
- `plugin/kvm/` - KVM 插件实现，包含 KVMHostVO、KVMHostFactory、Agent 通信等
- `compute/` - 通用计算资源管理，包含主机分配器、容量管理、集群管理
- `header/src/main/java/org/zstack/header/host/` - Host 接口定义、VO 基类、API 消息

### 1.2 Key Components

| Component | Location | Responsibility |
|-----------|----------|----------------|
| KVMHostFactory | plugin/kvm/ | KVM Host 创建工厂，实现 HypervisorFactory 接口 |
| KVMHost | plugin/kvm/ | KVM Host 实例管理，处理主机操作消息 |
| HostAllocatorManager | compute/allocator/ | 主机分配策略管理 |
| HostCapacityUpdater | compute/allocator/ | 主机容量更新（悲观锁） |
| HostTracker | compute/host/ | 主机状态追踪 |

---

## 2. Core VO Structures

### 2.1 Entity Inheritance Hierarchy

```
ResourceVO (base, uuid)
    |
    v
HostAO (@MappedSuperclass)
    |   - zoneUuid, clusterUuid
    |   - name, description, managementIp
    |   - hypervisorType, architecture
    |   - state (HostState), status (HostStatus)
    |   - createDate, lastOpDate
    |
    v
HostVO (@Entity)
    |   - capacity: HostCapacityVO (OneToOne, EAGER)
    |   - ipmi: HostIpmiVO (OneToOne, EAGER)
    |   - hwMonitorStatus: HostHwMonitorStatusVO (OneToOne, EAGER)
    |
    v
KVMHostVO (@Entity, @PrimaryKeyJoinColumn)
        - username (SSH)
        - password (@EncryptColumn, @Convert)
        - port (SSH port)
        - osDistribution
        - osRelease
        - osVersion
```

### 2.2 HostAO - Abstract Mapped Superclass

**File**: `header/src/main/java/org/zstack/header/host/HostAO.java`

```java
@MappedSuperclass
public class HostAO extends ResourceVO {
    @Column
    @ForeignKey(parentEntityClass = ZoneEO.class, onDeleteAction = ReferenceOption.RESTRICT)
    private String zoneUuid;

    @Column
    @ForeignKey(parentEntityClass = ClusterEO.class, onDeleteAction = ReferenceOption.RESTRICT)
    private String clusterUuid;

    @Column
    private String name;

    @Column
    private String description;

    @Column
    private String managementIp;

    @Column
    private String hypervisorType;

    @Column
    private String architecture;

    @Column
    @Enumerated(EnumType.STRING)
    private HostState state;

    @Column
    @Enumerated(EnumType.STRING)
    private HostStatus status;

    @Column
    private Timestamp createDate;

    @Column
    private Timestamp lastOpDate;
}
```

**Key Observations**:
- 使用 `@MappedSuperclass` 实现字段继承，不生成独立表
- 通过 `@ForeignKey` 定义与 Zone/Cluster 的级联关系
- `state` 和 `status` 使用枚举类型存储

### 2.3 HostVO - Base Host Entity

**File**: `header/src/main/java/org/zstack/header/host/HostVO.java`

```java
@Entity
@Table
@EO(EOClazz = HostEO.class)
@AutoDeleteTag
@BaseResource
@EntityGraph(
    parents = {
        @EntityGraph.Neighbour(type = ClusterVO.class, myField = "clusterUuid", targetField = "uuid"),
        @EntityGraph.Neighbour(type = ZoneVO.class, myField = "zoneUuid", targetField = "uuid"),
    }
)
public class HostVO extends HostAO {
    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "uuid")
    @NoView
    private HostCapacityVO capacity;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "uuid")
    @NoView
    private HostIpmiVO ipmi;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "uuid")
    @NoView
    private HostHwMonitorStatusVO hwMonitorStatus;
}
```

**Key Observations**:
- 使用 `@EO` 关联软删除实体 `HostEO`
- `@EntityGraph` 定义父子关系用于级联框架
- 容量、IPMI、硬件监控状态通过 `@OneToOne` EAGER 加载

### 2.4 KVMHostVO - KVM-Specific Extension

**File**: `plugin/kvm/src/main/java/org/zstack/kvm/KVMHostVO.java`

```java
@Entity
@Table
@PrimaryKeyJoinColumn(name="uuid", referencedColumnName="uuid")
@EO(EOClazz = HostEO.class, needView = false)
public class KVMHostVO extends HostVO {
    @Column
    private String username;

    @EncryptColumn
    @Column
    @Convert(converter = PasswordConverter.class)
    private String password;

    @Column
    private Integer port;

    @Column
    private String osDistribution;

    @Column
    private String osRelease;

    @Column
    private String osVersion;
}
```

**Key Observations**:
- 使用 `@PrimaryKeyJoinColumn` 实现 JOINED 继承策略
- 密码字段使用 `@EncryptColumn` + `PasswordConverter` 加密存储
- OS 信息在 Agent 连接时自动采集

### 2.5 HostCapacityVO - Capacity Tracking

**File**: `header/src/main/java/org/zstack/header/allocator/HostCapacityVO.java`

```java
@Entity
@Table
@EntityGraph(
    parents = {
        @EntityGraph.Neighbour(type = HostVO.class, myField = "uuid", targetField = "uuid")
    }
)
public class HostCapacityVO {
    @Id
    @Column
    @ForeignKey(parentEntityClass = HostEO.class, onDeleteAction = ReferenceOption.CASCADE)
    private String uuid;

    @Column @Index
    private long totalMemory;

    @Column @Index
    private long totalCpu;

    @Column
    private int cpuNum;

    @Column
    private int cpuSockets;

    @Column
    private int cpuCoreNum;

    @Column @Index
    private long availableMemory;

    @Column @Index
    private long availableCpu;

    @Column @Index
    private long totalPhysicalMemory;

    @Column @Index
    private long availablePhysicalMemory;
}
```

**Capacity Fields Explanation**:
| Field | Description |
|-------|-------------|
| totalMemory | 逻辑总内存（可超分） |
| availableMemory | 逻辑可用内存（可超分） |
| totalPhysicalMemory | 物理总内存 |
| availablePhysicalMemory | 物理可用内存 |
| totalCpu | 逻辑总 CPU（可超分） |
| availableCpu | 逻辑可用 CPU（可超分） |

---

## 3. Host Lifecycle Management

### 3.1 Add Host Workflow

```
APIAddKVMHostMsg
    |
    v
KVMHostFactory.createHost()
    |
    v
Create KVMHostVO (state=Enabled, status=Connecting)
    |
    v
Deploy KVM Agent (Ansible)
    |
    v
Connect to Agent (/host/connect)
    |
    v
Collect Host Facts (/host/fact)
    |
    v
Report Capacity (/host/capacity)
    |
    v
Update status -> Connected
    |
    v
PostHostConnectExtensionPoint.postConnect()
```

**Key API Message**: `APIAddKVMHostMsg`

```java
// Extends APIAddHostMsg
public class APIAddKVMHostMsg extends APIAddHostMsg {
    @APIParam(maxLength = 255)
    private String username;

    @APIParam(maxLength = 255, password = true)
    private String password;

    @APIParam(numberRange = {1, 65535}, required = false)
    private Integer sshPort;
}
```

### 3.2 Reconnect Host Workflow

```
APIReconnectHostMsg / Auto-Reconnect
    |
    v
PreHostConnectExtensionPoint.preConnect()
    |
    v
Update status -> Connecting
    |
    v
SSH Connectivity Check
    |
    v
Re-deploy Agent (if needed)
    |
    v
Agent /host/connect
    |
    v
Sync VM States (/vm/vmsync)
    |
    v
Sync Volume States (/vm/volumesync)
    |
    v
Update status -> Connected
    |
    v
PostHostConnectExtensionPoint.postConnect()
```

### 3.3 Delete Host Workflow

```
APIDeleteHostMsg
    |
    v
Pre-delete Validation
    - Check running VMs
    - Check maintenance state
    |
    v
CascadeFramework.asyncCascade(OP_DELETE)
    - Delete dependent resources
    - Clean up network bridges
    - Disconnect storage
    |
    v
Delete HostCapacityVO (CASCADE)
    |
    v
Delete KVMHostVO -> HostVO -> HostEO (soft delete)
```

### 3.4 Maintenance Mode Workflow

```
APIChangeHostStateMsg(state=PreMaintenance)
    |
    v
Set state -> PreMaintenance
    |
    v
HostMaintenancePolicyManager.getPolicy()
    - MigrateVm: Live migrate all VMs
    - StopVm: Stop all VMs
    - None: No action
    |
    v
Execute policy for each VM
    |
    v
All VMs evacuated?
    |
    +--> Yes --> Set state -> Maintenance
    |
    +--> No --> Retry / Timeout
```

**Maintenance States**:
- `PreMaintenance`: 准备进入维护模式，正在迁移工作负载
- `Maintenance`: 已进入维护模式，不接受新的 VM 调度

---

## 4. Agent Deployment Mechanism

### 4.1 Ansible-based Deployment

**Agent Playbook**: `kvm.py` (defined in KVMConstant.ANSIBLE_PLAYBOOK_NAME)

```
KVMHostFactory.connectAgent()
    |
    v
AnsibleRunner.run(kvm.py)
    |
    - Install dependencies (libvirt, qemu-kvm)
    - Deploy kvmagent Python package
    - Configure systemd service
    - Start kvmagent daemon
    |
    v
Wait for Agent Ready (/host/echo)
```

### 4.2 Agent Communication Paths

**Key Agent Endpoints** (from KVMConstant.java):

| Path | Purpose |
|------|---------|
| `/host/connect` | 初始连接，报告主机信息 |
| `/host/ping` | 心跳检测 |
| `/host/capacity` | 容量报告 |
| `/host/fact` | 主机信息采集 |
| `/host/echo` | Agent 存活检测 |
| `/vm/vmsync` | VM 状态同步 |
| `/vm/start` | 启动 VM |
| `/vm/stop` | 停止 VM |
| `/vm/migrate` | VM 迁移 |

### 4.3 HTTP Call Message Types

```java
// 异步 HTTP 调用
KVMHostAsyncHttpCallMsg
    - path: String
    - command: Object (JSON serialized)
    - noStatusCheck: boolean

// 同步 HTTP 调用
KVMHostSyncHttpCallMsg
    - path: String
    - command: Object
```

---

## 5. Capacity Management

### 5.1 Pessimistic Locking Strategy

**File**: `compute/src/main/java/org/zstack/compute/allocator/HostCapacityUpdater.java`

```java
@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class HostCapacityUpdater {

    private boolean lockCapacity() {
        // 使用 PESSIMISTIC_WRITE 锁定容量记录
        capacityVO = dbf.getEntityManager().find(
            HostCapacityVO.class,
            hostUuid,
            LockModeType.PESSIMISTIC_WRITE
        );
        // ...
    }

    @Transactional
    @DeadlockAutoRestart  // 死锁自动重试
    public boolean run(HostCapacityUpdaterRunnable runnable) {
        if (!lockCapacity()) {
            return false;
        }
        HostCapacityVO cap = runnable.call(capacityVO);
        if (cap != null) {
            merge();
            return true;
        }
        return false;
    }
}
```

**Key Points**:
- 使用 `PESSIMISTIC_WRITE` 实现行级锁
- `@DeadlockAutoRestart` 注解处理死锁重试
- 在事务中更新容量，保证一致性

### 5.2 Over-Provisioning

**File**: `compute/src/main/java/org/zstack/compute/allocator/HostCapacityOverProvisioningManagerImpl.java`

```java
public class HostCapacityOverProvisioningManagerImpl
    implements HostCapacityOverProvisioningManager {

    private double globalMemoryRatio = 1;
    private ConcurrentHashMap<String, Double> hostMemoryRatio = new ConcurrentHashMap<>();

    @Override
    public long calculateMemoryByRatio(String hostUuid, long capacity) {
        double ratio = getMemoryRatio(hostUuid);
        return Math.round(capacity / ratio);
    }

    @Override
    public long calculateHostAvailableMemoryByRatio(String hostUuid, long capacity) {
        double ratio = getMemoryRatio(hostUuid);
        return Math.round(capacity * ratio);
    }
}
```

**CPU Over-Provisioning**: `HostCpuOverProvisioningManagerImpl.java`

**Over-Provisioning Calculation**:
```
Logical Memory = Physical Memory * Memory Ratio
Logical CPU = Physical CPU * CPU Ratio

Example:
- Physical Memory: 64GB, Ratio: 1.5
- Logical Memory: 64GB * 1.5 = 96GB (可分配)
```

### 5.3 Capacity Reserve

**File**: `compute/src/main/java/org/zstack/compute/allocator/HostCapacityReserveManager.java`

用于预留一定量的主机资源，防止系统资源耗尽。

---

## 6. State Machine Definition

### 6.1 HostState - Administrative State

**File**: `header/src/main/java/org/zstack/header/host/HostState.java`

```java
public enum HostState {
    Enabled,
    Disabled,
    PreMaintenance,
    Maintenance;
}
```

**State Transitions**:

```
                    +-----------------+
                    |                 |
            +------>|    Enabled      |<------+
            |       |                 |       |
            |       +--------+--------+       |
            |                |                |
       enable()         disable()        enable()
            |                |                |
            |                v                |
            |       +--------+--------+       |
            |       |                 |       |
            +-------+    Disabled     +-------+
                    |                 |
                    +--------+--------+
                             |
                        preMaintain()
                             |
                             v
                    +--------+--------+
                    |                 |
                    | PreMaintenance  |
                    |                 |
                    +--------+--------+
                             |
                        maintain()
                             |
                             v
                    +--------+--------+
                    |                 |
                    |  Maintenance    |
                    |                 |
                    +-----------------+
```

**State Event Mapping**:
| Current State | Event | Next State |
|---------------|-------|------------|
| Enabled | disable | Disabled |
| Enabled | preMaintain | PreMaintenance |
| Disabled | enable | Enabled |
| Disabled | preMaintain | PreMaintenance |
| PreMaintenance | maintain | Maintenance |
| PreMaintenance | enable | Enabled |
| PreMaintenance | disable | Disabled |
| Maintenance | enable | Enabled |
| Maintenance | disable | Disabled |

### 6.2 HostStatus - Connection Status

**File**: `header/src/main/java/org/zstack/header/host/HostStatus.java`

```java
public enum HostStatus {
    Connecting,
    Connected,
    Disconnected;
}
```

**Status Transitions**:

```
                    +-----------------+
                    |                 |
            +------>|   Connecting    |<------+
            |       |                 |       |
            |       +--------+--------+       |
            |                |                |
       connecting()     connected()      connecting()
            |                |                |
            |                v                |
            |       +--------+--------+       |
            |       |                 |       |
            +-------+   Connected     +-------+
                    |                 |
                    +--------+--------+
                             |
                       disconnected()
                             |
                             v
                    +--------+--------+
                    |                 |
                    |  Disconnected   |
                    |                 |
                    +-----------------+
```

---

## 7. API Message Catalog

### 7.1 Host Header APIs (Generic)

| API Message | Description |
|-------------|-------------|
| `APIAddHostMsg` | 添加主机（抽象基类） |
| `APIDeleteHostMsg` | 删除主机 |
| `APIUpdateHostMsg` | 更新主机信息 |
| `APIQueryHostMsg` | 查询主机 |
| `APIChangeHostStateMsg` | 变更主机状态 |
| `APIReconnectHostMsg` | 重连主机 |
| `APIGetHypervisorTypesMsg` | 获取虚拟化类型列表 |
| `APIUpdateHostIpmiMsg` | 更新 IPMI 信息 |
| `APIPowerOnHostMsg` | IPMI 开机 |
| `APIShutdownHostMsg` | IPMI 关机 |
| `APIPowerResetHostMsg` | IPMI 重置电源 |
| `APIGetHostPowerStatusMsg` | 获取电源状态 |
| `APIGetHostWebSshUrlMsg` | 获取 Web SSH URL |
| `APIGetHostTaskMsg` | 获取主机任务 |
| `APICreateHostNetworkServiceTypeMsg` | 创建主机网络服务类型 |
| `APIDeleteHostNetworkServiceTypeMsg` | 删除主机网络服务类型 |
| `APIUpdateHostNetworkServiceTypeMsg` | 更新主机网络服务类型 |

### 7.2 KVM Plugin APIs

| API Message | Description |
|-------------|-------------|
| `APIAddKVMHostMsg` | 添加 KVM 主机 |
| `APIUpdateKVMHostMsg` | 更新 KVM 主机（SSH 凭据等） |
| `APIKvmRunShellMsg` | 在 KVM 主机上执行 Shell 命令 |
| `APIQueryHostOsCategoryMsg` | 查询主机 OS 类别 |
| `APIQueryKvmHypervisorInfoMsg` | 查询 KVM Hypervisor 信息 |
| `APICreateVmUserDefinedXmlHookScriptMsg` | 创建 VM XML Hook 脚本 |
| `APIUpdateVmUserDefinedXmlHookScriptMsg` | 更新 VM XML Hook 脚本 |
| `APIExpungeVmUserDefinedXmlHookScriptMsg` | 删除 VM XML Hook 脚本 |
| `APIQueryVmUserDefinedXmlHookScriptMsg` | 查询 VM XML Hook 脚本 |

---

## 8. Mapping to Header Plan v1.2

### 8.1 Unified Hardware VO Mapping

| Current KVM Structure | Proposed Unified Structure | Notes |
|----------------------|---------------------------|-------|
| HostAO | UnifiedHardwareAO | 抽象基类映射 |
| HostVO | ComputeNodeVO | 计算节点统一抽象 |
| KVMHostVO | KVMComputeNodeVO | KVM 特化实现 |
| HostCapacityVO | ComputeCapacityVO | 容量信息统一 |
| HostIpmiVO | HardwareManagementVO | IPMI/BMC 统一管理 |
| HostStatus | NodeConnectionStatus | 连接状态统一 |
| HostState | NodeAdminState | 管理状态统一 |

### 8.2 Interface Compatibility Analysis

**Preserved Interfaces** (MUST maintain backward compatibility):
1. `HypervisorFactory` - KVM Host 创建工厂接口
2. `Host` - 主机操作接口
3. `HostAllocatorStrategy` - 主机分配策略
4. `HostCapacityOverProvisioningManager` - 超分管理

**Extension Points for Unified Management**:
1. `AddHostMessage` - 可扩展添加主机消息
2. `HostInventory` - 可扩展主机清单返回
3. `HostFactory` - 可扩展主机工厂

### 8.3 Bidirectional Sync Requirements

```
KVMHostVO <---> UnifiedHardwareVO
    |
    v
HardwareReferenceVO
    - kvmHostUuid: String
    - unifiedHardwareUuid: String
    - syncDirection: ENUM (KVM_TO_UNIFIED, UNIFIED_TO_KVM, BIDIRECTIONAL)
    - lastSyncTime: Timestamp
```

**Sync Triggers**:
1. KVMHostVO 创建 -> 自动创建 UnifiedHardwareVO
2. KVMHostVO 更新 -> 同步更新 UnifiedHardwareVO
3. KVMHostVO 删除 -> 标记 UnifiedHardwareVO 为 orphaned
4. UnifiedHardwareVO 更新 -> 反向同步到 KVMHostVO（如适用）

### 8.4 Migration Strategy

**Phase 1**: 创建 UnifiedHardwareVO 并建立映射关系
**Phase 2**: 实现双向同步扩展点
**Phase 3**: 新 API 支持统一硬件视图
**Phase 4**: 逐步迁移上层应用使用新接口

---

## 9. Key Extension Points

### 9.1 Host Lifecycle Extensions

```java
// 连接前扩展点
PreHostConnectExtensionPoint.preConnect(HostInventory host)

// 连接后扩展点
PostHostConnectExtensionPoint.postConnect(HostInventory host)

// 主机状态变更扩展点
HostChangeStateExtensionPoint.preChangeState(...)
HostChangeStateExtensionPoint.afterChangeState(...)
```

### 9.2 Host Allocator Extensions

```java
// 主机分配器过滤扩展
KVMHostAllocatorFilterExtensionPoint.filterHostCandidates(...)

// 自定义分配策略
HostAllocatorStrategyFactory.getHostAllocatorStrategy(...)
```

---

## 10. Summary

### 10.1 Architectural Strengths

1. **Clean Inheritance**: HostAO -> HostVO -> KVMHostVO 继承链清晰
2. **Plugin Isolation**: KVM 特定逻辑完全隔离在 plugin/kvm/
3. **Extension Points**: 丰富的扩展点支持定制化
4. **Capacity Management**: 悲观锁 + 超分机制保证资源管理可靠性
5. **State Machine**: 清晰的状态转换定义

### 10.2 Integration Considerations for Unified Hardware

1. **Preserve API Compatibility**: 现有 APIAddKVMHostMsg 等 API 必须保持兼容
2. **Leverage Existing Patterns**: 复用现有的 Factory/Extension 模式
3. **Capacity Unification**: 需要统一容量模型以支持异构硬件
4. **State Model Alignment**: 统一 State/Status 枚举定义

### 10.3 Files Referenced

- `H:\ZStack\zstack\header\src\main\java\org\zstack\header\host\HostAO.java`
- `H:\ZStack\zstack\header\src\main\java\org\zstack\header\host\HostVO.java`
- `H:\ZStack\zstack\header\src\main\java\org\zstack\header\host\HostState.java`
- `H:\ZStack\zstack\header\src\main\java\org\zstack\header\host\HostStatus.java`
- `H:\ZStack\zstack\header\src\main\java\org\zstack\header\allocator\HostCapacityVO.java`
- `H:\ZStack\zstack\plugin\kvm\src\main\java\org\zstack\kvm\KVMHostVO.java`
- `H:\ZStack\zstack\plugin\kvm\src\main\java\org\zstack\kvm\KVMConstant.java`
- `H:\ZStack\zstack\compute\src\main\java\org\zstack\compute\allocator\HostCapacityUpdater.java`
- `H:\ZStack\zstack\compute\src\main\java\org\zstack\compute\allocator\HostCapacityOverProvisioningManagerImpl.java`

---

*End of Report*
