# Container 容器模块分析报告

## 1. 模块概述

### 1.1 模块位置
- **主目录**: `premium/plugin-premium/container/`
- **包路径**: `org.zstack.container`

### 1.2 模块定位
Container 模块是 ZStack 的容器平台管理模块，主要用于:
- 对接外部 Kubernetes 集群（通过 Zaku 或原生 K8s API）
- 同步容器资源（集群、节点、Pod、Service）到 ZStack
- 管理容器镜像仓库
- 支持 GPU 资源调度（HAMi 虚拟化 GPU）

### 1.3 核心设计理念
与 KVM/Baremetal 模块不同，Container 模块采用**被动同步**而非主动创建的设计：
- NativeHost 不支持通过 API 主动创建
- 所有资源都是从外部容器平台同步而来
- 使用周期性任务保持资源状态同步

---

## 2. 核心 VO 结构

### 2.1 NativeHostVO

**文件位置**: `container/src/main/java/org/zstack/container/entity/NativeHostVO.java`

```java
@Entity
@Table
@PrimaryKeyJoinColumn(name="uuid", referencedColumnName="uuid")
@EO(EOClazz = HostEO.class, needView = false)
@AutoDeleteTag
public class NativeHostVO extends HostVO {
    @Column
    @ForeignKey(parentEntityClass = ContainerManagementEndpointVO.class,
                onDeleteAction = ReferenceOption.CASCADE)
    private String endpointUuid;
}
```

**继承关系**:
```
ResourceVO
  └── HostAO (abstract mapped superclass)
        └── HostVO
              └── NativeHostVO  <-- 容器节点
```

**核心字段**:
| 字段 | 类型 | 说明 |
|------|------|------|
| uuid | String | 主键，与 HostVO 共享（来自 K8s Node UID） |
| endpointUuid | String | 外键，关联 ContainerManagementEndpointVO |

**关键特点**:
1. 继承 HostVO 的所有字段（name, managementIp, state, status, clusterUuid, zoneUuid 等）
2. 只增加了一个 `endpointUuid` 字段用于关联管理端点
3. 使用 CASCADE 删除策略：Endpoint 删除时自动删除所有 NativeHost
4. UUID 来源于 Kubernetes Node 的 UID（去掉连字符）

### 2.2 NativeClusterVO

**文件位置**: `container/src/main/java/org/zstack/container/entity/NativeClusterVO.java`

```java
@Entity
@Table
@PrimaryKeyJoinColumn(name="uuid", referencedColumnName="uuid")
@EO(EOClazz = ClusterEO.class, needView = false)
@AutoDeleteTag
public class NativeClusterVO extends ClusterVO {
    @Column
    @ForeignKey(parentEntityClass = ContainerManagementEndpointVO.class,
                onDeleteAction = ReferenceOption.CASCADE)
    private String endpointUuid;

    @Column
    private long id;                    // Zaku 集群 ID

    @Column
    private String bizUrl;              // 业务 URL

    @Column
    private String masterUrl;           // K8s Master URL

    @Column(length = 65535)
    @NoLogging
    private String kubeConfig;          // K8s 凭证（敏感信息）

    @Column
    private String prometheusURL;       // 监控地址

    @Column
    private String version;             // K8s 版本

    @Column
    private Integer nodeCount;          // 节点数量

    @Column
    private String createType;          // 创建类型

    @Column
    @Enumerated(EnumType.STRING)
    private ClusterStatusType status;   // 集群状态
}
```

**继承关系**:
```
ResourceVO
  └── ClusterAO (abstract mapped superclass)
        └── ClusterVO
              └── NativeClusterVO  <-- 容器集群
```

**关键特点**:
1. 包含完整的 Kubernetes 集群连接信息
2. `kubeConfig` 存储 K8s 认证凭证，使用 `@NoLogging` 防止日志泄露
3. 支持 Prometheus 监控集成
4. `id` 字段用于与 Zaku 平台的集群 ID 对应

### 2.3 ContainerManagementEndpointVO

