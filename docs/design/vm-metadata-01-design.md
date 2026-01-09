> **虚拟机元数据设计文档** | **核心设计** | [GC 与消息流](vm-metadata-02-gc.md) | [注册与运维](vm-metadata-03-registration.md) | [sblk 二进制协议](vm-metadata-04-sblk.md) | [API 设计](vm-metadata-05-api.md)

# 虚拟机元数据设计文档 —— Part 1: 核心设计

## 1. 概述

虚拟机元数据功能用于将 VM 及其关联资源（云盘、网卡、快照、SystemTag、ResourceConfig）的关键信息持久化到主存储上，以支持跨平台/灾难恢复场景下的虚拟机注册恢复。

### 1.1 全局配置

增加全局配置项 `vm.metadata.enabled`（Boolean，**默认为 false**），开启/关闭记录虚拟机元数据。

**理由**：注册虚拟机仅用于有容灾需求的场景。对于 99.9% 普通用户来说不会用到此功能。

---

## 2. 核心 DTO 结构

### 2.1 VmInstanceMetadataDTO

```
VmInstanceMetadataDTO
├── schemaVersion: String                              // 元数据 schema 版本（与 zsv 数据库版本一致）
├── vm: ResourceMetadata                               // 虚拟机自身
├── volumes: List<ResourceMetadata>                    // 根盘 + 数据盘
├── nics: List<ResourceMetadata>                       // 网卡（仅记录，注册时不恢复）
├── snapshots: Map<String, List<String>>               // volumeUuid → List<VolumeSnapshotVO JSON>
├── snapshotGroups: List<String>                       // List<VolumeSnapshotGroupVO JSON>
├── snapshotGroupRefs: List<String>                    // List<VolumeSnapshotGroupRefVO JSON>
├── snapshotReferences: Map<String, List<String>>      // volumeUuid → List<VolumeSnapshotReferenceVO JSON>
├── snapshotReferenceTrees: Map<String, List<String>>  // volumeUuid → List<VolumeSnapshotReferenceTreeVO JSON>
└── isTemplated: boolean                               // 是否为模板虚拟机（默认 false）
```

### 2.2 ResourceMetadata

```
ResourceMetadata
├── resourceUuid: String           // 资源 UUID（冗余，必须与 vo 内部 uuid 一致）
├── vo: String                     // VO 全量 JSON 明文
├── systemTags: String             // 该资源所有 SystemTagVO JSON 列表的整体 Base64 编码
└── resourceConfigs: String        // 该资源所有 ResourceConfigVO JSON 列表的整体 Base64 编码
```

### 2.3 快照引用数据结构

`snapshotReferences` 和 `snapshotReferenceTrees` 使用 `Map<String, List<String>>` 而非 `Map<String, String>`，原因是同一 volumeUuid 可能对应多条引用记录。

| VO | 含义 | 注册意义 |
|----|------|----------|
| `VolumeSnapshotReferenceVO` | 记录快照的引用关系（如模板创建、linked clone 时快照被引用） | 不恢复会导致快照引用计数为 0，可能被 GC 误删 |
| `VolumeSnapshotReferenceTreeVO` | 记录引用树结构，维护引用链路 | 维护引用层级关系，防止链路断裂 |

---

## 3. 编码策略 — per-Resource 字段级 Base64

**设计决策**：DTO 整体为明文 JSON 写入存储介质，**不对整体 JSON 做 Base64 编码**。对每个 `ResourceMetadata` 中的 `systemTags` 和 `resourceConfigs` 字段采用 **per-Resource 整体 Base64 编码**：将该资源的所有 SystemTagVO JSON 组成列表后做一次 Base64；ResourceConfig 同理。

- **sblk**：Slot Payload = DTO JSON 明文（其中每个资源的 systemTags 是一个 Base64 字符串，resourceConfigs 同理）
- **local/NFS**：文件内容 = DTO JSON 明文（编码方式同上）

**理由**：

1. 避免对整体 JSON 做 Base64 带来的 **4/3 空间膨胀**（10MB 元数据膨胀为 ~13.3MB）
2. SystemTag 和 ResourceConfig 的 JSON 内容可能包含特殊字符（多层嵌套、用户自定义内容），对其做 Base64 可避免 JSON 转义问题
3. VO、快照等主体数据保持明文，可通过 `APIReadVmInstanceMetadataFromPrimaryStorageMsg`（§12.2）读取完整元数据 JSON，屏蔽底层存储差异（sblk 二进制协议 vs local/NFS 文件）
4. 一致性检查 API 可直接对主体数据做结构化比较
5. per-Resource 整体编码（而非逐元素编码）减少 Base64 header 开销和解码次数

**编解码流程**：

```
写入：
  对每个 ResourceMetadata:
    systemTags     = Base64( JSON.toJsonString( List<SystemTagVO> ) )
    resourceConfigs = Base64( JSON.toJsonString( List<ResourceConfigVO> ) )
  DTO → JSON → 写入存储

读取：
  存储 → JSON → DTO
  对每个 ResourceMetadata:
    List<SystemTagVO>     = JSON.parseArray( Base64Decode(systemTags) )
    List<ResourceConfigVO> = JSON.parseArray( Base64Decode(resourceConfigs) )
```

**Checksum 不放在 DTO 内部**：

