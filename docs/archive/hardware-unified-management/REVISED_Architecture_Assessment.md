# 硬件统一管理 - 修订版架构评估 (Code-Driven)

**版本**: v4.1
**日期**: 2026-02-27
**方法**: 基于代码实证的架构分析（非理论推导）
**变更 v4.1**: 恢复 ServerCapacityVO 统一分配（双模式: 共享扣减 + 独占清零）；移除 EO 层

---

## 0. 代码实证摘要

本文档基于四个维度的代码级分析，所有结论附带文件路径和行号引用。

| 分析维度 | 关键数据 |
|---------|---------|
| HostVO 使用范围 | **641 个文件**引用, **19 张 FK 表**, **6 个子类** |
| 资源分配链路 | **15+ 处**发送 AllocateHostMsg, **17+ 个** AllocatorFlow |
| 四模块工作流对比 | 4 种完全不同的创建/连接/删除机制 |
| Cluster 语义 | **662 个文件**引用, `clusterUuid` FK RESTRICT（不可空） |

---

## 1. 原设计中需要修正的假设

### 1.1 ❌ "ServerPool 替代 Cluster 作为物理边界"

**代码事实**:
- `HostAO.clusterUuid` 是 FK RESTRICT（`header/src/main/java/org/zstack/header/host/HostAO.java`）
- L2Network 通过 `L2NetworkClusterRefVO` 绑定到 Cluster（CASCADE 删除）
- PrimaryStorage 通过 `PrimaryStorageClusterRefVO` 绑定到 Cluster（CASCADE 删除）
- 分配流程中 `DesignatedHostAllocatorFlow` 通过 `clusterUuid` 过滤主机
- 662 个文件引用 Cluster，premium 模块占 49%

**修正**: ServerPool 只能作为**可选的物理分组概念**与 Cluster 并存，不能替代 Cluster 在 L2/Storage 绑定中的核心角色。

### 1.2 ⚠️ "ServerCapacityVO 统一所有角色的容量管理"

**代码事实**:
- **只有 KVM** 目前使用容量管理（`ReportHostCapacityMessage` 在 `KVMHost.java:1788`）
- **Baremetal V1**: 无容量概念，状态为 HWInfoUnknown → Available → Allocated（独占分配）
- **Baremetal2**: 无 `ReportHostCapacityMessage`，使用状态机（Available/Allocated）做独占分配
- **Container**: 无容量追踪，NativeHostVO 的 HostCapacityVO 从未被写入

**修正 (v4.1)**: 保留 ServerCapacityVO 作为统一容量账本，但需要**适配不同角色的分配语义**：
- **共享角色** (KVM_HOST, NATIVE_HOST): 按需扣减 CPU/Memory，支持超分比
- **独占角色** (BARE_METAL, BARE_METAL2): 整机分配，reserve 时将 available 清零

这正是统一分配的核心价值——用同一套悲观锁机制处理共享和独占两种模式。
现有 HostCapacityVO 在 Phase 3 通过 HostAllocatorCompatibilityLayer 桥接。

### 1.3 ❌ "RoleAdapter 统一创建/连接接口"

**代码事实**:
- KVM: `KVMHostFactory.createHost()` → 直接 persist → `connectHook()` ShareFlowChain + Ansible + KVMHostConnectExtensionPoint
- BM1: `BaremetalChassisManagerImpl.handle()` → ChainTask 内直接 persist → IPMI power reset + PXE → HTTP 异步回调
- BM2: `BareMetal2ChassisManagerImpl.addBareMetal2Chassis()` → ChainTask + Factory persist → iPXE + ping tracker
- Container: `NativeFactory.createHost()` → **throws UnsupportedOperationException** → 节点由周期同步创建

**修正**: `RoleAdapter.createRole()` 方法签名无法覆盖这些差异。Container 根本不支持创建。应将 RoleAdapter 简化为**数据同步接口**，而非操作接口。

### 1.4 ❌ "PhysicalServerVO 插入 HostVO 继承链"

**代码事实**:
- HostVO 有 6 个子类（KVMHostVO, NativeHostVO, ESXHostVO, XDragonHostVO, V2VConversionHostVO, SimulatorHostVO）
- 19 张表 FK 到 HostEO（全部 CASCADE 或 SET_NULL）
- 645+ 文件引用 HostInventory
- BaremetalChassisVO（V1/V2）不在 HostVO 继承链中

**修正**: PhysicalServerVO 必须是**独立实体**，通过 PhysicalServerRoleVO 引用表与现有 VO 关联。

---

