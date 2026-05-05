# Container 模块深度代码分析

## 1. Endpoint 创建 FlowChain

### 文件：`APIAddContainerManagementEndpointMsg.java`

**行 17-31**: API 消息定义
```java
public class APIAddContainerManagementEndpointMsg extends APICreateMessage {
    @APIParam private String name;
    @APIParam(required = false) private String description;
    @APIParam private String managementIp;
    @APIParam private String vendor;
    @APIParam private Integer managementPort;
    @APIParam private String containerAccessKeyId;
    @APIParam private String containerAccessKeySecret;
}
```

**认证方式**: 使用 `containerAccessKeyId` 和 `containerAccessKeySecret`，对应 K8s 的 token/kubeconfig 认证。

---

## 2. 资源同步 FlowChain

### 文件：`ContainerEndpointBase.java`

**行 289-437**: `doSyncContainerManagementEndpoint()` 完整 FlowChain

### Flow 详解

#### Flow 1: `sync-cluster` (行 291-311)
```java
chain.then(new NoRollbackFlow() {
    String __name__ = "sync-cluster";

    @Override
    public void run(FlowTrigger trigger, Map data) {
        List<KubernetesClusterInventory> clusters = provider.listClusters(self);
        saveAsNativeClusters(clusters, zoneUuid);
        trigger.next();
    }
});
```

#### Flow 2: `sync-node` (行 312-333)
```java
chain.then(new NoRollbackFlow() {
    String __name__ = "sync-node";

    @Override
    public void run(FlowTrigger trigger, Map data) {
        syncNodes(new Completion(trigger) {
            @Override
            public void success() { trigger.next(); }

            @Override
            public void fail(ErrorCode errorCode) { trigger.fail(errorCode); }
        });
    }
});
```

#### Flow 3: `sync-pod` (行 334-350)
- 同步 K8s Pod

#### Flow 4: `sync-service` (行 351-367)
- 同步 K8s Service

#### Flow 5: `delete-staled-pci-devices` (行 368-387)
- 清理未关联的虚拟化 PCI 设备

#### Flow 6: `update-gpu-attachment-status` (行 388-408)
- 更新 GPU 分配状态

#### Flow 7: `sync-repository-and-images` (行 409-425)
- 同步镜像仓库和镜像

---

## 3. NativeCluster 创建

### Cluster 同步代码 (行 773-835)
```java
private void saveAsNativeClusters(List<KubernetesClusterInventory> clusters, String zoneUuid) {
    for (KubernetesClusterInventory cluster : clusters) {
        NativeClusterVO vo = containerUtils.toNativeClusterVO(cluster);
        vo.setEndpointUuid(self.getUuid());
        vo.setZoneUuid(zoneUuid);

        // 检查是否已存在
        if (Q.New(NativeClusterVO.class)
                .eq(NativeClusterVO_.endpointUuid, self.getUuid())
                .eq(NativeClusterVO_.id, vo.getId())
                .isExists()) {
            // 更新现有 cluster
            NativeClusterVO clusterInDb = ...find();
            clusterInDb.setKubeConfig(vo.getKubeConfig());
            clusterInDb.setVersion(vo.getVersion());
            dbf.update(clusterInDb);
        } else {
            dbf.persist(vo);  // 创建新 cluster
        }
    }
}
```

**创建方式**: **自动创建** - 通过同步流程自动创建，没有独立的 API

---

## 4. NativeHost 创建

### createHost() 抛出异常的原因

**文件**: `NativeFactory.java`

**行 24-26**:
```java
@Override
public HostVO createHost(HostVO vo, AddHostMessage msg) {
    throw new UnsupportedOperationException();
}
```

**原因**: NativeHost **不支持通过传统 AddHost API 创建**，只能通过同步流程自动创建。

### Host 实际创建位置 (行 497-575)
```java
private void syncNodesFromCluster(NativeClusterVO cluster, WhileCompletion completion) {
    // 从 K8s 获取 Nodes
    List<KubernetesNodeInventory> nodes = containerUtils
            .getNativeProvider(self.getVendor())
            .listNodes(self, NativeClusterInventory.valueOf(cluster));

    // 保存或更新 Host
    for (KubernetesNodeInventory node : nodes) {
        NativeHostVO host = containerUtils.toNativeHostVO(node);
        host.setEndpointUuid(self.getUuid());
        host.setZoneUuid(cluster.getZoneUuid());
        host.setClusterUuid(cluster.getUuid());

        NativeHostVO hostInDb = dbf.findByUuid(host.getUuid(), NativeHostVO.class);
        if (hostInDb != null) {
            // 更新已有 host
            hostInDb.setManagementIp(host.getManagementIp());
            hostInDb.setState(host.getState());
            dbf.update(hostInDb);
        } else {
            dbf.persist(host);  // 创建新 host
        }
    }
}
```

### DummyNativeHost 的使用

**文件**: `DummyNativeHost.java`

**行 17-43**:
```java
@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class DummyNativeHost implements Host {
    @Override
    public void handleMessage(Message msg) {
        if (msg instanceof PingHostMsg) {
            bus.reply(msg, new PingHostReply());  // 直接回复 Ping
        } else {
            bus.reply(msg, new MessageReply());   // 其他消息返回空回复
        }
    }
}
```

