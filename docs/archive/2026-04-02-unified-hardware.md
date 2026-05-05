# 统一硬件管理实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现 ZStack 统一硬件管理框架，将 KVM/BM2/Container 三种角色的物理服务器管理统一到 PhysicalServerVO 抽象层

**Architecture:** 三层架构（Physical → Role → Consumer），PhysicalServerCapacityVO 真表 + HostCapacityVO VIEW，PhysicalServerRoleProvider SPI 驱动角色接入

**Tech Stack:** Java 8, Spring 5.2.25, Hibernate 5.3.26, MySQL, Groovy (tests)

---

## Day 0 产出概览（已完成，不需要重复）

以下文件已在 `feature/unifi-host-dev` 分支上完成，共 37 个 Java 文件 + 1 个 DDL：

- `header/src/main/java/org/zstack/header/server/` — 37 个文件（VO、枚举、API 消息、Inventory）
- `conf/db/upgrade/V5.5.18__schema.sql` — 7 张表的 DDL
- `plugin/physicalServer/` — 模块骨架（PhysicalServerManagerImpl placeholder）

已有数据模型：
- PhysicalServerAO/VO, PhysicalServerRoleVO, PhysicalServerHardwareInfoVO, PhysicalServerHardwareDetailVO
- ServerPoolVO, ClusterServerPoolRefVO
- PhysicalServerProvisionNetworkVO, PhysicalServerProvisionNetworkClusterRefVO
- 枚举：ServerRoleType, SchedulingMode, PhysicalServerState/Status/PowerStatus, HardwareDetailType, ProvisionNetworkType, ServerPoolState
- API 消息：Create/Delete/Update/Query ServerPool, Create/Delete/Update/Query/ChangeState PhysicalServer
- Inventory：PhysicalServerInventory, PhysicalServerRoleInventory, ServerPoolInventory
- 常量：PhysicalServerConstant (SERVICE_ID="physicalServer")

---

## Week 1 (Day 1-5): 基础设施 + CRUD

### Task 1: 编译环境 + SDK 生成通路

**Goal:** 确保 zstack-unifi-host 项目能正确编译，physicalServer 模块纳入 Maven reactor，SDK 生成通路可用。

**Depends on:** Day 0 产出

**Files to create/modify:**
- `plugin/pom.xml` — 添加 physicalServer 子模块
- `conf/springConfigXml/PhysicalServerManager.xml` — Spring Bean 定义
- `conf/serviceConfig/physicalServer.xml` — 服务消息路由
- `conf/serviceConfig/serverPool.xml` — ServerPool 服务消息路由

#### Steps

- [ ] **Step 1.1: 将 physicalServer 模块纳入 Maven reactor**

  编辑 `plugin/pom.xml`，在 `<modules>` 列表中添加 `<module>physicalServer</module>`。

  ```xml
  <module>physicalServer</module>
  ```

  验证：
  ```bash
  cd /home/mj/zstack-workspace/zstack-unifi-host && mvn clean install -pl plugin/physicalServer -am -DskipTests 2>&1 | tail -5
  ```
  预期输出包含 `BUILD SUCCESS`。

- [ ] **Step 1.2: 创建 Spring Bean 配置 `conf/springConfigXml/PhysicalServerManager.xml`**

  ```xml
  <?xml version="1.0" encoding="UTF-8"?>
  <beans xmlns="http://www.springframework.org/schema/beans"
      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
      xmlns:zstack="http://zstack.org/schema/zstack"
      xsi:schemaLocation="http://www.springframework.org/schema/beans
           http://www.springframework.org/schema/beans/spring-beans-3.0.xsd
           http://zstack.org/schema/zstack
           http://zstack.org/schema/zstack/plugin.xsd"
      default-init-method="init" default-destroy-method="destroy">

      <bean id="PhysicalServerManager" class="org.zstack.server.PhysicalServerManagerImpl">
          <zstack:plugin>
              <zstack:extension interface="org.zstack.header.Component" />
              <zstack:extension interface="org.zstack.header.Service" />
          </zstack:plugin>
      </bean>

      <bean id="PhysicalServerApiInterceptor" class="org.zstack.server.PhysicalServerApiInterceptor">
          <zstack:plugin>
              <zstack:extension interface="org.zstack.header.apimediator.ApiMessageInterceptor" />
          </zstack:plugin>
      </bean>
  </beans>
  ```

- [ ] **Step 1.3: 创建服务消息路由 `conf/serviceConfig/physicalServer.xml`**

  ```xml
  <?xml version="1.0" encoding="UTF-8"?>
  <service xmlns="http://zstack.org/schema/zstack">
      <id>physicalServer</id>
      <interceptor>PhysicalServerApiInterceptor</interceptor>

      <message>
          <name>org.zstack.header.server.APICreatePhysicalServerMsg</name>
      </message>

      <message>
          <name>org.zstack.header.server.APIDeletePhysicalServerMsg</name>
      </message>

      <message>
          <name>org.zstack.header.server.APIUpdatePhysicalServerMsg</name>
      </message>

      <message>
          <name>org.zstack.header.server.APIChangePhysicalServerStateMsg</name>
      </message>

      <message>
          <name>org.zstack.header.server.APIQueryPhysicalServerMsg</name>
          <serviceId>query</serviceId>
      </message>
  </service>
  ```

- [ ] **Step 1.4: 创建 ServerPool 服务消息路由 `conf/serviceConfig/serverPool.xml`**

  ```xml
  <?xml version="1.0" encoding="UTF-8"?>
  <service xmlns="http://zstack.org/schema/zstack">
      <id>physicalServer</id>
      <interceptor>PhysicalServerApiInterceptor</interceptor>

      <message>
          <name>org.zstack.header.server.APICreateServerPoolMsg</name>
      </message>

      <message>
          <name>org.zstack.header.server.APIDeleteServerPoolMsg</name>
      </message>

      <message>
          <name>org.zstack.header.server.APIUpdateServerPoolMsg</name>
      </message>

      <message>
          <name>org.zstack.header.server.APIQueryServerPoolMsg</name>
          <serviceId>query</serviceId>
      </message>
  </service>
  ```

- [ ] **Step 1.5: 全量编译验证**

  ```bash
  cd /home/mj/zstack-workspace/zstack-unifi-host && mvn clean install -DskipTests 2>&1 | tail -20
  ```
  预期：BUILD SUCCESS，无编译错误。

- [ ] **Step 1.6: SDK 生成通路验证**

  ```bash
  cd /home/mj/zstack-workspace/zstack-unifi-host && ./runMavenProfile sdk 2>&1 | tail -10
  ```
  预期：sdk 模块生成成功，`sdk/src/main/java/org/zstack/sdk/` 下出现 `CreateServerPoolAction.java`、`CreatePhysicalServerAction.java` 等。

- [ ] **Step 1.7: Commit**

  ```
  feat(physicalServer): wire physicalServer module into Maven reactor and Spring context
  ```

---

### Task 2: PhysicalServerManagerImpl Service 骨架

**Goal:** 将 PhysicalServerManagerImpl 扩展为完整的 Service 实现，能处理 API 消息路由。

**Depends on:** Task 1

**Files to create/modify:**
- `plugin/physicalServer/src/main/java/org/zstack/server/PhysicalServerManagerImpl.java` — 完整 Service 实现
- `plugin/physicalServer/src/main/java/org/zstack/server/PhysicalServerApiInterceptor.java` — API 拦截器

#### Steps

- [ ] **Step 2.1: 实现 PhysicalServerManagerImpl Service 骨架**

  编辑 `plugin/physicalServer/src/main/java/org/zstack/server/PhysicalServerManagerImpl.java`：

  ```java
  package org.zstack.server;

  import org.springframework.beans.factory.annotation.Autowired;
  import org.zstack.core.cloudbus.CloudBus;
  import org.zstack.core.cloudbus.MessageSafe;
  import org.zstack.core.db.DatabaseFacade;
  import org.zstack.core.db.Q;
  import org.zstack.core.db.SQL;
  import org.zstack.header.AbstractService;
  import org.zstack.header.message.APIMessage;
  import org.zstack.header.message.Message;
  import org.zstack.header.server.*;
  import org.zstack.utils.Utils;
  import org.zstack.utils.logging.CLogger;

  public class PhysicalServerManagerImpl extends AbstractService {
      private static final CLogger logger = Utils.getLogger(PhysicalServerManagerImpl.class);

      @Autowired
      private CloudBus bus;
      @Autowired
      private DatabaseFacade dbf;

      @Override
      @MessageSafe
      public void handleMessage(Message msg) {
          if (msg instanceof APIMessage) {
              handleApiMessage((APIMessage) msg);
          } else {
              handleLocalMessage(msg);
          }
      }

      private void handleApiMessage(APIMessage msg) {
          if (msg instanceof APICreateServerPoolMsg) {
              handle((APICreateServerPoolMsg) msg);
          } else if (msg instanceof APIDeleteServerPoolMsg) {
              handle((APIDeleteServerPoolMsg) msg);
          } else if (msg instanceof APIUpdateServerPoolMsg) {
              handle((APIUpdateServerPoolMsg) msg);
          } else if (msg instanceof APICreatePhysicalServerMsg) {
              handle((APICreatePhysicalServerMsg) msg);
          } else if (msg instanceof APIDeletePhysicalServerMsg) {
              handle((APIDeletePhysicalServerMsg) msg);
          } else if (msg instanceof APIUpdatePhysicalServerMsg) {
              handle((APIUpdatePhysicalServerMsg) msg);
          } else if (msg instanceof APIChangePhysicalServerStateMsg) {
              handle((APIChangePhysicalServerStateMsg) msg);
          } else {
              bus.dealWithUnknownMessage(msg);
          }
      }

      private void handleLocalMessage(Message msg) {
          bus.dealWithUnknownMessage(msg);
      }

      @Override
      public String getId() {
          return bus.makeLocalServiceId(PhysicalServerConstant.SERVICE_ID);
      }

      @Override
      public boolean start() {
          return true;
      }

      @Override
      public boolean stop() {
          return true;
      }

      // --- API handlers (stubs, filled in Task 3 & 4) ---

      private void handle(APICreateServerPoolMsg msg) {
          // Task 3
      }

      private void handle(APIDeleteServerPoolMsg msg) {
          // Task 3
      }

      private void handle(APIUpdateServerPoolMsg msg) {
          // Task 3
      }

      private void handle(APICreatePhysicalServerMsg msg) {
          // Task 4
      }

      private void handle(APIDeletePhysicalServerMsg msg) {
          // Task 4
      }

      private void handle(APIUpdatePhysicalServerMsg msg) {
          // Task 4
      }

      private void handle(APIChangePhysicalServerStateMsg msg) {
          // Task 4
      }
  }
  ```

- [ ] **Step 2.2: 创建 PhysicalServerApiInterceptor**

  创建 `plugin/physicalServer/src/main/java/org/zstack/server/PhysicalServerApiInterceptor.java`：

  ```java
  package org.zstack.server;

  import org.springframework.beans.factory.annotation.Autowired;
  import org.zstack.core.db.DatabaseFacade;
  import org.zstack.core.db.Q;
  import org.zstack.header.apimediator.ApiMessageInterceptionException;
  import org.zstack.header.apimediator.ApiMessageInterceptor;
  import org.zstack.header.message.APIMessage;
  import org.zstack.header.server.*;
  import org.zstack.header.zone.ZoneVO;
  import org.zstack.header.zone.ZoneVO_;

  import static org.zstack.core.Platform.argerr;

  public class PhysicalServerApiInterceptor implements ApiMessageInterceptor {
      @Autowired
      private DatabaseFacade dbf;

      @Override
      public APIMessage intercept(APIMessage msg) throws ApiMessageInterceptionException {
          if (msg instanceof APICreateServerPoolMsg) {
              validate((APICreateServerPoolMsg) msg);
          } else if (msg instanceof APICreatePhysicalServerMsg) {
              validate((APICreatePhysicalServerMsg) msg);
          } else if (msg instanceof APIDeleteServerPoolMsg) {
              validate((APIDeleteServerPoolMsg) msg);
          }
          return msg;
      }

      private void validate(APICreateServerPoolMsg msg) {
          // Zone existence validated by @APIParam(resourceType = ZoneVO.class)
      }

      private void validate(APICreatePhysicalServerMsg msg) {
          // Validate poolUuid belongs to same zone
          if (msg.getPoolUuid() != null && msg.getZoneUuid() != null) {
              ServerPoolVO pool = dbf.findByUuid(msg.getPoolUuid(), ServerPoolVO.class);
              if (pool != null && !pool.getZoneUuid().equals(msg.getZoneUuid())) {
                  throw new ApiMessageInterceptionException(argerr(
                      "ServerPool[uuid:%s] belongs to Zone[uuid:%s], but PhysicalServer specifies Zone[uuid:%s]",
                      msg.getPoolUuid(), pool.getZoneUuid(), msg.getZoneUuid()
                  ));
              }
          }
      }

      private void validate(APIDeleteServerPoolMsg msg) {
          long count = Q.New(PhysicalServerVO.class)
              .eq(PhysicalServerAO_.poolUuid, msg.getUuid())
              .count();
          if (count > 0) {
              throw new ApiMessageInterceptionException(argerr(
                  "Cannot delete ServerPool[uuid:%s]: %d PhysicalServer(s) still belong to it. " +
                  "Please remove or reassign them first.", msg.getUuid(), count
              ));
          }
      }
  }
  ```

- [ ] **Step 2.3: 编译验证**

  ```bash
  cd /home/mj/zstack-workspace/zstack-unifi-host && mvn clean install -pl plugin/physicalServer -am -DskipTests 2>&1 | tail -5
  ```
  预期：BUILD SUCCESS。