## 2. 修订后的架构设计

### 2.1 核心原则（保留）

以下原则经验证后依然成立：
- ✅ 物理与逻辑解耦（PhysicalServer 与 Host/Chassis 分离）
- ✅ 向下兼容（现有 API 100% 不变）
- ✅ 通过引用表关联新旧结构
- ✅ 瀑布型同步（PhysicalServer → 角色资源）

### 2.2 核心原则（修订）

| 原设计 | 修订 | 原因 |
|--------|------|------|
| ServerPool 替代 Cluster | ServerPool 与 Cluster **并存** | Cluster FK RESTRICT, 662 文件引用 |
| ServerCapacityVO 统一分配 | **保留**, 用双模式 (共享扣减 + 独占清零) 适配所有角色 | 统一分配是核心设计目标 |
| RoleAdapter 统一操作接口 | RoleAdapter 简化为**数据同步 + 元数据提供**接口 | 4 模块操作机制完全不同 |
| 四大能力接口全部在 header 层 | 保留 PowerManageable/HardwareDiscoverable，移除 AgentDeployable/ClusterBindable | Agent 部署和 Cluster 绑定是角色层实现细节 |

### 2.3 修订后的架构层次

```
┌────────────────────────────────────────────────────────────────┐
│              Physical Server Layer (新增)                       │
│  PhysicalServerVO (物理资源唯一标识)                              │
│  ServerPoolVO (可选物理分组，与 Cluster 并存)                     │
│  ServerHardwareInfoVO (硬件发现信息)                              │
│  ServerCapacityVO (统一容量账本, 悲观锁分配)                      │
│  PhysicalServerRoleVO (角色映射引用表)                            │
│  AllocateServerMsg → ServerAllocatorChain → ServerCapacityUpdater│
│  作用: 统一视图 + 带外管理 + 硬件发现 + 统一资源分配               │
├────────────────────────────────────────────────────────────────┤
│              Role Resource Layer (现有，逐步桥接)                │
│  KVMHostVO (HostVO 继承链, Phase 3 通过兼容层桥接分配)            │
│  BaremetalChassisVO (独立实体, 独占模式 → ServerCapacity 清零)    │
│  BareMetal2ChassisVO (独立实体, 独占模式 → ServerCapacity 清零)   │
│  NativeHostVO (HostVO 继承链, 共享模式 → ServerCapacity 扣减)     │
│  作用: 业务操作、状态管理                                         │
├────────────────────────────────────────────────────────────────┤
│              Consumer Layer (现有，不变)                         │
│  VmInstanceVO / BaremetalInstanceVO / PodVO                     │
└────────────────────────────────────────────────────────────────┘
```

**关键变化**: PhysicalServer 层承担**统一资源分配**职责。
共享角色（KVM/Container）按需扣减，独占角色（Baremetal）整机清零。
Phase 3 通过 HostAllocatorCompatibilityLayer 拦截现有 AllocateHostMsg 转发至新引擎。

---

## 3. 修订后的数据模型

### 3.1 PhysicalServerVO (保留，微调)

```java
@Entity
@Table(name = "PhysicalServerVO")
@BaseResource
public class PhysicalServerVO extends ResourceVO {
    @Column private String name;
    @Column private String zoneUuid;          // FK → ZoneEO (RESTRICT)
    @Column private String serverPoolUuid;    // FK → ServerPoolVO (SET NULL), 可选
    @Column private String managementIp;
    @Column private String architecture;      // x86_64, aarch64
    @Column private String serialNumber;      // 全局唯一硬件标识
    @Column private String manufacturer;
    @Column private String model;

    // 带外管理 (OOB)
    @Column private String oobManagementType; // IPMI, REDFISH, NONE
    @Column private String oobAddress;
    @Column private Integer oobPort;
    @Column private String oobUsername;
    @Column private String oobPassword;       // @Password 加密

    // 状态
    @Column private String state;             // Enabled, Disabled, Maintenance
    @Column private String status;            // Connected, Disconnected, Unknown

    // 关联
    @OneToMany @JoinColumn(name = "serverUuid")
    private Set<PhysicalServerRoleVO> roles;

    @OneToOne @JoinColumn(name = "uuid")
    private ServerHardwareInfoVO hardwareInfo;
}
```

### 3.2 ServerPoolVO (简化，不再关联 L2)

```java
@Entity
@Table(name = "ServerPoolVO")
public class ServerPoolVO extends ResourceVO {
    @Column private String name;
    @Column private String description;
    @Column private String zoneUuid;          // FK → ZoneEO
    @Column private String state;             // Enabled, Disabled
}
```

