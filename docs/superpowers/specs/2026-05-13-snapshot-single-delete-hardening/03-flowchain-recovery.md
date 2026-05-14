# FlowChain 改造（混合恢复策略）

## 7.1 可逆性分类

| Flow | 可逆性 | 失败策略 |
|---|---|---|
| AllocatePrimaryStorageSpaceMsg | ✅ 可逆 | FlowChain 自带 rollback |
| CommitOnHypervisor/PrimaryStorage | ❌ 不可逆 | 不回滚，reconciler 前进式补全 |
| updateDatabaseAfterCommit | ✅ 事务回滚 | SQLBatch 自然回滚 + reconciler 二次对账 |
| 兄弟节点 rebase | ❌ 不可逆 | agent L2 抛错 → reconciler 修 DB parentUuid |

## 7.2 改造模板

见 `01-control-plane-reconciler.md` 5.5 节代码示例：error 回调先 reconcile 再 fail，原错误向上抛但 DB 已尽力收敛。

## 7.3 子流程失败处理

- **doCommitOnHypervisorOrPrimaryStorageFlow**：agent 抛错 → flow fail → reconciler 反查物理实际状态 → 修 DB；agent 实际成功但回复丢失的场景，reconciler 把 DB 推到"成功后状态"，但**仍返回原错误**给用户
- **updateDatabaseAfterCommitFlow**：SQLBatch 失败 → 物理已变 DB 未变 → reconciler 反推应有 DB 状态 → 重新 SQL；二次失败进 remaining
- **兄弟节点 rebase**：agent 单个 child 失败立即抛 → reconciler 比对每个 child backing 与 DB parentUuid 逐个修

## 7.4 异常场景验证（手算）

**场景 1：在线 active commit pivot 后 agent 进程死**

1. agent L1 dump 已写盘
2. 控制面 commit flow 超时 → error 回调
3. reconciler.reconcile(AfterCommitFail)
   - 拉物理 chain：base 已合并完成、top 已删
   - 检 I4（installPath 不一致）→ UPDATE_DB_INSTALL_PATH
   - 检 I3（src.parentUuid 仍指 dst）→ UPDATE_DB_PARENT_UUID
4. 用户收到原错误（commit timeout）
5. 重试删除 → DB 已收敛 → 走快速路径直接 deleteVolumeSnapshotAndSyncVolumeSize

✅ 闭环

**场景 2：DB 翻转 SQL 失败**

1. 物理已 commit 完成，updateDatabaseAfterCommitFlow 失败
2. reconciler 反推修 DB

✅ 闭环

**场景 3：兄弟节点 rebase 中途失败（5 个兄弟 rebase 完 2 个失败）**

1. agent L2 在第 3 个兄弟报错 → flow fail
2. reconciler 读所有兄弟 backing：
   - 已 rebase 的 2 个：`physical.backing` 已变（指向 base），DB `parentUuid` 仍指 dst（被删 VO）
     → I3b 子情形 (a) 触发：physical.backing 反查到 base.uuid → UPDATE_DB_PARENT_UUID = base.uuid
   - 未 rebase 的 3 个：`physical.backing` 仍指 dst.installPath（dst VO 已删，反查不到 alive VO）
     → I3b 子情形 (c) 触发：不动 DB，记 remaining，等下次重试推动物理 rebase
   - dst 自身：物理仍存在 + DB VO 已被 stepDelete 删 → I2 触发，SCHEDULE_GC_ORPHAN_FILE
     - 注：因 I2 评估顺序最末（见 `01` §5.4），不会误删尚被未 rebase 兄弟引用的 dst.installPath；
       SCHEDULE_GC 内部会再检物理是否仍被引用，若是则放弃删除
3. 重试删除请求 → reconciler 第二轮：未 rebase 的 3 个仍是 I3b(c)，agent 重做 rebase 后变 (a)；最后 dst 失去引用，GC 才真清

✅ 闭环（依赖 I3b 三子情形 + I2 末位评估，详见 `01-control-plane-reconciler.md` §5.3 / §5.4）

**场景 4：reconciler 自身 SQL 失败**

1. remaining[] 记录 + warn 日志
2. 下次任何对该树操作再次触发对账
3. 持续不一致 → 运维介入

✅ 至少不越修越坏

## 7.5 并发与锁

- reconciler 在 chainSubmit 锁内同步执行（commit/pull 的 done/error 仍持锁，期间不释放）
- **不引入额外锁、不做 CAS**：串行性由外层双重保护——
  - vm 队列：`APIDeleteVolumeSnapshotGroupMsg` 通过 `overlaySend` 排到 vm 队列，`completion.done()` 在 reconciler 跑完后才执行，下一个请求才能出队（见 `01` §5.6.1）
  - chainSubmit：同一棵快照树的所有 commit/pull 已串行
- 跨树并发：reconciler 只动当前 treeUuid VO，无冲突
- GC 异步框架自身去重，与新业务并发无影响

**代价权衡**：reconciler 期间持 chainSubmit + vm 队列锁，意味着同卷 / 同组下一个请求最多等待一次 reconcile（含 `GetVolumeBackingChainFromPrimaryStorageMsg` 网络往返，超时由 `volumeSnapshot.reconciler.timeout.sec=30` 兜底）。但用户调用本来就是串行排队，等待落在原本要排队的请求上，没有放大延迟。
