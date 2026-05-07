# 统一硬件管理 — API 调用流程（QA 测试计划用）

**版本**: v1.0
**日期**: 2026-03-19
**用途**: 提供完整的 API 调用链路和场景覆盖，供 QA 编写测试计划

---

## 1. 资源依赖关系图

```
Zone
 └── ServerPool (物理分组, 运维标签)
      ├── Cluster 关联 (ClusterVO.serverPoolUuid 指向同 Zone ServerPool)
      └── PhysicalServer (物理服务器)
           ├── PhysicalServerRole (角色映射, 1:N)
           │    ├── KVM_HOST -> HostVO/KVMHostVO
           │    ├── BAREMETAL_V2 -> BareMetal2ChassisVO
           │    └── CONTAINER_HOST -> NativeHostVO
           ├── PhysicalServerCapacity (容量, 1:1)
           ├── PhysicalServerHardwareInfo (硬件概要, 1:1)
           └── PhysicalServerHardwareDetail (硬件明细, 1:N)

Cluster ──多对一──► ServerPool (通过 ClusterVO.serverPoolUuid；业务层级跟随 ServerPool)

ProvisionNetwork ──N:N──► Cluster (通过 ProvisionNetworkClusterRefVO)
```

**创建顺序（前置依赖）**：Zone → ServerPool（默认池按 `serverPool.defaultCreationPolicy` 自动创建，或用户手动创建自定义池）→ PhysicalServer → Role（自动）
**关联顺序**：ServerPool ← Cluster（可选关联，写 ClusterVO.serverPoolUuid）、ProvisionNetwork ← Cluster
**级联顺序**：统一硬件主链路是 Zone → ServerPool → PhysicalServer → PhysicalServer* 子表；Cluster 通过 serverPoolUuid 关联到 ServerPool，不直接级联 PhysicalServer。

---

## 2. API 清单

### 2.1 ServerPool 管理

| API | 方法 | 路径 | 说明 |
|-----|------|------|------|
| APICreateZoneMsg | POST | /zones | 创建 Zone；默认池是否同步创建由 `serverPool.defaultCreationPolicy` 控制 |
| APICreateServerPoolMsg | POST | /server-pools | 创建物理分组（非默认池，`isDefault=false`） |
| APIDeleteServerPoolMsg | DELETE | /server-pools/{uuid} | 删除（需先移除所有 PhysicalServer） |
| APIUpdateServerPoolMsg | PUT | /server-pools/{uuid}/actions | 更新名称/位置/描述 |
| APIQueryServerPoolMsg | GET | /server-pools | 查询（标准 Query 语法） |
| APIChangeClusterServerPoolMsg | PUT | /clusters/{clusterUuid}/server-pool/actions | 修改 Cluster 关联的 ServerPool；serverPoolUuid 为空表示解除关联 |

### 2.2 PhysicalServer 管理

| API | 方法 | 路径 | 说明 |
|-----|------|------|------|
| APIAddPhysicalServerMsg | POST | /physical-servers | 添加物理服务器 |
| APIDeletePhysicalServerMsg | DELETE | /physical-servers/{uuid} | 删除（需先无 Active 角色） |
| APIUpdatePhysicalServerMsg | PUT | /physical-servers/{uuid}/actions | 更新信息 |
| APIQueryPhysicalServerMsg | GET | /physical-servers | 统一查询（跨角色） |
| APIChangePhysicalServerStateMsg | PUT | /physical-servers/{uuid}/actions | 变更 State（Enabled/Disabled/Maintenance） |
| APIPowerManagePhysicalServerMsg | PUT | /physical-servers/{uuid}/actions | 电源管理（PowerOn/Off/Reset/GetStatus） |
| APIDiscoverPhysicalServerHardwareMsg | PUT | /physical-servers/{uuid}/actions | 触发硬件发现 |

### 2.3 ProvisionNetwork 管理

| API | 方法 | 路径 | 说明 |
|-----|------|------|------|
| APICreateProvisionNetworkMsg | POST | /provision-networks | 创建装机网络 |
| APIDeleteProvisionNetworkMsg | DELETE | /provision-networks/{uuid} | 删除装机网络 |
| APIQueryProvisionNetworkMsg | GET | /provision-networks | 查询 |
| APIAttachProvisionNetworkToClusterMsg | POST | /provision-networks/{networkUuid}/clusters/{clusterUuid} | 关联 Cluster |
| APIDetachProvisionNetworkFromClusterMsg | DELETE | /provision-networks/{networkUuid}/clusters/{clusterUuid} | 解除关联 |