**关键修订**: 移除 `ServerPoolL2RefVO`。L2 网络绑定继续走 Cluster 路径。ServerPool 只是物理服务器的逻辑分组（如机房、机架），不参与网络/存储拓扑。

### 3.3 PhysicalServerRoleVO (保留)

```java
@Entity
@Table(name = "PhysicalServerRoleVO")
public class PhysicalServerRoleVO {
    @Id @GeneratedValue private long id;
    @Column private String serverUuid;        // FK → PhysicalServerVO (CASCADE)
    @Column private String roleType;          // KVM_HOST, BARE_METAL, BARE_METAL2, NATIVE_HOST
    @Column private String roleUuid;          // 指向 KVMHostVO.uuid / BaremetalChassisVO.uuid / etc.
    @Column private String syncStatus;        // InSync, OutOfSync
    @Column private Timestamp lastSyncTime;
    // 唯一约束: (serverUuid, roleType)
    // 唯一约束: (roleUuid)
}
```

### 3.4 VO 调整汇总

| VO | 原设计 | v4.1 决定 | 原因 |
|----|--------|---------|------|
| ServerCapacityVO | 统一容量账本 | **保留**, 双模式 (共享扣减/独占清零) | 统一分配是核心目标 |
| ServerPoolL2RefVO | 池-L2网络关联 | **移除** | L2 绑定走 Cluster 路径 |
| RoleAdapter 接口 | 统一操作接口 | **简化为 SPI** | 操作差异太大，无法统一 |

### 3.5 保留但简化的接口

```java
/**
 * 角色数据提供者 SPI - 用于 PhysicalServer 层聚合角色信息
 * 注意: 这不是操作接口，只是数据查询接口
 */
public interface PhysicalServerRoleProvider {
    /** 角色类型标识 */
    String getRoleType();

    /** 从角色资源反向查找/创建 PhysicalServer 关联 */
    PhysicalServerRoleVO resolvePhysicalServer(String roleUuid);

    /** 获取角色的容量消耗信息（用于聚合视图） */
    ServerCapacitySummary getCapacitySummary(String roleUuid);

    /** 获取角色的连接状态 */
    String getRoleStatus(String roleUuid);
}
```

---

## 4. 核心场景验证

### 4.1 场景: 注册新裸金属 → 绑定 KVM 角色

```
用户: APIRegisterPhysicalServerMsg (oobAddress, zoneUuid)
  → 创建 PhysicalServerVO (status=Unknown)
  → 通过 IPMI 发现硬件 → 创建 ServerHardwareInfoVO
  → PhysicalServerVO.status = Connected

用户: APIAddKVMHostMsg (clusterUuid, managementIp, username, password)
  → [现有逻辑不变] 创建 KVMHostVO, deploy agent, connect
  → [新增钩子] PostHostConnectExtensionPoint:
    → 通过 managementIp 匹配 PhysicalServerVO
    → 创建 PhysicalServerRoleVO (roleType=KVM_HOST, roleUuid=hostUuid)
```

**关键**: 现有 APIAddKVMHostMsg 流程完全不变，只在 PostConnect 扩展点添加关联逻辑。

### 4.2 场景: 查询物理服务器统一视图

```
用户: APIQueryPhysicalServerMsg
  → 返回 PhysicalServerInventory:
    - 基础信息 (SN, 厂商, 型号, OOB 地址)
    - 硬件信息 (CPU/内存/磁盘/网卡)
    - 角色列表:
      [{roleType: "KVM_HOST", roleUuid: "host-xxx", clusterName: "kvm-cluster-1", status: "Connected"}]
    - 容量汇总 (通过 PhysicalServerRoleProvider.getCapacitySummary() 聚合)
```

### 4.3 场景: 容器-VM 混部

```
前提: 物理服务器已有 KVM_HOST 角色

用户: 通过 K8s 将该节点加入容器集群
  → Container 周期同步发现新 NativeHostVO
  → [新增钩子] 同步后扩展点:
    → 通过 managementIp 匹配 PhysicalServerVO
    → 创建 PhysicalServerRoleVO (roleType=NATIVE_HOST, roleUuid=nativeHostUuid)

查询: APIQueryPhysicalServerMsg
  → 角色列表:
    [{roleType: "KVM_HOST", ...}, {roleType: "NATIVE_HOST", ...}]
  → 容量汇总: KVM 已用 + Container 已用 (只读聚合视图)
```