- [ ] **Step 2.4: Commit**

  ```
  feat(physicalServer): implement PhysicalServerManagerImpl service skeleton and API interceptor
  ```

---

### Task 3: ServerPool CRUD 实现

**Goal:** 完整实现 ServerPool 的 Create/Delete/Update API handler，包括 Cluster 关联/解除。

**Depends on:** Task 2

**Files to modify:**
- `plugin/physicalServer/src/main/java/org/zstack/server/PhysicalServerManagerImpl.java` — 填充 ServerPool handler

**Files to create:**
- `header/src/main/java/org/zstack/header/server/APIAttachClusterToServerPoolMsg.java`
- `header/src/main/java/org/zstack/header/server/APIAttachClusterToServerPoolEvent.java`
- `header/src/main/java/org/zstack/header/server/APIDetachClusterFromServerPoolMsg.java`
- `header/src/main/java/org/zstack/header/server/APIDetachClusterFromServerPoolEvent.java`

#### Steps

- [ ] **Step 3.1: 实现 ServerPool Create handler**

  在 PhysicalServerManagerImpl 中填充 `handle(APICreateServerPoolMsg)`：

  ```java
  private void handle(APICreateServerPoolMsg msg) {
      ServerPoolVO vo = new ServerPoolVO();
      vo.setUuid(msg.getResourceUuid() != null ? msg.getResourceUuid() : Platform.getUuid());
      vo.setName(msg.getName());
      vo.setZoneUuid(msg.getZoneUuid());
      vo.setPhysicalLocation(msg.getPhysicalLocation());
      vo.setNetworkTopology(msg.getNetworkTopology());
      vo.setState(ServerPoolState.Enabled);
      vo = dbf.persistAndRefresh(vo);

      // Register resource for RBAC
      acntMgr.createAccountResourceRef(msg.getSession().getAccountUuid(), vo.getUuid(), ServerPoolVO.class);

      APICreateServerPoolEvent evt = new APICreateServerPoolEvent(msg.getId());
      evt.setInventory(ServerPoolInventory.valueOf(vo));
      bus.publish(evt);
  }
  ```

- [ ] **Step 3.2: 实现 ServerPool Delete handler**

  ```java
  private void handle(APIDeleteServerPoolMsg msg) {
      dbf.removeByPrimaryKey(msg.getUuid(), ServerPoolVO.class);
      APIDeleteServerPoolEvent evt = new APIDeleteServerPoolEvent(msg.getId());
      bus.publish(evt);
  }
  ```

- [ ] **Step 3.3: 实现 ServerPool Update handler**

  ```java
  private void handle(APIUpdateServerPoolMsg msg) {
      ServerPoolVO vo = dbf.findByUuid(msg.getUuid(), ServerPoolVO.class);
      boolean update = false;
      if (msg.getName() != null) {
          vo.setName(msg.getName());
          update = true;
      }
      if (msg.getDescription() != null) {
          vo.setDescription(msg.getDescription());
          update = true;
      }
      // physicalLocation, networkTopology 同理
      if (update) {
          vo = dbf.updateAndRefresh(vo);
      }

      APIUpdateServerPoolEvent evt = new APIUpdateServerPoolEvent(msg.getId());
      evt.setInventory(ServerPoolInventory.valueOf(vo));
      bus.publish(evt);
  }
  ```

- [ ] **Step 3.4: 创建 AttachClusterToServerPool API 消息**

  创建 `header/src/main/java/org/zstack/header/server/APIAttachClusterToServerPoolMsg.java`：

  ```java
  package org.zstack.header.server;

  import org.springframework.http.HttpMethod;
  import org.zstack.header.cluster.ClusterVO;
  import org.zstack.header.identity.Action;
  import org.zstack.header.message.APIMessage;
  import org.zstack.header.message.APIParam;
  import org.zstack.header.rest.RestRequest;

  @Action(category = PhysicalServerConstant.SERVER_POOL_ACTION_CATEGORY)
  @RestRequest(
      path = "/server-pools/{poolUuid}/clusters/{clusterUuid}",
      method = HttpMethod.POST,
      responseClass = APIAttachClusterToServerPoolEvent.class
  )
  public class APIAttachClusterToServerPoolMsg extends APIMessage {
      @APIParam(resourceType = ServerPoolVO.class)
      private String poolUuid;

      @APIParam(resourceType = ClusterVO.class)
      private String clusterUuid;

      // getters/setters + __example__()
  }
  ```

  创建对应的 Event 类，以及 DetachCluster 的 Msg/Event。

- [ ] **Step 3.5: 实现 AttachCluster/DetachCluster handler**

  ```java
  private void handle(APIAttachClusterToServerPoolMsg msg) {
      ClusterServerPoolRefVO ref = new ClusterServerPoolRefVO();
      ref.setClusterUuid(msg.getClusterUuid());
      ref.setPoolUuid(msg.getPoolUuid());
      dbf.persist(ref);

      APIAttachClusterToServerPoolEvent evt = new APIAttachClusterToServerPoolEvent(msg.getId());
      evt.setInventory(ServerPoolInventory.valueOf(dbf.findByUuid(msg.getPoolUuid(), ServerPoolVO.class)));
      bus.publish(evt);
  }

  private void handle(APIDetachClusterFromServerPoolMsg msg) {
      SQL.New(ClusterServerPoolRefVO.class)
          .eq(ClusterServerPoolRefVO_.clusterUuid, msg.getClusterUuid())
          .eq(ClusterServerPoolRefVO_.poolUuid, msg.getPoolUuid())
          .delete();

      APIDetachClusterFromServerPoolEvent evt = new APIDetachClusterFromServerPoolEvent(msg.getId());
      bus.publish(evt);
  }
  ```

- [ ] **Step 3.6: 添加 Attach/Detach 消息到 serviceConfig**

  更新 `conf/serviceConfig/serverPool.xml`，添加 `APIAttachClusterToServerPoolMsg` 和 `APIDetachClusterFromServerPoolMsg`。

- [ ] **Step 3.7: 编译 + 验证**

  ```bash
  cd /home/mj/zstack-workspace/zstack-unifi-host && mvn clean install -DskipTests 2>&1 | tail -5
  ```
  预期：BUILD SUCCESS。

- [ ] **Step 3.8: Commit**

  ```
  feat(serverPool): implement ServerPool CRUD and Cluster attach/detach APIs
  ```

---

### Task 4: PhysicalServer CRUD 实现

**Goal:** 完整实现 PhysicalServer 的 Create/Delete/Update/ChangeState API handler。

**Depends on:** Task 3

**Files to modify:**
- `plugin/physicalServer/src/main/java/org/zstack/server/PhysicalServerManagerImpl.java`

#### Steps

- [ ] **Step 4.1: 实现 PhysicalServer Create handler**

  ```java
  private void handle(APICreatePhysicalServerMsg msg) {
      PhysicalServerVO vo = new PhysicalServerVO();
      vo.setUuid(msg.getResourceUuid() != null ? msg.getResourceUuid() : Platform.getUuid());
      vo.setName(msg.getName());
      vo.setZoneUuid(msg.getZoneUuid());
      vo.setPoolUuid(msg.getPoolUuid());
      vo.setManagementIp(msg.getManagementIp());
      vo.setArchitecture(msg.getArchitecture());
      vo.setSerialNumber(msg.getSerialNumber());
      vo.setManufacturer(msg.getManufacturer());
      vo.setModel(msg.getModel());
      vo.setState(PhysicalServerState.Enabled);
      vo.setStatus(PhysicalServerStatus.Connecting);
      vo.setPowerStatus(PhysicalServerPowerStatus.Unknown);
      vo.setOobManagementType(msg.getOobManagementType());
      vo.setOobAddress(msg.getOobAddress());
      vo.setOobPort(msg.getOobPort());
      vo.setOobUsername(msg.getOobUsername());
      vo.setOobPassword(msg.getOobPassword());
      vo = dbf.persistAndRefresh(vo);

      acntMgr.createAccountResourceRef(msg.getSession().getAccountUuid(), vo.getUuid(), PhysicalServerVO.class);

      APICreatePhysicalServerEvent evt = new APICreatePhysicalServerEvent(msg.getId());
      evt.setInventory(PhysicalServerInventory.valueOf(vo));
      bus.publish(evt);
  }
  ```

- [ ] **Step 4.2: 实现 PhysicalServer Delete handler（带 Active 角色检查）**

  ```java
  private void handle(APIDeletePhysicalServerMsg msg) {
      // Check no Active roles
      long activeRoleCount = Q.New(PhysicalServerRoleVO.class)
          .eq(PhysicalServerRoleVO_.serverUuid, msg.getUuid())
          .eq(PhysicalServerRoleVO_.roleStatus, "Active")
          .count();
      if (activeRoleCount > 0) {
          throw new OperationFailureException(operr(
              "Cannot delete PhysicalServer[uuid:%s]: %d active role(s) exist. Delete associated roles first.",
              msg.getUuid(), activeRoleCount
          ));
      }

      // Hard delete (no EO pattern)
      SQL.New(PhysicalServerHardwareDetailVO.class)
          .eq(PhysicalServerHardwareDetailVO_.serverUuid, msg.getUuid())
          .delete();
      dbf.removeByPrimaryKey(msg.getUuid(), PhysicalServerHardwareInfoVO.class);
      SQL.New(PhysicalServerRoleVO.class)
          .eq(PhysicalServerRoleVO_.serverUuid, msg.getUuid())
          .delete();
      dbf.removeByPrimaryKey(msg.getUuid(), PhysicalServerVO.class);

      APIDeletePhysicalServerEvent evt = new APIDeletePhysicalServerEvent(msg.getId());
      bus.publish(evt);
  }
  ```

- [ ] **Step 4.3: 实现 PhysicalServer Update handler**

  ```java
  private void handle(APIUpdatePhysicalServerMsg msg) {
      PhysicalServerVO vo = dbf.findByUuid(msg.getUuid(), PhysicalServerVO.class);
      boolean update = false;
      if (msg.getName() != null) { vo.setName(msg.getName()); update = true; }
      if (msg.getManagementIp() != null) { vo.setManagementIp(msg.getManagementIp()); update = true; }
      // ... 其余可更新字段
      if (update) {
          vo = dbf.updateAndRefresh(vo);
      }

      APIUpdatePhysicalServerEvent evt = new APIUpdatePhysicalServerEvent(msg.getId());
      evt.setInventory(PhysicalServerInventory.valueOf(vo));
      bus.publish(evt);
  }
  ```

- [ ] **Step 4.4: 实现 ChangeState handler（三层状态机）**

  ```java
  private void handle(APIChangePhysicalServerStateMsg msg) {
      PhysicalServerVO vo = dbf.findByUuid(msg.getUuid(), PhysicalServerVO.class);
      PhysicalServerState next = PhysicalServerState.valueOf(msg.getStateEvent());

      // State transition validation
      PhysicalServerState current = vo.getState();
      // Maintenance 状态下不参与分配（在分配引擎中检查）
      vo.setState(next);
      vo = dbf.updateAndRefresh(vo);

      APIChangePhysicalServerStateEvent evt = new APIChangePhysicalServerStateEvent(msg.getId());
      evt.setInventory(PhysicalServerInventory.valueOf(vo));
      bus.publish(evt);
  }
  ```

- [ ] **Step 4.5: 编译验证**

  ```bash
  cd /home/mj/zstack-workspace/zstack-unifi-host && mvn clean install -pl plugin/physicalServer -am -DskipTests 2>&1 | tail -5
  ```

- [ ] **Step 4.6: Commit**

  ```
  feat(physicalServer): implement PhysicalServer CRUD and ChangeState APIs
  ```

---

### Task 5: ProvisionNetwork CRUD + 集成测试骨架

**Goal:** 实现 ProvisionNetwork CRUD API，以及创建集成测试 Groovy 骨架。

**Depends on:** Task 4

**Files to create:**
- `header/src/main/java/org/zstack/header/server/APICreateProvisionNetworkMsg.java`
- `header/src/main/java/org/zstack/header/server/APICreateProvisionNetworkEvent.java`
- `header/src/main/java/org/zstack/header/server/APIDeleteProvisionNetworkMsg.java`
- `header/src/main/java/org/zstack/header/server/APIDeleteProvisionNetworkEvent.java`
- `header/src/main/java/org/zstack/header/server/APIQueryProvisionNetworkMsg.java`
- `header/src/main/java/org/zstack/header/server/APIQueryProvisionNetworkReply.java`
- `header/src/main/java/org/zstack/header/server/APIAttachProvisionNetworkToClusterMsg.java`
- `header/src/main/java/org/zstack/header/server/APIAttachProvisionNetworkToClusterEvent.java`
- `header/src/main/java/org/zstack/header/server/APIDetachProvisionNetworkFromClusterMsg.java`
- `header/src/main/java/org/zstack/header/server/APIDetachProvisionNetworkFromClusterEvent.java`
- `header/src/main/java/org/zstack/header/server/PhysicalServerProvisionNetworkInventory.java`
- `conf/serviceConfig/provisionNetwork.xml`
- `test/src/test/groovy/org/zstack/test/integration/server/ServerPoolCrudCase.groovy` — 集成测试骨架

#### Steps

