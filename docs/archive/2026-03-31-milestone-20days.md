# 统一硬件管理 22 工作日里程碑计划

> 原则：先谋后动。每个阶段先完成 API 定义 + SDK 生成 + 测试骨架，再写业务逻辑。
> 代码编写部分由 AI 辅助加速，人工侧重在设计决策、代码审查和测试验证。

## 前置条件（Day 0，已完成）
- [x] 5 篇 PRD 评审完毕（10 轮 Planner→Architect→Critic）
- [x] 架构设计文档 7 篇（主骨架 + 4 角色适配 + 分配器 + QA 测试计划）
- [x] Phase 1 数据模型层（41 files, 2467 行）— enums, VOs, API messages, DB schema
- [x] 编译通过（Java 8）

---

## Week 1: 基础设施 + ServerPool/PhysicalServer CRUD

### Day 1 — 编译环境 + SDK 生成通路
- [ ] 解决 worktree + premium 编译问题（独立 clone 或容器方案）
- [ ] `./runMavenProfile premium` 编译通过
- [ ] `./runMavenProfile sdk` 生成 SDK actions（CreateServerPoolAction, CreatePhysicalServerAction 等）
- [ ] `./runMavenProfile apihelper` 生成 ApiHelper.groovy
- [ ] 验证生成的 SDK 包含所有新增 API
- **产出**: 可编译可生成 SDK 的开发环境

### Day 2 — Spring/Service 配置 + ServerPool CRUD 实现
- [ ] 创建 `conf/springConfigXml/PhysicalServer.xml`（bean 定义 + extension point 注册）
- [ ] 创建 `conf/serviceConfig/physicalServer.xml`（API message 路由）
- [ ] 实现 `ServerPoolManagerImpl`（Create/Delete/Update/Query）
- [ ] 实现 `ServerPoolCascadeExtension`（Zone 删除级联）
- [ ] 实现 `ServerPoolApiInterceptor`（删除前检查关联 PhysicalServer）
- **产出**: ServerPool CRUD 可工作

### Day 3 — PhysicalServer CRUD 实现
- [ ] 实现 `PhysicalServerManagerImpl`（Create/Delete/Update/Query/ChangeState）
- [ ] 实现 serialNumber 应用层唯一性校验（NULL 值跳过）
- [ ] 实现 OOB 密码加密存储
- [ ] 实现 `PhysicalServerCascadeExtension`（ServerPool 删除前检查）
- [ ] 实现 `Cluster:ServerPool` Attach/Detach
- **产出**: PhysicalServer + ServerPool 完整 CRUD

### Day 4 — 集成测试：ServerPool + PhysicalServer CRUD
- [ ] 编写 `TestServerPoolCrud`（创建/查询/关联Cluster/删除级联）
- [ ] 编写 `TestPhysicalServerCrud`（创建/查询/更新/删除/状态变更）
- [ ] 编写 `TestPhysicalServerSerialNumberUnique`（唯一性约束）
- [ ] 编写 `TestPhysicalServerOobEncrypt`（密码加密验证）
- [ ] 所有测试通过
- **产出**: Phase 1 CRUD 测试覆盖

### Day 5 — Code Review + 修复 + ProvisionNetwork
- [ ] Review Day 2-4 代码质量（命名、异常处理、事务边界）
- [ ] 修复 review 发现的问题
- [ ] 实现 `ProvisionNetworkManagerImpl`（Create/Delete/Query + ClusterRef）
- [ ] 编写 ProvisionNetwork 集成测试
- **产出**: Phase 1 + Phase 2 完成，代码质量验证

---

## Week 2: Role SPI + 角色适配器

