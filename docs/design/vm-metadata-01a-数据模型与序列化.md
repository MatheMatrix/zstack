# VM 元数据 — 数据模型与序列化

## 目录

1. [概述](#1-概述)
2. [核心 DTO 结构](#2-核心-dto-结构)
3. [编码策略 — per-Resource 字段级 Base64](#3-编码策略--per-resource-字段级-base64)
4. [序列化关注点](#4-序列化关注点)
5. [反序列化关注点](#5-反序列化关注点)
6. [schemaVersion 版本规则](#6-schemaversion-版本规则)
7. [VmCdRomVO 等附属资源](#7-vmcdromvo-等附属资源)

---

## 1. 概述

虚拟机元数据功能用于将 VM 及其关联资源（云盘、网卡、快照、SystemTag、ResourceConfig）的关键信息持久化到主存储上，以支持跨平台/灾难恢复场景下的虚拟机注册恢复。

### 1.1 适用范围

本功能仅适用于 **`type = "UserVm"`** 的虚拟机实例。ApplianceVm（虚拟路由器、网关等系统 VM）不写入元数据、不支持注册。

`@MetadataImpact` 拦截器和 `buildVmInstanceMetadata()` 中均增加 `vmInstanceType != "UserVm"` 前置检查，不满足时静默跳过。

### 1.2 全局配置

增加全局配置项 `vm.metadata.enabled`（Boolean，**默认为 false**），开启/关闭记录虚拟机元数据。

**理由**：注册虚拟机仅用于有容灾需求的场景。对于 99.9% 普通用户来说不会用到此功能。

#### 1.2.1 开关切换策略

| 切换方向 | 行为 | 说明 |
|----------|------|------|
| **`false → true`** | 触发一次**分批全量 markDirty** 初始化所有 VM 的元数据 | 防止读写风暴，复用 Poller 自动限流 |
| **`true → false`** | **不自动删除**已有元数据文件/LV | 提供 `APICleanupVmInstanceMetadataMsg` 按需清理 |

**`false → true`（启用）详细流程**：

通过 `GlobalConfig.installUpdateExtension` 监听 `vm.metadata.enabled` 变更。检测到从 `false` 变为 `true` 时，提交延迟 30 秒的初始化任务（等待 Poller 启动就绪），执行分批 markDirty：

```java
VmGlobalConfig.VM_METADATA_ENABLED.installUpdateExtension((oldValue, newValue) -> {
    boolean wasEnabled = Boolean.parseBoolean(oldValue);
    boolean nowEnabled = Boolean.parseBoolean(newValue);

    if (!wasEnabled && nowEnabled) {
        // false → true: 分批初始化全量 VM 元数据
        submitBatchInitialization();
    }
    // true → false: 不做任何自动操作
});
```

`submitBatchInitialization()` 逻辑与升级全量刷新（[Part 2b §9.2](vm-metadata-02b-高可用与运维.md#92-刷新执行简化无-longjob)）共用相同的分批 SQL 模式，但使用独立配置项控制批次大小和批间延迟（详见 [Part 2b §9a](vm-metadata-02b-高可用与运维.md#9a-功能开关切换处理)）。

**`true → false`（禁用）详细说明**：

- Poller 的 `markDirty()` 和 `triggerFlushForVm()` 内前置检查 `vm.metadata.enabled`，关闭后自动停止新的标脏和刷写
- 已存在的 dirty 行不主动清理（自然过期或下次启用时重新处理）
- 已写入存储的元数据文件/LV **保留不删除**，避免误操作导致已有元数据丢失
- 运维可通过 `APICleanupVmInstanceMetadataMsg`（见 [Part 5 §6.3](vm-metadata-05-API设计.md#63-清理虚拟机元数据)）按需批量清理指定 PS 或指定 VM 的元数据

### 1.3 安全声明

- USERDATA  和 SystemTag 中可能包含 cloud-init 脚本 或者 password 信息。
- 未来如需加密，可通过 bump HeaderVersion 引入 `EncryptionType` 字段扩展。

### 1.4 命名规范

| 层面 | 前缀 | 示例 |
|------|------|------|
| DTO（对应 VmInstanceVO） | `VmInstanceMetadata*` | `VmInstanceMetadataDTO`、`VmInstanceMetadataCodec` |
| VO / DB 表 / 内部组件 | `VmMetadata*` | `VmMetadataDirtyVO`、`VmMetadataPathFingerprintVO`、`MetadataDirtyPoller` |

### 1.5 构建事务性能监控

`buildVmInstanceMetadata()` 运行在 `REPEATABLE READ` 事务中以保障快照一致性。：

- 处置建议：暂时不用考虑，可预期范围内，一个虚拟机没有那么多盘和快照，查询会很快。且元数据更新保证最终一致性。

---

## 2. 核心 DTO 结构

### 2.1 VmInstanceMetadataDTO

```
VmInstanceMetadataDTO
├── schemaVersion: String                              // 元数据 schema 版本（与 zsv 数据库版本一致）
├── vmCategory: VmMetadataCategory                     // VM 类型（REGULAR / TEMPLATE / TEMPLATE_CACHE）
├── vm: ResourceMetadata                               // 虚拟机自身
├── volumes: List<VolumeResourceMetadata>              // 根盘 + 数据盘（含引用）
├── nics: List<ResourceMetadata>                       // 网卡（仅记录，注册时不恢复）
├── snapshots: List<String>                            // 全部卷的 VolumeSnapshotVO JSON 列表（VM 级别扁平化）
├── snapshotGroups: List<String>                       // List<VolumeSnapshotGroupVO JSON>（VM 级别，横跨多卷）
└── snapshotGroupRefs: List<String>                    // List<VolumeSnapshotGroupRefVO JSON>（VM 级别）
```

**前置约束**：
- VM 必须有 Root Volume。无 Root Volume 的 VM（非法状态）跳过元数据构建，`markDirty()` 时以 WARN 日志记录。
- `volumes` 列表仅包含 `isShareable=false` 的 Volume。共享盘（`isShareable=true`）不纳入元数据，注册时也不恢复。理由：共享盘可能同时挂载在多个 VM 上，跨平台恢复时无法保证共享语义的一致性。
- **共享盘快照排除（讨论澄清）**：构建 `snapshots` 列表时，查询条件应排除共享盘的快照（`WHERE volumeUuid IN (非共享盘 UUID 列表)`）。若 VM 的数据盘中有共享盘，其快照也不纳入元数据。
- **空快照列表防护（讨论澄清）**：若 VM 的所有卷均无快照（`allSnapshots.isEmpty()`），跳过 `VolumeSnapshotTree.fromVOs()` 调用，直接设置 `dto.snapshots = Collections.emptyList()`。避免向空输入传递导致潜在的 NPE。

**确定性排序规则**：DB 数据不变时，多次构建必须产出完全相同的 JSON。所有 List 字段在序列化前按主键升序排列：

| 字段 | 排序键 |
|------|--------|
| `volumes` | `VolumeVO.uuid` |
| `nics` | `VmNicVO.uuid` |
| `snapshots` | **BFS 拓扑排序**（见下方说明） |
| `snapshotGroups` | `VolumeSnapshotGroupVO.uuid` |
| `snapshotGroupRefs` | `volumeSnapshotGroupUuid` 优先，`volumeUuid` 次之（复合键字典序） |
| `VolumeResourceMetadata.snapshotReferences` | `VolumeSnapshotReferenceVO.id` |
| `VolumeResourceMetadata.snapshotReferenceTrees` | `VolumeSnapshotReferenceTreeVO.uuid` |
| `systemTags`（Base64 编码前） | `SystemTagVO.uuid` |
| `resourceConfigs`（Base64 编码前） | `ResourceConfigVO.uuid` |

**snapshots 拓扑排序规则**（保证父快照在子快照之前，支持注册时按序恢复）：

```
1. 按 volumeUuid 分组，每组再按 treeUuid 分组
2. 同一 tree 内：使用已有的 VolumeSnapshotTree.fromVOs() + levelOrderTraversal()
   - 根节点（parentUuid = null）排最前
   - BFS 层序遍历，父先于子
3. 不同 tree 之间按 treeUuid ASC 排列
4. 不同 volume 之间按 volumeUuid ASC 排列
```

保证：**父先于子**（BFS 天然保证）、**确定性**（同层顺序由 `VolumeSnapshotTree` 内部保证）、**稳定性**（纯粹由 uuid + parentUuid 决定）。

**循环引用防护**：`VolumeSnapshotTree.fromVOs()` 内部以 `parentUuid` 构建有向图。若数据库中快照链存在循环引用（如 A→B→C→A，属于数据库层面的非法数据），BFS 遍历不会访问环上节点（无入度为 0 的根节点可达这些节点）。这些"孤立环"节点将不会出现在 `levelOrderTraversal()` 输出中。处理策略：构建完成后比对输出数量与输入数量——若 `result.size() < allSnapshots.size()`，说明存在不可达快照（环或孤立节点），记录 WARN 日志 `"Unreachable snapshots detected for VM {vmUuid}: {count} out of {total}, possible circular reference"` 并将遗漏的快照按 uuid ASC 追加到结果尾部（保证不丢数据，注册时依赖 FK 而非顺序重建关系）。

**排序安全性说明**：BFS 拓扑排序仅改变输出顺序，不改变节点内容。`parentUuid/parentId` 关系通过快照 VO 字段完整保留，注册恢复时不会发生层级信息丢失。

> **复用已有基础设施**：`VolumeSnapshotTree`（[VolumeSnapshotTree.java](../header/src/main/java/org/zstack/header/storage/snapshot/VolumeSnapshotTree.java)）已有完整的树构建和 BFS 层序遍历实现，无需重新实现。

```java
// 实际实现：复用 VolumeSnapshotTree
List<VolumeSnapshotVO> topoSort(List<VolumeSnapshotVO> allSnapshots) {
    // 先按 volumeUuid 分组，再按 treeUuid 分组（双层 TreeMap 保证 ASC 排序）
    Map<String, Map<String, List<VolumeSnapshotVO>>> byVolumeThenTree =
        allSnapshots.stream().collect(Collectors.groupingBy(
            VolumeSnapshotVO::getVolumeUuid, TreeMap::new,
            Collectors.groupingBy(VolumeSnapshotVO::getTreeUuid,
                TreeMap::new, Collectors.toList())));

    List<VolumeSnapshotVO> result = new ArrayList<>();
    for (Map<String, List<VolumeSnapshotVO>> treesInVolume : byVolumeThenTree.values()) {
        for (List<VolumeSnapshotVO> treeSnapshots : treesInVolume.values()) {
            VolumeSnapshotTree tree = VolumeSnapshotTree.fromVOs(treeSnapshots);
            List<VolumeSnapshotInventory> ordered = tree.levelOrderTraversal();
            for (VolumeSnapshotInventory inv : ordered) {
                result.add(findByUuid(treeSnapshots, inv.getUuid()));
            }
        }
    }
    return result;
}
```

### 2.2 VmMetadataCategory 枚举

> **权威定义**：此枚举同时适用于 Java DTO 和 sblk Header VM 摘要区。Part 4b Header 中 `VmCategory` 字段的取值与此枚举一一对应。

```java
public enum VmMetadataCategory {
    REGULAR,         // 0 — 普通虚拟机（含链式克隆子 VM）
    TEMPLATE,        // 1 — 模板虚拟机（TemplatedVmInstanceVO 存在）
    TEMPLATE_CACHE   // 2 — 模板缓存虚拟机（TemplatedVmInstanceCacheVO 中的 cacheVmInstanceUuid）
}
```

| 类型 | 判定条件 | 写入元数据 | 注册行为 |
|------|----------|:---:|----------|
| `REGULAR` | 非模板、非缓存的所有 VM | (Y) | 正常注册 |
| `TEMPLATE` | `TemplatedVmInstanceVO` 存在 | (Y) | 注册为普通 VM（不恢复模板身份） |
| `TEMPLATE_CACHE` | `TemplatedVmInstanceCacheVO.cacheVmInstanceUuid` 匹配 | (Y) | **拒绝注册**（返回 `METADATA_CACHE_VM_NOT_REGISTERABLE`） |

**扩展约束**：枚举在存储/传输层按 **int** 语义处理，当前占用值 `0~2`，预留 `3~99` 给未来类别扩展，避免与历史版本冲突。

**向后兼容策略**：新增枚举值时（如 v2+ 加入 `APPLIANCE = 3`），旧版本 Agent 读取到未知 int 值应按 `REGULAR` 降级处理（安全默认值）。Java 端 Gson 反序列化遇到未知枚举值返回 null，代码中对 `vmCategory == null` 已统一视为 `REGULAR`。因此无需额外版本协商，仅需在新增枚举时更新此注释标注已占用值。

**构建时判定逻辑**（先判缓存再判模板）：

```java
if (Q.New(TemplatedVmInstanceCacheVO.class)
        .eq(TemplatedVmInstanceCacheVO_.cacheVmInstanceUuid, vmUuid)
        .isExists()) {
    dto.vmCategory = VmMetadataCategory.TEMPLATE_CACHE;
} else if (Q.New(TemplatedVmInstanceVO.class)
        .eq(TemplatedVmInstanceVO_.uuid, vmUuid)
        .isExists()) {
    dto.vmCategory = VmMetadataCategory.TEMPLATE;
} else {
    dto.vmCategory = VmMetadataCategory.REGULAR;
}
```

### 2.3 ResourceMetadata

```
ResourceMetadata
├── resourceUuid: String           // 资源 UUID（冗余，必须与 vo 内部 uuid 一致）
├── vo: String                     // VO 全量 JSON 明文
├── systemTags: String             // 白名单过滤后的 SystemTagVO JSON 列表 Base64 编码
└── resourceConfigs: String        // 白名单过滤后的 ResourceConfigVO JSON 列表 Base64 编码
```

### 2.4 VolumeResourceMetadata

`VolumeResourceMetadata` 继承 `ResourceMetadata`，额外携带该卷的引用数据：

```
VolumeResourceMetadata extends ResourceMetadata
├── (inherited: resourceUuid, vo, systemTags, resourceConfigs)
├── snapshotReferences: List<String>           // 该卷的 VolumeSnapshotReferenceVO JSON 列表
└── snapshotReferenceTrees: List<String>       // 该卷关联的 VolumeSnapshotReferenceTreeVO JSON 列表
```

> **快照数据归属**：快照（`VolumeSnapshotVO`）提升到 `VmInstanceMetadataDTO.snapshots` 扁平列表，与 `snapshotGroups`/`snapshotGroupRefs` 同级。
> 引用数据（`snapshotReferences`/`snapshotReferenceTrees`）仍保留在 `VolumeResourceMetadata` 内，因为引用关系与具体卷紧密绑定。
> 注册时 ReferenceVO 的全局拓扑排序（按 parentId 依赖）从各 volume 收集后统一处理。

**快照提升到 DTO 层的设计理由**：

| # | 好处 | 详细说明 |
|---|------|----------|
| 1 | **注册恢复零额外操作** | 注册时先 persist 所有 VolumeVO → 再遍历 `snapshots` 列表逐条 persist VolumeSnapshotVO，`volumeUuid` FK 天然满足。若放在 Volume 层，需先解包每个 Volume 的 snapshots 再逐卷恢复，多一层循环嵌套。 |
| 2 | **与 snapshotGroups 同构** | `snapshotGroups`（VolumeSnapshotGroupVO）是天然 VM 级别概念（一个 group 横跨多卷）。snapshots 放同级后，三个快照相关列表在同一层级统一管理，结构对称。 |
| 3 | **全局拓扑排序一次完成** | 快照链变基（sblk rebase）需要全局拓扑顺序。扁平列表直接做一次 BFS 拓扑排序即可，无需先合并再排序。 |
| 4 | **一致性检查简化** | 对比 DB 与存储上的快照时，直接比对两个扁平列表。无需逐 Volume 打开再逐一比对。 |
| 5 | **Payload 构建效率** | Builder 一次 `SELECT * FROM VolumeSnapshotVO WHERE volumeUuid IN (...)` 查出所有快照，序列化为一个列表。 |
| 6 | **VolumeResourceMetadata 保持精简** | Volume 层只保留与本卷强绑定的数据（ReferenceVO/ReferenceTreeVO）。快照通过 `volumeUuid` 字段自带归属，无需冗余嵌套。 |

> **代价**：失去 Volume→Snapshot 的直观嵌套结构。但每条 `VolumeSnapshotVO` 自带 `volumeUuid`，注册时 `groupBy(volumeUuid)` 即可按卷分组，O(N) 遍历，可忽略。

| VO | 含义 | 注册意义 |
|----|------|----------|
| `VolumeSnapshotReferenceVO` | 记录链式克隆时子 VM 卷对缓存 VM 快照的依赖关系 | 不恢复会导致快照引用计数为 0，子 VM 执行 flatten/删除时无法正确清理物理快照文件 |
| `VolumeSnapshotReferenceTreeVO` | 引用树根节点，记录底层快照链的根信息 | 完全独立表（零 FK 约束），维护引用链路的树形结构 |

**VolumeSnapshotReferenceVO 查询范围说明（讨论澄清）**：构建 `VolumeResourceMetadata.snapshotReferences` 时，查询条件为 `WHERE referenceVolumeUuid = 当前 VM 的卷 UUID`（即子 VM 自身的卷 UUID），而非 `volumeUuid`（缓存 VM 的卷）。原因：一个 VM 只关心自己的引用记录，不需要包含其他 VM 对同一缓存快照的引用。`referenceVolumeUuid` 是 FK → `VolumeEO`，指向子 VM 的卷。

**关键 FK 约束**（基于 `V4.7.0__schema.sql` DDL）：

| 字段 | FK 目标 | ON DELETE | 含义 |
|------|---------|-----------|------|
| `ReferenceVO.referenceVolumeUuid` | `VolumeEO` | CASCADE | 子 VM 卷删除时级联删除引用记录 |
| `ReferenceVO.parentId` | 自身 `id` | SET NULL | 父引用删除后置 NULL |
| `ReferenceVO.treeUuid` | `ReferenceTreeVO` | SET NULL（DDL 实际值） | 树删除后置 NULL |
| `ReferenceVO.volumeUuid` | — | **无 FK** | 允许指向已删除的缓存 VM 卷 |
| `ReferenceVO.volumeSnapshotUuid` | — | **无 FK** | 允许指向已删除的缓存 VM 快照 |
| `ReferenceTreeVO.*` | — | **零 FK** | 完全独立表，所有字段均无外键约束 |

---

## 3. 编码策略 — per-Resource 字段级 Base64

**设计决策**：DTO 整体为明文 JSON 写入存储介质，**不对整体 JSON 做 Base64 编码**。仅对每个 `ResourceMetadata` 中的 `systemTags` 和 `resourceConfigs` 字段采用 **per-Resource 整体 Base64 编码**。

- **sblk**：Slot Payload = DTO JSON 明文
- **local/NFS**：文件内容 = DTO JSON 明文

**理由**：

1. 避免整体 Base64 带来的 4/3 空间膨胀
2. SystemTag/ResourceConfig 可能含特殊字符，Base64 避免 JSON 转义问题
3. 主体数据保持明文，`APIReadVmInstanceMetadataFromPrimaryStorageMsg` 可直接读取完整 JSON
4. 一致性检查 API 可直接对主体数据做结构化比较
5. per-Resource 整体编码减少 Base64 header 开销和解码次数

**容量说明**：Base64 对原始数据体积膨胀约 **33%**。当前方案仅对 `systemTags/resourceConfigs` 做字段级编码，整体可控。

**编解码流程**：

```
写入：
  对每个 ResourceMetadata:
    filteredTags    = 按白名单过滤 List<SystemTagVO>
    filteredConfigs = 按白名单过滤 List<ResourceConfigVO>
    systemTags      = Base64( JSON.toJsonString( sorted(filteredTags, by uuid) ) )
    resourceConfigs = Base64( JSON.toJsonString( sorted(filteredConfigs, by uuid) ) )
  DTO → JSON → 写入存储

读取：
  存储 → JSON → DTO
  对每个 ResourceMetadata:
    List<SystemTagVO>     = JSON.parseArray( Base64Decode(systemTags) )
    List<ResourceConfigVO> = JSON.parseArray( Base64Decode(resourceConfigs) )
```

---

## 4. 序列化关注点

| 关注点 | 方案 |
|--------|------|
| 嵌套 JSON 转义 | 保持 String 类型，Gson 自动处理双重转义/反转义 |
| JSON 字段顺序一致性 | 所有 DTO 字段使用 `@SerializedName` 注解显式命名并按声明顺序输出。**设计决策理由（讨论 Δ-序列化）**：纯依赖 Java 字段声明顺序在重构时有序变风险，`@SerializedName` 固化字段名使 JSON key 不受 Java 重命名影响，同时 Gson 按声明顺序输出已满足确定性要求，无需额外 `@Order` 注解 |
| null 字段处理 | Gson 默认跳过 null 字段（`new Gson()` 不输出 null），反序列化时 Java 默认值与 null 语义一致。**设计决策理由（讨论 Δ-null）**：DTO 中值为 null 的字段在序列化后的 JSON 中不存在对应 key，反序列化时 Java 字段保持声明默认值（引用类型为 null，基本类型为 0/false），语义等价，无需特殊处理 |
| SystemTag/ResourceConfig 写入策略 | 构建时按白名单过滤（见 §4.1），仅写入注册时需要恢复的 tag/config |
| 元数据大小 | 极端场景(24盘×256快照)约 5-10MB，仅 SystemTag/ResourceConfig 字段 Base64 编码，整体膨胀可忽略，在 sblk 单 Slot 32MB 内 |
| 不压缩的理由 | 正常场景 <100KB，极端场景罕见；压缩会增加 Agent 依赖和调试复杂度；未来可通过 bump HeaderVersion 引入 CompressionType 字段支持 |
| VO JSON 字段范围 | 包含所有非 `@Transient` 持久化字段；注册时 `id`（自增主键）由 DB 重新生成，`createDate` 保留原值，`lastOpDate` 替换为注册时间 |

### 4.1 SystemTag/ResourceConfig 构建时过滤规则

**构建时：白名单过滤**

序列化时按白名单过滤，仅将影响 VM 注册恢复的 SystemTag 和 ResourceConfig 写入元数据。

1. 白名单复用已有的 `CoreMemorySnapshotConfigs`（内存快照恢复功能维护的白名单，见下方），影响 VM XML 的 tag 和 config
2. 初始 SystemTag 白名单：`USERDATA`、`SSHKEY`、`BOOT_MODE`、`HOSTNAME`、`CPU_CORES`、`MACHINE_TYPE`、`VIRTIO` 等
3. 初始 ResourceConfig 白名单：`NESTED_VIRTUALIZATION`、`VM_CPU_QUOTA`、`VM_CLOCK_TRACK`、`LIBVIRT_CACHE_MODE` 等
4. 可通过 `@NeedRestoreOnVmApplySnapshot` 注解自动扩展白名单
4. 未命中白名单的 SystemTag/ResourceConfig 不写入元数据

**注册时：直接恢复，无需二次过滤**

> **设计决策**：元数据中的 SystemTag 和 ResourceConfig 已在构建时经过白名单过滤，注册恢复时直接持久化到 DB，**不再执行二次过滤**。

**理由**：
1. 构建时已过滤 → 元数据中只包含影响 VM XML 的 tag/config
2. 二次过滤无意义——白名单是同一套规则
3. 升级后白名单扩展时，已有 full-refresh 覆盖全量元数据

**白名单定义**（复用已有的 `CoreMemorySnapshotConfigs`，无需重复维护）：

> 内存快照恢复功能已有完整的 SystemTag / ResourceConfig 白名单定义（见 `CoreMemorySnapshotConfigs.java`），元数据功能直接复用，保证两个场景的白名单始终一致。

```java
// 元数据构建时直接使用 CoreMemorySnapshotConfigs 的白名单过滤：
// SystemTag 过滤：
CoreMemorySnapshotConfigs.restoreCandidatePatternedSystemTags  // PatternedSystemTag 列表
CoreMemorySnapshotConfigs.restoreCandidateSystemTags           // SystemTag 列表

// ResourceConfig 过滤——按资源类型分组：
CoreMemorySnapshotConfigs.vmRestoreCandidateConfigs            // VM 级别的 GlobalConfig
CoreMemorySnapshotConfigs.volumeRestoreCandidateConfigs        // Volume 级别的 GlobalConfig
CoreMemorySnapshotConfigs.vmNicRestoreCandidateConfigs         // VmNic 级别的 GlobalConfig

// 新增白名单条目两种方式：
// 1. 在 CoreMemorySnapshotConfigs 静态列表中直接添加
// 2. 在 GlobalConfig 字段上标注 @NeedRestoreOnVmApplySnapshot 注解（自动收集）
```

> 新增白名单条目 → 修改 `CoreMemorySnapshotConfigs` 或添加 `@NeedRestoreOnVmApplySnapshot` 注解。内存快照恢复和元数据注册两个场景同步受益。
> CI 可维护性保证：`MetadataWhitelistChecker`（统一 CI 检查）见 [Part 1b §3](vm-metadata-01b-API拦截与VM解析.md#3-统一-ci-检查--metadatawhitelistchecker)。

**演进说明（讨论澄清）**：`CoreMemorySnapshotConfigs` 当前命名绑定内存快照恢复场景。随着元数据功能上线，建议后续将其重构为更通用的命名（如 `VmConfigRestoreCandidates` 或 「影响虚拟机 XML 的配置」），统一表达「影响 VM 运行时配置、需要在恢复场景中还原的 Tag/Config 列表」语义。重构仅涉及类名和引用点，不改变白名单内容和收集逻辑。

**演进备注**：`buildVmInstanceMetadata()` 当前查询源列表为显式实现，v2+ 可提取为 SPI（如 `VmMetadataBuildSource`）以支持插件化扩展额外 VO/资源采集源。

---

## 5. 反序列化关注点

| 关注点 | 方案 |
|--------|------|
| 二步反序列化 | 先反序列化 DTO，再反序列化各 ResourceMetadata.vo 为具体 VO 类 |
| resourceUuid 一致性校验 | 反序列化后校验 `resourceMetadata.resourceUuid == parsedVO.getUuid()` |
| VO 字段版本兼容（多字段） | Gson 忽略缺失字段，填充 Java 默认值 |
| VO 字段版本兼容（少字段） | Gson 忽略未知字段 |
| Base64 解码失败 | systemTags/resourceConfigs 元素 Base64 解码失败时直接报错拒绝注册，不做部分恢复 |

---

## 6. schemaVersion 版本规则

### 6.1 版本号定义

`schemaVersion` 使用 ZStack 数据库版本号，即 **`dbf.getDbVersion()`** 的返回值，与数据库 schema 版本完全一致。

**版本号格式说明（讨论澄清）**：`dbf.getDbVersion()` 返回纯数字版本号（如 `"4.7.0"`），不含 `V` 前缀或 `__schema` 等后缀。该值直接来源于 `DatabaseFacade` 的版本查询，调用方无需额外清洗或裁剪。

### 6.2 比较规则

> **统一规则**：注册 API 和预检查 API 均使用精确匹配：`metadata.schemaVersion == dbf.getDbVersion()`。

| 场景 | 行为 |
|------|------|
| `metadata.schemaVersion == dbf.getDbVersion()` | 匹配，正常注册 |
| 不匹配 + `forceVersionMismatch=false` | 拒绝（`METADATA_SCHEMA_VERSION_MISMATCH`） |
| 不匹配 + `forceVersionMismatch=true` | 允许注册，缺失字段置 null，warnings 记录 |

**`forceVersionMismatch=true` 行为精确定义（讨论澄清）**：该标志**仅跳过 schemaVersion 精确匹配检查**，所有其他校验（UUID 冲突、installPath 存在性、readStatus 可用性、跨存储、vmCategory 类型等）均正常执行，不受此标志影响。缺失字段由 Gson 反序列化自动填充 Java 默认值（null/0/false），多余字段由 Gson 自动忽略。

### 6.3 版本生命周期

- 序列化时由 `VmMetadataBuilder` 自动填充 `dbf.getDbVersion()`
- 升级后通过全量刷新（批量 `markDirty`，Poller 自动处理）将所有 VM 元数据更新到新版本
- 刷写端不依赖旧版本元数据，从 DB 直接构建新版本元数据覆盖写入

### 6.4 升级时间窗口

- 升级后批量 `markDirty` 所有已启用元数据的 VM，Poller 自动分批处理（详见 [Part 2b §9](vm-metadata-02b-高可用与运维.md#9-升级后全量刷新)）
- 10 万 VM × 平均 50ms/VM ≈ 5000 秒 ≈ 83 分钟
- 窗口期内若需注册 VM，可使用 `APIUpdateVmMetadataMsg` 单独更新指定 VM 的元数据

### 6.5 vmCategory 兼容性

`vmCategory` 是新增字段，需要 bump `schemaVersion`：
- **新版本写入的元数据**：包含 `vmCategory` 字段
- **旧版本写入的元数据**：不含该字段，Gson 反序列化时 `vmCategory` 为 `null`
- **注册时处理**：`vmCategory == null` 视为 `REGULAR`（向后兼容）

---

## 7. VmCdRomVO 等附属资源

当前版本不纳入元数据。理由：

- CD-ROM 挂载状态通常不影响 VM 恢复启动
- USB/PCI 透传设备与宿主机绑定，跨环境无意义
- 后续版本如需支持，通过 `VmInstanceMetadataDTO` 新增字段 + bump schemaVersion