### 2.4 现有 API（不变，通过 VIEW/钩子自动联动）

| API | 行为变化 | 联动 |
|-----|---------|------|
| APIAddKVMHostMsg | 不变 | PostConnect 钩子自动创建 PhysicalServerVO + RoleVO |
| APIDeleteHostMsg | 不变 | HostDeleteExtensionPoint 更新 RoleVO 状态为 Stale |
| AddBareMetal2IpmiChassisMsg | 不变 | 硬件发现成功后自动创建 PhysicalServerVO + RoleVO |
| DeleteBareMetal2ChassisMsg | 不变 | 删除钩子更新 RoleVO 状态 |
| AllocateHostMsg | 特性开关控制 | 开启时走两阶段薄适配（ServerAllocatorChain + HostAllocatorChain） |

---

## 3. 核心测试场景和调用链路

### 场景 1：从零开始管理一台物理服务器

**前置条件**：已有 Zone（zone-001），`serverPool.defaultCreationPolicy` 使用默认值 `OnClusterCreate`

```
步骤 1: 查询 Zone 默认 ServerPool
  GET /server-pools?conditions=zoneUuid=zone-001&conditions=isDefault=true
  → 返回空列表，给用户在创建 Cluster 前手动创建自定义 ServerPool 的窗口期

步骤 2: 创建 Cluster
  POST /clusters
  {
    "name": "cluster-001",
    "zoneUuid": "zone-001",
    "hypervisorType": "KVM"
  }
  → 自动创建并绑定默认 ServerPool

步骤 3: 查询 Zone 默认 ServerPool
  GET /server-pools?conditions=zoneUuid=zone-001&conditions=isDefault=true
  → 返回唯一 ServerPoolInventory {
      name: "default-pool",
      zoneUuid: "zone-001",
      state: "Enabled",
      isDefault: true
    }
  → ClusterVO.serverPoolUuid 指向该默认 ServerPool

步骤 4: 创建自定义 ServerPool（可选）
  POST /server-pools
  {
    "name": "机房A-3楼",
    "zoneUuid": "zone-001",
    "physicalLocation": "Building A, Floor 3, Rack 1-10",
    "networkTopology": "ToR Switch x2, 10GbE"
  }
  → 返回 ServerPoolInventory { uuid: "pool-001", isDefault: false }

步骤 5: 添加物理服务器
  POST /physical-servers
  {
    "zoneUuid": "zone-001",
    "poolUuid": "pool-001",
    "name": "server-rack1-u01",
    "managementIp": "10.0.0.50",
    "oobManagementType": "IPMI",
    "oobAddress": "192.168.1.100",
    "oobPort": 623,
    "oobUsername": "admin",
    "oobPassword": "password"
  }
  → 返回 PhysicalServerInventory {
      uuid: "ps-001",
      state: "Enabled",
      status: "Connecting",
      powerStatus: "Unknown"
    }

步骤 6: 电源状态查询
  PUT /physical-servers/ps-001/actions
  { "action": "GetPowerStatus" }
  → 返回 { powerStatus: "PowerOn" }

步骤 7: 触发硬件发现
  PUT /physical-servers/ps-001/actions  (discoverHardware)
  → 异步任务，完成后：
    PhysicalServerVO.status = "Connected"
    PhysicalServerVO.serialNumber = "ABC123XYZ"
    PhysicalServerHardwareInfoVO 创建（cpuModel, cpuCores, totalMemory...）

步骤 8: 查询物理服务器
  GET /physical-servers?conditions=poolUuid=pool-001
  → 返回列表，含硬件信息和容量

分支: 新方式自定义窗口期
  1. 创建 Zone 后先创建自定义 ServerPool pool-001
  2. 再创建 Cluster
  → 不自动创建 default-pool
  → ClusterVO.serverPoolUuid 为空，用户通过 APIChangeClusterServerPoolMsg 手动绑定到 pool-001
```

### 场景 2：KVM Host 自动关联

**前置条件**：场景 1 完成，物理服务器 ps-001 已注册