- [ ] **Step 5.1: 创建 ProvisionNetwork API 消息类**

  创建 `APICreateProvisionNetworkMsg`：
  ```java
  package org.zstack.header.server;

  import org.springframework.http.HttpMethod;
  import org.zstack.header.identity.Action;
  import org.zstack.header.message.APICreateMessage;
  import org.zstack.header.message.APIParam;
  import org.zstack.header.rest.RestRequest;
  import org.zstack.header.zone.ZoneVO;

  @Action(category = PhysicalServerConstant.ACTION_CATEGORY)
  @RestRequest(
      path = "/provision-networks",
      method = HttpMethod.POST,
      parameterName = "params",
      responseClass = APICreateProvisionNetworkEvent.class
  )
  public class APICreateProvisionNetworkMsg extends APICreateMessage {
      @APIParam(maxLength = 255)
      private String name;

      @APIParam(required = false, maxLength = 2048)
      private String description;

      @APIParam(resourceType = ZoneVO.class)
      private String zoneUuid;

      @APIParam(validValues = {"STANDALONE_PXE", "GATEWAY_PXE"})
      private String type;

      @APIParam(required = false)
      private String dhcpInterface;

      @APIParam(required = false)
      private String dhcpRangeStartIp;

      @APIParam(required = false)
      private String dhcpRangeEndIp;

      @APIParam(required = false)
      private String dhcpRangeNetmask;

      @APIParam(required = false)
      private String dhcpRangeGateway;

      // getters/setters + __example__()
  }
  ```

  类似地创建 Delete/Query/Attach/Detach 消息和 Event 类、Inventory 类。

- [ ] **Step 5.2: 实现 ProvisionNetwork handler**

  在 PhysicalServerManagerImpl 中添加 ProvisionNetwork 的 Create/Delete/AttachCluster/DetachCluster handler。

  ```java
  private void handle(APICreateProvisionNetworkMsg msg) {
      PhysicalServerProvisionNetworkVO vo = new PhysicalServerProvisionNetworkVO();
      vo.setUuid(msg.getResourceUuid() != null ? msg.getResourceUuid() : Platform.getUuid());
      vo.setName(msg.getName());
      vo.setDescription(msg.getDescription());
      vo.setZoneUuid(msg.getZoneUuid());
      vo.setType(ProvisionNetworkType.valueOf(msg.getType()));
      vo.setDhcpInterface(msg.getDhcpInterface());
      vo.setDhcpRangeStartIp(msg.getDhcpRangeStartIp());
      vo.setDhcpRangeEndIp(msg.getDhcpRangeEndIp());
      vo.setDhcpRangeNetmask(msg.getDhcpRangeNetmask());
      vo.setDhcpRangeGateway(msg.getDhcpRangeGateway());
      vo.setState("Enabled");
      vo = dbf.persistAndRefresh(vo);

      acntMgr.createAccountResourceRef(msg.getSession().getAccountUuid(),
          vo.getUuid(), PhysicalServerProvisionNetworkVO.class);

      APICreateProvisionNetworkEvent evt = new APICreateProvisionNetworkEvent(msg.getId());
      evt.setInventory(PhysicalServerProvisionNetworkInventory.valueOf(vo));
      bus.publish(evt);
  }
  ```

- [ ] **Step 5.3: 添加 ProvisionNetwork serviceConfig**

  创建 `conf/serviceConfig/provisionNetwork.xml`：
  ```xml
  <?xml version="1.0" encoding="UTF-8"?>
  <service xmlns="http://zstack.org/schema/zstack">
      <id>physicalServer</id>
      <interceptor>PhysicalServerApiInterceptor</interceptor>

      <message>
          <name>org.zstack.header.server.APICreateProvisionNetworkMsg</name>
      </message>
      <message>
          <name>org.zstack.header.server.APIDeleteProvisionNetworkMsg</name>
      </message>
      <message>
          <name>org.zstack.header.server.APIQueryProvisionNetworkMsg</name>
          <serviceId>query</serviceId>
      </message>
      <message>
          <name>org.zstack.header.server.APIAttachProvisionNetworkToClusterMsg</name>
      </message>
      <message>
          <name>org.zstack.header.server.APIDetachProvisionNetworkFromClusterMsg</name>
      </message>
  </service>
  ```

- [ ] **Step 5.4: 创建集成测试骨架**

  创建 `test/src/test/groovy/org/zstack/test/integration/server/ServerPoolCrudCase.groovy`：

  ```groovy
  package org.zstack.test.integration.server

  import org.zstack.sdk.ServerPoolInventory
  import org.zstack.sdk.PhysicalServerInventory
  import org.zstack.test.integration.kvm.KvmTest
  import org.zstack.testlib.EnvSpec
  import org.zstack.testlib.SubCase

  class ServerPoolCrudCase extends SubCase {
      EnvSpec env

      @Override
      void setup() {
          useSpring(KvmTest.springSpec)
      }

      @Override
      void environment() {
          env = makeEnv {
              zone {
                  name = "zone"
              }
          }
      }

      @Override
      void test() {
          env.create {
              testCreateServerPool()
              testCreatePhysicalServer()
              testDeletePhysicalServer()
              testDeleteServerPool()
          }
      }

      void testCreateServerPool() {
          def zone = env.inventoryByName("zone")
          def pool = createServerPool {
              name = "pool-1"
              zoneUuid = zone.uuid
              physicalLocation = "DC1-Rack-A1"
          } as ServerPoolInventory

          assert pool.name == "pool-1"
          assert pool.zoneUuid == zone.uuid
      }

      void testCreatePhysicalServer() {
          // Test PhysicalServer creation with pool association
      }

      void testDeletePhysicalServer() {
          // Test deletion with Active role check
      }

      void testDeleteServerPool() {
          // Test deletion blocked when PhysicalServers exist
      }

      @Override
      void clean() {
          env.delete()
      }
  }
  ```

- [ ] **Step 5.5: 编译验证**

  ```bash
  cd /home/mj/zstack-workspace/zstack-unifi-host && mvn clean install -DskipTests 2>&1 | tail -5
  ```

- [ ] **Step 5.6: Commit**

  ```
  feat(provisionNetwork): implement ProvisionNetwork CRUD APIs and integration test skeleton
  ```

---

## Week 2 (Day 6-11): Role SPI + 角色适配

### Task 6: PhysicalServerRoleProvider SPI 接口定义 + 互斥逻辑

**Goal:** 定义 PhysicalServerRoleProvider SPI 接口和角色互斥检查逻辑。

**Depends on:** Task 4

**Files to create:**
- `header/src/main/java/org/zstack/header/server/PhysicalServerRoleProvider.java` — SPI 接口
- `header/src/main/java/org/zstack/header/server/PhysicalServerRoleStatus.java` — 角色状态枚举
- `header/src/main/java/org/zstack/header/server/RegisterRoleMsg.java` — 内部消息
- `header/src/main/java/org/zstack/header/server/RegisterRoleReply.java`

**Files to modify:**
- `plugin/physicalServer/src/main/java/org/zstack/server/PhysicalServerManagerImpl.java` — 添加 registerRole() 方法

#### Steps

- [ ] **Step 6.1: 定义 PhysicalServerRoleProvider SPI 接口**

  ```java
  package org.zstack.header.server;

  /**
   * SPI for role modules (KVM, BM2, Container) to integrate with
   * the unified physical server management framework.
   *
   * Each role module implements this interface and registers as a Spring Bean.
   * New role types only need to implement this SPI + register bean, no core code changes.
   */
  public interface PhysicalServerRoleProvider {
      /**
       * @return the role type this provider manages
       */
      ServerRoleType getRoleType();

      /**
       * @return the scheduling mode for this role type
       */
      SchedulingMode getSchedulingMode();

      /**
       * Called when a PhysicalServer is created that matches this role's resource.
       * The provider should sync any needed data.
       */
      void onPhysicalServerCreated(PhysicalServerVO server);

      /**
       * Called when a PhysicalServer is about to be deleted.
       * The provider should clean up any role-specific state.
       */
      void onPhysicalServerDeleted(PhysicalServerVO server);

      /**
       * Return the role-specific inventory for display in unified queries.
       * @param roleUuid the uuid of the role resource (e.g., HostVO.uuid)
       * @return role-specific inventory object, or null if not available
       */
      Object getRoleInventory(String roleUuid);
  }
  ```

- [ ] **Step 6.2: 定义角色状态枚举**

  ```java
  package org.zstack.header.server;

  public enum PhysicalServerRoleStatus {
      Active,
      Stale
  }
  ```

- [ ] **Step 6.3: 实现角色互斥检查和 registerRole() 核心逻辑**

  在 PhysicalServerManagerImpl 中添加：

  ```java
  /**
   * Core role registration logic with scheduling mode exclusion check.
   *
   * Exclusion matrix:
   * - INTERNAL_EXCLUSIVE conflicts with any existing INTERNAL_* role
   * - INTERNAL_SHARED conflicts with existing INTERNAL_EXCLUSIVE
   * - EXTERNAL_READONLY is compatible with everything (except duplicate roleType)
   * - Same roleType always rejected (UNIQUE constraint)
   */
  public PhysicalServerRoleVO registerRole(String serverUuid, ServerRoleType roleType,
                                            String roleUuid, SchedulingMode schedulingMode) {
      // 1. Check duplicate roleType
      boolean exists = Q.New(PhysicalServerRoleVO.class)
          .eq(PhysicalServerRoleVO_.serverUuid, serverUuid)
          .eq(PhysicalServerRoleVO_.roleType, roleType)
          .isExists();
      if (exists) {
          throw new OperationFailureException(operr(
              "PhysicalServer[uuid:%s] already has role[type:%s]", serverUuid, roleType));
      }

      // 2. Check scheduling mode exclusion
      List<PhysicalServerRoleVO> existingRoles = Q.New(PhysicalServerRoleVO.class)
          .eq(PhysicalServerRoleVO_.serverUuid, serverUuid)
          .eq(PhysicalServerRoleVO_.roleStatus, PhysicalServerRoleStatus.Active.toString())
          .list();

      for (PhysicalServerRoleVO existingRole : existingRoles) {
          checkSchedulingModeExclusion(existingRole.getSchedulingMode(), schedulingMode,
              existingRole.getRoleType(), roleType, serverUuid);
      }

      // 3. Create role mapping
      PhysicalServerRoleVO roleVO = new PhysicalServerRoleVO();
      roleVO.setUuid(Platform.getUuid());
      roleVO.setServerUuid(serverUuid);
      roleVO.setRoleType(roleType);
      roleVO.setRoleUuid(roleUuid);
      roleVO.setSchedulingMode(schedulingMode);
      roleVO.setRoleStatus(PhysicalServerRoleStatus.Active.toString());
      roleVO = dbf.persistAndRefresh(roleVO);

      logger.info(String.format("Registered role[type:%s, uuid:%s] for PhysicalServer[uuid:%s]",
          roleType, roleUuid, serverUuid));
      return roleVO;
  }

  private void checkSchedulingModeExclusion(SchedulingMode existing, SchedulingMode incoming,
                                             ServerRoleType existingType, ServerRoleType incomingType,
                                             String serverUuid) {
      // EXTERNAL_READONLY is always compatible
      if (existing == SchedulingMode.EXTERNAL_READONLY || incoming == SchedulingMode.EXTERNAL_READONLY) {
          return;
      }

      // INTERNAL_EXCLUSIVE conflicts with any INTERNAL_*
      if (existing == SchedulingMode.INTERNAL_EXCLUSIVE || incoming == SchedulingMode.INTERNAL_EXCLUSIVE) {
          throw new OperationFailureException(operr(
              "Scheduling mode conflict on PhysicalServer[uuid:%s]: " +
              "existing role[type:%s, mode:%s] conflicts with new role[type:%s, mode:%s]. " +
              "INTERNAL_EXCLUSIVE cannot coexist with other INTERNAL modes.",
              serverUuid, existingType, existing, incomingType, incoming
          ));
      }
  }
  ```

- [ ] **Step 6.4: 实现 serialNumber 三级降级匹配**

  ```java
  /**
   * Three-level degradation matching for finding existing PhysicalServerVO:
   * Level 1: serialNumber (highest priority)
   * Level 2: oobAddress + zoneUuid
   * Level 3: managementIp + zoneUuid (lowest priority)
   *
   * @return matched PhysicalServerVO or null if no match
   */
  public PhysicalServerVO matchExistingServer(String zoneUuid, String serialNumber,
                                               String oobAddress, String managementIp) {
      // Level 1: serialNumber match
      if (serialNumber != null && !isInvalidSerialNumber(serialNumber)) {
          PhysicalServerVO matched = Q.New(PhysicalServerVO.class)
              .eq(PhysicalServerAO_.zoneUuid, zoneUuid)
              .eq(PhysicalServerAO_.serialNumber, serialNumber)
              .find();
          if (matched != null) {
              logger.debug(String.format("Matched PhysicalServer[uuid:%s] by serialNumber[%s]",
                  matched.getUuid(), serialNumber));
              return matched;
          }
      }

      // Level 2: oobAddress + zoneUuid
      if (oobAddress != null) {
          PhysicalServerVO matched = Q.New(PhysicalServerVO.class)
              .eq(PhysicalServerAO_.zoneUuid, zoneUuid)
              .eq(PhysicalServerAO_.oobAddress, oobAddress)
              .find();
          if (matched != null) {
              logger.debug(String.format("Matched PhysicalServer[uuid:%s] by oobAddress[%s]",
                  matched.getUuid(), oobAddress));
              return matched;
          }
      }

      // Level 3: managementIp + zoneUuid
      if (managementIp != null) {
          PhysicalServerVO matched = Q.New(PhysicalServerVO.class)
              .eq(PhysicalServerAO_.zoneUuid, zoneUuid)
              .eq(PhysicalServerAO_.managementIp, managementIp)
              .find();
          if (matched != null) {
              logger.debug(String.format("Matched PhysicalServer[uuid:%s] by managementIp[%s]",
                  matched.getUuid(), managementIp));
              return matched;
          }
      }

      return null;
  }

  private static final Set<String> INVALID_SERIAL_NUMBERS = new HashSet<>(Arrays.asList(
      "Not Specified", "None", "NA", "N/A", "Default string",
      "To Be Filled By O.E.M.", "System Serial Number", "0123456789",
      "00000000", "empty", ""
  ));

  private boolean isInvalidSerialNumber(String serialNumber) {
      return serialNumber == null
          || serialNumber.trim().isEmpty()
          || INVALID_SERIAL_NUMBERS.contains(serialNumber.trim());
  }
  ```