- sblk：Slot 结构自带 Checksum 字段
- local/NFS：tmp + fsync + rename 原子写入 + `_checksum` 字段保证完整性

---

## 4. 序列化关注点

| 关注点 | 方案 |
|--------|------|
| 嵌套 JSON 转义 | 保持 String 类型，Gson 自动处理双重转义/反转义 |
| JSON 字段顺序一致性 | 使用统一的 `JSONObjectUtil`(Gson)，字段顺序由声明顺序决定 |
| null 字段处理 | Gson 默认跳过 null 字段，反序列化时 Java 默认值与 null 语义一致 |
| SystemTag/ResourceConfig 过滤 | 序列化前过滤，只保留影响虚拟机 xml 的 tag/config（见 §4.1） |
| 元数据大小 | 极端场景(24盘×256快照)约 5-10MB，仅 SystemTag/ResourceConfig 字段 Base64 编码，整体膨胀可忽略（主体数据无编码开销），在 sblk 单 Slot 32MB 内 |
| 不压缩的理由 | 正常场景 <100KB，极端场景罕见；压缩会增加 Agent 依赖和调试复杂度；未来可通过 bump HeaderVersion 引入 CompressionType 字段支持 |

### 4.1 SystemTag/ResourceConfig 过滤规则

过滤采用**白名单注册机制**：

1. 新增 `MetadataRelevantTagRegistry` 接口，各插件通过 `@Component` 注册影响 VM XML 的 tag category/key
2. 编译期 `CheckerCase` 校验新增 SystemTag 定义是否在 Registry 中声明
3. 初始白名单：`bootOrder`、`sshKey`、`hostname`、`bootMode`、`qga`、`cdrom`、`nicDriver`、`cpuModel`、`machineType`、`usbDevice` 等
4. ResourceConfig 同理，只保留 `vm.` 和 `volume.` 前缀的配置项

**可维护性保证**：新增 SystemTag/ResourceConfig/API 时，若未在白名单中声明，CI 报错，强制开发者判断是否纳入元数据。

**统一 CI 检查机制 — `MetadataWhitelistChecker`**：

将 API 标注检查、SystemTag 白名单检查、ResourceConfig 前缀检查合并为单一 CheckerCase：

```java
public class MetadataWhitelistChecker extends PostBuildCheckerCase {
    @Override
    public void check() {
        // Part 1: API @MetadataImpact 检查
        Set<Class<?>> allApiMsgs = BeanUtils.reflections
            .getSubTypesOf(APIMessage.class);
        for (Class<?> msgClass : allApiMsgs) {
            if (isQueryOrGetApi(msgClass)) continue;
            assertMetadataImpactPresent(msgClass);
        }

        // Part 2: SystemTag 白名单检查
        // 扫描所有 SystemTag.define() 调用，要求每个 tag 被某个
        // MetadataWhitelistProvider 声明为 relevant 或 irrelevant
        Set<String> allDefinedTags = scanAllSystemTagDefinitions();
        Set<String> registeredTags = collectFromProviders();
        for (String tag : allDefinedTags) {
            if (!registeredTags.contains(tag)) {
                fail("SystemTag '" + tag + "' not declared in MetadataWhitelistProvider");
            }
        }

        // Part 3: ResourceConfig 前缀检查
        // 所有 ResourceConfig category 必须在 Provider 中声明是否纳入
        Set<String> allConfigCategories = scanAllResourceConfigCategories();
        Set<String> registeredCategories = collectCategoriesFromProviders();
        for (String cat : allConfigCategories) {
            if (!registeredCategories.contains(cat)) {
                fail("ResourceConfig category '" + cat + "' not declared");
            }
        }
    }
}
```

**MetadataWhitelistProvider 接口**（统一 SystemTag + ResourceConfig + API 白名单注册）：

```java
public interface MetadataWhitelistProvider {
    /** 此模块需要纳入 VM 元数据的 SystemTag 模式列表 */
    List<MetadataTagPattern> getRelevantTagPatterns();

    /** 此模块需要纳入 VM 元数据的 ResourceConfig category 前缀列表 */
    List<String> getRelevantResourceConfigCategories();
}
```

各插件通过 `@Component` 实现 `MetadataWhitelistProvider` 注册自己的 SystemTag 和 ResourceConfig。

---

## 5. 反序列化关注点

| 关注点 | 方案 |
|--------|------|
| 二步反序列化 | 先反序列化 DTO，再反序列化各 ResourceMetadata.vo 为具体 VO 类 |
| resourceUuid 一致性校验 | 反序列化后校验 `resourceMetadata.resourceUuid == parsedVO.getUuid()` |
| VO 字段版本兼容（多字段） | Gson 忽略缺失字段，填充 Java 默认值 |
| VO 字段版本兼容（少字段） | Gson 忽略未知字段 |
| Base64 解码失败 | systemTags/resourceConfigs 元素 Base64 解码失败时直接报错拒绝注册，不做部分恢复 |
| VO JSON 字段范围 | 包含所有非 `@Transient` 持久化字段；注册时 `id`（自增主键）由 DB 重新生成，`createDate` 保留原值，`lastOpDate` 替换为注册时间 |

---

## 6. schemaVersion 版本规则

### 6.1 版本号定义

`schemaVersion` 使用 ZStack 产品的 **`MAJOR.MINOR`** 版本号（如 `"4.10"`），**不包含** patch 号。

