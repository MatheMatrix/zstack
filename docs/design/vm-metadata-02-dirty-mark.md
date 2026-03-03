# Dirty Mark + Poller

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

> 用一张 **`VmMetadataDirtyVO`** 表做脏标记（一个 VM 最多一行），**`PeriodicTask`** 轮询器定期认领并刷写，成功删行，失败释放等下轮。

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
| `vmInstanceUuid` FK CASCADE | VM 销毁自动删除脏标记 | 无残留 |
| `dirtyVersion` | 每次 markDirty +1 | 刷写前快照 version，成功后比较——检测刷写期间是否有新变更（见 §4.5）。语义比时间戳比较更明确，无精度问题 |
| `storageStructureChange` | OR 升级策略 | `@MetadataImpact(CONFIG)` → false（OP type 1），`@MetadataImpact(STORAGE)` → true（OP type 2）。多次 markDirty 取 OR：一旦标记为 STORAGE 则本轮不降级 |
| `lastOpDate` | MySQL 自动更新 | Poller 认领时排序依据（最早变更优先处理） |
| `nextRetryTime` | 退避控制 | 失败后不立刻重试，等到下次重试时间 |

## 2.2 DDL

```sql
CREATE TABLE VmMetadataDirtyVO (
    vmInstanceUuid VARCHAR(32) NOT NULL,
    managementNodeUuid VARCHAR(32) DEFAULT NULL,
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
CREATE INDEX idx_dirty_unclaimed ON VmMetadataDirtyVO (managementNodeUuid, nextRetryTime);
```