**作用**: 作为占位符处理 NativeHost 的消息，因为实际操作由 K8s 完成。

---

## 5. Pod 生命周期

### Pod 创建流程 (行 1173-1255)
```java
private void doSyncPodsFromNodes(List<NativeHostInventory> hosts, NoErrorCompletion completion) {
    new While<>(hosts).each((host, whileCompletion) -> {
        // 从 K8s 获取 Pods
        List<KubernetesPodInventory> pods = containerUtils.getNativeProvider(self.getVendor())
                .listPods(self, host);

        for (KubernetesPodInventory pod : pods) {
            PodVO podFromDB = Q.New(PodVO.class)
                    .eq(PodVO_.uuid, pod.getUuid())
                    .find();

            // 转换为 VmInstanceVO (继承关系)
            PodVO podFromKubernetes = containerUtils.podToVmInstanceVO(pod);

            if (podFromDB != null) {
                // 更新已有 Pod
                podFromDB.setHostUuid(host.getUuid());
                podFromDB.setCpuNum(podFromKubernetes.getCpuNum());
                podFromDB.setMemorySize(podFromKubernetes.getMemorySize());
                dbf.update(podFromDB);
            } else {
                // 创建新 Pod
                podFromKubernetes.setClusterId(cluster.getId());
                dbf.persistAndRefresh(podFromKubernetes);
            }
        }
    });
}
```

---

## 6. K8s Client 使用

### 常用 API 调用

| 方法 | 用途 |
|------|------|
| `provider.listClusters(self)` | 获取 K8s 集群列表 |
| `provider.listNodes(self, cluster)` | 获取 K8s Node 列表 |
| `provider.listPods(self, host)` | 获取 K8s Pod 列表 |
| `provider.listServices(...)` | 获取 K8s Service 列表 |
| `provider.listRepositories(...)` | 获取镜像仓库 |

### 错误处理 (行 511-516)
```java
try {
    nodes = containerUtils.getNativeProvider(self.getVendor())
            .listNodes(self, NativeClusterInventory.valueOf(cluster));
} catch (Exception e) {
    completion.addError(operr(...));
    logger.warn(String.format("Failed to list nodes: %s", e.getMessage()), e);
    completion.done();
    return;
}
```

---

## 7. 级联删除

### Endpoint 删除的级联处理 (行 172-191)
```java
private void deleteContainerResourceFromEndpoint(String endpointUuid) {
    // 1. 删除 Pod
    SQL.New("delete from PodVO pod where pod.hostUuid in " +
            "(select host.uuid from NativeHostVO host where host.endpointUuid = :endpointUuid)")
            .param("endpointUuid", endpointUuid)
            .execute();

    // 2. 删除 NativeHost
    SQL.New(NativeHostVO.class)
            .eq(NativeHostVO_.endpointUuid, endpointUuid)
            .hardDelete();

    // 3. 删除 NativeCluster
    SQL.New(NativeClusterVO.class)
            .eq(NativeClusterVO_.endpointUuid, endpointUuid)
            .hardDelete();

    // 4. 删除 Images, BackupStorage, Services
}
```

### 级联删除链

```
Endpoint 删除
  ├─ Pod 删除 (行 173)
  ├─ NativeHost 删除 (行 176)
  ├─ NativeCluster 删除 (行 179)
  └─ Images, BackupStorage, Services 删除 (行 182-190)
```

**是否向 K8s 发送删除请求**: **否** - 仅删除 ZStack 数据库记录，不操作 K8s 集群。

---

## 8. 容量管理

### PCI 设备状态追踪 (行 684-771)
```java
private void saveGpuDevicesFromPciDevices(...) {
    factory.createPciDevices(pciDevices.stream()
            .map(pciDeviceTO -> {
                PciDeviceVO pciDeviceVO = PciDeviceTO.getPciDeviceVO(...);
                pciDeviceVO.setVmInstanceUuid(podUuid);
                if (pciDeviceVO.getVmInstanceUuid() != null) {
                    pciDeviceVO.setStatus(PciDeviceStatus.Attached);
                } else {
                    pciDeviceVO.setStatus(PciDeviceStatus.System);
                }
                return pciDeviceVO;
            })
            .collect(Collectors.toList()));
}
```

---

## 9. 可抽象到 RoleAdapter 的操作

| Container 操作 | RoleAdapter 方法 |
|---------------|-----------------|
| Endpoint 创建 | `EndpointConnectable.connect()` |
| 资源同步 | `EndpointConnectable.syncResources()` |
| Host 容量读取 | `EndpointConnectable.getCapacity()` |
| 健康检查 | `EndpointConnectable.checkHealth()` |
| 获取 K8s Client | `EndpointConnectable.getNativeClient()` |

---

## 10. 核心设计特点

1. **被动同步架构**: 不创建 K8s 资源，只同步已有资源
2. **DummyNativeHost**: 作为占位符，实际管理在 K8s 侧
3. **NoRollbackFlow**: 所有 Flow 都是无回滚的
4. **级联删除**: 仅删除 ZStack 记录，不影响 K8s 集群
5. **容量追踪**: 基于 PCI 设备状态（Attached/System）

