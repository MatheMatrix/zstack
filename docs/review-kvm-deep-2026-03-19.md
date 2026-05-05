# KVM 角色适配器设计深度审阅

**审阅者**: KVM Host Domain Expert
**审阅日期**: 2026-03-19
**审阅对象**: `docs/architecture-kvm-adapter-2026-03-18.md` v1.0
**方法论**: 先读代码形成独立理解，再对照设计文档找差距

---

## 1. 从用户场景出发：AddKVMHost 的完整流程

管理员添加一台 KVM Host 的完整代码路径：

```
用户调用 APIAddKVMHostMsg (REST POST /hosts/kvm)
  │
  ▼
HostManagerImpl.handleApiMessage()                    [HostManagerImpl.java:110]
  └── handle(APIAddHostMsg)                           [HostManagerImpl.java:586]
      └── addHostInQueue()                            [HostManagerImpl.java:589]
          │
          ├── 1. 校验 Cluster 存在 & hypervisorType    [HostManagerImpl.java:371-383]
          ├── 2. 校验 managementIp 唯一               [HostManagerImpl.java:386-393]
          ├── 3. 创建 HostVO（state=Enabled, status=Connecting）
          │      └── KVMHostFactory.createHost()       [KVMHostFactory.java:173-185]
          │          └── new KVMHostVO(vo) → dbf.persistAndRefresh()
          │              设置 username/password/port
          │
          ├── 4. FlowChain: add-host
          │   ├── Flow: "call-before-add-host-extension"
          │   │      遍历 HostAddExtensionPoint         [HostManagerImpl.java:422-449]
          │   │
          │   ├── Flow: "send-connect-host-message"     [HostManagerImpl.java:451-470]
          │   │      发送 ConnectHostMsg(newAdd=true)
          │   │      │
          │   │      ▼
          │   │   HostBase.handle(ConnectHostMsg)        [HostBase.java:1233]
          │   │   └── connect() FlowChain:
          │   │       ├── "check-conditions-of-connection"
          │   │       ├── "connect-host" → connectHook()
          │   │       │   └── KVMHost.connectHook()     [KVMHost.java:5549]
          │   │       │       ├── check-host-is-taken-over
          │   │       │       ├── check-host-cpu-arch
          │   │       │       ├── apply-ansible-playbook
          │   │       │       ├── configure-iptables
          │   │       │       ├── echo-host
          │   │       │       ├── collect-kvm-host-facts  ★ serialNumber 在此采集
          │   │       │       │   └── HostFactCmd → HostFactResponse
          │   │       │       │       ret.getSystemSerialNumber() → SystemTag
          │   │       │       └── KVMHostConnectExtensionPoint 链
          │   │       │           └── KVMHostCapacityExtension
          │   │       │               └── CheckHostCapacityMsg
          │   │       │                   → ReportHostCapacityMessage
          │   │       │                   → HostCapacityVO 创建/更新
          │   │       │
          │   │       ├── "call-pre-connect-extensions"
          │   │       ├── "call-post-connect-extensions"  ★ 设计文档注入点
          │   │       │      遍历 PostHostConnectExtensionPoint
          │   │       ├── "recalculate-host-capacity"
          │   │       └── done → status=Connected, tracker.trackHost()
          │   │
          │   ├── Flow: check-host-architecture-match-cluster
          │   ├── Flow: "check-host-os-version"
          │   ├── Flow: "call-after-add-host-extension"
          │   └── done → 返回 HostInventory
          │
          └── error → dbf.remove(vo) + 清理
```

**关键发现**:

1. `serialNumber` 已由现有 `collect-kvm-host-facts` Flow 采集（`KVMHost.java:6141`），存入 `HostSystemTags.SYSTEM_SERIAL_NUMBER`。设计文档无需新增 agent 端点。

2. `HostCapacityVO` 在 connect 流程中由 `KVMHostCapacityExtension` 通过 `CheckHostCapacityMsg` → `ReportHostCapacityMessage` 创建（`HostAllocatorManagerImpl.java:287-313`）。这发生在 `connectHook()` 内部的 KVM 扩展链中，**早于** PostConnect 扩展点。

3. PostConnect 扩展点（`HostBase.java:1361-1391`）是正确的注入位置 -- 此时 HostCapacityVO 已存在，serialNumber 的 SystemTag 已写入。

---

## 2. 现有架构的精确诊断