**理由**：patch 版本通常不涉及 VO 字段变更，使用 MAJOR.MINOR 避免不必要的版本不匹配。

**比较规则**：注册时要求 `metadata.schemaVersion == Platform.getMajorMinorVersion()`，不匹配则拒绝。

### 6.2 版本生命周期

- 序列化时由 `VmMetadataBuilder` 自动填充 `Platform.getMajorMinorVersion()`
- 注册时若 schemaVersion 与当前平台版本不匹配，拒绝注册
- 升级后通过全量更新 GC 将所有 VM 的元数据更新到新版本
- 读取端（全量更新 GC）不依赖旧版本元数据，从 DB 直接构建新版本元数据覆盖写入

### 6.3 版本兼容规则

**核心规则**：元数据版本不兼容时，默认拒绝注册。仅当注册 API 显式指定 `force=true` 时允许跨版本注册（缺失字段置为 null，warnings 中记录）。

- 同 MAJOR 版本内的 MINOR 版本差异：默认拒绝，`force=true` 允许
- 跨 MAJOR 版本：直接拒绝，不支持 `force=true`
- 低于最小兼容版本的元数据直接拒绝注册和迁移
- 每个 MINOR 版本发布时更新 `MIN_COMPATIBLE_SCHEMA_VERSION` 常量

### 6.4 升级时间窗口

- 全量 GC 使用后台 `LongJob` 执行，限制并发（10 VM/批）
- 10 万 VM × 平均 50ms/VM ≈ 5000 秒 ≈ 83 分钟
- 窗口期内若需注册 VM，可使用 `APIUpgradeVmMetadataOnStorageMsg` 单独升级指定 VM 的元数据

### 6.5 版本迁移链

每个大版本提供 `MetadataMigrator_vX_to_vY`：

- 逐版本迁移，每个 Migrator 处理具体字段转换（重命名/语义变化）
- 低于 `MIN_COMPATIBLE_SCHEMA_VERSION` 的元数据直接拒绝注册和迁移
- 原环境不可用时提供独立升级 API/命令：读取旧元数据 → 反序列化（Gson 容忍缺失/多余字段）→ 当前版本重新序列化 → 写回

---

## 7. @MetadataImpact 注解与 API 拦截

### 7.1 注解定义

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface MetadataImpact {
    MetadataImpactLevel value() default MetadataImpactLevel.CONFIG;
    Class<? extends VmUuidFromApiResolver> resolver() default DirectVmUuidResolver.class;
    boolean updateOnFailure() default false;
}