**文件位置**: `container/src/main/java/org/zstack/container/entity/ContainerManagementEndpointVO.java`

```java
@Entity
@Table
@AutoDeleteTag
public class ContainerManagementEndpointVO extends ResourceVO {
    @Column
    private String name;
    @Column
    private String description;
    @Column
    private String managementIp;        // 管理 IP
    @Column
    private Integer managementPort;     // 管理端口
    @Column
    private String vendor;              // 提供商类型 (zaku/kubernetes)
    @Column
    private String accessKeyId;         // 认证 Key ID
    @Column
    private String accessKeySecret;     // 认证 Secret
    @Column
    private Timestamp createDate;
    @Column
    private Timestamp lastOpDate;
}
```

**角色定位**:
- 容器管理平台的连接入口
- 类似于 Baremetal 的 PXE Server，但用于容器平台
- 一个 Endpoint 可以管理多个 NativeCluster 和 NativeHost

**资源层级关系**:
```
ContainerManagementEndpointVO (管理端点)
  ├── NativeClusterVO (容器集群) [多个]
  │     ├── NativeHostVO (容器节点) [多个]
  │     │     └── PodVO (Pod) [多个]
  │     └── KubernetesServiceVO (K8s Service) [多个]
  ├── ContainerBackupStorageVO (镜像仓库) [多个]
  │     └── ContainerImageVO (容器镜像) [多个]
  └── ...
```

### 2.4 PodVO

**文件位置**: `container/src/main/java/org/zstack/container/entity/PodVO.java`

```java
@Entity
@Table
@PrimaryKeyJoinColumn(name="uuid", referencedColumnName="uuid")
@EO(EOClazz = VmInstanceEO.class, needView = false)
public class PodVO extends VmInstanceVO {
    @Column
    @Enumerated(EnumType.STRING)
    private PodStatusPhase status;      // Pod 状态 (Running/Pending/Failed/Succeeded/Unknown)

    @Column
    private Long clusterId;             // 所属集群 ID

    @Column
    private String namespace;           // K8s Namespace
}
```

**设计亮点**:
- Pod 继承自 VmInstanceVO，复用虚拟机的资源模型
- 可以使用 VmInstance 的 CPU、内存、镜像等字段
- 统一了容器和虚拟机的资源视图

---

## 3. Endpoint 同步机制

### 3.1 K8s API 连接建立

**连接方式**:
```java
// ContainerUtils.java
public ApiClient getK8SApiClient(String accountUuid, String clusterUuid) {
    NativeClusterVO cluster = dbf.findByUuid(clusterUuid, NativeClusterVO.class);
    // 使用 kubeConfig 创建 ApiClient
    return ClientBuilder.kubeconfig(
        KubeConfig.loadKubeConfig(new StringReader(cluster.getKubeConfig()))
    ).build();
}
```

**认证机制**:
- 使用存储在 `NativeClusterVO.kubeConfig` 中的凭证
- 支持标准的 Kubernetes kubeconfig 格式

### 3.2 全量同步流程

**触发方式**: 周期性任务 + API 触发

```java
// ContainerManagerImpl.java
@Override
public boolean start() {
    containerUtils.init();
    submitTaskToSyncContainerManagementEndpoint();  // 启动周期性同步

    // 配置变更时重新提交任务
    ContainerGlobalConfig.SYNC_CONTAINER_RESOURCE_INTERVAL_SECONDS
        .installUpdateExtension((oldConfig, newConfig) -> {
            submitTaskToSyncContainerManagementEndpoint();
        });
    return true;
}
```

**同步流程 (doSyncContainerManagementEndpoint)**:

