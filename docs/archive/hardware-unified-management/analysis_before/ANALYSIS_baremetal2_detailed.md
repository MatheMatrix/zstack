# Baremetal2 模块深度代码分析

## 1. 创建 Chassis FlowChain（三层继承完整链路）

### 文件：`BareMetal2ChassisManagerImpl.java`

#### API 消息入口（行 321-339）
```java
private void handle(APIAddBareMetal2ChassisMsg msg) {
    // 通过 Factory 模式转换为内部消息
    BareMetal2ChassisFactory factory = getBareMetal2ChassisFactory(BareMetal2ChassisType.valueOf(msg.getChassisType()));
    AddBareMetal2ChassisMsg amsg = factory.buildAddBareMetal2ChassisMsg(msg);
    bus.makeTargetServiceIdByResourceUuid(amsg, BareMetal2ChassisConstant.SERVICE_ID, amsg.getClusterUuid());
}
```

#### 队列化处理（行 358-387）
```java
private void addBareMetal2ChassisInQueue(...) {
    thdf.chainSubmit(new ChainTask() {
        public String getSyncSignature() {
            return "add-baremetal2-chassis";  // 全局锁保证串行执行
        }

        public void run(SyncTaskChain chain) {
            // 步骤 1: License 检查（行 368）
            ErrorCode err = checkBareMetal2License(msg);

            // 步骤 2: IPMI 凭证验证（行 370-372）
            if (msg instanceof AddBareMetal2IpmiChassisMsg) {
                err = chassisValidator.validate((AddBareMetal2IpmiChassisMsg) msg);
            }

            // 步骤 3: 执行创建
            addBareMetal2Chassis(msg, completion);
            chain.next();
        }
    });
}
```

### 核心创建流程（行 389-442）

#### 步骤 1: 验证集群（行 392-400）
```java
ClusterVO cluster = Q.New(ClusterVO.class)
    .eq(ClusterVO_.uuid, msg.getClusterUuid())
    .eq(ClusterVO_.state, ClusterState.Enabled)
    .eq(ClusterVO_.type, BareMetal2ClusterConstant.BM2_CLUSTER_TYPE)
    .find();
```

#### 步骤 2: 创建基础 VO（行 402-415）
```java
final BareMetal2ChassisVO chassis = new BareMetal2ChassisVO();
chassis.setUuid(Platform.getUuid());
chassis.setState(BareMetal2ChassisState.Enabled);
chassis.setStatus(BareMetal2ChassisStatus.HardwareInfoUnknown);  // 初始状态
chassis.setPowerStatus(BareMetal2ChassisPowerStatus.POWER_UNKNOWN);
```

#### 步骤 3: 调用 Factory 创建特定类型（行 417-418）

**文件**: `BareMetal2IpmiChassisFactory.java`

**行 53-65**: **三层继承实现**
```java
public BareMetal2ChassisInventory createBareMetal2Chassis(BareMetal2ChassisVO vo, AddBareMetal2ChassisMsg msg) {
    AddBareMetal2IpmiChassisMsg amsg = (AddBareMetal2IpmiChassisMsg) msg;

    // 【三层继承】：ResourceVO -> BareMetal2ChassisVO -> BareMetal2IpmiChassisVO
    BareMetal2IpmiChassisVO ivo = new BareMetal2IpmiChassisVO(vo);

    // IPMI 特有字段
    ivo.setIpmiAddress(amsg.getIpmiAddress());
    ivo.setIpmiPort(amsg.getIpmiPort());
    ivo.setIpmiUsername(amsg.getIpmiUsername());
    ivo.setIpmiPassword(amsg.getIpmiPassword());
    ivo.setType(BareMetal2ChassisConstant.IPMI_CHASSIS_TYPE);

    ivo = dbf.persistAndRefresh(ivo);
    return ivo.toInventory();
}
```

#### 步骤 4: 三种分支路径（行 422-441）
```java
if (msg.getReboot()) {
    // 【路径 1】：自动硬件发现（需重启）
    InspectBareMetal2ChassisMsg imsg = new InspectBareMetal2ChassisMsg();
    bus.send(imsg, ...);

} else if (BareMetal2Utils.isNonReboot(...)) {
    // 【路径 2】：非重启模式（静态 IP 直接部署）
    createBareMetal2InstanceNonReboot(chassis, msg, completion);

} else {
    // 【路径 3】：仅创建 Chassis
    completion.success(inventory);
}
```

---

## 2. 硬件发现 FlowChain

### 硬件信息同步器核心逻辑

**文件**: `BareMetal2ChassisHardwareInfoSyncer.java`

#### 核心同步流程（行 84-312）

**步骤 1**: 解析和验证（行 86-92）
```java
BareMetal2ChassisHardwareInfo info = JSONObjectUtil.toObject(hardwareInfo, ...);
```

**步骤 2**: 架构和启动模式验证（行 94-116）
```java
// 验证 CPU 架构必须与集群匹配
if (!info.architecture.equals(clusterArchitecture)) {
    chassis.setStatus(BareMetal2ChassisStatus.WrongArchitecture);
    completion.fail(operr(...));
    return;
}
```

**步骤 3**: ChassisOffering 自动创建/匹配（行 141-159）
```java
if (offeringUuid == null) {
    // 自动创建 Offering
    BareMetal2ChassisOfferingVO offer = new BareMetal2ChassisOfferingVO();
    offer.setName(info.cpuModelName);
    offer.setArchitecture(info.architecture);
    offer.setCpuModelName(info.cpuModelName);
    offer.setCpuNum(info.cpuNum);
    offer.setMemorySize(info.memorySize);
    offer = dbf.persistAndRefresh(offer);
    offeringUuid = offer.getUuid();
}
```