### Day 6 — Role SPI 接口设计 + 互斥逻辑
- [ ] 创建 `PhysicalServerRoleProvider` SPI 接口（6 个方法 + 完整 Javadoc）
- [ ] 创建 `RoleMatchContext`、`CapacityUsage`、`PowerManageable`、`HardwareDiscoverable` 接口
- [ ] 实现 `registerRole()` 互斥检查逻辑（EXCLUSIVE vs SHARED vs READONLY）
- [ ] 实现三级降级匹配 `findOrCreatePhysicalServer(RoleMatchContext)`
- [ ] 编写互斥矩阵单元测试（6 种组合）
- **产出**: SPI 接口 + 核心注册逻辑

### Day 7 — AttachRole/DetachRole API + 测试
- [ ] 实现 `APIAttachPhysicalServerRoleMsg` handler（编排：互斥检查 → 委托 RoleProvider → registerRole）
- [ ] 实现 `APIDetachPhysicalServerRoleMsg` handler（负载检查 → 委托角色模块删除 → 更新 RoleVO）
- [ ] 编写 `TestAttachDetachRole`（互斥拒绝、正常关联、force 强制移除）
- [ ] 编写 `TestRoleAutoAssociation`（serialNumber 匹配、IP 降级、新建）
- **产出**: 运维级角色管理 API 可工作

### Day 8 — KVM RoleProvider 适配
- [ ] 实现 `KvmPhysicalServerRoleProvider`（PostHostConnectExtensionPoint）
- [ ] KVM Host PostConnect 时自动创建 PhysicalServerVO + RoleVO
- [ ] KVM Host 删除时更新 RoleVO 状态为 Stale
- [ ] serialNumber 从 `HostSystemTags.SYSTEM_SERIAL_NUMBER` 读取
- [ ] 编写 `TestKvmRoleProvider`（添加 KVM Host → 自动创建 PhysicalServerVO → 删除 Host → RoleVO Stale）
- **产出**: KVM 角色自动关联可工作

### Day 9 — BM2 RoleProvider 适配
- [ ] 阅读理解 `BareMetal2ChassisManagerImpl` / `BareMetal2ChassisBase` 现有流程
- [ ] 新增 `BareMetal2ChassisLifecycleExtensionPoint`（afterChassisAdded/beforeChassisDeleted）
- [ ] 在 addBareMetal2Chassis / BareMetal2ChassisDeletionMsg handler 中埋点调用
- [ ] 实现 `Bm2PhysicalServerRoleProvider`（INTERNAL_EXCLUSIVE，弹性/绑定双模式映射）
- [ ] 匹配策略：ipmiAddress + zoneUuid 降级（BM2 无 chassis 级 serialNumber）
- [ ] 编写 BM2 RoleProvider 集成测试
- **产出**: BM2 角色适配完成

### Day 10 — Container RoleProvider 适配
- [ ] 阅读理解 `ContainerEndpointBase.syncNodes()` 流程
- [ ] 修复 `afterSyncNodes()` 未调用 bug（在 syncNodes 完成后调用）
- [ ] 扩展 `KubernetesNodeInventory` 添加 systemUUID、capacity 字段
- [ ] 实现 `ContainerPhysicalServerRoleProvider`（EXTERNAL_READONLY，容量扣减）
- [ ] 匹配策略：managementIp + zoneUuid 降级
- [ ] 编写 Container RoleProvider 集成测试
- **产出**: Container 角色适配完成（4 角色全部完成，BM1 延后）

### Day 11 — Role SPI Code Review + IPMI 扫描
- [ ] Review Day 6-10 代码（SPI 接口稳定性、钩子注入安全性）
- [ ] 修复 review 问题
- [ ] 实现 `ScanPhysicalServersLongJob`（IPMI 网段扫描 + 多凭据 + 去重）
- [ ] 编写 IPMI 扫描测试（mock ipmitool）
- **产出**: Phase 3 完成 + 自动发现

---

## Week 3: 容量管理 + 分配引擎