```
FlowChain: sync-container-management-endpoint
  │
  ├── Flow 1: sync-cluster
  │     └── provider.listClusters() -> saveAsNativeClusters()
  │
  ├── Flow 2: sync-node
  │     └── syncNodesFromCluster() -> 遍历每个集群同步节点
  │
  ├── Flow 3: sync-pod
  │     └── doSyncPodsFromNodes() -> 同步 Pod 到 PodVO
  │
  ├── Flow 4: sync-service
  │     └── doSyncServicesFromNodes() -> 同步 K8s Service
  │
  ├── Flow 5: delete-staled-pci-devices
  │     └── 清理孤立的 PCI 设备记录
  │
  ├── Flow 6: update-gpu-attachment-status
  │     └── 更新 GPU 分配状态
  │
  └── Flow 7: sync-repository-and-images
        └── 同步镜像仓库和容器镜像
```

### 3.3 节点同步详情 (syncNodesFromCluster)

```java
private void syncNodesFromCluster(NativeClusterVO cluster, WhileCompletion completion) {
    // 1. 检查 kubeConfig
    if (cluster.getKubeConfig() == null) {
        completion.done();
        return;
    }

    // 2. 调用 Provider 获取节点列表
    List<KubernetesNodeInventory> nodes = containerUtils
        .getNativeProvider(self.getVendor())
        .listNodes(self, NativeClusterInventory.valueOf(cluster));

    // 3. 保存或更新节点
    for (KubernetesNodeInventory node : nodes) {
        NativeHostVO host = containerUtils.toNativeHostVO(node);
        host.setEndpointUuid(self.getUuid());
        host.setZoneUuid(cluster.getZoneUuid());
        host.setClusterUuid(cluster.getUuid());

        NativeHostVO hostInDb = dbf.findByUuid(host.getUuid(), NativeHostVO.class);
        if (hostInDb != null) {
            // 更新现有节点
            hostInDb.setManagementIp(host.getManagementIp());
            hostInDb.setName(host.getName());
            hostInDb.setState(host.getState());
            hostInDb.setStatus(host.getStatus());
            dbf.update(hostInDb);
        } else {
            // 创建新节点
            dbf.persist(host);
        }

        // 4. 同步 GPU 设备
        saveGpuDevicesFromPciDevices(null, node.getUuid(), node.getPciDevices(), PciDeviceSource.Node);
    }

    // 5. 删除过期节点
    List<String> staleHostUuids = Q.New(NativeHostVO.class)
        .eq(NativeHostVO_.endpointUuid, self.getUuid())
        .notIn(NativeHostVO_.uuid, hostUuids)
        .select(NativeHostVO_.uuid)
        .listValues();
    deleteClusterResourcesByUuids(null, staleHostUuids, null);
}
```

### 3.4 增量同步机制

Container 模块主要采用**全量同步**策略：
- 每次同步都获取完整的资源列表
- 通过对比数据库记录识别新增/更新/删除的资源
- 使用 `SingleFlightTask` 防止并发同步

```java
private void syncContainerManagementEndpointInFlight(String zoneUuid, String hostUuid,
                                                      NativeProvider provider, Completion completion) {
    thdf.singleFlightSubmit(new SingleFlightTask(completion)
        .setSyncSignature(String.format("sync-container-management-endpoint-%s", self.getUuid()))
        .run((flightCompletion) -> {
            doSyncContainerManagementEndpoint(zoneUuid, hostUuid, provider, ...);
        })
    );
}
```

### 3.5 同步失败处理

- 使用 FlowChain 的 error handler 统一处理失败
- 失败时通过 Completion 回调返回 ErrorCode
- 日志记录详细的失败原因

---

## 4. NativeHost 创建特殊性

### 4.1 为什么不支持主动创建

**NativeFactory.java 核心代码**:
```java
public class NativeFactory implements HypervisorFactory {
    public static final HypervisorType hypervisorType =
        new HypervisorType(ContainerConstant.NATIVE_HYPERVISOR_TYPE);

    @Override
    public HostVO createHost(HostVO vo, AddHostMessage msg) {
        throw new UnsupportedOperationException();  // 直接抛出异常！
    }

    @Override
    public Host getHost(HostVO vo) {
        return new DummyNativeHost(vo);  // 返回 Dummy 实现
    }
}
```

