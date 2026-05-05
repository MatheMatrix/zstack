# ProvisionProvider SPI 详细设计 — 装机能力复用（v2 解耦版）

> **Date**: 2026-04-16
> **目标**: 抽象装机能力 SPI，让 KVM host 装机复用 BM2 Gateway 的 DHCP/TFTP/iPXE 基础设施
> **版本**: v2 — 解除装机和角色注册的耦合，两者是完全独立的操作

---

## 1. 核心设计原则（v2 修正）

### 1.1 v1 的错误

v1 设计让 `ProvisionProvider.startProvisioning()` 带 `callbackMsg` 字段，装完后主动发业务消息（如 `KvmInstallCompletedMsg`）触发后续注册。这是架构错误：

- **SPI 污染**: ProvisionProvider 必须知道 KVM/BM2 的存在（为了 callback 回对应业务 handler）
- **流程耦合**: "装 OS" 和 "注册角色" 被串在一个不可分割的链路里
- **扩展困难**: 每多一种用法（e.g., 装个纯 OS 不注册任何角色）就得改 SPI

### 1.2 v2 原则：完全解耦

**装机 = 原子能力，只做一件事**：在指定物理机上装 OS，装完为止。

- 装完后物理机就是一台有 OS 的物理机
- ProvisionProvider 不知道、不关心这台机器接下来会被用来做什么
- 没有业务 callback

**角色注册 = 独立动作**：只在已准备好的物理机上注册。

- `KvmRoleProvider.createRoleEntity()` 只发 `AddKVMHostMsg`
- 不知道、不关心 OS 是怎么来的（预装 / 手动 / 走 ProvisionProvider 都行）
- 前提是物理机有 OS 且 SSH 可达，凭据已知

**组合在外部**：想要"装 OS + 注册 KVM"的串行流程，用户串两个 API 调用（或 Should Have 的 orchestrator）。

---

## 2. ProvisionProvider SPI 定义

```java
/**
 * 装机能力 SPI。按 ProvisionNetworkType 选择具体实现。
 * 职责：在物理机上装 OS。不涉及角色注册、不触发业务 callback。
 *
 * Phase 2 接口刻意缩小：
 *   - 无 cancelProvisioning（cancel 语义复杂，对齐 BM2 现状延后 Phase 3）
 *   - 无 getSessionStatus（直接查 PhysicalServerProvisionSessionVO 即可，SPI 无需代理）
 */
public interface ProvisionProvider {

    ProvisionNetworkType getType();

    /** 准备装机网络基础设施（DHCP/TFTP/HTTP server），幂等 */
    void prepareNetwork(PhysicalServerProvisionNetworkInventory network,
                        String poolUuid,
                        Completion completion);

    /** 销毁装机网络基础设施 */
    void destroyNetwork(PhysicalServerProvisionNetworkInventory network,
                        String poolUuid,
                        Completion completion);

    /**
     * 在指定物理机上装 OS。
     * 完成态 = OS 已装、物理机已 reboot 到新 OS。
     *
     * 不发送任何业务回调消息。调用方通过查询 VO 获取 session.state。
     */
    void startProvisioning(ProvisionRequest request,
                           ReturnValueCompletion<ProvisionSessionInventory> completion);
}
```

### 2.1 ProvisionRequest（纯粹）

```java
public class ProvisionRequest {
    // 物理目标
    private String serverUuid;                 // PhysicalServer UUID
    private String networkUuid;                // ProvisionNetwork UUID

    // 装什么
    private String osImageUuid;                // OS 镜像 UUID
    private String osDistribution;             // rocky9 / centos8 / ubuntu22.04
    private String kickstartTemplate;          // kickstart/preseed 模板（内容或 UUID）
                                               // 模板里可含预装 agent / 设置初始凭据 / 配置网络等指令
                                               // 但这是模板的责任，不是 SPI 的关心

    // 可选：装机网卡指定
    private String provisionNicMac;            // 指定装机网卡 MAC（不填则首个可达 NIC）

    // 自定义参数（模板变量替换等）
    private Map<String, String> customParams;

    // ❌ 无 callbackMsg
    // ❌ 无 callbackData
    // ❌ 无业务上下文
}
```