- [ ] **Step 6.5: 编译验证**

  ```bash
  cd /home/mj/zstack-workspace/zstack-unifi-host && mvn clean install -pl plugin/physicalServer -am -DskipTests 2>&1 | tail -5
  ```

- [ ] **Step 6.6: Commit**

  ```
  feat(roleSPI): define PhysicalServerRoleProvider SPI, scheduling mode exclusion check, and serialNumber matching
  ```

---

### Task 7: AttachRole / DetachRole API

**Goal:** 实现角色绑定/解绑 API 消息和 handler（供 RoleProvider 钩子调用和管理员手动操作）。

**Depends on:** Task 6

**Files to create:**
- `header/src/main/java/org/zstack/header/server/APIAttachRoleToPhysicalServerMsg.java`
- `header/src/main/java/org/zstack/header/server/APIAttachRoleToPhysicalServerEvent.java`
- `header/src/main/java/org/zstack/header/server/APIDetachRoleFromPhysicalServerMsg.java`
- `header/src/main/java/org/zstack/header/server/APIDetachRoleFromPhysicalServerEvent.java`

#### Steps

- [ ] **Step 7.1: 创建 AttachRole API 消息**

  ```java
  @Action(category = PhysicalServerConstant.ACTION_CATEGORY)
  @RestRequest(
      path = "/physical-servers/{serverUuid}/roles",
      method = HttpMethod.POST,
      parameterName = "params",
      responseClass = APIAttachRoleToPhysicalServerEvent.class
  )
  public class APIAttachRoleToPhysicalServerMsg extends APIMessage {
      @APIParam(resourceType = PhysicalServerVO.class)
      private String serverUuid;

      @APIParam(validValues = {"KVM_HOST", "BAREMETAL_V2", "CONTAINER_HOST"})
      private String roleType;

      @APIParam
      private String roleUuid;

      // getters/setters + __example__()
  }
  ```

- [ ] **Step 7.2: 实现 AttachRole/DetachRole handler**

  ```java
  private void handle(APIAttachRoleToPhysicalServerMsg msg) {
      ServerRoleType roleType = ServerRoleType.valueOf(msg.getRoleType());
      PhysicalServerRoleProvider provider = roleProviders.get(roleType);
      SchedulingMode mode = provider != null ? provider.getSchedulingMode() : SchedulingMode.INTERNAL_SHARED;

      PhysicalServerRoleVO roleVO = registerRole(
          msg.getServerUuid(), roleType, msg.getRoleUuid(), mode);

      APIAttachRoleToPhysicalServerEvent evt = new APIAttachRoleToPhysicalServerEvent(msg.getId());
      evt.setInventory(PhysicalServerRoleInventory.valueOf(roleVO));
      bus.publish(evt);
  }

  private void handle(APIDetachRoleFromPhysicalServerMsg msg) {
      SQL.New(PhysicalServerRoleVO.class)
          .eq(PhysicalServerRoleVO_.serverUuid, msg.getServerUuid())
          .eq(PhysicalServerRoleVO_.roleType, ServerRoleType.valueOf(msg.getRoleType()))
          .set(PhysicalServerRoleVO_.roleStatus, PhysicalServerRoleStatus.Stale.toString())
          .update();

      APIDetachRoleFromPhysicalServerEvent evt = new APIDetachRoleFromPhysicalServerEvent(msg.getId());
      bus.publish(evt);
  }
  ```

- [ ] **Step 7.3: 将 RoleProvider 注册到 Map（Spring 注入）**

  ```java
  @Autowired(required = false)
  private List<PhysicalServerRoleProvider> roleProviderList;

  private Map<ServerRoleType, PhysicalServerRoleProvider> roleProviders = new HashMap<>();

  @Override
  public boolean start() {
      if (roleProviderList != null) {
          for (PhysicalServerRoleProvider provider : roleProviderList) {
              roleProviders.put(provider.getRoleType(), provider);
              logger.info(String.format("Registered PhysicalServerRoleProvider[type:%s, class:%s]",
                  provider.getRoleType(), provider.getClass().getSimpleName()));
          }
      }
      return true;
  }
  ```

- [ ] **Step 7.4: 编译验证 + Commit**

  ```
  feat(roleSPI): implement AttachRole/DetachRole APIs with RoleProvider Spring injection
  ```

---

### Task 8: KVM RoleProvider 实现

**Goal:** 实现 KvmRoleProvider，在 KVM Host PostConnect 时自动创建 PhysicalServerVO + RoleVO。

**Depends on:** Task 7

**Files to create:**
- `plugin/physicalServer/src/main/java/org/zstack/server/role/KvmRoleProvider.java`

**Files to modify:**
- `conf/springConfigXml/PhysicalServerManager.xml` — 注册 KvmRoleProvider Bean

#### Steps

- [ ] **Step 8.1: 创建 KvmRoleProvider**

  ```java
  package org.zstack.server.role;

  import org.springframework.beans.factory.annotation.Autowired;
  import org.zstack.core.db.DatabaseFacade;
  import org.zstack.core.db.Q;
  import org.zstack.header.host.HostVO;
  import org.zstack.header.host.HostVO_;
  import org.zstack.header.server.*;
  import org.zstack.server.PhysicalServerManagerImpl;
  import org.zstack.utils.Utils;
  import org.zstack.utils.logging.CLogger;

  public class KvmRoleProvider implements PhysicalServerRoleProvider {
      private static final CLogger logger = Utils.getLogger(KvmRoleProvider.class);

      @Autowired
      private PhysicalServerManagerImpl physicalServerManager;
      @Autowired
      private DatabaseFacade dbf;

      @Override
      public ServerRoleType getRoleType() {
          return ServerRoleType.KVM_HOST;
      }

      @Override
      public SchedulingMode getSchedulingMode() {
          return SchedulingMode.INTERNAL_SHARED;
      }

      @Override
      public void onPhysicalServerCreated(PhysicalServerVO server) {
          // KVM-specific initialization if needed
      }

      @Override
      public void onPhysicalServerDeleted(PhysicalServerVO server) {
          // KVM-specific cleanup if needed
      }

      @Override
      public Object getRoleInventory(String roleUuid) {
          HostVO host = dbf.findByUuid(roleUuid, HostVO.class);
          return host;  // Or convert to HostInventory
      }

      /**
       * Called from KVM PostConnect hook.
       * Matches or creates PhysicalServerVO, then registers KVM_HOST role.
       */
      public void onKvmHostPostConnect(String hostUuid, String zoneUuid,
                                        String serialNumber, String managementIp) {
          // 1. Match existing PhysicalServer
          PhysicalServerVO server = physicalServerManager.matchExistingServer(
              zoneUuid, serialNumber, null, managementIp);

          if (server == null) {
              // 2. Auto-create PhysicalServerVO
              // Need to find a poolUuid — use the cluster's associated pool if any
              HostVO host = dbf.findByUuid(hostUuid, HostVO.class);
              String poolUuid = findPoolForCluster(host.getClusterUuid());

              if (poolUuid == null) {
                  logger.warn(String.format(
                      "KVM Host[uuid:%s] cluster[uuid:%s] has no associated ServerPool, " +
                      "skipping PhysicalServer auto-creation",
                      hostUuid, host.getClusterUuid()));
                  return;
              }

              server = new PhysicalServerVO();
              server.setUuid(Platform.getUuid());
              server.setName(host.getName());
              server.setZoneUuid(zoneUuid);
              server.setPoolUuid(poolUuid);
              server.setManagementIp(managementIp);
              server.setSerialNumber(serialNumber);
              server.setState(PhysicalServerState.Enabled);
              server.setStatus(PhysicalServerStatus.Connected);
              server.setPowerStatus(PhysicalServerPowerStatus.PowerOn);
              server = dbf.persistAndRefresh(server);

              logger.info(String.format(
                  "Auto-created PhysicalServer[uuid:%s] for KVM Host[uuid:%s]",
                  server.getUuid(), hostUuid));
          }

          // 3. Register role
          physicalServerManager.registerRole(
              server.getUuid(), ServerRoleType.KVM_HOST, hostUuid,
              SchedulingMode.INTERNAL_SHARED);
      }

      private String findPoolForCluster(String clusterUuid) {
          return Q.New(ClusterServerPoolRefVO.class)
              .select(ClusterServerPoolRefVO_.poolUuid)
              .eq(ClusterServerPoolRefVO_.clusterUuid, clusterUuid)
              .findValue();
      }
  }
  ```

- [ ] **Step 8.2: 注册 KvmRoleProvider Bean**

  在 `conf/springConfigXml/PhysicalServerManager.xml` 中添加：
  ```xml
  <bean id="KvmRoleProvider" class="org.zstack.server.role.KvmRoleProvider">
      <zstack:plugin>
          <zstack:extension interface="org.zstack.header.server.PhysicalServerRoleProvider" />
      </zstack:plugin>
  </bean>
  ```

- [ ] **Step 8.3: 创建 KVM PostConnect 扩展点钩子**

  创建 `plugin/physicalServer/src/main/java/org/zstack/server/role/KvmPostConnectForPhysicalServer.java`：

  ```java
  package org.zstack.server.role;

  import org.springframework.beans.factory.annotation.Autowired;
  import org.zstack.compute.host.PostHostConnectExtensionPoint;
  import org.zstack.header.host.HostInventory;
  import org.zstack.utils.Utils;
  import org.zstack.utils.logging.CLogger;

  public class KvmPostConnectForPhysicalServer implements PostHostConnectExtensionPoint {
      private static final CLogger logger = Utils.getLogger(KvmPostConnectForPhysicalServer.class);

      @Autowired
      private KvmRoleProvider kvmRoleProvider;

      @Override
      public void postHostConnect(HostInventory host) {
          String serialNumber = getSerialNumberFromSystemTag(host.getUuid());
          kvmRoleProvider.onKvmHostPostConnect(
              host.getUuid(), host.getZoneUuid(),
              serialNumber, host.getManagementIp());
      }

      private String getSerialNumberFromSystemTag(String hostUuid) {
          SimpleQuery<SystemTagVO> q = dbf.createQuery(SystemTagVO.class);
          q.select(SystemTagVO_.tag);
          q.add(SystemTagVO_.resourceUuid, Op.EQ, hostUuid);
          q.add(SystemTagVO_.tag, Op.LIKE, "system::serial::number::%");
          String tag = q.findValue();
          if (tag == null) {
              return null;
          }
          // tag format: "system::serial::number::{value}"
          String sn = tag.split("::")[3];
          return PhysicalServerManagerImpl.isValidSerialNumber(sn) ? sn : null;
      }
  }
  ```

  注册到 Spring XML：
  ```xml
  <bean id="KvmPostConnectForPhysicalServer" class="org.zstack.server.role.KvmPostConnectForPhysicalServer">
      <zstack:plugin>
          <zstack:extension interface="org.zstack.compute.host.PostHostConnectExtensionPoint" />
      </zstack:plugin>
  </bean>
  ```

- [ ] **Step 8.4: 编译验证 + Commit**

  ```
  feat(kvmRole): implement KvmRoleProvider with PostConnect auto-registration
  ```

---

### Task 9: BM2 RoleProvider 实现

**Goal:** 实现 Bm2RoleProvider，BM2 Chassis 创建时同步创建 PhysicalServerVO + RoleVO。

**Depends on:** Task 7

**Files to create:**
- `plugin/physicalServer/src/main/java/org/zstack/server/role/Bm2RoleProvider.java`

#### Steps

- [ ] **Step 9.1: 创建 Bm2RoleProvider**

  ```java
  package org.zstack.server.role;

  public class Bm2RoleProvider implements PhysicalServerRoleProvider {
      @Override
      public ServerRoleType getRoleType() {
          return ServerRoleType.BAREMETAL_V2;
      }

      @Override
      public SchedulingMode getSchedulingMode() {
          return SchedulingMode.INTERNAL_EXCLUSIVE;
      }

      @Override
      public void onPhysicalServerCreated(PhysicalServerVO server) {
          // BM2-specific: sync hardware info from chassis
      }

      @Override
      public void onPhysicalServerDeleted(PhysicalServerVO server) {
          // BM2-specific cleanup
      }

      @Override
      public Object getRoleInventory(String roleUuid) {
          // Return BareMetal2ChassisVO inventory
          return null;
      }

      /**
       * Called after BM2 chassis hardware discovery succeeds.
       * Matches or creates PhysicalServerVO with INTERNAL_EXCLUSIVE role.
       */
      public void onBm2ChassisDiscovered(String chassisUuid, String zoneUuid,
                                          String serialNumber, String ipmiAddress) {
          PhysicalServerVO server = physicalServerManager.matchExistingServer(
              zoneUuid, serialNumber, ipmiAddress, null);

          if (server == null) {
              // Auto-create from BM2 chassis data
              server = new PhysicalServerVO();
              server.setUuid(Platform.getUuid());
              // ... fill from chassis
              server = dbf.persistAndRefresh(server);
          }

          physicalServerManager.registerRole(
              server.getUuid(), ServerRoleType.BAREMETAL_V2, chassisUuid,
              SchedulingMode.INTERNAL_EXCLUSIVE);
      }
  }
  ```

