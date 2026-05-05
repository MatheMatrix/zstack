# ZStack 统一硬件管理 - 完整实现计划 (21 Remaining FRs)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现 ZStack 统一硬件管理框架剩余 21 个 FR，覆盖 Role SPI、容量管理、分配引擎、兼容桥、电源管理、硬件发现全部功能

**Architecture:** 三层架构（Physical -> Role -> Consumer），PhysicalServerCapacityVO 真表 + HostCapacityVO VIEW，PhysicalServerRoleProvider SPI 驱动角色接入

**Tech Stack:** Java 8, Spring 5.2.25, Hibernate 5.3.26, MySQL, Groovy (tests)

**Build Commands:**
```bash
# 全量编译
JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64 mvn clean install -DskipTests -Dmaven.repo.local=.m2/repository

# Premium 编译
./runMavenProfile premium

# SDK 生成
./runMavenProfile sdk

# 单模块编译
JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64 mvn clean install -pl plugin/physicalServer -am -DskipTests -Dmaven.repo.local=.m2/repository
```

**Branch:** `feature/unifi-host-dev`

---

## 已完成 (Week 1) - 不需要重复

以下 FR 已在 `feature/unifi-host-dev` 分支上完成:
- FR-001: PhysicalServerAO/VO CRUD (Create/Delete/Update/Query/ChangeState)
- FR-005: OOB 凭据字段已在 VO 中（缺 @EncryptColumn）
- FR-006: State 状态机（Status/PowerStatus 自动更新未做）
- FR-007: ServerPool CRUD
- FR-008: Cluster:ServerPool attach/detach
- FR-009: PhysicalServer 归属 ServerPool
- FR-010: ProvisionNetworkVO CRUD
- FR-011: ProvisionNetwork ClusterRef
- FR-012: API 不限定角色类型
- 25 个集成测试方法已写

**已有文件清单:**
- `header/src/main/java/org/zstack/header/server/` — 62 个 Java 文件（VO、枚举、API 消息、Inventory）
- `plugin/physicalServer/` — PhysicalServerManagerImpl + PhysicalServerApiInterceptor
- `conf/db/upgrade/V5.5.18__schema.sql` — 8 张表 DDL
- `conf/springConfigXml/PhysicalServerManager.xml` — Spring 配置
- `conf/serviceConfig/physicalServer.xml` + `serverPool.xml` — 消息路由

---

## Phase 1 (Week 2): Role SPI + 角色 Attach/Detach + KVM RoleProvider

### Task 6: PhysicalServerRoleProvider SPI 接口定义 + 互斥逻辑

**Goal:** 定义 PhysicalServerRoleProvider SPI 接口，在 PhysicalServerManagerImpl 中实现 registerRole()/unregisterRole() 核心方法，包含互斥矩阵检查。

**Depends on:** Tasks 1-5 已完成

**FRs covered:** FR-022 (PhysicalServerRoleProvider SPI), FR-002 (PhysicalServerRoleVO 角色映射), FR-014 部分 (SchedulingMode 枚举已存在)

**Files to create/modify:**
- `header/src/main/java/org/zstack/header/server/PhysicalServerRoleProvider.java` — **新建** SPI 接口
- `header/src/main/java/org/zstack/header/server/RoleMatchContext.java` — **新建** 匹配上下文
- `header/src/main/java/org/zstack/header/server/CapacityUsage.java` — **新建** 容量消耗 DTO
- `header/src/main/java/org/zstack/header/server/PhysicalServerRoleProviderRegisterExtensionPoint.java` — **新建** 扩展点
- `plugin/physicalServer/src/main/java/org/zstack/server/PhysicalServerManagerImpl.java` — **修改** 增加 registerRole/unregisterRole

#### Steps

- [ ] **Step 6.1: 创建 CapacityUsage DTO**

  ```java
  // header/src/main/java/org/zstack/header/server/CapacityUsage.java
  package org.zstack.header.server;

  public class CapacityUsage {
      private long usedCpu;
      private long usedMemory;
      private long usedDisk;

      // getters/setters
  }
  ```

- [ ] **Step 6.2: 创建 RoleMatchContext**

  ```java
  // header/src/main/java/org/zstack/header/server/RoleMatchContext.java
  package org.zstack.header.server;

  /**
   * Context for role auto-association matching (FR-027).
   * Carries fields from the external resource to match against existing PhysicalServerVO.
   */
  public class RoleMatchContext {
      private String serialNumber;
      private String managementIp;
      private String zoneUuid;
      private String oobAddress;     // for BM matching
      private String roleUuid;       // the external resource UUID (HostVO.uuid, ChassisVO.uuid, etc.)
      private ServerRoleType roleType;
      private SchedulingMode schedulingMode;

      // getters/setters, builder pattern
  }
  ```

- [ ] **Step 6.3: 创建 PhysicalServerRoleProvider SPI 接口**

  ```java
  // header/src/main/java/org/zstack/header/server/PhysicalServerRoleProvider.java
  package org.zstack.header.server;

  import java.util.List;

  /**
   * SPI for role modules to integrate with unified physical server management.
   * Each role module (KVM, BM2, Container) implements this interface and registers as a Spring bean.
   *
   * PRD: FR-022
   */
  public interface PhysicalServerRoleProvider {
      /** Which role type this provider handles */
      ServerRoleType getRoleType();

      /** Default scheduling mode for this role type */
      SchedulingMode getSchedulingMode();

      /**
       * Get current capacity consumption of this role on a specific server.
       * Called during capacity recalculation (FR-017).
       */
      CapacityUsage getCapacityConsumption(String serverUuid);

      /** Lifecycle callback: after PhysicalServerVO is created for this role */
      void onPhysicalServerCreated(String serverUuid, String roleUuid);

      /** Lifecycle callback: before PhysicalServerVO role is detached */
      void onPhysicalServerRoleDetaching(String serverUuid, String roleUuid);

      /**
       * Check if a role has active workload preventing detach.
       * @return error message if busy, null if safe to detach
       */
      String checkBeforeDetach(String serverUuid, String roleUuid);
  }
  ```

- [ ] **Step 6.4: 在 PhysicalServerManagerImpl 中实现 registerRole() 互斥矩阵**

  互斥规则（来自 PRD Q1）:
  - INTERNAL_EXCLUSIVE 与 INTERNAL_SHARED 互斥
  - 同类型角色不允许重复注册
  - EXTERNAL_READONLY 与任何模式兼容

  ```java
  // 在 PhysicalServerManagerImpl 中增加:
  @Autowired
  private PluginRegistry pluginRgty;

  private Map<ServerRoleType, PhysicalServerRoleProvider> roleProviders = new HashMap<>();

  @Override
  public boolean start() {
      // 收集所有 RoleProvider
      for (PhysicalServerRoleProvider p : pluginRgty.getExtensionList(PhysicalServerRoleProvider.class)) {
          roleProviders.put(p.getRoleType(), p);
      }
      return true;
  }

  public PhysicalServerRoleVO registerRole(String serverUuid, RoleMatchContext ctx) {
      // 1. 查询已有角色
      List<PhysicalServerRoleVO> existingRoles = Q.New(PhysicalServerRoleVO.class)
              .eq(PhysicalServerRoleVO_.serverUuid, serverUuid)
              .list();

      // 2. 互斥检查
      SchedulingMode newMode = ctx.getSchedulingMode();
      for (PhysicalServerRoleVO existing : existingRoles) {
          // 同类型拒绝
          if (existing.getRoleType() == ctx.getRoleType()) {
              throw new OperationFailureException(operr(
                  "server[uuid:%s] already has role[type:%s]", serverUuid, ctx.getRoleType()));
          }
          // EXCLUSIVE vs SHARED 互斥
          if (isExclusiveConflict(existing.getSchedulingMode(), newMode)) {
              throw new OperationFailureException(operr(
                  "server[uuid:%s] has role[type:%s, mode:%s] which conflicts with new role[type:%s, mode:%s]",
                  serverUuid, existing.getRoleType(), existing.getSchedulingMode(),
                  ctx.getRoleType(), newMode));
          }
      }

      // 3. 创建 RoleVO
      PhysicalServerRoleVO role = new PhysicalServerRoleVO();
      role.setUuid(Platform.getUuid());
      role.setServerUuid(serverUuid);
      role.setRoleType(ctx.getRoleType());
      role.setRoleUuid(ctx.getRoleUuid());
      role.setSchedulingMode(newMode);
      role.setRoleStatus(PhysicalServerRoleStatus.Active);
      dbf.persist(role);

      // 4. 通知 RoleProvider
      PhysicalServerRoleProvider provider = roleProviders.get(ctx.getRoleType());
      if (provider != null) {
          provider.onPhysicalServerCreated(serverUuid, ctx.getRoleUuid());
      }

      return role;
  }

  private boolean isExclusiveConflict(SchedulingMode existing, SchedulingMode incoming) {
      if (existing == SchedulingMode.EXTERNAL_READONLY || incoming == SchedulingMode.EXTERNAL_READONLY) {
          return false;
      }
      if (existing == SchedulingMode.INTERNAL_EXCLUSIVE || incoming == SchedulingMode.INTERNAL_EXCLUSIVE) {
          return true;
      }
      return false;
  }
  ```