**设计原因**:
1. **资源来源不同**: NativeHost 代表 Kubernetes 集群中的 Node，其生命周期由 K8s 管理
2. **避免数据不一致**: 如果允许主动创建，可能导致 ZStack 和 K8s 的数据不同步
3. **单一数据源原则**: K8s 是节点的权威数据源，ZStack 只做镜像

### 4.2 同步创建流程

NativeHost 的创建完全由同步流程驱动：

```
外部容器平台 (K8s/Zaku)
        │
        ▼
周期性同步任务触发
        │
        ▼
SyncContainerManagementEndpointMsg
        │
        ▼
ContainerEndpointBase.doSyncContainerManagementEndpoint()
        │
        ▼
syncNodesFromCluster()
        │
        ├── provider.listNodes()  <-- 从 K8s API 获取节点
        │
        ├── containerUtils.toNativeHostVO()  <-- 转换为 VO
        │
        └── dbf.persist(host)  <-- 持久化到数据库
```

### 4.3 DummyNativeHost 机制

**文件位置**: `container/src/main/java/org/zstack/container/DummyNativeHost.java`

```java
@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class DummyNativeHost implements Host {
    @Autowired
    private CloudBus bus;

    private HostVO self;

    public DummyNativeHost(HostVO vo) {
        this.self = vo;
    }

    @Override
    public void handleMessage(Message msg) {
        if (msg instanceof PingHostMsg) {
            bus.reply(msg, new PingHostReply());  // 直接返回成功
        } else {
            logger.debug(String.format("Unhandled message[%s] for host[uuid:%s, name:%s]",
                msg.getClass(), self.getUuid(), self.getName()));
            bus.reply(msg, new MessageReply());  // 其他消息返回空回复
        }
    }

    @Override
    public String getId() {
        return String.format("dummy-native-host-%s", self.getUuid());
    }
}
```

**设计目的**:
1. **占位实现**: 满足 HypervisorFactory 接口要求
2. **消息兜底**: 处理发送到 NativeHost 的消息，避免消息丢失
3. **Ping 响应**: 对 PingHostMsg 直接返回成功（不需要真正 ping K8s 节点）

**与 KVMHost 的对比**:
| 特性 | KVMHost | DummyNativeHost |
|------|---------|-----------------|
| 消息处理 | 完整的业务逻辑 | 简单的占位回复 |
| SSH 连接 | 需要 | 不需要 |
| Agent 通信 | 有 KVM Agent | 无 Agent |
| 资源操作 | 可以直接操作 | 通过 K8s API 操作 |

---

## 5. 状态管理

### 5.1 Node Ready/NotReady 状态映射

**状态来源**: Kubernetes Node Condition

```java
// KubernetesNativeProvider.java
inventory.setStatus(
    node.getStatus() != null && node.getStatus().getConditions() != null
    ? String.valueOf(node.getStatus().getConditions().stream()
        .filter(condition -> condition.getType().equals("Ready"))
        .map(V1NodeCondition::getStatus)
        .findFirst().orElse(null))
    : null
);
```

**状态映射**:
| K8s Node Condition | ZStack HostStatus |
|-------------------|-------------------|
| Ready=True | Connected |
| Ready=False | Disconnected |
| Ready=Unknown | Disconnected |

### 5.2 与 HostState/HostStatus 的映射

NativeHostVO 继承 HostVO，使用相同的状态模型：

```java
// ContainerUtils.java (toNativeHostVO 方法中)
public NativeHostVO toNativeHostVO(KubernetesNodeInventory node) {
    NativeHostVO host = new NativeHostVO();
    // ... 其他字段

    // 状态映射
    if ("True".equals(node.getStatus())) {
        host.setStatus(HostStatus.Connected);
        host.setState(HostState.Enabled);
    } else {
        host.setStatus(HostStatus.Disconnected);
        host.setState(HostState.Disabled);
    }

    return host;
}
```

### 5.3 状态同步触发机制

1. **周期性同步**: 由 `SYNC_CONTAINER_RESOURCE_INTERVAL_SECONDS` 配置控制
2. **手动触发**: 通过 `APISyncContainerManagementEndpointMsg` API
3. **单次同步**: 可指定 `hostUuid` 只同步特定主机