### Day 12 — PhysicalServerCapacityVO + VIEW 设计验证 ⚠️ 最大风险点
- [ ] 创建 `PhysicalServerCapacityVO`（字段映射，@Column(name=...) 保持列名兼容）
- [ ] 编写 VIEW 迁移 SQL（ALTER TABLE RENAME + ADD COLUMN + CREATE VIEW）
- [ ] **在测试库上验证**：VIEW 创建后 HostCapacityVO 的 Hibernate @Entity 映射是否正常
- [ ] **验证 47 个读取方**：HostCapacityVO VIEW 对 @OneToOne EAGER join 透明
- [ ] 记录所有需要 null-safety 审计的 `.getCapacity()` 调用点
- **产出**: 容量 VIEW 迁移方案实测可行

### Day 13 — 6 个写入路径改造
- [ ] W1-W2: `HostAllocatorManagerImpl.handle(ReportHostCapacityMessage)` 改写 PhysicalServerCapacityVO
- [ ] W3: `HostCapacityUpdater._run()` 内部转为 PhysicalServerCapacityVO（接口签名不变）
- [ ] W4-W6: `HostCpuOverProvisioningManagerImpl` 3 条 JPQL 改为 `update PhysicalServerCapacityVO`
- [ ] 编写写入路径集成测试（写入 → 通过 VIEW 读取 → 数据一致）
- [ ] 全量回归测试（zstack/test）确认无回归
- **产出**: 容量单一数据源切换完成

### Day 14 — PhysicalServerCapacityUpdater + 超分比
- [ ] 实现 `PhysicalServerCapacityUpdater`（PESSIMISTIC_WRITE + @DeadlockAutoRestart）
- [ ] 实现 `OverProvisioningManagerImpl`（GlobalConfig 默认 + per-server SystemTag 覆盖）
- [ ] 实现容量重计算 `RecalculatePhysicalServerCapacityMsg`
- [ ] 编写并发扣减测试（多线程不超卖）
- [ ] 编写超分比修改 → 重计算测试
- **产出**: 容量写入引擎可工作

### Day 15 — ServerAllocatorChain + 7 个 Flow
- [ ] 创建 `AllocateServerMsg` / `AllocateServerSpec` / `AllocateServerReply`
- [ ] 实现 `ServerAllocatorChainImpl`（FlowChain 模式）
- [ ] 实现 7 个 Flow：Zone → Cluster → Pool → RoleType → Status → Capacity → Sort
- [ ] 实现 `ServerAllocatorFilterExtensionPoint`（外部模块自定义过滤）
- [ ] 编写每个 Flow 的独立单元测试
- [ ] 编写端到端分配测试（指定 Zone/Cluster/Pool → 正确分配）
- **产出**: 统一分配引擎可工作

### Day 16 — 混部容量 + 容量 Code Review
- [ ] 实现混部互为系统预留模型（KVM available = total - containerReserved - safetyBuffer）
- [ ] 实现 Safety Buffer（CPU max(4, total×5%), Memory max(4GB, total×10%)）
- [ ] 实现 `ServerReservedCapacityExtensionPoint`
- [ ] 编写混部容量测试（KVM+Container 共存不超卖）
- [ ] Review Day 12-15 代码（VIEW 迁移安全性、锁策略、死锁风险）
- **产出**: Phase 4 完成

---

## Week 4+: 兼容层 + 集成 + 收尾

### Day 17 — CompatibilityBridge 两阶段薄适配
- [ ] 在 `AllocateHostSpec` 中新增 `candidateHostUuids` 字段
- [ ] 实现 `CandidateHostUuidsFilterFlow`（HostAllocatorChain 头部过滤）
- [ ] 实现 `CompatibilityBridge`（HostAllocatorPreStartExtensionPoint）
  - Phase 1: AllocateHostMsg → AllocateServerSpec → ServerAllocatorChain
  - Phase 2: 候选集转 hostUuids → 注入 candidateHostUuids