public enum MetadataImpactLevel {
    NONE,     // 无关虚拟机配置
    CONFIG,   // 虚拟机普通配置更新
    STORAGE   // 虚拟机存储结构更新（快照/存储迁移等）
}
```

### 7.2 拦截范围澄清（重要）

**拦截器不是对所有 API 生效**。正确逻辑：

1. `ApiMetadataBehaviorsCheckerCase` 扫描所有 `APIMessage` 子类
2. **只有显式标注了 `@MetadataImpact` 且 level ≠ `NONE` 的 API** 才触发元数据更新
3. **未标注 `@MetadataImpact` 的 API 不触发任何操作**
4. `CheckerCase` 在 CI 编译阶段会报错，要求开发者对每个新增 API **显式声明 `@MetadataImpact`**
5. opt-out 的含义是：**必须标注，不标注 CI 报错**。`CONFIG` 是默认标注值（减少显式声明的工作量），不是"未标注时默认触发"

### 7.3 STORAGE 标记的特殊处理

`@MetadataImpact(STORAGE)` 类 API 在设计文档中标识该操作涉及存储拓扑变更。

> **注意**：op_type（CONFIG_UPDATE / STORAGE_CHANGE）不再由管理层面指定，而是由 Host Agent 端在写入时通过对比新旧 payload 的存储拓扑差异动态决定。详见 [Part 4 §5.2](vm-metadata-04-sblk.md)。
>
> `@MetadataImpact(STORAGE)` 注解仍保留，用于：
> - CI 编译期校验：标识哪些 API 涉及存储变更
> - 文档和代码可读性：开发者一目了然地知道该 API 涉及存储拓扑

### 7.4 updateOnFailure 触发条件

| 条件 | 是否触发 |
|------|----------|
| `updateOnFailure=true` + API 成功 | 触发 |
| `updateOnFailure=true` + API 失败（实际执行了部分逻辑） | 触发 |
| `updateOnFailure=true` + API 参数校验失败（未进入业务逻辑） | **不触发** |
| `updateOnFailure=false` + API 失败 | 不触发 |

**实现**：只有当 ThreadContext 中存在 `__metadata_vmUuids__`（即至少走过了 `BeforeDeliveryMessageInterceptor` 预捕获阶段）时才触发更新。纯参数校验失败不经过拦截器，自然不触发。

### 7.5 影响虚拟机元数据的 API 清单

#### SystemTag 相关

| API | Level | Resolver | updateOnFailure |
|-----|-------|----------|-----------------|
| `APICreateSystemTagMsg` | CONFIG | `ResourceUuidToVmResolver` | false |
| `APIDeleteTagMsg` | CONFIG | `ResourceUuidToVmResolver` | false |
| `APIUpdateSystemTagMsg` | CONFIG | `ResourceUuidToVmResolver` | false |
| `APISetVmBootOrderMsg` | CONFIG | `DirectVmUuidResolver` | false |
| `APIDeleteVmBootModeMsg` | CONFIG | `DirectVmUuidResolver` | false |
| `APIDeleteVmSshKeyMsg` | CONFIG | `DirectVmUuidResolver` | false |
| `APIDeleteVmHostnameMsg` | CONFIG | `DirectVmUuidResolver` | false |
| `APISetVmQgaMsg` | CONFIG | `DirectVmUuidResolver` | false |

#### ResourceConfig 相关

| API | Level | Resolver | updateOnFailure |
|-----|-------|----------|-----------------|
| `APIUpdateResourceConfigMsg` | CONFIG | `ResourceUuidToVmResolver` | false |
| `APIDeleteResourceConfigMsg` | CONFIG | `ResourceUuidToVmResolver` | false |

#### 磁盘加载卸载

| API | Level | Resolver | updateOnFailure |
|-----|-------|----------|-----------------|
| `APIAttachDataVolumeToVmMsg` | STORAGE | `VolumeToVmResolver` | false |
| `APIDetachDataVolumeFromVmMsg` | STORAGE | `PreCaptureVolumeToVmResolver` | false || `APIDeleteDataVolumeMsg` | STORAGE | `PreCaptureVolumeToVmResolver` | false |
| `APIRecoverDataVolumeMsg` | STORAGE | `VolumeToVmResolver` | false || `APIReimageVmInstanceMsg` | STORAGE | `DirectVmUuidResolver` | false |

#### 存储迁移

| API | Level | Resolver | updateOnFailure |
|-----|-------|----------|-----------------|
| `APIPrimaryStorageMigrateVmMsg` | STORAGE | `DirectVmUuidResolver` | false |
| `APIPrimaryStorageMigrateVolumeMsg` | STORAGE | `VolumeToVmResolver` | false |

#### 快照相关

| API | Level | Resolver | updateOnFailure |
|-----|-------|----------|-----------------|
| `APICreateVolumesSnapshotMsg` | STORAGE | `VolumeToVmResolver` | false |
| `APICreateVolumeSnapshotGroupMsg` | STORAGE | `DirectVmUuidResolver` | false |
| `APIDeleteVolumeSnapshotMsg` | STORAGE | `SnapshotToVmResolver` | false |
| `APIDeleteVolumeSnapshotGroupMsg` | STORAGE | `SnapshotGroupToVmResolver` | false |
| `APIRevertVolumeFromSnapshotMsg` | STORAGE | `SnapshotToVmResolver` | false |
| `APIFlattenVolumeMsg` | STORAGE | `VolumeToVmResolver` | false |

#### 克隆/模板

| API | Level | Resolver | updateOnFailure |
|-----|-------|----------|-----------------|
| `APICloneVmInstanceMsg` | STORAGE | `DirectVmUuidResolver` | **true** |
| `APICreateTemplatedVmInstanceFromVmInstanceMsg` | STORAGE | `DirectVmUuidResolver` | **true** |
| `APICreateVmInstanceFromTemplatedVmInstanceMsg` | STORAGE | `DirectVmUuidResolver` | false |
| `APIExportImageFromBackupStorageMsg` | STORAGE | `DirectVmUuidResolver` | false |
> **说明**：`APICloneVmInstanceMsg` 和 `APICreateTemplatedVmInstanceFromVmInstanceMsg` 的 Resolver 解析的是**源 VM** 的 UUID。新创建的 VM 的元数据由新建 VM 流程末尾自动生成（与正常创建 VM 一致），拦截器无需关心。
#### 模板虚拟机身份转换

| API | Level | Resolver | updateOnFailure |
|-----|-------|----------|------------------|
| `APIConvertVmInstanceToTemplatedVmInstanceMsg` | CONFIG | `DirectVmUuidResolver` | false |
| `APIConvertTemplatedVmInstanceToVmInstanceMsg` | CONFIG | `DirectVmUuidResolver` | false |

> **说明**：模板身份转换仅涉及 `TemplatedVmInstanceVO` 标记行的增删和 `isTemplated` 字段的变更，不改变存储拓扑，使用 CONFIG 级别。

#### 卷大小变更

| API | Level | Resolver | updateOnFailure |
|-----|-------|----------|-----------------|
| `APIResizeRootVolumeMsg` | STORAGE | `DirectVmUuidResolver` | false |
| `APIResizeDataVolumeMsg` | STORAGE | `VolumeToVmResolver` | false |

#### VM 配置变更

| API | Level | Resolver | updateOnFailure |
|-----|-------|----------|------------------|
| `APIUpdateVmInstanceMsg` | CONFIG | `DirectVmUuidResolver` | false |

#### 网卡相关

| API | Level | Resolver | updateOnFailure |
|-----|-------|----------|-----------------|
| `APIChangeVmNicNetworkMsg` | CONFIG | `NicToVmResolver` | false |
| `APIAttachVmNicToVmMsg` | CONFIG | `DirectVmUuidResolver` | false |
| `APIChangeVmNicStateMsg` | CONFIG | `NicToVmResolver` | false |
| `APIDeleteVmNicMsg` | CONFIG | `PreCaptureNicToVmResolver` | false |
| `APIDetachNicFromBondingMsg` | CONFIG | `NicToVmResolver` | false |
| `APIAttachNicToBondingMsg` | CONFIG | `NicToVmResolver` | false |

#### CD-ROM

| API | Level | Resolver | updateOnFailure |
|-----|-------|----------|-----------------|
| `APIDeleteVmCdRomMsg` | CONFIG | `DirectVmUuidResolver` | false |

---

## 8. VmUuid 解析器（Resolver）

### 8.1 解析时机（重要）

Resolver 在**两个时机**捕获 vmUuid：

1. **API 执行前**（`BeforeDeliveryMessageInterceptor`）：预解析 vmUuid 并缓存到 `pendingApis` ConcurrentHashMap 中（key = apiId）
2. **API 成功后**（`beforePublishEvent`）：从 `pendingApis` 读取缓存的 vmUuid，提交 GC

**执行线程说明**：`beforeDeliveryMessage()` 在消息投递线程中同步执行（`CloudBusImpl3.doSendAndCallbackFromQueue` 内部调用）。Resolver 的 DB 查询在此线程中执行，对单次 API 延迟影响极小（单次简单查询 <1ms）。

### 8.2 Resolver 接口

```java
public interface VmUuidFromApiResolver {
    /**
     * 从 API 消息解析出需要更新元数据的 vmUuid 列表
     * @return vmUuid 列表（可能为多个）
     */
    List<String> resolve(APIMessage msg);
}
```

### 8.3 内置 Resolver 实现

| Resolver | 逻辑 |
|----------|------|
| `DirectVmUuidResolver` | 从 `msg.getVmInstanceUuid()` 直接获取 |
| `VolumeToVmResolver` | 通过 volumeUuid 查 `VolumeVO.vmInstanceUuid` |
| `PreCaptureVolumeToVmResolver` | 同 VolumeToVmResolver，但标记为需要预捕获（API 执行前获取） |
| `SnapshotToVmResolver` | snapshotUuid → VolumeSnapshotVO.volumeUuid → VolumeVO.vmInstanceUuid |
| `SnapshotGroupToVmResolver` | groupUuid → refs → 多个 volumeUuid → 多个 vmUuid |
| `ResourceUuidToVmResolver` | resourceUuid 可能是 VM/Volume/NIC，逐一判断 |
| `NicToVmResolver` | nicUuid → VmNicVO.vmInstanceUuid |
| `PreCaptureNicToVmResolver` | 同 NicToVmResolver，标记为需要预捕获 |

### 8.4 新增 API 缺少注解的检测

`ApiMetadataBehaviorsCheckerCase`（编译期/CI 测试）：

1. 扫描所有 `APIMessage` 子类
2. 检查是否标注了 `@MetadataImpact`
3. **未标注 → CI 报错**，错误信息提示：`"API {className} 缺少 @MetadataImpact 注解。请判断该 API 是否与虚拟机配置相关，并添加 @MetadataImpact(NONE/CONFIG/STORAGE) 注解。"`
4. 标注了但 resolver 类不存在 → CI 报错

---

## 9. 存储层元数据

### 9.1 元数据存储路径

| 存储类型 | 路径 |
|----------|------|
| sblk | `/dev/{vg_uuid}/{vm_uuid}_vmmeta` |
| local/NFS | `{存储挂载路径}/rootVolumes/acct-{id}/vol-{uuid}/{vm_uuid}_vmmeta` |
| ceph/zbs/vhost | 当前版本不支持，后续按需扩展 |

### 9.2 元数据跟随根盘迁移

存储迁移 API（`APIPrimaryStorageMigrateVmMsg`）已标记为 `@MetadataImpact(STORAGE)`。迁移流程增加步骤：

1. 迁移根盘数据
2. 在新存储创建元数据文件
3. 删除旧存储的元数据文件（GC 异步清理，清理前 double-check 确认 VM 根盘确实不在旧存储上）

**double-check 实现**：GC 执行旧存储元数据删除前，查询当前根盘位置：

```java
String currentRootPsUuid = Q.New(VolumeVO.class)
    .eq(VolumeVO_.vmInstanceUuid, vmUuid)
    .eq(VolumeVO_.type, VolumeType.Root)
    .select(VolumeVO_.primaryStorageUuid)
    .findValue();