### 2.2 ProvisionSessionInventory

```java
public class ProvisionSessionInventory {
    private String uuid;
    private String serverUuid;
    private String networkUuid;
    private ProvisionState state;
    private int progressPercent;
    private String currentStep;
    private String errorMessage;
    private Timestamp startTime;
    private Timestamp completedTime;
}

public enum ProvisionState {
    Queued,
    Preparing,         // 生成 iPXE 配置
    IPxeBooting,       // IPMI 已触发 iPXE boot
    Installing,        // OS 安装中
    Completed,         // OS 装完，物理机已重启到新 OS — 终态
    Failed,
    Cancelled
}
```

### 2.3 PhysicalServerProvisionSessionVO

```java
@Entity @Table
public class PhysicalServerProvisionSessionVO {
    @Id private String uuid;

    @ForeignKey(parentEntityClass = PhysicalServerVO.class, onDeleteAction = RESTRICT)
    private String serverUuid;

    @ForeignKey(parentEntityClass = PhysicalServerProvisionNetworkVO.class, onDeleteAction = RESTRICT)
    private String networkUuid;

    private String providerName;                // GATEWAY_PXE / STANDALONE_PXE
    private String osImageUuid;
    private String osDistribution;
    private String kickstartTemplate;           // TEXT，审计用
    private String customParams;                // JSON

    @Enumerated(EnumType.STRING)
    private ProvisionState state;
    private int progressPercent;
    private String currentStep;
    private String errorMessage;

    private Timestamp startTime;
    private Timestamp completedTime;
    private Timestamp createDate;
    private Timestamp lastOpDate;

    // ❌ 无 callbackMsg / callbackData 字段
}
```

---

## 3. 两个完全独立的 API

### 3.1 装机 API — 只装 OS

```
APIProvisionPhysicalServerMsg {
    serverUuid: "ps-xxx",
    networkUuid: "pn-yyy",
    osImageUuid: "rocky9-kvm-ready",     # 恰好预装 KVM 依赖
    osDistribution: "rocky9",
    kickstartTemplate: "..."              # 可选，可包含预装 zstack-kvmagent 指令
}
    ↓
PhysicalServerManagerImpl.handle(APIProvisionPhysicalServerMsg):
    1. 查 PhysicalServer.poolUuid → 关联的 ProvisionNetwork
    2. 按 ProvisionNetwork.type 选 ProvisionProvider
    3. provider.startProvisioning(request, completion)
    4. 返回 ProvisionSession UUID 给用户
    ↓
用户轮询: APIQueryProvisionSessionMsg → 看到 state=Completed 时知道 OS 装好了
```

**这是唯一的结果**：物理机装好了 OS。没有任何角色创建、没有任何业务消息。

### 3.2 角色注册 API — 只注册

```
APIAttachPhysicalServerRoleMsg {
    serverUuid: "ps-xxx",
    roleType: "KVM_HOST",
    clusterUuid: "cluster-zzz",
    roleConfig: {
        username: "root",           # 和 kickstart 预置的一致
        password: "foo",
        sshPort: "22"
    }
}
    ↓
PhysicalServerManagerImpl.handle(APIAttachPhysicalServerRoleMsg):
    1. 互斥检查
    2. KvmRoleProvider.createRoleEntity(ctx)
    3. 创建 PhysicalServerRoleVO
    ↓
KvmRoleProvider.createRoleEntity:
    AddKVMHostMsg msg = new AddKVMHostMsg();
    msg.setServerUuid(ctx.getServerUuid());
    msg.setManagementIp(ctx.getManagementIp());
    msg.setClusterUuid(ctx.getClusterUuid());
    msg.setUsername(ctx.getRoleConfig().get("username"));
    msg.setPassword(ctx.getRoleConfig().get("password"));
    bus.call(msg);
    → HostManagerImpl 创建 KVMHostVO → ConnectHost → KVM agent 握手 → Active
    返回 host.getUuid()
```

