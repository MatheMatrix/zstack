# VM 元数据 — 脏标记与 Poller

## 目录

1. [概述](#1-概述)
2. [数据模型](#2-数据模型)
3. [markDirty — 标脏入口](#3-markdirty--标脏入口)
4. [MetadataDirtyPoller — 轮询刷写](#4-metadatadirtypoller--轮询刷写)
   - [4.8 Stale 恢复任务](#48-stale-恢复任务h2-修复)
5. [消息调用链](#5-消息调用链)
6. [并发控制（四层）](#6-并发控制四层)
   - [6.4 调优指南](#64-调优指南)
7. [约束与不変量](#7-约束与不変量)

---

# 1. 概述

## 1.1 问题：GC 框架的结构性错配

GC 框架是 **"一个任务对应一行 DB 记录"** 的模型。每次 API 成功都 `submit()` 创建新 GC 行，通过 ChainTask `maxPendingTasks=1` + `exceedMaxPendingCallback` 淘汰多余行。这导致：

| 问题 | 说明 |
|------|------|
| **GC 行爆炸** | 100 个 API → 100 行 GC，98 行立即 Done，需定期清理 |
| **deduplicateSubmit 不可用** | GC 执行期间 status 仍为 Idle，新 GC 被误判"已有在处理" |
| **双 MN 复杂度** | 需 hash 环路由 SubmitGCMsg + 执行层 delegation + reply 回退，6 种极端情况需逐一分析 |
| **delegation 消耗 retryCount** | 非 owner 上 triggerNow 的 delegation 失败也递增 retryCount |
| **框架修改** | 需修改 loadOrphanJobs 增加状态过滤、需新增索引 |

**根因**：元数据更新需要的是 **"标脏 → 合并 → 刷写"** 模型（多次修改合并为一次写入），而非 GC 的 **"一个失败任务 → 一次重试"** 模型。

## 1.2 新方案一句话

用一张 **`VmMetadataDirtyVO`** 表做脏标记（一个 VM 最多一行），**`PeriodicTask`** 轮询器定期认领并刷写，成功删行，失败释放等下轮。

灵感来源：`SecurityGroupFailureHostVO` + `FailureHostWorker` 模式。

## 1.3 核心不变量

- 刷写时始终从 DB 查询 VM 完整当前状态构建 payload，不使用触发 API 时的增量数据。
- 任何一次刷写完成后，存储上的元数据反映数据库最新完整状态。
- `buildVmInstanceMetadata()` 必须标注 `@Transactional(readOnly = true)`，MySQL InnoDB REPEATABLE READ 事务内所有查询使用同一快照，保证单次构建的读一致性。`readOnly = true` 不启动写事务，开销极小。

## 1.4 最终一致性模型

`buildVmInstanceMetadata()` 读 DB 到 `pwrite` 完成之间存在毫秒级窗口，期间其他 API 可能修改了 DB（如删除快照）。此时写入的元数据可能包含已过期信息。这不是问题——修改 DB 的 API 成功后会再次 `markDirty()`，下轮 Poller 从 DB 全量读取已反映最新状态，覆盖写入自然修正。

对注册场景，Part 3 §3.4 的 installPath 存在性检查提供额外兜底。

---

# 2. 数据模型

## 2.1 VmMetadataDirtyVO

```java
@Entity
@Table
public class VmMetadataDirtyVO {
    @Id
    @Column
    @ForeignKey(parentEntityClass = VmInstanceEO.class, onDeleteAction = ReferenceOption.CASCADE)
    private String vmInstanceUuid;   // 主键 = 天然去重

    @Column
    @ForeignKey(parentEntityClass = ManagementNodeVO.class, onDeleteAction = ReferenceOption.SET_NULL)
    private String managementNodeUuid;  // null = 未认领，非null = 已认领

    @Column
    private Timestamp lastClaimTime;    // 最近一次被 CAS 认领的时间（死锁防护）

    @Column
    private long dirtyVersion;       // 每次 markDirty 递增，用于检测刷写期间的新变更

    @Column
    private boolean storageStructureChange;  // 是否涉及存储结构变更（OP type 标记）

    @Column
    private int retryCount;          // 连续失败次数

    @Column
    private Timestamp nextRetryTime; // 下次可被认领的时间（退避控制）

    @Column
    private Timestamp createDate;

    @Column
    private Timestamp lastOpDate;    // 最后一次 markDirty 的时间（关键！）
}
```

**关键设计决策**：

| 设计点 | 决策 | 原因 |
|--------|------|------|
| `vmInstanceUuid` 做主键 | 一个 VM 最多一行 | 天然去重，100 个 API 只产生 1 行，不是 100 行 |
| `managementNodeUuid` FK SET_NULL | MN 宕机自动释放 | 无需额外孤儿扫描，DB 约束自动完成 |
| `lastClaimTime` | claim 存活时间上限控制 | 识别僵尸 claim，支持 stale 认领接管 |
| `vmInstanceUuid` FK CASCADE | VM 销毁自动删除脏标记 | 无残留 |
| `dirtyVersion` | 每次 markDirty +1 | 刷写前快照 version，成功后比较——检测刷写期间是否有新变更（见 §4.5）。语义比时间戳比较更明确，无精度问题 |

**`dirtyVersion` per-row 语义澄清**：`dirtyVersion` 是每行独立的单调递增计数器（从 1 开始），不是全局序列号。其用途仅限于同一 VM 的 `onFlushSuccess` 版本比较（检测刷写期间是否有新 markDirty），不用于跨 VM 排序。跨 VM 公平调度使用 `lastOpDate`。BIGINT 范围（9.2×10^18）足够单 VM 终身使用（假设 1000 次/秒，需 2.9 亿年溢出），无需溢出保护。
| `storageStructureChange` | OR 升级策略 | `@MetadataImpact(CONFIG)` → false（OP type 1），`@MetadataImpact(STORAGE)` → true（OP type 2）。多次 markDirty 取 OR：一旦标记为 STORAGE 则本轮不降级 |
| `lastOpDate` | MySQL 自动更新 | Poller 认领时排序依据（最早变更优先处理） |
| `nextRetryTime` | 退避控制 | 失败后不立刻重试，等到下次重试时间 |

## 2.2 DDL

```sql
CREATE TABLE VmMetadataDirtyVO (
    vmInstanceUuid VARCHAR(32) NOT NULL,
    managementNodeUuid VARCHAR(32) DEFAULT NULL,
    lastClaimTime TIMESTAMP NULL DEFAULT NULL,
    dirtyVersion BIGINT NOT NULL DEFAULT 1,
    storageStructureChange TINYINT(1) NOT NULL DEFAULT 0,
    retryCount INT NOT NULL DEFAULT 0,
    nextRetryTime TIMESTAMP NULL DEFAULT NULL,
    createDate TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lastOpDate TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (vmInstanceUuid),
    CONSTRAINT fkVmMetadataDirtyVOVmInstanceEO FOREIGN KEY (vmInstanceUuid)
        REFERENCES VmInstanceEO (uuid) ON DELETE CASCADE,
    CONSTRAINT fkVmMetadataDirtyVOManagementNodeVO FOREIGN KEY (managementNodeUuid)
        REFERENCES ManagementNodeVO (uuid) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
```

## 2.3 推荐索引

```sql
-- Poller CAS 认领查询: WHERE managementNodeUuid IS NULL AND nextRetryTime <= NOW()
CREATE INDEX idx_dirty_unclaimed ON VmMetadataDirtyVO (managementNodeUuid, lastClaimTime, nextRetryTime);
```

**约束**：`lastClaimTime` 允许为空（历史数据兼容）；新版本 claim 路径必须在 CAS 成功时写入当前时间。

与 GarbageCollectorVO 的详细对比见 [对比文档 §1](2/vm-metadata-new-02h-compare.md#1-数据模型对比vmmetadatadirtyvo-vs-garbagecollectorvo)。

---

# 3. markDirty — 标脏入口

## 3.1 核心逻辑

```java
public boolean markDirty(String vmInstanceUuid, boolean storageStructureChange) {
    // Q23 修复：返回 boolean 表示标脏是否成功（供 MetadataStaleRecoveryTask DP-03 使用）
    // 前置检查：功能开关
    if (!VmGlobalConfig.VM_METADATA_ENABLED.value(Boolean.class)) {
        return false;
    }

    // Q2-2: Galera 集群兼容写法，避免 INSERT ON DUPLICATE KEY 在高并发下死锁
    // Step 1: INSERT IGNORE（新行）
    int inserted = SQL.New("INSERT IGNORE INTO VmMetadataDirtyVO (vmInstanceUuid, dirtyVersion, storageStructureChange) " +
                           "VALUES (:vmUuid, 1, :ssc)")
        .param("vmUuid", vmInstanceUuid)
        .param("ssc", storageStructureChange)
        .execute();

    // Step 2: 仅在行已存在时执行 UPDATE（dirtyVersion +1, storageStructureChange OR 升级）
    if (inserted == 0) {
        int updated = SQL.New("UPDATE VmMetadataDirtyVO " +
                "SET dirtyVersion = dirtyVersion + 1, " +
                "    storageStructureChange = storageStructureChange OR :ssc " +
                "WHERE vmInstanceUuid = :vmUuid")
            .param("vmUuid", vmInstanceUuid)
            .param("ssc", storageStructureChange)
            .execute();

        // Q19 修复：INSERT IGNORE 返回 0（行已存在）但 UPDATE 也返回 0（行被并发删除）
        // 竞态窗口：INSERT IGNORE → onFlushSuccess DELETE → UPDATE（行已不存在）
        // 此时必须重新 INSERT，否则本次 markDirty 对应的 DB 变更将丢失
        if (updated == 0) {
            SQL.New("INSERT IGNORE INTO VmMetadataDirtyVO (vmInstanceUuid, dirtyVersion, storageStructureChange) " +
                    "VALUES (:vmUuid, 1, :ssc)")
                .param("vmUuid", vmInstanceUuid)
                .param("ssc", storageStructureChange)
                .execute();
        }
    }

    // 立即唤醒：尝试认领并提交刷写，不等待 Poller 轮询
    triggerFlushForVm(vmInstanceUuid);
    return true;
  } catch (Exception e) {
    logger.warn("markDirty failed for vm={}: {}", vmInstanceUuid, e.getMessage());
    return false;
  }
}
```

### 竞态分析与修复

**问题**：`INSERT IGNORE` 与 `UPDATE` 是两个非原子操作，存在以下竞态窗口：

```
T1: API-A 修改 DB
T2: API-A 调用 markDirty → INSERT IGNORE → inserted=0（行已存在，dirtyVersion=N）
T3: Poller flush 完成 → onFlushSuccess: DELETE WHERE dirtyVersion=N → 行被删除
T4: API-A: UPDATE WHERE vmInstanceUuid=:vmUuid → updated=0（行已不存在）
T5: API-A 调用 triggerFlushForVm → 无 dirty 行 → skip
→ API-A 的 DB 变更未被 flush 刷写！
```

**关键**：T3 的 flush 读 DB 快照在 T1 之前（flush 早于 API-A 的 DB 变更），因此写入的元数据不包含 API-A 的修改。T3 DELETE 成功因为 `dirtyVersion` 未被递增（T4 还未执行）。

**修复**：当 `inserted == 0 && updated == 0` 时，重新执行 `INSERT IGNORE`。使用 `INSERT IGNORE` 而非 `INSERT` 保证并发安全（若另一线程同时插入，IGNORE 避免异常）。重新创建的行 `dirtyVersion=1`，Poller/triggerFlush 将从 DB 全量读取最新状态并刷写。

**修复后时序**：

```
T1: API-A 修改 DB
T2: INSERT IGNORE → inserted=0
T3: onFlushSuccess DELETE → 行被删除
T4: UPDATE → updated=0
T5: 重新 INSERT IGNORE → 成功，dirtyVersion=1
T6: triggerFlushForVm → 认领新行 → flush 读 DB（包含 API-A 的修改）→ 写入 (Y)
```

/**
 * 便捷重载：默认 storageStructureChange=false（CONFIG 级别）。
 */
public boolean markDirty(String vmInstanceUuid) {
    return markDirty(vmInstanceUuid, false);
}
```

### 为什么 markDirty 需要检查 `vm.metadata.enabled`？

需要检查。虽然 `VmMetadataUpdateInterceptor` 层已检查功能开关，但 markDirty 还有其他调用方（级联删除、HA 回调、巡检恢复等），这些调用方未必都做了检查。在 markDirty 内统一检查是防御性编程的最低成本方案。

### 为什么不重置 retryCount？

如果 PS 持续不可用，连续 API 触发的 markDirty 不应重置重试计数器，否则永远不会触达上限告警。retryCount 仅在**刷写成功**时重置为 0。

### 为什么不修改 managementNodeUuid？

若 Poller 已认领此行正在刷写，markDirty 不应抢走它。`dirtyVersion` 递增后，刷写完成时会通过版本号比较发现“有新变更”，自动释放让下轮重处理（见 §4.5）。

### markDirty 后立即唤醒

markDirty 后立即调用 `triggerFlushForVm(vmUuid)` 尝试认领并提交刷写，消除最长 5s 的 Poller 等待延迟。Poller 降级为**安全网**，负责处理：退避中的行、MN 宕机后释放的行、triggerFlush 未能认领的行。

```java
/**
 * 立即尝试认领并刷写指定 VM 的 dirty 行。
 * 若行已被认领或处于退避期，跳过（Poller 安全网会处理）。
 */
private void triggerFlushForVm(String vmUuid) {
    String myId = Platform.getManagementServerId();
    // Q20 修复：findStaleClaimOwner 可能返回 null（无 stale claim）。
    // SQL 的 OR 分支使用 :staleId 参数，当 staleId=null 时
    // MySQL 会将 `managementNodeUuid = NULL` 解析为 FALSE（SQL 三值逻辑），
    // 不会误匹配任何行。但为避免依赖此隐式行为，显式处理：
    // staleId=null 时仅使用 IS NULL 分支，不包含 stale 接管条件。
    String staleId = findStaleClaimOwner(vmUuid, Duration.ofMinutes(10));

    String sql;
    if (staleId != null) {
        sql = "UPDATE VmMetadataDirtyVO " +
              "SET managementNodeUuid = :myId, lastClaimTime = CURRENT_TIMESTAMP " +
              "WHERE vmInstanceUuid = :vmUuid " +
              "AND (managementNodeUuid IS NULL " +
              "     OR (managementNodeUuid = :staleId AND lastClaimTime < CURRENT_TIMESTAMP - INTERVAL 10 MINUTE)) " + // 10 → vm.metadata.triggerFlush.staleMinutes
              "AND (nextRetryTime IS NULL OR nextRetryTime <= CURRENT_TIMESTAMP)";
    } else {
        sql = "UPDATE VmMetadataDirtyVO " +
              "SET managementNodeUuid = :myId, lastClaimTime = CURRENT_TIMESTAMP " +
              "WHERE vmInstanceUuid = :vmUuid " +
              "AND managementNodeUuid IS NULL " +
              "AND (nextRetryTime IS NULL OR nextRetryTime <= CURRENT_TIMESTAMP)";
    }

    int claimed = SQL.New(sql)
        .param("myId", myId)
        .param("staleId", staleId)  // null-safe: only used when staleId != null
        .param("vmUuid", vmUuid)
        .execute();

    if (claimed == 0) {
        logger.debug("triggerFlushForVm skip claim, vmUuid={}, reason=already-claimed-or-backoff", vmUuid); // Q2-3
        return;
    }

    VmMetadataDirtyVO dirty = dbf.findByUuid(vmUuid, VmMetadataDirtyVO.class);
    // DP-07 说明：dirty == null 是合法场景。CAS UPDATE 成功（claimed > 0）后、findByUuid 前，
    // 若同 MN 上一个 running flush 的 onFlushSuccess() 恰好执行了条件 DELETE
    // （dirtyVersion 未被新 markDirty 递增），则该行已被删除。
    // 此时直接 return 即可，无需告警——数据已经是最新的。
    if (dirty == null) return;

    submitFlushTask(dirty);  // 提交到 ChainTask（同 Poller 路径）
}
```

**退避中的行不会被立即唤醒**：若 dirty 行处于指数退避（`nextRetryTime > NOW()`），triggerFlush 的 WHERE 条件将其排除。这是有意设计——退避意味着 PS 可能不可用，markDirty 带来的新变更会在退避到期后由 Poller 一并处理。

**DP-06 分析：长时间 flush 与 stale claim 接管的交互**（补充说明）

`triggerFlushForVm()` 的 stale claim 接管阈值为 **10 分钟**，而 `claimDirtyRows()` 的僵尸清理阈值为 **15 分钟**。两者的不对称设计有以下意图：

| 路径 | 阈值 | 理由 |
|------|------|------|
| `triggerFlushForVm` stale 接管 | 10 min | API 热路径，优先保证响应性 |
| `cleanupZombieClaims` 僵尸清理 | 15 min | 批量路径，保守策略避免误抢 |

**潜在问题**：若某次 `doFlush` 因 PS 慢响应耗时 8-12 分钟（未超 5 分钟消息超时但含排队等待），
`triggerFlushForVm()` 可能在第 10 分钟接管该行的 claim，而原 flush 任务仍在 ChainTask 中运行，
导致同一 VM 短暂出现两个并发 flush 意图。由于 per-VM ChainTask `syncLevel=1`，
实际执行仍是串行的，不会产生数据不一致。但建议后续考虑引入 `flushStartTime` 字段，
让 stale 判断基于「flush 实际开始时间」而非「claim 时间」，避免误判。

## 3.2 调用位置

| 调用方 | 场景 | 说明 |
|--------|------|------|
| `VmMetadataUpdateInterceptor.beforePublishEvent()` | `@MetadataImpact` API 成功后 | 主流程 |
| `MetadataCascadeExtension.asyncCascade()` | 级联删除 Volume/Snapshot | 非 API 内部操作 |
| HA handler 完成回调 | HA 重启 VM | 非 API 内部操作 |
| 定时快照清理 handler | 快照删除 | 非 API 内部操作 |
| 内部卷迁移 handler | installPath 变更 | 非 API 内部操作 |
| 升级全量刷新 | 版本变更后批量触发 | 见 §9 |

**两道防线**：

1. **开发规范**：修改 VM 存储拓扑字段的内部消息处理器，成功后必须调用 `markDirty()`
2. **路径指纹巡检兜底**：每次刷写成功后记录 VM 的全量路径快照，独立 PeriodicTask 周期性比对 DB 当前路径 vs 快照，不一致则 `markDirty()`（见 §8.2）

对注册场景，即使元数据暂时落后于 DB，Part 3 §3.4 的 installPath 存在性检查提供额外兗底。

与 GC 方案 submit 的详细对比见 [对比文档 §2](2/vm-metadata-new-02h-compare.md#2-标脏入口对比markdirty-vs-gc-submit)。

---

# 4. MetadataDirtyPoller — 轮询刷写

## 4.1 基本结构

```java
public class MetadataDirtyPoller implements PeriodicTask {
    @Override
    public TimeUnit getTimeUnit() { return TimeUnit.SECONDS; }

    @Override
    public long getInterval() {
        return VmGlobalConfig.VM_METADATA_DIRTY_POLL_INTERVAL.value(Long.class);
        // 默认 5 秒，可通过 GlobalConfig 动态调整
    }

    @Override
    public String getName() { return "vm-metadata-dirty-poller"; }

    @Override
    public void run() {
        claimAndFlush();
    }
}
```

启动：在 `managementNodeReady()` 中 `thdf.submitPeriodicTask(new MetadataDirtyPoller())`。

GlobalConfig 变更时自动重启 Poller（与 SecurityGroup FailureHostWorker 一致）：

```java
VmGlobalConfig.VM_METADATA_DIRTY_POLL_INTERVAL.installUpdateExtension((oldValue, newValue) -> {
    restartPoller();
});
```

**Poller 角色定位**：markDirty 后立即调用 `triggerFlushForVm()` 已覆盖常规场景（见 §3.1）。Poller 降级为**安全网**，负责处理：
- 退避中的行（`nextRetryTime` 到期后才能认领）
- MN 宕机后 FK SET_NULL 释放的孤儿行
- triggerFlushForVm 认领失败的行（已被其他 MN Poller 认领）

## 4.2 认领（CAS 方式）

采用 CAS（单条 UPDATE WHERE NULL LIMIT N），比悲观锁更简洁，避免死锁风险。

```java
private List<VmMetadataDirtyVO> claimDirtyRows() {
    // DP-05 修复：僵尸 claim 清理从 claimDirtyRows() 提取为独立低频任务。
    // 原实现在每次 Poller 周期（5s）执行带 write-intent 的 UPDATE 扫描，
    // 增加了不必要的数据库压力。改为 cleanupZombieClaims() 独立定时执行（见下方）。

    // Step 1: CAS 原子认领 — 单条 UPDATE 天然原子
    String sql = "UPDATE VmMetadataDirtyVO " +
                 "SET managementNodeUuid = :myId, lastClaimTime = CURRENT_TIMESTAMP " +
                 "WHERE managementNodeUuid IS NULL " +
                 "AND (nextRetryTime IS NULL OR nextRetryTime <= CURRENT_TIMESTAMP) " +
                 "ORDER BY lastOpDate ASC, vmInstanceUuid ASC " +   // Q17 修复：按最后标脏时间排序（最早变更优先），vmInstanceUuid 作为稳定 tiebreaker
                 // 原 ORDER BY dirtyVersion ASC 有误：dirtyVersion 是 per-row 值（每行从 1 开始），
                 // 不同 VM 的 dirtyVersion 无序且可能相等，无法反映全局标脏顺序。
                 // lastOpDate 由 MySQL ON UPDATE CURRENT_TIMESTAMP 自动维护，反映最近一次 markDirty 时间，
                 // 适合作为公平调度指标。秒级精度足够（Poller 周期 5s >> 1s 精度）。
                 "LIMIT :batchSize";

    int claimed = SQL.New(sql)
        .param("myId", Platform.getManagementServerId())
        .param("batchSize", VmGlobalConfig.VM_METADATA_DIRTY_BATCH_SIZE.value(Integer.class))
        .execute();

    if (claimed == 0) return Collections.emptyList();

    // Step 2: 查询刚认领到的行（DP-01 修复：增加 lastClaimTime 过滤，
    //         仅返回本轮 CAS 认领的行，避免与 triggerFlushForVm 并发认领的行混入）
    // Q18 说明：thisCycleCutoff = now - 5s 是 Poller 周期的上界。若某轮 Poller 执行（含 CAS UPDATE）
    //          耗时超过 5s（极端负载），cutoff 可能过滤掉本轮认领的行。但这仅导致那些行不被本轮处理，
    //          下轮 Poller 仍会发现它们（已 claimed by this MN + lastClaimTime 匹配）。
    //          更精确的做法是在 Step 1 CAS 前记录 beforeClaim = CURRENT_TIMESTAMP，
    //          Step 2 使用 gte(lastClaimTime, beforeClaim)。但引入 Java↔DB 时间偏差风险。
    //          当前方案的 5s 余量足够覆盖 99.99% 场景，接受此权衡。
    Timestamp thisCycleCutoff = Timestamp.from(Instant.now().minus(Duration.ofSeconds(5)));
    return Q.New(VmMetadataDirtyVO.class)
        .eq(VmMetadataDirtyVO_.managementNodeUuid, Platform.getManagementServerId())
        .gte(VmMetadataDirtyVO_.lastClaimTime, thisCycleCutoff)
        .list();
}
```

**DP-05 改进：僵尸 claim 清理独立为低频任务**

```java
/**
 * 独立的僵尸 claim 清理任务（防御性措施）。
 * 从 claimDirtyRows() 提取，以避免每 5s Poller 周期执行不必要的 write-intent 扫描。
 * 建议间隔：60s（1 分钟），远低于 15 分钟僵尸阈值，足以及时发现异常。
 *
 * 僵尸清理的必要性分析：
 *   MN 正常崩溃 → FK SET NULL 立即释放 claim，Poller 下轮即可重认领。
 *   本任务覆盖的是 FK SET NULL 无法触发的场景：
 *     (a) MN 进程 hang 住（JVM 死锁 / 长 GC），心跳未失效但 flush 永久阻塞；
 *     (b) 网络分区导致目标 Agent 无响应，ChainTask 在 timeout 前持续持有 claim；
 *     (c) 极端：MN 已离线但 ManagementNodeVO 记录因 heartbeat 延迟尚未被清理。
 *   15 分钟阈值 > flush 最大超时（5×60s=5min），安全余量充足。
 */
@PeriodicTask(interval = 60, unit = TimeUnit.SECONDS)
private void cleanupZombieClaims() {
    SQL.New("UPDATE VmMetadataDirtyVO " +
            "SET managementNodeUuid = NULL, lastClaimTime = NULL " +
            "WHERE managementNodeUuid IS NOT NULL " +
            "AND lastClaimTime < CURRENT_TIMESTAMP - INTERVAL 15 MINUTE")
       .execute();
}
```

**说明**：`triggerFlushForVm()` 单 VM 抢占路径允许"stale claim 接管"（10 分钟），Poller 批量路径采用"先清理僵尸再 CAS"的保守策略（15 分钟），避免误抢活跃任务。

**CAS vs 悲观锁**：

| | CAS (UPDATE WHERE NULL) | 悲观锁 (SELECT FOR UPDATE) |
|---|---|---|
| 原子性 | 单条 UPDATE 天然原子 | 需事务包裹 SELECT + UPDATE |
| 死锁风险 | 无 | 双 MN 可能死锁 |
| 性能 | 无锁等待 | 有锁等待 |
| 实现复杂度 | 低 | 中 |

**MySQL 行锁分析**：CAS 的 `UPDATE ... WHERE managementNodeUuid IS NULL AND ... LIMIT N` 在 InnoDB 中会对满足 WHERE 条件的行加 **X 锁**（排他锁）。双 MN 并发执行时，先执行的 UPDATE 获得行锁并将 `managementNodeUuid` 置为非 NULL，后执行的 UPDATE 的 WHERE 条件不再匹配该行，`affected_rows=0`。`LIMIT N` 保证每次最多锁定 N 行，并发窗口极短（微秒级），不会引发死锁。

## 4.3 刷写（Flush）

认领成功后，对每个 dirty row 提交到 ChainTask 执行刷写：

```java
private void claimAndFlush() {
    List<VmMetadataDirtyVO> claimed = claimDirtyRows();
    for (VmMetadataDirtyVO dirty : claimed) {
        submitFlushTask(dirty);
    }
}

private void submitFlushTask(VmMetadataDirtyVO dirty) {
    // 讨论 Δ-1：原方案为嵌套 ChainTask（外层全局限流 + 内层 per-VM 串行）。
    // 重构为单层 per-VM ChainTask + AtomicInteger 全局限流，原因：
    //   1. 嵌套 ChainTask 的 outerChain.next() 必须在 innerChain 完成后调用，
    //      但 exceedMaxPendingCallback 中 outerChain.next() 直接调用导致
    //      outer slot 提前释放，全局限流语义被破坏。
    //   2. 嵌套结构难以推断 Chain 生命周期，增加维护和调试成本。
    //   3. AtomicInteger 全局计数器语义简单明确：flush 开始 increment、
    //      完成（成功/失败/exceed）decrement，超限时 skip。
    //
    // 新结构：
    //   - 全局 AtomicInteger globalFlushInFlight（初始 0）
    //   - submitFlushTask 先检查 globalFlushInFlight < maxConcurrent，
    //     超限时释放 claim 并 return
    //   - 通过则 increment，提交到 per-VM ChainTask(syncLevel=1, maxPending=1)
    //   - doFlush 完成回调中 decrement

    int maxConcurrent = VmGlobalConfig.VM_METADATA_GLOBAL_MAX_CONCURRENT.value(Integer.class);
    if (globalFlushInFlight.get() >= maxConcurrent) {
        // 全局并发已满，释放 claim，Poller 下轮重试
        releaseClaim(dirty.getVmInstanceUuid());
        return;
    }
    globalFlushInFlight.incrementAndGet();

    // 单层 per-VM 串行 + 去重
    thdf.chainSubmit(new ChainTask(null) {
        @Override
        public String getSyncSignature() {
            return "update-vm-" + dirty.getVmInstanceUuid() + "-metadata";
        }
        @Override
        public int getSyncLevel() { return 1; }
        @Override
        public int getMaxPendingTasks() { return 1; }
        @Override
        public String getDeduplicateString() { return getSyncSignature(); }

        @Override
        public void exceedMaxPendingCallback() {
            // 已有 running + pending，本次多余
            // Δ-1 改进：在单层结构中，exceed 时直接 decrement 并释放 claim
            globalFlushInFlight.decrementAndGet();
            releaseClaim(dirty.getVmInstanceUuid());
        }

        @Override
        public void run(SyncTaskChain chain) {
            doFlush(dirty, () -> {
                globalFlushInFlight.decrementAndGet();
                chain.next();
            });
        }
    });
}
```

## 4.4 doFlush 核心逻辑

```java
private void doFlush(VmMetadataDirtyVO dirty, Runnable chainNext) {
    String vmUuid = dirty.getVmInstanceUuid();

    // P2 修复：重新从 DB 读取 dirty 行，获取最新的 storageStructureChange 和 dirtyVersion。
    // 原因：submitFlushTask 传入的 dirty 对象是 CAS 认领时的缓存快照，排队等待期间
    // 可能有新的 markDirty(storageStructureChange=true) 通过 OR 升级了该字段。
    // 若使用缓存值，会导致本轮 flush 的 storageStructureChange=false，
    // 而 DB 中实际已为 true（如存储迁移触发的 markDirty），写入时用错 tmp 后缀。
    VmMetadataDirtyVO latestDirty = dbf.findByUuid(vmUuid, VmMetadataDirtyVO.class);
    if (latestDirty == null) {
        // VM 已删除（FK CASCADE）或 onFlushSuccess 已删除该行
        chainNext.run();
        return;
    }

    // 0. 记录刷写开始时的 dirtyVersion 快照（使用最新值）
    long snapshotVersion = latestDirty.getDirtyVersion();

    // 1. 前置检查：VM 是否存在
    if (!dbf.isExist(vmUuid, VmInstanceVO.class)) {
        // VM 已删除，FK CASCADE 应已删除 dirty 行，兜底删除
        SQL.New(VmMetadataDirtyVO.class)
           .eq(VmMetadataDirtyVO_.vmInstanceUuid, vmUuid).delete();
        chainNext.run();
        return;
    }

    // 1b. Q34 修复：过滤 Destroyed 状态的 VM
    // VM 正在销毁过程中（state=Destroyed），EO 尚未物理删除，FK CASCADE 未触发。
    // 此时刷写元数据无意义——销毁完成后 EO 删除时 dirty 行会被级联清理。
    // 主动删除 dirty 行释放 Poller 资源，避免对即将销毁的 VM 执行无效的 Agent 调用。
    VmInstanceState vmState = Q.New(VmInstanceVO.class)
        .eq(VmInstanceVO_.uuid, vmUuid)
        .select(VmInstanceVO_.state)
        .findValue();
    if (vmState == VmInstanceState.Destroyed) {
        SQL.New(VmMetadataDirtyVO.class)
           .eq(VmMetadataDirtyVO_.vmInstanceUuid, vmUuid).delete();
        chainNext.run();
        return;
    }

    // 2. 发送 UpdateVmInstanceMetadataMsg → VmInstanceBase 负责构建 payload 并写入主存储
    //    payload 构建（buildVmInstanceMetadata）和大小保护均在 VmInstanceBase 内部完成
    UpdateVmInstanceMetadataMsg msg = new UpdateVmInstanceMetadataMsg();
    msg.setUuid(vmUuid);
    msg.setStorageStructureChange(latestDirty.isStorageStructureChange());
    msg.setTimeout(TimeUnit.MINUTES.toMillis(5));
    bus.makeLocalServiceId(msg, VmInstanceConstant.SERVICE_ID);

    bus.send(msg, new CloudBusCallBack(null) {
        @Override
        public void run(MessageReply reply) {
            if (reply.isSuccess()) {
                onFlushSuccess(vmUuid, snapshotVersion);
            } else {
                onFlushFailure(vmUuid, reply.getError());
            }
            chainNext.run();
        }
    });
}
```

## 4.5 刷写成功处理

```java
// DP-04 修复 + 讨论 Δ-2：原方案使用 @Transactional 包装 DELETE + fallback UPDATE。
// 改为 SQLBatch 替代 @Transactional，原因：
//   1. @Transactional 由 Spring AOP 代理实现，要求方法为 public 且通过代理对象调用。
//      onFlushSuccess 作为内部回调方法，直接调用（this.onFlushSuccess）不经过代理，
//      @Transactional 不生效（"self-invocation 陷阱"）。
//   2. SQLBatch 是 ZStack 原生事务工具，无代理依赖，显式包装事务边界，
//      在 callback/lambda 场景中更可靠。
//   3. 逻辑不变：DELETE + fallback UPDATE 仍在同一事务内原子执行。
private void onFlushSuccess(String vmUuid, long snapshotVersion) {
    new SQLBatch() {
        @Override
        protected void scripts() {
            // 条件删除：仅当 dirtyVersion == snapshotVersion 时删除
            // 即"刷写期间没有新的 markDirty 到来"
            int deleted = SQL.New(VmMetadataDirtyVO.class)
                .eq(VmMetadataDirtyVO_.vmInstanceUuid, vmUuid)
                .eq(VmMetadataDirtyVO_.dirtyVersion, snapshotVersion)
                .delete();

            if (deleted == 0) {
                // dirtyVersion > snapshotVersion → 刷写期间有新变更
                // 释放认领，让 triggerFlush / Poller 重新处理
                // 同时重置 retryCount（本次成功说明通路正常）
                SQL.New(VmMetadataDirtyVO.class)
                   .eq(VmMetadataDirtyVO_.vmInstanceUuid, vmUuid)
                   .set(VmMetadataDirtyVO_.managementNodeUuid, null)
                   .set(VmMetadataDirtyVO_.retryCount, 0)
                   .set(VmMetadataDirtyVO_.nextRetryTime, null)
                   .update();
            }
            // deleted > 0 → 行已删除，彻底完成
        }
    }.execute();

    // 讨论 Δ-9：savePathFingerprint 复用 buildVmInstanceMetadata 的 installPath 列表。
    // 原方案在 savePathFingerprint 中独立查询 VolumeVO + VolumeSnapshotVO。
    // 改为 buildVmInstanceMetadata 返回复合对象（含 payload + installPath list），
    // onFlushSuccess 直接传入 installPath list 给 savePathFingerprint，
    // 避免重复查询，减少一次 DB roundtrip。
    // 具体实现：doFlush 中 buildVmInstanceMetadata 返回 BuildResult{payload, pathSnapshot}，
    // onFlushSuccess(vmUuid, snapshotVersion, pathSnapshot) 传入预计算的 pathSnapshot。
    savePathFingerprint(vmUuid);
}
```

**这是整个方案最关键的设计点**。`dirtyVersion` 比较确保不会丢失刷写期间产生的新变更：

```
T0: markDirty(vm-1) → INSERT, dirtyVersion=1
T1: 认领 → snapshotVersion=1
T2: 刷写进行中... buildVmInstanceMetadata() 读到 v1
T3: API 成功 → markDirty(vm-1) → dirtyVersion=2  ← 新变更！
T4: 刷写完成，写入 v1
T5: onFlushSuccess → DELETE WHERE dirtyVersion = 1
    → 当前 dirtyVersion=2 ≠ 1 → deleted=0
    → 释放认领 → triggerFlush 立即重处理 → 读到 v2 → 写入 v2 (Y)
```

如果不做 `dirtyVersion` 比较直接删除，T3 的变更就丢了——这正是 GC `deduplicateSubmit` 遇到的同类问题，新方案用版本号比较优雅解决。相比 `lastOpDate` 时间戳比较，`dirtyVersion` 整数比较语义更明确、无时间精度问题。

## 4.6 刷写失败处理

```java
private void onFlushFailure(String vmUuid, ErrorCode error) {
    // Q21 — 原子性分析：先 findByUuid 再 UPDATE 存在微窗口（读到的 retryCount 可能被
    //        并发 markDirty 改变）。但 markDirty 不修改 retryCount（仅递增 dirtyVersion），
    //        且同一 VM 同一时刻只有一个 flush 任务（Layer 1 CAS + Layer 3 per-VM syncLevel=1），
    //        因此 onFlushFailure 的 findByUuid→UPDATE 在同 VM 上无并发竞争。安全。
    VmMetadataDirtyVO dirty = dbf.findByUuid(vmUuid, VmMetadataDirtyVO.class);
    if (dirty == null) return;  // VM 已销毁，FK CASCADE 已清理

    int newRetryCount = dirty.getRetryCount() + 1;
    int maxRetry = VmGlobalConfig.VM_METADATA_MAX_RETRY.value(Integer.class);  // 默认 5
    int baseDelay = VmGlobalConfig.VM_METADATA_RETRY_BASE_DELAY_SECONDS.value(Integer.class);    // Q2-6
    int maxExponent = VmGlobalConfig.VM_METADATA_RETRY_MAX_EXPONENT.value(Integer.class);        // Q2-6

    if (newRetryCount >= maxRetry) {
        // 达到上限 → 告警 + 标记 stale（H2 修复：不再直接删除）
        logger.error("metadata update for vm {} failed after {} retries, marking as stale. " +
                     "MetadataStaleRecoveryTask will retry independently.",
                     vmUuid, newRetryCount);

        // 在 PathFingerprintVO 上标记 lastFlushFailed=true（M1 修复）
        SQL.New("UPDATE VmMetadataPathFingerprintVO " +
                "SET lastFlushFailed = 1 WHERE vmInstanceUuid = :vmUuid")
            .param("vmUuid", vmUuid)
            .execute();

        // 删除 dirty 行（释放 Poller 资源），stale 恢复由独立任务接管
        SQL.New(VmMetadataDirtyVO.class)
           .eq(VmMetadataDirtyVO_.vmInstanceUuid, vmUuid).delete();
        return;
    }

    // 未达上限 → 释放认领 + 指数退避（Q2-6: 参数改为 GlobalConfig）
    long delaySec = baseDelay * (1L << Math.min(newRetryCount, maxExponent));
    Timestamp nextRetry = Timestamp.from(Instant.now().plusSeconds(delaySec));

    SQL.New(VmMetadataDirtyVO.class)
       .eq(VmMetadataDirtyVO_.vmInstanceUuid, vmUuid)
       .set(VmMetadataDirtyVO_.managementNodeUuid, null)   // 释放认领
       .set(VmMetadataDirtyVO_.retryCount, newRetryCount)
       .set(VmMetadataDirtyVO_.nextRetryTime, nextRetry)
       .update();

    logger.warn("metadata update for vm {} failed (retry {}/{}), next retry at {}",
                vmUuid, newRetryCount, maxRetry, nextRetry);
}
```

**指数退避表**：

| 尝试次数 | retryCount 变化 | 下次退避延迟 | 累计耗时 |
|----------|-----------------|-------------|----------|
| 1 | 0 → 1 | 20s | ~25s |
| 2 | 1 → 2 | 40s | ~65s |
| 3 | 2 → 3 | 80s | ~145s |
| 4 | 3 → 4 | 160s | ~305s |
| 5 | 4 → 5 | — | 放弃 |

延迟公式：`vm.metadata.retry.baseDelaySeconds × 2^min(retryCount, vm.metadata.retry.maxExponent)`；默认值分别为 `10`、`10`。默认 5 次重试，总耗时约 5 分钟后放弃。

## 4.7 辅助方法

```java
private void releaseClaim(String vmUuid) {
    SQL.New(VmMetadataDirtyVO.class)
       .eq(VmMetadataDirtyVO_.vmInstanceUuid, vmUuid)
       .set(VmMetadataDirtyVO_.managementNodeUuid, null)
       .update();
}
```

## 4.8 Stale 恢复任务（H2 修复）

当 dirty 行因重试耗尽被删除后，低频 VM（长期无 `@MetadataImpact` API）将失去自愈机会。为此引入独立的 `MetadataStaleRecoveryTask`：

```java
public class MetadataStaleRecoveryTask implements PeriodicTask {
    @Override
    public long getInterval() {
        return VmGlobalConfig.VM_METADATA_STALE_RECOVERY_INTERVAL.value(Long.class);
        // 默认 1800 秒（30 分钟）
    }

    @Override
    public void run() {
        // 查找所有 lastFlushFailed=true 的指纹记录
        List<VmMetadataPathFingerprintVO> staleVms = SQL.New(
            "SELECT fp FROM VmMetadataPathFingerprintVO fp WHERE fp.lastFlushFailed = 1",
            VmMetadataPathFingerprintVO.class)
            .limit(VmGlobalConfig.VM_METADATA_STALE_RECOVERY_BATCH.value(Integer.class))  // 默认 100
            .list();

        for (VmMetadataPathFingerprintVO fp : staleVms) {
            // 重新 markDirty，给予全新的重试机会（retryCount=0）
            // DP-03 修复：先验证 markDirty 成功，再清除 stale 标记；
            //            若 markDirty 失败（如 DB 连接异常），保留 lastFlushFailed=true，
            //            下一轮 StaleRecoveryTask 会重试。
            boolean markSuccess = markDirty(fp.getVmInstanceUuid());
            if (markSuccess) {
                // markDirty 成功 → 安全清除 stale 标记（由下轮 Poller 处理）
                SQL.New("UPDATE VmMetadataPathFingerprintVO " +
                        "SET lastFlushFailed = 0 WHERE vmInstanceUuid = :vmUuid")
                    .param("vmUuid", fp.getVmInstanceUuid())
                    .execute();
            } else {
                // markDirty 失败 → 保留 lastFlushFailed=true，记录日志
                logger.warn("MetadataStaleRecoveryTask: markDirty failed for vm={}, " +
                            "keeping lastFlushFailed=true for next retry cycle",
                            fp.getVmInstanceUuid());
            }
        }

        if (!staleVms.isEmpty()) {
            logger.info("MetadataStaleRecoveryTask re-queued {} stale VMs for retry", staleVms.size());
        }
    }
}
```

**关键设计点**：
- 独立于 Poller 的 PeriodicTask，不受 Poller 退避机制约束
- 每 30 分钟扫描一次，每次最多处理 100 个 stale VM
- 重新 `markDirty()` 给予全新重试机会（retryCount=0），不继承历史退避
- **DP-03 修复**：`markDirty()` 返回 boolean，仅在成功时才清除 `lastFlushFailed`；失败时保留标记，下轮再试
- 若 PS 仍不可用，该 VM 会再次走完 Poller 的 5 次重试 → 再次标记 stale → 30 分钟后再次恢复，形成"慢速重试"闭环
- 当 PS 恢复可用时，下一轮 stale recovery 触发的 markDirty 自然成功

---

# 5. 消息调用链

## 5.1 新调用链

```
API (e.g. StartVmInstanceMsg) 成功
  ↓
VmMetadataUpdateInterceptor.beforePublishEvent()
  ↓
markDirty(vmUuid)   ← INSERT/UPDATE + dirtyVersion++，本地操作，无跨 MN
  ↓
triggerFlushForVm(vmUuid)   ← 立即唤醒：CAS 认领单行 + 提交 ChainTask
  ↓（认领失败时由 Poller 安全网兆底，≤5s）
  ↓ AtomicInteger globalFlushInFlight 检查（Δ-1：替代原嵌套 ChainTask 外层）
  ↓ per-VM ChainTask "update-vm-{vmUuid}-metadata" (syncLevel=1, maxPending=1)
  ↓
doFlush()
  → bus.send(UpdateVmInstanceMetadataMsg) → makeLocalServiceId
  ↓
VmInstanceBase.handle(UpdateVmInstanceMetadataMsg)
  → buildVmInstanceMetadata(vmUuid) — DB 全量读取（@Transactional(readOnly=true)）
  → payload 大小保护（>8MB 告警, >30MB 拒绝）
  ↓
bus.send(UpdateVmInstanceMetadataOnPrimaryStorageMsg) → makeLocalServiceId
  ↓
NFS/LocalStorage/SharedBlock.handle()
  ↓ ChainTask "update-metadata-on-ps-{psUuid}"
  ↓ 选取 Host → UpdateVmInstanceMetadataOnHypervisorMsg
  ↓ makeTargetServiceIdByResourceUuid(hostUuid)  ← 保留 hash 环路由
  ↓
HostBase.handle() → HTTP call to KVM agent
  ↓
成功 → onFlushSuccess() → 条件 DELETE
失败 → onFlushFailure() → 指数退避释放
```

**OP type 由管理层面指定**：`@MetadataImpact(CONFIG)` → OP type=1（仅配置变更），`@MetadataImpact(STORAGE)` → OP type=2（存储拓扑变更，sblk 场景设置 pending_op=2）。OP type 通过 `storageStructureChange` 字段贯穿整条消息链（`VmMetadataDirtyVO` → `UpdateVmInstanceMetadataMsg` → `UpdateVmInstanceMetadataOnPrimaryStorageMsg` → `UpdateVmInstanceMetadataOnHypervisorMsg`）。dirty 行使用 OR 升级策略：多次 markDirty 中只要有一次是 STORAGE，本轮刷写即使用 OP type=2。

**消息超时**：`UpdateVmInstanceMetadataMsg` 设置为 `5min`（防止内层任务 hang 导致 claim 长期占用）；`UpdateVmInstanceMetadataOnHypervisorMsg` 保持 `2min`。超时后统一进入 `onFlushFailure()` 释放认领并退避重试。

与 GC 方案消息链的详细对比见 [对比文档 §3](2/vm-metadata-new-02h-compare.md#3-消息调用链对比)。

## 5.2 消息路由策略

| 消息 | 路由方式 | 说明 |
|------|----------|------|
| `UpdateVmInstanceMetadataMsg` | `makeLocalServiceId` | Poller 本地发起 |
| `UpdateVmInstanceMetadataOnPrimaryStorageMsg` | `makeLocalServiceId` | 无本地状态依赖 |
| `UpdateVmInstanceMetadataOnHypervisorMsg` | `makeTargetServiceIdByResourceUuid(hostUuid)` | 需路由到 host-owner MN |

---

# 6. 并发控制（四层）

## 6.1 四层串行化保证

```
Layer 1 — DB CAS 认领
    UPDATE WHERE managementNodeUuid IS NULL → 同一行只被一个 MN 处理
    ⇒ 同一 VM 的刷写不会在两个 MN 上同时执行

Layer 2 — 全局限流（AtomicInteger）
    globalFlushInFlight AtomicInteger (默认上限 10，可通过 GlobalConfig 调整)
    ⇒ 同一 MN 最多 N 个 VM 同时更新
    讨论 Δ-1 变更：原方案为嵌套 ChainTask 外层全局队列，
    改为 AtomicInteger 计数器。语义等价但消除了嵌套 Chain 的复杂性。
    submitFlushTask 入口检查 get() >= maxConcurrent 时直接 releaseClaim 跳过。

Layer 3 — per-VM 串行队列 "update-vm-{vmUuid}-metadata"
    syncLevel=1, maxPendingTasks=1
    ⇒ 同一 VM 最多 1 个正在执行 + 1 个排队
    ⇒ 超出时 exceedMaxPendingCallback() → decrementAndGet + releaseClaim

Layer 4 — 主存储级队列 "update-metadata-on-ps-{psUuid}"
    syncLevel = vm.metadata.ps.maxConcurrent (GlobalConfig, 默认 5)
    ⇒ 同一 MN 上，同一存储最多 N 个并发写入
    ⇒ 双 MN 环境下实际全局并发 = 2 × syncLevel
```

与 GC 方案并发控制的详细对比见 [对比文档 §4](2/vm-metadata-new-02h-compare.md#4-并发控制对比)。

## 6.2 全局限流

**讨论 Δ-1 重构后结构**：

原嵌套 ChainTask 结构已简化为单层结构：

```
AtomicInteger globalFlushInFlight (上限 = vm.metadata.global.maxConcurrent, 默认 10)
  └── per-VM ChainTask: syncSignature = "update-vm-{vmUuid}-metadata"
        syncLevel = 1, maxPendingTasks = 1, deduplicateString = syncSignature
```

- `globalFlushInFlight` 控制全局并发数，每个 MN 最多 N 个 VM 同时更新
- per-VM ChainTask 保证 per-VM 串行 + 去重
- `exceedMaxPendingCallback` 中直接 `decrementAndGet()` + `releaseClaim()`，不再持有 claim

**per-MN 语义**：`globalFlushInFlight` 是 JVM 本地计数器。双 MN 环境下实际全局并发最大为 `2 × maxConcurrent`。DB CAS 认领已保证同一 VM 不会在两个 MN 上同时执行。

## 6.3 Layer 3 实现位置

各主存储 `handle(UpdateVmInstanceMetadataOnPrimaryStorageMsg)` 内部用 `thdf.chainSubmit()` 包装：

- `getSyncSignature()` → `"update-metadata-on-ps-" + self.getUuid()`
- `getSyncLevel()` → 读取 `VmGlobalConfig.VM_METADATA_PS_MAX_CONCURRENT`
- `run()` → 调用实际写入逻辑后 `chain.next()`

**外层全局计数器与 Layer 4 的交互**：AtomicInteger `globalFlushInFlight` 上限为 10（默认），限制单个 MN 上同时最多 10 个 VM 的元数据更新在执行。这 10 个并发任务分布在不同主存储上时，Layer 4 per-PS 队列 `syncLevel=5` 进一步约束同一存储的并发数。AtomicInteger 控制"总水位"，Layer 4 控制"每个 PS 的分水位"，二者共同生效。

**文档化要求**：`syncLevel` 和 AtomicInteger 全部为 JVM 本地语义。跨 MN 并发由 DB CAS 认领控制，不通过 ChainTask 全局共享队列。

## 6.4 调优指南

### 默认值推导

- `batchSize=50`：按平均每 VM flush 200ms 估算，50 台约 10s/轮；实际耗时受 `global.maxConcurrent=10` 并行限制。
- `global.maxConcurrent=10`：管理节点线程池默认 500 线程，10 个并发约占 2%，对其他业务影响可控。
- `ps.maxConcurrent=5`：限制单主存储写入并发，避免元数据 flush 风暴挤占业务 IO。

### 调优参考表

| 环境规模 | VM 数量 | batchSize | global.maxConcurrent | ps.maxConcurrent | pollInterval |
|----------|---------|-----------|----------------------|------------------|--------------|
| 小型 | <500 | 50 | 10 | 5 | 5s |
| 中型 | 500-5000 | 100 | 20 | 10 | 5s |
| 大型 | >5000 | 200 | 30 | 15 | 10s |

### 调优顺序建议

1. 先调 `global.maxConcurrent`（观察 MN CPU/线程池饱和度）；
2. 再调 `ps.maxConcurrent`（观察单 PS 延迟与业务 IO 干扰）；
3. 最后调 `batchSize` 与 `pollInterval`（平衡吞吐与扫描开销）。

---

**文档拆分**：§7-§13（高可用、恢复策略、升级刷新、Payload 保护、开发约束、GlobalConfig）已迁移至 [Part 2b — 高可用与运维](vm-metadata-02b-高可用与运维.md)。

# 7. 约束与不変量

| 约束 ID | 约束描述 | 违反后果 |
|---------|----------|----------|
| C-DM-01 | `markDirty` 在集群模式下必须使用 `INSERT IGNORE + UPDATE` 两步，禁止回退为 `INSERT ON DUPLICATE KEY`。当 `inserted==0 && updated==0` 时必须重新 `INSERT IGNORE` 防止竞态丢失。**例外**：升级全量刷新场景（Part 2b §9）中批量 `markDirty` 可使用等效的批量 INSERT IGNORE + 批量 UPDATE 优化，但必须保持「先 INSERT IGNORE 再 UPDATE」的两步语义 | Galera 高并发下死锁概率上升，标脏链路抖动；竞态下 DB 变更丢失不被刷写 |
| C-CL-02 | 任何 claim 成功路径必须写入 `lastClaimTime`，并执行僵尸 claim 清理（15 分钟）。注：僵尸清理已独立为低频任务 `cleanupZombieClaims()`（DP-05） | hang 任务可能导致 dirty 行永久锁定 |
| C-TM-03 | `doFlush` 消息超时不得低于 5 分钟，且超时必须进入 `onFlushFailure` 释放 claim | inner task 卡死时无法自愈 |
| C-RB-04 | 指数退避参数必须来自 GlobalConfig（baseDelay/maxExponent），禁止硬编码常量 | 运维无法按环境调优重试节奏 |
| C-SR-05 | 重试耗尽时必须在 `VmMetadataPathFingerprintVO` 标记 `lastFlushFailed=true`，不得仅删除 dirty 行后静默放弃 | Stale VM 永久失去自愈路径 |
| C-SR-06 | `MetadataStaleRecoveryTask` 的 `markDirty()` 必须使用 retryCount=0（全新起点），不得继承历史退避。同时必须验证 `markDirty()` 返回值，仅在成功时清除 `lastFlushFailed`（DP-03） | 历史退避会导致立即再次耗尽；无条件清除可能永久丢失 stale 标记 |
| C-SC-07 | `storageStructureChange` 标记仅在真正影响存储拓扑的操作中设置（卷创建/删除/迁移/挂载/卸载），不得在纯属性修改（如改名、改描述）时误设。升级全量刷新场景中，`storageStructureChange` 应始终为 `true`（因为无法判断升级前后存储拓扑是否变化） | 误设 true → 触发不必要的 sblk 存储拓扑重建，增加 IO 开销；误设 false → 升级后存储拓扑变更未反映到 sblk |
| C-FL-08 | `doFlush` 必须在前置检查中过滤 `VmInstanceVO.state == Destroyed` 的 VM，主动删除 dirty 行释放 Poller 资源 | 对即将销毁的 VM 执行无效 Agent 调用，浪费资源并可能因 VM 关联存储正在清理而失败 |