if (oldPsUuid.equals(currentRootPsUuid)) {
    // 根盘仍在旧存储（迁移可能已回滚），跳过删除
    logger.warn("VM {} root volume still on PS {}, skip metadata cleanup", vmUuid, oldPsUuid);
    return;
}
// 安全删除旧存储上的元数据
metadataStorageHandler.deleteMetadata(oldPsUuid, vmUuid, ...);
```

**竞态安全**：GC 删除与迁移回滚的竞态窗口中，最坏情况是删除了正确位置的元数据。此时下一次 `@MetadataImpact` API 触发的 GC 会重新写入，不会永久丢失。

### 9.3 各存储类型实现

**sblk（共享块存储）**

共享块存储场景使用完整的二进制协议，详见 [Part 4: sblk 二进制协议](vm-metadata-04-sblk.md)。

核心要点摘要：
- LV 命名：`{vm_uuid}_vmmeta`，路径 `/dev/{vg_uuid}/{vm_uuid}_vmmeta`
- 二进制格式：Header(512B) + Slot A + Slot B，A/B 双槽交替写入
- 三阶段原子写入：Phase 1(标记 pending_op) → Phase 2(写 Slot) → Phase 3(切换 ActiveSlot + 清 pending_op)
- 初始大小 4MB，阶梯式扩容至最大 64MB
- **LV 初始化后立即写入空 DTO 到 Slot A**（避免首次读取返回 CORRUPTED 与真正损坏混淆）
- **LV 初始化时执行 O_DIRECT sanity check**（校验 I/O 路径正确性）

**local/NFS**

- 文件路径：与根盘同目录下的 `vm_metadata.json`
- 文件内容：DTO JSON 明文（其中每个资源的 systemTags/resourceConfigs 为 per-Resource Base64 编码）
- 原子写入：先写 tmp 文件，fsync 后 rename 替换
- **完整性校验**：JSON 顶层增加 `_checksum` 字段，值为 `SHA-256(除 _checksum 外的所有 JSON 内容)`。读取时先校验 `_checksum`，不匹配则报错。写入时序列化 DTO → 计算 SHA-256 → 填入 `_checksum` → 写文件。成本极低（<100KB JSON SHA-256 耗时 <1ms），可检测静默位翻转和磁盘扇区错误

### 9.4 元数据生命周期

| 事件 | 行为 |
|------|------|
| 新创建虚拟机 | 自动创建元数据文件 |
| VM 删除 | 同步删除元数据文件 + 删除失败时提交 GC 异步重试（注：VM 级联删除 Volume/Snapshot 时，最终会删除 VM 元数据文件本身，无需单独更新元数据） |
| 存储迁移 | 新存储创建 → 旧存储 GC 异步清理 |
| 不支持的存储类型 | 静默跳过，不创建元数据文件 |

**存储迁移时的元数据生命周期**：

在 `VmStorageMigrateFlow` 中增加两步（均为 best-effort，失败不阻塞迁移）：

```
step N-1:  initializeMetadataOnTargetPS(vmUuid, targetPsUuid)
           → 如果 PS 类型支持元数据，创建空 LV / 空 JSON 文件
           → 后续 GC 写入完整数据