**前提**: 物理机 SSH 可达、凭据匹配、必要的 KVM 依赖已安装。如何满足这些前提是**用户的责任**（可通过先调 Provision API 满足、可手动预装、可用其他自动化工具）。

### 3.3 组合使用（用户侧）

想要"裸机 → KVM host"全自动的用户，串接两个 API：

```python
# 伪代码
session = APIProvisionPhysicalServer(serverUuid, networkUuid, "rocky9-kvm-ready", kickstart)
while True:
    s = APIQueryProvisionSession(session.uuid)
    if s.state == "Completed": break
    if s.state == "Failed": raise Exception(s.errorMessage)
    sleep(30)

# OS 已装好，zstack-kvmagent 预装并监听
APIAttachPhysicalServerRole(serverUuid, "KVM_HOST", clusterUuid, username, password)
```

这是**组合**，不是**耦合**。两个 API 各自独立，顺序由用户决定。

### 3.4 可选的高层编排 API（Should Have）

若需 UX 糖，可提供一条 API 封装组合操作：

```
APIProvisionAndAttachRoleMsg {
    serverUuid, networkUuid, osImageUuid, kickstartTemplate,
    roleType, clusterUuid, roleConfig
}
    ↓ 内部编排:
    1. 发起 ProvisionPhysicalServer，同步等到 Completed
    2. 发起 AttachPhysicalServerRole
    全部成功才响应
```

关键点：**这是 orchestrator API，不是 SPI 的一部分**。它在 PhysicalServerManagerImpl 里顺序调两个独立动作，SPI 依然保持纯粹。Phase 2 可以不做，Phase 3 按需补。

---

## 4. BM2 Gateway 实现 ProvisionProvider

### 4.1 Bm2GatewayPxeProvisionProvider

```java
@Component
public class Bm2GatewayPxeProvisionProvider implements ProvisionProvider {
    @Autowired private CloudBus bus;
    @Autowired private DatabaseFacade dbf;
    @Autowired private BareMetal2IpmiChassisHelper ipmiHelper;
    @Autowired private BareMetal2GatewayAllocator gatewayAllocator;

    public ProvisionNetworkType getType() { return ProvisionNetworkType.GATEWAY_PXE; }

    public void prepareNetwork(...) {
        // 复用现有 PrepareProvisionNetworkInGatewayMsg
    }

    public void startProvisioning(ProvisionRequest req,
                                   ReturnValueCompletion<ProvisionSessionInventory> completion) {
        // 1. 创建 session
        PhysicalServerProvisionSessionVO session = new PhysicalServerProvisionSessionVO();
        session.setUuid(Platform.getUuid());
        session.setServerUuid(req.getServerUuid());
        session.setNetworkUuid(req.getNetworkUuid());
        session.setProviderName(getType().toString());
        session.setOsImageUuid(req.getOsImageUuid());
        session.setKickstartTemplate(req.getKickstartTemplate());
        session.setState(ProvisionState.Preparing);
        session.setStartTime(new Timestamp(System.currentTimeMillis()));
        dbf.persist(session);

        // 2. 从 PhysicalServerVO 读 OOB 凭据
        PhysicalServerVO server = dbf.findByUuid(req.getServerUuid(), PhysicalServerVO.class);
        if (server.getOobAddress() == null) {
            failSession(session, "no OOB credentials on PhysicalServer");
            completion.fail(operr("no OOB credentials"));
            return;
        }

        // 3. BM2 Gateway 生成 iPXE 配置（MAC 绑定）
        String gatewayUuid = gatewayAllocator.findGatewayForNetwork(req.getNetworkUuid());
        generateIpxeConfigViaGateway(session, req, gatewayUuid);

        // 4. 通过 IPMI 触发 iPXE boot
        IpmiBootConfig ipmi = new IpmiBootConfig(
            server.getOobAddress(), server.getOobPort(),
            server.getOobUsername(), server.getOobPassword(), "ipxe");
        session.setState(ProvisionState.IPxeBooting);
        dbf.update(session);

        ipmiHelper.bootWithConfig(ipmi, new Completion(completion) {
            public void success() {
                session.setState(ProvisionState.Installing);
                dbf.update(session);
                completion.success(toInventory(session));
                // 后续 session 状态由 gateway agent 装完回调更新，
                // 最终到达 ProvisionState.Completed。
            }
            public void fail(ErrorCode errorCode) {
                failSession(session, errorCode.getDescription());
                completion.fail(errorCode);
            }
        });
    }

    // 装完后 gateway agent 回调 ZStack → 只更新 session.state=Completed
    // 不发任何业务消息，不关心后续
    public void onAgentCallback(String sessionUuid, boolean success, String errorMsg) {
        PhysicalServerProvisionSessionVO s = dbf.findByUuid(sessionUuid, ...);
        if (success) {
            s.setState(ProvisionState.Completed);
            s.setCompletedTime(new Timestamp(System.currentTimeMillis()));
        } else {
            s.setState(ProvisionState.Failed);
            s.setErrorMessage(errorMsg);
        }
        dbf.update(s);
    }
}
```

