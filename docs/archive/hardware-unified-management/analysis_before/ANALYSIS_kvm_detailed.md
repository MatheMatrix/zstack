# KVM 模块深度代码分析

## 1. AddHost FlowChain 完整流程

### 文件：`HostManagerImpl.java`

### FlowChain 构建 (行 419-562)
```java
FlowChain chain = FlowChainBuilder.newSimpleFlowChain();
chain.setName(String.format("add-host-%s", vo.getUuid()));
```

### Flow 执行顺序

#### Flow 1: `call-before-add-host-extension` (行 422-449)
```java
String __name__ = "call-before-add-host-extension";
ext.beforeAddHost(inv, new Completion(trigger) {...});
```

#### Flow 2: `send-connect-host-message` (行 451-470)
```java
String __name__ = "send-connect-host-message";
ConnectHostMsg connectMsg = new ConnectHostMsg(vo.getUuid());
connectMsg.setNewAdd(true);
connectMsg.setStartPingTaskOnFailure(false);
bus.makeTargetServiceIdByResourceUuid(connectMsg, HostConstant.SERVICE_ID, hvo.getUuid());
```

#### Flow 3: `check-cluster-architecture` (行 471-506)
```java
HostVO h = dbf.findByUuid(vo.getUuid(), HostVO.class);
String arch = h.getArchitecture();

ClusterVO cluster = dbf.findByUuid(msg.getClusterUuid(), ClusterVO.class);
if (cluster.getArchitecture() == null) {
    cluster.setArchitecture(arch);  // 首个 Host 设置 Cluster 架构
    dbf.update(cluster);
}

if (!arch.equals(cluster.getArchitecture())) {
    trigger.fail(operr(...));  // 架构不匹配时失败
}
```

#### Flow 4: `check-host-os-version` (行 507-518)
```java
String __name__ = "check-host-os-version";
final ErrorCode errorCode = factory.checkNewAddedHost(vo);
```

#### Flow 5: `call-after-add-host-extension` (行 519-535)
```java
String __name__ = "call-after-add-host-extension";
extEmitter.afterAddHost(inv, new Completion(trigger) {...});
```

---

## 2. Cluster 兼容性验证

### 文件：`KVMHostFactory.java`

### OS 版本一致性检查 (行 326-357)
```java
@Override
public ErrorCode checkNewAddedHost(HostVO vo) {
    // 1. 获取并验证当前 Host 的 OS
    final HostOperationSystem os = getHostOS(vo.getUuid());
    if (!os.isValid()) {
        return operr("the operation system[%s] of host is invalid", os);
    }

    // 2. 查询同 Cluster 中的其他 Host
    String otherHostUuid = Q.New(HostVO.class)
        .select(HostVO_.uuid)
        .eq(HostVO_.clusterUuid, vo.getClusterUuid())
        .notEq(HostVO_.uuid, vo.getUuid())
        .notEq(HostVO_.status, HostStatus.Connecting)
        .limit(1)
        .findValue();

    if (otherHostUuid == null) {
        return null;  // 首个 Host 无需检查
    }

    // 3. 获取已存在 Host 的 OS
    final HostOperationSystem otherOs = getHostOS(otherHostUuid);

    // 4. OS 版本一致性校验
    if (!os.equals(otherOs)) {
        return operr("cluster already has host with os version[%s], " +
            "but new added host has different os version[%s]",
            otherOs, os);
    }

    return null;
}
```

### HostOperationSystem 数据获取 (行 300-307)
```java
@Override
public HostOperationSystem getHostOS(String uuid) {
    Tuple tuple = Q.New(KVMHostVO.class)
        .select(KVMHostVO_.osDistribution, KVMHostVO_.osRelease, KVMHostVO_.osVersion)
        .eq(KVMHostVO_.uuid, uuid)
        .findTuple();
    return HostOperationSystem.of(
        tuple.get(0, String.class),  // osDistribution
        tuple.get(1, String.class),  // osRelease
        tuple.get(2, String.class)   // osVersion
    );
}
```

---

## 3. Ansible Agent 部署

