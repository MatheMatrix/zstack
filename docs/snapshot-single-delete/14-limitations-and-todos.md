# 14 — 已知限制 / TODO / FIXME

## 14.1 代码注释中的 TODO

### `VolumeTree.java:258`
```java
// TODO(clone) : When both chain cloning and single-node snapshot deletion are enabled,
//  it is necessary to consider the dependency relationships of all snapshot nodes in the
//  current snapshot tree within the VolumeSnapshotReferenceVO.
```
链克隆 + single 删除同时启用时，`VolumeSnapshotReferenceVO` 依赖关系未处理。

### `VolumeTree.java:394`
```java
// TODO(clone) : When both chain cloning and single-node snapshot deletion are enabled,
//  the following three functions must take into account the dependencies within the snapshot chain.
```
针对 `updateDatabaseAfterPullToVolume`、`updateDatabaseAfterPull`、`updateDatabaseAfterCommit`。

### `VolumeSnapshotTreeBase.java:355`
```java
// TODO: BUG FIX, when deleting a volume the cascade extension will send messages to all snapshots
// of this volume, which the oldest snapshot will delete descendant snapshots and set the volumeUuid
// to NULL for all snapshots, so the after messages are useless
```
卷删除时级联消息冗余。

### `VolumeSnapshotTreeBase.java:1325`
```java
//TODO add gc
```
物理文件删除失败无 GC 补偿。

### `VolumeSnapshotTreeBase.java:1520`
```java
//TODO: remove this
```

### `VolumeSnapshotTreeBase.java:2169`
```java
// TODO: refactor this: VolumeSnapshotGroupVO should has its own cascade extensions!
```

## 14.2 限制汇总

| 限制 | 位置 | 影响 |
|---|---|---|
| 链克隆 + single 不兼容 | `VolumeTree.java:258, 394` | `VolumeSnapshotReferenceVO` 依赖未维护，可能误删共享数据 |
| 物理删除无 GC | `VolumeSnapshotTreeBase.java:1325` | 文件/LV 泄露 |
| 卷删除级联消息冗余 | `VolumeSnapshotTreeBase.java:355` | 性能浪费，无功能影响 |
| 兄弟节点 parentUuid 暂不一致 | `updateDatabaseAfterCommit` | DB 与物理短暂不一致，依赖后续递归修复 |
| pull 但需 commit 抛 RuntimeException | `VolumeTree.java:371` | 未封装 ErrorCode，前端体验差 |
| VmState 限制 | `VolumeSnapshotTreeBase.java:854` | Migrating / Unknown 状态直接失败 |
| Ceph RBD 不支持 | `CephPrimaryStorageBase` | 无 commit/pull 实现，普通 RBD 快照无法 single 删除 |
| Group 无 Availability 检查 | `VolumeSnapshotGroupBase.java:212` | 删除前不检查组成员状态 |
| Group 并发度固定 5 | `:243` | 大组删除可能耗时长，但不可调 |
| Group 无整体回滚 | `:212-254` | 部分成功保留，需调用方处理错误列表 |

## 14.3 设计取舍

| 决策 | 理由 |
|---|---|
| 默认 `scope=chain` | 保持向后兼容，避免老 API 调用方行为突变 |
| 多子节点强制 pull | commit 会改 dst 路径，破坏其它兄弟语义 |
| 优先非 online 子节点 | 避开 qemu 持有的活跃 backing 链 |
| commit 用 path 互换 | 避免修改快照 uuid，保持外部引用稳定 |
| 失败不回滚 | 存储操作不可逆，靠幂等性支持重试 |
| 删除前不查 GroupAvailability | 由下层 `isOperationAllowed` 自校验，避免重复 |

## 14.4 后续改进建议（基于代码）

1. **Ceph 支持**：考虑用 `rbd snap flatten` + RBD clone 实现单节点删除
2. **GC 机制**：为 `deleteVolumeSnapshotAndSyncVolumeSize` 失败的物理文件加 GC 任务
3. **错误码封装**：`resolveDirection` 的 `IllegalArgumentException` 换成 `ErrorCode`
4. **链克隆兼容**：`VolumeSnapshotReferenceVO` 在 commit/pull DB 更新时同步处理
5. **并发度可配**：Group 删除并发度做成 GlobalConfig
6. **VmState 扩展**：评估 Migrating 等状态的支持
