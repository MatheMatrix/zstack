# KVM 模块分析报告

## 1. VO 结构

### 1.1 继承关系

```
ResourceVO (基类)
    ↓
HostAO (抽象映射超类 @MappedSuperclass)
    ↓
HostVO (实体类)
    ↓
KVMHostVO (KVM 特化实体类)
```

### 1.2 字段对比

| 字段名 | 来源 | 类型 | 说明 |
|--------|------|------|------|
| uuid | ResourceVO | String | 主键 |
| zoneUuid | HostAO | String | 所属区域 |
| clusterUuid | HostAO | String | 所属集群（必填） |
| name | HostAO | String | 主机名称 |
| managementIp | HostAO | String | 管理 IP 地址 |
| hypervisorType | HostAO | String | 虚拟化类型（KVM） |
| architecture | HostAO | String | CPU 架构 |
| state | HostAO | HostState | 业务状态 |
| status | HostAO | HostStatus | 连接状态 |
| **username** | KVMHostVO | String | SSH 用户名 |
| **password** | KVMHostVO | String | SSH 密码（加密） |
| **port** | KVMHostVO | Integer | SSH 端口 |
| **osDistribution** | KVMHostVO | String | 操作系统发行版 |
| **osRelease** | KVMHostVO | String | 操作系统版本号 |

### 1.3 关联的容量表（HostCapacityVO）

| 字段名 | 类型 | 说明 |
|--------|------|------|
| uuid | String | 主键，关联 HostVO.uuid |
| totalMemory | long | 总内存（字节） |
| availableMemory | long | 可用内存 |
| totalCpu | long | 总 CPU 容量 |
| availableCpu | long | 可用 CPU |
| cpuNum | int | CPU 逻辑核心数 |
| cpuSockets | int | CPU 插槽数 |

## 2. 与 Cluster 的强制关系

### 2.1 数据库约束

```java
@ForeignKey(parentEntityClass = ClusterEO.class, onDeleteAction = ReferenceOption.RESTRICT)
private String clusterUuid;
```

- **强制外键约束**：KVM Host 必须属于一个 Cluster
- **删除保护**：Cluster 存在 Host 时无法删除
- **hypervisorType 约束**：Cluster 必须是 KVM 类型

### 2.2 OS 版本一致性

同一 Cluster 内所有 Host 的 OS 版本必须一致（在 KVMHostFactory.checkNewAddedHost() 中校验）

## 3. 生命周期

### 3.1 添加 Host 流程

```
API请求 (AddKVMHostMsg)
    ↓
KVMHostFactory.createHost()
    ↓
创建 KVMHostVO（保存SSH凭证）
    ↓
自动触发 ConnectHost
    ↓
部署 KVM Agent（通过Ansible）
    ↓
收集硬件信息
    ↓
创建 HostCapacityVO
    ↓
状态变为 Connected
```

### 3.2 状态机

**HostState（业务状态）**：
- `Enabled` - 可用
- `Disabled` - 禁用
- `PreMaintenance` - 准备维护（迁移 VM）
- `Maintenance` - 维护模式

**HostStatus（连接状态）**：
- `Connecting` - 正在连接
- `Connected` - 已连接
- `Disconnected` - 已断开

## 4. Agent 通信机制

### 4.1 Agent 部署方式

- 使用 Ansible 部署 zstack-kvmagent
- 触发时机：AddHost 或 ConnectHost
- 检测变更后自动重新部署

### 4.2 通信方式

- **协议**：HTTP/HTTPS + JSON
- **端口**：7070（默认）
- **命令**：StartVmCmd、StopVmCmd、ConnectCmd 等

### 4.3 心跳检测

- TCP 长连接 + Ping 机制
- 心跳周期可配置
- 5秒无响应触发 PingHostMsg

## 5. 容量管理

### 5.1 容量字段

**物理容量**：
- `totalPhysicalMemory`：物理内存总量
- `cpuNum`：逻辑核心数

**虚拟化容量**：
- `totalMemory` = totalPhysicalMemory - 预留内存
- `availableMemory`：剩余可分配

### 5.2 容量更新时机

1. Connect 时初次采集
2. VM 启动扣减
3. VM 停止回收
4. VM 迁移同步
5. 定期 ping 刷新

### 5.3 容量预留

```
可分配内存 = 物理内存 - 预留内存 - 操作系统占用
```

## 6. 硬件信息采集

### 6.1 连接时采集

| 信息类别 | 存储位置 |
|----------|----------|
| 操作系统 | KVMHostVO (osDistribution, osRelease) |
| CPU | HostCapacityVO + SystemTag |
| 内存 | HostCapacityVO |
| 网络 | SystemTag |

### 6.2 定期更新

通过 KvmVmSyncPingTask 定期刷新 VM 状态和容量

## 7. 对 RoleAdapter 接口的建议

### 7.1 接口设计

```java
public interface KVMRoleAdapter extends HardwareRoleAdapter {
    // SSH 连接信息处理
    void syncSshCredentials(String hardwareUuid, String username,
        String password, Integer port);

    // Agent 部署触发
    void triggerAgentDeployment(String hardwareUuid);

    // 容量同步（双向）
    void syncCapacityToHost(String hardwareUuid, HardwareCapacityInfo hwCapacity);
    void syncCapacityFromHost(String hardwareUuid, HostCapacityVO hostCapacity);

    // Cluster 关联处理
    void associateWithCluster(String hardwareUuid, String clusterUuid);
    void validateClusterCompatibility(String hardwareUuid, String clusterUuid);

    // OS 信息同步
    void syncOsInfo(String hardwareUuid, String distribution,
        String release, String version);

    // 状态同步
    void syncHostState(String hardwareUuid, HostState state, HostStatus status);
}
```

### 7.2 关键同步点

1. **SSH 凭证**：必须加密存储（@EncryptColumn）
2. **Agent 部署**：通过 AnsibleFacade 调用
3. **容量同步**：VM 操作时触发
4. **Cluster 关联**：校验 hypervisorType 和 OS 版本

### 7.3 与裸金属的差异

| 特性 | KVM Host | 裸金属 Chassis |
|------|----------|----------------|
| Cluster 关系 | 强制关联 | 可选关联 |
| 管理方式 | SSH + Agent (in-band) | IPMI (out-of-band) |
| 部署方式 | Ansible | PXE |
| 容量管理 | 虚拟化容量（需预留） | 物理容量 |
| OS 要求 | 同 Cluster 内一致 | 无限制 |
