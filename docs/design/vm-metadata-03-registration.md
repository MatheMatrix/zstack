# 注册与运维

| 属性 | 值 |
|------|-----|
| 文档版本 | 2.0 |
| 最后更新 | 2026-03-03 |
| 状态 | 设计中 |

**修订记录**

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| 1.0 | 2026-02 | 初始版本 |
| 2.0 | 2026-03-03 | 重构：跨存储规则改为拒绝注册、schemaVersion 支持强制注册参数、按 PRD 格式重排 |

---

## 目录

1. [注册字段处理矩阵](#1-注册字段处理矩阵)
2. [跨存储数据盘处理规则](#2-跨存储数据盘处理规则)
3. [注册虚拟机详细流程](#3-注册虚拟机详细流程)
4. [注册事务回滚](#4-注册事务回滚)
5. [注册场景问题分析](#5-注册场景问题分析)
6. [扫描虚拟机](#6-扫描虚拟机)
7. [可观测性](#7-可观测性)
8. [设计决策汇总](#8-设计决策汇总)
9. [异常处理 — 错误码定义](#9-异常处理--错误码定义)

---

## 1. 注册字段处理矩阵

### 1.1 VmInstanceVO

| 字段 | 处理方式 | 说明 |
|------|----------|------|
| uuid | 保留 | 冲突时拒绝注册 |
| name | 保留 | — |
| description | 保留 | — |
| zoneUuid | API 参数 | 必填 |
| clusterUuid | API 参数 | 必填，赋值到 VO，决定首次启动调度范围 |
| hostUuid | 设 null | 注册后 VM 为 Stopped 状态 |
| lastHostUuid | 设 null | 新环境无意义 |
| instanceOfferingUuid | 设 null | 新环境可能不存在 |
| imageUuid | 保留 | 不校验存在性，仅在服务端日志记录。若指向不存在的镜像，`ReimageVmInstance`/`CloneVmInstance` 等操作会自行校验失败 |
| cpuNum | 保留 | — |
| memorySize | 保留 | — |
| platform | 保留 | — |
| architecture | 保留 | — |
| hypervisorType | 保留 | — |
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

### 1.5 TemplatedVmInstanceVO（模板虚拟机标记）

当元数据中 `isTemplated = true` 时，注册流程需恢复模板 VM 身份。

| 字段 | 处理方式 | 说明 |
|------|----------|------|
| uuid | 保留 | 与 VmInstanceVO.uuid 一致（FK CASCADE） |
| createDate | 重新生成 | 以注册时间为准 |
| lastOpDate | 重新生成 | 以注册时间为准 |

**不恢复的关联表**：

| VO | 处理方式 | 理由 |
|----|----------|------|
| `TemplatedVmInstanceCacheVO` | 不恢复 | 缓存 VM 是运行态产物，新环境首次从模板创建 VM 时自动创建 |
| `TemplatedVmInstanceRefVO` | 不恢复 | 子 VM 追溯关系属于旧环境，新环境不存在对应子 VM |

---

## 2. 跨存储数据盘处理规则

### 2.1 策略

虚拟机的所有磁盘（根盘 + 所有数据盘）必须位于同一主存储，否则**拒绝注册**。

**原因**：

- 跨存储时路径映射规则不统一（sblk 用 vg uuid 替换、NFS/Local 用挂载路径替换），混合存储的 installPath 无法在一次注册中完成。
- 快照组（SnapshotGroup）跨存储时会出现不完整引用，恢复语义复杂且易出错。
- 单存储注册简化了变基（rebase）、回滚、幂等等所有流程。

### 2.2 校验实现

在注册前置校验阶段，从元数据中提取所有 Volume 的 `primaryStorageUuid`（原值），检查经路径映射后是否全部归属到同一目标主存储。如果存在归属到不同主存储的 Volume，返回 `METADATA_CROSS_STORAGE` 错误并列出不匹配的 Volume UUID 和对应的存储信息。

### 2.3 SnapshotGroup 处理

由于所有磁盘在同一存储上，SnapshotGroup 天然完整——所有 ref 对应的 Volume 均在本次注册范围内。无需处理 incomplete 标记、分步注册、创建或复用等复杂逻辑。

SnapshotGroupVO 和 SnapshotGroupRefVO 在同一事务内一次性创建。

---

## 3. 注册虚拟机详细流程

### 3.1 API 定义

- **仅 admin 账户**可使用注册 API
- 注册结果返回 `warnings` 列表（如 schemaVersion 不匹配提醒、模板 VM 缓存提醒等）
- API 参数 `force`（Boolean，默认 false）：为 true 时 schemaVersion 不匹配也继续注册（见 §3.4 步骤 1）

### 3.2 状态流

```
(new) → Registering → Stopped → Starting → Running
              │
              └── 失败 → 回滚删除所有 VO
```

### 3.3 "注册 VM 未首次启动" ResourceConfig

| 时机 | 操作 |
|------|------|
| 注册完成 | 创建 `vm.metadata.registered.not.started` ResourceConfig |
| VM 首次到达 Running 状态 | 删除该 ResourceConfig，立即触发 `markDirty` 更新元数据 |
| 存在该 ResourceConfig 时 | 任何 `@MetadataImpact` API 的元数据更新被跳过 |

### 3.4 完整注册步骤

```
1. 前置校验
   ├── 元数据 JSON 解析 + systemTags/resourceConfigs Base64 解码 + Validator 校验
   ├── schemaVersion 匹配检查
   │   ├── 匹配 → 继续
   │   ├── 不匹配 + force=false → 拒绝注册（METADATA_VERSION_MISMATCH）
   │   └── 不匹配 + force=true → 继续注册，VO 中缺失的字段置为 null，
   │       warnings 中记录 "schemaVersion mismatch, missing fields set to null"
   ├── 跨存储校验：所有 Volume 必须归属同一目标主存储（见 §2）
   ├── UUID 冲突检测（VM/Volume/Snapshot/SnapshotGroup/SnapshotGroupRef/Reference/ReferenceTree）
   │   ├── 冲突且是 Registering 遗留 → 幂等回滚后重新注册
   │   └── 冲突且是正常资源 → 拒绝
   └── installPath 替换 + 路径存在性检查（Agent 校验）

2. 创建 VmInstanceVO
   ├── state = Registering
   ├── 打 SystemTag: vmMetadata::registeringMnUuid::{mnUuid}
   ├── 打 SystemTag: vmMetadata::registeringStartTime::{timestamp}
   └── 创建 "注册VM未首次启动" ResourceConfig

3. 还原 SystemTag / ResourceConfig
   ├── 为 SystemTag 和 ResourceConfig 生成新 UUID（Platform.getUuid()）
   └── resourceUuid 保持与 VM/Volume UUID 一致

4. 创建 VolumeVO
   ├── 替换 primaryStorageUuid、installPath、accountUuid
   └── 还原 volume 级 SystemTag / ResourceConfig

5. 快照还原
   ├── 获取元数据中所有快照，按所属快照树归类
   ├── 每棵快照树使用 VolumeSnapshotTree.fromInventories() 构建
   │   ├── 创建 VolumeSnapshotTreeVO
   │   ├── 层级遍历快照树，按顺序创建 VolumeSnapshotVO
   │   └── 校验每个 parentUuid 在已创建集合中存在（防御性校验）
   ├── 创建 VolumeSnapshotGroupVO + VolumeSnapshotGroupRefVO
   ├── 创建 VolumeSnapshotReferenceVO + VolumeSnapshotReferenceTreeVO
   └── 事务策略：上述所有 persist 在单个 @Transactional 内完成
       （批量 persist 每 100 条 flush + clear，异常触发回滚 → Saga 补偿链）

6. 执行变基（sblk / local / NFS）
   ├── 幂等：先 qemu-img info 检查当前 backing file
   │   ├── 已指向目标路径 → 跳过
   │   ├── 指向旧路径 → 执行 qemu-img rebase -u
   │   └── 指向异常路径 → 报错
   ├── 变基失败 → 整个注册回滚（删除所有已创建 VO）
   └── 变基成功 → 继续

7. 恢复模板 VM 身份（仅当 isTemplated = true）
   ├── 创建 TemplatedVmInstanceVO（uuid = VM UUID）
   ├── 创建 VmHaVO（haLevel=NeverStop，禁止模板 VM 触发 HA）
   └── 强制 state = Stopped（模板 VM 禁止直接启动）

8. 注册成功
   ├── 更新 VmInstanceVO.state = Stopped
   ├── 删除 registeringMnUuid tag
   └── 返回结果（含 warnings）
```

### 3.5 sblk 变基详细流程

```
原存储: sblkA (vg_uuid = "123xxx")
新存储: sblkB (vg_uuid = "456xxx")

快照链:
  /dev/123xxx/lv1_uuid
  /dev/123xxx/lv2_uuid (backing = /dev/123xxx/lv1_uuid)
  /dev/123xxx/lv3_uuid (backing = /dev/123xxx/lv2_uuid)

注册步骤:
  1. 读取元数据 /dev/456xxx/vm1_uuid_vmmeta
  2. 替换 VO 中 vg uuid: 123xxx → 456xxx（前缀锚定替换）
  3. 构建 installPath 映射（旧路径 → 新路径）
  4. 检查替换后的 installPath 在存储上存在
  5. 创建所有 VO
  6. 变基: 对每个有 backing file 的 LV 执行
     qemu-img rebase -u -b <新backing路径> <当前LV路径>
```

**路径替换安全机制**：使用前缀锚定替换（`String.startsWith(oldPrefix)` 检查后字符串拼接），而非 `replaceFirst()`。路径格式需通过正则预校验（如 sblk 格式 `/dev/{32位hex}/{32位hex}_\w+`）。

### 3.6 local / NFS 变基详细流程

```
原环境: /vms_ds/rootVolumes/acct-user1/vol-xxx/volume1.qcow2
新环境: /vms_ds2/rootVolumes/acct-user1/vol-xxx/volume1.qcow2

注册步骤:
  1. 读取元数据
  2. 替换 installPath 中存储挂载路径: /vms_ds/ → /vms_ds2/
  3. 构建 installPath 映射
  4. 注册时不移动文件（账户替换仅在 DB 层面）
  5. 变基: qemu-img rebase -u 修改 backing file 路径
```

> `qemu-img rebase -u` 只修改文件 backing file 元数据，不合并数据，操作极快。

### 3.7 无网卡 VM 的启动行为

注册后 VM 无网卡是允许的状态：

| 组件 | 行为 |
|------|------|
| `VmAllocateNicFlow` | nics 为空时跳过网卡分配 |
| `StartVmInstance` | `defaultL3NetworkUuid == null` 时不报错，允许无网卡启动 |
| 推荐流程 | 先给 VM 加网卡（`AttachVmNicToVm`），再启动 |

**保留网卡元数据的意义**：后续可能实现网络映射功能，会用到原网卡信息（如原 L3 网络、IP 地址等用于自动映射到新环境网络）。

### 3.8 Registering 状态 VM 的可见性

- `Registering` 状态的 VM **仅 admin 可见**
- 普通用户 `QueryVmInstance` 自动过滤 `state=Registering`
- admin 用户可查询到，但变更操作被拦截器拒绝（仅允许查询和取消注册）

---

## 4. 注册事务回滚

### 4.1 注册期间出错

以 VM UUID 为锚点，按以下顺序删除当前注册创建的所有 VO：

1. VolumeSnapshotReferenceTreeVO / VolumeSnapshotReferenceVO
2. VolumeSnapshotGroupRefVO / VolumeSnapshotGroupVO
3. VolumeSnapshotVO
4. VolumeVO（含 SystemTag / ResourceConfig）
5. TemplatedVmInstanceVO（如有）
6. VmHaVO 模板抑制记录（如有）
7. VmInstanceVO（含 SystemTag / ResourceConfig）

**不操作存储**：存储上的数据是用户迁移的，不因注册失败而删除。

### 4.2 MN 崩溃导致注册中断

MN 启动时扫描 `state=Registering` 的 VM，需满足以下 5 个条件才执行回滚：

| # | 条件 | 说明 |
|---|------|------|
| 1 | `VM.state == Registering` | 处于注册过渡态 |
| 2 | `VM.managementNodeUuid ∈ deadMNs \|\| IS NULL` | 关联 MN 已离线 |
| 3 | `now() - VM.lastOpDate > 30min` | 最后进度心跳超过 30 分钟前，注册流程已卡死（正常流程每步骤更新 lastOpDate，变基期间每 30 秒心跳） |
| 4 | 当前 MN 无此 VM 的活跃 ChainTask | 无人在处理 |
| 5 | CAS: `UPDATE ... SET state='Destroying' WHERE state='Registering' AND mnUuid=?` 成功 | 防并发 |

**注册流程心跳**：注册流程每步骤完成后更新 `lastOpDate` 作为进度心跳。变基步骤（可能持续数分钟）内部启动定时器每 30 秒更新一次 `lastOpDate`，完成后取消。所有心跳操作 best-effort，失败只记 warn 日志。

**触发时机**：MN 启动 / `ManagementNodeLeftEvent` / 每 5 分钟定时。

**MN UUID 判断规则**：

| 条件 | 行为 | 说明 |
|------|------|------|
| tag 中 mnUuid = 当前 MN UUID | 回滚 | 本 MN 上次注册中途中断 |
| tag 中 mnUuid ≠ 当前 MN UUID，且该 MN 不在线 | 回滚 | 原 MN 已崩溃，安全清理 |
| tag 中 mnUuid ≠ 当前 MN UUID，且该 MN 在线 | 跳过 | 另一个 MN 可能正在注册 |
| tag 中 mnUuid 与所有在线 MN UUID 都不同 | 回滚 | 原 MN 已不存在（UUID 变化场景） |
| 超过 `MAX_REGISTERING_TIMEOUT`（30 分钟） | CAS 后强制回滚 | 超时兜底 |

**强制回滚前再次检查**：再次读取 `VmInstanceVO.state` 确认仍为 Registering → CAS 更新 tag `registeringMnUuid → rollingBackMnUuid::{currentMnUuid}` → CAS 成功才执行回滚。

### 4.3 多 MN 并发回滚

集群重启时多个 MN 同时检测到同一 Registering VM。使用 SystemTag CAS 保证只有一个 MN 执行：`UPDATE ... WHERE tag = oldValue` 实现 CAS，只有一个 MN 更新成功。不引入新的 `VmInstanceState` 枚举，不影响现有状态机。

### 4.4 回滚范围确定

采用"标记 + 全量清理"策略：注册第一步创建 VmInstanceVO（Registering 状态）并打上 `registeringMnUuid` tag → 所有后续写入资源关联到该 VM UUID → 回滚时以 VM UUID 为锚点删除所有关联资源 → 最后删除 VmInstanceVO。

### 4.5 回滚本身失败

回滚操作设计为**可重入/幂等**：每次注册前检查目标 VM UUID 是否 Registering 状态 → 是则无条件执行回滚 → `DELETE` 操作天然幂等。DB 持续不可用属于系统级故障，不单独处理。

### 4.6 存储数据不删除

注册回滚只清理 DB 记录，不操作存储。存储上的数据是用户迁移的，不应因注册失败而删除。

---

## 5. 注册场景问题分析

### 5.1 UUID 冲突

**触发场景**：同一份存储被多次注册（误操作）/ 上次注册部分成功后重试 / 跨环境 UUID 碰撞（概率极低）。

**处理方案**：注册前批量查询所有涉及的 UUID，任一冲突立即拒绝并返回冲突明细。

**幂等注册**：检测到 UUID 冲突时判断冲突资源是否为上次注册遗留（检查 VM 是否带有 `vmMetadata::registeringMnUuid::` SystemTag）→ 是遗留（Registering 状态）→ 回滚清理后重新注册 → 资源是正常状态（非 Registering）→ 拒绝。

### 5.2 installPath 映射

API 要求用户提供 `oldPathIdentifier` 和 `newPathIdentifier`，使用前缀锚定替换。

**替换后校验**：校验所有 installPath 包含 `newPathIdentifier` → 不包含则报错拒绝 → 向 Agent 发送检查命令验证路径实际存在。

**Agent 不可达**：注册 API 本身要求主存储在线（需要读取元数据），如果此时存储/Agent 不可达，API 在更早阶段已失败。路径检查不引入额外失败点。

**文件不移动**：注册流程中不移动文件。账户替换只在 DB 层面（`VolumeVO.accountUuid → admin`），文件物理位置不变。移动大文件有失败风险，且跨文件系统移动非原子操作。

### 5.3 元数据损坏/不完整

JSON 解析 / Base64 字段解码 / 校验器任一步骤失败 → 拒绝注册。

**sblk 双 Slot 容错**：Active Slot Checksum 失败 → 切换 Backup Slot → Backup Slot 也失败 → 拒绝注册。Backup Slot 是上一版本数据 → 可能缺少最新快照 → 注册后建议执行"存储一致性扫描"。

**local/NFS 写入完整性**：采用 tmp + fsync + rename 原子写入。NFS v3/v4 同目录 rename 是原子的（RFC 7530）。

### 5.4 快照链变基的幂等性

执行 `qemu-img rebase -u` 前先执行 `qemu-img info` 检查当前 backing file：

| 当前 backing file | 行为 | 说明 |
|-------------------|------|------|
| 已指向目标路径 | 跳过 | 上次执行已成功 |
| 指向旧路径 | 执行变基 | 正常流程 |
| 指向其他路径 | 报错 | 异常状态，需人工介入 |

### 5.5 部分快照树失败

快照还原原子性以 **VM 为粒度**：任一快照树创建失败 → 整个注册回滚（删除所有已创建 VO），不做部分成功。部分成功会导致快照组引用不完整、快照链关系混乱，不如整体重试。

### 5.6 并发操作

- Registering 状态 VM 只允许查询，变更操作被拦截器拒绝
- ChainTask 串行：`syncSignature = vm-register-{vmUuid}`
- DB 主键约束防 UUID 并发创建
- 并发 `dbf.persist()` 触发 `DuplicateKeyException` → 触发回滚
- 回滚只删 `vmInstanceUuid = thisVmUuid` 的资源，不会删并发写入的其他资源

### 5.7 大量快照导致元数据过大

24 盘 × 256 快照，元数据可达 10MB+。消息中直接传输 `encodedMetadata` 字符串（CloudBusImpl3 基于 HTTP 传输，无硬性大小限制）。单次更新峰值约 40MB（DTO + JSON + 消息序列化，主体数据无 Base64 膨胀），对 4–8GB JVM 可接受。ChainTask `maxPendingTasks=1` 天然限制同一 VM 并发。

### 5.8 注册后 VM 启动失败

注册只保证 DB 一致性，不保证 VM 可启动。API 返回 `warnings` 列表提示潜在问题。

### 5.9 同一 VM 在多个存储上都有元数据

- 用户应使用包含实际磁盘数据的存储上的元数据注册
- 使用错误存储的元数据 → installPath 文件存在性检查失败
- 存储迁移成功后触发新存储元数据更新
- 异步清理旧存储上的元数据（清理前 double-check 确认 VM 根盘确实不在旧存储上）

### 5.10 模板虚拟机注册

当元数据 `isTemplated = true` 时，注册流程需额外处理模板 VM 身份恢复。

#### 5.10.1 注册流程差异

| 项目 | 普通 VM | 模板 VM |
|------|---------|----------|
| TemplatedVmInstanceVO | 不创建 | 创建（uuid = VM UUID） |
| VmHaVO 抑制 | 不处理 | 创建 VmHaVO（haLevel=NeverStop） |
| CacheVO | 不涉及 | 不恢复（运行态产物） |
| RefVO | 不涉及 | 不恢复（旧环境关系） |
| 状态 | Stopped | 强制 Stopped（模板 VM 不允许直接启动） |

#### 5.10.2 HA 抑制

模板 VM 在生产环境中通过 `VmHaVO`（`haLevel = NeverStop`）确保模板 VM 不会被 HA 服务自动启动。注册时创建此记录，NeverStop 级别使模板 VM 保持永远 Stopped 状态。用户将模板 VM 转换回普通 VM 后，手动删除 VmHaVO 记录。

#### 5.10.3 CacheVO 不恢复的影响

| 操作 | 行为 |
|------|------|
| 首次从模板创建 VM | 自动触发 Cache VM 创建（Clone + 快照组），耗时较长 |
| 后续从模板创建 VM | Cache VM 已存在，速度恢复正常 |
| warnings | 提示用户："模板 VM 已注册，首次创建子 VM 时将自动创建缓存，耗时可能较长" |

#### 5.10.4 RefVO 不恢复的影响

`TemplatedVmInstanceRefVO` 记录从模板创建的子 VM UUID。旧环境的子 VM 在新环境不存在，恢复 RefVO 会产生悬挂引用。新环境从模板创建新子 VM 时会自动生成新的 RefVO。

#### 5.10.5 注册回滚

模板 VM 注册回滚时，除普通回滚步骤外，额外删除 `TemplatedVmInstanceVO` 和 `VmHaVO`（模板 VM 的 HA 抑制记录）。

---

## 6. 扫描虚拟机

`APIGetVmInstanceMetadataFromPrimaryStorageMsg`（仅 admin，同步 GET）：获取指定主存储上所有虚拟机元数据文件的概要信息。

> **API 详细定义**见 [Part 1 §12.1](vm-metadata-01-design.md#121-获取主存储上的虚拟机元数据列表)。

**API 参数**：`uuid`（主存储 UUID，必填）。一次性返回所有结果，不分页。

| 存储类型 | 扫描方式 |
|----------|----------|
| sblk | 遍历 VG 中所有 LV，筛选 `lv_name.endswith('_vmmeta')` |
| local/NFS | 遍历根盘目录 `{存储挂载路径}/rootVolumes/acct-xxx/vol-xxx/`，筛选 `*_vmmeta` 文件 |

**返回值**：`List<VmInstanceMetadataStruct>`，每项包含 `name`（VM 名称）、`uuid`（VM UUID）、`path`（元数据文件路径）。

> **与原 `APIScanVmMetadataOnPrimaryStorageMsg` 的关系**：原设计提供分页参数（`start`/`limit`），经评估后简化为一次性返回——元数据文件数量与 VM 数量相当，通常不超过数千个，分页增加了客户端复杂度但无显著收益。API 名称统一为 `APIGetVmInstanceMetadataFromPrimaryStorageMsg`。

---

## 7. 可观测性

### 7.1 运维告警

新增报警器：**更新虚拟机元数据失败**。触发条件：达到最大重试次数仍失败。告警内容包含 vmUuid 和 psUuid。告警级别 WARNING。

### 7.2 一致性检查 API

`APICheckVmInstanceMetadataConsistencyMsg`（仅 CLI 运维使用，UI 不展示）：从数据库构建一份元数据 → 从存储读取虚拟机元数据 → 结构化比较 → 一致返回 `consistent: true`，不一致返回 DB 侧和存储侧的完整 JSON 供外部 diff。

> **API 详细定义**见 [Part 1 §12.4](vm-metadata-01-design.md#124-检查虚拟机元数据一致性)。

**比较时排除的字段**：`lastOpDate`（时间戳差异不影响业务语义）、`id`（自增 ID 不属于业务数据）、`managementNodeUuid`（运行时状态）。其余 VO 字段逐字段比较，任何差异都记入 diff 结果。

### 7.3 注册预检查 API

`APIPreCheckVmMetadataRegistrationMsg`（仅 CLI 运维使用）。API 详细定义见 [Part 1 §12.5.2](vm-metadata-01-design.md#1252-注册预检查)。

检查内容：

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

辅助检查项：

| 检查项 | 检查方式 | 级别 |
|--------|----------|------|
| 磁盘文件可读性 | Agent `qemu-img info` | WARNING |
| architecture 兼容性 | 检查目标 Zone 集群 | WARNING |
| hypervisorType 兼容性 | 检查目标 Zone hypervisor | WARNING |
| 元数据完整性 | Validator 全量校验 | ERROR |
| imageUuid 存在性 | 查询 ImageVO | WARNING |

> 预检查是"尽力而为"的辅助工具，不作为注册前置依赖。注册流程内部有自己的校验。

### 7.4 手动触发元数据更新 API

`APIUpdateVmMetadataMsg`（仅 admin CLI 使用，UI 不开放）：指定 vmUuid，手动触发一次全量元数据更新。用于达到最大重试次数后的手动恢复，或升级后单独更新指定 VM 的元数据。

> **API 详细定义**见 [Part 1 §12.5.1](vm-metadata-01-design.md#1251-手动触发元数据更新)。

---

## 8. 设计决策汇总

| 问题域 | 决策 | 理由 |
|--------|------|------|
| UUID 冲突 | 前置全量检查 + Registering 状态幂等回滚 | 防重复注册，支持安全重试 |
| MN 崩溃 | SystemTag 标记 + 启动扫描 + 超时兜底 + CAS 防并发 | 防中间状态泄漏 |
| 版本不匹配 | 默认拒绝 + `force=true` 允许强制注册（缺失字段置 null） | 兼顾安全性和灾难恢复灵活性 |
| 路径映射 | 用户提供标识符 + 前缀锚定替换 + 替换后校验 + 文件存在性检查 | 简单可靠 |
| 跨存储 | 拒绝注册，要求所有磁盘在同一存储 | 消除分步注册/incomplete 等复杂性 |
| 并发控制 | ChainTask 串行 + DB 主键约束 + CAS | 多层防护 |
| 数据损坏 | sblk 双 Slot 容错 + local/NFS 原子写入 + 解码校验 | 多级容错 |
| 大数据量 | 消息直传 + 全局并发控制 | 简化传输，限制资源 |
| 启动失败 | 注册只保证 DB 一致 + 预检查 API | 职责分离 |
| 旧元数据 | 异步清理 + 文件存在性校验 | 异步安全清理 |
| schemaVersion | 数据库版本号（`dbf.getDbVersion()`），`force=true` 允许跨版本注册 | 与数据库 schema 完全一致 |
| SystemTag 过滤 | 白名单注册 + CI 检查 | 新增 tag 自动被发现 |
| VO JSON 范围 | 所有非 @Transient 字段 | id 重生成，createDate 保留 |
| 压缩策略 | 不压缩 | 正常场景 <100KB，简化调试 |
| @MetadataImpact | 显式标注 + CI 强制 | 避免遗漏或误拦截 |
| Resolver 时机 | API 前预捕获 + API 后提交 | 解决 Detach 类操作问题 |
| 变基幂等 | `qemu-img info` 预检查 | 支持安全重试 |
| 文件移动 | 不移动，仅 DB 层面替换 accountUuid | 避免大文件移动风险 |
| 回滚条件 | tag mnUuid + 在线检查 + 超时兜底 + CAS | 防误删，防并发 |
| 不支持的存储 | 静默跳过 | 不影响非容灾用户 |
| Registering 可见性 | 仅 admin 可见 | 避免普通用户困惑 |
| 模板 VM isTemplated | boolean 字段代替完整 VO | TemplatedVmInstanceVO 无业务字段 |
| CacheVO | 不恢复 | 运行态产物，新环境自动创建 |
| RefVO | 不恢复 | 子 VM 追溯属于旧环境，新环境自动生成 |
| 模板 VM HA 抑制 | 注册时创建 VmHaVO（haLevel=NeverStop） | 防止模板 VM 被 HA 自动启动 |
| 模板 VM 状态 | 强制 Stopped | 模板 VM 禁止直接启动 |
| 存储数据 | 注册回滚不删除存储数据 | 存储数据由用户迁移，不因注册失败删除 |

---

## 9. 异常处理 — 错误码定义

| 错误码 | 含义 | 触发场景 |
|--------|------|----------|
| `METADATA_FILE_NOT_FOUND` | 元数据文件不存在 | 读取/注册时文件不存在 |
| `METADATA_CHECKSUM_MISMATCH` | SHA256 校验失败 | sblk Slot 数据损坏 |
| `METADATA_VERSION_MISMATCH` | schemaVersion 不匹配 | 跨版本注册且 force=false |
| `METADATA_UUID_CONFLICT` | UUID 与现有资源冲突 | 重复注册 |
| `METADATA_CROSS_STORAGE` | 磁盘分布在不同主存储 | 跨存储注册 |
| `METADATA_STORAGE_UNREACHABLE` | 存储路径不可达 | Agent 不可用或路径不存在 |
| `METADATA_REBASE_FAILED` | 快照链变基失败 | `qemu-img rebase` 执行失败 |
| `METADATA_LV_SPACE_INSUFFICIENT` | LV 空间不足 | payload 超过 64MB 上限 |
| `METADATA_STORAGE_NOT_SUPPORTED` | 存储类型不支持 | ceph/zbs/vhost 注册 |
| `METADATA_BASE64_DECODE_FAILED` | Base64 解码失败 | systemTags/resourceConfigs 字段 Base64 解码失败 |
| `METADATA_PARENT_UUID_DANGLING` | 快照 parentUuid 悬挂 | 快照链引用完整性校验失败 |
