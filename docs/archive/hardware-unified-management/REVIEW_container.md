# Phase 1 详细设计评审意见 -- Container 模块架构师

**评审人**: Container Module Expert
**评审文档**: PHASE1_Detailed_Design.md v1.1
**评审结论**: 需修改 (3 P0 + 2 P1)

---

## 一、PhysicalServerVO 属性覆盖

### 问题 1 [P0]: 缺少 endpointUuid 映射
`NativeHostVO` 的核心特有字段是 `endpointUuid`，通过 `@ForeignKey` 指向 `ContainerManagementEndpointVO`，CASCADE 删除。PhysicalServerRoleVO 仅记录 roleUuid，无法表达"容器角色来自哪个管理端点"。

**建议**: PhysicalServerRoleVO 增加 `sourceUuid`，或 SPI 增加 `getManagementEndpointUuid(String roleUuid)`。

### 问题 2 [P1]: 缺少 clusterUuid
NativeHostVO 继承 HostVO 持有 clusterUuid，同步时设为 NativeClusterVO.uuid。ServerPool 是扁平物理分组，无法表达 K8s 的 Cluster -> Node 层级语义。

### 问题 3 [P2]: OOB 字段在 API 中不应全为必填
容器节点通过 K8s API 同步入库，不涉及 OOB 管理。`APIRegisterPhysicalServerMsg` 中 oobAddress/oobUsername 应改为 `required = false`，oobManagementType 增加 `NONE` 选项。

---

## 二、角色关联机制的致命缺陷 [P0]

### NativeHost 创建完全不走 HostAfterConnectedExtensionPoint

**代码证据**:
1. `NativeFactory.createHost()` 直接抛 `UnsupportedOperationException`
2. 整个 container 模块没有任何代码实现或触发 `HostAfterConnectedExtensionPoint`
3. NativeHost 实际创建路径：`APISyncContainerManagementEndpointMsg` -> `ContainerEndpointBase.syncNodesFromCluster()` -> 直接 `dbf.persist(host)`
4. `DummyNativeHost` 对除 PingHostMsg 外的所有消息都返回空 MessageReply

**建议**:
- 方案 A（推荐）: 监听 `ContainerEndpointSyncExtensionPoint`
- 方案 B: 在 `syncNodesFromCluster()` 后增加新扩展点 `NativeHostSyncedExtensionPoint`
- 方案 C: 使用 DbEntityLister 感知 NativeHostVO 插入

注意: NativeHost 的 managementIp 来源于 K8s InternalIP，可能与物理服务器管理 IP 不同。

---

## 三、ServerCapacityVO 对容器不适用 [P0]

### 容器模块完全没有实现容量管理
- 整个 container 模块没有容量扣减代码
- K8s 自主调度 Pod，ZStack 只做事后同步
- Pod 的 CPU/Memory 可能为零（未设 limits）

### 共享扣减模式不适用
- KVM: ZStack 主动调度，先扣减再创建（事前）
- Container: K8s Scheduler 自主调度，ZStack 事后同步（事后）

AllocateServerMsg 如果为容器场景扣减 ServerCapacityVO，会出现数据不一致。

**建议**:
1. `ServerRoleType` 增加 `isExternallyScheduled()` 方法
2. NATIVE_HOST 的 ServerCapacityVO 以**只读同步**模式工作
3. `ServerCapacityUpdater` 对 NATIVE_HOST 拒绝 `reserve()` 操作

---

## 四、AllocateServerMsg 对容器无实际意义 [P0]

- ZStack 不创建 Pod（通过 K8s API 创建 Deployment）
- Pod 调度权在 K8s Scheduler（taints/tolerations, affinity, resource requests）
- ZStack 的 LEAST_USED / RANDOM 策略无法替代 K8s 调度

**建议**: 明确标注 NATIVE_HOST 不支持主动分配，或在 ServerRoleAllocatorFlow 中对 NATIVE_HOST 返回错误。

---

## 五、NativeHostVO 继承 HostVO 的影响 [P1]

继承链: `ResourceVO -> HostAO -> HostVO -> NativeHostVO`

### 影响 1: 双重容量记录
同时维护 HostCapacityVO（HostVO 层）和 ServerCapacityVO（PhysicalServerVO 层）容易不一致。

### 影响 2: 删除级联
NativeHostVO 通过 HostEO 软删除，PhysicalServerVO 物理删除。NativeHost 被删时 PhysicalServerRoleVO 需清理，但 PhysicalServerVO 应保留。

**建议**: 明确 PhysicalServerRoleVO 生命周期管理。Phase 1 ServerCapacityVO 仅记录物理硬件容量。

---

## 六、容器特有遗漏

1. **NativeClusterVO**: K8s 集群信息（kubeConfig, masterUrl, version, prometheusURL）完全未覆盖
2. **ContainerManagementEndpointVO**: 端点认证信息（accessKeyId/Secret）与 OOB 完全是两套体系
3. **GPU/PCI 设备管理**: HAMi 虚拟化 GPU 设备，ContainerEndpointBase 有专门的 PCI 设备同步
4. **Pod 生命周期特殊性**: namespace, PodStatusPhase, Pod 可被 K8s 随时驱逐重建

---

## 七、迁移风险

### 低风险
- 现有 API 不修改，数据模型独立，角色映射为引用

### 中风险
- managementIp 匹配可靠性（K8s InternalIP vs 管理IP）
- K8s 节点频繁上下线导致"僵尸关联"

### 高风险
- Phase 3 兼容层误操作：不应将 NativeHost 的 AllocateHostMsg 路由到 ServerCapacityUpdater
- 双容量数据维护成本

---

## 八、改进优先级

| 问题 | 级别 | 建议 |
|------|------|------|
| 角色关联扩展点错误 | P0 | 改用 ContainerEndpointSyncExtensionPoint |
| ServerCapacityVO 扣减模式不适用 K8s | P0 | NATIVE_HOST 仅做只读同步 |
| AllocateServerMsg 对 NATIVE_HOST 无意义 | P0 | 排除 NATIVE_HOST 或返回不支持 |
| 缺少 endpointUuid 映射 | P1 | PhysicalServerRoleVO 增加 sourceUuid |
| 缺少 clusterUuid | P1 | PhysicalServerRoleVO 增加 cluster 上下文 |
