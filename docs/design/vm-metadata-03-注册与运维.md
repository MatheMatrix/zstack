# VM 元数据 — 注册与运维

## 目录

1. [注册字段处理矩阵](#1-注册字段处理矩阵)
2. [跨存储数据盘处理规则](#2-跨存储数据盘处理规则)
3. [注册虚拟机详细流程](#3-注册虚拟机详细流程)
4. [注册事务回滚](#4-注册事务回滚)
5. [注册场景问题分析](#5-注册场景问题分析)
6. [可观测性](#6-可观测性)
7. [设计决策汇总](#7-设计决策汇总)
8. [运维指南：注册失败后的清理](#8-运维指南注册失败后的清理)
9. [约束与不変量](#9-约束与不変量)

**API 定义**（请求/响应/错误码）统一见 [Part 5: API 设计](vm-metadata-05-API设计.md)。本文档不重复定义 API 结构。

## 0. 依赖声明

| 依赖项 | 类型 | 来源 | 本文使用方式 |
|--------|------|------|-------------|
| `VmMetadataPathFingerprintVO.vmInstanceUuid` | 数据模型约束 | [Part 2b §1](vm-metadata-02b-高可用与运维.md#1-高可用策略) | 字符串 UUID 作为稳定锚点，支持 keyset 分页与跨环境映射 |
| sblk 读取状态语义（OK/NEED_REPAIR/RECOVERED/DEGRADED/CORRUPTED） | 读取契约 | [Part 4d §2.4](vm-metadata-04d-sblk读取与恢复.md#24-readresult-状态语义) | 注册前读取元数据与注册后校验的可用性判定 |
| `APICheckVmInstanceMetadataConsistencyMsg` | 运维 API | [Part 5 §5](vm-metadata-05-API设计.md#5-检查虚拟机元数据一致性) | 注册完成后的一致性复核与告警触发 |

---

## 1. 注册字段处理矩阵

### 1.1 VmInstanceVO

| 字段 | 处理方式 | 说明 |
|------|----------|------|
| uuid | 保留 | 冲突时拒绝注册 |
| name | 保留 | — |
| description | 保留 | — |
| zoneUuid | API 参数 | 必填 |
| clusterUuid | API 参数 | 必填，赋值到 VO |
| hostUuid | 设 null | 注册后 VM 为 Stopped 状态 |
| lastHostUuid | 设 null | 新环境无意义 |
| instanceOfferingUuid | 设 null | 新环境可能不存在 |
| imageUuid | 保留原值，目标环境不存在时置 null | 若 `dbf.findByUuid(imageUuid, ImageVO.class) == null` 则 `imageUuid = null`，并在 warnings 中记录 `"imageUuid {xxx} not found in target environment, set to null"` |
| cpuNum | 保留 | — |
| memorySize | 保留 | — |
| platform | 保留 | — |
| architecture | 保留 | — |
| hypervisorType | 保留 | — |
| type | 保留 | 保持原值（UserVm） |
| state | 硬编码 | Registering → Stopped |
| defaultL3NetworkUuid | 设 null | 网络不恢复 |
| managementNetworkUuid | 设 null | 网络不恢复 |
| accountUuid | 替换 | 当前调用者（admin） |

### 1.2 VolumeVO

| 字段 | 处理方式 | 说明 |
|------|----------|------|
| uuid | 保留 | 冲突时拒绝注册 |
| primaryStorageUuid | 替换 | 新主存储 UUID |
| installPath | 替换 | 路径映射（vg uuid / 挂载路径替换） |
| diskOfferingUuid | 设 null | 新环境可能不存在 |
| vmInstanceUuid | 保留 | 与注册 VM UUID 一致 |
| accountUuid | 替换 | 当前调用者 |

### 1.3 VolumeSnapshotVO

| 字段 | 处理方式 | 说明 |
|------|----------|------|
| uuid | 保留 | 冲突时拒绝注册 |
| primaryStorageUuid | 替换 | 新主存储 UUID |
| primaryStorageInstallPath | 替换 | 路径映射 |
| volumeUuid | 保留 | — |
| parentUuid | 保留 | 快照链关系 |

### 1.4 SystemTagVO / ResourceConfigVO

| 字段 | 处理方式 | 说明 |
|------|----------|------|
| id | 自增 | 数据库自动生成 |
| uuid | 重新生成 | `Platform.getUuid()` |
| resourceUuid | 保留 | 指向 VM/Volume 的 UUID 不变 |
| 其余字段 | 保留 | — |

**重要**：元数据中的 SystemTag/ResourceConfig 已在构建时经白名单过滤（见 [Part 1a §4.1](vm-metadata-01a-数据模型与序列化.md#41-systemtagresourceconfig-构建时过滤规则)），注册时**直接恢复到 DB，无需二次过滤**。

### 1.5 VolumeSnapshotGroupVO（快照组）

| 字段 | 处理方式 | 说明 |
|------|----------|------|
| uuid | 保留 | 冲突时拒绝注册 |
| name | 保留 | — |
| description | 保留 | — |
| vmInstanceUuid | 保留 | 与注册 VM UUID 一致 |
| snapshotCount | 保留 | — |
| accountUuid | 替换 | 当前调用者（admin） |
| createDate | 保留 | — |
| lastOpDate | 重新生成 | 注册时间 |

### 1.6 VolumeSnapshotGroupRefVO（快照组引用）

| 字段 | 处理方式 | 说明 |
|------|----------|------|
| id | 自增 | 数据库自动生成 |
| volumeSnapshotGroupUuid | 保留 | FK → VolumeSnapshotGroupVO（同事务内已创建） |
| volumeSnapshotUuid | 保留 | FK → VolumeSnapshotVO（同事务内已创建） |
| volumeUuid | 保留 | FK → VolumeVO（同事务内已创建） |
| deviceId | 保留 | 磁盘设备编号 |
| volumeType | 保留 | Root / Data |
| volumeName | 保留 | — |
| volumeSnapshotName | 保留 | — |
| volumeSnapshotInstallPath | 替换 | 路径映射 |
| snapshotDeleted | 保留 | 反映原始删除状态 |
| volumeLastAttachDate | 保留 | 原始挂载时间 |
| createDate | 保留 | — |
| lastOpDate | 重新生成 | 注册时间 |

### 1.7 VolumeSnapshotReferenceVO（快照引用记录）

| 字段 | 处理方式 | 说明 |
|------|----------|------|
| id | 重新生成 | auto-increment（仅作为存储主键，不作为映射锚点） |
| volumeUuid | 保留原值 | 缓存 VM 的卷 UUID（无 FK，允许悬挂） |
| volumeSnapshotUuid | 保留原值 | 缓存 VM 的快照 UUID（无 FK，允许悬挂） |
| volumeSnapshotInstallUrl | 替换 | 路径映射 |
| directSnapshotUuid | 保留原值 | 无 FK，允许悬挂 |
| directSnapshotInstallUrl | 替换 | 路径映射 |
| treeUuid | 保留 | 指向 VolumeSnapshotReferenceTreeVO.uuid |
| parentId | 直接设 null | 注册场景等效于模板缓存已删除状态，FK `ON DELETE SET NULL` 已将 parentId 置 null，无需映射回填 |
| referenceUuid | 保留原值 | — |
| referenceType | 保留 | — |
| referenceInstallUrl | 替换 | 路径映射 |
| referenceVolumeUuid | 保留 | 子 VM 自己的卷 UUID（FK → VolumeEO CASCADE） |
| createDate | 保留 | — |
| lastOpDate | 重新生成 | 注册时间 |

### 1.8 VolumeSnapshotReferenceTreeVO（快照引用树）

| 字段 | 处理方式 | 说明 |
|------|----------|------|
| uuid | 保留 | 冲突时跳过（幂等，多个子 VM 可能共享同一棵树） |
| rootImageUuid | 保留原值 | 无 FK |
| rootVolumeUuid | 保留原值 | 无 FK，允许悬挂 |
| rootInstallUrl | 替换 | 路径映射 |
| rootVolumeSnapshotUuid | 保留原值 | 无 FK，允许悬挂 |
| rootVolumeSnapshotTreeUuid | 保留原值 | 无 FK，允许悬挂 |
| primaryStorageUuid | 替换 | 新主存储 UUID |
| hostUuid | 按需处理 | Local 存储保留，SharedBlock 设 null |

---

## 2. 跨存储数据盘处理规则

### 2.1 策略

虚拟机的所有磁盘必须位于同一主存储，否则**拒绝注册**。

**原因**：跨存储路径映射规则不统一，快照组跨存储引用不完整，单存储简化所有流程。

**拒绝返回信息（改进）**：`CROSS_STORAGE_REJECTED: VM {vmUuid} has volumes on multiple primary storages: expectedPsUuid={targetPsUuid}, actualPsUuids={ps1,ps2,...}. Registration requires all volumes on one primary storage.`

### 2.2 SnapshotGroup 处理

所有磁盘在同一存储上 → SnapshotGroup 天然完整。SnapshotGroupVO 和 SnapshotGroupRefVO 在同一事务内一次性创建。

---

## 3. 注册虚拟机详细流程

### 3.1 状态流

```
(new) → Registering → Stopped → Starting → Running
              │
              └── 失败 → 回滚删除所有 VO
```

### 3.2 "注册 VM 未首次启动" ResourceConfig

| 时机 | 操作 |
|------|------|
| 注册完成 | 创建 `vm.metadata.registered.not.started` ResourceConfig |
| VM 首次到达 Running 状态 | 删除该 ResourceConfig，立即触发 `markDirty` |
| 存在该 ResourceConfig 时 | 任何 `@MetadataImpact` API 的元数据更新被跳过 |

**注册 VM + 普通 API 交互**：注册完成后、首次启动前，VM 处于 Stopped 状态且持有 `registered.not.started` ResourceConfig。
此时若执行 `APIUpdateVmInstanceMsg`（改名/描述），`VmMetadataUpdateInterceptor.afterCompletion()` 检测到
ResourceConfig 存在，跳过 `markDirty`。改名/描述的变更不会即时反映到元数据中。
**这是设计意图**：注册 VM 在未启动前具有完整的原始状态元数据（Step 7 markDirtyInternal 已写入）。
用户在 Stopped 阶段做的修改（改名等）将在 VM 首次到达 Running 时通过删除 ResourceConfig + markDirty 一次性同步。
若业务上无法接受此延迟，可通过 `APIUpdateVmMetadataMsg` 手动触发（该 API 绕过 ResourceConfig 检查）。

### 3.3 完整注册步骤

```
1. 前置校验
    ├── 元数据 JSON 解析 + Base64 解码 + Validator 校验
    ├── readStatus 可用性检查（见下方说明）
    ├── vmCategory 类型检查
   │   ├── REGULAR / TEMPLATE → 继续注册
   │   ├── TEMPLATE_CACHE → 拒绝注册
   │   └── null（旧版元数据） → 视为 REGULAR，继续
   ├── schemaVersion 精确匹配检查（见 Part 1a §6.2）
   ├── 跨存储校验：所有 Volume 归属同一目标主存储（见 §2），失败返回 expected/actual PS UUID 列表
   ├── UUID 冲突检测（VM/Volume/Snapshot/SnapshotGroup/SnapshotGroupRef/Reference/ReferenceTree）
   │   ├── **批量检测策略**：所有待检测 UUID 按每批 1000 个分组查询（`SELECT uuid FROM XxxVO WHERE uuid IN (:batch)`），
   │   │   避免单次 IN 子句超过数千个参数时的 SQL 解析性能退化。对于大快照链场景（54 磁盘 × 256 快照）UUID 总数可达万级。
   │   ├── 冲突且是 Registering 遗留 → 幂等回滚后重新注册
   │   └── 冲突且是正常资源 → 拒绝
    └── installPath 替换 + 路径存在性检查（Agent 校验）
        ├── Root Volume installPath 不存在 → BLOCK（拒绝注册）
        └── Data Volume installPath 不存在 → WARN（允许继续）

   readStatus 可用性校验逻辑：

   ```java
   // 从 metadataContent JSON 中提取 __readStatus（Read API 嵌入）
   String readStatus = metadata.get("__readStatus");
   if ("CORRUPTED".equals(readStatus) || "STORAGE_CHANGE_INCOMPLETE".equals(readStatus)) {
       throw new ApiMessageInterceptionException(argerr(
           "METADATA_READ_STATUS_UNUSABLE: metadata readStatus is %s, " +
           "cannot register. Please resolve the storage issue and re-read metadata.",
           readStatus));
   }
   ```

   **背景**：Register API 接收的 `metadataContent` 通常来自 Read API。Read API 在返回时将 `__readStatus` 字段嵌入 JSON 根级别。Register 入口解析此字段，对 `CORRUPTED`（双 Slot 损坏）和 `STORAGE_CHANGE_INCOMPLETE`（存储拓扑变更未完成）状态拒绝注册。`OK`/`NEED_REPAIR`/`RECOVERED`/`DEGRADED` 状态允许继续。若 `__readStatus` 字段不存在（手动构造的 JSON），视为 OK 继续。

   PreCheck 判定示例：

   ```java
   if (volume.isRootVolume() && !pathExists(volume.getInstallPath())) {
       result.add(PreCheckItem.block(INSTALL_PATH_EXIST,
               "Root volume install path does not exist: " + volume.getInstallPath()));
   } else if (!pathExists(volume.getInstallPath())) {
       result.add(PreCheckItem.warn(INSTALL_PATH_EXIST,
               "Data volume install path does not exist: " + volume.getInstallPath()));
   }
   ```

2. 创建 VmInstanceVO
   ├── state = Registering
   ├── 打 SystemTag: vmMetadata::registeringMnUuid::{mnUuid}
   ├── 打 SystemTag: vmMetadata::registeringStartTime::{timestamp}
   └── 创建 "注册VM未首次启动" ResourceConfig

3. 还原 SystemTag / ResourceConfig
   ├── 从元数据中解码（Base64 解码）
   ├── 直接恢复到 DB（构建时已过滤，无需二次过滤）
   └── 为每个 SystemTag/ResourceConfig 生成新 UUID（Platform.getUuid()）

4. 创建 VolumeVO
   ├── 替换 primaryStorageUuid、installPath、accountUuid
   └── 还原 volume 级 SystemTag / ResourceConfig

5. 快照还原
   ├── 每棵快照树使用 VolumeSnapshotTree.fromInventories() 构建
   │   ├── 创建 VolumeSnapshotTreeVO
   │   ├── 层级遍历快照树，按顺序创建 VolumeSnapshotVO
   │   └── 校验每个 parentUuid 在已创建集合中存在
   ├── 创建 VolumeSnapshotGroupVO + VolumeSnapshotGroupRefVO
   ├── 创建 VolumeSnapshotReferenceVO + VolumeSnapshotReferenceTreeVO
   └── 事务策略：批量 persist 每 100 条 flush + clear

   **大快照链性能说明**：极端场景下（24 磁盘 × 256 快照 = 6144 个 VolumeSnapshotVO + Group/Ref/Tree 关联记录），单事务写入量可达万级别。当前使用 `batch flush+clear per 100 rows` 缓解 JPA 一级缓存膨胀。若快照总数超过 1000，在 LongJob 进度中记录预计耗时，并在 `warnings` 中提示 `"Large snapshot chain detected ({count} snapshots), registration may take longer than usual"`。v2+ 考虑分卷分事务策略降低 Galera 复制延迟风险。

6. 执行变基（sblk / local / NFS）
   ├── **变基前重新校验 installPath 存在性**（Agent 调用）
   │   └── 若 Root Volume installPath 不存在 → 直接进入回滚路径，不执行 qemu-img 操作
   │       （Step 1 的校验与 Step 6 之间可能经过数分钟 VO 创建，存储侧可能发生变化）
   ├── 幂等：先 qemu-img info 检查当前 backing file
   │   ├── 已指向目标路径 → 跳过
   │   ├── 指向旧路径 → 执行 qemu-img rebase -u
   │   └── 指向异常路径 → 报错
   └── 变基失败 → 整个注册回滚

7. 注册成功
    ├── 更新 VmInstanceVO.state = Stopped + 删除 registeringMnUuid tag（同一事务内，保证原子性）
    ├── 事务提交后立即调用 `markDirtyInternal(vmUuid, true)` ← 绕过 ResourceConfig 检查，确保首次元数据写入
    │   **markDirtyInternal 机制说明**：`markDirtyInternal` 并非一个独立方法，而是直接调用 `markDirty()` 的内部路径。
    │   拑制发生在 `VmMetadataUpdateInterceptor.afterCompletion()` 中——当检测到
    │   `registered.not.started` ResourceConfig 存在时，拦截器 skip `markDirty` 调用。
    │   注册 Step 7 的 `markDirtyInternal` 不经过拦截器，而是从服务内部直接调用
    │   `VmMetadataDirtyMarker.markDirty(vmUuid, storageStructureChange=true)`，因此不受 ResourceConfig 拑制。
    ├── 异步触发 `APICheckVmInstanceMetadataConsistencyMsg(autoRepair=true)` 做注册后一致性复核；若发现不一致，在 Event.warnings 中记录差异项
    └── 返回结果（含 warnings）

    **注意**：`registered.not.started` ResourceConfig **不在此步删除**。该 Config 的完整生命周期见 §3.2：创建于 Step 2 → 抑制 Stopped 阶段的 `@MetadataImpact` API 触发 markDirty → VM 首次到达 Running 时删除并触发 markDirty。
```

### 3.4 sblk 变基详细流程

```
原存储: sblkA (vg_uuid = "123xxx")
新存储: sblkB (vg_uuid = "456xxx")

步骤:
  1. 替换 VO 中 vg uuid: 123xxx → 456xxx（前缀锚定替换）
  2. 校验替换后 installPath 在存储上存在
  3. 创建所有 VO
  4. 变基: qemu-img rebase -u -b <新backing路径> <当前LV路径>
```

**路径替换安全机制**：使用 `String.startsWith(oldPrefix)` 检查后字符串拼接，路径格式通过正则预校验。

**分隔符边界保护**：前缀锚定时要求 `oldPrefix` 以路径分隔符结束（如 `/oldVg/`），并验证替换点满足边界条件，避免将 `oldVg` 误命中为 `oldVg2` 或其他子串。

### 3.5 Registering 状态 VM 的可见性

- `Registering` 状态 VM **仅 admin 可见**
- 普通用户 `QueryVmInstance` 自动过滤
- admin 可查询但变更操作被拦截器拒绝

**Registering 状态 API 拦截实现位置**：在 `VmInstanceBase` 中统一处理，而非在每个 API handler 中单独检查。`VmInstanceBase.handleMessage()` 入口处增加 `state == Registering` 检查：除 `QueryVmInstanceMsg` 和内部注册消息外，所有其他消息均返回 `VM_IN_REGISTERING_STATE` 错误码。这避免了在新增 API 时遗漏添加 Registering 状态拦截的风险。

---

## 4. 注册事务回滚

### 4.1 回滚触发条件

MN 启动时扫描 `state=Registering` 的 VM，检查 `registeringMnUuid` SystemTag：

| tag 中 mnUuid | 条件 | 行为 |
|---------------|------|------|
| = 当前 MN UUID | — | 回滚 |
| ≠ 当前 MN UUID | 该 MN **不在线** | 回滚 |
| ≠ 当前 MN UUID | 该 MN **在线** | 跳过 |

**触发时机**：`managementNodeReady()` / `ManagementNodeLeftEvent` 回调。

### 4.2 回滚操作

注册流程中通过 `Set<String> createdVoUuids` 跟踪每步实际创建的 VO UUID。回滚时仅删除此集合中的记录，而非尝试删除“应该存在”的所有元数据对象——避免崩溃发生在中间步骤时删除未创建的 VO 引发的无效查询或 FK 异常。

按以下顺序删除当前注册创建的所有 VO：

1. VolumeSnapshotReferenceTreeVO（外层对象；**删除前检查依赖**：若该 TreeVO 下仍有其他 VM 的 ReferenceVO 行（多个子 VM 共享同一棵树），则保留 TreeVO 不删除，仅删除当前 VM 的 ReferenceVO 行）
2. VolumeSnapshotGroupRefVO / VolumeSnapshotGroupVO
3. VolumeSnapshotVO
4. VolumeVO（含 SystemTag / ResourceConfig）
5. VmInstanceVO（含 SystemTag / ResourceConfig）

回滚顺序采用“由外到内”原则，优先删除聚合根对象，利用数据库级联减少中途失败导致的残留。

**回滚后防御性清理 SQL**（幂等，可重复执行）：

```sql
DELETE t FROM VolumeSnapshotReferenceTreeVO t
LEFT JOIN VolumeSnapshotReferenceVO r ON r.treeUuid = t.uuid
WHERE r.id IS NULL;
```

**存储数据不删除**：存储上的数据是用户迁移的，不因注册失败而删除。

### 4.3 幂等与可重入

每步 `DELETE` 天然幂等。回滚中途再次崩溃 → 下次启动重新检测到 `Registering` → 继续回滚。

### 4.4 LongJob 超时与取消回滚

- 注册 LongJob 超时时，`cancel()` 必须调用 `rollbackRegistration(vmUuid)`，复用与失败路径相同的回滚逻辑。
- 超时后的中间态 VM 必须保留在 `Registering`，使后续 UUID 冲突检测可识别为“Registering 遗留”并触发幂等回滚重试。
- **LongJob 超时时长来源**：从 `APIRegisterVmInstanceFromMetadataMsg` 的 API timeout 配置推导（默认 30 分钟），而非硬编码。ChainTask 超时 = API timeout + 5 分钟余量。`registeringStartTime` 过期判定同样基于此配置值 + 5 分钟，而非硬编码 35 分钟。这允许运维通过调整 API timeout 统一控制注册超时行为。

---

## 5. 注册场景问题分析

### 5.1 UUID 冲突

注册前批量查询所有涉及的 UUID，任一冲突立即拒绝。检测到冲突时判断是否为 Registering 遗留 → 是 → 回滚后重新注册。

### 5.2 installPath 映射

路径映射采用**自动推导**，无需用户手动提供：

| 存储类型 | 旧路径标识符来源 | 新路径标识符来源 |
|----------|----------------|----------------|
| sblk | 从元数据 VolumeVO.installPath 提取旧 VG UUID | 目标主存储 VG UUID |
| local/NFS | 从元数据 VolumeVO.installPath 提取旧挂载路径 | 目标主存储 mountPath |

**文件不移动**：注册流程中不移动文件。账户替换只在 DB 层面。

### 5.3 元数据损坏/不完整

JSON 解析 / Base64 解码 / 校验器任一步骤失败 → 拒绝注册。sblk 双 Slot 容错机制详见 [Part 4d](vm-metadata-04d-sblk读取与恢复.md)。

### 5.4 快照链变基的幂等性

| 当前 backing file | 行为 |
|-------------------|------|
| 已指向目标路径 | 跳过 |
| 指向旧路径 | 执行变基 |
| 指向其他路径 | 报错 |

### 5.5 部分快照树失败

原子性以 **VM 为粒度**：任一快照树失败 → 整个注册回滚。

### 5.6 并发操作

- Registering 状态 VM 只允许查询
- ChainTask `syncSignature = vm-register-{vmUuid}`
- ChainTask 超时：`timeout = API timeout + 5 分钟余量`（见 §4.4，从 API 配置推导）
- **DB 主键是跨 MN 互斥的最终保证**：`INSERT VmInstanceVO(uuid=xxx, state=Registering)` 的主键重复即拦截并发注册。即使两个 MN 同时对同一 VM 发起注册，先提交的事务成功，后提交的因主键冲突失败。

**注册部分创建窗口分析**：Step 2 创建 VmInstanceVO 与 Step 5 创建快照之间可能经过数分钟（大快照链）。在此窗口内 VM 处于 Registering 状态，只允许查询操作（见此节第一条），且 Registering 状态的 VM 不会被 Poller 处理（无 dirty 行，因 markDirty 发生在 Step 7）。因此部分创建状态对外部操作不可见、不会被操作，安全。

### 5.7 无网卡 VM 的启动行为

注册后 VM 无网卡是允许的状态。推荐流程：先加网卡（`AttachVmNicToVm`），再启动。

#### 5.7.1 为什么不恢复网络

注册不恢复网络（NIC、L3 绑定、IP 分配、安全组），原因如下：

1. **L3 网络拓扑不可迁移**：源环境的 L3 网络 UUID、VLAN ID、CIDR 在目标环境中不存在或不相同
2. **IP 地址冲突风险**：源环境的 IP 可能已在目标环境中被分配给其他 VM
3. **安全组/VPC 规则依赖环境**：防火墙规则引用的 SecurityGroup UUID、VPC UUID 均为环境专属

#### 5.7.2 手动网络恢复步骤

```
1. 查看注册 warnings 中输出的原始 NIC 信息：
   "Original NIC config: {nicUuid, l3NetworkUuid, ip, mac, deviceId}"

2. 在目标环境中选择对应的 L3 网络：
   - 若已有匹配网段的 L3 → 直接使用
   - 若无 → 先创建 L3Network + IP Range

3. 挂载网卡：
   APIAttachVmNicToVmMsg(vmInstanceUuid, l3NetworkUuid)
   → 系统自动分配 IP + MAC

4. （可选）恢复安全组绑定：
   APIAddVmNicToSecurityGroupMsg(securityGroupUuid, vmNicUuids)

5. 启动 VM：
   APIStartVmInstanceMsg(uuid)
```

**注意**：注册时会在 `warnings` 中输出所有原始 NIC 配置信息（`l3NetworkUuid`、`ip`、`mac`、`deviceId`），供运维参考。目标环境中 MAC 地址会重新生成，不会与源环境冲突。

### 5.8 链式克隆虚拟机注册

#### 核心原则

注册后的子 VM 等效于**模板和缓存已被删除**的状态。不恢复模板、缓存及其快照的 DB 记录。

#### 注册流程差异

| 项目 | 普通 VM | 链式克隆子 VM |
|------|---------|--------------|
| VmInstanceVO | 创建 | 创建 |
| VolumeVO | 创建 | 创建 |
| VolumeSnapshotVO | 恢复子 VM 自己的 | 恢复子 VM 自己的 |
| VolumeSnapshotReferenceTreeVO | 不涉及 | check existence → skip or create |
| VolumeSnapshotReferenceVO | 不涉及 | 直接插入，parentId 统一置 null |

#### parentId 处理策略

注册场景等效于**模板缓存已被删除**的状态。缓存 VM 删除时，`VolumeSnapshotReferenceVO.parentId` 的 FK（`ON DELETE SET NULL`，自引用）已将所有子引用的 parentId 置为 null。因此注册时无需映射回填，直接全量插入 `parentId = null` 即可。

```
1. 插入全部 VolumeSnapshotReferenceVO，parentId 统一置 null
2. 无需第二阶段回填
```

**简化理由**：注册的子 VM 不可能依赖缓存 VM 的其他 Reference 行（缓存 VM 未被注册），因此 parentId 引用链在新环境中天然为空。

#### TreeVO 幂等性

多个子 VM 可能共享同一棵树。使用 UUID 做存在性检查：

```java
if (!Q.New(VolumeSnapshotReferenceTreeVO.class)
        .eq(VolumeSnapshotReferenceTreeVO_.uuid, treeVO.getUuid())
        .isExists()) {
    dbf.persist(treeVO);
}
```

---

## 6. 可观测性

### 6.1 运维告警

新增报警器：**更新虚拟机元数据失败**。触发条件：达到最大重试次数仍失败。

### 6.2 一致性检查

`APICheckVmInstanceMetadataConsistencyMsg`：从 DB 构建元数据 → 从存储读取 → 结构化比较。排除 `lastOpDate`、`id`、`managementNodeUuid` 字段。

API 详细定义见 [Part 5 §5](vm-metadata-05-API设计.md#5-检查虚拟机元数据一致性)。

### 6.3 注册预检查

`APIPreCheckVmMetadataRegistrationMsg`：检查 UUID 冲突、PS 可达性、版本兼容等。

API 详细定义见 [Part 5 §6.2](vm-metadata-05-API设计.md#62-注册预检查)。

### 6.4 手动触发元数据更新

`APIUpdateVmMetadataMsg`：指定 vmUuid，手动触发一次全量元数据更新。

API 详细定义见 [Part 5 §6.1](vm-metadata-05-API设计.md#61-手动触发元数据更新)。

---

## 7. 设计决策汇总

| 问题域 | 决策 | 理由 |
|--------|------|------|
| UUID 冲突 | 前置全量检查 + Registering 幂等回滚 | 防重复注册 |
| MN 崩溃 | SystemTag 标记 + 启动扫描 | 防中间状态泄漏 |
| 版本不匹配 | 默认拒绝 + `forceVersionMismatch` 允许强制注册 | 兼顾安全性和灵活性。**`forceVersionMismatch=true` 时字段映射策略**：(1) 目标版本新增但源版本缺失的字段 → 使用 Java 默认值（null/0/false），Gson 反序列化自动处理；(2) 源版本存在但目标版本已移除的字段 → 忽略（Gson 默认丢弃未知字段）；(3) 字段类型变更 → Gson 抛异常，注册失败并在 warnings 中列出被忽略/使用默认值的字段名列表 |
| 路径映射 | 自动推导 + 前缀锚定替换 + 文件存在性检查 | 简单可靠 |
| 跨存储 | 拒绝注册 | 消除复杂性 |
| 跨存储错误信息 | 返回 expected/actual PS UUID 明细 | 运维可直接定位冲突卷 |
| SystemTag 过滤 | 构建时白名单过滤，注册时直接恢复 | 无需二次过滤 |
| 模板 VM | 注册为普通 VM | 纯标记表无业务字段。**注意**：注册后不保留模板身份，若需要恢复模板功能需手动转换（`APIChangeVmInstanceToTemplateMsg`）。注册时 warnings 中记录 `"VM {uuid} was a template VM, registered as regular VM"`。**降级后丢失的能力**：(1) 不能从此 VM 创建链式克隆子 VM；(2) 不能作为模板发布到镜像市场；(3) 不能被其他用户用作创建 VM 的模板。**恢复方式**：调用 `APIChangeVmInstanceToTemplateMsg(vmInstanceUuid)` 将 VM 转回模板 |
| 缓存 VM | 写入元数据但拒绝注册 | 运行态产物，新环境自动创建 |
| 链式克隆子 VM | 仅恢复 Reference 表 | 等效于模板已删除状态 |
| Reference parentId 映射 | 直接置 null（等效模板缓存已删除） | 无需映射回填，简化注册流程 |
| 存储数据 | 注册回滚不删除 | 数据由用户迁移 |
| 变基幂等 | `qemu-img info` 预检查 | 支持安全重试 |
| 文件移动 | 不移动，仅 DB 替换 accountUuid | 避免大文件移动风险 |
| 注册超时 | API timeout + 5 分钟余量 ChainTask + cancel 回滚 | 从 API 配置推导，避免硬编码；防止 LongJob 超时残留 |
| Root 路径不存在 | `INSTALL_PATH_EXIST` 视为 BLOCK | Root 缺失不可启动 |
| 注册后校验 | 触发 ConsistencyCheck | 及早暴露存储/DB 偏差 |

---

## 8. 运维指南：注册失败后的清理

### 8.1 自动清理

MN 启动时自动扫描 `state=Registering` 的 VM 并回滚（见 §4.1）。正常情况下无需手动干预。

### 8.2 手动清理场景

当自动回滚未成功时（极端场景），运维可按以下顺序手动清理：

```sql
-- 1. 查找残留的 Registering VM
SELECT uuid, name FROM VmInstanceVO WHERE state = 'Registering';

-- 2. 按依赖顺序删除（先子后父）
-- 步骤同 §4.2 回滚操作：ReferenceTree→Reference→GroupRef→Group→Snapshot→Volume→VM
DELETE FROM VolumeSnapshotReferenceVO WHERE referenceVolumeUuid IN (SELECT uuid FROM VolumeVO WHERE vmInstanceUuid = '{vmUuid}');
DELETE FROM VolumeSnapshotGroupRefVO WHERE volumeUuid IN (SELECT uuid FROM VolumeVO WHERE vmInstanceUuid = '{vmUuid}');
DELETE FROM VolumeSnapshotVO WHERE volumeUuid IN (SELECT uuid FROM VolumeVO WHERE vmInstanceUuid = '{vmUuid}');
DELETE FROM VolumeVO WHERE vmInstanceUuid = '{vmUuid}';
DELETE FROM VmInstanceVO WHERE uuid = '{vmUuid}';
```

**重要**：存储上的数据不删除。存储数据由用户迁移而来，不因注册失败而清理。

---

## 9. 约束与不変量

| 约束 ID | 约束描述 | 违反后果 | 检查点 |
|---------|----------|----------|--------|
| C-03-1 | `VolumeSnapshotReferenceVO.parentId` 注册时统一置 null（等效模板缓存已删除状态），不做映射回填 | — | 注册步骤 §3.3-5 与链式克隆 §5.8 |
| C-03-2 | 跨存储（同 VM 卷分布多个 PS）必须拒绝注册，并返回 expected/actual PS UUID 明细 | 错误路径映射、快照组不完整 | 前置校验 §2.1 / §3.3-1 |
| C-03-3 | installPath 前缀替换必须满足分隔符边界（`/oldPrefix/`） | 子串误替换导致路径污染 | 路径映射 §3.4 / §5.2 |
| C-03-4 | 回滚删除顺序必须“由外到内”，并执行空树清理 SQL | Tree/Reference 残留与数据泄漏 | 回滚 §4.2 |
| C-03-5 | 注册 ChainTask 超时从 API timeout 配置推导（+ 5 分钟余量）；LongJob cancel 必须触发 `rollbackRegistration` | Registering 残留、后续冲突误判 | 并发与超时 §4.4 / §5.6 |
| C-03-6 | Root Volume `INSTALL_PATH_EXIST` 缺失必须 BLOCK；Data Volume 可 WARN | 注册成功但 VM 无法启动 | 前置校验 §3.3-1 |
| C-03-7 | 注册成功后必须触发一次 ConsistencyCheck | 存储与 DB 偏差延迟暴露 | 成功收敛 §3.3-7 / 可观测性 §6.2 |
| C-03-8 | PreCheck 与 Register **必须共享同一校验方法**（如 `validateRegistration()`），PreCheck = validate only，Register = validate + execute。新增校验项时只需修改一处 | PreCheck 通过但 Register 失败（或反之），用户体验矛盾 | 前置校验 §3.3-1 / PreCheck §6.3 / [Part 5 §6.2](vm-metadata-05-API设计.md#62-注册预检查) |