- [ ] **Step 6.5: 实现 unregisterRole()**

  ```java
  public void unregisterRole(String serverUuid, ServerRoleType roleType) {
      PhysicalServerRoleVO role = Q.New(PhysicalServerRoleVO.class)
              .eq(PhysicalServerRoleVO_.serverUuid, serverUuid)
              .eq(PhysicalServerRoleVO_.roleType, roleType)
              .find();
      if (role == null) {
          return;
      }

      // 负载检查
      PhysicalServerRoleProvider provider = roleProviders.get(roleType);
      if (provider != null) {
          String err = provider.checkBeforeDetach(serverUuid, role.getRoleUuid());
          if (err != null) {
              throw new OperationFailureException(operr(
                  "cannot detach role[type:%s] from server[uuid:%s]: %s", roleType, serverUuid, err));
          }
          provider.onPhysicalServerRoleDetaching(serverUuid, role.getRoleUuid());
      }

      dbf.remove(role);
  }
  ```

- [ ] **Step 6.6: 编译验证**

  ```bash
  JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64 mvn clean install -pl plugin/physicalServer -am -DskipTests -Dmaven.repo.local=.m2/repository
  ```

**Acceptance Criteria (FR-022 AC):**
- [ ] SPI 接口方法语义明确，有完整 Javadoc
- [ ] 四个角色模块均可实现此 SPI (接口设计不含角色特有假设)
- [ ] 互斥矩阵正确: EXCLUSIVE 与 SHARED 互斥，同类型拒绝，EXTERNAL_READONLY 与任何兼容

**Commit:** `<feature>[server]: PhysicalServerRoleProvider SPI and role exclusion logic`

---

### Task 7: APIAttachPhysicalServerRoleMsg / APIDetachPhysicalServerRoleMsg

**Goal:** 实现角色手动 Attach/Detach API，委托 registerRole()/unregisterRole() 执行。

**Depends on:** Task 6

**FRs covered:** FR-035 (APIAttachPhysicalServerRoleMsg), FR-036 (APIDetachPhysicalServerRoleMsg)

**Files to create/modify:**
- `header/src/main/java/org/zstack/header/server/APIAttachPhysicalServerRoleMsg.java` — **新建**
- `header/src/main/java/org/zstack/header/server/APIAttachPhysicalServerRoleEvent.java` — **新建**
- `header/src/main/java/org/zstack/header/server/APIDetachPhysicalServerRoleMsg.java` — **新建**
- `header/src/main/java/org/zstack/header/server/APIDetachPhysicalServerRoleEvent.java` — **新建**
- `plugin/physicalServer/src/main/java/org/zstack/server/PhysicalServerManagerImpl.java` — **修改** 增加 handle 方法
- `plugin/physicalServer/src/main/java/org/zstack/server/PhysicalServerApiInterceptor.java` — **修改** 增加校验
- `conf/serviceConfig/physicalServer.xml` — **修改** 添加消息路由

#### Steps

- [ ] **Step 7.1: 创建 APIAttachPhysicalServerRoleMsg**

  ```java
  @APIParam(resourceType = PhysicalServerVO.class)
  private String serverUuid;

  @APIParam(validValues = {"KVM_HOST", "BAREMETAL_V2", "CONTAINER_HOST"})
  private String roleType;

  @APIParam(required = false)
  private String roleUuid;  // 指定已有外部资源 UUID，可选
  ```

- [ ] **Step 7.2: 创建 APIDetachPhysicalServerRoleMsg**

  ```java
  @APIParam(resourceType = PhysicalServerVO.class)
  private String serverUuid;

  @APIParam(validValues = {"KVM_HOST", "BAREMETAL_V2", "CONTAINER_HOST"})
  private String roleType;
  ```

- [ ] **Step 7.3: 在 PhysicalServerManagerImpl 中实现 handle(APIAttachPhysicalServerRoleMsg)**

  调用 registerRole()，返回 PhysicalServerInventory（含角色列表）。

- [ ] **Step 7.4: 在 PhysicalServerManagerImpl 中实现 handle(APIDetachPhysicalServerRoleMsg)**

  调用 unregisterRole()，返回 PhysicalServerInventory。

- [ ] **Step 7.5: 在 ApiInterceptor 中增加校验**

  - serverUuid 必须存在
  - roleType 必须合法
  - Detach 时验证角色存在

- [ ] **Step 7.6: 更新 physicalServer.xml 消息路由**

- [ ] **Step 7.7: 编译 + SDK 生成验证**

  ```bash
  JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64 mvn clean install -DskipTests -Dmaven.repo.local=.m2/repository
  ./runMavenProfile sdk
  ```

**Acceptance Criteria (FR-035/036 AC):**
- [ ] Attach 时互斥检查 -> 委托 RoleProvider.createRole() -> registerRole()
- [ ] Detach 时负载检查 -> provider.checkBeforeDetach() -> unregisterRole()
- [ ] API 参数校验完整

**Commit:** `<feature>[server]: AttachRole and DetachRole APIs with exclusion check`

---

### Task 8: KVM Host RoleProvider 实现

**Goal:** 实现 KVM 角色适配器，在 KVM Host PostConnect 钩子中自动创建 PhysicalServerVO + RoleVO，支持 serialNumber 自动关联。

**Depends on:** Task 6, Task 7

**FRs covered:** FR-023 (KVM Host RoleProvider), FR-027 (角色自动关联)

**Files to create/modify:**
- `plugin/physicalServer/src/main/java/org/zstack/server/KvmRoleProvider.java` — **新建**
- `plugin/physicalServer/src/main/java/org/zstack/server/PhysicalServerAutoAssociator.java` — **新建** 三级降级匹配
- `conf/springConfigXml/PhysicalServerManager.xml` — **修改** 注册 Bean + 扩展点

#### Steps

- [ ] **Step 8.1: 创建 PhysicalServerAutoAssociator (FR-027 三级降级匹配)**

  ```java
  package org.zstack.server;

  /**
   * Three-level degradation matching for auto-association (PRD FR-027, FAQ-15):
   * 1. serialNumber (primary) — via KVM SYSTEM_SERIAL_NUMBER SystemTag
   * 2. oobAddress + zoneUuid (degraded) — BM scenario IPMI address
   * 3. managementIp + zoneUuid (fallback) — last resort
   *
   * Invalid serialNumber blacklist: "Not Specified", "To Be Filled", "Default string", ""
   */
  public class PhysicalServerAutoAssociator {

      private static final Set<String> INVALID_SERIAL_NUMBERS = new HashSet<>(Arrays.asList(
          "Not Specified", "To Be Filled", "Default string", "None", "N/A", ""
      ));

      /**
       * Find or create a PhysicalServerVO matching the given context.
       * @return the matched or newly created PhysicalServerVO uuid
       */
      public String findOrCreate(RoleMatchContext ctx) {
          // Level 1: serialNumber match
          if (isValidSerialNumber(ctx.getSerialNumber())) {
              PhysicalServerVO vo = Q.New(PhysicalServerVO.class)
                  .eq(PhysicalServerVO_.zoneUuid, ctx.getZoneUuid())
                  .eq(PhysicalServerVO_.serialNumber, ctx.getSerialNumber())
                  .find();
              if (vo != null) return vo.getUuid();
          }

          // Level 2: oobAddress + zoneUuid
          if (ctx.getOobAddress() != null) {
              PhysicalServerVO vo = Q.New(PhysicalServerVO.class)
                  .eq(PhysicalServerVO_.zoneUuid, ctx.getZoneUuid())
                  .eq(PhysicalServerVO_.oobAddress, ctx.getOobAddress())
                  .find();
              if (vo != null) return vo.getUuid();
          }

          // Level 3: managementIp + zoneUuid
          if (ctx.getManagementIp() != null) {
              PhysicalServerVO vo = Q.New(PhysicalServerVO.class)
                  .eq(PhysicalServerVO_.zoneUuid, ctx.getZoneUuid())
                  .eq(PhysicalServerVO_.managementIp, ctx.getManagementIp())
                  .find();
              if (vo != null) return vo.getUuid();
          }

          // No match: create new PhysicalServerVO
          return createNewPhysicalServer(ctx);
      }
  }
  ```

