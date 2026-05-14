# 不变量护栏总结 / 范围之外

## 10. 不变量护栏总结

设计的核心护栏（任意一项被破坏即视为设计失败）：

1. reconciler 永远不发 Commit/Pull/Delete*Msg（单元测试强制）
2. reconciler 不抛异常给调用方
3. reconciler 多次调用结果一致（幂等收敛）
4. agent L2 失败必抛 PostOpVerifyError，不静默
5. L1 dump 文件成功必删，失败必留
6. FlowChain error 路径必先 reconcile 后 fail
7. maxFixActions 熔断保护（默认 50）
8. 所有 GlobalConfig / kvmagent.conf 开关可独立关闭

## 11. 范围之外

- Ceph RBD：本设计不涉及（普通 RBD 快照不支持 commit/pull，超出 single 删除范围）
- **StorageSnapshot / Memory 快照 / CDP**：在 `VolumeSnapshotTreeBase.java:836` 提前 return，绕过 commit/pull 路径，由 `deleteVolumeSnapshotAndSyncVolumeSize` 直接处理，无需加固（详见 `docs/snapshot-single-delete/13-premium-and-cdp.md` §13.2）
- 链克隆 + single 删除并存（VolumeSnapshotReferenceVO TODO）：独立议题
- 全量定时 GC：本设计不引入；只做"操作后局部对账"
- VmState 扩展（如 Migrating）：独立议题
- 快照组并发度可配：独立议题