```
步骤 1: 添加 KVM Host（现有 API，不变）
  POST /hosts/kvm
  {
    "clusterUuid": "cluster-001",
    "managementIp": "10.0.0.50",    ← 和 ps-001 的 managementIp 相同
    "username": "root",
    "password": "password"
  }
  → KVM Host 连接成功
  → PostConnect 钩子自动执行：
    1. 获取 serialNumber（SYSTEM_SERIAL_NUMBER SystemTag）
    2. 调用 PhysicalServerManager.registerRole():
       a. matchExistingServer() → 通过 serialNumber 匹配到 ps-001
       b. checkSchedulingModeExclusion() → INTERNAL_SHARED，无冲突
       c. 创建 PhysicalServerRoleVO {
            serverUuid: "ps-001",
            roleUuid: "host-001",   ← HostVO.uuid
            roleType: KVM_HOST,
            schedulingMode: INTERNAL_SHARED,
            roleStatus: Active
          }
    3. 初始化 PhysicalServerCapacityVO（从 HostCapacityVO VIEW 读取）

步骤 2: 验证关联
  GET /physical-servers/ps-001
  → PhysicalServerInventory {
      uuid: "ps-001",
      roles: [
        { roleType: "KVM_HOST", roleUuid: "host-001", status: "Active" }
      ],
      capacity: { totalCpu: 64, availableCpu: 64, totalMemory: 128GB, ... }
    }

步骤 3: 验证 HostCapacityVO VIEW
  GET /hosts?conditions=uuid=host-001&fields=capacity
  → HostInventory 中 capacity 字段正常（通过 VIEW 从 PhysicalServerCapacityVO 读取）
```

### 场景 3：BM2 Chassis 自动关联

**前置条件**：场景 1 完成

```
步骤 1: 添加 BM2 Chassis（现有 API，不变）
  AddBareMetal2IpmiChassisMsg
  {
    "clusterUuid": "cluster-002",
    "ipmiAddress": "192.168.1.100",    ← 和 ps-001 的 oobAddress 相同
    "ipmiPort": 623,
    "ipmiUsername": "admin",
    "ipmiPassword": "password"
  }
  → BM2 Chassis 创建
  → 硬件发现成功后：
    1. 调用 PhysicalServerManager.registerRole():
       a. matchExistingServer() → 通过 oobAddress 匹配到 ps-001
       b. checkSchedulingModeExclusion() → INTERNAL_EXCLUSIVE vs 已有 INTERNAL_SHARED(KVM)
       c. ❌ 抛出 OperationFailureException：互斥冲突
    2. 如果物理机没有 KVM 角色：
       → 创建 PhysicalServerRoleVO { roleType: BAREMETAL_V2, schedulingMode: INTERNAL_EXCLUSIVE }

步骤 2: 验证互斥
  同一物理机不能同时有 KVM_HOST(INTERNAL_SHARED) + BAREMETAL_V2(INTERNAL_EXCLUSIVE)
  → API 应返回明确错误信息
```

### 场景 4：Container 混部（KVM + Container 共存）

**前置条件**：场景 2 完成（ps-001 已有 KVM 角色）

```
步骤 1: 添加 Container NativeHost（通过 K8s endpoint 同步）
  → Container 模块 syncNodesFromCluster() 发现 NativeHost
  → 通过 serialNumber（K8s Node systemUUID）匹配到 ps-001
  → checkSchedulingModeExclusion():
    EXTERNAL_READONLY vs INTERNAL_SHARED(KVM) → ✅ 允许共存
  → 创建 PhysicalServerRoleVO { roleType: CONTAINER_HOST, schedulingMode: EXTERNAL_READONLY }

步骤 2: 验证容量
  GET /physical-servers/ps-001
  → roles: [
      { roleType: "KVM_HOST", status: "Active" },
      { roleType: "CONTAINER_HOST", status: "Active" }
    ]
  → capacity: {
      totalCpu: 64,
      availableCpu: 4,        ← 64 - 40(KVM VM) - 20(K8s Pod) = 4
      totalMemory: 128GB,
      availableMemory: 8GB    ← 128 - 100(KVM) - 20(K8s) = 8
    }

步骤 3: 验证不超配
  创建 VM 需要 8 CPU → AllocateHostMsg
  → HostCapacityAllocatorFlow 查 HostCapacityVO(VIEW)
  → availableCpu = 4 < 8 → 分配失败
  → 正确行为：Container 消耗已计入，不会超配
```

### 场景 5：容量分配全链路（VM 创建）