- [ ] **Step 8.2: 创建 KvmRoleProvider**

  实现 `PhysicalServerRoleProvider` 和 `KVMHostConnectExtensionPoint`。

  ```java
  package org.zstack.server;

  /**
   * KVM Host role provider (PRD FR-023).
   * - PostConnect hook: auto-create PhysicalServerVO + RoleVO
   * - SchedulingMode: INTERNAL_SHARED
   * - serialNumber from KVM SystemTag SYSTEM_SERIAL_NUMBER
   */
  public class KvmRoleProvider implements PhysicalServerRoleProvider, KVMHostConnectExtensionPoint {

      @Override
      public ServerRoleType getRoleType() {
          return ServerRoleType.KVM_HOST;
      }

      @Override
      public SchedulingMode getSchedulingMode() {
          return SchedulingMode.INTERNAL_SHARED;
      }

      @Override
      public Flow createKvmHostConnectingFlow(KVMHostConnectedContext context) {
          return new NoRollbackFlow() {
              @Override
              public void run(FlowTrigger trigger, Map data) {
                  HostVO host = dbf.findByUuid(context.getInventory().getUuid(), HostVO.class);
                  // Extract serialNumber from SystemTag
                  String serialNumber = getSerialNumberFromSystemTag(host.getUuid());

                  RoleMatchContext ctx = new RoleMatchContext();
                  ctx.setSerialNumber(serialNumber);
                  ctx.setManagementIp(host.getManagementIp());
                  ctx.setZoneUuid(host.getZoneUuid());
                  ctx.setRoleUuid(host.getUuid());
                  ctx.setRoleType(ServerRoleType.KVM_HOST);
                  ctx.setSchedulingMode(SchedulingMode.INTERNAL_SHARED);

                  // poolUuid: derive from Cluster -> ServerPool mapping
                  ctx.setPoolUuid(resolvePoolUuid(host.getClusterUuid()));

                  String serverUuid = autoAssociator.findOrCreate(ctx);
                  registerRole(serverUuid, ctx);
                  trigger.next();
              }
          };
      }
  }
  ```

- [ ] **Step 8.3: 注册 KvmRoleProvider 到 Spring XML**

  ```xml
  <!-- conf/springConfigXml/PhysicalServerManager.xml -->
  <bean id="KvmRoleProvider" class="org.zstack.server.KvmRoleProvider">
      <zstack:plugin>
          <zstack:extension interface="org.zstack.header.server.PhysicalServerRoleProvider" />
          <zstack:extension interface="org.zstack.kvm.KVMHostConnectExtensionPoint" />
      </zstack:plugin>
  </bean>
  ```

- [ ] **Step 8.4: 处理 KVM Host 删除时角色清理**

  监听 HostVO 删除事件，调用 unregisterRole()。可通过实现 `HostDeleteExtensionPoint` 或 `ResourceDeletionExtensionPoint`。

- [ ] **Step 8.5: 集成测试**

  测试场景:
  1. 添加 KVM Host 后自动创建 PhysicalServerVO + RoleVO
  2. 同一 serialNumber 的第二个角色（Container）关联到同一 PhysicalServerVO
  3. 删除 KVM Host 后 RoleVO 被清理

- [ ] **Step 8.6: 编译验证**

  ```bash
  JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64 mvn clean install -DskipTests -Dmaven.repo.local=.m2/repository
  ```

**Acceptance Criteria (FR-023 AC):**
- [ ] 新建 KVM Host 自动创建 PhysicalServerVO + RoleVO
- [ ] 删除 KVM Host 自动更新 RoleVO 状态
- [ ] serialNumber 匹配优先于 managementIp 匹配 (FR-027)
- [ ] 降级匹配时 zoneUuid 必须一致

**Commit:** `<feature>[server]: KVM RoleProvider with auto-association and PostConnect hook`

---

### Task 9: FR-005 补完 @EncryptColumn + FR-006 补完 Status 自动更新

**Goal:** 补全 oobPassword 加密存储，实现 PhysicalServerTracker 周期探测 Status/PowerStatus。

**Depends on:** Tasks 1-5

**FRs covered:** FR-005 补完 (@EncryptColumn), FR-006 补完 (Status/PowerStatus auto-update)

**Files to create/modify:**
- `header/src/main/java/org/zstack/header/server/PhysicalServerAO.java` — **修改** 增加 @EncryptColumn
- `plugin/physicalServer/src/main/java/org/zstack/server/PhysicalServerTracker.java` — **新建** 周期探测
- `plugin/physicalServer/src/main/java/org/zstack/server/PhysicalServerManagerImpl.java` — **修改** 注册 Tracker

#### Steps

- [ ] **Step 9.1: 添加 @EncryptColumn 到 oobPassword**

  ```java
  // PhysicalServerAO.java
  @Column
  @EncryptColumn
  private String oobPassword;
  ```

  需要添加 import: `org.zstack.core.encrypt.EncryptColumn`

- [ ] **Step 9.2: 创建 PhysicalServerTracker**

  参考 `BareMetal2InstancePingTracker` 模式，实现周期 ping/探测:

  ```java
  package org.zstack.server;

  /**
   * Periodic tracker for PhysicalServer status (FR-006).
   * - Status: Connecting/Connected/Disconnected (via management IP ping or agent heartbeat)
   * - PowerStatus: PowerOn/PowerOff/Unknown (via IPMI/Redfish OOB query)
   */
  public class PhysicalServerTracker implements ManagementNodeReadyExtensionPoint {
      // 使用 thdf.submitPeriodicTask() 注册周期任务
      // 对于有 OOB 凭据的服务器: 通过 IPMI 查询 PowerStatus
      // 对于有 managementIp 的服务器: 通过 ping 判断 Status
      // 更新 SQL.New(PhysicalServerVO.class).set(...)
  }
  ```

- [ ] **Step 9.3: 编译验证**

**Acceptance Criteria:**
- [ ] oobPassword 加密存储（数据库中非明文）
- [ ] Status 由系统周期探测自动更新
- [ ] PowerStatus 由 IPMI/Redfish 查询获取（有 OOB 时）
- [ ] Maintenance 状态下不参与分配

**Commit:** `<feature>[server]: EncryptColumn for OOB password and PhysicalServerTracker`

---

### Task 10: BM2 RoleProvider 实现

**Goal:** 实现 BM2 角色适配器，在 BareMetal2Chassis 生命周期事件中同步创建/更新 PhysicalServerVO + RoleVO。

**Depends on:** Task 6, Task 8 (auto-associator)

**FRs covered:** FR-025 (BM2 RoleProvider)

**Files to create/modify:**
- `plugin/physicalServer/src/main/java/org/zstack/server/Bm2RoleProvider.java` — **新建**
- `conf/springConfigXml/PhysicalServerManager.xml` — **修改** 注册 Bean

**注意:** 需要确认 BM2 模块的扩展点接口。查看 `premium/baremetal2/` 中的 Extension 相关文件:
- `BareMetal2InstanceExtension.java` — 实例生命周期
- BM2 Chassis 创建钩子（需在 BM2 模块中寻找 Chassis 生命周期扩展点）

#### Steps

- [ ] **Step 10.1: 分析 BM2 Chassis 生命周期扩展点**

  在 `premium/baremetal2/` 中查找 Chassis 创建/删除的扩展点接口。如果不存在，需要在 header 中定义通用的回调机制（或使用 GlobalEventBus）。

- [ ] **Step 10.2: 创建 Bm2RoleProvider**

  ```java
  package org.zstack.server;

  /**
   * BM2 role provider (PRD FR-025).
   * - SchedulingMode: INTERNAL_EXCLUSIVE
   * - Syncs IPMI info from BM2 Chassis to PhysicalServerVO OOB fields
   * - Supports both elastic and binding modes
   */
  public class Bm2RoleProvider implements PhysicalServerRoleProvider {
      @Override
      public ServerRoleType getRoleType() {
          return ServerRoleType.BAREMETAL_V2;
      }

      @Override
      public SchedulingMode getSchedulingMode() {
          return SchedulingMode.INTERNAL_EXCLUSIVE;
      }

      // IPMI 信息同步: ipmiAddress -> oobAddress, ipmiUsername -> oobUsername, etc.
  }
  ```

- [ ] **Step 10.3: 注册到 Spring XML，实现 BM2 生命周期钩子**

- [ ] **Step 10.4: 编译验证（需要 premium 编译）**

  ```bash
  ./runMavenProfile premium
  ```

