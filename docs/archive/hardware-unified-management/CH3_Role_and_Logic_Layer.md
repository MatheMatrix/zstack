# 第三章：角色适配与接口设计 (Role Adapter Layer)

## 3.1 核心适配器接口 (RoleAdapter)

所有业务模块（KVM/裸金属/容器）必须实现此接口，将自身逻辑适配到物理层。

```java
package org.zstack.header.server;

public interface RoleAdapter {
    // 基础元数据
    ServerRoleType getRoleType();
    Class<?> getRoleVoClass();

    // 生命周期管理
    void createRole(PhysicalServerInventory server, RoleCreationContext context, ReturnValueCompletion<String> completion);
    void deleteRole(String roleUuid, NoErrorCompletion completion);

    // 数据同步（双向）
    void syncFromRole(String roleUuid, String serverUuid, Completion completion);
    void syncToRole(String serverUuid, String roleUuid, Completion completion);

    // 状态与校验
    RoleValidationResult validateForRole(PhysicalServerInventory server, RoleCreationContext context);

    // 容量消耗统计 (用于容量校准)
    ServerCapacityInfo getCapacityConsumption(String roleUuid);

    Object getRoleInventory(String roleUuid);

    // 生命周期钩子
    default void onRoleActivated(String roleUuid, String serverUuid) {}
    default void onRoleDeactivated(String roleUuid, String serverUuid) {}
    default void onRoleStateChanged(String roleUuid, String serverUuid, String oldState, String newState) {}
}
```

## 3.2 四大核心能力接口

为了避免接口膨胀，只保留以下 4 个通用能力接口。禁止添加特化接口。

### 1. PowerManageable (电源管理)
屏蔽 IPMI, Redfish, 虚拟电源的差异。
```java
public interface PowerManageable {
    void powerOn(String roleUuid, Completion completion);
    void powerOff(String roleUuid, Completion completion);
    void powerReset(String roleUuid, Completion completion);
    void getPowerStatus(String roleUuid, ReturnValueCompletion<String> completion);
}
```

### 2. HardwareDiscoverable (硬件发现)
负责采集详细硬件信息 (CPU/Mem/Disk/Nic)。
```java
public interface HardwareDiscoverable {
    void triggerDiscovery(String roleUuid, Completion completion);
    void handleHardwareInfoCallback(String identifier, ServerHardwareInfo info, Completion completion);
    void checkOobConnection(String oobAddress, int oobPort, String username, String password,
                            ReturnValueCompletion<ConnectionCheckResult> completion);
}
```

### 3. AgentDeployable (Agent 部署)
适用于 KVM 和 容器 等需要带内 Agent 的角色。
```java
public interface AgentDeployable {
    void deployAgent(String roleUuid, Completion completion);
    void reconnectAgent(String roleUuid, Completion completion);
    void checkAgentStatus(String roleUuid, ReturnValueCompletion<Boolean> completion);
}
```

### 4. ClusterBindable (集群绑定)
处理物理机进入逻辑集群的校验与绑定。
```java
public interface ClusterBindable {
    RoleValidationResult validateClusterCompatibility(String serverUuid, String clusterUuid);
    void bindToCluster(String roleUuid, String clusterUuid, Completion completion);
    void unbindFromCluster(String roleUuid, Completion completion);
}
```

## 3.3 典型适配器实现示例

### 5.6 各角色适配器实现

| 适配器 | 核心接口 | 能力接口 |
|--------|---------|---------|
| BaremetalRoleAdapter | RoleAdapter | PowerManageable, HardwareDiscoverable |
| Baremetal2RoleAdapter | RoleAdapter | PowerManageable, HardwareDiscoverable |
| KvmHostRoleAdapter | RoleAdapter | AgentDeployable, ClusterBindable |
| ContainerHostRoleAdapter | RoleAdapter | ClusterBindable |

### ContainerHostRoleAdapter (实现示例)
```java
// Container 的绑定不支持手动操作，必须通过 K8s 同步
class ContainerHostRoleAdapter implements RoleAdapter, ClusterBindable {
    @Override
    public void bindToCluster(String roleUuid, String clusterUuid, Completion completion) {
        throw new OperationFailureException(
            operr("Container host cluster binding is determined by K8s, manual binding not supported")
        );
    }

    @Override
    public void createRole(PhysicalServerInventory server, RoleCreationContext context,
                           ReturnValueCompletion<String> completion) {
        throw new OperationFailureException(
            operr("NativeHost can only be created through K8s sync, use SyncContainerEndpointMsg instead")
        );
    }
}
```
