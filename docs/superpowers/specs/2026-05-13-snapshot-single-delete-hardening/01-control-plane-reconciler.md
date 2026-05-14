# 控制面：VolumeSnapshotTreeReconciler

## 5.1 类设计

**位置**：`storage/src/main/java/org/zstack/storage/snapshot/VolumeSnapshotTreeReconciler.java`

```java
public class VolumeSnapshotTreeReconciler {
    @Autowired private CloudBus bus;
    @Autowired private DatabaseFacade dbf;

    public ReconcileResult reconcile(String treeUuid, String volumeUuid,
                                     ReconcileTrigger trigger);
}

public class ReconcileResult {
    boolean consistent;
    List<FixAction> appliedActions;
    List<InconsistencyReport> remaining;
}

public enum ReconcileTrigger {
    AfterCommitSuccess, AfterCommitFail,
    AfterPullSuccess,   AfterPullFail,
    AfterDeleteSuccess, AfterDeleteFail,
}
```

## 5.2 工作流程

```
reconcile(treeUuid, volumeUuid, trigger):
  1. 读 DB：Q.New(VolumeSnapshotVO).eq(treeUuid).list()
     若结果为空（dst 是树根的 commit 已完成切换到新 treeUuid 场景）
        → 通过 volumeUuid 查 latest VO，反推真实 treeUuid 重新加载
  2. 读物理：对每个 alive 叶节点发 GetVolumeBackingChainFromPrimaryStorageMsg
            （分叉链时多发，合并去重得到全树物理 chains）
            + GetSnapshotInstalledPathExistenceMsg
  3. 比对 → InconsistencyReport[]
  4. 翻译为 FixAction（受限动作集）
  5. 顺序执行；失败的进 remaining
```

**注**：step 2 对分叉链需遍历所有 alive 叶节点，而不是仅当前 volume.installPath 这条线性 chain，
否则 I4（installPath 错位到非当前叶所在分支）会漏检。

## 5.3 不一致检测（5 类）

| ID | 名称 | 检测 | 修复 |
|---|---|---|---|
| **I1** | 物理已不存在 / DB 仍有 | `physical.exists=false && dbVO != null` | DELETE_DB_VO + 重算 distance/parent |
| **I2** | DB 已删 / 物理仍在 | `physical.exists=true && dbVO=null` | SCHEDULE_GC_ORPHAN_FILE |
| **I3** | parentUuid 不一致 | `db.parent != null && physical.backing != db.parent.installPath`（必须先排除悬空 → I3b 优先评估）| UPDATE_DB_PARENT_UUID + distance |
| **I3b** | 悬空 parentUuid | `db.parentUuid != null && Q(VolumeSnapshotVO).eq(uuid, parentUuid) == null`（兄弟 rebase 完成后 parent VO 已被删，自身 parentUuid 仍指向已删 UUID）| 三种子情形：(a) `physical.backing` 能反查到树内某 alive VO → UPDATE_DB_PARENT_UUID = 该 VO.uuid；(b) `physical.backing == null`（已 rebase 到卷 base）→ UPDATE_DB_PARENT_UUID(null)；(c) `physical.backing` 存在但反查不到任何 alive VO（指向已被 stepDelete 的 VO 物理路径，物理 rebase 尚未发生）→ 不动 DB，记 remaining 由下次重试推动物理 rebase 后再修 |
| **I4** | installPath 不一致 | DB.installPath 物理不存在但能在树内任一 alive 叶 backing chain 中找到该 uuid 对应物理位置 | UPDATE_DB_INSTALL_PATH + size |
| **I5** | latest 标志错位 | aliveChain 末端 latest=false 或非末端 latest=true | UPDATE_DB_LATEST_FLAG |

## 5.4 受限动作集

```java
public enum FixActionType {
    DELETE_DB_VO,
    UPDATE_DB_PARENT_UUID,
    UPDATE_DB_INSTALL_PATH,
    UPDATE_DB_LATEST_FLAG,
    SCHEDULE_GC_ORPHAN_FILE
}
```

**显式禁止**：reconciler 不发 Commit/Pull/Delete*Msg、不调 agent rebase。修物理的责任全部在 agent 层。

**评估顺序**（强制）：
1. I1（自身物理不存在）
2. I3b（parent 悬空）— 必须先于 I3，避免 `db.parent` 为 null 时 I3 NPE
3. I3（parent 存在但 installPath 不一致）— 仅在 `db.parent != null` 时评估
4. I4（自身 installPath 错位）
5. I5（latest flag 错位）
6. I2（孤儿物理文件）— 最后处理，避免误删与 I1/I4 修复相关文件

## 5.5 调用点

`VolumeSnapshotTreeBase.java` 修改：

