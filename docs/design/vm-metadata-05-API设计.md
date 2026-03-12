# VM 元数据 — API 设计

## 目录

1. [API 总览](#1-api-总览)
2. [扫描虚拟机元数据](#2-扫描虚拟机元数据)
3. [读取虚拟机元数据](#3-读取虚拟机元数据)
4. [注册虚拟机](#4-注册虚拟机)
5. [检查虚拟机元数据一致性](#5-检查虚拟机元数据一致性)
6. [运维辅助 API](#6-运维辅助-api)
7. [内部消息](#7-内部消息)
8. [公共参数](#8-公共参数)
9. [统一错误码](#9-统一错误码)

---

## 1. API 总览

| # | API | 方向 | 权限 | 说明 |
|---|-----|------|------|------|
| 1 | `APIScanVmInstanceMetadataMsg` | 外部 | admin | 扫描主存储，返回有元数据的 VM 列表 |
| 2 | `APIReadVmInstanceMetadataMsg` | 外部 | admin | 读取指定 VM 的元数据 JSON |
| 3 | `APIRegisterVmInstanceFromMetadataMsg` | 外部 | admin | 从元数据注册 VM |
| 4 | `APICheckVmInstanceMetadataConsistencyMsg` | 外部 | admin | 检查 DB 与存储上元数据的一致性 |
| 5 | `APIUpdateVmMetadataMsg` | 外部 | admin | 手动触发指定 VM 的元数据全量刷写 |
| 6 | `APIPreCheckVmMetadataRegistrationMsg` | 外部 | admin | 注册前预检查 |
| 7 | `APICleanupVmInstanceMetadataMsg` | 外部 | admin | 批量清理指定范围的元数据文件/LV（仅 `enabled=false` 时可用） |
| 8 | `UpdateVmInstanceMetadataMsg` | 内部 | — | Poller/triggerFlush 发送给 VmInstanceBase |
| 9 | `UpdateVmInstanceMetadataOnPrimaryStorageMsg` | 内部 | — | 发送给主存储 handler |
| 10 | `UpdateVmInstanceMetadataOnHypervisorMsg` | 内部 | — | 发送给 Host Agent |

---

## 2. 扫描虚拟机元数据

### 2.1 APIScanVmInstanceMetadataMsg

扫描指定主存储上的元数据，返回有元数据文件/LV 的 VM 列表及摘要信息。

> **命名理由**：使用 `Scan` 而非 `Get`，因为该 API 不是从 DB 查询，而是触发 Agent 扫描存储，是一次性 I/O 操作。`Scan` 语义更准确，避免与标准 `APIGet*` 查询模式混淆。

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| primaryStorageUuids | List\<String\> | 否 | 指定主存储 UUID 列表；为空则扫描所有已连接 PS |
| vmUuids | List\<String\> | 否 | 仅扫描指定 VM 的元数据；为空则扫描全部 |

**响应 — APIScanVmInstanceMetadataReply**

| 字段 | 类型 | 说明 |
|------|------|------|
| success | Boolean | — |
| metadataList | List\<VmMetadataScanResult\> | 扫描结果列表 |

**VmMetadataScanResult**

| 字段 | 类型 | 说明 |
|------|------|------|
| vmUuid | String | VM UUID |
| vmName | String | VM 名称（来自 sblk Header 摘要 / JSON 文件内容） |
| vmCategory | String | VM 类别（REGULAR / TEMPLATE / TEMPLATE_CACHE） |
| primaryStorageUuid | String | 元数据所在主存储 UUID |
| primaryStorageType | String | 主存储类型（SharedBlock / LocalStorage / NFS） |
| schemaVersion | String | 元数据 schema 版本 |
| lastUpdateTime | Long | 最后更新时间戳（epoch ms） |
| metadataPath | String | 元数据路径（sblk LV path / JSON file path） |
| sizeBytes | Long | 元数据占用空间（字节） |

### 2.2 实现说明

- sblk：调用 Agent 扫描 VG 中所有 `*_vmmeta` LV，读取 Header 提取摘要信息（见 Part 4e §1）
- local/NFS：扫描 `{mountPath}/.zstack-vm-metadata/` 目录下 JSON 文件
- 扫描结果不含元数据内容，仅含摘要（轻量级）
- 对应的 Java 文件为 `APIScanVmInstanceMetadataMsg.java` / `APIScanVmInstanceMetadataReply.java`，位于 `header/storage/primary/`

> v2+ 规划（Q5-1）：Scan API 将补充分页参数 `start`/`limit`（或 `offset`/`limit`），避免大规模环境一次性返回过大结果集。

---

## 3. 读取虚拟机元数据

### 3.1 APIReadVmInstanceMetadataMsg

读取指定 VM 的完整元数据 JSON 内容。

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| vmUuid | String | 是 | 要读取的 VM UUID |
| primaryStorageUuid | String | 是 | 元数据所在主存储 UUID |

> **空 payload 处理**：若读取到的 payload 为空 JSON `{}`（初始化后尚未写入完整数据），仍返回 `readStatus=OK` + `metadataContent="{}"`。调用方检查 payload 内容有无实质字段决定是否可注册。
>
> **`__readStatus` 嵌入**：Read API 在返回 `metadataContent` 时，将当前 `readStatus` 值以 `"__readStatus": "<status>"` 字段嵌入 JSON 根级别。此字段供 Register API 入口校验数据可用性（见 [Part 3 §3.3-1](vm-metadata-03-注册与运维.md#33-完整注册步骤)）。手动构造的 metadataContent 若不含此字段，Register 视为 OK 继续。

**响应 — APIReadVmInstanceMetadataReply**

| 字段 | 类型 | 说明 |
|------|------|------|
| success | Boolean | — |
| metadataContent | String | 完整元数据 JSON 字符串 |
| schemaVersion | String | 元数据 schema 版本 |
| readStatus | String | OK / NEED_REPAIR / RECOVERED / DEGRADED / STORAGE_CHANGE_INCOMPLETE / CORRUPTED |
| repairAction | String | 可为 null。NEED_REPAIR/RECOVERED 时提示的修复动作（如 "complete_phase3" / "rebuild_header" / "full_refresh"） |
| warnings | List\<String\> | 读取过程中的非致命警告 |

### 3.2 readStatus 说明

| 状态 | 含义 | payload | is_usable | 后续操作 |
|------|------|---------|-----------|----------|
| OK | 正常读取，Checksum 校验通过 | (Y) 有效 | (Y) | — |
| NEED_REPAIR | Slot 可读但 Header 需修复（sblk，PendingOp 残留） | (Y) 有效 | (Y) | 管理平面发送 `RepairMetadataMsg` |
| RECOVERED | Header 损坏但通过 Slot 自描述恢复成功 | (Y) 有效 | (Y) | 管理平面发送 `RepairMetadataMsg` 重建 Header |
| STORAGE_CHANGE_INCOMPLETE | 存储拓扑已变更但元数据未更新（PendingOp=2 且 Phase 2 未完成） | (!) stale | (N) | **禁止注册**，必须 `markDirty` 全量重写 |
| DEGRADED | 单 Slot 损坏，通过另一 Slot 降级读取成功 | (!) 有效（非最新） | (Y) | 允许注册（如灾备场景）+ 必须告警 + 触发修复 |
| CORRUPTED | A/B 双 Slot 均损坏（sblk）或文件内容无效 | (N) | (N) | `markDirty` 全量重写 |

> sblk 读取与恢复的完整流程见 [Part 4d](vm-metadata-04d-sblk读取与恢复.md)。
>
> v2+ 规划（Q5-2）：Read API 增加 streaming/分块返回模式，降低超大 payload 场景下单次响应体压力。

---

## 4. 注册虚拟机

### 4.1 APIRegisterVmInstanceFromMetadataMsg

从元数据注册虚拟机。详细注册流程见 [Part 3](vm-metadata-03-注册与运维.md)。

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| metadataContent | String | 是 | 完整元数据 JSON（通常来自 `APIReadVmInstanceMetadataMsg` 的响应）。大小限制：超过 30MB 拒绝 |
| targetPrimaryStorageUuid | String | 是 | 目标主存储 UUID |
| zoneUuid | String | 是 | 目标 Zone UUID |
| clusterUuid | String | 是 | 目标 Cluster UUID |
| forceVersionMismatch | Boolean | 否 | 默认 false。设为 true 时允许 schemaVersion 不匹配的强制注册 |

**响应 — APIRegisterVmInstanceFromMetadataEvent**

| 字段 | 类型 | 说明 |
|------|------|------|
| success | Boolean | — |
| inventory | VmInstanceInventory | 注册成功的 VM Inventory |
| warnings | List\<String\> | 注册过程中的非致命警告（如 imageUuid 不存在、diskOfferingUuid 已清空、模板 VM 降级为普通 VM 等） |

> **LongJob**：注册操作通过 `LongJob` 框架异步执行，超时时间默认 30 分钟。原因：注册涉及大量 DB 写入 + Agent 调用（快照链变基），耗时可能较长。LongJob 提供进度查询、超时保护和 API 线程释放。
>
> **输入校验**：`metadataContent` 大小超过 30MB 立即拒绝（与 Part 2b §10 的 payload 大小保护一致）。校验在 API 入口层执行，在 JSON 解析之前。

### 4.2 状态转换

```
(new) → Registering → Stopped
              │
              └── 失败 → 回滚删除所有 VO
```

注册成功后 VM 处于 `Stopped` 状态。用户需要先添加网卡（`AttachVmNicToVm`）再启动。

### 4.3 注册后首次启动

VM 首次从 Stopped 转为 Running 时，删除 `vm.metadata.registered.not.started` ResourceConfig，立即触发 `markDirty`。此后元数据正常跟踪（见 [Part 3 §3.2](vm-metadata-03-注册与运维.md#32-注册-vm-未首次启动-resourceconfig)）。

### 4.4 v2+ 规划：批量注册 API

> 当前注册 N 个 VM 需执行 4N 次 API 调用（Scan + Read + PreCheck + Register × N），大规模灾备恢复场景（100+ VM）效率较低。
>
> v2+ 计划引入 `APIBatchRegisterVmInstanceFromMetadataMsg`：
> - **输入**：`vmUuids`（List\<String\>）+ `primaryStorageUuid` + `zoneUuid` + `clusterUuid`。内部自动执行 Read + PreCheck + Register。
> - **并行策略**：按 `vm.metadata.global.maxConcurrent` 控制并行度，分批注册。
> - **部分成功**：返回 `List<BatchRegisterResult>`，每个 VM 独立成功/失败，不因单 VM 失败中止整批。
> - **进度查询**：通过 LongJob 框架提供进度百分比（已完成 / 总数）。

---

## 5. 检查虚拟机元数据一致性

### 5.1 APICheckVmInstanceMetadataConsistencyMsg

从 DB 构建当前元数据 → 从存储读取已持久化元数据 → 结构化比较。

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| vmUuids | List\<String\> | 否 | 指定 VM 列表；为空则检查所有已启用元数据的 VM |
| primaryStorageUuid | String | 否 | 限定主存储范围 |
| autoRepair | Boolean | 否 | 默认 `false`。`true` 时对可修复不一致项自动执行 `markDirty(vmUuid)`（v1.1 新增） |

**响应 — APICheckVmInstanceMetadataConsistencyReply**

| 字段 | 类型 | 说明 |
|------|------|------|
| success | Boolean | — |
| results | List\<ConsistencyCheckResult\> | 每个 VM 的检查结果 |

**ConsistencyCheckResult**

| 字段 | 类型 | 说明 |
|------|------|------|
| vmUuid | String | VM UUID |
| consistent | Boolean | 是否一致 |
| diffs | List\<DiffEntry\> | 不一致的项目列表 |
| action | String | 自动修复操作（NONE / MARK_DIRTY） |

### 5.2 比较排除字段

以下字段在比较时忽略（属于运行时变化字段）：

- `lastOpDate` — 时间戳字段
- `id` — 自增 ID（SystemTag、ResourceConfig）
- `managementNodeUuid` — 运行时绑定 MN

> **Q37 — 排除字段完整清单**：除上述 3 项外，以下字段也需排除：
> - `accountUuid` — 注册场景中会被替换为目标环境 accountUuid，不应参与比对
> - `createDate` — 新创建 VO 的 createDate 与元数据中记录的不同（每次 persist 时由 DB 生成）
> - `VmInstanceVO.hostUuid` — 运行时动态绑定，VM Stopped 时为 null
> - `VmInstanceVO.lastHostUuid` — 运行时动态绑定
> - `VmInstanceVO.state` — 运行时状态，不属于结构化配置
> - `VolumeVO.actualSize` — Agent 端物理大小，不参与元数据一致性判定
> - `VolumeVO.status` — 运行时状态（Ready/NotInstantiated）
>
> **比对逻辑**：先按 `VmInstanceVO.uuid` 匹配 VM 主记录，再按各子 VO 的 `uuid` 逐项匹配 Volume/Snapshot/SystemTag/ResourceConfig。匹配成功后逐字段比对（排除上述字段）。排除字段列表允许通过 `ConsistencyCheckExcludedFields` 静态常量扩展，新增字段时添加注释说明排除原因。

### 5.3 自动修复

发现不一致时，自动调用 `markDirty(vmUuid)` 触发全量重写。这是内部消息丢失 `markDirty()` 的批量补救手段（见 [Part 2b §12.4 D1 补充说明](vm-metadata-02b-高可用与运维.md#d1-补充说明--内部消息-handler-遗漏-markdirty-的补救)）。

> 行为约束（Q5-3）：仅当 `autoRepair=true` 时执行自动修复；默认 `false` 只返回检查结果与建议动作，避免检查 API 带来隐式写入副作用。

**自动修复边界表**：

| 场景 | DB 状态 | 存储元数据状态 | 修复动作 | 说明 |
|------|---------|--------------|----------|------|
| DB 比存储新 | 有字段差异 | 旧版本 | MARK_DIRTY | 正常情况，刷写延迟或 markDirty 遗漏 |
| DB 缺少 UUID | VM 存在 | 存储上有元数据但 UUID 未在 DB 中 | MANUAL_CHECK | 可能是孤儿元数据，需人工确认 |
| 存储元数据损坏 | VM 存在 | CORRUPTED / 无法解析 | MARK_DIRTY | 全量重建 |
| 存储不可达 | VM 存在 | 无法读取 | SKIP + WARN | 不执行修复，记录告警 |

---

## 6. 运维辅助 API

### 6.1 手动触发元数据更新

#### APIUpdateVmMetadataMsg

指定 vmUuid，手动触发一次全量元数据更新。适用于运维人员发现元数据滞后时的即时修复。

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| vmUuid | String | 是 | 目标 VM UUID |

**响应 — APIUpdateVmMetadataEvent**

| 字段 | 类型 | 说明 |
|------|------|------|
| success | Boolean | — |

**实现**：直接调用 `markDirty(vmUuid, true)` 标脏为 STORAGE 级别（全量），triggerFlush 立即处理。

> 并发说明（Q5-4）：`APIUpdateVmMetadataMsg` 的同 VM 并发更新由 `ChainTask "update-vm-{vmUuid}-metadata"` 串行化保证，无需额外 API 级锁。

### 6.2 注册预检查

#### APIPreCheckVmMetadataRegistrationMsg

在正式注册前执行预检查，返回所有检查项的通过/失败状态，帮助用户提前发现问题。

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| metadataContent | String | 是 | 完整元数据 JSON |
| targetPrimaryStorageUuid | String | 是 | 目标主存储 UUID |
| zoneUuid | String | 否 | 目标 Zone UUID。若提供，额外校验 Zone 存在性及与 PS 的归属关系 |
| clusterUuid | String | 否 | 目标 Cluster UUID。若提供，额外校验 Cluster 存在性、与 PS 的连接性、及 Zone/Cluster 归属一致性 |
| forceVersionMismatch | Boolean | 否 | 默认 false。设为 true 时 `SCHEMA_VERSION_MATCH` 检查项不阻塞 |

**响应 — APIPreCheckVmMetadataRegistrationReply**

| 字段 | 类型 | 说明 |
|------|------|------|
| success | Boolean | — |
| checkResults | List\<PreCheckItem\> | 各检查项结果 |

**PreCheckItem**

| 字段 | 类型 | 说明 |
|------|------|------|
| name | String | 检查项名称 |
| passed | Boolean | 是否通过 |
| message | String | 检查详情 / 失败原因 |

### 6.3 清理虚拟机元数据

#### APICleanupVmInstanceMetadataMsg

批量清理指定范围的虚拟机元数据文件/LV 及关联 DB 记录。仅在 `vm.metadata.enabled=false` 时允许执行。

> **使用场景**：运维在关闭元数据功能后（`true → false`），按需回收存储空间。系统不自动清理，避免误操作丢失容灾数据。

**前置约束**：`vm.metadata.enabled` 必须为 `false`，否则返回错误 `METADATA_CLEANUP_REJECTED_WHILE_ENABLED`。

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| primaryStorageUuids | List\<String\> | 否 | 指定主存储范围。为空则清理所有 PS |
| vmUuids | List\<String\> | 否 | 指定 VM 范围。为空则清理所有 VM |

> 两个参数均为空时，清理**全部已存在元数据的 VM**。两个参数同时提供时取交集。

**响应 — APICleanupVmInstanceMetadataEvent**

| 字段 | 类型 | 说明 |
|------|------|------|
| success | Boolean | — |
| totalCleaned | Integer | 成功清理的 VM 数量 |
| totalFailed | Integer | 清理失败的 VM 数量 |
| failedVmUuids | List\<String\> | 清理失败的 VM UUID 列表（便于重试） |

**实现流程**：

1. 前置检查：`vm.metadata.enabled == false`
2. 根据参数确定清理范围（查 `VmMetadataPathFingerprintVO` 获取有元数据的 VM 列表）
3. 分批执行（keyset 分页，批次大小复用 `vm.metadata.upgrade.refreshBatchSize`）：
   - 对每个 VM 调用 `metadataStorageHandler.deleteMetadata(psUuid, vmUuid)`
   - 删除 `VmMetadataPathFingerprintVO` 记录
   - 删除残留 `VmMetadataDirtyVO` 记录（`INSERT IGNORE` 插入后未消费的行）
4. 汇总结果，部分失败不中止（best-effort），返回失败列表供运维重试

**幂等性**：`deleteMetadata` 遵循 C-01C-9 约束（删除不存在的元数据视为成功），重复调用不报错。

**并发控制**：使用全局 ChainTask `"cleanup-vm-metadata-global"`（syncLevel=5）限流，避免对存储造成批量删除压力。

### 6.4 预检查项清单

| 检查项 | 说明 | 失败级别 |
|--------|------|----------|
| `FORMAT_VALID` | 元数据 JSON 格式、Base64 编码完整性 | BLOCK |
| `SCHEMA_VERSION_MATCH` | `schemaVersion == dbf.getDbVersion()`（精确匹配） | BLOCK（除非 `forceVersionMismatch=true`） |
| `VM_CATEGORY_CHECK` | vmCategory 不是 TEMPLATE_CACHE | BLOCK |
| `UUID_CONFLICT` | VM/Volume/Snapshot 等 UUID 无冲突 | BLOCK |
| `PS_REACHABLE` | 目标主存储可达且状态正常 | BLOCK |
| `PS_TYPE_SUPPORTED` | 主存储类型支持元数据（sblk/local/NFS） | BLOCK |
| `CROSS_STORAGE_CHECK` | 所有磁盘属于同一主存储 | BLOCK |
| `INSTALL_PATH_EXIST` | 替换后路径在存储上存在。Root Volume 缺失为 BLOCK，Data Volume 缺失为 WARN | Root=BLOCK / Data=WARN |
| `READ_STATUS_USABLE` | 元数据 `__readStatus` 不为 CORRUPTED 或 STORAGE_CHANGE_INCOMPLETE | BLOCK |
| `CDROM_DETECTED` | 检测到 VM 挂载了 CDROM / ISO，注册后可能不可用 | WARN |

> **schemaVersion 校验逻辑**：使用精确匹配 `==` 比较数据库版本。不支持低版本数据库注册高版本元数据，也不支持高版本数据库注册低版本元数据（除非 `forceVersionMismatch=true`）。参见 [Part 1a §6.2](vm-metadata-01a-数据模型与序列化.md#62-注册时校验规则)。

`INSTALL_PATH_EXIST` 检查实现示例（Q5-6）：

```java
if (volume.isRootVolume() && !pathExists(volume.getInstallPath())) {
    result.add(PreCheckItem.block(INSTALL_PATH_EXIST, ...));
} else if (!pathExists(volume.getInstallPath())) {
    result.add(PreCheckItem.warn(INSTALL_PATH_EXIST, ...));
}
```

> `CDROM_DETECTED` 处理说明（Q5-7）：CDROM/ISO 挂载信息不在 VM 元数据范围内，注册后如业务需要须手动重新挂载。

---

## 7. 内部消息

### 7.1 UpdateVmInstanceMetadataMsg

由 Poller/triggerFlush 发送给 `VmInstanceBase`，触发构建元数据并写入主存储。

| 字段 | 类型 | 说明 |
|------|------|------|
| uuid | String | VM UUID |
| storageStructureChange | Boolean | 是否涉及存储拓扑变更（OP type 标记） |

路由：`makeLocalServiceId(msg, VmInstanceConstant.SERVICE_ID)`

超时：`setTimeout(5min)` — 大 payload O_DIRECT 写入 + 可能的 lvextend + 构建耗时（与 Part 2 §5.1 一致）

### 7.2 UpdateVmInstanceMetadataOnPrimaryStorageMsg

由 VmInstanceBase 发送给主存储 handler。

| 字段 | 类型 | 说明 |
|------|------|------|
| vmUuid | String | VM UUID |
| payload | String | 序列化后的元数据 JSON |
| storageStructureChange | Boolean | OP type |

路由：`makeLocalServiceId`

### 7.3 UpdateVmInstanceMetadataOnHypervisorMsg

由主存储 handler 发送给 Host Agent。

> **Agent 通信安全**：HTTP 请求携带 `agentToken`（通过 `X-ZStack-Agent-Token` header 传递），Agent 端校验 token 一致性。这与 ZStack 其他 Agent 通信一致，无额外认证机制。

| 字段 | 类型 | 说明 |
|------|------|------|
| hostUuid | String | 目标主机 UUID |
| vmUuid | String | VM UUID |
| payload | String | 元数据 JSON |
| installPath | String | 元数据存储路径 |
| storageStructureChange | Boolean | OP type |

路由：`makeTargetServiceIdByResourceUuid(hostUuid)` — hash 环路由到 host-owner MN

超时：`setTimeout(2min)`

### 7.4 消息调用链

```
VmMetadataUpdateInterceptor / Poller
  → markDirty + triggerFlushForVm
    → ChainTask "update-vm-metadata-global"
      → ChainTask "update-vm-{vmUuid}-metadata"
        → bus.send(UpdateVmInstanceMetadataMsg)
          → VmInstanceBase: build payload
          → bus.send(UpdateVmInstanceMetadataOnPrimaryStorageMsg)
            → PS handler: ChainTask "update-metadata-on-ps-{psUuid}"
              → bus.send(UpdateVmInstanceMetadataOnHypervisorMsg)
                → HostBase → HTTP call to Agent
```

完整消息链描述见 [Part 2 §5](vm-metadata-02-脏标记与Poller.md#5-消息调用链)。

### 7.5 RepairMetadataMsg

由管理平面发送给主存储 handler，用于修复 sblk Header（包括完成未完成的 Phase 3、清除 PendingOp、重建 Header）。

| 字段 | 类型 | 说明 |
|------|------|------|
| vmUuid | String | VM UUID |
| primaryStorageUuid | String | 元数据所在主存储 UUID |
| repairAction | String | 修复动作：`complete_phase3` / `clear_pending_op` / `rebuild_header` / `full_refresh` |

路由：`makeLocalServiceId` → 主存储 handler → Agent HTTP 调用

> `full_refresh` 等价于 `markDirty(vmUuid, true)`，但通过显式消息而非 Poller 间接触发，便于日志追踪。

### 7.6 BatchCheckMetadataStatusMsg

由管理平面发送给主存储 handler，批量检查多个 VM 的元数据 Header 状态，用于健康巡检。

| 字段 | 类型 | 说明 |
|------|------|------|
| primaryStorageUuid | String | 目标主存储 UUID |
| vmUuids | List\<String\> | 要检查的 VM UUID 列表 |

**响应 — BatchCheckMetadataStatusReply**

| 字段 | 类型 | 说明 |
|------|------|------|
| results | Map\<String, MetadataStatusResult\> | key=vmUuid, value=状态结果 |

**MetadataStatusResult**

| 字段 | 类型 | 说明 |
|------|------|------|
| readStatus | String | OK / NEED_REPAIR / RECOVERED / DEGRADED / STORAGE_CHANGE_INCOMPLETE / CORRUPTED |
| repairAction | String | 可为 null |
| lastUpdateTime | Long | 最后更新时间戳 |
| pendingOp | Integer | 当前 PendingOp 值（0/1/2） |

路由：`makeLocalServiceId` → 主存储 handler → Agent HTTP 调用（批量读 Header，不读 Slot）

---

## 8. 公共参数

### 8.1 GlobalConfig

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `vm.metadata.enabled` | Boolean | false | 元数据功能总开关 |
| `vm.metadata.dirty.pollIntervalSec` | Long | 5 | Poller 轮询间隔（秒） |
| `vm.metadata.dirty.batchSize` | Integer | 50 | 每轮 Poller 最多认领行数 |
| `vm.metadata.maxRetry` | Integer | 5 | 最大重试次数 |
| `vm.metadata.ps.maxConcurrent` | Integer | 5 | 同一 MN 同一 PS 最大并发写入 |
| `vm.metadata.global.maxConcurrent` | Integer | 10 | 同一 MN 最大并发 VM 更新数 |
| `vm.metadata.pathCheck.intervalSec` | Long | 300 | 路径指纹巡检间隔（秒） |

> 完整 GlobalConfig 配置说明见 [Part 2b §13](vm-metadata-02b-高可用与运维.md#13-globalconfig-配置项汇总)（权威来源）。本表仅为快速参考。

### 8.2 权限约束

所有 API 仅限 **admin** 操作。注册 VM 场景为灾难恢复，不面向普通用户。

---

## 9. 统一错误码

> **权威来源**：所有与 VM 元数据相关的错误码在此统一定义。其他文档应引用本节。

| 错误码 | 适用 API | 说明 |
|--------|---------|------|
| `METADATA_INVALID_FORMAT` | Read / Register / PreCheck | 元数据 JSON 格式错误、Base64 解码失败或校验器不通过 |
| `METADATA_SCHEMA_VERSION_MISMATCH` | Register / PreCheck | `schemaVersion != dbf.getDbVersion()`，且未设置 `forceVersionMismatch=true` |
| `METADATA_UUID_CONFLICT` | Register / PreCheck | VM、Volume、Snapshot 等 UUID 与已有资源冲突 |
| `METADATA_STORAGE_NOT_SUPPORTED` | Register / PreCheck / Scan | 主存储类型不支持元数据功能（如 Ceph、ZBS、vhost） |
| `METADATA_CROSS_STORAGE_FORBIDDEN` | Register / PreCheck | 元数据中的磁盘分布在多个主存储上 |
| `METADATA_INSTALL_PATH_NOT_FOUND` | Register | 替换后的 installPath 在目标存储上不存在 |
| `METADATA_CACHE_VM_NOT_REGISTERABLE` | Register / PreCheck | vmCategory = TEMPLATE_CACHE，缓存 VM 拒绝注册 |
| `METADATA_VM_REGISTERING` | Register | 目标 VM 正在被另一个注册操作处理中 |
| `METADATA_READ_CORRUPTED` | Read | A/B 双 Slot 均损坏（sblk）或文件不可读 |
| `METADATA_PAYLOAD_TOO_LARGE` | Update（内部） | Payload 超过 30MB 上限 |
| `METADATA_PS_UNREACHABLE` | PreCheck / Register / Update | 目标主存储不可达或状态异常 |
| `METADATA_FEATURE_DISABLED` | All | `vm.metadata.enabled = false` 时调用 API |

### 9.1 错误码格式

所有错误码使用 `SysErrors.METADATA_` 前缀，在 `VmMetadataErrors` 枚举中统一定义。API Reply/Event 中通过标准 `ErrorCode` 结构返回。