### 4.2 BM2 Gateway 小改造

1. **IPMI Helper 解耦**: `BareMetal2IpmiChassisHelper` 增加 `bootWithConfig(IpmiBootConfig)` 方法，只接收凭据不依赖 BM2 特定 VO
2. **iPXE 配置生成泛化**: `BareMetal2Gateway.generateIpxeConfig(ProvisionTarget)` 接收通用 `ProvisionTarget` 而不是 `BareMetal2InstanceTO`
3. **Gateway agent 回调**: 装完回调只带 `sessionUuid` 和成败，gateway 侧不关心后续

---

## 5. KvmRoleProvider 的最终形态（纯粹）

```java
public class KvmRoleProvider implements PhysicalServerRoleProvider {
    @Autowired private CloudBus bus;
    @Autowired private DatabaseFacade dbf;

    @Override
    public ServerRoleType getRoleType() { return ServerRoleType.KVM_HOST; }
    @Override
    public SchedulingMode getSchedulingMode() { return SchedulingMode.INTERNAL_SHARED; }

    @Override
    public String createRoleEntity(CreateRoleEntityContext ctx) {
        // 只做一件事：把物理机注册成 KVM Host
        // 不知道 OS 是怎么来的，也不触发装机
        AddKVMHostMsg msg = new AddKVMHostMsg();
        msg.setServerUuid(ctx.getServerUuid());             // 老 API 扩展的 serverUuid 字段
        msg.setName("kvm-" + ctx.getManagementIp());
        msg.setManagementIp(ctx.getManagementIp());
        msg.setClusterUuid(ctx.getClusterUuid());
        msg.setUsername(ctx.getRoleConfig().get("username"));
        msg.setPassword(ctx.getRoleConfig().get("password"));
        msg.setSshPort(Integer.parseInt(
            ctx.getRoleConfig().getOrDefault("sshPort", "22")));
        bus.makeTargetServiceIdByResourceUuid(msg, HostConstant.SERVICE_ID, ...);

        AddKVMHostReply reply = (AddKVMHostReply) bus.call(msg);
        if (!reply.isSuccess()) throw new OperationFailureException(reply.getError());
        return reply.getInventory().getUuid();
    }

    @Override
    public void deleteRoleEntity(String roleUuid) {
        DeleteHostMsg msg = new DeleteHostMsg();
        msg.setHostUuid(roleUuid);
        bus.makeTargetServiceIdByResourceUuid(msg, HostConstant.SERVICE_ID, roleUuid);
        MessageReply reply = bus.call(msg);
        if (!reply.isSuccess()) throw new OperationFailureException(reply.getError());
    }

    @Override
    public CapacityUsage getCapacityConsumption(String serverUuid, String roleUuid) { ... }
    @Override
    public RoleWorkloadStatus getWorkloadStatus(String serverUuid, String roleUuid) { ... }

    // ❌ 无任何装机代码
    // ❌ 无 KvmInstallCompletedMsg handler
    // ❌ 不订阅 ProvisionSession 事件
}
```