- [ ] **Step 9.2: 注册 Bm2RoleProvider Bean + 编译验证**

- [ ] **Step 9.3: Commit**

  ```
  feat(bm2Role): implement Bm2RoleProvider with INTERNAL_EXCLUSIVE scheduling
  ```

---

### Task 10: Container RoleProvider 实现

**Goal:** 实现 ContainerRoleProvider，NativeHost 连接时同步创建 EXTERNAL_READONLY 角色。

**Depends on:** Task 7

**Files to create:**
- `plugin/physicalServer/src/main/java/org/zstack/server/role/ContainerRoleProvider.java`

#### Steps

- [ ] **Step 10.1: 创建 ContainerRoleProvider**

  ```java
  package org.zstack.server.role;

  public class ContainerRoleProvider implements PhysicalServerRoleProvider {
      @Override
      public ServerRoleType getRoleType() {
          return ServerRoleType.CONTAINER_HOST;
      }

      @Override
      public SchedulingMode getSchedulingMode() {
          return SchedulingMode.EXTERNAL_READONLY;
      }

      /**
       * Called when NativeHost connects (K8s node sync).
       * K8s systemUUID normalized to match /sys/class/dmi/id/product_serial.
       * EXTERNAL_READONLY is compatible with INTERNAL_SHARED (KVM+Container coexistence).
       */
      public void onNativeHostConnected(String nativeHostUuid, String zoneUuid,
                                         String k8sSystemUuid, String managementIp) {
          String normalizedSerial = normalizeK8sSystemUuid(k8sSystemUuid);

          PhysicalServerVO server = physicalServerManager.matchExistingServer(
              zoneUuid, normalizedSerial, null, managementIp);

          if (server == null) {
              server = new PhysicalServerVO();
              // ... fill from NativeHost data
              server = dbf.persistAndRefresh(server);
          }

          physicalServerManager.registerRole(
              server.getUuid(), ServerRoleType.CONTAINER_HOST, nativeHostUuid,
              SchedulingMode.EXTERNAL_READONLY);
      }

      /**
       * K8s reports systemUUID in uppercase without dashes.
       * DMI product_serial may differ in format.
       * Normalize both to lowercase-with-dashes for comparison.
       */
      private String normalizeK8sSystemUuid(String uuid) {
          if (uuid == null) return null;
          String clean = uuid.toLowerCase().replaceAll("-", "");
          if (clean.length() == 32) {
              return clean.substring(0, 8) + "-" + clean.substring(8, 12) + "-" +
                     clean.substring(12, 16) + "-" + clean.substring(16, 20) + "-" +
                     clean.substring(20);
          }
          return uuid;
      }

      // ... other SPI methods
  }
  ```

- [ ] **Step 10.2: 注册 ContainerRoleProvider Bean + 编译验证**

- [ ] **Step 10.3: Commit**

  ```
  feat(containerRole): implement ContainerRoleProvider with EXTERNAL_READONLY scheduling
  ```

---

### Task 11: Code Review + IPMI 扫描基础

**Goal:** Week 2 收尾，完成角色互斥集成测试、IPMI 基础类。

**Depends on:** Task 8, 9, 10

**Files to create:**
- `test/src/test/groovy/org/zstack/test/integration/server/RoleExclusionCase.groovy`
- `plugin/physicalServer/src/main/java/org/zstack/server/oob/IpmiManager.java` — IPMI 操作封装（骨架）

#### Steps

- [ ] **Step 11.1: 创建角色互斥集成测试**

  ```groovy
  package org.zstack.test.integration.server

  import org.zstack.testlib.SubCase

  class RoleExclusionCase extends SubCase {
      // Test matrix:
      // KVM(SHARED) + BM2(EXCLUSIVE) = REJECT
      // KVM(SHARED) + Container(READONLY) = ALLOW
      // BM2(EXCLUSIVE) + Container(READONLY) = ALLOW
      // BM2(EXCLUSIVE) + KVM(SHARED) = REJECT
      // Same roleType = REJECT

      void testExclusiveConflictsWithShared() {
          // Create server, attach KVM role, then try BM2 → expect failure
      }

      void testReadonlyCompatibleWithShared() {
          // Create server, attach KVM role, then Container → expect success
      }

      void testDuplicateRoleTypeRejected() {
          // Create server, attach KVM, then KVM again → expect failure
      }
  }
  ```

- [ ] **Step 11.2: 创建 IpmiManager 骨架**

  ```java
  package org.zstack.server.oob;

  /**
   * Encapsulates IPMI operations (power control, hardware discovery).
   * Uses ipmitool CLI or Redfish REST API depending on OOB type.
   */
  public class IpmiManager {
      private static final String IPMITOOL = "ipmitool";

      public void powerOn(String oobAddress, int oobPort, String username, String password) {
          ShellUtils.run(String.format(
              "%s -I lanplus -H %s -p %d -U %s -P %s chassis power on",
              IPMITOOL, oobAddress, oobPort, username, password));
      }

      public void powerOff(String oobAddress, int oobPort, String username, String password) {
          ShellUtils.run(String.format(
              "%s -I lanplus -H %s -p %d -U %s -P %s chassis power off",
              IPMITOOL, oobAddress, oobPort, username, password));
      }

      public String getPowerStatus(String oobAddress, int oobPort, String username, String password) {
          ShellResult ret = ShellUtils.runAndReturn(String.format(
              "%s -I lanplus -H %s -p %d -U %s -P %s chassis power status",
              IPMITOOL, oobAddress, oobPort, username, password));
          if (ret.getRetCode() != 0) {
              return "Unknown";
          }
          // output: "Chassis Power is on" or "Chassis Power is off"
          String output = ret.getStdout().trim().toLowerCase();
          if (output.contains("is on")) return "PowerOn";
          if (output.contains("is off")) return "PowerOff";
          return "Unknown";
      }
  }
  ```

- [ ] **Step 11.3: 编译验证 + Commit**

  ```
  feat(roleTest): add role exclusion integration test and IPMI manager skeleton
  ```

---

## Week 3 (Day 12-16): 容量管理 + 分配引擎

### Task 12: PhysicalServerCapacityVO + HostCapacityVO VIEW

**Goal:** 创建 PhysicalServerCapacityVO 真表，将 HostCapacityVO 降级为只读 MySQL JOIN VIEW。

**Depends on:** Task 7

**Files to create:**
- `header/src/main/java/org/zstack/header/server/PhysicalServerCapacityVO.java`
- DDL migration script additions to `V5.5.18__schema.sql`

#### Steps

- [ ] **Step 12.1: 创建 PhysicalServerCapacityVO**

  ```java
  package org.zstack.header.server;

  import javax.persistence.*;
  import java.sql.Timestamp;

  @Entity
  @Table(name = "PhysicalServerCapacityVO")
  public class PhysicalServerCapacityVO {
      @Id
      @Column
      private String uuid;  // == PhysicalServerVO.uuid

      @Column
      private long totalPhysicalCpu;

      @Column
      private long availableCpu;

      @Column
      private long totalPhysicalMemory;

      @Column
      private long availableMemory;

      @Column
      private long totalCpu;  // totalPhysicalCpu * cpuOverProvisioningRatio

      @Column
      private long totalMemory;  // totalPhysicalMemory * memoryOverProvisioningRatio

      @Column
      private double cpuOverProvisioningRatio;

      @Column
      private double memoryOverProvisioningRatio;

      @Column
      private Timestamp lastOpDate;

      @Column
      private Timestamp createDate;

      @PreUpdate
      private void preUpdate() {
          lastOpDate = null;
      }

      // getters/setters
  }
  ```

- [ ] **Step 12.2: 添加 DDL — PhysicalServerCapacityVO 表**

  追加到 `V5.5.18__schema.sql`：

  ```sql
  CREATE TABLE IF NOT EXISTS `PhysicalServerCapacityVO` (
      `uuid` VARCHAR(32) NOT NULL,
      `totalPhysicalCpu` BIGINT NOT NULL DEFAULT 0,
      `availableCpu` BIGINT NOT NULL DEFAULT 0,
      `totalPhysicalMemory` BIGINT NOT NULL DEFAULT 0,
      `availableMemory` BIGINT NOT NULL DEFAULT 0,
      `totalCpu` BIGINT NOT NULL DEFAULT 0,
      `totalMemory` BIGINT NOT NULL DEFAULT 0,
      `cpuOverProvisioningRatio` DOUBLE NOT NULL DEFAULT 1.0,
      `memoryOverProvisioningRatio` DOUBLE NOT NULL DEFAULT 1.0,
      `lastOpDate` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
      `createDate` TIMESTAMP NOT NULL DEFAULT '0000-00-00 00:00:00',
      PRIMARY KEY (`uuid`),
      CONSTRAINT `fkPhysicalServerCapacityVOPhysicalServerVO` FOREIGN KEY (`uuid`)
          REFERENCES `PhysicalServerVO` (`uuid`) ON DELETE CASCADE
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8;
  ```

- [ ] **Step 12.3: 创建 HostCapacityVO VIEW DDL**

  追加到升级脚本（独立的 migration script，如 `V5.5.18.1__schema.sql`）：

  ```sql
  -- Phase 1: Rename existing HostCapacityVO table
  -- ALTER TABLE HostCapacityVO RENAME TO HostCapacityVO_backup;

  -- Phase 2: Create VIEW mapping PhysicalServerCapacityVO → HostCapacityVO
  -- This VIEW provides read compatibility for 47 existing readers
  -- CREATE OR REPLACE VIEW HostCapacityVO AS
  -- SELECT
  --     r.roleUuid AS uuid,
  --     c.totalCpu AS totalCpu,
  --     c.availableCpu AS availableCpu,
  --     c.totalMemory AS totalMemory,
  --     c.availableMemory AS availableMemory,
  --     c.totalPhysicalCpu AS totalPhysicalCpu,
  --     c.totalPhysicalMemory AS totalPhysicalMemory,
  --     c.lastOpDate AS lastOpDate,
  --     c.createDate AS createDate
  -- FROM PhysicalServerCapacityVO c
  -- JOIN PhysicalServerRoleVO r ON c.uuid = r.serverUuid
  -- WHERE r.roleType = 'KVM_HOST' AND r.roleStatus = 'Active';
  --
  -- NOTE: This VIEW migration is high-risk. Implement only after POC verification
  -- that Hibernate @Entity can map to a VIEW with @OneToOne EAGER loading.
  -- For Day 12, we validate the VIEW concept without actually replacing HostCapacityVO.
  ```

  **重要**: VIEW 迁移是最高风险点。先在开发环境 POC 验证 Hibernate 对 VIEW 的兼容性，确认后再执行实际替换。

- [ ] **Step 12.4: POC 验证 Hibernate VIEW 兼容性**

  创建测试 VIEW 验证：
  ```sql
  -- In dev database:
  CREATE VIEW test_capacity_view AS
  SELECT uuid, totalCpu, availableCpu, totalMemory, availableMemory
  FROM HostCapacityVO;

  -- Verify Hibernate can query this VIEW as an @Entity
  ```

  验证方法：
  ```bash
  # 在测试环境中执行一个简单的 JPQL 查询验证
  # SELECT hc FROM HostCapacityVO hc WHERE hc.uuid = :uuid
  # 确认通过 VIEW 查询结果与直接表查询一致
  ```

- [ ] **Step 12.5: 编译验证 + Commit**

  ```
  feat(capacity): create PhysicalServerCapacityVO table and prepare HostCapacityVO VIEW migration
  ```

---

### Task 13: 6 个写入路径改造

**Goal:** 将 HostCapacityVO 的 6 个写入路径改写到 PhysicalServerCapacityVO 真表。

**Depends on:** Task 12

**Files to create:**
- `plugin/physicalServer/src/main/java/org/zstack/server/capacity/PhysicalServerCapacityUpdater.java`

#### Steps

- [ ] **Step 13.1: 创建 PhysicalServerCapacityUpdater**

  ```java
  package org.zstack.server.capacity;

  import org.springframework.beans.factory.annotation.Autowired;
  import org.springframework.transaction.annotation.Transactional;
  import org.zstack.core.db.DatabaseFacade;
  import org.zstack.header.server.PhysicalServerCapacityVO;
  import org.zstack.utils.Utils;
  import org.zstack.utils.logging.CLogger;

  import javax.persistence.LockModeType;

  /**
   * Unified capacity updater with pessimistic locking.
   * All capacity writes go through this class (no raw JPQL updates).
   *
   * Lock acquisition order: by PhysicalServerCapacityVO.uuid (consistent with
   * existing HostCapacityUpdater to avoid deadlocks).
   */
  public class PhysicalServerCapacityUpdater {
      private static final CLogger logger = Utils.getLogger(PhysicalServerCapacityUpdater.class);

      @Autowired
      private DatabaseFacade dbf;

      private String serverUuid;

      public PhysicalServerCapacityUpdater(String serverUuid) {
          this.serverUuid = serverUuid;
      }

      @Transactional
      public PhysicalServerCapacityVO run(CapacityUpdateClosure closure) {
          PhysicalServerCapacityVO cap = dbf.getEntityManager()
              .find(PhysicalServerCapacityVO.class, serverUuid, LockModeType.PESSIMISTIC_WRITE);
          if (cap == null) {
              logger.warn(String.format("PhysicalServerCapacityVO[uuid:%s] not found, skip update", serverUuid));
              return null;
          }

          PhysicalServerCapacityVO updated = closure.update(cap);
          if (updated != null) {
              dbf.getEntityManager().merge(updated);
          }
          return updated;
      }

      public interface CapacityUpdateClosure {
          PhysicalServerCapacityVO update(PhysicalServerCapacityVO current);
      }
  }
  ```

