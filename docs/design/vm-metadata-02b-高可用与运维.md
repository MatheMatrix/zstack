# VM 元数据 — 高可用与运维

## 目录

7. [双 MN 高可用](#7-双-mn-高可用)
8. [管理平面恢复策略](#8-管理平面恢复策略)
9. [升级后全量刷新](#9-升级后全量刷新)
9a. [功能开关切换处理](#9a-功能开关切换处理)
10. [Payload 大小保护](#10-payload-大小保护)
11. [潜在代价与 tradeoff](#11-潜在代价与-tradeoff)
12. [开发约束清单](#12-开发约束清单)
13. [GlobalConfig 配置项汇总](#13-globalconfig-配置项汇总)
14. [可观测性指标](#14-可观测性指标)
15. [约束与不変量](#15-约束与不変量)

**注意**：章节编号保持与原 Part 2 一致（§7-§13），以保证跨文档引用不变。§1-§6（数据模型、markDirty、Poller、消息链、并发控制）见 [Part 2 — Dirty Mark + Poller](vm-metadata-02-脏标记与Poller.md)。

---

# 7. 双 MN 高可用

## 7.1 为什么不需要 hash 环路由

`VmMetadataDirtyVO` 是 **共享 DB 表**，两个 MN 的 Poller 都能看到。认领通过 **DB CAS** 保证互斥，不依赖 JVM 本地状态——谁先认领谁处理，无需协调"谁是 owner"。

## 7.2 MN 宕机场景（自动恢复）

```
T0:   MN-A Poller 认领 dirty(vm-1)
      DB: {vmUuid:vm-1, managementNodeUuid:MN-A}

T1:   MN-A 宕机

T2:   MN-B 心跳检测 → 删除 ManagementNodeVO(MN-A)
      FK ON DELETE SET NULL → dirty(vm-1).managementNodeUuid = NULL
      ← DB 约束自动完成，无需任何代码！

T3:   MN-B nodeLeft(MN-A) → 延迟 5s 后触发一轮 Poller
      → 发现 vm-1 未认领 → CAS 认领 → 刷写 (Y)
```

**接管延迟**：心跳超时(~30s) + nodeLeft 延迟 5s 触发 ≈ **~35 秒**

**M2 修复 — 延迟可配**：`nodeLeft` 延迟已通过 `vm.metadata.nodeLeft.delaySec`（§13）配置化（默认 5s）。对于网络抖动频繁的环境，运维可适当增大此值（如 10s）以扩大 in-flight flush 收敛窗口；对于需要快速接管的场景可减小至 3s。调整需与 Fence Check（§7.6）配合评估。

增加 `nodeLeft` 回调加速，但引入固定 5s 延迟避免与 dying MN 的 in-flight flush 窗口重叠。

```java
@Override
public void nodeLeft(ManagementNodeInventory inv) {
    // MN 宕机 → FK SET_NULL 已释放其认领的 dirty 行
    // 延迟 5s 再触发，给 dying MN 的 in-flight flush 收敛窗口
    thdf.submit(() -> {
        TimeUnit.SECONDS.sleep(5);
        claimAndFlush();
    });
}

@Override
public void nodeJoin(ManagementNodeInventory inv) {
    // 无需特殊处理，新 MN 的 Poller 正常启动即可
}

@Override
public void iAmDead(ManagementNodeInventory inv) {
    // 本 MN 即将死亡，不做处理
    // FK SET_NULL 会自动释放本 MN 认领的行
}

@Override
public void iJoin(ManagementNodeInventory inv) {
    // 由 managementNodeReady 启动 Poller
}
```

**接管延迟**：心跳超时(~30s) + nodeLeft 延迟 5s 触发 ≈ **~35 秒**。

**最大锁定时间分析**：dirty 行被认领后的最大锁定时间 = MN 心跳超时（默认约 60s） + Poller 间隔（默认 5s）= **~65s**。若 JVM GC pause < 60s，MN 仍存活，dirty 行在 pause 后继续处理；若 GC pause 超过心跳超时 → MN 被判定离线 → FK SET_NULL 释放认领 → 对端 MN 接管。

## 7.3 MN 加入场景（无影响）

```
T0:   MN-A 独自运行，Poller 认领并处理所有 dirty 行
T1:   MN-B 加入
T2:   MN-B Poller 启动 → 与 MN-A Poller 并行运行
      → 两个 Poller 竞争认领 → DB CAS 保证互斥 → 自然负载均衡
```

无需任何特殊处理。两个 Poller 天然分摊工作。

## 7.4 双 MN 负载分配

两个 MN 的 Poller 并行运行，通过 DB CAS 自然竞争：

- CAS `UPDATE ... WHERE managementNodeUuid IS NULL LIMIT N` → 每个 MN 各抢到一部分
- 负载分配取决于 Poller 执行时机，不保证精确 50/50

通常不需要精确均匀分配。如需更均匀可在 claim 查询中按 vmUuid 分片（`vmUuid % 2 = mnIndex`），但这引入了对 MN 数量的依赖，不推荐。

## 7.5 时序验证

### 正常态

```
MN-A: API 成功 → markDirty(vm-1) → INSERT dirty 行
MN-B: Poller → CAS claim → flush → 成功 → DELETE (Y)
→ 任何一个 MN 都可以处理任何 VM 的 dirty 行 (Y)
```

### MN 宕机

```
T0:   MN-A claim dirty(vm-1), 正在刷写
T1:   MN-A 宕机
T~30: MN-B 心跳检测 → 删除 ManagementNodeVO(A)
      → FK SET_NULL → dirty(vm-1).managementNodeUuid = NULL
T~35: MN-B nodeLeft(A) → 延迟 5s 后触发 claimAndFlush()
      → CAS claim vm-1 → flush → 成功 → DELETE (Y)
```

## 7.6 Zombie MN 防护（Fence Check）

GC pause 场景下，MN-A 可能被判定离线后又恢复执行旧任务。为避免 A/B 并发写同一 VM，在真正写 sblk 前增加认领围栏检查（QX-2）：

```java
// doFlush() 内，在发送 Agent 写请求前
VmMetadataDirtyVO dirty = dbf.findByUuid(vmUuid, VmMetadataDirtyVO.class);
if (dirty == null || !Platform.getManagementServerId().equals(dirty.getManagementNodeUuid())) {
    logger.warn("Lost claim on vm {}, abort flush write", vmUuid);
    return;
}
```

说明：fence check 之后到实际 pwrite 之间仍存在微窗口，最终一致性由 sblk 双 Slot + `WriteSequence` 单调递增兜底（读取选择更高 SeqNum）。

**M2 修复 — Fence Check 强化说明**：

Fence Check 的设计目的是**缩小** zombie MN 与接管 MN 并发写入的窗口，而非完全消除。完全消除需要分布式锁（如 etcd lease），成本不可接受。当前方案的安全性层次：

| 防护层 | 机制 | 窗口 |
|--------|------|------|
| Layer 1 | DB CAS 认领互斥 | 正常场景下无并发 |
| Layer 2 | Fence Check（dirty 行认领验证） | 仅 GC pause 后恢复的极端场景 |
| Layer 3 | sblk 双 Slot + WriteSequence 单调递增 | 即使并发写入，读取侧选择更高 SeqNum，保证最终一致 |
| Layer 4 | `nodeLeft` 延迟（默认 5s，§13 可配） | 降低 Layer 2 场景出现概率 |

**运维建议**：若监控发现 `Lost claim on vm` 日志频率升高，应检查 GC 配置或增大 `vm.metadata.nodeLeft.delaySec`。

**nodeLeft 5s + Fence Check 微窗口的残余风险分析**：
MN-A GC pause 恢复后可能在 Fence Check 通过与 pwrite 之间的微窗口内执行写入，同时 MN-B 的 nodeLeft 延迟 claimAndFlush 也在写入。此微窗口无法通过 DB CAS 消除（已脱离 DB 调度）。
**可接受的残余风险**：sblk 双 Slot + WriteSequence 单调递增保证了即使并发写入，读取侧永远选择更高 SeqNum 的 Slot，数据最终一致。非 sblk 存储（local/NFS）使用 atomic write（tmp+fsync+rename），最后一次 rename 覆盖前者，同样最终一致。

### MN 加入

```
T0:   MN-A 独自运行，处理所有 dirty
T1:   MN-B 加入 → Poller 启动
T6:   MN-A Poller: claim 3 rows → flush
      MN-B Poller: claim 2 rows → flush
      → 自然分摊 (Y)
```

---

# 8. 管理平面恢复策略

恢复策略表：

| 触发源 | 检测方式 | 管理平面行为 |
|--------|---------|-------------|
| 刷写达到重试上限 | `onFlushFailure()` | 告警日志 + 删除 dirty 行（下次 API 自动重试） |
| read 返回 NEED_REPAIR | 巡检/读取时 | `RepairMetadataMsg`（4KB Header 写） |
| read 返回 CORRUPTED | 巡检/读取时 | `markDirty(vmUuid)`（全量重写） |
| read 返回 STORAGE_CHANGE_INCOMPLETE | 巡检/读取时 | `markDirty(vmUuid)` |
| VG 空间不足 | Agent 返回错误码 | 告警 + 退避 + 巡检重试 |
| 注册崩溃残留 | MN 启动/定时扫描 | Saga 回滚（5 条件判断） |
| 存储迁移失败 | 迁移 post-hook（`afterMigrateVmStorageFailed`） | 告警 + `markDirty(vmUuid, storageStructureChange=true)` 自愈。`storageStructureChange=true` 确保下轮 Poller 刷写时 OP type=2 (STORAGE_CHANGE)，触发 sblk Agent 端重新定位 Slot。失败回滚同时执行 `deleteMetadata(targetPsUuid, vmUuid)` 清理目标端残留 + `nextRetryTime=NULL` 恢复 Poller。详见 [Part 1c §1.4](vm-metadata-01c-存储层与模板虚拟机.md#14-元数据生命周期) 失败回滚策略 |
| VM 销毁残留 | 销毁 post-hook + 巡检 | 孤儿 LV 检测 + 运维清理 |

## 8.1 重试上限后的恢复策略

采用“告警 + 下次 API 触发自动重试”的简化策略，移除 MetadataStaleEvent → recovery cycle 机制，避免无限重试循环。

当 `retryCount >= maxRetry`（默认 5 次，约 5 分钟）时：

1. **告警**：ERROR 日志记录 vmUuid + 失败原因 + 重试次数
2. **删除 dirty 行**：放弃本轮重试
3. **自然恢复**：下次该 VM 的 `@MetadataImpact` API 成功 → `markDirty()` → 全新重试（retryCount=0）

**为什么不需要 MetadataStaleEvent 恢复机制**：

| 方面 | 旧方案（recovery cycle） | 新方案（告警 + API 重试） |
|------|--------------------------|---------------------------|
| 复杂度 | 需 ResourceConfig 持久化 cycle 计数 + 优先队列 + 定时任务 | 无额外代码 |
| 无限循环风险 | 需 cycle 上限 + permanently stale 标记 | 不存在（只在 API 触发时重试） |
| 恢复时机 | 固定延迟 5 分钟 | 自然发生（下次 API 时） |
| PS 持续故障 | cycle 耗尽后 permanently stale | 每次 API 都重试一轮（5 次退避），不会无限堆积 |

路径指纹巡检作为兗底方案：发现路径漂移时调用 `markDirty()` 触发全新刷写（见 §8.2）。

## 8.2 路径指纹巡检 — 轻量级漂移检测

### 8.2.1 问题：为什么不能读存储比对

原方案"周期性全量比对 DB vs 存储元数据"需要 agent 调用读取存储上的 sblk 文件、解码、反序列化，对每个 VM 都是一次 I/O 操作。对于大规模环境（数千 VM），这个开销不可接受。

### 8.2.2 思路：写时记录路径快照，读时纯 DB 比对

每次 Poller 刷写成功后，将本次构建元数据时用到的**所有 Volume 和 Snapshot 的 installPath** 记录到 DB。一个独立的周期巡检任务从 DB 查询当前路径，与记录的快照比对——**整个过程零存储 I/O**。

### 8.2.3 路径指纹结构

```java
@Entity
@Table(name = "VmMetadataPathFingerprintVO")
public class VmMetadataPathFingerprintVO {
    @Id
    @Column
    @ForeignKey(parentEntityClass = VmInstanceEO.class, onDeleteAction = ReferenceOption.CASCADE)
    private String vmInstanceUuid;       // PK, FK → VmInstanceEO (CASCADE DELETE)

    @Column
    @Lob
    private String pathSnapshot;         // 上次刷写时的路径列表（JSON）

    @Column
    private Timestamp lastFlushTime;     // 记录时间

    @Column
    private boolean lastFlushFailed;    // H2/M1 修复：重试耗尽时置 true，MetadataStaleRecoveryTask 重新入队后置 false

    @Column
    private int staleRecoveryCount;     // Q27 熔断：MetadataStaleRecoveryTask 累计重入队次数，达到上限后停止自动恢复
}
```

**`lastFlushFailed` 字段说明（M1 修复）**：
- **写入时机**：Poller `onFlushFailure()` 中，当 `retryCount >= maxRetry` 时，在删除 dirty 行之前设置 `lastFlushFailed = true`
- **清除时机**：`MetadataStaleRecoveryTask`（见 [Part 2 §4.8](vm-metadata-02-脏标记与Poller.md#48-stale-恢复任务h2-修复)）扫描到该行后调用 `markDirty()` 并重置为 `false`
- **默认值**：`false`（正常刷写成功时不修改此字段）
- **与 §8.1 的关系**：§8.1 的"告警 + 删除 dirty 行 + 下次 API 自动重试"策略保持不变。`lastFlushFailed` 作为补充标记，使得即使没有后续 API 触发，`MetadataStaleRecoveryTask` 也能在 30 分钟内自动发现并重新入队

**无限慢重试回路的熔断机制**：
当 PS 长期不可达时，`lastFlushFailed=true → StaleRecoveryTask markDirty → Poller 5 次重试 → 再次 lastFlushFailed=true → 30min 后再来` 形成无限慢循环。
**熔断设计**：在 `VmMetadataPathFingerprintVO` 增加 `staleRecoveryCount INT DEFAULT 0` 字段，每次 `MetadataStaleRecoveryTask` 重新入队时递增。
当 `staleRecoveryCount >= vm.metadata.staleRecovery.maxCycles`（默认 10，即约 5 小时）时，置 `lastFlushFailed=false`（停止自动重入队），并记录 WARN 日志：
`"VM [{}] metadata stale recovery exceeded {} cycles, entering permanent-stale. Use APIUpdateVmMetadataMsg to manually trigger."`
管理员可通过 `APIUpdateVmMetadataMsg` 手动触发刷写，该 API 的 `markDirty` 调用会重置 `staleRecoveryCount=0`。
这避免了对永久不可达 PS 的无限资源消耗，同时保留了手动恢复能力。

`pathSnapshot` 格式（JSON，便于调试和日志输出）：

```json
{
  "volumes": [
    {"uuid": "vol-aaa", "installPath": "/dev/vg/vol-aaa"},
    {"uuid": "vol-bbb", "installPath": "/dev/vg/vol-bbb"}
  ],
  "snapshots": [
    {"uuid": "sp-001", "installPath": "/dev/vg/sp-001"},
    {"uuid": "sp-002", "installPath": "/dev/vg/sp-002"}
  ]
}
```

列表按 uuid 排序，确保同样的拓扑总是产生相同的 JSON，便于字符串直接比对。

**JSON 字段序确定性保证**：`buildPathJson()` 使用 Gson 序列化简单内部 POJO（仅含 `uuid` 和 `installPath` 两个 String 字段），Gson 按 Java 字段声明顺序输出（非 `@SerializedName` alphabetical），声明顺序在编译后固定。列表层面按 `uuid ASC` 排序。两层确定性保证——字段声明顺序 + 列表排序——确保相同拓扑始终产生 byte-identical JSON。

### 8.2.4 写入时机

Poller 刷写成功 → `deleteRow()` 前，调用 `savePathFingerprint(vmUuid)`：

```java
private void savePathFingerprint(String vmUuid) {
    List<VolumeVO> volumes = Q.New(VolumeVO.class)
        .eq(VolumeVO_.vmInstanceUuid, vmUuid)
        .orderBy(VolumeVO_.uuid, SimpleQuery.Od.ASC).list();
    List<VolumeSnapshotVO> snapshots = Q.New(VolumeSnapshotVO.class)
        .in(VolumeSnapshotVO_.volumeUuid, volumes.stream().map(VolumeVO::getUuid).collect(toList()))
        .orderBy(VolumeSnapshotVO_.uuid, SimpleQuery.Od.ASC).list();

    VmMetadataPathFingerprintVO fp = new VmMetadataPathFingerprintVO();
    fp.setVmInstanceUuid(vmUuid);
    fp.setPathSnapshot(buildPathJson(volumes, snapshots));
    fp.setLastFlushTime(new Timestamp(System.currentTimeMillis()));
    dbf.insertOrUpdate(fp);
}
```

### 8.2.5 巡检 PeriodicTask（Keyset 分页）

```java
public class MetadataPathDriftDetector implements PeriodicTask {
    @Override
    public long getInterval() {
        return VmGlobalConfig.VM_METADATA_PATH_CHECK_INTERVAL.value(Long.class);
        // 默认 300 秒（5 分钟）
    }

    @Override
    public void run() {
        int batchSize = VmGlobalConfig.VM_METADATA_PATH_CHECK_BATCH_SIZE.value(Integer.class); // default 500
        String lastUuid = "";
        while (true) {
            List<VmMetadataPathFingerprintVO> batch = SQL.New(
                "select fp from VmMetadataPathFingerprintVO fp where fp.vmInstanceUuid > :lastUuid order by fp.vmInstanceUuid asc",
                VmMetadataPathFingerprintVO.class)
                .param("lastUuid", lastUuid)
                .limit(batchSize)
                .list();
            if (batch.isEmpty()) {
                break;
            }

            for (VmMetadataPathFingerprintVO fp : batch) {
                String currentSnapshot = buildCurrentPathSnapshot(fp.getVmInstanceUuid());
                if (!fp.getPathSnapshot().equals(currentSnapshot)) {
                    logger.warn("path drift detected for VM [{}], recorded: {}, current: {}",
                        fp.getVmInstanceUuid(), fp.getPathSnapshot(), currentSnapshot);
                    markDirty(fp.getVmInstanceUuid());
                }
            }

            lastUuid = batch.get(batch.size() - 1).getVmInstanceUuid();
        }
    }
}
```

**设计要求**：禁止 `dbf.listAll(VmMetadataPathFingerprintVO.class)` 全量加载。大规模环境必须使用 keyset 分页（`vmInstanceUuid > lastUuid`，因 PK 为 `vmInstanceUuid` 而非自增 `id`）。

**keyset 分页与非事务性间隙**：INSERT IGNORE 和后续 UUID 分页查询不在同一事务中，期间可能有新 VM 创建或旧 VM 销毁。这是可接受的：新 VM 由下轮巡检覆盖；销毁的 VM 因 FK CASCADE 自动清理 dirty 行和 fingerprint 行。不需要额外处理。

### 8.2.6 对比原方案

| | 原方案（读存储比对） | 路径指纹巡检 |
|---|---|---|
| I/O 开销 | 每 VM 一次 agent 调用读 sblk | **零存储 I/O**，纯 SQL |
| 可运行频率 | 分钟级（受 agent 吞吐限制） | 秒级（仅 DB 查询） |
| 检测范围 | 完整内容（含格式/编码差异噪声） | 仅存储拓扑路径变更（精准） |
| 首次可用 | 需 VM 已写过元数据 | 同左（需至少一次刷写记录指纹） |
| 存储拓扑变更 | 100% 检测 | 100% 检测（路径覆盖所有拓扑变更） |
| 非拓扑字段变更 | 可检测（如 size/description） | **不检测**（这些已被 `@MetadataImpact` API 覆盖） |
| 调试友好 | 需读存储 + 解码 | JSON 路径列表直接可读，drift 时日志输出新旧对比 |

### 8.2.7 边界条件

| 场景 | 处理 |
|------|------|
| VM 从未刷写过元数据 | 无指纹记录 → 巡检跳过 |
| VM 已销毁 | FK CASCADE → 指纹记录自动删除 |
| 刷写成功但保存指纹前 MN 崩溃 | 指纹仍是旧的 → 下轮巡检发现 drift → markDirty → 重新刷写 + 更新指纹 |
| markDirty 已调用但尚未刷写 | 巡检发现 drift → 再次 markDirty → 幂等（dirty 行已存在，UPSERT 无副作用） |
| 并发刷写 + 巡检 | 巡检 markDirty 后 Poller 刷写覆盖 → 下轮巡检指纹一致 → 收敛 |

## 8.3 VM 销毁时的元数据清理

在 `ExpungeVmInstanceFlow` 链中增加 `NoRollbackFlow` step：查找根卷所在 PS → `metadataStorageHandler.deleteMetadata()` → **best-effort**，失败仅 WARN 日志，不阻塞 VM 物理清除。

**删除时机说明（讨论 Δ-5）**：元数据文件的删除发生在 Expunge（物理删除）阶段而非 Destroy（软删除）阶段。Destroy 时 VM 可通过 Recover 恢复，删除元数据将导致恢复后无法自愈。Expunge 是不可逆操作，此时删除是安全的。

dirty 行的清理由 FK CASCADE 自动完成（VM 物理删除 → VmInstanceEO 删除 → dirty 行级联删除）。

**VmInstanceEO 软删除时序**：ZStack 的 VM 删除分两阶段：
1. 软删除：`VmInstanceVO` 的 `@SoftDeletionCascade` 将记录从 `VmInstanceVO` 视图移除，但底层 `VmInstanceEO` 行仍存在。此时 FK CASCADE **不触发**，dirty 行保留。若 Poller 此时认领该 VM，Part 2 §4.3 的 Destroyed 状态过滤会跳过。
2. 物理删除：`GarbageCollectorVO` 驱动的清理任务在软删除后数分钟到数小时执行 `DELETE FROM VmInstanceEO WHERE uuid=?`，此时 FK CASCADE 触发，dirty 行 + fingerprint 行被级联删除。
在软删除到物理删除的窗口内，dirty 行存在但被 Poller 的 Destroyed 过滤跳过，不会产生多余 flush 操作。物理删除后所有关联行自动清理。整条链路无需额外处理。

## 8.4 孤儿元数据检测与清理

### 8.4.1 孤儿产生场景

| 场景 | 原因 | 孤儿位置 |
|------|------|----------|
| VM 销毁时 `deleteMetadata` 失败 | Agent 超时/PS 不可用 | 元数据残留在 VM 原根盘所在 PS |
| 存储迁移崩溃（[Part 1c §1.4](vm-metadata-01c-存储层与模板虚拟机.md#14-元数据生命周期) SM-02） | MN 在 Step 2 成功后、Step 8 前崩溃，且未触发回滚 | 目标 PS 上有孤儿元数据 |
| 存储迁移成功但 Step 8 清理失败 | 源 PS 删除元数据失败 | 源 PS 上残留旧元数据 |

### 8.4.2 检测机制 — MetadataOrphanDetector

独立 PeriodicTask，低频运行（默认每小时一次），扫描存储上的元数据并比对 DB 状态：

```java
public class MetadataOrphanDetector implements PeriodicTask {
    @Override
    public long getInterval() {
        return VmGlobalConfig.VM_METADATA_ORPHAN_CHECK_INTERVAL.value(Long.class);
        // 默认 3600 秒（1 小时）
    }

    @Override
    public void run() {
        // 逐 PS 扫描，复用 Scan API 的 Agent 调用
        List<PrimaryStorageVO> allPs = Q.New(PrimaryStorageVO.class)
            .in(PrimaryStorageVO_.type, List.of("SharedBlock", "LocalStorage", "NFS"))
            .eq(PrimaryStorageVO_.state, PrimaryStorageState.Enabled)
            .list();

        for (PrimaryStorageVO ps : allPs) {
            detectOrphansOnPs(ps);
        }
    }

    private void detectOrphansOnPs(PrimaryStorageVO ps) {
        // 1. Agent 扫描该 PS 上所有元数据条目（轻量：仅返回 vmUuid 列表）
        List<String> vmUuidsOnStorage = metadataStorageHandler.scanMetadataVmUuids(ps.getUuid());

        for (String vmUuid : vmUuidsOnStorage) {
            // 2. 检查 VM 是否存在
            VmInstanceVO vm = dbf.findByUuid(vmUuid, VmInstanceVO.class);
            if (vm == null) {
                // VM 已销毁 → 确认孤儿
                reportOrphan(ps.getUuid(), vmUuid, "VM_DELETED");
                continue;
            }

            // 3. 检查 VM 根盘是否在此 PS 上
            String rootPsUuid = Q.New(VolumeVO.class)
                .eq(VolumeVO_.vmInstanceUuid, vmUuid)
                .eq(VolumeVO_.type, VolumeType.Root)
                .select(VolumeVO_.primaryStorageUuid)
                .findValue();

            if (rootPsUuid != null && !rootPsUuid.equals(ps.getUuid())) {
                // 根盘在其他 PS → 此 PS 上的元数据是迁移残留
                reportOrphan(ps.getUuid(), vmUuid, "ROOT_ON_OTHER_PS");
            }
        }
    }

    private void reportOrphan(String psUuid, String vmUuid, String reason) {
        logger.warn("orphan metadata detected: ps={}, vm={}, reason={}", psUuid, vmUuid, reason);
        // 记录审计日志，不自动删除（安全起见）
    }
}
```

### 8.4.3 清理策略

孤儿元数据**仅报告不自动删除**，原因：
- 迁移崩溃后 MN 重启，`recoverStalledMigrationPauses()`（[Part 1c §1.6](vm-metadata-01c-存储层与模板虚拟机.md#16-存储迁移-poller-暂停的崩溃恢复)）重置 `nextRetryTime`，Poller 从 DB 全量重建写入正确 PS，迁移残留自然成为孤儿
- 自动删除有误删风险（如扫描与 DB 查询之间 Root Volume 正在迁移）

运维可通过以下方式按需清理：
1. `APICleanupVmInstanceMetadataMsg`（[Part 5 §6.3](vm-metadata-05-API设计.md#63-清理虚拟机元数据)）指定 `vmUuids` + `primaryStorageUuids` 精确清理
2. sblk：`lvremove {vg}/{vm_uuid}_vmmeta`
3. local/NFS：`rm {mountPath}/.zstack-vm-metadata/{vm_uuid}.json`

### 8.4.4 与 §8.3 的关系

| 场景 | §8.3 处理 | §8.4 兜底 |
|------|-----------|-----------|
| VM 销毁 deleteMetadata 成功 | (Y) 清理完成 | 不会检测到孤儿 |
| VM 销毁 deleteMetadata 失败 | (!) WARN 日志 | 1 小时后检测到 `VM_DELETED` 孤儿 |
| 迁移崩溃残留 | 不涉及（VM 未销毁） | 1 小时后检测到 `ROOT_ON_OTHER_PS` 孤儿 |

## 8.5 主存储卸载/重新挂载时的元数据行为

| 阶段 | 行为 |
|------|------|
| PS 卸载（Detach） | Poller flush 失败（Agent 不可达），dirty 行进入 retry→stale 周期。PathFingerprint 的 `lastFlushFailed=true`。Poller 不主动清理 dirty 行，保留供后续恢复。 |
| PS 保持卸载 | StaleRecoveryTask 周期性重入队 markDirty → 5 次重试失败 → 再次 stale → 最终触发 Q27 熔断，停止自动恢复（约 5 小时后）。WARN 日志提示管理员。 |
| PS 重新挂载（Reattach） | 下一次 API 触发的 `markDirty` 或管理员手动 `APIUpdateVmMetadataMsg` 重新入队。若已熔断，`APIUpdateVmMetadataMsg` 重置 `staleRecoveryCount=0`。Poller 正常 flush 恢复。 |
| 无需特殊处理的原因 | dirty 行和 fingerprint 行在 DB 中持久化，PS 卸载不影响 DB 状态。恢复后 Poller 从 DB 全量读取构建 payload，确保元数据完整。 |

---

# 9. 升级后全量刷新

## 9.1 触发条件

在 `managementNodeReady()` 回调中执行：

1. 查询所有在线 `ManagementNodeVO`，收集 version 集合
2. 若存在多个不同版本（滚动升级中）→ 跳过
3. 版本唯一且与 `lastRefreshVersion`（GlobalConfig 持久化）不同 → 提交延迟 10 分钟的定时任务
4. 10 分钟后再次检查所有 MN 版本是否一致 → 一致则执行全量刷新，不一致则跳过
5. **recent-nodeLeft 检查（M3 修复）**：执行全量刷新前，检查最近 15 分钟内是否有 `nodeLeft` 事件。若有，说明可能仍在滚动升级过程中（旧 MN 刚下线），延迟 10 分钟后重新从步骤 1 开始检查

**M3 修复说明**：滚动升级的典型模式是"停旧 MN → 升级 → 启新 MN"。在这个过程中，可能出现短暂的"版本唯一"假象：
- T0：MN-A(v2) 启动，MN-B(v1) 尚未下线 → 版本不同 → 步骤 2 跳过 (Y)
- T1：MN-B(v1) 下线 → `nodeLeft` 事件
- T2：MN-A(v2) 是唯一 MN → 版本唯一 → 步骤 3 匹配 → 提交延迟任务
- T3（10min 后）：步骤 4 检查 → 仍只有 MN-A → 版本一致 → 触发全量刷新
- T4（但 T3+5min 后）：MN-B(v2) 上线 → **此时已不需要再次全量刷新**

问题在步骤 T3：虽然版本一致，但 MN-B 还未上线。在 MN-B 上线前执行全量刷新是正确的（它也会处理），但若 MN-B 的 `managementNodeReady()` 也触发同样逻辑，会导致**两次全量刷新**。通过 `lastRefreshVersion` 检查可避免重复（步骤 3 的 `lastRefreshVersion != currentVersion` 条件），所以实际安全。

但真正的风险是：升级窗口内旧版 MN 的元数据刷写可能使用旧 schema，全量刷新应确保**所有 MN 都已升级完成**。`recent-nodeLeft` 检查补充了这一保证。

**延迟 10 分钟的原因**：滚动升级期间，第一个 MN 升级完成时可能短暂出现"版本唯一"假象（旧 MN 尚未恢复上线）。

## 9.2 刷新执行（简化，无 LongJob）

不需要 LongJob。直接批量 markDirty，Poller 自动处理。

```java
private void submitFullRefresh(String currentVersion) {
    logger.info("metadata full refresh: starting for version {}", currentVersion);

    // Q24 修复：按 C-DM-01 要求使用 INSERT IGNORE + UPDATE 两步，不使用 ON DUPLICATE KEY
    // 同时使用 keyset 分页替代 OFFSET，避免大数据集性能退化
    int batchSize = VmGlobalConfig.VM_METADATA_UPGRADE_REFRESH_BATCH_SIZE.value(Integer.class); // default 1000
    String lastUuid = "";
    int totalProcessed = 0;

    while (true) {
        // Step 1: INSERT IGNORE — 为尚无 dirty 行的 VM 创建新行
        // storageStructureChange=true（C-SC-07：升级后无法判断存储拓扑是否变化，保守使用 STORAGE 级别）
        int inserted = SQL.New(
            "INSERT IGNORE INTO VmMetadataDirtyVO (vmInstanceUuid, dirtyVersion, storageStructureChange) " +
            "SELECT v.uuid, 1, 1 FROM VmInstanceVO v " +
            "WHERE v.type = 'UserVm' AND v.uuid > :lastUuid " +
            "ORDER BY v.uuid ASC LIMIT :batchSize")
            .param("lastUuid", lastUuid)
            .param("batchSize", batchSize)
            .execute();

        // Step 2: UPDATE — 已有 dirty 行的 VM 递增 dirtyVersion + 升级 storageStructureChange
        SQL.New(
            "UPDATE VmMetadataDirtyVO d " +
            "INNER JOIN VmInstanceVO v ON d.vmInstanceUuid = v.uuid " +
            "SET d.dirtyVersion = d.dirtyVersion + 1, " +
            "    d.storageStructureChange = 1 " +
            "WHERE v.type = 'UserVm' AND v.uuid > :lastUuid " +
            "ORDER BY v.uuid ASC LIMIT :batchSize")
            .param("lastUuid", lastUuid)
            .param("batchSize", batchSize)
            .execute();

        // 更新 lastUuid 用于 keyset 分页
        List<String> batch = SQL.New("SELECT v.uuid FROM VmInstanceVO v " +
            "WHERE v.type = 'UserVm' AND v.uuid > :lastUuid " +
            "ORDER BY v.uuid ASC LIMIT :batchSize", String.class)
            .param("lastUuid", lastUuid)
            .param("batchSize", batchSize)
            .list();

        if (batch.isEmpty()) {
            break;
        }

        totalProcessed += batch.size();
        lastUuid = batch.get(batch.size() - 1);
    }

    logger.info("metadata full refresh: {} VMs processed for version {}", totalProcessed, currentVersion);

    // Poller 自动分批处理，ChainTask 自动限流

    // 更新 lastRefreshVersion — 必须在全量刷新完成后写入（讨论 Δ-8）
    // 不得在刷新开始前写入：若刷新过程中 MN 崩溃，提前写入会导致重启后
    // lastRefreshVersion 已等于 currentVersion，跳过本次刷新，遗留未处理的 VM。
    // 写在完成后：崩溃重启 → lastRefreshVersion 仍为旧值 → 重新触发全量刷新 → 幂等安全。
    VmGlobalConfig.VM_METADATA_LAST_REFRESH_VERSION.updateValue(currentVersion);
}
```

**说明**：原实现使用 `INSERT ... ON DUPLICATE KEY UPDATE` 单条 SQL，与 C-DM-01 约束（禁用 ON DUPLICATE KEY，避免 Galera 死锁）不一致。改为 `INSERT IGNORE + UPDATE` 两步语义，与 `markDirty()` 保持统一。同时将 `OFFSET` 分页改为 keyset 分页（`uuid > :lastUuid`），与 §9a.1 和 §8.2.5 保持一致，避免大数据集性能退化。

**storageStructureChange=true 已修正**：与 C-SC-07 约束对齐——升级全量刷新场景中无法判断存储拓扑是否变化，保守使用 `storageStructureChange=true`（原实现误设为 0）。

**为什么用分批批量 SQL 替代逐个 markDirty**：万级 VM 环境中，逐个 INSERT 产生万级 SQL 语句；单条超大批量 SQL 又可能超时。按 1000 行分批可在吞吐与稳定性间平衡。

---

# 9a. 功能开关切换处理

## 9a.1 `false → true`（启用）— 分批全量初始化

通过 `GlobalConfig.installUpdateExtension` 监听 `vm.metadata.enabled` 变更。检测到 `false → true` 时，提交分批初始化任务，为所有尚无元数据（无 dirty 行也无 PathFingerprint 记录）的 UserVm 创建 dirty 行。

**核心设计：防止读写风暴**

与升级全量刷新（§9.2）不同，开关启用可能在业务高峰时执行。直接批量 INSERT 大量 dirty 行后 Poller 瞬间看到全部可认领行，可能引发存储 IO 风暴。因此引入**批间延迟**：

```java
private void submitBatchInitialization() {
    thdf.submit(new Task<Void>(null) {
        @Override
        public Void call() {
            if (!VmGlobalConfig.VM_METADATA_ENABLED.value(Boolean.class)) {
                // 延迟执行前再次检查，防止快速 toggle 后仍执行初始化
                logger.info("vm.metadata.enabled toggled back to false before initialization, skip");
                return null;
            }

            int batchSize = VmGlobalConfig.VM_METADATA_INIT_BATCH_SIZE.value(Integer.class); // default 200
            long batchDelaySec = VmGlobalConfig.VM_METADATA_INIT_BATCH_DELAY_SEC.value(Long.class); // default 5
            String lastUuid = "";
            int totalInitialized = 0;

            while (true) {
                // 每轮检查开关状态，若已关闭则中止
                if (!VmGlobalConfig.VM_METADATA_ENABLED.value(Boolean.class)) {
                    logger.info("vm.metadata.enabled disabled during initialization, abort. initialized={}",
                                totalInitialized);
                    break;
                }

                // Keyset 分页查询尚无 dirty 行的 UserVm
                int initialized = SQL.New(
                    "INSERT IGNORE INTO VmMetadataDirtyVO (vmInstanceUuid, dirtyVersion, storageStructureChange) " +
                    "SELECT v.uuid, 1, 0 FROM VmInstanceVO v " +
                    "LEFT JOIN VmMetadataDirtyVO d ON v.uuid = d.vmInstanceUuid " +
                    "WHERE v.type = 'UserVm' AND v.uuid > :lastUuid AND d.vmInstanceUuid IS NULL " +
                    "ORDER BY v.uuid ASC LIMIT :batchSize")
                    .param("lastUuid", lastUuid)
                    .param("batchSize", batchSize)
                    .execute();

                // Q29 修复：移除 `if (initialized == 0) break;`——当本批所有 VM 都已有 dirty 行时
                // INSERT IGNORE affected_rows=0，但后续批次可能还有未初始化的 VM。
                // 终止条件改为 batchUuids.isEmpty()（见下方），确保真正遍历完全部 VM。

                totalInitialized += initialized;

                // 更新 lastUuid 用于 keyset 分页
                // Q29 — lastUuid 必须独立推进：当 INSERT IGNORE affected_rows=0（本批 VM 都已有 dirty 行）
                // 时 initialized==0，但 while 循环不能终止——后续批次可能还有未初始化的 VM。
                // lastUuid 基于 VmInstanceVO 全量 UUID 推进，而非 INSERT 结果。
                List<String> batchUuids = SQL.New("SELECT v.uuid FROM VmInstanceVO v " +
                    "WHERE v.type = 'UserVm' AND v.uuid > :lastUuid " +
                    "ORDER BY v.uuid ASC", String.class)
                    .param("lastUuid", lastUuid)
                    .limit(batchSize)
                    .list();

                if (batchUuids.isEmpty()) {
                    break;  // 真正遍历完所有 VM
                }
                lastUuid = batchUuids.get(batchUuids.size() - 1);

                logger.info("metadata initialization batch completed: {} VMs in this batch, {} total",
                            initialized, totalInitialized);

                // 批间延迟：等待 Poller 消化已有 dirty 行，避免瞬间堆积
                if (batchDelaySec > 0) {
                    try {
                        TimeUnit.SECONDS.sleep(batchDelaySec);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        logger.warn("metadata initialization interrupted");
                        break;
                    }
                }
            }

            logger.info("metadata initialization complete: {} VMs total", totalInitialized);
            return null;
        }
    }, Duration.ofSeconds(30));  // 延迟 30s 启动，等待 Poller 就绪
}
```

**关键设计点**：

| 设计点 | 决策 | 原因 |
|--------|------|------|
| 使用 `INSERT IGNORE` | 跳过已有 dirty 行的 VM | 幂等：重复触发不产生副作用 |
| `LEFT JOIN` 排除已有 dirty 行 | 仅为从未标脏的 VM 初始化 | 避免对已有 Poller 处理中的 VM 产生干扰 |
| Keyset 分页（`uuid > lastUuid`） | 避免 `OFFSET` 在大数据集上的性能退化 | 与路径指纹巡检（§8.2.5）保持一致 |
| 批间延迟（默认 5s） | 给 Poller 消化已提交 dirty 行的时间窗口 | 防止 dirty 行瞬间堆积万级，触发存储 IO 风暴 |
| 每轮重新检查 `vm.metadata.enabled` | 快速 toggle（开→关→开）场景下及时中止 | 防御性设计 |
| 延迟 30s 启动 | 等 Poller、ChainTask 线程池初始化完成 | `false→true` 可能在 MN 启动时通过 GlobalConfig 变更触发 |
| `storageStructureChange=0` | 首次初始化不涉及存储拓扑变更 | 用 CONFIG 级别即可，不需 STORAGE 级重建 |

**初始化进度可观测**：

- 日志输出每批和总计数
- Poller 的 `vm_metadata_dirty_queue_size` Gauge（§14）自然反映待处理积压量
- 运维可通过 `SELECT COUNT(*) FROM VmMetadataDirtyVO WHERE managementNodeUuid IS NULL` 查看剩余量

**与 §9.2 升级全量刷新的关系**：

| 维度 | §9 升级全量刷新 | §9a 开关启用初始化 |
|------|----------------|-------------------|
| 触发时机 | MN 升级后自动触发 | GlobalConfig 从 false 切换到 true |
| 涉及 VM 范围 | 所有 UserVm（含已有 dirty 行的） | 仅尚无 dirty 行的 UserVm |
| SQL 策略 | `INSERT ... ON DUPLICATE KEY UPDATE`（保证已有行也递增版本） | `INSERT IGNORE ... LEFT JOIN`（仅初始化新行） |
| 批间延迟 | 无（升级窗口通常业务量低） | 有（默认 5s，防止高峰期风暴） |
| 去重保护 | `lastRefreshVersion` 检查避免重复触发 | `LEFT JOIN` + `INSERT IGNORE` 天然幂等 |

## 9a.2 `true → false`（禁用）— 保留已有元数据，按需清理

关闭 `vm.metadata.enabled` 后：

1. **Poller 自动停止处理**：`markDirty()` 和 `triggerFlushForVm()` 的前置检查直接 return，不再产生新的标脏和刷写
2. **清理 PathFingerprint 记录（讨论 Δ-10）**：异步批量删除所有 `VmMetadataPathFingerprintVO` 行。原因：功能关闭期间存储拓扑可能发生变更（卷迁移、快照删除等），重新启用时旧指纹与实际拓扑不一致，会导致路径巡检（§8.2）产生大量误报 drift。清理采用 keyset 分页异步删除（每批 1000 行），不阻塞 GlobalConfig 变更回调。dirty 行的 FK CASCADE 不受影响。
3. **已有 dirty 行保留**：不主动清理 `VmMetadataDirtyVO` 表中的残留行。原因：
   - 若运维快速重新开启（toggle back），残留行可立即被 Poller 消费
   - 若长期关闭，残留行占用 DB 空间可忽略（每行 < 200 bytes）
4. **存储上的元数据文件/LV 保留**：不自动删除已持久化的元数据。理由：
   - 防止误操作导致已有容灾数据丢失
   - 元数据文件体积小（通常 < 500KB/VM），即使保留也不构成存储压力
   - 运维需要时可通过 `APICleanupVmInstanceMetadataMsg` 按需清理

**`APICleanupVmInstanceMetadataMsg`** 详见 [Part 5 §6.3](vm-metadata-05-API设计.md#63-清理虚拟机元数据)。核心设计：

- 可按 `primaryStorageUuids`（指定 PS）或 `vmUuids`（指定 VM）粒度清理
- 不传参数则清理**所有已停用（`vm.metadata.enabled=false`）时的全量数据**
- 清理操作为 `deleteMetadata` + 删除 `VmMetadataPathFingerprintVO` + 删除残留 `VmMetadataDirtyVO`
- 清理操作幂等：重复调用不报错

**安全约束**：`APICleanupVmInstanceMetadataMsg` 在 `vm.metadata.enabled=true` 时**拒绝执行**（错误码 `METADATA_CLEANUP_REJECTED_WHILE_ENABLED`），防止在功能启用状态下误清理正在使用的元数据。仅当功能关闭后才允许执行。

---

# 10. Payload 大小保护

在 `VmInstanceBase.doHandleUpdateVmInstanceMetadata()` 中，`buildVmInstanceMetadata()` 构建 payload 后进行大小检查：

| 阈值 | 行为 | 说明 |
|------|------|------|
| > 8MB | WARN 日志 | 早期预警，提示运维关注 |
| > 30MB | ERROR + 拒绝写入 + reply 错误 | 保护 sblk LV 空间 |

正常 VM 的 metadata payload 通常在 10KB~500KB 范围内。超过 8MB 几乎一定表示异常（如快照未清理导致数千条记录）。

### 10.0 容量公式与常量（QX-8）

```java
public final class VmMetadataConstants {
    public static final long SBLK_HEADER_SIZE = 4096L;
    public static final long SBLK_SLOT_HEADER_SIZE = 36L;
    public static final long SBLK_MAX_LV_SIZE = 64L * 1024 * 1024;

    public static long slotCapacity(long lvSize) {
        return ((lvSize - SBLK_HEADER_SIZE) / 2 / 4096) * 4096;
    }

    public static final long SBLK_MAX_SLOT_CAPACITY = slotCapacity(SBLK_MAX_LV_SIZE); // 33,550,336
    public static final long SBLK_MAX_PAYLOAD_SIZE = SBLK_MAX_SLOT_CAPACITY - SBLK_SLOT_HEADER_SIZE; // 33,550,300
    public static final long PAYLOAD_WARN_THRESHOLD = 8L * 1024 * 1024;
    public static final long PAYLOAD_REJECT_THRESHOLD = 30L * 1024 * 1024;
}
```

推导：64MB LV 下单 Slot 容量约 32MB；扣除 Slot Header（36 字节：Magic 4B + SeqNum 8B + SlotOffset 8B + SlotCapacity 8B + PayloadLen 8B）后可用 payload 约 31.99MB。30MB 阈值为显式保守余量。

### 10.1 Payload 大小与 sblk LV 大小映射

| Payload 大小范围 | LV 大小 | 典型场景 |
|-------------------|---------|----------|
| 0 ~ 2MB | 4MB（初始） | 普通 VM，1~5 个卷，少量快照 |
| 2MB ~ 4MB | 8MB | 多卷 VM，数十个快照 |
| 4MB ~ 8MB | 16MB | 大量快照的 VM |
| 8MB ~ 16MB | 32MB | 异常场景（WARN） |
| 16MB ~ 30MB | 64MB（上限） | 极端异常 |
| > 30MB | 拒绝写入 | 拒绝以保护存储 |

LV 初始 4MB，每次扩容翻倍，最大 64MB。扩容通过 `lvextend` 完成，详见 [Part 4e §2](vm-metadata-04e-sblk运维与IO.md#2-扩容)。

### 10.2 写入前运行时容量校验

`doFlush()` 必须基于**当前 LV 实际大小**执行动态容量校验，禁止仅依据静态 30MB 阈值：

```java
long lvSize = sblkAgent.getLvSize(psUuid, vmUuid);
long slotCap = VmMetadataConstants.slotCapacity(lvSize);
long currentPayloadCap = slotCap - VmMetadataConstants.SBLK_SLOT_HEADER_SIZE;

if (payloadSize > currentPayloadCap) {
    sblkAgent.expandLv(psUuid, vmUuid);
    long newLvSize = sblkAgent.getLvSize(psUuid, vmUuid);
    long newPayloadCap = VmMetadataConstants.slotCapacity(newLvSize) - VmMetadataConstants.SBLK_SLOT_HEADER_SIZE;
    if (payloadSize > newPayloadCap) {
        throw new PayloadTooLargeException(String.format(
            "payload=%d exceeds slot capacity=%d after expand, lvSize=%d", payloadSize, newPayloadCap, newLvSize));
    }
}
```

若扩容后仍不足（通常达到 64MB 上限），返回明确错误码 `VM_METADATA_PAYLOAD_TOO_LARGE`，不得将底层 IO 错误透传为通用失败。

---

# 11. 潜在代价与 tradeoff

| 代价 | 说明 | 缓解 |
|------|------|------|
| Poller 空转 | 无 dirty 行时每 5s 执行一次 SELECT → 0 rows | 开销极小（一次空查询 <1ms），可接受 |
| 双 MN 负载不均 | 两个 Poller 竞争认领，不保证 50/50 | 最终一致性保证所有行都会被处理 |
| 新增一张 DB 表 | VmMetadataDirtyVO | 结构简单，维护成本低 |
| 退避期间 Poller 查到但跳过 | nextRetryTime 尚未到 → WHERE 条件排除 | 索引命中，开销可忽略 |

---

# 12. 开发约束清单

## 12.1 API 标注约束

| # | 约束 | 原因 | 违反后果 |
|---|------|------|----------|
| A1 | 新增影响 VM 元数据的 API **必须**标注 `@MetadataImpact(Impact.CONFIG)` 或 `@MetadataImpact(Impact.STORAGE)` | 拦截器仅扫描带注解的 API 类 | 该 API 的变更不会触发元数据更新，存储侧数据过期 |
| A2 | 明确不影响元数据的 API **应当**标注 `@MetadataImpact(Impact.NONE)` | Opt-out 显式声明，利于 Code Review 审查覆盖率 | 无功能影响，但降低可审计性 |
| A3 | 涉及存储拓扑变更的 API（快照/迁移/删盘）必须使用 `Impact.STORAGE`，不可用 `Impact.CONFIG` | STORAGE 下发 OP type=2 通知 Agent 处理存储拓扑变更 | Agent 不执行存储拓扑处理，sblk 场景可能数据不一致 |
| A4 | `updateOnFailure=true` 仅用于可能部分成功的 API（如批量操作） | 默认 false：失败跳过；设为 true 时失败也 markDirty | 滥用会导致失败 API 也触发无意义的全量刷写 |

## 12.2 VM UUID 解析约束

| # | 约束 | 原因 | 违反后果 |
|---|------|------|----------|
| B1 | 非 VM 直接 API（如 Volume/Nic/Tag API）必须有 `VmUuidFromApiResolver` 能够处理 | 默认 Resolver 链仅覆盖 `VmInstanceMessage`/`VolumeMessage`/Tag API + 反射兜底 | 相关 VM 不会被 markDirty，元数据不更新 |
| B2 | Resolver 的 `resolveVmUuids()` 必须在 **API 执行前**调用（`beforeDeliveryMessage` 阶段） | API 执行后资源可能已删除（如 APIDeleteVolumeMsg → VolumeVO 不存在） | 无法查到关联 VM，markDirty 丢失 |
| B3 | 新增资源类型关联 VM 时，需在 `ResourceBasedVmUuidFromApiResolver.resolveByResourceType()` 中补充映射 | 当前仅覆盖 VmInstanceVO/VolumeVO/VmNicVO/VolumeSnapshotVO | Tag 操作目标为新资源类型时不触发元数据更新 |

## 12.3 元数据构建约束

| # | 约束 | 原因 | 违反后果 |
|---|------|------|----------|
| C1 | `buildVmInstanceMetadata()` 必须保留在 `VmMetadataBuilder` 中并标注 `@Transactional(readOnly=true)` | 6+ 条 SELECT 需在同一 REPEATABLE READ 快照内执行 | 读到跨快照不一致数据（如 Volume 存在但其 Snapshot 已被并发删除） |
| C2 | 新增元数据字段时，需同步更新 `VmInstanceMetadataDTO` 和 `VmMetadataBuilder` | DTO 是 payload 的唯一 schema 定义 | 字段不在 DTO 中则不会序列化到 payload |
| C3 | `ResourceMetadata` 中 `systemTags`/`resourceConfigs` 字段必须为 `String`（Base64 编码），不是 `List<String>` | 编码管线：VO 列表 → JSON 序列化 → Base64 → 单 String | 类型不匹配导致序列化异常 |

## 12.4 标脏与刷写约束

| # | 约束 | 原因 | 违反后果 |
|---|------|------|----------|
| D1 | 修改 VM 存储拓扑的**内部消息** handler 必须手动调用 `markDirty()` | 非 API 操作不经过 `VmMetadataUpdateInterceptor` | 变更后元数据不更新 |
| D2 | Handler 端写入失败时**不得**调用 `markDirty()`，必须 reply error 由上层重试 | Dirty 行已存在且由 Poller 管理 retryCount 和退避 | markDirty 重置 retryCount，绕过退避机制，可能无限快速重试 |

**D2 例外：存储迁移失败**（§8 恢复策略表）中 `afterMigrateVmStorageFailed` 调用 `markDirty(vmUuid, storageStructureChange=true)` 不违反 D2。原因：存储迁移失败的回滚会改变 installPath（从 target PS 回退到 source PS），旧 dirty 行中缓存的 installPath 指向已回滚的 target 路径，已不正确。此时必须重新 markDirty 以反映回滚后的 source-side 拓扑。这是 D2 的唯一显式例外。同时，回滚操作 `deleteMetadata(targetPsUuid) + nextRetryTime=NULL` 确保不会产生无限快速重试（retryCount 因是新 dirty 行而从 0 开始，退避机制正常生效）。
| D3 | Agent 端写入必须幂等（全量覆盖，不做增量 merge） | 同一 VM 的并发刷写（跨 MN 极端场景）最终应收敛到一致状态 | 增量 merge 可能导致数据残留或顺序依赖 |
| D4 | `exceedMaxPendingCallback` 中**必须**执行 `globalFlushInFlight.decrementAndGet()` + `releaseClaim()` | **讨论 Δ-1 更新**：采用单层 per-VM ChainTask + AtomicInteger 全局计数器后，exceedMaxPendingCallback 表示该 VM 已有 pending 任务排队，当前提交被拒绝。此时必须归还全局计数器配额并释放 DB 认领，让 Poller 下轮重新处理。旧约束（不得释放）基于嵌套 ChainTask 设计，已不适用 | 计数器泄漏导致全局并发配额耗尽，dirty 行被永久锁定 |
| D5 | `markDirty(vmUuid, storageStructureChange)` 中 `storageStructureChange` 必须保持 OR 语义（true 一旦出现即保持 true 至该行删除） | 保守策略确保任一存储拓扑变更最终走 `STORAGE_CHANGE` 写入路径（intentional conservative behavior） | 若改为覆盖语义，可能把真实拓扑变更降级为 CONFIG 路径 |

#### D1 补充说明 — 内部消息 handler 遗漏 `markDirty()` 的补救

**为什么会遗漏**：`@MetadataImpact` 注解 + CI 检查仅覆盖 `APIMessage` 子类。内部消息（如 HA handler、级联删除、定时清理等）不经过 `VmMetadataUpdateInterceptor`，CI 无法自动检测是否遗漏了 `markDirty()` 调用。

**补救手段——分三层**：

| 层次 | 手段 | 时效 | 说明 |
|------|------|------|------|
| 即时修复 | `APIUpdateVmMetadataMsg` | 秒级 | 运维/CLI 手动触发指定 VM 的全量元数据刷新（见 [Part 5 §6.1](vm-metadata-05-API设计.md#61-手动触发元数据更新)） |
| 批量修复 | `APICheckVmInstanceMetadataConsistencyMsg` | 分钟级 | 一致性检查发现 DB 与存储元数据不一致时自动 `markDirty()`（见 [Part 5 §5](vm-metadata-05-API设计.md#5-检查虚拟机元数据一致性)） |
| 长期防御 | 路径指纹巡检 | 分钟级 | 每次刷写成功后记录路径快照，独立 PeriodicTask 纯 DB 比对检测漂移，不一致则自动 `markDirty()`（见 §8.2） |

**根因修复流程**：

```
发现元数据滞后（运维报告 / 一致性检查告警）
  │
  ├─ 1. 定位遗漏的内部消息 handler
  │     - 查看该 VM 近期操作日志，找到触发存储变更但未更新元数据的内部操作
  │     - 在对应 handler 的成功回调中补充 markDirty(vmUuid) 调用
  │
  ├─ 2. 即时修复受影响的 VM
  │     - CLI: `UpdateVmMetadata uuid=<vmUuid>`
  │     - 或批量: `CheckVmInstanceMetadataConsistency`
  │
  └─ 3. 代码评审防护
        - 涉及 Volume/Snapshot/installPath 变更的内部消息 handler 代码评审时
          必须检查是否调用了 markDirty()
        - 评审 checklist 模板中增加 "元数据标脏" 检查项
```

**注意**：即使存在遗漏，全量覆盖写语义保证了补救时的正确性——任何时刻调用 `markDirty()` 都会触发从 DB 重新构建完整元数据并覆盖写入，不存在增量丢失问题。遗漏的影响是元数据**暂时落后于 DB**，而非**永久损坏**。

## 12.5 并发与线程约束

| # | 约束 | 原因 | 违反后果 |
|---|------|------|----------|
| E1 | `nodeLeft()`/`nodeJoined()` 回调中的 DB 操作必须通过 `thdf.submit()` 异步执行 | 回调在心跳检测线程上，阻塞会影响其他 MN 状态检测 | 心跳超时导致误判 MN 离线 |
| E2 | per-VM ChainTask（`metadata-dirty-flush-vm-{uuid}`）的 `maxPending=1`，不得修改 | 确保同一 VM 最多排队 1 个 pending 任务（+ 1 running） | pending 过多导致重复提交堆积 |
| E3 | 外层全局队列 `syncLevel` 和 Layer 3 per-PS 队列 `syncLevel` 的调整需评估 DB 连接池和 Agent 并发承受力 | 二者嵌套：全局水位 × per-PS 水位 决定实际并发 | 过大导致 DB/Agent 过载，过小导致刷写积压 |

---

# 13. GlobalConfig 配置项汇总

| 配置项 | 类型 | 默认值 | 说明 | 章节 |
|--------|------|--------|------|------|
| `vm.metadata.enabled` | Boolean | false | 元数据功能总开关 | §1 |
| `vm.metadata.dirty.pollIntervalSec` | Long | 5 | Poller 轮询间隔（秒），可动态调整 | §4.1 |
| `vm.metadata.dirty.batchSize` | Integer | 50 | 每轮 Poller 最多认领行数 | §4.2 |
| `vm.metadata.maxRetry` | Integer | 5 | 最大重试次数（达上限后告警 + 删除，下次 API 自动重试） | §4.6 |
| `vm.metadata.ps.maxConcurrent` | Integer | 5 | 同一 MN 同一 PS 最大并发写入 | §6.1 |
| `vm.metadata.global.maxConcurrent` | Integer | 10 | 同一 MN 最大并发 VM 更新数 | §6.2 |
| `vm.metadata.pathCheck.intervalSec` | Long | 300 | 路径指纹巡检间隔（秒） | §8.2 |
| `vm.metadata.pathCheck.batchSize` | Integer | 500 | 路径指纹巡检 keyset 分页批次 | §8.2.5 |
| `vm.metadata.upgrade.refreshDelaySec` | Long | 600 | 升级后全量刷新延迟时间（秒），等待滚动升级完成 | §9.1 |
| `vm.metadata.upgrade.refreshBatchSize` | Integer | 1000 | 升级全量刷新分批 SQL 批次大小 | §9.2 |
| `vm.metadata.nodeLeft.delaySec` | Long | 5 | nodeLeft 后延迟接管窗口，降低 zombie MN 竞态 | §7.2 |
| `vm.metadata.staleRecovery.intervalSec` | Long | 1800 | MetadataStaleRecoveryTask 扫描间隔（秒）（H2 修复） | §4.8 (Part 2) |
| `vm.metadata.staleRecovery.batchSize` | Integer | 100 | MetadataStaleRecoveryTask 每批扫描行数（H2 修复） | §4.8 (Part 2) |
| `vm.metadata.staleRecovery.maxCycles` | Integer | 10 | 单 VM 连续 stale recovery 熔断阈值，超过后停止自动恢复 | §8.2.3 |
| `vm.metadata.pendingApi.timeoutMinutes` | Long | 45 | pendingApis 超时清理阈值（分钟）（M4 修复） | §1.7 (Part 1b) |
| `vm.metadata.retry.baseDelaySeconds` | Integer | 10 | 指数退避基础延迟（秒） | §4.6 (Part 2) |
| `vm.metadata.retry.maxExponent` | Integer | 10 | 指数退避最大指数 | §4.6 (Part 2) |
| `vm.metadata.init.batchSize` | Integer | 200 | `false→true` 启用初始化每批 VM 数量 | §9a.1 |
| `vm.metadata.init.batchDelaySec` | Long | 5 | `false→true` 启用初始化批间延迟（秒），防止 IO 风暴 | §9a.1 |
| `vm.metadata.orphanCheck.intervalSec` | Long | 3600 | 孤儿元数据检测间隔（秒） | §8.4.2 |
| `vm.metadata.zombieClaim.thresholdMinutes` | Long | 15 | 僵尸 claim 判定阈值（分钟）：`lastClaimTime` 超过此时长的已认领 dirty 行视为僵尸，`cleanupZombieClaims()` 释放其认领 | §4.8 (Part 2), C-CL-02 |
| `vm.metadata.staleClaim.thresholdMinutes` | Long | 30 | `MetadataStaleRecoveryTask` 后台扫描的过期 claim 检测阈值（分钟）：`managementNodeUuid IS NOT NULL` 且 `lastClaimTime` 超过此时长的行被强制释放并重新入队。**注意**：此阈值仅用于后台周期任务，与 `triggerFlush.staleMinutes`（API 热路径）不同 | §4.8 (Part 2) |
| `vm.metadata.triggerFlush.staleMinutes` | Long | 10 | `triggerFlushForVm()` 内联 stale claim 接管阈值（分钟）：API 热路径中，若 dirty 行的 `lastClaimTime` 超过此时长且认领 MN 与传入的 `staleId` 一致，允许当前 MN 接管。与 `staleClaim.thresholdMinutes`（后台扫描 30 min）形成两级保护，详见 DP-06 | §3.1 (Part 2) |
| `vm.metadata.delete.maxRetry` | Integer | 3 | `deleteMetadata` 同步重试最大次数（ExpungeVmInstanceFlow 中使用） | §2 (Part 1c) |
| `vm.metadata.delete.baseDelaySec` | Long | 30 | `deleteMetadata` 同步重试基础延迟（秒），退避公式 `baseDelay × 2^retryIndex` | §2 (Part 1c) |
| `vm.metadata.lastRefreshVersion` | String | _(内部)_ | 升级全量刷新去重标记：记录最近一次已完成的升级刷新版本号，避免双 MN 重复触发。**仅供内部使用，运维不应手动修改** | §9 |

---

# 14. 可观测性指标

以下指标建议通过 ZStack 内置 Prometheus 埋点暴露，供 Grafana 看板使用。

| 指标名 | 类型 | 标签 | 说明 |
|----------|------|------|------|
| `vm_metadata_flush_total` | Counter | status={success,fail,skip} | 刷写总次数，按结果分类 |
| `vm_metadata_flush_duration_seconds` | Histogram | — | 单次刷写耗时（从 buildMetadata 到 Agent 返回） |
| `vm_metadata_dirty_queue_size` | Gauge | — | 当前未认领的 dirty 行数（每轮 Poller 统计） |
| `vm_metadata_poller_cycle_duration_seconds` | Histogram | — | 单轮 Poller 执行总耗时（含认领 + 提交） |
| `vm_metadata_registration_total` | Counter | status={success,fail,rollback} | 注册总次数 |
| `vm_metadata_retry_count` | Histogram | — | 每次刷写成功时的累计重试次数分布 |

**告警规则建议**：
- `vm_metadata_dirty_queue_size > 500 持续 5 分钟` → WARN（刷写积压）
- `rate(vm_metadata_flush_total{status="fail"}[5m]) > 10` → WARN（批量失败）
- `vm_metadata_flush_duration_seconds{quantile="0.99"} > 30` → WARN（单次刷写太慢）

---

# 15. 约束与不変量

| 约束 ID | 内容 | 来源章节 |
|---------|------|----------|
| C-02B-1 | `nodeLeft()` 接管必须延迟 5s 后触发 `claimAndFlush()`，不得立即抢占 | §7.2 |
| C-02B-2 | 执行 sblk 写入前必须校验 dirty 行 `managementNodeUuid == 本 MN`，失去认领立即放弃写入 | §7.6 |
| C-02B-3 | 路径巡检禁止 `dbf.listAll` 全量加载，必须采用 keyset 分页（`vmInstanceUuid > lastUuid`） | §8.2.5 |
| C-02B-4 | 升级全量刷新必须按批（默认 1000）执行批量 SQL，避免单次超大事务 | §9.2 |
| C-02B-5 | payload 上限必须同时满足静态阈值（30MB）与运行时 slot 容量校验，容量不足先扩容再写入 | §10.0, §10.2 |
| C-02B-6 | `storageStructureChange` 标志保持 OR 语义，直到 dirty 行成功删除前不得降级为 false | §12.4 |
| C-02B-7 | 容量计算常量（Header/SlotHeader/MAX_LV）必须集中定义并用于公式推导，禁止硬编码散落 | §10.0 |
| C-02B-8 | `VmMetadataPathFingerprintVO.lastFlushFailed` 仅在重试耗尽时置 true，仅由 `MetadataStaleRecoveryTask` 重置为 false，其他路径不得修改 | §8.2.3 (M1 修复) |
| C-02B-9 | 升级全量刷新执行前必须检查最近 15 分钟内无 `nodeLeft` 事件，否则延迟重试 | §9.1 (M3 修复) |
| C-02B-10 | `nodeLeft` 延迟（`vm.metadata.nodeLeft.delaySec`）调整需与 Fence Check 机制配合评估，不得单独修改 | §7.2, §7.6 (M2 修复) |
| C-02B-11 | `false→true` 初始化必须使用分批 + 批间延迟，禁止一次性全量 INSERT dirty 行 | §9a.1 |
| C-02B-12 | `APICleanupVmInstanceMetadataMsg` 必须在 `vm.metadata.enabled=false` 时才允许执行，`true` 时拒绝 | §9a.2, Part 5 §6.3 |
| C-02B-13 | `false→true` 初始化任务每批必须重新检查 `vm.metadata.enabled` 开关状态，关闭时立即中止 | §9a.1 |
| C-02B-14 | 孤儿元数据检测仅报告不自动删除，避免与进行中的存储迁移竞态导致误删 | §8.4.3 |