```java
private void commit(VolumeSnapshotLeaf child, VolumeTree tree, boolean online, Completion comp) {
    final String treeUuid = currentRoot.getTreeUuid();
    final String volumeUuid = volume.getUuid();
    final boolean dstIsRoot = (dstSnapshotInv.getParentUuid() == null);

    FlowChain chain = ... .done(new FlowDoneHandler(comp) {
        public void handle(Map data) {
            logReconcile(reconciler.reconcile(treeUuid, volumeUuid, AfterCommitSuccess));
            // dst 是根节点：updateDatabaseAfterCommit 会创建新 treeUuid 并迁移 VO
            // 此时旧 treeUuid 下已无 VO，需对账新 treeUuid（reconciler 内部通过 volumeUuid 反查）
            // 此处显式再调一次以护栏
            if (dstIsRoot) {
                logReconcile(reconciler.reconcile(null, volumeUuid, AfterCommitSuccess));
            }
            comp.success();
        }
    }).error(new FlowErrorHandler(comp) {
        public void handle(ErrorCode err, Map data) {
            try { logReconcile(reconciler.reconcile(treeUuid, volumeUuid, AfterCommitFail)); }
            catch (Throwable t) { logger.warn("reconcile failed", t); }
            comp.fail(err);
        }
    });
    chain.start();
}
```

`pull()` 与 `deleteVolumeSnapshotAndSyncVolumeSize()` 同结构改造。

**dst-is-root 双树对账**：commit 根节点时 SQLBatch 会 `persist(newTree)` 并把 src 子树迁到新 treeUuid（详见 `docs/snapshot-single-delete/05-commit-db-swap.md` §5.3）。
若调用方持有的是旧 treeUuid，reconciler step 1 会扫到空集合 → 通过 volumeUuid 反查 latest VO 即可拿到新 treeUuid，
所以传 `null` treeUuid 是合法签名，由 reconciler 自动解析。

**成功路径触发策略**：
- Phase 1-2（灰度观察期）：`done` 和 `error` 都触发，验证 reconciler 检测准确率
- Phase 4（默认开启后）：保留双触发。理由：L2 失败抛 `PostOpVerifyError` 已走 `error` 分支；
  但 SQLBatch 成功 + agent reply 路径也可能由于"agent 实际成功 reply 误标 fail"（场景 1 镜像）使 DB 与物理静默漂移，
  成功路径对账可在低概率下捕获这种漏报。每次成功操作的对账代价由 ISSUE 1 的锁外异步采样化解。

## 5.6 设计不变量

- **幂等收敛**：多次调用结果相同；不会把已一致状态修坏
- **不抛异常给调用方**：reconciler 失败不让 commit/pull 的成功变失败
- **同步运行在 chainSubmit 锁内**：reconciler 在 commit/pull 的 done/error 回调内同步执行，期间持 chainSubmit 锁；不引入额外锁、不做 CAS。串行性由外层 vm 队列 + chainSubmit 双重保证（见 §5.6.1）
- **SQLBatch 单事务**：所有 DB 修补原子

### 5.6.1 串行性来源（不需要额外锁的依据）

`APIDeleteVolumeSnapshotGroupMsg` → `VolumeSnapshotGroupBase.handleDelete` 通过 `overlaySend(DeleteVolumeSnapshotGroupInnerMsg)` 把请求排到 vm 队列；`completion.done()` 在 `overlaySend` 回调内调用，回调返回前下一个排队请求无法进入。叠加同一棵快照树的 `chainSubmit` 串行：

```
vm 队列 ──► chainSubmit ──► commit/pull flow ──► done/error ──► reconciler ──► comp.success/fail ──► chainSubmit 释放 ──► vm 队列释放
```

因此 reconciler 跑完前不会有任何同卷 / 同组的新请求观察到中间状态。原计划的"段 2 释放 chainSubmit + CAS"为冗余设计，已废弃。reconciler 内部仍然按 §5.2 顺序"读 DB → 拉物理 → SQLBatch 修补"线性执行，全程持锁。

## 5.7 熔断与降级

| GlobalConfig | 默认 | 含义 |
|---|---|---|
| `volumeSnapshot.reconciler.enabled` | true | 总开关 |
| `volumeSnapshot.reconciler.timeout.sec` | 30 | 拉物理 chain 超时 |
| `volumeSnapshot.reconciler.maxFixActions` | 50 | 单次最多修补数（熔断）|

## 5.8 可观测性

```
[VolumeSnapshotTreeReconciler] tree=<uuid> trigger=AfterCommitSuccess
  inconsistencies: I3(snap-a parentUuid mismatch), I2(orphan-file /xxx.qcow2)
  applied: UPDATE_DB_PARENT_UUID(snap-a), SCHEDULE_GC_ORPHAN_FILE(/xxx.qcow2)
  remaining: []
  duration_ms: 152
```