**前置条件**：KVM Host 已连接，特性开关开启

```
步骤 1: 创建 VM
  APICreateVmInstanceMsg { cpuNum: 4, memorySize: 8GB, ... }

步骤 2: 分配链路（两阶段薄适配）
  AllocateHostMsg
    → CompatibilityBridge.shouldIntercept() → true（特性开关开启）
    → 阶段1: ServerAllocatorChain
      ├── StatusFilterFlow: 过滤 state=Enabled, status=Connected
      ├── ClusterFilterFlow: 过滤指定 Cluster
      ├── CapacityFilterFlow: 过滤 availableCpu >= 4, availableMemory >= 8GB
      └── 输出: [ps-001, ps-003] （候选 PhysicalServer）
    → 映射: ps-001 → host-001, ps-003 → host-003 （通过 RoleVO）
    → 注入: spec.candidateHostUuids = [host-001, host-003]
    → 阶段2: 现有 HostAllocatorChain（在缩小的候选集上执行）
      ├── AttachedL2NetworkAllocatorFlow: L2 网络可达性
      ├── HostPrimaryStorageAllocatorFlow: 主存储可用性
      ├── TagAllocatorFlow: SystemTag 匹配
      ├── FilterFlow: KVM 插件过滤
      └── 输出: [host-001]
    → HostSortorChain: 排序
    → reserveCapacity: 悲观锁扣减 PhysicalServerCapacityVO
      UPDATE PhysicalServerCapacityVO
      SET availableCpu = availableCpu - 4,
          availableMemory = availableMemory - 8GB
      WHERE uuid = 'ps-001'
    → 返回 HostInventory(host-001)

步骤 3: 验证容量扣减
  GET /physical-servers/ps-001
  → capacity.availableCpu 减少 4
  → capacity.availableMemory 减少 8GB

  GET /hosts/host-001 （通过 VIEW）
  → capacity 数据与 PhysicalServer 一致（同一张表）
```

### 场景 6：电源管理

```
步骤 1: 关机
  PUT /physical-servers/ps-001/actions
  { "action": "PowerOff" }
  → PhysicalServerManager 查找 OOB 凭据
  → 前置检查：遍历所有 Active 角色调用 prePhysicalServerPowerOff(ps-001)
    - KVM RoleProvider：无运行中 VM → 允许
  → 通过 IPMI 发送关机指令
  → 更新 PhysicalServerVO.powerStatus = "PowerOff"
  → 返回成功

步骤 2: 开机
  PUT /physical-servers/ps-001/actions
  { "action": "PowerOn" }
  → 无前置检查（PowerOn 不需要）
  → IPMI 发送开机指令
  → powerStatus = "PowerOn"

步骤 3: 多角色时强制关机（前置检查拒绝场景）
  ps-001 有 KVM + Container 两个角色，KVM 上有运行中 VM
  PUT /physical-servers/ps-001/actions
  { "action": "PowerOff" }
  → 前置检查：
    - KVM RoleProvider.prePhysicalServerPowerOff() → 有运行中 VM → 返回 ErrorCode
  → ❌ 拒绝操作，返回错误："KVM host has running VMs, use force=true to override"

步骤 4: 多角色时 force=true 强制关机
  PUT /physical-servers/ps-001/actions
  { "action": "PowerOff", "force": true }
  → 跳过所有前置检查（不调用 prePhysicalServerPowerOff）
  → 直接执行 IPMI PowerOff
  → powerStatus = "PowerOff"

步骤 5: 无 OOB 凭据时的电源管理
  对未配置 oobAddress 的 PhysicalServer 执行 PowerOff
  → ❌ 立即拒绝，返回错误："OOB credentials not configured"
  → 不进行前置检查
```

### 场景 7：物理服务器删除链路

```
步骤 1: 尝试删除有 Active 角色的物理服务器
  DELETE /physical-servers/ps-001
  → 检查: roles 中有 Active 的 KVM_HOST
  → ❌ 拒绝删除，返回错误："请先删除关联的 KVM Host"

步骤 2: 先删除 KVM Host
  DELETE /hosts/host-001（现有 API）
  → HostDeleteExtensionPoint 触发:
    PhysicalServerRoleVO.roleStatus = Stale
  → HostCapacityVO(VIEW) 中 host-001 的记录消失（VIEW WHERE 条件不再匹配）

步骤 3: 再删除物理服务器
  DELETE /physical-servers/ps-001
  → 检查: 无 Active 角色（只有 Stale）
  → ✅ 删除 PhysicalServerVO
  → CASCADE 删除: PhysicalServerRoleVO, PhysicalServerCapacityVO, PhysicalServerHardwareInfoVO, PhysicalServerHardwareDetailVO

步骤 4: 验证回滚能力
  删除 PhysicalServer* 表后，现有 Host/Chassis 系统正常运行
```

