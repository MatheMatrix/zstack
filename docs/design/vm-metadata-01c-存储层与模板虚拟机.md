# VM 元数据 — 存储层与模板虚拟机

## 目录

1. [存储层元数据](#1-存储层元数据)
   1.6. [存储迁移 Poller 暂停的崩溃恢复](#16-存储迁移-poller-暂停的崩溃恢复)
2. [模板虚拟机与链式克隆元数据](#2-模板虚拟机与链式克隆元数据)
3. [新增/修改代码文件清单](#3-新增修改代码文件清单)
4. [约束与不変量](#4-约束与不変量)

---

## 1. 存储层元数据

### 1.1 元数据存储路径

| 存储类型 | 路径 | 约束 |
|----------|------|------|
| sblk | `/dev/{vg_uuid}/{vm_uuid}_vmmeta` | LV 名 `{vm_uuid}_vmmeta` 长度固定 39（32+7），远小于 LVM 名称上限 128 |
| local/NFS | `{mountPath}/.zstack-vm-metadata/{vm_uuid}.json` | 目录 `.zstack-vm-metadata` 必须以 `0700` 创建 |
| ceph/zbs/vhost | 当前版本不支持，后续按需扩展 | 不创建元数据容器 |

### 1.2 各存储类型实现

**sblk（共享块存储）**

详见 [Part 4a: sblk 概述](vm-metadata-04a-sblk存储协议概述.md) 及其子文档。核心要点：
- LV 命名：`{vm_uuid}_vmmeta`
- 长度安全性：`vm_uuid`（32 字符）+ `_vmmeta`（7 字符）= 39，低于 LVM 128 字符限制，无截断风险
- 二进制格式：Header(4KB) + Slot A + Slot B
- 三阶段原子写入
- 初始大小 4MB，阶梯式扩容至最大 64MB
- LV 初始化时写入 Header 并将完整 payload 写入 Slot A

**local/NFS**

- **`mountPath` 定义**：`mountPath = PrimaryStorageVO.url`。PS 所挂载集群的每台 Host 均有此挂载路径。
- **NFS 前置条件**：NFS PS 的挂载选项已强制 `no_root_squash`（ZStack 创建 NFS PS 时校验并要求），因此 Agent 进程以 root 身份操作元数据文件无权限问题。
- **目录创建**：`.zstack-vm-metadata` 目录的创建采用与 rootVolume 目录相同的逻辑（权限设置 `0700`，owner=root, group=root），防止非特权用户读取跨 VM 元数据文件。NFS 场景下目录自然跨 Host 共享；local 场景下各 Host 独立创建。
- **文件路径**：`{mountPath}/.zstack-vm-metadata/{vm_uuid}.json`（集中式目录，便于扫描）。元数据文件跟随根盘所在 PS，即元数据锚定在根盘位置。
- **初始文件**：`initializeMetadata` 创建元数据文件并写入当前 VM 的完整元数据 payload。若文件不存在则先创建再写入，若已存在则覆盖。创建流程同样使用 tmp + fsync + rename 原子写入路径。
- **writeMetadata 容器自动创建（讨论 Δ-4）**：`writeMetadata` 执行前自动检查 `.zstack-vm-metadata/` 目录是否存在，不存在时自动创建（`mkdir -p` + `chmod 0700`）。此设计将 `initializeMetadata` 和 `writeMetadata` 的容器创建逻辑统一，无需调用方显式区分"首次写入"和"后续更新"。`initializeMetadata` 在语义上仍然保留（VM 创建场景的入口），但底层实现可直接复用 `writeMetadata` 路径。
- **文件内容**：DTO JSON 明文（systemTags/resourceConfigs 为 per-Resource Base64 编码）
- **原子写入**：先写 tmp 文件 → `fsync(fd)` 刷盘 → `os.rename()` 替换 → `fsync(dirfd)` 刷新父目录元数据。`os.rename()` 等价于 Linux `mv`，原子替换目标文件，**rename 成功后 tmp 文件不会残留**（tmp 已变为目标文件）。仅在 write-tmp 完成后、rename 之前崩溃时，会残留一个 tmp 文件。`fsync(dirfd)` 保证 NFS 场景下目录项更新对其他客户端可见。并发安全性由 Poller CAS 认领机制保证（同一时刻只有一个 MN 持有某 VM 的 flush 权限），无需额外文件锁。
- **tmp 文件命名**：
  - 常规写入：`{vm_uuid}.json.tmp`
  - 存储结构变更写入（`storageStructureChange=true`）：`{vm_uuid}.json.sc.tmp`
  - 使用固定命名（非随机），每次写入覆盖同名 tmp 文件，避免崩溃后积累多个残留文件。
- **tmp 文件崩溃清理**：`os.rename()` 成功后 tmp 文件即消失（已成为目标文件），正常运行无残留。仅在崩溃窗口（write-tmp 完成 → rename 之前）会残留一个 `.tmp` 或 `.sc.tmp` 文件。Agent 启动时扫描 `.zstack-vm-metadata/` 目录中的 `*.tmp` 文件并删除即可。
- **写入前 tmp 清理（讨论补充）**：`writeMetadata` 在写入新 tmp 文件前，先删除同名的旧 tmp 文件（若存在）。使用 `os.O_CREAT | os.O_TRUNC` 标志打开 tmp 文件天然实现覆盖，无需显式删除。此设计避免崩溃后 Agent 未重启时旧 tmp 残留影响后续写入。
- **`storageStructureChange` 参数**：当 `storageStructureChange=true` 时，使用 `.sc.tmp` 后缀的 tmp 文件。此区分用于**注册时判断元数据是否可用**：若扫描到 `.sc.tmp` 残留，说明存储迁移写入未完成，该元数据文件的内容可能是迁移前的旧版本，注册时需标记为不可靠。写入逻辑本身（fsync + rename）无差异。
- **完整性校验**：不设 checksum 字段。`rename` 是 POSIX 原子操作，不存在半写文件场景；JSON 解析成功即内容完整。读取时若 `json.loads()` 抛异常 → 视为损坏 → 日志告警 → `markDirty()` → 下轮 Poller 从 DB 全量重建

#### local/NFS 各操作异常分析

**writeMetadata — 原子写入各阶段异常**

写入流程：`open(tmp)` → `write(payload)` → `fsync(fd)` → `close(fd)` → `os.rename(tmp, target)` → `open(dirfd)` → `fsync(dirfd)` → `close(dirfd)`

| 阶段 | 异常类型 | 文件系统状态 | 处理方式 |
|------|---------|-------------|---------|
| **open(tmp) 失败** | `IOError`（磁盘满、权限、目录不存在） | 无 tmp 文件产生，`.json` 不受影响 | Agent 返回错误 → Poller 标记失败 → 指数退避重试 |
| **write(payload) 失败** | `IOError`（磁盘满、NFS 超时） | tmp 文件可能含部分数据，`.json` 不受影响 | `finally` 中 `close(fd)` + 尝试 `os.remove(tmp)`；Agent 返回错误 |
| **write 成功，fsync(fd) 失败** | `IOError`（NFS server 拒绝刷盘） | tmp 数据可能仅在 client 缓存中，`.json` 不受影响 | 同上：`close` + `remove(tmp)` + 返回错误 |
| **fsync 成功，rename 前 Agent 崩溃** | 进程崩溃/OOM/kill | tmp 文件完整残留在磁盘上，`.json` 为上次成功写入的版本 | Agent 重启时扫描 `*.tmp` 删除。Poller 下轮重试 |
| **os.rename(tmp, target) 失败** | `OSError`（极罕见：跨文件系统 rename、NFS stale handle） | tmp 完整存在，`.json` 为旧版本 | 尝试 `os.remove(tmp)` 清理；Agent 返回错误 → 重试 |
| **rename 成功，fsync(dirfd) 前 Agent 崩溃** | 进程崩溃 | local：数据已持久化（ext4/xfs rename 同步更新目录项）。NFS：目录项更新可能未刷到 server，但 NFS client 重连后会自动同步 | 无需特殊处理。NFS 最坏场景：其他 Host 短暂看到旧文件名 → 下轮 Poller 写入时 fsync(dirfd) 补齐 |
| **fsync(dirfd) 失败** | `IOError`（NFS server 异常） | `.json` 内容已正确（rename 已完成），仅目录元数据未保证刷到 server | Agent 日志告警但**视为成功返回**（数据完整性已由 rename 保证，dirfd fsync 仅影响跨客户端可见性延迟） |

**关键不变量**：在写入流程的任何阶段崩溃或出错，`.json` 文件要么是上一次成功写入的完整版本，要么是本次新写入的完整版本。**不存在读到半写内容的可能**。

**writeMetadata — 异常处理伪代码**

```python
def write_metadata(meta_dir, vm_uuid, payload, storage_structure_change):
    target = os.path.join(meta_dir, f"{vm_uuid}.json")
    suffix = ".sc.tmp" if storage_structure_change else ".tmp"
    tmp = os.path.join(meta_dir, f"{vm_uuid}.json{suffix}")
    
    fd = None
    try:
        fd = os.open(tmp, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
        os.write(fd, payload.encode('utf-8'))
        os.fsync(fd)
        os.close(fd)
        fd = None
        
        os.rename(tmp, target)  # 原子替换，此后 tmp 不再存在
        
        # fsync(dirfd) — best-effort，失败不影响数据正确性
        try:
            dirfd = os.open(meta_dir, os.O_RDONLY)
            os.fsync(dirfd)
            os.close(dirfd)
        except OSError:
            logger.warn(f"fsync(dirfd) failed for {meta_dir}, "
                        "NFS cross-client visibility may be delayed")
    except Exception as e:
        if fd is not None:
            os.close(fd)
        # 清理残留 tmp（rename 前失败时 tmp 可能存在）
        try:
            os.remove(tmp)
        except FileNotFoundError:
            pass  # rename 已成功或 tmp 未创建
        raise e  # 向上层返回错误
```

**initializeMetadata — 异常分析**

| 阶段 | 异常类型 | 处理方式 |
|------|---------|---------|
| `mkdir(.zstack-vm-metadata)` 失败 | 磁盘满、权限 | Agent 返回错误 → 控制面标记 VM 元数据初始化失败 → `markDirty` 后由 Poller 重试 |
| `mkdir` 成功但 `chmod 0700` 失败 | NFS 权限异常 | 已创建目录可能权限不正确 → Agent 返回错误。下次重试时 `mkdir(exist_ok=True)` + 重新 `chmod` |
| 写入 payload 失败 | 同 writeMetadata 各阶段异常 | 同 writeMetadata 处理。目录已创建但文件不存在 → 下次 `initializeMetadata` 或 Poller `writeMetadata` 时创建 |

`initializeMetadata` 使用与 `writeMetadata` 相同的 tmp+rename 原子路径，因此文件层面的异常处理完全一致。额外关注点仅在目录创建阶段。

**readMetadata — 异常分析**

| 异常类型 | 处理方式 |
|---------|---------|
| 文件不存在（`FileNotFoundError`） | 返回 `null` → 调用方判断：可能是 VM 新创建尚未初始化，或文件被误删 |
| 文件存在但读取失败（`IOError`） | 返回错误 → 调用方按失败处理（重试或告警） |
| 文件内容非法 JSON（`json.loads()` 异常） | 视为损坏 → 返回错误 → 控制面 `markDirty()` → 下轮 Poller 从 DB 全量重建覆盖写入 |
| NFS stale file handle | Agent 返回错误 → Poller 重试（NFS 重连后恢复） |
| `.sc.tmp` 残留文件检测（讨论补充） | `readMetadata` 读取 `.json` 文件时，同步检查同目录是否存在 `{vm_uuid}.json.sc.tmp` 文件。若存在，说明存储迁移写入未完成（write-tmp 成功但 rename 前崩溃），在返回结果中标记 `storageChangeIncomplete=true`，注册端据此拒绝注册（readStatus = `STORAGE_CHANGE_INCOMPLETE`）。普通 `.tmp` 残留不影响 readStatus（仅代表普通写入中断，`.json` 文件本身仍为上次成功写入的完整版本） |

**deleteMetadata — 异常分析**

| 异常类型 | 处理方式 |
|---------|---------|
| 文件不存在（`FileNotFoundError`） | **视为成功**（C-01C-9 幂等约束） |
| 删除失败（`IOError`/权限） | Agent 返回错误 → 控制面同步重试（3 次指数退避：30s/60s/120s）→ 仍失败则残留为孤儿文件，由 Part 2b §8.3 巡检清理。注：VM 删除后 FK CASCADE 已清除 dirty 行，Poller 无法介入 |
| 同时删除 `.tmp`/`.sc.tmp` 残留 | `deleteMetadata` 除了删除 `.json` 文件外，还应尝试删除同名的 `.json.tmp` 和 `.json.sc.tmp`（如存在），避免孤儿 tmp 残留。删除 tmp 失败不影响主操作成功 |

**NFS 特有异常场景**

| 场景 | 表现 | 影响 | 处理 |
|------|------|------|------|
| **NFS server 宕机** | 所有文件操作阻塞/超时返回 `EIO` | Poller flush 全部失败 | 指数退避重试。NFS 恢复后自动恢复正常。不影响 VM 运行 |
| **NFS client 端缓存过期** | `readMetadata` 可能读到旧版本 | 扫描/注册场景可能看到过期数据 | 可接受：注册场景会做额外校验；Poller 下轮 flush 覆盖 |
| **NFS mount 断开（`ESTALE`）** | 文件操作返回 `errno=116 ESTALE` | 同 NFS server 宕机 | 同上。Agent 应捕获 `ESTALE` 并返回可重试错误码 |
| **多 Host 并发操作同一 `.json`** | 理论上不会发生（Poller CAS 保证单 MN 持有） | — | 防御性措施：若检测到文件被意外修改（mtime 变化），日志告警但不中断写入 |

### 1.3 MetadataStorageHandler 接口

不同存储类型的元数据读写操作通过统一接口抽象：

```java
public interface MetadataStorageHandler {
    void initializeMetadata(String psUuid, String vmUuid, String payloadJson, Completion completion);
    void deleteMetadata(String psUuid, String vmUuid, Completion completion);
    void writeMetadata(String psUuid, String vmUuid, String payloadJson,
                       boolean storageStructureChange, Completion completion);
    void readMetadata(String psUuid, String vmUuid, ReturnValueCompletion<String> completion);
    boolean isMetadataSupported(String psType);

    /**
     * 扫描指定 PS 上所有元数据条目，返回 VmMetadataEntry 列表（轻量级，不读取 payload）。
     * sblk: 扫描 VG 中所有 *_vmmeta LV，提取 vmUuid 前缀
     * local/NFS: 列举 .zstack-vm-metadata/ 目录下 *.json 文件名
     * 用途: MetadataOrphanDetector (Part 2b §8.4.2)、Scan API (Part 5 §2)
     *
     * 返回类型变更说明（讨论 Δ-7）：原方案返回 List<String>（纯 vmUuid），
     * 改为返回 List<VmMetadataEntry>，其中 VmMetadataEntry 包含：
     *   - vmUuid: String — 虚拟机 UUID
     *   - hostUuid: String — 对于 Local Storage，标识元数据文件所在 Host；
     *                        对于 SharedBlock/NFS 等共享存储，hostUuid 可为 null。
     * 原因：Local Storage 场景下扫描需要逐 Host 执行，调用方需要知道元数据
     * 位于哪台 Host 上以便后续操作（如孤儿清理、注册时路由）。若仅返回 vmUuid，
     * 调用方无法区分同一 PS 不同 Host 上的元数据条目。
     */
    void scanMetadataVmUuids(String psUuid, ReturnValueCompletion<List<VmMetadataEntry>> completion);

    /**
     * 元数据扫描结果条目。
     */
    class VmMetadataEntry {
        private String vmUuid;
        private String hostUuid;  // nullable: SharedBlock/NFS 场景为 null
    }
}
```

**重要设计约束**：Agent 端不解析 DTO 内容。控制面负责 DTO 的构建、序列化和反序列化。Agent 只负责将 payload 原样写入/读取。

| 实现类 | 存储类型 | initializeMetadata | writeMetadata | readMetadata | deleteMetadata |
|--------|---------|-------------------|---------------|--------------|----------------|
| `SblkMetadataStorageHandler` | SharedBlock | 创建 LV + 写 Header + 写入完整 payload | 三阶段原子写入 LV | 读 Header + Active Slot | `lv_delete` |
| `LocalNfsMetadataStorageHandler` | Local/NFS | 创建文件 + 写入完整 payload | tmp（区分 `.tmp`/`.sc.tmp`）+ fsync + rename | 读 JSON（解析失败视为损坏） | `os.remove()` |

**Handler 动态路由**（SM-07 修复）：`MetadataStorageHandler` 通过 `psUuid` 参数动态路由——每次调用时根据 `PrimaryStorageVO.type` 查找对应 Handler 实现，支持同一迁移流程中源/目标使用不同 Handler。例如 VM 从 SharedBlock 迁移到 NFS 时，Step 4 `initializeMetadata(targetPsUuid)` 路由到 `LocalNfsMetadataStorageHandler`，Step 7 `deleteMetadata(sourcePsUuid)` 路由到 `SblkMetadataStorageHandler`。

### 1.4 元数据生命周期

| 事件 | 行为 |
|------|------|
| 新创建虚拟机 | 自动创建元数据文件 |

**VM 创建失败时的元数据清理**：`APICreateVmInstanceMsg` 的 FlowChain 在末尾 Flow 调用 `initializeMetadata` + `markDirty`。若 FlowChain 中更早的 Flow（如分配 IP、创建磁盘）失败，FlowChain 的 rollback 机制会回退所有已完成 Flow（包括 VmInstanceVO 本身通过 `VmAllocateVolumeFlow.rollback` 等清理）。由于 `initializeMetadata` Flow 尚未执行，存储侧不存在元数据文件，无需清理。若 `initializeMetadata` 本身执行成功但后续 Flow 失败（极端场景），`VmCreationRollbackFlow` 应包含 `deleteMetadata` 调用清理残留。若 `initializeMetadata` 执行失败，FlowChain rollback 删除所有已创建 VO，FK CASCADE 清理 dirty 行（如有），孤儿文件由 Part 2b §8.4 巡检兜底。

| VM 删除 | 同步删除元数据文件；删除失败时同步重试（3 次指数退避），仍失败 → 孤儿 LV/文件残留 → 由健康巡检（[Part 2b §8.3](vm-metadata-02b-高可用与运维.md#83-vm-销毁时的元数据清理)）兜底清理。注意：VM 删除后 FK CASCADE 已删除 `VmMetadataDirtyVO` 行，Poller 无法介入，因此使用同步重试 |

**元数据删除时机（讨论 Δ-5）**：元数据文件的删除发生在 **ExpungeVmInstanceFlow**（物理删除阶段），而非 DestroyVmInstanceFlow（软删除阶段）。原因：
1. DestroyVm 仅执行软删除（`VmInstanceVO` → `VmInstanceEO`），VM 可通过 `APIRecoverVmInstanceMsg` 恢复。若在 Destroy 时删除元数据，Recover 后元数据丢失且无法自动恢复（需手动触发全量刷写）。
2. Expunge 是不可逆的物理清除，此时删除元数据是安全的（VM 不可能再恢复）。
3. Destroyed 状态的 VM 已被 Poller 的前置检查过滤（Part 2 §4.4），不会执行无效刷写。
4. Destroy → Expunge 窗口内元数据保留不影响存储空间（元数据文件通常 <500KB）。

**deleteMetadata 重试参数可配**：当前硬编码 3 次重试（30s/60s/120s）。改为通过 GlobalConfig 配置：`vm.metadata.delete.maxRetry`（默认 3）、`vm.metadata.delete.baseDelaySec`（默认 30）。计算方式：`baseDelay × 2^(retryIndex)`，与 Poller 退避公式一致。此配置项添加到 Part 2b §13 GlobalConfig 汇总表。

| 存储迁移 | 暂停 Poller → 数据迁移 → DB 更新 → 目标端初始化写入与校验 → 恢复 Poller + markDirty → 源端清理 |
| 不支持的存储类型 | 静默跳过，不创建元数据文件 |

**存储迁移场景分类**（SM-08 修复）：

| 场景 | 条件 | 元数据处理 |
|------|------|-----------|
| **(A) 整 VM 存储迁移** | Root Volume 参与迁移（含或不含 DataVolume） | 执行完整 7-step 流程：暂停 Poller → 数据迁移 → DB 更新 → 目标端初始化写入 + read-back 校验 → 恢复 Poller → 源端清理 |
| **(B) 单 DataVolume 迁移** | 仅 DataVolume 迁移，Root Volume 不动 | 元数据锚定在 Root PS，**无需**暂停 Poller / initializeMetadata / deleteMetadata。迁移完成后仅需 `markDirty(vmUuid, true)` 触发 Poller 重写（因 `VolumeVO.installPath` 已变更，payload 需更新） |

以下 7-step 流程仅适用于场景 (A)。场景 (B) 的判断依据：迁移的卷列表中不包含 `VolumeVO.type = Root` 的卷。

**存储迁移时的元数据生命周期**（强一致路径，失败阻断源端清理）：

```
Step 1: 暂停该 VM 的 Poller flush
        INSERT IGNORE INTO VmMetadataDirtyVO (vmInstanceUuid, dirtyVersion, storageStructureChange) VALUES (:vmUuid, 1, 1)
        UPDATE VmMetadataDirtyVO SET nextRetryTime='2099-12-31 23:59:59' WHERE vmInstanceUuid=:vmUuid
```

**Step 1 INSERT IGNORE 说明（讨论 Δ-6）**：原方案直接 UPDATE `nextRetryTime`，但若该 VM 当前无 dirty 行（已被 Poller 成功处理后删除），UPDATE 将匹配 0 行，后续 Step 6 恢复 Poller 时也无行可操作。改为先 `INSERT IGNORE`（确保 dirty 行存在）再 `UPDATE`（设定暂停哨兵值），与 `markDirty()` 的两步语义保持一致。`storageStructureChange=1` 因为存储迁移必然涉及存储拓扑变更。
Step 2: 数据迁移（卷/快照）
Step 3: DB 更新（VolumeVO.primaryStorageUuid/installPath）
Step 4: initializeMetadataOnTargetPS(vmUuid, targetPsUuid)
        # DB 已指向目标端，payload 基于最新 DB 构建，installPath 已是目标端路径。
        # 同时完成容器创建（sblk LV / NFS 目录）和正确 payload 写入，无 stale 数据。
        # SM-03 交叉引用：storageStructureChange=true → OP type=2 STORAGE_CHANGE，参见 Part 4c §3
Step 5: readMetadata(targetPsUuid, vmUuid) + readStatus 校验 + JSON 可解析性验证（参见 D-1c-1，不设 checksum）
Step 6: 恢复 Poller（nextRetryTime=NULL）并 markDirty(vmUuid, true)
Step 7: deleteMetadataOnSourcePS(vmUuid, sourcePsUuid)
```

Step 5 为必选保护：在源端清理前，目标端必须已有完整元数据，禁止仅依赖异步 Poller 首刷。

**为什么 initializeMetadata 在 DB 更新之后执行**：在 §1.4 流程中，`initializeMetadataOnTargetPS` 被安排在 Step 4（DB 更新之后），而非数据迁移之前。这一设计消除了 stale payload 问题——payload 基于最新 DB 构建，`installPath` 已指向目标端，无需额外覆盖写入。若采用旧方案（在数据迁移前预创建元数据），payload 中的 `installPath` 仍指向源 PS，需要后续步骤覆盖，且在 MN 崩溃时会产生内容过期的孤儿元数据。当前方案的优势：(1) 一次写入即正确，无 stale 数据；(2) 缩小孤儿窗口——仅 Step 4 成功后、Step 5~6 失败时才需要 SM-01 回滚清理目标端残留；(3) sblk 场景下无需先创建空 LV 再覆盖。

**Step 6 `markDirty` 防御性设计理由**（SM-05 修复）：Step 4 已同步写入完整元数据到目标端，Step 6 的 `markDirty(vmUuid, true)` 看似冗余，但作为防御性措施是必要的——Step 2~5 执行期间可能有其他 API 修改 VM 配置（如热插拔网卡、修改 HA 级别），导致 Step 4 同步写入的内容与最新 DB 状态存在微小时间差。`markDirty` 确保 Poller 异步刷写能将目标端元数据收敛到最终一致状态。

**迁移清理前 double-check（Root Volume）**：

```java
String rootPsUuid = Q.New(VolumeVO.class)
    .eq(VolumeVO_.vmInstanceUuid, vmUuid)
    .eq(VolumeVO_.type, VolumeType.Root)
    .select(VolumeVO_.primaryStorageUuid)
    .findValue();
if (oldPsUuid.equals(rootPsUuid)) {
    logger.warn("VM {} root volume still on source PS {}, skip metadata cleanup", vmUuid, oldPsUuid);
    return;
}
String targetPayload = metadataStorageHandler.readMetadata(targetPsUuid, vmUuid);
if (targetPayload == null || targetPayload.trim().length() <= 2) { // 防御性检查：异常场景下可能出现空文件或损坏文件
    logger.warn("VM {} target metadata is empty, skip source cleanup", vmUuid);
    return;
}
metadataStorageHandler.deleteMetadata(oldPsUuid, vmUuid, ...);
```

元数据锚定在根盘所在 PS，因此 double-check **仅需校验根盘**的 `primaryStorageUuid` 是否仍在源 PS 上。DataVolume 的位置不影响元数据存储位置。

**C-01C-9**（约束）：`deleteMetadata` 必须幂等——删除不存在的元数据（LV 已删除或 JSON 文件不存在）必须返回成功（不抛异常）。`SblkMetadataStorageHandler.deleteMetadata` 中 `lv_delete` 对不存在的 LV 应返回 0（非错误）；`LocalNfsMetadataStorageHandler.deleteMetadata` 中 `os.remove()` 应捕获 `FileNotFoundError` 并视为成功。

**失败回滚策略**：Step 2-6 任一步失败，必须执行 `nextRetryTime=NULL` 恢复 Poller，且不得执行 Step 7。后续通过 `markDirty(vmUuid, false)` 回到源路径刷写。

**SM-01 修复：Step 4 成功后的目标端清理**：若 Step 4 `initializeMetadataOnTargetPS` 已成功（目标端 LV 或 JSON 文件已创建并写入 payload），Step 5~6 任一步失败时，回滚必须先执行 `deleteMetadata(targetPsUuid, vmUuid)` 清理目标端残留，再恢复 Poller。不清理会导致：(1) 后续重试时 `initializeMetadata` 报"已存在"错误；(2) 目标端残留成为孤儿资源。回滚顺序：`deleteMetadata(target)` → `nextRetryTime=NULL` → `markDirty(vmUuid, false)`。

**flush 路径解析策略**（关联 [Part 2b §8.2](vm-metadata-02b-高可用与运维.md#82-路径指纹巡检--轻量级漂移检测)）：

- `doFlush()` 每次都从当前 `VolumeVO.installPath/primaryStorageUuid` 动态解析目标 PS
- 禁止缓存上一次 flush 的 psUuid/path
- 迁移回滚后下一轮 flush 自动回到源 PS（对应 Q2b-7 修复点）

**存储迁移时序分析**（QX-1 全链路一致性）：

```
T1: pause poller(nextRetryTime=FAR_FUTURE)                           — 对应 Step 1
T2: 数据搬迁（卷/快照）                                               — 对应 Step 2
T3: DB installPath/psUuid 切换为 target                              — 对应 Step 3
T4: initializeMetadataOnTargetPS(vmUuid, targetPsUuid)               — 对应 Step 4
    # DB 已指向目标端，payload 基于最新 DB 构建，installPath 已是目标端路径。
    # 同时完成容器创建（sblk LV / NFS 目录）和正确 payload 写入，无 stale 数据。
    # SM-02 崩溃窗口：T4→T5 之间若 MN 崩溃，目标端已创建元数据但尚未校验。
    # DB 已指向目标端，§1.6 崩溃恢复重置 nextRetryTime 后，Poller 基于当前 DB
    # 写入目标端，自动收敛到正确状态。无孤儿风险（DB 与元数据位置一致）。
T5: readMetadata + read-back verify + JSON 可解析性验证              — 对应 Step 5
T6: resume poller(nextRetryTime=NULL) + markDirty(vmUuid, true)      — 对应 Step 6
T7: root/data volume 双重校验 + target 非空校验 → deleteMetadata     — 对应 Step 7
```

**关键保证**：源端清理前已经完成目标端同步写入和 read-back 校验；Poller 仅作为后续收敛机制，而非迁移正确性的前置条件。

**与旧方案的差异**：早期设计中 `initializeMetadataOnTargetPS` 在数据迁移之前执行（预创建），此时 DB 尚未切换，写入的 payload 包含源端 `installPath`（stale 数据），需后续覆盖。现已调整为 Step 4（DB 更新之后），消除 stale payload 问题，并缩小了 SM-02 崩溃窗口的影响——崩溃后 Poller 恢复即可写入正确的目标端数据，无需孤儿巡检兜底。

### 1.5 不支持的存储类型

| 场景 | 行为 |
|------|------|
| VM 根盘在不支持的存储上 | 静默跳过，不创建元数据文件 |
| `@MetadataImpact` 拦截器触发时 | 检查根盘存储类型，不支持的直接跳过 markDirty |
| 注册 API 指定不支持的存储 | 返回错误 `METADATA_STORAGE_NOT_SUPPORTED` |

**Local Storage + VM 热迁移（非存储迁移）的元数据处理**：VM 热迁移（`APIMigrateVmMsg`）仅迁移 VM 进程，不移动磁盘数据。Local Storage 场景下，VM 热迁移**不支持**（ZStack 约束：Local Storage 的 VM 不允许热迁移，仅允许存储迁移）。因此不存在"VM 迁移到另一 Host 但元数据文件在源 Host Local 磁盘上"的场景。SharedBlock/NFS 场景下热迁移不影响元数据位置（通过共享存储访问）。此场景无需额外处理。

### 1.6 存储迁移 Poller 暂停的崩溃恢复（H3 修复）

**问题**：§1.4 Step 1 将 `nextRetryTime` 设为 `'2099-12-31 23:59:59'` 暂停 Poller，Step 6 恢复为 NULL。若 MN 在 Step 1 之后、Step 6 之前崩溃（或迁移流程异常退出未触发失败回滚），该 dirty 行的 `nextRetryTime` 将永久停留在远未来值，导致该 VM 的元数据刷写被永久阻塞。

**修复方案 — MN 启动扫描 + 自动重置**：

在 `managementNodeReady()` 回调中，Poller 启动前执行一次性扫描：

```java
/**
 * 崩溃恢复：检测并重置被迁移暂停但未恢复的 dirty 行。
 * 判断条件：nextRetryTime > NOW() + 1 hour（正常退避最大值远小于此）
 * 安全性：若迁移确实仍在进行（MN 未崩溃），该行的 managementNodeUuid 不为 NULL，
 *         不会被 Poller 认领；此处仅重置 nextRetryTime 和 retryCount，不修改认领状态。
 */
private void recoverStalledMigrationPauses() {
    // DP-10 修复：改为精确匹配迁移暂停哨兵值 '2099-12-31 23:59:59'，
    //            避免误重置正常指数退避的行（最大退避约 2.8h，远小于此阈值）。
    //            原代码使用 `> TIMESTAMPADD(HOUR, 1, CURRENT_TIMESTAMP)` 存在误重置风险。
    int recovered = SQL.New(
        "UPDATE VmMetadataDirtyVO " +
        "SET nextRetryTime = NULL, retryCount = 0 " +  // SM-09 修复：同时重置 retryCount，给予完整重试配额
        "WHERE nextRetryTime = '2099-12-31 23:59:59'")
        .execute();
    if (recovered > 0) {
        logger.warn("Recovered {} dirty rows with stalled migration pause (nextRetryTime far in future)", recovered);
    }
}
```

**调用时机**：`managementNodeReady()` 中，先调用 `recoverStalledMigrationPauses()`，再启动 Poller（`thdf.submitPeriodicTask()`）。

**安全性分析**：

| 场景 | 行为 | 安全性 |
|------|------|--------|
| MN 崩溃后重启，迁移已失败 | `nextRetryTime` 被重置为 NULL → Poller 正常认领 → 从 DB 全量重建 | (Y) 安全（DB 已反映回滚后状态） |
| MN 崩溃后重启，迁移已成功（Step 4 DB 已切换） | 同上，flush 到新 PS | (Y) 安全（DB installPath 已指向目标） |
| 双 MN 场景，另一 MN 正在执行迁移 | dirty 行 `managementNodeUuid` 不为 NULL → Poller CAS 条件排除 → 不会重复处理 | (Y) 安全（仅重置时间，不抢认领） |
| 正常退避中的 dirty 行（retryCount < max） | 退避最大值 = `baseDelay × 2^maxExponent`（默认 10 × 1024 ≈ 10240s ≈ 2.8h） | (Y) 安全（精确匹配哨兵值，不会误重置正常退避行） |

**已采用精确匹配**（DP-10 修复）：使用 `nextRetryTime = '2099-12-31 23:59:59'` 而非 `> NOW() + 1h`，完全消除误重置正常退避行的风险。最终 SQL：

```sql
UPDATE VmMetadataDirtyVO SET nextRetryTime = NULL, retryCount = 0
WHERE nextRetryTime = '2099-12-31 23:59:59'
```

**与 §1.4 失败回滚的关系**：§1.4 的失败回滚策略（"Step 2-6 任一步失败，必须执行 `nextRetryTime=NULL` 恢复 Poller"）覆盖了**正常失败**场景。本节 H3 修复覆盖的是**异常退出**场景（MN 崩溃、JVM OOM、进程被 kill 等导致回滚逻辑未执行）。两者互补，无冲突。

---

## 2. 模板虚拟机与链式克隆元数据

### 2.1 模板 VM 数据模型概述

```
VmInstanceVO (type = "UserVm", 模板 VM)
  │ uuid (1:1, CASCADE)
  ▼
TemplatedVmInstanceVO                    ← 纯标记表
  ├── TemplatedVmInstanceCacheVO         ← 缓存 VM
  │     └── cacheVmInstanceUuid → VmInstanceVO (缓存 VM)
  │           └── VolumeVO → VolumeSnapshotVO
  │                 └── VolumeSnapshotReferenceVO ← 子 VM 的引用记录
  └── TemplatedVmInstanceRefVO           ← 子 VM 追溯
        └── vmInstanceUuid → VmInstanceVO (子 VM)
```

### 2.2 元数据策略

#### 模板 VM（vmCategory = TEMPLATE）

写入元数据，注册时作为普通 VM 恢复（不恢复模板身份）。

模板 VM 的元数据存储位置与普通 VM 一致：**以 RootVolume 所在 Primary Storage 为唯一存储锚点**，不使用 `TemplatedVmInstanceCacheVO` 的缓存卷位置作为元数据路径来源。

**不纳入元数据的关联表**：

| VO | 理由 |
|----|------|
| `TemplatedVmInstanceVO` | 纯标记表无业务字段，`vmCategory=TEMPLATE` 已标记身份 |
| `TemplatedVmInstanceCacheVO` | 缓存 VM 是运行态产物，跨环境无意义 |
| `TemplatedVmInstanceRefVO` | 子 VM 追溯关系属于旧环境 |

#### 缓存 VM（vmCategory = TEMPLATE_CACHE）

**写入元数据**（供扫描展示），但**拒绝注册**。

- 写入理由：扫描结果中 admin 可识别缓存 VM 身份
- 不注册理由：缓存 VM 是内部运行态资源，新环境自动创建

#### 子 VM / 链式克隆（vmCategory = REGULAR）

作为普通 VM 注册，额外恢复 `VolumeSnapshotReferenceTreeVO` 和 `VolumeSnapshotReferenceVO`。注册后等效于**模板和缓存已被删除**的状态。

### 2.3 VolumeSnapshotReferenceVO/TreeVO 的 FK 约束分析

权威 FK 定义见 [Part 1a §2.4](vm-metadata-01a-数据模型与序列化.md#24-volumeresourcemetadata)。

**DDL 层面 FK 约束摘要**：

| 表 | 字段 | FK 目标 | ON DELETE | 注册安全性 |
|----|------|---------|-----------|------------|
| `VolumeSnapshotReferenceTreeVO` | 所有字段 | **无 FK 约束** | — | (Y) 可直接插入，`rootVolumeSnapshotUuid`/`rootVolumeUuid`/`hostUuid` 等均为逻辑引用 |
| `VolumeSnapshotReferenceVO` | `referenceVolumeUuid` | `VolumeEO.uuid` | CASCADE | (Y) 指向子 VM 的卷，注册时先创建 VolumeVO 即可满足 |
| `VolumeSnapshotReferenceVO` | `treeUuid` | `VolumeSnapshotReferenceTreeVO.uuid` | SET NULL | (Y) 先插入 TreeVO 即可满足 |
| `VolumeSnapshotReferenceVO` | `parentId` | 自引用 `VolumeSnapshotReferenceVO.id` | SET NULL | (Y) 按层级顺序插入 |
| `VolumeSnapshotReferenceVO` | `volumeUuid`, `volumeSnapshotUuid`, `directSnapshotUuid`, `referenceUuid` | **无 FK 约束** | — | (Y) 可引用旧环境不存在的 UUID（逻辑引用） |

**关键结论**：`VolumeSnapshotReferenceTreeVO.rootVolumeSnapshotUuid` 无 FK 到 `VolumeSnapshotVO`，因此注册子 VM 时即使缓存 VM 的快照不存在于新环境，TreeVO 插入也不会违反约束。注册顺序：`VolumeVO`（子 VM 卷）→ `VolumeSnapshotReferenceTreeVO` → `VolumeSnapshotReferenceVO`。

子 VM 的 Reference 记录在缓存 VM 被删除后仍然安全可用。代码层面验证（`VolumeSnapshotReferenceUtils.java`）：

| 操作场景 | 是否需要缓存 VM 的 VolumeSnapshotVO | 原因 |
|---------|:---:|------|
| 删除子 VM 卷 | 否 | `backingVolumeDeletedInDb=true` 时直接走 `deleteBitsOnPs` |
| Flatten 子 VM（无快照） | 否 | `referenceType=VolumeVO` → 直接删 ref |
| Flatten 子 VM（有快照） | 仅子 VM 自己的 | `ref.getReferenceUuid()` 查子 VM 快照 |
| 子 VM 创建快照 | 否 | 仅更新 ref 字段 |
| 子 VM 删除快照 | 仅子 VM 自己的 | 查询条件限制为子 VM 快照 UUID |
| 子 VM 卷路径变更 | 否 | 仅更新 `referenceInstallUrl` |

### 2.4 模板相关 API 的 @MetadataImpact

| 操作 | @MetadataImpact | vmCategory 变化 |
|------|----------------|-----------------|
| 普通 VM 转模板 VM | `CONFIG` | REGULAR → TEMPLATE |
| 模板 VM 转回普通 VM | `CONFIG` | TEMPLATE → REGULAR |
| 从模板创建子 VM（首次） | 不影响模板本身 | 自动创建 TEMPLATE_CACHE |
| 更新模板 VM 属性 | `CONFIG` | 不变 |

---

## 3. 约束与不変量

| 约束 ID | 内容 | 来源章节 |
|---------|------|----------|
| C-01C-2 | sblk LV 名称使用 `{vm_uuid}_vmmeta`，长度计算为 39，必须始终小于 LVM 128 上限 | §1.1, §1.2 |
| C-01C-3 | 模板 VM（TEMPLATE）元数据写入位置锚定 RootVolume 所在 PS，不依赖 cache VM 路径 | §2.2 |
| C-01C-4 | 存储迁移必须在源端清理前完成目标端同步写入与 read-back 校验；禁止仅依赖异步 Poller 首次刷写 | §1.4 |
| C-01C-5 | 存储迁移清理必须校验根盘的 `primaryStorageUuid` 是否仍在源 PS，若根盘仍在源 PS 则不得 deleteMetadata(source) | §1.4 |
| C-01C-6 | flush 路径必须按 `VolumeVO.installPath/primaryStorageUuid` 动态解析，不得缓存历史路径 | §1.4 |
| C-01C-7 | 迁移期间对 dirty 行 `nextRetryTime` 的暂停/恢复必须成对出现；失败回滚时必须恢复 Poller | §1.4 |
| C-01C-8 | MN 启动时必须扫描并重置 `nextRetryTime='2099-12-31 23:59:59'` 的迁移暂停行；该扫描必须在 Poller 启动之前执行 | §1.6 |
| C-01C-9 | `deleteMetadata` 必须幂等——删除不存在的元数据必须返回成功（不抛异常） | §1.4 |
| C-01C-10 | local/NFS 的 tmp 文件使用固定命名（`.tmp`/`.sc.tmp`），Agent 启动时扫描清理 `*.tmp` 残留 | §1.2 |
| C-01C-11 | `MetadataStorageHandler` 接口必须包含 `scanMetadataVmUuids()` 方法，用于孤儿检测和 Scan API | §1.3 |
| C-01C-12 | `deleteMetadata` 重试参数（次数、退避基础延迟）必须通过 GlobalConfig 配置，不得硬编码 | §1.4 |
