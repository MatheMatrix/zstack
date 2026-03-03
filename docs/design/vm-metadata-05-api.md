# API 设计

## 1. 概述

本文档定义虚拟机元数据功能对外暴露的所有 API。按职责分为三类：

| 类别 | 通信模式 | API |
|------|----------|-----|
| **元数据查询** | 同步 GET → Reply | `APIGetVmInstanceMetadataFromPrimaryStorageMsg`、`APIReadVmInstanceMetadataFromPrimaryStorageMsg` |
| **注册操作** | 异步 POST/PUT → Event | `APIRegisterVmInstanceMsg` |
| **运维诊断** | 异步 PUT → Event（仅 CLI） | `APICheckVmInstanceMetadataConsistencyMsg`、`APIUpdateVmMetadataMsg`、`APIPreCheckVmMetadataRegistrationMsg` |

**ZStack REST 惯例**

| HTTP 方法 | 消息基类 | 响应 | 语义 |
|-----------|----------|------|------|
| GET | `APISyncCallMessage` → `APIReply` | 同步返回 | 只读查询 |
| POST | `APIMessage` → `APIEvent` | 异步（轮询/WebSocket） | 创建资源 |
| PUT + `isAction=true` | `APIMessage` → `APIEvent` | 异步 | 非幂等操作/动作 |

所有 API 仅 **admin** 账户可调用。

---

## 2. 获取主存储上的虚拟机元数据列表

列出指定主存储上所有虚拟机元数据文件的概要信息（VM 名称、UUID、文件路径），用于在注册前了解存储上有哪些可恢复的 VM。

### 2.1 请求