### 场景 8：ServerPool 生命周期

```
步骤 1: 创建 ServerPool
  POST /server-pools { name: "Pool-A", zoneUuid: "zone-001" }
  → 返回 pool-001

步骤 2: 关联 Cluster
  PUT /clusters/cluster-001/server-pool/actions
  { "serverPoolUuid": "pool-001" }
  → 更新 ClusterVO.serverPoolUuid = pool-001

步骤 3: 尝试关联第二个 Pool 到同一 Cluster
  PUT /clusters/cluster-001/server-pool/actions
  { "serverPoolUuid": "pool-002" }
  → ✅ 覆盖 ClusterVO.serverPoolUuid = pool-002
  → 一个 Cluster 当前只指向一个 ServerPool

步骤 4: 多个 Cluster 关联同一 Pool
  PUT /clusters/cluster-002/server-pool/actions
  { "serverPoolUuid": "pool-001" }
  → ✅ 允许（多对一）

步骤 5: 跨 Zone 关联校验
  cluster-001.zoneUuid != pool-003.zoneUuid
  PUT /clusters/cluster-001/server-pool/actions
  { "serverPoolUuid": "pool-003" }
  → ❌ 拒绝，Cluster 和 ServerPool 必须属于同一 Zone

步骤 6: 删除有 PhysicalServer 的 ServerPool
  DELETE /server-pools/pool-001
  → 检查: pool-001 下有 ps-001
  → ❌ 拒绝删除（RESTRICT）

步骤 7: 移除 PhysicalServer 后删除
  更新 ps-001 的 poolUuid 到其他 Pool
  DELETE /server-pools/pool-001
  → ✅ 成功
  → 清空所有指向 pool-001 的 ClusterVO.serverPoolUuid

步骤 8: 删除 Zone 的完整级联
  Zone zone-001 下存在：
    ServerPool pool-001
      -> Cluster cluster-001 (serverPoolUuid = pool-001)
      -> PhysicalServer ps-001
    PhysicalServer ps-001 -> Role/Capacity/HardwareInfo/HardwareDetail
  DELETE /zones/zone-001
  → ✅ 删除 ServerPoolVO(pool-001)
  → ✅ 删除 PhysicalServerVO(ps-001)
  → ✅ 删除 PhysicalServerRoleVO/PhysicalServerCapacityVO/PhysicalServerHardwareInfoVO/PhysicalServerHardwareDetailVO
  → ✅ 删除/清理 ClusterVO(cluster-001) 的 serverPoolUuid 引用，避免 ServerPool 删除路径残留引用
```

### 场景 9：HostCapacityVO VIEW 透明性验证

```
目的：验证 HostCapacityVO 作为 VIEW 对现有代码完全透明

步骤 1: KVM Host 首次上报容量（INSERT 路径）
  HostAllocatorManagerImpl.handle(ReportHostCapacityMessage)
  → 改造后: INSERT INTO PhysicalServerCapacityVO
  → 验证: SELECT FROM HostCapacityVO(VIEW) 可查到数据

步骤 2: VM 创建扣减容量（UPDATE 路径）
  创建 VM → HostCapacityUpdater.run()
  → 改造后: SELECT FOR UPDATE PhysicalServerCapacityVO → merge
  → 验证: HostCapacityVO(VIEW) 中 availableCpu 减少

步骤 3: CPU 超分比变更（JPQL 路径）
  修改全局 CPU 超分比
  → 改造后: 触发 RecalculatePhysicalServerCapacityMsg（不再裸 JPQL）
  → 验证: PhysicalServerCapacityVO.totalCpu 更新
  → 验证: HostCapacityVO(VIEW) 中 totalCpu 同步更新

步骤 4: 删除 KVM Host 后容量清理
  DELETE HostVO
  → PhysicalServerRoleVO.roleStatus = Stale
  → HostCapacityVO(VIEW) 中该记录消失（WHERE roleType='KVM_HOST' AND roleStatus='Active'）
  → PhysicalServerCapacityVO 记录仍在（供其他角色使用或最终随 PhysicalServer 删除而 CASCADE）
```