### Ansible 模块部署 (行 379-385)
```java
private void deployAnsibleModule() {
    if (CoreGlobalProperty.UNIT_TEST_ON) {
        return;
    }
    asf.deployModule(KVMConstant.ANSIBLE_MODULE_PATH, KVMConstant.ANSIBLE_PLAYBOOK_NAME);
}
```

### 管理节点 Agent 变更检测 (行 1046-1087)
```java
@Override
@AsyncThread
public void managementNodeReady() {
    // 检测 Ansible 模块是否变更
    if (!asf.isModuleChanged(KVMConstant.ANSIBLE_PLAYBOOK_NAME)) {
        return;
    }

    // 获取本节点管理的所有 Host
    List<String> hostUuids = getHostManagedByUs();

    // 构建 ConnectHostMsg 列表
    List<ConnectHostMsg> msgs = new ArrayList<>();
    for (String huuid : hostUuids) {
        ConnectHostMsg msg = new ConnectHostMsg();
        msg.setNewAdd(false);  // 不是新增，是重连
        msg.setUuid(huuid);
        msgs.add(msg);
    }

    // 并行发送消息重连 Host
    bus.send(msgs, HostGlobalConfig.HOST_LOAD_PARALLELISM_DEGREE.value(Integer.class), ...);
}
```

---

## 4. 心跳机制实现

### TCP Server 启动 (行 902-929)
```java
@AsyncThread
private void startTcpServer() throws IOException {
    try (Selector selector = Selector.open();
         ServerSocketChannel serverSocket = ServerSocketChannel.open()) {

        serverSocket.bind(new InetSocketAddress("0.0.0.0", KVMGlobalProperty.TCP_SERVER_PORT));
        serverSocket.configureBlocking(false);
        serverSocket.register(selector, SelectionKey.OP_ACCEPT);
        ByteBuffer buffer = ByteBuffer.allocate(256);

        // NIO 事件循环
        Integer interval = KVMGlobalConfig.CONNECTION_SERVER_UPDATE_INTERVAL.value(Integer.class);
        while (KVMGlobalConfig.ENABLE_HOST_TCP_CONNECTION_CHECK.value(Boolean.class)) {
            selector.select(interval);
            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> iter = selectedKeys.iterator();
            while (iter.hasNext()) {
                SelectionKey key = iter.next();

                if (key.isAcceptable()) {
                    register(selector, serverSocket);  // 接受新连接
                }

                if (key.isReadable()) {
                    tryToRead(buffer, key);  // 读取心跳数据
                }

                iter.remove();
            }
        }
    }
}
```

### 超时检测机制 (行 858-898)
```java
// 周期性检查超时的 Socket
checkSocketChannelTimeoutThread = thdf.submitPeriodicTask(new PeriodicTask() {
    @Override
    public long getInterval() {
        return KVMGlobalConfig.HOST_CONNECTION_CHECK_INTERVAL.value(Long.class);
    }

    @Override
    public void run() {
        long currentTime = timeHelper.getCurrentTimeMillis();
        socketTimeoutMap.entrySet().stream()
            .filter(entry -> isTimeout(entry.getValue(), currentTime))
            .forEach(entry -> checkHostConnection(entry.getKey()));

        socketTimeoutMap.entrySet().removeIf(entry ->
            isTimeout(entry.getValue(), currentTime));
    }

    // 超时阈值 5 秒
    private boolean isTimeout(Long timeInMap, long currentTime) {
        return timeInMap + 5000 < currentTime;
    }
});
```

### 连接断开后触发 Ping (行 945-965)
```java
private void checkHostConnection(SocketChannel client) throws IOException {
    SocketAddress remoteAddress = client.getRemoteAddress();
    client.close();
    socketTimeoutMap.remove(client);

    // 通过 IP 查找 Host UUID
    String managementIp = remoteAddress.toString().split("/")[1].split(":")[0];
    String hostUuid = Q.New(HostVO.class)
        .select(HostVO_.uuid)
        .eq(HostVO_.managementIp, managementIp)
        .findValue();

    // 发送 PingHostMsg 主动检测
    PingHostMsg pingHostMsg = new PingHostMsg();
    pingHostMsg.setHostUuid(hostUuid);
    bus.send(pingHostMsg);
}
```