- [ ] 实现 GlobalConfig 开关（`unifiedHardwareManagement.enabled`，Could Have）
- [ ] 编写 Bridge 集成测试（开启/关闭对比）
- **产出**: 兼容层可工作

### Day 18 — 存量数据迁移 + 统一查询
- [ ] 编写幂等迁移 SQL（KVM/BM2/Container 存量 → PhysicalServerVO + RoleVO）
- [ ] 迁移脚本同步注册 ResourceVO + AccountResourceRefVO
- [ ] 创建默认 ServerPool per Zone
- [ ] 编写迁移测试（执行两次不重复、QueryPhysicalServerMsg 可查到所有存量）
- [ ] 验证 QueryPhysicalServerMsg 统一查询（跨角色、分页、过滤）
- **产出**: 迁移 + 统一查询可工作

### Day 19 — 统一电源管理 + 硬件发现
- [ ] 实现电源管理 API（PowerOn/PowerOff/PowerReset/PowerStatus）
- [ ] 通过 OOB 凭据执行 IPMI/Redfish 命令
- [ ] 实现 `HardwareDiscoverable` 默认实现（OOB FRU 读取）
- [ ] 编写电源管理测试 + 硬件发现测试
- **产出**: 统一 API 完成

### Day 20 — 全量回归 + 端到端验证
- [ ] `zstack/test` 全量测试通过（零新增失败）
- [ ] `premium/test-premium` 全量测试通过
- [ ] 端到端场景验证：
  1. 创建 ServerPool → 创建 PhysicalServer → 添加 KVM Host → 自动关联
  2. 同一物理机注册 Container → 混部容量正确
  3. 创建 VM → CompatibilityBridge 分配 → 行为不变
  4. 存量迁移 → 统一查询 → 电源操作
- [ ] 无新增 WARN/ERROR 日志
- **产出**: 全量回归绿灯

### Day 21 — 回归修复 buffer
- [ ] 修复 Day 20 发现的回归问题
- [ ] 补充边界条件测试
- [ ] 二次全量回归验证
- **产出**: 所有测试绿灯

### Day 22 — Code Review + 文档 + MR
- [ ] 最终 code review（架构一致性、API 稳定性、安全性）
- [ ] 更新架构设计文档（实际实现 vs 设计的差异）
- [ ] 更新 CLAUDE.md / AGENTS.md（新模块开发指南）
- [ ] 创建 GitLab MR（zstack + premium 两个 MR）
- [ ] 同步 Confluence 页面
- **产出**: MR 提交，进入 review 流程

---

## 风险项

| 风险 | 影响 | 缓解 |
|------|------|------|
| HostCapacityVO → VIEW 后 Hibernate 兼容性 | Day 12-13 可能阻塞 | Day 12 先在测试库实测，失败则降级为双写 |
| Premium 编译环境（worktree + submodule） | SDK 生成阻塞 | Day 1 用独立 clone 或容器解决 |
| BM2/Container 钩子注入点不存在 | Day 9-10 需要新增 ExtensionPoint | 已在 PRD review 中标注，工作量可控 |
| 6 个写入路径改造回归 | Day 13 可能引入 bug | 全量回归测试覆盖 |
| 混部容量互为预留的实际效果 | Day 16 边界条件复杂 | Safety buffer 保底 |

## 里程碑检查点

| 检查点 | 日期 | 标准 |
|--------|------|------|
| M1: CRUD 可工作 | Day 5 完成 | ServerPool/PhysicalServer/ProvisionNetwork CRUD + 测试通过 |
| M2: SPI + 4 角色适配 | Day 11 完成 | KVM/BM2/Container 自动关联 + 互斥检查 + 测试通过 |
| M3: 容量引擎 | Day 16 完成 | VIEW 迁移 + 分配引擎 + 混部 + 测试通过 |
| M4: 全量完成 | Day 22 完成 | 兼容层 + 迁移 + 全量回归 + MR 提交 |