step N:    deleteMetadataOnSourcePS(vmUuid, sourcePsUuid)
           → 删除旧 LV / 旧 JSON 文件
           → 失败 → 日志告警 + 健康巡检孤儿检测清理
```

**迁移元数据创建失败的处理**：initializeMetadata 失败时只记告警，不阻塞迁移。后续 GC 写入时会自动创建（GC handler 检测到 LV 不存在 → 调用 initializeMetadata → 写入数据）。

### 9.5 不支持的存储类型

ceph、zbs、vhost 当前版本不支持元数据功能。处理逻辑：

| 场景 | 行为 |
|------|------|
| 全局开关开启，VM 根盘在不支持的存储上 | 静默跳过，不创建元数据文件，不报错 |
| `@MetadataImpact` 拦截器触发时 | 检查根盘所在存储类型，不支持的存储类型直接跳过 GC 提交 |
| 注册 API 指定不支持的存储 | 返回错误 `METADATA_STORAGE_NOT_SUPPORTED` |

### 9.6 MetadataStorageHandler 接口

不同存储类型的元数据读写操作通过统一接口抽象：

```java
public interface MetadataStorageHandler {
    /** 初始化元数据存储（创建 LV / 空 JSON 文件） */
    void initializeMetadata(String psUuid, String vmUuid, Completion completion);

    /** 删除元数据（删除 LV / JSON 文件） */
    void deleteMetadata(String psUuid, String vmUuid, Completion completion);

    /** 写入元数据（控制面构建好 payload，直接透传到 Agent 写入存储） */
    void writeMetadata(String psUuid, String vmUuid, String payloadJson, Completion completion);

    /**
     * 读取元数据（从存储读取并返回原始 JSON 字符串）
     * @return payload JSON 字符串（控制面负责解析，Agent 不解析 DTO 内容）
     */
    void readMetadata(String psUuid, String vmUuid, ReturnValueCompletion<String> completion);

    /** 检查该存储类型是否支持元数据 */
    boolean isMetadataSupported(String psType);
}
```

> **重要设计约束**：Agent 端不解析 DTO 内容。控制面（Java 端）负责 DTO 的构建、序列化和反序列化。Agent 只负责将控制面传入的 payload 原样写入存储，或从存储读取原样返回。sblk Agent 仅在写入时对比新旧 payload 的存储拓扑差异来决定 op_type（见 Part 4 §5.2）。

| 实现类 | 存储类型 | initializeMetadata | writeMetadata | readMetadata | deleteMetadata |
|--------|---------|-------------------|---------------|--------------|----------------|
| `SblkMetadataStorageHandler` | SharedBlock | 创建 LV + 写初始 Header | 三阶段原子写入 LV | 读 Header + Active Slot | `lv_delete` |
| `LocalNfsMetadataStorageHandler` | Local/NFS | 创建空 JSON 文件 | tmp + fsync + rename | 读 JSON 文件 + checksum 校验 | `os.remove()` |

---

## 10. 模板虚拟机（Templated VM）元数据

### 10.1 模板 VM 数据模型概述

模板虚拟机通过独立的 `TemplatedVmInstanceVO` 标记表来标识身份，与 `VmInstanceVO` 通过 uuid 外键 1:1 关联。模板 VM 的 `VmInstanceVO.type` 仍为 `"UserVm"`，无特殊 type。

```
VmInstanceVO (type = "UserVm")
  │ uuid = uuid (1:1, CASCADE)
  ▼