```java
@RestRequest(
    path = "/primary-storage/vm-instances/metadata",
    method = HttpMethod.GET,
    responseClass = APIGetVmInstanceMetadataFromPrimaryStorageReply.class
)
public class APIGetVmInstanceMetadataFromPrimaryStorageMsg
        extends APISyncCallMessage implements PrimaryStorageMessage {
    @APIParam(resourceType = PrimaryStorageVO.class)
    private String uuid;  // 主存储 UUID
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `uuid` | String | 是 | 目标主存储 UUID |

### 2.2 响应

```java
@RestResponse(allTo = "all")
public class APIGetVmInstanceMetadataFromPrimaryStorageReply extends APIReply {
    private List<VmInstanceMetadataStruct> vmInstanceMetadatas;
}
```

```java
public class VmInstanceMetadataStruct {
    private String name;    // 虚拟机名称（从元数据文件 DTO 中提取）
    private String uuid;    // 虚拟机 UUID
    private String path;    // 元数据文件路径
}
```

**path 返回值示例**

| 存储类型 | 示例 |
|----------|------|
| sblk | `/dev/{vg_uuid}/{vm_uuid}_vmmeta` |
| local/NFS | `/vms_ds/rootVolumes/acct-{id}/vol-{uuid}/{vm_uuid}_vmmeta` |

### 2.3 设计要点

1. **一次性返回所有**：不分页。元数据文件数量与 VM 数量相当，通常不超过数千个。每个 `VmInstanceMetadataStruct` 仅包含 3 个字符串字段（约 200B），1000 个 VM 的响应 payload 约 200KB，对 HTTP 传输无压力。
2. **不判断元数据好坏**：扫描阶段只列出文件，不读取 Slot payload、不校验 Checksum。元数据是否损坏在注册时才做完整校验。
3. **name/uuid 提取方式**：
   - **sblk**：读取 Header（512B）验证 Magic 有效后，读取 Active Slot 的前 1MB，解析 payload JSON 提取 `vm.vo` 中的 `name` 和 `uuid` 字段。如果 Header 无效或 Slot 损坏，`name` 返回 `null`，`uuid` 从 LV 名称中提取（`{vm_uuid}_vmmeta` → `vm_uuid`）。
   - **local/NFS**：读取 JSON 文件，解析提取 `vm.vo` 中的 `name` 和 `uuid`。如果文件损坏，`name` 返回 `null`，`uuid` 从文件名提取。
4. **同步 API**（`APISyncCallMessage`）：扫描操作为只读、无副作用，结果集有限，适合同步返回。

> **历史说明**：本 API 取代了早期设计的 `APIScanVmMetadataOnPrimaryStorageMsg`。原设计提供分页参数（`start`/`limit`），经评估后改为一次性返回——元数据文件数量有限，分页增加了客户端复杂度但无显著收益。

---

## 3. 获取指定虚拟机元数据详情

从主存储上读取指定 VM 的元数据文件内容并返回，用于运维诊断和灾难恢复前的数据检查。

### 3.1 请求

```java
@RestRequest(
    path = "/primary-storage/{primaryStorageUuid}/vm-instances/{vmUuid}/metadata",
    method = HttpMethod.GET,
    responseClass = APIReadVmInstanceMetadataFromPrimaryStorageReply.class
)
public class APIReadVmInstanceMetadataFromPrimaryStorageMsg
        extends APISyncCallMessage implements PrimaryStorageMessage {
    @APIParam(resourceType = PrimaryStorageVO.class)
    private String primaryStorageUuid;

    @APIParam
    private String vmUuid;  // 目标 VM UUID
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `primaryStorageUuid` | String | 是 | 主存储 UUID |
| `vmUuid` | String | 是 | 虚拟机 UUID（用于定位元数据文件） |

### 3.2 响应

```java
@RestResponse(allTo = "all")
public class APIReadVmInstanceMetadataFromPrimaryStorageReply extends APIReply {
    private String metadata;    // 元数据 DTO JSON 全文（明文，systemTags/resourceConfigs 为 Base64）
}
```

### 3.3 设计要点

1. **返回原始 JSON 全文**：控制面从存储读取后直接返回（调用 `MetadataStorageHandler.readMetadata()`），不做反序列化或结构变换。调用方（CLI/运维工具）可自行解析 JSON、解码 Base64 字段。
2. **替代 zstack-ctl 直接读取**：核心设计（[Part 1](vm-metadata-01-design.md) §3）原提到"VO、快照等主体数据保持明文，zstack-ctl 可直接读取，无需解码"。实际上 sblk 场景下 zstack-ctl 无法直接读取 LV 上的二进制 Slot 数据。本 API 提供统一的读取入口，屏蔽底层存储差异（sblk 二进制协议 vs local/NFS JSON 文件），所有存储类型通过同一 API 获取明文 JSON。
3. **同步 API**：读取单个 VM 元数据（通常 <1MB），延迟在百毫秒级，适合同步返回。
4. **sblk 读取行为**：调用 [Part 4](vm-metadata-04-sblk.md) §6 读取流程（`read_metadata`），如果返回 `NEED_REPAIR` 或 `RECOVERED` 状态，仍返回可用 payload（`is_usable() == True`）。如果 `CORRUPTED` 或 `STORAGE_CHANGE_INCOMPLETE`，返回 `METADATA_CHECKSUM_MISMATCH` 错误。
5. **local/NFS 读取行为**：读取 JSON 文件 + `_checksum` 校验。校验失败返回 `METADATA_CHECKSUM_MISMATCH`。

---

## 4. 注册虚拟机

从主存储上的元数据文件恢复注册虚拟机。异步操作，涉及 DB 写入、变基、校验等多步骤。

### 4.1 请求

```java
@RestRequest(
    path = "/vm-instances/register",
    method = HttpMethod.POST,
    responseClass = APIRegisterVmInstanceEvent.class,
    parameterName = "params"
)
public class APIRegisterVmInstanceMsg extends APIMessage implements PrimaryStorageMessage {
    @APIParam
    private String metadataPath;

    @APIParam(resourceType = PrimaryStorageVO.class)
    private String primaryStorageUuid;

    @APIParam(resourceType = ClusterVO.class)
    private String clusterUuid;

    @APIParam(resourceType = ZoneVO.class)
    private String zoneUuid;

    @APIParam(required = false)
    private boolean forceVersionMismatch = false;

    @APIParam(required = false, resourceType = HostVO.class)
    private String hostUuid;
}
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `metadataPath` | String | 是 | — | 元数据文件路径（来自 §2 API 返回的 `path` 字段） |
| `primaryStorageUuid` | String | 是 | — | 目标主存储 UUID |
| `clusterUuid` | String | 是 | — | 目标集群 UUID |
| `zoneUuid` | String | 是 | — | 目标区域 UUID |
| `forceVersionMismatch` | boolean | 否 | false | 为 true 时忽略 schemaVersion 不匹配强制注册（缺失字段置 null） |
| `hostUuid` | String | 否 | null | 指定注册后首次启动的目标 Host（不指定则由调度器决定） |

### 4.2 响应

```java
@RestResponse(allTo = "inventory")
public class APIRegisterVmInstanceEvent extends APIEvent {
    private VmInstanceInventory inventory;
    private List<String> warnings;   // 注册过程中的非致命警告列表
}
```

### 4.3 设计要点

1. **异步 API**（`APIMessage` + `APIEvent`）：注册涉及读取元数据、校验、DB 多表写入、快照链变基等重操作，耗时可达数分钟，必须异步。
2. **`metadataPath`** 由 §2 API 返回，用户无需手动拼接路径。
3. **`forceVersionMismatch`** 对应 [Part 1](vm-metadata-01-design.md) §6.3 的版本兼容规则：默认 `false` 时 schemaVersion（`dbf.getDbVersion()`）不匹配直接拒绝；`true` 时允许跨版本注册（缺失字段置 null）。
4. **`zoneUuid`** 是必填参数，用于替换元数据中的 `VmInstanceVO.zoneUuid`（跨环境注册时原 Zone 不存在）。
5. **`clusterUuid`** 用于确定可用 Host 范围。注册后 VM 状态为 Stopped，`clusterUuid` 赋值到 `VmInstanceVO.clusterUuid`（有助于首次启动调度）。
6. **`hostUuid`** 可选参数，指定后变基操作在该 Host 上执行，也作为首选启动 Host。不指定时由系统选择 cluster 内可用 Host。
7. **`warnings`** 列表包含非致命提示：schemaVersion 不匹配（force=true 场景）、模板 VM 缓存需首次创建等。
8. **仅 admin 账户**可调用（API 授权层控制）。

**完整注册流程**详见 [Part 3: 注册与运维](vm-metadata-03-registration.md) §3。

---

## 5. 检查虚拟机元数据一致性

对比数据库中的 VM 状态与存储上的元数据文件，检查是否一致。仅 CLI 开放，UI 不展示。

### 5.1 请求

```java
@RestRequest(
    path = "/vm-instances/{uuid}/consistency",
    method = HttpMethod.PUT,
    responseClass = APICheckVmInstanceMetadataConsistencyEvent.class,
    isAction = true
)
public class APICheckVmInstanceMetadataConsistencyMsg
        extends APIMessage implements VmInstanceMessage {
    @APIParam(resourceType = VmInstanceVO.class)
    private String uuid;
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `uuid` | String | 是 | 虚拟机 UUID |

### 5.2 响应

```java
public class APICheckVmInstanceMetadataConsistencyEvent extends APIEvent {
    private boolean consistent;
    private String vmMetadataInDb;       // 从 DB 构建的元数据 JSON（不一致时返回）
    private String vmMetadataInStorage;  // 从存储读取的元数据 JSON（不一致时返回）
}
```

### 5.3 行为说明

| 场景 | consistent | vmMetadataInDb | vmMetadataInStorage |
|------|-----------|----------------|---------------------|
| 一致 | `true` | null | null |
| 不一致 | `false` | DB 侧元数据 JSON 全文 | 存储侧元数据 JSON 全文 |
| 存储上无元数据文件 | `false` | DB 侧元数据 JSON 全文 | null |

### 5.4 设计要点

1. **异步 API**（`APIMessage` + `APIEvent`）：需要从存储异步读取元数据文件。PUT + `isAction=true` 符合 ZStack 非幂等操作的 REST 惯例。
2. **比较逻辑**：从 DB 调用 `buildVmInstanceMetadata()` 生成一份 → 从存储调用 `MetadataStorageHandler.readMetadata()` 读取一份 → 结构化比较。比较时排除 `lastOpDate`、`id`、`managementNodeUuid` 字段（见 [Part 3](vm-metadata-03-registration.md) §7.2）。
3. **一致时不返回 JSON**：节省网络开销，调用方只需判断 `consistent` 即可。
4. **不一致时返回完整 JSON**：调用方可使用外部 diff 工具分析差异。JSON 为明文格式，其中 systemTags/resourceConfigs 字段仍为 Base64 编码，需调用方自行解码查看。
5. **仅 CLI 使用**：该 API 用于运维诊断，不在 UI 中暴露。可配合 `zstack-cli` 使用。

---

## 6. 运维辅助 API（仅 CLI）

### 6.1 手动触发元数据更新

```java
@RestRequest(
    path = "/vm-instances/{uuid}/actions",
    method = HttpMethod.PUT,
    responseClass = APIUpdateVmMetadataEvent.class,
    isAction = true
)
public class APIUpdateVmMetadataMsg extends APIMessage implements VmInstanceMessage {
    @APIParam(resourceType = VmInstanceVO.class)
    private String uuid;
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `uuid` | String | 是 | 虚拟机 UUID |

指定 vmUuid，手动触发一次全量元数据更新。用于达到最大重试次数后的手动恢复，或升级后单独更新指定 VM 的元数据。

详见 [Part 3](vm-metadata-03-registration.md) §7.4。

### 6.2 注册预检查

```java
@RestRequest(
    path = "/vm-instances/metadata/precheck",
    method = HttpMethod.PUT,
    responseClass = APIPreCheckVmMetadataRegistrationEvent.class,
    isAction = true
)
public class APIPreCheckVmMetadataRegistrationMsg extends APIMessage {
    @APIParam
    private String metadataPath;

    @APIParam(resourceType = PrimaryStorageVO.class)
    private String primaryStorageUuid;
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `metadataPath` | String | 是 | 元数据文件路径 |
| `primaryStorageUuid` | String | 是 | 主存储 UUID |

预检查注册条件（UUID 冲突、PS 可达性、版本兼容等），返回检查结果列表。不执行实际注册操作。

**检查项**

| # | 检查项 | 条件 | 失败错误码 | 级别 |
|---|--------|------|-----------|------|
| 1 | VM UUID 唯一性 | `!exists(VmInstanceVO, uuid)` | `REG.UUID_CONFLICT` | ERROR |
| 2 | Volume UUID 唯一性 | `!exists(VolumeVO, uuid) for all` | `REG.UUID_CONFLICT` | ERROR |
| 3 | Snapshot UUID 唯一性 | `!exists(VolumeSnapshotVO, uuid) for all` | `REG.UUID_CONFLICT` | ERROR |
| 4 | PS 可达性 | `PS.status == Connected` | `REG.PS_NOT_AVAILABLE` | ERROR |
| 5 | PS 类型兼容 | `type ∈ {SharedBlock, LocalStorage, NFS}` | `REG.PS_INCOMPATIBLE` | ERROR |
| 6 | 跨存储校验 | 所有 Volume 归属同一目标 PS | `REG.CROSS_STORAGE` | ERROR |
| 7 | 存储路径可映射 | 前缀锚定替换后 Agent 验证路径存在 | `REG.PATH_UNMAPPABLE` | ERROR |
| 8 | Schema 版本兼容 | `≤ MAX_SUPPORTED_SCHEMA_VERSION` | `REG.SCHEMA_INCOMPATIBLE` | ERROR |
| 9 | Host 资源充足 | 至少一台 Host 满足 cpu/memory（考虑 overcommit ratio） | `REG.NO_CAPABLE_HOST` | WARNING |

**辅助检查项**

| 检查项 | 检查方式 | 级别 |
|--------|----------|------|
| 磁盘文件可读性 | Agent `qemu-img info` | WARNING |
| architecture 兼容性 | 检查目标 Zone 集群 | WARNING |
| hypervisorType 兼容性 | 检查目标 Zone hypervisor | WARNING |
| 元数据完整性 | Validator 全量校验 | ERROR |
| imageUuid 存在性 | 查询 ImageVO | WARNING |

> 预检查是"尽力而为"的辅助工具，不作为注册前置依赖。注册流程内部有自己的校验。

详见 [Part 3](vm-metadata-03-registration.md) §7.3。

---

## 7. API 汇总

| API | HTTP | 类型 | 权限 | 场景 |
|-----|------|------|------|------|
| `APIGetVmInstanceMetadataFromPrimaryStorageMsg` | `GET /primary-storage/vm-instances/metadata` | 同步 | admin | 列出存储上所有 VM 元数据概要 |
| `APIReadVmInstanceMetadataFromPrimaryStorageMsg` | `GET /primary-storage/{psUuid}/vm-instances/{vmUuid}/metadata` | 同步 | admin | 读取指定 VM 的完整元数据 JSON |
| `APIRegisterVmInstanceMsg` | `POST /vm-instances/register` | 异步 | admin | 从元数据注册恢复虚拟机 |
| `APICheckVmInstanceMetadataConsistencyMsg` | `PUT /vm-instances/{uuid}/consistency` | 异步 | admin(CLI) | 一致性检查 |
| `APIUpdateVmMetadataMsg` | `PUT /vm-instances/{uuid}/actions` | 异步 | admin(CLI) | 手动触发元数据全量更新 |
| `APIPreCheckVmMetadataRegistrationMsg` | `PUT /vm-instances/metadata/precheck` | 异步 | admin(CLI) | 注册预检查（不执行注册） |

---

## 8. 设计合理性分析

### 8.1 同步/异步选择

| API | 选择 | 理由 |
|-----|------|------|
| 获取元数据列表 | 同步 GET | 只读扫描，结果集有限（<200KB），无副作用 |
| 读取指定 VM 元数据 | 同步 GET | 读取单个文件（<1MB），延迟百毫秒级 |
| 注册虚拟机 | 异步 POST | 多步骤重操作（读取→校验→DB写入→变基），耗时可达数分钟 |
| 一致性检查 | 异步 PUT | 需异步读取存储文件 + 结构化比较 |
| 手动更新元数据 | 异步 PUT | 需序列化→写入存储→校验 |
| 注册预检查 | 异步 PUT | 需访问 Agent 验证路径存在性等 |

### 8.2 不分页的合理性

元数据文件数量与 VM 数量 1:1 对应。单个 `VmInstanceMetadataStruct` 约 200B，1000 VM 的响应 ≈ 200KB，万级 VM 场景 ≈ 2MB，仍在 HTTP 响应合理范围内。分页增加客户端轮询复杂度（需 do-while 循环累加），对产品形态（CLI 列表展示）无额外收益。

### 8.3 一致性检查返回格式

返回 DB 侧和存储侧的完整 JSON 字符串，而非结构化 diff 对象。理由：

- 该 API 面向 CLI 运维，调用方可使用 `jq`、`diff` 等外部工具分析差异
- 结构化 diff 增加 API 复杂度（需定义 DiffEntry 结构、处理嵌套集合差异），但该 API 仅用于诊断，调用频率极低，投入产出比不高
- 一致时不返回 JSON，节省 95%+ 场景的网络开销

### 8.4 新增 Read API 的必要性

核心设计 [Part 1](vm-metadata-01-design.md) §3 原提到"VO、快照等主体数据保持明文，zstack-ctl 可直接读取"。但实际场景中：

- **sblk 存储**：元数据存储在 LV 中的二进制 Slot 格式，zstack-ctl 无法直接读取
- **权限隔离**：zstack-ctl 运行在 MN 节点，不一定有 Host 侧存储的直接访问权限
- **一致性入口**：统一通过 `MetadataStorageHandler` 读取，自动处理 Slot 选择、Checksum 校验、损坏修复

因此新增 `APIReadVmInstanceMetadataFromPrimaryStorageMsg` 作为唯一的元数据读取入口，屏蔽底层存储差异。

### 8.5 现有代码与设计的差距

| 项目 | 现有代码 | 设计要求 | 说明 |
|------|----------|----------|------|
| `APIRegisterVmInstanceMsg` 字段 | `metadataPath`, `primaryStorageUuid`, `clusterUuid`, `hostUuid` | 增加 `zoneUuid`（必填）、`forceVersionMismatch` | 跨环境注册需要 zoneUuid；版本兼容需要 force 参数 |
| `APIRegisterVmInstanceReply` 命名 | 类名为 `Reply` 但 `extends APIEvent` | 建议改为 `APIRegisterVmInstanceEvent` | 符合异步 API → Event 的 ZStack 命名惯例 |
| `APIGetVmInstanceMetadataFromPrimaryStorageReply` 返回类型 | `List<String> vmInstanceMetadata` | `List<VmInstanceMetadataStruct> vmInstanceMetadatas` | 结构化返回，含 name/uuid/path |
| `APIReadVmInstanceMetadataFromPrimaryStorageMsg` | 不存在 | 需新建 | 替代 zstack-ctl 直接读取 |
| `APICheckVmInstanceMetadataConsistencyMsg` | 不存在 | 需新建 | 一致性诊断 |

---

## 9. 错误码

| 错误码 | 含义 | 触发 API |
|--------|------|----------|
| `METADATA_FILE_NOT_FOUND` | 元数据文件不存在 | Read、Register |
| `METADATA_CHECKSUM_MISMATCH` | SHA256 校验失败 | Read、Register、Consistency |
| `METADATA_VERSION_MISMATCH` | schemaVersion 不匹配（force=false） | Register |
| `METADATA_UUID_CONFLICT` | UUID 与现有资源冲突 | Register、PreCheck |
| `METADATA_CROSS_STORAGE` | 磁盘分布在不同主存储 | Register、PreCheck |
| `METADATA_STORAGE_UNREACHABLE` | 存储路径不可达 | 所有 API |
| `METADATA_REBASE_FAILED` | 快照链变基失败 | Register |
| `METADATA_STORAGE_NOT_SUPPORTED` | 存储类型不支持（ceph/zbs/vhost） | 所有 API |
| `METADATA_BASE64_DECODE_FAILED` | Base64 解码失败 | Register |