### 2.1 HostCapacityVO 的写入路径（完整盘点）

**写入方式 1: EntityManager.find + PESSIMISTIC_WRITE + merge**
- `HostCapacityUpdater.lockCapacity()` + `merge()` — `HostCapacityUpdater.java:75,96`
- 调用方 4 处（生产代码）：
  - `HostCapacityReserveManagerImpl.reserveCapacityWithChecking()` :253
  - `HostCapacityReserveManagerImpl.updateCapacityWithoutChecking()` :289
  - `HostAllocatorManagerImpl.handle(RecalculateHostCapacityMsg)` :247（recalculate 路径）
  - `HostAllocatorManagerImpl.returnComputeResourceCapacity()` :834

**写入方式 2: dbf.persist / dbf.update（直接操作）**
- `HostAllocatorManagerImpl.handle(ReportHostCapacityMessage)` — :287-335
  - `dbf.persist(vo)` :313（首次创建）
  - `dbf.update(vo)` :335（重连更新）

**写入方式 3: JPQL UPDATE（批量 DML）**  ★ 关键风险
- `HostCpuOverProvisioningManagerImpl.updateHostsCpuCapacity()` :70-78
  ```java
  "update HostCapacityVO cap set cap.totalCpu = cap.cpuNum * %s"
  ```
- `HostCpuOverProvisioningManagerImpl.updateHostCpuCapacityByUuid()` :96
  ```java
  "update HostCapacityVO cap set cap.totalCpu = cap.cpuNum * %s where cap.uuid = :huuid"
  ```

**写入路径总结**: 生产代码共 **3 种写入模式、6 个写入点**。设计文档声称 "59 个调用方零改动" 只统计了 HostCapacityUpdater 的调用方，遗漏了直接 persist/update 和 JPQL UPDATE。

### 2.2 HostCapacityVO 的读取路径

**EAGER 加载（隐式读取）**:
- `HostVO` 通过 `@OneToOne(fetch = FetchType.EAGER)` 关联 `HostCapacityVO` — `HostVO.java:26-29`
- **任何** `dbf.findByUuid(uuid, HostVO.class)` 或 JPQL `SELECT h FROM HostVO h` 都会触发 JOIN 加载
- 影响范围：275 个引用 HostVO 的 Java 文件

**显式读取**:
- `HostCapacityAllocatorFlow.allocate()` 通过 `hvo.getCapacity()` — `HostCapacityAllocatorFlow.java:44-46`
- `StoppedVmAwareLeastVmPreferredSortFlow` 通过 `Q.New(HostCapacityVO.class)` — :45
- `HostAllocatorManagerImpl.handle(ReportHostCapacityMessage)` 通过 `dbf.findByUuid` — :287
- 测试代码中约 30 处 `dbf.findByUuid(uuid, HostCapacityVO.class)`

### 2.3 Host-Cluster 耦合

- `HostAO.clusterUuid` 有 FK 约束指向 `ClusterEO`，`onDeleteAction = RESTRICT` — `HostAO.java:25`
- `AddHostMsg.clusterUuid` 是 **必填参数** — `AddHostMsg.java:17`
- KVM Host 无法脱离 Cluster 存在。统一层的 PhysicalServerVO 如果不要求 clusterUuid，需要处理这个不对称性

### 2.4 Cluster-L2Network 耦合

- `AttachedL2NetworkAllocatorFlow` 通过 `L2NetworkClusterRefVO` 过滤 Host — `AttachedL2NetworkAllocatorFlow.java`
- 分配链中 Host 的网络能力完全取决于其 Cluster 绑定了哪些 L2Network
- 统一层如果引入 ServerPool 概念替代部分 Cluster 功能，必须保留这条 Cluster → L2Network 关联链

---

## 3. 设计文档逐条评审

### 3.1 KvmPhysicalServerRoleProvider（第 1 章）

| 条目 | 评价 | 说明 |
|------|------|------|
| getRoleType() → KVM_HOST | ✅ 合理 | 简单常量返回，无争议 |
| getSchedulingMode() → INTERNAL_SHARED | ✅ 合理 | 准确描述 KVM 的共享调度语义 |
| getCapacityConsumption() | ⚠️ 需修改 | 从 PhysicalServerCapacityVO 读取已用量是正确方向，但文档中计算 `usedCpu = totalCpu - availableCpu` 在超分场景下语义模糊。应明确是逻辑已用（含超分）还是物理已用 |
| onPhysicalServerCreated() | ✅ 合理 | 预留钩子，KVM 不需要动作 |
| onPhysicalServerDeleted() | ⚠️ 需修改 | 标记 Stale 后，如果 PhysicalServer 被误删再重建，Stale 状态的 RoleVO 不会自动恢复。应在 PostConnect 的幂等逻辑中处理这个 case |
| getInventory() | ✅ 合理 | 从 HostVO 读取并转换为 KvmRoleInventory |
| matchExistingServer() → null | ✅ 合理 | 使用默认匹配逻辑，KVM 无特殊需求 |