### 场景 10：存量数据迁移验证

```
前置条件：老版本已有 KVM Host、BM2 Chassis、Container NativeHost

步骤 1: 执行升级 SQL
  Flyway 脚本自动执行:
  → ALTER TABLE HostCapacityVO RENAME TO PhysicalServerCapacityVO
  → CREATE VIEW HostCapacityVO AS SELECT ... FROM PhysicalServerCapacityVO JOIN ...
  → INSERT INTO PhysicalServerVO (为每个 HostVO 生成)
  → INSERT INTO PhysicalServerRoleVO (关联映射)
  → INSERT INTO ResourceVO (资源注册)
  → INSERT INTO AccountResourceRefVO (权限注册)

步骤 2: 验证现有 API 不变
  GET /hosts → 正常返回，capacity 字段正常（通过 VIEW）
  POST /vm-instances → 创建 VM 正常，分配链路不变
  GET /baremetal2/chassis → 正常返回

步骤 3: 验证新 API 可用
  GET /physical-servers → 返回所有物理服务器（含 KVM/BM2/Container）
  → 每台物理服务器含 roles 列表

步骤 4: 验证幂等
  再执行一次迁移脚本 → INSERT IGNORE，无报错，无重复数据

步骤 5: 验证回滚
  DROP VIEW HostCapacityVO
  ALTER TABLE PhysicalServerCapacityVO RENAME TO HostCapacityVO
  (重建 FK)
  → 现有系统恢复正常，新的 PhysicalServer* 表可安全删除
```

---

## 4. 互斥规则矩阵（registerRole 测试用例）

| 已有角色 \ 新角色 | KVM_HOST (SHARED) | BAREMETAL_V2 (EXCLUSIVE) | CONTAINER_HOST (READONLY) |
|----------------|:-:|:-:|:-:|
| 无角色 | ✅ | ✅ | ✅ |
| KVM_HOST (SHARED) | ❌ 同类型 | ❌ 互斥 | ✅ |
| BAREMETAL_V2 (EXCLUSIVE) | ❌ 互斥 | ❌ 同类型 | ✅ |
| CONTAINER_HOST (READONLY) | ✅ | ✅ | ❌ 同类型 |
| KVM + CONTAINER | ❌ 同类型 | ❌ 互斥 | ❌ 同类型 |

---

## 5. 特性开关测试

| GlobalConfig | 行为 |
|-------------|------|
| `server.allocator.enabled = false` | AllocateHostMsg 完全走旧 HostAllocatorChain，不经过 ServerAllocatorChain |
| `server.allocator.enabled = true` | AllocateHostMsg 走两阶段薄适配 |
| `server.allocator.roleType.KVM_HOST = false` | KVM 分配走旧路径，其他角色走新路径 |
| `serverPool.defaultCreationPolicy = OnClusterCreate` | 默认策略；创建 Zone 不立即创建默认池，首次创建 Cluster 时若 Zone 下无任何 ServerPool，则创建 `default-pool` 并绑定该 Cluster |
| `serverPool.defaultCreationPolicy = OnZoneCreate` | 传统策略；创建 Zone 时立即创建唯一默认池 `default-pool` |
| `serverPool.defaultCreationPolicy = Manual` | 手动策略；系统不自动创建默认池，用户通过 APICreateServerPoolMsg 和 APIChangeClusterServerPoolMsg 管理 |

---

## 6. 错误场景覆盖

| 场景 | 预期错误 | 错误码 |
|------|---------|--------|
| 创建 PhysicalServer 时 poolUuid 不存在 | "ServerPool not found" | RESOURCE_NOT_FOUND |
| 创建 PhysicalServer 时 zoneUuid 和 Pool 的 zoneUuid 不一致 | "Zone mismatch" | INVALID_ARGUMENT |
| 删除有 Active 角色的 PhysicalServer | "Active roles exist, delete roles first" | OPERATION_NOT_ALLOWED |
| 删除有 PhysicalServer 的 ServerPool | "ServerPool not empty" | OPERATION_NOT_ALLOWED |
| INTERNAL_EXCLUSIVE + INTERNAL_SHARED 互斥 | "Scheduling mode conflict" | OPERATION_NOT_ALLOWED |
| 电源管理无 OOB 凭据 | "OOB credentials not configured" | OPERATION_NOT_ALLOWED |
| 容量不足时分配 | "No physical server with available capacity" | HOST_ALLOCATION_ERROR |
| serialNumber 重复（同 Zone 内） | "Duplicate serialNumber in zone" | DUPLICATE_RESOURCE |
| Cluster 和 ServerPool 不在同一 Zone | "Cluster and ServerPool must be in the same zone" | INVALID_ARGUMENT |