- [ ] **Step 13.2: 识别 6 个写入路径并创建包装器**

  6 个写入路径（参考 HostCapacityUpdater 现有调用点）：
  1. **ReportHostCapacityMessage** — 首次上报容量（INSERT）
  2. **VM Create → reserveCapacity** — 扣减 available（UPDATE）
  3. **VM Destroy → releaseCapacity** — 释放 available（UPDATE）
  4. **CPU 超分比变更** — 重计算 totalCpu（UPDATE）
  5. **Memory 超分比变更** — 重计算 totalMemory（UPDATE）
  6. **Host Reconnect → 重新上报** — 更新 totalPhysical（UPDATE）

  为每个路径创建对应的 closure 方法。

- [ ] **Step 13.3: 编译验证 + Commit**

  ```
  feat(capacity): implement PhysicalServerCapacityUpdater with pessimistic locking for 6 write paths
  ```

---

### Task 14: CapacityUpdater 超分比管理

**Goal:** 实现超分比管理器，支持全局 GlobalConfig 和 per-server 覆盖。

**Depends on:** Task 13

**Files to create:**
- `plugin/physicalServer/src/main/java/org/zstack/server/capacity/ServerCapacityOverProvisioningManager.java`
- `header/src/main/java/org/zstack/header/server/PhysicalServerGlobalConfig.java`

#### Steps

- [ ] **Step 14.1: 定义 GlobalConfig**

  ```java
  package org.zstack.header.server;

  import org.zstack.core.config.GlobalConfig;
  import org.zstack.core.config.GlobalConfigDefinition;
  import org.zstack.core.config.GlobalConfigValidation;

  @GlobalConfigDefinition
  public class PhysicalServerGlobalConfig {
      public static final String CATEGORY = "physicalServer";

      @GlobalConfigValidation(numberGreaterThan = 0)
      public static GlobalConfig CPU_OVER_PROVISIONING_RATIO =
          new GlobalConfig(CATEGORY, "cpu.overProvisioningRatio");

      @GlobalConfigValidation(numberGreaterThan = 0)
      public static GlobalConfig MEMORY_OVER_PROVISIONING_RATIO =
          new GlobalConfig(CATEGORY, "memory.overProvisioningRatio");

      @GlobalConfigValidation
      public static GlobalConfig ALLOCATOR_ENABLED =
          new GlobalConfig(CATEGORY, "allocator.enabled");
  }
  ```

- [ ] **Step 14.2: 实现超分比管理器**

  ```java
  package org.zstack.server.capacity;

  import org.zstack.header.server.PhysicalServerGlobalConfig;

  public class ServerCapacityOverProvisioningManagerImpl {
      public double getCpuOverProvisioningRatio(String serverUuid) {
          // 1. Check per-server ResourceConfig override
          // 2. Fall back to GlobalConfig default
          return PhysicalServerGlobalConfig.CPU_OVER_PROVISIONING_RATIO.value(Double.class);
      }

      public double getMemoryOverProvisioningRatio(String serverUuid) {
          return PhysicalServerGlobalConfig.MEMORY_OVER_PROVISIONING_RATIO.value(Double.class);
      }

      /**
       * Recalculate totalCpu/totalMemory when ratio changes.
       * totalCpu = totalPhysicalCpu * ratio
       * totalMemory = totalPhysicalMemory * ratio
       */
      public void recalculateCapacity(String serverUuid) {
          // Use PhysicalServerCapacityUpdater with pessimistic lock
      }
  }
  ```

- [ ] **Step 14.3: 注册 GlobalConfig 变更监听**

  在 PhysicalServerManagerImpl.start() 中添加：
  ```java
  PhysicalServerGlobalConfig.CPU_OVER_PROVISIONING_RATIO.installUpdateExtension(
      (oldConfig, newConfig) -> {
          // Trigger recalculation for all servers
          recalculateAllCapacity();
      }
  );
  ```

- [ ] **Step 14.4: 添加 GlobalConfig XML**

  创建 `conf/globalConfig/physicalServer.xml`：
  ```xml
  <?xml version="1.0" encoding="UTF-8"?>
  <globalConfig>
      <config>
          <category>physicalServer</category>
          <name>cpu.overProvisioningRatio</name>
          <defaultValue>1</defaultValue>
          <description>CPU over-provisioning ratio for physical servers</description>
      </config>
      <config>
          <category>physicalServer</category>
          <name>memory.overProvisioningRatio</name>
          <defaultValue>1</defaultValue>
          <description>Memory over-provisioning ratio for physical servers</description>
      </config>
      <config>
          <category>physicalServer</category>
          <name>allocator.enabled</name>
          <defaultValue>false</defaultValue>
          <description>Enable unified server allocator (feature switch)</description>
      </config>
  </globalConfig>
  ```

- [ ] **Step 14.5: 编译验证 + Commit**

  ```
  feat(capacity): implement over-provisioning ratio manager with GlobalConfig and per-server override
  ```

---

### Task 15: ServerAllocatorChain + 7 Flow

**Goal:** 实现统一分配引擎 ServerAllocatorChain，包含 7 个可扩展 Flow。

**Depends on:** Task 14

**Files to create:**
- `header/src/main/java/org/zstack/header/server/AllocateServerMsg.java`
- `header/src/main/java/org/zstack/header/server/AllocateServerReply.java`
- `header/src/main/java/org/zstack/header/server/ServerAllocatorFilterExtensionPoint.java`
- `header/src/main/java/org/zstack/header/server/ServerReservedCapacityExtensionPoint.java`
- `plugin/physicalServer/src/main/java/org/zstack/server/allocator/ServerAllocatorChain.java`
- `plugin/physicalServer/src/main/java/org/zstack/server/allocator/AbstractServerAllocatorFlow.java`
- `plugin/physicalServer/src/main/java/org/zstack/server/allocator/ZoneFilterFlow.java`
- `plugin/physicalServer/src/main/java/org/zstack/server/allocator/ClusterFilterFlow.java`
- `plugin/physicalServer/src/main/java/org/zstack/server/allocator/PoolFilterFlow.java`
- `plugin/physicalServer/src/main/java/org/zstack/server/allocator/RoleTypeFilterFlow.java`
- `plugin/physicalServer/src/main/java/org/zstack/server/allocator/StatusFilterFlow.java`
- `plugin/physicalServer/src/main/java/org/zstack/server/allocator/CapacityFilterFlow.java`
- `plugin/physicalServer/src/main/java/org/zstack/server/allocator/SortFilterFlow.java`

#### Steps

- [ ] **Step 15.1: 创建 AllocateServerMsg/Reply**

  ```java
  package org.zstack.header.server;

  import org.zstack.header.message.NeedReplyMessage;

  public class AllocateServerMsg extends NeedReplyMessage {
      private String zoneUuid;
      private String clusterUuid;
      private String poolUuid;
      private ServerRoleType requiredRoleType;
      private long requiredCpu;
      private long requiredMemory;
      private String avoidServerUuid;  // For migration scenarios
      // getters/setters
  }
  ```

  ```java
  package org.zstack.header.server;

  import org.zstack.header.message.MessageReply;

  public class AllocateServerReply extends MessageReply {
      private PhysicalServerInventory server;
      private String allocatedRoleUuid;  // The roleUuid on the selected server
      // getters/setters
  }
  ```

- [ ] **Step 15.2: 定义扩展点**

  ```java
  package org.zstack.header.server;

  import java.util.List;

  /**
   * Extension point for third-party modules to inject custom filter logic
   * into the ServerAllocatorChain.
   */
  public interface ServerAllocatorFilterExtensionPoint {
      List<PhysicalServerVO> filterCandidates(List<PhysicalServerVO> candidates,
                                               AllocateServerMsg msg);
  }
  ```

  ```java
  package org.zstack.header.server;

  /**
   * Extension point for declaring system-reserved capacity
   * that should be subtracted from available capacity.
   */
  public interface ServerReservedCapacityExtensionPoint {
      long getReservedCpu(String serverUuid);
      long getReservedMemory(String serverUuid);
  }
  ```

- [ ] **Step 15.3: 实现 AbstractServerAllocatorFlow**

  ```java
  package org.zstack.server.allocator;

  import org.zstack.header.server.AllocateServerMsg;
  import org.zstack.header.server.PhysicalServerVO;

  import java.util.List;

  public abstract class AbstractServerAllocatorFlow {
      /**
       * Filter candidates based on this flow's criteria.
       * @param candidates current candidate list (mutable)
       * @param msg the allocation request
       * @return filtered candidates (subset of input)
       */
      public abstract List<PhysicalServerVO> filter(List<PhysicalServerVO> candidates,
                                                      AllocateServerMsg msg);
  }
  ```

- [ ] **Step 15.4: 实现 7 个 Flow**

  **ZoneFilterFlow:**
  ```java
  public class ZoneFilterFlow extends AbstractServerAllocatorFlow {
      @Override
      public List<PhysicalServerVO> filter(List<PhysicalServerVO> candidates, AllocateServerMsg msg) {
          if (msg.getZoneUuid() == null) return candidates;
          return candidates.stream()
              .filter(s -> s.getZoneUuid().equals(msg.getZoneUuid()))
              .collect(Collectors.toList());
      }
  }
  ```

  **ClusterFilterFlow:**
  ```java
  public class ClusterFilterFlow extends AbstractServerAllocatorFlow {
      @Override
      public List<PhysicalServerVO> filter(List<PhysicalServerVO> candidates, AllocateServerMsg msg) {
          if (msg.getClusterUuid() == null) return candidates;
          // Filter by ClusterServerPoolRefVO mapping
          String poolUuid = Q.New(ClusterServerPoolRefVO.class)
              .select(ClusterServerPoolRefVO_.poolUuid)
              .eq(ClusterServerPoolRefVO_.clusterUuid, msg.getClusterUuid())
              .findValue();
          if (poolUuid == null) return candidates;
          return candidates.stream()
              .filter(s -> poolUuid.equals(s.getPoolUuid()))
              .collect(Collectors.toList());
      }
  }
  ```

  **PoolFilterFlow:** 按 poolUuid 过滤。

  **RoleTypeFilterFlow:** 按 requiredRoleType 过滤（检查 PhysicalServerRoleVO）。

  **StatusFilterFlow:** 过滤 state=Enabled, status=Connected, Maintenance 排除。

  **CapacityFilterFlow:** 按 requiredCpu/requiredMemory 过滤。

  **SortFilterFlow:** 按 availableCpu 降序排序（Least VM preferred 策略）。

- [ ] **Step 15.5: 实现 ServerAllocatorChain 组装**

  ```java
  package org.zstack.server.allocator;

  import org.springframework.beans.factory.annotation.Autowired;
  import org.zstack.header.server.*;

  import java.util.List;

  public class ServerAllocatorChain {
      @Autowired
      private List<AbstractServerAllocatorFlow> flows;

      @Autowired(required = false)
      private List<ServerAllocatorFilterExtensionPoint> filterExtensions;

      public PhysicalServerVO allocate(AllocateServerMsg msg) {
          // Start with all candidates
          List<PhysicalServerVO> candidates = loadAllCandidates();

          // Run through flow chain
          for (AbstractServerAllocatorFlow flow : flows) {
              candidates = flow.filter(candidates, msg);
              if (candidates.isEmpty()) {
                  throw new OperationFailureException(operr(
                      "No physical server available after %s filter",
                      flow.getClass().getSimpleName()));
              }
          }

          // Run extension point filters
          if (filterExtensions != null) {
              for (ServerAllocatorFilterExtensionPoint ext : filterExtensions) {
                  candidates = ext.filterCandidates(candidates, msg);
              }
          }

          // Return first candidate (already sorted)
          return candidates.get(0);
      }

      private List<PhysicalServerVO> loadAllCandidates() {
          return Q.New(PhysicalServerVO.class).list();
      }
  }
  ```

- [ ] **Step 15.6: 配置 Flow 链到 Spring XML**

  更新 `conf/springConfigXml/PhysicalServerManager.xml`：
  ```xml
  <bean id="ServerAllocatorChain" class="org.zstack.server.allocator.ServerAllocatorChain">
      <property name="flowNames">
          <list>
              <value>org.zstack.server.allocator.ZoneFilterFlow</value>
              <value>org.zstack.server.allocator.ClusterFilterFlow</value>
              <value>org.zstack.server.allocator.PoolFilterFlow</value>
              <value>org.zstack.server.allocator.RoleTypeFilterFlow</value>
              <value>org.zstack.server.allocator.StatusFilterFlow</value>
              <value>org.zstack.server.allocator.CapacityFilterFlow</value>
              <value>org.zstack.server.allocator.SortFilterFlow</value>
          </list>
      </property>
  </bean>
  ```

- [ ] **Step 15.7: 在 PhysicalServerManagerImpl 中处理 AllocateServerMsg**

  ```java
  private void handleLocalMessage(Message msg) {
      if (msg instanceof AllocateServerMsg) {
          handle((AllocateServerMsg) msg);
      } else {
          bus.dealWithUnknownMessage(msg);
      }
  }

  private void handle(AllocateServerMsg msg) {
      PhysicalServerVO server = serverAllocatorChain.allocate(msg);

      // Reserve capacity with pessimistic lock
      new PhysicalServerCapacityUpdater(server.getUuid()).run(cap -> {
          cap.setAvailableCpu(cap.getAvailableCpu() - msg.getRequiredCpu());
          cap.setAvailableMemory(cap.getAvailableMemory() - msg.getRequiredMemory());
          return cap;
      });

      AllocateServerReply reply = new AllocateServerReply();
      reply.setServer(PhysicalServerInventory.valueOf(server));
      bus.reply(msg, reply);
  }
  ```