TemplatedVmInstanceVO              ← 纯标记表，无额外业务字段
  ├── uuid (FK → VmInstanceEO)
  ├── createDate
  └── lastOpDate

关联表（不纳入元数据）：
  TemplatedVmInstanceCacheVO       ← 运行态：缓存 VM（从模板 Clone 而来）
  TemplatedVmInstanceRefVO         ← 追溯：记录从模板创建出的子 VM
```

### 10.2 元数据中的模板标识

`VmInstanceMetadataDTO.isTemplated` 字段记录 VM 是否为模板虚拟机。

**构建时**（`buildVmInstanceMetadata`）：

```java
dto.isTemplated = Q.New(TemplatedVmInstanceVO.class)
        .eq(TemplatedVmInstanceVO_.uuid, vmInstanceUuid)
        .isExists();
```

**不纳入元数据的关联表及理由**：

| VO | 是否纳入 | 理由 |
|----|---------|------|
| `TemplatedVmInstanceVO` | 通过 `isTemplated` boolean 代替 | 标记表无业务字段，`createDate`/`lastOpDate` 注册时重新生成 |
| `TemplatedVmInstanceCacheVO` | ❌ 不纳入 | 缓存 VM 是运行态产物（按需 Clone + 快照组），跨环境无意义，新环境首次从模板创建 VM 时自动创建 |
| `TemplatedVmInstanceRefVO` | ❌ 不纳入 | 子 VM 追溯关系属于旧环境，新环境不存在对应子 VM，引用无意义 |

### 10.3 模板 VM 元数据更新时机

| 操作 | @MetadataImpact | 说明 |
|------|----------------|------|
| 普通 VM 转模板 VM | `CONFIG` | `isTemplated` false → true |
| 模板 VM 转回普通 VM | `CONFIG` | `isTemplated` true → false，同时删除 CacheVO/RefVO |
| 从模板创建子 VM | 不影响模板本身 | 子 VM 有独立的元数据 |
| 更新模板 VM 属性（CPU/内存/名称） | `CONFIG` | 通过 `UpdateVmInstanceMsg` 间接触发 |

### 10.4 缓存 VM 的元数据处理

缓存 VM（`TemplatedVmInstanceCacheVO.cacheVmInstanceUuid`）有自己独立的 `VmInstanceVO`（type=UserVm）。

**处理策略**：缓存 VM 的元数据**不写入**。

理由：
- 缓存 VM 是内部运行态资源，用户不直接管理
- 注册缓存 VM 无意义，它是从模板 Clone 的中间产物
- 避免注册缓存 VM 时产生冗余的非模板 VM

**实现**：`VmMetadataUpdateInterceptor` 中增加过滤逻辑：

```java
// 如果 vmUuid 对应的是缓存 VM，跳过元数据更新
boolean isCacheVm = Q.New(TemplatedVmInstanceCacheVO.class)
        .eq(TemplatedVmInstanceCacheVO_.cacheVmInstanceUuid, vmUuid)
        .isExists();