### 3.2 PostConnect 钩子（第 2 章）

| 条目 | 评价 | 说明 |
|------|------|------|
| 注入点选择 PostHostConnectExtensionPoint | ✅ 合理 | 此时 HostCapacityVO 已创建、SystemTag 已写入，时序正确 |
| 失败不阻塞 Host 连接 | ✅ 合理 | try-catch + trigger.next() 是正确的设计 |
| 幂等性（重连不重复创建） | ✅ 合理 | 先查 RoleVO 再决定创建或更新 |
| serialNumber 获取 | ⚠️ 需修改 | 文档引用了不存在的 `HostSystemTags.PHYSICAL_SERIAL_NUMBER`。实际应使用已有的 `HostSystemTags.SYSTEM_SERIAL_NUMBER`（由 `saveGeneralHostHardwareFacts` 在 `KVMHost.java:6141` 写入）。无需新增 agent 端点或 SystemTag |
| resolvePoolUuid() 自动创建默认 Pool | ⚠️ 需修改 | 在 PostConnect 流程中自动创建 ServerPoolVO 可能导致并发创建重复的 "default-pool"。应加分布式锁或使用 INSERT IGNORE |
| matchPhysicalServer 降级匹配 | ⚠️ 需修改 | managementIp + zoneUuid 匹配在 Host 迁移 IP 场景下可能误匹配。建议匹配时增加 architecture 约束 |

### 3.3 容量映射（第 3 章）— **最大风险点**

| 条目 | 评价 | 说明 |
|------|------|------|
| HostCapacityVO → VIEW 的方向 | ❌ **应推翻** | 详见下方 3.3.1 分析 |
| HostCapacityUpdater 包装器"59 个调用方零改动" | ❌ **数据错误** | 详见下方 3.3.2 分析 |
| 字段映射表 | ✅ 合理 | 映射关系清晰完整 |
| 超分比同步策略 | ✅ 合理 | PostConnect 时读取并写入，变更时通过现有 recalculate 路径同步 |

#### 3.3.1 HostCapacityVO → VIEW：为什么应该推翻

**核心问题：Hibernate @Entity 映射到 MySQL VIEW 存在 4 个致命冲突。**

**冲突 1: PESSIMISTIC_WRITE 锁定 VIEW 行**

`HostCapacityUpdater.lockCapacity()` 使用 `SELECT ... FOR UPDATE`（`LockModeType.PESSIMISTIC_WRITE`）：
```java
// HostCapacityUpdater.java:75
capacityVO = dbf.getEntityManager().find(HostCapacityVO.class, hostUuid, LockModeType.PESSIMISTIC_WRITE);
```

MySQL VIEW 上的 `SELECT ... FOR UPDATE` 行为取决于 VIEW 定义：
- 简单 VIEW（单表无 JOIN）：可以加锁，锁定的是底层表的行
- JOIN VIEW：MySQL **不支持** `SELECT ... FOR UPDATE`，会报错 `Can not update VIEW`

设计文档提出的 VIEW 定义需要 JOIN `PhysicalServerCapacityVO` 和 `PhysicalServerRoleVO`，这是 JOIN VIEW，`SELECT ... FOR UPDATE` 会失败。

**好处**（如果能解决）：单表锁消除死锁风险
**坏处**（无法回避）：JOIN VIEW 上 FOR UPDATE 直接报 SQL 错误，所有容量扣减路径崩溃

**冲突 2: JPQL UPDATE 语句直接操作 HostCapacityVO**

`HostCpuOverProvisioningManagerImpl` 使用 JPQL UPDATE 批量修改 HostCapacityVO：
```java
// HostCpuOverProvisioningManagerImpl.java:70
"update HostCapacityVO cap set cap.totalCpu = cap.cpuNum * %s"
```