---

## 6. 集群绑定

### 6.1 NativeHost 与 NativeCluster 的关系

**关系图**:
```
NativeClusterVO (1) ──────────── (N) NativeHostVO
       │                               │
       │ clusterUuid (FK)              │ clusterUuid (FK)
       │                               │
       └───────────────────────────────┘
```

**绑定机制**:
```java
// syncNodesFromCluster 中
for (KubernetesNodeInventory node : nodes) {
    NativeHostVO host = containerUtils.toNativeHostVO(node);
    host.setEndpointUuid(self.getUuid());
    host.setZoneUuid(cluster.getZoneUuid());      // 继承 Zone
    host.setClusterUuid(cluster.getUuid());       // 绑定到 Cluster
    // ...
}
```

### 6.2 Cluster 兼容性验证

当前容器模块没有像 KVM 那样的严格兼容性验证，因为：
- 集群和节点都是从外部同步的
- 节点自动归属于其所在的 K8s 集群
- 不存在手动分配节点到集群的场景

---

## 7. API 消息清单

### 7.1 Endpoint 相关 API

| API 消息类 | 功能 | 说明 |
|-----------|------|------|
| `APIAddContainerManagementEndpointMsg` | 添加管理端点 | 注册新的容器平台连接 |
| `APIUpdateContainerManagementEndpointMsg` | 更新管理端点 | 修改端点配置 |
| `APIDeleteContainerManagementEndpointMsg` | 删除管理端点 | 删除端点及其所有资源 |
| `APISyncContainerManagementEndpointMsg` | 同步端点资源 | 触发全量同步 |
| `APIQueryContainerManagementEndpointMsg` | 查询端点 | 支持 QueryAPI |
| `APIDeleteContainerResourceFromEndpointMsg` | 删除端点资源 | 只删除资源不删除端点 |

### 7.2 资源查询 API

| API 消息类 | 功能 |
|-----------|------|
| `APIQueryNativeClusterMsg` | 查询容器集群 |
| `APIQueryNativeHostMsg` | 查询容器节点 |
| `APIQueryContainerImageMsg` | 查询容器镜像 |

### 7.3 其他 API

| API 消息类 | 功能 |
|-----------|------|
| `APIGetContainerUsageMsg` | 获取容器使用情况 |

### 7.4 内部消息

| 消息类 | 功能 |
|--------|------|
| `SyncContainerManagementEndpointMsg` | 内部同步触发 |
| `CreateDeploymentMsg` | 创建 K8s Deployment |
| `DeleteDeploymentMsg` | 删除 K8s Deployment |
| `CreateKubernetesServiceMsg` | 创建 K8s Service |
| `DeleteKubernetesServiceMsg` | 删除 K8s Service |
| `AddLabelToPodMsg` | 为 Pod 添加标签 |

---

## 8. Provider 机制

### 8.1 NativeProvider 接口

```java
public interface NativeProvider {
    NativeProviderType getNativeProviderType();
    List<KubernetesClusterInventory> listClusters(ContainerManagementEndpointVO endpoint);
    KubernetesClusterInventory getCluster(ContainerManagementEndpointVO endpoint, String clusterId);
    List<KubernetesNodeInventory> listNodes(ContainerManagementEndpointVO endpoint, NativeClusterInventory cluster);
    KubernetesNodeInventory getNode(ContainerManagementEndpointVO endpoint, NativeClusterInventory cluster, String nodeName);
    List<KubernetesPodInventory> listPods(ContainerManagementEndpointVO endpoint, NativeHostInventory node);
    List<KubernetesServiceInventory> listServices(String clusterUuid);
}
```

### 8.2 KubernetesNativeProvider

原生 Kubernetes 提供者实现：
- 直接使用 Kubernetes Java Client
- 通过 kubeConfig 认证
- 支持 Node、Pod、Service 的同步