if (isCacheVm) {
    return; // 不提交 GC
}
```

### 10.5 schemaVersion 兼容性

`isTemplated` 是新增字段，需要 bump `schemaVersion`：

- **新版本写入的元数据**：包含 `isTemplated` 字段
- **旧版本写入的元数据**：不含该字段，Gson 反序列化时 `boolean` 默认值为 `false`（即非模板）
- **降级行为正确**：旧元数据注册时按非模板处理，符合预期

---

## 11. VmCdRomVO 等附属资源

当前版本不纳入元数据。理由：

- CD-ROM 挂载状态通常不影响 VM 恢复启动
- USB/PCI 透传设备与宿主机绑定，跨环境无意义
- 后续版本如需支持，通过 `VmInstanceMetadataDTO` 新增字段 + bump schemaVersion

---

## 12. API 接口设计

> **完整 API 文档已独立为 [Part 5: API 设计](vm-metadata-05-api.md)**，包含全部请求/响应定义、设计要点及合理性分析。本章仅保留汇总索引。

| API | HTTP | 类型 | 说明 | 详见 |
|-----|------|------|------|------|
| `APIGetVmInstanceMetadataFromPrimaryStorageMsg` | `GET /primary-storage/vm-instances/metadata` | 同步 | 列出存储上所有 VM 元数据概要 | [Part 5 §2](vm-metadata-05-api.md#2-获取主存储上的虚拟机元数据列表) |
| `APIReadVmInstanceMetadataFromPrimaryStorageMsg` | `GET /primary-storage/{psUuid}/vm-instances/{vmUuid}/metadata` | 同步 | 读取指定 VM 的完整元数据 JSON | [Part 5 §3](vm-metadata-05-api.md#3-获取指定虚拟机元数据详情) |
| `APIRegisterVmInstanceMsg` | `POST /vm-instances/register` | 异步 | 从元数据注册恢复虚拟机 | [Part 5 §4](vm-metadata-05-api.md#4-注册虚拟机) |
| `APICheckVmInstanceMetadataConsistencyMsg` | `PUT /vm-instances/{uuid}/consistency` | 异步 | 一致性检查（仅 CLI） | [Part 5 §5](vm-metadata-05-api.md#5-检查虚拟机元数据一致性) |
| `APIUpdateVmMetadataMsg` | `PUT /vm-instances/{uuid}/actions` | 异步 | 手动触发元数据全量更新（仅 CLI） | [Part 5 §6.1](vm-metadata-05-api.md#61-手动触发元数据更新) |
| `APIPreCheckVmMetadataRegistrationMsg` | `PUT /vm-instances/metadata/precheck` | 异步 | 注册预检查（仅 CLI） | [Part 5 §6.2](vm-metadata-05-api.md#62-注册预检查) |

---

## 13. 新增/修改代码文件清单

### 13.1 新增文件

| 文件 | 位置 | 说明 |
|------|------|------|
| VmInstanceMetadataDTO.java | header/vm/ | 核心 DTO |
| VmInstanceMetadataCodec.java | header/vm/ | 编解码工具 |
| VmInstanceMetadataValidator.java | header/vm/ | 校验器 |
| VmInstanceMetadataConstants.java | header/vm/ | 常量定义 |
| VmInstanceMetadataRegistrationSpec.java | header/vm/ | 注册参数 |
| VmInstanceMetadataFieldProcessor.java | compute/vm/ | 注册字段处理器 |
| MetadataImpact.java | header/vm/ | API 影响类型注解 |
| VmUuidFromApiResolver.java | header/vm/ | vmUuid 解析接口 |
| UpdateVmInstanceMetadataMsg.java | header/vm/ | MN 内部消息 |
| UpdateVmInstanceMetadataReply.java | header/vm/ | MN 内部消息回复 |
| UpdateVmInstanceMetadataOnPrimaryStorageMsg.java | header/vm/ | 主存储消息 |
| SubmitTimeBasedGarbageCollectorMsg.java | header/gc/ | 跨 MN 提交 GC 消息 |
| MetadataStorageHandler.java | header/vm/ | 存储层元数据操作接口（§9.6） |
| MetadataTagPattern.java | header/vm/ | SystemTag 白名单模式定义 |
| MetadataWhitelistProvider.java | header/vm/ | SystemTag/ResourceConfig/API 白名单注册接口 |
| MetadataWhitelistChecker.java | test/ | CI 编译期统一白名单检查（@MetadataImpact + SystemTag + ResourceConfig） |
| MetadataTagAnnotationChecker.java | test/ | CI 编译期 SystemTag 标注检查 |
| MetadataHealthCheckJob.java | compute/vm/ | 定期健康巡检任务（Part 2 §11） |
| RegistrationCleanupJob.java | compute/vm/ | 注册崩溃残留清理任务 |
| MetadataStaleEvent.java | header/vm/ | 元数据过期事件（GC 放弃后发出） |
| BatchCheckMetadataStatusMsg.java | header/vm/ | 批量检查元数据 Header 状态 |
| RepairMetadataMsg.java | header/vm/ | 修复元数据 Header（512B 写入） |
| VmInstanceMetadataStruct.java | header/storage/primary/ | 元数据概要结构体（§12.1） |
| APIGetVmInstanceMetadataFromPrimaryStorageMsg.java | header/storage/primary/ | 获取元数据列表 API（§12.1） |
| APIGetVmInstanceMetadataFromPrimaryStorageReply.java | header/storage/primary/ | 获取元数据列表响应 |
| APIReadVmInstanceMetadataFromPrimaryStorageMsg.java | header/storage/primary/ | 读取指定 VM 元数据 API（§12.2） |
| APIReadVmInstanceMetadataFromPrimaryStorageReply.java | header/storage/primary/ | 读取指定 VM 元数据响应 |
| APIRegisterVmInstanceMsg.java | header/storage/primary/ | 注册虚拟机 API（§12.3） |
| APIRegisterVmInstanceEvent.java | header/storage/primary/ | 注册虚拟机响应事件 |
| APICheckVmInstanceMetadataConsistencyMsg.java | header/vm/ | 一致性检查 API（§12.4） |
| APICheckVmInstanceMetadataConsistencyEvent.java | header/vm/ | 一致性检查响应事件 |
| APIUpdateVmMetadataMsg.java | header/vm/ | 手动更新元数据 API（§12.5.1） |
| APIPreCheckVmMetadataRegistrationMsg.java | header/vm/ | 注册预检查 API（§12.5.2） |

### 13.2 修改文件

| 文件 | 修改内容 |
|------|----------|
| UpdateVmInstanceMetadataGC.java | ChainTask 去重、指数退避、retryCount 持久化、owner 归集 |
| VmMetadataUpdateInterceptor.java | 使用 gc.submit()，统一 NAME 格式，方案 B 远程提交+回退 |
| VmInstanceBase.java | handler 加 ChainTask 串行化；失败路径不创建新 GC；消息改 makeLocalServiceId |
| GarbageCollector.java | loadFromVO 加乐观锁（条件更新） |
| GarbageCollectorManagerImpl.java | 新增 `handle(SubmitTimeBasedGarbageCollectorMsg)` 处理逻辑 |
| ThreadFacade.java | 预留扩展（`hasPendingTask` 已被 `exceedMaxPendingCallback` 取代，当前无需修改） |
| ThreadFacadeImpl.java | 预留扩展（当前无需修改） |