MySQL 对 VIEW 的 UPDATE 有严格限制：包含 JOIN、聚合函数、子查询的 VIEW 不可更新。设计文档的 VIEW 定义包含 JOIN，因此这些 UPDATE 语句会报错。

**好处**：无
**坏处**：CPU 超分比修改功能直接不可用

**冲突 3: dbf.persist() 创建新行**

`HostAllocatorManagerImpl.handle(ReportHostCapacityMessage)` 在 Host 首次上报容量时调用：
```java
// HostAllocatorManagerImpl.java:313
dbf.persist(vo);  // 插入新的 HostCapacityVO 行
```

MySQL 不支持对包含 JOIN 的 VIEW 执行 INSERT。

**好处**：无
**坏处**：新 Host 首次连接时容量初始化失败

**冲突 4: Hibernate EntityManager.merge()**

`HostCapacityUpdater.merge()` 使用 EntityManager.merge() 更新持久化对象：
```java
// HostCapacityUpdater.java:96
capacityVO = dbf.getEntityManager().merge(capacityVO);
```

merge() 内部会生成 UPDATE 语句，在 JOIN VIEW 上同样会失败。

#### 3.3.2 "59 个调用方零改动"是错误的

设计文档声称只需修改 HostCapacityUpdater 的包装器，现有 59 个调用方零改动。实际盘点：

| 写入模式 | 调用点数 | VIEW 方案是否可行 | 包装器方案是否可行 |
|---------|---------|-----------------|------------------|
| HostCapacityUpdater (find+lock+merge) | 4 处 | ❌ FOR UPDATE 失败 | ✅ 包装器可拦截 |
| dbf.persist / dbf.update 直接操作 | 2 处 | ❌ INSERT/UPDATE 失败 | ❌ 不经过 HostCapacityUpdater |
| JPQL UPDATE 批量 DML | 3 处 | ❌ UPDATE VIEW 失败 | ❌ 不经过 HostCapacityUpdater |
| **合计** | **9 处** | **0 处可行** | **4 处可行** |

即使放弃 VIEW 方案改用"包装器 + 真表双写"，仍有 5 处写入不经过 HostCapacityUpdater，需要逐一修改。

### 3.4 CompatibilityBridge（第 4 章）

| 条目 | 评价 | 说明 |
|------|------|------|
| AllocateHostMsg 字段分析 | ✅ 合理 | 16 个字段的逐一分析准确完整 |
| originalMessage 透传方案 | ✅ 合理 | 通过 spec.getOriginalMessage() 向下转型访问 KVM 特有字段，避免在 AllocateServerMsg 中重复定义 |
| 16 个 Flow 的复用评估 | ✅ 合理 | 结论准确：0 个可原样复用，3 个逻辑可复用，13 个需适配 |
| Shadow Mode 策略 | ✅ 合理 | 灰度期间新旧引擎同时执行、对比验证是稳妥的做法 |
| 遗漏：DesignatedAllocateHostMsg.gpuSpecs | ⚠️ 需修改 | GPU 分配是 premium 功能，文档提到了 gpuSpecs 字段但未分析统一层如何处理 GPU 资源调度 |

### 3.5 兼容性风险分析（第 5 章）

| 条目 | 评价 | 说明 |
|------|------|------|
| HostVO 引用影响 275 个文件 | ✅ 合理 | 实测 HostVO 引用约 48 个生产文件、HostCapacityVO 引用 47 个文件（含测试），量级正确 |
| 风险排序 | ⚠️ 需修改 | 排序中 "HostCapacityUpdater 包装器路径" 定为中风险，实际应为**高风险**（因为 VIEW 方案不可行，需要重新设计） |
| 不修改 HostVO/KVMHost.java | ✅ 合理 | 通过 ExtensionPoint 注入的原则完全正确 |
| 特性开关 + Shadow Mode | ✅ 合理 | 防护措施充分 |

---

## 4. 遗漏分析

### 4.1 设计文档完全未覆盖的风险

#### 4.1.1 ReportHostCapacityExtensionPoint 的适配

`HostAllocatorManagerImpl.handle(ReportHostCapacityMessage)` 在 persist/update HostCapacityVO 前会调用 `ReportHostCapacityExtensionPoint` 扩展点：

```java
// HostAllocatorManagerImpl.java:310-312
for (ReportHostCapacityExtensionPoint ext : pluginRgty.getExtensionList(ReportHostCapacityExtensionPoint.class)) {
    vo = ext.reportHostCapacity(s);
}
```

