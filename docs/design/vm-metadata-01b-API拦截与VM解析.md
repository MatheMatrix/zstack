# VM 元数据 — API 拦截与 VM 解析

## 目录

1. [@MetadataImpact 注解与 API 拦截](#1-metadataimpact-注解与-api-拦截)
   - [1.5 条件性影响（v2+）](#15-条件性影响v2)
   - [1.6 内部消息覆盖策略](#16-内部消息覆盖策略)
   - [1.7 pendingApis 生命周期治理](#17-pendingapis-生命周期治理)
2. [VmUuid 解析器（Resolver）](#2-vmuuid-解析器resolver)
   - [2.4 Resolver → API 映射表](#24-resolver--api-映射表)
3. [统一 CI 检查 — MetadataWhitelistChecker](#3-统一-ci-检查--metadatawhitelistchecker)
   - [3.1 CI 扩展：内部消息 markDirty 审计](#31-ci-扩展内部消息-markdirty-审计)
   - [3.2 设计决策：为什么不用 ExtensionPoint](#32-设计决策为什么不用-extensionpoint-监听-tagconfig-变更)
4. [影响虚拟机元数据的 API 清单](#4-影响虚拟机元数据的-api-清单)
5. [约束与不変量](#5-约束与不変量)

---

## 1. @MetadataImpact 注解与 API 拦截

### 1.1 注解定义

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

### 1.2 拦截范围（重要）

**所有 `APIMessage` 子类必须标注 `@MetadataImpact`，CI 强制检查。**

1. `MetadataWhitelistChecker` 扫描所有 `APIMessage` 子类，**未标注 `@MetadataImpact` 的 API 直接 CI 报错**
2. 开发者必须为每个 API 显式赋值 `NONE`、`CONFIG` 或 `STORAGE`
3. 运行时拦截器只对 level ≠ `NONE` 的 API 触发元数据更新

**opt-out 策略说明**（存量 API 标注）：
- `@MetadataImpact(NONE)` — 与 VM 配置无关的 API（网络管理、用户管理等）
- `@MetadataImpact(STORAGE)` — 涉及存储拓扑变更的 API（快照、存储迁移、卷加卸等）
- `@MetadataImpact` 或 `@MetadataImpact(CONFIG)` — 其余 VM 相关配置更新 API

### 1.3 STORAGE 标记的精确定义

`@MetadataImpact(STORAGE)` 表示该 API 导致 VM 的**存储结构**发生变化。以下**任一条件**成立即判定：

| # | 条件 | 典型 API |
|---|------|----------|
| 1 | VM 的卷列表发生变化（数量增减） | Attach/Detach/DeleteDataVolume |
| 2 | 任一卷的 installPath 发生变化 | StorageMigrate, Reimage, Flatten |
| 3 | 任一卷的快照数量发生变化 | Create/DeleteSnapshot, SnapshotGroup |

**不属于存储结构变化的场景**：卷大小变更（resize）—— installPath 不变、快照不变、卷数不变，归类为 `CONFIG`。

**对 sblk 的影响**：`@MetadataImpact(CONFIG)` → OP type=1（`CONFIG_UPDATE`），`@MetadataImpact(STORAGE)` → OP type=2（`STORAGE_CHANGE`）。OP type 通过 `storageStructureChange` 字段贯穿整条消息链。详见 [Part 4c §2](vm-metadata-04c-sblk写入流程.md#2-核心流程三阶段原子写入)。

### 1.4 updateOnFailure 触发条件

| 条件 | 是否触发 |
|------|----------|
| `updateOnFailure=true` + API 成功 | 触发 |
| `updateOnFailure=true` + API 失败（实际执行了部分逻辑） | 触发 |
| `updateOnFailure=true` + API 参数校验失败（未进入业务逻辑） | **不触发** |
| `updateOnFailure=false` + API 失败 | 不触发 |

**关键实现 — `__metadata_vmUuids__` 的设置时机**：

`__metadata_vmUuids__` 是 ThreadContext 中的标记键，由 `BeforeDeliveryMessageInterceptor` 在 API 参数校验通过后设置。其存在性即"是否进入过业务逻辑"的可靠标志。纯参数校验失败不经过拦截器，ThreadContext 中无此键，自然不触发 `updateOnFailure`。

```
时序流程：
  CloudBus 收到 APIMessage
    ├─ 参数校验失败 → 直接返回错误 → ThreadContext 无 __metadata_vmUuids__
    └─ 参数校验通过 → 进入 BeforeDeliveryMessageInterceptor
         ├─ @MetadataImpact level ≠ NONE → Resolver.resolve(msg) → 存入 ThreadContext + pendingApis
         └─ API 业务逻辑执行
              ├─ 成功 → beforePublishEvent → markDirty
              └─ 失败 → 检查 updateOnFailure + __metadata_vmUuids__ → 满足则 markDirty
```

### 1.5 条件性影响（v2+）

Q1b-5 结论：现阶段保持 `@MetadataImpact` 简单枚举语义（`NONE/CONFIG/STORAGE`），不在 v1/v1.1 引入条件表达式。

| 项 | v1/v1.1 决策 | v2+ 预留 |
|----|--------------|----------|
| 注解模型 | 固定 `MetadataImpactLevel` + 固定 Resolver | 可扩展 `condition` 或策略接口 |
| 风险控制 | 采用保守标注：不确定场景优先 `CONFIG`/`STORAGE` 而非 `NONE` | 引入运行时条件判定避免过度刷新 |
| 文档约束 | 任何“条件性”需求先落入 API 评审清单并记录原因 | 条件模型落地需单独 RFC |

### 1.6 内部消息覆盖策略

`@MetadataImpact` 仅覆盖 `APIMessage`。对内部消息（`Message` 子类）采用**显式注册 + 代码审计**：

```java
private static final Set<Class<? extends Message>> INTERNAL_METADATA_MESSAGES = Set.of(
    AllocateHostMsg.class,
    MigrateVmMsg.class,
    ChangeVmIpMsg.class,
    DetachDataVolumeFromVmMsg.class,
    DeleteVolumeSnapshotMsg.class
    // 仅示例：实际清单以 vm-instance / storage / network 相关处理器审查结果为准
);
```

- 注册表用于声明“已知会影响元数据且不经过 API 拦截器”的消息类型。
- 对应 handler 在事务提交后调用 `markDirty()`，避免 dirty mark 指向未提交快照。
- Poller 的 stale 窗口为设计内窗口，不消除；通过“提交后 markDirty + 下一轮全量构建”收敛（见 [Part 2 §1.4](vm-metadata-02-脏标记与Poller.md#14-最终一致性模型)）。

### 1.7 pendingApis 生命周期治理

`pendingApis` 为 `ConcurrentHashMap<apiId, PendingApiContext>`，新增超时治理（超时时间可通过 GlobalConfig 配置），防止 API 超时导致 entry 泄漏：

1. 每 5 分钟执行一次清理任务。
2. 移除“创建时间 > `VmGlobalConfig.VM_METADATA_PENDING_API_TIMEOUT_MINUTES`（默认 45 分钟）”的 entry。
3. 对被清理 entry 的 vmUuid 执行 `markDirty()`（最终一致）。
4. `afterCompletion` 增加 null check：`remove(apiId)` 返回 null 时按“已被清理”分支继续，不报错。

**Per-API 超时策略（讨论 Δ-3）**：原方案使用固定 45 分钟超时，无法匹配 API 类型差异。改为从 `PendingApiContext` 中记录 API 类名，清理时根据 API 类型动态计算超时：
- 普通 API：使用 API 自身的 `timeout` 字段（若该 API 为 LongJob 触发，则取 LongJob 超时）。
- 回退默认值：若 API 无显式 timeout 配置，使用 `VM_METADATA_PENDING_API_TIMEOUT_MINUTES`（默认 45min）。
- 此设计确保 LongJob 场景（如存储迁移可达数小时）不会被过早清理，同时普通短 API 不会等待过久才触发 markDirty。

**MN 重启时 pendingApis 丢失的处理策略**：`pendingApis` 是 JVM 内存结构，MN 重启后全部丢失。对正在执行中的 API，有以下几种情况：
- **API 执行已到达 `afterCompletion`**：`remove(apiId)` 返回 null → 按"已清理"分支处理 → 对 vmUuids 执行 `markDirty()`，保证最终一致。由于 MN 已重启，此路径不会执行。
- **API 尚未完成即 MN 崩溃**：API 执行被中断，`markDirty()` 未被调用。恢复依赖两条路径：(1) 用户重新发起 API → 新的 API 触发 `markDirty()`；(2) 路径指纹巡检（Part 2b §8.2）发现漂移 → 自动 `markDirty()`。
- **结论**：MN 重启丢失 pendingApis 不会导致数据永久不一致，最终一致性由 Poller + 路径巡检保证。无需持久化 pendingApis（持久化成本高于收益）。

**`updateOnFailure` 与 pendingApis 的交互**：`updateOnFailure=true` 的 API 在失败时通过 `afterCompletion(reply)` 回调处理。回调从 `pendingApis.remove(apiId)` 取出预缓存的 vmUuids，检查 `reply.isSuccess()` 为 false，若 `updateOnFailure=true` 则执行 `markDirty()`。与成功路径使用同一 pendingApis entry，无额外数据结构。若 entry 已被超时清理，`remove()` 返回 null，此时 vmUuids 已在清理时被 `markDirty()` 过，不会遗漏。
```java
scheduledPool.scheduleAtFixedRate(() -> {
    Instant deadline = Instant.now().minus(Duration.ofMinutes(VmGlobalConfig.VM_METADATA_PENDING_API_TIMEOUT_MINUTES.value(Long.class)));
    pendingApis.entrySet().removeIf(e -> {
        if (e.getValue().getCreateTime().isBefore(deadline)) {
            e.getValue().getVmUuids().forEach(vm -> markDirty(vm, e.getValue().isStorageStructureChange()));
            return true;
        }
        return false;
    });
}, 5, 5, TimeUnit.MINUTES);
```

---

## 2. VmUuid 解析器（Resolver）

### 2.1 解析时机

Resolver 在**两个时机**捕获 vmUuid：

1. **API 执行前**（`BeforeDeliveryMessageInterceptor`）：预解析 vmUuid 并缓存到 `pendingApis` ConcurrentHashMap 中（key = apiId）
2. **API 成功后**（`beforePublishEvent`）：从 `pendingApis` 读取缓存的 vmUuid，调用 `markDirty(vmUuid)` 标脏

**执行线程说明**：`beforeDeliveryMessage()` 在消息投递线程中同步执行。Resolver 的 DB 查询在此线程中执行，对单次 API 延迟影响极小（<1ms）。

### 2.2 Resolver 接口

```java
public interface VmUuidFromApiResolver {
    List<String> resolve(APIMessage msg);
}
```

### 2.3 内置 Resolver 实现

| Resolver | 逻辑 |
|----------|------|
| `DirectVmUuidResolver` | 从 `msg.getVmInstanceUuid()` 直接获取 |
| `VolumeToVmResolver` | 通过 volumeUuid 查 `VolumeVO.vmInstanceUuid` |
| `PreCaptureVolumeToVmResolver` | 同 VolumeToVmResolver，标记为需要预捕获（API 执行前获取） |
| `SnapshotToVmResolver` | snapshotUuid → VolumeSnapshotVO.volumeUuid → VolumeVO.vmInstanceUuid |
| `SnapshotGroupToVmResolver` | groupUuid → refs → 多个 volumeUuid → 多个 vmUuid |
| `ResourceUuidToVmResolver` | resourceUuid 可能是 VM/Volume/NIC，逐一判断 |

**`ResourceUuidToVmResolver` 过滤非 VM 相关资源**：`APICreateSystemTagMsg` 等 Tag API 的 `resourceUuid` 可能指向任意资源类型（Host、Zone、L3Network 等）。`ResourceUuidToVmResolver` 的实现按以下优先级解析：
1. `dbf.findByUuid(resourceUuid, VmInstanceVO.class)` → 非 null 则直接返回 vmUuid
2. `dbf.findByUuid(resourceUuid, VolumeVO.class)` → 取 `vmInstanceUuid`
3. `dbf.findByUuid(resourceUuid, VmNicVO.class)` → 取 `vmInstanceUuid`
4. 以上均为 null → 返回空列表（该 Tag 不关联 VM，跳过 markDirty）

此为已有实现的显式文档化。每步查询命中索引，开销 < 1ms。非 VM 相关 Tag（如 Host Tag）在第 4 步返回空，不触发任何元数据操作。

| `NicToVmResolver` | nicUuid → VmNicVO.vmInstanceUuid |
| `PreCaptureNicToVmResolver` | 同 NicToVmResolver，标记为需要预捕获 |

### 2.4 Resolver → API 映射表

该表用于代码评审和 CI 问题定位；权威 API 列表以 §4 为准。

| Resolver | 典型 API | 说明 |
|----------|----------|------|
| `DirectVmUuidResolver` | `APIUpdateVmInstanceMsg`、`APISetVmBootOrderMsg`、`APIReimageVmInstanceMsg`、`APICloneVmInstanceMsg` | API 入参直接包含 vmUuid |
| `VolumeToVmResolver` | `APIAttachDataVolumeToVmMsg`、`APIRecoverDataVolumeMsg`、`APIPrimaryStorageMigrateVolumeMsg`、`APIResizeDataVolumeMsg` | 通过 volumeUuid 反查 VM |
| `PreCaptureVolumeToVmResolver` | `APIDetachDataVolumeFromVmMsg`、`APIDeleteDataVolumeMsg` | 删除/卸载场景需 API 前预捕获 |
| `SnapshotToVmResolver` | `APIDeleteVolumeSnapshotMsg`、`APIRevertVolumeFromSnapshotMsg` | snapshotUuid → volumeUuid → vmUuid |
| `SnapshotGroupToVmResolver` | `APIDeleteVolumeSnapshotGroupMsg` | groupUuid 可映射多个 VM（跨卷） |
| `ResourceUuidToVmResolver` | `APICreateSystemTagMsg`、`APIDeleteTagMsg`、`APIUpdateResourceConfigMsg` | 资源类型可能是 VM/Volume/NIC，需多分支解析 |
| `NicToVmResolver` | `APIChangeVmNicNetworkMsg`、`APIChangeVmNicStateMsg`、`APIDetachNicFromBondingMsg` | nicUuid → vmUuid |
| `PreCaptureNicToVmResolver` | `APIDeleteVmNicMsg` | 删除场景需 API 前预捕获 |

---

## 3. 统一 CI 检查 — MetadataWhitelistChecker

**注意**：此为唯一的 CI 检查类，合并了 API 注解检查、SystemTag 白名单检查、ResourceConfig 白名单检查。
白名单数据复用 `CoreMemorySnapshotConfigs`（已有的内存快照恢复候选列表），不再另建 Provider 接口。

```java
public class MetadataWhitelistChecker extends PostBuildCheckerCase {
    @Override
    public void check() {
        // Part 1: API @MetadataImpact 注解 + Resolver 检查
        Set<Class<?>> allApiMsgs = BeanUtils.reflections.getSubTypesOf(APIMessage.class);
        for (Class<?> msgClass : allApiMsgs) {
            if (isQueryOrGetApi(msgClass)) continue;
            assertMetadataImpactPresent(msgClass);       // 注解必须存在
            assertResolverValid(msgClass);                // level ≠ NONE 时检查 Resolver
        }

        // Part 2: SystemTag 白名单检查（数据来源：CoreMemorySnapshotConfigs）
        Set<String> allDefinedTags = scanAllSystemTagDefinitions();
        Set<String> registeredTags = new HashSet<>();
        registeredTags.addAll(toTagNames(CoreMemorySnapshotConfigs.restoreCandidatePatternedSystemTags));
        registeredTags.addAll(toTagNames(CoreMemorySnapshotConfigs.restoreCandidateSystemTags));
        // @NeedRestoreOnVmApplySnapshot 注解标注的 Tag 自动纳入
        registeredTags.addAll(collectAnnotatedTags(NeedRestoreOnVmApplySnapshot.class));
        for (String tag : allDefinedTags) {
            if (!registeredTags.contains(tag)) {
                fail("SystemTag '" + tag + "' not in CoreMemorySnapshotConfigs whitelist");
            }
        }

        // Part 3: ResourceConfig 白名单检查（数据来源：CoreMemorySnapshotConfigs）
        Set<String> allConfigCategories = scanAllResourceConfigCategories();
        Set<String> registeredCategories = new HashSet<>();
        registeredCategories.addAll(toConfigNames(CoreMemorySnapshotConfigs.vmRestoreCandidateConfigs));
        registeredCategories.addAll(toConfigNames(CoreMemorySnapshotConfigs.volumeRestoreCandidateConfigs));
        registeredCategories.addAll(toConfigNames(CoreMemorySnapshotConfigs.vmNicRestoreCandidateConfigs));
        for (String cat : allConfigCategories) {
            if (!registeredCategories.contains(cat)) {
                fail("ResourceConfig '" + cat + "' not in CoreMemorySnapshotConfigs whitelist");
            }
        }
    }
}
```

### 3.1 CI 扩展：内部消息 markDirty 审计

在现有三段检查基础上，补充 Part 4 审计（对 STORAGE 级内部消息为 ERROR 阻断级，其余为 WARNING）：

1. 扫描 `AbstractHandler<XxxMsg>` / `MessageHandler` 实现。
2. 若检测到 VM 相关 VO（`VmInstanceVO`/`VolumeVO`/`VolumeSnapshotVO`/`VmNicVO`）写操作且未调用 `markDirty()`，输出 WARNING。
3. 若 handler 处理的消息类型命中 `INTERNAL_METADATA_MESSAGES`，但未在注册表注释中标注触发来源，输出 WARNING。
4. **STORAGE 级阻断**：若 handler 处理的消息类型命中 `INTERNAL_METADATA_MESSAGES` 且该消息在注册表中标注为 `STORAGE` 级别，但 handler 未调用 `markDirty()`，则**输出 ERROR 并阻断 CI 构建**（`fail()`），而非仅 WARNING。原因：STORAGE 级遗漏会导致 sblk OP type 错误，影响存储拓扑一致性，风险远高于 CONFIG 级遗漏。

**说明**：CONFIG 级内部消息的检查仍为"辅助发现"WARNING，不阻断 CI；STORAGE 级为强制阻断 ERROR。此区分确保高风险路径不被遗漏，同时避免对低风险基础设施路径（升级脚本、巡检修复）误杀。

**CI 报错引导示例**：
```
"API APIAttachGpuDeviceToVmMsg 的 resolver GpuToVmResolver 未找到。
 请实现 VmUuidFromApiResolver 接口，从该 API 消息中解析出关联的 vmUuid。
 参考内置实现：NicToVmResolver、VolumeToVmResolver 等。"
```

---

### 3.2 设计决策：为什么不用 ExtensionPoint 监听 Tag/Config 变更？

**背景**：ZStack 内部 SystemTag 的修改路径主要有三类：

| 路径 | 频次 | 是否触发 lifecycle callback |
|------|------|---------------------------|
| `TagManager.newSystemTagCreator().create()` | ~263 处 | (Y) 触发 |
| `SystemTag.delete()` | ~143 处 | (Y) 触发 |
| `SQL.New(SystemTagVO.class)` / `dbf.persist(SystemTagVO)` | ~15 处 | (N) 完全绕过 |

**结论：不额外引入 `SystemTagLifeCycleExtensionPoint`**，理由如下：

1. **用户发起的 Tag/Config 修改全部通过 API**（§4.1/§4.2 已列出），API 拦截器已覆盖
2. **内部 Tag 操作发生在已标注 `@MetadataImpact` 的 API 执行上下文中**（如 `APICreateVmInstanceMsg` 流程内的 `setBootMode` 系统标签），上层 API 已触发 `markDirty`
3. **~15 处直接 SQL 操作**均为基础设施级别（Host 重连写硬件信息、升级迁移脚本、IAM 操作），不涉及 `CoreMemorySnapshotConfigs` 中的元数据相关 Tag（USERDATA、SSHKEY、BOOT_MODE 等）
4. **即使极端情况遗漏**，Poller 安全网（Part 2 §4）从 DB 全量构建 DTO 写入，最终一致
5. **ExtensionPoint 成本高**：需在 ~500+ 内部操作中逐一过滤白名单，不值得

ResourceConfig 同理：`ResourceConfig.updateValue()` 内部调用 ~92 处触发 extension，`SQL.New(ResourceConfigVO.class)` 直接操作仅 2 处（基础设施级别）。API 拦截 + Poller 安全网已足够。

---

## 4. 影响虚拟机元数据的 API 清单

以下列出所有需要标注 `@MetadataImpact` 且 level ≠ `NONE` 的 API。

### 4.1 SystemTag 相关

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

### 4.2 ResourceConfig 相关

| API | Level | Resolver | updateOnFailure |
|-----|-------|----------|-----------------|
| `APIUpdateResourceConfigMsg` | CONFIG | `ResourceUuidToVmResolver` | false |
| `APIDeleteResourceConfigMsg` | CONFIG | `ResourceUuidToVmResolver` | false |

### 4.3 磁盘加载卸载

| API | Level | Resolver | updateOnFailure |
|-----|-------|----------|-----------------|
| `APIAttachDataVolumeToVmMsg` | STORAGE | `VolumeToVmResolver` | false |
| `APIDetachDataVolumeFromVmMsg` | STORAGE | `PreCaptureVolumeToVmResolver` | false |
| `APIDeleteDataVolumeMsg` | STORAGE | `PreCaptureVolumeToVmResolver` | false |
| `APIRecoverDataVolumeMsg` | STORAGE | `VolumeToVmResolver` | false |
| `APIReimageVmInstanceMsg` | STORAGE | `DirectVmUuidResolver` | false |

### 4.4 存储迁移

| API | Level | Resolver | updateOnFailure |
|-----|-------|----------|-----------------|
| `APIPrimaryStorageMigrateVmMsg` | STORAGE | `DirectVmUuidResolver` | false |
| `APIPrimaryStorageMigrateVolumeMsg` | STORAGE | `VolumeToVmResolver` | false |
| `APILocalStorageMigrateVolumeMsg` | STORAGE | `VolumeToVmResolver` | **true** |

### 4.5 快照相关

| API | Level | Resolver | updateOnFailure |
|-----|-------|----------|-----------------|
| `APICreateVolumesSnapshotMsg` | STORAGE | `VolumeToVmResolver` | false |
| `APICreateVolumeSnapshotGroupMsg` | STORAGE | `DirectVmUuidResolver` | **true** |
| `APIDeleteVolumeSnapshotMsg` | STORAGE | `SnapshotToVmResolver` | false |
| `APIDeleteVolumeSnapshotGroupMsg` | STORAGE | `SnapshotGroupToVmResolver` | **true** |
| `APIRevertVolumeFromSnapshotMsg` | STORAGE | `SnapshotToVmResolver` | false |
| `APIFlattenVolumeMsg` | STORAGE | `VolumeToVmResolver` | false |

**审计结论**：`APICreateVolumeSnapshotGroupMsg`、`APILocalStorageMigrateVolumeMsg`、`APIDeleteVolumeSnapshotGroupMsg` 为批量/部分成功风险 API，统一要求 `updateOnFailure=true`。

| API | 风险类型 | updateOnFailure 要求 |
|-----|----------|----------------------|
| `APICreateVolumeSnapshotGroupMsg` | 多卷快照，可能部分卷成功 | **true** |
| `APILocalStorageMigrateVolumeMsg` | 迁移流程分段执行，可能部分生效 | **true** |
| `APIDeleteVolumeSnapshotGroupMsg` | 组内快照删除可能部分成功 | **true** |

### 4.6 克隆/模板

| API | Level | Resolver | updateOnFailure |
|-----|-------|----------|-----------------|
| `APICloneVmInstanceMsg` | STORAGE | `DirectVmUuidResolver` | **true** |
| `APICreateTemplatedVmInstanceFromVmInstanceMsg` | STORAGE | `DirectVmUuidResolver` | **true** |
| `APICreateVmInstanceFromTemplatedVmInstanceMsg` | STORAGE | `DirectVmUuidResolver` | false |
| `APIExportImageFromBackupStorageMsg` | NONE | — | false |

**说明**：Clone/Template 的 Resolver 解析**源 VM** UUID。新建 VM 的元数据由新建流程末尾自动生成。

**设计决策**：`APIExportImageFromBackupStorageMsg` 已确认为 `NONE`。导出镜像不修改 VM 配置/存储拓扑，不参与 resolver 解析与标脏链路。

### 4.7 模板虚拟机身份转换

| API | Level | Resolver | updateOnFailure |
|-----|-------|----------|------------------|
| `APIConvertVmInstanceToTemplatedVmInstanceMsg` | CONFIG | `DirectVmUuidResolver` | false |
| `APIConvertTemplatedVmInstanceToVmInstanceMsg` | CONFIG | `DirectVmUuidResolver` | false |

模板身份转换不改变存储拓扑，使用 CONFIG。元数据刷新时会重新计算 `vmCategory`。

### 4.8 卷大小变更

| API | Level | Resolver | updateOnFailure |
|-----|-------|----------|------------------|
| `APIResizeRootVolumeMsg` | CONFIG | `DirectVmUuidResolver` | false |
| `APIResizeDataVolumeMsg` | CONFIG | `VolumeToVmResolver` | false |

### 4.9 VM 配置变更

| API | Level | Resolver | updateOnFailure |
|-----|-------|----------|------------------|
| `APIUpdateVmInstanceMsg` | CONFIG | `DirectVmUuidResolver` | false || `APIRecoverVmInstanceMsg` | CONFIG | `DirectVmUuidResolver` | false |

**`APIRecoverVmInstanceMsg` 纳入说明（讨论补充）**：Recover VM 将 Destroyed 状态的 VM 恢复为 Stopped，该操作改变了 VM 状态但不涉及存储拓扑变更，因此标注为 `CONFIG`。Recover 后 VM 需要重新刷写元数据（Destroyed 状态期间元数据可能已被 Poller 删除）。
### 4.10 网卡相关

| API | Level | Resolver | updateOnFailure |
|-----|-------|----------|-----------------|
| `APIChangeVmNicNetworkMsg` | CONFIG | `NicToVmResolver` | false |
| `APIAttachVmNicToVmMsg` | CONFIG | `DirectVmUuidResolver` | false |
| `APIChangeVmNicStateMsg` | CONFIG | `NicToVmResolver` | false |
| `APIDeleteVmNicMsg` | CONFIG | `PreCaptureNicToVmResolver` | false |
| `APIDetachNicFromBondingMsg` | CONFIG | `NicToVmResolver` | false |
| `APIAttachNicToBondingMsg` | CONFIG | `NicToVmResolver` | false |

### 4.11 VM 创建与未纳入元数据的 API

**VM 创建**：`APICreateVmInstanceMsg` 不通过 `@MetadataImpact` 拦截器触发。VM 创建 FlowChain 末尾直接调用 `initializeMetadata()` + `markDirty()`，确保元数据文件在 VM 创建成功后即被初始化和首次刷写。

**VM 创建元数据初始化时机（讨论澄清）**：元数据初始化采用异步 post-success hook 模式，即在 `CreateVmInstanceFlow` 主 Flow 全部成功后、返回 API 结果前，通过异步回调执行 `markDirty(vmUuid, true)`。失败不影响 VM 创建结果，Poller 安全网会在后续轮次重试。此设计避免元数据写入失败导致整个 VM 创建回滚。

**CD-ROM**：`APIDeleteVmCdRomMsg` 标注为 `@MetadataImpact(NONE)`。CD-ROM 当前版本不纳入元数据（见 [Part 1a §7](vm-metadata-01a-数据模型与序列化.md#7-vmcdromvo-等附属资源)），删除 CD-ROM 不触发元数据更新。

## 5. 约束与不変量

**`INTERNAL_METADATA_MESSAGES` 完备性保证**：静态注册表无法自动发现新增内部消息。保证完备性的手段为三层防线：
1. **开发规范**：修改 VM 存储拓扑字段的内部消息 handler，成功后必须调用 `markDirty()`（Part 2b §12.4 D1）
2. **CI Part 4**：`MetadataWhitelistChecker` 扫描 handler 实现中的 VO 写操作 + markDirty 调用（§3.1），STORAGE 级遗漏为 ERROR 阻断
3. **路径指纹巡检**：运行时兜底，检测实际路径漂移并自动 markDirty（Part 2b §8.2）

无需为 `INTERNAL_METADATA_MESSAGES` 引入自动发现机制。CI + 运行时双重防线已提供足够保障。

**非 KVM Hypervisor 排除**：`@MetadataImpact` 标注在 `APIMessage` 类层面，不区分 Hypervisor 类型。运行时拦截器在 Resolver 解析出 vmUuid 后，**通过查询 `VmInstanceVO.hypervisorType` 过滤**：仅 `KVM` 类型的 VM 继续标脏，其他类型（VMware、Simulator 等）静默跳过。此过滤在 `markDirty()` 入口处实现（与 `type != "UserVm"` 检查同层），不增加 Resolver 复杂度。非 KVM VM 的存储驱动不支持元数据格式（无 sblk LV / 无 `.zstack-vm-metadata` 目录），跳过是正确行为。

| 约束 ID | 约束描述 | 违反后果 |
|---------|----------|----------|
| C-IC | `INTERNAL_METADATA_MESSAGES` 与内部 handler 的 `markDirty()` 调用点必须一一可追溯，新增内部消息需同步更新注册表与注释来源 | 内部路径修改被遗漏，Poller 长期读旧状态 |
| C-IM | 所有 `APIMessage` 子类必须显式标注 `@MetadataImpact`（可为 NONE）；`MetadataWhitelistChecker` 扫描全量子类，不允许“默认未声明” | 新增 API 逃逸拦截链，行为不可预测 |
| C-PA | `pendingApis` 必须具备超时清理（5min 周期、可配超时（默认 45min））与 afterCompletion null-safe 逻辑；清理时需补 `markDirty()` | 内存泄漏或超时 API 的最终一致性断裂 |
| C-RS | Resolver 选择需与 API 资源语义匹配；删除/卸载类 API 必须使用 pre-capture resolver 或等价机制 | API 完成后资源已消失，无法解析 vmUuid 导致漏标脏 |
| C-H1 | `INTERNAL_METADATA_MESSAGES` 中标注为 STORAGE 级别的内部消息，其 handler 必须调用 `markDirty()`；CI Part 4 对此类遗漏执行 ERROR 阻断（`fail()`） | STORAGE 级内部路径遗漏 markDirty，sblk OP type 错误，存储拓扑一致性断裂 |
| C-M4 | `pendingApis` 超时时间必须通过 `VmGlobalConfig.VM_METADATA_PENDING_API_TIMEOUT_MINUTES` 配置（默认 45 分钟），不得硬编码 | 超时配置无法随 LongJob 场景调整，导致 entry 泄漏或过早清理 |