**Acceptance Criteria (FR-025 AC):**
- [ ] BM2 Chassis 创建/删除自动同步 PhysicalServerVO + RoleVO
- [ ] IPMI 信息正确同步到 OOB 字段
- [ ] INTERNAL_EXCLUSIVE 模式正确标记

**Commit:** `<feature>[server]: BM2 RoleProvider with chassis lifecycle sync`

---

### Task 11: Container RoleProvider 实现

**Goal:** 实现 Container 角色适配器，EXTERNAL_READONLY 模式。

**Depends on:** Task 6, Task 8 (auto-associator)

**FRs covered:** FR-026 (Container RoleProvider)

**Files to create/modify:**
- `plugin/physicalServer/src/main/java/org/zstack/server/ContainerRoleProvider.java` — **新建**
- `conf/springConfigXml/PhysicalServerManager.xml` — **修改**

**注意:** NativeHostVO 不在当前 repo 中（属于 cube 模块）。Container RoleProvider 需设计为:
1. 如果 cube 模块在 classpath 中，通过反射或弱引用方式监听 NativeHost 事件
2. 如果 cube 不在 classpath，RoleProvider 仅注册但不激活自动同步
3. 手动 Attach 始终可用（通过 APIAttachPhysicalServerRoleMsg）

#### Steps

- [ ] **Step 11.1: 创建 ContainerRoleProvider**

  ```java
  package org.zstack.server;

  /**
   * Container/K8s role provider (PRD FR-026).
   * - SchedulingMode: EXTERNAL_READONLY
   * - Capacity reported by K8s, ZStack does not deduct
   * - NativeHost module may not be in classpath (cube is separate)
   */
  public class ContainerRoleProvider implements PhysicalServerRoleProvider {
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
          // EXTERNAL_READONLY: capacity is informational, reported by K8s
          // Return whatever K8s reports as "used" for display purposes
          return new CapacityUsage(); // zero consumption from ZStack's perspective
      }
  }
  ```

- [ ] **Step 11.2: 注册到 Spring XML**

- [ ] **Step 11.3: 编译验证**

**Acceptance Criteria (FR-026 AC):**
- [ ] EXTERNAL_READONLY 模式下不参与 ZStack 容量分配
- [ ] K8s 报告的容量可同步到 PhysicalServerCapacityVO（只读展示）
- [ ] 手动 Attach 可用（NativeHost 不在 classpath 时降级）

**Commit:** `<feature>[server]: Container RoleProvider with EXTERNAL_READONLY mode`

---

## Phase 2 (Week 3): 统一容量管理

### Task 12: PhysicalServerCapacityVO 真表 + DDL

**Goal:** 创建 PhysicalServerCapacityVO 真表，设计 HostCapacityVO VIEW 迁移方案（VIEW 迁移在 Phase 4 FR-030 中执行）。

**Depends on:** Tasks 1-5

**FRs covered:** FR-013 (PhysicalServerCapacityVO)

**Files to create/modify:**
- `header/src/main/java/org/zstack/header/server/PhysicalServerCapacityVO.java` — **新建**
- `header/src/main/java/org/zstack/header/server/PhysicalServerCapacityVO_.java` — **新建** metamodel
- `header/src/main/java/org/zstack/header/server/CapacityState.java` — **新建** 枚举
- `conf/db/upgrade/V5.5.18__schema.sql` — **修改** 增加 DDL

#### Steps

- [ ] **Step 12.1: 创建 CapacityState 枚举**

  ```java
  package org.zstack.header.server;

  public enum CapacityState {
      Initialized,
      Ready,
      Allocated,
      Recalculating,
      Stale
  }
  ```

- [ ] **Step 12.2: 创建 PhysicalServerCapacityVO**

  字段对齐 HostCapacityVO（保证 VIEW 映射）+ 新增字段:

  ```java
  @Entity
  @Table(name = "PhysicalServerCapacityVO")
  public class PhysicalServerCapacityVO {
      @Id
      @Column
      @ForeignKey(parentEntityClass = PhysicalServerVO.class, onDeleteAction = ReferenceOption.CASCADE)
      private String uuid;  // == PhysicalServerVO.uuid

      // --- 与 HostCapacityVO 完全对齐的字段 (VIEW 映射需要) ---
      @Column
      private long totalCpu;           // = totalPhysicalCpu * cpuOverprovisioningRatio

      @Column
      private long availableCpu;

      @Column
      private long totalMemory;        // = totalPhysicalMemory * memoryOverprovisioningRatio

      @Column
      private long availableMemory;

      @Column
      private long totalPhysicalMemory;

      @Column
      private long availablePhysicalMemory;

      @Column
      private int cpuNum;

      @Column
      private int cpuSockets;

      @Column
      private int cpuCoreNum;

      // --- 新增字段 ---
      @Column
      private double cpuOverprovisioningRatio;

      @Column
      private double memoryOverprovisioningRatio;

      @Column
      private long totalDisk;

      @Column
      private long availableDisk;

      @Column
      private long reservedMemory;

      @Column
      @Enumerated(EnumType.STRING)
      private CapacityState capacityState;
  }
  ```

- [ ] **Step 12.3: 添加 DDL**

  ```sql
  CREATE TABLE IF NOT EXISTS `PhysicalServerCapacityVO` (
      `uuid` VARCHAR(32) NOT NULL,
      `totalCpu` BIGINT NOT NULL DEFAULT 0,
      `availableCpu` BIGINT NOT NULL DEFAULT 0,
      `totalMemory` BIGINT NOT NULL DEFAULT 0,
      `availableMemory` BIGINT NOT NULL DEFAULT 0,
      `totalPhysicalMemory` BIGINT NOT NULL DEFAULT 0,
      `availablePhysicalMemory` BIGINT NOT NULL DEFAULT 0,
      `cpuNum` INT NOT NULL DEFAULT 0,
      `cpuSockets` INT NOT NULL DEFAULT 0,
      `cpuCoreNum` INT NOT NULL DEFAULT 0,
      `cpuOverprovisioningRatio` DOUBLE NOT NULL DEFAULT 1.0,
      `memoryOverprovisioningRatio` DOUBLE NOT NULL DEFAULT 1.0,
      `totalDisk` BIGINT NOT NULL DEFAULT 0,
      `availableDisk` BIGINT NOT NULL DEFAULT 0,
      `reservedMemory` BIGINT NOT NULL DEFAULT 0,
      `capacityState` VARCHAR(32) NOT NULL DEFAULT 'Initialized',
      PRIMARY KEY (`uuid`),
      CONSTRAINT `fkPhysicalServerCapacityVOPhysicalServerVO` FOREIGN KEY (`uuid`)
          REFERENCES `PhysicalServerVO` (`uuid`) ON DELETE CASCADE
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8;
  ```

- [ ] **Step 12.4: 编译验证**

**Acceptance Criteria (FR-013 AC):**
- [ ] PhysicalServerCapacityVO 是真表，字段与 HostCapacityVO 对齐
- [ ] FK CASCADE 到 PhysicalServerVO
- [ ] capacityState 枚举包含 5 个状态

**Commit:** `<feature>[server]: PhysicalServerCapacityVO capacity table`

---

### Task 13: PhysicalServerCapacityUpdater 悲观锁扣减

**Goal:** 实现容量更新器，使用 PESSIMISTIC_WRITE 锁 + @DeadlockAutoRestart，支持 SHARED/EXCLUSIVE/READONLY 三模式。

**Depends on:** Task 12

**FRs covered:** FR-015 (PhysicalServerCapacityUpdater), FR-014 (SchedulingMode 三模式)

**Files to create/modify:**
- `plugin/physicalServer/src/main/java/org/zstack/server/PhysicalServerCapacityUpdater.java` — **新建**

#### Steps

- [ ] **Step 13.1: 创建 PhysicalServerCapacityUpdater**

  参考 `HostCapacityUpdater` 的 lockCapacity/run 模式:

  ```java
  @Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
  public class PhysicalServerCapacityUpdater {
      @Autowired
      private DatabaseFacade dbf;

      private String serverUuid;
      private PhysicalServerCapacityVO capacityVO;
      private PhysicalServerCapacityVO originalCopy;

      public PhysicalServerCapacityUpdater(String serverUuid) {
          this.serverUuid = serverUuid;
      }

      /**
       * Lock capacity row with PESSIMISTIC_WRITE, run closure, persist.
       * NOTE: @Transactional and @DeadlockAutoRestart must NOT be on the same method (NFR-010).
       */
      @DeadlockAutoRestart
      public boolean run(PhysicalServerCapacityUpdaterRunnable runnable) {
          return new SQLBatchWithReturn<Boolean>() {
              @Override
              protected Boolean scripts() {
                  capacityVO = lockCapacity();
                  if (capacityVO == null) return false;
                  originalCopy = copy(capacityVO);
                  boolean ret = runnable.run(capacityVO);
                  if (ret) {
                      dbf.getEntityManager().merge(capacityVO);
                  }
                  return ret;
              }
          }.execute();
      }

      private PhysicalServerCapacityVO lockCapacity() {
          return dbf.getEntityManager().find(
              PhysicalServerCapacityVO.class, serverUuid, LockModeType.PESSIMISTIC_WRITE);
      }
  }

  @FunctionalInterface
  public interface PhysicalServerCapacityUpdaterRunnable {
      boolean run(PhysicalServerCapacityVO cap);
  }
  ```

