# 测试策略

## 8.1 测试金字塔

```
         ┌──────────────────────┐
         │  E2E (~5 cases)       │
         ├──────────────────────┤
         │  Integration (~30)    │
         ├──────────────────────┤
         │  Unit (~100)          │
         └──────────────────────┘
```

## 8.2 单元测试（控制面）

`storage/src/test/.../VolumeSnapshotTreeReconcilerTest.java`：

- test_I1_physical_missing_db_present
- test_I2_orphan_file
- test_I3_parent_uuid_mismatch
- test_I4_install_path_swap
- test_I5_latest_flag_wrong
- test_idempotent_double_call
- test_max_fix_actions_circuit_breaker
- test_physical_unreachable
- test_sql_batch_fail
- **test_no_business_action_dispatched**（不变量护栏：spy CloudBus 验证从未发 Commit/Pull/Delete*Msg）

## 8.3 单元测试（数据面）

`kvmagent/test/test_snapshot_recovery.py`：

- test_chain_snapshot_dump_load
- test_take_chain_snapshot_with_missing_file
- test_verify_post_commit_backing_unchanged
- test_verify_post_commit_size_shrank
- test_verify_post_rebase_mismatch
- test_verify_post_pull_full_rebase
- test_qemu_img_check_corrupted
- test_blockjob_state_machine_pivot_path
- test_blockjob_timeout_cancellation
- test_recovery_file_lifecycle

## 8.4 集成测试（ZSTACK_SIMULATOR）

- TestSingleSnapshotDeleteCommitSuccess
- TestSingleSnapshotDeleteCommitFailReconcile
- TestSingleSnapshotDeletePullForkChain
- TestSingleSnapshotDeleteSqlBatchFail
- TestSingleSnapshotDeleteRetryIdempotent
- TestSingleSnapshotDeleteOrphanGc
- TestSingleSnapshotDeleteSiblingDbCorrection

## 8.5 E2E 测试

| 编号 | 步骤 |
|---|---|
| E1 | 5 层链 → 删中间快照（在线 commit）→ 验证文件链与 DB |
| E2 | 同 E1 + 中途 `kill -9 kvmagent` → 重启 → 验证 reconcile + 重试 |
| E3 | 分叉链（2 子节点）→ 删根节点 → 验证两子各自 backing 与 DB |
| E4 | 离线 pull 大文件（10 GB qcow2 fullRebase）→ 中途断电 → 启动恢复诊断 |
| E5 | 快照组（3 卷），其中 1 卷 reconcile 失败 → 验证其它卷不受影响 |
