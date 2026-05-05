# 第五章：监控与生命周期 (Lifecycle & Monitoring)

## 5.1 资源上线流程 (Discovery-First)

采用“先发现，后绑定”的原则。硬件作为纯粹的资源先入库，再根据需要赋予角色。

### 12.1 流程图

```
User                API / ServerManager            Hardware / RoleAdapter
  │                         │                               │
  │ APIAddPhysicalServerMsg │                               │
  ├────────────────────────►│                               │
  │ (OOB IP, Credentials)   │ 1. checkOobConnection()       │
  │                         ├──────────────────────────────►│
  │                         │ 2. triggerDiscovery()         │
  │                         ├──────────────────────────────►│
  │                         │◄──────────────────────────────┤
  │                         │ 3. Create PhysicalServerVO    │
  │                         │    (SN, CPU, Mem, Model...)   │
  │◄────────────────────────┤                               │
  │                         │                               │
  │ APIBindPhysicalServerRoleMsg                            │
  ├────────────────────────►│                               │
  │ (RoleType, Config)      │ 4. validateForRole()          │
  │                         ├──────────────────────────────►│
  │                         │ 5. createRole()               │
  │                         ├──────────────────────────────►│
  │                         │    (Create KVMHostVO etc.)    │
  │                         │ 6. create PhysicalServerRoleVO│
  │◄────────────────────────┤                               │
```

### 12.2 关键消息设计

```java
// APIAddPhysicalServerMsg: 仅通过 OOB 添加硬件资源
@APIMessage
public class APIAddPhysicalServerMsg extends APICreateMessage {
    @APIParam private String zoneUuid;
    @APIParam(required = false) private String poolUuid;
    @APIParam private String oobAddress;
    @APIParam private String oobUsername;
    @APIParam private String oobPassword;
    @APIParam private OobManagementType oobManagementType;
}

// APIBindPhysicalServerRoleMsg: 为现有硬件绑定业务角色
@APIMessage
public class APIBindPhysicalServerRoleMsg extends APIMessage {
    @APIParam(resourceType = PhysicalServerVO.class)
    private String serverUuid;
    @APIParam
    private String roleType; // KVM_HOST, BARE_METAL, NATIVE_HOST
    @APIParam(required = false)
    private Map<String, String> roleConfig; // 角色特有配置，如 KVM 的 managementIp
}
```

## 5.2 双轨监控架构

为了解决单一监控维度的盲区，采用双轨并行监控。

| 轨道 | 监控源 | 负责对象 | 状态同步 |
|-----|--------|---------|---------|
| **带外 (OOB)** | BMC / Redfish | 物理硬件健康 (电源/风扇/温度) | 轮询 -> 更新 `PhysicalServerVO.status` |
| **带内 (IB)** | Agent (KVM/OS) | 业务连通性 (Libvirt/Docker) | 心跳 -> 更新 `PhysicalServerRoleVO.status` |

### 状态合成逻辑
`PhysicalServerVO.status` 是最终的物理可用性状态：
*   如果 **OOB** 断连 -> `Disconnected` (物理失联)
*   如果 **OOB** 正常 但 **关键角色IB** 断连 -> `Disconnected` (业务不可用)
*   只有 **OOB** 和 **关键角色IB** 都正常 -> `Connected`

## 5.3 状态机定义

### ServerState (管理状态)
*   `Enabled`: 允许分配。
*   `Disabled`: 禁止分配新资源，存量资源保持运行。
*   `Maintenance`: 维护模式，触发存量资源迁移。

### ServerStatus (运行状态)
*   `Connecting`: 正在建立连接或部署 Agent。
*   `Connected`: 通讯正常。
*   `Disconnected`: 通讯中断。

### 状态同步代码示例
```java
public class ServerStatusManager {
    // 处理带外监控回调
    public void onOobStatusChanged(String serverUuid, String oobStatus) {
        // 如果 OOB 断开，PhysicalServer 设为 Disconnected
        // 并通知所有关联角色进入 Unknown/Disconnected 状态
    }

    // 处理带内角色心跳回调 (通过 ExtensionPoint)
    public void onRoleStatusChanged(String roleUuid, String oldStatus, String newStatus) {
        // 通过 PhysicalServerRoleVO 找到 serverUuid
        // 综合评估是否需要更新 PhysicalServerVO.status
    }
}
```