- [ ] **Step 13.2: 实现按 SchedulingMode 的扣减逻辑**

  ```java
  // INTERNAL_EXCLUSIVE: 分配时清零所有可用量
  // INTERNAL_SHARED: 正常扣减 requiredCpu/requiredMemory
  // EXTERNAL_READONLY: 容量消耗计入但不通过此 updater 扣减（由 K8s 同步）
  ```

- [ ] **Step 13.3: 编译验证**

**Acceptance Criteria (FR-015 AC):**
- [ ] 并发扣减不产生超卖（PESSIMISTIC_WRITE 锁）
- [ ] 死锁自动重试（@DeadlockAutoRestart）
- [ ] @Transactional 和 @DeadlockAutoRestart 不在同一方法上 (NFR-010)

**Commit:** `<feature>[server]: PhysicalServerCapacityUpdater with pessimistic locking`

---

### Task 14: ServerCapacityOverProvisioningManager + 容量重计算

**Goal:** 实现超分比管理器和容量重计算消息处理。

**Depends on:** Task 12, Task 13

**FRs covered:** FR-016 (OverProvisioningManager), FR-017 (RecalculateCapacity), FR-021 (ServerReservedCapacityExtensionPoint, Should Have)

**Files to create/modify:**
- `header/src/main/java/org/zstack/header/server/ServerCapacityOverProvisioningManager.java` — **新建** 接口
- `header/src/main/java/org/zstack/header/server/RecalculatePhysicalServerCapacityMsg.java` — **新建**
- `header/src/main/java/org/zstack/header/server/ServerReservedCapacityExtensionPoint.java` — **新建** (Should Have)
- `plugin/physicalServer/src/main/java/org/zstack/server/ServerCapacityOverProvisioningManagerImpl.java` — **新建**
- `plugin/physicalServer/src/main/java/org/zstack/server/PhysicalServerManagerImpl.java` — **修改** 处理重计算消息

#### Steps

- [ ] **Step 14.1: 创建 ServerCapacityOverProvisioningManager 接口**

  ```java
  package org.zstack.header.server;

  public interface ServerCapacityOverProvisioningManager {
      double getCpuOverProvisioningRatio(String serverUuid);
      double getMemoryOverProvisioningRatio(String serverUuid);
      void setCpuOverProvisioningRatio(String serverUuid, double ratio);
      void setMemoryOverProvisioningRatio(String serverUuid, double ratio);
  }
  ```

  参考 `HostCpuOverProvisioningManager` / `HostMemoryOverProvisioningManager` 实现模式。

- [ ] **Step 14.2: 实现 ServerCapacityOverProvisioningManagerImpl**

  支持全局 GlobalConfig 默认值 + per-server SystemTag 覆盖。

- [ ] **Step 14.3: 创建 RecalculatePhysicalServerCapacityMsg**

  ```java
  // 内部消息（非 API），支持指定 serverUuid 或全量重计算
  public class RecalculatePhysicalServerCapacityMsg extends NeedReplyMessage {
      private String serverUuid;  // null = 全量重计算
  }
  ```

- [ ] **Step 14.4: 实现重计算逻辑（税收模式）**

  ```
  Available = Total - Σ(业务税 via RoleProvider.getCapacityConsumption()) - Σ(系统税 via ServerReservedCapacityExtensionPoint)
  ```

- [ ] **Step 14.5: 创建 ServerReservedCapacityExtensionPoint (Should Have)**

  ```java
  package org.zstack.header.server;

  /**
   * Extension point for system-level capacity reservation (OS overhead, Ceph agent, etc.)
   * PRD FR-021 (Should Have)
   */
  public interface ServerReservedCapacityExtensionPoint {
      CapacityUsage getReservedCapacity(String serverUuid);
  }
  ```

- [ ] **Step 14.6: 注册 GlobalConfig**

  ```java
  // PhysicalServerGlobalConfig.java
  @GlobalConfigDef(defaultValue = "1", category = "physicalServer")
  public static GlobalConfig CPU_OVER_PROVISIONING_RATIO;

  @GlobalConfigDef(defaultValue = "1", category = "physicalServer")
  public static GlobalConfig MEMORY_OVER_PROVISIONING_RATIO;
  ```

- [ ] **Step 14.7: 编译验证**

**Acceptance Criteria:**
- [ ] FR-016: 全局 GlobalConfig + per-server 覆盖
- [ ] FR-016: 修改超分比触发容量重计算
- [ ] FR-017: 重计算后容量数据准确（税收模式）
- [ ] FR-021: ServerReservedCapacityExtensionPoint 可被外部模块实现

**Commit:** `<feature>[server]: capacity over-provisioning manager and recalculation`

---

## Phase 3 (Week 4): 统一分配引擎

### Task 15: AllocateServerMsg + ServerAllocatorChain

**Goal:** 实现统一分配消息和 Flow 责任链。

**Depends on:** Task 12, Task 13, Task 14

**FRs covered:** FR-018 (AllocateServerMsg), FR-019 (ServerAllocatorChain), FR-020 (FilterExtensionPoint)

**Files to create/modify:**
- `header/src/main/java/org/zstack/header/server/AllocateServerMsg.java` — **新建**
- `header/src/main/java/org/zstack/header/server/AllocateServerReply.java` — **新建**
- `header/src/main/java/org/zstack/header/server/ServerAllocatorSpec.java` — **新建** 分配规格
- `header/src/main/java/org/zstack/header/server/ServerAllocatorFilterExtensionPoint.java` — **新建**
- `plugin/physicalServer/src/main/java/org/zstack/server/allocator/ServerAllocatorChain.java` — **新建**
- `plugin/physicalServer/src/main/java/org/zstack/server/allocator/ServerAllocatorChainBuilder.java` — **新建**
- `plugin/physicalServer/src/main/java/org/zstack/server/allocator/AbstractServerAllocatorFlow.java` — **新建**
- `plugin/physicalServer/src/main/java/org/zstack/server/allocator/ZoneFilterFlow.java` — **新建**
- `plugin/physicalServer/src/main/java/org/zstack/server/allocator/ClusterFilterFlow.java` — **新建**
- `plugin/physicalServer/src/main/java/org/zstack/server/allocator/PoolFilterFlow.java` — **新建**
- `plugin/physicalServer/src/main/java/org/zstack/server/allocator/RoleTypeFilterFlow.java` — **新建**
- `plugin/physicalServer/src/main/java/org/zstack/server/allocator/StatusFilterFlow.java` — **新建**
- `plugin/physicalServer/src/main/java/org/zstack/server/allocator/CapacityFilterFlow.java` — **新建**
- `plugin/physicalServer/src/main/java/org/zstack/server/allocator/SortFilterFlow.java` — **新建**
- `plugin/physicalServer/src/main/java/org/zstack/server/allocator/ExtensionFilterFlow.java` — **新建**

#### Steps

- [ ] **Step 15.1: 创建 AllocateServerMsg**

  ```java
  package org.zstack.header.server;

  public class AllocateServerMsg extends NeedReplyMessage {
      private ServerRoleType requiredRoleType;
      private long requiredCpu;
      private long requiredMemory;
      private String clusterUuid;
      private String zoneUuid;
      private String serverUuid;      // 指定分配
      private String poolUuid;        // 池内分配
      private SchedulingMode schedulingMode;
      private List<String> avoidServerUuids;
  }
  ```

- [ ] **Step 15.2: 创建 ServerAllocatorSpec（分配规格 DTO）**

  仿 `HostAllocatorSpec`，不含 `originalMessage` 引用（两阶段薄适配模式）。

- [ ] **Step 15.3: 创建 AbstractServerAllocatorFlow 基类**

  参考 `AbstractHostAllocatorFlow`:

  ```java
  public abstract class AbstractServerAllocatorFlow {
      protected List<PhysicalServerVO> candidates;

      public abstract void allocate(ServerAllocatorSpec spec);
      // next(), skip(), fail() 方法由 Chain 驱动
  }
  ```