> 与 GarbageCollectorVO 的详细对比见 [对比文档 §1](2/vm-metadata-new-02h-compare.md#1-数据模型对比vmmetadatadirtyvo-vs-garbagecollectorvo)。

---

# 3. markDirty — 标脏入口

## 3.1 核心逻辑

```java
public void markDirty(String vmInstanceUuid, boolean storageStructureChange) {
    // 前置检查：功能开关
    if (!VmGlobalConfig.VM_METADATA_ENABLED.value(Boolean.class)) {
        return;
    }

    // INSERT ... ON DUPLICATE KEY UPDATE dirtyVersion = dirtyVersion + 1
    // 若行不存在 → INSERT（dirtyVersion=1, managementNodeUuid=null, retryCount=0）
    // 若行已存在 → dirtyVersion +1（标记"有新变更"）
    //   storageStructureChange 使用 OR 升级：一旦标 STORAGE 则不降级
    //   不重置 retryCount（保留退避状态）
    //   不修改 managementNodeUuid（不干扰正在执行的刷写）
    //   不修改 nextRetryTime（不干扰退避计时）
    SQL.New("INSERT INTO VmMetadataDirtyVO (vmInstanceUuid, dirtyVersion, storageStructureChange) " +
            "VALUES (:vmUuid, 1, :ssc) " +
            "ON DUPLICATE KEY UPDATE dirtyVersion = dirtyVersion + 1, " +
            "storageStructureChange = storageStructureChange OR VALUES(storageStructureChange)")
       .param("vmUuid", vmInstanceUuid)
       .param("ssc", storageStructureChange)
       .execute();

    // 立即唤醒：尝试认领并提交刷写，不等待 Poller 轮询
    triggerFlushForVm(vmInstanceUuid);
}

/**
 * 便捷重载：默认 storageStructureChange=false（CONFIG 级别）。
 */
public void markDirty(String vmInstanceUuid) {
    markDirty(vmInstanceUuid, false);
}
```

### 为什么 markDirty 需要检查 `vm.metadata.enabled`？

> **讨论结论**：需要检查。虽然 `VmMetadataUpdateInterceptor` 层已检查功能开关，但 markDirty 还有其他调用方（级联删除、HA 回调、巡检恢复等），这些调用方未必都做了检查。在 markDirty 内统一检查是防御性编程的最低成本方案。

### 为什么不重置 retryCount？

如果 PS 持续不可用，连续 API 触发的 markDirty 不应重置重试计数器，否则永远不会触达上限告警。retryCount 仅在**刷写成功**时重置为 0。

### 为什么不修改 managementNodeUuid？

若 Poller 已认领此行正在刷写，markDirty 不应抢走它。`dirtyVersion` 递增后，刷写完成时会通过版本号比较发现“有新变更”，自动释放让下轮重处理（见 §4.5）。

### markDirty 后立即唤醒

> **讨论结论**：markDirty 后立即调用 `triggerFlushForVm(vmUuid)` 尝试认领并提交刷写，消除最长 5s 的 Poller 等待延迟。Poller 降级为**安全网**，负责处理：退避中的行、MN 宕机后释放的行、triggerFlush 未能认领的行。

```java
/**
 * 立即尝试认领并刷写指定 VM 的 dirty 行。
 * 若行已被认领或处于退避期，跳过（Poller 安全网会处理）。
 */
private void triggerFlushForVm(String vmUuid) {
    int claimed = SQL.New("UPDATE VmMetadataDirtyVO " +
                          "SET managementNodeUuid = :myId " +
                          "WHERE vmInstanceUuid = :vmUuid " +
                          "AND managementNodeUuid IS NULL " +
                          "AND (nextRetryTime IS NULL OR nextRetryTime <= CURRENT_TIMESTAMP)")
        .param("myId", Platform.getManagementServerId())
        .param("vmUuid", vmUuid)
        .execute();

    if (claimed == 0) return;  // 已被认领 or 退避中 → Poller 处理

    VmMetadataDirtyVO dirty = dbf.findByUuid(vmUuid, VmMetadataDirtyVO.class);
    if (dirty == null) return;

    submitFlushTask(dirty);  // 提交到 ChainTask（同 Poller 路径）
}
```

> **退避中的行不会被立即唤醒**：若 dirty 行处于指数退避（`nextRetryTime > NOW()`），triggerFlush 的 WHERE 条件将其排除。这是有意设计——退避意味着 PS 可能不可用，markDirty 带来的新变更会在退避到期后由 Poller 一并处理。

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
2. **健康巡检兜底**（暂缓）：后续版本可引入周期巡检全量比对 DB vs 存储元数据，发现不一致则 `markDirty()` 触发 full-refresh

> 对注册场景，即使元数据暂时落后于 DB，Part 3 §3.4 的 installPath 存在性检查提供额外兜底。

> 与 GC 方案 submit 的详细对比见 [对比文档 §2](2/vm-metadata-new-02h-compare.md#2-标脏入口对比markdirty-vs-gc-submit)。

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

> **Poller 角色定位**：markDirty 后立即调用 `triggerFlushForVm()` 已覆盖常规场景（见 §3.1）。Poller 降级为**安全网**，负责处理：
> - 退避中的行（`nextRetryTime` 到期后才能认领）
> - MN 宕机后 FK SET_NULL 释放的孤儿行
> - triggerFlushForVm 认领失败的行（已被其他 MN Poller 认领）

## 4.2 认领（CAS 方式）

> **讨论结论**：采用 CAS（单条 UPDATE WHERE NULL LIMIT N），比悲观锁更简洁，避免死锁风险。

```java
private List<VmMetadataDirtyVO> claimDirtyRows() {
    // Step 1: CAS 原子认领 — 单条 UPDATE 天然原子
    String sql = "UPDATE VmMetadataDirtyVO " +
                 "SET managementNodeUuid = :myId " +
                 "WHERE managementNodeUuid IS NULL " +
                 "AND (nextRetryTime IS NULL OR nextRetryTime <= CURRENT_TIMESTAMP) " +
                 "ORDER BY lastOpDate ASC " +   // 最早变更的优先
                 "LIMIT :batchSize";

    int claimed = SQL.New(sql)
        .param("myId", Platform.getManagementServerId())
        .param("batchSize", VmGlobalConfig.VM_METADATA_DIRTY_BATCH_SIZE.value(Integer.class))
        .execute();

    if (claimed == 0) return Collections.emptyList();

    // Step 2: 查询刚认领到的行
    return Q.New(VmMetadataDirtyVO.class)
        .eq(VmMetadataDirtyVO_.managementNodeUuid, Platform.getManagementServerId())
        .list();
}
```

**CAS vs 悲观锁**：

| | CAS (UPDATE WHERE NULL) | 悲观锁 (SELECT FOR UPDATE) |
|---|---|---|
| 原子性 | 单条 UPDATE 天然原子 | 需事务包裹 SELECT + UPDATE |
| 死锁风险 | 无 | 双 MN 可能死锁 |
| 性能 | 无锁等待 | 有锁等待 |
| 实现复杂度 | 低 | 中 |

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
    // 外层全局限流
    thdf.chainSubmit(new ChainTask(null) {
        @Override
        public String getSyncSignature() {
            return "update-vm-metadata-global";
        }
        @Override
        public int getSyncLevel() {
            return VmGlobalConfig.VM_METADATA_GLOBAL_MAX_CONCURRENT.value(Integer.class);
        }

        @Override
        public void run(SyncTaskChain outerChain) {
            // 内层 per-VM 串行 + 去重
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
                    // 已有 running + pending，本次多余 → 释放认领
                    releaseClaim(dirty.getVmInstanceUuid());
                    outerChain.next();
                }

                @Override
                public void run(SyncTaskChain innerChain) {
                    doFlush(dirty, () -> {
                        innerChain.next();
                        outerChain.next();
                    });
                }
            });
        }
    });
}
```

## 4.4 doFlush 核心逻辑

```java
private void doFlush(VmMetadataDirtyVO dirty, Runnable chainNext) {
    String vmUuid = dirty.getVmInstanceUuid();

    // 0. 记录刷写开始时的 dirtyVersion 快照
    long snapshotVersion = dirty.getDirtyVersion();

    // 1. 前置检查：VM 是否存在
    if (!dbf.isExist(vmUuid, VmInstanceVO.class)) {
        // VM 已删除，FK CASCADE 应已删除 dirty 行，兜底删除
        SQL.New(VmMetadataDirtyVO.class)
           .eq(VmMetadataDirtyVO_.vmInstanceUuid, vmUuid).delete();
        chainNext.run();
        return;
    }

    // 2. 发送 UpdateVmInstanceMetadataMsg → VmInstanceBase 负责构建 payload 并写入主存储
    //    payload 构建（buildVmInstanceMetadata）和大小保护均在 VmInstanceBase 内部完成
    UpdateVmInstanceMetadataMsg msg = new UpdateVmInstanceMetadataMsg();
    msg.setUuid(vmUuid);
    msg.setStorageStructureChange(dirty.isStorageStructureChange());
    msg.setTimeout(TimeUnit.MINUTES.toMillis(2));
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
private void onFlushSuccess(String vmUuid, long snapshotVersion) {
    // 条件删除：仅当 dirtyVersion == snapshotVersion 时删除
    // 即"刷写期间没有新的 markDirty 到来"
    int deleted = SQL.New(VmMetadataDirtyVO.class)
        .eq(VmMetadataDirtyVO_.vmInstanceUuid, vmUuid)
        .eq(VmMetadataDirtyVO_.dirtyVersion, snapshotVersion)
        .delete();

    if (deleted == 0) {
        // dirtyVersion > snapshotVersion → 刷写期间有新变更（markDirty 递增了 dirtyVersion）
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
    → 释放认领 → triggerFlush 立即重处理 → 读到 v2 → 写入 v2 ✓
```

如果不做 `dirtyVersion` 比较直接删除，T3 的变更就丢了——这正是 GC `deduplicateSubmit` 遇到的同类问题，新方案用版本号比较优雅解决。相比 `lastOpDate` 时间戳比较，`dirtyVersion` 整数比较语义更明确、无时间精度问题。

## 4.6 刷写失败处理

```java
private void onFlushFailure(String vmUuid, ErrorCode error) {
    VmMetadataDirtyVO dirty = dbf.findByUuid(vmUuid, VmMetadataDirtyVO.class);
    if (dirty == null) return;  // VM 已销毁，FK CASCADE 已清理

    int newRetryCount = dirty.getRetryCount() + 1;
    int maxRetry = VmGlobalConfig.VM_METADATA_MAX_RETRY.value(Integer.class);  // 默认 5

    if (newRetryCount >= maxRetry) {
        // 达到上限 → 告警 + 删除行
        // 下次该 VM 的 @MetadataImpact API 成功时会重新 markDirty，自然重试
        logger.error("metadata update for vm {} failed after {} retries, giving up. " +
                     "Will auto-retry on next API that modifies this VM.",
                     vmUuid, newRetryCount);
        SQL.New(VmMetadataDirtyVO.class)
           .eq(VmMetadataDirtyVO_.vmInstanceUuid, vmUuid).delete();
        return;
    }

    // 未达上限 → 释放认领 + 指数退避
    long delaySec = BASE_DELAY_SECONDS * (1L << Math.min(newRetryCount, MAX_EXPONENT));
    // BASE_DELAY_SECONDS=10, MAX_EXPONENT=10
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

> 延迟公式：`BASE_DELAY_SECONDS × 2^min(retryCount, MAX_EXPONENT)`，其中 `BASE_DELAY_SECONDS = 10`，`MAX_EXPONENT = 10`。默认 5 次重试，总耗时约 5 分钟后放弃。

## 4.7 辅助方法

```java
private void releaseClaim(String vmUuid) {
    SQL.New(VmMetadataDirtyVO.class)
       .eq(VmMetadataDirtyVO_.vmInstanceUuid, vmUuid)
       .set(VmMetadataDirtyVO_.managementNodeUuid, null)
       .update();
}
```

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
  ↓ 外层 ChainTask "update-vm-metadata-global" (syncLevel=N)
  ↓ 内层 ChainTask "update-vm-{vmUuid}-metadata" (maxPending=1)
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

> **OP type 由管理层面指定**：`@MetadataImpact(CONFIG)` → OP type=1（仅配置变更），`@MetadataImpact(STORAGE)` → OP type=2（存储拓扑变更，sblk 场景设置 pending_op=2）。OP type 通过 `storageStructureChange` 字段贯穿整条消息链（`VmMetadataDirtyVO` → `UpdateVmInstanceMetadataMsg` → `UpdateVmInstanceMetadataOnPrimaryStorageMsg` → `UpdateVmInstanceMetadataOnHypervisorMsg`）。dirty 行使用 OR 升级策略：多次 markDirty 中只要有一次是 STORAGE，本轮刷写即使用 OP type=2。

> **消息超时**：`UpdateVmInstanceMetadataMsg` 和 `UpdateVmInstanceMetadataOnHypervisorMsg` 均 `setTimeout(2min)`，防止大 payload 的 O_DIRECT 写入 + 可能的 lvextend 操作超出默认消息超时。

> 与 GC 方案消息链的详细对比见 [对比文档 §3](2/vm-metadata-new-02h-compare.md#3-消息调用链对比)。

## 5.2 消息路由策略

| 消息 | 路由方式 | 说明 |
|------|----------|------|
| `UpdateVmInstanceMetadataMsg` | `makeLocalServiceId` | Poller 本地发起 |
| `UpdateVmInstanceMetadataOnPrimaryStorageMsg` | `makeLocalServiceId` | 无本地状态依赖 |
| `UpdateVmInstanceMetadataOnHypervisorMsg` | `makeTargetServiceIdByResourceUuid(hostUuid)` | 需路由到 host-owner MN |

---

# 6. 并发控制（三层）

## 6.1 三层串行化保证

```
Layer 1 — DB CAS 认领
    UPDATE WHERE managementNodeUuid IS NULL → 同一行只被一个 MN 处理
    ⇒ 同一 VM 的刷写不会在两个 MN 上同时执行

Layer 2 — ChainTask 队列 "update-vm-{vmUuid}-metadata"
    syncLevel=1, maxPendingTasks=1
    ⇒ 同一 VM 最多 1 个正在执行 + 1 个排队
    ⇒ 超出时 exceedMaxPendingCallback() 释放认领

Layer 3 — 主存储级队列 "update-metadata-on-ps-{psUuid}"
    syncLevel = vm.metadata.ps.maxConcurrent (GlobalConfig, 默认 5)
    ⇒ 同一 MN 上，同一存储最多 N 个并发写入
    ⇒ 双 MN 环境下实际全局并发 = 2 × syncLevel
```

> 与 GC 方案并发控制的详细对比见 [对比文档 §4](2/vm-metadata-new-02h-compare.md#4-并发控制对比)。

## 6.2 全局限流

嵌套 ChainTask 结构：

```
外层: syncSignature = "update-vm-metadata-global"
      syncLevel = vm.metadata.global.maxConcurrent (默认 10)
  内层: syncSignature = "update-vm-{vmUuid}-metadata"
        syncLevel = 1, maxPendingTasks = 1, deduplicateString = syncSignature
```

- 外层控制全局并发数，每个 MN 最多 N 个 VM 同时更新
- 内层保证 per-VM 串行 + 去重
- 两层都是 JVM 本地 ChainTask，无跨 MN 开销

> **per-MN 语义**：外层 `syncLevel` 是 JVM 本地限制。双 MN 环境下实际全局并发最大为 `2 × syncLevel`。DB CAS 认领已保证同一 VM 不会在两个 MN 上同时执行，全局并发 2N 对存储层压力可控（Layer 3 per-PS 限流进一步约束）。

## 6.3 Layer 3 实现位置

各主存储 `handle(UpdateVmInstanceMetadataOnPrimaryStorageMsg)` 内部用 `thdf.chainSubmit()` 包装：

- `getSyncSignature()` → `"update-metadata-on-ps-" + self.getUuid()`
- `getSyncLevel()` → 读取 `VmGlobalConfig.VM_METADATA_PS_MAX_CONCURRENT`
- `run()` → 调用实际写入逻辑后 `chain.next()`

**外层全局队列与 Layer 3 的交互**：外层全局队列 `syncLevel=10` 限制单个 MN 上同时最多 10 个 VM 的元数据更新在执行。这 10 个并发任务分布在不同主存储上时，Layer 3 per-PS 队列 `syncLevel=5` 进一步约束同一存储的并发数。外层控制"总水位"，Layer 3 控制"每个 PS 的分水位"，二者嵌套生效。

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

T3:   MN-B nodeLeft(MN-A) → 立即触发一轮 Poller
      → 发现 vm-1 未认领 → CAS 认领 → 刷写 ✓
```

**接管延迟**：心跳超时(~30s) + nodeLeft 立即触发 ≈ **~30 秒**

> **讨论结论**：增加 `nodeLeft` 回调加速。实现 `ManagementNodeChangeListener`，在 `nodeLeft()` 时立即调用 `claimAndFlush()`，不等待下一个 Poller 周期。

```java
@Override
public void nodeLeft(ManagementNodeInventory inv) {
    // MN 宕机 → FK SET_NULL 已释放其认领的 dirty 行
    // 立即触发一轮 Poller，尽快接管
    claimAndFlush();
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

**接管延迟**：心跳超时(~30s) + nodeLeft 立即触发 ≈ **~30 秒**。

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

> 通常不需要精确均匀分配。如需更均匀可在 claim 查询中按 vmUuid 分片（`vmUuid % 2 = mnIndex`），但这引入了对 MN 数量的依赖，不推荐。

## 7.5 时序验证

### 正常态

```
MN-A: API 成功 → markDirty(vm-1) → INSERT dirty 行
MN-B: Poller → CAS claim → flush → 成功 → DELETE ✓
→ 任何一个 MN 都可以处理任何 VM 的 dirty 行 ✓
```

### MN 宕机

```
T0:   MN-A claim dirty(vm-1), 正在刷写
T1:   MN-A 宕机
T~30: MN-B 心跳检测 → 删除 ManagementNodeVO(A)
      → FK SET_NULL → dirty(vm-1).managementNodeUuid = NULL
T~30: MN-B nodeLeft(A) → 立即触发 claimAndFlush()
      → CAS claim vm-1 → flush → 成功 → DELETE ✓
```

### MN 加入

```
T0:   MN-A 独自运行，处理所有 dirty
T1:   MN-B 加入 → Poller 启动
T6:   MN-A Poller: claim 3 rows → flush
      MN-B Poller: claim 2 rows → flush
      → 自然分摊 ✓
```

---

# 8. 管理平面恢复策略

恢复策略表：

| 触发源 | 检测方式 | 管理平面行为 |
|--------|---------|-------------|
| 刷写达到重试上限 | `onFlushFailure()` | 告警日志 + 删除 dirty 行（下次 API 自动重试） |
| read 返回 NEED_REPAIR | 巡检/读取时 | `RepairMetadataMsg`（512B Header 写） |
| read 返回 CORRUPTED | 巡检/读取时 | `markDirty(vmUuid)`（全量重写） |
| read 返回 STORAGE_CHANGE_INCOMPLETE | 巡检/读取时 | `markDirty(vmUuid)` |
| VG 空间不足 | Agent 返回错误码 | 告警 + 退避 + 巡检重试 |
| 注册崩溃残留 | MN 启动/定时扫描 | Saga 回滚（5 条件判断） |
| 存储迁移失败 | 迁移 post-hook | 告警 + markDirty 自愈 |
| VM 销毁残留 | 销毁 post-hook + 巡检 | 孤儿 LV 检测 + 运维清理 |

## 8.1 重试上限后的恢复策略

> **讨论结论**：采用“告警 + 下次 API 触发自动重试”的简化策略，移除 MetadataStaleEvent → recovery cycle 机制，避免无限重试循环。

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

> 健康巡检可作为远期兜底方案：发现不一致时调用 `markDirty()` 触发全新重试（本期暂不实现）。

## 8.2 ~~健康巡检~~（暂缓，不在本期实现）

> **决策**：健康巡检功能暂时不做，后续版本根据实际运维需求再决定是否引入。
> 当前恢复策略依赖"告警 + 下次 API 触发自动重试"机制（§8.1），以及级联删除 + FK CASCADE 自动清理。

## 8.3 VM 销毁时的元数据清理

在 `DestroyVmInstanceFlow` 链中增加 `NoRollbackFlow` step：查找根卷所在 PS → `metadataStorageHandler.deleteMetadata()` → **best-effort**，失败仅 WARN 日志，不阻塞 VM 销毁。

孤儿检测：健康巡检中匹配 `_vmmeta` LV 但无对应 `VmInstanceVO` → 标记孤儿 → 审计日志。

> dirty 行的清理由 FK CASCADE 自动完成（VM 销毁 → VmInstanceEO 删除 → dirty 行级联删除）。

---

# 9. 升级后全量刷新

## 9.1 触发条件

在 `managementNodeReady()` 回调中执行：

1. 查询所有在线 `ManagementNodeVO`，收集 version 集合
2. 若存在多个不同版本（滚动升级中）→ 跳过
3. 版本唯一且与 `lastRefreshVersion`（GlobalConfig 持久化）不同 → 提交延迟 10 分钟的定时任务
4. 10 分钟后再次检查所有 MN 版本是否一致 → 一致则执行全量刷新，不一致则跳过

> **延迟 10 分钟的原因**：滚动升级期间，第一个 MN 升级完成时可能短暂出现"版本唯一"假象（旧 MN 尚未恢复上线）。

## 9.2 刷新执行（简化，无 LongJob）

> **讨论结论**：不需要 LongJob。直接批量 markDirty，Poller 自动处理。

```java
private void submitFullRefresh(String currentVersion) {
    List<String> vmUuids = getAllMetadataEnabledVmUuids();
    logger.info("metadata full refresh: {} VMs to refresh for version {}",
                vmUuids.size(), currentVersion);

    // 批量 INSERT dirty 行
    for (String vmUuid : vmUuids) {
        markDirty(vmUuid);  // INSERT ON DUPLICATE KEY UPDATE
    }
    // Poller 自动分批处理，ChainTask 自动限流

    // 更新 lastRefreshVersion
    VmGlobalConfig.VM_METADATA_LAST_REFRESH_VERSION.updateValue(currentVersion);
}
```

> 如果 VM 数量很大（万级），逐个 `markDirty()` 的 INSERT 可优化为批量 SQL：
> ```sql
> INSERT INTO VmMetadataDirtyVO (vmInstanceUuid)
> SELECT uuid FROM VmInstanceVO WHERE ...
> ON DUPLICATE KEY UPDATE lastOpDate = CURRENT_TIMESTAMP
> ```

---

# 10. Payload 大小保护

在 `VmInstanceBase.doHandleUpdateVmInstanceMetadata()` 中，`buildVmInstanceMetadata()` 构建 payload 后进行大小检查：

| 阈值 | 行为 | 说明 |
|------|------|------|
| > 8MB | WARN 日志 | 早期预警，提示运维关注 |
| > 30MB | ERROR + 拒绝写入 + reply 错误 | 保护 sblk LV 空间 |

正常 VM 的 metadata payload 通常在 10KB~500KB 范围内。超过 8MB 几乎一定表示异常（如快照未清理导致数千条记录）。

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
| D3 | Agent 端写入必须幂等（全量覆盖，不做增量 merge） | 同一 VM 的并发刷写（跨 MN 极端场景）最终应收敛到一致状态 | 增量 merge 可能导致数据残留或顺序依赖 |
| D4 | `exceedMaxPendingCallback` 中**不得**释放认领（`releaseClaim`） | 该行可能正在被同 MN 的 running task 处理，释放后其他 MN 可能并发认领 | 跨 MN 并发刷写同一 VM |

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
