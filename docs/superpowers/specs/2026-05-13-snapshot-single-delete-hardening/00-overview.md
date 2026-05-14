# 单盘快照删除一致性加固设计 - 总览

- 状态：Draft
- 日期：2026-05-13
- 关联：ZSV-5799；MR zstack#7674 / premium#10776 / utility#5743
- 调研基线：`docs/snapshot-single-delete/00-overview.md`

## 文档拆分

| 文件 | 内容 |
|---|---|
| `00-overview.md` | 背景 / 目标 / 约束 / 整体架构（本文） |
| `01-control-plane-reconciler.md` | 控制面 VolumeSnapshotTreeReconciler 设计 |
| `02-data-plane-validation.md` | 数据面 4 层 L1-L4 校验 |
| `03-flowchain-recovery.md` | FlowChain 混合恢复策略与异常场景 |
| `04-testing-strategy.md` | 测试金字塔与用例清单 |
| `05-rollout-plan.md` | 灰度 / 监控 / 回滚 / 风险登记 |
| `06-invariants-and-scope.md` | 不变量护栏总结 / 范围之外 |

---

## 1. 背景

ZSV-5799 引入了 `scope=single` 单节点快照删除（commit/pull 路径）。现有实现的关键不足（详见 `docs/snapshot-single-delete/14-limitations-and-todos.md`）：

- **物理文件泄漏**：commit/pull/delete 物理失败后只 warn，文件/LV 残留
- **DB 不一致**：DB 翻转后失败留下错位 path、悬空 parentUuid、兄弟节点 backing 与 DB parentUuid 不一致
- **重试不幂等**：失败后中间状态可能让重试失败
- **节点孤立**：分叉链兄弟节点物理 rebase 完成、DB 未更新
- **在线 VM**：active commit pivot 状态机不严谨

## 2. 目标

加固现有删除单盘快照逻辑，确保：

1. **不变量 1**：操作结束后 DB `(uuid, installPath, parentUuid, distance, treeUuid)` 与物理 qcow2 backing chain 必须一致
2. **不变量 2**：失败重试可从任意中间状态推进到目标态，**不依赖任何额外状态字段**
3. **不变量 3**：物理删除失败不破坏不变量 1（VO 删，孤儿文件由 warn 记录）

## 3. 约束与决策

| 维度 | 决策 |
|---|---|
| 一致性范围 | 物理泄漏 + DB 一致 + 重试幂等 + 在线 VM 安全，全部覆盖 |
| 状态机 | **不加新表 / 不加新字段**，靠扫描 + qcow2 物理状态推断 |
| GC 触发 | **只在操作完成 / 失败后** 跑当前快照树的局部对账 |
| 控制面预检 | 不做；首次执行走轻量路径 |
| 数据面校验 | L1 dump + L2 verify + L3 check + L4 blockJob 状态机加固，全开 |
| 物理删除失败 | 维持现状（VO 删 + warn） |
| 失败恢复 | 混合策略：可逆 flow rollback；不可逆 flow 由 reconciler 前进式补全 |

## 4. 整体架构

```
                          ┌─────────────────────────────────────────────┐
 用户 / API               │  控制面（zstack management）                  │
 APIDeleteVolumeSnapshotMsg                                              │
        │                 │  ┌─────────────────────────┐                  │
        ▼                 │  │ VolumeSnapshotTreeBase   │                 │
 VolumeSnapshotTreeBase    │  │  deletion()              │                 │
        │                 │  │   stepDelete()           │                 │
        │ commit/pull/del │  └────────┬─────────────────┘                 │
        ▼                 │           │ success/fail                       │
 FlowChain                │           ▼                                    │
        │                 │  ┌─────────────────────────┐                  │
        │ each step ends  │  │ VolumeSnapshotTreeReconciler （新）        │
        └────────────────►│  │  reconcile(treeUuid)                       │
                          │  │   1) 拉物理 backing chain                    │
                          │  │   2) 与 DB 比对                              │
                          │  │   3) 输出 fix actions（受限动作集）             │
                          │  │   4) 顺序执行；记 remaining                    │
                          │  └────────┬─────────────────┘                 │
                          │           │                                    │
                          └───────────┼────────────────────────────────────┘
                                      │ GetVolumeBackingChainFromPrimaryStorageMsg
                                      ▼
                          ┌─────────────────────────────────────────────┐
                          │  数据面（kvm agent）                          │
                          │                                              │
                          │  vm_plugin.py / *_plugin.py                  │
                          │   ├─ L1 操作前 dump chain → recovery file     │
                          │   ├─ qemu-img commit/rebase 主操作            │
                          │   ├─ L2 操作后 verify_backing_chain           │
                          │   ├─ L3 异常路径 qemu-img check               │
                          │   └─ L4 _wait_for_block_job 状态机加固         │
                          └─────────────────────────────────────────────┘
```

**核心组件三处**：

1. **控制面**：抽出 `VolumeSnapshotTreeReconciler`（新类，不是新服务），负责 DB ↔ 物理对账
2. **数据面**：4 层校验工具集中在 `kvmagent/zstacklib/utils/snapshot_recovery.py`（新建），所有存储后端共享
3. **FlowChain**：success / fail 回调都先调 reconciler