- [ ] **Step 15.4: 实现 7 个基础 Flow**

  | Flow | 功能 | 参考 |
  |------|------|------|
  | ZoneFilterFlow | 按 zoneUuid 过滤 | DesignatedHostAllocatorFlow |
  | ClusterFilterFlow | 按 clusterUuid 过滤（通过 RoleVO.roleUuid -> HostVO.clusterUuid JOIN） | |
  | PoolFilterFlow | 按 poolUuid 过滤 | |
  | RoleTypeFilterFlow | 按 requiredRoleType 过滤，确保有 Active 角色 | |
  | StatusFilterFlow | 排除 Disabled/Maintenance 状态 | HostStateAndHypervisorAllocatorFlow |
  | CapacityFilterFlow | 按 requiredCpu/Memory 过滤 | HostCapacityAllocatorFlow |
  | SortFilterFlow | 随机 / 最少 VM 优先排序 | RandomSortFlow |

- [ ] **Step 15.5: 实现 ExtensionFilterFlow（FR-020）**

  ```java
  public class ExtensionFilterFlow extends AbstractServerAllocatorFlow {
      @Autowired
      private PluginRegistry pluginRgty;

      @Override
      public void allocate(ServerAllocatorSpec spec) {
          for (ServerAllocatorFilterExtensionPoint ext :
                  pluginRgty.getExtensionList(ServerAllocatorFilterExtensionPoint.class)) {
              candidates = ext.filterServers(candidates, spec);
          }
          next(candidates);
      }
  }
  ```

- [ ] **Step 15.6: 创建 ServerAllocatorChain 和 Builder**

  参考 `HostAllocatorChain`/`HostAllocatorChainBuilder`。Chain 驱动 Flow 列表依次执行。

- [ ] **Step 15.7: 在 PhysicalServerManagerImpl 中处理 AllocateServerMsg**

  ```java
  private void handleLocalMessage(Message msg) {
      if (msg instanceof AllocateServerMsg) {
          handle((AllocateServerMsg) msg);
      } else {
          bus.dealWithUnknownMessage(msg);
      }
  }
  ```

- [ ] **Step 15.8: 集成测试**

  测试场景:
  1. 按 zoneUuid + roleType 分配，返回符合条件的 PhysicalServer
  2. 容量不足时分配失败
  3. Maintenance 状态服务器被排除
  4. EXCLUSIVE 模式分配后清零可用量

- [ ] **Step 15.9: 编译验证**

**Acceptance Criteria:**
- [ ] FR-018: 可正确分配四种角色类型，失败返回明确错误码
- [ ] FR-019: Flow 可通过 Spring 注入扩展，每个 Flow 独立可测试
- [ ] FR-020: 扩展点可被外部模块实现

**Commit:** `<feature>[server]: AllocateServerMsg and ServerAllocatorChain with 7 flows`

---

## Phase 4 (Week 5): 兼容桥 + 迁移 + 统一查询

### Task 16: CompatibilityBridge 薄代理

**Goal:** 拦截 AllocateHostMsg，转换为 AllocateServerMsg，结果反向映射回 HostInventory。

**Depends on:** Task 15

**FRs covered:** FR-028 (CompatibilityBridge), FR-029 (Feature Switch, Could Have)

**Files to create/modify:**
- `plugin/physicalServer/src/main/java/org/zstack/server/compat/CompatibilityBridge.java` — **新建**
- `plugin/physicalServer/src/main/java/org/zstack/server/compat/PhysicalServerGlobalConfig.java` — **新建**（如 Task 14 未创建）
- `conf/springConfigXml/PhysicalServerManager.xml` — **修改** 注册 Bridge

#### Steps

- [ ] **Step 16.1: 创建 PhysicalServerGlobalConfig（特性开关）**

  ```java
  @GlobalConfigDef(defaultValue = "false", category = "physicalServer",
      description = "Enable CompatibilityBridge to route AllocateHostMsg through new engine")
  public static GlobalConfig COMPAT_BRIDGE_ENABLED;

  // 按角色类型独立控制 (Could Have FR-029)
  @GlobalConfigDef(defaultValue = "false", category = "physicalServer")
  public static GlobalConfig COMPAT_BRIDGE_KVM_ENABLED;

  @GlobalConfigDef(defaultValue = "false", category = "physicalServer")
  public static GlobalConfig COMPAT_BRIDGE_BM_ENABLED;
  ```

- [ ] **Step 16.2: 创建 CompatibilityBridge**

  ```java
  package org.zstack.server.compat;

  /**
   * Thin proxy that intercepts AllocateHostMsg and routes through ServerAllocatorChain (FR-028).
   *
   * Design: Two-phase thin adaptation:
   * Phase 1: ServerAllocatorChain does generic filtering (zone, cluster, pool, roleType, capacity)
   * Phase 2: Result injected back as candidateHostUuids into original AllocateHostMsg,
   *          then existing HostAllocatorChain handles role-specific parameters
   *
   * This approach preserves 100% backward compatibility (NFR-001).
   */
  public class CompatibilityBridge implements HostAllocatorStrategyExtensionPoint {
      // 1. 检查 GlobalConfig 开关
      // 2. 将 AllocateHostMsg 字段映射到 AllocateServerMsg
      // 3. 调用 ServerAllocatorChain
      // 4. 将结果 PhysicalServerVO -> RoleVO.roleUuid -> candidateHostUuids
      // 5. 注入到原始消息，继续走现有 HostAllocatorChain
  }
  ```

- [ ] **Step 16.3: 编译验证**

**Acceptance Criteria (FR-028/029 AC):**
- [ ] AllocateHostMsg 透传到新引擎后行为不变
- [ ] 开关可运行时修改，无需重启
- [ ] 开关关闭时 100% 走旧路径

**Commit:** `<feature>[server]: CompatibilityBridge with feature switch`

---

### Task 17: 存量数据迁移脚本

**Goal:** 编写幂等 SQL 迁移脚本，为所有存量 HostVO 生成对应的 PhysicalServerVO + RoleVO + CapacityVO，并将 HostCapacityVO 改为 VIEW。

**Depends on:** Task 12, Task 16

**FRs covered:** FR-030 (Migration Script), FR-013 部分 (HostCapacityVO -> VIEW)

**Files to create/modify:**
- `conf/db/upgrade/V5.5.19__schema.sql` — **新建** 迁移脚本

#### Steps

- [ ] **Step 17.1: 编写幂等迁移脚本**

  ```sql
  -- V5.5.19__schema.sql: Migrate existing hosts to unified model

  -- Step 1: Migrate KVM hosts to PhysicalServerVO
  INSERT IGNORE INTO PhysicalServerVO (uuid, name, zoneUuid, poolUuid, managementIp, state, status, powerStatus, createDate, lastOpDate)
  SELECT
      h.uuid,
      h.name,
      h.zoneUuid,
      COALESCE(csp.poolUuid, 'default-pool'),  -- fallback if no pool assigned
      h.managementIp,
      CASE h.state WHEN 'Enabled' THEN 'Enabled' WHEN 'Disabled' THEN 'Disabled' ELSE 'Enabled' END,
      CASE h.status WHEN 'Connected' THEN 'Connected' WHEN 'Connecting' THEN 'Connecting' ELSE 'Disconnected' END,
      'Unknown',
      h.createDate,
      h.lastOpDate
  FROM HostVO h
  LEFT JOIN ClusterServerPoolRefVO csp ON h.clusterUuid = csp.clusterUuid
  WHERE h.hypervisorType = 'KVM'
  ON DUPLICATE KEY UPDATE lastOpDate = VALUES(lastOpDate);

  -- Step 2: Create PhysicalServerRoleVO for KVM hosts
  INSERT IGNORE INTO PhysicalServerRoleVO (uuid, serverUuid, roleType, roleUuid, schedulingMode, roleStatus, createDate, lastOpDate)
  SELECT
      REPLACE(UUID(), '-', ''),
      h.uuid,
      'KVM_HOST',
      h.uuid,
      'INTERNAL_SHARED',
      'Active',
      NOW(),
      NOW()
  FROM HostVO h
  WHERE h.hypervisorType = 'KVM'
  AND NOT EXISTS (
      SELECT 1 FROM PhysicalServerRoleVO r
      WHERE r.serverUuid = h.uuid AND r.roleType = 'KVM_HOST'
  );

  -- Step 3: Migrate HostCapacityVO data to PhysicalServerCapacityVO
  INSERT IGNORE INTO PhysicalServerCapacityVO
      (uuid, totalCpu, availableCpu, totalMemory, availableMemory,
       totalPhysicalMemory, availablePhysicalMemory, cpuNum, cpuSockets, cpuCoreNum,
       cpuOverprovisioningRatio, memoryOverprovisioningRatio,
       totalDisk, availableDisk, reservedMemory, capacityState)
  SELECT
      hc.uuid,
      hc.totalCpu, hc.availableCpu, hc.totalMemory, hc.availableMemory,
      hc.totalPhysicalMemory, hc.availablePhysicalMemory, hc.cpuNum, hc.cpuSockets, hc.cpuCoreNum,
      1.0, 1.0,
      0, 0, 0, 'Ready'
  FROM HostCapacityVO hc
  INNER JOIN HostVO h ON hc.uuid = h.uuid
  WHERE h.hypervisorType = 'KVM';

  -- Step 4: BM2 Chassis migration (similar pattern)
  -- INSERT IGNORE INTO PhysicalServerVO ... FROM BareMetal2ChassisVO ...
  -- INSERT IGNORE INTO PhysicalServerRoleVO ... roleType='BAREMETAL_V2', schedulingMode='INTERNAL_EXCLUSIVE' ...

  -- Step 5 (DEFERRED to post-validation):
  -- ALTER TABLE HostCapacityVO RENAME TO HostCapacityVO_backup;
  -- CREATE VIEW HostCapacityVO AS SELECT ... FROM PhysicalServerCapacityVO JOIN PhysicalServerRoleVO ...;
  -- NOTE: VIEW migration is HIGH RISK, requires extensive testing with all 47 read paths.
  -- For v1.0, keep both tables in sync. VIEW migration is a separate task.
  ```