---

## 7. 数据一致性验证点

| 验证点 | 检查方法 |
|--------|---------|
| PhysicalServerCapacityVO 是唯一容量真表 | 直接查 DB 表，确认 HostCapacityVO 是 VIEW |
| VIEW 数据实时一致 | 修改 PhysicalServerCapacityVO 后立即查 HostCapacityVO VIEW |
| 容量不超配 | 所有角色消耗之和 ≤ 物理总量 |
| 角色匹配唯一性 | 同一 PhysicalServer 不出现重复 roleType |
| 迁移数据完整性 | 存量 Host/Chassis 数量 = PhysicalServerRoleVO 数量 |
| ResourceVO 注册 | 每个 PhysicalServerVO 在 ResourceVO 中有记录 |
| AccountResourceRefVO 注册 | 每个 PhysicalServerVO 有 admin 账户关联 |
| ServerPool 删除清理 Cluster 引用 | 删除 ServerPool 后查询 ClusterVO.serverPoolUuid 为空 |
| 默认 ServerPool 创建策略 | `OnClusterCreate` 下创建 Zone 后无默认池，首次创建 Cluster 后默认池唯一且 Cluster 已绑定；`OnZoneCreate` 下创建 Zone 后默认池唯一；`Manual` 下不自动创建默认池 |
| Zone 删除完整级联 | 删除 Zone 后 ServerPoolVO、PhysicalServerVO、PhysicalServerRoleVO、PhysicalServerCapacityVO、PhysicalServerHardwareInfoVO、PhysicalServerHardwareDetailVO 均无残留；关联 Cluster 不保留 serverPoolUuid 悬挂引用 |

---

## 8. 验收标准（AC）

| AC | 验收点 | 集成测试覆盖 |
|----|--------|--------------|
| AC-1 | PhysicalServer 删除会清理 PhysicalServerRoleVO、PhysicalServerCapacityVO、PhysicalServerHardwareInfoVO、PhysicalServerHardwareDetailVO | PhysicalServerCascadeCase |
| AC-2 | ServerPool 级联删除路径会先删除池内 PhysicalServer，再删除 PhysicalServer 子表 | PhysicalServerCascadeCase |
| AC-3 | 直接删除空 ServerPool 会清空 ClusterVO.serverPoolUuid，不留下 Cluster 到已删除 Pool 的引用 | PhysicalServerCascadeCase、ServerPoolCrudCase |
| AC-4 | 删除 Zone 时按 Zone → ServerPool → PhysicalServer 主链路级联，Cluster 只作为 ServerPool 关联对象清理引用 | PhysicalServerCascadeCase |
| AC-5 | Cluster 关联 ServerPool 使用 ClusterVO.serverPoolUuid，一个 Cluster 同时只指向一个 Pool，多个 Cluster 可以指向同一个 Pool | ServerPoolCrudCase |
| AC-6 | Cluster 只能关联同 Zone 的 ServerPool，跨 Zone 关联被 API 拦截 | ServerPoolCrudCase |
| AC-7 | PRD 里的集成测试覆盖率目标不低于 95%，本 feature 的级联、关联、错误路径都必须有集成 case 覆盖 | PhysicalServerCascadeCase、ServerPoolCrudCase |
| AC-8 | 默认 `OnClusterCreate` 策略下，创建 Zone 不生成默认池；首次创建 Cluster 时若 Zone 下无任何 ServerPool，则生成唯一默认池并绑定 Cluster，名称 `default-pool`，`isDefault=true`，状态 `Enabled` | PhysicalServerCascadeCase |
| AC-9 | 默认池策略支持 `OnZoneCreate` 传统行为和 `Manual` 手动行为；`OnClusterCreate` 下若用户在首次创建 Cluster 前已创建自定义 ServerPool，则不自动创建默认池，Cluster 保持未绑定等待用户手动关联 | PhysicalServerCascadeCase |