**注意**: 容量分配依然由各角色自己负责。PhysicalServer 层只提供聚合视图，不参与分配决策。混部场景下的资源超卖风险由运维通过 ServerPool 配额或手动管控。

---

## 5. 实施影响评估

### 5.1 需要新增的代码

| 组件 | 路径 | 文件数 |
|------|------|--------|
| PhysicalServerVO + AO + EO | `header/src/main/java/org/zstack/header/server/` | ~8 |
| ServerPoolVO + Inventory | 同上 | ~4 |
| PhysicalServerRoleVO | 同上 | ~3 |
| ServerHardwareInfoVO | 同上 | ~3 |
| API 消息 (Register/Query/Update) | 同上 | ~10 |
| PhysicalServerManagerImpl | `core/src/main/java/org/zstack/core/server/` 或新模块 | ~5 |
| PhysicalServerRoleProvider SPI | `header/src/main/java/org/zstack/header/server/` | ~2 |
| KVM RoleProvider 实现 | `plugin/kvm/` 或 `compute/` | ~2 |
| BM1 RoleProvider 实现 | `premium/baremetal/` | ~2 |
| BM2 RoleProvider 实现 | `premium/baremetal2/` | ~2 |
| Container RoleProvider 实现 | `premium/plugin-premium/container/` | ~2 |
| DB 迁移脚本 | `conf/db/upgrade/` | 1 |
| Spring 配置 | `conf/springConfigXml/` | 1-2 |
| **合计** | | **~45 文件** |

### 5.2 需要修改的现有代码

| 变更 | 文件数 | 风险 |
|------|--------|------|
| KVM PostConnect 扩展 (关联 PhysicalServer) | 1-2 | 低 |
| Container 同步后扩展 (关联 PhysicalServer) | 1-2 | 低 |
| BM1/BM2 创建后钩子 (关联 PhysicalServer) | 2-4 | 低 |
| **合计修改** | **~8 文件** | **低风险** |

### 5.3 不需要修改的代码

| 组件 | 文件数 | 为什么不需要改 |
|------|--------|--------------|
| HostVO 继承链 | 641 | PhysicalServerVO 是独立实体 |
| HostCapacityVO + 分配器 | 59 | 容量管理留在角色层 |
| Cluster 体系 | 662 | ServerPool 不替代 Cluster |
| 现有 API (AddKVMHostMsg 等) | 所有 | 100% 兼容 |

---

## 6. 与原设计的对比总结

| 维度 | 原设计 (CH1-CH6 / Confluence) | 修订设计 v4.1 (本文档) |
|------|------|------|
| PhysicalServerVO 定位 | 物理资源的唯一管理方，负责分配 | 物理资源唯一标识 + 统一分配 (无 EO 层) |
| ServerPool | 替代 Cluster 作为物理边界 | 可选物理分组，与 Cluster 并存 |
| ServerCapacityVO | 唯一容量账本，取代 HostCapacityVO | **保留**, 双模式: 共享扣减 + 独占清零 |
| RoleAdapter | 统一操作接口 (create/delete/sync) | 简化为数据查询 SPI |
| 能力接口 | 4 个 (Power/Discovery/Agent/Cluster) | 2 个 (Power/Discovery)，其余留在角色层 |
| 分配流程 | AllocateServerMsg 全新引擎 | AllocateServerMsg + Phase 3 兼容层桥接 AllocateHostMsg |
| 修改范围 | 未评估（理论设计） | ~50 新增 + ~8 修改（代码级评估） |
| 兼容风险 | 未验证 | 零风险（不修改任何现有接口/FK） |
| 迁移策略 | 三阶段(双写→迁移→清理) | Phase 1 纯增量 → Phase 3 兼容层切换 → Phase 4 清理 |

---

## 7. 下一步

1. **Phase 1 (数据模型 + 分配接口)**: 创建 VO/Inventory/API 定义 + ServerCapacityVO + AllocateServerMsg (header 层) → 见 `PHASE1_Detailed_Design.md`
2. **Phase 2 (核心服务)**: 实现 PhysicalServerManagerImpl + ServerAllocatorManager + ServerCapacityUpdater
3. **Phase 3 (兼容层桥接)**: 实现 HostAllocatorCompatibilityLayer，拦截 AllocateHostMsg → AllocateServerMsg
4. **Phase 4 (角色关联)**: 在各模块添加 PostConnect/PostCreate 钩子，自动创建 PhysicalServerRoleVO
5. **Phase 5 (监控集成)**: OOB 监控 + 硬件健康状态聚合

Phase 1-2 可独立上线验证。Phase 3 是核心收割，切换分配引擎。
