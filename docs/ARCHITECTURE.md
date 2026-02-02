# ZStack 后端架构索引

> 本文档描述 ZStack IaaS 云计算平台后端的核心架构设计、组件机制和扩展方式。

## 目录

- [一、架构概览](#一架构概览)
- [二、核心框架层](#二核心框架层)
  - [2.1 消息总线 (CloudBus)](#21-消息总线-cloudbus)
  - [2.2 依赖注入与组件管理](#22-依赖注入与组件管理)
  - [2.3 数据库访问层 (DatabaseFacade)](#23-数据库访问层-databasefacade)
  - [2.4 异步编程模型 (ThreadFacade)](#24-异步编程模型-threadfacade)
  - [2.5 错误处理机制 (ErrorFacade)](#25-错误处理机制-errorfacade)
- [三、API 消息设计](#三api-消息设计)
  - [3.1 消息类型层次](#31-消息类型层次)
  - [3.2 数据模型架构](#32-数据模型架构)
  - [3.3 关键注解](#33-关键注解)
- [四、REST API 层](#四rest-api-层)
  - [4.1 核心组件](#41-核心组件)
  - [4.2 请求处理流程](#42-请求处理流程)
  - [4.3 异步 API 机制](#43-异步-api-机制)
  - [4.4 限流机制](#44-限流机制)
- [五、工作流引擎 (FlowChain)](#五工作流引擎-flowchain)
  - [5.1 工作流类型](#51-工作流类型)
  - [5.2 Flow 接口](#52-flow-接口)
  - [5.3 回滚机制](#53-回滚机制)
- [六、插件扩展系统](#六插件扩展系统)
  - [6.1 插件类型分类](#61-插件类型分类)
  - [6.2 插件注册方式](#62-插件注册方式)
  - [6.3 扩展点类型](#63-扩展点类型)
- [七、配置管理系统](#七配置管理系统)
  - [7.1 GlobalConfig (全局配置)](#71-globalconfig-全局配置)
  - [7.2 ResourceConfig (资源配置)](#72-resourceconfig-资源配置)
- [八、启动流程](#八启动流程)
  - [8.1 启动入口](#81-启动入口)
  - [8.2 管理节点启动步骤](#82-管理节点启动步骤)
  - [8.3 数据库 Schema 管理](#83-数据库-schema-管理)
- [九、测试框架](#九测试框架)
  - [9.1 测试框架结构](#91-测试框架结构)
  - [9.2 模拟器机制](#92-模拟器机制)
  - [9.3 环境定义 DSL](#93-环境定义-dsl)
- [十、架构特性总结](#十架构特性总结)

---

## 一、架构概览

ZStack 是一个企业级开源 IaaS 云计算平台，采用 **Java + Spring + JPA/Hibernate** 技术栈，以**消息总线**为核心构建分布式、可扩展的微服务架构。

### 核心设计理念

| 理念 | 说明 |
|------|------|
| **全异步架构** | 所有操作通过消息总线异步执行，提升系统吞吐量 |
| **插件化扩展** | 通过扩展点机制实现模块解耦，便于新功能接入 |
| **声明式 API** | 注解驱动的 REST API 设计，简化开发 |
| **统一错误处理** | AOP + 错误码体系，保证系统稳定性 |

### 核心技术栈

| 层次 | 技术 |
|------|------|
| 依赖注入 | Spring Framework |
| ORM | JPA / Hibernate |
| AOP | AspectJ |
| 消息传输 | HTTP / RabbitMQ |
| 测试框架 | JUnit + Groovy DSL |
| 数据库迁移 | Flyway |
| 连接池 | C3P0 |

### 项目模块结构

```
zstack/
├── core/           # 核心框架（消息总线、数据库、线程、错误处理）
├── header/         # API 消息定义、VO/Inventory、常量枚举
├── rest/           # REST API 服务器
├── portal/         # 管理节点启动入口
├── configuration/  # 配置管理
├── resourceconfig/ # 资源级配置
├── compute/        # 计算资源管理
├── storage/        # 存储资源管理
├── network/        # 网络资源管理
├── identity/       # 身份认证
├── image/          # 镜像管理
├── plugin/         # 插件模块（KVM、Ceph、虚拟路由器等）
├── test/           # 集成测试
├── testlib/        # 测试框架库
├── simulator/      # 模拟器
└── conf/           # 配置文件和数据库 Schema
```

---

## 二、核心框架层

核心框架位于 `core/src/main/java/org/zstack/core/` 目录。

### 2.1 消息总线 (CloudBus)

消息总线是 ZStack 架构的核心，负责服务间通信。

#### 核心类

| 类/接口 | 路径 | 说明 |
|---------|------|------|
| `CloudBus` | `core/cloudbus/CloudBus.java` | 消息总线核心接口 |
| `CloudBusImpl3` | `core/cloudbus/CloudBusImpl3.java` | 当前实现（基于 HTTP） |
| `CloudBusImpl2` | `core/cloudbus/CloudBusImpl2.java` | 旧实现（基于 RabbitMQ） |

#### 消息类型层次

```
Message (基础消息)
  └── NeedReplyMessage (需要回复的消息)
      └── APIMessage (API 消息)
          ├── APICreateMessage (创建资源)
          ├── APIGetMessage (获取资源)
          └── APIQueryMessage (查询资源)

MessageReply (消息回复)
  └── APIReply (API 回复)

Event (事件)
  └── APIEvent (API 事件)
```

#### 核心功能

| 方法 | 说明 |
|------|------|
| `send(msg, callback)` | 异步发送消息，通过回调接收响应 |
| `call(msg)` | 同步调用，阻塞等待回复 |
| `route(msg)` | 根据 `serviceId` 路由消息到对应服务 |
| `publish(event)` | 发布事件给所有订阅者 |
| `registerService(service)` | 注册服务到消息总线 |

#### 使用示例

```java
// 异步发送
bus.send(msg, new CloudBusCallBack(completion) {
    @Override
    public void run(MessageReply reply) {
        if (reply.isSuccess()) {
            // 处理成功
        } else {
            // 处理失败
        }
    }
});

// 同步调用
MessageReply reply = bus.call(msg);
```

### 2.2 依赖注入与组件管理

#### 核心类

| 类/接口 | 路径 | 说明 |
|---------|------|------|
| `ComponentLoader` | `core/componentloader/ComponentLoader.java` | 组件加载器接口 |
| `ComponentLoaderImpl` | `core/componentloader/ComponentLoaderImpl.java` | 基于 Spring 的实现 |
| `PluginRegistry` | `core/componentloader/PluginRegistry.java` | 插件注册表接口 |
| `PluginRegistryImpl` | `core/componentloader/PluginRegistryImpl.java` | 插件注册实现 |
| `Platform` | `core/Platform.java` | 平台入口（静态访问） |

#### 组件生命周期接口

```java
// 组件接口 - 定义生命周期
public interface Component {
    boolean start();  // 启动
    boolean stop();   // 停止
}

// 服务接口 - 可处理消息
public interface Service extends Component {
    void handleMessage(Message msg);  // 处理消息
    String getId();                    // 服务 ID
    int getSyncLevel();                // 同步级别
    List<String> getAliasIds();        // 别名
}
```

#### 获取组件

```java
// 通过 Platform 静态方法获取
DatabaseFacade dbf = Platform.getComponentLoader().getComponent(DatabaseFacade.class);

// 或使用 Spring 注入
@Autowired
private DatabaseFacade dbf;
```

### 2.3 数据库访问层 (DatabaseFacade)

#### 核心类

| 类/接口 | 路径 | 说明 |
|---------|------|------|
| `DatabaseFacade` | `core/db/DatabaseFacade.java` | 数据库门面接口 |
| `DatabaseFacadeImpl` | `core/db/DatabaseFacadeImpl.java` | JPA/Hibernate 实现 |
| `SQLBatch` | `core/db/SQLBatch.java` | SQL 批处理抽象类 |
| `SimpleQuery` | `core/db/SimpleQuery.java` | 查询构建器 |
| `SQL` | `core/db/SQL.java` | SQL 工具类 |

#### 核心功能

| 方法 | 说明 |
|------|------|
| `persist(entity)` | 持久化实体 |
| `update(entity)` | 更新实体 |
| `remove(entity)` | 删除实体（支持软删除） |
| `findByUuid(uuid, class)` | 按 UUID 查找 |
| `createQuery(class)` | 创建查询 |
| `eoCleanup(class)` | 清理软删除的实体对象 |

#### 软删除机制

```java
// VO 定义时使用 @EO 注解指定软删除实体
@Entity
@Table
@EO(EOClazz = VmInstanceEO.class)
public class VmInstanceVO extends VmInstanceAO {
    // ...
}

// 软删除级联
@Entity
@SoftDeletionCascades({
    @SoftDeletionCascade(parent = VmInstanceVO.class, joinColumn = "vmInstanceUuid")
})
public class VmNicVO extends VmNicAO {
    // ...
}
```

#### 死锁处理

```java
// 使用 @DeadlockAutoRestart 自动重试
@DeadlockAutoRestart
public void updateResource() {
    // 可能发生死锁的操作
}
```

### 2.4 异步编程模型 (ThreadFacade)

#### 核心类

| 类/接口 | 路径 | 说明 |
|---------|------|------|
| `ThreadFacade` | `core/thread/ThreadFacade.java` | 线程管理门面接口 |
| `ThreadFacadeImpl` | `core/thread/ThreadFacadeImpl.java` | 线程池实现 |
| `Completion` | `header/core/Completion.java` | 无返回值回调 |
| `ReturnValueCompletion` | `header/core/ReturnValueCompletion.java` | 有返回值回调 |
| `NoErrorCompletion` | `header/core/NoErrorCompletion.java` | 无错误回调 |

#### 回调接口层次

```
AbstractCompletion (抽象基类)
├── Completion (无返回值回调)
│   ├── success()
│   └── fail(ErrorCode)
│
├── ReturnValueCompletion<T> (有返回值回调)
│   ├── success(T returnValue)
│   └── fail(ErrorCode)
│
└── NoErrorCompletion (无错误回调)
    └── done()
```

#### 异步执行注解

| 注解 | 说明 |
|------|------|
| `@AsyncThread` | 异步执行方法 |
| `@SyncThread` | 同步执行（带同步级别） |
| `@ScheduledThread` | 定时执行 |

#### 同步控制机制

```java
// SyncTask 接口 - 支持同步级别
public interface SyncTask<T> extends Task<T> {
    String getSyncSignature();  // 同步签名，相同签名的任务串行执行
    int getSyncLevel();         // 同步级别
}

// 使用示例
thdf.syncSubmit(new SyncTask<Void>() {
    @Override
    public String getSyncSignature() {
        return "host-" + hostUuid;  // 同一主机的操作串行执行
    }
    
    @Override
    public Void call() throws Exception {
        // 执行操作
        return null;
    }
});
```

#### SingleFlight 模式

```java
// 相同 key 的请求只执行一次
thdf.singleFlightSubmit(new SingleFlightTask(completion) {
    @Override
    public String getSingleFlightKey() {
        return "refresh-" + resourceUuid;
    }
    
    @Override
    public void run(SingleFlightCompletion completion) {
        // 执行操作
        completion.success(result);
    }
});
```

### 2.5 错误处理机制 (ErrorFacade)

#### 核心类

| 类/接口 | 路径 | 说明 |
|---------|------|------|
| `ErrorFacade` | `core/errorcode/ErrorFacade.java` | 错误处理门面接口 |
| `ErrorFacadeImpl` | `core/errorcode/ErrorFacadeImpl.java` | 错误处理实现 |
| `ErrorCode` | `header/errorcode/ErrorCode.java` | 错误码类 |
| `OperationFailureException` | `header/exception/OperationFailureException.java` | 操作失败异常 |

#### AOP 异常处理注解

| 注解 | 切面 | 说明 |
|------|------|------|
| `@ExceptionSafe` | `ExceptionSafeAspect.aj` | 自动捕获异常 |
| `@MessageSafe` | `MessageSafeAspect.aj` | 消息处理异常自动回复 |

#### 错误码定义

```xml
<!-- XML 定义错误码 -->
<error>
    <code>SYS.1001</code>
    <description>Internal error</description>
</error>
```

#### 异常处理流程

```
方法抛出异常
    ↓
AOP 拦截 (ExceptionSafeAspect / MessageSafeAspect)
    ↓
转换为 ErrorCode
    ↓
通过 Completion.fail(ErrorCode) 回调
    或
通过 bus.replyErrorByMessageType() 回复消息
```

---

## 三、API 消息设计

API 消息定义位于 `header/src/main/java/org/zstack/header/` 目录。

### 3.1 消息类型层次

| 类型 | 命名模式 | 基类 | 用途 |
|------|---------|------|------|
| 请求消息 | `API*Msg` | `APIMessage` | 客户端请求 |
| 异步事件 | `API*Event` | `APIEvent` | 异步操作结果通知 |
| 同步回复 | `API*Reply` | `APIReply` | 同步查询结果 |

#### 消息流模式

```
同步 API:  APIMessage → CloudBus.call() → APIReply
异步 API:  APIMessage → CloudBus.send() → APIEvent (通过回调)

资源操作模式:
- Create: APICreateXxxMsg → APICreateXxxEvent
- Update: APIUpdateXxxMsg → APIUpdateXxxEvent
- Delete: APIDeleteXxxMsg → APIDeleteXxxEvent
- Query:  APIQueryXxxMsg  → APIQueryXxxReply
- Get:    APIGetXxxMsg    → APIGetXxxReply
```

### 3.2 数据模型架构

#### 层次结构

```
AO (Abstract Object) - 定义字段
  └── VO (Value Object) - 数据库实体
      └── EO (Entity Object) - 软删除视图

数据流转:
VO (数据库实体) → Inventory (DTO) → API Response (REST 响应)
```

#### VO 定义示例

```java
@Entity
@Table
@EO(EOClazz = VmInstanceEO.class)
@BaseResource
@EntityGraph(
    parents = {
        @EntityGraph.Neighbour(type = ZoneVO.class, myField = "zoneUuid", targetField = "uuid"),
    },
    friends = {
        @EntityGraph.Neighbour(type = ImageVO.class, myField = "imageUuid", targetField = "uuid"),
    }
)
public class VmInstanceVO extends VmInstanceAO implements OwnedByAccount, ToInventory {
    // 字段定义
}
```

#### Inventory 定义示例

```java
@Inventory(mappingVOClass = VmInstanceVO.class)
@ExpandedQueries({
    @ExpandedQuery(expandedField = "zone", inventoryClass = ZoneInventory.class, foreignKey = "zoneUuid")
})
public class VmInstanceInventory implements Serializable {
    
    public static VmInstanceInventory valueOf(VmInstanceVO vo) {
        return new VmInstanceInventory(vo);
    }
    
    public static List<VmInstanceInventory> valueOf(Collection<VmInstanceVO> vos) {
        return vos.stream().map(VmInstanceInventory::valueOf).collect(Collectors.toList());
    }
}
```

### 3.3 关键注解

#### @RestRequest

```java
@RestRequest(
    path = "/vm-instances",
    method = HttpMethod.POST,
    responseClass = APICreateVmInstanceEvent.class,
    parameterName = "params"
)
public class APICreateVmInstanceMsg extends APICreateMessage {
    // ...
}
```

#### @APIParam

```java
@APIParam(
    required = true,           // 必填
    maxLength = 255,           // 最大长度
    resourceType = ZoneVO.class, // 资源类型验证
    checkAccount = true,       // 账户权限检查
    validValues = {"Enabled", "Disabled"} // 有效值
)
private String zoneUuid;
```

#### @RestResponse

```java
@RestResponse(allTo = "inventory")  // 所有字段映射到 inventory
public class APICreateVmInstanceEvent extends APIEvent {
    private VmInstanceInventory inventory;
}
```

---

## 四、REST API 层

REST API 实现位于 `rest/src/main/java/org/zstack/rest/` 目录。

### 4.1 核心组件

| 类 | 说明 |
|----|------|
| `RestServer` | REST API 服务器核心实现 |
| `RestServerController` | Spring MVC 入口控制器 |
| `RateLimiter` | 限流器（Token Bucket 算法） |
| `AsyncRestApiStore` | 异步 API 存储接口 |
| `MysqlAsyncRestStore` | 异步 API 存储实现 |
| `RequestData` | 请求数据封装 |

### 4.2 请求处理流程

```
HTTP 请求
    ↓
RestServerController (Spring MVC 入口)
    ↓
RestServer.handle()
    ↓
┌─────────────────────────────────┐
│ 1. 限流检查 (RateLimiter)       │
│ 2. 请求拦截器处理               │
│ 3. 路径匹配 (AntPathMatcher)    │
│ 4. 认证处理                     │
│    ├─ OAuth: Authorization: OAuth <token>
│    └─ AccessKey: Authorization: ZStack <id>:<signature>
│ 5. 参数解析                     │
│    ├─ GET/DELETE: Query String  │
│    └─ POST/PUT: Request Body    │
│ 6. 构建 APIMessage              │
│ 7. 发送到 CloudBus              │
│    ├─ 同步调用 → 200 OK         │
│    └─ 异步调用 → 202 Accepted   │
└─────────────────────────────────┘
```

### 4.3 异步 API 机制

#### 工作流程

```
1. 请求提交
   POST /v1/vm-instances
   → 保存到 AsyncRestVO (状态: processing)
   → 发送消息到 CloudBus
   → 返回 202 Accepted
   → Location: /v1/api-jobs/{uuid}

2. 状态查询
   GET /v1/api-jobs/{uuid}
   → processing: 202 Accepted
   → done + success: 200 OK
   → done + failed: 503 Service Unavailable
   → expired: 404 Not Found

3. WebHook 回调 (可选)
   请求头: X-Web-Hook: <url>
   任务完成时自动 POST 结果到 WebHook URL
```

### 4.4 限流机制

基于 Token Bucket 算法实现：

```java
public class RateLimiter {
    private final LoadingCache<String, TokenBucket> requestCache;
    private final int maxRequestsPerMinute;
    
    // 基于客户端 IP 限流
    public boolean isRateLimitExceeded(String clientIp) {
        TokenBucket bucket = requestCache.get(clientIp);
        return !bucket.tryConsume();
    }
}
```

配置：
- 默认限制：12000 请求/分钟
- 可通过 `RestGlobalProperty.REST_RATE_LIMITS` 配置

---

## 五、工作流引擎 (FlowChain)

工作流实现位于 `core/src/main/java/org/zstack/core/workflow/` 目录。

### 5.1 工作流类型

| 类型 | 特点 | 适用场景 |
|------|------|---------|
| `SimpleFlowChain` | 内存执行，无持久化，性能好 | 简单业务流程 |
| `WorkFlowChain` | 数据库持久化，支持恢复 | 复杂流程，需状态恢复 |
| `AsyncWorkFlowChain` | 异步执行和回调 | 异步场景 |
| `ShareFlowChain` | 动态流程编排 | 运行时构建流程 |

### 5.2 Flow 接口

```java
public interface Flow {
    // 执行流程
    void run(FlowTrigger trigger, Map data);
    
    // 回滚流程
    void rollback(FlowRollback trigger, Map data);
    
    // 是否跳过（默认不跳过）
    default boolean skip(Map data) { return false; }
}

public interface FlowTrigger {
    void next();              // 继续下一个流程
    void fail(ErrorCode err); // 失败并触发回滚
}

public interface FlowRollback {
    void rollback();          // 继续回滚
    void skipRestRollbacks(); // 跳过剩余回滚
}
```

#### 使用示例

```java
new SimpleFlowChain()
    .setName("create-vm-flow")
    .then(new Flow() {
        @Override
        public void run(FlowTrigger trigger, Map data) {
            // 步骤 1：分配资源
            data.put("allocated", true);
            trigger.next();
        }
        
        @Override
        public void rollback(FlowRollback trigger, Map data) {
            // 回滚：释放资源
            releaseResource();
            trigger.rollback();
        }
    })
    .then(new NoRollbackFlow() {
        @Override
        public void run(FlowTrigger trigger, Map data) {
            // 步骤 2：启动 VM（无需回滚）
            trigger.next();
        }
    })
    .done(new FlowDoneHandler(completion) {
        @Override
        public void handle(Map data) {
            // 完成处理
            completion.success();
        }
    })
    .error(new FlowErrorHandler(completion) {
        @Override
        public void handle(ErrorCode err, Map data) {
            // 错误处理
            completion.fail(err);
        }
    })
    .start();
```

### 5.3 回滚机制

#### SimpleFlowChain 回滚

- 使用栈结构（LIFO）实现逆序回滚
- 调用 `trigger.next()` 时，当前 Flow 入栈
- 调用 `trigger.fail(err)` 时，触发回滚
- 支持 `skipRestRollbacks()` 跳过剩余回滚

#### WorkFlowChain 回滚

- 每个 Flow 状态持久化到 `WorkFlowVO`
- 支持 `carryOn(chainUuid)` 从断点继续执行
- 自动判断继续策略：重启或继续回滚

---

## 六、插件扩展系统

插件位于 `plugin/` 目录，每个插件是独立的 Maven 模块。

### 6.1 插件类型分类

| 类型 | 插件 | 说明 |
|------|------|------|
| **Hypervisor** | `kvm` | KVM 虚拟化 |
| **Primary Storage** | `nfsPrimaryStorage`, `localstorage`, `ceph`, `sharedMountPointPrimaryStorage` | 主存储 |
| **Backup Storage** | `sftpBackupStorage` | 备份存储 |
| **Network Provider** | `virtualRouterProvider`, `flatNetworkProvider`, `sdnController` | 网络服务提供者 |
| **Network Service** | `securityGroup`, `portForwarding`, `loadBalancer`, `eip`, `vip` | 网络服务 |
| **认证/授权** | `ldap`, `loginPlugin`, `directory` | 身份认证 |

### 6.2 插件注册方式

#### XML 配置方式

```xml
<!-- 在 Spring XML 中注册插件 -->
<bean id="KVMHostFactory" class="org.zstack.kvm.KVMHostFactory">
    <zstack:plugin>
        <zstack:extension interface="org.zstack.header.host.HypervisorFactory" />
        <zstack:extension interface="org.zstack.header.Component" />
        <zstack:extension interface="org.zstack.header.Service" />
    </zstack:plugin>
</bean>
```

#### 代码方式 (PluginDSL)

```java
// 使用 PluginDSL 在代码中声明扩展
PluginDSL.plugin("myPlugin")
    .extension(SomeExtensionPoint.class, this)
    .register();
```

### 6.3 扩展点类型

#### Factory 模式

```java
// 定义工厂接口
public interface HypervisorFactory {
    HypervisorType getHypervisorType();
    Host createHost(HostVO vo);
}

// 实现工厂
public class KVMHostFactory implements HypervisorFactory {
    public static final HypervisorType hypervisorType = new HypervisorType("KVM");
    
    @Override
    public HypervisorType getHypervisorType() {
        return hypervisorType;
    }
}

// 使用工厂
List<HypervisorFactory> factories = pluginRgty.getExtensionList(HypervisorFactory.class);
```

#### Backend 模式

```java
// 定义后端接口
public interface PortForwardingBackend {
    NetworkServiceProviderType getProviderType();
    void applyPortForwardingRule(PortForwardingRuleInventory rule, Completion completion);
}

// 实现后端
public class VirtualRouterPortForwardingBackend implements PortForwardingBackend {
    @Override
    public NetworkServiceProviderType getProviderType() {
        return VirtualRouterConstant.VIRTUAL_ROUTER_PROVIDER_TYPE;
    }
}

// 注册和使用
Map<String, PortForwardingBackend> backends = new HashMap<>();
for (PortForwardingBackend backend : pluginRgty.getExtensionList(PortForwardingBackend.class)) {
    backends.put(backend.getProviderType().toString(), backend);
}
```

#### ExtensionPoint 回调模式

```java
// 定义扩展点接口
public interface KVMStartVmExtensionPoint {
    void beforeStartVmOnKvm(KVMHostInventory host, VmInstanceSpec spec, StartVmCmd cmd);
    void afterStartVmOnKvm(KVMHostInventory host, VmInstanceSpec spec, StartVmCmd cmd);
}

// 实现扩展点
public class CephPrimaryStorageFactory implements KVMStartVmExtensionPoint {
    @Override
    public void beforeStartVmOnKvm(KVMHostInventory host, VmInstanceSpec spec, StartVmCmd cmd) {
        // 在 VM 启动前执行 Ceph 相关逻辑
    }
}

// 触发扩展点
for (KVMStartVmExtensionPoint ext : pluginRgty.getExtensionList(KVMStartVmExtensionPoint.class)) {
    ext.beforeStartVmOnKvm(host, spec, cmd);
}
```

---

## 七、配置管理系统

### 7.1 GlobalConfig (全局配置)

位于 `core/src/main/java/org/zstack/core/config/` 目录。

#### 核心类

| 类 | 说明 |
|----|------|
| `GlobalConfig` | 配置项实体 |
| `GlobalConfigFacade` | 配置管理门面接口 |
| `GlobalConfigFacadeImpl` | 配置管理实现 |
| `GlobalConfigVO` | 配置数据库实体 |

#### 配置来源优先级

```
1. 数据库（运行时值）
2. XML 配置文件（conf/globalConfig/*.xml）
3. Java 注解（@GlobalConfigDef）
4. 扩展点自动生成
```

#### 定义方式

**XML 方式**:
```xml
<!-- conf/globalConfig/xxx.xml -->
<globalConfig>
    <config>
        <category>vm</category>
        <name>vm.cleanTraffic</name>
        <description>Whether to clean traffic when deleting VM</description>
        <defaultValue>false</defaultValue>
        <type>java.lang.Boolean</type>
    </config>
</globalConfig>
```

**注解方式**:
```java
@GlobalConfigDefinition
public class VmGlobalConfig {
    public static final String CATEGORY = "vm";
    
    @GlobalConfigValidation(notNull = true)
    public static GlobalConfig VM_CLEAN_TRAFFIC = new GlobalConfig(CATEGORY, "vm.cleanTraffic");
}
```

#### 使用方式

```java
// 获取配置值
boolean cleanTraffic = VmGlobalConfig.VM_CLEAN_TRAFFIC.value(Boolean.class);

// 监听配置变更
VmGlobalConfig.VM_CLEAN_TRAFFIC.installUpdateExtension((oldValue, newValue) -> {
    // 处理配置变更
});
```

### 7.2 ResourceConfig (资源配置)

位于 `resourceconfig/src/main/java/org/zstack/resourceconfig/` 目录。

#### 与 GlobalConfig 的区别

| 特性 | GlobalConfig | ResourceConfig |
|------|-------------|----------------|
| 作用域 | 全局共享 | 资源级独立设置 |
| 存储 | `GlobalConfigVO` | `ResourceConfigVO` |
| 查找顺序 | 直接返回全局值 | 资源 → 父资源 → GlobalConfig |

#### 绑定方式

```java
@GlobalConfigDefinition
public class VmGlobalConfig {
    // 绑定到 VM 和 Cluster，支持多级继承
    @BindResourceConfig({VmInstanceVO.class, ClusterVO.class})
    public static GlobalConfig VM_VIDEO_TYPE = new GlobalConfig(CATEGORY, "videoType");
}
```

#### 查找优先级

```
1. 资源自身配置（ResourceConfigVO 中 resourceUuid 匹配）
2. 父资源配置（通过 DBGraph 查找父资源）
3. GlobalConfig 默认值
```

#### API 接口

| API | 说明 |
|-----|------|
| `APIUpdateResourceConfigMsg` | 更新资源配置 |
| `APIDeleteResourceConfigMsg` | 删除资源配置 |
| `APIGetResourceConfigMsg` | 获取资源配置 |
| `APIGetResourceBindableConfigMsg` | 查询可绑定的配置 |

---

## 八、启动流程

### 8.1 启动入口

```
Servlet 容器启动
    ↓
BootstrapWebListener.contextInitialized()
    → 触发 Platform 静态初始化
    ↓
BootstrapContextLoaderListener.contextInitialized()
    → 加载 Spring 上下文 (beanRefContext.xml → zstack.xml)
    ↓
ComponentLoaderWebListener.contextInitialized()
    → Platform.createComponentLoaderFromWebApplicationContext()
    → ManagementNodeManager.startNode()
```

### 8.2 管理节点启动步骤

`ManagementNodeManagerImpl.start()` 使用 FlowChain 编排启动步骤：

| 步骤 | 名称 | 说明 |
|------|------|------|
| 1 | `bootstrap-cloudbus` | CloudBus 初始化（已在 Platform 中完成） |
| 2 | `populate-components` | 收集所有 Component 和 Service |
| 3 | `register-node-on-cloudbus` | 注册管理节点服务到 CloudBus |
| 4 | `call-prepare-db-extension` | 准备数据库初始值 |
| 5 | `start-components` | 启动所有组件和服务 |
| 6 | `create-DB-record` | 创建/更新 ManagementNodeVO |
| 7 | `start-heartbeat` | 启动心跳机制 |
| 8 | `start-api-mediator` | 启动 API 中介器 |
| 9 | `set-node-to-running` | 设置节点状态为 RUNNING |
| 10 | `I-join` | 触发节点加入事件 |
| 11 | `node-is-ready` | 调用 ManagementNodeReadyExtensionPoint |
| 12 | `listen-node-life-cycle-events` | 监听节点生命周期事件 |
| 13 | `say-I-join` | 通知其他管理节点 |

### 8.3 数据库 Schema 管理

#### Flyway 迁移

```bash
# 部署脚本 (conf/deploydb.sh)
1. 创建数据库（如果不存在）
2. 复制 SQL 文件到 Flyway 目录
   - conf/db/V0.6__schema.sql (基础 schema)
   - conf/db/upgrade/*.sql (升级脚本)
3. Flyway clean (清理)
4. Flyway baseline (创建 baseline)
5. Flyway migrate (执行迁移，outOfOrder=true)
```

#### Schema 文件结构

```
conf/db/
├── V0.6__schema.sql           # 基础 schema
├── upgrade/
│   ├── V2.5.0__schema.sql     # 升级脚本
│   ├── V3.9.0__schema.sql
│   └── ...
├── beforeMigrate.sql          # 迁移前执行
└── beforeValidate.sql         # 验证前执行
```

---

## 九、测试框架

测试框架位于 `test/` 和 `testlib/` 目录。

### 9.1 测试框架结构

```groovy
// 测试用例基类
class ExampleCase extends SubCase {
    EnvSpec env

    @Override
    void setup() {
        // 配置 Spring，指定需要的服务模块
        spring {
            sftpBackupStorage()
            localStorage()
            kvm()
        }
    }

    @Override
    void environment() {
        // 定义测试环境
        env = env {
            zone {
                name = "zone1"
                cluster { ... }
            }
        }
    }

    @Override
    void test() {
        // 创建环境并执行测试
        env.create {
            testMethod1()
            testMethod2()
        }
    }

    @Override
    void clean() {
        env.delete()
    }
}
```

#### 生命周期

```
setup()       → 配置 Spring，指定模块
environment() → 定义测试环境（不创建）
test()        → env.create() 创建环境，执行测试
clean()       → 清理环境
```

### 9.2 模拟器机制

模拟器拦截 Agent HTTP 请求，返回模拟响应。

```groovy
class KVMSimulator implements Simulator {
    @Override
    void registerSimulators(EnvSpec spec) {
        // 注册 KVM Agent 路径处理器
        spec.simulator(KVMConstant.KVM_HOST_CAPACITY_PATH) { HttpEntity<String> e, EnvSpec espec ->
            def rsp = new KVMAgentCommands.HostCapacityResponse()
            rsp.success = true
            rsp.cpuNum = 8
            rsp.totalMemory = SizeUnit.GIGABYTE.toByte(32)
            return rsp
        }

        spec.simulator(KVMConstant.KVM_START_VM_PATH) { HttpEntity<String> e ->
            def cmd = JSONObjectUtil.toObject(e.body, KVMAgentCommands.StartVmCmd.class)
            def rsp = new KVMAgentCommands.StartVmResponse()
            rsp.success = true
            return rsp
        }
    }
}
```

#### 处理器类型

| 类型 | 说明 |
|------|------|
| `simulator(path, closure)` | 基础 HTTP 处理器 |
| `preSimulator(path, closure)` | 请求前处理器 |
| `afterSimulator(path, closure)` | 请求后处理器 |
| `hijackSimulator(path, closure)` | 最终拦截处理器 |

### 9.3 环境定义 DSL

```groovy
env = env {
    zone {
        name = "zone"
        cluster {
            name = "cluster"
            kvmHost {
                name = "host"
                totalCpu = 8
                totalMem = SizeUnit.GIGABYTE.toByte(32)
            }
        }
        instanceOffering {
            name = "instanceOffering"
            cpuNum = 2
            memorySize = SizeUnit.GIGABYTE.toByte(4)
        }
        diskOffering {
            name = "diskOffering"
            diskSize = SizeUnit.GIGABYTE.toByte(100)
        }
        sftpBackupStorage {
            name = "bs"
            url = "/tmp/bs"
        }
        nfsPrimaryStorage {
            name = "ps"
            url = "/tmp/ps"
        }
        l2NoVlanNetwork {
            name = "l2"
            l3Network {
                name = "l3"
                ip {
                    startIp = "192.168.0.2"
                    endIp = "192.168.0.254"
                    gateway = "192.168.0.1"
                    netmask = "255.255.255.0"
                }
            }
        }
        vm {
            name = "vm"
            useHost("host")
            useL3Networks("l3")
            useInstanceOffering("instanceOffering")
            useImage("image")
        }
    }
}
```

---

## 十、架构特性总结

### 设计模式应用

| 模式 | 应用场景 |
|------|---------|
| **门面模式** | `DatabaseFacade`, `ThreadFacade`, `ErrorFacade`, `GlobalConfigFacade` |
| **工厂模式** | `HypervisorFactory`, `PrimaryStorageFactory`, `BackupStorageFactory` |
| **观察者模式** | 事件订阅机制、配置变更监听 |
| **策略模式** | 插件扩展点、Backend 接口 |
| **模板方法** | `SQLBatch`, `AbstractService`, `Flow` |
| **责任链模式** | `FlowChain` 工作流 |
| **代理模式** | AOP 增强（AspectJ） |

### 架构优势

| 优势 | 说明 |
|------|------|
| **高度解耦** | 消息总线 + 插件系统实现模块解耦 |
| **可扩展性** | 扩展点机制便于新功能接入 |
| **异步优先** | 全异步架构提升系统吞吐量 |
| **容错性** | FlowChain 支持回滚和状态恢复 |
| **统一抽象** | 门面模式简化复杂性 |
| **测试友好** | 模拟器机制便于集成测试 |
| **配置灵活** | GlobalConfig + ResourceConfig 多级配置 |

### 关键目录索引

| 目录 | 内容 |
|------|------|
| `core/` | 核心框架（消息总线、数据库、线程、错误处理） |
| `header/` | API 消息定义、VO/Inventory、常量枚举 |
| `rest/` | REST API 服务器 |
| `portal/` | 管理节点启动入口 |
| `configuration/` | 配置管理 |
| `resourceconfig/` | 资源级配置 |
| `plugin/` | 插件模块 |
| `test/` | 集成测试 |
| `testlib/` | 测试框架库 |
| `conf/` | 配置文件和数据库 Schema |

---

## 附录：常用类速查

### 核心框架

| 类 | 包路径 | 用途 |
|----|--------|------|
| `Platform` | `org.zstack.core` | 平台入口，静态访问组件 |
| `CloudBus` | `org.zstack.core.cloudbus` | 消息总线 |
| `DatabaseFacade` | `org.zstack.core.db` | 数据库访问 |
| `ThreadFacade` | `org.zstack.core.thread` | 线程管理 |
| `ErrorFacade` | `org.zstack.core.errorcode` | 错误处理 |
| `PluginRegistry` | `org.zstack.core.componentloader` | 插件注册 |
| `GlobalConfigFacade` | `org.zstack.core.config` | 全局配置 |

### API 相关

| 类 | 包路径 | 用途 |
|----|--------|------|
| `APIMessage` | `org.zstack.header.message` | API 消息基类 |
| `APIEvent` | `org.zstack.header.message` | API 事件基类 |
| `APIReply` | `org.zstack.header.message` | API 回复基类 |
| `RestServer` | `org.zstack.rest` | REST API 服务器 |

### 工作流

| 类 | 包路径 | 用途 |
|----|--------|------|
| `SimpleFlowChain` | `org.zstack.core.workflow` | 简单工作流 |
| `Flow` | `org.zstack.header.core.workflow` | 流程接口 |
| `FlowTrigger` | `org.zstack.header.core.workflow` | 流程触发器 |

### 回调

| 类 | 包路径 | 用途 |
|----|--------|------|
| `Completion` | `org.zstack.header.core` | 无返回值回调 |
| `ReturnValueCompletion` | `org.zstack.header.core` | 有返回值回调 |
| `NoErrorCompletion` | `org.zstack.header.core` | 无错误回调 |

---

*文档版本: 1.0*
*最后更新: 2025-01*
