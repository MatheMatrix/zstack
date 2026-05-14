# 上线计划

## 9.1 灰度

```
Phase 1 (周 1)：默认 false 上线
  - 仅日志旁路：reconcile 跑但不执行 FixAction
  - 验证检测准确率

Phase 2 (周 2)：测试环境开启
  - 全测试集群 enabled=true，跑 E2E + 压力

Phase 3 (周 3-4)：开发/UAT 集群灰度
  - 一台真实业务集群打开，观察一周

Phase 4 (周 5+)：默认开启
  - release notes，保留 GlobalConfig 关闭通道
```

## 9.2 监控告警

| 日志 grep | 阈值 |
|---|---|
| `[VolumeSnapshotTreeReconciler] applied:` | > 10/h |
| `[VolumeSnapshotTreeReconciler] remaining:` | > 0 |
| `[VolumeSnapshotTreeReconciler] circuit-breaker triggered` | 立即 |
| `PostOpVerifyError` | > 5/h |
| `recovery: corrupted file` | 立即 |

## 9.3 文档

| 产出 | 位置 |
|---|---|
| 设计 spec | 本目录 |
| 运维手册 | `docs/snapshot-single-delete/15-operation-runbook.md` |
| Reconciler 排错指南 | 同上附录 |
| GlobalConfig | release notes |

## 9.4 回滚预案

1. **快速止血**：`updateGlobalConfig volumeSnapshot reconciler.enabled false`
2. **代码回滚**：reconciler 调用全 try-catch，关闭等价于现状
3. **数据修复**：reconciler 只动 DB 不动物理，最坏 SQL 反向恢复

agent 侧 L1/L2/L4 经 `kvmagent.conf` 开关独立回滚。

## 9.5 风险登记

| 风险 | 等级 | 缓解 |
|---|---|---|
| reconciler 误判物理状态错改 DB | 高 | 单元测试 + 灰度日志旁路 + circuit-breaker |
| L1 dump 文件累积撑爆磁盘 | 中 | 24h 自动清理 + 磁盘监控 |
| L4 状态机改造引入回归 | 中 | 单元测试 + fallback 开关 |
| 对账 SQL 与并发新建快照冲突 | 低 | chainSubmit 已串行 |
| GCJob 入队过多 | 低 | 现有框架去重 |