- [ ] **Step 15.8: 编译验证 + Commit**

  ```
  feat(allocator): implement ServerAllocatorChain with 7 pluggable flows and extension points
  ```

---

### Task 16: 混部容量 + Code Review

**Goal:** 实现 KVM + Container 混部场景的容量管理逻辑。

**Depends on:** Task 15

**Files to modify:**
- `plugin/physicalServer/src/main/java/org/zstack/server/capacity/PhysicalServerCapacityUpdater.java`

**Files to create:**
- `test/src/test/groovy/org/zstack/test/integration/server/MixedDeployCapacityCase.groovy`

#### Steps

- [ ] **Step 16.1: 实现混部容量计算逻辑**

  ```java
  /**
   * Mixed deployment capacity model:
   *
   * KVM perspective:
   *   available = totalPhysical - containerReserved - safetyBuffer
   *   Then apply overProvisioning ratio to get KVM allocatable
   *
   * Container perspective:
   *   Capacity reported by K8s, recorded as-is in PhysicalServerCapacityVO
   *   Node Taint triggered when physicalAvailable < safetyBuffer
   *
   * Safety buffer configurable via GlobalConfig:
   *   physicalServer.safetyBuffer.cpu (default: 2 cores)
   *   physicalServer.safetyBuffer.memory (default: 4 GB)
   */
  public void updateMixedCapacity(String serverUuid,
                                    long containerCpuUsed, long containerMemoryUsed) {
      new PhysicalServerCapacityUpdater(serverUuid).run(cap -> {
          long safetyBufferCpu = PhysicalServerGlobalConfig.SAFETY_BUFFER_CPU.value(Long.class);
          long safetyBufferMemory = PhysicalServerGlobalConfig.SAFETY_BUFFER_MEMORY.value(Long.class);

          // KVM available = total physical - container used - safety buffer
          long kvmPhysicalAvailable = cap.getTotalPhysicalCpu() - containerCpuUsed - safetyBufferCpu;
          kvmPhysicalAvailable = Math.max(0, kvmPhysicalAvailable);

          // Apply overProvisioning ratio
          cap.setTotalCpu((long) (kvmPhysicalAvailable * cap.getCpuOverProvisioningRatio()));
          // Similar for memory
          long memPhysicalAvailable = cap.getTotalPhysicalMemory() - containerMemoryUsed - safetyBufferMemory;
          memPhysicalAvailable = Math.max(0, memPhysicalAvailable);
          cap.setTotalMemory((long) (memPhysicalAvailable * cap.getMemoryOverProvisioningRatio()));

          return cap;
      });
  }
  ```

- [ ] **Step 16.2: 添加 Safety Buffer GlobalConfig**

  在 `conf/globalConfig/physicalServer.xml` 中添加：
  ```xml
  <config>
      <category>physicalServer</category>
      <name>safetyBuffer.cpu</name>
      <defaultValue>2</defaultValue>
      <description>CPU safety buffer (cores) reserved between KVM and Container</description>
  </config>
  <config>
      <category>physicalServer</category>
      <name>safetyBuffer.memory</name>
      <defaultValue>4294967296</defaultValue>
      <description>Memory safety buffer (bytes, default 4GB) reserved between KVM and Container</description>
  </config>
  ```

- [ ] **Step 16.3: 创建混部容量集成测试**

  ```groovy
  package org.zstack.test.integration.server

  import org.zstack.testlib.SubCase

  class MixedDeployCapacityCase extends SubCase {
      // Test scenarios:
      // 1. KVM only: available = totalPhysical * ratio
      // 2. KVM + Container: available = (totalPhysical - containerUsed - safety) * ratio
      // 3. Container fills up: KVM available drops, no overselling
      // 4. Safety buffer breach: reject new VM allocation
  }
  ```

- [ ] **Step 16.4: Code Review 检查点**

  验证清单：
  - [ ] 所有容量写入通过 PhysicalServerCapacityUpdater，无裸 JPQL
  - [ ] @Transactional 和 @DeadlockAutoRestart 不在同一方法
  - [ ] 锁获取顺序与现有 HostCapacityUpdater 一致
  - [ ] 不修改现有 VO 文件名/变量名/方法签名
  - [ ] 现有 47 个 HostCapacityVO 读取方不受影响

- [ ] **Step 16.5: 编译验证 + Commit**

  ```
  feat(capacity): implement KVM+Container mixed deployment capacity model with safety buffer
  ```

---

## Week 4+ (Day 17-22): 兼容层 + 集成

### Task 17: CompatibilityBridge 薄代理

**Goal:** 实现两阶段薄适配，拦截 AllocateHostMsg 透传到 ServerAllocatorChain。

**Depends on:** Task 15, Task 14 (特性开关)

**Files to create:**
- `plugin/physicalServer/src/main/java/org/zstack/server/compatibility/ServerAllocatorCompatibilityBridge.java`

#### Steps

- [ ] **Step 17.1: 实现 CompatibilityBridge**

  ```java
  package org.zstack.server.compatibility;

  import org.springframework.beans.factory.annotation.Autowired;
  import org.zstack.core.cloudbus.CloudBus;
  import org.zstack.core.db.Q;
  import org.zstack.header.allocator.AllocateHostMsg;
  import org.zstack.header.allocator.AllocateHostReply;
  import org.zstack.header.host.HostInventory;
  import org.zstack.header.host.HostVO;
  import org.zstack.header.server.*;
  import org.zstack.server.allocator.ServerAllocatorChain;

  /**
   * Two-phase thin adapter:
   *
   * Phase 1: ServerAllocatorChain filters PhysicalServerVO candidates
   *          → maps to HostVO UUIDs via RoleVO
   *          → injects as candidateHostUuids into AllocateHostMsg
   *
   * Phase 2: Existing HostAllocatorChain runs on the narrowed candidate set
   *          (L2 Network, PrimaryStorage, Tags, etc.)
   *
   * Result: HostInventory returned as before, completely transparent to caller.
   */
  public class ServerAllocatorCompatibilityBridge {
      @Autowired
      private ServerAllocatorChain serverAllocatorChain;
      @Autowired
      private CloudBus bus;

      /**
       * @return true if the feature switch is enabled for this role type
       */
      public boolean shouldIntercept(AllocateHostMsg msg) {
          return PhysicalServerGlobalConfig.ALLOCATOR_ENABLED.value(Boolean.class);
      }

      /**
       * Phase 1: Pre-filter using ServerAllocatorChain.
       * Narrows candidate set before handing off to existing HostAllocatorChain.
       */
      public void preFilter(AllocateHostMsg msg) {
          AllocateServerMsg serverMsg = new AllocateServerMsg();
          serverMsg.setZoneUuid(msg.getZoneUuid());
          serverMsg.setClusterUuid(msg.getClusterUuid());
          serverMsg.setRequiredRoleType(ServerRoleType.KVM_HOST);
          serverMsg.setRequiredCpu(msg.getCpuNum());
          serverMsg.setRequiredMemory(msg.getMemorySize());

          List<PhysicalServerVO> candidates = serverAllocatorChain.filterOnly(serverMsg);

          // Map PhysicalServerVO → HostVO UUIDs via RoleVO
          List<String> hostUuids = new ArrayList<>();
          for (PhysicalServerVO server : candidates) {
              String hostUuid = Q.New(PhysicalServerRoleVO.class)
                  .select(PhysicalServerRoleVO_.roleUuid)
                  .eq(PhysicalServerRoleVO_.serverUuid, server.getUuid())
                  .eq(PhysicalServerRoleVO_.roleType, ServerRoleType.KVM_HOST)
                  .eq(PhysicalServerRoleVO_.roleStatus, PhysicalServerRoleStatus.Active.toString())
                  .findValue();
              if (hostUuid != null) {
                  hostUuids.add(hostUuid);
              }
          }

          // Inject narrowed candidate set
          if (msg.getCandidateHostUuids() != null) {
              hostUuids.retainAll(msg.getCandidateHostUuids());
          }
          msg.setCandidateHostUuids(hostUuids);
      }
  }
  ```

- [ ] **Step 17.2: 注册 CompatibilityBridge 作为 HostAllocator 扩展点**

  通过 Spring XML 注册为 `HostAllocateExtensionPoint`（如果存在此扩展点），
  或作为 `MessageInterceptor` 拦截 AllocateHostMsg。

- [ ] **Step 17.3: 编译验证 + Commit**

  ```
  feat(compat): implement two-phase CompatibilityBridge thin adapter for AllocateHostMsg
  ```

---

### Task 18: 存量数据迁移 + 统一查询

**Goal:** 编写幂等数据迁移脚本，为存量 Host/Chassis/NativeHost 生成 PhysicalServerVO + RoleVO。实现 QueryPhysicalServerMsg 统一查询。

**Depends on:** Task 17

**Files to create/modify:**
- `conf/db/upgrade/V5.5.18.2__schema.sql` — 存量迁移脚本

#### Steps

- [ ] **Step 18.1: 编写幂等迁移 SQL 脚本**

  ```sql
  -- Idempotent data migration: generate PhysicalServerVO for all existing hosts

  -- 1. Migrate KVM Hosts
  INSERT IGNORE INTO PhysicalServerVO
      (uuid, name, zoneUuid, poolUuid, managementIp, state, status, powerStatus, createDate, lastOpDate)
  SELECT
      REPLACE(UUID(), '-', ''),  -- generate deterministic UUID
      h.name,
      h.zoneUuid,
      COALESCE(ref.poolUuid, 'default-pool'),  -- use cluster's pool or default
      h.managementIp,
      CASE h.state WHEN 'Enabled' THEN 'Enabled' WHEN 'Disabled' THEN 'Disabled'
          ELSE 'Enabled' END,
      CASE h.status WHEN 'Connected' THEN 'Connected' ELSE 'Disconnected' END,
      'Unknown',
      h.createDate,
      NOW()
  FROM HostVO h
  LEFT JOIN ClusterServerPoolRefVO ref ON h.clusterUuid = ref.clusterUuid
  WHERE h.uuid NOT IN (
      SELECT r.roleUuid FROM PhysicalServerRoleVO r WHERE r.roleType = 'KVM_HOST'
  );

  -- 2. Generate PhysicalServerRoleVO for KVM
  INSERT IGNORE INTO PhysicalServerRoleVO
      (uuid, serverUuid, roleType, roleUuid, schedulingMode, roleStatus, createDate, lastOpDate)
  SELECT
      REPLACE(UUID(), '-', ''),
      ps.uuid,
      'KVM_HOST',
      h.uuid,
      'INTERNAL_SHARED',
      'Active',
      NOW(),
      NOW()
  FROM HostVO h
  JOIN PhysicalServerVO ps ON ps.managementIp = h.managementIp AND ps.zoneUuid = h.zoneUuid
  WHERE h.uuid NOT IN (
      SELECT r.roleUuid FROM PhysicalServerRoleVO r WHERE r.roleType = 'KVM_HOST'
  );

  -- 3. Register in ResourceVO
  INSERT IGNORE INTO ResourceVO (uuid, resourceName, resourceType)
  SELECT uuid, name, 'PhysicalServerVO' FROM PhysicalServerVO
  WHERE uuid NOT IN (SELECT uuid FROM ResourceVO WHERE resourceType = 'PhysicalServerVO');

  -- 4. Register in AccountResourceRefVO (admin account)
  INSERT IGNORE INTO AccountResourceRefVO
      (uuid, accountUuid, ownerAccountUuid, resourceUuid, resourceType, createDate, lastOpDate)
  SELECT
      REPLACE(UUID(), '-', ''),
      (SELECT uuid FROM AccountVO WHERE name = 'admin' LIMIT 1),
      (SELECT uuid FROM AccountVO WHERE name = 'admin' LIMIT 1),
      ps.uuid,
      'PhysicalServerVO',
      NOW(),
      NOW()
  FROM PhysicalServerVO ps
  WHERE ps.uuid NOT IN (
      SELECT resourceUuid FROM AccountResourceRefVO WHERE resourceType = 'PhysicalServerVO'
  );
  ```

  **注意**: 实际迁移脚本需要更精确的 serialNumber 提取（从 SystemTag 或 IPMI FRU）和确定性 UUID 生成（避免多次执行产生不同 UUID）。生产环境应使用 `MD5(CONCAT(zoneUuid, managementIp))` 或类似方式生成确定性 UUID。

- [ ] **Step 18.2: 验证迁移脚本幂等性**

  ```bash
  # 在开发数据库执行两次，验证无报错无重复数据
  mysql -u root -p zstack < V5.5.18.2__schema.sql
  mysql -u root -p zstack < V5.5.18.2__schema.sql
  # 验证 PhysicalServerVO 记录数 == 期望值
  mysql -u root -p zstack -e "SELECT COUNT(*) FROM PhysicalServerVO"
  ```

- [ ] **Step 18.3: 验证 QueryPhysicalServerMsg 跨角色查询**

  QueryPhysicalServerMsg 已在 Day 0 定义，会由 ZStack Query 框架自动处理。验证：
  ```bash
  # 通过 SDK 调用 QueryPhysicalServer
  # 预期返回所有迁移后的 PhysicalServer，含 roles 列表
  ```