---

## 6. 两张流程图对比

### 6.1 v1（错误，耦合）

```
AttachRole(KVM_HOST, osImageUuid) 
  → KvmRoleProvider.createRoleEntity
     ├─ 看到 osImageUuid 非空 → 走装机流程
     ├─ 调 ProvisionProvider（传 callbackMsg=KvmInstallCompletedMsg）
     ├─ 装完 gateway 发 KvmInstallCompletedMsg
     └─ KvmInstallCompletedHandler 创建 KVMHostVO
[ProvisionProvider 必须知道 KVM 存在，耦合]
```

### 6.2 v2（正确，解耦）

```
步骤 1（独立）:
  APIProvisionPhysicalServer(osImage="rocky9-kvm-ready", kickstart="预装 zstack-kvmagent")
    → ProvisionProvider.startProvisioning
    → 装完 session.state = Completed
    → 结束（没有回调、没有角色创建）

步骤 2（独立）:
  APIAttachPhysicalServerRole(KVM_HOST, username, password)
    → KvmRoleProvider.createRoleEntity
    → AddKVMHostMsg → ConnectHost → Active
    → 结束（没有装机逻辑）

[ProvisionProvider 不知道 KVM 存在]
[KvmRoleProvider 不知道装机存在]
[组合由用户决定]
```

---

## 7. Phase 划分（修正）

### Phase 2 Must Have

- `ProvisionProvider` SPI（纯粹，无 callback）
- `PhysicalServerProvisionSessionVO` DDL + Entity（标准 ResourceVO）
- `Bm2GatewayPxeProvisionProvider` 实现
- BM2 Gateway 小改造（IPMI Helper 解耦 + iPXE 配置泛化）
- `APIProvisionPhysicalServerMsg` — 发起装机的唯一主动 API
- BM2 Instance 装机流程保持不变（向后兼容）

> `APIQueryPhysicalServerProvisionSessionMsg` 由框架自动生成（`PhysicalServerProvisionSessionVO` 是标准 ResourceVO，用 ZStack 通用 Query 机制查询），不用单独设计。

### Phase 2 Should Have

- `APIProvisionAndAttachRoleMsg` 高层编排 API（UX 糖，可推 Phase 3）

### Phase 3

- `APICancelProvisionSessionMsg` — cancel 语义需按 session 状态分类处理，BM2 现状无 cancel 能力，推 Phase 3 再加
- 为 KVM 提供 hypervisor-ready OS 镜像 + kickstart 模板
- BM2 Instance 装机重构走 ProvisionProvider（等价重构）
- 文档：如何基于 ProvisionProvider + kickstart 模板定制自动化

---

## 8. 对 FR-012 的影响

原 PRD FR-012:
> （v1.1+ Could Have）KVM 角色的 RoleProvider 可使用 ProvisionNetwork 进行 OS 安装（裸机装 KVM ISO），需全新开发 PXE boot → kickstart/preseed → 自动注册 Host 流程

**v2 修正**:
- Phase 2 提供通用 `ProvisionProvider` SPI + BM2 Gateway 适配 — 装机能力就位
- Phase 3 只需提供 KVM-ready OS 镜像 + kickstart（内容工作，非架构工作）
- KVM host 的"自动注册"就是**用户调完 Provision API 后再调 AttachRole API** — 没有"自动"的魔法
- 真想要一键式，做 `APIProvisionAndAttachRoleMsg` orchestrator — 但它只是调两个原子 API，不是 SPI 的一部分
