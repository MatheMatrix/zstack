# 13 — Premium / CDP / 灾备兼容性

## 13.1 Premium 侧改动

搜索 `/d/0zw/zw/premium/` 中与单节点快照删除直接相关的代码：

| 文件 | 说明 |
|---|---|
| `mevoco/.../VolumeSnapshotDeletionOverlayVmMsg.java`（第6行） | 6 行 OverlayMessage 壳，**无 scope/direction 业务逻辑** |
| `CreateDataVolumeFromVolumeSnapshotGroupFlow.java` | 创建数据卷流程，与删除无关 |
| `CreateRootTemplateFromVolumeSnapshotFlow.java` | 创建模板流程，与删除无关 |
| 阿里云 Hybrid | `AliyunSnapshotCascadeExtension`，**不走** single 路径 |

**结论**：Premium **未重写** `VolumeSnapshotTreeBase` / `VolumeTree` / `VolumeSnapshotGroupBase`。single 删除完全由开源主库实现，Premium 无额外扩展。

## 13.2 CDP / StorageSnapshot 类型

`VolumeSnapshotTreeBase.java:836`：

```java
if (VolumeSnapshotConstant.STORAGE_SNAPSHOT_TYPE.toString().equals(currentRoot.getType())
        || Objects.equals(currentRoot.getVolumeType(), VolumeType.Memory.toString())) {
    deleteVolumeSnapshotAndSyncVolumeSize(new Completion(completion) { ... });
    return;
}
```

CDP / StorageSnapshot 类型 / Memory 快照绕过整个 commit/pull 逻辑，**直接调用存储层删除**。

原因：
- StorageSnapshot 是存储后端原生快照（如 RBD snapshot），ZStack 不掌握其链结构
- Memory 快照不是 qcow2 文件链
- 都不需要 commit/pull 合并

## 13.3 Ceph 不兼容

`CephPrimaryStorageBase` 未实现：
- `CommitVolumeSnapshotOnPrimaryStorageMsg`
- `PullVolumeSnapshotOnPrimaryStorageMsg`

普通 RBD 快照在 `cephdriver.py:87` 通过 `rbd snap rm` 删除，**无中间节点合并能力**。

例外：`CephPrimaryStorageBase.java:2984` 临时快照场景硬编码 `scope=Single, direction=Commit`，但这只是 ZStack 层面的删除消息标志，实际不走 commit 逻辑。

## 13.4 灾备 / 备份

经搜索：
- **未发现**灾备/CDP/Backup 调用链直接发 `DeleteVolumeSnapshotGroupInnerMsg`
- **未发现**对 single 模式的额外 cascade / 索引同步逻辑

## 13.5 OverlayMsg 串行化

`VolumeSnapshotDeletionOverlayVmMsg` 作用：把删除消息包裹后路由到 `VmInstance` 的 mailbox，保证与 VM 状态变更操作互斥。Premium 侧的 OverlayMsg 与开源侧一致，无额外业务。