**步骤 4**: 更新 Chassis 状态（行 161-171）
```java
chassis.setChassisOfferingUuid(offeringUuid);
if (hasInstance) {
    chassis.setStatus(BareMetal2ChassisStatus.Allocated);
} else {
    chassis.setStatus(BareMetal2ChassisStatus.Available);
}
```

### 细粒度硬件 VO 创建流程

#### NIC 同步（行 173-231）
- **MAC 地址冲突检查**
- **差异化同步**: Add/Update/Delete 三种操作
- **Provision NIC 标记**: `isProvisionNic` 和 `isPrimaryProvisionNic`

#### Disk 同步（行 233-245）
- 策略: 先删除后重建

#### PCI/GPU 设备同步（行 247-372）
- GPU 通过 `serialNumber + PCI 信息` 双重判断

---

## 3. Instance 部署 FlowChain

### FlowChain 构建（行 572-579）
```java
createBareMetal2InstanceFlowBuilder = FlowChainBuilder.newBuilder()
    .setFlowClassNames(createBareMetal2InstanceWorkFlowElements)
    .construct();
```

### 关键 Flow 详解

#### Flow 1: BareMetal2InstanceAllocateChassisFlow
- **输入**: `BareMetal2InstanceSpec`
- **输出**: `spec.setDestChassis(rly.getChassis())`
- **Rollback**: 根据操作类型决定是否释放 Chassis

#### Flow 2: BareMetal2InstanceAllocateGatewayFlow
- **输入**: `BareMetal2InstanceSpec`
- **输出**: `spec.setDestHost(rly.getGateway())`
- **无 Rollback**（NoRollbackFlow）

#### Flow 3: BareMetal2InstanceCreateProvisionConfigurationsFlow
- **并发创建**: 向所有 Gateway 发送 `CreateProvisionConfigurationInGatewayMsg`
- **Rollback**: 删除所有已创建的配置

---

## 4. Gateway 分配策略

### 策略工厂模式

**接口**: `BareMetal2GatewayAllocatorStrategyFactory`

### 策略实现

| 策略 | 说明 |
|-----|------|
| `DefaultGatewayAllocatorStrategy` | 默认排序逻辑 |
| `LeastBmPreferredGatewayAllocatorStrategy` | 优先选择负载最少的 Gateway |
| `LastGatewayPreferredAllocatorStrategy` | 优先选择上次使用的 Gateway |

### Gateway 与 Cluster 的 N:N 关联

**关联表**: `BareMetal2GatewayClusterRefVO`
- 一个 Gateway 可关联多个 Cluster
- 一个 Cluster 可关联多个 Gateway

---

## 5. 三元状态机

### Chassis 的三元状态

| 状态类型 | 枚举 | 初始值 |
|---------|------|-------|
| State | `BareMetal2ChassisState` | `Enabled` |
| Status | `BareMetal2ChassisStatus` | `HardwareInfoUnknown` |
| PowerStatus | `BareMetal2ChassisPowerStatus` | `POWER_UNKNOWN` |

### 自动释放孤儿 Chassis（行 658-682）
```java
@Transactional
private void releaseBareMetal2Chassis() {
    // 1. 查找所有 Allocated 状态的 Chassis
    List<String> allocated = Q.New(BareMetal2ChassisVO.class)
        .eq(BareMetal2ChassisVO_.status, BareMetal2ChassisStatus.Allocated)
        .select(BareMetal2ChassisVO_.uuid)
        .listValues();

    // 2. 查找真正被 Instance 占用的 Chassis
    List<String> reallyAllocated = Q.New(BareMetal2InstanceVO.class)
        .notNull(BareMetal2InstanceVO_.chassisUuid)
        .select(BareMetal2InstanceVO_.chassisUuid)
        .listValues();

    // 3. 释放孤儿 Chassis
    allocated.removeAll(reallyAllocated);
    releaseBareMetal2Chassis(allocated);
}
```

**触发时机**: 管理节点启动时自动执行

---

## 6. Factory 模式实现

### Factory 注册机制（行 638-657）
```java
private void populateExtensions() {
    for (BareMetal2ChassisFactory factory : pluginRegistry.getExtensionList(BareMetal2ChassisFactory.class)) {
        chassisFactories.put(factory.getChassisType().toString(), factory);
    }
}
```

### Factory 使用（行 118-124）
```java
public BareMetal2ChassisFactory getBareMetal2ChassisFactory(BareMetal2ChassisType type) {
    BareMetal2ChassisFactory factory = chassisFactories.get(type.toString());
    return factory;
}
```

---

## 7. 与 v1 的核心差异

| 维度 | Baremetal v1 | Baremetal2 |
|-----|-------------|------------|
| **架构模式** | 单一实现 | Factory + Extension Point |
| **协议支持** | 仅 IPMI | IPMI/Redfish/SSH (可扩展) |
| **PXE 服务器** | PxeServer (1:N Cluster) | Gateway (N:N Cluster) |
| **分配策略** | 简单轮询 | 多策略 |
| **状态管理** | 二元 | 三元 |
| **硬件信息** | JSON 粗粒度 | 细粒度 VO |
| **ChassisOffering** | 无 | 自动创建和匹配 |
| **部署模式** | PXE Only | PXE + Static IP |

