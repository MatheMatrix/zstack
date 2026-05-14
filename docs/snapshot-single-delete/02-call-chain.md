# 02 — 处理链路总览

## 2.1 快照组删除链路

```
APIDeleteVolumeSnapshotGroupMsg
 └─ VolumeSnapshotGroupBase.handle()                   GroupBase.java:163
     └─ handleDelete()                                  GroupBase.java:187
         └─ DeleteVolumeSnapshotGroupInnerMsg            (携带 scope/direction)
             └─ While 循环每个 VolumeSnapshotVO         GroupBase.java:212
                  └─ DeleteVolumeSnapshotMsg(scope,direction)
                       └─ VolumeSnapshotTreeBase
                            └─ deletion()                TreeBase.java:358
                                ├─ scope=chain  → deleteChainFlows()    :487
                                └─ scope=single → deleteSingleFlows()   :828
                                     └─ stepDelete()                    :875
                                          ├─ 叶节点  → deleteVolumeSnapshotAndSyncVolumeSize
                                          ├─ 单子节点 → resolveDirection → commit() / pull()
                                          └─ 多子节点 → pull() （强制）
```

## 2.2 关键透传点

`VolumeSnapshotGroupBase.java:221-228`：
```java
DeleteVolumeSnapshotMsg rmsg = new DeleteVolumeSnapshotMsg();
rmsg.setScope(msg.getScope());
rmsg.setDirection(msg.getDirection());
bus.makeTargetServiceIdByResourceUuid(rmsg, VolumeSnapshotConstant.SERVICE_ID, ...);
```

## 2.3 关键类索引

| 文件 | 作用 |
|---|---|
| `header/.../APIDeleteVolumeSnapshotMsg.java:49` | 单快照 API 入口 |
| `header/.../APIDeleteVolumeSnapshotGroupMsg.java:24` | 快照组 API 入口 |
| `storage/.../group/VolumeSnapshotGroupBase.java:212` | Group → 单快照消息分发 |
| `storage/.../VolumeSnapshotTreeBase.java:473` | scope 分支点 |
| `storage/.../VolumeSnapshotTreeBase.java:875` | stepDelete 递归 |
| `storage/.../VolumeSnapshotTreeBase.java:921` | commit() 流程 |
| `storage/.../VolumeSnapshotTreeBase.java:1097` | pull() 流程 |
| `storage/.../VolumeTree.java:364` | resolveDirection 决策 |
| `storage/.../VolumeTree.java:418/471` | updateDatabaseAfter Pull/Commit |
| `plugin/kvm/.../KVMHost.java:1043/1159` | 在线 commit/pull |
| `kvmagent/plugins/vm_plugin.py:3915` | libvirt blockCommit 核心 |
| `zstacklib/utils/linux.py:1389` | qcow2 工具函数 |