- [ ] **Step 17.2: 验证幂等性**

  脚本重复执行不产生重复数据（INSERT IGNORE / ON DUPLICATE KEY UPDATE）。

- [ ] **Step 17.3: 创建默认 ServerPool（如果不存在）**

  迁移脚本需要先创建一个 default ServerPool 用于无 pool 的存量数据。

**Acceptance Criteria (FR-030 AC):**
- [ ] 脚本幂等，重复执行不产生重复数据
- [ ] serialNumber 尽可能从 SystemTag 提取
- [ ] 无 serialNumber 时使用 managementIp + zoneUuid
- [ ] 迁移后 QueryPhysicalServerMsg 可查到所有存量物理机

**Commit:** `<feature>[server]: idempotent migration script for existing hosts`

---

### Task 18: QueryPhysicalServer 增强（role/hardware/capacity joins）

**Goal:** 增强 QueryPhysicalServerMsg，支持关联查询角色列表、硬件汇总、容量信息。

**Depends on:** Task 12, Task 6

**FRs covered:** FR-031 (QueryPhysicalServer with joins)

**Files to create/modify:**
- `header/src/main/java/org/zstack/header/server/PhysicalServerInventory.java` — **修改** 增加嵌套字段
- `plugin/physicalServer/src/main/java/org/zstack/server/PhysicalServerManagerImpl.java` — **修改** 查询后填充关联数据

#### Steps

- [ ] **Step 18.1: 在 PhysicalServerInventory 中增加关联字段**

  ```java
  // 已有基础字段...

  // 新增关联查询字段
  @Queryable(mappingClass = PhysicalServerRoleInventory.class,
      joinColumn = @JoinColumn(name = "serverUuid", referencedColumnName = "uuid"))
  private List<PhysicalServerRoleInventory> roles;

  @Queryable(mappingClass = PhysicalServerHardwareInfoInventory.class,
      joinColumn = @JoinColumn(name = "uuid"))
  private PhysicalServerHardwareInfoInventory hardwareInfo;

  @Queryable(mappingClass = PhysicalServerCapacityInventory.class,
      joinColumn = @JoinColumn(name = "uuid"))
  private PhysicalServerCapacityInventory capacity;
  ```

  注意: 需要创建 `PhysicalServerHardwareInfoInventory` 和 `PhysicalServerCapacityInventory` 如果尚未存在。

- [ ] **Step 18.2: 实现查询后数据填充**

  使用 `InventoryCollectExtensionPoint` 或在 QueryReply 返回前手动填充。

- [ ] **Step 18.3: 编译验证 + 集成测试**

**Acceptance Criteria (FR-031 AC):**
- [ ] 查询结果包含角色列表、硬件汇总、容量信息
- [ ] 支持按 roleType、poolUuid、state 过滤
- [ ] 支持分页、排序

**Commit:** `<feature>[server]: enhanced QueryPhysicalServer with role/hardware/capacity joins`

---

## Phase 5 (Week 6): 电源管理 + 硬件发现 + 扫描

### Task 19: 统一电源管理 API

**Goal:** 实现 PowerManageable 接口，支持 IPMI/Redfish 协议的 powerOn/Off/Reset/Status 操作。

**Depends on:** Task 9 (OOB 凭据)

**FRs covered:** FR-032 (统一电源管理 API)

**Files to create/modify:**
- `header/src/main/java/org/zstack/header/server/PowerManageable.java` — **新建** 接口
- `header/src/main/java/org/zstack/header/server/APIPowerOnPhysicalServerMsg.java` — **新建**
- `header/src/main/java/org/zstack/header/server/APIPowerOnPhysicalServerEvent.java` — **新建**
- `header/src/main/java/org/zstack/header/server/APIPowerOffPhysicalServerMsg.java` — **新建**
- `header/src/main/java/org/zstack/header/server/APIPowerOffPhysicalServerEvent.java` — **新建**
- `header/src/main/java/org/zstack/header/server/APIPowerResetPhysicalServerMsg.java` — **新建**
- `header/src/main/java/org/zstack/header/server/APIPowerResetPhysicalServerEvent.java` — **新建**
- `header/src/main/java/org/zstack/header/server/APIGetPhysicalServerPowerStatusMsg.java` — **新建**
- `header/src/main/java/org/zstack/header/server/APIGetPhysicalServerPowerStatusReply.java` — **新建**
- `plugin/physicalServer/src/main/java/org/zstack/server/power/IpmiPowerManager.java` — **新建**
- `plugin/physicalServer/src/main/java/org/zstack/server/power/RedfishPowerManager.java` — **新建**
- `conf/serviceConfig/physicalServer.xml` — **修改** 增加消息路由

#### Steps

- [ ] **Step 19.1: 创建 PowerManageable 接口**

  ```java
  package org.zstack.header.server;

  public interface PowerManageable {
      void powerOn(String serverUuid, Completion completion);
      void powerOff(String serverUuid, Completion completion);
      void powerReset(String serverUuid, Completion completion);
      void getPowerStatus(String serverUuid, ReturnValueCompletion<PhysicalServerPowerStatus> completion);
  }
  ```

- [ ] **Step 19.2: 创建 4 组 API 消息（PowerOn/Off/Reset/GetStatus）**

- [ ] **Step 19.3: 实现 IpmiPowerManager**

  使用 ipmitool 命令行或 Java IPMI 库:

  ```java
  // ipmitool -I lanplus -H {oobAddress} -p {oobPort} -U {oobUsername} -P {oobPassword} power on
  // ipmitool -I lanplus -H {oobAddress} -p {oobPort} -U {oobUsername} -P {oobPassword} power status
  ```

- [ ] **Step 19.4: 实现 RedfishPowerManager（基础骨架）**

  Redfish REST API: `POST /redfish/v1/Systems/1/Actions/ComputerSystem.Reset`

- [ ] **Step 19.5: 在 PhysicalServerManagerImpl 中路由电源 API**

  根据 `oobManagementType` 选择 IPMI 或 Redfish 实现。操作结果更新 `PhysicalServerPowerStatus`。

- [ ] **Step 19.6: 集成测试 + 编译验证**

**Acceptance Criteria (FR-032 AC):**
- [ ] 支持 IPMI 和 Redfish 两种协议
- [ ] 电源操作结果更新 PhysicalServerPowerStatus
- [ ] 无 OOB 凭据时返回明确错误

**Commit:** `<feature>[server]: unified power management API (IPMI/Redfish)`

---

### Task 20: 统一硬件发现 API (Should Have)

**Goal:** 实现硬件信息采集接口，通过 OOB（IPMI FRU）或 agent 获取硬件数据。

**Depends on:** Task 9 (OOB 凭据), FR-003/FR-004 (HardwareInfo/DetailVO 已存在)

**FRs covered:** FR-033 (统一硬件发现 API, Should Have), FR-003 补完, FR-004 补完