扩展点实现可以修改 HostCapacityVO 的字段值（如调整 availableMemory 扣减预留值）。如果 HostCapacityVO 变为 VIEW 或包装器拦截 persist，这些扩展点的行为需要重新评估。

#### 4.1.2 HostVO @OneToOne EAGER 加载的性能影响

HostVO 上有 3 个 EAGER @OneToOne 关联：
```java
// HostVO.java:26-39
@OneToOne(fetch = FetchType.EAGER) private HostCapacityVO capacity;
@OneToOne(fetch = FetchType.EAGER) private HostIpmiVO ipmi;
@OneToOne(fetch = FetchType.EAGER) private HostHwMonitorStatusVO hwMonitorStatus;
```

如果 HostCapacityVO 变为 VIEW，Hibernate 在加载 HostVO 时会对 VIEW 执行 JOIN 查询。VIEW 的 JOIN 查询性能取决于 MySQL 优化器是否能正确使用索引。在大规模环境（>1000 Host）中，每次加载 HostVO 列表都会触发 VIEW JOIN，可能成为性能瓶颈。

**即使不用 VIEW，保留真表双写方案**，这个 EAGER 加载模式也意味着：任何新增到 HostVO 的 @OneToOne 关联都会影响所有 HostVO 查询的性能。设计文档正确地提出"不在 HostVO 上加任何新注解"，但 VIEW 方案间接通过替换底层表影响了这个 EAGER 加载的行为。

#### 4.1.3 Flyway 迁移的回滚问题

设计文档声称"可安全删除 PhysicalServer* 表恢复原状"（NFR-008）。但如果 HostCapacityVO 已降级为 VIEW：
1. 回滚需要先 DROP VIEW，再 CREATE TABLE 还原原始表结构
2. 还原后需要从 PhysicalServerCapacityVO 反向填充数据到 HostCapacityVO 表
3. 回滚期间 HostCapacityVO 不存在，所有涉及 HostVO 的查询会报错

这不是"安全删除表"这么简单。回滚脚本的复杂度被严重低估。

#### 4.1.4 Host 迁移到不同 Cluster 的场景

现有代码不支持 Host 在 Cluster 间迁移（HostAO.clusterUuid 是 FK，Cluster 删除时 `RESTRICT`）。但统一层引入的 PhysicalServerVO 不绑定 Cluster，PhysicalServerRoleVO 记录 clusterUuid。

如果未来支持"同一物理服务器在不同 Cluster 中承担 KVM 角色"，需要处理：
- 同一 Host IP 在不同 Cluster 的唯一性校验（当前 `HostManagerImpl.java:386-393` 按 managementIp + hypervisorType 判重）
- PhysicalServerRoleVO.clusterUuid 与 HostAO.clusterUuid 的一致性
- 容量在 Cluster 维度的归属

设计文档未讨论这个场景，建议明确排除或规划。

#### 4.1.5 数据库 FK 约束的冲突

`HostCapacityVO.uuid` 有 FK 约束指向 `HostEO`（`onDeleteAction = CASCADE`）：
```java
// HostCapacityVO.java:25
@ForeignKey(parentEntityClass = HostEO.class, onDeleteAction = ReferenceOption.CASCADE)
```

如果 HostCapacityVO 变为 VIEW，这个 FK 约束无法在 VIEW 上定义。需要在 PhysicalServerCapacityVO 上重新建立级联关系，或者通过应用层逻辑保证删除一致性。

---

## 5. 改进建议

### 5.1 必选（不做会导致方案不可行）

#### M1: 放弃 HostCapacityVO → VIEW 方案

**原因**: JOIN VIEW 上的 FOR UPDATE、INSERT、UPDATE 均不可行（见 3.3.1 分析）。

**替代方案: 保留 HostCapacityVO 真表，采用"写入拦截 + 异步同步"模式。**

```
写入路径                       改造方式
────────                      ──────────
HostCapacityUpdater           改为"双写包装器"：
  (4 个调用方)                 先写 HostCapacityVO（保持现有行为），
                              再异步写 PhysicalServerCapacityVO

ReportHostCapacityMessage     在 handler 末尾增加 afterReport 钩子：
  (2 个调用方)                 通过 ExtensionPoint 触发 PhysicalServerCapacityVO 同步

JPQL UPDATE (HostCpuOver-     在 updateHostsCpuCapacity() 末尾
  ProvisioningManagerImpl)     增加 PhysicalServerCapacityVO 的同步更新
  (3 个调用方)
```

