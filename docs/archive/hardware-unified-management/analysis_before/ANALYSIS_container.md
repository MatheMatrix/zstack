# Container 模块分析报告

## 1. VO 结构

### 1.1 NativeHostVO

**继承关系**：
```
ResourceVO (base)
  └─ HostVO (standard host)
      └─ NativeHostVO (container host)
```

**特有字段**：
| 字段名 | 类型 | 说明 |
|--------|------|------|
| endpointUuid | String | 关联到 ContainerManagementEndpointVO（CASCADE） |

**特点**：
- 极简设计，只添加了 Endpoint 关联字段
- 完全复用 HostVO 的所有功能
- 不存储 SSH 凭证（通过 Endpoint 连接）

### 1.2 NativeClusterVO

**继承关系**：
```
ResourceVO (base)
  └─ ClusterVO (standard cluster)
      └─ NativeClusterVO (container cluster)
```

**特有字段**：
| 字段名 | 类型 | 说明 |
|--------|------|------|
| endpointUuid | String | 关联到容器管理端点 |
| id | long | 集群在容器系统中的内部 ID |
| masterUrl | String | K8s Master API URL |
| kubeConfig | String | Kubernetes 配置文件（65535 字符） |
| prometheusURL | String | Prometheus 监控 URL |
| version | String | K8s 版本号 |
| nodeCount | int | 节点数量 |
| status | ClusterStatusType | 集群状态 |

## 2. Endpoint 机制

### 2.1 ContainerManagementEndpointVO

**作用**：容器编排引擎的连接管理点（K8s API Server / Docker Daemon）

**字段**：
| 字段名 | 类型 | 说明 |
|--------|------|------|
| name | String | 端点名称 |
| managementIp | String | 管理 IP 地址 |
| managementPort | Integer | 管理端口 |
| vendor | String | 厂商标识（kubernetes/docker） |
| accessKeyId | String | 访问凭证 ID |
| accessKeySecret | String | 访问凭证密钥 |

### 2.2 关联模式

```
ContainerManagementEndpointVO (1)
  ├─ NativeClusterVO (N) - 一个 Endpoint 可管理多个 K8s 集群
  └─ NativeHostVO (N) - 一个 Endpoint 可管理多个容器 Host
```

**设计优势**：
- 集中凭证管理
- 支持一个容器平台管理多个集群/节点
- 便于切换或升级容器编排引擎

## 3. 生命周期管理

### 3.1 NativeFactory

**关键发现**：
- `createHost()` 方法抛出 `UnsupportedOperationException`
- NativeHost **不支持通过标准 AddHostMessage 添加**
- 创建流程由 Container 模块的专用 API 处理

### 3.2 推测的生命周期流程

```
1. 创建 ContainerManagementEndpointVO（连接容器编排引擎）
2. 通过专用 API 从 Endpoint 同步/导入 Host
3. 系统自动创建 NativeHostVO 并关联 Endpoint
4. 定期同步状态和容量
```

## 4. Pod 与容量管理

### 4.1 PodVO 结构

**继承关系**：
```
ResourceVO (base)
  └─ VmInstanceVO (虚拟机实例)
      └─ PodVO (容器 Pod)
```

**Pod 特有字段**：
| 字段名 | 类型 | 说明 |
|--------|------|------|
| status | PodStatusPhase | Pod 状态（Running/Pending/Failed） |
| clusterId | Long | 关联的容器集群 ID |
| namespace | String | K8s 命名空间 |

**复用 VmInstanceVO 字段**：
- cpuNum、memorySize
- hostUuid、state、imageUuid

### 4.2 容量统计方式

- Pod 作为 VmInstanceVO 的子类，直接参与 Host 容量计算
- 通过 HostCapacityVO 统计 totalCpu/availableCpu、totalMemory/availableMemory
- Pod 和 VM 在容量管理层面是等价的

## 5. 核心操作

### 5.1 Endpoint 级别

- 创建 Endpoint：配置容器编排引擎连接
- 测试连接：验证凭证和网络可达性
- 同步集群/节点：从 Endpoint 导入

### 5.2 Host 级别

- 连接/断开：通过 Endpoint 管理
- 状态同步：从容器编排引擎同步
- 容量查询

### 5.3 Pod 级别

- Pod 调度：基于 Host 容量分配
- 生命周期管理：创建/启动/停止/删除
- 状态同步：从 K8s 同步

## 6. 对 RoleAdapter 接口的建议

### 6.1 Endpoint 关联处理

```java
// RoleAdapter 需支持 Endpoint 关联
String getEndpointUuid();
void setEndpointUuid(String endpointUuid);

// 获取 Endpoint 配置
ContainerManagementEndpointVO getEndpoint();
```

### 6.2 Pod 容量统计

```java
// Pod 资源统计
List<VmInstanceVO> getRunningPods();
void updateCapacityForPod(PodVO pod, CapacityOperation operation);
```

**注意**：Pod 通过 VmInstanceVO 继承，已自动计入容量，无需特殊处理

### 6.3 建议的 RoleAdapter 方法

```java
public interface ContainerHostRoleAdapter extends RoleAdapter {
    // Endpoint 管理
    String getEndpointUuid();
    void setEndpointUuid(String endpointUuid);

    // 连接管理（通过 Endpoint）
    boolean testConnection();
    void reconnect();

    // 容器特定操作
    List<PodVO> getRunningPods();
    void syncPodCapacity();

    // 状态同步
    void syncFromKubernetes();
}
```

### 6.4 数据同步注意事项

1. **Endpoint UUID 同步**：必须双向同步
2. **容量计算复用**：Pod 已通过 VmInstanceVO 计入
3. **状态映射**：K8s NotReady → ZStack Disconnected
4. **级联删除**：Endpoint 删除时处理 UnifiedHardwareVO

## 7. 总结

Container 模块的核心特点：
- **极简 Host 模型**：只添加 endpointUuid
- **集中式连接管理**：通过 Endpoint 统一管理
- **VM 语义复用**：Pod 作为 VmInstanceVO 子类
- **多层次资源**：Endpoint → Cluster → Host → Pod