**Files to create/modify:**
- `header/src/main/java/org/zstack/header/server/HardwareDiscoverable.java` — **新建** 接口
- `header/src/main/java/org/zstack/header/server/APIDiscoverPhysicalServerHardwareMsg.java` — **新建**
- `header/src/main/java/org/zstack/header/server/APIDiscoverPhysicalServerHardwareEvent.java` — **新建**
- `plugin/physicalServer/src/main/java/org/zstack/server/hardware/IpmiHardwareDiscoverer.java` — **新建**
- `plugin/physicalServer/src/main/java/org/zstack/server/hardware/AgentHardwareDiscoverer.java` — **新建**

#### Steps

- [ ] **Step 20.1: 创建 HardwareDiscoverable 接口**

  ```java
  package org.zstack.header.server;

  public interface HardwareDiscoverable {
      /** Discover hardware info and write to HardwareInfoVO/HardwareDetailVO */
      void discover(String serverUuid, Completion completion);
  }
  ```

- [ ] **Step 20.2: 创建 APIDiscoverPhysicalServerHardwareMsg**

- [ ] **Step 20.3: 实现 IpmiHardwareDiscoverer**

  通过 `ipmitool fru print` 获取:
  - Board Mfg (manufacturer), Board Product (model)
  - Board Serial (serialNumber)

- [ ] **Step 20.4: 实现 AgentHardwareDiscoverer（通过已有 KVM agent）**

  读取 `/sys/class/dmi/id/product_serial`, `/proc/cpuinfo`, `/proc/meminfo` 等。
  需要目标服务器有已连接的 KVM Host 角色。

- [ ] **Step 20.5: 写入 HardwareInfoVO 和 HardwareDetailVO**

  采集数据后:
  - 更新 PhysicalServerHardwareInfoVO（汇总）
  - 批量 INSERT PhysicalServerHardwareDetailVO（明细）

- [ ] **Step 20.6: 编译验证**

**Acceptance Criteria (FR-033/003/004 AC):**
- [ ] 支持 OOB 和 agent 两种采集方式
- [ ] 采集结果写入 HardwareInfoVO/HardwareDetailVO
- [ ] 可手动触发或在首次连接时自动触发
- [ ] 通过 QueryPhysicalServerMsg 可查询硬件汇总 (FR-003)
- [ ] 支持按 detailType 查询明细 (FR-004)

**Commit:** `<feature>[server]: unified hardware discovery API (IPMI FRU + agent)`

---

### Task 21: APIScanPhysicalServersMsg (LongJob)

**Goal:** 实现 IPMI 网络扫描 LongJob，自动发现网段内的物理服务器。

**Depends on:** Task 19 (IpmiPowerManager)

**FRs covered:** FR-034 (APIScanPhysicalServersMsg)

**Files to create/modify:**
- `header/src/main/java/org/zstack/header/server/APIScanPhysicalServersMsg.java` — **新建**
- `header/src/main/java/org/zstack/header/server/APIScanPhysicalServersEvent.java` — **新建**
- `plugin/physicalServer/src/main/java/org/zstack/server/ScanPhysicalServersLongJob.java` — **新建**
- `conf/springConfigXml/PhysicalServerManager.xml` — **修改** 注册 LongJob

#### Steps

- [ ] **Step 21.1: 创建 APIScanPhysicalServersMsg**

  ```java
  @LongJobFor(ScanPhysicalServersLongJob.class)
  public class APIScanPhysicalServersMsg extends APICreateMessage {
      @APIParam
      private String zoneUuid;

      @APIParam
      private String poolUuid;

      @APIParam(required = false)
      private String ipRange;      // e.g. "192.168.1.1-192.168.1.254"

      @APIParam(required = false)
      private String oobUsername;   // default IPMI credentials for scanning

      @APIParam(required = false)
      private String oobPassword;
  }
  ```

- [ ] **Step 21.2: 实现 ScanPhysicalServersLongJob**

  参考 `AddKVMHostLongJob`:

  ```java
  public class ScanPhysicalServersLongJob implements LongJob {
      @Override
      public void start(LongJobVO job, ReturnValueCompletion<LongJobInventory> completion) {
          // 1. 解析 IP 范围
          // 2. 对每个 IP 尝试 IPMI 连接 (ipmitool -I lanplus -H {ip} power status)
          // 3. 连接成功的 IP 创建 PhysicalServerVO
          // 4. 触发硬件发现
          // 5. 报告进度
      }
  }
  ```

- [ ] **Step 21.3: 注册 LongJob Spring Bean**

- [ ] **Step 21.4: 编译验证**

**Acceptance Criteria (FR-034 AC):**
- [ ] 扫描指定 IP 范围，发现 IPMI 可达的物理服务器
- [ ] 自动创建 PhysicalServerVO 并填充 OOB 信息
- [ ] 作为 LongJob 执行，支持进度报告和取消

**Commit:** `<feature>[server]: ScanPhysicalServers LongJob for IPMI network discovery`

---

## 附录

### FR-024: BM1 RoleProvider (Could Have - 延后)

**Priority:** Could Have - 当前迭代不实现

**Rationale:** BM2 优先策略。SPI 接口设计已保证 BM1 可无修改接入:
- ServerRoleType 枚举保留 BAREMETAL_V1
- SPI 不含 BM2 特有假设
- ProvisionNetworkVO 保留 STANDALONE_PXE 类型
- RoleMatchContext 包含 oobAddress 字段

**Future implementation:**
- 监听 BaremetalChassis 创建/删除
- IPMI 信息从 ChassisVO 同步
- SchedulingMode = INTERNAL_EXCLUSIVE

---

### 任务依赖图

```
Week 1 (Done)
  Tasks 1-5: CRUD + SDK + Tests

Week 2 (Phase 1: Role SPI)
  Task 6: SPI 接口 + 互斥逻辑
  Task 7: Attach/Detach API  ───> depends on Task 6
  Task 8: KVM RoleProvider    ───> depends on Task 6
  Task 9: EncryptColumn + Tracker (并行)
  Task 10: BM2 RoleProvider   ───> depends on Task 6, 8
  Task 11: Container Provider ───> depends on Task 6, 8

Week 3 (Phase 2: Capacity)
  Task 12: CapacityVO         ───> depends on Tasks 1-5
  Task 13: CapacityUpdater    ───> depends on Task 12
  Task 14: OverProvisioning   ───> depends on Task 12, 13

Week 4 (Phase 3: Allocator)
  Task 15: AllocatorChain     ───> depends on Task 12, 13, 14

Week 5 (Phase 4: Compat + Migration)
  Task 16: CompatBridge       ───> depends on Task 15
  Task 17: Migration SQL      ───> depends on Task 12, 16
  Task 18: Query Enhancement  ───> depends on Task 12, 6

Week 6 (Phase 5: Power + Discovery + Scan)
  Task 19: Power Management   ───> depends on Task 9
  Task 20: Hardware Discovery  ───> depends on Task 9 (Should Have)
  Task 21: Scan LongJob       ───> depends on Task 19
```

### 并行执行建议

以下任务可并行执行:
- Task 6 + Task 9 + Task 12（三个无依赖任务，Phase 1 和 Phase 2 可交叉启动）
- Task 10 + Task 11（BM2 和 Container Provider 互不依赖）
- Task 17 + Task 18（迁移和查询增强互不依赖）
- Task 19 + Task 20（电源和发现互不依赖）

### NFR 验证检查点

| 检查点 | 时机 | 验证方式 |
|--------|------|---------|
| NFR-001 API 兼容 | Task 16 完成后 | 现有 API 测试全通过 |
| NFR-002 零回归 | 每个 Task 完成后 | 编译 + 单元测试 |
| NFR-003 JOIN 性能 | Task 15 完成后 | 分配链路 benchmark < 5ms |
| NFR-004 死锁安全 | Task 13 完成后 | @DeadlockAutoRestart 配置验证 |
| NFR-005 Blame 保护 | 每个 Task | git diff 检查无不必要重命名 |
| NFR-006 SPI 可扩展 | Task 11 完成后 | 三个 Provider 验证 |
| NFR-007 迁移幂等 | Task 17 完成后 | 脚本重复执行验证 |
| NFR-009 技术栈 | 每次编译 | Java 8 编译通过 |
| NFR-010 事务约束 | Task 13 | AspectJ 编译通过 |

### 编译验证命令速查

```bash
# 全量编译（每个 Phase 结束后必须执行）
JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64 mvn clean install -DskipTests -Dmaven.repo.local=.m2/repository

# Premium 编译（BM2 相关 Task）
./runMavenProfile premium

# SDK 生成（新 API 消息后必须执行）
./runMavenProfile sdk

# 单模块快速编译
JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64 mvn clean install -pl plugin/physicalServer -am -DskipTests -Dmaven.repo.local=.m2/repository

# 运行集成测试
JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64 mvn test -pl testlib -Dtest=PhysicalServerTest -Dmaven.repo.local=.m2/repository
```