**好处**:
- HostCapacityVO 保持真表，所有现有读写路径零改动
- PhysicalServerCapacityVO 作为投影表，由统一层消费
- 回滚简单：删除 PhysicalServerCapacityVO 表和同步逻辑即可

**坏处**:
- 存在两份数据，需要对账机制保障一致性
- 写入路径多一次 DB 操作（但可异步化）

#### M2: 修正 serialNumber 引用

将设计文档中 `HostSystemTags.PHYSICAL_SERIAL_NUMBER` 替换为已有的 `HostSystemTags.SYSTEM_SERIAL_NUMBER`。无需新增 SystemTag 或 agent 端点。

对应代码位置：`KVMHost.java:6141` 已在 `saveGeneralHostHardwareFacts()` 中通过 `recordHardwareChangesAndCreateTag(HostSystemTags.SYSTEM_SERIAL_NUMBER, ...)` 写入。

#### M3: 修正"59 个调用方零改动"的说法

实际写入路径 9 处，其中 5 处不经过 HostCapacityUpdater。设计文档需要完整盘点并逐一给出改造方案。

### 5.2 可选（可以延迟但建议做）

#### O1: resolvePoolUuid() 增加并发保护

建议使用 `INSERT ... ON DUPLICATE KEY UPDATE` 或分布式锁，避免多个 Host 并发 PostConnect 时重复创建 "default-pool"。

#### O2: matchPhysicalServer() 增加 architecture 约束

降级匹配条件从 `managementIp + zoneUuid` 增加为 `managementIp + zoneUuid + architecture`，减少误匹配风险。

#### O3: 增加定时对账任务

由于 M1 方案采用双写而非 VIEW，需要增加定时对账任务（建议 5 分钟周期），对比 HostCapacityVO 和 PhysicalServerCapacityVO 的数据一致性。设计文档原本的异步事件 + 定时对账方案（在 VIEW 方案前的旧设计）是正确的方向。

#### O4: 明确排除 Host 跨 Cluster 迁移场景

在设计文档中明确声明：Phase 1 不支持同一物理服务器在不同 Cluster 中承担 KVM 角色。PhysicalServerRoleVO.clusterUuid 与 HostAO.clusterUuid 保持一致，由 PostConnect 钩子保证。

#### O5: 补充 GPU 资源在统一层的处理方案

DesignatedAllocateHostMsg.gpuSpecs 是 KVM 分配的重要字段（直通 GPU 场景），CompatibilityBridge 需要确保 originalMessage 透传后 GPU 分配 Flow 仍能正确工作。

---

## 6. 总结

### 设计文档整体评价

| 维度 | 评分 | 说明 |
|------|------|------|
| SPI 方法设计 | 8/10 | 方法定义合理，语义清晰。个别方法（getCapacityConsumption）需明确超分语义 |
| PostConnect 钩子设计 | 9/10 | 注入点选择正确，失败隔离完善，幂等性考虑充分。serialNumber 引用需修正 |
| 容量管理 | 3/10 | **VIEW 方案存在 4 个致命冲突，必须推翻**。需改为双写真表方案 |
| CompatibilityBridge | 8/10 | 16 个 Flow 的分析详尽准确，Shadow Mode 策略稳妥。缺少 GPU 场景分析 |
| 兼容性风险分析 | 7/10 | 风险识别基本完整，但容量路径的风险等级评估偏低 |
| 总体可行性 | 6/10 | 容量管理部分需要重新设计后方可执行，其余部分可直接进入开发 |

### 最大风险点

**HostCapacityVO → VIEW 方案是本设计的最大风险点**。它不是"实现难度大"，而是"技术上不可行"（MySQL JOIN VIEW 不支持 FOR UPDATE / INSERT / 多数 UPDATE）。必须在进入开发前切换到双写真表方案，否则会在容量扣减的核心路径上遇到数据库级别的错误，导致 VM 创建/启动/迁移全部失败。

### 最大亮点

PostConnect 钩子的设计（第 2 章）是整篇文档中最成熟的部分：
- 正确识别了注入点的时序约束
- 失败隔离做得彻底（try-catch + trigger.next()）
- 幂等性处理完善（查 RoleVO 再决定创建或更新）
- 多级降级策略（serialNumber → managementIp）务实可靠