---

## 5. VM 事件处理

### VM 关机事件 (行 579-596)
```java
restf.registerSyncHttpCallHandler(KVMConstant.KVM_REPORT_VM_SHUTDOWN_EVENT,
    ReportVmShutdownEventCmd.class, cmd -> {
    // 发送 VM 关机事件消息
    KvmReportVmShutdownEventMsg msg = new KvmReportVmShutdownEventMsg();
    msg.setVmInstanceUuid(cmd.vmUuid);
    bus.send(msg);
    return null;
});
```

### VM 启动事件 (行 606-609)
```java
restf.registerSyncHttpCallHandler(KVMConstant.KVM_REPORT_VM_START_EVENT,
    ReportVmStartEventCmd.class, cmd -> {
    evf.fire(VmCanonicalEvents.VM_LIBVIRT_REPORT_START, cmd.vmUuid);
    return null;
});
```

### Agent 主动请求重连 (行 532-542)
```java
restf.registerSyncHttpCallHandler(KVMConstant.KVM_RECONNECT_ME,
    ReconnectMeCmd.class, cmd -> {
    ReconnectHostMsg msg = new ReconnectHostMsg();
    msg.setHostUuid(cmd.hostUuid);
    bus.send(msg);
    return null;
});
```

---

## 6. 容量管理

### 容量上报周期任务 (行 779-804)
```java
private synchronized void startReportHostCapacityTask() {
    reportHostCapacityTask = thdf.submitPeriodicTask(new PeriodicTask() {
        @Override
        public long getInterval() {
            return getReportInterval();
        }

        @Override
        public void run() {
            reportHostCapacity();
        }
    });
}
```

### 容量上报实现 (行 806-835)
```java
private void reportHostCapacity() {
    // 查询所有已连接的 Host
    List<String> hostUuids = Q.New(HostVO.class)
        .select(HostVO_.uuid)
        .eq(HostVO_.status, HostStatus.Connected)
        .listValues();

    // 并行检查所有 Host 容量
    new While<>(hostUuids).step((hostUuid, completion) -> {
        CheckHostCapacityMsg msg = new CheckHostCapacityMsg();
        msg.setHostUuid(hostUuid);

        bus.send(msg, new CloudBusCallBack(completion) {
            @Override
            public void run(MessageReply rly) {
                // 重新计算容量
                RecalculateHostCapacityMsg rmsg = new RecalculateHostCapacityMsg();
                rmsg.setHostUuid(hostUuid);
                bus.send(rmsg);
                completion.done();
            }
        });
    }, 15).run(new NopeWhileDoneCompletion());
}
```

---

## 7. 扩展点机制

### 扩展点初始化 (行 359-369)
```java
protected void populateExtensions() {
    // 收集所有 KVMHostConnectExtensionPoint 实现
    connectExtensions = pluginRgty.getExtensionList(KVMHostConnectExtensionPoint.class);

    // 收集网卡信息补全扩展点
    for (KVMCompleteNicInformationExtensionPoint ext :
         pluginRgty.getExtensionList(KVMCompleteNicInformationExtensionPoint.class)) {
        completeNicInfoExtensions.put(ext.getL2NetworkTypeVmNicOn(), ext);
    }
}
```

---

## 8. 可抽象到 RoleAdapter 的操作

| KVM 操作 | RoleAdapter 方法 |
|---------|-----------------|
| `createHost()` | `createRole()` |
| `deployAnsibleModule()` | `AgentDeployable.deployAgent()` |
| `connectHost()` | `AgentDeployable.reconnectAgent()` |
| `checkNewAddedHost()` | `ClusterBindable.validateClusterCompatibility()` |
| `reportHostCapacity()` | `RoleAdapter.getCapacityInfo()` |
| TCP 心跳检测 | `AgentDeployable.checkAgentStatus()` |