**特点**:
```java
@Override
public List<KubernetesClusterInventory> listClusters(ContainerManagementEndpointVO endpoint) {
    throw new UnsupportedOperationException("Kubernetes does not support multiple clusters");
}
```
- 原生 K8s 不支持多集群管理，需要配合 Zaku 等多集群管理平台

---

## 9. 与 Header 计划 v1.2 的映射分析

### 9.1 容器资源统一硬件视图需求

| 现有容器资源 | 统一硬件视图映射 | 说明 |
|-------------|-----------------|------|
| NativeHostVO | UnifiedHardwareVO (type=CONTAINER_NODE) | 容器节点作为硬件资源 |
| NativeClusterVO | - | 集群是逻辑分组，不是物理硬件 |
| PodVO | - | Pod 是计算实例，不是硬件 |

### 9.2 HardwareReferenceVO 映射设计

```java
// 容器节点的 HardwareReference 映射
HardwareReferenceVO {
    uuid: "...",
    unifiedHardwareUuid: "<UnifiedHardwareVO.uuid>",
    resourceUuid: "<NativeHostVO.uuid>",
    resourceType: "NativeHostVO",
    syncDirection: BIDIRECTIONAL
}
```

### 9.3 接口适配要点

1. **管理 IP 映射**: NativeHostVO 继承 HostVO.managementIp，可直接使用
2. **状态映射**: K8s Ready 状态需要转换为统一的 HardwareState
3. **容量信息**: 需要从 K8s Node 的 allocatable/capacity 获取
4. **Endpoint 信息**: 需要在 UnifiedHardwareVO 中保留对 Endpoint 的引用

### 9.4 特殊考虑

1. **只读性质**: 容器节点不支持主动创建/删除，统一视图应标记为只读
2. **同步机制**: 需要考虑如何触发统一视图的更新（监听 NativeHostVO 变更）
3. **GPU 资源**: 容器模块有独特的 HAMi GPU 虚拟化支持，需要在统一视图中体现

---

## 10. 模块依赖关系

### 10.1 内部依赖

```
container/
  ├── entity/          # VO 定义
  ├── message/         # API 消息
  ├── hami/            # HAMi GPU 管理
  └── 核心类
       ├── ContainerManagerImpl     # 主服务实现
       ├── ContainerEndpointBase    # 端点操作基类
       ├── ContainerUtils           # 工具类
       ├── NativeFactory            # 主机工厂
       ├── DummyNativeHost          # 占位 Host 实现
       └── KubernetesNativeProvider # K8s 提供者
```

### 10.2 外部依赖

- **Kubernetes Java Client**: 用于 K8s API 调用
- **pciDevice 模块**: GPU/PCI 设备管理
- **core 模块**: CloudBus, DatabaseFacade, ThreadFacade 等

---

## 11. 总结

### 11.1 容器模块的核心特点

1. **被动同步**: 所有资源都是从外部容器平台同步而来，不支持主动创建
2. **层级结构**: Endpoint -> Cluster -> Host -> Pod 的清晰层级
3. **复用继承**: NativeHostVO 继承 HostVO，PodVO 继承 VmInstanceVO
4. **Provider 模式**: 支持多种容器平台（Zaku、原生 K8s）

### 11.2 与统一硬件管理的关联

- NativeHostVO 是容器模块中唯一代表物理/虚拟硬件的资源
- 需要通过 HardwareReferenceVO 建立与 UnifiedHardwareVO 的映射
- 同步机制需要考虑容器模块的被动同步特性

### 11.3 接口设计建议

作为容器模块架构 Lead，对于统一硬件管理接口设计，我建议：

1. **保留 Endpoint 关联**: UnifiedHardwareVO 需要支持关联 ContainerManagementEndpointVO
2. **标记只读属性**: 容器节点应标记为不可通过统一视图直接操作
3. **状态同步钩子**: 在 syncNodesFromCluster 中添加 Extension Point 用于通知统一视图更新
4. **GPU 资源抽象**: 需要考虑 HAMi 虚拟化 GPU 在统一视图中的表示方式