- [ ] **Step 18.4: Commit**

  ```
  feat(migration): create idempotent data migration script for existing hosts to PhysicalServerVO
  ```

---

### Task 19: 电源管理 + 硬件发现

**Goal:** 实现统一电源管理 API 和硬件发现 API。

**Depends on:** Task 11 (IpmiManager)

**Files to create:**
- `header/src/main/java/org/zstack/header/server/APIPowerManagePhysicalServerMsg.java`
- `header/src/main/java/org/zstack/header/server/APIPowerManagePhysicalServerEvent.java`
- `header/src/main/java/org/zstack/header/server/APIDiscoverPhysicalServerHardwareMsg.java`
- `header/src/main/java/org/zstack/header/server/APIDiscoverPhysicalServerHardwareEvent.java`
- `header/src/main/java/org/zstack/header/server/PowerManageable.java` — SPI
- `header/src/main/java/org/zstack/header/server/HardwareDiscoverable.java` — SPI

#### Steps

- [ ] **Step 19.1: 创建电源管理 API 消息**

  ```java
  @Action(category = PhysicalServerConstant.ACTION_CATEGORY)
  @RestRequest(
      path = "/physical-servers/{uuid}/actions",
      method = HttpMethod.PUT,
      isAction = true,
      responseClass = APIPowerManagePhysicalServerEvent.class
  )
  public class APIPowerManagePhysicalServerMsg extends APIMessage {
      @APIParam(resourceType = PhysicalServerVO.class)
      private String uuid;

      @APIParam(validValues = {"PowerOn", "PowerOff", "PowerReset", "GetPowerStatus"})
      private String action;

      @APIParam(required = false)
      private boolean force;

      // getters/setters + __example__()
  }
  ```

- [ ] **Step 19.2: 实现电源管理 handler**

  ```java
  private void handle(APIPowerManagePhysicalServerMsg msg) {
      PhysicalServerVO server = dbf.findByUuid(msg.getUuid(), PhysicalServerVO.class);

      // Check OOB credentials
      if (server.getOobAddress() == null) {
          throw new OperationFailureException(operr(
              "OOB credentials not configured for PhysicalServer[uuid:%s]", msg.getUuid()));
      }

      // Pre-power-off check: consult all Active role providers
      if ("PowerOff".equals(msg.getAction()) && !msg.isForce()) {
          for (PhysicalServerRoleVO role : server.getRoles()) {
              if (!"Active".equals(role.getRoleStatus())) continue;
              PhysicalServerRoleProvider provider = roleProviders.get(role.getRoleType());
              if (provider instanceof PowerManageable) {
                  ErrorCode err = ((PowerManageable) provider).prePhysicalServerPowerOff(server);
                  if (err != null) {
                      throw new OperationFailureException(err);
                  }
              }
          }
      }

      // Execute IPMI command
      IpmiManager ipmi = new IpmiManager();
      switch (msg.getAction()) {
          case "PowerOn":
              ipmi.powerOn(server.getOobAddress(), server.getOobPort(),
                  server.getOobUsername(), server.getOobPassword());
              server.setPowerStatus(PhysicalServerPowerStatus.PowerOn);
              break;
          case "PowerOff":
              ipmi.powerOff(server.getOobAddress(), server.getOobPort(),
                  server.getOobUsername(), server.getOobPassword());
              server.setPowerStatus(PhysicalServerPowerStatus.PowerOff);
              break;
          case "PowerReset":
              ipmi.powerOff(server.getOobAddress(), server.getOobPort(),
                  server.getOobUsername(), server.getOobPassword());
              ipmi.powerOn(server.getOobAddress(), server.getOobPort(),
                  server.getOobUsername(), server.getOobPassword());
              server.setPowerStatus(PhysicalServerPowerStatus.PowerOn);
              break;
          case "GetPowerStatus":
              String status = ipmi.getPowerStatus(server.getOobAddress(), server.getOobPort(),
                  server.getOobUsername(), server.getOobPassword());
              server.setPowerStatus(PhysicalServerPowerStatus.valueOf(status));
              break;
      }

      dbf.updateAndRefresh(server);

      APIPowerManagePhysicalServerEvent evt = new APIPowerManagePhysicalServerEvent(msg.getId());
      evt.setInventory(PhysicalServerInventory.valueOf(server));
      bus.publish(evt);
  }
  ```

- [ ] **Step 19.3: 定义 PowerManageable SPI**

  ```java
  package org.zstack.header.server;

  import org.zstack.header.errorcode.ErrorCode;

  /**
   * SPI for role providers to participate in power management decisions.
   * Implement this interface on your RoleProvider if the role has pre-conditions
   * for power operations (e.g., KVM should check for running VMs before power-off).
   */
  public interface PowerManageable {
      /**
       * Called before power-off. Return non-null ErrorCode to reject the operation.
       */
      ErrorCode prePhysicalServerPowerOff(PhysicalServerVO server);
  }
  ```

- [ ] **Step 19.4: 实现硬件发现 API（Should Have）**

  ```java
  private void handle(APIDiscoverPhysicalServerHardwareMsg msg) {
      PhysicalServerVO server = dbf.findByUuid(msg.getUuid(), PhysicalServerVO.class);

      // Discover via OOB (IPMI FRU) or agent (/sys/class/dmi/)
      // Write to HardwareInfoVO + HardwareDetailVO

      PhysicalServerHardwareInfoVO hwInfo = new PhysicalServerHardwareInfoVO();
      hwInfo.setUuid(server.getUuid());
      // ... fill from discovery
      dbf.persistAndRefresh(hwInfo);

      server.setStatus(PhysicalServerStatus.Connected);
      dbf.updateAndRefresh(server);

      APIDiscoverPhysicalServerHardwareEvent evt =
          new APIDiscoverPhysicalServerHardwareEvent(msg.getId());
      evt.setInventory(PhysicalServerInventory.valueOf(server));
      bus.publish(evt);
  }
  ```

- [ ] **Step 19.5: 添加电源/发现消息到 serviceConfig**

- [ ] **Step 19.6: 编译验证 + Commit**

  ```
  feat(powerManagement): implement unified power management and hardware discovery APIs
  ```

---

### Task 20: 回归测试 - ServerPool + PhysicalServer 完整流程

**Goal:** 编写完整的集成测试覆盖 ServerPool 和 PhysicalServer 的全生命周期。

**Depends on:** Task 19

**Files to create:**
- `test/src/test/groovy/org/zstack/test/integration/server/PhysicalServerLifecycleCase.groovy`

#### Steps

- [ ] **Step 20.1: 完善 ServerPoolCrudCase**

  补充以下测试场景：
  - 创建 ServerPool → 关联 Cluster → 创建 PhysicalServer → 查询 → 删除
  - 删除有 PhysicalServer 的 ServerPool → 预期失败
  - 一个 Cluster 重复关联 ServerPool → 预期失败（UNIQUE 约束）
  - 多个 Cluster 关联同一 ServerPool → 预期成功

- [ ] **Step 20.2: 创建 PhysicalServer 完整生命周期测试**

  ```groovy
  class PhysicalServerLifecycleCase extends SubCase {
      void testFullLifecycle() {
          // 1. Create Zone + ServerPool
          // 2. Create PhysicalServer with OOB info
          // 3. Query → verify state/status/powerStatus
          // 4. ChangeState to Maintenance
          // 5. ChangeState back to Enabled
          // 6. Delete → verify cascade cleanup
      }

      void testDeleteWithActiveRoleBlocked() {
          // 1. Create PhysicalServer
          // 2. Manually attach a role
          // 3. Try delete → expect failure
          // 4. Detach role
          // 5. Delete → expect success
      }

      void testSerialNumberUniqueness() {
          // Same serialNumber + zoneUuid → expect failure on second create
          // Different zone → expect success
      }
  }
  ```

- [ ] **Step 20.3: 运行测试**

  ```bash
  cd /home/mj/zstack-workspace/zstack-unifi-host/test && \
  mvn test -Dtest=PhysicalServerLifecycleCase 2>&1 | tail -20
  ```

- [ ] **Step 20.4: Commit**

  ```
  test(physicalServer): add comprehensive lifecycle and edge case integration tests
  ```

---

### Task 21: 回归测试 - 角色互斥 + 容量分配

**Goal:** 验证角色互斥矩阵和容量分配链路的正确性。

**Depends on:** Task 20

**Files to create:**
- `test/src/test/groovy/org/zstack/test/integration/server/ServerAllocatorCase.groovy`

#### Steps

- [ ] **Step 21.1: 完善角色互斥测试（从 Task 11 骨架补充）**

  覆盖互斥矩阵所有组合：
  | 已有 \ 新 | KVM(SHARED) | BM2(EXCLUSIVE) | Container(READONLY) |
  |-----------|:-----------:|:--------------:|:-------------------:|
  | 无 | OK | OK | OK |
  | KVM | FAIL(dup) | FAIL(excl) | OK |
  | BM2 | FAIL(excl) | FAIL(dup) | OK |
  | Container | OK | OK | FAIL(dup) |
  | KVM+Container | FAIL(dup) | FAIL(excl) | FAIL(dup) |

- [ ] **Step 21.2: 创建分配引擎测试**

  ```groovy
  class ServerAllocatorCase extends SubCase {
      void testAllocateByZone() { /* ... */ }
      void testAllocateByCluster() { /* ... */ }
      void testAllocateByRoleType() { /* ... */ }
      void testCapacityFilter() { /* ... */ }
      void testNoAvailableServer() { /* expect clear error */ }
      void testMaintenanceServerExcluded() { /* ... */ }
  }
  ```

- [ ] **Step 21.3: 运行测试 + Commit**

  ```
  test(allocator): add allocator chain and role exclusion matrix tests
  ```

---

### Task 22: 最终集成 + MR 准备

**Goal:** 最终回归验证、代码清理、提交 MR。

**Depends on:** Task 21

#### Steps

- [ ] **Step 22.1: 全量编译验证**

  ```bash
  cd /home/mj/zstack-workspace/zstack-unifi-host && mvn clean install -DskipTests 2>&1 | tail -20
  ```

- [ ] **Step 22.2: 运行 zstack/test 全量测试**

  ```bash
  cd /home/mj/zstack-workspace/zstack-unifi-host/test && mvn test 2>&1 | tail -30
  ```
  预期：零回归，所有现有测试通过。

- [ ] **Step 22.3: 代码清理检查**

  验证清单：
  - [ ] 无未实现的 TODO（或已标记为 Should Have 延后）
  - [ ] 所有新 API 有 `__example__()` 方法
  - [ ] 不修改现有 VO 文件名/变量名/方法签名（NFR-005）
  - [ ] Java 8 兼容（无 Java 9+ API 使用）
  - [ ] 所有新表无 EO 模式，直接硬删除
  - [ ] oobPassword 字段未出现在 Inventory 中
  - [ ] PhysicalServerCapacityVO 是真表
  - [ ] 特性开关默认关闭（allocator.enabled = false）

- [ ] **Step 22.4: 生成 SDK**

  ```bash
  cd /home/mj/zstack-workspace/zstack-unifi-host && ./runMavenProfile sdk
  ```

- [ ] **Step 22.5: 提交 MR**

  ```bash
  git push origin feature/unifi-host-dev
  # 创建 MR: feature/unifi-host-dev → master
  ```

---

## 附录

### A. 文件清单总览

| 模块 | 新建文件数 | 修改文件数 | 说明 |
|------|-----------|-----------|------|
| header/server/ | ~15 | 0 | SPI、新 API 消息、CapacityVO |
| plugin/physicalServer/ | ~20 | 1 | Service、RoleProviders、Allocator、Capacity |
| conf/springConfigXml/ | 1 | 0 | PhysicalServerManager.xml |
| conf/serviceConfig/ | 3 | 0 | physicalServer/serverPool/provisionNetwork.xml |
| conf/globalConfig/ | 1 | 0 | physicalServer.xml |
| conf/db/upgrade/ | 1-2 | 1 | 迁移脚本 |
| test/ | ~5 | 0 | Groovy 集成测试 |
| **合计** | **~45** | **~2** | |

### B. 风险缓解清单

| 风险 | 缓解措施 | 对应 Task |
|------|---------|----------|
| HostCapacityVO VIEW 与 Hibernate 不兼容 | Task 12 POC 验证，失败则改用 Wrapper 模式 | Task 12 |
| 悲观锁引入死锁 | 锁顺序一致 + @DeadlockAutoRestart | Task 13 |
| CompatibilityBridge 复杂度超预期 | 薄代理模式，保留原始消息引用 | Task 17 |
| 存量迁移遗漏边缘数据 | 幂等脚本 + 重复执行验证 | Task 18 |
| 全量回归时间不足 | 特性开关默认关闭，零影响 | Task 22 |

### C. 架构决策记录 (ADR)

1. **新表不用 EO 模式** — PhysicalServerVO 直接 `DELETE FROM`，减少软删除复杂度
2. **裸 JPQL 删除** — 绕过 Hibernate cascade 对性能的影响
3. **容量写入统一走 Updater** — 悲观锁保证一致性，避免裸 SQL UPDATE
4. **PhysicalServerCapacityVO 是真表** — HostCapacityVO 降为 VIEW
5. **EXTERNAL_READONLY 计入 available** — Container 消耗扣减，不超配
6. **serialNumber 三级降级** — 兼容白牌/虚拟化环境
7. **Wrap, don't delete** — 新增包装层，不删除任何现有代码
8. **BM1 延后 (Could)** — v5.5.18 不实现 BAREMETAL_V1 适配器
9. **两阶段薄适配** — CompatibilityBridge 只做候选集预过滤，不替换 HostAllocatorChain
